package com.catsmoker.obd2ai.diagnostics

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** A scan read back from disk: the codes and when they were saved (null = legacy file). */
data class SavedScan(val codes: List<DtpCodeDTO>, val savedAtMs: Long?)

/**
 * Persists assessed fault codes to internal file `dtc_results.json`.
 *
 * The saved assessment is the expensive part: each code may have been explained
 * by a paid AI call, so losing it on process death would mean paying twice. The
 * file now also records when it was written, so a reopened overview can say the
 * list is a saved scan instead of pretending it is live. Files written by older
 * versions are a bare array and still parse, with no timestamp.
 */
object DtcStore {
    const val FILE_NAME = "dtc_results.json"

    /** Parsed copy of the file; cleared by every write, so it cannot go stale. */
    private val cache = ScanCache()

    fun toJson(results: List<DtpCodeDTO>, savedAtMs: Long? = null): String {
        val array = JSONArray()
        for (dto in results) {
            array.put(
                JSONObject()
                    .put("errorCode", dto.errorCode)
                    .put("severity", dto.severity.ordinal)
                    .put("title", dto.title)
                    .put("detail", dto.detail)
                    .put("implications", dto.implications)
                    .put("suggestedActions", JSONArray(dto.suggestedActions))
                    .put("offline", dto.offline)
                    .put("sources", JSONArray(dto.sources.map { it.name }))
            )
        }
        val root = JSONObject().put("codes", array)
        if (savedAtMs != null) root.put("savedAtMs", savedAtMs)
        return root.toString()
    }

    fun fromJson(json: String): List<DtpCodeDTO> = fromJsonScan(json).codes

    /** Parses either the current object shape or the legacy bare array. */
    fun fromJsonScan(json: String): SavedScan {
        return try {
            val trimmed = json.trim()
            val root = JSONObject(trimmed)
            val array = root.optJSONArray("codes") ?: JSONArray()
            val savedAt = root.optLong("savedAtMs", 0L).takeIf { it > 0L }
            SavedScan(parseCodes(array), savedAt)
        } catch (_: Exception) {
            // Legacy file: the whole document is the array.
            try {
                SavedScan(parseCodes(JSONArray(json.trim())), null)
            } catch (e: Exception) {
                Log.e("DtcStore", "Failed to parse cached DTC results", e)
                SavedScan(emptyList(), null)
            }
        }
    }

    private fun parseCodes(array: JSONArray): List<DtpCodeDTO> =
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val code = obj.optString("errorCode").ifBlank { return@mapNotNull null }
            val severity = runCatching { ErrorSeverity.fromInt(obj.optInt("severity")) }
                .getOrDefault(ErrorSeverity.MEDIUM)
            val actions = obj.optJSONArray("suggestedActions")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList()
            // Unknown names (e.g. written by a newer app version) are
            // skipped, never fatal: sources are hints, not data.
            val sources = obj.optJSONArray("sources")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    runCatching { DtcSource.valueOf(arr.optString(idx)) }.getOrNull()
                }.toSet()
            } ?: emptySet()
            DtpCodeDTO(
                errorCode = code,
                severity = severity,
                title = obj.optString("title", code),
                detail = obj.optString("detail"),
                implications = obj.optString("implications"),
                suggestedActions = actions,
                offline = obj.optBoolean("offline", false),
                sources = sources
            )
        }

    fun save(context: Context, results: List<DtpCodeDTO>, savedAtMs: Long? = null) {
        runCatching {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(toJson(results, savedAtMs).toByteArray())
            }
        }.onSuccess {
            // This process just wrote the file, so this is the truth.
            cache.put(SavedScan(results, savedAtMs))
        }.onFailure { e ->
            // The file may be half-written or untouched: never trust the cache.
            cache.clear()
            Log.e("DtcStore", "Failed to cache DTC results", e)
        }
    }

    fun load(context: Context): List<DtpCodeDTO> = loadScan(context).codes

    fun loadScan(context: Context): SavedScan {
        cache.get()?.let { return it }
        val scan = runCatching {
            context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        }.mapCatching(::fromJsonScan).getOrDefault(SavedScan(emptyList(), null))
        cache.put(scan)
        return scan
    }

    /** Drops the parsed copy, e.g. after a tool or test rewrote the file. */
    fun invalidateCache() {
        cache.clear()
    }
}
