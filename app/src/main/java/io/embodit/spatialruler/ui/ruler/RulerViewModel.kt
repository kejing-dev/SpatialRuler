package io.embodit.spatialruler.ui.ruler

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.embodit.spatialruler.data.repository.AnchorGateway
import io.embodit.spatialruler.data.repository.AnchorOutcome
import io.embodit.spatialruler.data.repository.SessionRepository
import io.embodit.spatialruler.domain.model.AnchorPose
import io.embodit.spatialruler.domain.model.BlockReason
import io.embodit.spatialruler.domain.model.DialogState
import io.embodit.spatialruler.domain.model.FeedbackKind
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.MenuState
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.Phase
import io.embodit.spatialruler.domain.model.PendingTap
import io.embodit.spatialruler.domain.model.PENDING_TAP_MS
import io.embodit.spatialruler.domain.model.Pin
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.PullEnd
import io.embodit.spatialruler.domain.model.RulerAction
import io.embodit.spatialruler.domain.model.RulerEffect
import io.embodit.spatialruler.domain.model.RulerSettings
import io.embodit.spatialruler.domain.model.RulerState
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SnackKind
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.StoredPin
import io.embodit.spatialruler.domain.model.StoredSession
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.port.MeasureEnvironment
import io.embodit.spatialruler.domain.port.RulerLogger
import io.embodit.spatialruler.domain.usecase.AimResult
import io.embodit.spatialruler.domain.usecase.ComputeToleranceUseCase
import io.embodit.spatialruler.domain.usecase.FormatReadingUseCase
import io.embodit.spatialruler.domain.usecase.PullLineUseCase
import io.embodit.spatialruler.domain.usecase.ReduceRulerUseCase
import io.embodit.spatialruler.domain.usecase.ResolvePlacementUseCase
import io.embodit.spatialruler.platform.RulerGraph
import io.embodit.spatialruler.platform.RulerLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unidirectional state holder of the measuring screen.
 *
 * Events (scene callbacks, panel controls, test hooks) are turned into domain actions; the pure
 * [ReduceRulerUseCase] produces the next state plus effects; effects are carried out here (anchor
 * store, storage, log) and feed their results back as actions. Everything runs on the main thread.
 *
 * Launch order: the stored preferences are read at once ([RulerAction.SettingsLoaded]) so that the body
 * panel can say something in the right language before the scene exists; pins and tapes follow when
 * the scene reports [RulerEvent.SceneReady].
 */
