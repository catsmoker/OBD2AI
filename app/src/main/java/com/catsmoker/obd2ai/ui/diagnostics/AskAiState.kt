package com.catsmoker.obd2ai.ui.diagnostics

import androidx.compose.runtime.saveable.Saver
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the Ask-AI screen must keep across a rotation: the transcript,
 * the follow-up history the model is allowed to see, and a question whose
 * answer never arrived. Saved without an in-flight flag — a request cannot
 * survive the Activity, so after a rotation the screen shows an interrupted
 * notice with an explicit Retry instead of silently billing a second call.
 */
data class AskAiChat(
    val messages: List<ChatMessage> = emptyList(),
    val history: List<Pair<String, String>> = emptyList(),
    val pendingQuestion: String? = null
)

/** JSON round-trip for [AskAiChat]; malformed saved state degrades to empty. */
object AskAiStateCodec {

    fun encode(state: AskAiChat): String {
        val root = JSONObject()
        val messages = JSONArray()
        state.messages.forEach { message ->
            messages.put(
                JSONObject()
                    .put("user", message.fromUser)
                    .put("text", message.text)
            )
        }
        root.put("messages", messages)
        val history = JSONArray()
        state.history.forEach { (question, answer) ->
            history.put(JSONArray().put(question).put(answer))
        }
        root.put("history", history)
        state.pendingQuestion?.let { root.put("pending", it) }
        return root.toString()
    }

    fun decode(raw: String?): AskAiChat {
        if (raw.isNullOrBlank()) return AskAiChat()
        return try {
            val root = JSONObject(raw)
            val messages = mutableListOf<ChatMessage>()
            val messageArray = root.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until messageArray.length()) {
                val obj = messageArray.optJSONObject(i) ?: continue
                val text = obj.optString("text")
                if (text.isNotEmpty()) {
                    messages += ChatMessage(text = text, fromUser = obj.optBoolean("user"))
                }
            }
            val history = mutableListOf<Pair<String, String>>()
            val historyArray = root.optJSONArray("history") ?: JSONArray()
            for (i in 0 until historyArray.length()) {
                val pair = historyArray.optJSONArray(i) ?: continue
                val question = pair.optString(0)
                val answer = pair.optString(1)
                if (question.isNotEmpty()) history += question to answer
            }
            AskAiChat(
                messages = messages,
                history = history,
                pendingQuestion = root.optString("pending").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            AskAiChat()
        }
    }
}

/** `rememberSaveable` saver used by the Ask-AI screen. */
val AskAiChatSaver: Saver<AskAiChat, String> = Saver(
    save = { AskAiStateCodec.encode(it) },
    restore = { AskAiStateCodec.decode(it) }
)
