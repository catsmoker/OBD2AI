package com.catsmoker.obd2ai.diagnostics

import com.catsmoker.obd2ai.obd.DemoObdSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for severity mapping, the bundled DTC dictionary and freeze-frame parsing. */
class DiagnosticModelsTest {

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

    @Test
    fun `dtc dictionary parses entries and skips bad rows`() {
        val table = DtcDictionary.fromJson(
            """[{"code":"P0420","title":"T","detail":"D","implications":"I","actions":["A"]},
               |{"code":"","title":"bad"},
               |{"nope":true}]""".trimMargin()
        )
        assertEquals(1, table.size)
        assertEquals("T", table.getValue("P0420").title)
    }

    @Test
    fun `dtc dictionary never throws on garbage`() {
        assertTrue(DtcDictionary.fromJson("not json{{").isEmpty())
        assertTrue(DtcDictionary.fromJson("").isEmpty())
    }

    @Test
    fun `dtc dictionary lookup covers exact and misfire family`() {
        val table = DtcDictionary.fromJson(
            """[{"code":"P0300","title":"Misfire","detail":"D","implications":"I","actions":[]}]"""
        )
        assertEquals("Misfire", DtcDictionary.lookup("P0301", table)?.title)
        assertEquals("Misfire", DtcDictionary.lookup("p0312", table)?.title)
        assertEquals(null, DtcDictionary.lookup("P0420", table))
    }

    @Test
    fun `bundled dictionary parses and covers the demo codes`() {
        val raw = java.io.File("src/main/res/raw/dtc_generic.json").readText()
        val table = DtcDictionary.fromJson(raw)
        assertTrue("dictionary too small: ${table.size}", table.size >= 20)
        for (code in DemoObdSource.storedCodes + DemoObdSource.pendingCodes) {
            assertTrue("no entry for $code", DtcDictionary.lookup(code, table) != null)
        }
    }

    @Test
    fun `freeze dtc bytes decode like stored codes`() {
        assertEquals("P0301", FreezeFrame.decodeDtcBytes(0x03, 0x01))
        assertEquals("U0123", FreezeFrame.decodeDtcBytes(0xC1, 0x23))
        assertEquals(null, FreezeFrame.decodeDtcBytes(0x00, 0x00))
    }

    @Test
    fun `freeze frame parses a full mode 02 reply set`() {
        val frame = FreezeFrame.parse(
            dtcRaw = "42020301",
            rpmRaw = "420C1AF8",
            speedRaw = "420D1F",
            coolantRaw = "42055A",
            loadRaw = "420482"
        )
        assertEquals("P0301", frame.dtc)
        assertEquals(1726, frame.rpm)
        assertEquals(31, frame.speedKmh)
        assertEquals(50, frame.coolantC)
        assertEquals(50, frame.loadPct)
    }

    @Test
    fun `freeze frame yields nulls for missing or truncated replies`() {
        val frame = FreezeFrame.parse("NO DATA", "420C1A", null, "4205ZZ", "")
        assertEquals(null, frame.dtc)
        assertEquals(null, frame.rpm)
        assertEquals(null, frame.speedKmh)
        assertEquals(null, frame.coolantC)
        assertEquals(null, frame.loadPct)
    }
}
