package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonChip
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.usecase.FormatReadingUseCase
import io.embodit.spatialruler.ui.ruler.ChipUi
import io.embodit.spatialruler.ui.ruler.PlateUi
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.debug.probe
import io.embodit.spatialruler.ui.ruler.scene.LiveTolerance
import io.embodit.spatialruler.ui.theme.RulerColors
import io.embodit.spatialruler.ui.theme.RulerType
import kotlin.math.abs

/**
 * Reading plates are pre-built panels of a fixed size. The dark pill inside takes the width of its
 * content (96 - 184 dp) and the whole height of the panel (40 dp), so no empty band can swallow a
 * pinch. A panel takes the pointer over its whole rectangle, also where it is empty, so no part of it
 * may stay empty: the scene makes the panel as narrow as the pill, and the pill is drawn stretched
 * over the whole panel (see `PanelRig`, finding 4). What the user sees is the pill at its own width.
 */
internal val PlateWidth = 184.dp
internal val PlateHeight = 40.dp
private val PlateMinWidth = 96.dp
internal val ChipPanelWidth = 104.dp
internal val ChipPanelHeight = 36.dp

/** What a candidate chip covers of its panel (a `ButtonChip` takes the width of its text; this is its usual size). */
internal val ChipPillWidth = 88.dp
internal val ChipPillHeight = 32.dp

private val plateFormat = FormatReadingUseCase()
private const val WARNING = "▲ "
private const val FROZEN = "⦸ "

/** A pill is drawn while the panel's width is within this share of the pill's own. */
private const val STRETCH_SLACK = 0.12f

/** Plates of tapes that do not end on the selected pin recede to this (F9). */
private const val DIMMED_ALPHA = 0.55f

/**
 * The reading plate that rides on a tape: one line, [mark] value unit (simplify spec C). ▲ in `alert`
 * when an end is far or unsaved, ⦸ while tracking is lost. It is the face of a 3D object and stands in
 * front of arbitrary passthrough, so it is a solid dark plate and all of its text uses fixed light
 * colours. Tapping it selects the tape; the tolerance is on the tape strip.
 *
 * @param live length shown while the tape is still being pulled out (the reading counts up); the
 *   scene writes it at most ten times a second
 * @param applied share of the panel's width that the scene has made the panel (the pill's width over
 *   the panel's): the pill is drawn stretched by the inverse
 * @param onPill reports the width of the pill, dp (the scene places the plate and its stem by it)
 * @param watch the panel's pointer watcher; it goes on the pill, so that the empty rest of the panel
 *   lets the pointer through to what is behind it
 * @param hover the pill takes the hover effect; off while a pin is dragged, because a plate the captured
 *   pointer crosses would keep the hover tint and read as dimmed (QA R5b-3)
 */
@Composable
internal fun ReadingPlate(
    plate: PlateUi?,
    unit: RulerUnit,
    live: Float?,
    applied: Float,
    onPill: (Float) -> Unit,
    onEvent: (RulerEvent) -> Unit,
    watch: Modifier = Modifier,
    hover: Boolean = true,
) {
    Box(modifier = Modifier.size(PlateWidth, PlateHeight), contentAlignment = Alignment.Center) {
        if (plate == null) return@Box
        val interaction = remember { MutableInteractionSource() }
        Pill(
            selected = plate.selected,
            bar = if (plate.frozen) RulerColors.StaleGrey else RulerColors.TapeYellow,
            applied = applied,
            onPill = onPill,
            dimmed = plate.dimmed,
            modifier =
                watch
                    .then(if (hover) Modifier.spatialHoverEffect() else Modifier)
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = { onEvent(RulerEvent.PlateClicked(plate.tapeId)) },
                    )
                    .controllerHapticFeedback(interactionSource = interaction)
                    .probe("plate:${plate.tapeId}"),
        ) {
            val reading = if (live != null) plateFormat.distance(live.toDouble(), unit, 0) else plate.reading
            ReadingValue(
                reading = reading,
                color = if (plate.frozen) RulerColors.OnInkSecondary else PicoTheme.colorScheme.labelPrimaryLight,
                mark = if (plate.frozen) FROZEN else if (plate.warn && live == null) WARNING else "",
                markColor = if (plate.frozen) RulerColors.OnInkSecondary else PicoTheme.colorScheme.alert,
            )
        }
    }
}