class RulerViewModel(
    private val repository: SessionRepository,
    private val anchors: AnchorGateway,
    private val environment: MeasureEnvironment,
    private val logger: RulerLogger,
    private val defaults: () -> RulerSettings,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel(), RulerAim {

    private val reducer = ReduceRulerUseCase()
    private val placement = ResolvePlacementUseCase()
    private val tolerance = ComputeToleranceUseCase()
    private val format = FormatReadingUseCase()
    private val pullLine = PullLineUseCase()

    private var domain = RulerState()
    private val _state = MutableStateFlow(RulerUiState())
    val state: StateFlow<RulerUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<RulerUiEffect>(extraBufferCapacity = 16)

    /** The hand that holds the tracked pointer in use: touch feedback goes there. */
    private var pointerRight = true
    val effects: SharedFlow<RulerUiEffect> = _effects.asSharedFlow()

    /** tape id -> plate slot; a tape keeps its plate for as long as it exists. */
    private val plateSlots = LinkedHashMap<String, Int>()
    private var lastViewState = ""
    private var snackJob: Job? = null
    private var snackTimerFor = -1
    private var snackEndsAtMs = 0L
    private val persistMutex = Mutex()
    private var failNextAnchor = false
    private var started = false
    private var sceneReady = false

    /** What storage held at launch; kept so that a change of preferences before the restore writes nothing else. */
    private var storedAtLaunch: StoredSession? = null
    private var sessionRestored = false
    private var forceBlocked = false
    private var longSnacks = false

    /** When a menu or dialog last closed: a scene tap right after it was the pinch that closed it (R2-2). */
    private var modalClosedMs = Long.MIN_VALUE / 4
    private var rebaseJob: Job? = null

    init {
        viewModelScope.launch {
            anchors.moves.collect { record -> dispatch(RulerAction.AnchorMoved(record.id, record.pose)) }
        }
        viewModelScope.launch {
            val stored = repository.load()
            storedAtLaunch = stored
            dispatch(RulerAction.SettingsLoaded(stored?.settings, defaults()))
        }
        publish()
    }

    /** Current domain state (tests and the debug `dump`). */
    val snapshot: RulerState
        get() = domain

    fun onEvent(event: RulerEvent) {
        when (event) {
            RulerEvent.SceneReady -> {
                sceneReady = true
                start()
            }
            is RulerEvent.PlanesChanged -> {
                dispatch(RulerAction.PlanesChanged(event.count, event.scanning))
                resolvePendingTap()
            }
            is RulerEvent.UnmappedTapped -> if (!consumedByModal("nothing")) unmappedTapped(event)
            is RulerEvent.SurfaceTapped -> if (!consumedByModal("surface")) surfaceTapped(event)
            is RulerEvent.PinTapped -> if (!consumedByModal("pin")) dispatch(RulerAction.TapPin(event.pinId))
            RulerEvent.NothingTapped -> if (!consumedByModal("nothing")) dispatch(RulerAction.TapNothing(domain.scanning))
            is RulerEvent.PointerTriggered -> if (!consumedByModal("pointer")) pointerTriggered(event)
            is RulerEvent.ContactTriggered -> if (!consumedByModal("contact")) contactTriggered(event)
            is RulerEvent.AimSnapped -> {
                pointerRight = event.right
                _effects.tryEmit(RulerUiEffect.Feedback(FeedbackKind.SNAP, event.right))
            }
            is RulerEvent.PointerHand -> pointerRight = event.right
            is RulerEvent.DragStarted -> if (!consumedByModal("drag")) dragStarted(event)
            is RulerEvent.PinDragStarted -> dispatch(RulerAction.StartPinDrag(event.pinId))
            is RulerEvent.PinDragEnded -> pinDragEnded(event)
            is RulerEvent.DragEnded -> dragEnded(event)
            is RulerEvent.TrackingChanged -> dispatch(RulerAction.TrackingChanged(event.lost))
            is RulerEvent.SourceChanged -> dispatch(RulerAction.SourceChanged(event.lost))
            RulerEvent.CoachDismissed -> control("coach_got_it", RulerAction.CoachDismissed)
            RulerEvent.DebugCoachReset -> dispatch(RulerAction.CoachReset)
            is RulerEvent.UnitSelected -> control("unit_${event.unit.tag}", RulerAction.SetUnit(event.unit))
            RulerEvent.UndoClicked -> control("undo", RulerAction.Undo)
            is RulerEvent.SnapToggled -> control("snap", RulerAction.SetSnap(event.on))
            RulerEvent.MoreClicked -> control("more", RulerAction.ToggleMoreMenu)
            RulerEvent.MenuDismissed -> dispatch(RulerAction.DismissMenu)
            is RulerEvent.LanguageSelected -> control("lang_${event.language.tag}", RulerAction.SetLanguage(event.language))
            RulerEvent.ClearAllClicked -> control("menu_clear", RulerAction.RequestClearAll)
            RulerEvent.ClearConfirmed -> control("dialog_confirm", RulerAction.ConfirmClearAll)
            RulerEvent.DialogDismissed -> control("dialog_cancel", RulerAction.DismissDialog)
            RulerEvent.ExitClicked -> control("menu_exit", RulerAction.Exit)
            is RulerEvent.BodyKeyPressed -> bodyKey(event.name)
            RulerEvent.BlockedRetryClicked -> control("blocked_retry", RulerAction.RetryBlocked)
            RulerEvent.BlockedExitClicked -> control("blocked_exit", RulerAction.Exit)
            is RulerEvent.PlateClicked -> control("plate:${event.tapeId}", RulerAction.TapPlate(event.tapeId))
            is RulerEvent.ChipClicked -> control("chip:${event.pinId}", RulerAction.ChooseCandidate(event.pinId))
            RulerEvent.PinTagClosed -> control("pin_close", RulerAction.ClosePinTag)
            is RulerEvent.Stepped -> stepped(event)
            RulerEvent.PinRemoveClicked -> control("pin_remove", RulerAction.RemovePin())
            RulerEvent.TapeRetractClicked -> control("tape_retract", RulerAction.RetractTape())
            is RulerEvent.SnackActionClicked -> control("snackbar_action", RulerAction.SnackAction(event.snackId))
            is RulerEvent.SnackClosed -> dispatch(RulerAction.SnackDismissed(event.snackId))
            is RulerEvent.DebugPlace -> {
                val p = placement.atPoint(event.point, environment.viewOrigin(), environment.surfaces(), Grade.COARSE)
                dispatch(RulerAction.PlacePin(p))
            }
            is RulerEvent.DebugConnect -> dispatch(RulerAction.Connect(event.a, event.b))
            is RulerEvent.DebugDelete -> dispatch(RulerAction.RemovePin(event.pinId))
            RulerEvent.DebugClear -> {
                dispatch(RulerAction.RequestClearAll)
                dispatch(RulerAction.ConfirmClearAll)
            }
            RulerEvent.DebugFailNextAnchor -> {
                failNextAnchor = true
                logger.log("ANCHOR fail_next armed")
            }
            is RulerEvent.DebugElsewhere -> dispatch(RulerAction.MarkElsewhere(event.count))
            is RulerEvent.DebugBlocked -> debugBlocked(event.on)
            is RulerEvent.DebugLongSnacks -> {
                longSnacks = event.on
                logger.log("SNACK long=${event.on}")
                snackTimerFor = -1 // the message that is showing gets its new time
                publish()
            }
        }
    }

    private fun control(name: String, action: RulerAction) {
        logger.log("UI $name")
        dispatch(action)
    }

    /**
     * R2-2: a pinch at the scene while a menu is open closes the menu and does nothing else; one that
     * arrives within [MODAL_GRACE_MS] of a menu or dialog closing was the pinch that closed it.
     */
    private fun consumedByModal(what: String): Boolean {
        if (domain.menu != MenuState.CLOSED) {
            logger.log("TAP consumed reason=menu-open event=$what")
            dispatch(RulerAction.DismissMenu)
            return true
        }
        if (clock() - modalClosedMs < MODAL_GRACE_MS) {
            logger.log("TAP consumed reason=menu-closing event=$what")
            return true
        }
        return false
    }

    /** R2-4: the anchor of [pinId] is re-created once its fine-tune has rested for 0.6 s (the reducer decides whether it must). */
    private fun scheduleRebase(pinId: String) {
        rebaseJob?.cancel()
        rebaseJob =
            viewModelScope.launch {
                delay(REBASE_REST_MS)
                dispatch(RulerAction.RebaseDue(pinId))
            }
    }

    /** A body key reached by its stable name (see `BodyGuard`: a pinch whose line of sight ends on the key). */
    private fun bodyKey(name: String) {
        val s = domain
        val event: RulerEvent? =
            when {
                name == "undo" -> if (s.phase == Phase.READY && s.history.isNotEmpty()) RulerEvent.UndoClicked else null
                name == "more" -> RulerEvent.MoreClicked
                name.startsWith("unit_") -> RulerUnit.fromTag(name.removePrefix("unit_"))?.let { RulerEvent.UnitSelected(it) }
                else -> null
            }
        if (event != null && s.settingsKnown) onEvent(event)
    }

    private fun stepped(event: RulerEvent.Stepped) {
        val axis = if (event.axis == NudgeAxis.U) "u" else "v"
        logger.log("UI step_${axis}_${if (event.step > 0) "plus" else "minus"}")
        dispatch(RulerAction.Nudge(event.axis, event.step, rangeOfArmed()))
    }

    private fun rangeOfArmed(): Float? {
        val pin = domain.armedPin ?: return null
        return environment.viewOrigin()?.distanceTo(pin.position)
    }

    private fun surfaceTapped(event: RulerEvent.SurfaceTapped) {
        val plane = environment.surface(event.planeId)
        if (plane == null) {
            dispatch(RulerAction.TapNothing(domain.scanning))
            return
        }
        val p =
            placement.onTappedSurface(
                plane = plane,
                stagePoint = event.stagePoint,
                viewOrigin = environment.viewOrigin(),
                planes = environment.surfaces(),
                snapEnabled = domain.settings.snap,
                tapeHead = domain.armedPin,
                grade = Grade.COARSE, // a system tap is where the eyes (or the mouse) were
            )
        dispatch(RulerAction.PlacePin(p))
    }

    /**
     * A system tap that met no surface collider: if the analytic planes already know a surface along the line
     * of sight the pin is placed at once; otherwise the tap waits as the pending aim (the latest one only) and a
     * ticker resolves it when a plane arrives under it, or drops it after [PENDING_TAP_MS].
     */
    private fun unmappedTapped(event: RulerEvent.UnmappedTapped) {
        if (domain.phase != Phase.READY || domain.pull != null) {
            dispatch(RulerAction.TapNothing(domain.scanning))
            return
        }
        when (val aim = pendingAim(event.ray)) {
            is AimResult.OnSurface -> dispatch(RulerAction.PlacePin(aim.placement.copy(via = "system")))
            else -> {
                dispatch(RulerAction.TapUnmapped(PendingTap(event.ray.origin, event.ray.direction, clock()), domain.scanning))
                startPendingTicker()
            }
        }
    }

    private fun pendingAim(ray: PointerRay): AimResult =
        placement.aim(
            ray = ray,
            planes = environment.surfaces(),
            pins = emptyList(),
            snapEnabled = domain.settings.snap,
            tapeHead = domain.armedPin,
            grade = Grade.COARSE,
        )

    private fun resolvePendingTap() {
        val pending = domain.pendingTap ?: return
        when (val aim = pendingAim(PointerRay(pending.origin, pending.direction))) {
            is AimResult.OnSurface -> dispatch(RulerAction.PendingTapResolved(aim.placement.copy(via = "system")))
            else -> dispatch(RulerAction.PendingTapTick)
        }
    }

    private var pendingJob: Job? = null

    private fun startPendingTicker() {
        if (pendingJob?.isActive == true) return
        pendingJob =
            viewModelScope.launch {
                while (domain.pendingTap != null) {
                    delay(PENDING_TAP_POLL_MS)
                    resolvePendingTap()
                }
            }
    }

    private fun pointerTriggered(event: RulerEvent.PointerTriggered) {
        when (val aim = aim(event.ray, event.grade, exclude = null)) {
            is AimResult.OnPin -> dispatch(RulerAction.TapPin(aim.pin.id))
            is AimResult.OnSurface -> dispatch(RulerAction.PlacePin(aim.placement.copy(via = event.via)))
            AimResult.Nothing -> dispatch(RulerAction.TapNothing(domain.scanning))
        }
    }

    /** Contact channel: the tool tip is the point; only an existing pin within reach of the tip takes its place. */
    private fun contactTriggered(event: RulerEvent.ContactTriggered) {
        when (val aim = placement.contact(event.tip, environment.viewOrigin(), environment.surfaces(), domain.visiblePins, event.pinch)) {
            is AimResult.OnPin -> dispatch(RulerAction.TapPin(aim.pin.id))
            is AimResult.OnSurface -> dispatch(RulerAction.PlacePin(aim.placement))
            AimResult.Nothing -> dispatch(RulerAction.TapNothing(domain.scanning))
        }
    }

    private fun aim(ray: PointerRay, grade: Grade, exclude: String?, tapeHead: Pin? = domain.armedPin): AimResult =
        placement.aim(
            ray = ray,
            planes = environment.surfaces(),
            pins = domain.visiblePins,
            snapEnabled = domain.settings.snap,
            tapeHead = tapeHead,
            grade = grade,
            exclude = exclude,
        )

    // ------------------------------------------------------------------ press-and-pull, truing by drag

    private fun dragStarted(event: RulerEvent.DragStarted) {
        val pinId = event.pinId
        if (pinId != null) {
            // a drag from the tape-head stub of a pin pulls a new tape out of it (a drag on the pin itself moves the pin)
            dispatch(RulerAction.StartPull(placement = null, pinId = pinId))
            return
        }
        val plane = event.planeId?.let { environment.surface(it) } ?: return
        // the press is where the eyes were: the head pin is set by the pointer-channel rule
        val p =
            placement.onTappedSurface(
                plane = plane,
                stagePoint = event.stagePoint,
                viewOrigin = environment.viewOrigin(),
                planes = environment.surfaces(),
                snapEnabled = domain.settings.snap,
                tapeHead = null,
                grade = Grade.COARSE,
            ).copy(via = "pull")
        dispatch(RulerAction.StartPull(placement = p, pinId = null))
    }

    /** Release of a pin drag: the pin lands where the pointer's line (or the tool tip) meets a surface. */
    private fun pinDragEnded(event: RulerEvent.PinDragEnded) {
        if (domain.pinDrag != event.pinId) return
        val others = domain.visiblePins.filter { it.id != event.pinId }
        val result: AimResult? =
            when {
                event.cancelled -> null
                event.tip != null -> placement.contact(event.tip, environment.viewOrigin(), environment.surfaces(), others, pinch = false)
                event.ray != null ->
                    placement.aim(
                        ray = event.ray,
                        planes = environment.surfaces(),
                        pins = others,
                        snapEnabled = domain.settings.snap,
                        tapeHead = null,
                        grade = event.grade,
                        exclude = event.pinId,
                        via = event.via,
                    )
                else -> null
            }
        val landed = (result as? AimResult.OnSurface)?.placement?.copy(grade = event.grade, via = event.via)
        dispatch(RulerAction.EndPinDrag(event.pinId, landed, event.via, event.gain))
    }

    private fun dragEnded(event: RulerEvent.DragEnded) {
        val s = domain
        val pull = s.pull ?: return
        val ray = event.ray
        if (event.cancelled || ray == null) {
            dispatch(RulerAction.CancelPull)
            return
        }
        val grade = if (event.steady) Grade.FINE else Grade.COARSE
        val end =
            when (val aim = aim(ray, grade, exclude = pull.headPinId)) {
                is AimResult.OnPin -> PullEnd.OnPin(aim.pin.id)
                is AimResult.OnSurface -> PullEnd.OnSurface(aim.placement.copy(via = "pull"))
                AimResult.Nothing -> PullEnd.Nowhere(s.scanning)
            }
        dispatch(RulerAction.EndPull(end))
    }

    override fun dragMode(): DragMode =
        when {
            domain.pull != null -> DragMode.PULL
            domain.pinDrag != null -> DragMode.PIN
            else -> DragMode.NONE
        }

    override fun preview(ray: PointerRay, exclude: String?): AimPreview =
        // a dragged pin ([exclude]) is not its own tape head: no plumb / level snapping to where it was
        when (val aim = aim(ray, Grade.COARSE, exclude = exclude ?: domain.pull?.headPinId, tapeHead = if (exclude != null) null else domain.armedPin)) {
            is AimResult.OnPin -> {
                val head = aim.pin.position + aim.pin.normal * ResolvePlacementUseCase.PIN_HEAD_M
                AimPreview(head, aim.pin.normal, head.distanceTo(ray.origin), SnapKind.PIN, aim.pin.id, far = false)
            }
            is AimResult.OnSurface -> {
                val p = aim.placement
                AimPreview(p.point, p.normal, p.rangeM, p.snapped, null, far = p.rangeM > ComputeToleranceUseCase.FAR_RANGE_M, seatA = p.seatA, seatB = p.seatB)
            }
            AimResult.Nothing -> AimPreview(null, Vec3.UP, 0f, SnapKind.NONE, null, far = false)
        }

    // ------------------------------------------------------------------ launch

    private fun start() {
        if (!sceneReady) return
        if (started && domain.phase != Phase.EXITED) return
        if (domain.phase == Phase.EXITED) {
            // a Stage that is opened again in the same process starts from storage
            domain = RulerState(settings = domain.settings, settingsKnown = domain.settingsKnown)
            plateSlots.clear()
            sessionRestored = false
            publish()
        }
        started = true
        viewModelScope.launch {
            val blocked = if (forceBlocked) BlockReason.UNKNOWN else settledAvailability()
            if (blocked != null) {
                dispatch(RulerAction.Blocked(blocked))
                return@launch
            }
            val stored = repository.load()
            storedAtLaunch = stored
            val loaded = loadAnchors()
            sessionRestored = true
            dispatch(RulerAction.Restored(stored, loaded, defaults()))
            if (domain.phase == Phase.RESTORING) {
                delay(RESTORE_SHOW_MS)
                dispatch(RulerAction.RestoreShown)
            }
        }
    }

    /** Anchors of this space. The store may not be ready right after the Stage opens: a few retries. */
    private suspend fun loadAnchors(): Map<String, AnchorPose> {
        repeat(LOAD_ATTEMPTS) { attempt ->
            val t0 = clock()
            when (val r = anchors.loadAll()) {
                is AnchorOutcome.Ok -> {
                    logger.log("ANCHOR load ok count=${r.value.size} ms=${clock() - t0}")
                    return r.value.associate { it.id to it.pose }
                }
                is AnchorOutcome.Failed -> {
                    logger.log("ANCHOR load failed code=${r.code} msg=${r.message} attempt=${attempt + 1}")
                    delay(LOAD_RETRY_MS)
                }
            }
        }
        return emptyMap()
    }

    /**
     * Why the spatial features cannot be used, or null. The app may still be on its way into Full Space
     * when the scene is ready: a reason that goes away within two seconds is none.
     */
    private suspend fun settledAvailability(): BlockReason? {
        var reason = environment.availability()
        var waited = 0L
        while (reason != null && waited < AVAILABILITY_WAIT_MS) {
            delay(AVAILABILITY_STEP_MS)
            waited += AVAILABILITY_STEP_MS
            reason = environment.availability()
        }
        return reason
    }

    /** Test hook: the session starts again as if the spatial features were unavailable (or available again). */
    private fun debugBlocked(on: Boolean) {
        forceBlocked = on
        logger.log("BLOCKED forced=$on")
        if (on && domain.phase != Phase.BLOCKED && domain.phase != Phase.EXITED) {
            domain = RulerState(settings = domain.settings, settingsKnown = domain.settingsKnown, planeCount = domain.planeCount, scanning = domain.scanning)
            plateSlots.clear()
            sessionRestored = false
            started = false
            publish()
            start()
        }
    }

    // ------------------------------------------------------------------ reduce + effects

    private fun dispatch(action: RulerAction) {
        val before = domain
        val reduction = reducer.reduce(domain, action, clock())
        domain = reduction.state
        reduction.effects.forEach(::perform)
        val after = domain
        if ((before.menu != MenuState.CLOSED && after.menu == MenuState.CLOSED) || (before.dialog != DialogState.NONE && after.dialog == DialogState.NONE)) {
            modalClosedMs = clock()
        }
        // fine-tune rests: a drag ended or a key was stepped -> 0.6 s; the pin's tag closed or another pin took over -> now
        val tuned = before.armedPinId
        if (tuned != null) {
            if (after.armedPinId != tuned || (before.tuneOpen && !after.tuneOpen)) {
                rebaseJob?.cancel()
                if (action !is RulerAction.RebaseDue) dispatch(RulerAction.RebaseDue(tuned))
            } else if (action is RulerAction.Nudge) {
                scheduleRebase(tuned)
            }
        }
        publish()
    }

    private fun perform(effect: RulerEffect) {
        when (effect) {
            is RulerEffect.Log -> logger.log(effect.line)
            is RulerEffect.Feedback -> _effects.tryEmit(RulerUiEffect.Feedback(effect.kind, pointerRight))
            is RulerEffect.CreateAnchor -> createAnchor(effect)
            is RulerEffect.RebaseAnchor -> rebaseAnchor(effect)
            is RulerEffect.RemoveAnchor ->
                viewModelScope.launch {
                    val outcome = anchors.remove(effect.anchorId)
                    logger.log("ANCHOR removed uuid=${effect.anchorId} ok=${outcome is AnchorOutcome.Ok}")
                }
            // orphans found at launch: one batch, after the launch window, one at a time, so that the scene and the planes come first
            is RulerEffect.RemoveOrphans ->
                viewModelScope.launch {
                    delay(ORPHAN_CLEANUP_DELAY_MS)
                    for (id in effect.anchorIds) {
                        val outcome = anchors.remove(id)
                        logger.log("ANCHOR removed uuid=$id ok=${outcome is AnchorOutcome.Ok} orphan=true")
                        yield()
                    }
                }
            RulerEffect.Persist -> persist(toStored(domain))
            RulerEffect.ExitApp ->
                viewModelScope.launch {
                    persistMutex.withLock { } // storage has been written
                    _effects.emit(RulerUiEffect.ExitApp)
                }
            RulerEffect.Restart -> {
                started = false
                sessionRestored = false
                plateSlots.clear()
                start()
            }
        }
    }

    private fun createAnchor(effect: RulerEffect.CreateAnchor) {
        viewModelScope.launch {
            val t0 = clock()
            val outcome =
                if (failNextAnchor) {
                    failNextAnchor = false
                    AnchorOutcome.Failed(-1, "injected by fail_next_anchor")
                } else {
                    anchors.create(effect.position, effect.pinId)
                }
            val ms = clock() - t0
            when (outcome) {
                is AnchorOutcome.Ok -> {
                    logger.log("ANCHOR saved id=${effect.pinId} uuid=${outcome.value.id} ms=$ms pos=${ReduceRulerUseCase.fmt(outcome.value.pose.position)}")
                    dispatch(RulerAction.AnchorSaved(effect.pinId, outcome.value.id, outcome.value.pose))
                }
                is AnchorOutcome.Failed -> {
                    logger.log("ANCHOR failed id=${effect.pinId} uuid=none ms=$ms code=${outcome.code} msg=${outcome.message}")
                    dispatch(RulerAction.AnchorFailed(effect.pinId))
                }
            }
        }
    }

    private fun rebaseAnchor(effect: RulerEffect.RebaseAnchor) {
        viewModelScope.launch {
            val t0 = clock()
            val outcome =
                if (failNextAnchor) {
                    failNextAnchor = false
                    AnchorOutcome.Failed(-1, "injected by fail_next_anchor")
                } else {
                    anchors.create(effect.position, effect.pinId)
                }
            val ms = clock() - t0
            when (outcome) {
                is AnchorOutcome.Ok -> dispatch(RulerAction.AnchorRebased(effect.pinId, outcome.value.id, outcome.value.pose))
                is AnchorOutcome.Failed -> {
                    logger.log("ANCHOR rebase failed id=${effect.pinId} uuid_old=${effect.oldAnchorId} ms=$ms code=${outcome.code} msg=${outcome.message}")
                    dispatch(RulerAction.AnchorFailed(effect.pinId))
                }
            }
        }
    }

    private fun persist(session: StoredSession) {
        viewModelScope.launch { persistMutex.withLock { repository.save(session) } }
    }

    private fun toStored(s: RulerState): StoredSession {
        if (!sessionRestored) {
            // pins and tapes have not been read yet: only the preferences may change in storage
            return (storedAtLaunch ?: StoredSession()).copy(settings = s.settings)
        }
        val pins =
            s.pins
                .filter { it.anchorId != null && (it.saveState == SaveState.SAVED || it.elsewhere) }
                .map { p ->
                    StoredPin(
                        id = p.id,
                        anchorId = p.anchorId!!,
                        normalLocal = p.normalLocal,
                        uLocal = p.uLocal,
                        vLocal = p.vLocal,
                        offsetUMm = p.offsetUMm,
                        offsetVMm = p.offsetVMm,
                        surface = p.surface,
                        planeLabel = p.planeLabel,
                        grade = p.grade,
                        snapped = p.snapped,
                        far = p.far,
                        rangeM = p.rangeM,
                        pinch = p.pinch,
                    )
                }
        val ids = pins.map { it.id }.toSet()
        return StoredSession(
            pins = pins,
            tapes = s.tapes.filter { it.a in ids && it.b in ids },
            pinSeq = s.pinSeq,
            tapeSeq = s.tapeSeq,
            settings = s.settings,
        )
    }

    // ------------------------------------------------------------------ state -> UI

    private fun publish() {
        val s = domain
        val name = s.viewStateName
        if (name != lastViewState) {
            lastViewState = name
            logger.log("STATE $name")
        }
        val snack = s.snack
        if ((snack?.id ?: -1) != snackTimerFor) {
            snackJob?.cancel()
            snackTimerFor = snack?.id ?: -1
            snackJob = null
            if (snack != null) {
                val sticky = snack.kind.durationMs == SnackKind.STICKY
                val duration = if (longSnacks && !sticky) LONG_SNACK_MS else snack.kind.durationMs
                logger.log("SNACK ${snack.kind.tag} id=${snack.id} ms=${if (sticky) "sticky" else duration.toString()}")
                snackEndsAtMs = if (sticky) Long.MAX_VALUE else clock() + duration
                if (!sticky) {
                    snackJob =
                        viewModelScope.launch {
                            delay(duration)
                            dispatch(RulerAction.SnackDismissed(snack.id))
                        }
                }
            }
        }
        _state.value = buildUi(s)
    }

    private fun buildUi(s: RulerState): RulerUiState {
        val unit = s.settings.unit
        val armed = s.armedPin
        val working = s.phase == Phase.READY
        val explicit = armed != null && s.armedExplicit && working && s.pull == null
        val visibleTapes = s.visibleTapes

        plateSlots.keys.retainAll(visibleTapes.map { it.id }.toSet())
        for (t in visibleTapes) {
            if (t.id in plateSlots) continue
            val used = plateSlots.values.toSet()
            val free = (0 until PLATE_SLOTS).firstOrNull { it !in used } ?: continue
            plateSlots[t.id] = free
        }

        val plates = arrayOfNulls<PlateUi>(PLATE_SLOTS)
        val tapes =
            visibleTapes.map { t ->
                val a = s.pin(t.a)!!
                val b = s.pin(t.b)!!
                val tol = tolerance.tapeToleranceMm(a, b)
                val length = a.position.distanceTo(b.position).toDouble()
                val slot = plateSlots[t.id] ?: -1
                val selected = t.id == s.selectedTapeId
                if (slot >= 0) {
                    plates[slot] =
                        PlateUi(
                            tapeId = t.id,
                            reading = format.distance(length, unit, tol),
                            tolerance = format.tolerance(tol, unit),
                            toleranceMm = tol,
                            warn = a.warns || b.warns,
                            unsaved = a.saveState == SaveState.UNSAVED || b.saveState == SaveState.UNSAVED,
                            selected = selected,
                            frozen = s.trackingLost,
                            dimmed = explicit && !t.touches(armed!!.id),
                        )
                }
                TapeUi(t.id, t.a, t.b, selected, dimmed = explicit && !t.touches(armed!!.id), plateSlot = slot)
            }

        val chips = arrayOfNulls<ChipUi>(CHIP_SLOTS)
        if (explicit && !s.trackingLost) {
            s.candidatesFor(armed!!).forEachIndexed { i, q ->
                if (i < CHIP_SLOTS) {
                    chips[i] = ChipUi(q.id, format.distance(q.position.distanceTo(armed.position).toDouble(), unit, 0).text)
                }
            }
        }

        val pinTag =
            if (!explicit) null
            else
                PinTagUi(
                    pinId = armed!!.id,
                    surface = armed.surface,
                    end = endOf(armed),
                    toleranceMm = tolerance.pinToleranceMm(armed),
                    tolerance = format.tolerance(tolerance.pinToleranceMm(armed), unit),
                    warn = armed.warns,
                    tuneOpen = s.tuneOpen,
                    offsetU = format.offset(armed.offsetUMm),
                    offsetV = format.offset(armed.offsetVMm),
                    horizontalSurface = armed.onHorizontalSurface,
                )

        val selectedTape = visibleTapes.firstOrNull { it.id == s.selectedTapeId }
        val shownTape = selectedTape ?: visibleTapes.firstOrNull { it.id == s.lastTapeId }
        val tapeTag =
            selectedTape?.let { t ->
                val a = s.pin(t.a)!!
                val b = s.pin(t.b)!!
                val tol = tolerance.tapeToleranceMm(a, b)
                TapeTagUi(t.id, format.distance(a.position.distanceTo(b.position).toDouble(), unit, 0), format.tolerance(tol, unit), endOf(a), endOf(b), warn = a.warns || b.warns)
            }

        fun reading(tapeId: String?, context: LcdContext): LcdUi? {
            val t = visibleTapes.firstOrNull { it.id == tapeId } ?: return null
            val a = s.pin(t.a)!!
            val b = s.pin(t.b)!!
            val tol = tolerance.tapeToleranceMm(a, b)
            return LcdUi(context, tolerance = format.tolerance(tol, unit), reading = format.distance(a.position.distanceTo(b.position).toDouble(), unit, tol), busy = false)
        }

        val lcd =
            when {
                !s.settingsKnown -> LcdUi(LcdContext.BLANK, busy = true)
                s.phase == Phase.STARTING -> LcdUi(LcdContext.STARTING, busy = true)
                // blocked: no spinner, the dialog says why (F15)
                s.phase == Phase.BLOCKED -> LcdUi(LcdContext.BLOCKED, busy = false)
                s.trackingLost -> reading(shownTape?.id, LcdContext.LOST) ?: LcdUi(LcdContext.LOST, busy = false)
                s.pull != null -> LcdUi(LcdContext.PULLING, busy = false, live = true)
                // fine-tune is open: the tape that shows what the move does is the newest one on this pin
                explicit && s.tuneOpen ->
                    (reading(s.referenceTape(armed!!.id)?.id, LcdContext.TUNING) ?: LcdUi(LcdContext.TUNING, busy = false)).copy(live = s.pinDrag != null)
                armed != null && working -> LcdUi(LcdContext.ARMED, busy = false)
                shownTape != null -> reading(shownTape.id, if (selectedTape != null) LcdContext.SELECTED else LcdContext.LAST)!!
                s.scanning -> LcdUi(LcdContext.SCANNING, planeCount = s.planeCount, busy = true)
                else -> LcdUi(LcdContext.READY, busy = s.pendingTap != null)
            }

        return RulerUiState(
            viewState = s.viewStateName,
            phase = s.phase,
            settingsKnown = s.settingsKnown,
            unit = unit,
            language = s.settings.language,
            snapEnabled = s.settings.snap,
            canUndo = working && s.history.isNotEmpty() && s.pull == null,
            canClear = working && s.pins.isNotEmpty(),
            lcd = lcd,
            pins = s.visiblePins.map { PinUi(it.id, it.position, it.normal, if (it.rebasing) SaveState.PENDING else it.saveState, it.id == s.armedPinId, it.seat) },
            tapes = tapes,
            plates = plates.toList(),
            chips = chips.toList(),
            pinTag = pinTag,
            tapeTag = tapeTag,
            menu = if (s.phase == Phase.READY || s.phase == Phase.STARTING || s.phase == Phase.RESTORING) s.menu else MenuState.CLOSED,
            clearDialog = if (s.dialog == DialogState.CONFIRM_CLEAR) ClearDialogUi(s.pins.size, s.tapes.size) else null,
            blocked = s.phase == Phase.BLOCKED,
            snack = s.snack?.let { SnackUi(it.id, it.kind, it.countA, it.countB) },
            planeCount = s.planeCount,
            scanning = s.scanning,
            pullHead = s.pull?.headPinId,
            trackingLost = s.trackingLost,
            pinDragging = s.pinDrag != null,
            pendingTap = s.pendingTap != null,
            coachStep = coachStep(s),
            headSigmaMm = (s.pull?.let { s.pin(it.headPinId) } ?: s.armedPin)?.let { tolerance.pinSigmaMm(it) },
        )
    }

    private fun endOf(pin: Pin) = EndUi(pin.grade, pin.snapped, pin.far, pin.saveState)

    // ------------------------------------------------------------------ test hooks

    /** The whole model as one JSON line (debug `dump`). */
    fun dumpJson(): String {
        val s = domain
        val root = JSONObject()
        root.put("state", s.viewStateName)
        root.put("phase", s.phase.name)
        root.put("planes", s.planeCount)
        root.put("scanning", s.scanning)
        root.put("pendingTap", s.pendingTap != null)
        root.put("unit", s.settings.unit.tag)
        root.put("lang", s.settings.language.tag)
        root.put("snap", s.settings.snap)
        root.put("settings_known", s.settingsKnown)
        root.put("armed", s.armedPinId ?: JSONObject.NULL)
        root.put("armedExplicit", s.armedExplicit)
        root.put("tune", s.tuneOpen)
        root.put("drag", s.pinDrag ?: JSONObject.NULL)
        root.put("pull", s.pull?.headPinId ?: JSONObject.NULL)
        root.put("tracking_lost", s.trackingLost)
        root.put("selectedTape", s.selectedTapeId ?: JSONObject.NULL)
        root.put("lastTape", s.lastTapeId ?: JSONObject.NULL)
        root.put("history", s.history.size)
        root.put("menu", s.menu.name.lowercase())
        root.put("dialog", s.dialog.name.lowercase())
        root.put("snack", s.snack?.kind?.tag ?: JSONObject.NULL)
        val snack = s.snack
        if (snack != null) {
            val sticky = snack.kind.durationMs == SnackKind.STICKY
            root.put("snack_id", snack.id)
            root.put("snack_counts", JSONArray().put(snack.countA).put(snack.countB))
            root.put("snack_remaining_ms", if (sticky) -1L else (snackEndsAtMs - clock()).coerceAtLeast(0L))
        }
        val pins = JSONArray()
        for (p in s.pins) {
            val pos = p.position
            pins.put(
                JSONObject()
                    .put("id", p.id)
                    .put("uuid", p.anchorId ?: JSONObject.NULL)
                    .put("pos", JSONArray().put(r4(pos.x)).put(r4(pos.y)).put(r4(pos.z)))
                    .put("normal", JSONArray().put(r4(p.normal.x)).put(r4(p.normal.y)).put(r4(p.normal.z)))
                    .put("grade", p.grade.tag)
                    .put("snapped", p.snapped.tag)
                    .put("far", p.far)
                    .put("range_m", r4(p.rangeM))
                    .put("sigma_mm", r4(tolerance.pinSigmaMm(p)))
                    .put("tol_mm", tolerance.pinToleranceMm(p))
                    .put("plane", p.planeLabel)
                    .put("surface", p.surface.name)
                    .put("save", p.saveState.tag)
                    .put("elsewhere", p.elsewhere)
                    .put("offset_mm", JSONArray().put(p.offsetUMm).put(p.offsetVMm))
            )
        }
        root.put("pins", pins)
        val tapes = JSONArray()
        for (t in s.tapes) {
            val a = s.pin(t.a) ?: continue
            val b = s.pin(t.b) ?: continue
            val tol = tolerance.tapeToleranceMm(a, b)
            val length = a.position.distanceTo(b.position)
            tapes.put(
                JSONObject()
                    .put("id", t.id)
                    .put("a", t.a)
                    .put("b", t.b)
                    .put("len_m", r4(length))
                    .put("tol_mm", tol)
                    .put("text", format.distance(length.toDouble(), s.settings.unit, tol).text)
                    .put("tol_text", format.tolerance(tol, s.settings.unit))
                    .put("plate_slot", plateSlots[t.id] ?: -1)
            )
        }
        root.put("tapes", tapes)
        return root.toString()
    }

    private fun r4(v: Float): Double = Math.round(v * 10000.0) / 10000.0

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RulerViewModel(
                repository = RulerGraph.repository(appContext),
                anchors = RulerGraph.anchors,
                environment = RulerGraph.environment,
                logger = RulerLog,
                defaults = RulerGraph::defaultSettings,
            )
                as T
    }

    /** First-run coachmark (one step): shows until the first tape is measured or ✓. */
    private fun coachStep(s: RulerState): Int =
        if (s.settings.coachSeen || !s.settingsKnown || s.phase != Phase.READY || s.trackingLost || s.dialog != DialogState.NONE) 0 else 1

    companion object {
        /** A pending tap is tried against the planes this often. */
        private const val PENDING_TAP_POLL_MS = 300L

        /** Orphan anchors found at launch are removed after this, off the launch window. */
        private const val ORPHAN_CLEANUP_DELAY_MS = 5_000L

        /** Restored pins rise and tapes re-form for this long before the count is announced. */
        const val RESTORE_SHOW_MS = 900L
        private const val LOAD_ATTEMPTS = 4
        private const val LOAD_RETRY_MS = 700L
        private const val AVAILABILITY_WAIT_MS = 2000L
        private const val AVAILABILITY_STEP_MS = 250L
        private const val LONG_SNACK_MS = 60_000L

        /** A scene tap this soon after a menu or dialog closed was the pinch that closed it. */
        const val MODAL_GRACE_MS = 400L

        /** Fine-tune has rested this long: the anchor is re-created when the offset is beyond 50 mm. */
        const val REBASE_REST_MS = 600L

        /** With more tapes than this, unselected plates only show the value. */
    }
}
