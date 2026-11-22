package com.catsmoker.obd2ai.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the console's bounded transcript ring. */
class ConsoleLogTest {

    @Test
    fun `console log keeps newest lines within capacity`() {
        val log = ConsoleLog(capacity = 3)
        log.append("a")
        log.append("b")
        log.append("c")
        log.append("d")
        assertEquals(listOf("b", "c", "d"), log.snapshot())
    }

    @Test
    fun `console log trims by characters as well as by lines`() {
        val log = ConsoleLog(capacity = 50, maxChars = 20)
        log.append("0123456789")
        log.append("abcdefghij")
        log.append("ABCDEFGHIJ")
        // 30 chars in, cap 20: the oldest entry goes, the two newest stay.
        assertEquals(listOf("abcdefghij", "ABCDEFGHIJ"), log.snapshot())
    }

    @Test
    fun `one oversized reply is kept so the answer is never lost`() {
        val log = ConsoleLog(capacity = 10, maxChars = 5)
        log.append("previous")
        log.append("0123456789ABCDEF")
        assertEquals(listOf("0123456789ABCDEF"), log.snapshot())
    }

    @Test
    fun `export joins the transcript with newlines`() {
        val log = ConsoleLog()
        log.append("> ATZ")
        log.append("ELM327 v1.5")
        assertEquals("> ATZ\nELM327 v1.5", log.text())
    }

    @Test
    fun `console log clears fully`() {
        val log = ConsoleLog()
        log.append("a")
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }
}
