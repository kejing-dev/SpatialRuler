package io.embodit.spatialruler.ui.ruler.scene

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.gesture.SpatialDragValue
import com.pico.spatial.ui.foundation.gesture.SpatialTapValue
import com.pico.spatial.ui.geometry.Offset3D
import io.embodit.spatialruler.domain.model.MenuState
import io.embodit.spatialruler.domain.model.Phase
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.UnitSystem
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.ClearSurfaceUseCase
import io.embodit.spatialruler.domain.usecase.FacePanelUseCase
import io.embodit.spatialruler.domain.usecase.HitSurfaceUseCase
import io.embodit.spatialruler.domain.usecase.LayoutTapeUseCase
import io.embodit.spatialruler.domain.usecase.PlacePlateUseCase
import io.embodit.spatialruler.domain.usecase.AdaptiveGain
import io.embodit.spatialruler.domain.usecase.ComputeToleranceUseCase
import io.embodit.spatialruler.domain.usecase.DragPinUseCase
import io.embodit.spatialruler.domain.usecase.PlaceTagUseCase
import io.embodit.spatialruler.domain.usecase.PlateChoice
import io.embodit.spatialruler.domain.usecase.PlatePose
import io.embodit.spatialruler.domain.usecase.PlateTape
import io.embodit.spatialruler.domain.usecase.PullLineUseCase
import io.embodit.spatialruler.domain.usecase.ResolvePlacementUseCase
import io.embodit.spatialruler.domain.usecase.ScaleForDistanceUseCase
import io.embodit.spatialruler.domain.usecase.SelectTickLevelUseCase
import io.embodit.spatialruler.domain.usecase.SnapPointUseCase
import io.embodit.spatialruler.domain.usecase.TagKind
import io.embodit.spatialruler.domain.usecase.TagPlacement
import io.embodit.spatialruler.domain.usecase.TagScene
import io.embodit.spatialruler.domain.usecase.ViewFrame
import io.embodit.spatialruler.domain.usecase.ViewObject
import io.embodit.spatialruler.domain.usecase.ViewRect
import io.embodit.spatialruler.domain.usecase.ViewSegment
import io.embodit.spatialruler.platform.RulerGraph
import io.embodit.spatialruler.platform.RulerLog
import io.embodit.spatialruler.platform.input.SystemPointer
import io.embodit.spatialruler.platform.sense.PlaneTracker
import io.embodit.spatialruler.platform.tracking.HeadPose
import io.embodit.spatialruler.ui.ruler.CHIP_SLOTS
import io.embodit.spatialruler.ui.ruler.DragMode
import io.embodit.spatialruler.ui.ruler.PLATE_SLOTS
import io.embodit.spatialruler.ui.ruler.RulerAim
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.RulerUiState
import io.embodit.spatialruler.ui.ruler.TapeUi
import io.embodit.spatialruler.ui.ruler.components.BodyKeys
import io.embodit.spatialruler.ui.ruler.components.BodyPanelHeight
import io.embodit.spatialruler.ui.ruler.components.BodyPanelWidth
import io.embodit.spatialruler.ui.ruler.components.ChipPanelHeight
import io.embodit.spatialruler.ui.ruler.components.ChipPanelWidth
import io.embodit.spatialruler.ui.ruler.components.ChipPillHeight
import io.embodit.spatialruler.ui.ruler.components.ChipPillWidth
import io.embodit.spatialruler.ui.ruler.components.DialogPanelHeight
import io.embodit.spatialruler.ui.ruler.components.DialogPanelWidth
import io.embodit.spatialruler.ui.ruler.components.MenuPanelHeight
import io.embodit.spatialruler.ui.ruler.components.MenuPanelWidth
import io.embodit.spatialruler.ui.ruler.components.OverlayHost
import io.embodit.spatialruler.ui.ruler.components.CoachPanelHeight
import io.embodit.spatialruler.ui.ruler.components.CoachPanelWidth
import io.embodit.spatialruler.ui.ruler.components.OverlayHosting
import io.embodit.spatialruler.ui.ruler.components.PinTagHeight
import io.embodit.spatialruler.ui.ruler.components.PlateHeight
import io.embodit.spatialruler.ui.ruler.components.PlateWidth
import io.embodit.spatialruler.ui.ruler.components.SnackPanelHeight
import io.embodit.spatialruler.ui.ruler.components.SnackPanelWidth
import io.embodit.spatialruler.ui.ruler.components.TagWidth
import io.embodit.spatialruler.ui.ruler.components.TapeTagHeight
import io.embodit.spatialruler.ui.ruler.components.TapeTagWidth
import io.embodit.spatialruler.ui.ruler.debug.RulerProbe
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.usecase.CommitWindow
import io.embodit.spatialruler.domain.usecase.FindContactUseCase
import io.embodit.spatialruler.domain.usecase.FineAim
import io.embodit.spatialruler.domain.usecase.GradeAimUseCase
import io.embodit.spatialruler.domain.usecase.PinchLatch
import io.embodit.spatialruler.domain.usecase.PointerChannel
import io.embodit.spatialruler.domain.usecase.SelectPointerSourceUseCase
import io.embodit.spatialruler.domain.usecase.SteadyAim
import io.embodit.spatialruler.domain.usecase.TriggerStage
import io.embodit.spatialruler.domain.usecase.TriggerStages
import io.embodit.spatialruler.platform.input.ControllerPointerSource
import io.embodit.spatialruler.platform.input.HandPointerSource
import io.embodit.spatialruler.ui.ruler.AimPreview
import io.embodit.spatialruler.ui.ruler.ReticleLabel
import io.embodit.spatialruler.ui.ruler.components.ReticleTagHeight
import io.embodit.spatialruler.ui.ruler.components.ReticleTagWidth
import androidx.compose.runtime.mutableStateOf

/** Ids of the attachment panels; the Compose side declares exactly these, once. */
/** What the live plate's second line says: the tolerance the tape would get, and whether the aim is far ("▲"). */
data class LiveTolerance(val mm: Int, val far: Boolean)

object PanelIds {
    const val BODY = "body"
    const val LIVE_PLATE = "plateLive"
    const val PIN_TAG = "pinTag"
    const val TAPE_TAG = "tapeTag"
    const val RETICLE_TAG = "reticleTag"
    const val MENU = "menuPanel"
    const val DIALOG = "dialogPanel"
    const val SNACK = "snackPanel"
    const val COACH = "coachPanel"
    const val CALIBRATION = "calib"

    fun plate(slot: Int) = "plate$slot"

    fun chip(slot: Int) = "chip$slot"

    /** Every product panel, in pool order. */
    val pool: List<String> =
        buildList {
            add(BODY)
            repeat(PLATE_SLOTS) { add(plate(it)) }
            add(LIVE_PLATE)
            repeat(CHIP_SLOTS) { add(chip(it)) }
            add(PIN_TAG)
            add(TAPE_TAG)
            add(RETICLE_TAG)
            add(MENU)
            add(COACH)
            add(DIALOG)
            add(SNACK)
        }
}

/** The framework instantiates the system itself, so the running scene is reached through this holder. */
internal object ActiveScene {
    @Volatile var scene: RulerScene? = null
}

/** The one per-frame ECS system of the app. */
class RulerFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.scene?.onFrame(context)
    }
}

/**
 * The 3D side of the app: everything that lives in the room (pins, tapes, guides, the instrument body,
 * reticle), the tap and drag targets, and the placement of the pre-built panels.
 *
 * Rules:
 * - one calibrated stage root; all positions are Stage metres in its local space;
 * - Compose state is rendered here through [render] (idempotent diff); per-frame work happens in
 *   [onFrame] and never goes through Compose, except [liveLengths] and [plateFar], which are written
 *   at most 10 times a second and only when they change;
 * - while a drag is under way, where it points is asked from [aim] every frame (a read, no state
 *   change); the ViewModel hears of a drag when it begins, when it ends, and while a pin is trued at
 *   most 10 times a second;
 * - motion is time-based (the emulator often runs at 2 - 4 fps) and no state needs it to be read;
 * - everything started in [attach] is stopped in [detach].
 *
 * Main thread only.
 */
class RulerScene(private val sink: (RulerEvent) -> Unit, private val aim: RulerAim) : RulerProbe.SceneProbe {

    /** tape id -> length shown on its plate while the tape is still being pulled out; [LIVE] is the tape under the hand. */
    val liveLengths = mutableStateMapOf<String, Float>()

    /**
     * Panel id -> the panel is in view. A pooled panel composes its content only while this is true:
     * what a panel draws while it is parked out of sight stays broken when it is moved into view.
     */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /**
     * Share of its panel's width that a panel's content fills, as the scene has applied it to the
     * panel's holder. The content draws itself stretched by the inverse (see `PanelRig`, finding 4).
     */
    val panelStretch = mutableStateMapOf<String, Float>()

    /** Plate slot -> the plate is more than 6 m away and only shows its value line. */
    val plateFar = mutableStateMapOf<Int, Boolean>()

    /** Tolerance the live tape would get if it were set now (M3); null: not known (the plate shows "..."). */
    val liveTolerance = mutableStateOf<LiveTolerance?>(null)

    /** Bumped to make `SpatialView.update` run again while panel entities are still missing. */
    val bindTick = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private val planeTracker = RulerGraph.planes
    private val headTracker = RulerGraph.head
    private val synthetic = RulerGraph.syntheticPointer

    // tracked pointers (controller ray, hand ray, tool tip): sampled once per frame in aimPointer
    private val controllerSource = ControllerPointerSource()
    private val handSource = HandPointerSource()
    private val selectSource = SelectPointerSourceUseCase()
    private val steadyAim = SteadyAim()
    private val commitWindow = CommitWindow()
    private val fineAim = FineAim()
    private val triggerStages = TriggerStages()
    private val pinchLatch = PinchLatch()
    private val gradeAim = GradeAimUseCase()
    private val findContact = FindContactUseCase()
    private var forcedSource: PointerChannel? = null
    private var forcedSteady: Boolean? = null
    private var activeChannel = PointerChannel.SYSTEM
    private var pointerRight = true
    private var fineOn = false
    private var lastSnap = SnapKind.NONE
    private var lastTrackedCommitMs = NEVER
    private var liveWriteMs = 0L
    private var reticleKey = ""
    private var reticleLogMs = 0L
    private var tagWidthDp = ReticleTagWidth.value

    /** What the tag beside the reticle says; written when it changes (not per frame). */
    val reticleLabel = mutableStateOf<ReticleLabel?>(null)

    // the body holds still while a key is pressed and for 0.6 s after
    private var bodyPressed = false
    private var lastBodyKeyMs = NEVER

    /** The body panel's pointer: a press holds the body still (lazy follow). */
    fun onBodyPointer(pressed: Boolean) {
        bodyPressed = pressed
        lastBodyKeyMs = SystemClock.elapsedRealtime()
        noteSystemInput()
    }

    /** When the system pointer (gaze + pinch, emulator mouse, controller ray through the system) last delivered anything. */
    private var lastSystemInputMs = NEVER

    /**
     * The system pointer delivered input (a tap, a drag, a panel pointer event): it is a working source, so the
     * "raise a hand or pick up a controller" message is taken down and not shown again until a tracked source
     * was there and went away (R3-2).
     */
    fun noteSystemInput() {
        lastSystemInputMs = SystemClock.elapsedRealtime()
        if (sourceLostSent) {
            sourceLostSent = false
            sink(RulerEvent.SourceChanged(lost = false))
        }
    }
    private val hits = HitSurfaceUseCase()
    private val scales = ScaleForDistanceUseCase()
    private val layout = LayoutTapeUseCase()
    private val ticks = SelectTickLevelUseCase()
    private val facingRule = FacePanelUseCase()
    private val clearSurface = ClearSurfaceUseCase(hits)
    private val placePlate = PlacePlateUseCase(scales)
    private val placeTag = PlaceTagUseCase(clearSurface, facingRule)
    private val pullLine = PullLineUseCase()

    private var content: SpatialViewContent? = null
    private var stageRoot: Entity? = null
    private var contentProbe: Entity? = null
    private var materials: SceneMaterials? = null
    private var pins: PinViews? = null
    private var colliders: PlaneColliders? = null
    private var rig: PanelRig? = null
    private var body: BodyView? = null
    private var reticle: Reticle? = null
    private var tapeMeshes: TapeMeshes? = null
    private var sharedResources: SharedResources? = null

    /** One mesh build per frame: see [MeshBudget]. */
    private val budget = MeshBudget(1)
    private var stub: TapeBlade? = null
    private var liveBlade: TapeBlade? = null
    private var liveMount: PlateMount? = null
    private var liveSeat: SeatFigure? = null
    private var attached = false

    /** Completed when the body panel has drawn its first frame: the build waits for it (see [build]). */
    private var bodyDrawn = CompletableDeferred<Unit>()
    private var attachedAtMs = 0L

    /** Called by the body panel when it has drawn; only the first call after [attach] counts. */
    fun onBodyPanelDrawn() {
        if (bodyDrawn.isCompleted) return
        bodyDrawn.complete(Unit)
        RulerLog.i("SCENE body panel drawn ms=${SystemClock.elapsedRealtime() - attachedAtMs}")
    }

    /** The background build of [attach]; cancelled by [detach]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var buildJob: Job? = null

    private class TapeEntry(val id: String, val blade: TapeBlade, val mount: PlateMount, val shadow: Strip, var a: String, var b: String) {
        var slot = -1
        var selected = false
        var dimmed = false
        var animStartMs = NEVER
        var animDurationMs = 0f
        var growFraction = 0f
        var settledAtMs = NEVER
        var lastA: Vec3? = null
        var lastB: Vec3? = null
        var lastLiveWriteMs = 0L
        var ghostFrom: Vec3? = null
        var ghostTo: Vec3? = null
        var ghostStartMs = 0L
        var ghostDurationMs = 0f

        /** Where the plate stands; chosen when the tape locks, a pin moves or a tape goes away. */
        var choice = PlateChoice(0.5f, 1)
        var chosen = false

        /** The chosen side as a direction in the room, so that the plate does not change sides when the view turns. */
        var side: Vec3? = null
        var badSinceMs = NEVER

        /** The place was clear of the blade and of every pin when it was chosen. */
        var heldWhenChosen = true

        /** Direction from the tape to the head when it locked: where a (nearly) vertical tape faces at close range. */
        var lockSide: Vec3? = null

