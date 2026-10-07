package io.embodit.spatialruler.ui.ruler.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import io.embodit.spatialruler.platform.RulerLog
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.RulerUiState
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Seams for the emulator test harness. Nothing here is product UI and nothing here does anything by
 * itself: the hooks are only driven by the debug-build receiver (`src/debug`). In a release build no
 * receiver exists, [RulerProbe.enabled] stays false and the bounds modifiers are no-ops.
 */
object RulerProbe {
    /** Set once at start-up from the debuggable flag of the app. */
    @Volatile var enabled: Boolean = false

    /** Bounds of one control inside its panel, panel-local pixels, origin top-left. */
    data class ControlBounds(val cx: Float, val cy: Float, val w: Float, val h: Float)

    private val controls = ConcurrentHashMap<String, ConcurrentHashMap<String, ControlBounds>>()
    private val panelSizes = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun report(panel: String, name: String, bounds: ControlBounds, panelWidth: Int, panelHeight: Int) {
        controls.getOrPut(panel) { ConcurrentHashMap() }[name] = bounds
        panelSizes[panel] = panelWidth to panelHeight
    }

    fun reportPanel(panel: String, width: Int, height: Int) {
        panelSizes[panel] = width to height
    }

    /** One line per pointer event a pooled panel receives (not for moves): `PTR panel=<id> ev=<type> x= y=`. */
    fun pointerEvent(panel: String, type: String, x: Float, y: Float) {
        RulerLog.i(String.format(Locale.US, "PTR panel=%s ev=%s x=%.1f y=%.1f", panel, type, x, y))
    }

    fun forget(panel: String, name: String) {
        controls[panel]?.remove(name)
    }

    fun controlsOf(panel: String): Map<String, ControlBounds> = controls[panel]?.toSortedMap() ?: emptyMap()

    fun sizeOf(panel: String): Pair<Int, Int>? = panelSizes[panel]

    /** Panels that are not attachment panels of the scene (system popups: menu, dialog, snackbar). */
    fun popupPanels(known: Set<String>): List<String> = controls.keys.filter { it !in known && controls[it]?.isNotEmpty() == true }.sorted()

    /** What the screen exposes to the debug receiver while it is composed. */
    interface Host {
        fun dispatch(event: RulerEvent)

        fun uiState(): RulerUiState

        fun dumpJson(): String

        /** Scene queries and test controls; see `RulerScene`. */
        fun scene(): SceneProbe?
    }

    /** The part of the 3D scene the harness may read or poke. */
    interface SceneProbe {
        /** `POSE hmd pos=... q(...) fwd=... up=... right=... pointer_eye=... eye_pos=...` (five decimals). */
        fun poseLine(): String

        /** `PANEL id=... pos=... q(...) scale=... size_px=WxH` for every visible panel (five decimals). */
        fun panelLines(): List<Pair<String, String>>

        /**
         * `RIG slot ...` for every pooled panel (shown or not: state, engine pose, where it lies in the
         * view), `RIG body ...`, every `RIG violation ...` that holds right now, and `RIG summary ...`.
         */
        fun rigLines(): List<String>

        fun planesSummary(): List<String>

        fun setSyntheticRay(values: FloatArray?): String

        /** The trigger of the synthetic pointer: "" = one full press, `half`, `full`, `release` = hold it there. */
        fun triggerSynthetic(stage: String): String

        /** Tool tip of the synthetic pointer (contact channel), Stage metres; null takes it away. */
        fun setSyntheticTip(values: FloatArray?): String

        /** The synthetic pointer disappears (a hand that leaves the view). */
        fun loseSynthetic(): String

        /** `controller`, `hand`, `system`, `synthetic`, or anything else for automatic selection. */
        fun forceSource(tag: String): String

        /** `SRC active=... forced=... trigger=... synthetic=... controller[...] hand[...]`: what every pointer source delivers right now. */
        fun sourceLine(): String

        /** Slides the body back to its designed pose now (lazy follow). */
        fun parkBody(): String

        /** Forces the aim to count as steady (true) or not (false); null: by its own timing. */
        fun forceSteady(on: Boolean?): String

        fun setLitMaterials(lit: Boolean): String

