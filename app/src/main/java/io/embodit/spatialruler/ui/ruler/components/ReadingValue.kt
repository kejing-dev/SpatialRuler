package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.pico.spatial.ui.design.Text
import io.embodit.spatialruler.R
import io.embodit.spatialruler.domain.usecase.ReadingText
import io.embodit.spatialruler.ui.theme.RulerType

/** Opacity of the digits the tolerance does not support (design: "the last digit fades"). */
private const val FADED_DIGIT_ALPHA = 0.42f

/**
 * A distance reading: number in the reading style (tabular figures), unit suffix smaller. The digits
 * that the tolerance does not support are faded: the last one from ±1 cm, the last two from ±10 cm
 * (in feet and inches the whole inch figure with its mark). Null shows the empty readout "— —".
 *
 * [mark] is drawn in front of the number in [markColor] (the warning sign of a plate that only has
 * room for its value line).
 */
@Composable
internal fun ReadingValue(
    reading: ReadingText?,
    color: Color,
    modifier: Modifier = Modifier,
    mark: String = "",
    markColor: Color = color,
    style: TextStyle = RulerType.reading,
) {
    val numberStyle = style
    val unitStyle = RulerType.unit.toSpanStyle()
    if (reading == null) {
        Text(text = rs(R.string.lcd_empty), modifier = modifier, color = color, style = numberStyle, maxLines = 1)
        return
    }
    val faded = color.copy(alpha = color.alpha * FADED_DIGIT_ALPHA)
    val text =
        remember(reading, color, numberStyle, unitStyle, mark, markColor) {
            buildAnnotatedString {
                if (mark.isNotEmpty()) withStyle(unitStyle.copy(color = markColor)) { append(mark) }
                append(reading.main)
                if (reading.fadeLast) withStyle(numberStyle.toSpanStyle().copy(color = faded)) { append(reading.last) }
                else append(reading.last)
                withStyle(if (reading.fadeSuffix) unitStyle.copy(color = faded) else unitStyle) { append(reading.suffix) }
            }
        }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = numberStyle,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