        fun destroy() {
            blade.destroy()
            mount.destroy()
            shadow.destroy()
        }
    }

    /** Something as the viewer sees it this frame. */
    private class Seen(val rect: ViewRect, val range: Float, val center: Vec3)

    private val tapes = LinkedHashMap<String, TapeEntry>()
    private val tapeGhosts = ArrayList<TapeEntry>()
    private val guides = arrayOfNulls<GuideThread>(CHIP_SLOTS)
    private val chipKey = arrayOfNulls<String>(CHIP_SLOTS)
    private val chipStep = IntArray(CHIP_SLOTS)
    private var ui = RulerUiState()
    private var head: HeadPose = HeadPose(Vec3(0f, 1.6f, 0f), Quat4.IDENTITY)
    private var hasHead = false

    // what lies where in the view this frame (plate layout and tag placement work on these)
    private val pinSeen = HashMap<String, Seen>()
    private val pinBoxes = ArrayList<ViewRect>()
    private val pillSeen = HashMap<String, Seen>()
    private val lastPills = HashMap<String, ViewRect>()
    private val chipSeen = HashMap<String, Seen>()
    private val bladeSeen = HashMap<String, ViewSegment>()
    private var stubSeen: ViewRect? = null
    private var bodySeen: ViewRect? = null

    /** Width of the pill each plate draws, dp; reported by the panels (plain data, not Compose state). */
    private val pillWidthDp = FloatArray(PLATE_SLOTS + 1) { PlateWidth.value }
    private var layoutDirty = true

    private var lastPlaneCount = -1
    private var lastScanning: Boolean? = null
    private var lastPlanesEmitMs = 0L
    private var lastLost = false
    private var dialogPlacedFor = false
    private var calibDist = 0.9f
    private var calibScale = 1f
    private var calibOffX = 0f
    private var calibOffY = 0f
    private var calibSizeDp = 0

    // rig rule check and frame counter
    private var lastRigCheckMs = 0L
    private val violationLoggedMs = HashMap<String, Long>()
    private var fpsOn = false
    private var fpsFrames = 0
    private var fpsSinceMs = 0L

    /** A press that may become a press-and-pull or a true-up drag. */
    private class Drag(val pinId: String?, val planeId: String?, val press: Vec3, val target: String, val kind: String) {
        var totalPx = Vec3.ZERO
        var hand = Vec3.ZERO
        var begun = false
        var mode = DragMode.NONE

        /** Pin drag (round 5): the pin that moves; each drag event's hand step turns its aim line ([movePinDrag]). */
        var pin: PinDragState? = null
        var prevHand = Vec3.ZERO
        var tail: Vec3? = null
        var tailNormal = Vec3.UP
        var tailPin: String? = null
        var tailMovedMs = 0L
        var ray: PointerRay? = null
        var moves = 0
        var lastLogMs = 0L
        var lastSentMs = 0L
        var sentHand: Vec3? = null
        var lastLiveWriteMs = 0L
    }

    private var drag: Drag? = null

    /**
     * A pin being moved by direct manipulation (round 5), by the system pointer (inside [Drag]) or by a
     * tracked pointer ([trackedDrag]): where it came from, where the pointer aims now, the lagging draw
     * position of the pin, and the gain that was on.
     */
    private inner class PinDragState(val pinId: String, val from: Vec3, val fromNormal: Vec3, val via: String, val wasSelected: Boolean) {
        val gain = AdaptiveGain()

        /** System drag (round 5b): the line of sight as the cursor turns it (raw) and as the gain lets the pin follow it (aim). */
        var rawDir: Vec3? = null
        var aimDir: Vec3? = null
        var lastMoveMs = NEVER

        /** Metres of system travel per unit of view-plane cursor motion, fixed at the press (`DragPinUseCase.equivalentDepth`). */
        var equivalent = 0f

        /** Height of the pressed cap over the surface: the aim ray is lowered by it so that the cap, not the foot, stays under the cursor. */
        var capHeight = 0f
        var target: Vec3? = null
        var targetNormal = Vec3.UP
        var targetRay: PointerRay? = null
        var targetTip: Vec3? = null
        var targetFar = false
        var targetSnap = SnapKind.NONE
        var lagPos: Vec3? = null
        var lagMs = NEVER
        var lastLogMs = NEVER
        var lastLiveMs = NEVER
        var contact = false
        var fine = false
        val startedMs = SystemClock.elapsedRealtime()
    }

    private var trackedDrag: PinDragState? = null
    private var ghostRing: ModelEntity? = null
    private var ghostCasing: ModelEntity? = null

    /**
     * Plates whose hover effect is on (round 5d, QA R5c): a pin drag turns it off for every plate, and a plate
     * takes it back on the next pointer enter after the drag (the SpatialUI hover tint latches on a plate the
     * captured pointer crossed, and re-arming at once showed it again).
     */
    val plateHover = mutableStateMapOf<String, Boolean>()

    /** A pointer entered a plate panel (from the panel's watcher): hover is armed again, unless a pin is being dragged. */
    fun plateEntered(panelId: String) {
        if (drag?.pin != null || trackedDrag != null) return
        if (plateHover[panelId] != true) plateHover[panelId] = true
    }
    private val drags = DragPinUseCase()
    private var stubTouch: Entity? = null
    private var stubTouchPin: String? = null
    private var lastDragEndMs = NEVER
    private var pullEndedMs = NEVER
    private var pullEndedHead: String? = null

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    /**
     * Called from `SpatialView(initial = ...)`: puts up the instrument's face and starts sensing, then
     * builds everything else in the background.
     *
     * Order: the body panel first. It needs no mesh, and the SpatialView shows nothing before this call
     * returns (measured: with the whole build inside it, the Stage stayed empty for the 9 - 22 s the
     * build takes at the emulator's idle frame rate). So this call only makes the panel slots and binds
     * the panels; the housing and then everything in the room are built by [build], one mesh per
     * rendered frame. Nothing in the room is drawn, hit or reported before the build is complete
     * ([attached]).
     */
    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (attached || stageRoot != null) detach()
        val t0 = SystemClock.elapsedRealtime()
        attachedAtMs = t0
        bodyDrawn = CompletableDeferred()
        this.content = content
        val root = Entity()
        runCatching { root.setName("stageRoot") }
        content.addEntity(root)
        val probe = Entity()
        runCatching { probe.setName("contentProbe") }
        content.addEntity(probe)
        contentProbe = probe
        // An entity added to the SpatialView is not at the Stage origin (0.256 m off in z for a
        // full-size view). Move the root once so that its local space equals Stage space.
        val offset = root.convertPositionFrom(Vector3.ZERO, null)
        val rotation = root.convertRotationFrom(Quat.identity(), null)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        stageRoot = root
        RulerLog.i("STAGE root offset=${RulerLog.pos(offset.domain())} check=${RulerLog.pos(root.convertPositionTo(Vector3.ZERO, null).domain())}")

        // the instrument's face: panel slots on the head-locked body, bound at once
        val rig = PanelRig(root) { id, shown -> panelShown[id] = shown }
        this.rig = rig
        val body = BodyView(root)
        this.body = body
        buildPanelSlots(rig, body)
        headTracker.start()
        controllerSource.start()
        handSource.start()
        planeTracker.start(
            PlaneTracker.Listener { id, surface, _ ->
                if (surface != null) colliders?.upsert(surface) else colliders?.remove(id)
            }
        )
        ActiveScene.scene = this
        bindPanels(attachments)
        RulerLog.i("SCENE body panel up ms=${SystemClock.elapsedRealtime() - t0}")
        buildJob =
            scope.launch {
                try {
                    build(root, body, t0)
                } catch (t: Throwable) {
                    if (t !is CancellationException) RulerLog.e("SCENE build failed: $t", t)
                    throw t
                }
            }
    }

    /**
     * The housing, then everything in the room. Creating a mesh takes about one rendered frame, so the
     * build gives the main thread back after every mesh (`yield`): the Stage stays responsive while the
     * body panel says "Getting ready…".
     */
    private suspend fun build(root: Entity, body: BodyView, t0: Long) {
        val steps = StringBuilder()
        var stepStart = SystemClock.elapsedRealtime()
        fun step(name: String) {
            val now = SystemClock.elapsedRealtime()
            steps.append(' ').append(name).append('=').append(now - stepStart)
            stepStart = now
        }
        // the instrument's face is in view before the first mesh is made
        val drawn = withTimeoutOrNull(PANEL_FIRST_MS) { bodyDrawn.await() } != null
        yield()
        step(if (drawn) "panel" else "panel_timeout")
        val shared = SharedResources()
        sharedResources = shared
        val m = SceneMaterials(shared)
        materials = m
        step("materials")
        body.build(m)
        step("body")

        val tapeShared = TapeMeshes.build(shared, layout)
        tapeMeshes = tapeShared
        body.addMouth(tapeShared)
        step("tape")
        pins = PinViews(root, m, shared, scales, tapeShared.band).also { it.prepare() }
        step("pins")
        colliders =
            PlaneColliders(root).also {
                it.buildBackstop()
                // surfaces that were reported while the scene was being built
                planeTracker.all.forEach(it::upsert)
            }
        step("backstop")
        stub = TapeBlade(root, "tape_stub", m, layout, ticks, scales, tapeShared, budget)
        liveBlade = TapeBlade(root, "tape_live", m, layout, ticks, scales, tapeShared, budget)
        liveMount = PlateMount(root, tapeShared, m)
        liveSeat = SeatFigure(root, tapeShared.band, m)
        yield()

        RulerGraph.anchors.start()
        attached = true
        step("sensing")
        RulerLog.i("SCENE attached build_ms=${SystemClock.elapsedRealtime() - t0} steps_ms:$steps")
        render(ui)
        sink(RulerEvent.SceneReady)
    }

    private fun buildPanelSlots(rig: PanelRig, body: BodyView) {
        rig.onBody(PanelIds.BODY, body.mount, BodyView.HEAD_ROTATION, Vec3(0f, 0f, BodyView.PANEL_Z), BodyView.PANEL_SCALE, BodyPanelWidth.value, BodyPanelHeight.value, shown = true)
        repeat(PLATE_SLOTS) { rig.world(PanelIds.plate(it), PlateWidth.value, PlateHeight.value) }
        rig.world(PanelIds.LIVE_PLATE, PlateWidth.value, PlateHeight.value)
        repeat(CHIP_SLOTS) { rig.world(PanelIds.chip(it), ChipPanelWidth.value, ChipPanelHeight.value) }
        rig.world(PanelIds.PIN_TAG, TagWidth.value, PinTagHeight.value)
        rig.world(PanelIds.TAPE_TAG, TapeTagWidth.value, TapeTagHeight.value)
        rig.world(PanelIds.RETICLE_TAG, ReticleTagWidth.value, ReticleTagHeight.value)
        // fallback hosts of the floating surfaces (used only when the SpatialUI component is not)
        val k = BodyView.PANEL_SCALE
        val dp = PanelRig.DP_M * k
        val keyRight = (BodyPanelWidth.value / 2f - 12f) * dp
        val menuY = BodyPanelHeight.value / 2f * dp + 0.012f + MenuPanelHeight.value * dp / 2f
        rig.onBody(PanelIds.MENU, body.mount, BodyView.HEAD_ROTATION, Vec3(keyRight - MenuPanelWidth.value * dp / 2f, menuY, BodyView.PANEL_Z + 0.012f), k, MenuPanelWidth.value, MenuPanelHeight.value, shown = false)
        rig.world(PanelIds.DIALOG, DialogPanelWidth.value, DialogPanelHeight.value)
        rig.head(PanelIds.SNACK, SNACK_HEAD_OFFSET, FLOATING_SCALE, SnackPanelWidth.value, SnackPanelHeight.value)
        // first-run coachmark: to the right of the body, in the body's plane, lower edge level with the body's
        rig.onBody(
            PanelIds.COACH,
            body.mount,
            BodyView.HEAD_ROTATION,
            Vec3(BodyView.HALF_X + COACH_GAP_M + CoachPanelWidth.value * dp / 2f, CoachPanelHeight.value * dp / 2f - BodyView.HALF_Y, BodyView.PANEL_Z),
            k,
            CoachPanelWidth.value,
            CoachPanelHeight.value,
            shown = false,
        )
    }

    /** Called from `SpatialView(update = ...)`: parents freshly composed panel entities under their holders. */
    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        val calibration = rig[PanelIds.CALIBRATION]
        val calibrationWasBound = calibration?.panel != null
        val ids = if (calibration != null) PanelIds.pool + PanelIds.CALIBRATION else PanelIds.pool
        val complete = rig.bind(attachments, ids, SystemClock.elapsedRealtime())
        if (calibration != null && !calibrationWasBound && calibration.panel != null) {
            RulerLog.i("CALIB panel attached dist=$calibDist scale=$calibScale off=($calibOffX, $calibOffY) sizeDp=$calibSizeDp valid=${calibration.panel?.valid}")
        }
        if (!complete) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    private val rebind = Runnable { if (stageRoot != null) bindTick.intValue++ }

    /** Stops everything [attach] started and destroys the entities; also cleans up a build that was cut short. */
    fun detach() {
        if (!attached && stageRoot == null) return
        attached = false
        buildJob?.cancel()
        buildJob = null
        if (ActiveScene.scene === this) ActiveScene.scene = null
        main.removeCallbacksAndMessages(null)
        planeTracker.stop()
        headTracker.stop()
        controllerSource.stop()
        handSource.stop()
        RulerGraph.anchors.stop()
        synthetic.lose()
        activeChannel = PointerChannel.SYSTEM
        reticleLabel.value = null
        liveLengths.clear()
        panelShown.clear()
        panelStretch.clear()
        plateFar.clear()
        drag = null
        runCatching { rig?.release() }
        tapes.values.forEach { it.destroy() }
        tapes.clear()
        tapeGhosts.forEach { it.destroy() }
        tapeGhosts.clear()
        for (i in guides.indices) {
            guides[i]?.destroy()
            guides[i] = null
        }
        pinTag.destroy()
        tapeTag.destroy()
        pins?.clear()
        colliders?.clear()
        runCatching { stageRoot?.destroy(true) }
        runCatching { contentProbe?.destroy(true) }
        // global resources outlive their users: they are released here
        sharedResources?.close()
        sharedResources = null
        stageRoot = null
        contentProbe = null
        content = null
        pins = null
        colliders = null
        rig = null
        body = null
        reticle = null
        stub = null
        liveBlade = null
        liveMount = null
        liveSeat = null
        materials = null
        tapeMeshes = null
        hasHead = false
        lastPlaneCount = -1
        lastScanning = null
        lastLost = false
        pinSeen.clear()
        pinBoxes.clear()
        pillSeen.clear()
        lastPills.clear()
        chipSeen.clear()
        chipKey.fill(null)
        bladeSeen.clear()
        RulerLog.i("SCENE detached")
    }

    // =========================================================================================
    // model -> entities (called when the UI state changes)
    // =========================================================================================

    fun render(state: RulerUiState) {
        val previous = ui
        ui = state
        if (!attached) return
        val now = SystemClock.elapsedRealtime()
        val restoring = state.phase == Phase.RESTORING
        if (layoutChanged(previous, state)) layoutDirty = true
        syncTapes(state, now, restoring)
        pins?.sync(state.pins, now, restoring, state.trackingLost)
        syncPanels(state)
    }

    /** A tape locked or went away, or a pin moved: the plates choose their places again. */
    private fun layoutChanged(before: RulerUiState, after: RulerUiState): Boolean {
        if (before.tapes.size != after.tapes.size || before.pins.size != after.pins.size) return true
        for (i in after.tapes.indices) if (before.tapes[i].id != after.tapes[i].id) return true
        for (i in after.pins.indices) {
            if (before.pins[i].id != after.pins[i].id || before.pins[i].position != after.pins[i].position) return true
        }
        return false
    }

    /** Width of the pill a plate draws (plate slot, or [PLATE_SLOTS] for the live plate); called by the panels. */
    fun reportPill(slot: Int, widthDp: Float) {
        if (slot !in pillWidthDp.indices || widthDp <= 0f) return
        squeeze(if (slot == PLATE_SLOTS) PanelIds.LIVE_PLATE else PanelIds.plate(slot), widthDp)
        if (abs(pillWidthDp[slot] - widthDp) < 0.5f) return
        pillWidthDp[slot] = widthDp
        layoutDirty = true
    }

    /** Width of the snackbar's capsule, dp; called by the panel. */
    fun reportSnack(widthDp: Float) {
        if (widthDp > 0f) squeeze(PanelIds.SNACK, widthDp)
    }

    /** Makes the panel [id] exactly as wide as its content of [widthDp] and tells the content. */
    private fun squeeze(id: String, widthDp: Float) {
        val slot = rig?.get(id) ?: return
        slot.widthFactor = widthDp / slot.widthDp
        if (panelStretch[id] != slot.widthFactor) panelStretch[id] = slot.widthFactor
    }

    private fun syncTapes(state: RulerUiState, now: Long, restoring: Boolean) {
        val root = stageRoot ?: return
        val m = materials ?: return
        val shared = tapeMeshes ?: return
        val wanted = HashSet<String>()
        val positions = state.pins.associate { it.id to it.position }
        state.tapes.forEachIndexed { index, t ->
            wanted += t.id
            var entry = tapes[t.id]
            if (entry == null) {
                entry =
                    TapeEntry(
                        t.id,
                        TapeBlade(root, "tape:${t.id}", m, layout, ticks, scales, shared, budget),
                        PlateMount(root, shared, m),
                        Strip(root, shared.band, m.tapeShadow, "tapeShadow:${t.id}"),
                        t.a,
                        t.b,
                    )
                val a = positions[t.a]
                val b = positions[t.b]
                val length = if (a != null && b != null) a.distanceTo(b) else 1f
                if (a != null && b != null && length > 1e-3f) {
                    val dir = (b - a) / length
                    val side = (head.position - a.lerp(b, 0.5f)).rejectFrom(dir)
                    if (side.length > 1e-3f) entry.lockSide = side.normalized()
                }
                when {
                    restoring -> {
                        // restored tapes re-form one after the other, after their pins have risen
                        entry.animStartMs = now + RESTORE_TAPE_DELAY_MS + index * RESTORE_TAPE_STAGGER_MS
                        entry.animDurationMs = RESTORE_TAPE_MS
                        entry.growFraction = 1f
                    }
                    pullEndedMs != NEVER && now - pullEndedMs < PULL_LOCK_MS && (t.a == pullEndedHead || t.b == pullEndedHead) -> {
                        // it was pulled out by hand: it is at its length already and only settles
                        entry.animStartMs = now
                        entry.animDurationMs = PULL_SETTLE_MS
                        entry.growFraction = 0f
                        pullEndedMs = NEVER
                    }
                    else -> {
                        // the pull-out: the blade runs to the tail, then the plate slides to its place
                        entry.animStartMs = now
                        entry.animDurationMs = (520f + length * 140f).coerceIn(620f, 1100f)
                        entry.growFraction = GROW_FRACTION
                    }
                }
                tapes[t.id] = entry
            }
            applyTape(entry, t)
            entry.blade.setStale(state.trackingLost)
        }
        val gone = tapes.keys.filter { it !in wanted }
        for (id in gone) {
            val entry = tapes.remove(id) ?: continue
            liveLengths.remove(id)
            entry.mount.hide()
            entry.shadow.hide()
            val a = entry.lastA
            val b = entry.lastB
            if (a == null || b == null) {
                entry.destroy()
                continue
            }
            val aStays = positions.containsKey(entry.a)
            val bStays = positions.containsKey(entry.b)
            if (aStays && !bStays || !aStays && bStays) {
                // its pin was removed: the tape runs back into the pin that stays
                entry.ghostFrom = if (aStays) a else b
                entry.ghostTo = if (aStays) b else a
                entry.ghostDurationMs = RETRACT_WITH_PIN_MS
            } else {
                // retracted: back into the tape head at 6 m/s
                entry.ghostFrom = a
                entry.ghostTo = b
                entry.ghostDurationMs = (a.distanceTo(b) / RETRACT_SPEED_M_S * 1000f).coerceIn(160f, 450f)
            }
            entry.ghostStartMs = now
            tapeGhosts += entry
        }
    }

    private fun applyTape(entry: TapeEntry, t: TapeUi) {
        entry.a = t.a
        entry.b = t.b
        entry.selected = t.selected
        entry.dimmed = t.dimmed
        entry.slot = t.plateSlot
    }

    private fun syncPanels(state: RulerUiState) {
        val rig = rig ?: return
        // plates: a slot without a tape is hidden; positions are set per frame
        val used = HashSet<Int>()
        state.tapes.forEach { if (it.plateSlot >= 0) used += it.plateSlot }
        repeat(PLATE_SLOTS) { slot ->
            val s = rig[PanelIds.plate(slot)] ?: return@repeat
            if (slot !in used) {
                s.hide()
                if (plateFar.containsKey(slot)) plateFar.remove(slot)
            }
            val tapeId = state.plates.getOrNull(slot)?.tapeId
            s.rename(if (tapeId != null) "plate:$tapeId" else "plate:slot$slot")
        }
        repeat(CHIP_SLOTS) { slot ->
            val s = rig[PanelIds.chip(slot)] ?: return@repeat
            val chip = state.chips.getOrNull(slot)
            if (chip == null) {
                s.hide()
                guides[slot]?.hide()
            }
            s.rename(if (chip != null) "chip:${chip.pinId}" else "chip:slot$slot")
        }
        if (state.pinTag == null) {
            rig[PanelIds.PIN_TAG]?.hide()
            pinTag.reset()
        }
        // the dragged pin is drawn where the drag says until the state has caught up with the release
        if (drag?.pin == null && trackedDrag == null) pins?.clearDrag()
        if (state.tapeTag == null) {
            rig[PanelIds.TAPE_TAG]?.hide()
            tapeTag.reset()
        }

        // floating surfaces hosted on plain panels (design assumption 9; the menu has only this host since round 4)
        val menu = rig[PanelIds.MENU]
        if (state.menu != MenuState.CLOSED) menu?.show(null, null) else menu?.hide()

        val dialog = rig[PanelIds.DIALOG]
        val dialogWanted = state.blocked || (OverlayHosting.dialog.value == OverlayHost.PANEL && state.clearDialog != null)
        if (dialogWanted) {
            if (!dialogPlacedFor) {
                // appears straight ahead, 0.9 m away, and then stays where it is
                val flat = Vec3(head.forward.x, 0f, head.forward.z).let { if (it.length < 0.1f) Vec3.AHEAD else it.normalized() }
                dialog?.show(head.position + flat * FLOATING_DISTANCE_M, FLOATING_SCALE)
                dialogPlacedFor = true
            }
        } else {
            dialog?.hide()
            dialogPlacedFor = false
        }

        val snack = rig[PanelIds.SNACK]
        if (OverlayHosting.snackbar.value == OverlayHost.PANEL && state.snack != null) snack?.show(null, null) else snack?.hide()

        val coach = rig[PanelIds.COACH]
        if (state.coachStep > 0 && state.menu == MenuState.CLOSED) coach?.show(null, null) else coach?.hide()
        if (state.coachStep != coachShown) {
            coachShown = state.coachStep
            RulerLog.i("COACH step=${state.coachStep}")
        }
    }

    // =========================================================================================
    // per frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val root = stageRoot ?: return
        if (root.scene == null || context.scene != root.scene) return
        val now = SystemClock.elapsedRealtime()
        budget.reset()
        headTracker.pose()?.let {
            head = it
            hasHead = true
        }
        val headPos = head.position
        val rig = rig ?: return
        rig.onFrame(now)
        rig.stowBehind(head.view)
        // the instrument body rests in the room, keeps out of real surfaces and follows the head lazily
        val bodyHold = bodyPressed || (lastBodyKeyMs != NEVER && now - lastBodyKeyMs < BODY_KEY_HOLD_MS) || ui.menu != MenuState.CLOSED || ui.clearDialog != null || ui.blocked
        // the body is first parked for a real head pose: the tracker's first poses are the origin, which would put it under the floor
        if (hasHead && head.position.length > 1e-3f) body?.follow(head, planeTracker.all, planeTracker.count, now, bodyHold)
        countFrame(now)
        if (!attached) return

        val state = ui
        val system = state.unit.system
        val frame = ViewFrame(head.view)

        pollTracking(now)
        pollPlanes(now)

        val pinViews = pins ?: return
        pinViews.onFrame(now, headPos)
        seePins(state, frame, pinViews)
        seeBody(frame)

        drawTapes(state, system, now, frame, pinViews)
        drawGhosts(system, now, headPos)
        reticle?.viewer = headPos
        val aimLive = aimPointer(state, system, now, frame, pinViews)
        drawStub(state, system, now, frame, pinViews, hidden = aimLive || drag?.pin != null || trackedDrag != null)
        // (a pin drag owns the live lengths and the reticle: the pull must not take them down each frame)
        drawPull(state, system, now, frame, pinViews, aimLive || drag?.pin != null || trackedDrag != null)
        drawSystemPinDrag(state, now, frame, pinViews)
        placeCandidates(state, frame, pinViews)
        placePinTag(state, now, frame, pinViews)
        placeTapeTag(state, now, frame, pinViews)
        rig.faceViewer(headPos)
        checkRig(now, frame)
    }

    /** Every pin as the viewer sees it: its head, with the keep-clear box of the layout rules around it. */
    private fun seePins(state: RulerUiState, frame: ViewFrame, pinViews: PinViews) {
        pinSeen.clear()
        pinBoxes.clear()
        for (pin in state.pins) {
            val at = pinViews.headPoint(pin.id) ?: continue
            val seen = frame.see(at) ?: continue
            val rect = ViewRect(seen.yaw, seen.pitch, PlacePlateUseCase.PIN_BOX_DEG, PlacePlateUseCase.PIN_BOX_DEG)
            pinSeen[pin.id] = Seen(rect, seen.range, at)
            pinBoxes += rect
        }
    }

    /** What the instrument body covers of the view: the bounding rectangle of its box. */
    private fun seeBody(frame: ViewFrame) {
        val body = body ?: return
        var yawLo = Float.MAX_VALUE
        var yawHi = -Float.MAX_VALUE
        var pitchLo = Float.MAX_VALUE
        var pitchHi = -Float.MAX_VALUE
        for (corner in body.corners()) {
            val v = frame.see(corner)
            if (v == null) {
                bodySeen = null
                return
            }
            yawLo = min(yawLo, v.yaw)
            yawHi = max(yawHi, v.yaw)
            pitchLo = min(pitchLo, v.pitch)
            pitchHi = max(pitchHi, v.pitch)
        }
        // the tape mouth and its hook stick out to the right
        bodySeen = ViewRect((yawLo + yawHi) / 2f + BODY_MOUTH_DEG / 2f, (pitchLo + pitchHi) / 2f, (yawHi - yawLo) / 2f + BODY_MOUTH_DEG / 2f + 0.5f, (pitchHi - pitchLo) / 2f + 0.5f)
    }

    private fun drawTapes(state: RulerUiState, system: UnitSystem, now: Long, frame: ViewFrame, pinViews: PinViews) {
        val rig = rig ?: return
        val headPos = frame.pose.position
        lastPills.clear()
        for ((id, seen) in pillSeen) lastPills[id] = seen.rect
        pillSeen.clear()
        bladeSeen.clear()
        val relayout = layoutDirty
        layoutDirty = false
        val placed = ArrayList<ViewRect>()
        for (entry in tapes.values) {
            val a = pinViews.headPoint(entry.a)
            val b = pinViews.headPoint(entry.b)
            val slot = if (entry.slot >= 0) rig[PanelIds.plate(entry.slot)] else null
            if (a == null || b == null) {
                entry.blade.hide()
                entry.mount.hide()
                entry.shadow.hide()
                slot?.hide()
                continue
            }
            entry.lastA = a
            entry.lastB = b
            val full = b - a
            val length = full.length
            if (length < 1e-4f) continue
            val dir = full / length
            var shown = length
            var growing = false
            var slide = 1f
            var stamp = 1f
            var sway = 0f
            if (entry.animStartMs != NEVER) {
                val p = (now - entry.animStartMs) / entry.animDurationMs
                when {
                    p < 0f -> {
                        shown = 0f
                        growing = true
                    }
                    p >= 1f -> entry.animStartMs = NEVER
                    p < entry.growFraction -> {
                        growing = true
                        shown = length * easeOutPull(p / entry.growFraction)
                    }
                    else -> {
                        val g = entry.growFraction
                        slide = easeOutCubic((p - g) / max(0.01f, (1f - g) * 0.6f))
                        if (p > 0.82f) stamp = 1f + 0.08f * sin((p - 0.82f) / 0.18f * PI.toFloat())
                    }
                }
                if (!growing && entry.settledAtMs == NEVER) entry.settledAtMs = now
            }
            // settling: the blade swings sideways twice and comes to rest
            if (entry.settledAtMs != NEVER && entry.settledAtMs != DONE) {
                val q = (now - entry.settledAtMs) / SETTLE_SWAY_MS
                if (q in 0f..1f) sway = SETTLE_SWAY_M * sin(q * 4f * PI.toFloat()) * (1f - q) else if (q > 1f) entry.settledAtMs = DONE
            }

            // the blade comes out of the head pin (a); its zero end with the hook is at the tail
            val tip = if (growing) a + dir * shown else b
            val pa = pinViews.position(entry.a)
            val pb = pinViews.position(entry.b)
            val na = pinViews.normal(entry.a)
            val nb = pinViews.normal(entry.b)
            val onOnePlane = pa != null && pb != null && na != null && nb != null && na.dot(nb) > SAME_PLANE_COS && abs((pb - pa).dot(na)) < SAME_PLANE_M
            val reference =
                when {
                    onOnePlane -> na
                    abs(dir.y) > NEAR_VERTICAL_COS -> entry.lockSide
                    else -> null
                }
            // tapes that leave the same pin lie in one plane at close range: a fraction of a millimetre apart, they do not flicker
            val stack = (reference ?: Vec3.UP) * (STACK_M * (if (entry.slot >= 0) entry.slot % STACK_LEVELS else 0))
            val drawn = shown > 0f && entry.blade.draw(tip + stack, a + stack, headPos, system, entry.selected, settled = !growing, reference = reference, sway = sway)
            if (!drawn) entry.blade.hide()
            entry.blade.setOpacity(if (entry.dimmed) DIMMED_OPACITY else 1f)

            // contact shadow on the surface both pins sit on, as long as the visible blade
            if (drawn && onOnePlane) {
                val lift = na * SHADOW_LIFT_M
                val footTip = if (growing) pa + (pb - pa) * (shown / length) else pb
                entry.shadow.draw(footTip + lift, pa + lift, LayoutTapeUseCase.WIDTH_M * entry.blade.widthScale + SHADOW_MARGIN_M, na)
                entry.shadow.setOpacity(if (entry.dimmed) DIMMED_OPACITY else 1f)
            } else {
                entry.shadow.hide()
            }

            if (drawn) {
                val va = frame.see(a)
                val vt = frame.see(tip)
                if (va != null && vt != null) {
                    bladeSeen[entry.id] =
                        ViewSegment(va.yaw, va.pitch, va.range, vt.yaw, vt.pitch, vt.range, ViewFrame.angle(LayoutTapeUseCase.WIDTH_M / 2f * entry.blade.widthScale, min(va.range, vt.range)))
                }
            }

            // the reading counts up on the plate while the blade runs out (written at most 10 Hz)
            if (growing) {
                if (now - entry.lastLiveWriteMs >= LIVE_WRITE_MS) {
                    val trueLength = if (pa != null && pb != null) pa.distanceTo(pb) else length
                    liveLengths[entry.id] = trueLength * (shown / length)
                    entry.lastLiveWriteMs = now
                }
            } else if (liveLengths.containsKey(entry.id) && !onDraggedPin(entry.a, entry.b)) {
                // (a tape on the pin being dragged keeps the live length that `stepPinDrag` writes, QA R5-3)
                liveLengths.remove(entry.id)
            }

            if (slot == null) {
                entry.mount.hide()
                continue
            }
            if (!drawn) {
                slot.hide()
                entry.mount.hide()
                continue
            }
            squeeze(PanelIds.plate(entry.slot), pillWidthDp[entry.slot])
            val halfW = pillWidthDp[entry.slot] / 2f * PanelRig.DP_M
            val halfH = PlateHeight.value / 2f * PanelRig.DP_M
            if (!entry.chosen || relayout) {
                val others = if (relayout) placed else lastPills.filterKeys { it != entry.id }.values.toList()
                val whole = PlateTape(entry.id, a, b, halfW, halfH)
                entry.choice = placePlate.choose(frame, whole, pinBoxes, others)
                entry.chosen = true
                // the side is kept as a direction in the room, taken from the whole tape (not from the
                // short piece that is visible while the blade runs out)
                val chosenPose = placePlate.pose(frame, whole, entry.choice)
                val out = chosenPose?.let { (it.center - it.rider).rejectFrom(dir) }
                entry.side = if (out != null && out.length > 1e-4f) out.normalized() else null
                // among pins that stand closer together than a pill is wide no place is clear: such a
                // choice is the best there is and is kept until the layout changes
                entry.heldWhenChosen = chosenPose != null && placePlate.holds(frame, whole, chosenPose, pinBoxes)
                entry.badSinceMs = NEVER
            }
            // it rides the tail while the blade runs out, then slides to its place
            val fraction = if (growing) 1f else 1f + (entry.choice.fraction - 1f) * slide
            val pose = plateOnSide(frame, PlateTape(entry.id, a, tip, halfW, halfH), fraction, entry)
            if (pose == null) {
                slot.hide()
                entry.mount.hide()
                continue
            }
            if (!growing && slide >= 1f) {
                // a place that was clear and has gone bad (the view changed) is given up after 0.4 s
                if (!entry.heldWhenChosen || placePlate.holds(frame, PlateTape(entry.id, a, b, halfW, halfH), pose, pinBoxes)) {
                    entry.badSinceMs = NEVER
                } else if (entry.badSinceMs == NEVER) {
                    entry.badSinceMs = now
                } else if (now - entry.badSinceMs > REPLACE_AFTER_MS) {
                    entry.chosen = false
                }
            }
            val range = clearRange(headPos, pose.center, halfW * scales.panel(pose.range), halfH * scales.panel(pose.range), slot.lastYaw)
            val at = headPos + (pose.center - headPos).normalized() * range
            val k = scales.panel(range)
            slot.show(at, k * stamp)
            val far = scales.plateIsFar(pose.range)
            if ((plateFar[entry.slot] == true) != far) plateFar[entry.slot] = far
            val pill = ViewRect(pose.rect.yaw, pose.rect.pitch, pose.rect.halfYaw, pose.rect.halfPitch)
            pillSeen[entry.id] = Seen(pill, range, at)
            placed += pill
            val rider = if (growing) riderBack(tip, a) else pose.rider
            entry.mount.place(rider, entry.blade.rotation, LayoutTapeUseCase.WIDTH_M * entry.blade.widthScale, nearestEdge(at, headPos, slot.lastYaw, halfW * k, halfH * k, rider), k)
            entry.mount.setOpacity(if (entry.dimmed) DIMMED_OPACITY else 1f)
        }
    }

    /** The plate of [tape] at [fraction] on the side that was chosen for it, kept as a direction in the room. */
    private fun plateOnSide(frame: ViewFrame, tape: PlateTape, fraction: Float, entry: TapeEntry): PlatePose? {
        val up = placePlate.pose(frame, tape, PlateChoice(fraction, 1))
        val down = placePlate.pose(frame, tape, PlateChoice(fraction, -1))
        if (up == null || down == null) return up ?: down
        val kept = entry.side ?: return if (entry.choice.side >= 0) up else down
        return if ((up.center - up.rider).dot(kept) >= (down.center - down.rider).dot(kept)) up else down
    }

    /**
     * Surface rule for a room panel: how far from the head it may stand so that its corners stay 2 cm
     * on the head's side of every recognised surface. Never nearer than 0.45 m (or than it wanted to be).
     */
    private fun clearRange(headPos: Vec3, center: Vec3, halfW: Float, halfH: Float, lastYaw: Float?, lower: Float = ScaleForDistanceUseCase.PANEL_MIN_DISTANCE_M): Float {
        val to = center - headPos
        val range = to.length
        if (range <= lower || range < 1e-3f) return range
        val allowed = clearSurface.maxRange(headPos, to / range, range, halfW / range, halfH / range, planeTracker.all, facingRule, lastYaw)
        return max(allowed, lower)
    }

    /** Middle of the edge of a panel (centre [at], half sizes in metres) that is nearest to [to]. */
    private fun nearestEdge(at: Vec3, headPos: Vec3, lastYaw: Float?, halfW: Float, halfH: Float, to: Vec3): Vec3 {
        val f = facingRule.facing(at, headPos, lastYaw)
        var best = at + f.right * halfW
        var bestDistance = best.distanceTo(to)
        for (candidate in arrayOf(at - f.right * halfW, at + f.up * halfH, at - f.up * halfH)) {
            val d = candidate.distanceTo(to)
            if (d < bestDistance) {
                best = candidate
                bestDistance = d
            }
        }
        return best
    }

    private fun drawGhosts(system: UnitSystem, now: Long, headPos: Vec3) {
        if (tapeGhosts.isEmpty()) return
        val it = tapeGhosts.iterator()
        while (it.hasNext()) {
            val g = it.next()
            val from = g.ghostFrom
            val to = g.ghostTo
            val p = (now - g.ghostStartMs) / g.ghostDurationMs
            if (from == null || to == null || p >= 1f) {
                g.destroy()
                it.remove()
                continue
            }
            // the hook runs back to the pin the blade came out of
            val tip = from + (to - from) * (1f - p * p)
            if (!g.blade.draw(tip, from, headPos, system, selected = false, settled = false)) g.blade.hide()
        }
    }

    /** The tape head: a short length of blade with its hook, waiting on the pin that was touched last. */
    private fun drawStub(state: RulerUiState, system: UnitSystem, now: Long, frame: ViewFrame, pinViews: PinViews, hidden: Boolean) {
        val blade = stub ?: return
        stubSeen = null
        val armed = state.pins.firstOrNull { it.armed }
        // (hidden: a tracked pointer is pulling the live tape out of this pin)
        if (hidden || armed == null || state.phase != Phase.READY || state.pullHead != null || state.trackingLost) {
            blade.hide()
            stubTouch?.enabled = false
            return
        }
        val k = pinViews.scale(armed.id)
        val n = armed.normal
        val base = armed.position + n * ((ResolvePlacementUseCase.PIN_HEAD_M + PinViews.ARMED_LIFT_M) * k)
        val side = if (abs(n.y) > 0.5f) head.right else Vec3.UP
        // it sways a little: "the next thing you touch is the tail"
        val sway = sin(now % 100000L / 420f) * 0.14f
        val dir = (n * 0.75f + side * (0.66f + sway)).normalized()
        val free = base + dir * (STUB_LENGTH_M * k)
        // the free end is the zero end with the hook
        blade.draw(free, base, frame.pose.position, system, selected = false, settled = true, uniformScale = k, widen = false, fixedStep = 1)
        stubSeen = frame.bounds(base, free, 0.5f + ViewFrame.angle(LayoutTapeUseCase.WIDTH_M * k, max(0.3f, base.distanceTo(frame.pose.position))))
        // its touch target, the hook end only (the last 3 cm of the stub; it never overlaps the pin's touch sphere, QA
        // R5-4): a press-and-drag here pulls a tape from the pin; a drag on the pin itself moves the pin
        val root = stageRoot
        if (root != null) {
            val touch =
                stubTouch
                    ?: Entity().also {
                        it.components.set(InteractableComponent())
                        it.components.set(CollisionComponent(collisionShape = listOf(ShapeResource.createSphere(STUB_TOUCH_M)), physicsMaterial = PhysicsMaterialResource()))
                        root.addChild(it)
                        stubTouch = it
                    }
            if (stubTouchPin != armed.id) {
                runCatching { touch.setName("stub:${armed.id}") }
                stubTouchPin = armed.id
            }
            touch.setLocal(position = free - dir * (STUB_TOUCH_M * k), scale = Vec3(k, k, k))
            touch.enabled = true
        }
    }

    /** The tape under the hand during a press-and-pull: live blade with ticks, reticle, snapping cue, counting plate. */
    private fun drawPull(state: RulerUiState, system: UnitSystem, now: Long, frame: ViewFrame, pinViews: PinViews, aimLive: Boolean) {
        val live = liveBlade ?: return
        val rig = rig ?: return
        val d = drag
        val headId = state.pullHead
        if (d == null || !d.begun || d.mode != DragMode.PULL || headId == null) {
            // (the reticle and the seat belong to aimPointer when no tape is pulled by a system drag)
            if (!aimLive) hideLiveTape()
            return
        }
        val out = pinViews.headPoint(headId)
        val foot = pinViews.position(headId)
        if (out == null || foot == null) return // the head pin's view appears with the next render
        val headPos = frame.pose.position
        val ray = pullLine.ray(headPos, d.press, d.hand)
        d.ray = ray
        val preview = ray?.let { aim.preview(it) }
        val point = preview?.point
        if (preview != null && point != null) {
            val before = d.tail
            if (before == null || before.distanceTo(point) > max(0.002f, preview.distance * STEADY_TAN)) d.tailMovedMs = now
            d.tail = point
            d.tailNormal = preview.normal
            d.tailPin = preview.pinId
        }
        val tail = d.tail
        if (tail == null) {
            live.hide()
            liveMount?.hide()
            rig[PanelIds.LIVE_PLATE]?.hide()
            return
        }
        // aimed at a pin: the tape goes to that pin's head; otherwise to where the new pin's head will be
        val aimedPin = d.tailPin
        val tip = (if (aimedPin != null) pinViews.headPoint(aimedPin) else null) ?: (tail + d.tailNormal * (ResolvePlacementUseCase.PIN_HEAD_M * scales.pin(tail.distanceTo(headPos))))
        val tailFoot = (if (aimedPin != null) pinViews.position(aimedPin) else null) ?: tail
        val held = now - d.tailMovedMs
        val aimSigma = preview?.let { toleranceModel.pinSigmaMm(if (held >= STEADY_MS) Grade.FINE else Grade.COARSE, it.snapped, it.far, it.distance) }
        drawLiveTape(out, foot, tip, tailFoot, system, now, frame, liveTolerance(state.headSigmaMm, aimSigma, preview, far = false))

        if (point != null) {
            val r = reticle ?: Reticle(stageRoot ?: return, materials ?: return).also { reticle = it }
            r.show(tail, d.tailNormal, preview.distance, (held.toFloat() / STEADY_MS).coerceIn(0f, 1f), preview.far)
            showSeat(preview, tail, d.tailNormal, headPos)
        } else {
            reticle?.hide()
            liveSeat?.hide()
        }
    }

    /** The seat figure of a snapped aim (corner, edge, plumb, level); hidden otherwise. */
    private fun showSeat(preview: AimPreview, at: Vec3, normal: Vec3, headPos: Vec3) {
        val seat = liveSeat ?: return
        if (preview.snapped == SnapKind.CORNER || preview.snapped == SnapKind.EDGE || preview.snapped == SnapKind.PLUMB || preview.snapped == SnapKind.LEVEL) {
            seat.set(preview.snapped, at, normal, preview.seatA, preview.seatB)
            seat.draw(max(1f, preview.distance * Reticle.SCALE_PER_M), headPos)
        } else {
            seat.hide()
        }
    }

    /**
     * The tape that is being pulled, by a system drag or by a tracked pointer: the moving blade with its
     * ticks from the head pin ([out], foot [foot]) to [tip], and the plate that rides its tail and counts
     * (the length goes to Compose at most ten times a second).
     */
    private fun drawLiveTape(out: Vec3, foot: Vec3, tip: Vec3, tailFoot: Vec3, system: UnitSystem, now: Long, frame: ViewFrame, tolerance: LiveTolerance?) {
        val live = liveBlade ?: return
        val rig = rig ?: return
        val headPos = frame.pose.position
        val drawn = live.draw(tip, out, headPos, system, selected = false, settled = false)
        if (!drawn) live.hide()
        if (now - liveWriteMs >= LIVE_WRITE_MS) {
            liveLengths[LIVE] = foot.distanceTo(tailFoot)
            if (liveTolerance.value != tolerance) liveTolerance.value = tolerance
            liveWriteMs = now
        }
        val slot = rig[PanelIds.LIVE_PLATE]
        val pose = if (drawn) placePlate.pose(frame, PlateTape(LIVE, out, tip, pillWidthDp[PLATE_SLOTS] / 2f * PanelRig.DP_M, PlateHeight.value / 2f * PanelRig.DP_M), PlateChoice(1f, 1)) else null
        if (slot == null || pose == null) {
            slot?.hide()
            liveMount?.hide()
            return
        }
        squeeze(PanelIds.LIVE_PLATE, pillWidthDp[PLATE_SLOTS])
        val halfW = pillWidthDp[PLATE_SLOTS] / 2f * PanelRig.DP_M
        val halfH = PlateHeight.value / 2f * PanelRig.DP_M
        val range = clearRange(headPos, pose.center, halfW * scales.panel(pose.range), halfH * scales.panel(pose.range), slot.lastYaw)
        val at = headPos + (pose.center - headPos).normalized() * range
        val k = scales.panel(range)
        slot.show(at, k)
        // M2: while the tape runs out the rider sits 45 mm back from the tail; the plate stays at the tail
        val rider = riderBack(tip, out)
        liveMount?.place(rider, live.rotation, LayoutTapeUseCase.WIDTH_M * live.widthScale, nearestEdge(at, headPos, slot.lastYaw, halfW * k, halfH * k, rider), k)
    }

    /** The rider of a running tape: [RIDER_BACK_M] back from the tail [tip] towards [out] (the middle of a shorter tape). */
    private fun riderBack(tip: Vec3, out: Vec3): Vec3 {
        val length = tip.distanceTo(out)
        return if (length >= 2f * RIDER_BACK_M) tip + (out - tip) * (RIDER_BACK_M / length) else (tip + out) * 0.5f
    }

    private fun hideLiveTape() {
        liveBlade?.hide()
        liveMount?.hide()
        rig?.get(PanelIds.LIVE_PLATE)?.hide()
        if (liveLengths.containsKey(LIVE)) liveLengths.remove(LIVE)
        if (liveTolerance.value != null) liveTolerance.value = null
    }

    /** Reticle, seat figure and reticle tag of a tracked pointer. */
    private fun hideAim() {
        reticle?.hide()
        liveSeat?.hide()
        rig?.get(PanelIds.RETICLE_TAG)?.hide()
        if (reticleLabel.value != null) reticleLabel.value = null
    }

    private fun placeCandidates(state: RulerUiState, frame: ViewFrame, pinViews: PinViews) {
        val rig = rig ?: return
        val root = stageRoot ?: return
        val m = materials ?: return
        val shared = tapeMeshes ?: return
        val headPos = frame.pose.position
        chipSeen.clear()
        val armed = state.pinTag?.pinId
        // no chips while a pin is being dragged: they would show stale distances over the reticle (QA R5-3 / R5-8)
        val dragging = drag?.pin != null || trackedDrag != null
        val from = if (armed != null && !dragging) pinViews.headPoint(armed) else null
        repeat(CHIP_SLOTS) { slot ->
            val chip = state.chips.getOrNull(slot)
            val to = if (chip != null) pinViews.headPoint(chip.pinId) else null
            val s = rig[PanelIds.chip(slot)]
            if (chip == null || from == null || to == null) {
                s?.hide()
                guides[slot]?.hide()
                chipKey[slot] = null
                return@repeat
            }
            val guide = guides[slot] ?: GuideThread(root, "guide:$slot", m, scales, shared).also { guides[slot] = it }
            guide.draw(from, to, headPos)
            // on the thread: a quarter of the way from the other pin, but at least 2.5 degrees from it, at most half way
            val length = to.distanceTo(from)
            val apart = to.distanceTo(headPos) * CHIP_APART_TAN
            val designed = if (length < 1e-4f) 0.5f else max(CHIP_SHARE, apart / length).coerceAtMost(0.5f)
            val halfW = ChipPillWidth.value / 2f * PanelRig.DP_M
            val halfH = ChipPillHeight.value / 2f * PanelRig.DP_M
            // a reading plate may stand where the chip belongs (the plate of a tape on the other pin): when the
            // chip appears it moves along its thread, towards the middle, to the first place that is clear of
            // the plates. Chosen once per chip, so it does not hop when the head moves.
            val key = armed + ">" + chip.pinId
            if (chipKey[slot] != key) {
                chipKey[slot] = key
                chipStep[slot] = 0
                for (step in 0..CHIP_STEPS) {
                    val spot = to.lerp(from, designed + (0.5f - designed) * step / CHIP_STEPS)
                    val rect = frame.rect(spot, halfW * scales.panel(spot.distanceTo(headPos)), halfH * scales.panel(spot.distanceTo(headPos)))?.inflated(PlacePlateUseCase.GAP_DEG)
                    if (rect == null || pillSeen.values.none { it.rect.intersects(rect) }) {
                        chipStep[slot] = step
                        break
                    }
                }
            }
            val share = designed + (0.5f - designed) * chipStep[slot] / CHIP_STEPS
            val on = to.lerp(from, share)
            val wanted = on + (headPos - on).normalized() * CHIP_LIFT_M
            val k0 = scales.panel(wanted.distanceTo(headPos))
            var range = clearRange(headPos, wanted, halfW * k0, halfH * k0, s?.lastYaw)
            // F9 (tag rule R3.3 for chips): in front of every plate it overlaps, 5 cm nearer, never nearer than 0.45 m
            frame.rect(wanted, halfW * k0, halfH * k0)?.inflated(CHIP_PROBE_DEG)?.let { probe ->
                var limit = range
                for (seen in pillSeen.values) if (seen.rect.intersects(probe)) limit = min(limit, seen.range - PlaceTagUseCase.IN_FRONT_M)
                range = if (limit < PlaceTagUseCase.MIN_RANGE_M) min(range, PlaceTagUseCase.MIN_RANGE_M) else limit
            }
            val at = headPos + (wanted - headPos).normalized() * range
            val k = scales.panel(range)
            s?.show(at, k)
            frame.rect(at, halfW * k, halfH * k)?.let { chipSeen[chip.pinId] = Seen(it, range, at) }
        }
    }

    // ------------------------------------------------------------------------------- tags

    /**
     * A tag and where it stands: the slot that was chosen for it is kept while it holds (so that the
     * tag does not hop when the head moves) and chosen again when the tag opens, when it changes its
     * size, and when the kept slot has failed for 0.4 s.
     */
    private inner class TagKeeper(private val kind: TagKind) {
        private var key: String? = null
        private var slot = -1
        private var steps = 0
        private var badSinceMs = NEVER
        private var leader: Strip? = null
        private var logged: String? = null

        fun reset() {
            key = null
            slot = -1
            badSinceMs = NEVER
            leader?.hide()
        }

        fun place(key: String, scene: TagScene, frame: ViewFrame, now: Long): TagPlacement {
            val planes = planeTracker.all
            var placement: TagPlacement? = null
            if (this.key == key && slot >= 0) {
                placement = placeTag.keep(frame, scene, planes, slot, steps)
                if (placement != null) {
                    badSinceMs = NEVER
                } else {
                    if (badSinceMs == NEVER) badSinceMs = now
                    if (now - badSinceMs <= REPLACE_AFTER_MS) placement = placeTag.at(frame, scene, planes, slot, steps)
                }
            }
            if (placement == null) {
                placement = placeTag.choose(frame, scene, planes)
                this.key = key
                slot = placement.slot
                steps = placement.steps
                badSinceMs = NEVER
            }
            val line = "TAG ${kind.name.lowercase(Locale.US)} key=$key slot=${PlaceTagUseCase.SLOT_NAMES[placement.slot]} steps=${placement.steps} leader=${placement.leader}"
            if (line != logged) {
                logged = line
                RulerLog.i(line + String.format(Locale.US, " range=%.3f anchor_range=%.3f", placement.range, scene.anchorRange))
            }
            return placement
        }

        /** The leader from the anchor to the middle of the nearest edge of the tag; hidden when [on] is false. */
        fun lead(on: Boolean, anchor: Vec3, at: Vec3, halfW: Float, halfH: Float, k: Float, headPos: Vec3, lastYaw: Float?) {
            if (!on) {
                leader?.hide()
                return
            }
            val root = stageRoot ?: return
            val m = materials ?: return
            val band = tapeMeshes?.band ?: return
            val strip = leader ?: Strip(root, band, m.leader, "tagLeader").also { leader = it }
            val to = nearestEdge(at, headPos, lastYaw, halfW, halfH, anchor)
            strip.draw(anchor, to, LEADER_WIDTH_M * max(1f, k), headPos - anchor)
        }

        fun destroy() {
            leader?.destroy()
            leader = null
            reset()
        }
    }

    private val pinTag = TagKeeper(TagKind.PIN)
    private val tapeTag = TagKeeper(TagKind.TAPE)
    private val toleranceModel = ComputeToleranceUseCase()
    private var coachShown = -1

    /** R2-1: when a tracked source was last there, and whether its loss has been reported. */
    private var lastTrackedMs = NEVER
    private var hadTracked = false
    private var sourceLostSent = false

    private fun placePinTag(state: RulerUiState, now: Long, frame: ViewFrame, pinViews: PinViews) {
        val rig = rig ?: return
        val tag = state.pinTag ?: return
        val self = pinSeen[tag.pinId]
        val slot = rig[PanelIds.PIN_TAG] ?: return
        if (self == null) {
            slot.hide()
            return
        }
        val headPos = frame.pose.position
        val k0 = scales.panel(self.range)
        val halfW = slot.widthDp / 2f * PanelRig.DP_M
        val halfH = slot.heightDp / 2f * PanelRig.DP_M

        // may not be covered at all: the pin and its tape head, the pins joined to it by a tape or a
        // candidate thread, the plates of its tapes, the candidate chips, the instrument body
        val hard = ArrayList<ViewRect>()
        val soft = ArrayList<ViewRect>()
        val joined = HashSet<String>()
        val ownTapes = HashSet<String>()
        hard += self.rect
        stubSeen?.let { hard += it }
        for (t in state.tapes) {
            if (t.a == tag.pinId || t.b == tag.pinId) {
                joined += if (t.a == tag.pinId) t.b else t.a
                ownTapes += t.id
            }
        }
        for (chip in state.chips) if (chip != null) joined += chip.pinId
        for ((id, seen) in pinSeen) {
            if (id == tag.pinId) continue
            if (id in joined) hard += seen.rect else soft += seen.rect
        }
        for ((id, seen) in pillSeen) {
            if (id in ownTapes) hard += seen.rect.inflated(0.5f) else soft += seen.rect
        }
        for (seen in chipSeen.values) hard += seen.rect.inflated(0.5f)
        bodySeen?.let { hard += it }
        // while the pin is dragged its ghost ring (where it was) must stay visible too
        (drag?.pin ?: trackedDrag)?.let { p -> frame.rect(p.from, GHOST_CLEAR_M, GHOST_CLEAR_M)?.let { hard += it } }

        val scene =
            TagScene(
                kind = TagKind.PIN,
                anchor = self.rect,
                anchorRange = self.range,
                halfYaw = ViewFrame.angle(halfW * k0, self.range),
                halfPitch = ViewFrame.angle(halfH * k0, self.range),
                clearYaw = ViewFrame.angle(PIN_TAG_CLEAR_M * k0, self.range),
                clearPitch = ViewFrame.angle(PIN_TAG_CLEAR_M * k0, self.range),
                hard = hard,
                soft = soft,
                behind = behind(),
                blades = bladeSeen.values.toList(),
            )
        val placement = pinTag.place(tag.pinId, scene, frame, now)
        val at = frame.point(placement.yaw, placement.pitch, placement.range)
        val k = scales.panel(placement.range)
        slot.show(at, k)
        pinTag.lead(placement.leader, self.center, at, halfW * k, halfH * k, k, headPos, slot.lastYaw)
    }

    private fun placeTapeTag(state: RulerUiState, now: Long, frame: ViewFrame, pinViews: PinViews) {
        val rig = rig ?: return
        val tag = state.tapeTag ?: return
        val slot = rig[PanelIds.TAPE_TAG] ?: return
        val plate = pillSeen[tag.tapeId]
        val entry = tapes[tag.tapeId]
        if (plate == null || entry == null) return // the plate is not in view: the tag stays where it was
        val headPos = frame.pose.position
        val k0 = scales.panel(plate.range)
        val halfW = slot.widthDp / 2f * PanelRig.DP_M
        val halfH = slot.heightDp / 2f * PanelRig.DP_M

        val hard = ArrayList<ViewRect>()
        val soft = ArrayList<ViewRect>()
        for ((id, seen) in pinSeen) if (id == entry.a || id == entry.b) hard += seen.rect else soft += seen.rect
        for ((id, seen) in pillSeen) if (id != tag.tapeId) soft += seen.rect
        bodySeen?.let { hard += it }

        val gap = ViewFrame.angle(TAPE_TAG_GAP_M * k0, plate.range)
        val scene =
            TagScene(
                kind = TagKind.TAPE,
                anchor = plate.rect,
                anchorRange = plate.range,
                halfYaw = ViewFrame.angle(halfW * k0, plate.range),
                halfPitch = ViewFrame.angle(halfH * k0, plate.range),
                clearYaw = plate.rect.halfYaw + gap,
                clearPitch = plate.rect.halfPitch + gap,
                hard = hard,
                soft = soft,
                behind = behind(),
                blades = bladeSeen.values.toList(),
            )
        val placement = tapeTag.place(tag.tapeId, scene, frame, now)
        val at = frame.point(placement.yaw, placement.pitch, placement.range)
        val k = scales.panel(placement.range)
        slot.show(at, k)
        tapeTag.lead(placement.leader, plate.center, at, halfW * k, halfH * k, k, headPos, slot.lastYaw)
    }

    /** Plates, chips and pins with their distance: a tag stands in front of those it overlaps. */
    private fun behind(): List<ViewObject> {
        val out = ArrayList<ViewObject>(pinSeen.size + pillSeen.size + chipSeen.size)
        for (seen in pinSeen.values) out += ViewObject(seen.rect, seen.range)
        for (seen in pillSeen.values) out += ViewObject(seen.rect, seen.range)
        for (seen in chipSeen.values) out += ViewObject(seen.rect, seen.range)
        return out
    }

    // ------------------------------------------------------------------------------- polls

    private fun pollPlanes(now: Long) {
        val count = planeTracker.count
        // no "scan finished" signal exists: scanning ends when no new plane arrived for a while
        val scanning = count == 0 || now - planeTracker.lastAddedAtMs < SCAN_QUIET_MS
        if (count == lastPlaneCount && scanning == lastScanning) return
        if (now - lastPlanesEmitMs < PLANES_EMIT_MS && scanning == lastScanning) return
        lastPlanesEmitMs = now
        if (count != lastPlaneCount) {
            RulerLog.i("PLANES n=$count scanning=$scanning")
            layoutDirty = true
        }
        lastPlaneCount = count
        lastScanning = scanning
        sink(RulerEvent.PlanesChanged(count, scanning))
    }

    /** Lost tracking freezes the session; the head tracker is the app's sign of it. */
    private fun pollTracking(now: Long) {
        val lost = headTracker.lost(now)
        if (lost == lastLost) return
        lastLost = lost
        sink(RulerEvent.TrackingChanged(lost))
    }

    /**
     * Tracked pointers: controller ray, hand ray and the tool tip of the contact channel (and the test
     * ray of the debug build, which can stand in for either device). Once per frame: pick the source,
     * read it, work out fine aim, steadiness and the commit position, draw the reticle with its tag and
     * the live tape from the armed pin, and on the press edge hand one commit to the ViewModel.
     *
     * One press, one action. While a tracked source is active, its press is what sets a pin on a
     * surface; the system tap that the same press may also produce on a surface collider is dropped (see
     * [onSpatialTap]). A press whose ray meets the instrument body or a panel with controls first is left
     * to the system's own click on that panel. System taps on pins, plates, chips and panel controls
     * work as before, except a tap on a pin within 0.7 s of a tracked commit (the same press).
     *
     * @return true when the live tape was drawn from the armed pin to the aim
     */
    private fun aimPointer(state: RulerUiState, system: UnitSystem, now: Long, frame: ViewFrame, pinViews: PinViews): Boolean {
        val forced = forcedSource
        val test = synthetic.sample(now)
        val controller = controllerSource.sample(now) ?: test?.takeIf { forced == PointerChannel.CONTROLLER }
        val hand = handSource.sample(now) ?: test?.takeIf { forced == PointerChannel.HAND }
        val channel = selectSource.select(forced, controller != null, hand != null, test != null)
        if (channel != activeChannel) {
            activeChannel = channel
            RulerLog.i("SRC active=${channel.tag}")
            steadyAim.reset()
            commitWindow.clear()
            triggerStages.reset()
            pinchLatch.reset()
            lastSnap = SnapKind.NONE
        }
        val input =
            when (channel) {
                PointerChannel.CONTROLLER -> controller
                PointerChannel.HAND -> hand
                PointerChannel.SYNTHETIC -> test
                PointerChannel.SYSTEM -> null
            }
        // "Raise a hand or pick up a controller": three seconds without a tracked source, after one was there, and
        // nothing from the system pointer since it went (the system pointer is a working source, R3-2)
        if (channel != PointerChannel.SYSTEM) {
            lastTrackedMs = now
            hadTracked = true
            if (sourceLostSent) {
                sourceLostSent = false
                sink(RulerEvent.SourceChanged(lost = false))
            }
        } else if (hadTracked && !sourceLostSent && now - lastTrackedMs >= SOURCE_LOST_MS && lastSystemInputMs < lastTrackedMs) {
            sourceLostSent = true
            sink(RulerEvent.SourceChanged(lost = true))
        }
        val pulling = drag?.let { it.begun && it.mode == DragMode.PULL } == true
        val measuring = state.phase == Phase.READY && !state.trackingLost && !state.blocked && state.clearDialog == null && state.pullHead == null
        if (input == null || !measuring || pulling) {
            trackedDrag?.let {
                // the source went away under a drag: the pin stays where it was
                endPinDrag(it, cancelled = true, fine = false, contact = it.contact)
                trackedDrag = null
            }
            // (a system pin drag owns the reticle and its tag: hiding them here every frame would keep the tag from composing)
            if (!pulling && drag?.pin == null) hideAim()
            return false
        }
        if (input.right != pointerRight) {
            pointerRight = input.right
            sink(RulerEvent.PointerHand(input.right))
        }
        val root = stageRoot ?: return false
        val m = materials ?: return false
        val r = reticle ?: Reticle(root, m).also { reticle = it }
        val headPos = frame.pose.position

        // the press: a pinch for a hand, the trigger in three stages for everything else
        val isHand = channel == PointerChannel.HAND
        val edge = if (isHand) pinchLatch.update(input.press, input.ready) else triggerStages.update(input.press, input.pressed)
        // fine aim lasts from the half press through the full press, so the commit keeps the slowed ray
        val fine = !isHand && (triggerStages.stage == TriggerStage.HALF || (triggerStages.stage == TriggerStage.FULL && fineAim.active))
        if (fine != fineOn) {
            fineOn = fine
            RulerLog.i("AIM fine=${if (fine) "on" else "off"}")
        }
        val ray = fineAim.apply(input.ray, fine)

        // contact channel: the tool tip is within reach of a surface
        val tip = input.tip
        val contact = if (tip != null) findContact.find(tip, headPos, planeTracker.all) else null

        // a tracked pin drag under way (round 5): the pin follows the aim while the trigger / pinch stays closed
        trackedDrag?.let { td ->
            val held = if (isHand) pinchLatch.pressed else triggerStages.stage == TriggerStage.FULL
            if (!held) {
                endPinDrag(td, cancelled = false, fine = fineAim.active || td.fine, contact = td.contact)
                trackedDrag = null
                return false
            }
            if (tip != null && contact != null) {
                td.contact = true
                td.fine = false
                val down = PointerRay(tip + contact.normal * CONTACT_LOOK_M, -contact.normal)
                stepPinDrag(td, aim.preview(down, exclude = td.pinId), null, tip, state, now, frame, pinViews)
            } else {
                td.contact = false
                td.fine = fineAim.active
                stepPinDrag(td, aim.preview(ray, exclude = td.pinId), ray, null, state, now, frame, pinViews)
            }
            logReticle(now, "drag", td.target, td.target?.distanceTo(headPos) ?: 0f, 0f, td.targetSnap.tag)
            return true
        }
        if (tip != null && contact != null) {
            val distance = tip.distanceTo(headPos)
            val progress = forcedSteady?.let { if (it) 1f else 0f } ?: steadyAim.update(tip, distance, now)
            commitWindow.add(tip, now)
            val touching = pinTouching(tip)
            r.showContact(tip, contact.normal, distance, progress >= 1f)
            liveSeat?.hide()
            placeReticleTag(null, tip, distance, frame) // the diamond says it all (simplify spec I)
            val aimSigma = toleranceModel.pinSigmaMm(Grade.CONTACT, SnapKind.NONE, false, distance, pinch = isHand)
            val live = drawAimTape(state, system, now, frame, pinViews, tip, contact.normal, touching, aimSigma)
            logReticle(now, "contact", tip, distance, progress, if (touching != null) "pin" else "none")
            if (edge && touching != null) {
                // the tip closed on a pin: it is picked up (a press that does not move it is a tap on it)
                startTrackedDrag(touching, channel, contact = true)
                return true
            }
            if (edge) {
                val committed = commitWindow.commit(now) ?: tip
                RulerLog.i(String.format(Locale.US, "TAP pointer kind=%s contact=true surface=%s off_mm=%.1f stage=%s", channel.tag, contact.plane.label, contact.distance * 1000f, RulerLog.pos(committed)))
                lastTrackedCommitMs = now
                sink(RulerEvent.ContactTriggered(committed, pinch = isHand))
            }
            return live
        }

        // ray channel
        val preview = aim.preview(ray)
        val point = preview.point
        val blocked = blockedBy(ray, if (point != null) point.distanceTo(ray.origin) else PANEL_CHECK_M)
        if (blocked != null) {
            // the ray points at the body or at a panel with controls: that is the system pointer's business
            hideAim()
            hideLiveTape()
            steadyAim.reset()
            commitWindow.clear()
            logReticle(now, "blocked:$blocked", null, 0f, 0f, "none")
            if (edge) RulerLog.i("AIM commit skipped reason=$blocked src=${channel.tag}")
            return false
        }
        if (point == null) {
            // nothing along the ray: the reticle floats in the air and says why
            steadyAim.reset()
            commitWindow.clear()
            val at = ray.origin + ray.direction * NO_SURFACE_M
            val distance = at.distanceTo(headPos)
            r.showNone(at, -ray.direction, distance)
            liveSeat?.hide()
            placeReticleTag(if (state.scanning) ReticleLabel.MAPPING else ReticleLabel.NO_SURFACE, at, distance, frame)
            hideLiveTape()
            logReticle(now, if (state.scanning) "mapping" else "none", at, distance, 0f, "none")
            if (edge) {
                RulerLog.i("TAP pointer kind=${channel.tag} stage=null")
                lastTrackedCommitMs = now
                sink(RulerEvent.PointerTriggered(ray, Grade.COARSE, channel.tag))
            }
            return false
        }
        val distance = point.distanceTo(headPos)
        val progress = forcedSteady?.let { if (it) 1f else 0f } ?: steadyAim.update(point, distance, now)
        val steady = progress >= 1f
        commitWindow.add(point, now)
        r.show(point, preview.normal, distance, progress, preview.far)
        showSeat(preview, point, preview.normal, headPos)
        // simplify spec I: a figure only where the reticle has none - the seat bars, the pin and the merged ring speak for themselves
        val label =
            when {
                fine -> ReticleLabel.FINE
                preview.far -> ReticleLabel.FAR
                else -> null
            }
        placeReticleTag(label, point, distance, frame)
        if (preview.snapped != lastSnap) {
            // a lighter touch and a tick when the aim jumps onto a seat (not for the pin that was set a moment ago)
            if (preview.snapped != SnapKind.NONE && (lastTrackedCommitMs == NEVER || now - lastTrackedCommitMs > TRACKED_TAP_MS)) sink(RulerEvent.AimSnapped(input.right))
            lastSnap = preview.snapped
        }
        val aimSigma = toleranceModel.pinSigmaMm(gradeAim.grade(contact = false, steady = steady, halfPress = fineAim.active), preview.snapped, preview.far, distance)
        val live = drawAimTape(state, system, now, frame, pinViews, point, preview.normal, preview.pinId, aimSigma, preview.far)
        val what =
            when {
                preview.snapped != SnapKind.NONE -> "snap"
                fine -> "fine"
                preview.far -> "far"
                steady -> "steady"
                else -> "aim"
            }
        logReticle(now, what, point, distance, progress, preview.snapped.tag)
        if (edge && preview.pinId != null) {
            // the ray closed on a pin: it is picked up (a press that does not move it is a tap on it)
            startTrackedDrag(preview.pinId, channel, contact = false)
            return true
        }
        if (edge) {
            val committed = commitWindow.commit(now) ?: point
            val grade = gradeAim.grade(contact = false, steady = steady, halfPress = fineAim.active)
            RulerLog.i("TAP pointer kind=${channel.tag} steady=$steady fine=${fineAim.active} grade=${grade.tag} stage=${RulerLog.pos(committed)}")
            lastTrackedCommitMs = now
            val to = committed - ray.origin
            sink(RulerEvent.PointerTriggered(if (to.length > 1e-4f) PointerRay(ray.origin, to.normalized()) else ray, grade, channel.tag))
        }
        return live
    }

    /** The live tape from the armed pin to where a tracked pointer aims; false when there is no such tape to draw. */
    private fun drawAimTape(state: RulerUiState, system: UnitSystem, now: Long, frame: ViewFrame, pinViews: PinViews, tail: Vec3, normal: Vec3, tailPin: String?, aimSigmaMm: Float?, aimFar: Boolean = false): Boolean {
        val armed = state.pins.firstOrNull { it.armed }
        val out = armed?.let { pinViews.headPoint(it.id) }
        val foot = armed?.let { pinViews.position(it.id) }
        if (armed == null || out == null || foot == null || armed.id == tailPin) {
            hideLiveTape()
            return false
        }
        val headPos = frame.pose.position
        // aimed at a pin: the tape goes to that pin's head; otherwise to where the new pin's head will be
        val tip = (if (tailPin != null) pinViews.headPoint(tailPin) else null) ?: (tail + normal * (ResolvePlacementUseCase.PIN_HEAD_M * scales.pin(tail.distanceTo(headPos))))
        val tailFoot = (if (tailPin != null) pinViews.position(tailPin) else null) ?: tail
        drawLiveTape(out, foot, tip, tailFoot, system, now, frame, liveTolerance(state.headSigmaMm, aimSigmaMm, preview = null, far = aimFar))
        return true
    }

    /**
     * M3: the tolerance a tape set now would get: head grade (+) aim grade, root sum of squares, on the
     * ladder. Null when either side is not known (the plate then shows "...").
     */
    private fun liveTolerance(headSigmaMm: Float?, aimSigmaMm: Float?, preview: AimPreview?, far: Boolean): LiveTolerance? {
        if (headSigmaMm == null || aimSigmaMm == null) return null
        val mm = toleranceModel.roundUp(sqrt(headSigmaMm * headSigmaMm + aimSigmaMm * aimSigmaMm))
        return LiveTolerance(mm, far || preview?.far == true)
    }

    /** The reticle tag: to the lower right of the reticle, clear of its rings, turned to the viewer like every room panel. */
    private fun placeReticleTag(label: ReticleLabel?, at: Vec3, distance: Float, frame: ViewFrame) {
        val slot = rig?.get(PanelIds.RETICLE_TAG) ?: return
        if (reticleLabel.value != label) reticleLabel.value = label
        if (label == null) {
            slot.hide()
            return
        }
        val headPos = frame.pose.position
        val k0 = scales.panel(distance)
        val halfW = tagWidthDp / 2f * PanelRig.DP_M
        val halfH = ReticleTagHeight.value / 2f * PanelRig.DP_M
        val clear = RETICLE_RADIUS_M * max(1f, distance * Reticle.SCALE_PER_M) + RETICLE_TAG_GAP_M
        val wanted = at + frame.pose.right * (clear + halfW * k0) - frame.pose.up * (clear + halfH * k0) + (headPos - at).normalized() * 0.03f
        squeeze(PanelIds.RETICLE_TAG, tagWidthDp)
        val range = clearRange(headPos, wanted, halfW * k0, halfH * k0, slot.lastYaw)
        slot.show(headPos + (wanted - headPos).normalized() * range, scales.panel(range))
    }

    /** Width of the pill the reticle tag draws, dp; called by the panel. */
    fun reportTag(widthDp: Float) {
        if (widthDp <= 0f) return
        tagWidthDp = widthDp
        squeeze(PanelIds.RETICLE_TAG, widthDp)
    }

    /**
     * What [ray] meets before it has gone [range] metres that takes a press itself: the instrument body,
     * or a shown panel with controls. Null when the way to the aim is free. (The live plate and the
     * reticle tag are no controls.)
     */
    private fun blockedBy(ray: PointerRay, range: Float): String? {
        val target = ray.origin + ray.direction * range
        val crossing = body?.crossing(ray.origin, target)
        if (crossing != null && crossing < 1f - 1e-3f) return "body"
        val rig = rig ?: return null
        for (slot in rig.all) {
            if (!slot.shown || slot.panel == null) continue
            if (slot.id == PanelIds.LIVE_PLATE || slot.id == PanelIds.RETICLE_TAG || slot.id == PanelIds.BODY) continue
            val center = slotPosition(slot)
            val turn = slotRotation(slot)
            val normal = turn.rotate(Vec3(0f, 0f, 1f))
            val along = ray.direction.dot(normal)
            if (abs(along) < 1e-5f) continue
            val t = (center - ray.origin).dot(normal) / along
            if (t <= 0f || t >= range) continue
            val p = ray.origin + ray.direction * t - center
            val halfW = slot.widthDp / 2f * PanelRig.DP_M * slot.scale * slot.widthFactor
            val halfH = slot.heightDp / 2f * PanelRig.DP_M * slot.scale
            if (abs(p.dot(turn.rotate(Vec3.RIGHT))) <= halfW && abs(p.dot(turn.rotate(Vec3.UP))) <= halfH) return "panel:${slot.id}"
        }
        return null
    }

    /** `RETICLE state=... stage=... dist=... stable=... snap=... src=...`: when the state changes, and every two seconds. */
    private fun logReticle(now: Long, state: String, at: Vec3?, distance: Float, progress: Float, snap: String) {
        if (!RulerProbe.enabled) return
        val key = "$state/$snap/${progress >= 1f}"
        if (key == reticleKey && now - reticleLogMs < RETICLE_LOG_MS) return
        reticleKey = key
        reticleLogMs = now
        RulerLog.i(String.format(Locale.US, "RETICLE state=%s stage=%s dist=%.3f stable=%.2f snap=%s src=%s", state, RulerLog.pos(at), distance, progress, snap, activeChannel.tag))
    }

    /** The rule of [PanelRig], checked twice a second; a broken rule is logged (`RIG violation ...`). */
    private fun checkRig(now: Long, frame: ViewFrame) {
        if (now - lastRigCheckMs < RIG_CHECK_MS) return
        lastRigCheckMs = now
        val rig = rig ?: return
        for (line in rig.violations(frame, ::slotPosition)) {
            val key = line.substringBefore(" yaw=")
            val last = violationLoggedMs[key]
            if (last != null && now - last < RIG_REPEAT_MS) continue
            violationLoggedMs[key] = now
            RulerLog.w(line + " hidemode=${rig.hideMode.name.lowercase(Locale.US)}")
        }
    }

    private fun countFrame(now: Long) {
        if (!fpsOn) return
        fpsFrames++
        val span = now - fpsSinceMs
        if (span < 1000L) return
        RulerLog.i(String.format(Locale.US, "FPS app=%.1f frames=%d ms=%d", fpsFrames * 1000f / span, fpsFrames, span))
        fpsFrames = 0
        fpsSinceMs = now
    }

    // =========================================================================================
    // input
    // =========================================================================================

    /** Where the system pointer's ray starts (see [SystemPointer]): the head, shifted to the eye when that is known. */
    private fun pointerOrigin(): Vec3 = head.position + head.right * (SystemPointer.eyeOffsetM ?: 0f)

    /**
     * The guard in front of the instrument body. A pinch that is aimed at the body must never act on
     * what lies behind it. The body's collider and panel normally take such a pinch; if one arrives on
     * a surface, a pin or a room panel although the line of sight to it runs through the body, it is
     * dropped here, the body panel is brought up afresh, and, where the pointer's origin is known,
     * the key under the line of sight is pressed instead.
     *
     * @return true when the pinch was dropped
     */
    private fun guardedByBody(target: Vec3, what: String): Boolean {
        val body = body ?: return false
        val origin = pointerOrigin()
        val crossing = body.crossing(origin, target) ?: return false
        if (crossing >= 1f - 1e-3f) return false
        var key: String? = null
        if (SystemPointer.eyeOffsetM != null) {
            val at = body.panelPoint(origin, target, BodyKeys.panelWidthPx, BodyKeys.panelHeightPx)
            if (at != null) key = BodyKeys.at(at.first, at.second)
        }
        RulerLog.w("TAP blocked by=body target=$what stage=${RulerLog.pos(target)} eye=${SystemPointer.eyeName} key=${key ?: "none"}")
        rig?.get(PanelIds.BODY)?.kick()
        if (key != null) sink(RulerEvent.BodyKeyPressed(key))
        return true
    }

    /**
     * A press on a room panel (plate, chip, tag) at the panel pixel ([x], [y]): true when the line of
     * sight to it runs through the instrument body, in which case the panel must not act on it.
     */
    fun pressGuarded(panelId: String, x: Float, y: Float): Boolean {
        val slot = rig?.get(panelId) ?: return false
        if (slot.mount != PanelRig.Mount.WORLD || !slot.shown) return false
        val pixel = PanelRig.DP_M / 2f * slot.scale
        val f = slot.facing
        val at = slot.position + f.right * ((x - slot.widthDp) * pixel * slot.widthFactor) + f.up * ((slot.heightDp - y) * pixel)
        return guardedByBody(at, "panel:$panelId")
    }

    /** A system spatial tap (gaze + pinch, emulator mouse, later also ray pinch) on one of our entities. */
    fun onSpatialTap(value: SpatialTapValue) {
        val now = SystemClock.elapsedRealtime()
        noteSystemInput()
        val target = value.targetEntity
        val name = runCatching { target?.getName() }.getOrNull() ?: "none"
        val stage = toStage(value.position.x, value.position.y, value.position.z)
        val kind = value.interactionKind
        val view = String.format(Locale.US, "view_px=(%.1f, %.1f, %.1f)", value.position.x, value.position.y, value.position.z)
        if (target == null || stage == null) {
            RulerLog.i("TAP other target=$name kind=$kind stage=${RulerLog.pos(stage)} $view")
            return
        }
        if (lastDragEndMs != NEVER && now - lastDragEndMs < TAP_AFTER_DRAG_MS) {
            RulerLog.i("TAP ignored reason=after-drag target=$name kind=$kind stage=${RulerLog.pos(stage)}")
            return
        }
        val pinId = pins?.pinIdOf(target) ?: stubPinOf(name)
        val planeId = colliders?.planeIdOf(target)
        val tracked = activeChannel != PointerChannel.SYSTEM
        when {
            pinId != null -> {
                if (guardedByBody(stage, name)) return
                if (tracked && lastTrackedCommitMs != NEVER && now - lastTrackedCommitMs < TRACKED_TAP_MS) {
                    // the same press: the tracked pointer has already acted on it
                    RulerLog.i("TAP pin id=$pinId kind=$kind ignored reason=tracked-commit src=${activeChannel.tag}")
                    return
                }
                RulerLog.i("TAP pin id=$pinId kind=$kind stage=${RulerLog.pos(stage)} $view")
                sink(RulerEvent.PinTapped(pinId))
            }
            planeId != null -> {
                if (guardedByBody(stage, name)) return
                if (tracked) {
                    // one press, one pin: on a surface the tracked pointer's own press places it
                    RulerLog.i("TAP surface plane=${name.removePrefix("plane:")} kind=$kind ignored reason=tracked-source src=${activeChannel.tag}")
                    return
                }
                // a press inside a pin's touch sphere was aimed at that pin: right after a pin is set its
                // collider is not up yet, and a quick second pinch on the same spot arrives on the surface
                val touched = pinTouching(stage)
                if (touched != null) {
                    RulerLog.i("TAP pin id=$touched kind=$kind stage=${RulerLog.pos(stage)} $view via=surface")
                    sink(RulerEvent.PinTapped(touched))
                } else {
                    RulerLog.i("TAP surface plane=${name.removePrefix("plane:")} kind=$kind stage=${RulerLog.pos(stage)} $view near=${nearestPin(stage)}")
                    sink(RulerEvent.SurfaceTapped(planeId, stage))
                }
            }
            colliders?.isBackstop(target) == true -> {
                if (guardedByBody(stage, name)) return
                if (tracked) {
                    RulerLog.i("TAP backstop kind=$kind ignored reason=tracked-source src=${activeChannel.tag}")
                    return
                }
                // the line of sight through the tapped point is kept as a pending aim until a surface arrives under it
                val origin = head.position
                val to = stage - origin
                RulerLog.i("TAP backstop kind=$kind stage=${RulerLog.pos(stage)} $view pending=${to.length > 1e-3f}")
                if (to.length > 1e-3f) sink(RulerEvent.UnmappedTapped(PointerRay(origin, to.normalized()))) else sink(RulerEvent.NothingTapped)
            }
            else -> RulerLog.i("TAP other target=$name kind=$kind stage=${RulerLog.pos(stage)} $view")
        }
    }

    /**
     * A system spatial drag (gaze + pinch and move, emulator mouse drag) on one of our entities.
     *
     * The system only says how far the hand has moved since the last event (`dragAmount`, view
     * pixels). A press on a surface or on a pin becomes a press-and-pull once the hand has moved a
     * centimetre: the press point is the tape head, and where the tape's tail is follows from the
     * pointing-line rule ([PullLineUseCase]), not from where the pointer is on screen. With fine-tune
     * open, a drag on the selected pin slides that pin along its surface instead.
     *
     * Log: `DRAG start|move|end|cancel ... stage=(...)`; `stage` is the press point at the start and
     * the tail (or the pin that is trued) afterwards.
     */
    fun onSpatialDrag(phase: String, start: Offset3D?, value: SpatialDragValue) {
        val now = SystemClock.elapsedRealtime()
        if (phase == "start") noteSystemInput()
        val amount = value.dragAmount
        when (phase) {
            "start" -> {
                drag = null
                val target = value.targetEntity
                val name = runCatching { target?.getName() }.getOrNull() ?: "none"
                val stage = if (start != null) toStage(start.x, start.y, start.z) else null
                var pinId = target?.let { pins?.pinIdOf(it) } ?: stubPinOf(name)
                var planeId = target?.let { colliders?.planeIdOf(it) }
                // The system can name the surface behind a pin as the target although the press point lies
                // inside the pin's touch sphere (seen on the emulator): such a press is a press on that pin.
                if (pinId == null && planeId != null && stage != null) {
                    val near = pinTouching(stage)
                    if (near != null) {
                        pinId = near
                        planeId = null
                    }
                }
                // A press inside a pin's touch sphere moves that pin even when the system names the stub (QA R5-4): the pin's head wins.
                var targetName = name
                if (name.startsWith("stub:") && stage != null) {
                    val near = pinTouching(stage)
                    if (near != null) {
                        pinId = near
                        targetName = "pin:$near"
                    }
                }
                val retarget = if (pinId != null && (targetName != name || (target != null && pins?.pinIdOf(target) == null && stubPinOf(name) == null))) " retarget=pin:$pinId" else ""
                RulerLog.i("DRAG start target=$name kind=${value.interactionKind} stage=${RulerLog.pos(stage)} device=${devicePose(value)}$retarget")
                // While a tracked pointer is in charge its own press sets pins: a system drag only moves a pin
                // (or pulls from its stub); it does not pull a tape from a surface as well.
                if (activeChannel != PointerChannel.SYSTEM && pinId == null) {
                    RulerLog.i("DRAG start ignored reason=tracked-source src=${activeChannel.tag}")
                    return
                }
                if (stage == null || (pinId == null && planeId == null)) return
                if (guardedByBody(stage, name)) return
                drag = Drag(pinId, planeId, stage, targetName, value.interactionKind.toString())
            }
            "move" -> {
                val d = drag ?: return
                d.moves++
                d.totalPx = Vec3(d.totalPx.x + amount.x, d.totalPx.y + amount.y, d.totalPx.z + amount.z)
                d.hand = handDelta(d.totalPx) ?: d.hand
                if (!d.begun && d.hand.length >= PULL_START_M) beginDrag(d, now)
                if (d.begun && d.mode == DragMode.PIN) {
                    val deltaPx = sqrt(amount.x * amount.x + amount.y * amount.y + amount.z * amount.z)
                    d.pin?.let { p -> movePinDrag(p, d.hand - d.prevHand, deltaPx, now) }
                    d.prevHand = d.hand
                }
                if (d.moves <= 3 || now - d.lastLogMs >= DRAG_LOG_MS) {
                    d.lastLogMs = now
                    RulerLog.i(
                        String.format(
                            Locale.US, "DRAG move mode=%s amount_px=(%.1f, %.1f, %.1f) total_px=(%.1f, %.1f, %.1f) hand=%s stage=%s n=%d",
                            modeName(d), amount.x, amount.y, amount.z, d.totalPx.x, d.totalPx.y, d.totalPx.z, RulerLog.pos(d.hand), RulerLog.pos(dragPoint(d)), d.moves,
                        )
                    )
                }
            }
            "end", "cancel" -> {
                val d = drag
                drag = null
                if (d == null) {
                    RulerLog.i("DRAG $phase mode=none stage=null")
                    return
                }
                val cancelled = phase == "cancel"
                if (!d.begun) {
                    RulerLog.i("DRAG $phase mode=none hand=${RulerLog.pos(d.hand)} stage=${RulerLog.pos(d.press)} n=${d.moves}")
                    return
                }
                lastDragEndMs = now
                when (d.mode) {
                    DragMode.PIN -> {
                        val p = d.pin
                        RulerLog.i("DRAG $phase mode=pin hand=${RulerLog.pos(d.hand)} stage=${RulerLog.pos(dragPoint(d))} n=${d.moves}")
                        if (p != null) endPinDrag(p, cancelled, fine = p.gain.fineOn, contact = false)
                    }
                    DragMode.PULL -> {
                        val ray = pullLine.ray(head.position, d.press, d.hand)
                        val steady = now - d.tailMovedMs >= STEADY_MS
                        RulerLog.i("DRAG $phase mode=pull hand=${RulerLog.pos(d.hand)} stage=${RulerLog.pos(d.tail)} steady=$steady n=${d.moves}")
                        pullEndedMs = now
                        pullEndedHead = ui.pullHead
                        sink(RulerEvent.DragEnded(if (cancelled) null else ray, steady, cancelled))
                        hideLiveTape()
                        hideAim()
                    }
                    DragMode.NONE -> RulerLog.i("DRAG $phase mode=none hand=${RulerLog.pos(d.hand)} stage=null n=${d.moves}")
                }
            }
        }
    }

    /**
     * For the log of a surface tap: the pin nearest to the line of sight of the tap that was not taken,
     * as `<id>:<degrees off>deg`, or `none`.
     */
    private fun nearestPin(point: Vec3): String {
        val views = pins ?: return "none"
        val origin = pointerOrigin()
        val to = point - origin
        if (to.length < 1e-3f) return "none"
        val direction = to.normalized()
        var best: String? = null
        var bestAngle = Float.MAX_VALUE
        for (pin in ui.pins) {
            val at = views.headPoint(pin.id) ?: continue
            val toPin = at - origin
            if (toPin.length < 1e-3f) continue
            val angle = SnapPointUseCase.angleDeg(direction, toPin.normalized())
            if (angle < bestAngle) {
                bestAngle = angle
                best = pin.id
            }
        }
        return if (best == null) "none" else String.format(Locale.US, "%s:%.2fdeg", best, bestAngle)
    }

    /** The pin whose touch sphere contains [point], if any. */
    private fun pinTouching(point: Vec3): String? {
        val views = pins ?: return null
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for (pin in ui.pins) {
            val at = views.headPoint(pin.id) ?: continue
            val reach = PinViews.TOUCH_RADIUS_M * scales.pinTouch(at.distanceTo(head.position)) + 0.002f
            val d = at.distanceTo(point)
            if (d <= reach && d < bestDistance) {
                best = pin.id
                bestDistance = d
            }
        }
        return best
    }

    private fun beginDrag(d: Drag, now: Long) {
        d.begun = true
        d.tailMovedMs = now
        val pinId = d.pinId
        if (pinId != null && !d.target.startsWith("stub:")) {
            // a press on a pin moves the pin (round 5); the stub of a pin pulls a tape from it
            val from = pins?.position(pinId)
            val n = pins?.normal(pinId)
            if (from != null && n != null) {
                d.mode = DragMode.PIN
                val p = PinDragState(pinId, from, n, "system", wasSelected = ui.pinTag?.pinId == pinId)
                d.pin = p
                d.prevHand = d.hand
                // The system counts its drag travel from the point where it recognised the drag (the press point it
                // reports, about a degree along the way): the line of sight starts there, turned by the travel so far.
                val headPos = head.position
                val q = d.press - headPos
                val pitchDeg = Math.toDegrees(asin(head.forward.y.coerceIn(-1f, 1f).toDouble())).toFloat()
                p.equivalent = drags.equivalentDepth(q.length, pitchDeg)
                val k = pins?.scale(pinId) ?: 1f
                // the cap as it rests after the drag (the pin is selected then): the aim ray is lowered by this much
                p.capHeight = ResolvePlacementUseCase.PIN_HEAD_M * k + PinViews.ARMED_LIFT_M
                val cap = from + n * p.capHeight
                val line = drags.startLine(headPos, cap, d.press, d.hand, head.right, head.up, head.forward)
                val dir = drags.turn(line, d.hand, p.equivalent, head.right, head.up, head.forward)
                p.rawDir = dir
                p.aimDir = dir
                p.gain.begin(now)
                p.lastMoveMs = now
                pins?.setDrag(pinId, from, n)
                sink(RulerEvent.PinDragStarted(pinId))
                RulerLog.i("DRAG begin mode=pin target=${d.target} kind=${d.kind} stage=${RulerLog.pos(d.press)}")
                return
            }
        }
        sink(RulerEvent.DragStarted(d.planeId, d.pinId, d.press))
        d.mode = aim.dragMode()
        RulerLog.i("DRAG begin mode=${modeName(d)} target=${d.target} kind=${d.kind} stage=${RulerLog.pos(d.press)}")
    }

    /** The line of sight from [head] through [point]. */
    private fun throughRay(head: Vec3, point: Vec3): PointerRay? {
        val to = point - head
        return if (to.length < 1e-4f) null else PointerRay(head, to.normalized())
    }

    /** The pin a `stub:<pinId>` entity (the tape-head stub's touch target) belongs to. */
    private fun stubPinOf(name: String): String? = if (name.startsWith("stub:")) name.removePrefix("stub:") else null

    // ------------------------------------------------------------------------------- pin drag (round 5)

    /**
     * One frame of a pin drag by the system pointer: the aim line that the drag events turned ([movePinDrag]) meets
     * the surfaces; between events stillness engages the fine gain (the system sends an event only while the hand moves).
     */
    private fun drawSystemPinDrag(state: RulerUiState, now: Long, frame: ViewFrame, pinViews: PinViews) {
        val d = drag ?: return
        val p = d.pin ?: return
        if (!d.begun || d.mode != DragMode.PIN) return
        val before = p.gain.gain
        if (p.gain.tick(now) != before) logGain(p, 0f)
        val dir = p.aimDir ?: return
        // the ray is lowered by the cap's height over the surface: its hit is where the foot must stand for the cap to lie on the line of sight
        val ray = PointerRay(frame.pose.position - p.fromNormal * p.capHeight, dir)
        p.fine = p.gain.fineOn
        stepPinDrag(p, aim.preview(ray, exclude = p.pinId), ray, null, state, now, frame, pinViews)
    }

    /**
     * One drag event of a system pin drag (round 5b, QA R5-1 / R5-2). The hand step moves the raw line of sight by
     * the cursor's own view-plane motion: the system reports the cursor's travel on a plane through the recognised
     * press point, scaled by `equivalentDepth` of that point's depth (fixed for the drag), so the step is divided by
     * that and a cursor move of x pixels moves the line by x pixels at any distance. The line's angular speed over
     * the event sets the gain, and
     * the aim line (the one the pin follows) turns by the step scaled by it; the raw line keeps the whole travel, so a
     * fast move after a fine stretch is not lost. Every change of the gain is logged: `PIN drag gain id= gain= speed= reason=`.
     */
    private fun movePinDrag(p: PinDragState, step: Vec3, deltaPx: Float, now: Long) {
        val raw = p.rawDir ?: return
        val aimed = p.aimDir ?: return
        val turned = drags.turn(raw, step, p.equivalent, head.right, head.up, head.forward)
        // the event's own speed: its step over its own window (the first event after a rest is not averaged over the rest)
        val dt = if (p.lastMoveMs == NEVER) 0L else (now - p.lastMoveMs).coerceAtMost(AdaptiveGain.SPEED_WINDOW_MS)
        val speed = if (dt > 0L) drags.angularSpeedDegPerS(raw, turned, dt) else 0f
        p.lastMoveMs = now
        p.rawDir = turned
        val before = p.gain.gain
        val g = p.gain.sample(speed, deltaPx >= AdaptiveGain.MOTION_PX, now)
        if (g != before) logGain(p, speed)
        p.aimDir = drags.turn(aimed, step * g, p.equivalent, head.right, head.up, head.forward)
    }

    private fun logGain(p: PinDragState, speed: Float) {
        RulerLog.i(String.format(Locale.US, "PIN drag gain id=%s gain=%s speed=%.1f reason=%s", p.pinId, gainTag(p.gain.gain), speed, p.gain.lastChange))
    }

    /**
     * The part of a pin drag that both pointers share: the target is the pointer's hit on the surfaces
     * (the last valid one when there is none), the reticle with its seat marks stands there, a hollow
     * ghost ring stays where the pin was, the pin itself follows with a short lag, and the tapes that
     * end on it count live (plates and the body's readout).
     */
    private fun stepPinDrag(p: PinDragState, preview: AimPreview, ray: PointerRay?, tip: Vec3?, state: RulerUiState, now: Long, frame: ViewFrame, pinViews: PinViews) {
        val headPos = frame.pose.position
        val point = preview.point
        if (point != null && preview.pinId == null) {
            p.target = point
            p.targetNormal = preview.normal
            p.targetRay = ray
            p.targetTip = tip
            p.targetFar = preview.far
            if (preview.snapped != p.targetSnap) {
                // a tick when the pin jumps onto a seat
                if (preview.snapped != SnapKind.NONE) sink(RulerEvent.AimSnapped(pointerRight))
                p.targetSnap = preview.snapped
            }
        }
        val target = p.target ?: return
        val dt = if (p.lagMs == NEVER) 0L else now - p.lagMs
        p.lagMs = now
        val lag = p.lagPos
        // the system drag has no smoothing of its own (round 5c): the pin stands where the events put the line; a tracked pointer's pin lags 30 ms
        val pos = if (p.via == "system" || lag == null || dt <= 0L) target else lag.lerp(target, 1f - exp(-dt / DragPinUseCase.LAG_MS))
        p.lagPos = pos
        pinViews.setDrag(p.pinId, pos, p.targetNormal)
        ghost(p.from, p.fromNormal, pinViews.scale(p.pinId), show = true)
        val distance = target.distanceTo(headPos)
        val root = stageRoot ?: return
        val m = materials ?: return
        val r = reticle ?: Reticle(root, m).also { reticle = it }
        // the reticle stands above the lifted pin's top: the pin never hides its ring while it lags behind the target
        val overPin = PinViews.HEIGHT_M * pinViews.scale(p.pinId) + PinViews.ARMED_LIFT_M + PinViews.DRAG_LIFT_M + RETICLE_OVER_PIN_M
        r.show(target, p.targetNormal, distance, 0f, p.targetFar, lift = overPin)
        if (point != null) showSeat(preview, target, p.targetNormal, headPos) else liveSeat?.hide()
        placeReticleTag(if (p.fine) ReticleLabel.FINE else if (p.targetFar) ReticleLabel.FAR else null, target, distance, frame)
        if (p.lastLiveMs == NEVER || now - p.lastLiveMs >= LIVE_WRITE_MS) {
            p.lastLiveMs = now
            var reference: Float? = null
            for (t in state.tapes) {
                if (t.a != p.pinId && t.b != p.pinId) continue
                val other = pinViews.position(if (t.a == p.pinId) t.b else t.a) ?: continue
                val length = other.distanceTo(pos)
                liveLengths[t.id] = length
                reference = length
            }
            if (reference != null) liveLengths[LIVE] = reference else if (liveLengths.containsKey(LIVE)) liveLengths.remove(LIVE)
        }
        if (p.lastLogMs == NEVER || now - p.lastLogMs >= PIN_DRAG_LOG_MS) {
            p.lastLogMs = now
            RulerLog.i("PIN drag move id=${p.pinId} from=${RulerLog.pos(p.from)} to=${RulerLog.pos(target)} via=${p.via} gain=${gainTag(if (p.fine) 0.25f else 1f)} snap=${p.targetSnap.tag}")
        }
    }

    /** Release of a pin drag: the pin lands where the pointer aimed (the ViewModel resolves the surface), or stays put. */
    private fun endPinDrag(p: PinDragState, cancelled: Boolean, fine: Boolean, contact: Boolean) {
        val pinViews = pins
        for (slot in 0 until PLATE_SLOTS) plateHover[PanelIds.plate(slot)] = false
        // the pin stays drawn at its target until the state has caught up (render() clears the drag)
        p.target?.let { pinViews?.setDrag(p.pinId, it, p.targetNormal) }
        ghost(show = false)
        hideAim()
        for (t in ui.tapes) if ((t.a == p.pinId || t.b == p.pinId) && liveLengths.containsKey(t.id)) liveLengths.remove(t.id)
        if (liveLengths.containsKey(LIVE)) liveLengths.remove(LIVE)
        val grade = drags.gradeOnRelease(contact, fine)
        val tap = p.target == null || drags.cancelled(p.from, p.target!!)
        RulerLog.i("PIN drag release id=${p.pinId} from=${RulerLog.pos(p.from)} to=${RulerLog.pos(p.target)} via=${p.via} gain=${gainTag(if (fine) 0.25f else 1f)} grade=${grade.tag} cancelled=$cancelled tap=$tap")
        sink(RulerEvent.PinDragEnded(p.pinId, p.targetRay, p.targetTip, grade, p.via, gainTag(if (fine) 0.25f else 1f), cancelled))
        // a tracked press that did not move the pin was a tap on it: a selected pin is let go of, as before
        if (p.via != "system" && !cancelled && tap && p.wasSelected) sink(RulerEvent.PinTapped(p.pinId))
    }

    private fun gainTag(gain: Float): String = if (gain < 1f) "1:4" else "1:1"

    /** One end of a tape is the pin being dragged (by the system pointer or by a tracked one). */
    private fun onDraggedPin(a: String, b: String): Boolean {
        val id = drag?.pin?.pinId ?: trackedDrag?.pinId ?: return false
        return a == id || b == id
    }

    /**
     * The hollow ring that marks where the dragged pin was: washer diameter, `labelPrimaryLight` 70 % over a 1 mm
     * `bladeEdge` casing (0.3 mm nearer the surface), both with the depth test off like the reticle, so that it reads
     * on a dark floor and does not lose to the room mesh.
     */
    private fun ghost(at: Vec3 = Vec3.ZERO, normal: Vec3 = Vec3.UP, scale: Float = 1f, show: Boolean) {
        val root = stageRoot ?: return
        val m = materials ?: return
        val g =
            ghostRing
                ?: ModelEntity(MeshFactory.ring(0.0128f, 0.015f, "pin_ghost"), m.pinGhost).also {
                    runCatching { it.setName("pinGhost") }
                    root.addChild(it)
                    ghostRing = it
                }
        val casing =
            ghostCasing
                ?: ModelEntity(MeshFactory.ring(0.0118f, 0.016f, "pin_ghost_case"), m.reticleCasing).also {
                    runCatching { it.setName("pinGhostCase") }
                    root.addChild(it)
                    ghostCasing = it
                }
        g.enabled = show
        casing.enabled = show
        if (show) {
            val rotation = Quat4.fromTo(Vec3.UP, normal)
            val k = Vec3(scale, scale, scale)
            g.setLocal(position = at + normal * GHOST_LIFT_M, rotation = rotation, scale = k)
            casing.setLocal(position = at + normal * (GHOST_LIFT_M - GHOST_CASING_BACK_M), rotation = rotation, scale = k)
        }
    }

    /** A tracked pointer closed on a pin: the pin follows the pointer until the trigger / pinch opens. */
    private fun startTrackedDrag(pinId: String, channel: PointerChannel, contact: Boolean) {
        val from = pins?.position(pinId) ?: return
        val n = pins?.normal(pinId) ?: Vec3.UP
        val p = PinDragState(pinId, from, n, channel.tag, wasSelected = ui.pinTag?.pinId == pinId)
        p.contact = contact
        trackedDrag = p
        pins?.setDrag(pinId, from, n)
        sink(RulerEvent.PinDragStarted(pinId))
        RulerLog.i("PIN drag begin id=$pinId via=${channel.tag} contact=$contact from=${RulerLog.pos(from)}")
    }

    private fun modeName(d: Drag): String =
        when {
            !d.begun -> "press"
            d.mode == DragMode.PULL -> "pull"
            d.mode == DragMode.PIN -> "pin"
            else -> "none"
        }

    /** What a drag is moving right now: the tail of the tape, or the pin that is trued. */
    private fun dragPoint(d: Drag): Vec3? =
        when {
            !d.begun -> d.press
            d.mode == DragMode.PULL -> d.tail
            d.mode == DragMode.PIN -> d.pin?.target ?: d.pinId?.let { pins?.position(it) }
            else -> null
        }

    private fun devicePose(value: SpatialDragValue): String =
        runCatching {
                val p = value.inputDevicePose.rawPosition
                String.format(Locale.US, "(%.1f, %.1f, %.1f)", p.x, p.y, p.z)
            }
            .getOrDefault("null")

    /** A hand movement in view pixels -> Stage metres. */
    private fun handDelta(px: Vec3): Vec3? {
        val moved = toStage(px.x, px.y, px.z) ?: return null
        val origin = toStage(0f, 0f, 0f) ?: return null
        return moved - origin
    }

    /** View-local pixels of a spatial tap -> Stage metres, through the SpatialView content conversion. */
    private fun toStage(x: Float, y: Float, z: Float): Vec3? {
        val c = content ?: return null
        val probe = contentProbe ?: return null
        return runCatching {
                val local = c.convertPosition(Vector3(x, y, z), ViewCoordinateSpace.Local, c.localSpatialCoordinateSpace)
                probe.convertPositionTo(local, null).domain()
            }
            .onFailure { RulerLog.w("TAP convert failed: $it") }
            .getOrNull()
    }

    // =========================================================================================
    // test-harness probe (driven by the debug receiver only)
    // =========================================================================================

    override fun poseLine(): String {
        val q = head.rotation
        val eye = pointerOrigin()
        return "POSE hmd pos=${RulerLog.pos5(head.position)} ${RulerLog.quat5(q)} fwd=${RulerLog.pos5(head.forward)} up=${RulerLog.pos5(head.up)} right=${RulerLog.pos5(head.right)} valid=$hasHead" +
            " pointer_eye=${SystemPointer.eyeName} eye_pos=${RulerLog.pos5(eye)}"
    }

    /**
     * Stage position of a slot's holder: where it was last put (shown or away). Room panels are in Stage
     * space already; the others hang on the head-locked body or on a head anchor, whose pose the app
     * computes from the head pose (the engine does not report the transform of a head anchor).
     */
    private fun slotPosition(slot: PanelRig.Slot): Vec3 =
        when (slot.mount) {
            PanelRig.Mount.WORLD -> slot.placed
            // fixed to the head-locked body: the body's mount + the place in the mount's frame
            PanelRig.Mount.CHILD -> (body?.center ?: (head.position + head.rotation.rotate(BodyView.HEAD_OFFSET))) + (body?.anchorRotation ?: head.rotation).rotate(slot.placed)
            PanelRig.Mount.HEAD -> head.position + head.rotation.rotate((slot.headOffset ?: Vec3.ZERO) + slot.placed)
        }

    private fun slotRotation(slot: PanelRig.Slot): Quat4 =
        when (slot.mount) {
            PanelRig.Mount.WORLD -> slot.facing.rotation
            PanelRig.Mount.CHILD -> (body?.anchorRotation ?: head.rotation) * slot.headRotation
            PanelRig.Mount.HEAD -> head.rotation * slot.headRotation
        }

    override fun panelLines(): List<Pair<String, String>> {
        val rig = rig ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (slot in rig.all) {
            if (!slot.shown || slot.panel == null) continue
            val position = slotPosition(slot)
            val rotation = slotRotation(slot)
            val size = RulerProbe.sizeOf(slot.id)
            val w = size?.first ?: (slot.widthDp * 2f).toInt()
            val h = size?.second ?: (slot.heightDp * 2f).toInt()
            // stretch_x: the panel is this much narrower than scale x size (its content fills its whole width)
            out += slot.id to String.format(Locale.US, "PANEL id=%s pos=%s %s scale=%.5f size_px=%dx%d stretch_x=%.5f", slot.id, RulerLog.pos5(position), RulerLog.quat5(rotation), slot.scale, w, h, slot.widthFactor)
        }
        return out
    }

    override fun rigLines(): List<String> {
        val rig = rig ?: return emptyList()
        val frame = ViewFrame(head.view)
        val out = ArrayList<String>()
        out += rig.dump(frame, ::slotPosition, ::slotRotation)
        val body = body
        if (body != null) {
            out +=
                String.format(
                    Locale.US,
                    "RIG body center=%s %s half=(%.5f, %.5f, %.5f) rest_offset=%s collider=%s name=body",
                    RulerLog.pos5(body.center), RulerLog.quat5(body.rotation), BodyView.HALF_X, BodyView.HALF_Y, BodyView.HALF_Z, RulerLog.pos5(body.applied), body.colliderName.replace(' ', '_'),
                )
            out += "RIG body engine mount=${RulerLog.pos5(runCatching { body.mount.convertPositionTo(Vector3.ZERO, stageRoot).domain() }.getOrNull())} anchor=${RulerLog.pos5(runCatching { body.anchor.convertPositionTo(Vector3.ZERO, stageRoot).domain() }.getOrNull())}"
        }
        val violations = rig.violations(frame, ::slotPosition)
        out += violations
        out += "RIG summary slots=${rig.all.size} shown=${rig.all.count { it.shown }} bound=${rig.all.count { it.panel != null }} violations=${violations.size} hidemode=${rig.hideMode.name.lowercase(Locale.US)}"
        return out
    }

    override fun planesSummary(): List<String> =
        planeTracker.all.map { p ->
            "PLANE ${p.label} kind=${p.kind} center=${RulerLog.pos(p.centroid)} normal=${RulerLog.pos(p.normal)} verts=${p.vertices.joinToString(" ") { RulerLog.pos(it) }}"
        }

    override fun setSyntheticRay(values: FloatArray?): String {
        if (values == null || values.size < 6) {
            synthetic.set(null)
            return "POINTER cleared"
        }
        val origin = Vec3(values[0], values[1], values[2])
        val direction = Vec3(values[3], values[4], values[5]).normalized()
        if (direction.length < 0.5f) return "POINTER rejected: zero direction"
        synthetic.set(PointerRay(origin, direction))
        val hit = hits.nearest(planeTracker.all, origin, direction)
        return "POINTER set origin=${RulerLog.pos(origin)} dir=${RulerLog.pos(direction)} hit=${hit?.let { "${it.plane.label} stage=${RulerLog.pos(it.point)} dist=${String.format(Locale.US, "%.4f", it.distance)}" } ?: "none"}"
    }

    override fun triggerSynthetic(stage: String): String {
        if (!synthetic.active) return "TRIGGER ignored: no pointer"
        when (stage) {
            "half" -> synthetic.hold(0.5f)
            "full" -> synthetic.hold(1f)
            "release" -> synthetic.hold(0f)
            else -> {
                synthetic.trigger()
                return "TRIGGER queued"
            }
        }
        return "TRIGGER $stage"
    }

    override fun setSyntheticTip(values: FloatArray?): String {
        if (values == null || values.size < 3) {
            synthetic.tip(null)
            return "CONTACT tip cleared"
        }
        val tip = Vec3(values[0], values[1], values[2])
        synthetic.tip(tip)
        val hit = findContact.find(tip, head.position, planeTracker.all)
        return "CONTACT tip=${RulerLog.pos(tip)} surface=${hit?.let { "${it.plane.label} off_mm=${String.format(Locale.US, "%.1f", it.distance * 1000f)}" } ?: "none"}"
    }

    override fun loseSynthetic(): String {
        synthetic.lose()
        return "POINTER lost"
    }

    override fun forceSource(tag: String): String {
        forcedSource = PointerChannel.fromTag(tag)
        return "SRC forced=${forcedSource?.tag ?: "auto"}"
    }

    override fun sourceLine(): String {
        val now = SystemClock.elapsedRealtime()
        fun describe(input: io.embodit.spatialruler.platform.input.PointerInput?): String =
            if (input == null) "none"
            else String.format(Locale.US, "origin=%s dir=%s tip=%s press=%.2f pressed=%s ready=%s hand=%s", RulerLog.pos5(input.ray.origin), RulerLog.pos5(input.ray.direction), RulerLog.pos(input.tip), input.press, input.pressed, input.ready, if (input.right) "right" else "left")
        return "SRC active=${activeChannel.tag} forced=${forcedSource?.tag ?: "auto"} trigger=${triggerStages.stage.name.lowercase(Locale.US)} synthetic=${synthetic.active} controller[${describe(controllerSource.sample(now))}] hand[${describe(handSource.sample(now))}]"
    }

    override fun parkBody(): String {
        val b = body ?: return "BODY ignored: no body"
        b.park(head, planeTracker.all, SystemClock.elapsedRealtime())
        return "BODY park requested"
    }

    override fun forceSteady(on: Boolean?): String {
        forcedSteady = on
        return "STEADY forced=${on ?: "auto"}"
    }

    override fun setLitMaterials(lit: Boolean): String {
        val m = materials ?: return "LIT ignored: no scene"
        m.lit = lit
        pins?.refreshMaterials()
        body?.refreshMaterials()
        tapes.values.forEach {
            it.blade.refreshMaterials()
            it.mount.refreshMaterials()
        }
        stub?.refreshMaterials()
        liveBlade?.refreshMaterials()
        liveMount?.refreshMaterials()
        return "LIT ${if (lit) "on" else "off"}"
    }

    override fun tuneLitMaterials(steelMetallic: Float, steelGlow: Float, shellRed: Float, shellGreen: Float, shellBlue: Float): String {
        val m = materials ?: return "LIT ignored: no scene"
        m.tuneLit(steelMetallic, steelGlow, Vec3(shellRed, shellGreen, shellBlue))
        return "LIT tuned steel_metallic=$steelMetallic steel_glow=$steelGlow shell_glow=($shellRed, $shellGreen, $shellBlue)"
    }

    override fun setToneMapping(on: Boolean): String {
        val m = materials ?: return "TONEMAP ignored: no scene"
        m.toneMapped = on
        return "TONEMAP ${if (on) "on" else "off"}"
    }

    override fun setFpsLine(on: Boolean): String {
        fpsOn = on
        fpsFrames = 0
        fpsSinceMs = SystemClock.elapsedRealtime()
        return "FPS line ${if (on) "on" else "off"}"
    }

    override fun forceTrackingLost(lost: Boolean): String {
        headTracker.forceLost = lost
        return "TRACK forced lost=$lost"
    }

    override fun bodyCollider(behindGlassMm: Float?): String {
        val body = body ?: return "COLLIDER ignored: no scene"
        if (behindGlassMm == null) body.frameCollider() else body.boxCollider(behindGlassMm / 1000f)
        return "COLLIDER body ${body.colliderName}"
    }

    override fun bodyGhost(on: Boolean): String {
        val body = body ?: return "GHOST ignored: no scene"
        val slot = rig?.get(PanelIds.BODY) ?: return "GHOST ignored: no scene"
        body.setColliderEnabled(!on)
        if (on) slot.hide() else slot.show(null, null)
        return "GHOST body ${if (on) "on: panel and collider let pinches through, the guard has to catch them" else "off"}"
    }

    override fun calibration(on: Boolean, dist: Float, scale: Float, offX: Float, offY: Float, sizeDp: Int, facing: Boolean): String {
        val rig = rig ?: return "CALIB ignored: no scene"
        // always a fresh holder: the offsets are baked into its anchor component, and a panel that
        // is resized in place keeps stale rows on the emulator
        rig.remove(PanelIds.CALIBRATION)
        if (!on) return "CALIB off"
        calibDist = dist
        calibScale = scale
        calibOffX = offX
        calibOffY = offY
        calibSizeDp = sizeDp
        val slot = rig.head(PanelIds.CALIBRATION, Vec3(offX, offY, -dist), scale, sizeDp.toFloat(), sizeDp.toFloat(), facing = facing)
        slot.show(null, null)
        main.post(rebind)
        return "CALIB on dist=$dist scale=$scale sizeDp=$sizeDp off=($offX, $offY)"
    }

    override fun hideMode(mode: String): String {
        val rig = rig ?: return "HIDEMODE ignored: no scene"
        rig.hideMode =
            when (mode) {
                "park" -> PanelRig.HideMode.PARK
                "holder" -> PanelRig.HideMode.DISABLE_HOLDER
                "panel" -> PanelRig.HideMode.DISABLE_PANEL
                else -> PanelRig.HideMode.AUTO
            }
        return "HIDEMODE ${rig.hideMode}"
    }

    override fun gestureMode(mode: String): String {
        RulerProbe.gestureMode.value = mode
        return "GEST mode=$mode"
    }

    companion object {
        /** Key of [liveLengths] for the tape that is being pulled by hand. */
        const val LIVE = "live"
        private const val NEVER = Long.MIN_VALUE
        private const val DONE = Long.MIN_VALUE + 1
        private const val REBIND_DELAY_MS = 150L

        /** The build waits this long at most for the body panel's first frame. */
        private const val PANEL_FIRST_MS = 3000L
        private const val GROW_FRACTION = 0.55f
        private const val RESTORE_TAPE_DELAY_MS = 350L
        private const val RESTORE_TAPE_STAGGER_MS = 110L
        private const val RESTORE_TAPE_MS = 500f
        private const val RETRACT_WITH_PIN_MS = 260f
        private const val RETRACT_SPEED_M_S = 6f
        private const val LIVE_WRITE_MS = 100L
        private const val DIMMED_OPACITY = 0.55f
        private const val STUB_LENGTH_M = 0.085f
        private const val SCAN_QUIET_MS = 6000L
        private const val PLANES_EMIT_MS = 500L

        /** Settling: 5 mm sideways, two swings, 0.4 s. */
        private const val SETTLE_SWAY_M = 0.005f
        private const val SETTLE_SWAY_MS = 400f

        /** A tape that was pulled by hand is locked within this time of the release; it then only settles. */
        private const val PULL_LOCK_MS = 800L
        private const val PULL_SETTLE_MS = 450f

        /** Two pins sit on one plane: normals within 5 degrees, both points within 10 mm of it. */
        private const val SAME_PLANE_COS = 0.9962f
        private const val SAME_PLANE_M = 0.010f

        /** A tape within 20 degrees of the vertical. */
        private const val NEAR_VERTICAL_COS = 0.9397f
        private const val STACK_M = 0.0003f
        private const val STACK_LEVELS = 5
        private const val SHADOW_LIFT_M = 0.0004f
        private const val SHADOW_MARGIN_M = 0.010f

        /** A plate or tag place that has gone bad is given up after this long. */
        private const val REPLACE_AFTER_MS = 400L

        /** A chip stands at least 2.5 degrees from the pin it leads to (tan 2.5), a quarter of the thread from it. */
        private const val CHIP_APART_TAN = 0.04366f
        private const val CHIP_SHARE = 0.24f
        private const val CHIP_LIFT_M = 0.02f
        private const val CHIP_STEPS = 4

        /** Clearance between a pin and its tag, and between a plate and the tape tag, metres at panel scale 1. */
        private const val PIN_TAG_CLEAR_M = 0.045f
        private const val TAPE_TAG_GAP_M = 0.008f
        private const val LEADER_WIDTH_M = 0.0015f

        /** The tape mouth and hook reach about 2.5 degrees beyond the right end of the body. */
        private const val BODY_MOUTH_DEG = 2.5f

        /** A press becomes a drag when the hand has moved a centimetre. */
        private const val PULL_START_M = 0.01f

        /** `PIN drag move` lines at most this often. */
        private const val PIN_DRAG_LOG_MS = 250L

        /** Half size of the keep-clear box around a dragged pin's ghost ring. */
        private const val GHOST_CLEAR_M = 0.03f

        /** The ghost ring floats this much over the surface; its casing sits this much under the ring. */
        private const val GHOST_LIFT_M = 0.003f
        private const val GHOST_CASING_BACK_M = 0.0003f

        /** The reticle of a pin drag stands this much above the dragged pin's top. */
        private const val RETICLE_OVER_PIN_M = 0.003f

        /** Touch sphere at the hook end of the tape-head stub, metres (scaled with the pin): the last 3 cm of the stub. */
        private const val STUB_TOUCH_M = 0.015f

        /** The contact drag looks down onto the surface from this far above the tip. */
        private const val CONTACT_LOOK_M = 0.05f
        private const val DRAG_LOG_MS = 100L
        private const val TAP_AFTER_DRAG_MS = 350L

        /** The tail counts as still when it has moved less than 0.15 degrees for 0.35 s. */
        private const val STEADY_MS = 350L

        /** Where the reticle floats when the ray hits nothing, and how far a ray is checked for panels then. */
        private const val NO_SURFACE_M = 1.5f
        private const val PANEL_CHECK_M = 12f

        /** A system tap on a pin this soon after a tracked pointer committed is the same press. */
        private const val TRACKED_TAP_MS = 700L
        private const val BODY_KEY_HOLD_MS = 600L
        private const val RETICLE_LOG_MS = 2000L
        private const val RETICLE_RADIUS_M = 0.016f
        private const val RETICLE_TAG_GAP_M = 0.025f

        /** A chip's rectangle is widened by this when it looks for plates it overlaps (F9). */
        private const val CHIP_PROBE_DEG = 0.5f

        /** The rider of a running tape sits this far back from the tail (M2). */
        private const val RIDER_BACK_M = 0.045f

        /** Gap between the body's right face and the coachmark panel: clear of the tape mouth and its hook. */
        private const val COACH_GAP_M = 0.052f

        /** No tracked source for this long, after one was there: "raise a hand or pick up a controller". */
        private const val SOURCE_LOST_MS = 3000L
        private const val STEADY_TAN = 0.0026f
        private const val RIG_CHECK_MS = 500L
        private const val RIG_REPEAT_MS = 5000L

        /** Dialog and snackbar: 0.9 m from the head, shown at 1.125. */
        private const val FLOATING_DISTANCE_M = 0.9f
        private const val FLOATING_SCALE = 1.125f

        /** 14 degrees above the view centre, 0.9 m away: (0, sin14, -cos14) * 0.9. */
        private val SNACK_HEAD_OFFSET = Vec3(0f, 0.2177f, -0.8733f)
    }
}

