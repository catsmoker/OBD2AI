package com.catsmoker.obd2ai.ai

/**
 * Pure state rules for the Offline AI / Online AI master switches and their
 * voice/text outputs. Used by the Settings screen; the dashboard never calls
 * these. Kept here (not in a fragment companion) so Settings doesn't depend
 * on the dashboard. No Android imports — JVM-tested.
 */
object AiSwitchRules {

    /** Which assistant a master switch belongs to (mutual-exclusion helper). */
    enum class AiSystem { OFFLINE, ONLINE }

    /**
     * Mutual-exclusion state machine for the two master switches. Turning
     * one assistant ON turns the other OFF; turning one OFF leaves the
     * other unchanged (both may be OFF, never both ON).
     */
    fun resolveAiSwitches(
        toggled: AiSystem,
        checked: Boolean,
        offlineOn: Boolean,
        onlineOn: Boolean
    ): Pair<Boolean, Boolean> = when (toggled) {
        AiSystem.OFFLINE -> if (checked) Pair(true, false) else Pair(false, onlineOn)
        AiSystem.ONLINE -> if (checked) Pair(false, true) else Pair(offlineOn, false)
    }

    /**
     * Online AI must always keep at least one output: killing the last
     * enabled one revives the other instead ([toggledVoice] tells which
     * switch the user just flipped).
     */
    fun enforceOnlineOutput(toggledVoice: Boolean, voiceOn: Boolean, textOn: Boolean): Pair<Boolean, Boolean> =
        when {
            voiceOn || textOn -> Pair(voiceOn, textOn)
            toggledVoice -> Pair(false, true)
            else -> Pair(true, false)
        }

    /**
     * Same at-least-one-output rule for Offline AI: killing the last
     * enabled output revives the other instead.
     */
    fun enforceOfflineOutput(toggledVoice: Boolean, voiceOn: Boolean, textOn: Boolean): Pair<Boolean, Boolean> =
        enforceOnlineOutput(toggledVoice, voiceOn, textOn)
}
