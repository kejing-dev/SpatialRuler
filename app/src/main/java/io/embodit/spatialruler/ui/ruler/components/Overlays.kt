package io.embodit.spatialruler.ui.ruler.components

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.design.windows.LocalSnackbarHostState
import com.pico.spatial.ui.design.windows.SnackbarDuration
import com.pico.spatial.ui.design.windows.SnackbarResult
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.spatialruler.R
import io.embodit.spatialruler.domain.model.SnackKind
import io.embodit.spatialruler.platform.RulerLog
import io.embodit.spatialruler.ui.ruler.ClearDialogUi
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.SnackUi
import io.embodit.spatialruler.ui.ruler.debug.LocalProbePanel
import io.embodit.spatialruler.ui.ruler.debug.probe
import io.embodit.spatialruler.ui.theme.RulerColors
import kotlin.math.abs

/**
 * The dialogs of the design are shown at 1.125, 0.9 m from the head, on a pre-built 480 x 280 dp
 * panel. The card inside is 400 dp wide and hugs its content (about 212 dp for the clear dialog).
 */
internal val DialogPanelWidth = 480.dp
internal val DialogPanelHeight = 280.dp
private val DialogCardWidth = 400.dp

/** The snackbar of the design is 240 - 560 x 56 dp; the pre-built panel has room for the widest one. */
internal val SnackPanelWidth = 560.dp
internal val SnackPanelHeight = 72.dp

/** Frames the SpatialUI dialog gets to compose its content before the panel takes over. */
private const val NATIVE_PROBE_FRAMES = 4

private class NativeProbe {
    var composed = false
}

/** Floating surfaces fade in over this (F11). */
private const val FADE_IN_MS = 150

// ---------------------------------------------------------------------------------------------
// Clear all confirmation, and "spatial features unavailable"
// ---------------------------------------------------------------------------------------------

/**
 * The confirmation as the SpatialUI [AlertDialog]. In SDK 6.1.9 that component composes nothing inside
 * a Stage (dialogs only exist in a WindowContainer), so the product host is [DialogPanel]. This path
 * is only taken when the debug command `overlay dialog native` asks for it, to re-check on another
 * build; it falls back to the panel by itself and logs why.
 */
@Composable
internal fun ClearDialog(dialog: ClearDialogUi?, onEvent: (RulerEvent) -> Unit) {
    if (dialog == null || OverlayHosting.dialog.value != OverlayHost.NATIVE) return
    val probe = remember { NativeProbe() }
    AlertDialog(
        onDismissRequest = { onEvent(RulerEvent.DialogDismissed) },
        title = {
            SideEffect { probe.composed = true }
            Text(text = rs(R.string.menu_clear), color = PicoTheme.colorScheme.labelPrimary)
        },
        content = { Text(text = rs(R.string.dialog_clear_body), color = PicoTheme.colorScheme.labelPrimary) },
        buttons = {
            CompositionLocalProvider(LocalProbePanel provides "dialog") {
                ClearDialogButtons(onEvent = onEvent)
            }
        },
    )
    LaunchedEffect(Unit) {
        repeat(NATIVE_PROBE_FRAMES) { withFrameNanos {} }
        if (probe.composed) {
            RulerLog.i("OVERLAY dialog host=native shown=true")
        } else {
            RulerLog.i("OVERLAY dialog host=native shown=false frames=$NATIVE_PROBE_FRAMES reason=AlertDialog composed no content in the Stage -> host=panel")
            OverlayHosting.dialog.value = OverlayHost.PANEL
        }
    }
}

/**
 * Dialog buttons: 48 dp high, at least 120 dp wide, capsule, an icon of 20 dp in front of the word. A
 * copy of the pre-defined Regular size keeps its text style (a size made by `ButtonDefaults.buttonSize`
 * has a small label); neither carries the capsule shape of the pre-defined sizes, so the buttons name
 * it themselves.
 */
private val DialogButtonSize = ButtonDefaults.Regular.copy(height = 48.dp, minWidth = 120.dp)

@Composable
private fun DialogButton(name: String, iconRes: Int, text: String, container: Color, content: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.probe(name),
        size = DialogButtonSize,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = content, maxLines = 1)
    }
}