/**
 * Reticle of a pointing line. It lies on the surface, tilts with its normal (at most 60 degrees away
 * from the viewer), is never hidden by depth and grows with the distance beyond 1 m (k = distance *
 * 0.9). Inner ring of 32 mm (3 mm stroke); outside it the stability ring, which closes from 72 mm onto
 * the inner ring while the aim holds still, and both turn into one thick green ring when it is steady;
 * cross and centre dot. Every stroke has a dark casing under it (`bladeEdge` 70 %, 2.2 times as wide,
 * 0.3 mm towards the surface) so that it reads on a bright wall. Beyond 3 m the inner ring is dashed
 * and orange. In contact the tool tip itself is shown: a solid diamond with a tight ring. Where the ray
 * hits nothing: a hollow dashed ring in `onInkSecondary`, in the air, facing back along the ray.
 */
internal class Reticle(root: Entity, private val materials: SceneMaterials) {
    private val holder = Entity()

    /** A stroke of the reticle with its casing: the same mesh 2.2 times as wide, dark, 0.3 mm nearer the surface. */
    private inner class Stroke(mesh: MeshResource, casing: MeshResource, material: Material) {
        val face = ModelEntity(mesh, material)
        val back = ModelEntity(casing, materials.reticleCasing)

        init {
            back.setLocal(position = Vec3(0f, -CASING_BACK_M, 0f))
            holder.addChild(back)
            holder.addChild(face)
        }

        var enabled: Boolean
            get() = face.enabled
            set(value) {
                face.enabled = value
                back.enabled = value
            }

        fun scale(k: Float) {
            face.setUniformScale(k)
            back.setUniformScale(k)
        }

        fun material(m: Material) = face.setMaterial(m)
    }

