package com.catsmoker.obd2ai.ui.trip

/**
 * Whether the trip screen must explain why its distance is not moving.
 *
 * Live OBD polling only runs while the dashboard is open, so a running trip on
 * a real adapter counts time and idle time but adds no distance until the
 * driver goes back to Live data. Demo mode is exempt: it writes the flows
 * directly, so its numbers keep moving. Pure logic, tested.
 */
object TripNotice {

    fun showDistanceHint(running: Boolean, demo: Boolean): Boolean = running && !demo
}
