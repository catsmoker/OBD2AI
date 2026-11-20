package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.diagnostics.DtcDictionary
import com.catsmoker.obd2ai.diagnostics.DtcInfo
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.diagnostics.MilStatus
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cached bundled dictionary, loaded once per process. */
private object DictionaryCache {
    var dictionary: Map<String, DtcInfo>? = null
}

/**
 * What the screen knows about its fault-code data. Never claim an empty
 * result before a successful read: [IDLE] means "not read yet" and
 * [FAILED] means "the read failed" — only [DONE] may say "no codes".
 */
private enum class ScanState { IDLE, SCANNING, DONE, FAILED }

/** One fault-code row: severity dot, code, title, detail, source pills. */
@Composable
private fun ErrorCodeRow(
    item: DtpCodeDTO,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    when (item.severity) {
                        ErrorSeverity.HIGH -> MaterialTheme.colorScheme.error
                        ErrorSeverity.MEDIUM -> MaterialTheme.colorScheme.primary
                        ErrorSeverity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.errorCode,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            if (item.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (DtcSource.STORED in item.sources) {
                        StatusBadge(label = context.getString(R.string.dtc_source_stored), active = true)
                    }
                    if (DtcSource.PENDING in item.sources) {
                        StatusBadge(label = context.getString(R.string.dtc_source_pending), active = false)
                    }
                    if (DtcSource.PERMANENT in item.sources) {
                        StatusBadge(label = context.getString(R.string.dtc_source_permanent), active = false)
                    }
                }
            }
        }
    }
}

/**
 * Compose content for the error-overview screen. Owns the scan itself so the
 * result list is real Compose state (the previous version wrote to
 * [ObdDataHolder] but rendered a state that could never change, so the list
 * stayed empty no matter what the ECU reported).
 */
@Composable
fun ErrorOverviewScreen(
    obdHelper: ObdHelper,
    aiService: AiService,
    context: Context,
    onCodeClick: (String) -> Unit,
    showError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var codes by remember { mutableStateOf<List<DtpCodeDTO>>(emptyList()) }
    var mil by remember { mutableStateOf<MilStatus?>(null) }
    var adapterLine by remember { mutableStateOf("") }
    var showAdapter by remember { mutableStateOf(false) }
    var scanState by remember { mutableStateOf(ScanState.IDLE) }
    var failureMessage by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val demoMode = remember { obdHelper.demoMode }
    val scope = rememberCoroutineScope()

    // Adapter/vehicle identity is informational: never blocks the scan.
    LaunchedEffect(Unit) {
        if (!obdHelper.isConnected) return@LaunchedEffect
        val health = async(Dispatchers.IO) { runCatching { obdHelper.getAdapterHealth() } }
        val vehicle = async(Dispatchers.IO) { runCatching { obdHelper.getVehicleInfo() } }
        val healthResult = health.await().getOrNull()
        val vehicleResult = vehicle.await().getOrNull()
        if (healthResult != null || vehicleResult != null) {
            adapterLine = listOfNotNull(
                healthResult?.let {
                    context.getString(R.string.error_overview_adapter_health, it.first, it.second, it.third)
                },
                vehicleResult?.let {
                    context.getString(R.string.error_overview_vehicle_info, it.first, it.second, it.third)
                }
            ).joinToString("\n")
            showAdapter = true
        }
    }

    fun scan() {
        if (scanState == ScanState.SCANNING) return
        scanState = ScanState.SCANNING
        failureMessage = ""
        cleared = false
        scope.launch {
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
                    mil = milStatus
                    ObdDataHolder.lastMil = milStatus
                }
                if (allCodes.isEmpty()) {
                    ObdDataHolder.dtpResults = emptyList()
                    DtcStore.save(context, emptyList())
                    withContext(Dispatchers.Main) {
                        codes = emptyList()
                        scanState = ScanState.DONE
                    }
                    return@launch
                }
                // Without a key every network call would fail: assess offline
                // up front. With a key, a failed call degrades to offline info.
                val useOffline = !aiService.hasApiKey()
                val dict = DictionaryCache.dictionary ?: runCatching {
                    context.resources.openRawResource(R.raw.dtc_generic)
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
                DtcStore.save(context, results)
                withContext(Dispatchers.Main) {
                    codes = results
                    scanState = ScanState.DONE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ErrorOverviewFragment", "Error loading/assessing codes", e)
                withContext(Dispatchers.Main) {
                    failureMessage = e.message ?: e.javaClass.simpleName
                    scanState = ScanState.FAILED
                }
            }
        }
    }

    fun clearCodes() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { obdHelper.clearTroubleCodes() }
                ObdDataHolder.dtpResults = emptyList()
                DtcStore.save(context, emptyList())
                withContext(Dispatchers.Main) {
                    codes = emptyList()
                    mil = null
                    ObdDataHolder.lastMil = null
                    scanState = ScanState.DONE
                    cleared = true
                    failureMessage = ""
                }
                Toast.makeText(
                    context,
                    R.string.error_overview_clear_done,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ErrorOverviewFragment", "Error clearing codes", e)
                withContext(Dispatchers.Main) {
                    showError(
                        context.getString(R.string.no_errors_found, e.message.orEmpty())
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        ObdButton(
            onClick = { scan() },
            enabled = scanState != ScanState.SCANNING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.error_overview_scan_button))
        }
        Spacer(modifier = Modifier.height(12.dp))
        ObdOutlinedButton(
            onClick = { clearDialog = true },
            enabled = scanState != ScanState.SCANNING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.error_overview_clear_button))
        }

        if (demoMode) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = context.getString(R.string.error_overview_demo_badge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when {
                mil?.milOn == true ->
                    context.getString(R.string.error_overview_mil_on, mil!!.storedCount)
                mil != null -> context.getString(R.string.error_overview_mil_off)
                else -> context.getString(R.string.error_overview_mil_unknown)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (showAdapter) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = adapterLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (scanState) {
            ScanState.IDLE -> Text(
                text = context.getString(R.string.diag_scan_state_idle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ScanState.SCANNING -> {
                Text(
                    text = context.getString(R.string.diag_scan_state_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ScanState.FAILED -> Text(
                text = context.getString(R.string.diag_scan_state_failed, failureMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            ScanState.DONE -> {
                if (codes.isEmpty()) {
                    Text(
                        text = if (cleared) {
                            context.getString(R.string.error_overview_clear_done)
                        } else {
                            context.getString(R.string.error_overview_no_errors)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val allOffline = codes.all { it.offline }
                    val sorted = codes.sortedByDescending { it.severity.ordinal }
                    val low = sorted.count { it.severity == ErrorSeverity.LOW }
                    val med = sorted.count { it.severity == ErrorSeverity.MEDIUM }
                    val high = sorted.count { it.severity == ErrorSeverity.HIGH }
                    var stats = context.getString(R.string.error_overview_error_counts, low, med, high)
                    if (allOffline) {
                        stats += " · " + context.getString(R.string.error_overview_offline_note)
                    }
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(items = sorted, key = { it.errorCode }) { item ->
                            ErrorCodeRow(item = item, onClick = { onCodeClick(item.errorCode) })
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(text = stringResource(R.string.error_overview_clear_confirm_title)) },
            text = { Text(text = stringResource(R.string.error_overview_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    clearDialog = false
                    clearCodes()
                }) {
                    Text(text = stringResource(R.string.dialog_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

/** Fragment host: owns navigation and the shared services. */