    private val ring = Stroke(MeshFactory.ring(0.0130f, 0.0160f, "reticle_ring"), MeshFactory.ring(0.0112f, 0.0178f, "reticle_ring_case"), materials.reticleYellow)
    private val steadyRing = Stroke(MeshFactory.ring(0.0125f, 0.0165f, "reticle_steady"), MeshFactory.ring(0.0101f, 0.0189f, "reticle_steady_case"), materials.reticleGreen)
    private val farRing =
        Stroke(
            MeshFactory.ring(0.0130f, 0.0160f, "reticle_far", dashes = 10, dashFill = 0.55f),
            MeshFactory.ring(0.0112f, 0.0178f, "reticle_far_case", dashes = 10, dashFill = 0.55f),
            materials.reticleAlert,
        )
    private val noneRing =
        Stroke(
            MeshFactory.ring(0.0130f, 0.0160f, "reticle_none", dashes = 10, dashFill = 0.55f),
            MeshFactory.ring(0.0112f, 0.0178f, "reticle_none_case", dashes = 10, dashFill = 0.55f),
            materials.reticleSecondary,
        )
    private val stability = Stroke(MeshFactory.ring(0.0335f, 0.0360f, "reticle_stability"), MeshFactory.ring(0.0320f, 0.0375f, "reticle_stability_case"), materials.reticleWhite)
    private val cross =
        Stroke(
            MeshFactory.cross(0.0125f, 0.0016f, "reticle_cross", gap = 0.0055f),
            MeshFactory.cross(0.0144f, 0.0035f, "reticle_cross_case", gap = 0.0036f),
            materials.reticleWhite,
        )
    private val dot = Stroke(MeshFactory.disc(0.0025f, "reticle_dot", segments = 16), MeshFactory.disc(0.0055f, "reticle_dot_case", segments = 16), materials.reticleWhite)
    private val diamond = ModelEntity(MeshFactory.disc(0.0065f, "reticle_diamond", segments = 4), materials.reticleWhite)
    private val tight = ModelEntity(MeshFactory.ring(0.0092f, 0.0105f, "reticle_tight"), materials.reticleWhite)
    private var stabilityGreen = false
    private var tightGreen = false

