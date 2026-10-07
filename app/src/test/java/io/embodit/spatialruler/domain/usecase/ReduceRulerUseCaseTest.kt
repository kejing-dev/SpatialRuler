package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.AnchorPose
import io.embodit.spatialruler.domain.model.BlockReason
import io.embodit.spatialruler.domain.model.DialogState
import io.embodit.spatialruler.domain.model.FeedbackKind
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.LanguagePref
import io.embodit.spatialruler.domain.model.MenuState
import io.embodit.spatialruler.domain.model.NudgeAxis
import io.embodit.spatialruler.domain.model.Phase
import io.embodit.spatialruler.domain.model.PendingTap
import io.embodit.spatialruler.domain.model.Placement
import io.embodit.spatialruler.domain.model.PullEnd
import io.embodit.spatialruler.domain.model.PullState
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.RulerAction
import io.embodit.spatialruler.domain.model.RulerEffect
import io.embodit.spatialruler.domain.model.RulerSettings
import io.embodit.spatialruler.domain.model.RulerState
import io.embodit.spatialruler.domain.model.RulerUnit
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SeatMark
import io.embodit.spatialruler.domain.model.SnackKind
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.StoredPin
import io.embodit.spatialruler.domain.model.StoredSession
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Tape
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.pin
import io.embodit.spatialruler.placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReduceRulerUseCaseTest {

    /** Drives the reducer like the ViewModel does, with a hand-moved clock. */
    private class Machine {
        private val reducer = ReduceRulerUseCase()
        var state = RulerState()
        val effects = ArrayList<RulerEffect>()
        var now = 10_000L
        private var anchorSeq = 0

        fun send(action: RulerAction, advanceMs: Long = 100): Machine {
            now += advanceMs
            val r = reducer.reduce(state, action, now)
            state = r.state
            effects += r.effects
            return this
        }

        fun ready(settings: RulerSettings = RulerSettings()): Machine = send(RulerAction.Restored(null, emptyMap(), settings))

        fun place(x: Float, y: Float, z: Float, grade: Grade = Grade.COARSE, snapped: SnapKind = SnapKind.NONE, rangeM: Float = 1f): Machine =
            send(RulerAction.PlacePin(placement(Vec3(x, y, z), grade = grade, snapped = snapped, rangeM = rangeM)))

        /** Answers every pending anchor request with success, like the platform would. */
        fun saveAnchors(): Machine {
            val pending = effects.filterIsInstance<RulerEffect.CreateAnchor>().filter { it.pinId in state.pendingCreates }
            for (request in pending.distinctBy { it.pinId }) {
                send(RulerAction.AnchorSaved(request.pinId, "anchor-${++anchorSeq}", AnchorPose(request.position)))
            }
            return this
        }

        fun clearEffects(): Machine {
            effects.clear()
            return this
        }

        val logs: List<String>
            get() = effects.filterIsInstance<RulerEffect.Log>().map { it.line }

        inline fun <reified T : RulerEffect> count(): Int = effects.count { it is T }
    }

    private fun readyMachine() = Machine().ready()

    // ------------------------------------------------------------------ launch and restore

    @Test
    fun firstLaunch_withoutStorage_isReadyWithTheDefaults_andDropsOrphanAnchors() {
        val m = Machine()
        assertEquals("starting", m.state.viewStateName)
        m.send(RulerAction.Restored(null, mapOf("stray" to AnchorPose(Vec3.ZERO)), RulerSettings(unit = RulerUnit.FT_IN)))
        assertEquals(Phase.READY, m.state.phase)
        assertEquals(RulerUnit.FT_IN, m.state.settings.unit)
        assertEquals("idle-scanning", m.state.viewStateName)
        assertTrue(m.effects.any { it is RulerEffect.RemoveOrphans && "stray" in it.anchorIds } || RulerEffect.RemoveAnchor("stray") in m.effects)
        assertTrue(m.logs.any { it == "RESTORE pins=0 tapes=0 elsewhere=0 orphans=1 stored=false" })
        m.send(RulerAction.PlanesChanged(12, scanning = false))
        assertEquals("idle", m.state.viewStateName)
    }

    @Test
    fun actionsBeforeTheStageIsReady_areIgnored() {
        val m = Machine().place(0f, 0f, -1f)
        assertTrue(m.state.pins.isEmpty())
        assertEquals(0, m.count<RulerEffect.CreateAnchor>())
        m.send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.CLOSED, m.state.menu)
    }

    private fun storedPin(id: String, anchor: String) =
        StoredPin(id, anchor, Vec3.UP, Vec3.RIGHT, Vec3.AHEAD, 0, 0, SurfaceKind.FLOOR, "FLOOR:f1000000", Grade.COARSE, SnapKind.NONE, false, 1.5f)

    @Test
    fun relaunch_restoresPinsFromTheirAnchors_thenAnnouncesTheCounts() {
        val stored =
            StoredSession(
                pins = listOf(storedPin("p1", "A"), storedPin("p2", "B"), storedPin("p3", "GONE")),
                tapes = listOf(Tape("t1", "p1", "p2"), Tape("t2", "p2", "p3")),
                pinSeq = 3,
                tapeSeq = 2,
                settings = RulerSettings(unit = RulerUnit.CM, language = LanguagePref.ZH, snap = false),
            )
        val anchors = mapOf("A" to AnchorPose(Vec3(0f, 0f, -1f)), "B" to AnchorPose(Vec3(1f, 0f, -1f)), "ORPHAN" to AnchorPose(Vec3.ZERO))
        val m = Machine().send(RulerAction.Restored(stored, anchors, RulerSettings()))
        assertEquals(Phase.RESTORING, m.state.phase)
        assertEquals("restoring", m.state.viewStateName)
        assertEquals(listOf("p1", "p2"), m.state.visiblePins.map { it.id })
        assertEquals(listOf("t1"), m.state.visibleTapes.map { it.id }) // t2 ends at a pin of another space
        assertNear(Vec3(1f, 0f, -1f), m.state.pin("p2")!!.position)
        assertEquals(SaveState.SAVED, m.state.pin("p1")!!.saveState)
        assertTrue(m.state.pin("p3")!!.elsewhere)
        assertEquals(RulerUnit.CM, m.state.settings.unit)
        assertEquals("t1", m.state.lastTapeId)
        assertTrue(m.effects.any { it is RulerEffect.RemoveOrphans && "ORPHAN" in it.anchorIds } || RulerEffect.RemoveAnchor("ORPHAN") in m.effects)
        assertEquals(0, m.count<RulerEffect.CreateAnchor>()) // restored pins already have their anchors
        assertTrue(m.logs.any { it == "RESTORE pins=2 tapes=1 elsewhere=1 orphans=1 stored=true" })

        m.send(RulerAction.RestoreShown)
        assertEquals(Phase.READY, m.state.phase)
        val snack = m.state.snack!!
        assertEquals(SnackKind.RESTORED, snack.kind)
        assertEquals(2, snack.countA)
        assertEquals(1, snack.countB)
        // the pins of another space are announced next, with "Clear them"
        m.send(RulerAction.SnackDismissed(snack.id))
        assertEquals(SnackKind.ELSEWHERE, m.state.snack!!.kind)
        assertEquals(1, m.state.snack!!.countA)
        m.send(RulerAction.SnackAction(m.state.snack!!.id))
        assertNull(m.state.pin("p3"))
        assertEquals(listOf("t1"), m.state.tapes.map { it.id })
        // new ids continue after the stored ones
        m.place(0f, 0f, -2f)
        assertNotNull(m.state.pin("p4"))
    }

    // ------------------------------------------------------------------ pairing rule

    @Test
    fun firstPin_becomesTheTapeHead_andAsksForItsAnchor() {
        val m = readyMachine().place(0f, 0f, -1f)
        val pin = m.state.pin("p1")!!
        assertEquals(SaveState.PENDING, pin.saveState)
        assertEquals("p1", m.state.armedPinId)
        assertFalse(m.state.armedExplicit)
        assertEquals("pulling", m.state.viewStateName)
        assertTrue(RulerEffect.CreateAnchor("p1", Vec3(0f, 0f, -1f)) in m.effects)
        assertTrue(RulerEffect.Persist in m.effects)
        assertTrue(m.logs.any { it.startsWith("PIN add id=p1 pos=(0.0000, 0.0000, -1.0000) plane=FLOOR:f1000000 grade=coarse") })
    }

    @Test
    fun secondPin_isTheTail_theTapeIsPulled_andTheHeadIsReleased() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f)
        assertEquals(listOf(Tape("t1", "p1", "p2")), m.state.tapes)
        assertNull(m.state.armedPinId)
        assertEquals("t1", m.state.lastTapeId)
        assertTrue(m.state.settings.coachSeen)
        // both pins are coarse at 1 m: 12 mm each, 16.97 -> 20 mm
        assertTrue(m.logs.any { it == "TAPE add id=t1 a=p1 b=p2 len_m=1.0000 tol_mm=20" })
        // the next point starts a new measurement instead of chaining
        m.place(0f, 0f, -2f)
        assertEquals("p3", m.state.armedPinId)
        assertEquals(1, m.state.tapes.size)
    }

    @Test
    fun touchingAPin_selectsItAsHead_touchingItAgainCancels_touchingAnotherPullsATape() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).place(0f, 0f, -2f).send(RulerAction.TapPin("p3"))
        assertNull(m.state.armedPinId) // p3 was the head: touching it again cancels
        m.send(RulerAction.TapPin("p1"))
        assertEquals("p1", m.state.armedPinId)
        assertTrue(m.state.armedExplicit)
        assertEquals("pin-selected-tuning", m.state.viewStateName)
        // candidates: not yet joined to the head, nearest first
        assertEquals(listOf("p3"), m.state.candidatesFor(m.state.armedPin!!).map { it.id })
        m.send(RulerAction.TapPin("p3"))
        assertEquals(Tape("t2", "p1", "p3"), m.state.tapes.last())
        assertNull(m.state.armedPinId)
    }

    @Test
    fun pointOnTopOfAPin_isThatPin_soAQuickSecondPinchLeavesNoZeroLengthTape() {
        // the second pinch lands 5 mm from the pin that was just set: it is that pin, touched again
        val m = readyMachine().place(0f, 0f, -1f).place(0.004f, 0f, -1.003f)
        assertEquals(1, m.state.pins.size)
        assertTrue(m.state.tapes.isEmpty())
        assertNull(m.state.armedPinId)
        // with another pin as the head, a point on top of p1 joins the two
        m.place(1f, 0f, -1f).place(0f, 0.002f, -1f)
        assertEquals(2, m.state.pins.size)
        assertEquals(listOf(Tape("t1", "p2", "p1")), m.state.tapes)
        // 2 cm beside a pin is a new pin
        m.place(0.02f, 0f, -1f)
        assertEquals(3, m.state.pins.size)
    }

    @Test
    fun everyVisibleChange_asksForItsSoundAndTouch() {
        val m = readyMachine().place(0f, 0f, -1f)
        assertEquals(listOf(FeedbackKind.PIN_DROP), m.effects.filterIsInstance<RulerEffect.Feedback>().map { it.kind })
        m.place(1f, 0f, -1f)
        assertEquals(
            listOf(FeedbackKind.PIN_DROP, FeedbackKind.PIN_DROP, FeedbackKind.TAPE_LOCK),
            m.effects.filterIsInstance<RulerEffect.Feedback>().map { it.kind },
        )
        m.send(RulerAction.RetractTape("t1"))
        assertEquals(FeedbackKind.RETRACT, m.effects.filterIsInstance<RulerEffect.Feedback>().last().kind)
        m.send(RulerAction.TapNothing(stillScanning = false))
        assertEquals(FeedbackKind.FAIL, m.effects.filterIsInstance<RulerEffect.Feedback>().last().kind)
    }

    @Test
    fun candidateChip_pullsTheTapeFromTheHead() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).place(0f, 0f, -2f).send(RulerAction.TapPin("p3"))
        m.send(RulerAction.TapPin("p2")).send(RulerAction.ChooseCandidate("p3"))
        assertEquals(Tape("t2", "p2", "p3"), m.state.tapes.last())
        assertEquals("t2", m.state.lastTapeId)
    }

    @Test
    fun secondTapeBetweenTheSameTwoPins_isRefused() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f)
        val history = m.state.history.size
        m.send(RulerAction.TapPin("p2")).send(RulerAction.TapPin("p1"))
        assertEquals(1, m.state.tapes.size)
        assertEquals(SnackKind.DUPLICATE, m.state.snack!!.kind)
        assertEquals(history, m.state.history.size) // nothing changed, nothing to undo
        assertTrue(m.logs.any { it == "TAPE duplicate id=t1 a=p2 b=p1" })
        m.send(RulerAction.Connect("p1", "p2"))
        assertEquals(1, m.state.tapes.size)
    }

    @Test
    fun plate_selectsItsTape_andATapElsewhereOnlyDeselects() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).send(RulerAction.TapPlate("t1"))
        assertEquals("t1", m.state.selectedTapeId)
        assertEquals("tape-selected", m.state.viewStateName)
        m.place(0f, 0f, -3f)
        assertNull(m.state.selectedTapeId)
        assertEquals(2, m.state.pins.size) // the tap let go of the tape, it did not set a pin
        m.send(RulerAction.TapPlate("t1")).send(RulerAction.TapPlate("t1"))
        assertNull(m.state.selectedTapeId)
    }

    // ------------------------------------------------------------------ anchors

    @Test
    fun anchorResult_makesThePinSaved_andKeepsItsSurfaceFrame() {
        val m = readyMachine().place(0f, 0f, -1f)
        // the platform reports the anchor a hair away and turned by 90 degrees about Y
        val turned = Quat4.axisAngle(Vec3.UP, (Math.PI / 2).toFloat())
        m.send(RulerAction.AnchorSaved("p1", "A1", AnchorPose(Vec3(0.001f, 0f, -1f), turned)))
        val pin = m.state.pin("p1")!!
        assertEquals(SaveState.SAVED, pin.saveState)
        assertEquals("A1", pin.anchorId)
        assertNear(Vec3(0.001f, 0f, -1f), pin.position)
        assertNear(Vec3.UP, pin.normal)
        assertNear(Vec3.RIGHT, pin.uAxis) // still "to the right" in the Stage
        assertEquals(mapOf("p1" to "A1"), m.state.liveAnchors)
        assertTrue(m.state.pendingCreates.isEmpty())
    }

    @Test
    fun anchorThatArrivesForARemovedPin_isRemovedAgain() {
        val m = readyMachine().place(0f, 0f, -1f).send(RulerAction.RemovePin("p1")).clearEffects()
        m.send(RulerAction.AnchorSaved("p1", "LATE", AnchorPose(Vec3(0f, 0f, -1f))))
        assertTrue(m.effects.any { it is RulerEffect.RemoveOrphans && "LATE" in it.anchorIds } || RulerEffect.RemoveAnchor("LATE") in m.effects)
        assertTrue(m.state.liveAnchors.isEmpty())
    }

    @Test
    fun failedAnchor_leavesAnUnsavedPin_retryAsksAgain_andSuccessIsAnnounced() {
        val m = readyMachine().place(0f, 0f, -1f).send(RulerAction.AnchorFailed("p1"))
        assertEquals(SaveState.UNSAVED, m.state.pin("p1")!!.saveState)
        assertTrue(m.state.pin("p1")!!.warns)
        val snack = m.state.snack!!
        assertEquals(SnackKind.UNSAVED, snack.kind)
        assertEquals("p1", snack.pinId)
        // measuring goes on with an unsaved pin, and no anchor is requested behind the user's back
        m.clearEffects().place(1f, 0f, -1f)
        assertEquals(1, m.state.tapes.size)
        assertEquals(listOf("p2"), m.effects.filterIsInstance<RulerEffect.CreateAnchor>().map { it.pinId })
        // Retry
        m.clearEffects().send(RulerAction.SnackAction(snack.id))
        assertEquals(SaveState.PENDING, m.state.pin("p1")!!.saveState)
        assertEquals(listOf("p1"), m.effects.filterIsInstance<RulerEffect.CreateAnchor>().map { it.pinId })
        m.send(RulerAction.AnchorSaved("p1", "A1", AnchorPose(Vec3(0f, 0f, -1f))))
        assertEquals(SaveState.SAVED, m.state.pin("p1")!!.saveState)
        assertEquals(SnackKind.SAVED, m.state.snack!!.kind)
    }

    @Test
    fun anchorThatMoves_movesItsPin_andTheTapeLengthFollows() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors()
        val anchor = m.state.pin("p2")!!.anchorId!!
        m.send(RulerAction.AnchorMoved(anchor, AnchorPose(Vec3(1.5f, 0f, -1f))))
        assertNear(1.5f, m.state.pin("p1")!!.position.distanceTo(m.state.pin("p2")!!.position))
    }

    // ------------------------------------------------------------------ editing

    @Test
    fun fineTune_movesThePinOneMillimetreAlongItsSurface() {
        val m = readyMachine().place(0f, 0f, -1f, snapped = SnapKind.CORNER).send(RulerAction.TapPin("p1")).send(RulerAction.TapPin("p1"))
        assertEquals("pin-selected-tuning", m.state.viewStateName) // a selected pin always has its steppers (round 5)
        m.send(RulerAction.Nudge(NudgeAxis.U, 1, rangeM = 0.6f)).send(RulerAction.Nudge(NudgeAxis.V, -1, rangeM = 0.6f))
        val pin = m.state.pin("p1")!!
        assertEquals(1, pin.offsetUMm)
        assertEquals(-1, pin.offsetVMm)
        assertNear(Vec3(0.001f, 0f, -0.999f), pin.position, 1e-6f)
        // trued by hand: off its seat, finely aimed, at the distance it was trued from
        assertEquals(Grade.FINE, pin.grade)
        assertEquals(SnapKind.NONE, pin.snapped)
        assertNear(0.6f, pin.rangeM)
        assertTrue(m.logs.any { it.startsWith("PIN nudge id=p1 du=1 dv=0") })
    }

    @Test
    fun runOfNudges_isOneUndoStep_andTheOffsetIsNotCapped() {
        val m = readyMachine().place(0f, 0f, -1f)
        val before = m.state.history.size
        repeat(5) { m.send(RulerAction.Nudge(NudgeAxis.U, 1, null), advanceMs = 400) }
        assertEquals(before + 1, m.state.history.size)
        assertEquals(5, m.state.pin("p1")!!.offsetUMm)
        m.send(RulerAction.Nudge(NudgeAxis.U, 1, null), advanceMs = 3000) // after a pause: a new step
        assertEquals(before + 2, m.state.history.size)
        m.send(RulerAction.Undo)
        assertEquals(5, m.state.pin("p1")!!.offsetUMm)
        m.send(RulerAction.Undo)
        assertEquals(0, m.state.pin("p1")!!.offsetUMm)
        // R2-4: there is no silent cap at 50 mm; only the 2 m sanity bound refuses (and says so)
        m.send(RulerAction.TapPin("p1"))
        repeat(60) { m.send(RulerAction.Nudge(NudgeAxis.V, -1, null), advanceMs = 10) }
        assertEquals(-60, m.state.pin("p1")!!.offsetVMm)
        // a drag that would leave the sanity bound is refused and logged
        m.clearEffects().send(RulerAction.StartPinDrag("p1")).send(RulerAction.EndPinDrag("p1", placement(Vec3(0f, 0f, -1f + (ReduceRulerUseCase.MAX_OFFSET_MM + 1) / 1000f))))
        assertEquals(-60, m.state.pin("p1")!!.offsetVMm)
        assertTrue(m.logs.any { it.startsWith("PIN drag refused id=p1") })
    }

    // ------------------------------------------------------------------ anchor rebase (R2-4)

    /** A pin drag (round 5) of [pinId] that ends at [to] on the floor, with [grade]. */
    private fun Machine.dragTo(pinId: String, to: Vec3, grade: Grade = Grade.COARSE, snapped: SnapKind = SnapKind.NONE, rangeM: Float = 1f, gain: String = "1:1"): Machine =
        send(RulerAction.StartPinDrag(pinId)).send(RulerAction.EndPinDrag(pinId, placement(to, grade = grade, snapped = snapped, rangeM = rangeM), "system", gain))

    private fun Machine.tunedBeyond50(): Machine {
        // the new pin is the tape head: one tap cancels that, the next selects it; then it is dragged 80 mm right and 30 mm nearer
        place(0f, 0f, -1f).saveAnchors().send(RulerAction.TapPin("p1")).send(RulerAction.TapPin("p1"))
        dragTo("p1", Vec3(0.08f, 0f, -0.97f))
        assertEquals(80 to -30, state.pin("p1")!!.offsetUMm to state.pin("p1")!!.offsetVMm)
        return this
    }

    @Test
    fun rebaseDue_beyond50mm_recreatesTheAnchorAtThePin_showsPinning_andResetsTheOffset() {
        val m = readyMachine().place(0f, 0f, -1f).saveAnchors().send(RulerAction.TapPin("p1")).send(RulerAction.TapPin("p1"))
        // the drag ends 80 mm right and 30 mm nearer: the release re-creates the anchor there (no 0.6 s wait)
        m.clearEffects().dragTo("p1", Vec3(0.08f, 0f, -0.97f))
        val pin = m.state.pin("p1")!!
        val position = pin.position
        val old = pin.anchorId!!
        assertNear(Vec3(0.08f, 0f, -0.97f), position, 1e-6f)
        assertTrue(m.logs.any { it.startsWith("PIN drag end id=p1") && it.contains("plane=same") })
        val request = m.effects.filterIsInstance<RulerEffect.RebaseAnchor>().single()
        assertEquals("p1", request.pinId)
        assertEquals(old, request.oldAnchorId)
        assertNear(position, request.position, 1e-6f)
        assertTrue(m.state.pin("p1")!!.rebasing)
        assertEquals(SaveState.SAVED, m.state.pin("p1")!!.saveState) // the old anchor still holds it
        assertEquals(80, m.state.pin("p1")!!.offsetUMm)
        // a second due while one is under way asks nothing more
        m.send(RulerAction.RebaseDue("p1"))
        assertEquals(1, m.count<RulerEffect.RebaseAnchor>())

        m.clearEffects().send(RulerAction.AnchorRebased("p1", "anchor-new", AnchorPose(position)))
        val rebased = m.state.pin("p1")!!
        assertFalse(rebased.rebasing)
        assertEquals("anchor-new", rebased.anchorId)
        assertEquals(0 to 0, rebased.offsetUMm to rebased.offsetVMm)
        assertNear(position, rebased.position, 1e-6f) // the pin has not moved
        assertEquals(SaveState.SAVED, rebased.saveState)
        assertEquals("anchor-new", m.state.liveAnchors["p1"])
        assertTrue(RulerEffect.RemoveAnchor(old) in m.effects)
        assertTrue(RulerEffect.Persist in m.effects)
        assertTrue(m.logs.any { it.startsWith("ANCHOR rebase id=p1 uuid_old=$old uuid_new=anchor-new") })
        // the surface frame survives in the new anchor's frame
        assertNear(Vec3(1f, 0f, 0f), rebased.uAxis, 1e-6f)
    }

    @Test
    fun rebase_keepsConnectedTapesIntact() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors()
        assertEquals(1, m.state.tapes.size)
        m.send(RulerAction.TapPin("p1")).dragTo("p1", Vec3(0f, 0f, -1.07f))
        val position = m.state.pin("p1")!!.position
        m.send(RulerAction.AnchorRebased("p1", "anchor-new", AnchorPose(position)))
        assertEquals(1, m.state.tapes.size)
        assertTrue(m.state.tapes[0].joins("p1", "p2"))
        assertNear(position, m.state.pin("p1")!!.position, 1e-6f)
    }

    @Test
    fun rebaseDue_isNotTaken_within50mm_whileDragging_orForAnUnsavedPin() {
        val m = readyMachine().place(0f, 0f, -1f).saveAnchors().send(RulerAction.TapPin("p1")).send(RulerAction.TapPin("p1"))
        m.clearEffects().dragTo("p1", Vec3(0.03f, 0f, -1.04f)) // exactly 50 mm
        assertEquals(30 to 40, m.state.pin("p1")!!.offsetUMm to m.state.pin("p1")!!.offsetVMm)
        assertEquals(0, m.count<RulerEffect.RebaseAnchor>())
        m.send(RulerAction.RebaseDue("p1"))
        assertEquals(0, m.count<RulerEffect.RebaseAnchor>())
        m.send(RulerAction.StartPinDrag("p1")).clearEffects().send(RulerAction.RebaseDue("p1"))
        assertEquals(0, m.count<RulerEffect.RebaseAnchor>()) // a drag is under way: the release decides
        assertEquals("pin-drag", m.state.viewStateName)
        // an unsaved pin has no anchor to re-create: the offset simply stays
        val u = readyMachine().place(0f, 0f, -1f)
        u.send(RulerAction.AnchorFailed("p1")).send(RulerAction.TapPin("p1")).send(RulerAction.TapPin("p1"))
        u.clearEffects().dragTo("p1", Vec3(0.09f, 0f, -1f)).send(RulerAction.RebaseDue("p1"))
        assertEquals(0, u.count<RulerEffect.RebaseAnchor>())
        assertEquals(90, u.state.pin("p1")!!.offsetUMm)
    }

    @Test
    fun undoAcrossARebase_putsThePinBackExactly_andRecreatesTheAnchorThere() {
        // p1 - p2 is a tape; p1 is nudged one step, then (a separate undo step) dragged 86 mm, which re-creates its anchor
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors()
        val origin = m.state.pin("p1")!!.position
        m.send(RulerAction.TapPin("p1")).send(RulerAction.Nudge(NudgeAxis.U, 1, null))
        val nudged = m.state.pin("p1")!!.position
        m.send(RulerAction.StartPinDrag("p1"), advanceMs = 3000).send(RulerAction.EndPinDrag("p1", placement(Vec3(0.081f, 0f, -0.97f))))
        val dragged = m.state.pin("p1")!!.position
        assertTrue(m.state.pin("p1")!!.rebasing) // 86 mm: the release itself re-creates the anchor
        m.send(RulerAction.AnchorRebased("p1", "anchor-new", AnchorPose(dragged)))
        assertEquals("anchor-new", m.state.pin("p1")!!.anchorId)
        assertEquals(0 to 0, m.state.pin("p1")!!.offsetUMm to m.state.pin("p1")!!.offsetVMm)
        assertNear(dragged, m.state.pin("p1")!!.position, 1e-6f)

        m.clearEffects().send(RulerAction.Undo)
        val pin = m.state.pin("p1")!!
        assertNear(nudged, pin.position, 1e-3f) // back at the pre-drag place, to the millimetre of the offset model
        assertEquals("anchor-new", pin.anchorId)
        assertTrue(pin.rebasing) // and the anchor is being re-created there
        val request = m.effects.filterIsInstance<RulerEffect.RebaseAnchor>().single()
        assertNear(nudged, request.position, 1e-6f) // exactly where the undo step says
        assertEquals("anchor-new", request.oldAnchorId)
        assertTrue(m.logs.any { it.startsWith("ANCHOR rebase start id=p1 uuid_old=anchor-new") && it.endsWith("reason=undo") })
        assertEquals(1, m.state.tapes.size)
        assertNear(nudged.distanceTo(m.state.pin("p2")!!.position), m.state.pin("p1")!!.position.distanceTo(m.state.pin("p2")!!.position), 1e-3f)

        m.clearEffects().send(RulerAction.AnchorRebased("p1", "anchor-3", AnchorPose(nudged)))
        val back = m.state.pin("p1")!!
        assertNear(nudged, back.position, 1e-6f) // exact again
        assertEquals(0 to 0, back.offsetUMm to back.offsetVMm)
        assertEquals("anchor-3", back.anchorId)
        assertFalse(back.rebasing)
        assertTrue(RulerEffect.RemoveAnchor("anchor-new") in m.effects)
        // a second undo takes the nudge back: the same anchor, a 1 mm offset, no new anchor
        m.clearEffects().send(RulerAction.Undo)
        val first = m.state.pin("p1")!!
        assertNear(origin, first.position, 1e-3f)
        assertEquals("anchor-3", first.anchorId)
        assertEquals(0, m.count<RulerEffect.RebaseAnchor>())
    }

    @Test
    fun rebasedAnchorThatMissedTheRequest_leavesThePinWhereItIs() {
        val m = readyMachine().tunedBeyond50()
        val position = m.state.pin("p1")!!.position
        m.send(RulerAction.AnchorRebased("p1", "anchor-new", AnchorPose(position + Vec3(0.004f, 0f, 0f))))
        val pin = m.state.pin("p1")!!
        assertNear(position, pin.position, 1e-6f)
        assertEquals(-4 to 0, pin.offsetUMm to pin.offsetVMm)
        assertTrue(m.logs.any { it.contains("shift_mm=4") })
    }

    @Test
    fun rebaseFailure_keepsTheOldAnchorAndTheOffset() {
        val m = readyMachine().tunedBeyond50().clearEffects()
        val old = m.state.pin("p1")!!.anchorId
        assertTrue(m.state.pin("p1")!!.rebasing)
        m.send(RulerAction.AnchorFailed("p1"))
        val pin = m.state.pin("p1")!!
        assertFalse(pin.rebasing)
        assertEquals(old, pin.anchorId)
        assertEquals(SaveState.SAVED, pin.saveState)
        assertEquals(80 to -30, pin.offsetUMm to pin.offsetVMm)
        assertTrue(m.state.snacks.isEmpty())
    }

    @Test
    fun rebaseAnsweredAfterThePinIsGone_removesTheNewAnchor() {
        val m = readyMachine().tunedBeyond50()
        m.send(RulerAction.RemovePin("p1")).clearEffects()
        assertNull(m.state.pin("p1"))
        m.send(RulerAction.AnchorRebased("p1", "anchor-new", AnchorPose(Vec3(0.08f, 0f, -0.97f))))
        assertTrue(RulerEffect.RemoveAnchor("anchor-new") in m.effects)
    }

    // ------------------------------------------------------------------ menu pinches (R2-2)

    @Test
    fun aPinchThatDismissesTheMenu_isConsumed() {
        val m = readyMachine().send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.MORE, m.state.menu)
        m.clearEffects().send(RulerAction.PlacePin(placement(Vec3(0f, 0f, -1f))))
        assertEquals(MenuState.CLOSED, m.state.menu)
        assertTrue(m.state.pins.isEmpty())
        assertTrue(m.logs.any { it.startsWith("TAP consumed reason=menu") })
        // the next pinch places
        m.send(RulerAction.PlacePin(placement(Vec3(0f, 0f, -1f))))
        assertEquals(1, m.state.pins.size)
        // the same for a pin, a plate, nothing, a pull and a chip while the menu is open
        m.send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.MORE, m.state.menu)
        m.send(RulerAction.TapPin("p1"))
        assertEquals(MenuState.CLOSED, m.state.menu)
        assertEquals("p1", m.state.armedPinId) // a real tap on the head would have cancelled it
        assertFalse(m.state.armedExplicit)
        m.send(RulerAction.ToggleMoreMenu).send(RulerAction.StartPull(placement(Vec3(1f, 0f, -1f)), null))
        assertEquals(MenuState.CLOSED, m.state.menu)
        assertNull(m.state.pull)
        assertEquals(1, m.state.pins.size)
        // the More key closes it; another body key only closes it too (a native popup would have swallowed that press)
        m.send(RulerAction.ToggleMoreMenu).send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.CLOSED, m.state.menu)
        m.send(RulerAction.ToggleMoreMenu).send(RulerAction.SetUnit(RulerUnit.MM))
        assertEquals(MenuState.CLOSED, m.state.menu)
        assertEquals(RulerUnit.M, m.state.settings.unit)
        m.send(RulerAction.SetUnit(RulerUnit.MM))
        assertEquals(RulerUnit.MM, m.state.settings.unit)
    }

    // ------------------------------------------------------------------ no tracked source (R2-1)

    @Test
    fun noTrackedSource_showsTheRaiseAHandMessage_untilOneIsBack() {
        val m = readyMachine().clearEffects().send(RulerAction.SourceChanged(lost = true))
        assertTrue(m.state.noSource)
        assertEquals(SnackKind.NO_SOURCE, m.state.snack?.kind)
        assertEquals(SnackKind.STICKY, SnackKind.NO_SOURCE.durationMs)
        assertTrue(m.logs.any { it == "SOURCE lost" })
        m.send(RulerAction.SourceChanged(lost = true))
        assertEquals(1, m.state.snacks.size)
        m.send(RulerAction.SourceChanged(lost = false))
        assertFalse(m.state.noSource)
        assertNull(m.state.snack)
        // not before the session is ready
        val s = Machine().send(RulerAction.SourceChanged(lost = true))
        assertFalse(s.state.noSource)
        assertNull(s.state.snack)
    }

    // ------------------------------------------------------------------ coachmark

    @Test
    fun coachmark_isDismissedByGotIt_andCanBeResetForTheTests() {
        val m = readyMachine()
        assertFalse(m.state.settings.coachSeen)
        m.clearEffects().send(RulerAction.CoachDismissed)
        assertTrue(m.state.settings.coachSeen)
        assertTrue(RulerEffect.Persist in m.effects)
        assertTrue(m.logs.any { it == "COACH dismissed reason=got_it" })
        m.clearEffects().send(RulerAction.CoachDismissed)
        assertEquals(0, m.count<RulerEffect.Persist>())
        m.send(RulerAction.CoachReset)
        assertFalse(m.state.settings.coachSeen)
    }

    @Test
    fun removingAPin_retractsItsTapes_andRemovesItsAnchor() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).place(0f, 0f, -2f).send(RulerAction.TapPin("p1")).saveAnchors()
        // p1 - p2 and p3 - p1
        assertEquals(2, m.state.tapes.size)
        val anchor = m.state.pin("p1")!!.anchorId!!
        m.clearEffects().send(RulerAction.TapPin("p1")).send(RulerAction.RemovePin())
        assertNull(m.state.pin("p1"))
        assertTrue(m.state.tapes.isEmpty())
        assertNull(m.state.lastTapeId)
        assertTrue(RulerEffect.RemoveAnchor(anchor) in m.effects)
        assertEquals(2, m.logs.count { it.startsWith("TAPE remove") && it.endsWith("reason=pin") })
        assertTrue(m.logs.any { it == "PIN remove id=p1" })
    }

    @Test
    fun retract_removesOnlyTheTape() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).send(RulerAction.TapPlate("t1")).send(RulerAction.RetractTape())
        assertTrue(m.state.tapes.isEmpty())
        assertEquals(2, m.state.pins.size)
        assertNull(m.state.selectedTapeId)
        assertTrue(m.logs.any { it == "TAPE remove id=t1 a=p1 b=p2 reason=retract" })
    }

    // ------------------------------------------------------------------ undo

    @Test
    fun undo_stepsBackThroughPinsAndTapes_andKeepsTheAnchorStoreInStep() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors()
        val secondAnchor = m.state.pin("p2")!!.anchorId!!
        m.clearEffects().send(RulerAction.Undo)
        // the second placement is undone: its pin, its tape and its anchor go
        assertEquals(listOf("p1"), m.state.pins.map { it.id })
        assertTrue(m.state.tapes.isEmpty())
        assertTrue(RulerEffect.RemoveAnchor(secondAnchor) in m.effects)
        assertEquals(SaveState.SAVED, m.state.pin("p1")!!.saveState) // anchor facts are not undone
        m.send(RulerAction.Undo)
        assertTrue(m.state.pins.isEmpty())
        assertTrue(m.state.history.isEmpty())
        // ids are never reused, so a late anchor result cannot hit the wrong pin
        m.place(0f, 0f, -1f)
        assertEquals("p3", m.state.pins.single().id)
    }

    @Test
    fun undo_bringsBackARemovedPin_andAsksForANewAnchor() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors()
        m.send(RulerAction.RemovePin("p1")).clearEffects().send(RulerAction.Undo)
        assertEquals(listOf("p1", "p2"), m.state.pins.map { it.id })
        assertEquals(listOf("t1"), m.state.tapes.map { it.id })
        assertEquals(SaveState.PENDING, m.state.pin("p1")!!.saveState)
        assertEquals(listOf("p1"), m.effects.filterIsInstance<RulerEffect.CreateAnchor>().map { it.pinId })
        assertEquals(SaveState.SAVED, m.state.pin("p2")!!.saveState)
    }

    @Test
    fun undoHistory_holdsTwentySteps() {
        val m = readyMachine()
        repeat(25) { m.place(it * 0.1f, 0f, -1f) }
        assertEquals(RulerState.MAX_HISTORY, m.state.history.size)
        repeat(30) { m.send(RulerAction.Undo) }
        assertEquals(5, m.state.pins.size) // the five oldest steps are gone
        assertTrue(m.state.history.isEmpty())
    }

    // ------------------------------------------------------------------ menu, clear, exit

    @Test
    fun moreMenu_languageAndSnapChangeInPlace_andTheMenuStaysOpen() {
        val m = readyMachine().send(RulerAction.ToggleMoreMenu)
        assertEquals("more-menu", m.state.viewStateName)
        m.clearEffects().send(RulerAction.SetLanguage(LanguagePref.ZH))
        assertEquals(MenuState.MORE, m.state.menu) // the result is seen in the open menu
        assertEquals(LanguagePref.ZH, m.state.settings.language)
        assertEquals(listOf("LANG zh"), m.logs)
        assertTrue(RulerEffect.Persist in m.effects)
        m.clearEffects().send(RulerAction.SetSnap(false))
        assertEquals(MenuState.MORE, m.state.menu)
        assertFalse(m.state.settings.snap)
        assertEquals(listOf("SNAP off"), m.logs)
        // Clear all closes the menu (and opens the dialog when there are pins); the More key closes it
        m.send(RulerAction.RequestClearAll)
        assertEquals(MenuState.CLOSED, m.state.menu)
        m.send(RulerAction.ToggleMoreMenu).send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.CLOSED, m.state.menu)
    }

    @Test
    fun unitAndSnap_areSettingsThatPersist() {
        val m = readyMachine().send(RulerAction.SetUnit(RulerUnit.MM)).send(RulerAction.SetSnap(false))
        assertEquals(RulerUnit.MM, m.state.settings.unit)
        assertFalse(m.state.settings.snap)
        assertEquals(listOf("UNIT mm", "SNAP off"), m.logs.filter { it.startsWith("UNIT") || it.startsWith("SNAP") })
        assertEquals(2, m.count<RulerEffect.Persist>())
    }

    @Test
    fun clearAll_asksFirst_thenRemovesEverythingIncludingTheAnchors() {
        val m = readyMachine().send(RulerAction.RequestClearAll)
        assertEquals(DialogState.NONE, m.state.dialog) // nothing to clear: no dialog
        m.place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors().send(RulerAction.ToggleMoreMenu).send(RulerAction.RequestClearAll)
        assertEquals("confirm-clear", m.state.viewStateName)
        assertEquals(MenuState.CLOSED, m.state.menu)
        m.send(RulerAction.DismissDialog)
        assertEquals(2, m.state.pins.size)
        m.send(RulerAction.RequestClearAll).clearEffects().send(RulerAction.ConfirmClearAll)
        assertTrue(m.state.pins.isEmpty())
        assertTrue(m.state.tapes.isEmpty())
        assertTrue(m.state.history.isEmpty()) // cannot be undone
        assertEquals(2, m.count<RulerEffect.RemoveAnchor>())
        assertEquals(SnackKind.CLEARED, m.state.snack!!.kind)
        assertTrue(m.logs.any { it == "CLEAR pins=2 tapes=1" })
        assertTrue(m.state.liveAnchors.isEmpty())
    }

    @Test
    fun exit_keepsOnlySavedPins_andClosesAfterPersisting() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f)
        m.send(RulerAction.AnchorSaved("p1", "A1", AnchorPose(Vec3(0f, 0f, -1f)))).send(RulerAction.AnchorFailed("p2"))
        m.clearEffects().send(RulerAction.Exit)
        assertEquals(Phase.EXITED, m.state.phase)
        assertEquals("exited", m.state.viewStateName)
        assertEquals(listOf("p1"), m.state.pins.map { it.id })
        assertTrue(m.state.tapes.isEmpty()) // its other end was never saved
        assertEquals(listOf(RulerEffect.Persist, RulerEffect.ExitApp), m.effects.filter { it !is RulerEffect.Log })
        assertEquals(mapOf("p1" to "A1"), m.state.liveAnchors) // saved anchors stay in the store
    }

    @Test
    fun tapWithoutASurfaceYet_waitsAsThePendingAim_withTheMappingMessage_andNoFailure() {
        val m = readyMachine().send(RulerAction.TapUnmapped(PendingTap(Vec3(0f, 1.6f, 0f), Vec3(0f, -0.7f, -0.7f), 10_100L), stillScanning = true))
        assertEquals(SnackKind.NOT_MAPPED, m.state.snack!!.kind)
        assertEquals(10_100L, m.state.pendingTap!!.atMs)
        assertEquals(0, m.effects.count { it is RulerEffect.Feedback && it.kind == FeedbackKind.FAIL })
        assertTrue(m.state.pins.isEmpty())
        // a later surface-less tap replaces it: only the latest is kept
        m.send(RulerAction.TapUnmapped(PendingTap(Vec3(0f, 1.6f, 0f), Vec3(0.3f, -0.7f, -0.6f), 10_900L), stillScanning = true))
        assertEquals(10_900L, m.state.pendingTap!!.atMs)
        assertTrue(m.logs.any { it.startsWith("TAP pending") && it.contains("replaced=true") })
    }

    @Test
    fun pendingTap_isPlacedWhenASurfaceArrivesUnderIt_throughTheNormalPath() {
        val m = readyMachine().send(RulerAction.TapUnmapped(PendingTap(Vec3(0f, 1.6f, 0f), Vec3(0f, -0.7f, -0.7f), 10_100L), stillScanning = true))
        m.send(RulerAction.PendingTapResolved(placement(Vec3(0f, 0f, -1.6f))), advanceMs = 1400)
        assertNull(m.state.pendingTap)
        assertEquals(1, m.state.pins.size)
        assertEquals("p1", m.state.armedPinId)
        assertTrue(m.logs.any { it.startsWith("PIN add id=p1") && it.endsWith("deferred=true") })
        assertTrue(m.logs.any { it.startsWith("TAP pending resolved after_ms=1500") })
        // a stray resolve without a pending tap places nothing
        m.send(RulerAction.PendingTapResolved(placement(Vec3(0.5f, 0f, -1.6f))))
        assertEquals(1, m.state.pins.size)
    }

    @Test
    fun pendingTap_isDroppedAfter15s_withTheNoSurfaceMessage_andEndedByAnyPlacedPin() {
        val m = readyMachine().send(RulerAction.TapUnmapped(PendingTap(Vec3(0f, 1.6f, 0f), Vec3(0f, -0.7f, -0.7f), 10_100L), stillScanning = true))
        m.send(RulerAction.PendingTapTick, advanceMs = 14_000)
        assertNotNull(m.state.pendingTap)
        m.send(RulerAction.PendingTapTick, advanceMs = 1_500)
        assertNull(m.state.pendingTap)
        assertEquals(SnackKind.NO_SURFACE, m.state.snack!!.kind)
        assertTrue(m.state.pins.isEmpty())
        // a pin placed any other way ends a pending tap
        m.send(RulerAction.TapUnmapped(PendingTap(Vec3(0f, 1.6f, 0f), Vec3(0f, -0.7f, -0.7f), m.now), stillScanning = false))
        m.place(0f, 0f, -1f)
        assertNull(m.state.pendingTap)
        assertEquals(1, m.state.pins.size)
    }

    @Test
    fun tapThatHitsNoSurface_saysSo_butNotOverAndOver() {
        val m = readyMachine().send(RulerAction.TapNothing(stillScanning = false))
        assertEquals(SnackKind.NO_SURFACE, m.state.snack!!.kind)
        val first = m.state.snack!!.id
        m.send(RulerAction.TapNothing(stillScanning = false), advanceMs = 1000)
        assertEquals(first, m.state.snack!!.id)
        m.send(RulerAction.TapNothing(stillScanning = true), advanceMs = 4000)
        assertEquals(SnackKind.NOT_MAPPED, m.state.snack!!.kind)
        assertTrue(m.state.pins.isEmpty())
    }

    @Test
    fun messageThatWaitsForAnAnswer_isNotPushedAwayByAPlainOne() {
        val m = readyMachine().place(0f, 0f, -1f).send(RulerAction.AnchorFailed("p1"))
        m.send(RulerAction.TapNothing(stillScanning = false))
        assertEquals(SnackKind.UNSAVED, m.state.snack!!.kind) // still asking "Retry"
        m.send(RulerAction.SnackDismissed(m.state.snack!!.id))
        assertEquals(SnackKind.NO_SURFACE, m.state.snack!!.kind)
    }

    @Test
    fun farPlacement_isMarked_andDoublesItsShare() {
        val m = readyMachine().place(0f, 0f, -3.5f, rangeM = 3.8f).place(0f, 0f, -1f, rangeM = 1.8f)
        assertTrue(m.state.pin("p1")!!.far)
        assertFalse(m.state.pin("p2")!!.far)
        // (8 + 15.2) * 2 = 46.4 and 8 + 7.2 = 15.2 -> 48.8 -> 50 mm
        assertTrue(m.logs.any { it == "TAPE add id=t1 a=p1 b=p2 len_m=2.5000 tol_mm=50" })
    }

    // ------------------------------------------------------------------ tape limit

    /** A ready machine that already holds [pins] saved pins in a row and the tapes between neighbours. */
    private fun chain(pins: Int): Machine {
        val m = readyMachine()
        val list = (1..pins).map { pin("p$it", Vec3(it * 0.1f, 0f, -1f)) }
        val tapes = (1 until pins).map { Tape("t$it", "p$it", "p${it + 1}") }
        m.state = m.state.copy(pins = list, tapes = tapes, pinSeq = pins, tapeSeq = pins - 1, liveAnchors = list.associate { it.id to it.anchorId!! })
        return m
    }

    @Test
    fun twentyTapes_areTheLimit_theTailPinIsStillSet_andBecomesTheHead() {
        val m = chain(21)
        assertEquals(RulerState.MAX_TAPES, m.state.tapes.size)
        m.send(RulerAction.TapPin("p1")).place(5f, 0f, -1f)
        assertEquals(20, m.state.tapes.size)
        assertEquals(22, m.state.pins.size)
        assertEquals("p22", m.state.armedPinId) // the new pin is the next tape head
        assertFalse(m.state.armedExplicit)
        assertEquals(SnackKind.TAPE_LIMIT, m.state.snack!!.kind)
        assertTrue(m.logs.any { it == "TAPE refused reason=limit a=p1 b=p22 max=20" })
    }

    @Test
    fun atTheLimit_noOtherWayPullsATapeEither_untilOneIsRetracted() {
        val m = chain(21).send(RulerAction.TapPin("p1"))
        // touching another pin: it becomes the head instead
        m.send(RulerAction.TapPin("p5"))
        assertEquals(20, m.state.tapes.size)
        assertEquals("p5", m.state.armedPinId)
        assertTrue(m.state.armedExplicit)
        // a candidate chip, and the test hook
        m.send(RulerAction.ChooseCandidate("p9")).send(RulerAction.Connect("p1", "p3"))
        assertEquals(20, m.state.tapes.size)
        assertEquals(3, m.logs.count { it.startsWith("TAPE refused reason=limit") })
        // between two pins that are joined already the answer is still "there is one"
        m.send(RulerAction.Connect("p1", "p2"))
        assertEquals(SnackKind.DUPLICATE, m.state.snack!!.kind)
        // room again after a retract
        m.send(RulerAction.RetractTape("t20")).send(RulerAction.Connect("p1", "p3"))
        assertEquals(20, m.state.tapes.size)
        assertTrue(m.state.tapes.any { it.joins("p1", "p3") })
    }

    // ------------------------------------------------------------------ press-and-pull

    @Test
    fun pressAndPull_onASurface_setsHeadTailAndTape_asOneUndoStep() {
        val m = readyMachine()
        val before = m.state.history.size
        m.send(RulerAction.StartPull(placement(Vec3(0f, 0f, -1f)), pinId = null))
        assertEquals("pulling-drag", m.state.viewStateName)
        assertEquals(PullState("p1", headIsNew = true), m.state.pull)
        assertEquals("p1", m.state.armedPinId)
        // taps do nothing while the tape is under the hand, and there is nothing to undo yet
        m.place(3f, 0f, -1f).send(RulerAction.TapPin("p1")).send(RulerAction.Undo)
        assertEquals(1, m.state.pins.size)
        assertEquals("p1", m.state.pull!!.headPinId)
        m.send(RulerAction.EndPull(PullEnd.OnSurface(placement(Vec3(1f, 0f, -1f), grade = Grade.FINE))))
        assertNull(m.state.pull)
        assertNull(m.state.armedPinId)
        assertEquals(listOf(Tape("t1", "p1", "p2")), m.state.tapes)
        assertEquals(Grade.FINE, m.state.pin("p2")!!.grade)
        assertEquals(before + 1, m.state.history.size)
        assertTrue(m.logs.containsAll(listOf("PULL start head=p1 new=true", "PULL end head=p1 tail=p2")))
        assertTrue(m.logs.any { it.startsWith("TAPE add id=t1 a=p1 b=p2 len_m=1.0000") })
        m.send(RulerAction.Undo)
        assertTrue(m.state.pins.isEmpty())
        assertTrue(m.state.tapes.isEmpty())
    }

    /** Two lone pins p1 and p2, nothing armed. */
    private fun twoPins(): Machine =
        readyMachine().place(0f, 0f, -1f).send(RulerAction.TapPin("p1")).place(1f, 0f, -1f).send(RulerAction.TapPin("p2"))

    @Test
    fun pressAndPull_fromAPinToAnotherPin_joinsThem() {
        val m = twoPins()
        assertNull(m.state.armedPinId)
        val before = m.state.history.size
        m.send(RulerAction.StartPull(null, "p1"))
        assertEquals(PullState("p1", headIsNew = false), m.state.pull)
        m.send(RulerAction.EndPull(PullEnd.OnPin("p2")))
        assertEquals(listOf(Tape("t1", "p1", "p2")), m.state.tapes)
        assertEquals(before + 1, m.state.history.size)
        assertNull(m.state.armedPinId)
    }

    @Test
    fun pullThatChangesNothing_leavesNoUndoStep_andKeepsTheHead() {
        val m = twoPins().send(RulerAction.Connect("p1", "p2"))
        val before = m.state.history.size
        // let go on the head itself
        m.send(RulerAction.StartPull(null, "p1")).send(RulerAction.EndPull(PullEnd.OnPin("p1")))
        assertEquals(before, m.state.history.size)
        assertEquals("p1", m.state.armedPinId) // still the tape head: "pulling"
        assertEquals("pulling", m.state.copy(scanning = false).viewStateName)
        // let go on a pin it is joined to already
        m.send(RulerAction.StartPull(null, "p1")).send(RulerAction.EndPull(PullEnd.OnPin("p2")))
        assertEquals(before, m.state.history.size)
        assertEquals(1, m.state.tapes.size)
        assertEquals(SnackKind.DUPLICATE, m.state.snack!!.kind)
        // taken away by the system
        m.send(RulerAction.StartPull(null, "p2")).send(RulerAction.CancelPull)
        assertNull(m.state.pull)
        assertEquals(before, m.state.history.size)
        assertTrue(m.logs.any { it == "PULL cancel head=p2" })
    }

    @Test
    fun pullReleasedWhereNoSurfaceIs_keepsTheNewHead_andSaysSo() {
        val m = readyMachine().send(RulerAction.StartPull(placement(Vec3(0f, 0f, -1f)), null))
        m.send(RulerAction.EndPull(PullEnd.Nowhere(stillScanning = false)))
        assertNull(m.state.pull)
        assertEquals(1, m.state.pins.size)
        assertEquals("p1", m.state.armedPinId)
        assertEquals(SnackKind.NO_SURFACE, m.state.snack!!.kind)
        // the head it set is one undo step
        m.send(RulerAction.Undo)
        assertTrue(m.state.pins.isEmpty())
    }

    @Test
    fun pullAtTheTapeLimit_setsTheTailPin_butNoTape() {
        val m = chain(21).send(RulerAction.StartPull(null, "p1"))
        m.send(RulerAction.EndPull(PullEnd.OnSurface(placement(Vec3(5f, 0f, -1f)))))
        assertEquals(20, m.state.tapes.size)
        assertEquals(22, m.state.pins.size)
        assertEquals("p22", m.state.armedPinId)
        assertEquals(SnackKind.TAPE_LIMIT, m.state.snack!!.kind)
    }

    // ------------------------------------------------------------------ truing by drag

    @Test
    fun pinDrag_movesTheSelectedPin_asOneUndoStep_andKeepsTheGradeOfTheRelease() {
        val m = readyMachine().place(0f, 0f, -1f, snapped = SnapKind.CORNER).saveAnchors()
        // a press on a pin that moves selects it and drags it; no toggle (round 5)
        m.clearEffects().send(RulerAction.StartPinDrag("p1"))
        assertEquals("pin-drag", m.state.viewStateName)
        assertEquals("p1", m.state.armedPinId)
        assertTrue(m.state.armedExplicit && m.state.tuneOpen)
        assertTrue(m.logs.any { it == "PIN drag start id=p1 from=(0.0000, 0.0000, -1.0000)" })
        val before = m.state.history.size
        m.clearEffects().send(RulerAction.EndPinDrag("p1", placement(Vec3(0.02f, 0f, -0.99f), grade = Grade.FINE, rangeM = 0.7f), "system", "1:4"))
        val pin = m.state.pin("p1")!!
        assertEquals("pin-selected-tuning", m.state.viewStateName) // the strip stays open for the steppers
        assertEquals(20 to -10, pin.offsetUMm to pin.offsetVMm)
        assertEquals(Grade.FINE, pin.grade)
        assertEquals(SnapKind.NONE, pin.snapped)
        assertNear(0.7f, pin.rangeM)
        assertNear(Vec3(0.02f, 0f, -0.99f), pin.position, 1e-6f) // 20 mm to the right, 10 mm nearer
        assertTrue(m.logs.any { it.startsWith("PIN drag end id=p1 from=(0.0000, 0.0000, -1.0000) to=(0.0200, 0.0000, -0.9900) via=system gain=1:4 plane=same grade=fine") })
        assertEquals(1, m.count<RulerEffect.Persist>())
        assertEquals(1, m.effects.count { it is RulerEffect.Feedback && it.kind == FeedbackKind.PIN_DROP })
        assertEquals(before + 1, m.state.history.size)
        assertEquals(0, m.count<RulerEffect.RebaseAnchor>()) // 22 mm: the anchor stays
        m.send(RulerAction.Undo)
        assertEquals(0 to 0, m.state.pin("p1")!!.offsetUMm to m.state.pin("p1")!!.offsetVMm)
        assertEquals(SnapKind.CORNER, m.state.pin("p1")!!.snapped)
        // a release that no drag started is ignored
        m.send(RulerAction.EndPinDrag("p1", placement(Vec3(0.05f, 0f, -1f))))
        assertEquals(0, m.state.pin("p1")!!.offsetUMm)
        // a contact release keeps the contact grade and the seat it snapped to
        m.send(RulerAction.TapPin("p1")).send(RulerAction.StartPinDrag("p1")).send(RulerAction.EndPinDrag("p1", placement(Vec3(0.05f, 0f, -1f), grade = Grade.CONTACT, snapped = SnapKind.EDGE), "hand", "1:1"))
        assertEquals(Grade.CONTACT, m.state.pin("p1")!!.grade)
        assertEquals(SnapKind.EDGE, m.state.pin("p1")!!.snapped)
    }

    @Test
    fun pinDrag_releasedWithinTenMillimetres_changesNothing() {
        val m = readyMachine().place(0f, 0f, -1f).saveAnchors().send(RulerAction.TapPin("p1")).send(RulerAction.TapPin("p1"))
        val before = m.state.history.size
        m.clearEffects().send(RulerAction.StartPinDrag("p1")).send(RulerAction.EndPinDrag("p1", placement(Vec3(0.009f, 0f, -1f))))
        assertEquals(0 to 0, m.state.pin("p1")!!.offsetUMm to m.state.pin("p1")!!.offsetVMm)
        assertEquals(before, m.state.history.size)
        assertEquals(0, m.count<RulerEffect.Persist>() + m.count<RulerEffect.RebaseAnchor>())
        assertTrue(m.logs.any { it.startsWith("PIN drag end id=p1") && it.endsWith("cancelled=true") })
        assertEquals("pin-selected-tuning", m.state.viewStateName)
        // no surface under the pointer at release: nothing either
        m.send(RulerAction.StartPinDrag("p1")).send(RulerAction.EndPinDrag("p1", null))
        assertEquals(before, m.state.history.size)
        assertNull(m.state.pinDrag)
    }

    @Test
    fun pinDrag_ontoAnotherPlane_reSeatsThePinWithAFreshAnchor_andUndoBringsItBack() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors().send(RulerAction.TapPin("p1"))
        val old = m.state.pin("p1")!!.anchorId!!
        val origin = m.state.pin("p1")!!.position
        // a wall 1.5 m ahead, facing the viewer
        val wall = Placement(Vec3(0.2f, 0.4f, -1.5f), Vec3(0f, 0f, 1f), Vec3.RIGHT, Vec3.UP, SurfaceKind.WALL, "WALL:w1000000", Grade.COARSE, SnapKind.NONE, 1.6f)
        m.clearEffects().send(RulerAction.StartPinDrag("p1")).send(RulerAction.EndPinDrag("p1", wall))
        val pin = m.state.pin("p1")!!
        assertNear(wall.point, pin.position, 1e-6f)
        assertNear(Vec3(0f, 0f, 1f), pin.normal, 1e-6f)
        assertEquals(SurfaceKind.WALL, pin.surface)
        assertEquals(SaveState.PENDING, pin.saveState) // "pinning" until its new anchor exists
        assertTrue(RulerEffect.RemoveAnchor(old) in m.effects)
        assertTrue(m.logs.any { it.startsWith("ANCHOR rebase start id=p1 uuid_old=$old") && it.endsWith("reason=drag-plane") })
        assertTrue(m.logs.any { it.startsWith("PIN drag end id=p1") && it.contains("plane=new") })
        val create = m.effects.filterIsInstance<RulerEffect.CreateAnchor>().single()
        assertNear(wall.point, create.position, 1e-6f)
        assertEquals(1, m.state.tapes.size) // the tape follows its pin
        m.clearEffects().send(RulerAction.AnchorSaved("p1", "anchor-wall", AnchorPose(wall.point)))
        assertEquals(SaveState.SAVED, m.state.pin("p1")!!.saveState)
        assertEquals("anchor-wall", m.state.pin("p1")!!.anchorId)
        // one undo: back on the floor, with a fresh anchor there
        m.clearEffects().send(RulerAction.Undo)
        val back = m.state.pin("p1")!!
        assertNear(origin, back.position, 1e-6f)
        assertNear(Vec3.UP, back.normal, 1e-6f)
        assertEquals(SaveState.PENDING, back.saveState)
        assertTrue(RulerEffect.RemoveAnchor("anchor-wall") in m.effects)
        assertNear(origin, m.effects.filterIsInstance<RulerEffect.CreateAnchor>().single().position, 1e-6f)
        assertEquals(1, m.state.tapes.size)
        m.send(RulerAction.AnchorSaved("p1", "anchor-floor", AnchorPose(origin)))
        assertEquals(SaveState.SAVED, m.state.pin("p1")!!.saveState)
        assertNear(origin, m.state.pin("p1")!!.position, 1e-6f)
    }

    @Test
    fun anAnchorAnsweredForAPlaceThePinHasLeft_isStale_andAnotherIsMade() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors().send(RulerAction.TapPin("p1"))
        val wall = Placement(Vec3(0.2f, 0.4f, -1.5f), Vec3(0f, 0f, 1f), Vec3.RIGHT, Vec3.UP, SurfaceKind.WALL, "WALL:w1000000", Grade.COARSE, SnapKind.NONE, 1.6f)
        m.send(RulerAction.StartPinDrag("p1")).send(RulerAction.EndPinDrag("p1", wall))
        val origin = Vec3(0f, 0f, -1f)
        m.send(RulerAction.Undo) // before the wall anchor was answered
        assertNear(origin, m.state.pin("p1")!!.position, 1e-6f)
        m.clearEffects().send(RulerAction.AnchorSaved("p1", "anchor-wall", AnchorPose(wall.point)))
        assertTrue(RulerEffect.RemoveAnchor("anchor-wall") in m.effects) // stale: made for the wall
        assertTrue(m.logs.any { it.startsWith("ANCHOR stale id=p1 uuid=anchor-wall") })
        assertNear(origin, m.state.pin("p1")!!.position, 1e-6f)
        assertNear(origin, m.effects.filterIsInstance<RulerEffect.CreateAnchor>().single().position, 1e-6f)
    }

    // ------------------------------------------------------------------ tracking, availability, dialogs

    @Test
    fun lostTracking_freezesTheSession_untilItComesBack() {
        val m = readyMachine().place(0f, 0f, -1f).place(1f, 0f, -1f).saveAnchors()
        m.send(RulerAction.TrackingChanged(true))
        assertTrue(m.state.trackingLost)
        assertEquals("tracking-lost", m.state.viewStateName)
        assertEquals(SnackKind.LOST, m.state.snack!!.kind)
        // nothing can be measured, and anchor poses reported now are not trusted
        m.place(2f, 0f, -1f).send(RulerAction.TapPin("p1")).send(RulerAction.StartPull(null, "p1"))
        m.send(RulerAction.AnchorMoved("anchor-1", AnchorPose(Vec3(5f, 0f, -1f))))
        assertEquals(2, m.state.pins.size)
        assertNull(m.state.armedPinId)
        assertNull(m.state.pull)
        assertNear(Vec3(0f, 0f, -1f), m.state.pin("p1")!!.position, 0f)
        // the message stands until tracking is back; a passing message waits behind it
        m.send(RulerAction.TapNothing(stillScanning = false))
        assertEquals(SnackKind.LOST, m.state.snack!!.kind)
        m.send(RulerAction.TrackingChanged(true)) // told twice: nothing new
        assertEquals(1, m.logs.count { it == "TRACK lost" })
        m.send(RulerAction.TrackingChanged(false))
        assertFalse(m.state.trackingLost)
        assertEquals(SnackKind.RECOVERED, m.state.snack!!.kind)
        assertTrue(m.state.snacks.none { it.kind == SnackKind.LOST })
        assertTrue(m.logs.any { it == "TRACK recovered" })
        m.place(2f, 0f, -1f)
        assertEquals(3, m.state.pins.size)
    }

    @Test
    fun trackingLostDuringAPull_letsTheTapeGo() {
        val m = twoPins().send(RulerAction.StartPull(null, "p1"))
        val steps = m.state.history.size
        m.send(RulerAction.TrackingChanged(true))
        assertNull(m.state.pull)
        assertEquals(steps - 1, m.state.history.size) // the pull left no undo step
    }

    @Test
    fun unavailableSpatialFeatures_blockTheSession_retryStartsAgain_exitLeaves() {
        val m = Machine().send(RulerAction.SettingsLoaded(RulerSettings(unit = RulerUnit.CM), RulerSettings()))
        m.send(RulerAction.Blocked(BlockReason.WITHOUT_PERMISSION))
        assertEquals(Phase.BLOCKED, m.state.phase)
        assertEquals("blocked", m.state.viewStateName)
        assertEquals(BlockReason.WITHOUT_PERMISSION, m.state.blockReason)
        assertTrue(m.logs.any { it == "BLOCKED reason=without_permission" })
        // the dialog is modal
        m.send(RulerAction.SetUnit(RulerUnit.MM)).send(RulerAction.ToggleMoreMenu)
        assertEquals(RulerUnit.CM, m.state.settings.unit)
        assertEquals(MenuState.CLOSED, m.state.menu)
        // planes that were seen while the dialog was up stay known after Retry: the scene reports them only on change
        m.send(RulerAction.PlanesChanged(7, scanning = false))
        m.send(RulerAction.RetryBlocked)
        assertEquals(Phase.STARTING, m.state.phase)
        assertTrue(m.state.settingsKnown)
        assertEquals(RulerUnit.CM, m.state.settings.unit)
        assertEquals(7, m.state.planeCount)
        assertFalse(m.state.scanning)
        assertEquals(1, m.count<RulerEffect.Restart>())
        // only a session that is starting can be blocked
        val ready = readyMachine().send(RulerAction.Blocked(BlockReason.UNKNOWN))
        assertEquals(Phase.READY, ready.state.phase)
        m.send(RulerAction.Blocked(BlockReason.NOT_IN_FULL_SPACE)).send(RulerAction.Exit)
        assertEquals(Phase.EXITED, m.state.phase)
        assertEquals(1, m.count<RulerEffect.ExitApp>())
    }

    @Test
    fun clearDialog_isModal_nothingElseReactsWhileItIsOpen() {
        val m = readyMachine().place(0f, 0f, -1f).send(RulerAction.RequestClearAll)
        assertEquals(DialogState.CONFIRM_CLEAR, m.state.dialog)
        m.place(1f, 0f, -1f)
            .send(RulerAction.TapPin("p1"))
            .send(RulerAction.SetUnit(RulerUnit.CM))
            .send(RulerAction.SetSnap(false))
            .send(RulerAction.Undo)
            .send(RulerAction.ToggleMoreMenu)
            .send(RulerAction.StartPull(placement(Vec3(2f, 0f, -1f)), null))
        assertEquals(1, m.state.pins.size)
        assertEquals(RulerUnit.M, m.state.settings.unit)
        assertTrue(m.state.settings.snap)
        assertEquals(MenuState.CLOSED, m.state.menu)
        assertNull(m.state.pull)
        assertEquals(DialogState.CONFIRM_CLEAR, m.state.dialog)
        // it closes through its own two buttons only
        m.send(RulerAction.DismissDialog).place(1f, 0f, -1f)
        assertEquals(2, m.state.pins.size)
        m.send(RulerAction.RequestClearAll).send(RulerAction.ConfirmClearAll)
        assertTrue(m.state.pins.isEmpty())
    }

    @Test
    fun storedPreferences_areKnownBeforeTheSession_andWhatIsSetMeanwhileSurvivesTheRestore() {
        val m = Machine()
        assertFalse(m.state.settingsKnown)
        m.send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.CLOSED, m.state.menu) // nothing can be shown in the right language yet
        m.send(RulerAction.SettingsLoaded(RulerSettings(unit = RulerUnit.FT_IN, language = LanguagePref.ZH), RulerSettings()))
        assertTrue(m.state.settingsKnown)
        assertEquals(Phase.STARTING, m.state.phase)
        assertEquals(RulerUnit.FT_IN, m.state.settings.unit)
        // while the scene is being built: more (language, exit), units and snap work
        m.send(RulerAction.ToggleMoreMenu)
        assertEquals(MenuState.MORE, m.state.menu)
        m.send(RulerAction.ToggleMoreMenu) // (a unit key with the menu open would only close it)
        m.send(RulerAction.SetUnit(RulerUnit.CM)).send(RulerAction.SetSnap(false))
        m.send(RulerAction.Restored(StoredSession(settings = RulerSettings(unit = RulerUnit.FT_IN)), emptyMap(), RulerSettings()))
        assertEquals(Phase.READY, m.state.phase)
        assertEquals(RulerUnit.CM, m.state.settings.unit)
        assertFalse(m.state.settings.snap)
        assertEquals(LanguagePref.ZH, m.state.settings.language)
        // first launch: nothing stored, the defaults for the system language apply
        val first = Machine().send(RulerAction.SettingsLoaded(null, RulerSettings(unit = RulerUnit.FT_IN)))
        assertEquals(RulerUnit.FT_IN, first.state.settings.unit)
        // told twice: the first answer stands
        first.send(RulerAction.SettingsLoaded(RulerSettings(unit = RulerUnit.MM), RulerSettings()))
        assertEquals(RulerUnit.FT_IN, first.state.settings.unit)
    }

    @Test
    fun pinsThatAreNotInThisSpace_areHidden_andCanBeForgotten() {
        val m = chain(4)
        m.clearEffects().send(RulerAction.MarkElsewhere(2))
        assertEquals(listOf("p1", "p2"), m.state.visiblePins.map { it.id })
        assertEquals(listOf("t1"), m.state.visibleTapes.map { it.id })
        assertEquals(4, m.state.pins.size) // kept in storage
        assertEquals(SnackKind.ELSEWHERE, m.state.snack!!.kind)
        assertEquals(2, m.state.snack!!.countA)
        assertEquals(0, m.count<RulerEffect.RemoveAnchor>()) // their anchors belong to the other space
        assertTrue(m.logs.any { it == "PIN elsewhere ids=p3,p4" })
        m.send(RulerAction.SnackAction(m.state.snack!!.id))
        assertEquals(listOf("p1", "p2"), m.state.pins.map { it.id })
        assertEquals(listOf("t1"), m.state.tapes.map { it.id })
    }

    @Test
    fun snappedPin_remembersItsSeat_forTheMarkItLandsWith() {
        val corner = placement(Vec3(2f, 0f, -4f), snapped = SnapKind.CORNER).copy(seatA = Vec3(-1f, 0f, 0f), seatB = Vec3(0f, 0f, 1f))
        val m = readyMachine().send(RulerAction.PlacePin(corner))
        assertEquals(SeatMark(SnapKind.CORNER, Vec3(-1f, 0f, 0f), Vec3(0f, 0f, 1f)), m.state.pin("p1")!!.seat)
        assertEquals(SnapKind.CORNER, m.state.pin("p1")!!.snapped)
        // plumb is an aiming aid: it leaves a mark, but the pin does not count as seated
        val plumb = placement(Vec3(0f, 0f, -1f), snapped = SnapKind.PLUMB).copy(seatA = Vec3.UP)
        m.send(RulerAction.PlacePin(plumb))
        assertEquals(SeatMark(SnapKind.PLUMB, Vec3.UP, null), m.state.pin("p2")!!.seat)
        assertEquals(SnapKind.NONE, m.state.pin("p2")!!.snapped)
        m.place(1f, 0f, -2f)
        assertNull(m.state.pin("p3")!!.seat)
        // a pin that comes back through undo does not land again
        m.send(RulerAction.RemovePin("p1")).send(RulerAction.Undo)
        assertNull(m.state.pin("p1")!!.seat)
    }
}
