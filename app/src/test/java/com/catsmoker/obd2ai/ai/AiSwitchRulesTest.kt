package com.catsmoker.obd2ai.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the AI master-switch and output state rules. */
class AiSwitchRulesTest {

    @Test
    fun `resolveAiSwitches keeps the assistants exclusive`() {
        // Turning one ON kills the other.
        assertEquals(
            Pair(true, false),
            AiSwitchRules.resolveAiSwitches(AiSwitchRules.AiSystem.OFFLINE, true, false, true)
        )
        assertEquals(
            Pair(false, true),
            AiSwitchRules.resolveAiSwitches(AiSwitchRules.AiSystem.ONLINE, true, true, false)
        )
        // Turning one OFF leaves the other unchanged; both may rest OFF.
        assertEquals(
            Pair(false, true),
            AiSwitchRules.resolveAiSwitches(AiSwitchRules.AiSystem.OFFLINE, false, true, true)
        )
        assertEquals(
            Pair(false, false),
            AiSwitchRules.resolveAiSwitches(AiSwitchRules.AiSystem.OFFLINE, false, true, false)
        )
        assertEquals(
            Pair(true, false),
            AiSwitchRules.resolveAiSwitches(AiSwitchRules.AiSystem.ONLINE, false, true, true)
        )
        assertEquals(
            Pair(false, false),
            AiSwitchRules.resolveAiSwitches(AiSwitchRules.AiSystem.ONLINE, false, false, true)
        )
    }

    @Test
    fun `enforceOnlineOutput always keeps one output`() {
        assertEquals(Pair(true, true), AiSwitchRules.enforceOnlineOutput(true, true, true))
        assertEquals(Pair(false, true), AiSwitchRules.enforceOnlineOutput(true, false, true))
        assertEquals(Pair(true, false), AiSwitchRules.enforceOnlineOutput(false, true, false))
        // Killing the last output revives the other one instead.
        assertEquals(Pair(false, true), AiSwitchRules.enforceOnlineOutput(true, false, false))
        assertEquals(Pair(true, false), AiSwitchRules.enforceOnlineOutput(false, false, false))
    }

    @Test
    fun `enforceOfflineOutput always keeps one output`() {
        assertEquals(Pair(true, true), AiSwitchRules.enforceOfflineOutput(true, true, true))
        assertEquals(Pair(false, true), AiSwitchRules.enforceOfflineOutput(true, false, true))
        assertEquals(Pair(true, false), AiSwitchRules.enforceOfflineOutput(false, true, false))
        assertEquals(Pair(false, true), AiSwitchRules.enforceOfflineOutput(true, false, false))
        assertEquals(Pair(true, false), AiSwitchRules.enforceOfflineOutput(false, false, false))
    }
}
