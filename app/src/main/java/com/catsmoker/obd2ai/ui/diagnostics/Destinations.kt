package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.catsmoker.obd2ai.ui.components.ErrorDialog
import com.catsmoker.obd2ai.ui.components.ScreenScaffold

/**
 * The fault row the user tapped, handed to the detail screen so it never has
 * to re-derive it (and never has to touch disk before its first frame).
 * Purely in-memory: after process death the destinations fall back to the
 * cached scan.
 */
internal object DiagnosticsSelection {
    @Volatile
    var selected: DtpCodeDTO? = null
}

/**
 * The row to show for [errorCode]: the handed-over one when it is really this
 * code, otherwise the cached scan. Pure so the handoff rule stays tested.
 */
internal fun resolveSelectedCode(
    errorCode: String,
    handedOver: DtpCodeDTO?,
    cached: List<DtpCodeDTO>
): DtpCodeDTO? =
    handedOver?.takeIf { it.errorCode == errorCode }
        ?: cached.find { it.errorCode == errorCode }

/** Memory first, then the cached scan read off the main thread. */
@Composable
private fun rememberResolvedCode(errorCode: String): Pair<DtpCodeDTO?, Boolean> {
    val context = LocalContext.current
    val handedOver = remember(errorCode) { DiagnosticsSelection.selected }
    var cached by remember(errorCode) { mutableStateOf<List<DtpCodeDTO>>(emptyList()) }
    var attempted by remember(errorCode) { mutableStateOf(false) }
    LaunchedEffect(errorCode) {
        if (handedOver?.errorCode != errorCode) {
            cached = withContext(Dispatchers.IO) { DtcStore.load(context) }
            attempted = true
        }
    }
    val resolved = resolveSelectedCode(errorCode, handedOver, cached)
    return resolved to (resolved != null || attempted)
}

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
        onBack = { navController.navigateUp() },
        modifier = Modifier.fillMaxSize()
    ) {
        ErrorOverviewScreen(
            obdHelper = activity.obdHelper,
            context = LocalContext.current,
            onCodeClick = { dto ->
                DiagnosticsSelection.selected = dto
                navController.navigate("errorDetail/${dto.errorCode}") {
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
    val (dto, resolved) = rememberResolvedCode(errorCode)
    if (dto == null) {
        if (!resolved) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
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
    val (dto, resolved) = rememberResolvedCode(errorCode)
    if (dto == null) {
        if (!resolved) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
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
