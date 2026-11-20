package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.ui.components.ErrorDialog
import com.catsmoker.obd2ai.ui.components.ScreenScaffold

/**
 * Destinations for the diagnostics flow. The screens themselves
 * ([ErrorOverviewScreen], [ErrorDetailScreen], [AskAiScreen]) are unchanged
 * composables; these wrappers own navigation, the DTO lookup and the
 * Compose error dialog that replaces the old ErrorDialogFragment.
 */
@Composable
fun ErrorOverviewDestination(activity: MainActivity, navController: NavHostController) {
    var errorText by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(
        title = stringResource(R.string.error_overview_title),
        onBack = null,
        modifier = Modifier.fillMaxSize()
    ) {
        ErrorOverviewScreen(
            obdHelper = activity.obdHelper,
            aiService = activity.aiService,
            context = LocalContext.current,
            onCodeClick = { code ->
                navController.navigate("errorDetail/$code") {
                    launchSingleTop = true
                }
            },
            showError = { text -> errorText = text }
        )
    }
    ErrorDialog(message = errorText, onDismiss = { errorText = null })
}

@Composable
fun ErrorDetailDestination(
    activity: MainActivity,
    navController: NavHostController,
    errorCode: String
) {
    val context = LocalContext.current
    // Results live in memory, but fall back to the last cached scan so the
    // detail screen still works after process death.
    val dto: DtpCodeDTO? = remember(errorCode) {
        ObdDataHolder.dtpResults.find { it.errorCode == errorCode }
            ?: DtcStore.load(context).find { it.errorCode == errorCode }
    }
    if (dto == null) {
        Text(
            text = stringResource(R.string.error_detail_gone, errorCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    ErrorDetailScreen(
        dto = dto,
        context = context,
        obdHelper = activity.obdHelper,
        onAskAi = {
            navController.navigate("askAi/${dto.errorCode}") {
                launchSingleTop = true
            }
        },
        onShare = { shareReport(context, dto) },
        onBack = { navController.navigateUp() }
    )
}

@Composable
fun AskAiDestination(activity: MainActivity, navController: NavHostController, errorCode: String) {
    val context = LocalContext.current
    // Same lookup as the detail screen: memory first, cached scan second.
    val dto: DtpCodeDTO? = remember(errorCode) {
        ObdDataHolder.dtpResults.find { it.errorCode == errorCode }
            ?: DtcStore.load(context).find { it.errorCode == errorCode }
    }
    if (dto == null) {
        Text(
            text = stringResource(R.string.error_detail_gone, errorCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    var errorText by remember { mutableStateOf<String?>(null) }
    AskAiScreen(
        dto = dto,
        errorCode = errorCode,
        obdHelper = activity.obdHelper,
        aiService = activity.aiService,
        context = context,
        onLogEvent = { name -> activity.logAnalyticsEvent(name) },
        onShowError = { text -> errorText = text },
        onNavBack = { navController.navigateUp() }
    )
    ErrorDialog(message = errorText, onDismiss = { errorText = null })
}

/** Localized pill/source name for one read mode. */
private fun sourceLabel(context: Context, source: DtcSource): String = context.getString(
    when (source) {
        DtcSource.STORED -> R.string.dtc_source_stored
        DtcSource.PENDING -> R.string.dtc_source_pending
        DtcSource.PERMANENT -> R.string.dtc_source_permanent
    }
)

/** Shares a fault's assessment as plain text (mechanic, notes app…). */
private fun shareReport(context: Context, dto: DtpCodeDTO) {
    val body = buildString {
        appendLine("${dto.errorCode} — ${dto.title}")
        appendLine(context.getString(R.string.error_details_code_label, dto.severity))
        if (dto.sources.isNotEmpty()) {
            val names = dto.sources.sortedBy { it.ordinal }
                .joinToString(", ") { sourceLabel(context, it) }
            appendLine(context.getString(R.string.error_detail_sources, names))
        }
        appendLine()
        appendLine(dto.detail)
        appendLine()
        appendLine(dto.implications)
        if (dto.suggestedActions.isNotEmpty()) {
            appendLine()
            dto.suggestedActions.forEach { appendLine("- $it") }
        }
        appendLine()
        append(context.getString(R.string.app_name))
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${dto.errorCode} ${dto.title}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(share, context.getString(R.string.error_detail_share))
    )
}
