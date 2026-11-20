package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.StatusBadge
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.launch

/** Localized pill/source name for one read mode. */
private fun sourceLabel(context: Context, source: DtcSource): String = context.getString(
    when (source) {
        DtcSource.STORED -> R.string.dtc_source_stored
        DtcSource.PENDING -> R.string.dtc_source_pending
        DtcSource.PERMANENT -> R.string.dtc_source_permanent
    }
)

/** One suggested-action row with a bullet. */
@Composable
private fun ActionRow(action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
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
    var freezeText by remember { mutableStateOf("") }

    // Load the mode-02 freeze frame once after composition.
    LaunchedEffect(Unit) {
        val freeze = runCatching { obdHelper.getFreezeFrame() }.getOrNull()
        val lines = listOfNotNull(
            freeze?.dtc?.let { context.getString(R.string.freeze_dtc, it) },
            freeze?.rpm?.let { context.getString(R.string.freeze_rpm, it) },
            freeze?.speedKmh?.let { context.getString(R.string.freeze_speed, it) },
            freeze?.coolantC?.let { context.getString(R.string.freeze_coolant, it) },
            freeze?.loadPct?.let { context.getString(R.string.freeze_load, it) }
        )
        freezeText = lines.ifEmpty {
            listOf(context.getString(R.string.freeze_unavailable))
        }.joinToString("\n")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        when (dto.severity) {
                            ErrorSeverity.HIGH -> MaterialTheme.colorScheme.error
                            ErrorSeverity.MEDIUM -> MaterialTheme.colorScheme.primary
                            ErrorSeverity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = dto.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dto.errorCode,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = context.getString(R.string.error_details_code_label, dto.severity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        if (dto.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = context.getString(
                    R.string.error_detail_sources,
                    dto.sources.sortedBy { it.ordinal }.joinToString(", ") { sourceLabel(context, it) }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.error_details_description_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dto.detail,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit(1.4f, TextUnitType.Em)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.error_details_solutions_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dto.implications,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = TextUnit(1.4f, TextUnitType.Em)),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.error_details_ai_suggestions_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            dto.suggestedActions.forEach { action ->
                ActionRow(action)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = context.getString(R.string.freeze_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = freezeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        ObdButton(
            onClick = onAskAi,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.ask_ai_button))
        }
        Spacer(modifier = Modifier.height(12.dp))
        ObdOutlinedButton(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = context.getString(R.string.error_detail_share))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Fragment host: owns navigation, freeze-frame load, share, and the dto lookup. */
