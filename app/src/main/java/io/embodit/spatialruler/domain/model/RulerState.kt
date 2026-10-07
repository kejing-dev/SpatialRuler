package io.embodit.spatialruler.domain.model

enum class Phase { STARTING, RESTORING, READY, BLOCKED, EXITED }

enum class MenuState { CLOSED, MORE }

enum class DialogState { NONE, CONFIRM_CLEAR }

private const val STICKY_MS = -1L

/**
 * Global one-line messages ("snackbar" in the design). [durationMs] is how long one shows;
 * [STICKY] messages stay until what they report is over.
 */
enum class SnackKind(val tag: String, val hasAction: Boolean, val durationMs: Long) {
    RESTORED("restored", false, 3000),
    ELSEWHERE("elsewhere", true, 7000),
    UNSAVED("unsaved", true, 7000),
    SAVED("saved", false, 1800),
    NO_SURFACE("no_surface", false, 3000),
    NOT_MAPPED("not_mapped", false, 3000),
    DUPLICATE("duplicate", false, 3000),
    CLEARED("cleared", false, 1800),
    TAPE_LIMIT("tape_limit", false, 4000),
    LOST("lost", false, STICKY_MS),
    RECOVERED("recovered", false, 1800),

    /** No hand or controller has been tracked for three seconds (after one was): stands until one is back. */
    NO_SOURCE("no_source", false, STICKY_MS);

    companion object {
        const val STICKY = STICKY_MS
    }
}

data class Snack(
    val id: Int,
    val kind: SnackKind,
    val countA: Int = 0,
    val countB: Int = 0,
    val pinId: String? = null,
)

/** One undo step: the measuring model before the step. */
data class HistoryEntry(
    val pins: List<Pin>,
    val tapes: List<Tape>,
    val lastTapeId: String?,
    val tag: String?,
    val timeMs: Long,
)

/**
 * A press-and-pull that is under way: the press set (or picked) the tape head [headPinId]; the tail is
 * set when the press is released. [headIsNew] is true when the press itself placed the head pin.
 */
data class PullState(val headPinId: String, val headIsNew: Boolean)

/** Why the app cannot run its spatial features (shown in the blocking dialog; the copy is the same for all). */
enum class BlockReason { NOT_IN_FULL_SPACE, WITHOUT_PERMISSION, DEVICE_NOT_SUPPORTED, UNKNOWN }

/**
 * The whole product state. One rule drives pairing: the pin touched last is the tape head
 * ([armedPinId]); the next point that is set or touched is the tape tail.
 */
data class RulerState(
    val phase: Phase = Phase.STARTING,
    val pins: List<Pin> = emptyList(),
    val tapes: List<Tape> = emptyList(),
    val pinSeq: Int = 0,
    val tapeSeq: Int = 0,
    val lastTapeId: String? = null,
    val armedPinId: String? = null,
    /** True when the head was chosen by touching an existing pin: its tag and candidates show. */
    val armedExplicit: Boolean = false,
    val tuneOpen: Boolean = false,
    val selectedTapeId: String? = null,
    val settings: RulerSettings = RulerSettings(),
    /** False until the stored preferences have been read: nothing that depends on them may be shown. */
    val settingsKnown: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val menu: MenuState = MenuState.CLOSED,
    val dialog: DialogState = DialogState.NONE,
    val planeCount: Int = 0,
    val scanning: Boolean = true,
    /** Queue of messages; the first one is showing. */
    val snacks: List<Snack> = emptyList(),
    val snackSeq: Int = 0,
    val lastNoSurfaceMs: Long = NEVER,
    /** pin id -> anchor id of the anchors that exist in the platform's store right now. */
    val liveAnchors: Map<String, String> = emptyMap(),
    /** Pins whose anchor creation is under way. */
    val pendingCreates: Set<String> = emptySet(),
    /** Press-and-pull under way. */
    val pull: PullState? = null,
    /** The pin that is being dragged by direct manipulation (press, drag, release), or null. */
    val pinDrag: String? = null,
    /** A tap that met no recognised surface yet: the line of sight through the tapped point, placed when a plane arrives under it. */
    val pendingTap: PendingTap? = null,
    /** Spatial tracking is lost: readings are frozen, nothing can be measured until it is back. */
    val trackingLost: Boolean = false,
    /** No tracked pointer source for three seconds after one was active (the "raise a hand" message stands). */
    val noSource: Boolean = false,
    val blockReason: BlockReason? = null,
) {
    fun pin(id: String?): Pin? = if (id == null) null else pins.firstOrNull { it.id == id }

    fun tape(id: String?): Tape? = if (id == null) null else tapes.firstOrNull { it.id == id }

    val armedPin: Pin?
        get() = pin(armedPinId)

    val visiblePins: List<Pin>
        get() = pins.filter { !it.elsewhere }

    /** Tapes whose two pins are present in this space. */
    val visibleTapes: List<Tape>
        get() = tapes.filter { t -> pin(t.a)?.elsewhere == false && pin(t.b)?.elsewhere == false }

    val snack: Snack?
        get() = snacks.firstOrNull()

    /** Pins that could take a tape from [head]: not yet joined to it, nearest first, at most six. */
    fun candidatesFor(head: Pin): List<Pin> =
        visiblePins
            .filter { q -> q.id != head.id && tapes.none { it.joins(head.id, q.id) } }
            .sortedBy { it.position.distanceTo(head.position) }
            .take(MAX_CANDIDATES)

    /** The tape whose live length the readout shows while [pinId] is tuned: the newest tape on that pin. */
    fun referenceTape(pinId: String): Tape? = visibleTapes.lastOrNull { it.touches(pinId) }

    /** Stable state name used in logs (`STATE <name>`) and by the tests. */
    val viewStateName: String
        get() =
            when {
                phase == Phase.STARTING -> "starting"
                phase == Phase.RESTORING -> "restoring"
                phase == Phase.EXITED -> "exited"
                phase == Phase.BLOCKED -> "blocked"
                dialog == DialogState.CONFIRM_CLEAR -> "confirm-clear"
                menu == MenuState.MORE -> "more-menu"
                trackingLost -> "tracking-lost"
                pull != null -> "pulling-drag"
                pinDrag != null -> "pin-drag"
                armedPinId != null ->
                    if (!armedExplicit) "pulling"
                    else if (tuneOpen) "pin-selected-tuning"
                    else "pin-selected"
                selectedTapeId != null -> "tape-selected"
                scanning -> "idle-scanning"
                else -> "idle"
            }

    companion object {
        const val MAX_CANDIDATES = 6
        const val MAX_HISTORY = 20

        /** A session holds at most this many tapes: one reading plate each, from a pre-built pool. */
        const val MAX_TAPES = 20
        const val NEVER = Long.MIN_VALUE / 4
    }
}

