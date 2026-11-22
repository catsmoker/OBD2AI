package com.catsmoker.obd2ai.ui.connect

/**
 * What kind of failure a connect attempt hit, so the screen can say "could not
 * reach it" instead of pasting a socket message at the driver. Pure: the raw
 * message from `ObdHelper`/`BluetoothHelper` is classified by its known
 * prefixes, and anything unrecognized is reported as generic rather than
 * guessed at.
 */
enum class ConnectFailureKind {
    /** The socket never opened: wrong IP/port, adapter off, out of range. */
    UNREACHABLE,

    /** Bluetooth forgot the adapter (unpaired, or a stale paired entry). */
    NOT_PAIRED,

    /** No Bluetooth adapter at all. */
    BLUETOOTH_UNAVAILABLE,

    /** Permission problems, invalid input, or anything we do not recognize. */
    GENERIC;

    companion object {
        fun classify(message: String?): ConnectFailureKind {
            val text = message?.trim()?.lowercase() ?: return GENERIC
            return when {
                text.startsWith("failed to connect to") -> UNREACHABLE
                // ObdHelper's own message once the transport was torn down.
                text.contains("connection not established") -> UNREACHABLE
                // Mid-session socket failures: the link is gone, not slow.
                text.contains("broken pipe") -> UNREACHABLE
                text.contains("socket closed") -> UNREACHABLE
                text.contains("connection reset") -> UNREACHABLE
                text.contains("device not found") -> NOT_PAIRED
                text.contains("bluetooth adapter is not available") -> BLUETOOTH_UNAVAILABLE
                else -> GENERIC
            }
        }
    }
}
