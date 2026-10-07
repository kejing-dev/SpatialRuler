package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.ToggleIconButton
import com.pico.spatial.ui.design.ToggleIconButtonDefaults
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.spatialruler.R
import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.RulerUiState
import io.embodit.spatialruler.ui.ruler.debug.probe
import io.embodit.spatialruler.ui.theme.RulerColors

/** Where a floating surface of the design is hosted inside the Stage. */
enum class OverlayHost {
    /** The SpatialUI component itself (AlertDialog / SnackbarHost). */
    NATIVE,

    /** A plain pre-built attachment panel with the same size and copy (design assumption 9 fallback). */
    PANEL,
}

/**
 * Hosting of the floating surfaces (design assumption 9, settled on the emulator with SDK 6.1.9):
 * `AlertDialog` composes nothing in a Stage and `SnackbarHost` answers "not supported in Stage", so
 * dialogs and the snackbar are pre-built attachment panels made to the components' metrics; the
 * debug command `overlay` switches those two hosts, to re-check on another build. The More menu has
 * only the panel host since round 4: the SpatialUI `Menu` rows lost real pinches (round 3, R2-3), and
 * the simplify spec turned the menu into a segment, a toggle and two buttons, which no popup carries.
 */
object OverlayHosting {
    val dialog = mutableStateOf(OverlayHost.PANEL)
    val snackbar = mutableStateOf(OverlayHost.PANEL)
}

/** The More menu panel: 256 x 120 dp, shown at 0.75 above the More key. */
internal val MenuPanelWidth = 256.dp
internal val MenuPanelHeight = 120.dp
private val MenuPanelPadding = 8.dp
private val KeyWidth = 56.dp
private val KeyHeight = 48.dp

/**
 * The More menu (simplify spec F): language segment (follow system / 中文 / EN) and the snap toggle in
 * the first row, "Clear all" and Exit in the second. Language and snap change in place and the menu
 * stays open, so the result is seen; Clear all closes the menu and opens the dialog; Exit closes the
 * Stage. "Clear all" keeps its two words: it is the only irreversible action, and the trash glyph alone
 * also means "remove this pin" on the strip.
 */
@Composable
internal fun MenuPanel(state: RulerUiState, filled: Boolean, onEvent: (RulerEvent) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier =
            Modifier
                .size(MenuPanelWidth, MenuPanelHeight)
                .clip(shape)
                .backgroundMaterial(enable = true, style = Material.Thick)
                .padding(MenuPanelPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!filled) return@Column
        Row(
            modifier = Modifier.fillMaxWidth().height(KeyHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LanguageSegments(language = state.language, onEvent = onEvent, modifier = Modifier.width(180.dp).fillMaxHeight())
            ToggleIconButton(
                checked = state.snapEnabled,
                onCheckedChange = { onEvent(RulerEvent.SnapToggled(it)) },
                modifier = Modifier.probe("snap"),
                // `interaction` is the blade yellow: content on it is tape ink, white would not read
                colors =
                    ToggleIconButtonDefaults.toggleIconButtonColors(
                        checkedContainerColor = PicoTheme.colorScheme.interaction,
                        checkedContentColor = RulerColors.TapeInk,
                        uncheckedContainerColor = PicoTheme.colorScheme.fillPrimary,
                        uncheckedContentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                size = ToggleIconButtonDefaults.toggleIconButtonSize(width = KeyWidth, height = KeyHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_snap),
                    contentDescription = rs(R.string.action_snap),
                    modifier = Modifier.size(24.dp),
                    tint = if (state.snapEnabled) RulerColors.TapeInk else PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(KeyHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val clearContent = if (state.canClear) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelQuaternary
            Button(
                onClick = { onEvent(RulerEvent.ClearAllClicked) },
                modifier = Modifier.width(180.dp).probe("menu_clear"),
                size = ButtonDefaults.Regular.copy(height = KeyHeight, minWidth = 180.dp),
                shape = CircleShape,
                enabled = state.canClear,
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = clearContent),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_trash),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = clearContent,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = rs(R.string.menu_clear), color = clearContent, maxLines = 1)
            }
            IconButton(
                onClick = { onEvent(RulerEvent.ExitClicked) },
                modifier = Modifier.probe("menu_exit"),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = PicoTheme.colorScheme.fillPrimary,
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                size = IconButtonDefaults.iconButtonSize(width = KeyWidth, height = KeyHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_exit),
                    contentDescription = rs(R.string.menu_exit),
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
        }
    }
}

/** Follow system (globe) / 中文 / EN: three segments of 60 dp, styled like the unit segments. */
@Composable
private fun LanguageSegments(language: LanguagePref, onEvent: (RulerEvent) -> Unit, modifier: Modifier = Modifier) {
    SegmentControl(modifier = modifier, backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        LanguagePref.entries.forEach { option ->
            SegmentItem(
                selected = language == option,
                onClick = { onEvent(RulerEvent.LanguageSelected(option)) },
                // 60 x 48 dp each (spec F; QA R4-2): the control gets no say in the item size
                modifier = Modifier.width(60.dp).height(KeyHeight).probe("lang_${option.tag}"),
                // 60 dp items: 14 sp so that 中文 fits beside the item's own padding
                textStyle = PicoTheme.typography.labelLarge,
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = PicoTheme.colorScheme.labelPrimary,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.fillSecondary,
                        selectedItemContentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                title = {
                    when (option) {
                        LanguagePref.SYSTEM ->
                            Icon(
                                painter = painterResource(R.drawable.ic_globe),
                                contentDescription = rs(R.string.lang_system),
                                modifier = Modifier.size(20.dp),
                                tint = PicoTheme.colorScheme.labelPrimary,
                            )
                        // design-style: inherited-content-color SegmentItem
                        LanguagePref.ZH -> Text(text = rs(R.string.lang_zh), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                        LanguagePref.EN -> {
                            val english = rs(R.string.lang_en)
                            // design-style: inherited-content-color SegmentItem
                            Text(text = rs(R.string.lang_en_short), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.semantics { contentDescription = english })
                        }
                    }
                },
            )
        }
    }
}
