package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.windows.SnackbarHost
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import io.embodit.spatialruler.ui.ruler.CHIP_SLOTS
import io.embodit.spatialruler.ui.ruler.PLATE_SLOTS
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.RulerUiState
import io.embodit.spatialruler.ui.ruler.debug.LocalProbePanel
import io.embodit.spatialruler.ui.ruler.debug.RulerProbe
import io.embodit.spatialruler.ui.ruler.scene.PanelIds
import io.embodit.spatialruler.ui.ruler.scene.RulerScene

/**
 * The Stage content: one full-size SpatialView that carries the 3D scene and the panel pool.
 *
 * Every attachment panel is declared here once, with a fixed size, whether it is showing or not
 * (a panel created or resized on demand takes seconds to appear on the emulator and shows stale
 * rows). [RulerScene] parents each panel entity under its holder and shows or hides the holder.
 *
 * Window model: single panel with popups. The body panel is the only standing panel; plates, chips
 * and tags ride on 3D objects; menu, dialog and snackbar are floating surfaces.
 */
@Composable
internal fun RulerStage(state: RulerUiState, scene: RulerScene, onEvent: (RulerEvent) -> Unit) {
    val context = LocalContext.current
    val bindTick = scene.bindTick.intValue
    val gestureMode = RulerProbe.gestureMode.value
    val calibrationPanel = RulerProbe.calibrationPanel.value
    val tapOn = gestureMode == "both" || gestureMode == "tap"
    val dragOn = gestureMode == "both" || gestureMode == "drag"

    SpatialView(
        modifier =
            Modifier
                .fillMaxSize()
                // Tap and drag live in separate pointerInput blocks: two spatial recognizers must
                // not share one block. A tap sets a pin or picks one; a drag is a press-and-pull or,
                // with fine-tune open, slides the selected pin (see RulerScene.onSpatialDrag).
                .then(
                    if (tapOn) {
                        Modifier.pointerInput(gestureMode) {
                            detectSpatialTapGesture(context = context, targetedToEntity = TargetEntity.any()) { value ->
                                scene.onSpatialTap(value)
                            }
                        }
                    } else Modifier
                )
                .then(
                    if (dragOn) {
                        Modifier.pointerInput(gestureMode) {
                            detectSpatialDragGesture(
                                context = context,
                                targetedToEntity = TargetEntity.any(),
                                onDragStart = { start, value -> scene.onSpatialDrag("start", start, value) },
                                onDragEnd = { value -> scene.onSpatialDrag("end", null, value) },
                                onDragCancel = { value -> scene.onSpatialDrag("cancel", null, value) },
                            ) { value ->
                                scene.onSpatialDrag("move", null, value)
                            }
                        }
                    } else Modifier
                ),
        attachments = {
            AttachmentPanel(PanelIds.BODY) {
                PoolPanel(PanelIds.BODY, scene, watched = true) { _, _ ->
                    // floating surfaces belong to the standing panel; see OverlayHosting
                    SnackbarHost {
                        BodyPanel(
                            state = state,
                            liveLength = scene.liveLengths[RulerScene.LIVE],
                            onEvent = onEvent,
                            // the scene builds its meshes only after the instrument's face has drawn
                            modifier = Modifier.drawWithContent {
                                drawContent()
                                scene.onBodyPanelDrawn()
                            },
                        )
                        ClearDialog(dialog = state.clearDialog, onEvent = onEvent)
                        SnackbarBridge(snack = state.snack, onEvent = onEvent)
                    }
                }
            }
            repeat(PLATE_SLOTS) { slot ->
                AttachmentPanel(PanelIds.plate(slot)) {
                    PoolPanel(PanelIds.plate(slot), scene, guarded = true, hugs = true) { inView, watch ->
                        val plate = if (inView) state.plates.getOrNull(slot) else null
                        ReadingPlate(
                            plate = plate,
                            unit = state.unit,
                            live = plate?.let { scene.liveLengths[it.tapeId] },
                            applied = scene.panelStretch[PanelIds.plate(slot)] ?: 1f,
                            onPill = { scene.reportPill(slot, it) },
                            onEvent = onEvent,
                            watch = watch,
                            hover = !state.pinDragging && (scene.plateHover[PanelIds.plate(slot)] ?: true),
                        )
                    }
                }
            }
            AttachmentPanel(PanelIds.LIVE_PLATE) {
                // not a control: nothing of it takes the pointer
                PoolPanel(PanelIds.LIVE_PLATE, scene, hugs = true) { inView, _ ->
                    LivePlate(
                        length = if (inView) scene.liveLengths[RulerScene.LIVE] else null,
                        tolerance = if (inView) scene.liveTolerance.value else null,
                        unit = state.unit,
                        applied = scene.panelStretch[PanelIds.LIVE_PLATE] ?: 1f,
                        onPill = { scene.reportPill(PLATE_SLOTS, it) },
                    )
                }
            }
            repeat(CHIP_SLOTS) { slot ->
                AttachmentPanel(PanelIds.chip(slot)) {
                    PoolPanel(PanelIds.chip(slot), scene, guarded = true, hugs = true) { inView, watch ->
                        CandidateChip(chip = if (inView) state.chips.getOrNull(slot) else null, onEvent = onEvent, watch = watch)
                    }
                }
            }
            AttachmentPanel(PanelIds.PIN_TAG) {
                PoolPanel(PanelIds.PIN_TAG, scene, guarded = true) { inView, _ -> PinTag(tag = if (inView) state.pinTag else null, onEvent = onEvent) }
            }
            AttachmentPanel(PanelIds.TAPE_TAG) {
                PoolPanel(PanelIds.TAPE_TAG, scene, guarded = true) { inView, _ -> TapeTag(tag = if (inView) state.tapeTag else null, onEvent = onEvent) }
            }
            AttachmentPanel(PanelIds.RETICLE_TAG) {
                // not a control: nothing of it takes the pointer
                PoolPanel(PanelIds.RETICLE_TAG, scene, hugs = true) { inView, _ ->
                    ReticleTag(
                        label = if (inView) scene.reticleLabel.value else null,
                        applied = scene.panelStretch[PanelIds.RETICLE_TAG] ?: 1f,
                        onWidth = { scene.reportTag(it) },
                    )
                }
            }
            // the More menu: language segment, snap toggle, Clear all, Exit
            AttachmentPanel(PanelIds.MENU) {
                PoolPanel(PanelIds.MENU, scene) { inView, _ -> MenuPanel(state = state, filled = inView, onEvent = onEvent) }
            }
            // dialog and snackbar: the SpatialUI components do not work in a Stage, these panels are the product hosts
            AttachmentPanel(PanelIds.DIALOG) {
                PoolPanel(PanelIds.DIALOG, scene, guarded = true) { inView, _ ->
                    DialogPanel(dialog = if (inView) state.clearDialog else null, blocked = inView && state.blocked, onEvent = onEvent)
                }
            }
            AttachmentPanel(PanelIds.SNACK) {
                PoolPanel(PanelIds.SNACK, scene, hugs = true) { inView, watch ->
                    SnackPanel(
                        snack = if (inView) state.snack else null,
                        onEvent = onEvent,
                        watch = watch,
                        applied = scene.panelStretch[PanelIds.SNACK] ?: 1f,
                        onWidth = { scene.reportSnack(it) },
                    )
                }
            }
            // first-run coachmark beside the body (design "引导气泡")
            AttachmentPanel(PanelIds.COACH) {
                PoolPanel(PanelIds.COACH, scene) { inView, _ -> CoachPanel(step = if (inView) state.coachStep else 0, onEvent = onEvent) }
            }
            // test-harness calibration target; exists only while the debug build asks for it
            if (calibrationPanel != null) {
                AttachmentPanel(PanelIds.CALIBRATION) { calibrationPanel() }
            }
        },
        update = { _, attachments ->
            // reading the tick makes this block run again while panel entities are still missing
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}

/**
 * Watches the pointer of a pooled panel before its content does.
 *
 * [guarded]: a press whose line of sight runs through the instrument body is taken away from the panel
 * (see `RulerScene.pressGuarded`): a room panel behind the body must not act on a pinch that was aimed
 * at the body. [logged] (debuggable builds): `PTR panel=<id> ev=Press|Release|Enter|Exit x= y=`, the
 * test harness' sign of which panel the system pointer reached.
 *
 * Positions are panel pixels, whatever part of the panel is watched ([place] keeps where that part
 * lies). The watcher sits on the part of the panel that is drawn: the root for a panel that is filled,
 * the pill, chip or capsule for a panel whose content is smaller.
 */
private fun Modifier.panelPointer(id: String, scene: RulerScene, guarded: Boolean, logged: Boolean, place: WatchedPlace): Modifier =
    onGloballyPositioned { place.coordinates = it }
        .pointerInput(id, guarded, logged) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Move) continue
                    if (event.type == PointerEventType.Enter && id.startsWith("plate")) scene.plateEntered(id)
                    val local = event.changes.firstOrNull()?.position
                    val at = if (local == null) null else place.coordinates?.takeIf { it.isAttached }?.localToRoot(local) ?: local
                    if (id == PanelIds.BODY && (event.type == PointerEventType.Press || event.type == PointerEventType.Release)) {
                        scene.onBodyPointer(event.type == PointerEventType.Press)
                    } else {
                        scene.noteSystemInput()
                    }
                    if (logged) RulerProbe.pointerEvent(id, event.type.toString(), at?.x ?: 0f, at?.y ?: 0f)
                    if (guarded && at != null && event.type == PointerEventType.Press && scene.pressGuarded(id, at.x, at.y)) {
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }

/** Where the watched part of a panel lies in its panel (a stretched pill is not where its layout says). */
private class WatchedPlace {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Root of a pooled panel. [content] gets `inView`: the panel has been moved into view. A pooled panel
 * must draw its content only then (a fixed-size empty panel otherwise): what it draws while parked
 * out of sight stays broken on the emulator when it is shown (black glass, a few rows of content).
 * It also names the panel for the test harness and reports its pixel size.
 *
 * [hugs]: the content is smaller than the panel and the rest must let the pointer through. The content
 * then gets the pointer watcher as `watch` and puts it on its drawn part; otherwise the watcher sits on
 * the root and `watch` is empty.
 */
@Composable
private fun PoolPanel(
    id: String,
    scene: RulerScene,
    guarded: Boolean = false,
    hugs: Boolean = false,
    watched: Boolean = false,
    content: @Composable (inView: Boolean, watch: Modifier) -> Unit,
) {
    val inView = scene.panelShown[id] == true
    val logged = RulerProbe.enabled
    val place = remember { WatchedPlace() }
    val watcher = if (logged || guarded || watched) Modifier.panelPointer(id, scene, guarded, logged, place) else Modifier
    CompositionLocalProvider(LocalProbePanel provides id) {
        when {
            hugs -> {
                // the size report takes no pointer: a box without a pointer modifier is not hit
                if (logged) {
                    Box(modifier = Modifier.onGloballyPositioned { RulerProbe.reportPanel(id, it.size.width, it.size.height) }) { content(inView, watcher) }
                } else {
                    content(inView, watcher)
                }
            }
            logged || guarded || watched -> {
                val sized = if (logged) Modifier.onGloballyPositioned { RulerProbe.reportPanel(id, it.size.width, it.size.height) } else Modifier
                Box(modifier = sized.then(watcher)) { content(inView, Modifier) }
            }
            else -> content(inView, Modifier)
        }
    }
}
