package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTapeUseCaseTest {
    private val layout = LayoutTapeUseCase()

    private fun TapeGeometry.all(): List<TapeRect> = band + ink + red + edge

    @Test
    fun tapeShorterThanTwoMillimetres_hasNoGeometry() {
        val g = layout.layout(0.001f, UnitSystem.METRIC, 1)
        assertTrue(g.all().isEmpty())
        assertEquals(0, g.tickCount)
    }

    @Test
    fun halfMetre_atCentimetreLevel_hasFortyNineTicks_theFirstOneHidesInTheZeroEnd() {
        val g = layout.layout(0.5f, UnitSystem.METRIC, 1)
        assertEquals(49, g.tickCount) // 2 cm .. 50 cm; the 1 cm tick lies inside the 12 mm red zero end
        assertEquals(0, g.wholeBandCount)
        assertEquals(49, g.ink.size)
        val zeroEnd = g.red.first()
        assertNear(0f, zeroEnd.x0, 0f)
        assertNear(LayoutTapeUseCase.ZERO_END_M, zeroEnd.x1, 0f)
        assertNear(-0.0064f, zeroEnd.y0, 1e-6f) // between the two 1.6 mm rims
        assertNear(0.0064f, zeroEnd.y1, 1e-6f)
    }

    @Test
    fun wholeMetres_getARedBandInsteadOfATick() {
        val g = layout.layout(2.5f, UnitSystem.METRIC, 10)
        assertEquals(2, g.wholeBandCount) // 1 m and 2 m
        assertEquals(23, g.tickCount) // every 10 cm up to 2.5 m, minus the two red bands
        assertEquals(3, g.red.size) // zero end + two bands
        val band = g.red[1]
        assertNear(1f - LayoutTapeUseCase.WHOLE_BAND_M / 2f, band.x0, 1e-5f)
        assertNear(1f + LayoutTapeUseCase.WHOLE_BAND_M / 2f, band.x1, 1e-5f)
    }

    @Test
    fun designConstants_rimWholeBandAndZeroEnd() {
        assertNear(0.0016f, LayoutTapeUseCase.EDGE_M, 0f)
        assertNear(0.0028f, LayoutTapeUseCase.EDGE_SELECTED_M, 0f)
        assertNear(0.010f, LayoutTapeUseCase.WHOLE_BAND_M, 0f)
        assertNear(0.012f, LayoutTapeUseCase.ZERO_END_M, 0f)
        assertNear(0.016f, LayoutTapeUseCase.WIDTH_M, 0f)
    }

    @Test
    fun movingPiece_metricFine_isTenCentimetresOfTicks_withHalfATickAtEitherEnd_andNoRed() {
        val g = layout.segment(UnitSystem.METRIC, 1)
        assertNear(0.1f, LayoutTapeUseCase.segmentLength(UnitSystem.METRIC), 0f)
        assertTrue(g.red.isEmpty())
        assertEquals(0, g.wholeBandCount)
        assertEquals(10, g.tickCount) // 1 .. 9 cm, and the half tick at 10 cm
        assertEquals(11, g.ink.size) // plus the half tick at the start
        val first = g.ink.first()
        assertNear(0f, first.x0, 0f)
        assertNear(0.0005f, first.x1, 1e-7f) // half of the 1 mm line
        val last = g.ink.last()
        assertNear(0.1f, last.x1, 1e-6f)
        assertNear(0.0995f, last.x0, 1e-6f)
        // laid end to end the two halves make the 10 cm tick, as high as in the printed face
        assertNear(-0.008f + 0.80f * LayoutTapeUseCase.WIDTH_M, first.y1, 1e-6f)
        assertNear(first.y1, last.y1, 1e-6f)
    }

    @Test
    fun movingPiece_metricCoarse_onlyHasTheFiveAndTenCentimetreTicks() {
        val g = layout.segment(UnitSystem.METRIC, 5)
        assertEquals(2, g.tickCount)
        assertEquals(3, g.ink.size)
        val five = g.ink[1]
        assertNear(0.05f, (five.x0 + five.x1) / 2f, 1e-6f)
        assertNear(-0.008f + 0.56f * LayoutTapeUseCase.WIDTH_M, five.y1, 1e-6f)
    }

    @Test
    fun movingPiece_imperial_isOneFootOfInchTicks() {
        val g = layout.segment(UnitSystem.IMPERIAL, 1)
        assertNear(0.3048f, LayoutTapeUseCase.segmentLength(UnitSystem.IMPERIAL), 0f)
        assertEquals(12, g.tickCount)
        assertTrue(g.red.isEmpty())
        val six = g.ink.first { kotlin.math.abs((it.x0 + it.x1) / 2f - 0.1524f) < 1e-4f }
        assertNear(-0.008f + 0.62f * LayoutTapeUseCase.WIDTH_M, six.y1, 1e-6f)
    }

    @Test
    fun movingPieces_coverTheirFaceExactlyOnce() {
        for ((system, step) in listOf(UnitSystem.METRIC to 1, UnitSystem.METRIC to 5, UnitSystem.IMPERIAL to 1)) {
            val rects = layout.segment(system, step).all()
            val expected = LayoutTapeUseCase.segmentLength(system).toDouble() * LayoutTapeUseCase.WIDTH_M
            assertTrue(kotlin.math.abs(rects.sumOf { it.area.toDouble() } - expected) < expected * 1e-3)
            for (i in rects.indices) for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                assertTrue("$a overlaps $b", minOf(a.x1, b.x1) - maxOf(a.x0, b.x0) <= 1e-6f || minOf(a.y1, b.y1) - maxOf(a.y0, b.y0) <= 1e-6f)
            }
        }
    }

    @Test
    fun imperial_ticksEveryInch_redBandEveryFoot() {
        val g = layout.layout(1f, UnitSystem.IMPERIAL, 1)
        assertEquals(3, g.wholeBandCount) // 1 ft, 2 ft, 3 ft
        assertEquals(36, g.tickCount) // 39 inches fit into a metre
    }

    @Test
    fun faceIsCoveredExactlyOnce_noOverlaps_noGaps() {
        for ((length, system, step) in listOf(Triple(0.5f, UnitSystem.METRIC, 1), Triple(2.5f, UnitSystem.METRIC, 5), Triple(1.3f, UnitSystem.IMPERIAL, 1), Triple(0.005f, UnitSystem.METRIC, 1))) {
            val rects = layout.layout(length, system, step).all()
            val area = rects.sumOf { it.area.toDouble() }
            val expected = length.toDouble() * LayoutTapeUseCase.WIDTH_M
            assertTrue("area $area vs $expected for $length $system", kotlin.math.abs(area - expected) < expected * 1e-3)
            for (i in rects.indices) for (j in i + 1 until rects.size) {
                val a = rects[i]
                val b = rects[j]
                val overlapX = minOf(a.x1, b.x1) - maxOf(a.x0, b.x0)
                val overlapY = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
                assertTrue("$a overlaps $b", overlapX <= 1e-6f || overlapY <= 1e-6f)
            }
            assertTrue(rects.all { it.x0 >= 0f && it.x1 <= length + 1e-6f && it.x1 > it.x0 && it.y1 > it.y0 })
        }
    }

    @Test
    fun selectedTape_hasAThickerOutline() {
        val normal = layout.layout(0.2f, UnitSystem.METRIC, 1)
        val selected = layout.layout(0.2f, UnitSystem.METRIC, 1, LayoutTapeUseCase.EDGE_SELECTED_M)
        assertNear(LayoutTapeUseCase.EDGE_M, normal.edge[0].y1 - normal.edge[0].y0, 1e-6f)
        assertNear(LayoutTapeUseCase.EDGE_SELECTED_M, selected.edge[0].y1 - selected.edge[0].y0, 1e-6f)
        assertEquals(2, selected.edge.size)
    }

    @Test
    fun tickHeights_followTheDesign() {
        assertNear(0.34f, LayoutTapeUseCase.heightFraction(UnitSystem.METRIC, 3))
        assertNear(0.56f, LayoutTapeUseCase.heightFraction(UnitSystem.METRIC, 5))
        assertNear(0.80f, LayoutTapeUseCase.heightFraction(UnitSystem.METRIC, 10))
        assertNear(0.36f, LayoutTapeUseCase.heightFraction(UnitSystem.IMPERIAL, 5))
        assertNear(0.62f, LayoutTapeUseCase.heightFraction(UnitSystem.IMPERIAL, 6))
        // the 5 cm tick of a real layout rises to 56 % of the blade width
        val g = layout.layout(0.06f, UnitSystem.METRIC, 1)
        val fiveCm = g.ink.first { kotlin.math.abs((it.x0 + it.x1) / 2f - 0.05f) < 1e-4f }
        assertNear(-0.008f + 0.56f * LayoutTapeUseCase.WIDTH_M, fiveCm.y1, 1e-6f)
    }
}
