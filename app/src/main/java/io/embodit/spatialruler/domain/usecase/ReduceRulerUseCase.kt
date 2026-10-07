package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.DialogState
import io.embodit.spatialruler.domain.model.FeedbackKind
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.HistoryEntry
import io.embodit.spatialruler.domain.model.MenuState
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.PENDING_TAP_MS
import io.embodit.spatialruler.domain.model.Phase
import io.embodit.spatialruler.domain.model.Pin
import io.embodit.spatialruler.domain.model.Placement
import io.embodit.spatialruler.domain.model.PullEnd
import io.embodit.spatialruler.domain.model.PullState
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Reduction
import io.embodit.spatialruler.domain.model.RulerAction
import io.embodit.spatialruler.domain.model.RulerEffect
import io.embodit.spatialruler.domain.model.RulerState
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SeatMark
import io.embodit.spatialruler.domain.model.Snack
import io.embodit.spatialruler.domain.model.SnackKind
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.Tape
import io.embodit.spatialruler.domain.model.Vec3
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The product state machine as a pure function: (state, action, time) -> (state, effects).
 *
 * Pairing rule: the pin touched last is the tape head; the next point set or pin touched is the tail.
 * The anchor store always mirrors the current pins: [reconcileAnchors] asks for an anchor for every
 * pin that has none and removes anchors whose pin is gone (delete, undo, clear).
 */
