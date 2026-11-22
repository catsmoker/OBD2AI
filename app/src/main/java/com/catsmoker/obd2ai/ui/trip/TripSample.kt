package com.catsmoker.obd2ai.ui.trip

/**
 * What the trip timer is allowed to believe about the car.
 *
 * `ObdDataHolder` keeps the last value it saw. Once live monitoring is off —
 * the driver left the dashboard, or the adapter link died — those strings are
 * stale, and sampling them would keep adding distance for a car nobody is
 * measuring. Unknown is the honest input: the trip keeps counting time and
 * idle time, but no distance it did not observe.
 *
 * Pure logic, tested.
 */
object TripSample {

    /** The literal the rest of the app uses for "no reading", never zero. */
    const val UNKNOWN = "--"

    /**
     * Both a running poll and demo mode produce genuine, current readings;
     * anything else leaves the flows holding a value nobody refreshed.
     */
    fun dataAllowed(monitoring: Boolean, demo: Boolean): Boolean = monitoring || demo

    fun speedText(monitoring: Boolean, text: String): String =
        if (monitoring) text else UNKNOWN

    fun rpmText(monitoring: Boolean, text: String): String =
        if (monitoring) text else UNKNOWN
}
