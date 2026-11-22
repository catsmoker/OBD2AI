package com.catsmoker.obd2ai.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

/** The classifier must never invent a cause it cannot see in the message. */
class ConnectFailureKindTest {

    @Test
    fun `socket failures are unreachable, for both transports`() {
        assertEquals(
            ConnectFailureKind.UNREACHABLE,
            ConnectFailureKind.classify("Failed to connect to 10.0.2.2:35000.")
        )
        assertEquals(
            ConnectFailureKind.UNREACHABLE,
            ConnectFailureKind.classify("Failed to connect to device (secure and insecure).")
        )
    }

    @Test
    fun `a torn down transport is unreachable`() {
        assertEquals(
            ConnectFailureKind.UNREACHABLE,
            ConnectFailureKind.classify("OBD connection not established.")
        )
    }

    @Test
    fun `dead sockets are unreachable, not slow adapters`() {
        for (message in listOf("Broken pipe", "Socket closed", "Connection reset by peer")) {
            assertEquals(message, ConnectFailureKind.UNREACHABLE, ConnectFailureKind.classify(message))
        }
    }

    @Test
    fun `pairing and bluetooth problems keep their own cause`() {
        assertEquals(ConnectFailureKind.NOT_PAIRED, ConnectFailureKind.classify("Device not found"))
        assertEquals(
            ConnectFailureKind.BLUETOOTH_UNAVAILABLE,
            ConnectFailureKind.classify("Bluetooth adapter is not available.")
        )
    }

    @Test
    fun `unknown and empty messages stay generic`() {
        assertEquals(ConnectFailureKind.GENERIC, ConnectFailureKind.classify(null))
        assertEquals(ConnectFailureKind.GENERIC, ConnectFailureKind.classify("   "))
        assertEquals(ConnectFailureKind.GENERIC, ConnectFailureKind.classify("No reply from adapter (timeout)."))
    }
}
