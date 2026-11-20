package com.catsmoker.obd2ai.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the three-step setup state machine (pure companion logic). */
class SetupStateTest {

    @Test
    fun `next step advances and clamps at step 3`() {
        assertEquals(SetupFragment.STEP_2, SetupFragment.nextStep(SetupFragment.STEP_1))
        assertEquals(SetupFragment.STEP_3, SetupFragment.nextStep(SetupFragment.STEP_2))
        assertEquals(SetupFragment.STEP_3, SetupFragment.nextStep(SetupFragment.STEP_3))
    }

    @Test
    fun `prev step retreats and clamps at step 1`() {
        assertEquals(SetupFragment.STEP_1, SetupFragment.prevStep(SetupFragment.STEP_2))
        assertEquals(SetupFragment.STEP_2, SetupFragment.prevStep(SetupFragment.STEP_3))
        assertEquals(SetupFragment.STEP_1, SetupFragment.prevStep(SetupFragment.STEP_1))
    }

    @Test
    fun `step 1 gates on agreement`() {
        assertFalse(SetupFragment.canGoNext(false))
        assertTrue(SetupFragment.canGoNext(true))
    }

    @Test
    fun `progress fills in thirds across the three steps`() {
        assertEquals(33, SetupFragment.progressFor(SetupFragment.STEP_1))
        assertEquals(66, SetupFragment.progressFor(SetupFragment.STEP_2))
        assertEquals(100, SetupFragment.progressFor(SetupFragment.STEP_3))
        assertEquals(33, SetupFragment.progressFor(0))
        assertEquals(100, SetupFragment.progressFor(9))
    }
}
