package io.embodit.spatialruler.debug

import android.content.Context
import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.platform.RulerLog
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.RulerUiState
import io.embodit.spatialruler.ui.ruler.components.OverlayHost
import io.embodit.spatialruler.ui.ruler.components.OverlayHosting
import io.embodit.spatialruler.ui.ruler.components.localizedResources
import io.embodit.spatialruler.ui.ruler.components.snackTexts
import io.embodit.spatialruler.ui.ruler.debug.RulerProbe
import java.io.File
import java.util.Locale
import org.json.JSONObject

/**
 * Test hooks of the debug build. One log line (tag `SpatialRuler`) answers every command.
 *
 * Measuring
 * - `pointer` a=ox,oy,oz,dx,dy,dz   synthetic pointing ray in Stage metres (no numbers: remove it)
 * - `trigger` [s=half|full|release]  no word: one full press of the synthetic pointer (same code path as a real
 *                                    one); half = hold it half pressed (4 : 1 fine aim), full = hold it fully
 *                                    pressed (commits once), release = let go
 * - `contact` a=x,y,z                tool tip of the synthetic pointer (contact channel); no numbers: remove it
 * - `hand_lost`                      the synthetic pointer disappears (ray, tip and trigger)
 * - `source` s=controller|hand|system|synthetic|auto   which pointer aims. `controller` / `hand`: that device,
 *                                    and while it delivers nothing the synthetic pointer stands in for it (its
 *                                    pins are logged `via=controller` / `via=hand`); `system`: taps only; `auto`:
 *                                    synthetic while set, else controller, else hand, else system
 * - `steady` s=on|off|auto           the aim counts as steady / as not steady / by its own timing (0.35 s still)
 * - `body` s=park                    slides the body back to its designed pose now (lazy follow; logs `BODY follow`, `BODY park`)
 * - `src`                            `SRC active=... forced=... trigger=... synthetic=... controller[origin= dir= tip= press=
 *                                    pressed= ready= hand=] hand[...]`: what every pointer source delivers right now
 * - `place` a=x,y,z                  pin at a Stage point (attached to the nearest detected surface within 5 cm)
 * - `tap_pin` s=<id>                 same as a tap on that pin
 * - `connect` s="<idA> <idB>"        tape between two pins
 * - `undo` | `delete` s=<id> | `clear`
 * - `unit` s=m|cm|mm|ftin | `lang` s=system|zh|en | `snap` s=on|off
 * - `fail_next_anchor`               the next anchor creation fails (unsaved pin + retry snackbar; also the next anchor rebase)
 * - `coach` s=reset                  the first-run coachmark shows again
 * - `ui` s=<control name>            press a panel control by its stable name (see `bounds`)
 *
 * Designed states that the emulator never reaches by itself
 * - `tracking` s=lost|ok             the head tracker reports lost tracking (readings freeze) / its real state
 * - `blocked` s=on|off               on: the session starts again as "spatial features unavailable" (dialog with
 *                                    Retry / Exit); off: lifts it, press Retry to continue
 * - `elsewhere` a=<n>                treats the last n pins as "not in this space" (hidden, snackbar "Clear them")
 * - `snack` s=long|normal            timed snackbars stay 60 s / their designed time
 * - `marks` s=long|normal            the seat mark of a snapped pin stays 10 s / its designed 0.8 s
 *
 * Reading back
 * - `dump`                           the whole model as one JSON line (`DUMP {...}`), also written to files/dump.json;
 *                                    includes the snackbar's text and its remaining time
 * - `pose`                           `POSE hmd pos=(x, y, z) q(x, y, z, w) fwd=... pointer_eye=... eye_pos=...`, five decimals
 * - `bounds`                         `PANEL id=...` for every visible panel and `CTRL panel=... name=...` for its controls
 * - `rig`                            `RIG slot ...` for every pooled panel, `RIG body ...`, `RIG violation ...`, `RIG summary ...`
 * - `planes`                         `PLANE <label> kind=... center=... normal=... verts=...`
 * - `fps` s=on|off                   `FPS app=... frames=... ms=...` once a second
 *
 * Harness
 * - `calib` s=on|off|clear a=dist,scale,sizeDp[,offX,offY]   head-locked calibration target
 * - `gest` s=both|tap|drag|none      which spatial gesture detectors are installed
 * - `overlay` s="[dialog|snackbar] native|panel"         host of the dialog / snackbar (the menu is always a panel)
 * - `lit` s=on|off                   physically based or unlit materials for steel and the body
 * - `lit` a=metallic,steelGlow,r,g,b  tune the lit materials: metallic of the steel, the share of its own colour the
 *                                    steel emits, and the emission of the yellow housing
 * - `tonemap` s=on|off               tone mapping of the unlit materials (off is the product setting)
 * - `ghost` s=on|off                 on: the body panel is hidden and its collider is off, so pinches aimed at the
 *                                    body reach what is behind it; the guard must answer `TAP blocked by=body ...`
 * - `collider` [a=<mm>]               the body's collider: no number = the product's frame around the glass;
 *                                    a number = one box over the housing, its front face that far behind the glass
 * - `hidemode` s=auto|park|holder|panel   how pooled panels are hidden (default auto; `park` reproduces the
 *                                    dead pointer ray through a head anchor that holds a parked panel)
 */
