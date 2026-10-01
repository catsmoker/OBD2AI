package com.catsmoker.obd2ai.ui.common

/**
 * Which world the app currently lives in, derived from [ObdHelper]'s
 * existing `demoMode` / `isConnected` — no parallel state model. The menu
 * renders from this: disconnected shows only connection-independent
 * actions; connected/demo additionally show the four data screens.
 * Pure logic, tested.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTED,
    DEMO;

    companion object {
        fun resolve(demoMode: Boolean, isConnected: Boolean): ConnectionState = when {
            demoMode -> DEMO
            isConnected -> CONNECTED
            else -> DISCONNECTED
        }
    }
}
