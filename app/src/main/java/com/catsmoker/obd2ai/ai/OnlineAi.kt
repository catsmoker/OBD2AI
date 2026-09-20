package com.catsmoker.obd2ai.ai

import android.content.Context
import android.util.Log
import com.catsmoker.obd2ai.obd.SlowTelemetry
import com.catsmoker.obd2ai.prefs.PrefsKeys

// ONLINE AI — online diagnostic assistant (separate from the Offline AI cues).

/** How chatty the Online AI may be. */
enum class AiFrequency(val prefValue: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun fromPref(value: String?): AiFrequency =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }
}

/** Voice character of the Online AI's spoken replies. */
enum class AiPersonality(val prefValue: String) {
    NORMAL("normal"),
    FUNNY("funny"),
    PROFESSIONAL("professional");

    companion object {
        fun fromPref(value: String?): AiPersonality =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }

    fun styleLine(): String = when (this) {
        FUNNY -> "You may include at most one light joke; never joke about safety-critical faults."
        PROFESSIONAL -> "Be concise and formal. No jokes, no filler words."
        NORMAL -> "Be warm and plain-spoken, like a knowledgeable friend in the passenger seat."
    }
}

/** Urgency of a Online AI occasion; also drives queue priority and card time. */
enum class OnlineAiSeverity { INFO, WARNING, FAULT }

/** Readings the Online AI reasons about at one moment; null = unknown. */
data class OnlineAiSnapshot(
    val speedKmh: Int?,
    val rpm: Int?,
    val coolantC: Int?,
    val engineLoadPct: Int?,
    val voltageV: Float?,
    val fuelPct: Int?,
    val codes: List<String>
)

// =================================================================================
// ONLINE AI PERSONALITY — brain-generated companion reactions (Online AI only).
// Unlike Offline AI's static joke pools, these occasions only describe WHEN
// something may be said and in what style; the WORDS are generated live by
// the AI brain from the actual readings. Everything routes through the
// normal INFO-severity card + voice queue, so safety ordering, output
// switches, cooldowns and costs stay governed by the existing machinery.
// To add/moderate community lines, edit styleExamples below (or remove an
// occasion from OCCASIONS); the manager needs no changes.
// =================================================================================

object OnlineAiPersonality {
    enum class Rarity { COMMON, UNCOMMON, RARE, LEGENDARY }

    data class PersonalityOccasion(
        val key: String,
        val rarity: Rarity,
        val probability: Float,
        val cooldownMs: Long,
        val styleExamples: List<String>
    )

    const val SPEED_130 = "speed_130"
    const val SPEED_140 = "speed_140"
    const val SPEED_150 = "speed_150"
    const val RPM_HIGH = "rpm_high"
    const val RPM_SPIKE = "rpm_spike"
    const val COOLANT_WARM = "coolant_warm"
    const val FAULT_NEW = "fault_new"
    const val FAULT_RETURN = "fault_return"
    const val COMBO_SPEED_RPM = "combo_speed_rpm"
    const val COMBO_SPEED_FAULT = "combo_speed_fault"
    const val COMBO_RPM_COOLANT = "combo_rpm_coolant"
    const val COMBO_CALM_HOT = "combo_calm_hot"
    const val ULTRA = "ultra"

