package com.catsmoker.obd2ai.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the two-step setup state machine (pure companion logic). */
class SetupStateTest {

    @Test
    fun `next step advances and clamps at the last step`() {
        assertEquals(SetupState.STEP_2, SetupState.nextStep(SetupState.STEP_1))
        assertEquals(SetupState.STEP_2, SetupState.nextStep(SetupState.STEP_2))
    }

    @Test
    fun `prev step retreats and clamps at step 1`() {
        assertEquals(SetupState.STEP_1, SetupState.prevStep(SetupState.STEP_2))
        assertEquals(SetupState.STEP_1, SetupState.prevStep(SetupState.STEP_1))
    }

    @Test
    fun `step 1 gates on agreement`() {
        assertFalse(SetupState.canGoNext(false))
        assertTrue(SetupState.canGoNext(true))
    }

    @Test
    fun `progress fills in halves across the two steps`() {
        assertEquals(50, SetupState.progressFor(SetupState.STEP_1))
        assertEquals(100, SetupState.progressFor(SetupState.STEP_2))
        assertEquals(50, SetupState.progressFor(0))
        assertEquals(100, SetupState.progressFor(9))
    }

    @Test
    fun `there are exactly two steps`() {
        assertEquals(2, SetupState.TOTAL_STEPS)
    }
}
