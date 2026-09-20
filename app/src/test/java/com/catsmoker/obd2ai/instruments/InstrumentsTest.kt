package com.catsmoker.obd2ai.instruments

import com.catsmoker.obd2ai.ai.OnlineAiManager
import com.catsmoker.obd2ai.speedometers.SpeedometerHost
import com.catsmoker.obd2ai.speedometers.SpeedometerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the cluster instruments' pure zone/state math. */
class InstrumentsTest {

    @Test
    fun `tacho face covers every speedometer style`() {
        for (style in SpeedometerStyle.entries) {
            val face = SpeedometerHost.tachoFaceFor(style)
            assertTrue("$style has no face", face in TachoFace.entries)
        }
    }

    @Test
    fun `tacho faces match their style families`() {
        assertEquals(TachoFace.NEEDLE, SpeedometerHost.tachoFaceFor(SpeedometerStyle.CLASSIC))
        assertEquals(TachoFace.SEGMENT, SpeedometerHost.tachoFaceFor(SpeedometerStyle.SPORT))
        assertEquals(TachoFace.NEON, SpeedometerHost.tachoFaceFor(SpeedometerStyle.FUTURISTIC))
        assertEquals(TachoFace.MINIMAL, SpeedometerHost.tachoFaceFor(SpeedometerStyle.MINIMAL))
        assertEquals(TachoFace.DIGITAL, SpeedometerHost.tachoFaceFor(SpeedometerStyle.DIGITAL))
    }

    @Test
    fun `shift band starts at the shift point as a fraction`() {
        assertEquals(0.4375f, RpmGaugeView.shiftBandStartFrac(3500), 0.001f)
        assertEquals(1f, RpmGaugeView.shiftBandStartFrac(9000), 0.001f)
        assertEquals(0f, RpmGaugeView.shiftBandStartFrac(-100), 0.001f)
    }

    @Test
    fun `coolant zones reuse warn and critical thresholds`() {
        assertEquals(CoolantZone.COLD, CoolantGaugeView.zoneFor(60, 105, 120))
        assertEquals(CoolantZone.NORMAL, CoolantGaugeView.zoneFor(87, 105, 120))
        assertEquals(CoolantZone.NORMAL, CoolantGaugeView.zoneFor(104, 105, 120))
        assertEquals(CoolantZone.WARN, CoolantGaugeView.zoneFor(105, 105, 120))
        assertEquals(CoolantZone.WARN, CoolantGaugeView.zoneFor(119, 105, 120))
        assertEquals(CoolantZone.CRITICAL, CoolantGaugeView.zoneFor(120, 105, 120))
        assertEquals(CoolantZone.CRITICAL, CoolantGaugeView.zoneFor(140, 105, 120))
    }

    @Test
    fun `coolant custom thresholds move the warn band`() {
        // A user warn threshold of 95 must move the band with it.
        assertEquals(CoolantZone.WARN, CoolantGaugeView.zoneFor(97, 95, 120))
        assertEquals(CoolantZone.NORMAL, CoolantGaugeView.zoneFor(90, 95, 120))
    }

    @Test
    fun `voltage states match the online AI interpretation`() {
        assertEquals(VoltState.UNKNOWN, VoltageGaugeView.stateFor(null))
        assertEquals(VoltState.NORMAL, VoltageGaugeView.stateFor(13.8f))
        assertEquals(VoltState.NORMAL, VoltageGaugeView.stateFor(12.0f))
        assertEquals(VoltState.NORMAL, VoltageGaugeView.stateFor(15.0f))
        assertEquals(VoltState.BAD, VoltageGaugeView.stateFor(11.9f))
        assertEquals(VoltState.BAD, VoltageGaugeView.stateFor(15.1f))
        // Instrument and AI must never disagree.
        for (v in listOf(11.5f, 12.0f, 13.8f, 15.0f, 15.5f)) {
            assertEquals(
                "voltage $v",
                OnlineAiManager.voltageBad(v),
                VoltageGaugeView.stateFor(v) == VoltState.BAD
            )
        }
    }

    @Test
    fun `fuel states match the online AI interpretation`() {
        assertEquals(FuelState.UNKNOWN, FuelGaugeView.stateFor(null))
        assertEquals(FuelState.NORMAL, FuelGaugeView.stateFor(62))
        assertEquals(FuelState.NORMAL, FuelGaugeView.stateFor(16))
        assertEquals(FuelState.LOW, FuelGaugeView.stateFor(15))
        assertEquals(FuelState.LOW, FuelGaugeView.stateFor(0))
        for (pct in listOf(0, 5, 15, 16, 62, 100)) {
            assertEquals(
                "fuel $pct",
                OnlineAiManager.fuelLow(pct),
                FuelGaugeView.stateFor(pct) == FuelState.LOW
            )
        }
    }
}
