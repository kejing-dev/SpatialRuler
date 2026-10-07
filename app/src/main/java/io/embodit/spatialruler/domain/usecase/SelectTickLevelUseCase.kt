package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.UnitSystem
import kotlin.math.tan

/**
 * Chooses the finest tick level that is still worth drawing at a viewing distance. A level whose
 * neighbouring ticks would be closer than about 0.2 degrees of visual angle is left out.
 *
 * The result is the tick step as a multiple of the base graduation (1 cm or 1 in).
 */
class SelectTickLevelUseCase {

    fun finestStep(viewDistanceM: Float, system: UnitSystem): Int {
        val steps = stepsOf(system)
        val base = baseOf(system)
        val needed = viewDistanceM.coerceAtLeast(0f) * MIN_SPACING_TAN
        for (step in steps) if (base * step >= needed) return step
        return steps.last()
    }

    /**
     * Same as [finestStep] but keeps [current] while the distance is inside a small band around the
     * switch-over, so that the tape does not flicker between two levels when the head moves a little.
     */
    fun finestStepWithHysteresis(viewDistanceM: Float, system: UnitSystem, current: Int?): Int {
        val wanted = finestStep(viewDistanceM, system)
        if (current == null || wanted == current || current !in stepsOf(system)) return wanted
        val near = finestStep(viewDistanceM * (1f - HYSTERESIS), system)
        val far = finestStep(viewDistanceM * (1f + HYSTERESIS), system)
        return if (current == near || current == far) current else wanted
    }

    companion object {
        /** tan(0.2 degrees) */
        val MIN_SPACING_TAN: Float = tan(Math.toRadians(0.2)).toFloat()
        private const val HYSTERESIS = 0.08f

        const val CM = 0.01f
        const val INCH = 0.0254f

        private val METRIC_STEPS = intArrayOf(1, 5, 10, 50, 100)
        private val IMPERIAL_STEPS = intArrayOf(1, 6, 12, 36)

        fun stepsOf(system: UnitSystem): IntArray =
            if (system == UnitSystem.METRIC) METRIC_STEPS else IMPERIAL_STEPS

        /** Base graduation in metres: 1 cm or 1 in. */
        fun baseOf(system: UnitSystem): Float = if (system == UnitSystem.METRIC) CM else INCH

        /** Number of base graduations in the whole unit that gets a red band: 1 m or 1 ft. */
        fun wholeOf(system: UnitSystem): Int = if (system == UnitSystem.METRIC) 100 else 12
    }
}
