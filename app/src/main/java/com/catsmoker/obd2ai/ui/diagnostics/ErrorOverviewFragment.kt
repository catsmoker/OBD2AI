package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
import com.catsmoker.obd2ai.ui.common.ErrorDialogFragment
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Cached bundled dictionary, loaded once per process. */
private object DictionaryCache {
    var dictionary: Map<String, DtcInfo>? = null
}

/** One fault-code row: icon, code badge, title, detail, source pills. */
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            if (item.offline) {
                Text(
                    text = context.getString(R.string.error_offline_suffix, ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (item.offline) context.getString(R.string.error_offline_suffix, item.detail) else item.detail,
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

/** Compose content for the error-overview screen. */
@Composable
fun ErrorOverviewScreen(
    obdHelper: ObdHelper,
    aiService: AiService,
    context: Context,
    onCodeClick: (String) -> Unit,
    onAnalyze: () -> Unit,
    onClearConfirmed: () -> Unit,
    showError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val codes by remember { mutableStateOf(emptyList<DtpCodeDTO>()) }
    var mil by remember { mutableStateOf<MilStatus?>(null) }
    var adapterLine by remember { mutableStateOf("") }
    var showAdapter by remember { mutableStateOf(false) }
    val demoMode by remember { mutableStateOf(obdHelper.demoMode) }

    LaunchedEffect(Unit) {
        if (!obdHelper.isConnected) return@LaunchedEffect
        val health = async { runCatching { obdHelper.getAdapterHealth() } }
        val vehicle = async { runCatching { obdHelper.getVehicleInfo() } }
        val healthResult = health.await().getOrNull()
        val vehicleResult = vehicle.await().getOrNull()
        if (healthResult != null || vehicleResult != null) {
            adapterLine = listOfNotNull(
                healthResult?.let { context.getString(R.string.error_overview_adapter_health, it.first, it.second, it.third) },
                vehicleResult?.let { context.getString(R.string.error_overview_vehicle_info, it.first, it.second, it.third) }
            ).joinToString("\n")
            showAdapter = true
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = context.getString(R.string.error_overview_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(20.dp))

        ObdButton(
            onClick = onAnalyze,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.error_overview_scan_button))
        }
        Spacer(modifier = Modifier.height(12.dp))
        ObdOutlinedButton(
            onClick = onClearConfirmed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.error_overview_clear_button))
        }

        if (demoMode) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = context.getString(R.string.error_overview_demo_badge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = when {
                mil != null && mil!!.milOn -> context.getString(R.string.error_overview_mil_on, mil!!.storedCount)
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

        if (codes.isEmpty()) {
            Text(
                text = context.getString(R.string.error_overview_no_errors),
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
            if (allOffline) stats += " · " + context.getString(R.string.error_overview_offline_note)
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(items = sorted, key = { it.errorCode }) { item ->
                    ErrorCodeRow(item = item, onClick = { onCodeClick(item.errorCode) })
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Confirm-clear dialog. */
@Composable
private fun ClearConfirmDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.error_overview_clear_confirm_title)) },
        text = { Text(text = stringResource(R.string.error_overview_clear_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.dialog_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}

/** Fragment host: owns navigation and the analyze/clear coroutines. */
class ErrorOverviewFragment : Fragment() {
    private lateinit var obdHelper: ObdHelper
    private lateinit var aiService: AiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        obdHelper = (activity as MainActivity).obdHelper
        aiService = (activity as MainActivity).aiService

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    var clearDialog by remember { mutableStateOf(false) }

                    ErrorOverviewScreen(
                        obdHelper = obdHelper,
                        aiService = aiService,
                        context = requireContext(),
                        onCodeClick = { code ->
                            findNavController().navigate(
                                com.catsmoker.obd2ai.ui.diagnostics.ErrorOverviewFragmentDirections
                                    .actionErrorOverviewFragmentToErrorDetailFragment(code)
                            )
                        },
                        onAnalyze = ::loadAndAssessErrorCodes,
                        onClearConfirmed = { clearDialog = true },
                        showError = { text ->
                            ErrorDialogFragment.newInstance(text).show(parentFragmentManager, "errorDialog")
                        }
                    )
                    ClearConfirmDialog(
                        visible = clearDialog,
                        onConfirm = {
                            clearDialog = false
                            doClearCodes()
                        },
                        onDismiss = { clearDialog = false }
                    )
                    // Wire the clear button to open the dialog instead of clearing directly.
                    // (The onClearConfirmed above is invoked only after confirmation.)
                }
            }
        }
    }

    private fun loadAndAssessErrorCodes() {
        val ctx = requireContext()
        val useOffline = !aiService.hasApiKey()
        val dict = DictionaryCache.dictionary ?: runCatching {
            ctx.resources.openRawResource(R.raw.dtc_generic).bufferedReader().use { it.readText() }
        }.mapCatching { DtcDictionary.fromJson(it) }.getOrDefault(emptyMap()).also {
            DictionaryCache.dictionary = it
        }

        lifecycleScope.launch {
            try {
                val stored = async { runCatching { obdHelper.getDtpCodes() } }
                val pending = async { runCatching { obdHelper.getPendingDtpCodes() } }
                val permanent = async { runCatching { obdHelper.getPermanentDtpCodes() } }
                val mil = async { runCatching { obdHelper.getMilStatus() } }
                val reads = listOf(stored.await(), pending.await(), permanent.await())
                if (reads.all { it.isFailure }) {
                    throw reads.firstNotNullOf { it.exceptionOrNull() }
                }
                val allCodes = reads.mapNotNull { it.getOrNull() }.flatten().distinct()
                val sourceMap = ObdHelper.mergeCodeSources(
                    stored.await().getOrDefault(emptyList()),
                    pending.await().getOrDefault(emptyList()),
                    permanent.await().getOrDefault(emptyList())
                )
                val milStatus = mil.await().getOrNull()
                if (allCodes.isEmpty()) {
                    ObdDataHolder.dtpResults = emptyList()
                    DtcStore.save(ctx, emptyList())
                    return@launch
                }
                val results = allCodes.map { code ->
                    async {
                        val entry = DtcDictionary.lookup(code, dict)
                        val assessed = if (useOffline) {
                            AiService.buildOfflineAssessment(code, entry)
                        } else {
                            runCatching { aiService.getDtpCodeAssessment(code) }
                                .getOrElse { AiService.buildOfflineAssessment(code, entry) }
                        }
                        assessed.copy(sources = sourceMap[code.trim().uppercase()] ?: emptySet())
                    }
                }.awaitAll()
                ObdDataHolder.dtpResults = results
                DtcStore.save(ctx, results)
            } catch (e: Exception) {
                Log.e("ErrorOverviewFragment", "Error loading/assessing codes", e)
                requireActivity().runOnUiThread {
                    ErrorDialogFragment.newInstance(
                        ctx.getString(R.string.no_errors_found, e.message)
                    ).show(parentFragmentManager, "errorDialog")
                }
            }
        }
    }

    private fun doClearCodes() {
        val ctx = requireContext()
        lifecycleScope.launch {
            try {
                obdHelper.clearTroubleCodes()
                ObdDataHolder.dtpResults = emptyList()
                DtcStore.save(ctx, emptyList())
                ctx.getString(R.string.error_overview_clear_done).let { msg ->
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(requireActivity(), msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ErrorOverviewFragment", "Error clearing codes", e)
                requireActivity().runOnUiThread {
                    ErrorDialogFragment.newInstance(
                        ctx.getString(R.string.no_errors_found, e.message)
                    ).show(parentFragmentManager, "errorDialog")
                }
            }
        }
    }

    companion object {
        // Exposed so the Compose screen can open the clear-confirm dialog.
        var clearDialogRequested: Boolean = false
            private set
    }
}
