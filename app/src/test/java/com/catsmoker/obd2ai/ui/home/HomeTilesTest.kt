package com.catsmoker.obd2ai.ui.home

import com.catsmoker.obd2ai.ui.common.ConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline review exists only for a saved document while disconnected; every
 * linked state already has the real Diagnostics tile.
 */
class HomeTilesTest {

    @Test
    fun `a disconnected home offers the saved scan`() {
        assertTrue(HomeTiles.showSavedDiagnostics(ConnectionState.DISCONNECTED, hasSavedScan = true))
    }

    @Test
    fun `nothing saved means no tile`() {
        assertFalse(HomeTiles.showSavedDiagnostics(ConnectionState.DISCONNECTED, hasSavedScan = false))
    }

    @Test
    fun `a linked home uses the normal diagnostics tile`() {
        assertFalse(HomeTiles.showSavedDiagnostics(ConnectionState.CONNECTED, hasSavedScan = true))
        assertFalse(HomeTiles.showSavedDiagnostics(ConnectionState.DEMO, hasSavedScan = true))
    }
}
