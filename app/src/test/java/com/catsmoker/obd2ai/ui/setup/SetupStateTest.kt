package com.catsmoker.obd2ai.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the three-step setup state machine (pure companion logic). */
class SetupStateTest {

    @Test
    fun `next step advances and clamps at step 3`() {
        assertEquals(SetupState.STEP_2, SetupState.nextStep(SetupState.STEP_1))
        assertEquals(SetupState.STEP_3, SetupState.nextStep(SetupState.STEP_2))
        assertEquals(SetupState.STEP_3, SetupState.nextStep(SetupState.STEP_3))
    }

    @Test
    fun `prev step retreats and clamps at step 1`() {
        assertEquals(SetupState.STEP_1, SetupState.prevStep(SetupState.STEP_2))
        assertEquals(SetupState.STEP_2, SetupState.prevStep(SetupState.STEP_3))
        assertEquals(SetupState.STEP_1, SetupState.prevStep(SetupState.STEP_1))
    }

    @Test
    fun `step 1 gates on agreement`() {
        assertFalse(SetupState.canGoNext(false))
        assertTrue(SetupState.canGoNext(true))
    }

    @Test
    fun `progress fills in thirds across the three steps`() {
        assertEquals(33, SetupState.progressFor(SetupState.STEP_1))
        assertEquals(66, SetupState.progressFor(SetupState.STEP_2))
        assertEquals(100, SetupState.progressFor(SetupState.STEP_3))
        assertEquals(33, SetupState.progressFor(0))
        assertEquals(100, SetupState.progressFor(9))
    }
}
