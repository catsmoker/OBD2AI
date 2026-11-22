package com.catsmoker.obd2ai.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stored transport values must resolve exactly, and never invent a default. */
class TransportMemoryTest {

    @Test
    fun `round trip for both transports`() {
        for (transport in ObdTransport.entries) {
            assertEquals(transport, ObdTransport.fromValue(transport.prefValue))
        }
    }

    @Test
    fun `unknown or missing values stay unknown`() {
        assertNull(ObdTransport.fromValue(null))
        assertNull(ObdTransport.fromValue(""))
        assertNull(ObdTransport.fromValue("usb"))
        assertNull(ObdTransport.fromValue("Bluetooth"))
    }
}