    val OCCASIONS: Map<String, PersonalityOccasion> = mapOf(
        SPEED_130 to PersonalityOccasion(
            SPEED_130, Rarity.COMMON, 0.30f, 10 * 60_000L,
            listOf(
                "Okay, I see you.",
                "Someone's in a hurry today.",
                "Yo, speed racer.",
                "Alright, we're moving.",
                "Okay… I noticed that.",
                "Bro really likes speed."
            )
        ),
        SPEED_140 to PersonalityOccasion(
            SPEED_140, Rarity.UNCOMMON, 0.30f, 15 * 60_000L,
            listOf(
                "Okay, we're getting serious now.",
                "I see what you're doing.",
                "Someone woke up today.",
                "Alright, racer.",
                "Okay, I respect the enthusiasm."
            )
        ),
        SPEED_150 to PersonalityOccasion(
            SPEED_150, Rarity.RARE, 0.20f, 25 * 60_000L,
            listOf(
                "Okay… we're really doing this.",
                "Ladies and gentlemen, we have a racer.",
                "And there he goes.",
                "I have officially noticed the speed.",
                "Okay, that escalated quickly.",
                "You really said full send."
            )
        ),
        RPM_HIGH to PersonalityOccasion(
            RPM_HIGH, Rarity.UNCOMMON, 0.30f, 12 * 60_000L,
            listOf(
                "Okay, she's singing.",
                "That engine is definitely awake.",
                "Someone's having fun with the throttle.",
                "Yeah, those revs are getting serious.",
                "The engine has entered the conversation.",
                "Okay… I heard that.",
                "She's really singing today."
            )
        ),
        RPM_SPIKE to PersonalityOccasion(
            RPM_SPIKE, Rarity.UNCOMMON, 0.35f, 12 * 60_000L,
            listOf(
                "Whoa, that was a jump.",
                "Okay, where did those revs come from?",
                "That escalated quickly.",
                "Someone got excited."
            )
        ),
        COOLANT_WARM to PersonalityOccasion(
            COOLANT_WARM, Rarity.UNCOMMON, 0.30f, 15 * 60_000L,
            listOf(
                "Yeah… she's getting a little warm.",
                "The engine is having a hot day.",
                "Things are getting toasty.",
                "Someone turned up the heat.",
                "I think the engine would like some air conditioning.",
                "Yeah… that's definitely warmer than I'd like.",
                "The engine is not exactly chilling right now."
            )
        ),
        FAULT_NEW to PersonalityOccasion(
            FAULT_NEW, Rarity.RARE, 0.50f, 20 * 60_000L,
            listOf(
                "Well… that's new.",
                "Uh, we have a new development.",
                "Your car just gave me something interesting.",
                "Okay, I wasn't expecting that.",
                "Looks like we have a new guest.",
                "The car has entered the chat.",
                "Well, that's one way to get my attention.",
                "Apparently, the car has something to say."
            )
        ),
        FAULT_RETURN to PersonalityOccasion(
            FAULT_RETURN, Rarity.RARE, 0.40f, 25 * 60_000L,
            listOf(
                "Oh… you again.",
                "I remember this one.",
                "We meet again.",
                "Apparently, we're doing this again.",
                "Yeah, this looks familiar.",
                "It came back.",
                "I was hoping we were done with this one."
            )
        ),
        COMBO_SPEED_RPM to PersonalityOccasion(
            COMBO_SPEED_RPM, Rarity.RARE, 0.30f, 20 * 60_000L,
            listOf(
                "Okay, somebody's having fun.",
                "The engine is definitely awake now.",
                "I see we're giving the engine some exercise."
            )
        ),
        COMBO_SPEED_FAULT to PersonalityOccasion(
            COMBO_SPEED_FAULT, Rarity.RARE, 0.40f, 20 * 60_000L,
            listOf(
                "Seriously? Right now?",
                "Okay, that's interesting timing.",
                "The car picked an interesting moment to complain."
            )
        ),
        COMBO_RPM_COOLANT to PersonalityOccasion(
            COMBO_RPM_COOLANT, Rarity.UNCOMMON, 0.30f, 15 * 60_000L,
            listOf(
                "We're revving it pretty hard, and the temperature noticed.",
                "The engine is working overtime today."
            )
        ),
        COMBO_CALM_HOT to PersonalityOccasion(
            COMBO_CALM_HOT, Rarity.COMMON, 0.25f, 12 * 60_000L,
            listOf(
                "At least she's keeping her cool.",
                "High revs, calm temperature. Interesting."
            )
        ),
        ULTRA to PersonalityOccasion(
            ULTRA, Rarity.LEGENDARY, 0.02f, 60 * 60_000L,
            listOf(
                "Okay… I definitely noticed that.",
                "I'm not saying anything. I'm just observing.",
                "Interesting choice.",
                "You know what? Fair enough.",
                "I was not expecting that.",
                "Okay, I'll remember that one.",
                "Well… that happened.",
                "I have questions.",
                "The car and I are going to need a meeting.",
                "I'm starting to understand your driving style.",
                "You really like keeping things interesting."
            )
        )
    )

