package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.components.SectionLabel
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.glass.GlassCard
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing
import com.catsmoker.obd2ai.ui.theme.ObdTheme
import com.catsmoker.obd2ai.ui.theme.PanelInk
import com.catsmoker.obd2ai.ui.theme.Phosphor

/** Localized pill/source name for one read mode. */
private fun sourceLabel(context: Context, source: DtcSource): String = context.getString(
    when (source) {
        DtcSource.STORED -> R.string.dtc_source_stored
        DtcSource.PENDING -> R.string.dtc_source_pending
        DtcSource.PERMANENT -> R.string.dtc_source_permanent
    }
)

@Composable
private fun severityColor(severity: ErrorSeverity): Color = when (severity) {
    ErrorSeverity.HIGH -> MaterialTheme.colorScheme.error
    ErrorSeverity.MEDIUM -> ObdTheme.colors.warning
    ErrorSeverity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** One suggested-action row with a numbered accent bullet. */
@Composable
private fun ActionRow(index: Int, action: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = action,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Compose content for the error-detail screen. */
@Composable
fun ErrorDetailScreen(
    dto: DtpCodeDTO,
    context: Context,
    obdHelper: ObdHelper,
    onAskAi: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // One Mode 02 read per scan: reopening a code shows the frame instantly
    // instead of flickering from "reading" to values.
    var freezeLoaded by remember { mutableStateOf(FreezeFrameCache.isLoaded()) }
    var freeze by remember {
        mutableStateOf(if (FreezeFrameCache.isLoaded()) FreezeFrameCache.get() else null)
    }
    LaunchedEffect(Unit) {
        if (!freezeLoaded) {
            freeze = runCatching { obdHelper.getFreezeFrame() }.getOrNull()
            FreezeFrameCache.put(freeze)
            freezeLoaded = true
        }
    }
    val freezeText = when {
        !freezeLoaded -> context.getString(R.string.freeze_reading)
        freeze == null -> context.getString(R.string.freeze_unavailable)
        else -> listOfNotNull(
            freeze?.dtc?.let { context.getString(R.string.freeze_dtc, it) },
            freeze?.rpm?.let { context.getString(R.string.freeze_rpm, it) },
            freeze?.speedKmh?.let { context.getString(R.string.freeze_speed, it) },
            freeze?.coolantC?.let { context.getString(R.string.freeze_coolant, it) },
            freeze?.loadPct?.let { context.getString(R.string.freeze_load, it) }
        ).joinToString("\n")
    }

    ScreenScaffold(
        title = dto.title,
        subtitle = context.getString(R.string.error_details_code_label, dto.severity),
        onBack = onBack,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Hero: the code itself, as an instrument readout.
            GlassCard(
                shape = RoundedCornerShape(ObdRadius.Card),
                elevation = 4.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(severityColor(dto.severity), CircleShape)
                        )
                        Text(
                            text = dto.errorCode,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        StatusBadge(
                            label = context.getString(R.string.error_details_code_label, dto.severity),
                            active = dto.severity == ErrorSeverity.HIGH,
                            activeColor = severityColor(dto.severity)
                        )
                    }
                    if (dto.sources.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = context.getString(
                                R.string.error_detail_sources,
                                dto.sources.sortedBy { it.ordinal }
                                    .joinToString(", ") { sourceLabel(context, it) }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            SectionLabel(text = stringResource(R.string.error_details_description_label), accent = true)
            SectionCard {
                Text(
                    text = dto.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            SectionLabel(text = stringResource(R.string.error_details_solutions_label), accent = true)
            SectionCard {
                Text(
                    text = dto.implications,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            SectionLabel(text = stringResource(R.string.error_details_ai_suggestions_label), accent = true)
            SectionCard {
                if (dto.suggestedActions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.freeze_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dto.suggestedActions.forEachIndexed { index, action ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        ActionRow(index = index, action = action)
                    }
                }
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            SectionLabel(text = stringResource(R.string.freeze_title), accent = true)
            // Freeze frame is raw ECU data: monospace on the engraved panel.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelInk, RoundedCornerShape(ObdRadius.Card))
                    .padding(18.dp)
            ) {
                Text(
                    text = freezeText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Phosphor
                )
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))

            ObdButton(
                onClick = onAskAi,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.ask_ai_button))
            }
            Spacer(Modifier.height(ObdSpacing.CardGap))
            ObdOutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.error_detail_share))
            }

            Spacer(Modifier.height(ObdSpacing.SectionGap))
        }
    }
}
