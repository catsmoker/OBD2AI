package com.catsmoker.obd2ai.diagnostics

import android.content.Context
import android.util.Log
import org.json.JSONObject

/** Persists assessed fault codes to internal file `dtc_results.json`. */
object DtcStore {
    const val FILE_NAME = "dtc_results.json"

    fun toJson(results: List<DtpCodeDTO>): String {
        val array = org.json.JSONArray()
        for (dto in results) {
            array.put(
                JSONObject()
                    .put("errorCode", dto.errorCode)
                    .put("severity", dto.severity.ordinal)
                    .put("title", dto.title)
                    .put("detail", dto.detail)
                    .put("implications", dto.implications)
                    .put("suggestedActions", org.json.JSONArray(dto.suggestedActions))
                    .put("offline", dto.offline)
                    .put("sources", org.json.JSONArray(dto.sources.map { it.name }))
            )
        }
        return array.toString()
    }

    fun fromJson(json: String): List<DtpCodeDTO> {
        return try {
            val array = org.json.JSONArray(json)
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
        } catch (e: Exception) {
            Log.e("DtcStore", "Failed to parse cached DTC results", e)
            emptyList()
        }
    }

    fun save(context: Context, results: List<DtpCodeDTO>) {
        runCatching {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(toJson(results).toByteArray())
            }
        }.onFailure { e -> Log.e("DtcStore", "Failed to cache DTC results", e) }
    }

    fun load(context: Context): List<DtpCodeDTO> {
        return runCatching {
            context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        }.mapCatching(::fromJson).getOrDefault(emptyList())
    }
}
