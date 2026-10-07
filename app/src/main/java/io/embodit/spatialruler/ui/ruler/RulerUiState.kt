package io.embodit.spatialruler.ui.ruler

import io.embodit.spatialruler.domain.model.FeedbackKind
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.domain.model.MenuState
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.Phase
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SeatMark
import io.embodit.spatialruler.domain.model.SnackKind
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.ReadingText

/** Number of pre-built reading plates: one per tape, and a session holds at most twenty tapes. */
const val PLATE_SLOTS = 20

/** Number of pre-built candidate chips (the design shows at most six candidates). */
const val CHIP_SLOTS = 6

/**
 * What the readout window on the instrument body says in its first line. [BLANK]: the stored
 * preferences are not known yet, so nothing that depends on language or unit is shown.
 */
enum class LcdContext { BLANK, STARTING, SCANNING, BLOCKED, READY, ARMED, PULLING, TUNING, LAST, SELECTED, LOST }

data class LcdUi(
    val context: LcdContext = LcdContext.BLANK,
    val planeCount: Int = 0,
    /** Tolerance text for LAST / SELECTED / TUNING; empty when there is none. */
    val tolerance: String = "",
    /** Null shows "— —". */
    val reading: ReadingText? = null,
    val busy: Boolean = true,
    /** The value is the live length the scene writes while a tape is being pulled out by drag. */
    val live: Boolean = false,
)

/** A pin as the 3D scene draws it. */
data class PinUi(
    val id: String,
    val position: Vec3,
    val normal: Vec3,
    val saveState: SaveState,
    val armed: Boolean,
    /** Seat the pin landed on: drawn once, when the pin appears. */
    val seat: SeatMark? = null,
)

/** A tape as the 3D scene draws it: the blade comes out of pin [a] and its hook lies on pin [b]. */
data class TapeUi(
    val id: String,
    val a: String,
    val b: String,
    val selected: Boolean,
    val dimmed: Boolean,
    val plateSlot: Int,
)

/** Content of one reading plate. */
data class PlateUi(
    val tapeId: String,
    val reading: ReadingText,
    val tolerance: String,
    val toleranceMm: Int,
    val warn: Boolean,
    val unsaved: Boolean,
    val selected: Boolean,
    /** Tracking is lost: the reading is frozen. */
    val frozen: Boolean = false,
    /** A pin is selected and this tape does not end on it: the plate recedes to 55 % (F9). */
    val dimmed: Boolean = false,
)

/** Content of one candidate chip: the distance from the tape head to [pinId]. */
data class ChipUi(val pinId: String, val text: String)

/** How one end of a tape was pinned. */
data class EndUi(
    val grade: Grade,
    val snapped: SnapKind,
    val far: Boolean,
    val saveState: SaveState,
)

data class PinTagUi(
    val pinId: String,
    val surface: SurfaceKind,
    val end: EndUi,
    val toleranceMm: Int,
    /** The pin's own tolerance in the display unit, e.g. "±5 mm" or "±0.2″". */
    val tolerance: String,
    val warn: Boolean,
    val tuneOpen: Boolean,
    val offsetU: String,
    val offsetV: String,
    val horizontalSurface: Boolean,
)

data class TapeTagUi(
    val tapeId: String,
    val reading: ReadingText,
    val tolerance: String,
    val start: EndUi,
    val end: EndUi,
    /** An end is far or unsaved: the strip's badge carries ▲ in `alert`. */
    val warn: Boolean = false,
)

data class SnackUi(val id: Int, val kind: SnackKind, val countA: Int = 0, val countB: Int = 0)

data class ClearDialogUi(val pins: Int, val tapes: Int)