    /** Minimum silence between any two personality reactions. */
    fun globalCooldownMs(frequency: AiFrequency): Long = when (frequency) {
        AiFrequency.LOW -> 20 * 60_000L
        AiFrequency.NORMAL -> 8 * 60_000L
        AiFrequency.HIGH -> 3 * 60_000L
    }

    /** Which rarities may speak at all (quiet modes keep only special moments). */
    fun allowsPersonality(frequency: AiFrequency, rarity: Rarity): Boolean = when (frequency) {
        AiFrequency.LOW -> rarity == Rarity.RARE || rarity == Rarity.LEGENDARY
        AiFrequency.NORMAL -> rarity != Rarity.COMMON
        AiFrequency.HIGH -> true
    }

    /** Speed Easter tier for personality: 0 = none, 1 = 130+, 2 = 140+, 3 = 150+. */
    fun speedTier(kmh: Int): Int = when {
        kmh >= 150 -> 3
        kmh >= 140 -> 2
        kmh >= 130 -> 1
        else -> 0
    }

    fun poolForSpeedTier(tier: Int): String = when (tier) {
        3 -> SPEED_150
        2 -> SPEED_140
        else -> SPEED_130
    }

    /** Sudden throttle stab: jump of at least 1500 RPM between reads. */
    fun isRpmSpike(prevRpm: Int?, rpm: Int): Boolean =
        prevRpm != null && rpm - prevRpm >= 1500

    /** Warm-but-not-alarming coolant band (the 110 °C+ watch stays diagnostic). */
    fun enteredWarmZone(prevC: Int?, nowC: Int?): Boolean {
        if (prevC == null || nowC == null) return false
        return prevC < 100 && nowC >= 100 && nowC < 110
    }

    fun comboSpeedRpm(speedKmh: Int?, rpm: Int?, shiftRpm: Int): Boolean =
        (speedKmh ?: 0) >= 130 && (rpm ?: 0) >= shiftRpm + 1500

    fun comboSpeedFault(speedKmh: Int?): Boolean = (speedKmh ?: 0) >= 120

    fun comboRpmCoolant(rpm: Int?, shiftRpm: Int, coolantC: Int?): Boolean =
        (rpm ?: 0) >= shiftRpm + 1500 && (coolantC ?: 0) >= 100

    fun calmHotRpm(rpm: Int?, shiftRpm: Int, coolantC: Int?): Boolean =
        (rpm ?: 0) >= shiftRpm + 1500 && coolantC != null && coolantC < 100

    /** Probability gate: [random01] must fall below [probability]. */
    fun rollEasterEgg(random01: Float, probability: Float): Boolean =
        random01 in 0f..<probability

    /** Normalized duplicate check so the same generated line never repeats. */
    fun isDuplicateOfRecent(text: String, recent: ArrayDeque<String>): Boolean {
        val norm = text.lowercase().filter { it.isLetterOrDigit() }
        if (norm.isEmpty()) return false
        return recent.any { it.lowercase().filter { c -> c.isLetterOrDigit() } == norm }
    }

    internal fun buildPersonalitySystem(personality: AiPersonality): String {
        val tone = when (personality) {
            AiPersonality.FUNNY -> "Lean into the humor; one light joke per reply at most."
            AiPersonality.PROFESSIONAL -> "Stay witty but restrained and professional. No jokes about faults."
            AiPersonality.NORMAL -> "Be warm with a light playful edge."
        }
        return "You are a funny car companion riding along, reacting to live OBD-II data. " +
            tone +
            " Keep every reply to 25 words or fewer, plain text, one or two short sentences," +
            " no markdown, no JSON, no lists, no emojis." +
            " Sound like a casual American car-enthusiast friend in the passenger seat:" +
            " confident, playful, occasionally sarcastic, never childish." +
            " No profanity, no insults at the driver, never pretend to be human." +
            " For TTS: spell measurements out in words (say 'one thirty' or 'a hundred" +
            " and twenty-one degrees', never digits with symbols like '130' or '121 °C')."
    }

