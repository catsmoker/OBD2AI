package com.catsmoker.obd2ai.vehicle

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Stores the trip in internal `trip_state.json` so a running trip survives the
 * process being killed (low memory, app swipe, crash). Deliberately tiny and
 * plain: a handful of numbers, no user content, deleted on Reset.
 *
 * File IO is blocking — callers use `Dispatchers.IO`. The JSON helpers are pure
 * so they run in JVM tests.
 */
object TripStore {

    const val FILE_NAME = "trip_state.json"

    fun toJson(state: TripState): String = JSONObject()
        .put("startMs", state.startMs ?: JSONObject.NULL)
        .put("lastMs", state.lastMs ?: JSONObject.NULL)
        .put("elapsedMs", state.elapsedMs)
        .put("distanceKm", state.distanceKm)
        .put("maxKmh", state.maxKmh)
        .put("maxRpm", state.maxRpm)
        .put("idleMs", state.idleMs)
        .put("driveMs", state.driveMs)
        .toString()

    /** Null when the payload is missing or unreadable — never a zeroed trip. */
    fun fromJson(json: String?): TripState? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            if (!obj.has("elapsedMs")) return null
            TripState(
                startMs = obj.optLongOrNull("startMs"),
                lastMs = obj.optLongOrNull("lastMs"),
                elapsedMs = obj.optLong("elapsedMs").coerceAtLeast(0L),
                distanceKm = obj.optDouble("distanceKm", 0.0).coerceAtLeast(0.0),
                maxKmh = obj.optDouble("maxKmh", 0.0).coerceAtLeast(0.0),
                maxRpm = obj.optInt("maxRpm", 0).coerceAtLeast(0),
                idleMs = obj.optLong("idleMs").coerceAtLeast(0L),
                driveMs = obj.optLong("driveMs").coerceAtLeast(0L)
            )
        } catch (e: Exception) {
            Log.e("TripStore", "Failed to parse stored trip", e)
            null
        }
    }

    fun load(context: Context): TripState? = runCatching {
        fromJson(file(context).takeIf { it.exists() }?.readText())
    }.getOrNull()

    fun save(context: Context, state: TripState) {
        runCatching { file(context).writeText(toJson(state)) }
            .onFailure { Log.e("TripStore", "Failed to store trip", it) }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)
}
