package com.catsmoker.obd2ai.ui.console

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.CircleIconButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing
import com.catsmoker.obd2ai.ui.theme.PanelInk
import com.catsmoker.obd2ai.ui.theme.PanelPaper
import com.catsmoker.obd2ai.ui.theme.Phosphor
import kotlinx.coroutines.launch

/**
 * Raw OBD console for power users: type any AT command or PID ("010C"),
 * see the adapter's raw reply. Shares the connection, so avoid it while
 * Live Data is polling. Transcript is capped by [ConsoleLog] (200 lines).
 *
 * Mirrors the XML fragment: append command + adapter reply to a capped log,
 * render as scrollable text auto-scrolled to the bottom, Send on tap,
 * Clear wipes the log. Shows "Not connected to an adapter." when there is
 * no active connection.
 */
class ConsoleLog(
    private val capacity: Int = 200,
    private val maxChars: Int = 32_000
) {
    private val lines = ArrayDeque<String>()
    private var chars = 0

    /**
     * Adds one entry (a command or a whole adapter reply) and trims the oldest
     * entries once the log is too long in lines *or* in characters — a chatty
     * adapter can return a multi-kilobyte reply, and the transcript is
     * re-laid-out as one Text on every append. The newest entry always stays,
     * even when it alone is larger than [maxChars].
     */
    fun append(line: String) {
        lines.addLast(line)
        chars += line.length
        while (lines.size > 1 && (lines.size > capacity || chars > maxChars)) {
            chars -= lines.removeFirst().length
        }
    }

    fun clear() {
        lines.clear()
        chars = 0
    }

    fun snapshot(): List<String> = lines.toList()

    /** Whole transcript, for clipboard export. */
    fun text(): String = lines.joinToString("\n")
}

/** Compose content for the console screen. */
@Composable
fun ConsoleScreen(
    obdHelper: ObdHelper,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var copied by remember { mutableStateOf(false) }

    // Capped transcript (pure, tested logic) plus the rendered text observed by composition.
    val transcript = remember { ConsoleLog() }
    var log by remember { mutableStateOf("") }

    // "Not connected" notice when there's no active connection.
    LaunchedEffect(Unit) {
        if (!obdHelper.isConnected) {
            transcript.append(context.getString(R.string.obd_console_not_connected))
            log = transcript.snapshot().joinToString("\n")
        }
    }

    var input by remember { mutableStateOf("") }

    // New adapter output lands at the bottom of the panel; scrolling there is
    // what makes the console usable while typing.
    LaunchedEffect(log) {
        if (log.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun sendCommand() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        input = ""
        transcript.append("> $cmd")
        log = transcript.snapshot().joinToString("\n")
        scope.launch {
            try {
                val reply = obdHelper.sendRaw(cmd)
                transcript.append(reply.trimEnd())
                log = transcript.snapshot().joinToString("\n")
            } catch (e: Exception) {
                transcript.append("Error: ${e.message}")
                log = transcript.snapshot().joinToString("\n")
            }
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.obd_console_title),
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
        trailingContent = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            context.getString(R.string.obd_console_title),
                            transcript.text()
                        )
                    )
                    copied = true
                }
            ) {
                Text(text = stringResource(R.string.obd_console_copy))
            }
            TextButton(
                onClick = {
                    transcript.clear()
                    log = ""
                }
            ) {
                Text(text = stringResource(R.string.obd_console_clear))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (copied) {
                    stringResource(R.string.obd_console_copied)
                } else {
                    stringResource(R.string.console_local_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (copied) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            LaunchedEffect(copied) {
                if (copied) {
                    kotlinx.coroutines.delay(2_000)
                    copied = false
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Terminal panel: engraved, high-contrast, monospace. It is a
            // deliberate counterpoint to the rest of the glass UI — raw data
            // should look raw.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PanelInk, RoundedCornerShape(ObdRadius.Card))
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = log.ifEmpty { context.getString(R.string.obd_console_placeholder) },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (log.isEmpty()) PanelPaper.copy(alpha = 0.5f) else Phosphor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(ObdSpacing.CardGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.console_input_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(ObdRadius.Pill),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendCommand() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.obd_console_send),
                    onClick = { sendCommand() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(ObdSpacing.CardGap))
        }
    }
}
