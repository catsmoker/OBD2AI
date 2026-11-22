package com.catsmoker.obd2ai.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A drop note may only appear while the app is disconnected, and only with text. */
class ConnectionLossReasonTest {

    @Test
    fun `a reason shows while disconnected`() {
        assertEquals(
            "Broken pipe",
            ConnectionState.visibleLossReason(ConnectionState.DISCONNECTED, "Broken pipe")
        )
    }

    @Test
    fun `a connected or demo session never shows a drop note`() {
        assertNull(ConnectionState.visibleLossReason(ConnectionState.CONNECTED, "Broken pipe"))
        assertNull(ConnectionState.visibleLossReason(ConnectionState.DEMO, "Broken pipe"))
    }

    @Test
    fun `blank or absent reasons stay silent`() {
        assertNull(ConnectionState.visibleLossReason(ConnectionState.DISCONNECTED, null))
        assertNull(ConnectionState.visibleLossReason(ConnectionState.DISCONNECTED, "   "))
        assertEquals(
            "Socket closed",
            ConnectionState.visibleLossReason(ConnectionState.DISCONNECTED, "  Socket closed ")
        )
    }
}
