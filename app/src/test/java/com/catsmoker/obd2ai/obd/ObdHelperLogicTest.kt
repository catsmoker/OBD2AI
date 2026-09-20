package com.catsmoker.obd2ai.obd

import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.MilStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the connection-layer parsing helpers: DTC splitting, MIL
 * status, voltage, code-source pills, typed recovery, the ELM sanitizer
 * taxonomy and the per-PID health budget. JVM only.
 */
class ObdHelperLogicTest {

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

    @Test
    fun `splitErrors drops ELM327 status chatter`() {
        assertTrue(ObdHelper.splitErrors("NODATA").isEmpty())
        assertTrue(ObdHelper.splitErrors("?").isEmpty())
        assertTrue(ObdHelper.splitErrors("SEARCHING... NO DATA").isEmpty())
        assertTrue(ObdHelper.splitErrors("STOPPED").isEmpty())
        assertTrue(ObdHelper.splitErrors("UNABLE TO CONNECT").isEmpty())
        assertEquals(
            listOf("P0301"),
            ObdHelper.splitErrors("P0301 STOPPED")
        )
        assertEquals(
            listOf("P0301", "P0455"),
            ObdHelper.splitErrors("SEARCHING...\rP0301,P0455\r>")
        )
    }

    @Test
    fun `splitErrors drops CAN voltage and timeout chatter`() {
        assertEquals(listOf("P0301"), ObdHelper.splitErrors("P0301 CAN ERROR\r\n>"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("BUS BUSY"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("BUSINIT"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("LOW VOLTAGE LVRESET"))
        assertEquals(emptyList<String>(), ObdHelper.splitErrors("TIMEOUT FCRX"))
    }

    // ------------------------------------------------------------------
    // MIL status parsing
    // ------------------------------------------------------------------

    @Test
    fun `parseMilStatus maps lamp state and stored count`() {
        assertEquals(MilStatus(true, 3), ObdHelper.parseMilStatus("true", "3"))
        assertEquals(MilStatus(false, 0), ObdHelper.parseMilStatus("false", "0"))
    }

    @Test
    fun `parseMilStatus is defensive about garbage input`() {
        assertEquals(MilStatus(false, 0), ObdHelper.parseMilStatus("maybe", "many"))
        assertEquals(MilStatus(false, 0), ObdHelper.parseMilStatus("false", "-2"))
    }

    @Test
    fun `parseVoltage finds the first decimal`() {
        assertEquals(13.8f, ObdHelper.parseVoltage("13.8 V")!!, 0.001f)
        assertEquals(12.6f, ObdHelper.parseVoltage("12.6V")!!, 0.001f)
        assertEquals(null, ObdHelper.parseVoltage("NO DATA"))
    }

    // ------------------------------------------------------------------
    // Code-source pills: which read mode reported each code
    // ------------------------------------------------------------------

    @Test
    fun `mergeCodeSources unions modes per code`() {
        val merged = ObdHelper.mergeCodeSources(
            stored = listOf("P0301", "P0420"),
            pending = listOf("P0420", "P0455"),
            permanent = listOf("P0455")
        )
        assertEquals(setOf(DtcSource.STORED), merged.getValue("P0301"))
        assertEquals(setOf(DtcSource.STORED, DtcSource.PENDING), merged.getValue("P0420"))
        assertEquals(setOf(DtcSource.PENDING, DtcSource.PERMANENT), merged.getValue("P0455"))
    }

    @Test
    fun `mergeCodeSources normalizes case and skips blanks`() {
        val merged = ObdHelper.mergeCodeSources(
            stored = listOf("p0301", "  "),
            pending = emptyList(),
            permanent = listOf("P0301")
        )
        assertEquals(setOf(DtcSource.STORED, DtcSource.PERMANENT), merged.getValue("P0301"))
        assertEquals(1, merged.size)
    }

    @Test
    fun `mergeCodeSources is empty when every mode is empty`() {
        assertTrue(ObdHelper.mergeCodeSources(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    // ------------------------------------------------------------------
    // Connection robustness: typed recovery
    // ------------------------------------------------------------------

    @Test
    fun `recoveryFor maps adapter failures to re-init`() {
        fun raw(text: String) = com.github.eltonvs.obd.command.ObdRawResponse(text, 0L)
        assertEquals(
            ElmRecovery.REINIT,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.UnableToConnectException(MySpeedCommand(), raw("UNABLE TO CONNECT"))
            )
        )
        assertEquals(
            ElmRecovery.REINIT,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.BusInitException(MySpeedCommand(), raw("BUS INIT... ERROR"))
            )
        )
    }

    @Test
    fun `recoveryFor retries stops and ignores no-data`() {
        fun raw(text: String) = com.github.eltonvs.obd.command.ObdRawResponse(text, 0L)
        assertEquals(
            ElmRecovery.RETRY,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.StoppedException(MySpeedCommand(), raw("STOPPED"))
            )
        )
        assertEquals(
            ElmRecovery.NONE,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.NoDataException(MySpeedCommand(), raw("NO DATA"))
            )
        )
        assertEquals(
            ElmRecovery.NONE,
            ObdHelper.recoveryFor(
                com.github.eltonvs.obd.command.UnSupportedCommandException(MySpeedCommand(), raw("?"))
            )
        )
        assertEquals(ElmRecovery.RETRY, ObdHelper.recoveryFor(java.io.IOException("boom")))
    }

    // ------------------------------------------------------------------
    // ELM sanitizer + status taxonomy
    // ------------------------------------------------------------------

    @Test
    fun `sanitizeLines drops garbage bytes and empty lines`() {
        // Control bytes, DEL and non-ASCII clone noise must vanish; the
        // printable lines (and the ">" prompt) survive, empties drop out.
        val raw = "410D1F\r\r" + 0.toChar() + 1.toChar() + ">" +
            0x7F.toChar() + 0xA1.toChar() + "\n\n  \nSEARCHING...\r>"
        val lines = ElmSanitizer.sanitizeLines(raw)
        assertEquals(listOf("410D1F", ">", "SEARCHING...", ">"), lines)
    }

    @Test
    fun `isValidPidLine accepts hex and spaces only`() {
        assertTrue(ElmSanitizer.isValidPidLine("41 0D 1F"))
        assertTrue(ElmSanitizer.isValidPidLine("410D1F"))
        assertTrue(!ElmSanitizer.isValidPidLine("410DZZ"))
        assertTrue(!ElmSanitizer.isValidPidLine("NO DATA"))
        assertTrue(!ElmSanitizer.isValidPidLine(""))
        assertTrue(!ElmSanitizer.isValidPidLine("OK"))
    }

    @Test
    fun `classifyStatus names every adapter state`() {
        assertEquals(ElmStatus.NO_DATA, ElmSanitizer.classifyStatus("NO DATA\r\r>"))
        assertEquals(ElmStatus.NO_DATA, ElmSanitizer.classifyStatus("NODATA"))
        assertEquals(ElmStatus.SEARCHING, ElmSanitizer.classifyStatus("SEARCHING..."))
        assertEquals(ElmStatus.STOPPED, ElmSanitizer.classifyStatus("STOPPED"))
        assertEquals(ElmStatus.UNABLE_TO_CONNECT, ElmSanitizer.classifyStatus("UNABLE TO CONNECT"))
        assertEquals(ElmStatus.BUS_BUSY, ElmSanitizer.classifyStatus("BUS BUSY"))
        assertEquals(ElmStatus.BUS_ERROR, ElmSanitizer.classifyStatus("BUS ERROR"))
        assertEquals(ElmStatus.BUS_ERROR, ElmSanitizer.classifyStatus("BUSINIT: ERROR"))
        assertEquals(ElmStatus.CAN_ERROR, ElmSanitizer.classifyStatus("CAN ERROR"))
        assertEquals(ElmStatus.LOW_VOLTAGE_RESET, ElmSanitizer.classifyStatus("LVRESET"))
        assertEquals(ElmStatus.GENERIC_ERROR, ElmSanitizer.classifyStatus("?"))
        assertEquals(ElmStatus.OK, ElmSanitizer.classifyStatus("OK"))
        assertEquals(ElmStatus.NONE, ElmSanitizer.classifyStatus("410D1F"))
    }

    @Test
    fun `classifyStatus checks specifics before generic markers`() {
        // "?" must not swallow real states; "ERROR" must not swallow CAN errors.
        assertEquals(ElmStatus.UNABLE_TO_CONNECT, ElmSanitizer.classifyStatus("UNABLE TO CONNECT\r?"))
        assertEquals(ElmStatus.CAN_ERROR, ElmSanitizer.classifyStatus("CAN ERROR"))
        // "OK" inside ordinary words is not an adapter OK.
        assertEquals(ElmStatus.NONE, ElmSanitizer.classifyStatus("BROKEN"))
    }

    @Test
    fun `status recovery tells the caller what to do`() {
        assertEquals(ElmRecovery.NONE, ElmStatus.NONE.recovery)
        assertEquals(ElmRecovery.NONE, ElmStatus.NO_DATA.recovery)
        assertEquals(ElmRecovery.RETRY, ElmStatus.SEARCHING.recovery)
        assertEquals(ElmRecovery.RETRY, ElmStatus.STOPPED.recovery)
        assertEquals(ElmRecovery.REINIT, ElmStatus.UNABLE_TO_CONNECT.recovery)
        assertEquals(ElmRecovery.REINIT, ElmStatus.CAN_ERROR.recovery)
        assertEquals(ElmRecovery.REINIT, ElmStatus.LOW_VOLTAGE_RESET.recovery)
        assertEquals(ElmRecovery.RETRY, ElmStatus.GENERIC_ERROR.recovery)
    }

    // ------------------------------------------------------------------
    // Per-PID health tracker (AndrOBD 3-strike lesson)
    // ------------------------------------------------------------------

    @Test
    fun `pid tracker disables a pid after three straight failures`() {
        val tracker = PidHealthTracker()
        assertTrue(!tracker.recordFailure(0x0C))
        assertTrue(!tracker.recordFailure(0x0C))
        assertTrue(tracker.recordFailure(0x0C))
        assertTrue(tracker.isDisabled(0x0C))
        assertEquals(setOf(0x0C), tracker.disabledPids())
    }

    @Test
    fun `pid tracker success resets the streak`() {
        val tracker = PidHealthTracker()
        tracker.recordFailure(0x0D)
        tracker.recordFailure(0x0D)
        tracker.recordSuccess(0x0D)
        tracker.recordFailure(0x0D)
        tracker.recordFailure(0x0D)
        assertTrue(!tracker.isDisabled(0x0D))
    }

    @Test
    fun `pid tracker isolates pids and resets cleanly`() {
        val tracker = PidHealthTracker()
        tracker.recordFailure(0x0C)
        tracker.recordFailure(0x0C)
        tracker.recordFailure(0x0C)
        assertTrue(!tracker.isDisabled(0x0D))
        tracker.reset()
        assertTrue(!tracker.isDisabled(0x0C))
        assertTrue(tracker.disabledPids().isEmpty())
    }
}
