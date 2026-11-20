package com.catsmoker.obd2ai.ui.console

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
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
class ConsoleLog(private val capacity: Int = 200) {
    private val lines = ArrayDeque<String>()

    fun append(line: String) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    fun clear() {
        lines.clear()
    }

    fun snapshot(): List<String> = lines.toList()
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
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.console_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ObdButton(
                    onClick = { sendCommand() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.obd_console_send))
                }
                ObdOutlinedButton(
                    onClick = {
                        transcript.clear()
                        log = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.obd_console_clear))
                }
            }
        }
    }
}
