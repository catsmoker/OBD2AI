package com.catsmoker.obd2ai.ui.diagnostics

import com.catsmoker.obd2ai.diagnostics.DtpCodeDTO
import com.catsmoker.obd2ai.diagnostics.DtcSource
import com.catsmoker.obd2ai.diagnostics.ErrorSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The detail screen trusts the row the list handed over, but must never trust
 * it blindly: a stale handoff from a previous tap could open the wrong code.
 */
class DiagnosticsSelectionTest {

    private fun dto(code: String) = DtpCodeDTO(
        errorCode = code,
        severity = ErrorSeverity.MEDIUM,
        title = "title $code",
        detail = "detail",
        implications = "implications",
        suggestedActions = emptyList(),
        offline = true,
        sources = setOf(DtcSource.STORED)
    )

    @Test
    fun `handed over row wins when it is the same code`() {
        val handed = dto("P0301")
        val resolved = resolveSelectedCode("P0301", handed, listOf(dto("P0420")))
        assertEquals(handed, resolved)
    }

    @Test
    fun `stale handoff falls back to the cached scan`() {
        val cached = listOf(dto("P0301"), dto("P0420"))
        val resolved = resolveSelectedCode("P0420", dto("P0301"), cached)
        assertEquals("P0420", resolved?.errorCode)
    }

    @Test
    fun `unknown code resolves to null`() {
        assertNull(resolveSelectedCode("U0100", null, listOf(dto("P0301"))))
        assertNull(resolveSelectedCode("U0100", dto("P0301"), emptyList()))
    }
}