/** Everything the panels and the 3D scene render. Immutable; produced by [RulerViewModel]. */
data class RulerUiState(
    val viewState: String = "starting",
    val phase: Phase = Phase.STARTING,
    /** Language, unit and snap have been read from storage: only then may panel text be composed. */
    val settingsKnown: Boolean = false,
    val unit: RulerUnit = RulerUnit.M,
    val language: LanguagePref = LanguagePref.SYSTEM,
    val snapEnabled: Boolean = true,
    val canUndo: Boolean = false,
    val canClear: Boolean = false,
    val lcd: LcdUi = LcdUi(),
    val pins: List<PinUi> = emptyList(),
    val tapes: List<TapeUi> = emptyList(),
    /** Indexed by plate slot. */
    val plates: List<PlateUi?> = List(PLATE_SLOTS) { null },
    /** Indexed by chip slot. */
    val chips: List<ChipUi?> = List(CHIP_SLOTS) { null },
    val pinTag: PinTagUi? = null,
    val tapeTag: TapeTagUi? = null,
    val menu: MenuState = MenuState.CLOSED,
    val clearDialog: ClearDialogUi? = null,
    /** The spatial features are unavailable: the blocking dialog shows. */
    val blocked: Boolean = false,
    val snack: SnackUi? = null,
    val planeCount: Int = 0,
    val scanning: Boolean = true,
    /** Head pin of the press-and-pull that is under way. */
    val pullHead: String? = null,
    val trackingLost: Boolean = false,
    /** A tap waits for a surface to arrive under it (the body shows its spinner). */
    val pendingTap: Boolean = false,
    /** A pin is being dragged: plates take no hover (a plate the pointer crosses would keep the hover tint, QA R5b-3). */
    val pinDragging: Boolean = false,
    /** First-run coachmark: 1 while it shows (until the first tape or ✓), else 0. */
    val coachStep: Int = 0,
    /** One-sigma contribution of the tape head that a live tape would get, mm (null: no head). */
    val headSigmaMm: Float? = null,
)

/** One-shot requests from the ViewModel to the screen. */
sealed interface RulerUiEffect {
    /** Close the Stage: storage has been written. */
    data object ExitApp : RulerUiEffect

    /** Play the sound and the touch that go with a visible change; [right]: the hand that holds the pointer. */
    data class Feedback(val kind: FeedbackKind, val right: Boolean) : RulerUiEffect
}

/** The figure beside the reticle (tracked-ray channels), only where the reticle has none of its own (simplify spec I). */
enum class ReticleLabel { FINE, FAR, NO_SURFACE, MAPPING }

/** What a drag that the scene reports is doing. */
enum class DragMode { NONE, PULL, PIN }

/** Where a pointing line lands right now: drawn as the reticle and the tail of the live tape. */
data class AimPreview(
    /** Null: nothing detected along the line. */
    val point: Vec3?,
    val normal: Vec3,
    val distance: Float,
    val snapped: SnapKind,
    /** The line points at this pin. */
    val pinId: String?,
    val far: Boolean,
    /** Directions of the seat the point was snapped to (see `SnapResult`); null when it was not. */
    val seatA: Vec3? = null,
    val seatB: Vec3? = null,
)

/**
 * Questions the scene asks while a drag is under way. They are answered from the current state on the
 * main thread and change nothing: per-frame aiming never goes through the state flow.
 */
interface RulerAim {
    fun dragMode(): DragMode

    /** Where [ray] lands: the same aiming and snapping code that sets the pin on release; [exclude] is a pin the ray may not pick (the one being dragged). */
    fun preview(ray: PointerRay, exclude: String? = null): AimPreview
}

/** Everything that reaches the ViewModel: scene callbacks, panel controls and test hooks. */
sealed interface RulerEvent {
    // ---- 3D scene
    /** Resources are built, sensing has started: storage and anchors can be read. */
    data object SceneReady : RulerEvent

    data class PlanesChanged(val count: Int, val scanning: Boolean) : RulerEvent

    /** A system tap landed on a detected surface. [stagePoint] is on the surface's collider. */
    data class SurfaceTapped(val planeId: String, val stagePoint: Vec3) : RulerEvent

    data class PinTapped(val pinId: String) : RulerEvent

    /** A system tap that hit only the backstop. */
    data object NothingTapped : RulerEvent

    /** A system tap that hit only the backstop, with the line of sight through the tapped point: kept as a pending aim. */
    data class UnmappedTapped(val ray: PointerRay) : RulerEvent

    /**
     * A tracked pointer (controller, hand, test ray) committed: [ray] runs from its origin through the
     * commit point (the median of the aim just before the press), [grade] is what the aim earned, [via]
     * names the source for the log.
     */
    data class PointerTriggered(val ray: PointerRay, val grade: Grade, val via: String) : RulerEvent

