package com.catsmoker.obd2ai.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the `dtc_results.json` assessment cache. */
class DtcStoreTest {

    @Test
    fun `DtcStore round-trips assessed results`() {
        val results = listOf(
            DtpCodeDTO("P0301", ErrorSeverity.HIGH, "Misfire", "detail", "impl", listOf("a", "b"), offline = false),
            DtpCodeDTO("C1234", ErrorSeverity.MEDIUM, "Chassis fault", "detail", "impl", listOf("x"), offline = true)
        )

        val restored = DtcStore.fromJson(DtcStore.toJson(results))

        assertEquals(results, restored)
    }

    @Test
    fun `a saved scan remembers when it was written`() {
        val dto = DtpCodeDTO("P0301", ErrorSeverity.HIGH, "T", "D", "I", listOf("A"))
        val scan = DtcStore.fromJsonScan(DtcStore.toJson(listOf(dto), savedAtMs = 1_700_000_000_000L))
        assertEquals(1_700_000_000_000L, scan.savedAtMs)
        assertEquals("P0301", scan.codes.single().errorCode)
    }

    @Test
    fun `a legacy bare array still loads, without a timestamp`() {
        val dto = DtpCodeDTO("P0420", ErrorSeverity.LOW, "T", "D", "I", emptyList())
        // Files written before timestamps existed were the array itself.
        val legacy = """[{"errorCode":"P0420","severity":3,"title":"T","detail":"D","implications":"I","suggestedActions":[],"offline":false,"sources":[]}]"""
        val scan = DtcStore.fromJsonScan(legacy)
        assertEquals(null, scan.savedAtMs)
        assertEquals(dto.errorCode, scan.codes.single().errorCode)
    }

    @Test
    fun `DtcStore returns empty list for corrupt cache`() {
        assertTrue(DtcStore.fromJson("not json at all").isEmpty())
        assertTrue(DtcStore.fromJson("[]").isEmpty())
    }

    @Test
    fun `DtcStore skips entries without a code`() {
        val json = """[{"title": "no code here"}]"""
        assertTrue(DtcStore.fromJson(json).isEmpty())
    }

    @Test
    fun `DtcStore round-trips code sources`() {
        val dto = DtpCodeDTO(
            "P0301", ErrorSeverity.HIGH, "T", "D", "I", listOf("A"),
            sources = setOf(DtcSource.STORED, DtcSource.PENDING)
        )
        val parsed = DtcStore.fromJson(DtcStore.toJson(listOf(dto)))
        assertEquals(setOf(DtcSource.STORED, DtcSource.PENDING), parsed.single().sources)
    }

    @Test
    fun `DtcStore ignores unknown future sources`() {
        val dto = DtpCodeDTO("P0301", ErrorSeverity.LOW, "T", "D", "I", emptyList())
        val json = DtcStore.toJson(listOf(dto)).replace("\"sources\":[]", "\"sources\":[\"FUTURE\"]")
        assertTrue(DtcStore.fromJson(json).single().sources.isEmpty())
    }
}