/** How a press-and-pull ends. */
sealed interface PullEnd {
    /** Released over a detected surface: the tail pin is set there. */
    data class OnSurface(val placement: Placement) : PullEnd

    /** Released on an existing pin: that pin is the tail. */
    data class OnPin(val pinId: String) : PullEnd

    /** Released where no surface is detected. */
    data class Nowhere(val stillScanning: Boolean) : PullEnd
}

/** Everything that can happen to the product state. */
sealed interface RulerAction {
    /** The stored preferences have been read (null: first launch, [defaults] apply). */
    data class SettingsLoaded(val settings: RulerSettings?, val defaults: RulerSettings) : RulerAction

    /** Storage and the anchor store have been read. [anchors]: anchor id -> current pose. */
    data class Restored(val stored: StoredSession?, val anchors: Map<String, AnchorPose>, val defaults: RulerSettings) : RulerAction

    data object RestoreShown : RulerAction

    data class PlanesChanged(val count: Int, val scanning: Boolean) : RulerAction

    data class PlacePin(val placement: Placement) : RulerAction

    data class TapPin(val pinId: String) : RulerAction

    data class ChooseCandidate(val pinId: String) : RulerAction

    data class TapPlate(val tapeId: String) : RulerAction

    /** A tap that hit no detected surface. */
    data class TapNothing(val stillScanning: Boolean) : RulerAction

    /** A system tap that hit no recognised surface while the room may still be mapping: kept as a pending aim (the latest one only). */
    data class TapUnmapped(val tap: PendingTap, val stillScanning: Boolean) : RulerAction

    /** A recognised plane arrived under the pending aim: the pin is placed there through the normal path. */
    data class PendingTapResolved(val placement: Placement) : RulerAction

    /** Time passes while a tap is pending: after [PENDING_TAP_MS] without a surface it is dropped. */
    data object PendingTapTick : RulerAction

    /**
     * A press that turned into a drag. On a surface the press sets the tape head there ([placement]);
     * on a pin that pin is the head ([pinId]).
     */
    data class StartPull(val placement: Placement?, val pinId: String?) : RulerAction

    /** The press-and-pull was released. */
    data class EndPull(val end: PullEnd) : RulerAction

    /** The press-and-pull was taken away by the system: the head stays, no tail is set. */
    data object CancelPull : RulerAction

    data object ClosePinTag : RulerAction


    /** One stepper click: [step] is +1 or -1 millimetre. [rangeM] is the current viewer-to-pin distance. */
    data class Nudge(val axis: NudgeAxis, val step: Int, val rangeM: Float?) : RulerAction

    /** The selected pin starts / stops being dragged along its surface. */
    /** A press on [pinId] has started to move it (round 5): the pin is selected and follows the pointer until EndPinDrag. */
    data class StartPinDrag(val pinId: String) : RulerAction

