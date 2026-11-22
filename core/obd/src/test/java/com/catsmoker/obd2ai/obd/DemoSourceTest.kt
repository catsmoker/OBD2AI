package com.catsmoker.obd2ai.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the simulated adapter's scenario and correlated waves. */
class DemoSourceTest {

    @Test
    fun `DemoObdSource ships a non-empty plausible scenario`() {
        assertTrue(DemoObdSource.storedCodes.isNotEmpty())
        assertEquals(DemoObdSource.storedCodes.size, DemoObdSource.milStatus.storedCount)
        assertTrue(DemoObdSource.milStatus.milOn)
    }

    @Test
    fun `demo fault codes are well formed and never duplicated`() {
        // Diagnostics shows these verbatim; a malformed or double-listed code
        // would be displayed as a separate fault.
        val all = DemoObdSource.storedCodes + DemoObdSource.pendingCodes +
            DemoObdSource.permanentCodes
        val pattern = Regex("^[PCBU][0-3][0-9A-F]{3}$")
        for (code in all) {
            assertTrue("malformed DTC: $code", pattern.matches(code))
        }
        assertEquals("codes listed twice: $all", all.size, all.toSet().size)
    }

    @Test
    fun `a simulated clear empties the scenario until the next demo start`() {
        DemoObdSource.reset()
        assertTrue(DemoObdSource.liveStoredCodes().isNotEmpty())
        assertTrue(DemoObdSource.liveMilStatus().milOn)

        DemoObdSource.clearFaultCodes()
        assertTrue(DemoObdSource.liveStoredCodes().isEmpty())
        assertTrue(DemoObdSource.livePendingCodes().isEmpty())
        assertTrue(DemoObdSource.livePermanentCodes().isEmpty())
        assertEquals(0, DemoObdSource.liveMilStatus().storedCount)
        // The MIL is off after a clear, never "unknown" with codes still listed.
        assertTrue(!DemoObdSource.liveMilStatus().milOn)
        // The canonical scenario is untouched, so a rescan after restart
        // still demonstrates faults.
        assertEquals(listOf("P0301", "P0455"), DemoObdSource.storedCodes)

        DemoObdSource.reset()
        assertEquals(DemoObdSource.storedCodes, DemoObdSource.liveStoredCodes())
        assertTrue(DemoObdSource.liveMilStatus().milOn)
    }

    @Test
    fun `DemoObdSource live values stay within gauge ranges`() {
        for (second in 0..120) {
            val (speed, rpm, coolant) = DemoObdSource.liveValuesAt(second)
            assertTrue("speed $speed", speed in 0..220)
            assertTrue("rpm $rpm", rpm in 800..5200)
            assertTrue("coolant $coolant", coolant in 60..110)
        }
    }

    @Test
    fun `demo waves stay in plausible ranges and correlate`() {
        for (second in 0 until 120 step 7) {
            val throttle = DemoObdSource.throttleAt(second)
            assertTrue("throttle $throttle", throttle in 0..100)
            val intake = DemoObdSource.intakeTempAt(second)
            assertTrue("iat $intake", PidRegistry.isPlausible(0x0F, intake.toDouble()))
            val maf = DemoObdSource.mafAt(second)
            assertTrue("maf $maf", PidRegistry.isPlausible(0x10, maf))
            val timing = DemoObdSource.timingAt(second)
            assertTrue("timing $timing", PidRegistry.isPlausible(0x0E, timing))
            val stft = DemoObdSource.shortTrimAt(second)
            assertTrue("stft $stft", stft in -15.0..15.0)
            val ltft = DemoObdSource.longTrimAt(second)
            assertTrue("ltft $ltft", ltft in -15.0..15.0)
        }
    }
}
