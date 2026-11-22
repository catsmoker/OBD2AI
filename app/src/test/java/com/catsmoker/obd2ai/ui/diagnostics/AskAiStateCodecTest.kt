package com.catsmoker.obd2ai.ui.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rotation must not lose the Ask-AI conversation, and a half-finished question
 * must come back as an explicit retry — never as a silent second AI call.
 */
class AskAiStateCodecTest {

    @Test
    fun `round trip keeps the transcript and the model history`() {
        val state = AskAiChat(
            messages = listOf(
                ChatMessage("Why is P0301 flashing?", fromUser = true),
                ChatMessage("A flashing light means an active misfire.", fromUser = false)
            ),
            history = listOf("Why is P0301 flashing?" to "A flashing light means an active misfire.")
        )
        val restored = AskAiStateCodec.decode(AskAiStateCodec.encode(state))
        assertEquals(state.messages, restored.messages)
        assertEquals(state.history, restored.history)
        assertNull(restored.pendingQuestion)
    }

    @Test
    fun `awkward text survives the round trip`() {
        val tricky = "Line one\nLine two · 42 % \"quoted\" \\slash — 北京 🚗 {json}"
        val state = AskAiChat(
            messages = listOf(ChatMessage(tricky, fromUser = true)),
            history = listOf(tricky to tricky)
        )
        val restored = AskAiStateCodec.decode(AskAiStateCodec.encode(state))
        assertEquals(tricky, restored.messages.single().text)
        assertEquals(tricky, restored.history.single().second)
    }

    @Test
    fun `an unanswered question is remembered so it can be retried`() {
        val state = AskAiChat(
            messages = listOf(ChatMessage("And the coil pack?", fromUser = true)),
            pendingQuestion = "And the coil pack?"
        )
        val restored = AskAiStateCodec.decode(AskAiStateCodec.encode(state))
        assertEquals("And the coil pack?", restored.pendingQuestion)
    }

    @Test
    fun `missing or malformed saved state degrades to an empty chat`() {
        assertEquals(AskAiChat(), AskAiStateCodec.decode(null))
        assertEquals(AskAiChat(), AskAiStateCodec.decode(""))
        assertEquals(AskAiChat(), AskAiStateCodec.decode("not json"))
        assertEquals(AskAiChat(), AskAiStateCodec.decode("{}"))
    }
}
