package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.Pin
import io.embodit.spatialruler.domain.model.SnapKind
import kotlin.math.sqrt

/**
 * Tolerance model of the design (constants are design values, to be calibrated on a device):
 * contact 2 mm; fine 3 mm + 0.1 % of the range; coarse 8 mm + 0.4 % of the range; snapped to a plane
 * corner or edge 6 mm; doubled beyond 3 m. A tape combines both ends as a root sum of squares.
 *
 * A tolerance is only ever rounded **up**, to the next step of the ladder 2, 3, 5, 8 mm, 1, 1.5, 2, 3,
 * 5, 8 cm, 10, 15, 20, 30, 50 cm. Anything above the ladder is [OVER_LADDER_MM] and reads "> 50 cm".
 */
class ComputeToleranceUseCase {

    /** One-sigma style contribution of a single pin, millimetres. */
    fun pinSigmaMm(grade: Grade, snapped: SnapKind, far: Boolean, rangeM: Float, pinch: Boolean = false): Float {
        var s =
            when {
                snapped == SnapKind.CORNER || snapped == SnapKind.EDGE -> SNAPPED_MM
                grade == Grade.CONTACT -> if (pinch) PINCH_CONTACT_MM else CONTACT_MM
                grade == Grade.FINE -> FINE_BASE_MM + FINE_PER_M * rangeM
                else -> COARSE_BASE_MM + COARSE_PER_M * rangeM
            }
        if (far) s *= 2f
        return s
    }

    fun pinSigmaMm(pin: Pin): Float = pinSigmaMm(pin.grade, pin.snapped, pin.far, pin.rangeM, pin.pinch)

    /** Tolerance shown on a pin's badge. */
    fun pinToleranceMm(pin: Pin): Int = roundUp(pinSigmaMm(pin))

    /** Tolerance of a tape between two pins, on the ladder. */
    fun tapeToleranceMm(a: Pin, b: Pin): Int {
        val sa = pinSigmaMm(a)
        val sb = pinSigmaMm(b)
        return roundUp(sqrt(sa * sa + sb * sb))
    }

    /** Smallest ladder value that is not below [mm]; [OVER_LADDER_MM] above the top of the ladder. */
    fun roundUp(mm: Float): Int {
        for (step in LADDER_MM) if (mm <= step + 1e-4f) return step
        return OVER_LADDER_MM
    }

    companion object {
        val LADDER_MM = intArrayOf(2, 3, 5, 8, 10, 15, 20, 30, 50, 80, 100, 150, 200, 300, 500)

        /** A tolerance above the ladder: shown as "> 50 cm". */
        const val OVER_LADDER_MM = 1000

        /** Beyond this range a placement is marked "Far". */
        const val FAR_RANGE_M = 3.0f

        /** From this tolerance on, the last digit of a reading is shown faded. */
        const val FADE_FROM_MM = 10

        /** From this tolerance on, the last two digits (imperial: the whole inch figure) are faded. */
        const val FADE_TWO_FROM_MM = 100

        private const val CONTACT_MM = 2f

        /** Contact with the pinch point of a hand. */
        private const val PINCH_CONTACT_MM = 8f
        private const val FINE_BASE_MM = 3f
        private const val FINE_PER_M = 1.0f
        private const val COARSE_BASE_MM = 8f
        private const val COARSE_PER_M = 4.0f
        private const val SNAPPED_MM = 6f
    }
}
