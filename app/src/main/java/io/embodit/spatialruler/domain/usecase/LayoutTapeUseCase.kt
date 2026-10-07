package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.UnitSystem

/** Axis-aligned rectangle on the tape face: x along the tape from its zero end, y across it. Metres. */
data class TapeRect(val x0: Float, val x1: Float, val y0: Float, val y1: Float) {
    val area: Float
        get() = (x1 - x0) * (y1 - y0)
}

/**
 * The tape face cut into non-overlapping rectangles, one list per colour. Because nothing overlaps,
 * the mesh built from it lies in one plane without depth fighting.
 */
data class TapeGeometry(
    val band: List<TapeRect>,
    val ink: List<TapeRect>,
    val red: List<TapeRect>,
    val edge: List<TapeRect>,
    val tickCount: Int,
    val wholeBandCount: Int,
)

/**
 * Lays out a true-scale tape: ticks every [step] base graduations (1 cm or 1 in) rising from one
 * edge, a red band at every whole metre / foot, a red zero end, and a dark rim along both edges.
 * No numerals: the platform has no 3D text, numbers live on the reading plate.
 *
 * x = 0 is the zero end of the blade: the end with the hook, which lies on the tail pin. Ticks and
 * whole-unit bands are counted from there; the blade comes out of the head pin at x = length.
 */
class LayoutTapeUseCase {

    fun layout(
        lengthM: Float,
        system: UnitSystem,
        step: Int,
        edgeWidthM: Float = EDGE_M,
    ): TapeGeometry = build(lengthM, system, step, edgeWidthM, piece = false)

    /**
     * One repeatable piece of printed blade, [segmentLength] long, for the blade while it moves: the
     * same ticks as [layout] but no zero end and no whole-unit band, and half a tick at either end so
     * that pieces laid end to end show full ticks at their joints.
     */
    fun segment(system: UnitSystem, step: Int, edgeWidthM: Float = EDGE_M): TapeGeometry =
        build(segmentLength(system), system, step, edgeWidthM, piece = true)

    private fun build(lengthM: Float, system: UnitSystem, step: Int, edgeWidthM: Float, piece: Boolean): TapeGeometry {
        val length = lengthM.coerceAtLeast(0f)
        val band = ArrayList<TapeRect>()
        val ink = ArrayList<TapeRect>()
        val red = ArrayList<TapeRect>()
        val edge = ArrayList<TapeRect>()
        if (length < MIN_LENGTH_M) return TapeGeometry(band, ink, red, edge, 0, 0)

        val half = WIDTH_M / 2f
        val yLo = -half + edgeWidthM
        val yHi = half - edgeWidthM
        edge += TapeRect(0f, length, -half, yLo)
        edge += TapeRect(0f, length, yHi, half)

        val base = SelectTickLevelUseCase.baseOf(system)
        val whole = SelectTickLevelUseCase.wholeOf(system)
        val tickWidth = tickWidthOf(system, step)
        var ticks = 0
        var wholeBands = 0
        var cursor: Float

        if (piece) {
            // the joint tick: its other half is the end of the previous piece
            cursor = tickWidth / 2f
            val top = (-half + heightFraction(system, 0) * WIDTH_M).coerceIn(yLo, yHi)
            ink += TapeRect(0f, cursor, yLo, top)
            if (top < yHi) band += TapeRect(0f, cursor, top, yHi)
        } else {
            // the zero end is red
            cursor = minOf(length, ZERO_END_M)
            red += TapeRect(0f, cursor, yLo, yHi)
        }

        var i = 1
        while (true) {
            val k = i * step
            val s = k * base
            if (s > length + 1e-5f) break
            i++
            val isWhole = !piece && k % whole == 0
            val w = if (isWhole) WHOLE_BAND_M else tickWidth
            val x0 = (s - w / 2f).coerceAtLeast(0f)
            val x1 = (s + w / 2f).coerceAtMost(length)
            if (x0 < cursor - 1e-6f || x1 - x0 < 1e-5f) continue // inside the previous feature
            if (x0 > cursor) band += TapeRect(cursor, x0, yLo, yHi)
            if (isWhole) {
                red += TapeRect(x0, x1, yLo, yHi)
                wholeBands++
            } else {
                val top = (-half + heightFraction(system, k) * WIDTH_M).coerceIn(yLo, yHi)
                ink += TapeRect(x0, x1, yLo, top)
                if (top < yHi) band += TapeRect(x0, x1, top, yHi)
                ticks++
            }
            cursor = x1
        }
        if (cursor < length) band += TapeRect(cursor, length, yLo, yHi)
        return TapeGeometry(band, ink, red, edge, ticks, wholeBands)
    }

    companion object {
        /** A 16 mm blade. */
        const val WIDTH_M = 0.016f
        const val ZERO_END_M = 0.012f

        /** Red band at every whole metre / foot, full width between the rims. */
        const val WHOLE_BAND_M = 0.010f

        /** Dark rim along both edges; a selected tape gets a wider one. */
        const val EDGE_M = 0.0016f
        const val EDGE_SELECTED_M = 0.0028f
        const val MIN_LENGTH_M = 0.002f

        /** Length of the repeatable piece used while the blade moves: 10 cm, or 1 ft. */
        fun segmentLength(system: UnitSystem): Float = if (system == UnitSystem.METRIC) 0.1f else 0.3048f

        /** Share of the blade width a tick covers: 1 cm 34 %, 5 cm 56 %, 10 cm 80 %; 1 in 36 %, 6 in 62 %. */
        fun heightFraction(system: UnitSystem, k: Int): Float =
            if (system == UnitSystem.METRIC) {
                when {
                    k % 10 == 0 -> 0.80f
                    k % 5 == 0 -> 0.56f
                    else -> 0.34f
                }
            } else {
                if (k % 6 == 0) 0.62f else 0.36f
            }

        /** Printed line width; coarser levels are only drawn from far away, so they are printed wider. */
        fun tickWidthOf(system: UnitSystem, step: Int): Float =
            if (system == UnitSystem.METRIC) {
                when {
                    step <= 1 -> 0.0010f
                    step <= 5 -> 0.0025f
                    step <= 10 -> 0.0040f
                    else -> 0.0080f
                }
            } else {
                if (step <= 1) 0.0015f else 0.0040f
            }
    }
}
