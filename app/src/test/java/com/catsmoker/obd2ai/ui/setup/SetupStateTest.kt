package com.catsmoker.obd2ai.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the two-step setup state machine (pure companion logic). */
class SetupStateTest {

    @Test
    fun `next step advances and clamps at step 2`() {
        assertEquals(SetupFragment.STEP_2, SetupFragment.nextStep(SetupFragment.STEP_1))
        assertEquals(SetupFragment.STEP_2, SetupFragment.nextStep(SetupFragment.STEP_2))
    }

    @Test
    fun `prev step retreats and clamps at step 1`() {
        assertEquals(SetupFragment.STEP_1, SetupFragment.prevStep(SetupFragment.STEP_2))
        assertEquals(SetupFragment.STEP_1, SetupFragment.prevStep(SetupFragment.STEP_1))
    }

    @Test
    fun `step 1 gates on agreement`() {
        assertFalse(SetupFragment.canGoNext(false))
        assertTrue(SetupFragment.canGoNext(true))
    }

    @Test
    fun `progress fills halfway on step 1 and full on step 2`() {
        assertEquals(50, SetupFragment.progressFor(SetupFragment.STEP_1))
        assertEquals(100, SetupFragment.progressFor(SetupFragment.STEP_2))
    }
}