class ReduceRulerUseCase(
    private val tolerance: ComputeToleranceUseCase = ComputeToleranceUseCase(),
) {
    private val drags = DragPinUseCase()

    fun reduce(state: RulerState, action: RulerAction, nowMs: Long): Reduction {
        // a dialog is modal: while it is open nothing else reacts, it closes only through its buttons
        if ((state.dialog != DialogState.NONE || state.phase == Phase.BLOCKED) && action.isUserInput) return Reduction(state, emptyList())
        // a pinch that dismisses an open menu is consumed: it does not place a pin or act on what is behind (R2-2)
        if (state.menu != MenuState.CLOSED && action.isSceneTap) {
            return Reduction(state.copy(menu = MenuState.CLOSED), listOf(RulerEffect.Log("TAP consumed reason=menu action=${action::class.simpleName}")))
        }
        val fx = ArrayList<RulerEffect>()
        val next =
            when (action) {
                is RulerAction.SettingsLoaded ->
                    if (state.settingsKnown) state
                    else state.copy(settings = action.settings ?: action.defaults, settingsKnown = true)
                is RulerAction.Restored -> restored(state, action, fx)
                RulerAction.RestoreShown -> restoreShown(state)
                is RulerAction.PlanesChanged ->
                    state.copy(planeCount = action.count, scanning = action.scanning)
                is RulerAction.PlacePin -> placePin(state, action, nowMs, fx)
                is RulerAction.TapPin -> tapPin(state, action.pinId, nowMs, fx)
                is RulerAction.ChooseCandidate -> chooseCandidate(state, action.pinId, nowMs, fx)
                is RulerAction.TapPlate -> tapPlate(state, action.tapeId)
                is RulerAction.TapNothing -> {
                    if (state.measuring) fx += RulerEffect.Feedback(FeedbackKind.FAIL)
                    tapNothing(state, action.stillScanning, nowMs)
                }
                is RulerAction.TapUnmapped -> tapUnmapped(state, action, nowMs, fx)
                is RulerAction.PendingTapResolved -> {
                    val pending = state.pendingTap ?: return Reduction(state, fx)
                    fx += RulerEffect.Log("TAP pending resolved after_ms=${nowMs - pending.atMs} plane=${action.placement.planeLabel}")
                    placePin(state.copy(pendingTap = null), RulerAction.PlacePin(action.placement), nowMs, fx, deferred = true)
                }
                RulerAction.PendingTapTick -> pendingTapTick(state, nowMs, fx)
                is RulerAction.StartPull -> startPull(state, action, nowMs, fx)
                is RulerAction.EndPull -> endPull(state, action.end, nowMs, fx)
                RulerAction.CancelPull -> {
                    val pull = state.pull
                    if (pull == null) state
                    else {
                        fx += RulerEffect.Log("PULL cancel head=${pull.headPinId}")
                        dropEmptyPull(state.copy(pull = null), pull)
                    }
                }
                RulerAction.ClosePinTag -> if (state.ready) state.disarmed() else state
                is RulerAction.Nudge -> nudge(state, action, nowMs, fx)
                is RulerAction.StartPinDrag -> startPinDrag(state, action.pinId, fx)
                is RulerAction.EndPinDrag -> endPinDrag(state, action, nowMs, fx)
                is RulerAction.RemovePin -> removePin(state, action.pinId ?: state.armedPinId, nowMs, fx)
                is RulerAction.RetractTape -> retract(state, action.tapeId ?: state.selectedTapeId, nowMs, fx)
                is RulerAction.Connect -> connect(state, action, nowMs, fx)
                RulerAction.Undo -> undo(state, fx)
                is RulerAction.SetUnit -> {
                    fx += RulerEffect.Log("UNIT ${action.unit.tag}")
                    fx += RulerEffect.Persist
                    state.copy(settings = state.settings.copy(unit = action.unit))
                }
                is RulerAction.SetSnap -> {
                    fx += RulerEffect.Log("SNAP ${if (action.on) "on" else "off"}")
                    fx += RulerEffect.Persist
                    state.copy(settings = state.settings.copy(snap = action.on))
                }
                is RulerAction.SetLanguage -> {
                    // changed in place from the menu's language segment: the menu stays open, so the result is seen
                    fx += RulerEffect.Log("LANG ${action.language.tag}")
                    fx += RulerEffect.Persist
                    state.copy(settings = state.settings.copy(language = action.language))
                }
                RulerAction.ToggleMoreMenu ->
                    if (!state.menuAvailable) state
                    else state.copy(menu = if (state.menu == MenuState.CLOSED) MenuState.MORE else MenuState.CLOSED)
                RulerAction.DismissMenu -> state.copy(menu = MenuState.CLOSED)
                RulerAction.RequestClearAll ->
                    if (!state.ready || state.pins.isEmpty()) state.copy(menu = MenuState.CLOSED)
                    else state.disarmed().copy(menu = MenuState.CLOSED, selectedTapeId = null, dialog = DialogState.CONFIRM_CLEAR)
                RulerAction.ConfirmClearAll -> clearAll(state, fx)
                RulerAction.DismissDialog -> state.copy(dialog = DialogState.NONE)
                RulerAction.Exit -> exit(state, fx)
                is RulerAction.AnchorSaved -> anchorSaved(state, action, fx)
                is RulerAction.AnchorFailed -> {
                    fx += RulerEffect.Feedback(FeedbackKind.FAIL)
                    anchorFailed(state, action.pinId)
                }
                is RulerAction.AnchorMoved -> anchorMoved(state, action)
                is RulerAction.SnackAction -> snackAction(state, action.snackId, fx)
                is RulerAction.SnackDismissed ->
                    if (state.snack?.id == action.snackId) state.copy(snacks = state.snacks.drop(1)) else state
                is RulerAction.TrackingChanged -> tracking(state, action.lost, fx)
                is RulerAction.SourceChanged -> source(state, action.lost, fx)
                is RulerAction.RebaseDue -> rebaseDue(state, action.pinId, fx)
                is RulerAction.AnchorRebased -> anchorRebased(state, action, fx)
                RulerAction.CoachDismissed ->
                    if (state.settings.coachSeen) state
                    else {
                        fx += RulerEffect.Persist
                        state.coachDone(fx, "got_it")
                    }
                RulerAction.CoachReset -> {
                    fx += RulerEffect.Log("COACH reset")
                    fx += RulerEffect.Persist
                    state.copy(settings = state.settings.copy(coachSeen = false))
                }
                is RulerAction.Blocked ->
                    if (state.phase != Phase.STARTING) state
                    else {
                        fx += RulerEffect.Log("BLOCKED reason=${action.reason.name.lowercase(Locale.US)}")
                        state.copy(phase = Phase.BLOCKED, blockReason = action.reason, menu = MenuState.CLOSED)
                    }
                RulerAction.RetryBlocked ->
                    if (state.phase != Phase.BLOCKED) state
                    else {
                        fx += RulerEffect.Restart
                        // what is known about the room stays: the scene reports planes only when they change
                        RulerState(settings = state.settings, settingsKnown = state.settingsKnown, planeCount = state.planeCount, scanning = state.scanning)
                    }
                is RulerAction.MarkElsewhere -> markElsewhere(state, action.count, fx)
            }
        return Reduction(reconcileAnchors(next, fx), fx)
    }

    // ------------------------------------------------------------------ launch / restore

    private fun restored(state: RulerState, action: RulerAction.Restored, fx: MutableList<RulerEffect>): RulerState {
        if (state.phase != Phase.STARTING) return state
        val stored = action.stored
        // preferences that were read (and maybe changed) while the scene was being built are kept
        val settings = if (state.settingsKnown) state.settings else stored?.settings ?: action.defaults
        if (stored == null) {
            if (action.anchors.isNotEmpty()) fx += RulerEffect.RemoveOrphans(action.anchors.keys.toList())
            fx += RulerEffect.Log("RESTORE pins=0 tapes=0 elsewhere=0 orphans=${action.anchors.size} stored=false")
            return state.copy(phase = Phase.READY, settings = settings, settingsKnown = true)
        }
        val live = LinkedHashMap<String, String>()
        val pins =
            stored.pins.map { sp ->
                val pose = action.anchors[sp.anchorId]
                if (pose != null) live[sp.id] = sp.anchorId
                Pin(
                    id = sp.id,
                    anchorId = sp.anchorId,
                    base = pose?.position ?: Vec3.ZERO,
                    baseRotation = pose?.rotation ?: Quat4.IDENTITY,
                    normalLocal = sp.normalLocal,
                    uLocal = sp.uLocal,
                    vLocal = sp.vLocal,
                    offsetUMm = sp.offsetUMm,
                    offsetVMm = sp.offsetVMm,
                    surface = sp.surface,
                    planeLabel = sp.planeLabel,
                    grade = sp.grade,
                    snapped = sp.snapped,
                    far = sp.far,
                    rangeM = sp.rangeM,
                    pinch = sp.pinch,
                    saveState = SaveState.SAVED,
                    elsewhere = pose == null,
                )
            }
        val known = stored.pins.map { it.anchorId }.toSet()
        val orphans = action.anchors.keys.filter { it !in known }
        if (orphans.isNotEmpty()) fx += RulerEffect.RemoveOrphans(orphans)
        val ids = pins.map { it.id }.toSet()
        val tapes = stored.tapes.filter { it.a in ids && it.b in ids }
        val next =
            state.copy(
                pins = pins,
                tapes = tapes,
                pinSeq = stored.pinSeq,
                tapeSeq = stored.tapeSeq,
                settings = settings,
                settingsKnown = true,
                liveAnchors = live,
                menu = MenuState.CLOSED,
            )
        val here = next.visiblePins.size
        fx +=
            RulerEffect.Log(
                "RESTORE pins=$here tapes=${next.visibleTapes.size} elsewhere=${pins.size - here} orphans=${orphans.size} stored=true"
            )
        return if (pins.isEmpty()) next.copy(phase = Phase.READY)
        else next.copy(phase = Phase.RESTORING, lastTapeId = next.visibleTapes.lastOrNull()?.id)
    }

    private fun restoreShown(state: RulerState): RulerState {
        if (state.phase != Phase.RESTORING) return state
        var s = state.copy(phase = Phase.READY)
        val here = s.visiblePins.size
        val gone = s.pins.size - here
        if (here > 0) s = s.enqueueSnack(SnackKind.RESTORED, here, s.visibleTapes.size)
        if (gone > 0) s = s.enqueueSnack(SnackKind.ELSEWHERE, gone)
        return s
    }

    // ------------------------------------------------------------------ pins and tapes

    /** Adds the pin of [p] to the model; returns the new state and the pin's id. */
    private fun addPin(state: RulerState, p: Placement, fx: MutableList<RulerEffect>, deferred: Boolean = false): Pair<RulerState, String> {
        val id = "p${state.pinSeq + 1}"
        // only corner / edge seats stay on the pin; plumb / level and pin snaps are aiming aids
        val seat = if (p.snapped == SnapKind.CORNER || p.snapped == SnapKind.EDGE) p.snapped else SnapKind.NONE
        val pin =
            Pin(
                id = id,
                anchorId = null,
                base = p.point,
                normalLocal = p.normal,
                uLocal = p.uAxis,
                vLocal = p.vAxis,
                surface = p.surface,
                planeLabel = p.planeLabel,
                grade = p.grade,
                snapped = seat,
                far = p.rangeM > ComputeToleranceUseCase.FAR_RANGE_M,
                rangeM = p.rangeM,
                pinch = p.pinch,
                saveState = SaveState.PENDING,
                seat = if (p.snapped == SnapKind.NONE || p.snapped == SnapKind.PIN) null else SeatMark(p.snapped, p.seatA, p.seatB),
            )
        fx +=
            RulerEffect.Log(
                "PIN add id=$id pos=${fmt(pin.position)} plane=${p.planeLabel} grade=${p.grade.tag} snapped=${p.snapped.tag} range_m=${f4(p.rangeM)} via=${p.via}" +
                    (if (deferred) " deferred=true" else "")
            )
        fx += RulerEffect.Feedback(FeedbackKind.PIN_DROP)
        return state.copy(pins = state.pins + pin, pinSeq = state.pinSeq + 1) to id
    }

    private fun placePin(state: RulerState, action: RulerAction.PlacePin, nowMs: Long, fx: MutableList<RulerEffect>, deferred: Boolean = false): RulerState {
        if (!state.measuring || state.pull != null) return state
        // a point on top of a pin is that pin (a quick second pinch on the same spot): never two pins in one place
        val same = state.pins.firstOrNull { !it.elsewhere && it.position.distanceTo(action.placement.point) < SAME_PIN_M }
        if (same != null) return tapPin(state, same.id, nowMs, fx)
        // a pin placed any way ends a pending tap
        var s = state.copy(menu = MenuState.CLOSED, pendingTap = null)
        // a tap elsewhere first lets go of the selected tape
        if (s.selectedTapeId != null) return s.copy(selectedTapeId = null)
        s = s.withHistory(null, nowMs)
        val (withPin, id) = addPin(s, action.placement, fx, deferred)
        s = withPin
        val head = s.armedPinId
        s =
            when {
                head == null || s.pin(head) == null -> s.armed(id)
                // the limit is reached: the pin is set all the same and becomes the next tape head
                s.atTapeLimit -> refuseTape(s, head, id, fx).armed(id)
                else -> lockTape(s, head, id, fx).disarmed().coachDone(fx, "first_tape")
            }
        fx += RulerEffect.Persist
        return s
    }

    private fun tapPin(state: RulerState, pinId: String, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        if (!state.measuring || state.pull != null) return state
        val pin = state.pin(pinId) ?: return state
        if (pin.elsewhere) return state
        var s = state.copy(menu = MenuState.CLOSED)
        val head = s.armedPinId
        return when {
            head == pinId -> s.disarmed() // touching the head again cancels it
            head != null && s.pin(head) != null -> {
                if (s.atTapeLimit && !s.hasTape(head, pinId)) {
                    return refuseTape(s, head, pinId, fx).copy(armedPinId = pinId, armedExplicit = true, tuneOpen = true, selectedTapeId = null)
                }
                if (!s.hasTape(head, pinId)) s = s.withHistory(null, nowMs)
                s = lockTape(s, head, pinId, fx).disarmed().coachDone(fx, "first_tape")
                fx += RulerEffect.Persist
                s
            }
            else -> s.copy(armedPinId = pinId, armedExplicit = true, tuneOpen = true, selectedTapeId = null)
        }
    }

    private fun chooseCandidate(state: RulerState, pinId: String, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        val head = state.armedPinId ?: return state
        if (!state.measuring || state.pin(pinId) == null || head == pinId) return state
        if (state.atTapeLimit && !state.hasTape(head, pinId)) return refuseTape(state, head, pinId, fx)
        var s = if (state.hasTape(head, pinId)) state else state.withHistory(null, nowMs)
        s = lockTape(s, head, pinId, fx).disarmed().coachDone(fx, "first_tape")
        fx += RulerEffect.Persist
        return s
    }

    private fun connect(state: RulerState, action: RulerAction.Connect, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        if (!state.ready || action.a == action.b) return state
        if (state.pin(action.a) == null || state.pin(action.b) == null) return state
        if (state.atTapeLimit && !state.hasTape(action.a, action.b)) return refuseTape(state, action.a, action.b, fx)
        var s = if (state.hasTape(action.a, action.b)) state else state.withHistory(null, nowMs)
        s = lockTape(s, action.a, action.b, fx).disarmed()
        fx += RulerEffect.Persist
        return s
    }

    /** Pulls a tape from [a] to [b]; a second tape between the same two pins is refused. */
    private fun lockTape(state: RulerState, a: String, b: String, fx: MutableList<RulerEffect>): RulerState {
        if (a == b) return state
        val duplicate = state.tapes.firstOrNull { it.joins(a, b) }
        if (duplicate != null) {
            fx += RulerEffect.Log("TAPE duplicate id=${duplicate.id} a=$a b=$b")
            return state.copy(lastTapeId = duplicate.id).showSnack(SnackKind.DUPLICATE)
        }
        val pa = state.pin(a) ?: return state
        val pb = state.pin(b) ?: return state
        val tape = Tape("t${state.tapeSeq + 1}", a, b)
        val length = pa.position.distanceTo(pb.position)
        fx +=
            RulerEffect.Log(
                "TAPE add id=${tape.id} a=$a b=$b len_m=${f4(length)} tol_mm=${tolerance.tapeToleranceMm(pa, pb)}"
            )
        fx += RulerEffect.Feedback(FeedbackKind.TAPE_LOCK)
        return state.copy(tapes = state.tapes + tape, tapeSeq = state.tapeSeq + 1, lastTapeId = tape.id)
    }

    /** The session already holds [RulerState.MAX_TAPES] tapes: no tape is made, a message says why. */
    private fun refuseTape(state: RulerState, a: String, b: String, fx: MutableList<RulerEffect>): RulerState {
        fx += RulerEffect.Log("TAPE refused reason=limit a=$a b=$b max=${RulerState.MAX_TAPES}")
        return state.showSnack(SnackKind.TAPE_LIMIT)
    }

    private fun tapPlate(state: RulerState, tapeId: String): RulerState {
        if (!state.ready || state.tape(tapeId) == null || state.pull != null) return state
        val s = state.disarmed().copy(menu = MenuState.CLOSED)
        return s.copy(selectedTapeId = if (state.selectedTapeId == tapeId) null else tapeId, lastTapeId = tapeId)
    }

    private fun tapNothing(state: RulerState, stillScanning: Boolean, nowMs: Long): RulerState {
        if (!state.measuring || state.pull != null) return state
        val s = state.copy(menu = MenuState.CLOSED)
        if (s.selectedTapeId != null) return s.copy(selectedTapeId = null)
        return s.noSurface(stillScanning, nowMs)
    }

    /**
     * A surface-less system tap while the room may still be mapping: kept as the (only) pending aim, with
     * the "still mapping" message and no failure feedback. A later one replaces it; a pin placed any other
     * way, a surface arriving under it, or [PENDING_TAP_MS] ends it.
     */
    private fun tapUnmapped(state: RulerState, action: RulerAction.TapUnmapped, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        if (!state.measuring || state.pull != null) return state
        var s = state.copy(menu = MenuState.CLOSED)
        if (s.selectedTapeId != null) s = s.copy(selectedTapeId = null)
        val replaced = s.pendingTap != null
        s = s.copy(pendingTap = action.tap)
        fx += RulerEffect.Log("TAP pending at=${fmt(action.tap.origin)} dir=${fmt(action.tap.direction)} scanning=${action.stillScanning} replaced=$replaced")
        return s.noSurface(stillScanning = true, nowMs = nowMs)
    }

    private fun pendingTapTick(state: RulerState, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        val pending = state.pendingTap ?: return state
        if (nowMs - pending.atMs < PENDING_TAP_MS) return state
        fx += RulerEffect.Log("TAP pending expired after_ms=${nowMs - pending.atMs}")
        return state.copy(pendingTap = null, lastNoSurfaceMs = 0L).noSurface(stillScanning = false, nowMs = nowMs)
    }

    // ------------------------------------------------------------------ press-and-pull

    private fun startPull(state: RulerState, action: RulerAction.StartPull, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        if (!state.measuring || state.dialog != DialogState.NONE || state.pull != null) return state
        var s = state.copy(menu = MenuState.CLOSED, selectedTapeId = null)
        // the whole pull (head, tail and tape) is one undo step
        s = s.withHistory("$PULL_TAG$nowMs", nowMs)
        val pinId = action.pinId
        if (pinId != null) {
            val pin = state.pin(pinId) ?: return state
            if (pin.elsewhere) return state
            fx += RulerEffect.Log("PULL start head=$pinId new=false")
            return s.armed(pinId).copy(pull = PullState(pinId, headIsNew = false))
        }
        val placement = action.placement ?: return state
        val (withPin, id) = addPin(s, placement, fx)
        fx += RulerEffect.Log("PULL start head=$id new=true")
        fx += RulerEffect.Persist
        return withPin.armed(id).copy(pull = PullState(id, headIsNew = true))
    }

    private fun endPull(state: RulerState, end: PullEnd, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        val pull = state.pull ?: return state
        var s = state.copy(pull = null)
        val head = pull.headPinId
        if (s.pin(head) == null) return s.disarmed()
        return when (end) {
            is PullEnd.OnSurface -> {
                val (withPin, id) = addPin(s, end.placement, fx)
                s = withPin
                s =
                    if (s.atTapeLimit) refuseTape(s, head, id, fx).armed(id)
                    else lockTape(s, head, id, fx).disarmed().coachDone(fx, "first_tape")
                fx += RulerEffect.Log("PULL end head=$head tail=$id")
                fx += RulerEffect.Persist
                s
            }
            is PullEnd.OnPin -> {
                val tail = end.pinId
                if (tail == head || s.pin(tail) == null) {
                    // let go on the head itself: nothing was pulled
                    fx += RulerEffect.Log("PULL end head=$head tail=none")
                    return dropEmptyPull(s, pull)
                }
                val before = s.tapes.size
                s =
                    if (s.atTapeLimit && !s.hasTape(head, tail)) refuseTape(s, head, tail, fx)
                    else lockTape(s, head, tail, fx).disarmed().coachDone(fx, "first_tape")
                fx += RulerEffect.Log("PULL end head=$head tail=$tail")
                if (s.tapes.size == before) s = dropEmptyPull(s, pull) else fx += RulerEffect.Persist
                s
            }
            is PullEnd.Nowhere -> {
                fx += RulerEffect.Log("PULL end head=$head tail=none")
                dropEmptyPull(s, pull).noSurface(end.stillScanning, nowMs)
            }
        }
    }

    /** A pull that changed nothing must not leave an undo step behind. */
    private fun dropEmptyPull(state: RulerState, pull: PullState): RulerState {
        if (pull.headIsNew) return state // the head pin it set is undone by that step
        val last = state.history.lastOrNull() ?: return state
        return if (last.tag?.startsWith(PULL_TAG) == true) state.copy(history = state.history.dropLast(1)) else state
    }

    // ------------------------------------------------------------------ fine-tune

    private fun nudge(state: RulerState, action: RulerAction.Nudge, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        val pin = state.armedPin ?: return state
        if (!state.measuring || action.step == 0) return state
        val step = if (action.step > 0) 1 else -1
        val du = pin.offsetUMm + if (action.axis == NudgeAxis.U) step else 0
        val dv = pin.offsetVMm + if (action.axis == NudgeAxis.V) step else 0
        val moved = moveTo(state, pin, du, dv, action.rangeM, nowMs, fx, "nudge") ?: return state
        fx += RulerEffect.Persist
        return moved
    }

    /**
     * A press on a pin has moved past the drag slop (round 5): the pin is selected (its strip opens) and
     * follows the pointer until [endPinDrag]. Nothing is moved yet; the scene draws the pin at the target.
     */
    private fun startPinDrag(state: RulerState, pinId: String, fx: MutableList<RulerEffect>): RulerState {
        if (!state.measuring || state.pull != null) return state
        val pin = state.pin(pinId) ?: return state
        if (pin.elsewhere) return state
        fx += RulerEffect.Log("PIN drag start id=$pinId from=${fmt(pin.position)}")
        return state.copy(
            menu = MenuState.CLOSED,
            armedPinId = pinId,
            armedExplicit = true,
            tuneOpen = true,
            selectedTapeId = null,
            pinDrag = pinId,
        )
    }

    /**
     * The drag was released (round 5, spec 3). On the pin's own plane the move is an offset in its frame,
     * with the anchor re-created when it goes beyond 50 mm (the standard rebase, "pinning" meanwhile);
     * on another plane the pin re-seats there with a fresh anchor (the old one is removed). A release
     * within 10 mm of where the pin was is no change. One undo step takes the whole move back.
     */
    private fun endPinDrag(state: RulerState, action: RulerAction.EndPinDrag, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        if (state.pinDrag != action.pinId) return state
        var s = state.copy(pinDrag = null)
        val pin = s.pin(action.pinId) ?: return s
        val placement = action.placement
        if (placement == null || drags.cancelled(pin.position, placement.point)) {
            fx += RulerEffect.Log("PIN drag end id=${pin.id} from=${fmt(pin.position)} to=${if (placement == null) "none" else fmt(placement.point)} via=${action.via} gain=${action.gain} cancelled=true")
            return s
        }
        s = s.withHistory(null, nowMs)
        val target = placement.point
        val range = placement.rangeM
        val far = range > ComputeToleranceUseCase.FAR_RANGE_M
        val same = drags.samePlane(pin.position, pin.normal, target, placement.normal)
        val moved: Pin
        if (same) {
            val delta = target - pin.base
            moved =
                pin.copy(
                    offsetUMm = (delta.dot(pin.uAxis) * 1000f).roundToInt(),
                    offsetVMm = (delta.dot(pin.vAxis) * 1000f).roundToInt(),
                    grade = placement.grade,
                    snapped = placement.snapped,
                    rangeM = range,
                    far = far,
                    seat = null,
                )
            if (abs(moved.offsetUMm) > MAX_OFFSET_MM || abs(moved.offsetVMm) > MAX_OFFSET_MM) {
                fx += RulerEffect.Log("PIN drag refused id=${pin.id} reason=beyond-${MAX_OFFSET_MM}mm")
                return state.copy(pinDrag = null)
            }
        } else {
            // another surface: the pin is set there afresh, its old anchor goes, a new one is made at the target
            moved =
                pin.copy(
                    anchorId = null,
                    saveState = SaveState.PENDING,
                    base = target,
                    baseRotation = Quat4.IDENTITY,
                    normalLocal = placement.normal,
                    uLocal = placement.uAxis,
                    vLocal = placement.vAxis,
                    offsetUMm = 0,
                    offsetVMm = 0,
                    surface = placement.surface,
                    planeLabel = placement.planeLabel,
                    grade = placement.grade,
                    snapped = placement.snapped,
                    rangeM = range,
                    far = far,
                    retrying = false,
                    rebasing = false,
                    seat = null,
                )
            pin.anchorId?.let { fx += RulerEffect.RemoveAnchor(it) }
            fx += RulerEffect.Log("ANCHOR rebase start id=${pin.id} uuid_old=${pin.anchorId ?: "none"} pos=${fmt(target)} reason=drag-plane")
            s = s.copy(liveAnchors = s.liveAnchors - pin.id)
        }
        fx += RulerEffect.Log("PIN drag end id=${pin.id} from=${fmt(pin.position)} to=${fmt(moved.position)} via=${action.via} gain=${action.gain} plane=${if (same) "same" else "new"} grade=${placement.grade.tag} snapped=${placement.snapped.tag}")
        fx += RulerEffect.Feedback(FeedbackKind.PIN_DROP)
        fx += RulerEffect.Persist
        s = s.copy(pins = s.pins.map { if (it.id == pin.id) moved else it })
        // on its own plane beyond 50 mm: the anchor is re-created at the pin now (the old one holds it meanwhile)
        if (same && moved.offsetMm() > REBASE_MM) s = startRebase(s, moved, moved.position, fx, "drag")
        return s
    }

    /**
     * Moves [pin] to the fine-tune offsets; a run of moves of one pin is one undo step. There is no
     * cap at the 50 mm of the anchor model: beyond [REBASE_MM] the anchor is re-created when the
     * fine-tune rests (see [rebaseDue]). [MAX_OFFSET_MM] is only a sanity bound, and refusing is logged.
     */
    private fun moveTo(state: RulerState, pin: Pin, du: Int, dv: Int, rangeM: Float?, nowMs: Long, fx: MutableList<RulerEffect>, verb: String): RulerState? {
        if (abs(du) > MAX_OFFSET_MM || abs(dv) > MAX_OFFSET_MM) {
            fx += RulerEffect.Log("PIN $verb refused id=${pin.id} du=$du dv=$dv reason=beyond-${MAX_OFFSET_MM}mm")
            return null
        }
        val s = state.withHistory("nudge:${pin.id}", nowMs)
        val range = rangeM ?: pin.rangeM
        // a pin that was trued by hand has left its seat and counts as finely aimed
        val trued =
            pin.copy(
                offsetUMm = du,
                offsetVMm = dv,
                grade = if (pin.grade == Grade.CONTACT) Grade.CONTACT else Grade.FINE,
                snapped = SnapKind.NONE,
                rangeM = range,
                far = range > ComputeToleranceUseCase.FAR_RANGE_M,
            )
        fx += RulerEffect.Log("PIN $verb id=${pin.id} du=$du dv=$dv pos=${fmt(trued.position)}")
        return s.copy(pins = s.pins.map { if (it.id == pin.id) trued else it })
    }

    private fun removePin(state: RulerState, pinId: String?, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        if (!state.ready || pinId == null || state.pin(pinId) == null) return state
        var s = state.withHistory(null, nowMs)
        s.tapes.filter { it.touches(pinId) }.forEach {
            fx += RulerEffect.Log("TAPE remove id=${it.id} a=${it.a} b=${it.b} reason=pin")
        }
        fx += RulerEffect.Log("PIN remove id=$pinId")
        val tapes = s.tapes.filter { !it.touches(pinId) }
        s = s.copy(pins = s.pins.filter { it.id != pinId }, tapes = tapes)
        s = s.copy(
            lastTapeId = if (s.tape(s.lastTapeId) != null) s.lastTapeId else tapes.lastOrNull()?.id,
            selectedTapeId = if (s.tape(s.selectedTapeId) != null) s.selectedTapeId else null,
        )
        fx += RulerEffect.Persist
        return s.disarmed()
    }

    private fun retract(state: RulerState, tapeId: String?, nowMs: Long, fx: MutableList<RulerEffect>): RulerState {
        val tape = state.tape(tapeId) ?: return state
        if (!state.ready) return state
        val s = state.withHistory(null, nowMs)
        fx += RulerEffect.Log("TAPE remove id=${tape.id} a=${tape.a} b=${tape.b} reason=retract")
        fx += RulerEffect.Feedback(FeedbackKind.RETRACT)
        val tapes = s.tapes.filter { it.id != tape.id }
        fx += RulerEffect.Persist
        return s.copy(tapes = tapes, selectedTapeId = null, lastTapeId = tapes.lastOrNull()?.id)
    }

    private fun undo(state: RulerState, fx: MutableList<RulerEffect>): RulerState {
        if (!state.ready || state.pull != null) return state
        val entry = state.history.lastOrNull() ?: return state.copy(menu = MenuState.CLOSED)
        // anchor facts are not undone: a pin that still exists keeps its current anchor pose
        val current = state.pins.associateBy { it.id }
        val rePinned = HashSet<String>()
        val pins =
            entry.pins.map { old ->
                val now = current[old.id]
                when {
                    now == null -> old.copy(seat = null)
                    !drags.samePlane(now.position, now.normal, old.position, old.normal) -> {
                        // the pin was dragged onto another surface since: back onto the old one, with a fresh anchor there
                        now.anchorId?.let { fx += RulerEffect.RemoveAnchor(it) }
                        fx += RulerEffect.Log("ANCHOR rebase start id=${old.id} uuid_old=${now.anchorId ?: "none"} pos=${fmt(old.position)} reason=undo-plane")
                        rePinned += old.id
                        old.copy(
                            anchorId = null,
                            saveState = SaveState.PENDING,
                            base = old.position,
                            baseRotation = Quat4.IDENTITY,
                            normalLocal = old.normal,
                            uLocal = old.uAxis,
                            vLocal = old.vAxis,
                            offsetUMm = 0,
                            offsetVMm = 0,
                            retrying = false,
                            rebasing = false,
                            seat = null,
                        )
                    }
                    now.anchorId != null && now.anchorId != old.anchorId -> {
                        // the anchor was re-created since (fine-tune beyond 50 mm, R2-4): the old offsets belong to the old
                        // anchor's frame. The pin goes back to its old Stage position, expressed in the new frame; the
                        // anchor is re-created there below, so that the offset is zero again (R3-1).
                        val delta = old.position - now.base
                        old.copy(
                            base = now.base,
                            baseRotation = now.baseRotation,
                            anchorId = now.anchorId,
                            saveState = now.saveState,
                            retrying = now.retrying,
                            rebasing = now.rebasing,
                            normalLocal = now.normalLocal,
                            uLocal = now.uLocal,
                            vLocal = now.vLocal,
                            offsetUMm = (delta.dot(now.uAxis) * 1000f).roundToInt(),
                            offsetVMm = (delta.dot(now.vAxis) * 1000f).roundToInt(),
                            seat = null,
                        )
                    }
                    else -> old.copy(base = now.base, baseRotation = now.baseRotation, anchorId = now.anchorId, saveState = now.saveState, retrying = now.retrying, rebasing = now.rebasing, seat = null)
                }
            }
        val restoredIds = pins.map { it.id }.toSet()
        state.pins.filter { it.id !in restoredIds }.forEach { fx += RulerEffect.Log("PIN remove id=${it.id} reason=undo") }
        val tapeIds = entry.tapes.map { it.id }.toSet()
        state.tapes.filter { it.id !in tapeIds }.forEach {
            fx += RulerEffect.Log("TAPE remove id=${it.id} a=${it.a} b=${it.b} reason=undo")
        }
        fx += RulerEffect.Log("UNDO depth=${state.history.size - 1} pins=${pins.size} tapes=${entry.tapes.size}")
        fx += RulerEffect.Persist
        var s =
            state.copy(
                pins = pins,
                tapes = entry.tapes,
                lastTapeId = entry.lastTapeId,
                history = state.history.dropLast(1),
                armedPinId = null,
                armedExplicit = false,
                tuneOpen = false,
                pinDrag = null,
                selectedTapeId = null,
                menu = MenuState.CLOSED,
                liveAnchors = state.liveAnchors - rePinned,
                // a message about something that was just undone is stale; a standing one stays
                snacks = state.snacks.filter { it.kind.durationMs == SnackKind.STICKY },
            )
        // a pin that went back across a rebase gets its anchor re-created at its old place (exactly: the entry's position)
        for (old in entry.pins) {
            val pin = s.pin(old.id) ?: continue
            if (pin.id !in rePinned && pin.anchorId != null && pin.anchorId != old.anchorId && pin.offsetMm() > REBASE_MM) s = startRebase(s, pin, old.position, fx, "undo")
        }
        return s
    }

    private fun clearAll(state: RulerState, fx: MutableList<RulerEffect>): RulerState {
        if (state.dialog != DialogState.CONFIRM_CLEAR) return state
        fx += RulerEffect.Log("CLEAR pins=${state.pins.size} tapes=${state.tapes.size}")
        fx += RulerEffect.Persist
        return state.disarmed()
            .copy(
                pins = emptyList(),
                tapes = emptyList(),
                history = emptyList(),
                lastTapeId = null,
                selectedTapeId = null,
                dialog = DialogState.NONE,
                snacks = state.snacks.filter { it.kind.durationMs == SnackKind.STICKY },
            )
            .showSnack(SnackKind.CLEARED)
    }

    private fun exit(state: RulerState, fx: MutableList<RulerEffect>): RulerState {
        if (state.phase == Phase.EXITED) return state
        // pins that never got an anchor do not survive
        val kept = state.pins.filter { it.saveState == SaveState.SAVED || it.elsewhere }
        val ids = kept.map { it.id }.toSet()
        fx += RulerEffect.Persist
        fx += RulerEffect.ExitApp
        return state.disarmed()
            .copy(
                pins = kept,
                tapes = state.tapes.filter { it.a in ids && it.b in ids },
                phase = Phase.EXITED,
                menu = MenuState.CLOSED,
                dialog = DialogState.NONE,
                selectedTapeId = null,
                snacks = emptyList(),
                pull = null,
                trackingLost = false,
            )
    }

    // ------------------------------------------------------------------ tracking and availability

    private fun tracking(state: RulerState, lost: Boolean, fx: MutableList<RulerEffect>): RulerState {
        if (state.trackingLost == lost || state.phase == Phase.EXITED || state.phase == Phase.BLOCKED) return state
        if (lost) {
            fx += RulerEffect.Log("TRACK lost")
            var s = state
            val pull = s.pull
            if (pull != null) s = dropEmptyPull(s.copy(pull = null), pull)
            return s.copy(trackingLost = true, pinDrag = null, menu = MenuState.CLOSED).showSnack(SnackKind.LOST)
        }
        fx += RulerEffect.Log("TRACK recovered")
        return state.copy(trackingLost = false, snacks = state.snacks.filter { it.kind != SnackKind.LOST }).showSnack(SnackKind.RECOVERED)
    }

    /** "Raise a hand or pick up a controller to continue" stands while no tracked source is there (R2-1). */
    private fun source(state: RulerState, lost: Boolean, fx: MutableList<RulerEffect>): RulerState {
        if (state.noSource == lost || state.phase != Phase.READY) return state
        if (lost) {
            fx += RulerEffect.Log("SOURCE lost")
            return state.copy(noSource = true).showSnack(SnackKind.NO_SOURCE)
        }
        fx += RulerEffect.Log("SOURCE back")
        return state.copy(noSource = false, snacks = state.snacks.filter { it.kind != SnackKind.NO_SOURCE })
    }

    private fun markElsewhere(state: RulerState, count: Int, fx: MutableList<RulerEffect>): RulerState {
        if (!state.ready || count <= 0) return state
        val gone = state.visiblePins.takeLast(count).map { it.id }.toSet()
        if (gone.isEmpty()) return state
        fx += RulerEffect.Log("PIN elsewhere ids=${gone.joinToString(",")}")
        val s =
            state.disarmed().copy(
                pins = state.pins.map { if (it.id in gone) it.copy(elsewhere = true) else it },
                // their anchors are simply "not found here": they are not removed from the store
                liveAnchors = state.liveAnchors - gone,
                selectedTapeId = null,
            )
        return s.copy(lastTapeId = s.visibleTapes.lastOrNull()?.id).showSnack(SnackKind.ELSEWHERE, gone.size)
    }

    // ------------------------------------------------------------------ anchors

    private fun anchorSaved(state: RulerState, action: RulerAction.AnchorSaved, fx: MutableList<RulerEffect>): RulerState {
        val pending = state.pendingCreates - action.pinId
        val pin = state.pin(action.pinId)
        val existing = state.liveAnchors[action.pinId]
        if (pin == null || pin.elsewhere || state.phase == Phase.EXITED || (existing != null && existing != action.anchorId)) {
            // the pin went away while its anchor was being created: do not leave the anchor behind
            fx += RulerEffect.RemoveAnchor(action.anchorId)
            return state.copy(pendingCreates = pending)
        }
        if (pin.saveState == SaveState.PENDING && pin.anchorId == null && pin.base.distanceTo(action.pose.position) > STALE_ANCHOR_M) {
            // the pin was re-seated (a drag, or an undo) while this anchor was being made: it is stale, another is made where the pin is now
            fx += RulerEffect.Log("ANCHOR stale id=${pin.id} uuid=${action.anchorId} pos=${fmt(action.pose.position)} pin=${fmt(pin.base)}")
            fx += RulerEffect.RemoveAnchor(action.anchorId)
            return state.copy(pendingCreates = pending)
        }
        // keep the surface frame where it is in the Stage and express it in the anchor's own frame
        val inverse = action.pose.rotation.conjugate()
        val saved =
            pin.copy(
                anchorId = action.anchorId,
                base = action.pose.position,
                baseRotation = action.pose.rotation,
                normalLocal = inverse.rotate(pin.normal),
                uLocal = inverse.rotate(pin.uAxis),
                vLocal = inverse.rotate(pin.vAxis),
                saveState = SaveState.SAVED,
                retrying = false,
            )
        fx += RulerEffect.Persist
        var s =
            state.copy(
                pins = state.pins.map { if (it.id == pin.id) saved else it },
                liveAnchors = state.liveAnchors + (pin.id to action.anchorId),
                pendingCreates = pending,
            )
        if (pin.retrying) s = s.showSnack(SnackKind.SAVED)
        return s
    }

    private fun anchorFailed(state: RulerState, pinId: String): RulerState {
        val pending = state.pendingCreates - pinId
        val pin = state.pin(pinId) ?: return state.copy(pendingCreates = pending)
        if (pin.rebasing) {
            // the new anchor could not be made: the old one keeps holding the pin, offset and all
            return state.copy(pins = state.pins.map { if (it.id == pinId) it.copy(rebasing = false) else it }, pendingCreates = pending)
        }
        if (pin.saveState != SaveState.PENDING) return state.copy(pendingCreates = pending)
        val failed = pin.copy(saveState = SaveState.UNSAVED, anchorId = null, retrying = false)
        return state.copy(pins = state.pins.map { if (it.id == pinId) failed else it }, pendingCreates = pending)
            .showSnack(SnackKind.UNSAVED, pinId = pinId)
    }

    /**
     * Design rule "pin = anchor pose + local offset" without a silent cap (R2-4): once the fine-tune of a
     * saved pin has rested with an offset beyond [REBASE_MM], a new anchor is created at the pin's
     * current position. Meanwhile the pin shows as "pinning" and the old anchor still holds it.
     */
    private fun rebaseDue(state: RulerState, pinId: String, fx: MutableList<RulerEffect>): RulerState {
        val pin = state.pin(pinId) ?: return state
        if (!state.measuring || pin.elsewhere || pin.rebasing) return state
        if (state.pinDrag == pinId) return state // a drag is under way: the release decides
        val anchorId = pin.anchorId
        if (pin.saveState != SaveState.SAVED || anchorId == null) return state
        if (pin.offsetMm() <= REBASE_MM) return state
        return startRebase(state, pin, pin.position, fx, "rest")
    }

    /** Asks for a new anchor at [position] for the saved [pin]; the pin shows "pinning" until it is answered. */
    private fun startRebase(state: RulerState, pin: Pin, position: Vec3, fx: MutableList<RulerEffect>, reason: String): RulerState {
        val anchorId = pin.anchorId
        if (pin.rebasing || pin.elsewhere || pin.saveState != SaveState.SAVED || anchorId == null) return state
        fx += RulerEffect.Log("ANCHOR rebase start id=${pin.id} uuid_old=$anchorId du=${pin.offsetUMm} dv=${pin.offsetVMm} pos=${fmt(position)} reason=$reason")
        fx += RulerEffect.RebaseAnchor(pin.id, position, anchorId)
        return state.copy(pins = state.pins.map { if (it.id == pin.id) it.copy(rebasing = true) else it })
    }

    private fun anchorRebased(state: RulerState, action: RulerAction.AnchorRebased, fx: MutableList<RulerEffect>): RulerState {
        val pin = state.pin(action.pinId)
        if (pin == null || pin.elsewhere || !pin.rebasing || state.phase == Phase.EXITED) {
            // the pin went away (or was never rebasing): do not leave the new anchor behind
            fx += RulerEffect.RemoveAnchor(action.anchorId)
            return if (pin == null || !pin.rebasing) state else state.copy(pins = state.pins.map { if (it.id == pin.id) it.copy(rebasing = false) else it })
        }
        val old = pin.anchorId
        val inverse = action.pose.rotation.conjugate()
        // the pin stays where it is now: whatever the new anchor missed it by (or the pin moved meanwhile, e.g. an
        // undo while the anchor was being made) remains as its offset - normally zero
        val delta = pin.position - action.pose.position
        val moved =
            pin.copy(
                anchorId = action.anchorId,
                base = action.pose.position,
                baseRotation = action.pose.rotation,
                normalLocal = inverse.rotate(pin.normal),
                uLocal = inverse.rotate(pin.uAxis),
                vLocal = inverse.rotate(pin.vAxis),
                offsetUMm = (delta.dot(pin.uAxis) * 1000f).roundToInt(),
                offsetVMm = (delta.dot(pin.vAxis) * 1000f).roundToInt(),
                saveState = SaveState.SAVED,
                retrying = false,
                rebasing = false,
            )
        val shiftMm = (delta.length * 1000f).roundToInt()
        fx += RulerEffect.Log("ANCHOR rebase id=${pin.id} uuid_old=${old ?: "none"} uuid_new=${action.anchorId} shift_mm=$shiftMm pos=${fmt(moved.position)}")
        if (old != null && old != action.anchorId) fx += RulerEffect.RemoveAnchor(old)
        fx += RulerEffect.Persist
        // the tapes name their pins by id: they stay as they are
        return state.copy(
            pins = state.pins.map { if (it.id == pin.id) moved else it },
            liveAnchors = state.liveAnchors + (pin.id to action.anchorId),
        )
    }

    private fun anchorMoved(state: RulerState, action: RulerAction.AnchorMoved): RulerState {
        // while tracking is lost the readings are frozen: anchor poses reported then are not trusted
        if (state.trackingLost) return state
        if (state.pins.none { it.anchorId == action.anchorId && !it.elsewhere && !it.rebasing }) return state
        return state.copy(
            pins =
                state.pins.map {
                    if (it.anchorId == action.anchorId && !it.elsewhere && !it.rebasing) it.copy(base = action.pose.position, baseRotation = action.pose.rotation)
                    else it
                }
        )
    }

    private fun snackAction(state: RulerState, snackId: Int, fx: MutableList<RulerEffect>): RulerState {
        val snack = state.snack ?: return state
        if (snack.id != snackId) return state
        val s = state.copy(snacks = state.snacks.drop(1))
        return when (snack.kind) {
            SnackKind.UNSAVED -> {
                val pin = s.pin(snack.pinId)
                if (pin == null || pin.saveState != SaveState.UNSAVED) s
                else s.copy(pins = s.pins.map { if (it.id == pin.id) it.copy(saveState = SaveState.PENDING, retrying = true) else it })
            }
            SnackKind.ELSEWHERE -> {
                val gone = s.pins.filter { it.elsewhere }.map { it.id }.toSet()
                fx += RulerEffect.Log("PIN forget elsewhere=${gone.size}")
                fx += RulerEffect.Persist
                s.copy(pins = s.pins.filter { it.id !in gone }, tapes = s.tapes.filter { it.a !in gone && it.b !in gone })
            }
            else -> s
        }
    }

    /**
     * Keeps the anchor store equal to the current pins: removes anchors whose pin is gone and asks for
     * an anchor for every pin that should have one. A pin whose save failed waits for the user (Retry).
     */
    private fun reconcileAnchors(state: RulerState, fx: MutableList<RulerEffect>): RulerState {
        if (state.phase == Phase.STARTING || state.phase == Phase.BLOCKED) return state
        var live = state.liveAnchors
        val ids = state.pins.filter { !it.elsewhere }.map { it.id }.toSet()
        for ((pinId, anchorId) in state.liveAnchors) {
            if (pinId !in ids) {
                fx += RulerEffect.RemoveAnchor(anchorId)
                live = live - pinId
            }
        }
        if (state.phase == Phase.EXITED) return if (live === state.liveAnchors) state else state.copy(liveAnchors = live)
        var pending = state.pendingCreates
        var changed = false
        val pins =
            state.pins.map { pin ->
                if (pin.elsewhere) return@map pin
                val liveId = live[pin.id]
                val fixed =
                    when {
                        liveId != null ->
                            if (pin.saveState == SaveState.SAVED && pin.anchorId == liveId) pin
                            else pin.copy(saveState = SaveState.SAVED, anchorId = liveId, retrying = false)
                        pin.id in pending ->
                            if (pin.saveState == SaveState.PENDING && pin.anchorId == null) pin
                            else pin.copy(saveState = SaveState.PENDING, anchorId = null)
                        pin.saveState == SaveState.UNSAVED -> pin
                        else -> {
                            fx += RulerEffect.CreateAnchor(pin.id, pin.base)
                            pending = pending + pin.id
                            if (pin.saveState == SaveState.PENDING && pin.anchorId == null) pin
                            else pin.copy(saveState = SaveState.PENDING, anchorId = null)
                        }
                    }
                if (fixed !== pin) changed = true
                fixed
            }
        if (!changed && live === state.liveAnchors && pending === state.pendingCreates) return state
        return state.copy(pins = if (changed) pins else state.pins, liveAnchors = live, pendingCreates = pending)
    }

    // ------------------------------------------------------------------ helpers

    /** What the user does to pins, tapes, tags and the body keys (not: answers to a dialog, and what the system reports). */
    private val RulerAction.isUserInput: Boolean
        get() =
            when (this) {
                is RulerAction.PlacePin,
                is RulerAction.TapPin,
                is RulerAction.ChooseCandidate,
                is RulerAction.TapPlate,
                is RulerAction.TapNothing,
                is RulerAction.TapUnmapped,
                is RulerAction.StartPull,
                RulerAction.ClosePinTag,
                is RulerAction.Nudge,
                is RulerAction.StartPinDrag,
                is RulerAction.EndPinDrag,
                is RulerAction.RemovePin,
                is RulerAction.RetractTape,
                is RulerAction.Connect,
                RulerAction.Undo,
                is RulerAction.SetUnit,
                is RulerAction.SetSnap,
                is RulerAction.SetLanguage,
                RulerAction.ToggleMoreMenu,
                RulerAction.RequestClearAll,
                is RulerAction.SnackAction,
                RulerAction.CoachDismissed,
                is RulerAction.MarkElsewhere -> true
                else -> false
            }

    /**
     * A pinch at the scene, at a floating control or at a body key other than More: consumed when it
     * dismisses an open menu (the menu's own controls - language, snap, Clear all, Exit - and the More
     * key keep working).
     */
    private val RulerAction.isSceneTap: Boolean
        get() =
            when (this) {
                is RulerAction.PlacePin,
                is RulerAction.TapPin,
                is RulerAction.ChooseCandidate,
                is RulerAction.TapPlate,
                is RulerAction.TapNothing,
                is RulerAction.TapUnmapped,
                is RulerAction.StartPull,
                is RulerAction.Connect,
                is RulerAction.SetUnit,
                RulerAction.Undo -> true
                else -> false
            }

    /** Length of the fine-tune offset, millimetres. */
    private fun Pin.offsetMm(): Float = hypot(offsetUMm.toFloat(), offsetVMm.toFloat())

    private val RulerState.ready: Boolean
        get() = phase == Phase.READY

    /** Pins and tapes can be set and moved: the session is ready and tracking is not lost. */
    private val RulerState.measuring: Boolean
        get() = phase == Phase.READY && !trackingLost

    /** The More menu (language, exit) also works while the scene is still being built. */
    private val RulerState.menuAvailable: Boolean
        get() = phase == Phase.READY || ((phase == Phase.STARTING || phase == Phase.RESTORING) && settingsKnown)

    private val RulerState.atTapeLimit: Boolean
        get() = visibleTapes.size >= RulerState.MAX_TAPES

    private fun RulerState.hasTape(a: String, b: String): Boolean = tapes.any { it.joins(a, b) }

    /** [pinId] becomes the tape head without being selected (no tag, no candidates). */
    private fun RulerState.armed(pinId: String): RulerState =
        copy(armedPinId = pinId, armedExplicit = false, tuneOpen = false, selectedTapeId = null)

    private fun RulerState.disarmed(): RulerState =
        if (armedPinId == null && !armedExplicit && !tuneOpen) this
        else copy(armedPinId = null, armedExplicit = false, tuneOpen = false)

    /** The coachmark is over for good; [reason] is `first_tape` or `got_it` (`COACH dismissed reason=`). */
    private fun RulerState.coachDone(fx: MutableList<RulerEffect>, reason: String): RulerState {
        if (settings.coachSeen) return this
        fx += RulerEffect.Log("COACH dismissed reason=$reason")
        return copy(settings = settings.copy(coachSeen = true))
    }

    private fun RulerState.noSurface(stillScanning: Boolean, nowMs: Long): RulerState {
        if (nowMs - lastNoSurfaceMs < NO_SURFACE_REPEAT_MS) return this
        return copy(lastNoSurfaceMs = nowMs).showSnack(if (stillScanning) SnackKind.NOT_MAPPED else SnackKind.NO_SURFACE)
    }

    /** Records an undo step. A run of nudges of the same pin within 2.5 s is one step. */
    private fun RulerState.withHistory(tag: String?, nowMs: Long): RulerState {
        val last = history.lastOrNull()
        if (tag != null && last != null && last.tag == tag && nowMs - last.timeMs < COALESCE_MS) {
            return copy(history = history.dropLast(1) + last.copy(timeMs = nowMs))
        }
        val entry = HistoryEntry(pins, tapes, lastTapeId, tag, nowMs)
        return copy(history = (history + entry).takeLast(RulerState.MAX_HISTORY))
    }

    /**
     * Shows a message now. A message that waits for an answer, or that stands until its cause is over,
     * is not pushed away by a passing one.
     */
    private fun RulerState.showSnack(kind: SnackKind, a: Int = 0, b: Int = 0, pinId: String? = null): RulerState {
        val snack = Snack(snackSeq + 1, kind, a, b, pinId)
        val current = snacks.firstOrNull()
        val standing = snacks.filter { it.kind.stands }
        val queue =
            if (current != null && current.kind.stands && !kind.stands) standing + snack
            else listOf(snack) + standing
        return copy(snacks = queue.take(MAX_SNACKS), snackSeq = snackSeq + 1)
    }

    private val SnackKind.stands: Boolean
        get() = hasAction || durationMs == SnackKind.STICKY

    /** Appends a message behind the one that is showing. */
    private fun RulerState.enqueueSnack(kind: SnackKind, a: Int = 0, b: Int = 0): RulerState =
        copy(snacks = (snacks + Snack(snackSeq + 1, kind, a, b)).take(MAX_SNACKS), snackSeq = snackSeq + 1)

    companion object {
        const val COALESCE_MS = 2500L
        const val NO_SURFACE_REPEAT_MS = 3500L
        /** Sanity bound of a fine-tune offset (2 m); the design's 50 mm is [REBASE_MM], not a cap. */
        const val MAX_OFFSET_MM = 2000

        /** Beyond this offset the anchor is re-created at the pin when the fine-tune rests. */
        const val REBASE_MM = 50f

        /** An anchor answered this far from the pin's base was made for a place the pin has left (drag, undo). */
        const val STALE_ANCHOR_M = 0.05f

        /** A new point this close to a pin is that pin. */
        const val SAME_PIN_M = 0.010f
        private const val MAX_SNACKS = 3
        private const val PULL_TAG = "pull@"

        /** Stage position with four decimals, the format the tests grep for. */
        fun fmt(v: Vec3): String = String.format(Locale.US, "(%.4f, %.4f, %.4f)", v.x, v.y, v.z)

        fun f4(v: Float): String = String.format(Locale.US, "%.4f", v)
    }
}
