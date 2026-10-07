package io.embodit.spatialruler.domain.usecase

import kotlin.math.max

/**
 * Size rules of the design for things that must stay readable or hittable at 0.3 - 6 m.
 * All inputs are viewing distances in metres; all outputs are uniform scale factors.
 */
class ScaleForDistanceUseCase {

    /**
     * "Constant visual angle" panels (reading plate, pin tag, tape tag, candidate chip): the panel
     * entity is scaled by distance / 0.8, with the distance clamped to 0.45 - 6 m, so that a reading
     * keeps its size in view over the whole measuring range.
     */
    fun panel(distanceM: Float): Float =
        distanceM.coerceIn(PANEL_MIN_DISTANCE_M, PANEL_MAX_DISTANCE_M) / PANEL_REFERENCE_M

    /** Beyond this distance a plate keeps its size and only shows its value line. */
    fun plateIsFar(distanceM: Float): Boolean = distanceM > PANEL_MAX_DISTANCE_M

    /** A pin is drawn life size up to 2.5 m and grows with the distance beyond, twice at 5 m and more. */
    fun pin(distanceM: Float): Float = (distanceM / PIN_LIFE_SIZE_UNTIL_M).coerceIn(1f, PIN_MAX_SCALE)

    /**
     * The touch sphere of a pin has its own reach: 28 mm at the pin's head, growing from 1.3 m so that
     * it stays about 1.2 degrees wide (the factor is relative to the pin's own radius, not to [pin]).
     */
    fun pinTouch(distanceM: Float): Float = (distanceM / PIN_TOUCH_FULL_SIZE_UNTIL_M).coerceIn(1f, PIN_TOUCH_MAX_SCALE)

    /** The 16 mm blade is widened so that it never looks thinner than about half a degree. */
    fun tapeWidth(distanceM: Float): Float =
        max(1f, distanceM * HALF_DEGREE_TAN / LayoutTapeUseCase.WIDTH_M).coerceAtMost(TAPE_MAX_SCALE)

    /** Guide lines (candidate threads): 2 mm, widened with distance to stay visible. */
    fun guideWidthM(distanceM: Float): Float = max(GUIDE_WIDTH_M, distanceM * GUIDE_WIDTH_PER_M)

    companion object {
        const val PANEL_REFERENCE_M = 0.8f
        const val PANEL_MIN_DISTANCE_M = 0.45f
        const val PANEL_MAX_DISTANCE_M = 6.0f
        const val PIN_LIFE_SIZE_UNTIL_M = 2.5f
        const val PIN_MAX_SCALE = 2f
        const val PIN_TOUCH_FULL_SIZE_UNTIL_M = 1.3f
        const val PIN_TOUCH_MAX_SCALE = 4f
        const val TAPE_MAX_SCALE = 4f
        const val GUIDE_WIDTH_M = 0.0020f
        private const val GUIDE_WIDTH_PER_M = 0.0016f

        /** tan(0.5 degrees) */
        private const val HALF_DEGREE_TAN = 0.0087269f
    }
}
