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
    fun `console log clears fully`() {
        val log = ConsoleLog()
        log.append("a")
        log.clear()
        assertTrue(log.snapshot().isEmpty())
    }
}
