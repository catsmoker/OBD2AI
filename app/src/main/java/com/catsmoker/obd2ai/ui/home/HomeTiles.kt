package com.catsmoker.obd2ai.ui.home

import com.catsmoker.obd2ai.ui.common.ConnectionState

/**
 * When a saved fault-code scan may be opened from Home.
 *
 * The tile grid is hidden while disconnected, which used to put yesterday's
 * codes out of reach exactly when the car is not there. A saved scan is a
 * document, not a live reading, so it may be reviewed offline — but only then:
 * while connected or in demo the normal Diagnostics tile already leads to a
 * real read. Pure logic, tested.
 */
object HomeTiles {

    fun showSavedDiagnostics(state: ConnectionState, hasSavedScan: Boolean): Boolean =
        state == ConnectionState.DISCONNECTED && hasSavedScan
}