    internal fun buildPersonalityUser(
        occasion: PersonalityOccasion,
        personality: AiPersonality,
        speedKmh: Int?,
        rpm: Int?,
        coolantC: Int?,
        engineLoadPct: Int?,
        codes: List<String>,
        detail: String
    ): String {
        fun fmt(value: Any?, unit: String) = value?.let { "$it $unit" } ?: "unknown"
        val lines = listOf(
            "Situation: $detail",
            "Live readings: speed ${fmt(speedKmh, "km/h")}, rpm ${fmt(rpm, "RPM")}," +
                " coolant ${fmt(coolantC, "degrees Celsius")}," +
                " engine load ${fmt(engineLoadPct, "percent")}," +
                " stored codes: ${codes.ifEmpty { listOf("none") }.joinToString(", ")}",
            "Your persona setting: ${personality.name.lowercase()}."
        )
        val examples = occasion.styleExamples.joinToString("\n") { "- $it" }
        return (lines + listOf(
            "Sound like these (same vibe, fresh words — never copy one exactly):",
            examples,
            "Reply with ONLY the reaction."
        )).joinToString("\n")
    }
}

/**
 * The Online AI's event/decision layer + brain caller. Offline AI (scripted MP3 cues)
 * is a separate system and stays untouched: this manager only turns
 * *meaningful changes* into AI-generated explanations.
 *
 * Constructed per Live Data screen; call the onX entry points from the
 * telemetry collectors (main thread) and [stop] when leaving. All network
 * failures back off silently — the dashboard and Offline AI keep working.
 */