    /** Where the viewer's head is (Stage metres); set by the scene every frame. */
    var viewer: Vec3 = Vec3.ZERO

    init {
        runCatching { holder.setName("reticle") }
        root.addChild(holder)
        listOf(diamond, tight).forEach { holder.addChild(it) }
        holder.enabled = false
    }

    /** An aim on a surface. [progress] runs from 0 to 1 while the aim holds still; 1 = steady. [lift] raises it off the surface (over a dragged pin). */
    fun show(point: Vec3, normal: Vec3, distance: Float, progress: Float, far: Boolean, lift: Float = 0f) {
        place(point + normal * (0.002f + lift), normal, distance)
        val steady = progress >= 1f
        ring.enabled = !steady && !far
        steadyRing.enabled = steady && !far
        farRing.enabled = far
        noneRing.enabled = false
        cross.enabled = true
        dot.enabled = true
        diamond.enabled = false
        tight.enabled = false
        // closing onto the inner ring; on a far aim it stays, green once it has closed
        stability.enabled = !steady || far
        if (stability.enabled) {
            stability.scale(1f + (CLOSED - 1f) * progress.coerceIn(0f, 1f))
            if (steady != stabilityGreen) {
                stabilityGreen = steady
                stability.material(if (steady) materials.reticleGreen else materials.reticleWhite)
            }
        }
        holder.enabled = true
    }

