package io.embodit.spatialruler.ui.ruler

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.data.repository.AnchorRecord
import io.embodit.spatialruler.domain.model.AnchorPose
import io.embodit.spatialruler.domain.model.BlockReason
import io.embodit.spatialruler.domain.model.FeedbackKind
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.domain.model.MenuState
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.Phase
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.RulerSettings
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SnackKind
import io.embodit.spatialruler.domain.model.Vec3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RulerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scheduler = dispatcher.scheduler
    private val repository = FakeSessionRepository()
    private val anchors = FakeAnchorGateway()
    private val environment = FakeEnvironment()
    private val logger = RecordingLogger()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(defaults: RulerSettings = RulerSettings()): RulerViewModel =
        RulerViewModel(repository, anchors, environment, logger, { defaults }, clock = { scheduler.currentTime })

    @Test
    fun tapBeforeThePlanesArrive_waitsAsThePendingAim_andLandsAsAPinWhenTheyArrive() {
        val room = FakeEnvironment(planes = emptyList())
        val vm = RulerViewModel(repository, anchors, room, logger, { RulerSettings() }, clock = { scheduler.currentTime })
        vm.onEvent(RulerEvent.SceneReady)
        scheduler.runCurrent()
        vm.onEvent(RulerEvent.PlanesChanged(0, scanning = true))
        val ray = PointerRay(Room.HEAD, (Vec3(0f, 0f, -1.2f) - Room.HEAD).normalized())
        vm.onEvent(RulerEvent.UnmappedTapped(ray))
        scheduler.runCurrent()
        assertTrue(vm.state.value.pendingTap)
        assertTrue(vm.state.value.pins.isEmpty())
        assertTrue(vm.state.value.lcd.busy)
        assertTrue(logger.lines.none { it.startsWith("PIN add") })
        // 1.4 s later the floor is recognised: the pin lands where the line of sight meets it
        scheduler.advanceTimeBy(1_400)
        scheduler.runCurrent()
        room.add(Room.floor)
        vm.onEvent(RulerEvent.PlanesChanged(1, scanning = false))
        scheduler.runCurrent()
        assertFalse(vm.state.value.pendingTap)
        assertEquals(1, vm.state.value.pins.size)
        assertTrue(logger.lines.any { it.startsWith("PIN add id=p1") && it.contains("via=system") && it.endsWith("deferred=true") })
        assertEquals("pulling", vm.state.value.viewState)
    }

    @Test
    fun pendingTap_isDroppedAfter15s_whenNoSurfaceComes() {
        val room = FakeEnvironment(planes = emptyList())
        val vm = RulerViewModel(repository, anchors, room, logger, { RulerSettings() }, clock = { scheduler.currentTime })
        vm.onEvent(RulerEvent.SceneReady)
        scheduler.runCurrent()
        vm.onEvent(RulerEvent.PlanesChanged(0, scanning = true))
        vm.onEvent(RulerEvent.UnmappedTapped(PointerRay(Room.HEAD, (Vec3(0f, 0f, -1.2f) - Room.HEAD).normalized())))
        scheduler.runCurrent()
        assertTrue(vm.state.value.pendingTap)
        scheduler.advanceTimeBy(15_500)
        scheduler.runCurrent()
        assertFalse(vm.state.value.pendingTap)
        assertTrue(vm.state.value.pins.isEmpty())
        assertTrue(logger.lines.any { it.startsWith("TAP pending expired") })
    }

    /** A ViewModel whose Stage is up and whose room has been scanned. */
    private fun readyViewModel(): RulerViewModel {
        val vm = newViewModel()
        vm.onEvent(RulerEvent.SceneReady)
        scheduler.runCurrent()
        vm.onEvent(RulerEvent.PlanesChanged(Room.all.size, scanning = false))
        return vm
    }

    private fun RulerViewModel.send(vararg events: RulerEvent) {
        for (event in events) {
            onEvent(event)
            scheduler.runCurrent()
        }
    }

    @Test
    fun launch_goesFromStartingToScanningToIdle_andNothingBlocks() {
        val vm = newViewModel(RulerSettings(unit = RulerUnit.FT_IN))
        assertEquals("starting", vm.state.value.viewState)
        // nothing has been read yet: the body panel only shows its spinner
        assertEquals(LcdContext.BLANK, vm.state.value.lcd.context)
        assertFalse(vm.state.value.settingsKnown)
        scheduler.runCurrent()
        assertTrue(vm.state.value.settingsKnown)
        assertEquals(LcdContext.STARTING, vm.state.value.lcd.context)
        assertEquals(RulerUnit.FT_IN, vm.state.value.unit) // known before the scene exists
        vm.send(RulerEvent.SceneReady)
        assertEquals("idle-scanning", vm.state.value.viewState)
        assertEquals(RulerUnit.FT_IN, vm.state.value.unit) // first-launch default
        vm.send(RulerEvent.PlanesChanged(3, scanning = true))
        assertEquals(LcdUi(LcdContext.SCANNING, planeCount = 3, busy = true), vm.state.value.lcd)
        // a pin can be set while the room is still being scanned
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        assertEquals(1, vm.state.value.pins.size)
        vm.send(RulerEvent.PlanesChanged(9, scanning = false), RulerEvent.PinTapped("p1"))
        assertEquals("idle", vm.state.value.viewState)
        assertEquals(LcdContext.READY, vm.state.value.lcd.context)
        assertEquals(listOf("STATE starting", "STATE idle-scanning", "STATE pulling", "STATE idle"), logger.lines.filter { it.startsWith("STATE") })
    }

    @Test
    fun tapOnASurface_setsAPinAtTheTapPoint_andItsAnchorIsSaved() {
        val vm = readyViewModel()
        vm.onEvent(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0.5f, 0.003f, -2f)))
        // before the anchor result arrives the pin is "pinning"
        val pending = vm.state.value.pins.single()
        assertEquals(SaveState.PENDING, pending.saveState)
        assertTrue(pending.armed)
        assertNear(Vec3(0.501002f, 0f, -2.004008f), pending.position, 1e-4f)
        assertEquals("pulling", vm.state.value.viewState)
        assertEquals(LcdContext.ARMED, vm.state.value.lcd.context)
        scheduler.runCurrent()
        val saved = vm.state.value.pins.single()
        assertEquals(SaveState.SAVED, saved.saveState)
        assertEquals(1, anchors.store.size)
        assertNear(pending.position, anchors.store.values.single().pose.position, 0f)
        assertTrue(logger.lines.any { it.startsWith("PIN add id=p1 pos=(0.5010, 0.0000, -2.0040) plane=FLOOR:f1000000 grade=coarse") })
        assertTrue(logger.lines.any { it.startsWith("ANCHOR saved id=p1 uuid=uuid-1 ms=") })
        assertEquals(listOf("p1"), repository.stored!!.pins.map { it.id })
    }

    @Test
    fun twoPins_makeATape_withPlateBodyReadoutAndTolerance() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)))
        val state = vm.state.value
        assertEquals(listOf(TapeUi("t1", "p1", "p2", selected = false, dimmed = false, plateSlot = 0)), state.tapes)
        val plate = state.plates[0]!!
        assertEquals("1.000 m", plate.reading.text)
        // coarse at 1.80 m and 2.06 m: 15.2 and 16.2 mm -> 22.3 -> 30 mm; the last digit fades
        assertEquals(30, plate.toleranceMm)
        assertEquals("±3 cm", plate.tolerance)
        assertTrue(plate.reading.fadeLast)
        assertEquals(LcdContext.LAST, state.lcd.context)
        assertEquals("1.000 m", state.lcd.reading!!.text)
        assertEquals("±3 cm", state.lcd.tolerance)
        assertEquals("idle", state.viewState)
        assertTrue(state.canUndo)
        assertTrue(logger.lines.any { it == "TAPE add id=t1 a=p1 b=p2 len_m=1.0000 tol_mm=30" })
    }

    @Test
    fun unit_reformatsEveryReading() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)))
        vm.send(RulerEvent.UnitSelected(RulerUnit.CM))
        assertEquals("100.0 cm", vm.state.value.plates[0]!!.reading.text)
        vm.send(RulerEvent.UnitSelected(RulerUnit.MM))
        assertEquals("1000 mm", vm.state.value.lcd.reading!!.text)
        vm.send(RulerEvent.UnitSelected(RulerUnit.FT_IN))
        assertEquals("3′ 3.4″", vm.state.value.plates[0]!!.reading.text)
        assertEquals("±1.2″", vm.state.value.plates[0]!!.tolerance)
        assertEquals(RulerUnit.FT_IN, repository.stored!!.settings.unit)
        assertTrue(logger.lines.containsAll(listOf("UI unit_cm", "UNIT cm", "UNIT mm", "UNIT ftin")))
    }

    @Test
    fun selectingAPin_showsItsTagAndCandidateChips_andAChipPullsTheTape() {
        val vm = readyViewModel()
        vm.send(
            RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)),
            RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)),
            RulerEvent.DebugPlace(Vec3(0f, 0f, -3f)),
            RulerEvent.PinTapped("p3"), // p3 was the head: cancel
            RulerEvent.PinTapped("p1"),
        )
        val state = vm.state.value
        assertEquals("pin-selected-tuning", state.viewState)
        val tag = state.pinTag!!
        assertEquals("p1", tag.pinId)
        assertTrue(tag.tuneOpen) // the steppers are always there (round 5)
        assertEquals(listOf("p3"), state.chips.filterNotNull().map { it.pinId })
        assertEquals("2.000 m", state.chips[0]!!.text)
        assertFalse(state.tapes.single().dimmed) // t1 touches the selected pin
        vm.send(RulerEvent.ChipClicked("p3"))
        assertEquals(listOf("t1", "t2"), vm.state.value.tapes.map { it.id })
        assertNull(vm.state.value.pinTag)
        assertEquals(listOf(0, 1), vm.state.value.tapes.map { it.plateSlot })
    }

    @Test
    fun fineTune_stepsMoveThePinAndTheReading_andUndoTakesThemBack() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)), RulerEvent.PinTapped("p2"))
        assertEquals("pin-selected-tuning", vm.state.value.viewState)
        repeat(3) { vm.send(RulerEvent.Stepped(NudgeAxis.U, 1)) }
        assertEquals("+3", vm.state.value.pinTag!!.offsetU)
        // "right" for a viewer at the origin looking at (1, 0, -1) is the diagonal (1, 0, 1) / sqrt 2:
        // 3 mm along it lengthen the tape from (0, 0, -1) by 2.1 mm
        assertEquals("1.002 m", vm.state.value.plates[0]!!.reading.text)
        assertTrue(logger.lines.count { it == "UI step_u_plus" } == 3)
        vm.send(RulerEvent.UndoClicked)
        assertEquals("1.000 m", vm.state.value.plates[0]!!.reading.text)
    }

    @Test
    fun anchorFailure_leavesAnUnsavedPin_withARetrySnackbar() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugFailNextAnchor, RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)))
        assertEquals(SaveState.UNSAVED, vm.state.value.pins.single().saveState)
        val snack = vm.state.value.snack!!
        assertEquals(SnackKind.UNSAVED, snack.kind)
        assertTrue(anchors.store.isEmpty())
        assertTrue(logger.lines.any { it.startsWith("ANCHOR failed id=p1 uuid=none ms=") })
        assertTrue(repository.stored!!.pins.isEmpty()) // an unsaved pin is not stored
        vm.send(RulerEvent.SnackActionClicked(snack.id))
        assertEquals(SaveState.SAVED, vm.state.value.pins.single().saveState)
        assertEquals(SnackKind.SAVED, vm.state.value.snack!!.kind)
        assertEquals(1, anchors.store.size)
        // messages go away by themselves
        scheduler.advanceTimeBy(SnackKind.SAVED.durationMs + 1)
        scheduler.runCurrent()
        assertNull(vm.state.value.snack)
    }

    @Test
    fun relaunch_restoresPinsAndTapesFromTheAnchorStore_andReportsTheCounts() {
        val first = readyViewModel()
        first.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)), RulerEvent.UnitSelected(RulerUnit.CM), RulerEvent.LanguageSelected(LanguagePref.ZH), RulerEvent.SnapToggled(false))
        assertEquals(2, repository.stored!!.pins.size)
        // the space re-localised a little while the app was closed
        val moved = anchors.store.values.last()
        anchors.store[moved.id] = AnchorRecord(moved.id, moved.name, AnchorPose(Vec3(1.004f, 0f, -1f)))

        val second = newViewModel()
        second.send(RulerEvent.SceneReady)
        assertEquals(Phase.RESTORING, second.state.value.phase)
        assertEquals("restoring", second.state.value.viewState)
        assertEquals(2, second.state.value.pins.size)
        assertEquals("100.4 cm", second.state.value.plates[0]!!.reading.text) // positions come from the anchors
        assertEquals(RulerUnit.CM, second.state.value.unit)
        assertEquals(LanguagePref.ZH, second.state.value.language)
        assertFalse(second.state.value.snapEnabled)
        assertNull(second.state.value.snack)
        scheduler.advanceTimeBy(RulerViewModel.RESTORE_SHOW_MS + 1)
        scheduler.runCurrent()
        assertEquals(Phase.READY, second.state.value.phase)
        assertEquals(SnackUi(1, SnackKind.RESTORED, countA = 2, countB = 1), second.state.value.snack)
        assertTrue(logger.lines.any { it == "RESTORE pins=2 tapes=1 elsewhere=0 orphans=0 stored=true" })
        assertEquals(2, anchors.store.size) // nothing was created twice
    }

    @Test
    fun anchorStoreThatIsNotReadyYet_isAskedAgain() {
        anchors.failNextLoads = 2
        val vm = newViewModel()
        vm.send(RulerEvent.SceneReady)
        assertEquals("starting", vm.state.value.viewState)
        scheduler.advanceTimeBy(2000)
        scheduler.runCurrent()
        assertEquals("idle-scanning", vm.state.value.viewState)
        assertEquals(3, anchors.loads)
        assertEquals(2, logger.lines.count { it.startsWith("ANCHOR load failed code=-8") })
    }

    @Test
    fun clearAll_needsConfirmation_andEmptiesTheAnchorStore() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)))
        vm.send(RulerEvent.MoreClicked)
        assertEquals(MenuState.MORE, vm.state.value.menu)
        assertTrue(vm.state.value.canClear)
        vm.send(RulerEvent.ClearAllClicked)
        assertEquals(ClearDialogUi(pins = 2, tapes = 1), vm.state.value.clearDialog)
        assertEquals("confirm-clear", vm.state.value.viewState)
        vm.send(RulerEvent.DialogDismissed)
        assertEquals(2, anchors.store.size)
        vm.send(RulerEvent.ClearAllClicked, RulerEvent.ClearConfirmed)
        assertTrue(vm.state.value.pins.isEmpty())
        assertTrue(vm.state.value.plates.all { it == null })
        assertTrue(anchors.store.isEmpty())
        assertTrue(repository.stored!!.pins.isEmpty())
        assertFalse(vm.state.value.canUndo)
        assertEquals(SnackKind.CLEARED, vm.state.value.snack!!.kind)
    }

    @Test
    fun syntheticPointer_goesThroughTheSameAimingCode_andSteadyAimIsFine() {
        val vm = readyViewModel()
        val ray = PointerRay(Room.HEAD, (Vec3(0.5f, 0f, -2f) - Room.HEAD).normalized())
        vm.send(RulerEvent.PointerTriggered(ray, Grade.FINE, "synthetic"))
        val json = JSONObject(vm.dumpJson())
        val pin = json.getJSONArray("pins").getJSONObject(0)
        assertEquals("fine", pin.getString("grade"))
        assertEquals("saved", pin.getString("save"))
        assertEquals("FLOOR:f1000000", pin.getString("plane"))
        assertEquals(0.5, pin.getJSONArray("pos").getDouble(0), 1e-3)
        assertEquals("pulling", json.getString("state"))
        assertEquals("m", json.getString("unit"))
        // pointing at the pin again selects it instead of setting a second one
        vm.send(RulerEvent.PointerTriggered(PointerRay(Room.HEAD, (Vec3(0.5f, 0.018f, -2f) - Room.HEAD).normalized()), Grade.COARSE, "synthetic"))
        assertEquals(1, vm.state.value.pins.size)
        // into the void: a message, no pin
        vm.send(RulerEvent.PointerTriggered(PointerRay(Room.HEAD, Vec3.UP), Grade.COARSE, "synthetic"))
        assertEquals(SnackKind.NO_SURFACE, vm.state.value.snack!!.kind)
    }

    @Test
    fun exit_writesStorageFirst_thenAsksTheScreenToCloseTheStage() {
        val vm = readyViewModel()
        val seen = ArrayList<RulerUiEffect>()
        val collector = CoroutineScope(dispatcher)
        collector.launch { vm.effects.collect { seen += it } }
        scheduler.runCurrent()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)))
        val savesBefore = repository.saves
        vm.send(RulerEvent.MoreClicked, RulerEvent.ExitClicked)
        // (the pin that was set before also asked for its sound and touch)
        assertEquals(listOf<RulerUiEffect>(RulerUiEffect.ExitApp), seen.filter { it !is RulerUiEffect.Feedback })
        assertTrue(seen.any { it == RulerUiEffect.Feedback(FeedbackKind.PIN_DROP, right = true) })
        assertTrue(repository.saves > savesBefore)
        assertEquals("exited", vm.state.value.viewState)
        assertNotNull(repository.stored)
        collector.cancel()
    }

    @Test
    fun relocalisedAnchor_movesItsPin_whileTheAppRuns() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)))
        val record = anchors.store.values.last()
        anchors.moves.tryEmit(AnchorRecord(record.id, record.name, AnchorPose(Vec3(1.25f, 0f, -1f))))
        scheduler.runCurrent()
        assertEquals("1.250 m", vm.state.value.plates[0]!!.reading.text)
    }

    // ------------------------------------------------------------------ start-up

    @Test
    fun storedPreferences_areKnownBeforeTheSceneExists_andChangingThemThenLeavesTheStoredPinsAlone() {
        val first = readyViewModel()
        first.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)), RulerEvent.UnitSelected(RulerUnit.CM), RulerEvent.LanguageSelected(LanguagePref.ZH))
        val second = newViewModel()
        scheduler.runCurrent() // storage has been read; the scene is still being built
        val starting = second.state.value
        assertEquals("starting", starting.viewState)
        assertTrue(starting.settingsKnown)
        assertEquals(RulerUnit.CM, starting.unit)
        assertEquals(LanguagePref.ZH, starting.language)
        assertEquals(LcdContext.STARTING, starting.lcd.context)
        assertTrue(starting.pins.isEmpty())
        assertFalse(starting.canUndo)
        // units and the menu work while it is getting ready; only the preferences change in storage
        second.send(RulerEvent.UnitSelected(RulerUnit.MM), RulerEvent.MoreClicked)
        assertEquals(RulerUnit.MM, repository.stored!!.settings.unit)
        assertEquals(2, repository.stored!!.pins.size)
        assertEquals(MenuState.MORE, second.state.value.menu)
        second.send(RulerEvent.SceneReady)
        assertEquals(2, second.state.value.pins.size)
        assertEquals(RulerUnit.MM, second.state.value.unit)
        assertEquals(LanguagePref.ZH, second.state.value.language)
    }

    @Test
    fun spatialFeaturesUnavailable_blockWithADialog_andRetryStartsAgain() {
        environment.blocked = BlockReason.WITHOUT_PERMISSION
        val vm = newViewModel()
        vm.send(RulerEvent.SceneReady)
        // the reason gets two seconds to go away by itself (the Stage may still be opening)
        assertEquals("starting", vm.state.value.viewState)
        scheduler.advanceTimeBy(2_100)
        scheduler.runCurrent()
        assertTrue(vm.state.value.blocked)
        assertEquals("blocked", vm.state.value.viewState)
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)), RulerEvent.UnitSelected(RulerUnit.MM))
        assertTrue(vm.state.value.pins.isEmpty())
        assertEquals(RulerUnit.M, vm.state.value.unit)
        environment.blocked = null
        vm.send(RulerEvent.BlockedRetryClicked)
        assertFalse(vm.state.value.blocked)
        assertEquals("idle-scanning", vm.state.value.viewState)
        assertTrue(logger.lines.containsAll(listOf("BLOCKED reason=without_permission", "UI blocked_retry")))
    }

    @Test
    fun trackedPointerCommit_setsThePinWithItsGradeAndSaysWhichSourceSetIt() {
        val vm = readyViewModel()
        val ray = PointerRay(Room.HEAD, (Vec3(0.5f, 0f, -2f) - Room.HEAD).normalized())
        vm.send(RulerEvent.PointerTriggered(ray, Grade.FINE, "controller"))
        assertTrue(logger.lines.any { it.startsWith("PIN add id=p1") && it.contains("grade=fine") && it.endsWith("via=controller") })
        // a system tap says so too
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(1f, 0.003f, -2f)))
        assertTrue(logger.lines.any { it.startsWith("PIN add id=p2") && it.endsWith("via=system") })
    }

    @Test
    fun contactCommit_takesTheToolTipItself_andOnlySnapsToAPin() {
        val vm = readyViewModel()
        // the tip is 7 mm above the fitted floor: the pin is where the tip is, not on the plane
        vm.send(RulerEvent.ContactTriggered(Vec3(0.3f, 0.007f, -0.6f), pinch = false))
        val pin = vm.state.value.pins.single()
        assertNear(Vec3(0.3f, 0.007f, -0.6f), pin.position, 1e-6f)
        assertTrue(logger.lines.any { it.startsWith("PIN add id=p1") && it.contains("grade=contact") && it.endsWith("via=contact") })
        // a second touch within the pin's reach is that pin (the head is touched again: cancelled), not a new one
        vm.send(RulerEvent.ContactTriggered(Vec3(0.31f, 0.015f, -0.6f), pinch = false))
        assertEquals(1, vm.state.value.pins.size)
        // a tip that is in reach of no surface sets nothing
        vm.send(RulerEvent.ContactTriggered(Vec3(0.3f, 0.5f, -0.6f), pinch = true))
        assertEquals(1, vm.state.value.pins.size)
        // contact with the pinch point of a hand is coarser: 8 mm against 2 mm
        vm.send(RulerEvent.ContactTriggered(Vec3(1.3f, 0.002f, -0.6f), pinch = true))
        val json = JSONObject(vm.dumpJson()).getJSONArray("pins")
        assertEquals(2, json.getJSONObject(0).getInt("tol_mm"))
        assertEquals(8, json.getJSONObject(1).getInt("tol_mm"))
    }

    @Test
    fun stageThatIsStillOpening_isNotBlocked_whenFullSpaceArrivesWithinTwoSeconds() {
        // the provider says "not in full space" for the first second after the scene is ready
        environment.blocked = BlockReason.NOT_IN_FULL_SPACE
        environment.blockedAnswers = 4
        val vm = newViewModel()
        vm.send(RulerEvent.SceneReady)
        scheduler.advanceTimeBy(1_500)
        scheduler.runCurrent()
        assertFalse(vm.state.value.blocked)
        assertEquals("idle-scanning", vm.state.value.viewState)
        assertTrue(logger.lines.none { it.startsWith("BLOCKED") })
    }

    @Test
    fun debugHook_blocksARunningSession_andRetryBringsThePinsBack() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)))
        vm.send(RulerEvent.DebugBlocked(true))
        assertTrue(vm.state.value.blocked)
        assertTrue(vm.state.value.pins.isEmpty())
        vm.send(RulerEvent.DebugBlocked(false), RulerEvent.BlockedRetryClicked)
        assertEquals("restoring", vm.state.value.viewState)
        assertEquals(2, vm.state.value.pins.size)
        assertEquals(1, vm.state.value.tapes.size)
    }

    // ------------------------------------------------------------------ press-and-pull, truing

    @Test
    fun pressAndPull_setsTheHeadAtThePress_tellsWhereTheLinePoints_andSetsTheTailOnRelease() {
        val vm = readyViewModel()
        assertEquals(DragMode.NONE, vm.dragMode())
        vm.send(RulerEvent.DragStarted(Room.floor.id, null, Vec3(0f, 0.003f, -1f)))
        assertEquals("pulling-drag", vm.state.value.viewState)
        assertEquals("p1", vm.state.value.pullHead)
        assertEquals(DragMode.PULL, vm.dragMode())
        assertEquals(LcdContext.PULLING, vm.state.value.lcd.context)
        assertTrue(vm.state.value.lcd.live)
        assertFalse(vm.state.value.canUndo)
        // while the tape is under the hand the scene asks where the line points; asking changes nothing
        val ray = PointerRay(Room.HEAD, (Vec3(1f, 0f, -1f) - Room.HEAD).normalized())
        val preview = vm.preview(ray)
        assertNear(Vec3(1f, 0f, -1f), preview.point!!, 1e-4f)
        assertNull(preview.pinId)
        assertEquals(1, vm.state.value.pins.size)
        // the line never picks the pin it comes from, and may point at nothing
        assertNull(vm.preview(PointerRay(Room.HEAD, (Vec3(0f, 0.0145f, -1.002f) - Room.HEAD).normalized())).pinId)
        assertNull(vm.preview(PointerRay(Room.HEAD, Vec3.UP)).point)
        vm.send(RulerEvent.DragEnded(ray, steady = true, cancelled = false))
        assertEquals("idle", vm.state.value.viewState)
        assertEquals(DragMode.NONE, vm.dragMode())
        assertEquals(listOf(TapeUi("t1", "p1", "p2", selected = false, dimmed = false, plateSlot = 0)), vm.state.value.tapes)
        assertEquals("1.000 m", vm.state.value.plates[0]!!.reading.text)
        // the press is where the eyes were (coarse); a release with the hand still is a fine aim
        val pins = JSONObject(vm.dumpJson()).getJSONArray("pins")
        assertEquals("coarse", pins.getJSONObject(0).getString("grade"))
        assertEquals("fine", pins.getJSONObject(1).getString("grade"))
        assertTrue(logger.lines.containsAll(listOf("PULL start head=p1 new=true", "PULL end head=p1 tail=p2")))
        // the whole pull is one undo step
        vm.send(RulerEvent.UndoClicked)
        assertTrue(vm.state.value.pins.isEmpty())
        assertTrue(vm.state.value.tapes.isEmpty())
    }

    @Test
    fun pressAndPull_fromAPinToAnotherPin_andAPullTheSystemTakesAway() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.PinTapped("p1"), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)), RulerEvent.PinTapped("p2"))
        assertEquals("idle", vm.state.value.viewState)
        vm.send(RulerEvent.DragStarted(null, "p1", Vec3(0f, 0.02f, -1f)))
        assertEquals("p1", vm.state.value.pullHead)
        assertEquals(1, vm.state.value.pins.count { it.armed })
        val toP2 = PointerRay(Room.HEAD, (Vec3(1f, 0.0145f, -1f) - Room.HEAD).normalized())
        assertEquals("p2", vm.preview(toP2).pinId)
        vm.send(RulerEvent.DragEnded(toP2, steady = false, cancelled = false))
        assertEquals(listOf("t1"), vm.state.value.tapes.map { it.id })
        assertEquals(2, vm.state.value.pins.size)
        vm.send(RulerEvent.DragStarted(null, "p2", Vec3(1f, 0.02f, -1f)), RulerEvent.DragEnded(null, steady = false, cancelled = true))
        assertNull(vm.state.value.pullHead)
        assertEquals(1, vm.state.value.tapes.size)
        assertEquals("pulling", vm.state.value.viewState) // p2 stays the tape head
        assertTrue(logger.lines.any { it == "PULL cancel head=p2" })
    }

    /** A pointing line from the head to [to] (the way the scene reports where a drag was released). */
    private fun rayTo(to: Vec3): PointerRay = PointerRay(Room.HEAD, (to - Room.HEAD).normalized())

    @Test
    fun aDragOnAPin_movesIt_theReadoutAndPlateFollow_andOneUndoTakesItBack() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)), RulerEvent.PinTapped("p1"))
        // the readout shows the newest tape on this pin while it is selected
        assertEquals(LcdContext.TUNING, vm.state.value.lcd.context)
        assertEquals("1.000 m", vm.state.value.lcd.reading!!.text)
        assertEquals("±3 cm", vm.state.value.lcd.tolerance)
        vm.send(RulerEvent.PinDragStarted("p1"))
        assertEquals("pin-drag", vm.state.value.viewState)
        assertEquals(DragMode.PIN, vm.dragMode())
        assertTrue(vm.state.value.lcd.live) // the body counts the live length the scene writes
        assertNull(vm.state.value.pullHead)
        // released 20 mm along the floor towards the other pin, at the slowed gain
        vm.send(RulerEvent.PinDragEnded("p1", rayTo(Vec3(0.02f, 0f, -1f)), null, Grade.FINE, "system", "1:4", cancelled = false))
        assertEquals("pin-selected-tuning", vm.state.value.viewState)
        assertEquals(DragMode.NONE, vm.dragMode())
        assertEquals("+20", vm.state.value.pinTag!!.offsetU)
        assertEquals("0", vm.state.value.pinTag!!.offsetV)
        assertEquals("0.980 m", vm.state.value.plates[0]!!.reading.text)
        assertEquals(LcdContext.TUNING, vm.state.value.lcd.context)
        assertFalse(vm.state.value.lcd.live)
        assertEquals("0.980 m", vm.state.value.lcd.reading!!.text)
        assertEquals(Grade.FINE, vm.snapshot.pin("p1")!!.grade) // the pin now counts as finely aimed
        assertTrue(logger.lines.any { it.startsWith("PIN drag end id=p1") && it.contains("via=system gain=1:4 plane=same grade=fine") })
        // the drag is one undo step
        vm.send(RulerEvent.UndoClicked)
        assertEquals("1.000 m", vm.state.value.plates[0]!!.reading.text)
    }

    @Test
    fun aDragReleasedOverNothing_orCancelled_leavesThePin() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.PinTapped("p1"), RulerEvent.PinTapped("p1"))
        val steps = vm.snapshot.history.size
        vm.send(RulerEvent.PinDragStarted("p1"), RulerEvent.PinDragEnded("p1", null, null, Grade.COARSE, "system", "1:1", cancelled = true))
        assertEquals("pin-selected-tuning", vm.state.value.viewState)
        assertEquals("0", vm.state.value.pinTag!!.offsetU)
        assertEquals(steps, vm.snapshot.history.size)
        vm.send(RulerEvent.PinDragStarted("p1"), RulerEvent.PinDragEnded("p1", rayTo(Vec3(0f, 5f, -1f)), null, Grade.COARSE, "system", "1:1", cancelled = false))
        assertEquals("0", vm.state.value.pinTag!!.offsetU)
        assertEquals(steps, vm.snapshot.history.size)
    }

    @Test
    fun selectingAPinWithoutATape_showsNoReading() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.PinTapped("p1"), RulerEvent.PinTapped("p1"))
        assertEquals(LcdUi(LcdContext.TUNING, busy = false), vm.state.value.lcd)
        vm.send(RulerEvent.PinTagClosed)
        assertEquals(LcdContext.READY, vm.state.value.lcd.context)
    }

    // ------------------------------------------------------------------ limits and designed states

    @Test
    fun twentyTapes_fillThePlatePool_theNextOneIsRefusedWithAMessage() {
        val vm = readyViewModel()
        repeat(40) { i -> vm.send(RulerEvent.DebugPlace(Vec3(-1.9f + i * 0.09f, 0f, -1f - (i % 2) * 0.3f))) }
        assertEquals(20, vm.state.value.tapes.size)
        assertEquals(PLATE_SLOTS, vm.state.value.plates.count { it != null })
        assertEquals((0 until 20).toList(), vm.state.value.tapes.map { it.plateSlot })
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -3f)), RulerEvent.DebugPlace(Vec3(0.5f, 0f, -3f)))
        assertEquals(20, vm.state.value.tapes.size)
        assertEquals(42, vm.state.value.pins.size)
        assertEquals(SnackKind.TAPE_LIMIT, vm.state.value.snack!!.kind)
        assertEquals("p42", vm.state.value.pins.single { it.armed }.id)
        assertTrue(logger.lines.any { it == "TAPE refused reason=limit a=p41 b=p42 max=20" })
    }

    @Test
    fun lostTracking_freezesPlatesAndReadout_untilItIsBack() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)))
        vm.send(RulerEvent.TrackingChanged(true))
        assertEquals("tracking-lost", vm.state.value.viewState)
        assertTrue(vm.state.value.trackingLost)
        assertTrue(vm.state.value.plates[0]!!.frozen)
        assertEquals(LcdContext.LOST, vm.state.value.lcd.context)
        assertEquals("1.000 m", vm.state.value.lcd.reading!!.text)
        assertEquals(SnackKind.LOST, vm.state.value.snack!!.kind)
        // the message stands; nothing can be measured
        scheduler.advanceTimeBy(60_000)
        scheduler.runCurrent()
        assertEquals(SnackKind.LOST, vm.state.value.snack!!.kind)
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -2f)))
        assertEquals(2, vm.state.value.pins.size)
        vm.send(RulerEvent.TrackingChanged(false))
        assertEquals("idle", vm.state.value.viewState)
        assertFalse(vm.state.value.plates[0]!!.frozen)
        assertEquals(SnackKind.RECOVERED, vm.state.value.snack!!.kind)
        scheduler.advanceTimeBy(SnackKind.RECOVERED.durationMs + 1)
        scheduler.runCurrent()
        assertNull(vm.state.value.snack)
    }

    @Test
    fun pinsOfAnotherSpace_areHidden_andTheSnackbarOffersToClearThem() {
        val vm = readyViewModel()
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(1f, 0f, -1f)), RulerEvent.DebugPlace(Vec3(0f, 0f, -2f)), RulerEvent.PinTapped("p3"))
        vm.send(RulerEvent.DebugElsewhere(1))
        assertEquals(listOf("p1", "p2"), vm.state.value.pins.map { it.id })
        assertEquals(SnackUi(vm.state.value.snack!!.id, SnackKind.ELSEWHERE, countA = 1), vm.state.value.snack)
        vm.send(RulerEvent.SnackActionClicked(vm.state.value.snack!!.id))
        assertEquals(2, JSONObject(vm.dumpJson()).getJSONArray("pins").length())
    }

    @Test
    fun bodyKeyReachedThroughTheGuard_doesWhatThatKeyDoes() {
        val vm = readyViewModel()
        // snap is no longer a body key (round 4: it lives in the menu): the guard ignores it like any unknown name
        vm.send(RulerEvent.BodyKeyPressed("unit_cm"), RulerEvent.BodyKeyPressed("snap"), RulerEvent.BodyKeyPressed("undo"), RulerEvent.BodyKeyPressed("no_such_key"))
        assertEquals(RulerUnit.CM, vm.state.value.unit)
        assertTrue(vm.state.value.snapEnabled)
        assertTrue(logger.lines.contains("UI unit_cm"))
        assertFalse(logger.lines.contains("UI snap"))
        assertFalse(logger.lines.contains("UI undo")) // nothing to undo: the key is disabled
        vm.send(RulerEvent.BodyKeyPressed("more"))
        assertEquals(MenuState.MORE, vm.state.value.menu)
        vm.send(RulerEvent.DebugPlace(Vec3(0f, 0f, -1f)), RulerEvent.BodyKeyPressed("undo"))
        assertTrue(vm.state.value.pins.isEmpty())
    }

    @Test
    fun dump_tellsWhichSnackbarIsShowingAndForHowLong_andLongSnackbarsStayAMinute() {
        val vm = readyViewModel()
        vm.send(RulerEvent.NothingTapped)
        var json = JSONObject(vm.dumpJson())
        assertEquals("no_surface", json.getString("snack"))
        assertEquals(SnackKind.NO_SURFACE.durationMs, json.getLong("snack_remaining_ms"))
        scheduler.advanceTimeBy(1000)
        assertEquals(SnackKind.NO_SURFACE.durationMs - 1000, JSONObject(vm.dumpJson()).getLong("snack_remaining_ms"))
        scheduler.advanceTimeBy(2001)
        scheduler.runCurrent()
        assertNull(vm.state.value.snack)
        assertTrue(JSONObject(vm.dumpJson()).isNull("snack"))
        // the test switch
        scheduler.advanceTimeBy(5000)
        vm.send(RulerEvent.DebugLongSnacks(true), RulerEvent.NothingTapped)
        json = JSONObject(vm.dumpJson())
        assertEquals(60_000L, json.getLong("snack_remaining_ms"))
        scheduler.advanceTimeBy(30_000)
        scheduler.runCurrent()
        assertEquals(SnackKind.NO_SURFACE, vm.state.value.snack!!.kind)
        scheduler.advanceTimeBy(30_001)
        scheduler.runCurrent()
        assertNull(vm.state.value.snack)
        assertTrue(logger.lines.any { it == "SNACK no_surface id=2 ms=60000" })
        // sticky messages say so
        vm.send(RulerEvent.TrackingChanged(true))
        assertEquals(-1L, JSONObject(vm.dumpJson()).getLong("snack_remaining_ms"))
        assertTrue(JSONObject(vm.dumpJson()).getBoolean("tracking_lost"))
    }

    // ------------------------------------------------------------------ R2-4: anchor rebase after the fine-tune rests

    @Test
    fun dragBeyond50mm_recreatesTheAnchorAtRelease() {
        val vm = readyViewModel()
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        scheduler.advanceUntilIdle() // the anchor is created
        val old = vm.snapshot.pin("p1")!!.anchorId!!
        vm.send(RulerEvent.PinTapped("p1"), RulerEvent.PinTapped("p1"))
        // dragged 80 mm to the right: the release re-creates the anchor there
        vm.send(RulerEvent.PinDragStarted("p1"), RulerEvent.PinDragEnded("p1", rayTo(Vec3(0.08f, 0f, -1f)), null, Grade.COARSE, "system", "1:1", cancelled = false))
        val position = vm.snapshot.pin("p1")!!.position
        assertNear(Vec3(0.08f, 0f, -1f), position, 1e-4f)
        assertTrue(logger.lines.any { it.startsWith("ANCHOR rebase start id=p1 uuid_old=$old") && it.endsWith("reason=drag") })
        scheduler.advanceUntilIdle()
        val pin = vm.snapshot.pin("p1")!!
        assertFalse(pin.rebasing)
        assertNotEquals(old, pin.anchorId)
        assertEquals(0 to 0, pin.offsetUMm to pin.offsetVMm)
        assertNear(position, pin.position, 1e-6f)
        assertEquals(SaveState.SAVED, pin.saveState)
        assertEquals(SaveState.SAVED, vm.state.value.pins.single().saveState)
        assertNull(anchors.store[old]) // the old anchor is gone from the store
        assertNotNull(anchors.store[pin.anchorId!!])
        assertTrue(logger.lines.any { it.startsWith("ANCHOR rebase id=p1 uuid_old=$old uuid_new=${pin.anchorId}") })
    }

    @Test
    fun closingTheTag_rebasesAtOnce_andWithin50mmNothingHappens() {
        val vm = readyViewModel()
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        scheduler.advanceUntilIdle()
        val old = vm.snapshot.pin("p1")!!.anchorId!!
        vm.send(RulerEvent.PinTapped("p1"), RulerEvent.PinTapped("p1"))
        vm.send(RulerEvent.PinDragStarted("p1"), RulerEvent.PinDragEnded("p1", rayTo(Vec3(0.045f, 0f, -1f)), null, Grade.COARSE, "system", "1:1", cancelled = false))
        assertEquals(45, vm.snapshot.pin("p1")!!.offsetUMm)
        scheduler.advanceUntilIdle()
        assertEquals(old, vm.snapshot.pin("p1")!!.anchorId) // 45 mm: the anchor stays
        repeat(6) { vm.send(RulerEvent.Stepped(NudgeAxis.U, 1)) }
        assertEquals(51, vm.snapshot.pin("p1")!!.offsetUMm)
        vm.send(RulerEvent.PinTagClosed) // the tag closes before the 0.6 s are over: rebase now (the fake gateway answers at once)
        // (the tapped pin sits 2 mm nearer than the drag's target: dv = -2)
        assertTrue(logger.lines.any { it.startsWith("ANCHOR rebase start id=p1 uuid_old=$old du=51 dv=-2") && it.endsWith("reason=rest") })
        scheduler.advanceUntilIdle()
        assertNotEquals(old, vm.snapshot.pin("p1")!!.anchorId)
        assertEquals(0, vm.snapshot.pin("p1")!!.offsetUMm)
    }

    @Test
    fun undoAfterARebase_bringsThePinBackExactly_andTheTapeFollows() {
        val vm = readyViewModel()
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)), RulerEvent.SurfaceTapped(Room.floor.id, Vec3(1f, 0.003f, -1f)))
        scheduler.advanceUntilIdle()
        assertEquals(1, vm.snapshot.tapes.size)
        val origin = vm.snapshot.pin("p1")!!.position
        val length0 = vm.state.value.plates.filterNotNull().single().reading
        val first = vm.snapshot.pin("p1")!!.anchorId!!
        vm.send(RulerEvent.PinTapped("p1"))
        vm.send(RulerEvent.PinDragStarted("p1"), RulerEvent.PinDragEnded("p1", rayTo(Vec3(0.08f, 0f, -1.03f)), null, Grade.COARSE, "system", "1:1", cancelled = false))
        scheduler.advanceUntilIdle() // the release re-creates the anchor
        val rebased = vm.snapshot.pin("p1")!!
        assertNotEquals(first, rebased.anchorId)
        assertEquals(0 to 0, rebased.offsetUMm to rebased.offsetVMm)
        assertTrue(rebased.position.distanceTo(origin) > 0.08f)

        vm.send(RulerEvent.UndoClicked)
        scheduler.advanceUntilIdle()
        val back = vm.snapshot.pin("p1")!!
        assertNear(origin, back.position, 1e-6f) // exactly: the anchor was re-created at the undo step's position
        assertEquals(0 to 0, back.offsetUMm to back.offsetVMm)
        assertEquals(SaveState.SAVED, back.saveState)
        assertFalse(back.rebasing)
        assertNotEquals(rebased.anchorId, back.anchorId)
        assertNull(anchors.store[rebased.anchorId!!])
        assertNull(anchors.store[first])
        assertNotNull(anchors.store[back.anchorId!!])
        assertEquals(length0, vm.state.value.plates.filterNotNull().single().reading)
        assertTrue(logger.lines.any { it.startsWith("ANCHOR rebase start id=p1") && it.endsWith("reason=undo") })
    }

    // ------------------------------------------------------------------ R2-2: the pinch that closes a menu does nothing else

    @Test
    fun aSceneTapWhileTheMenuIsOpen_orRightAfterItClosed_isConsumed() {
        val vm = readyViewModel()
        vm.send(RulerEvent.MoreClicked)
        assertEquals(MenuState.MORE, vm.state.value.menu)
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        assertEquals(MenuState.CLOSED, vm.state.value.menu)
        assertTrue(vm.state.value.pins.isEmpty())
        assertTrue(logger.lines.any { it == "TAP consumed reason=menu-open event=surface" })
        // the system closed the menu first and the tap arrives 100 ms later: still the same pinch
        vm.send(RulerEvent.MoreClicked, RulerEvent.MenuDismissed)
        scheduler.advanceTimeBy(100)
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        assertTrue(vm.state.value.pins.isEmpty())
        assertTrue(logger.lines.any { it == "TAP consumed reason=menu-closing event=surface" })
        // after the grace a tap places
        scheduler.advanceTimeBy(RulerViewModel.MODAL_GRACE_MS)
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        assertEquals(1, vm.state.value.pins.size)
    }

    // ------------------------------------------------------------------ R2-1 / coachmark through the view model

    @Test
    fun sourceLost_showsTheSnack_andTheCoachmarkFollowsTheFirstPinAndTape() {
        val vm = readyViewModel()
        assertEquals(1, vm.state.value.coachStep)
        vm.send(RulerEvent.SourceChanged(lost = true))
        assertEquals(SnackKind.NO_SOURCE, vm.state.value.snack?.kind)
        vm.send(RulerEvent.SourceChanged(lost = false))
        assertNull(vm.state.value.snack)
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(0f, 0.003f, -1f)))
        assertEquals(1, vm.state.value.coachStep) // one step: it stays until the first tape
        vm.send(RulerEvent.SurfaceTapped(Room.floor.id, Vec3(1f, 0.003f, -1f)))
        assertEquals(1, vm.state.value.tapes.size)
        assertEquals(0, vm.state.value.coachStep)
        vm.send(RulerEvent.DebugCoachReset)
        assertEquals(1, vm.state.value.coachStep)
        vm.send(RulerEvent.CoachDismissed)
        assertEquals(0, vm.state.value.coachStep)
        assertTrue(logger.lines.any { it == "UI coach_got_it" })
    }
}