    /**
     * The drag of [pinId] was released at [placement] (null: cancelled, or no surface under the pointer).
     * [via] names the pointer channel and [gain] the gain that was on at release, for the log.
     */
    data class EndPinDrag(val pinId: String, val placement: Placement?, val via: String = "system", val gain: String = "1:1") : RulerAction

    /** The selected pin is dragged to these fine-tune offsets, millimetres. */

    /** Removes [pinId], or the tape head when null. */
    data class RemovePin(val pinId: String? = null) : RulerAction


    /** Retracts [tapeId], or the selected tape when null. */
    data class RetractTape(val tapeId: String? = null) : RulerAction

    data class Connect(val a: String, val b: String) : RulerAction

    data object Undo : RulerAction

    data class SetUnit(val unit: RulerUnit) : RulerAction

    data class SetSnap(val on: Boolean) : RulerAction

    data class SetLanguage(val language: LanguagePref) : RulerAction

    data object ToggleMoreMenu : RulerAction

    data object DismissMenu : RulerAction

    data object RequestClearAll : RulerAction

    data object ConfirmClearAll : RulerAction

    data object DismissDialog : RulerAction

    data object Exit : RulerAction

    data class AnchorSaved(val pinId: String, val anchorId: String, val pose: AnchorPose) : RulerAction

    data class AnchorFailed(val pinId: String) : RulerAction

    data class AnchorMoved(val anchorId: String, val pose: AnchorPose) : RulerAction

    data class SnackAction(val snackId: Int) : RulerAction

    data class SnackDismissed(val snackId: Int) : RulerAction

    /** Spatial tracking was lost or came back. */
    data class TrackingChanged(val lost: Boolean) : RulerAction

    /** The scene reports: no tracked pointer source for three seconds after one was active (lost), or one is back. */
    data class SourceChanged(val lost: Boolean) : RulerAction

    /**
     * Fine-tune of [pinId] has rested (0.6 s without a drag, or its tag closed): when the offset is beyond
     * [ReduceRulerUseCase.REBASE_MM] the pin's anchor is re-created at the pin's current position.
     */
    data class RebaseDue(val pinId: String) : RulerAction

    /** The re-created anchor of [pinId] exists: the pin takes it, with a zero offset. */
    data class AnchorRebased(val pinId: String, val anchorId: String, val pose: AnchorPose) : RulerAction

    /** "Got it" on the coachmark: it never shows again. */
    data object CoachDismissed : RulerAction

    /** Debug: the coachmark shows again as on the first run. */
    data object CoachReset : RulerAction

    /** The spatial features the app needs are not available. */
    data class Blocked(val reason: BlockReason) : RulerAction

    /** "Retry" in the blocking dialog: start again. */
    data object RetryBlocked : RulerAction

    /** Test hook: treats the last [count] pins as "not in this space". */
    data class MarkElsewhere(val count: Int) : RulerAction
}

/** Side effects the reducer asks for; the ViewModel carries them out. */
sealed interface RulerEffect {
    data class CreateAnchor(val pinId: String, val position: Vec3) : RulerEffect

    data class RemoveAnchor(val anchorId: String) : RulerEffect

    /** Anchors of pins that no longer exist, found at launch: removed in one batch off the launch path. */
    data class RemoveOrphans(val anchorIds: List<String>) : RulerEffect

    /** Create a new anchor at [position] for [pinId], to replace [oldAnchorId] (answered by AnchorRebased / AnchorFailed). */
    data class RebaseAnchor(val pinId: String, val position: Vec3, val oldAnchorId: String?) : RulerEffect

    data object Persist : RulerEffect

    data object ExitApp : RulerEffect

    /** Check the capabilities and read storage again (after "Retry" in the blocking dialog). */
    data object Restart : RulerEffect

    /** One log line with a stable prefix (PIN, TAPE, UNIT, LANG, SNAP, RESTORE, ...). */
    data class Log(val line: String) : RulerEffect

    /** Sound and touch that go with a visible change (design: 声音与触感). */
    data class Feedback(val kind: FeedbackKind) : RulerEffect
}

/** What the user hears and feels: a pin drops, the aim snaps to a seat, a tape locks, a tape is retracted, something failed. */
enum class FeedbackKind { PIN_DROP, SNAP, TAPE_LOCK, RETRACT, FAIL }

data class Reduction(val state: RulerState, val effects: List<RulerEffect>)

/**
 * A tap that met no recognised surface (the backstop) while the room may still be mapping: the line of sight
 * from the head at the tap through the tapped point, and when it was made. Resolved when a plane arrives
 * under it, dropped after [PENDING_TAP_MS].
 */
data class PendingTap(val origin: Vec3, val direction: Vec3, val atMs: Long)

/** A pending tap waits this long for a surface. */
const val PENDING_TAP_MS = 15_000L