class OnlineAiManager(
    private val context: Context,
    private val aiService: AiService,
    private val listener: Listener
) {
    interface Listener {
        fun onOnlineAiResponse(text: String, severity: OnlineAiSeverity)
    }

    companion object {
        const val SLOW_POLL_MS = 30_000L
        private const val FAILURE_BACKOFF_MS = 5 * 60_000L
        private const val RPM_HARD_SECONDS = 8L

        const val COOLANT_WATCH_C = 110
        const val COOLANT_WATCH_RELEASE_C = 105
        /** Shared with the voltage/fuel instruments: same interpretation everywhere. */
        const val VOLTAGE_LOW_V = 12.0f
        const val VOLTAGE_HIGH_V = 15.0f
        const val FUEL_LOW_PCT = 15
        private const val ENGINE_LOAD_HIGH_PCT = 95
        private const val RPM_HARD_OFFSET = 1500

        /** New vs cleared fault codes between two slow polls. */
        fun dtcDiff(known: Set<String>, current: List<String>): Pair<List<String>, List<String>> {
            val now = current.toSet()
            return Pair((now - known).sorted(), (known - now).sorted())
        }

        /** Early-warning latch below Offline AI's 120 °C critical alarm (110 trip / 105 release). */
        fun nextCoolantWatch(tempC: Int?, watching: Boolean): Boolean = when {
            watching -> (tempC ?: 0) >= COOLANT_WATCH_RELEASE_C
            else -> (tempC ?: 0) >= COOLANT_WATCH_C
        }

        fun voltageBad(voltageV: Float?): Boolean =
            voltageV != null && (voltageV < VOLTAGE_LOW_V || voltageV > VOLTAGE_HIGH_V)

        fun fuelLow(fuelPct: Int?): Boolean =
            fuelPct != null && fuelPct <= FUEL_LOW_PCT

        fun loadHigh(loadPct: Int?): Boolean =
            (loadPct ?: 0) >= ENGINE_LOAD_HIGH_PCT

        /** Frequency gates which severities may speak at all. */
        fun allows(severity: OnlineAiSeverity, frequency: AiFrequency): Boolean = when (frequency) {
            AiFrequency.LOW -> severity == OnlineAiSeverity.FAULT
            AiFrequency.NORMAL -> severity != OnlineAiSeverity.INFO
            AiFrequency.HIGH -> true
        }

        /** Repeat cooldown per severity, stretched (LOW) or halved (HIGH). */
        fun repeatCooldownMs(severity: OnlineAiSeverity, frequency: AiFrequency): Long {
            val base = when (severity) {
                OnlineAiSeverity.FAULT -> 10 * 60_000L
                OnlineAiSeverity.WARNING -> 10 * 60_000L
                OnlineAiSeverity.INFO -> 30 * 60_000L
            }
            return when (frequency) {
                AiFrequency.LOW -> base * 2
                AiFrequency.NORMAL -> base
                AiFrequency.HIGH -> base / 2
            }
        }

        internal fun buildSystemPrompt(personality: AiPersonality): String =
            "You are a smart car companion riding along, watching live OBD-II data. " +
                personality.styleLine() +
                " Keep every reply to 40 words or fewer, plain text, no markdown, no JSON, no lists." +
                " Match the mood to the severity: calm for info, concerned for warnings," +
                " serious for faults. Never be dramatic about normal readings."

        internal fun buildUserText(headline: String, severity: OnlineAiSeverity, snapshot: OnlineAiSnapshot, extra: String): String {
            fun fmt(value: Any?, unit: String) = value?.let { "$it $unit" } ?: "unknown"
            val lines = listOf(
                "Event: $headline",
                "Severity: ${severity.name.lowercase()}",
                "Speed: ${fmt(snapshot.speedKmh, "km/h")}",
                "RPM: ${fmt(snapshot.rpm, "RPM")}",
                "Coolant: ${fmt(snapshot.coolantC, "°C")}",
                "Engine load: ${fmt(snapshot.engineLoadPct, "%")}",
                "Battery: ${fmt(snapshot.voltageV, "V")}",
                "Fuel: ${fmt(snapshot.fuelPct, "%")}",
                "Stored codes: ${snapshot.codes.ifEmpty { listOf("none") }.joinToString(", ")}"
            )
            return (lines + if (extra.isNotBlank()) listOf("Note: $extra") else emptyList())
                .joinToString("\n")
        }
    }

    private fun prefs() =
        context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    private var lastSpeedKmh: Int? = null
    private var lastRpm: Int? = null
    private var lastCoolantC: Int? = null
    private var lastLoadPct: Int? = null
    private var lastVoltageV: Float? = null
    private var lastFuelPct: Int? = null

    private val announcedCodes = mutableSetOf<String>()
    private var codesSeeded = false
    private var coolantWatching = false
    private var rpmHardSinceMs: Long? = null
    private val lastSpokenMs = mutableMapOf<String, Long>()
    private var failureBackoffUntilMs: Long = 0L
    private var stopped = false
    // -- Personality state (independent cooldowns; failures never touch diagnostics)
    private var prevSpeedKmh: Int? = null
    private var prevRpm: Int? = null
    private var lastShiftRpm: Int = 3500
    private var lastPersonalityMs = 0L
    private val personalityCooldowns = mutableMapOf<String, Long>()
    private val recentPersonality = ArrayDeque<String>()
    private var personalityBackoffUntilMs: Long = 0L
    private val clearedCodes = mutableSetOf<String>()

    fun stop() {
        stopped = true
    }

    /** False when the brain cannot generate: cloud provider without a key,
     * or custom provider without a server URL. */
    fun isBrainConfigured(): Boolean {
        if (!aiService.hasApiKey()) return false
        val config = aiService.getConfig()
        return !(config.provider.showBaseUrl && config.baseUrl.isEmpty())
    }

    /** Latest speed only feeds the snapshot; overspeed voice stays Offline AI's job. */
    suspend fun onSpeed(kmh: Float) {
        val prev = prevSpeedKmh
        val now = kmh.toInt()
        lastSpeedKmh = now
        prevSpeedKmh = now
        if (stopped || prev == null) return
        val oldTier = OnlineAiPersonality.speedTier(prev)
        val newTier = OnlineAiPersonality.speedTier(now)
        if (newTier > oldTier && newTier > 0) {
            maybePersonality(
                OnlineAiPersonality.poolForSpeedTier(newTier),
                "Speed reached $now km/h."
            )
            if (OnlineAiPersonality.comboSpeedRpm(now, lastRpm, lastShiftRpm)) {
                maybePersonality(
                    OnlineAiPersonality.COMBO_SPEED_RPM,
                    "Speed $now km/h with RPM ${lastRpm ?: "unknown"}."
                )
            }
        }
    }

    /** Sustained hard running (well above the shift point) earns one WARNING. */
    suspend fun onRpm(rpm: Int, shiftRpm: Int) {
        val prev = prevRpm
        lastRpm = rpm
        prevRpm = rpm
        lastShiftRpm = shiftRpm
        if (stopped) return
        if (prev != null) {
            if (OnlineAiPersonality.isRpmSpike(prev, rpm)) {
                maybePersonality(
                    OnlineAiPersonality.RPM_SPIKE,
                    "RPM jumped from $prev to $rpm."
                )
            }
            if (prev < shiftRpm + 2000 && rpm >= shiftRpm + 2000) {
                maybePersonality(
                    OnlineAiPersonality.RPM_HIGH,
                    "RPM is at $rpm (shift point is $shiftRpm)."
                )
            }
            if (OnlineAiPersonality.comboSpeedRpm(lastSpeedKmh, rpm, shiftRpm) &&
                !OnlineAiPersonality.comboSpeedRpm(lastSpeedKmh, prev, shiftRpm)
            ) {
                // Entered the combo via RPM (speed-side entries are caught in onSpeed).
                maybePersonality(
                    OnlineAiPersonality.COMBO_SPEED_RPM,
                    "Speed ${lastSpeedKmh ?: "unknown"} km/h with RPM $rpm."
                )
            }
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (rpm > shiftRpm + RPM_HARD_OFFSET) {
            val since = rpmHardSinceMs ?: now.also { rpmHardSinceMs = it }
            if (now - since >= RPM_HARD_SECONDS * 1000L) {
                rpmHardSinceMs = now // re-arm the window instead of firing every read
                generate(
                    kind = "rpm_hard",
                    severity = OnlineAiSeverity.WARNING,
                    headline = "Engine has been running hard at $rpm RPM for a while",
                    extra = "Shift point is $shiftRpm RPM."
                )
            }
        } else {
            rpmHardSinceMs = null
        }
    }

    /** Early coolant watch; the 120 °C critical alarm stays Offline AI's scripted job. */
    suspend fun onCoolant(tempC: Int?) {
        val prev = lastCoolantC
        lastCoolantC = tempC
        if (stopped) return
        val watching = nextCoolantWatch(tempC, coolantWatching)
        if (watching && !coolantWatching) {
            generate(
                kind = "coolant_watch",
                severity = OnlineAiSeverity.WARNING,
                headline = "Coolant is climbing at ${tempC ?: "?"} °C",
                extra = "Below the critical alarm; advise watching it, not panic."
            )
        }
        coolantWatching = watching
        if (OnlineAiPersonality.enteredWarmZone(prev, tempC)) {
            maybePersonality(
                OnlineAiPersonality.COOLANT_WARM,
                "Coolant is at ${tempC ?: "?"} degrees and climbing."
            )
        }
        if (OnlineAiPersonality.comboRpmCoolant(lastRpm, lastShiftRpm, tempC) &&
            !OnlineAiPersonality.comboRpmCoolant(lastRpm, lastShiftRpm, prev)
        ) {
            // Entered the combo via temperature (RPM-side entries are caught in onRpm).
            maybePersonality(
                OnlineAiPersonality.COMBO_RPM_COOLANT,
                "RPM ${lastRpm ?: "unknown"} with coolant at ${tempC ?: "?"} degrees."
            )
        }
        if (OnlineAiPersonality.calmHotRpm(lastRpm, lastShiftRpm, tempC) &&
            !OnlineAiPersonality.calmHotRpm(lastRpm, lastShiftRpm, prev)
        ) {
            maybePersonality(
                OnlineAiPersonality.COMBO_CALM_HOT,
                "RPM ${lastRpm ?: "unknown"} but coolant is only ${tempC ?: "?"} degrees."
            )
        }
    }

    suspend fun onEngineLoad(loadPct: Int?) {
        lastLoadPct = loadPct
        if (stopped || !loadHigh(loadPct)) return
        generate(
            kind = "load_high",
            severity = OnlineAiSeverity.WARNING,
            headline = "Engine load very high at $loadPct%",
            extra = ""
        )
    }

    /** Slow poll: new/cleared codes, voltage and fuel. First poll also reports
     * stored codes once (a drive-start heads-up), then only changes speak. */
    suspend fun onSlowPoll(telemetry: SlowTelemetry) {
        lastVoltageV = telemetry.voltageV
        lastFuelPct = telemetry.fuelPct
        if (stopped) return
        val frequency = AiFrequency.fromPref(prefs().getString(PrefsKeys.AI_FREQUENCY, null))

        if (!codesSeeded) {
            codesSeeded = true
            announcedCodes.addAll(telemetry.codes)
            if (telemetry.codes.isNotEmpty()) {
                generate(
                    kind = "dtc_baseline",
                    severity = OnlineAiSeverity.FAULT,
                    headline = "Stored fault codes at drive start: ${telemetry.codes.joinToString(", ")}",
                    extra = "Brief heads-up on each code, then what to watch for."
                )
            }
        } else {
            val (newCodes, cleared) = dtcDiff(announcedCodes, telemetry.codes)
            clearedCodes.addAll(cleared)
            if (newCodes.isNotEmpty()) {
                announcedCodes.addAll(newCodes)
                generate(
                    kind = "new_dtc",
                    severity = OnlineAiSeverity.FAULT,
                    headline = "New fault code${if (newCodes.size > 1) "s" else ""} detected: ${newCodes.joinToString(", ")}",
                    extra = ""
                )
                val returned = newCodes.filter { it in clearedCodes }
                clearedCodes.removeAll(returned.toSet())
                val brandNew = newCodes - returned.toSet()
                if (returned.isNotEmpty()) {
                    maybePersonality(
                        OnlineAiPersonality.FAULT_RETURN,
                        "Code ${returned.joinToString(", ")} came back after clearing."
                    )
                }
                if (brandNew.isNotEmpty()) {
                    if (OnlineAiPersonality.comboSpeedFault(lastSpeedKmh)) {
                        maybePersonality(
                            OnlineAiPersonality.COMBO_SPEED_FAULT,
                            "New code ${brandNew.joinToString(", ")} while doing " +
                                "${lastSpeedKmh ?: "unknown"} km/h."
                        )
                    } else {
                        maybePersonality(
                            OnlineAiPersonality.FAULT_NEW,
                            "New fault code detected: ${brandNew.joinToString(", ")}."
                        )
                    }
                }
            }
            if (cleared.isNotEmpty()) {
                announcedCodes.removeAll(cleared.toSet())
                generate(
                    kind = "dtc_cleared",
                    severity = OnlineAiSeverity.INFO,
                    headline = "These codes cleared: ${cleared.joinToString(", ")}",
                    extra = "Confirm the fix looks real, in one short sentence."
                )
            }
        }

        if (voltageBad(telemetry.voltageV)) {
            generate(
                kind = "voltage",
                severity = OnlineAiSeverity.WARNING,
                headline = "Battery voltage abnormal at ${telemetry.voltageV} V",
                extra = "Below 12 V suggests charging trouble; above 15 V suggests overcharging."
            )
        }
        if (fuelLow(telemetry.fuelPct)) {
            generate(
                kind = "fuel_low",
                severity = if (frequency == AiFrequency.HIGH) OnlineAiSeverity.INFO else OnlineAiSeverity.WARNING,
                headline = "Fuel level low at ${telemetry.fuelPct}%",
                extra = ""
            )
        }
    }

    private suspend fun generate(kind: String, severity: OnlineAiSeverity, headline: String, extra: String) {
        if (stopped) return
        val prefs = prefs()
        if (!prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)) return
        val frequency = AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null))
        if (!allows(severity, frequency)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < failureBackoffUntilMs) return // fail silent, retry later — never nag
        val last = lastSpokenMs[kind] ?: 0L
        if (now - last < repeatCooldownMs(severity, frequency)) return
        val personality = AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null))
        val snapshot = OnlineAiSnapshot(
            lastSpeedKmh, lastRpm, lastCoolantC, lastLoadPct,
            lastVoltageV, lastFuelPct, announcedCodes.sorted()
        )
        val text = try {
            aiService.chatText(
                buildSystemPrompt(personality),
                buildUserText(headline, severity, snapshot, extra)
            )
        } catch (e: Exception) {
            Log.e("OnlineAiManager", "AI generation failed; backing off", e)
            failureBackoffUntilMs = now + FAILURE_BACKOFF_MS
            return
        }
        if (text.isBlank()) return
        lastSpokenMs[kind] = now
        listener.onOnlineAiResponse(text, severity)
    }

    /**
     * Personality reaction gate (Online AI only). Runs AFTER any diagnostic
     * handling at the same trigger so warnings always go first. Cooldowns are
     * recorded when an API call is committed to, so near-simultaneous
     * triggers from two collectors can't double-spend.
     */
    private suspend fun maybePersonality(occasionKey: String, detail: String) {
        if (stopped) return
        val prefs = prefs()
        if (!prefs.getBoolean(PrefsKeys.ONLINE_AI_ENABLED, true)) return
        if (!isBrainConfigured()) return
        val frequency = AiFrequency.fromPref(prefs.getString(PrefsKeys.AI_FREQUENCY, null))
        val personality = AiPersonality.fromPref(prefs.getString(PrefsKeys.AI_PERSONALITY, null))
        var occasion = OnlineAiPersonality.OCCASIONS[occasionKey] ?: return
        if (!OnlineAiPersonality.allowsPersonality(frequency, occasion.rarity)) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < personalityBackoffUntilMs) return
        if (now - lastPersonalityMs < OnlineAiPersonality.globalCooldownMs(frequency)) return
        if (now - (personalityCooldowns[occasion.key] ?: 0L) < occasion.cooldownMs) return
        if (!rollGate(occasion.probability)) return
        // Ultra-rare substitution: same slot, its own gates, still just one call.
        val ultra = OnlineAiPersonality.OCCASIONS[OnlineAiPersonality.ULTRA]!!
        if (OnlineAiPersonality.allowsPersonality(frequency, ultra.rarity) &&
            now - (personalityCooldowns[ultra.key] ?: 0L) >= ultra.cooldownMs &&
            rollGate(ultra.probability)
        ) {
            occasion = ultra
        }
        lastPersonalityMs = now
        personalityCooldowns[occasion.key] = now
        val snapshot = OnlineAiSnapshot(
            lastSpeedKmh, lastRpm, lastCoolantC, lastLoadPct,
            lastVoltageV, lastFuelPct, announcedCodes.sorted()
        )
        val (system, user) = Pair(
            OnlineAiPersonality.buildPersonalitySystem(personality),
            OnlineAiPersonality.buildPersonalityUser(
                occasion, personality,
                snapshot.speedKmh, snapshot.rpm, snapshot.coolantC, snapshot.engineLoadPct,
                snapshot.codes, detail
            )
        )
        val text = try {
            aiService.chatText(system, user, 80)
        } catch (e: Exception) {
            Log.e("OnlineAiManager", "Personality generation failed; backing off", e)
            personalityBackoffUntilMs = now + FAILURE_BACKOFF_MS
            return
        }
        if (text.isBlank()) return
        if (OnlineAiPersonality.isDuplicateOfRecent(text, recentPersonality)) return
        recentPersonality.addLast(text.lowercase().filter { it.isLetterOrDigit() })
        while (recentPersonality.size > 8) recentPersonality.removeFirst()
        // INFO severity: queued behind everything, preempted by anything.
        listener.onOnlineAiResponse(text, OnlineAiSeverity.INFO)
    }

    private fun rollGate(probability: Float): Boolean =
        OnlineAiPersonality.rollEasterEgg(kotlin.random.Random.Default.nextFloat(), probability)
}
