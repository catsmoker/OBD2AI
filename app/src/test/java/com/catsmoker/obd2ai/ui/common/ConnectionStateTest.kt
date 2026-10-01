package com.catsmoker.obd2ai.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the menu's connection-state resolution. */
class ConnectionStateTest {

    @Test
    fun `demo wins over everything`() {
        assertEquals(ConnectionState.DEMO, ConnectionState.resolve(demoMode = true, isConnected = true))
        assertEquals(ConnectionState.DEMO, ConnectionState.resolve(demoMode = true, isConnected = false))
    }

    @Test
    fun `real connection resolves to connected`() {
        assertEquals(
            ConnectionState.CONNECTED,
            ConnectionState.resolve(demoMode = false, isConnected = true)
        )
    }

    @Test
    fun `nothing linked resolves to disconnected`() {
        assertEquals(
            ConnectionState.DISCONNECTED,
            ConnectionState.resolve(demoMode = false, isConnected = false)
        )
    }
}