/** Cancel and the destructive confirmation. Content on the `error` fill is tape ink: white measured 2.7 : 1 there. */
@Composable
private fun ClearDialogButtons(onEvent: (RulerEvent) -> Unit) {
    DialogButton("dialog_cancel", R.drawable.ic_close, rs(R.string.action_cancel), PicoTheme.colorScheme.fillTertiary, PicoTheme.colorScheme.labelPrimary) {
        onEvent(RulerEvent.DialogDismissed)
    }
    DialogButton("dialog_confirm", R.drawable.ic_trash, rs(R.string.action_clear), PicoTheme.colorScheme.error, RulerColors.TapeInk) {
        onEvent(RulerEvent.ClearConfirmed)
    }
}

/** Exit and Retry of the "spatial features unavailable" dialog. Content on the `interaction` fill is tape ink. */
@Composable
private fun BlockedDialogButtons(onEvent: (RulerEvent) -> Unit) {
    DialogButton("blocked_exit", R.drawable.ic_exit, rs(R.string.action_exit), PicoTheme.colorScheme.fillTertiary, PicoTheme.colorScheme.labelPrimary) {
        onEvent(RulerEvent.BlockedExitClicked)
    }
    DialogButton("blocked_retry", R.drawable.ic_retry, rs(R.string.action_retry), PicoTheme.colorScheme.interaction, RulerColors.TapeInk) {
        onEvent(RulerEvent.BlockedRetryClicked)
    }
}

/** An icon with a figure beside it, as in the dialog header and the Restored snackbar. */
@Composable
private fun IconCount(iconRes: Int, count: Int, iconSize: androidx.compose.ui.unit.Dp, color: Color, textStyle: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(iconSize), tint = color)
        Text(text = count.toString(), color = color, style = textStyle, maxLines = 1, softWrap = false)
    }
}

/**
 * The dialogs of the app (simplify spec G): the clear confirmation ([dialog]) and "spatial features
 * unavailable" ([blocked]). A plain attachment panel built to the metrics of the SpatialUI
 * `AlertDialog`: 32 dp padding, header 24 / 30, one sentence 16 / 24, 24 dp of air, buttons at the
 * right, 8 dp apart; `Material.Thick`, so the content sits on glass and uses `labelPrimary`. The clear
 * dialog's header is the trash glyph with the counts of pins and tapes as icon + figure; the blocked
 * dialog keeps its title, because the fix is in system settings, which no glyph can name. The card is
 * placed 0.9 m in front of the head when it opens and closes only through its buttons.
 */