internal object DebugCommands {
    private var calibDist = 0.9f
    private var calibScale = 1f
    private var calibSizeDp = 480
    private var calibOffX = 0f
    private var calibOffY = 0f

    fun run(context: Context, raw: String, a: String?, s: String?) {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val name = tokens.first().lowercase(Locale.US)
        val rest = tokens.drop(1)
        val numbers = floats(a ?: rest.joinToString(","))
        val text = (s ?: rest.joinToString(" ")).trim()
        val host = RulerProbe.host
        if (host == null) {
            RulerLog.i("CMD $name ignored: the Stage is not running")
            return
        }
        try {
            dispatch(context, host, name, numbers, text)
        } catch (t: Throwable) {
            RulerLog.e("CMD $name failed: $t", t)
        }
    }

    private fun dispatch(context: Context, host: RulerProbe.Host, name: String, numbers: List<Float>, text: String) {
        val scene = host.scene()
        val word = text.lowercase(Locale.US)
        when (name) {
            "pointer" -> RulerLog.i(scene?.setSyntheticRay(if (numbers.size >= 6) numbers.toFloatArray() else null) ?: NO_SCENE)
            "trigger" -> RulerLog.i(scene?.triggerSynthetic(word) ?: NO_SCENE)
            "contact" -> RulerLog.i(scene?.setSyntheticTip(if (numbers.size >= 3) numbers.toFloatArray() else null) ?: NO_SCENE)
            "hand_lost" -> RulerLog.i(scene?.loseSynthetic() ?: NO_SCENE)
            "source" -> RulerLog.i(scene?.forceSource(word) ?: NO_SCENE)
            "src" -> RulerLog.i(scene?.sourceLine() ?: NO_SCENE)
            "body" -> RulerLog.i(if (word == "park") scene?.parkBody() ?: NO_SCENE else "CMD body needs s=park")
            "steady" -> RulerLog.i(scene?.forceSteady(if (word == "on") true else if (word == "off") false else null) ?: NO_SCENE)
            "place" -> {
                if (numbers.size < 3) return RulerLog.i("CMD place needs a=x,y,z")
                host.dispatch(RulerEvent.DebugPlace(Vec3(numbers[0], numbers[1], numbers[2])))
            }
            "tap_pin" -> {
                RulerLog.i("TAP pin id=$text kind=Debug stage=null")
                host.dispatch(RulerEvent.PinTapped(text))
            }
            "connect" -> {
                val ids = text.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                if (ids.size < 2) return RulerLog.i("CMD connect needs s=\"<idA> <idB>\"")
                host.dispatch(RulerEvent.DebugConnect(ids[0], ids[1]))
            }
            "undo" -> host.dispatch(RulerEvent.UndoClicked)
            "delete" -> host.dispatch(RulerEvent.DebugDelete(text))
            "clear" -> host.dispatch(RulerEvent.DebugClear)
            "unit" -> {
                val unit = RulerUnit.fromTag(word) ?: return RulerLog.i("CMD unit unknown '$text'")
                host.dispatch(RulerEvent.UnitSelected(unit))
            }
            "lang" -> {
                val language = LanguagePref.fromTag(word) ?: return RulerLog.i("CMD lang unknown '$text'")
                host.dispatch(RulerEvent.LanguageSelected(language))
            }
            "snap" -> host.dispatch(RulerEvent.SnapToggled(word == "on"))
            "fail_next_anchor" -> host.dispatch(RulerEvent.DebugFailNextAnchor)
            "coach" -> if (word == "reset") host.dispatch(RulerEvent.DebugCoachReset) else RulerLog.i("CMD coach needs s=reset")
            "ui" -> {
                val event = controlEvent(text, host.uiState()) ?: return RulerLog.i("CMD ui unknown or unavailable control '$text'")
                host.dispatch(event)
            }
            "tracking" -> RulerLog.i(scene?.forceTrackingLost(word == "lost") ?: NO_SCENE)
            "blocked" -> host.dispatch(RulerEvent.DebugBlocked(word != "off"))
            "elsewhere" -> host.dispatch(RulerEvent.DebugElsewhere(numbers.firstOrNull()?.toInt() ?: word.toIntOrNull() ?: 1))
            "snack" -> host.dispatch(RulerEvent.DebugLongSnacks(word == "long" || word == "on"))
            "marks" -> {
                RulerProbe.markStretch = if (word == "long" || word == "on") 12.5f else 1f
                RulerLog.i("MARKS stretch=${RulerProbe.markStretch}")
            }
            "dump" -> dump(context, host)
            "pose" -> RulerLog.i(scene?.poseLine() ?: NO_SCENE)
            "bounds" -> bounds(scene)
            "rig" -> {
                val lines = scene?.rigLines() ?: return RulerLog.i(NO_SCENE)
                lines.forEach { RulerLog.i(it) }
            }
            "planes" -> {
                val lines = scene?.planesSummary() ?: emptyList()
                lines.forEach { RulerLog.i(it) }
                RulerLog.i("PLANES listed n=${lines.size}")
            }
            "fps" -> RulerLog.i(scene?.setFpsLine(word != "off") ?: NO_SCENE)
            "calib" -> calibration(scene, word, numbers)
            "gest" -> RulerLog.i(scene?.gestureMode(word.ifEmpty { "both" }) ?: NO_SCENE)
            "hidemode" -> RulerLog.i(scene?.hideMode(word) ?: NO_SCENE)
            "overlay" -> overlay(word)
            "lit" ->
                if (numbers.size >= 5) RulerLog.i(scene?.tuneLitMaterials(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4]) ?: NO_SCENE)
                else RulerLog.i(scene?.setLitMaterials(word != "off") ?: NO_SCENE)
            "tonemap" -> RulerLog.i(scene?.setToneMapping(word != "off") ?: NO_SCENE)
            "collider" -> RulerLog.i(scene?.bodyCollider(numbers.firstOrNull()) ?: NO_SCENE)
            "ghost" -> RulerLog.i(scene?.bodyGhost(word != "off") ?: NO_SCENE)
            else -> RulerLog.i("CMD unknown '$name'")
        }
    }

    /** The event a control with this stable name sends when it is pressed. */
    private fun controlEvent(name: String, state: RulerUiState): RulerEvent? =
        when {
            name == "undo" -> RulerEvent.UndoClicked
            name == "snap" -> RulerEvent.SnapToggled(!state.snapEnabled)
            name == "more" -> RulerEvent.MoreClicked
            name.startsWith("unit_") -> RulerUnit.fromTag(name.removePrefix("unit_"))?.let { RulerEvent.UnitSelected(it) }
            name == "pin_remove" -> state.pinTag?.let { RulerEvent.PinRemoveClicked }
            name == "pin_close" -> state.pinTag?.let { RulerEvent.PinTagClosed }
            name == "step_u_minus" -> RulerEvent.Stepped(NudgeAxis.U, -1)
            name == "step_u_plus" -> RulerEvent.Stepped(NudgeAxis.U, 1)
            name == "step_v_minus" -> RulerEvent.Stepped(NudgeAxis.V, -1)
            name == "step_v_plus" -> RulerEvent.Stepped(NudgeAxis.V, 1)
            name == "tape_retract" -> state.tapeTag?.let { RulerEvent.TapeRetractClicked }
            name == "coach_got_it" -> if (state.coachStep > 0) RulerEvent.CoachDismissed else null
            name == "menu_clear" -> RulerEvent.ClearAllClicked
            name == "menu_exit" -> RulerEvent.ExitClicked
            name.startsWith("lang_") -> LanguagePref.fromTag(name.removePrefix("lang_"))?.let { RulerEvent.LanguageSelected(it) }
            name == "dialog_cancel" -> RulerEvent.DialogDismissed
            name == "dialog_confirm" -> RulerEvent.ClearConfirmed
            name == "blocked_retry" -> if (state.blocked) RulerEvent.BlockedRetryClicked else null
            name == "blocked_exit" -> if (state.blocked) RulerEvent.BlockedExitClicked else null
            name == "snackbar_action" -> state.snack?.let { RulerEvent.SnackActionClicked(it.id) }
            name.startsWith("chip:") -> RulerEvent.ChipClicked(name.removePrefix("chip:"))
            name.startsWith("plate:") -> RulerEvent.PlateClicked(name.removePrefix("plate:"))
            else -> null
        }

    private fun dump(context: Context, host: RulerProbe.Host) {
        var json = host.dumpJson()
        // the snackbar as the user reads it, in the language chosen in the app
        val state = host.uiState()
        val snack = state.snack
        if (snack != null) {
            runCatching {
                val texts = snackTexts(localizedResources(context, state.language), snack)
                json = JSONObject(json).put("snack_text", texts.message).put("snack_desc", texts.description).put("snack_action", texts.action).toString()
            }
        }
        runCatching { File(context.getExternalFilesDir(null), "dump.json").writeText(json) }
        if (json.length <= LOG_CHUNK) {
            RulerLog.i("DUMP $json")
        } else {
            // logcat cuts long lines: the whole JSON is in files/dump.json, the log carries it in parts
            val parts = json.chunked(LOG_CHUNK)
            parts.forEachIndexed { i, part -> RulerLog.i("DUMP+ ${i + 1}/${parts.size} $part") }
        }
    }

    /** `PANEL ...` for every visible panel, each followed by the `CTRL ...` lines of its controls. */
    private fun bounds(scene: RulerProbe.SceneProbe?) {
        val panels = scene?.panelLines() ?: emptyList()
        var controls = 0
        val shown = HashSet<String>()
        for ((id, line) in panels) {
            shown += id
            RulerLog.i(line)
            controls += controlLines(id)
        }
        // SpatialUI popups (the More menu) are not attachment panels: no pose, pixel bounds inside the popup only
        for (id in RulerProbe.popupPanels(known = KNOWN_PANELS)) {
            val size = RulerProbe.sizeOf(id)
            RulerLog.i("PANEL id=$id popup=true pos=null q=null scale=null size_px=${size?.first ?: 0}x${size?.second ?: 0}")
            controls += controlLines(id)
        }
        RulerLog.i("BOUNDS panels=${panels.size} controls=$controls")
    }

    private fun controlLines(panel: String): Int {
        val controls = RulerProbe.controlsOf(panel)
        for ((name, b) in controls) {
            RulerLog.i(String.format(Locale.US, "CTRL panel=%s name=%s cx=%.1f cy=%.1f w=%.1f h=%.1f", panel, name, b.cx, b.cy, b.w, b.h))
        }
        return controls.size
    }

    private fun calibration(scene: RulerProbe.SceneProbe?, mode: String, numbers: List<Float>) {
        if (scene == null) return RulerLog.i(NO_SCENE)
        when (mode) {
            "off" -> {
                // the scene lets go of the panel entity first, then the attachment leaves the composition
                scene.calibration(false, calibDist, calibScale, calibOffX, calibOffY, calibSizeDp, false)
                RulerProbe.calibrationPanel.value = null
                RulerLog.i("CALIB off")
            }
            "clear" -> {
                CalibState.clear()
                RulerLog.i("CALIB cleared")
            }
            else -> {
                if (numbers.isNotEmpty()) calibDist = numbers[0]
                if (numbers.size >= 2) calibScale = numbers[1]
                if (numbers.size >= 3) calibSizeDp = numbers[2].toInt()
                if (numbers.size >= 5) {
                    calibOffX = numbers[3]
                    calibOffY = numbers[4]
                }
                CalibState.clear()
                CalibState.dist = calibDist
                CalibState.scale = calibScale
                val sizeDp = calibSizeDp
                RulerProbe.calibrationPanel.value = { CalibPanel(sizeDp) }
                // a sixth number of 1 turns the target towards the head (default: parallel to the image plane)
                val facing = numbers.size >= 6 && numbers[5] >= 0.5f
                RulerLog.i(scene.calibration(true, calibDist, calibScale, calibOffX, calibOffY, calibSizeDp, facing))
            }
        }
    }

    private fun overlay(text: String) {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val host = if (words.lastOrNull() == "panel") OverlayHost.PANEL else OverlayHost.NATIVE
        val which = if (words.size >= 2) words.first() else "all"
        if (which == "menu") RulerLog.i("OVERLAY menu has only the panel host since round 4")
        if (which == "all" || which == "dialog") OverlayHosting.dialog.value = host
        if (which == "all" || which == "snackbar") OverlayHosting.snackbar.value = host
        RulerLog.i("OVERLAY menu=PANEL dialog=${OverlayHosting.dialog.value} snackbar=${OverlayHosting.snackbar.value}")
    }

    private fun floats(text: String): List<Float> = text.split(Regex("[,\\s]+")).mapNotNull { it.trim().toFloatOrNull() }

    private const val NO_SCENE = "CMD ignored: no scene"
    private const val LOG_CHUNK = 3500
    private val KNOWN_PANELS: Set<String> =
        io.embodit.spatialruler.ui.ruler.scene.PanelIds.pool.toSet() + io.embodit.spatialruler.ui.ruler.scene.PanelIds.CALIBRATION
}
