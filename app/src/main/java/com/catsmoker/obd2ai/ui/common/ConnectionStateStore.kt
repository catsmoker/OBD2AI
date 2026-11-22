package com.catsmoker.obd2ai.ui.common

import com.catsmoker.obd2ai.obd.ObdHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared observable connection state for the single-activity Compose app:
 * Home, Connect and Diagnostics all read it instead of each keeping a copy.
 * [state] is refreshed after demo/connect/disconnect changes and by
 * [ObdHelper.onConnectionLost].
 */
object ConnectionStateStore {
    val state = MutableStateFlow(ConnectionState.DISCONNECTED)

    private val _lossReason = MutableStateFlow<String?>(null)

    /**
     * Why a real adapter link died, so Home can explain the drop instead of
     * only flipping the badge to "Offline". Null unless [reportLoss] set it and
     * the app is still disconnected — the reason never outlives its failure.
     */
    val lossReason: StateFlow<String?> = _lossReason.asStateFlow()

    /** Re-read the resolved connection state from the live helper. */
    fun refresh(obdHelper: ObdHelper) {
        val resolved = ConnectionState.resolve(obdHelper.demoMode, obdHelper.isConnected)
        state.value = resolved
        // Reconnecting (or leaving demo) invalidates an old failure message.
        if (resolved != ConnectionState.DISCONNECTED) _lossReason.value = null
    }

    /** The transport died mid-session: show the link as gone, with the reason. */
    fun reportLoss(reason: String?) {
        state.value = ConnectionState.DISCONNECTED
        _lossReason.value = reason?.takeIf { it.isNotBlank() }
    }

    fun clearLoss() {
        _lossReason.value = null
    }
}
