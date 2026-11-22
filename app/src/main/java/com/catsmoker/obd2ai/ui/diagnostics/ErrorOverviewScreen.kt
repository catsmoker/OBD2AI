package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import com.catsmoker.obd2ai.ui.components.CollapsibleSectionCard
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.connect.ConnectFailureKind
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.glass.GlassCard
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing
import com.catsmoker.obd2ai.ui.theme.ObdTheme

/** Severity color, paired with text so color is never the only signal. */
@Composable
private fun severityColor(severity: ErrorSeverity): Color = when (severity) {
    ErrorSeverity.HIGH -> MaterialTheme.colorScheme.error
    ErrorSeverity.MEDIUM -> ObdTheme.colors.warning
    ErrorSeverity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** One fault-code row inside the results card. */
@Composable
private fun ErrorCodeRow(
    item: DtpCodeDTO,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(severityColor(item.severity), CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = item.errorCode,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Compose content for the error-overview screen. Owns the scan itself so the
 * result list is real Compose state (the previous version wrote to
 * [ObdDataHolder] but rendered a state that could never change, so the list
 * stayed empty no matter what the ECU reported).
 *
 * States are explicit: not-read, scanning, failed, done-healthy, done-with-
 * codes. A failed read never renders as "no codes".
 */
@Composable
fun ErrorOverviewScreen(
    obdHelper: ObdHelper,
    context: Context,
    onCodeClick: (DtpCodeDTO) -> Unit,
    showError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ErrorOverviewViewModel = hiltViewModel()
    val codes by viewModel.codes
    val mil by viewModel.mil
    val adapterLine by viewModel.adapterLine
    val showAdapter by viewModel.showAdapter
    val scanState by viewModel.scanState
    val failureMessage by viewModel.failureMessage
    var clearDialog by viewModel.clearDialog
    val cleared by viewModel.cleared
    val cachedAt by viewModel.cachedAt
    val demoMode = remember { obdHelper.demoMode }

    LaunchedEffect(Unit) { viewModel.loadAdapterInfo() }

    var openGroups by rememberSaveable { mutableStateOf(listOf(DtcSource.STORED.name)) }
    val toggleGroup: (String) -> Unit = { key ->
        openGroups = if (openGroups.contains(key)) openGroups - key else openGroups + key
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero: the lamp that matters is the check-engine light.
        GlassCard(
            shape = RoundedCornerShape(ObdRadius.Card),
            elevation = 4.dp
        ) {
            Column(Modifier.fillMaxWidth()) {
                StatusBadge(
                    label = when {
                        mil?.milOn == true -> stringResource(R.string.error_overview_mil_badge_on)
                        mil != null -> stringResource(R.string.error_overview_mil_badge_off)
                        else -> stringResource(R.string.error_overview_mil_badge_unknown)
                    },
                    active = mil?.milOn == true,
                    activeColor = ObdTheme.colors.warning
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        mil?.milOn == true ->
                            context.getString(R.string.error_overview_mil_on, mil!!.storedCount)
                        mil != null -> context.getString(R.string.error_overview_mil_off)
                        else -> context.getString(R.string.error_overview_mil_unknown)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showAdapter) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = adapterLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (demoMode) {
                    Spacer(Modifier.height(8.dp))
                    StatusBadge(
                        label = context.getString(R.string.error_overview_demo_badge),
                        active = true
                    )
                }
            }
        }

        Spacer(Modifier.height(ObdSpacing.CardGap))

        // One dominant action, one destructive secondary: side-by-side made the
        // longer label wrap and the two buttons disagree on height.
        Button(
            onClick = { viewModel.scan() },
            // Demo has no fault-code source: without a transport every scan
            // ends in "connection not established", so say it up front instead
            // of running a read that cannot work.
            enabled = !demoMode && scanState != ScanState.SCANNING,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
        ) {
            Text(
                text = context.getString(R.string.error_overview_scan_button),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(ObdSpacing.CardGap))
        ObdOutlinedButton(
            onClick = { clearDialog = true },
            enabled = !demoMode && scanState != ScanState.SCANNING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = context.getString(R.string.error_overview_clear_button),
                textAlign = TextAlign.Center
            )
        }

        if (!demoMode && cachedAt != null && scanState == ScanState.IDLE) {
            // Restored from disk: say where it came from, never present it as
            // the current state of the car.
            Spacer(Modifier.height(ObdSpacing.CardGap))
            Text(
                text = savedScanLine(cachedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (demoMode) {
            Spacer(Modifier.height(ObdSpacing.CardGap))
            StatePanel(
                icon = Icons.Outlined.ErrorOutline,
                text = context.getString(R.string.diag_demo_no_codes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(ObdSpacing.SectionGap))

        // A list restored from disk is shown as a result, never as "not
        // scanned yet" — its freshness is stated by the line above.
        val displayState =
            if (!demoMode && scanState == ScanState.IDLE && cachedAt != null && codes.isNotEmpty()) {
                ScanState.DONE
            } else {
                scanState
            }

        when (displayState) {
            // In demo the note above already explains why there is nothing to
            // scan; "tap Scan for Fault Codes" would point at a dead button.
            // A restored saved scan renders through DONE below.
            ScanState.IDLE -> if (!demoMode && codes.isEmpty()) StatePanel(
                icon = Icons.Outlined.ErrorOutline,
                text = context.getString(R.string.diag_scan_state_idle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ScanState.SCANNING -> SectionCard {
                Text(
                    text = context.getString(R.string.diag_scan_state_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            ScanState.FAILED -> {
                // Same classification as Connect and Live, so one failure is
                // described the same way wherever it surfaces.
                val headline = when (ConnectFailureKind.classify(failureMessage)) {
                    ConnectFailureKind.UNREACHABLE ->
                        context.getString(R.string.connect_failed_unreachable)
                    ConnectFailureKind.NOT_PAIRED ->
                        context.getString(R.string.connect_failed_not_paired)
                    ConnectFailureKind.BLUETOOTH_UNAVAILABLE ->
                        context.getString(R.string.connect_failed_bluetooth)
                    ConnectFailureKind.GENERIC -> null
                }
                StatePanel(
                    icon = Icons.Outlined.ErrorOutline,
                    text = if (headline != null && failureMessage.isNotBlank()) {
                        headline + "\n" + failureMessage
                    } else {
                        context.getString(R.string.diag_scan_state_failed, failureMessage)
                    },
                    tint = MaterialTheme.colorScheme.error
                )
            }

            ScanState.DONE -> {
                if (codes.isEmpty()) {
                    StatePanel(
                        icon = Icons.Outlined.CheckCircle,
                        text = if (cleared) {
                            context.getString(R.string.error_overview_clear_done)
                        } else {
                            context.getString(R.string.error_overview_no_errors)
                        },
                        tint = ObdTheme.colors.success
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = ObdSpacing.LabelGap)
                    )
                    // A code can be stored and pending at the same time, so it
                    // is listed under every source it reports; the chips on the
                    // row and the unique totals above keep that honest.
                    val groups = listOf(
                        DtcSource.STORED to context.getString(R.string.dtc_source_stored),
                        DtcSource.PENDING to context.getString(R.string.dtc_source_pending),
                        DtcSource.PERMANENT to context.getString(R.string.dtc_source_permanent)
                    )
                    groups.forEachIndexed { index, (source, label) ->
                        val members = if (source == DtcSource.STORED) {
                            sorted.filter { source in it.sources || it.sources.isEmpty() }
                        } else {
                            sorted.filter { source in it.sources }
                        }
                        if (members.isEmpty()) return@forEachIndexed
                        if (index > 0) Spacer(Modifier.height(ObdSpacing.CardGap))
                        CollapsibleSectionCard(
                            title = context.getString(
                                R.string.error_overview_source_group,
                                label,
                                members.size
                            ),
                            expanded = openGroups.contains(source.name),
                            onToggle = { toggleGroup(source.name) },
                            expandLabel = context.getString(R.string.settings_section_expand),
                            collapseLabel = context.getString(R.string.settings_section_collapse)
                        ) {
                            members.forEachIndexed { i, item ->
                                ErrorCodeRow(item = item, onClick = { onCodeClick(item) })
                                if (i < members.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ObdSpacing.SectionGap))
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            shape = RoundedCornerShape(ObdRadius.Card),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(text = stringResource(R.string.error_overview_clear_confirm_title)) },
            text = { Text(text = stringResource(R.string.error_overview_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    clearDialog = false
                    viewModel.clearCodes(onError = showError)
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

/** Centered icon + message for empty/error states. */
@Composable
private fun StatePanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The saved-scan caption: a date only when the file really carries one, so a
 * legacy cache never invents when it was written.
 */
@Composable
private fun savedScanLine(cachedAt: Long?): String {
    if (!CachedScanLabel.hasDate(cachedAt)) {
        return stringResource(R.string.diag_saved_scan_undated)
    }
    val stamp = remember(cachedAt) {
        java.text.DateFormat
            .getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            .format(java.util.Date(cachedAt ?: 0L))
    }
    return stringResource(R.string.diag_saved_scan, stamp)
}