        /** Lit materials: metallic of the steel, the share of its own colour the steel emits, and the housing's emission (r, g, b). */
        fun tuneLitMaterials(steelMetallic: Float, steelGlow: Float, shellRed: Float, shellGreen: Float, shellBlue: Float): String

        /** Tone mapping of the unlit materials (off is the product setting). */
        fun setToneMapping(on: Boolean): String

        /** `FPS app=... frames=... ms=...` once a second while on. */
        fun setFpsLine(on: Boolean): String

        /** Makes the head tracker report lost tracking (on) or its real state (off). */
        fun forceTrackingLost(lost: Boolean): String

        /**
         * On: the body panel is hidden and the body's collider is off, so that every pinch aimed at the body
         * reaches what lies behind it: the state the guard in front of the body exists for.
         */
        fun bodyGhost(on: Boolean): String

        /**
         * The body's collider: null = the product's frame around the glass; a number = one box over the
         * whole housing with its front face that many millimetres behind the glass (to re-check which
         * presses on the panel such a box takes).
         */
        fun bodyCollider(behindGlassMm: Float?): String

        /** Shows / hides / moves the head-locked calibration panel of the harness. */
        fun calibration(on: Boolean, dist: Float, scale: Float, offX: Float, offY: Float, sizeDp: Int, facing: Boolean): String

        fun gestureMode(mode: String): String

        /** `auto`, `park`, `holder` or `panel`: how pooled panels are hidden (see `PanelRig.HideMode`). */
        fun hideMode(mode: String): String
    }

    @Volatile var host: Host? = null

    /** Extra head-locked attachment panel (id `calib`) supplied by the debug build; null in release. */
    val calibrationPanel = mutableStateOf<(@Composable () -> Unit)?>(null)

    /** `both`, `tap`, `drag` or `none`: which spatial gesture detectors the Stage installs. */
    val gestureMode = mutableStateOf("both")

    /**
     * Stretches the marks that only show for a moment (the seat mark of a snapped pin: 0.8 s), so that a
     * console screenshot can catch them. 1 = the designed time.
     */
    @Volatile var markStretch: Float = 1f
}

/** Id of the attachment panel (or popup) whose content is being composed. */
val LocalProbePanel = compositionLocalOf { "" }

/**
 * Registers the on-panel bounds of a control under a stable [name] so that the test harness can aim
 * at it (`bounds` debug command). A no-op unless the app is debuggable.
 */
@Composable
fun Modifier.probe(name: String): Modifier {
    if (!RulerProbe.enabled) return this
    val panel = LocalProbePanel.current
    DisposableEffect(panel, name) { onDispose { RulerProbe.forget(panel, name) } }
    return this.onGloballyPositioned { c ->
        if (c.isAttached) {
            // as drawn in the panel: a control inside a stretched layer reports its stretched bounds
            val root = c.findRootCoordinates()
            val topLeft = root.localPositionOf(c, Offset.Zero)
            val bottomRight = root.localPositionOf(c, Offset(c.size.width.toFloat(), c.size.height.toFloat()))
            RulerProbe.report(
                panel,
                name,
                RulerProbe.ControlBounds((topLeft.x + bottomRight.x) / 2f, (topLeft.y + bottomRight.y) / 2f, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                root.size.width,
                root.size.height,
            )
        }
    }
}

/** Like [probe] but splits a stepper into its minus (left) and plus (right) key areas. */
@Composable
fun Modifier.probeStepper(minusName: String, plusName: String): Modifier {
    if (!RulerProbe.enabled) return this
    val panel = LocalProbePanel.current
    DisposableEffect(panel, minusName, plusName) {
        onDispose {
            RulerProbe.forget(panel, minusName)
            RulerProbe.forget(panel, plusName)
        }
    }
    return this.onGloballyPositioned { c ->
        if (c.isAttached) {
            val p = c.positionInRoot()
            val root = c.findRootCoordinates().size
            val h = c.size.height.toFloat()
            val key = minOf(h, c.size.width / 3f)
            RulerProbe.report(panel, minusName, RulerProbe.ControlBounds(p.x + key / 2f, p.y + h / 2f, key, h), root.width, root.height)
            RulerProbe.report(panel, plusName, RulerProbe.ControlBounds(p.x + c.size.width - key / 2f, p.y + h / 2f, key, h), root.width, root.height)
        }
    }
}
