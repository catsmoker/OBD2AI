package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.diagnostics.R as DiagnosticsR
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.diagnostics.DtcDictionary
import com.catsmoker.obd2ai.diagnostics.DtcInfo
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.MilStatus
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Lifecycle of a DTC scan; [FAILED] means "the read failed" — only [DONE] may say "no codes". */
enum class ScanState { IDLE, SCANNING, DONE, FAILED }

/** Process-wide dictionary cache so repeated scans don't re-read the raw resource. */
private object DictionaryCache {
    var dictionary: Map<String, DtcInfo>? = null
}

/**
 * State holder for the fault-code overview: the DTC scan across
 * stored/pending/permanent sources, MIL status, per-code AI assessment with
 * offline fallback, and clearing. Honesty rules live here — a failed read is
 * [ScanState.FAILED] with the real reason, never a fake empty list.
 */
@HiltViewModel
class ErrorOverviewViewModel @Inject constructor(
    private val obdHelper: ObdHelper,
    private val aiService: AiService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val codes = mutableStateOf<List<DtpCodeDTO>>(emptyList())
    val mil = mutableStateOf<MilStatus?>(null)
    val adapterLine = mutableStateOf("")
    val showAdapter = mutableStateOf(false)
    val scanState = mutableStateOf(ScanState.IDLE)
    val failureMessage = mutableStateOf("")
    val clearDialog = mutableStateOf(false)
    val cleared = mutableStateOf(false)

    /**
     * When [codes] came from disk instead of this session's scan, this holds the
     * save time so the screen can say so. Null once a fresh scan lands.
     */
    val cachedAt = mutableStateOf<Long?>(null)

    init {
        // The saved list may carry paid AI assessments: show it rather than an
        // empty "not scanned yet" screen, clearly labelled as a saved scan.
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { DtcStore.loadScan(appContext) }
            if (saved.codes.isEmpty()) return@launch
            if (codes.value.isEmpty() && scanState.value == ScanState.IDLE) {
                codes.value = saved.codes
                ObdDataHolder.dtpResults = saved.codes
                cachedAt.value = saved.savedAtMs ?: 0L
            }
        }
    }

    /** Adapter/vehicle identity is informational: never blocks the scan. */
    fun loadAdapterInfo() {
        if (adapterLine.value.isNotEmpty() || !obdHelper.isConnected) return
        viewModelScope.launch {
            val health = async(Dispatchers.IO) { runCatching { obdHelper.getAdapterHealth() } }
            val vehicle = async(Dispatchers.IO) { runCatching { obdHelper.getVehicleInfo() } }
            val healthResult = health.await().getOrNull()
            val vehicleResult = vehicle.await().getOrNull()
            if (healthResult != null || vehicleResult != null) {
                adapterLine.value = listOfNotNull(
                    healthResult?.let {
                        appContext.getString(
                            R.string.error_overview_adapter_health,
                            it.first, it.second, it.third
                        )
                    },
                    vehicleResult?.let {
                        appContext.getString(
                            R.string.error_overview_vehicle_info,
                            it.first, it.second, it.third
                        )
                    }
                ).joinToString("\n")
                showAdapter.value = true
            }
        }
    }

    fun scan() {
        if (scanState.value == ScanState.SCANNING) return
        scanState.value = ScanState.SCANNING
        failureMessage.value = ""
        cleared.value = false
        // A new read invalidates the freeze frame from the previous one.
        FreezeFrameCache.invalidate()
        viewModelScope.launch {
            try {
                val stored = async(Dispatchers.IO) { runCatching { obdHelper.getDtpCodes() } }
                val pending = async(Dispatchers.IO) { runCatching { obdHelper.getPendingDtpCodes() } }
                val permanent = async(Dispatchers.IO) { runCatching { obdHelper.getPermanentDtpCodes() } }
                val milRead = async(Dispatchers.IO) { runCatching { obdHelper.getMilStatus() } }
                val reads = listOf(stored.await(), pending.await(), permanent.await())
                // One unsupported mode must not discard the others; only a
                // cycle where every read failed is a real failure.
                if (reads.all { it.isFailure }) {
                    throw reads.firstNotNullOf { it.exceptionOrNull() }
                }
                val allCodes = reads.mapNotNull { it.getOrNull() }.flatten().distinct()
                val sourceMap = ObdHelper.mergeCodeSources(
                    stored.await().getOrDefault(emptyList()),
                    pending.await().getOrDefault(emptyList()),
                    permanent.await().getOrDefault(emptyList())
                )
                val milStatus = milRead.await().getOrNull()
                withContext(Dispatchers.Main) {
                    mil.value = milStatus
                    ObdDataHolder.lastMil = milStatus
                }
                if (allCodes.isEmpty()) {
                    ObdDataHolder.dtpResults = emptyList()
                    DtcStore.save(appContext, emptyList())
                    withContext(Dispatchers.Main) {
                        codes.value = emptyList()
                        cachedAt.value = null
                        scanState.value = ScanState.DONE
                    }
                    return@launch
                }
                // Without a key every network call would fail: assess offline
                // up front. With a key, a failed call degrades to offline info.
                val useOffline = !aiService.hasApiKey()
                val dict = DictionaryCache.dictionary ?: runCatching {
                    appContext.resources.openRawResource(DiagnosticsR.raw.dtc_generic)
                        .bufferedReader().use { it.readText() }
                }.mapCatching { DtcDictionary.fromJson(it) }.getOrDefault(emptyMap()).also {
                    DictionaryCache.dictionary = it
                }
                val results = allCodes.map { code ->
                    async(Dispatchers.IO) {
                        val entry = DtcDictionary.lookup(code, dict)
                        val assessed = if (useOffline) {
                            AiService.buildOfflineAssessment(code, entry)
                        } else {
                            runCatching { aiService.getDtpCodeAssessment(code) }
                                .getOrElse { AiService.buildOfflineAssessment(code, entry) }
                        }
                        assessed.copy(
                            sources = sourceMap[code.trim().uppercase()] ?: emptySet()
                        )
                    }
                }.awaitAll()
                ObdDataHolder.dtpResults = results
                DtcStore.save(appContext, results, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    codes.value = results
                    // Fresh read: the list is no longer a saved one.
                    cachedAt.value = null
                    scanState.value = ScanState.DONE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ErrorOverviewViewModel", "Error loading/assessing codes", e)
                withContext(Dispatchers.Main) {
                    failureMessage.value = e.message ?: e.javaClass.simpleName
                    scanState.value = ScanState.FAILED
                }
            }
        }
    }

    /** Clears stored codes; UI-side failures surface through [onError]. */
    fun clearCodes(onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { obdHelper.clearTroubleCodes() }
                FreezeFrameCache.invalidate()
                ObdDataHolder.dtpResults = emptyList()
                DtcStore.save(appContext, emptyList())
                withContext(Dispatchers.Main) {
                    codes.value = emptyList()
                    cachedAt.value = null
                    mil.value = null
                    ObdDataHolder.lastMil = null
                    scanState.value = ScanState.DONE
                    cleared.value = true
                    failureMessage.value = ""
                }
                Toast.makeText(
                    appContext,
                    R.string.error_overview_clear_done,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ErrorOverviewViewModel", "Error clearing codes", e)
                withContext(Dispatchers.Main) {
                    onError(appContext.getString(R.string.no_errors_found, e.message.orEmpty()))
                }
            }
        }
    }
}