    /** A tool tip in reach of a surface committed: [tip] itself is the point. [pinch]: the tip is the pinch point of a hand. */
    data class ContactTriggered(val tip: Vec3, val pinch: Boolean) : RulerEvent

    /** The aim of a tracked pointer has snapped onto a seat (sound and touch only). [right]: the hand that holds the pointer. */
    data class AimSnapped(val right: Boolean) : RulerEvent

    /** Which hand holds the active tracked pointer (where touch feedback goes). */
    data class PointerHand(val right: Boolean) : RulerEvent

    /** A press on a surface ([planeId]) or on a pin ([pinId]) turned into a drag. */
    data class DragStarted(val planeId: String?, val pinId: String?, val stagePoint: Vec3) : RulerEvent

    /** A press on [pinId] has moved past the drag slop: the pin follows the pointer (round 5). */
    data class PinDragStarted(val pinId: String) : RulerEvent

    /**
     * The drag of [pinId] was released: the pin lands where [ray] (or the tool tip [tip]) meets a surface,
     * with [grade]; [via] and [gain] for the log; [cancelled] when the system took the drag away.
     */
    data class PinDragEnded(val pinId: String, val ray: PointerRay?, val tip: Vec3?, val grade: Grade, val via: String, val gain: String, val cancelled: Boolean) : RulerEvent

    /** The drag was released with the pointing line at [ray] (null: no line), or taken away by the system. */
    data class DragEnded(val ray: PointerRay?, val steady: Boolean, val cancelled: Boolean) : RulerEvent

    /** Spatial tracking was lost or came back. */
    data class TrackingChanged(val lost: Boolean) : RulerEvent

    // ---- instrument body
    data class UnitSelected(val unit: RulerUnit) : RulerEvent

    data object UndoClicked : RulerEvent

    data class SnapToggled(val on: Boolean) : RulerEvent

    data object MoreClicked : RulerEvent

    data object MenuDismissed : RulerEvent

    data class LanguageSelected(val language: LanguagePref) : RulerEvent

    data object ClearAllClicked : RulerEvent

    data object ClearConfirmed : RulerEvent

    data object DialogDismissed : RulerEvent

    data object ExitClicked : RulerEvent

    /** A pinch whose line of sight ends on a key of the body panel (see `BodyGuard`). */
    data class BodyKeyPressed(val name: String) : RulerEvent

    // ---- blocking dialog
    data object BlockedRetryClicked : RulerEvent

    data object BlockedExitClicked : RulerEvent

    // ---- panels riding on objects
    data class PlateClicked(val tapeId: String) : RulerEvent

    data class ChipClicked(val pinId: String) : RulerEvent

    data object PinTagClosed : RulerEvent

    data class Stepped(val axis: NudgeAxis, val step: Int) : RulerEvent

    data object PinRemoveClicked : RulerEvent

    data object TapeRetractClicked : RulerEvent

    data class SnackActionClicked(val snackId: Int) : RulerEvent

    data class SnackClosed(val snackId: Int) : RulerEvent

    // ---- test hooks (sent by the debug receiver only)
    data class DebugPlace(val point: Vec3) : RulerEvent

    data class DebugConnect(val a: String, val b: String) : RulerEvent

    data class DebugDelete(val pinId: String) : RulerEvent

    data object DebugClear : RulerEvent

    data object DebugFailNextAnchor : RulerEvent

    /** Treats the last [count] pins as "not in this space". */
    data class DebugElsewhere(val count: Int) : RulerEvent

    /** Forces the "spatial features unavailable" state at the next start (on) or lifts it (off). */
    data class DebugBlocked(val on: Boolean) : RulerEvent

    /** Timed snackbars stay for a minute (on) or their designed time (off). */
    data class DebugLongSnacks(val on: Boolean) : RulerEvent

    /** The scene has had no tracked pointer source for three seconds after one was active (lost), or has one again. */
    data class SourceChanged(val lost: Boolean) : RulerEvent

    /** "Got it" on the coachmark. */
    data object CoachDismissed : RulerEvent

    /** Debug `coach reset`: the coachmark shows again. */
    data object DebugCoachReset : RulerEvent
}
