package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Badge
import com.pico.spatial.ui.design.BadgeDefaults
import com.pico.spatial.ui.design.Divider
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Stepper
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.spatialruler.R
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.ui.ruler.PinTagUi
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.TapeTagUi
import io.embodit.spatialruler.ui.ruler.debug.probe
import io.embodit.spatialruler.ui.ruler.debug.probeStepper
import io.embodit.spatialruler.ui.theme.RulerColors

/**
 * Strips are pre-built at fixed sizes (simplify spec D and E): the pin strip exists collapsed and with
 * the fine-tune steppers; the tape strip is one row.
 */
internal val TagWidth = 304.dp
internal val PinTagHeight = 128.dp
internal val TapeTagWidth = 166.dp
internal val TapeTagHeight = 70.dp

private val KeyWidth = 56.dp
private val KeyHeight = 48.dp
private val StepperWidth = 133.dp

/**
 * Glass root + solid dark inner card (the same `plateInk` face as plates and chips; 3 dp of glass show
 * around it). Everything on the card uses the fixed on-ink colours, so the contrast never depends on
 * the room behind the glass.
 */
@Composable
private fun TagCard(width: Dp, height: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    // AttachmentPanel root with Material.Regular glass: the Stage form of the preview's `sui-augment`.
    Box(
        modifier =
            Modifier
                .size(width, height)
                .clip(shape)
                .backgroundMaterial(enable = true, style = Material.Regular)
                .padding(3.dp)
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
                    .background(RulerColors.PlateInk)
                    .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** A dark key on the card: `onInkFill` with light content. */
@Composable
private fun cardKeyColors() =
    IconButtonDefaults.iconButtonColors(
        containerColor = RulerColors.OnInkFill,
        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
    )

/**
 * The badge of a strip: [text] in 14 / 18 at weight 600 with tabular figures, `alert` when [warn].
 * [description] names the glyphs for accessibility (the badge itself shows figures and marks only).
 */
@Composable
private fun StripBadge(text: String, warn: Boolean, description: String, modifier: Modifier = Modifier) {
    val content = if (warn) PicoTheme.colorScheme.alert else PicoTheme.colorScheme.labelPrimaryLight
    Box(modifier = modifier.semantics { contentDescription = description }, contentAlignment = Alignment.CenterStart) {
        Badge(
            badgeColor = BadgeDefaults.badgeColors(backgroundColor = RulerColors.OnInkFill, contentColor = content),
            badgeSize = BadgeDefaults.Regular,
        ) {
            Text(
                text = text,
                color = content,
                style = PicoTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum"),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** "▲ " in front of a tolerance when the end is far or unsaved. */
private const val WARNING = "▲ "

/**
 * Strip of the selected pin (round 5): badge (grade glyph, ▲ when far or unsaved, its own ±), Remove,
 * Close, and the two steppers that finish a move to the millimetre: ‹ › left / right and ˅ ˄ nearer /
 * farther (down / up on a vertical surface). Moving the pin is direct: press it, drag, release.
 */
@Composable
internal fun PinTag(tag: PinTagUi?, onEvent: (RulerEvent) -> Unit) {
    TagCard(width = TagWidth, height = PinTagHeight) {
        if (tag == null) return@TagCard
        Row(
            modifier = Modifier.fillMaxWidth().height(KeyHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val gradeName =
                rs(
                    when (tag.end.grade) {
                        Grade.CONTACT -> R.string.grade_contact
                        Grade.FINE -> R.string.grade_fine
                        Grade.COARSE -> R.string.grade_coarse
                    }
                )
            val markName =
                when {
                    tag.end.far -> " · " + rs(R.string.grade_far)
                    tag.end.saveState == SaveState.UNSAVED -> " · " + rs(R.string.grade_unsaved)
                    else -> ""
                }
            StripBadge(
                text = tag.end.grade.glyph + " " + (if (tag.warn) WARNING else "") + tag.tolerance,
                warn = tag.warn,
                description = gradeName + markName + " · " + tag.tolerance,
                modifier = Modifier.width(158.dp).padding(end = 4.dp),
            )
            IconButton(
                onClick = { onEvent(RulerEvent.PinRemoveClicked) },
                modifier = Modifier.probe("pin_remove"),
                colors = cardKeyColors(),
                size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = rs(R.string.pin_remove),
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
            IconButton(
                onClick = { onEvent(RulerEvent.PinTagClosed) },
                modifier = Modifier.probe("pin_close"),
                colors = cardKeyColors(),
                size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = rs(R.string.action_close),
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
        }
        run {
            Divider(color = RulerColors.OnInkFill)
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TuneStepper(
                    value = tag.offsetU,
                    minusName = "step_u_minus",
                    plusName = "step_u_plus",
                    minusTurn = 180f,
                    plusTurn = 0f,
                    minusDescription = rs(R.string.step_left),
                    plusDescription = rs(R.string.step_right),
                    onStep = { onEvent(RulerEvent.Stepped(NudgeAxis.U, it)) },
                )
                TuneStepper(
                    value = tag.offsetV,
                    minusName = "step_v_minus",
                    plusName = "step_v_plus",
                    minusTurn = 90f,
                    plusTurn = 270f,
                    minusDescription = rs(if (tag.horizontalSurface) R.string.step_near else R.string.step_down),
                    plusDescription = rs(if (tag.horizontalSurface) R.string.step_far else R.string.step_up),
                    onStep = { onEvent(RulerEvent.Stepped(NudgeAxis.V, it)) },
                )
            }
        }
    }
}

/**
 * One fine-tune stepper, 133 x 40: chevrons for the two directions (one drawable turned by
 * [minusTurn] / [plusTurn] degrees), the value as the signed number of millimetres ("+2", "0", "−1").
 */
@Composable
private fun TuneStepper(
    value: String,
    minusName: String,
    plusName: String,
    minusTurn: Float,
    plusTurn: Float,
    minusDescription: String,
    plusDescription: String,
    onStep: (Int) -> Unit,
) {
    Stepper(
        value = value,
        onStep = { delta -> onStep(if (delta < 0) -1 else 1) },
        modifier = Modifier.width(StepperWidth).probeStepper(minusName, plusName),
        decreaseIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = minusDescription,
                modifier = Modifier.size(16.dp).rotate(minusTurn),
                tint = PicoTheme.colorScheme.labelPrimaryLight,
            )
        },
        increaseIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = plusDescription,
                modifier = Modifier.size(16.dp).rotate(plusTurn),
                tint = PicoTheme.colorScheme.labelPrimaryLight,
            )
        },
        stepLength = 1,
        editable = false,
        textStyle = PicoTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum, lnum"),
        textColor = PicoTheme.colorScheme.labelPrimaryLight,
        backgroundColor = RulerColors.OnInkFill,
    )
}

/**
 * Strip of the selected tape (spec E): its ± (▲ in front when an end is far or unsaved) and Retract.
 * No close: a second tap on the plate, or a tap on a surface or on nothing, deselects the tape.
 */
@Composable
internal fun TapeTag(tag: TapeTagUi?, onEvent: (RulerEvent) -> Unit) {
    TagCard(width = TapeTagWidth, height = TapeTagHeight) {
        if (tag == null) return@TagCard
        Row(
            modifier = Modifier.fillMaxWidth().height(KeyHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StripBadge(
                text = (if (tag.warn) WARNING else "") + tag.tolerance,
                warn = tag.warn,
                description = (if (tag.warn) rs(R.string.grade_far) + " · " else "") + tag.tolerance,
                modifier = Modifier.width(80.dp),
            )
            IconButton(
                onClick = { onEvent(RulerEvent.TapeRetractClicked) },
                modifier = Modifier.probe("tape_retract"),
                colors = cardKeyColors(),
                size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_retract),
                    contentDescription = rs(R.string.tape_retract),
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
        }
    }
}