    /** Contact: the tool tip itself. The tight ring is green while the tip is held still. */
    fun showContact(tip: Vec3, normal: Vec3, distance: Float, steady: Boolean) {
        place(tip, normal, distance)
        ring.enabled = false
        steadyRing.enabled = false
        farRing.enabled = false
        noneRing.enabled = false
        stability.enabled = false
        cross.enabled = false
        dot.enabled = false
        diamond.enabled = true
        tight.enabled = true
        if (steady != tightGreen) {
            tightGreen = steady
            tight.setMaterial(if (steady) materials.reticleGreen else materials.reticleWhite)
        }
        holder.enabled = true
    }

    /** The ray hits nothing: a hollow dashed ring in `onInkSecondary` [at] a point in the air, facing [towards] the pointer. */
    fun showNone(at: Vec3, towards: Vec3, distance: Float) {
        place(at, towards, distance)
        ring.enabled = false
        steadyRing.enabled = false
        farRing.enabled = false
        noneRing.enabled = true
        stability.enabled = false
        cross.enabled = false
        dot.enabled = false
        diamond.enabled = false
        tight.enabled = false
        holder.enabled = true
    }

    fun hide() {
        holder.enabled = false
    }

    private fun place(at: Vec3, normal: Vec3, distance: Float) {
        // never smaller than about 1.6 degrees of visual angle
        val k = maxOf(1f, distance * SCALE_PER_M)
        holder.setLocal(position = at, rotation = Quat4.fromTo(Vec3.UP, seen(at, normal)), scale = Vec3(k, k, k))
    }

