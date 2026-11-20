package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.obd.ObdHelper
import com.catsmoker.obd2ai.prefs.NavStyle
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared observable navigation inputs for the single-activity Compose app.
 * The chrome (dock/rail) and the Home screen both derive from these instead
 * of each fragment keeping its own copy:
 *
 * - [state]: resolved [ConnectionState] — refreshed after demo/connect/
 *   disconnect changes and by [ObdHelper.onConnectionLost].
 * - [cardsMode]: the chosen navigation style; Settings writes it live.
 */
object ConnectionStateStore {
    val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val cardsMode = MutableStateFlow(false)

    /** Re-read the resolved connection state from the live helper. */
    fun refresh(obdHelper: ObdHelper) {
        state.value = ConnectionState.resolve(obdHelper.demoMode, obdHelper.isConnected)
    }

    fun setCardsMode(tag: String?) {
        cardsMode.value = AppNav.isCardsMode(tag)
    }

    fun setNavStyle(style: NavStyle) {
        cardsMode.value = style == NavStyle.CARDS
    }
}
