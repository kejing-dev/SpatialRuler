package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.RulerUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * A reading split for display: [main] + [last] is the number, [suffix] the unit. [fadeLast] asks the
 * UI to draw [last] faded: the digits that the tolerance does not support. [last] is one digit from a
 * tolerance of 1 cm, and from 10 cm the last two digits (in feet and inches: the whole inch figure,
 * with its unit mark, see [fadeSuffix]).
 */
data class ReadingText(
    val main: String,
    val last: String,
    val suffix: String,
    val fadeLast: Boolean,
    val fadeSuffix: Boolean = false,
) {
    val text: String
        get() = main + last + suffix
}

/**
 * Formats distances and tolerances. Numbers and unit symbols are the same in every language and the
 * decimal separator is always ".".
 */
class FormatReadingUseCase {

    fun distance(meters: Double, unit: RulerUnit, toleranceMm: Int): ReadingText {
        val fade = toleranceMm >= ComputeToleranceUseCase.FADE_FROM_MM
        val two = toleranceMm >= ComputeToleranceUseCase.FADE_TWO_FROM_MM
        val d = if (meters.isFinite() && meters > 0.0) meters else 0.0
        return when (unit) {
            RulerUnit.M -> split(fixed(d, 3), " m", fade, if (two) 2 else 1)
            RulerUnit.CM -> split(fixed(d * 100.0, 1), " cm", fade, if (two) 2 else 1)
            RulerUnit.MM -> split((d * 1000.0).roundToLong().toString(), " mm", fade, if (two) 2 else 1)
            RulerUnit.FT_IN -> {
                val inches = d / METRES_PER_INCH
                var feet = floor(inches / 12.0).toLong()
                var rest = fixed(inches - feet * 12.0, 1)
                if (rest == "12.0") {
                    feet += 1
                    rest = "0.0"
                }
                val feetPart = if (feet > 0) "$feet$PRIME " else ""
                // from ±10 cm the whole inch figure is not trustworthy: it fades together with its mark
                if (two) ReadingText(feetPart, rest, DOUBLE_PRIME, fadeLast = true, fadeSuffix = true)
                else split(feetPart + rest, DOUBLE_PRIME, fade, 1)
            }
        }
    }

    /**
     * Metric: millimetres below 1 cm ("±8 mm"), centimetres from 1 cm ("±1.5 cm", "±10 cm", "±50 cm"),
     * the same in m, cm and mm. Feet and inches: the metric step in inches, rounded up to 0.1″ for
     * steps up to 8 cm and to a whole inch from 10 cm ("±0.4″", "±3.2″", "±4″", "±20″").
     * Above the ladder: "> 50 cm" / "> 20″", without the ± sign.
     */
    fun tolerance(toleranceMm: Int, unit: RulerUnit): String {
        val over = toleranceMm > ComputeToleranceUseCase.LADDER_MM.last()
        if (unit == RulerUnit.FT_IN) {
            if (over) return "> " + wholeInches(ComputeToleranceUseCase.LADDER_MM.last()) + DOUBLE_PRIME
            if (toleranceMm >= ComputeToleranceUseCase.FADE_TWO_FROM_MM) return "±" + wholeInches(toleranceMm) + DOUBLE_PRIME
            val tenths = ceil(toleranceMm / MM_PER_INCH * 10.0 - 1e-9) / 10.0
            return "±" + fixed(tenths, 1) + DOUBLE_PRIME
        }
        if (over) return "> " + centimetres(ComputeToleranceUseCase.LADDER_MM.last()) + " cm"
        if (toleranceMm >= 10) return "±" + centimetres(toleranceMm) + " cm"
        return "±$toleranceMm mm"
    }

    /** Fine-tune offset in millimetres: "+2", "−1", "0". */
    fun offset(mm: Int): String =
        when {
            mm > 0 -> "+$mm"
            mm < 0 -> "$MINUS${abs(mm)}"
            else -> "0"
        }

    private fun centimetres(mm: Int): String {
        val cm = mm / 10.0
        return if (cm == floor(cm)) cm.toLong().toString() else fixed(cm, 1)
    }

    private fun wholeInches(mm: Int): String = ceil(mm / MM_PER_INCH - 1e-9).toLong().toString()

    /** Splits off the last [digits] digits (a decimal point between them goes along). */
    private fun split(number: String, suffix: String, fade: Boolean, digits: Int): ReadingText {
        var cut = number.length
        var seen = 0
        while (cut > 0 && seen < digits) {
            cut--
            if (number[cut].isDigit()) seen++
        }
        // never fade the whole number: a reading keeps at least its leading digit
        if (cut == 0 && number.length > 1) cut = 1
        return ReadingText(number.substring(0, cut), number.substring(cut), suffix, fade)
    }

    private fun fixed(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    companion object {
        const val METRES_PER_INCH = 0.0254
        const val MM_PER_INCH = 25.4
        const val PRIME = "′"
        const val DOUBLE_PRIME = "″"
        const val MINUS = "−"
    }
}
