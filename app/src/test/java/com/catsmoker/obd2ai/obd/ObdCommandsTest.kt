package com.catsmoker.obd2ai.obd

import com.github.eltonvs.obd.command.ObdRawResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the defensive Mode-01 command handlers, the PID registry
 * and the support-bitmask discovery. No Android dependencies; JVM only.
 */
class ObdCommandsTest {

    // ------------------------------------------------------------------
    // OBD hex command handlers
    // ------------------------------------------------------------------

    @Test
    fun `speed command parses classic ELM response`() {
        val response = ObdRawResponse("410D1F\r\r>", elapsedTime = 0L)
        assertEquals("31", MySpeedCommand().handler(response))
    }

    @Test
    fun `speed command handles response with echo`() {
        // Some adapters echo the request before the answer; processedValue strips
        // whitespace, so "010D\r410D00" contains the identifier for parsing.
        val response = ObdRawResponse("010D\r410D00\r\r>", elapsedTime = 0L)
        assertEquals("0", MySpeedCommand().handler(response))
    }

    @Test
    fun `speed command returns blank when identifier missing`() {
        val response = ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)
        assertEquals("", MySpeedCommand().handler(response))
    }

    @Test
    fun `speed command returns blank on non-hex payload`() {
        val response = ObdRawResponse("410DZZ\r\r>", elapsedTime = 0L)
        assertEquals("", MySpeedCommand().handler(response))
    }

    @Test
    fun `rpm command parses two-byte value`() {
        // (0x1A * 256 + 0xF8) / 4 = 6904 / 4 = 1726 RPM
        val response = ObdRawResponse("410C1AF8\r\r>", elapsedTime = 0L)
        assertEquals("1726", MyRPMCommand().handler(response))
    }

    @Test
    fun `rpm command returns blank when truncated`() {
        val response = ObdRawResponse("410C1A\r\r>", elapsedTime = 0L)
        assertEquals("", MyRPMCommand().handler(response))
    }

    @Test
    fun `rpm command returns blank on non-hex payload`() {
        val response = ObdRawResponse("410CZZZZ\r\r>", elapsedTime = 0L)
        assertEquals("", MyRPMCommand().handler(response))
    }

    @Test
    fun `coolant temperature command subtracts 40 offset`() {
        // 0x5A = 90 -> 90 - 40 = 50 °C
        val response = ObdRawResponse("41055A\r\r>", elapsedTime = 0L)
        assertEquals("50", MyCoolantTempCommand().handler(response))
    }

    @Test
    fun `coolant temperature command returns blank when missing`() {
        val response = ObdRawResponse("4105\r\r>", elapsedTime = 0L)
        assertEquals("", MyCoolantTempCommand().handler(response))
    }

    @Test
    fun `coolant temperature command returns blank on non-hex payload`() {
        val response = ObdRawResponse("4105ZZ\r\r>", elapsedTime = 0L)
        assertEquals("", MyCoolantTempCommand().handler(response))
    }

    // ------------------------------------------------------------------
    // Extended PID command handlers (throttle, intake temp, MAF, timing, trims)
    // ------------------------------------------------------------------

    @Test
    fun `throttle command parses single byte percent`() {
        // 0x82 = 130 -> 130 * 100 / 255 = 50 (integer division)
        val response = ObdRawResponse("411182\r\r>", elapsedTime = 0L)
        assertEquals("50", MyThrottleCommand().handler(response))
    }

    @Test
    fun `throttle command returns blank on garbage`() {
        assertEquals("", MyThrottleCommand().handler(ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)))
        assertEquals("", MyThrottleCommand().handler(ObdRawResponse("4111ZZ\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `intake temp command subtracts 40 offset`() {
        // 0x46 = 70 -> 30 °C
        val response = ObdRawResponse("410F46\r\r>", elapsedTime = 0L)
        assertEquals("30", MyIntakeTempCommand().handler(response))
    }

    @Test
    fun `intake temp command returns blank on garbage`() {
        assertEquals("", MyIntakeTempCommand().handler(ObdRawResponse("410F\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `maf command parses two byte grams per second`() {
        // (0x01 * 256 + 0xF4) / 100 = 500 / 100 = 5.0 g/s
        val response = ObdRawResponse("411001F4\r\r>", elapsedTime = 0L)
        assertEquals("5.0", MyMafCommand().handler(response))
    }

    @Test
    fun `maf command returns blank when truncated or garbage`() {
        assertEquals("", MyMafCommand().handler(ObdRawResponse("411001\r\r>", elapsedTime = 0L)))
        assertEquals("", MyMafCommand().handler(ObdRawResponse("4110ZZZZ\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `timing command handles negative advance`() {
        // 0x40 = 64 -> 64 / 2 - 64 = -32.0 (retarded timing is valid)
        val response = ObdRawResponse("410E40\r\r>", elapsedTime = 0L)
        assertEquals("-32.0", MyTimingCommand().handler(response))
    }

    @Test
    fun `timing command parses positive advance`() {
        // 0xA0 = 160 -> 80 - 64 = 16.0
        val response = ObdRawResponse("410EA0\r\r>", elapsedTime = 0L)
        assertEquals("16.0", MyTimingCommand().handler(response))
    }

    @Test
    fun `short fuel trim command centers 128 at zero`() {
        // 0x80 = 128 -> 0.0 %; 0x90 = 144 -> +12.5 %
        assertEquals("0.0", MyShortFuelTrimCommand().handler(ObdRawResponse("410680\r\r>", elapsedTime = 0L)))
        assertEquals("12.5", MyShortFuelTrimCommand().handler(ObdRawResponse("410690\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `long fuel trim command handles negative trim`() {
        // 0x70 = 112 -> (112 - 128) * 100 / 128 = -12.5 %
        val response = ObdRawResponse("410870\r\r>", elapsedTime = 0L)
        assertEquals("-12.5", MyLongFuelTrimCommand().handler(response))
    }

    @Test
    fun `fuel trim commands return blank on garbage`() {
        assertEquals("", MyShortFuelTrimCommand().handler(ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)))
        assertEquals("", MyLongFuelTrimCommand().handler(ObdRawResponse("4108ZZ\r\r>", elapsedTime = 0L)))
    }

    // ------------------------------------------------------------------
    // PID registry + support bitmask discovery
    // ------------------------------------------------------------------

    @Test
    fun `pid registry has unique pids`() {
        val pids = PidRegistry.all.map { it.pid }
        assertEquals(pids.size, pids.toSet().size)
    }

    @Test
    fun `pid registry covers the polled and discovered pids`() {
        for (pid in listOf(0x04, 0x05, 0x06, 0x08, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x2F, 0x42)) {
            assertTrue("missing PID ${pid.toString(16)}", PidRegistry.forPid(pid) != null)
        }
    }

    @Test
    fun `support bitmask decodes classic 0100 response`() {
        // BE1FA813: PIDs 01,03-07,0C-10,11,13,15,1C,1F,20 supported.
        val supported = PidRegistry.parseSupportBitmask("BE1FA813", 0x00)
        assertTrue(supported.contains(0x01))
        assertTrue(supported.contains(0x0C))
        assertTrue(supported.contains(0x0D))
        assertTrue(supported.contains(0x11))
        assertTrue(supported.contains(0x20))
        assertTrue(!supported.contains(0x02))
        assertTrue(!supported.contains(0x08))
    }

    @Test
    fun `support bitmask offsets ranges correctly`() {
        // 0x0120 range: same payload shape means 0x21, 0x2C, 0x2D...
        val supported = PidRegistry.parseSupportBitmask("BE1FA813", 0x20)
        assertTrue(supported.contains(0x21))
        assertTrue(supported.contains(0x2C))
        assertTrue(supported.contains(0x2D))
        assertTrue(!supported.contains(0x0C))
    }

    @Test
    fun `support bitmask tolerates case whitespace and garbage`() {
        assertEquals(
            PidRegistry.parseSupportBitmask("BE1FA813", 0x00),
            PidRegistry.parseSupportBitmask("be 1f a8 13", 0x00),
        )
        assertTrue(PidRegistry.parseSupportBitmask("", 0x00).isEmpty())
        assertTrue(PidRegistry.parseSupportBitmask("NO DATA", 0x00).isEmpty())
        assertTrue(PidRegistry.parseSupportBitmask("ZZ", 0x00).isEmpty())
    }

    @Test
    fun `support probe command extracts payload after identifier`() {
        val response = ObdRawResponse("4100BE1FA813\r\r>", elapsedTime = 0L)
        assertEquals("BE1FA813", MySupportedPidsCommand("00").handler(response))
    }

    @Test
    fun `support probe command returns blank without identifier`() {
        assertEquals("", MySupportedPidsCommand("00").handler(ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)))
    }

    @Test
    fun `plausibility gate rejects out of range readings`() {
        assertTrue(PidRegistry.isPlausible(0x11, 50.0))
        assertTrue(!PidRegistry.isPlausible(0x11, 101.0))
        assertTrue(!PidRegistry.isPlausible(0x05, 300.0))
        assertTrue(PidRegistry.isPlausible(0x0E, -32.0))
        // Unknown PIDs pass through (no definition to judge by).
        assertTrue(PidRegistry.isPlausible(0x99, 12345.0))
    }

    @Test
    fun `unknown support state counts as supported`() {
        ObdDataHolder.supportedPids = null
        assertTrue(ObdDataHolder.isPidSupported(0x0C))
        ObdDataHolder.supportedPids = setOf(0x0C)
        assertTrue(ObdDataHolder.isPidSupported(0x0C))
        assertTrue(!ObdDataHolder.isPidSupported(0x11))
        ObdDataHolder.supportedPids = null
    }

    @Test
    fun `fast batch parses four gauges from one reply`() {
        val batch = ObdHelper.parseFastBatch("410C1AF8410D1F41055A410482\r\r>")
        assertEquals("1726", batch[PidRegistry.PID_RPM])
        assertEquals("31", batch[PidRegistry.PID_SPEED])
        assertEquals("50", batch[PidRegistry.PID_COOLANT_TEMP])
        assertEquals("50", batch[PidRegistry.PID_ENGINE_LOAD])
    }

    @Test
    fun `fast batch yields blanks for missing frames`() {
        val batch = ObdHelper.parseFastBatch("NO DATA\r\r>")
        assertEquals(4, batch.size)
        assertTrue(batch.values.all { it.isEmpty() })
    }
}
