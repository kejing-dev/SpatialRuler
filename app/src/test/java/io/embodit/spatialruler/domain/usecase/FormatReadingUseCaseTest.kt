package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.RulerUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatReadingUseCaseTest {
    private val format = FormatReadingUseCase()

    @Test
    fun metricUnits_haveFixedDecimals() {
        assertEquals("1.235 m", format.distance(1.2346, RulerUnit.M, 8).text)
        assertEquals("123.5 cm", format.distance(1.2346, RulerUnit.CM, 8).text)
        assertEquals("1235 mm", format.distance(1.2346, RulerUnit.MM, 8).text)
        assertEquals("0.013 m", format.distance(0.0128, RulerUnit.M, 8).text)
        assertEquals("13 mm", format.distance(0.0128, RulerUnit.MM, 8).text)
    }

    @Test
    fun feetAndInches_showFeetOnlyFromOneFoot() {
        assertEquals("3′ 11.2″", format.distance(1.2, RulerUnit.FT_IN, 8).text) // 47.24 in
        assertEquals("9.4″", format.distance(0.24, RulerUnit.FT_IN, 8).text) // sub-foot: no feet part
        assertEquals("13′ 1.5″", format.distance(4.0, RulerUnit.FT_IN, 8).text) // 157.48 in
        assertEquals("0.0″", format.distance(0.0, RulerUnit.FT_IN, 8).text)
    }

    @Test
    fun inchesThatRoundToTwelve_carryIntoTheFeet() {
        // 11.996 in would print as "12.0″": it must read 1′ 0.0″
        assertEquals("1′ 0.0″", format.distance(0.3047, RulerUnit.FT_IN, 8).text)
        assertEquals("2′ 0.0″", format.distance(0.6095, RulerUnit.FT_IN, 8).text)
    }

    @Test
    fun reading_isSplitSoThatTheLastDigitCanFade() {
        val coarse = format.distance(1.2346, RulerUnit.M, 10)
        assertEquals("1.23", coarse.main)
        assertEquals("5", coarse.last)
        assertEquals(" m", coarse.suffix)
        assertTrue(coarse.fadeLast)
        assertFalse(format.distance(1.2346, RulerUnit.M, 8).fadeLast)
        val imperial = format.distance(1.2, RulerUnit.FT_IN, 30)
        assertEquals("3′ 11.", imperial.main)
        assertEquals("2", imperial.last)
        assertEquals("″", imperial.suffix)
        assertTrue(imperial.fadeLast)
    }

    @Test
    fun tolerance_usesMillimetresBelowOneCentimetre_thenCentimetres_inEveryMetricUnit() {
        assertEquals("±8 mm", format.tolerance(8, RulerUnit.M))
        assertEquals("±1 cm", format.tolerance(10, RulerUnit.CM))
        assertEquals("±1.5 cm", format.tolerance(15, RulerUnit.MM))
        assertEquals("±5 cm", format.tolerance(50, RulerUnit.M))
        assertEquals("±8 cm", format.tolerance(80, RulerUnit.M))
        assertEquals("±10 cm", format.tolerance(100, RulerUnit.MM))
        assertEquals("±15 cm", format.tolerance(150, RulerUnit.CM))
        assertEquals("±50 cm", format.tolerance(500, RulerUnit.M))
    }

    @Test
    fun tolerance_aboveTheLadder_isWrittenAsMoreThanItsTop_withoutThePlusMinus() {
        assertEquals("> 50 cm", format.tolerance(ComputeToleranceUseCase.OVER_LADDER_MM, RulerUnit.M))
        assertEquals("> 50 cm", format.tolerance(ComputeToleranceUseCase.OVER_LADDER_MM, RulerUnit.MM))
        assertEquals("> 20″", format.tolerance(ComputeToleranceUseCase.OVER_LADDER_MM, RulerUnit.FT_IN))
    }

    @Test
    fun tolerance_inInches_isTheMetricStepRoundedUp_toATenthAndFromTenCentimetresToAWholeInch() {
        val series = listOf("±0.1″", "±0.2″", "±0.2″", "±0.4″", "±0.4″", "±0.6″", "±0.8″", "±1.2″", "±2.0″", "±3.2″", "±4″", "±6″", "±8″", "±12″", "±20″")
        assertEquals(series, ComputeToleranceUseCase.LADDER_MM.map { format.tolerance(it, RulerUnit.FT_IN) })
    }

    @Test
    fun fromTenCentimetres_theLastTwoDigitsFade_andInInchesTheWholeInchFigure() {
        val m = format.distance(4.472, RulerUnit.M, 100)
        assertEquals("4.4" to "72", m.main to m.last)
        assertTrue(m.fadeLast)
        assertFalse(m.fadeSuffix)
        val cm = format.distance(4.472, RulerUnit.CM, 150)
        assertEquals("44" to "7.2", cm.main to cm.last)
        val mm = format.distance(4.472, RulerUnit.MM, 500)
        assertEquals("44" to "72", mm.main to mm.last)
        val imperial = format.distance(4.472, RulerUnit.FT_IN, 100) // 176.06 in
        assertEquals("14′ ", imperial.main)
        assertEquals("8.1", imperial.last)
        assertEquals("″", imperial.suffix)
        assertTrue(imperial.fadeLast)
        assertTrue(imperial.fadeSuffix)
        assertEquals("14′ 8.1″", imperial.text)
        // one step below: only the last digit
        val below = format.distance(4.472, RulerUnit.M, 80)
        assertEquals("4.47" to "2", below.main to below.last)
        assertEquals("14′ 8." to "1", format.distance(4.472, RulerUnit.FT_IN, 80).let { it.main to it.last })
    }

    @Test
    fun aReadingNeverFadesAway_itKeepsItsLeadingDigit() {
        val short = format.distance(0.005, RulerUnit.CM, 100) // "0.5 cm"
        assertEquals("0" to ".5", short.main to short.last)
        val tiny = format.distance(0.007, RulerUnit.MM, 100) // "7 mm"
        assertEquals("" to "7", tiny.main to tiny.last)
        // less than a foot: the inch figure is all there is
        val inches = format.distance(0.24, RulerUnit.FT_IN, 100)
        assertEquals("" to "9.4", inches.main to inches.last)
        assertEquals("9.4″", inches.text)
    }

    @Test
    fun offsets_carryTheirSign() {
        assertEquals("+2", format.offset(2))
        assertEquals("−1", format.offset(-1))
        assertEquals("0", format.offset(0))
    }

    @Test
    fun invalidDistances_readZero() {
        assertEquals("0.000 m", format.distance(-0.5, RulerUnit.M, 8).text)
        assertEquals("0.000 m", format.distance(Double.NaN, RulerUnit.M, 8).text)
        assertEquals("0.0 cm", format.distance(Double.POSITIVE_INFINITY, RulerUnit.CM, 8).text)
    }
}