/**
 * The plate at the tail of a tape that is being pulled out: one line with the counting length, ▲ while
 * the aim is far (M3's live tolerance only drives that mark). Not a control.
 */
@Composable
internal fun LivePlate(length: Float?, tolerance: LiveTolerance?, unit: RulerUnit, applied: Float, onPill: (Float) -> Unit) {
    Box(modifier = Modifier.size(PlateWidth, PlateHeight), contentAlignment = Alignment.Center) {
        if (length == null) return@Box
        Pill(selected = false, bar = RulerColors.TapeYellow, applied = applied, onPill = onPill, modifier = Modifier) {
            ReadingValue(
                reading = plateFormat.distance(length.toDouble(), unit, 0),
                color = PicoTheme.colorScheme.labelPrimaryLight,
                mark = if (tolerance?.far == true) WARNING else "",
                markColor = PicoTheme.colorScheme.alert,
            )
        }
    }
}

/**
 * The dark pill of a plate: yellow index bar, then the one line of [content]. As wide as its content,
 * 96 - 184 dp, 40 dp high, and drawn stretched over the whole panel: the scene has made the panel
 * [applied] times its width. Until the scene has followed a new width the pill is not drawn (it would
 * look stretched).
 */
@Composable
private fun Pill(
    selected: Boolean,
    bar: androidx.compose.ui.graphics.Color,
    applied: Float,
    onPill: (Float) -> Unit,
    modifier: Modifier,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val density = LocalDensity.current
    val panelPx = with(density) { PlateWidth.toPx() }
    Row(
        modifier =
            Modifier
                .widthIn(min = PlateMinWidth, max = PlateWidth)
                .height(PlateHeight)
                .onSizeChanged { onPill(it.width / density.density) }
                .graphicsLayer {
                    val own = size.width / panelPx
                    scaleX = 1f / applied
                    // small differences (a digit more while the reading counts) are drawn; a pill that the panel does not fit yet is not
                    alpha = if (abs(applied - own) <= own * STRETCH_SLACK) (if (dimmed) DIMMED_ALPHA else 1f) else 0f
                }
                .clip(shape)
                .background(RulerColors.PlateInk)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) RulerColors.TapeYellow else RulerColors.OnInkFill,
                    shape = shape,
                )
                .then(modifier)
                .padding(start = 7.dp, top = 5.dp, end = 12.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // index bar: this plate belongs to a tape
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bar)
        )
        Box(modifier = Modifier.height(30.dp), contentAlignment = Alignment.CenterStart) { content() }
    }
}

/** Distance from the tape head to another pin; tapping it pulls a tape between the two. */
@Composable
internal fun CandidateChip(chip: ChipUi?, onEvent: (RulerEvent) -> Unit, watch: Modifier = Modifier) {
    Box(modifier = Modifier.size(ChipPanelWidth, ChipPanelHeight), contentAlignment = Alignment.Center) {
        if (chip == null) return@Box
        ButtonChip(
            label = {
                Text(
                    text = chip.text,
                    color = PicoTheme.colorScheme.labelPrimaryLight,
                    style = RulerType.figure,
                    maxLines = 1,
                )
            },
            onClick = { onEvent(RulerEvent.ChipClicked(chip.pinId)) },
            modifier = watch.probe("chip:${chip.pinId}"),
            chipSize = ChipsDefaults.Small,
            colors =
                ChipsDefaults.chipColors(
                    contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    backgroundColor = RulerColors.PlateInk,
                ),
        )
    }
}
