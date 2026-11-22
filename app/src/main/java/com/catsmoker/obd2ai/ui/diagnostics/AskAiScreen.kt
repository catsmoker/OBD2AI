package com.catsmoker.obd2ai.ui.diagnostics

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.ai.AiService
import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.obd.ObdDataHolder
import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.ui.components.CircleIconButton
import com.catsmoker.obd2ai.ui.components.ScreenScaffold
import com.catsmoker.obd2ai.ui.components.SectionCard
import com.catsmoker.obd2ai.ui.glass.GlassCard
import com.catsmoker.obd2ai.ui.theme.ObdRadius
import com.catsmoker.obd2ai.ui.theme.ObdSpacing
import kotlinx.coroutines.launch

/** One line in the Ask-AI follow-up chat: the driver's question or the AI answer. */
data class ChatMessage(val text: String, val fromUser: Boolean)

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
    val listState = rememberLazyListState()

    // Transcript, model history and any unanswered question survive rotation;
    // the conversation is never rebuilt from scratch and no request is re-sent
    // behind the driver's back.
    var chat by rememberSaveable(stateSaver = AskAiChatSaver) { mutableStateOf(AskAiChat()) }
    var question by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var vin by rememberSaveable { mutableStateOf<String?>(null) }

    // Load VIN (best effort) once — it enriches the AI context.
    LaunchedEffect(Unit) {
        if (vin == null) {
            scope.launch {
                vin = runCatching { obdHelper.getVehicleInfo().first }.getOrNull()
                    ?.takeIf { it.isNotBlank() && it != "?" }
            }
        }
    }

    fun requestAnswer(q: String) {
        thinking = true
        chat = chat.copy(pendingQuestion = q)
        val contextVin = vin
        val history = chat.history
        scope.launch {
            try {
                val answer = aiService.askFollowUp(dto, ObdDataHolder.lastMil, contextVin, history, q)
                thinking = false
                chat = chat.copy(
                    messages = chat.messages + ChatMessage(answer, fromUser = false),
                    history = (history + (q to answer)).takeLast(10),
                    pendingQuestion = null
                )
                onLogEvent("ask_ai_sent")
            } catch (e: Exception) {
                Log.e("AskAiScreen", "Follow-up answer failed", e)
                thinking = false
                chat = chat.copy(pendingQuestion = null)
                onShowError(context.getString(R.string.ask_ai_failed, e.message))
            }
        }
    }

    fun send() {
        val q = question.trim()
        if (q.isEmpty() || thinking) return
        question = ""
        chat = chat.copy(messages = chat.messages + ChatMessage(q, fromUser = true))
        requestAnswer(q)
    }

    val messages = chat.messages


    // Keep the newest message in view.
    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.ask_ai_title),
        subtitle = errorCode,
        onBack = onNavBack,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Context card: what the assistant knows and what it costs.
            GlassCard(
                shape = RoundedCornerShape(ObdRadius.Card),
                contentPadding = 16.dp,
                blurPx = 14.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = context.getString(R.string.ask_ai_welcome, errorCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ask_ai_data_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!aiService.hasApiKey()) {
                Spacer(Modifier.height(ObdSpacing.CardGap))
                SectionCard {
                    Text(
                        text = stringResource(R.string.ask_ai_no_key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(ObdSpacing.CardGap))

            // Chat transcript.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = messages, key = { it.hashCode().toString() }) { message ->
                    ChatBubble(message = message)
                }
                if (thinking) {
                    item(key = "thinking") { ThinkingRow() }
                } else if (chat.pendingQuestion != null) {
                    item(key = "interrupted") {
                        InterruptedRow(onRetry = { requestAnswer(chat.pendingQuestion!!) })
                    }
                }
            }

            Spacer(Modifier.height(ObdSpacing.CardGap))

            // Composer: pill field + one clear send action.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.ask_ai_question_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(ObdRadius.Pill),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.ask_ai_send),
                    onClick = { send() },
                    containerColor = if (thinking) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (thinking) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            }

            Spacer(Modifier.height(ObdSpacing.CardGap))
        }
    }

}

/**
 * Shown when a request did not survive a configuration change: the question is
 * still there, the answer is not, and retrying is the driver's decision.
 */
@Composable
private fun InterruptedRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(ObdRadius.Inner)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.ask_ai_interrupted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.ask_ai_retry))
        }
    }
}

/** Pulsing "thinking" placeholder in the assistant's lane. */
@Composable
private fun ThinkingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.ask_ai_thinking),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One chat bubble: sender label + text; user right/primary, AI left/tonal. */
@Composable
private fun ChatBubble(message: ChatMessage) {
    val context = LocalContext.current
    val bubbleColor = if (message.fromUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.fromUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = context.getString(if (message.fromUser) R.string.chat_label_you else R.string.chat_label_ai),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bubbleColor, RoundedCornerShape(ObdRadius.Inner))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (message.fromUser) FontWeight.Medium else null
            )
        }
    }
}