@Composable
internal fun DialogPanel(dialog: ClearDialogUi?, blocked: Boolean, onEvent: (RulerEvent) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier = Modifier.size(DialogPanelWidth, DialogPanelHeight), contentAlignment = Alignment.Center) {
        if (dialog == null && !blocked) return@Box
        // F11: the card fades in over 150 ms (no frame with an empty dark card)
        val fade = remember(blocked) { Animatable(0f) }
        LaunchedEffect(blocked) { fade.animateTo(1f, tween(FADE_IN_MS)) }
        val ink = PicoTheme.colorScheme.labelPrimary
        val figures = PicoTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum, lnum")
        Column(
            modifier =
                Modifier
                    .width(DialogCardWidth)
                    .heightIn(max = DialogPanelHeight)
                    .graphicsLayer { alpha = fade.value }
                    .clip(shape)
                    .backgroundMaterial(enable = true, style = Material.Thick)
                    .padding(32.dp),
        ) {
            if (blocked) {
                Row(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(painter = painterResource(R.drawable.ic_no_surface), contentDescription = null, modifier = Modifier.size(32.dp), tint = ink)
                    Text(text = rs(R.string.dialog_blocked_title), color = ink, style = PicoTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else if (dialog != null) {
                val counts = rq(R.plurals.count_pins, dialog.pins) + ", " + rq(R.plurals.count_tapes, dialog.tapes)
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = counts },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(painter = painterResource(R.drawable.ic_trash), contentDescription = null, modifier = Modifier.size(32.dp), tint = ink)
                    IconCount(R.drawable.ic_pin, dialog.pins, 24.dp, ink, figures)
                    IconCount(R.drawable.ic_tape, dialog.tapes, 24.dp, ink, figures)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (blocked) rs(R.string.dialog_blocked_body) else rs(R.string.dialog_clear_body),
                color = ink,
                style = PicoTheme.typography.bodyLargeMultiline,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (blocked) BlockedDialogButtons(onEvent = onEvent) else ClearDialogButtons(onEvent = onEvent)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Snackbar
// ---------------------------------------------------------------------------------------------

/** The glyph in front of a snackbar message. */
internal enum class SnackIcon { CHECK, PIN, WARNING, NO_SURFACE, TAPE, SPINNER }

/** The glyph on a snackbar's action button. */
internal enum class SnackActionIcon { TRASH, RETRY }

/**
 * What a snackbar shows: [message] after [icon] (at most six words; "" for Restored, whose message is
 * the counts [pins] · [tapes] as icon + figure), and an optional [action] word with [actionIcon].
 * [description] is always empty since round 4 and only kept for the debug `dump`.
 */
internal class SnackTexts(
    val message: String,
    val action: String,
    val icon: SnackIcon,
    val actionIcon: SnackActionIcon? = null,
    val pins: Int = -1,
    val tapes: Int = -1,
) {
    val description: String
        get() = ""
}

@Composable
@ReadOnlyComposable
internal fun snackTexts(snack: SnackUi): SnackTexts = snackTexts(LocalRulerResources.current, snack)

/** Copy of a snackbar in the language of [resources] (also read by the debug `dump`). */
internal fun snackTexts(resources: Resources, snack: SnackUi): SnackTexts {
    fun s(id: Int): String = resources.getString(id)
    return when (snack.kind) {
        // the message is figures only: pins and tapes as icon + count
        SnackKind.RESTORED -> SnackTexts("${snack.countA} · ${snack.countB}", "", SnackIcon.CHECK, pins = snack.countA, tapes = snack.countB)
        SnackKind.ELSEWHERE -> SnackTexts("${snack.countA} " + s(R.string.snack_elsewhere), s(R.string.action_clear), SnackIcon.PIN, SnackActionIcon.TRASH)
        SnackKind.UNSAVED -> SnackTexts(s(R.string.snack_unsaved), s(R.string.action_retry), SnackIcon.WARNING, SnackActionIcon.RETRY)
        SnackKind.SAVED -> SnackTexts(s(R.string.snack_saved), "", SnackIcon.CHECK)
        SnackKind.NO_SURFACE -> SnackTexts(s(R.string.snack_no_surface), "", SnackIcon.NO_SURFACE)
        SnackKind.NOT_MAPPED -> SnackTexts(s(R.string.snack_not_mapped), "", SnackIcon.SPINNER)
        SnackKind.DUPLICATE -> SnackTexts(s(R.string.snack_duplicate), "", SnackIcon.TAPE)
        SnackKind.CLEARED -> SnackTexts(s(R.string.snack_cleared), "", SnackIcon.CHECK)
        SnackKind.TAPE_LIMIT -> SnackTexts(s(R.string.snack_tape_limit), "", SnackIcon.TAPE)
        SnackKind.LOST -> SnackTexts(s(R.string.snack_lost), "", SnackIcon.NO_SURFACE)
        SnackKind.RECOVERED -> SnackTexts(s(R.string.snack_recovered), "", SnackIcon.CHECK)
        SnackKind.NO_SOURCE -> SnackTexts(s(R.string.snack_no_source), "", SnackIcon.WARNING)
    }
}

/**
 * Shows [snack] through the SpatialUI snackbar host (`LocalSnackbarHostState`). In SDK 6.1.9 that host
 * answers `SnackbarResult.Failure` inside a Stage, so the product host is [SnackPanel]. This path is
 * only taken when the debug command `overlay snackbar native` asks for it; it falls back to the panel
 * by itself and logs the answer.
 */
@Composable
internal fun SnackbarBridge(snack: SnackUi?, onEvent: (RulerEvent) -> Unit) {
    if (OverlayHosting.snackbar.value != OverlayHost.NATIVE) return
    val host = LocalSnackbarHostState.current
    val texts = snack?.let { snackTexts(it) }
    LaunchedEffect(snack?.id, texts?.message) {
        if (snack == null || texts == null) {
            host.dismiss()
            return@LaunchedEffect
        }
        val result =
            host.show(
                message = texts.message,
                description = texts.description,
                action = texts.action,
                duration = SnackbarDuration.custom(if (snack.kind.durationMs == SnackKind.STICKY) Long.MAX_VALUE / 4 else snack.kind.durationMs),
            )
        when (result) {
            is SnackbarResult.ActionPerformed -> onEvent(RulerEvent.SnackActionClicked(snack.id))
            is SnackbarResult.Dismissed -> onEvent(RulerEvent.SnackClosed(snack.id))
            is SnackbarResult.Failure -> {
                RulerLog.i("OVERLAY snackbar host=native shown=false reason=${result.details} -> host=panel")
                OverlayHosting.snackbar.value = OverlayHost.PANEL
            }
        }
    }
}

/**
 * The snackbar of the app (simplify spec H): a head-locked attachment panel 14 degrees above the view
 * centre, built to the metrics of the SpatialUI snackbar: 28 dp corners, `Material.Thick`, a leading
 * icon of 24 dp (or a 20 dp spinner), the message 16 / 20 at weight 600 on one line, and an optional
 * action button (icon + one word, `interaction` fill, tape ink). The capsule takes the width of its
 * message (240 - 560 dp) inside the fixed-size panel. Icons on the glass are `labelPrimary`.
 */
@Composable
internal fun SnackPanel(snack: SnackUi?, onEvent: (RulerEvent) -> Unit, watch: Modifier = Modifier, applied: Float = 1f, onWidth: (Float) -> Unit = {}) {
    Box(modifier = Modifier.size(SnackPanelWidth, SnackPanelHeight), contentAlignment = Alignment.Center) {
        if (snack == null) return@Box
        val texts = snackTexts(snack)
        val shape = RoundedCornerShape(28.dp)
        val density = LocalDensity.current
        val panelPx = with(density) { SnackPanelWidth.toPx() }
        val ink = PicoTheme.colorScheme.labelPrimary
        // F11: nothing is drawn until the capsule has its width, then it fades in over 150 ms
        val fade = remember(snack.id) { Animatable(0f) }
        LaunchedEffect(snack.id) { fade.animateTo(1f, tween(FADE_IN_MS)) }
        Row(
            modifier =
                watch
                    .widthIn(min = 240.dp, max = SnackPanelWidth)
                    .defaultMinSize(minHeight = 56.dp)
                    .onSizeChanged { onWidth(it.width / density.density) }
                    // the panel is made as narrow as the capsule and the capsule is drawn over all of it (see PanelRig, finding 4)
                    .graphicsLayer {
                        val own = size.width / panelPx
                        scaleX = 1f / applied
                        alpha = if (abs(applied - own) <= own * 0.12f) fade.value else 0f
                    }
                    .clip(shape)
                    .backgroundMaterial(enable = true, style = Material.Thick)
                    .probe("snackbar")
                    .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (texts.icon) {
                SnackIcon.SPINNER ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ink, progressSize = CircularProgressDefaults.circleProgressSize(20.dp))
                else ->
                    Icon(
                        painter =
                            painterResource(
                                when (texts.icon) {
                                    SnackIcon.CHECK -> R.drawable.ic_check
                                    SnackIcon.PIN -> R.drawable.ic_pin
                                    SnackIcon.WARNING -> R.drawable.ic_warning
                                    SnackIcon.NO_SURFACE -> R.drawable.ic_no_surface
                                    SnackIcon.TAPE, SnackIcon.SPINNER -> R.drawable.ic_tape
                                }
                            ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = ink,
                    )
            }
            val messageStyle = PicoTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")
            if (texts.pins >= 0) {
                val counts = rq(R.plurals.count_pins, texts.pins) + ", " + rq(R.plurals.count_tapes, texts.tapes)
                Row(
                    modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = counts },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconCount(R.drawable.ic_pin, texts.pins, 24.dp, ink, messageStyle)
                    IconCount(R.drawable.ic_tape, texts.tapes, 24.dp, ink, messageStyle)
                }
            } else {
                Text(
                    text = texts.message,
                    color = ink,
                    style = messageStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            if (texts.action.isNotEmpty()) {
                Button(
                    onClick = { onEvent(RulerEvent.SnackActionClicked(snack.id)) },
                    modifier = Modifier.probe("snackbar_action"),
                    size = ButtonDefaults.Regular.copy(height = 40.dp, minWidth = 96.dp),
                    shape = CircleShape,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PicoTheme.colorScheme.interaction,
                            contentColor = RulerColors.TapeInk,
                        ),
                ) {
                    Icon(
                        painter = painterResource(if (texts.actionIcon == SnackActionIcon.TRASH) R.drawable.ic_trash else R.drawable.ic_retry),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = RulerColors.TapeInk,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = texts.action, color = RulerColors.TapeInk, maxLines = 1)
                }
            }
        }
    }
}
