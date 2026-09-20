package com.catsmoker.obd2ai.diagnostics

import android.graphics.Color
import android.util.Log

data class DtpCodeDTO(
    val errorCode: String,
    val severity: ErrorSeverity,
    val title: String,
    val detail: String,
    val implications: String,
    val suggestedActions: List<String>,
    /** True when produced by the offline fallback instead of the OpenAI assessment. */
    val offline: Boolean = false,
    /**
     * Which read modes reported this code (stored/pending/permanent).
     * Empty = unknown (e.g. older caches) — UI hides the pills then.
     */
    val sources: Set<DtcSource> = emptySet()
)

/** Which OBD read mode reported a fault code. */
enum class DtcSource {
    STORED,
    PENDING,
    PERMANENT;
}

/** One bundled generic fault-code explanation (offline, no network). */
data class DtcInfo(
    val code: String,
    val title: String,
    val detail: String,
    val implications: String,
    val actions: List<String>
)

/**
 * Bundled generic DTC knowledge (`res/raw/dtc_generic.json`, English only —
 * like the Easter-egg lines, translating a fault library is future work).
 * Covers common codes so the offline fallback says something specific
 * instead of "unknown system". Pure logic, tested.
 */
object DtcDictionary {
    /** Parses the bundled table; bad rows are skipped, garbage yields empty. */
    fun fromJson(json: String): Map<String, DtcInfo> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val code = obj.optString("code").trim().uppercase()
                val title = obj.optString("title").trim()
                if (code.isEmpty() || title.isEmpty()) return@mapNotNull null
                val actions = obj.optJSONArray("actions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList()
                code to DtcInfo(
                    code = code,
                    title = title,
                    detail = obj.optString("detail"),
                    implications = obj.optString("implications"),
                    actions = actions
                )
            }.toMap()
        } catch (e: Exception) {
            Log.e("DtcDictionary", "Failed to parse DTC dictionary", e)
            emptyMap()
        }
    }

    /**
     * Exact match first; cylinder misfires P0301..P0312 share the P0300
     * family entry. Returns null when nothing is known — callers fall back
     * to the generic system/origin text.
     */
    fun lookup(code: String, table: Map<String, DtcInfo>): DtcInfo? {
        val key = code.trim().uppercase()
        table[key]?.let { return it }
        val misfireCylinder = key.length == 5 && key[4].isDigit() &&
            (key.startsWith("P030") || key == "P0310" || key == "P0311" || key == "P0312")
        if (misfireCylinder) {
            return table["P0300"]
        }
        return null
    }
}

data class MilStatus(
    val milOn: Boolean,
    val storedCount: Int
)

enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromInt(value: Int) = when (value) {
            0 -> LOW
            1 -> MEDIUM
            2 -> HIGH
            else -> throw IllegalArgumentException("Invalid severity level: $value")
        }

        fun getColor(severity: ErrorSeverity) = when (severity) {
            MEDIUM -> Color.rgb(255, 165, 0)
            HIGH -> Color.RED
            else -> Color.GRAY
        }
    }
}

/**
 * Snapshot of sensor values stored by the ECU when a fault was set
 * (OBD mode 02). Every field is null when that reading is unavailable —
 * callers show "not available", never zeros. Pure parsing, tested.
 */
data class FreezeFrame(
    val dtc: String?,
    val rpm: Int?,
    val speedKmh: Int?,
    val coolantC: Int?,
    val loadPct: Int?
) {
    companion object {
        /**
         * Decodes one 2-byte DTC exactly like stored-code readers do:
         * bits 7-6 = P/C/B/U, bits 5-4 = 0/1/2/3, then three hex nibbles.
         * 0x0000 means "no code", so it yields null.
         */
        fun decodeDtcBytes(b0: Int, b1: Int): String? {
            if (b0 == 0 && b1 == 0) return null
            val prefix = when (b0 shr 6) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val digits = intArrayOf((b0 shr 4) and 0x03, b0 and 0x0F, (b1 shr 4) and 0x0F, b1 and 0x0F)
            return prefix + digits.joinToString("") { it.toString(16).uppercase() }
        }

        /** Hex payload after a mode-02 identifier ("420C" + N bytes), or null. */
        fun payloadAfter(raw: String?, identifier: String, bytes: Int): String? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.uppercase().filter { it.isLetterOrDigit() }
            val index = clean.indexOf(identifier)
            if (index < 0) return null
            val hex = clean.substring(index + identifier.length).take(bytes * 2)
            if (hex.length < bytes * 2) return null
            if (hex.any { it !in '0'..'9' && it !in 'A'..'F' }) return null
            return hex
        }

        /** Parses one mode-02 reply set; unknown/truncated replies yield nulls. */
        fun parse(
            dtcRaw: String?,
            rpmRaw: String?,
            speedRaw: String?,
            coolantRaw: String?,
            loadRaw: String?
        ): FreezeFrame {
            val dtc = payloadAfter(dtcRaw, "4202", 2)?.let { hex ->
                decodeDtcBytes(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16))
            }
            fun oneByte(raw: String?, id: String, f: (Int) -> Int): Int? {
                val hex = payloadAfter(raw, id, 1) ?: return null
                return f(hex.toInt(16))
            }
            fun twoBytes(raw: String?, id: String, f: (Int, Int) -> Int): Int? {
                val hex = payloadAfter(raw, id, 2) ?: return null
                return f(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16))
            }
            return FreezeFrame(
                dtc = dtc,
                rpm = twoBytes(rpmRaw, "420C") { a, b -> (a * 256 + b) / 4 },
                speedKmh = oneByte(speedRaw, "420D") { it },
                coolantC = oneByte(coolantRaw, "4205") { it - 40 },
                loadPct = oneByte(loadRaw, "4204") { it * 100 / 255 }
            )
        }
    }
}
