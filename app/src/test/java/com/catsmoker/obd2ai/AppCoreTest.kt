package com.catsmoker.obd2ai

import com.github.eltonvs.obd.command.ObdRawResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure parsing/formatting logic in AppCore.kt.
 * These functions have no Android dependencies, so they run on the JVM.
 */
class AppCoreTest {

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
    fun `speed command returns zero when identifier missing`() {
        val response = ObdRawResponse("NO DATA\r\r>", elapsedTime = 0L)
        assertEquals("0", MySpeedCommand().handler(response))
    }

    @Test
    fun `rpm command parses two-byte value`() {
        // (0x1A * 256 + 0xF8) / 4 = 6904 / 4 = 1726 RPM
        val response = ObdRawResponse("410C1AF8\r\r>", elapsedTime = 0L)
        assertEquals("1726", MyRPMCommand().handler(response))
    }

    @Test
    fun `rpm command returns zero when truncated`() {
        val response = ObdRawResponse("410C1A\r\r>", elapsedTime = 0L)
        assertEquals("0", MyRPMCommand().handler(response))
    }

    @Test
    fun `coolant temperature command subtracts 40 offset`() {
        // 0x5A = 90 -> 90 - 40 = 50 °C
        val response = ObdRawResponse("41055A\r\r>", elapsedTime = 0L)
        assertEquals("50", MyCoolantTempCommand().handler(response))
    }

    @Test
    fun `coolant temperature command returns zero when missing`() {
        val response = ObdRawResponse("4105\r\r>", elapsedTime = 0L)
        assertEquals("0", MyCoolantTempCommand().handler(response))
    }

    // ------------------------------------------------------------------
    // DTC list splitting
    // ------------------------------------------------------------------

    @Test
    fun `splitErrors returns empty list for NO DATA`() {
        assertTrue(ObdHelper.splitErrors("NO DATA").isEmpty())
    }

    @Test
    fun `splitErrors is case-insensitive for NO DATA`() {
        assertTrue(ObdHelper.splitErrors("no data").isEmpty())
    }

    @Test
    fun `splitErrors returns empty list for blank input`() {
        assertTrue(ObdHelper.splitErrors("   ").isEmpty())
    }

    @Test
    fun `splitErrors splits on whitespace and commas`() {
        assertEquals(
            listOf("P0301", "P0302", "P0455"),
            ObdHelper.splitErrors("P0301 P0302,P0455")
        )
    }

    @Test
    fun `splitErrors trims stray whitespace around tokens`() {
        assertEquals(
            listOf("P0301", "P0442"),
            ObdHelper.splitErrors(" P0301 , P0442 ")
        )
    }

    // ------------------------------------------------------------------
    // OpenAI JSON parsing
    // ------------------------------------------------------------------

    @Test
    fun `parseErrorInfo parses a well-formed assessment`() {
        val json = """
            {
              "errorCode": "P0301",
              "severity": 2,
              "title": "Cylinder 1 Misfire",
              "detail": "Cylinder 1 is misfiring.",
              "implications": "Damage to catalytic converter possible.",
              "suggestedActions": ["Replace spark plug", "Check ignition coil"]
            }
        """.trimIndent()

        val dto = OpenAIService.parseErrorInfo(json)

        assertEquals("P0301", dto.errorCode)
        assertEquals(ErrorSeverity.HIGH, dto.severity)
        assertEquals("Cylinder 1 Misfire", dto.title)
        assertEquals(listOf("Replace spark plug", "Check ignition coil"), dto.suggestedActions)
    }

    @Test
    fun `parseErrorInfo falls back to placeholder DTO on invalid JSON`() {
        val dto = OpenAIService.parseErrorInfo("not json at all")

        assertEquals("Error", dto.errorCode)
        assertEquals(ErrorSeverity.LOW, dto.severity)
        assertEquals("Parsing Error", dto.title)
    }

    @Test
    fun `parseErrorInfo falls back to placeholder DTO on missing fields`() {
        val dto = OpenAIService.parseErrorInfo("""{"errorCode": "P0301"}""")

        assertEquals("Error", dto.errorCode)
        assertEquals("Try again.", dto.suggestedActions.single())
    }

    // ------------------------------------------------------------------
    // ErrorSeverity mapping
    // ------------------------------------------------------------------

    @Test
    fun `ErrorSeverity fromInt maps known values and rejects others`() {
        assertEquals(ErrorSeverity.LOW, ErrorSeverity.fromInt(0))
        assertEquals(ErrorSeverity.MEDIUM, ErrorSeverity.fromInt(1))
        assertEquals(ErrorSeverity.HIGH, ErrorSeverity.fromInt(2))
        var threw = false
        try {
            ErrorSeverity.fromInt(9)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
