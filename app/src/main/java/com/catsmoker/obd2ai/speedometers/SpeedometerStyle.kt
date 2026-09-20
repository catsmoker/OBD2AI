package com.catsmoker.obd2ai.speedometers

import com.catsmoker.obd2ai.R
import com.catsmoker.obd2ai.prefs.PrefsKeys
import kotlin.math.roundToInt

/**
 * The selectable driving-display styles. The dashboard never hard-codes one
 * gauge: it resolves the user's pick via [fromId] (persisted under
 * [PrefsKeys.SPEEDOMETER_STYLE]), builds it with `SpeedometerHost`, and feeds
 * shared vehicle data through `BaseSpeedometerView.setSpeed`/`setRpm`.
 *
 * Adding another style later = one entry here + one `BaseSpeedometerView`
 * subclass + one `when` branch in `SpeedometerHost`. Nothing else moves.
 */
enum class SpeedometerStyle(
    val id: String,
    val titleRes: Int,
    val descRes: Int
) {
    DIGITAL("digital", R.string.speedometer_digital, R.string.speedometer_digital_desc),
    CLASSIC("classic", R.string.speedometer_classic, R.string.speedometer_classic_desc),
    MINIMAL("minimal", R.string.speedometer_minimal, R.string.speedometer_minimal_desc),
    SPORT("sport", R.string.speedometer_sport, R.string.speedometer_sport_desc),
    FUTURISTIC("futuristic", R.string.speedometer_futuristic, R.string.speedometer_futuristic_desc),
    PERFORMANCE("performance", R.string.speedometer_performance, R.string.speedometer_performance_desc),
    AUTOMOTIVE("automotive", R.string.speedometer_automotive, R.string.speedometer_automotive_desc),
    RPM_FOCUSED("rpm_focused", R.string.speedometer_rpm_focused, R.string.speedometer_rpm_focused_desc);

    companion object {
        /** Circular-dial geometry shared by the arc styles (degrees). */
        const val START_ANGLE = 140f
        const val SWEEP_ANGLE = 260f
        const val MIN_ANGLE = 220f
        const val MAX_ANGLE = -40f

        /** Sport arc: top-half 180-degree sweep (degrees). */
        const val SPORT_START_ANGLE = 180f
        const val SPORT_SWEEP_ANGLE = 180f

        fun fromId(id: String?): SpeedometerStyle =
            entries.firstOrNull { it.id == id } ?: CLASSIC

        /**
         * Carousel position with wrap-around (last → first, first → last),
         * so gallery navigation never needs boundary checks. Pure, tested.
         */
        fun wrappedIndex(index: Int): Int {
            val size = entries.size
            return ((index % size) + size) % size
        }

        fun clampSpeed(kmh: Float, max: Float): Float =
            kmh.coerceIn(0f, max.coerceAtLeast(1f))

        fun clampRpm(rpm: Int, rpmMax: Int): Int =
            rpm.coerceIn(0, rpmMax.coerceAtLeast(1))

        /** 0..1 needle position; unknown/empty reads rest at zero, never invented. */
        fun fraction(value: Float, max: Float): Float =
            if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)

        fun mapSpeedToAngle(speed: Float, max: Float): Float =
            MIN_ANGLE + ((MAX_ANGLE - MIN_ANGLE) * fraction(speed, max))

        fun mapAngleToSpeed(angle: Float, max: Float): Float =
            ((angle - MIN_ANGLE) / (MAX_ANGLE - MIN_ANGLE) * max).coerceIn(0f, max)

        /** RPM warn tier around the shift point: 0 = calm, 1 = warm, 2 = shift. */
        fun rpmZone(rpm: Int, shiftRpm: Int = PrefsKeys.DEFAULT_SHIFT_RPM): Int = when {
            rpm < maxOf(shiftRpm - 1000, 1000) -> 0
            rpm < maxOf(shiftRpm, 1000 + 500) -> 1
            else -> 2
        }

        /**
         * Numeral count for the Classic tick ring, tuned so labels stay round:
         * 220 km/h -> 11 intervals of 20; 140 mph -> 7 intervals of 20.
         * Pure logic, pinned by tests.
         */
        fun majorTickCount(maxSpeed: Float): Int =
            (maxSpeed / 20f).roundToInt().coerceIn(6, 12)
    }
}
