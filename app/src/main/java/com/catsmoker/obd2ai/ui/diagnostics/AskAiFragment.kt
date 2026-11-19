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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.catsmoker.obd2ai.MainActivity
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.diagnostics.DtcStore
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.common.ErrorDialogFragment
import com.catsmoker.obd2ai.ui.components.ObdButton
import com.catsmoker.obd2ai.ui.components.ObdOutlinedButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.theme.Obd2AiTheme
import kotlinx.coroutines.launch

/** One line in the Ask-AI follow-up chat: the driver's question or the AI answer. */
data class ChatMessage(val text: String, val fromUser: Boolean)

/** Fragment host: owns navigation, the message history, VIN load, and the ask coroutine. */
class AskAiFragment : Fragment() {
    private lateinit var obdHelper: ObdHelper
    private lateinit var aiService: AiService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args = AskAiFragmentArgs.fromBundle(requireArguments())
        val errorCode = args.errorCode
        obdHelper = (activity as MainActivity).obdHelper
        aiService = (activity as MainActivity).aiService

        // Same lookup as the detail screen: memory first, cached scan second.
        val dto = ObdDataHolder.dtpResults.find { it.errorCode == errorCode }
            ?: DtcStore.load(requireContext()).find { it.errorCode == errorCode }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Obd2AiTheme(darkTheme = isSystemInDarkTheme()) {
                    if (dto == null) {
                        Text(
                            text = requireContext().getString(R.string.error_detail_gone, errorCode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(24.dp)
                        )
                    } else {
                        AskAiScreen(
                            dto = dto,
                            errorCode = errorCode,
                            obdHelper = obdHelper,
                            aiService = aiService,
                            context = requireContext(),
                            onLogEvent = { name -> (activity as MainActivity).logAnalyticsEvent(name) },
                            onShowError = { text ->
                                ErrorDialogFragment.newInstance(text)
                                    .show(parentFragmentManager, "errorDialog")
                            },
                            onNavBack = { findNavController().navigateUp() }
                        )
                    }
                }
            }
        }
    }
}

/** Compose content for the Ask-AI screen. */
@Composable
fun AskAiScreen(
    dto: DtpCodeDTO,
    errorCode: String,
    obdHelper: ObdHelper,
    aiService: AiService,
    context: Context,
    onLogEvent: (String) -> Unit,
    onShowError: (String) -> Unit,
    onNavBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var question by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var vin by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    // Load VIN (best effort) — enriches the AI context.
    LaunchedEffect(Unit) {
        scope.launch {
            vin = runCatching { obdHelper.getVehicleInfo().first }.getOrNull()
                ?.takeIf { it.isNotBlank() && it != "?" }
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.ask_ai_title),
        onBack = onNavBack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = context.getString(R.string.ask_ai_welcome, errorCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ask_ai_data_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!aiService.hasApiKey()) {
                Text(
                    text = stringResource(R.string.ask_ai_no_key),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chat transcript.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                items(items = messages, key = { it.hashCode().toString() }) { message ->
                    ChatBubble(message = message)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (thinking) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ask_ai_thinking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.ask_ai_question_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ObdButton(
                    onClick = {
                        val q = question.trim()
                        if (q.isNotEmpty() && !thinking) {
                            question = ""
                            messages = messages + ChatMessage(q, fromUser = true)
                            thinking = true
                            scope.launch {
                                try {
                                    val answer = aiService.askFollowUp(
                                        dto, ObdDataHolder.lastMil, vin, history, q
                                    )
                                    val newHistory = (history + (q to answer)).takeLast(10)
                                    history = newHistory
                                    thinking = false
                                    messages = messages + ChatMessage(answer, fromUser = false)
                                    onLogEvent("ask_ai_sent")
                                } catch (e: Exception) {
                                    Log.e("AskAiFragment", "Follow-up answer failed", e)
                                    thinking = false
                                    onShowError(context.getString(R.string.ask_ai_failed, e.message))
                                }
                            }
                        }
                    },
                    enabled = !thinking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.ask_ai_send))
                }
            }
        }
    }
}

/** One chat bubble: sender label + text; user right/primary, AI left/surface. */
@Composable
private fun ChatBubble(message: ChatMessage) {
    val context = LocalContext.current
    val side = if (message.fromUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = side
    ) {
        Text(
            text = context.getString(if (message.fromUser) R.string.chat_label_you else R.string.chat_label_ai),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .background(bubbleColor, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (message.fromUser) androidx.compose.ui.text.font.FontWeight.Bold else null
                )
            }
        }
    }
}
