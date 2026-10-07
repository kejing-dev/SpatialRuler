package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.spatialruler.R
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.usecase.FormatReadingUseCase
import io.embodit.spatialruler.ui.ruler.LcdContext
import io.embodit.spatialruler.ui.ruler.LcdUi
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.RulerUiState
import io.embodit.spatialruler.ui.theme.RulerColors
import io.embodit.spatialruler.ui.theme.RulerType

/** The body panel is 340 x 136 dp, shown at scale 0.75 on the face of the instrument (20.4 x 8.2 cm). */
internal val BodyPanelWidth = 340.dp
internal val BodyPanelHeight = 136.dp
private val KeyWidth = 56.dp
private val KeyHeight = 48.dp

/** The annunciator at the right end of the readout: a fixed 20 dp box, so that the value never moves. */
private val AnnunciatorSize = 20.dp

/**
 * The only standing panel: the face of the tape-measure body (simplify spec B). One readout window
 * (196 x 56: the value at 32 sp and a 20 dp annunciator), two keys (undo, more) and the unit segments.
 * Snap and everything else live in "More". No words: the state of the session is one glyph.
 *
 * It is the first thing in view. Until the stored language, unit and snap are known it shows the
 * spinner only: no value, no selected unit, keys off. While the scene is being built undo is off;
 * more and the unit segments work.
 *
 * [liveLength] is the length of the tape that is being pulled by hand (written by the scene at most
 * ten times a second); null when no tape is being pulled.
 */
@Composable
internal fun BodyPanel(
    state: RulerUiState,
    liveLength: Float?,
    onEvent: (RulerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val known = state.settingsKnown
    // `sui-augment` in the Web preview only stands for "free panel with a Material.Regular glass root".
    // Augment itself is WindowContainer-only, so in the Stage the panel is an AttachmentPanel whose
    // root clips to its shape and applies the material (the one sanctioned exception of the mapping).
    // The glass sits in the dark bezel of the 3D body, so it never lies over raw passthrough.
    Column(
        modifier =
            Modifier
                .size(BodyPanelWidth, BodyPanelHeight)
                .clip(shape)
                .backgroundMaterial(enable = true, style = Material.Regular)
                .padding(12.dp)
                .then(modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Readout(lcd = state.lcd, unit = state.unit, liveLength = liveLength, known = known, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onEvent(RulerEvent.UndoClicked) },
                modifier = Modifier.bodyKey("undo"),
                colors = keyColors(),
                size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
                enabled = known && state.canUndo,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = if (known) rs(R.string.action_undo) else null,
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
            IconButton(
                onClick = { onEvent(RulerEvent.MoreClicked) },
                modifier = Modifier.bodyKey("more"),
                colors = keyColors(),
                size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
                enabled = known,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = if (known) rs(R.string.action_more) else null,
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
        }
        UnitSegments(unit = if (known) state.unit else null, onEvent = onEvent)
    }
}

@Composable
private fun keyColors() =
    IconButtonDefaults.iconButtonColors(
        containerColor = PicoTheme.colorScheme.fillPrimary,
        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
    )

private val readoutFormat = FormatReadingUseCase()

/**
 * The readout window: the current, live or last reading at 32 sp on a solid dark face, and the
 * annunciator of the session state at the right end (table B of the simplify spec). The precision
 * shows as dimmed digits; the exact tolerance is on the strips.
 */
@Composable
private fun Readout(lcd: LcdUi, unit: RulerUnit, liveLength: Float?, known: Boolean, modifier: Modifier = Modifier) {
    val blank = lcd.context == LcdContext.BLANK
    Row(
        modifier =
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PicoTheme.colorScheme.fillPrimary)
                .padding(start = 12.dp, end = 10.dp)
                .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (!blank) {
                // while a tape is pulled by hand the value is its live length; nothing is known about its accuracy yet
                val reading =
                    if (lcd.live && liveLength != null) remember(liveLength, unit) { readoutFormat.distance(liveLength.toDouble(), unit, 0) }
                    else lcd.reading
                ReadingValue(
                    reading = reading,
                    color = if (lcd.context == LcdContext.LOST) RulerColors.OnInkSecondary else PicoTheme.colorScheme.labelPrimaryLight,
                    style = RulerType.readout,
                )
            }
        }
        Box(modifier = Modifier.size(AnnunciatorSize), contentAlignment = Alignment.Center) {
            Annunciator(lcd.context, known)
        }
    }
}

/** One glyph for the state of the session; nothing in the idle states (the value then has the whole row). */
@Composable
private fun Annunciator(context: LcdContext, known: Boolean) {
    when (context) {
        LcdContext.BLANK, LcdContext.STARTING, LcdContext.SCANNING ->
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = RulerColors.OnInkSecondary,
                progressSize = CircularProgressDefaults.circleProgressSize(18.dp),
            )
        LcdContext.ARMED, LcdContext.PULLING ->
            Icon(
                painter = painterResource(R.drawable.ic_tape),
                contentDescription = if (known) rs(R.string.lcd_armed) else null,
                modifier = Modifier.size(AnnunciatorSize),
                tint = RulerColors.TapeYellow,
            )
        LcdContext.TUNING ->
            Icon(
                painter = painterResource(R.drawable.ic_move),
                contentDescription = if (known) rs(R.string.lcd_tuning_plain) else null,
                modifier = Modifier.size(AnnunciatorSize),
                tint = PicoTheme.colorScheme.labelPrimaryLight,
            )
        LcdContext.LOST ->
            Icon(
                painter = painterResource(R.drawable.ic_no_surface),
                contentDescription = if (known) rs(R.string.lcd_lost) else null,
                modifier = Modifier.size(AnnunciatorSize),
                tint = PicoTheme.colorScheme.error,
            )
        LcdContext.BLOCKED, LcdContext.READY, LcdContext.LAST, LcdContext.SELECTED -> Unit
    }
}

/**
 * m / cm / mm / ft·in: four equal segments (each at least 64 dp wide), 48 dp high. [unit] is null
 * while the stored unit is not known yet: no segment is selected and none can be pressed.
 */
@Composable
private fun UnitSegments(unit: RulerUnit?, onEvent: (RulerEvent) -> Unit) {
    SegmentControl(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        backgroundColor = PicoTheme.colorScheme.fillTertiary,
    ) {
        RulerUnit.entries.forEach { option ->
            val label =
                when (option) {
                    RulerUnit.M -> rs(R.string.unit_m)
                    RulerUnit.CM -> rs(R.string.unit_cm)
                    RulerUnit.MM -> rs(R.string.unit_mm)
                    RulerUnit.FT_IN -> rs(R.string.unit_ftin)
                }
            SegmentItem(
                selected = unit == option,
                onClick = { if (unit != null) onEvent(RulerEvent.UnitSelected(option)) },
                // 79 x 48 dp each (QA R4-2: the control's own item height is 40)
                modifier = Modifier.weight(1f).height(48.dp).bodyKey("unit_${option.tag}"),
                textStyle = PicoTheme.typography.titleMedium,
                // text straight on the glass: labelPrimary for every segment (labelTertiary measured 3.0 : 1 there)
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = PicoTheme.colorScheme.labelPrimary,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.fillSecondary,
                        selectedItemContentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Text(text = label, maxLines = 1)
                },
            )
        }
    }
}