    /**
     * The normal the reticle is drawn with. On a surface that is seen almost edge-on (a shelf at eye
     * height) a flat ring would shrink to a line: the reticle never leans more than 60 degrees away from
     * the viewer.
     */
    private fun seen(at: Vec3, normal: Vec3): Vec3 {
        val to = viewer - at
        if (to.length < 1e-3f) return normal
        val v = to.normalized()
        val facing = normal.dot(v)
        if (facing >= MIN_FACING) return normal
        val across = normal - v * facing
        if (across.length < 1e-4f) return v
        return (across.normalized() * MAX_LEAN_SIN + v * MIN_FACING).normalized()
    }

    companion object {
        /** The reticle grows with the distance beyond 1 m: k = max(1, distance * 0.9). */
        const val SCALE_PER_M = 0.9f

        /** The stability ring at rest is 72 mm across; closed it lies on the inner ring (mid radius 14.5 / 34.75 mm). */
        private const val CLOSED = 0.417f

        /** cos and sin of 60 degrees: the most the reticle leans away from the viewer. */
        private const val MIN_FACING = 0.5f
        private const val MAX_LEAN_SIN = 0.866f

        /** The casing sits this much nearer the surface than its stroke. */
        private const val CASING_BACK_M = 0.0003f
    }
}
