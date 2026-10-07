package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure rules of a pin drag (round 5, reworked in round 5b): the hand step turns the line of sight by the
 * cursor's angle at any depth, the adaptive gain engages fine after 250 ms of stillness and lets go on the first
 * fast sample without losing motion, cancel radius, re-seat test, grade on release.
 */
class DragPinUseCaseTest {
    private companion object {
        const val FX = 1286.8f
    }

    private val rules = DragPinUseCase()

    private val right = Vec3(1f, 0f, 0f)
    private val up = Vec3(0f, 1f, 0f)
    private val forward = Vec3(0f, 0f, -1f)

    /** The system's travel for a cursor move of [px] framebuffer pixels (FX = 1286.8 px per unit of view-plane motion at unit depth) on a drag recognised [depth] metres in front of the view, [below] metres under the eye. */
    private fun systemTravel(px: Float, depth: Float, pitch: Float = 0f): Vec3 = Vec3(rules.equivalentDepth(depth, pitch) * px / FX, 0f, 0f)

    /** View-plane x of a direction at unit depth, in framebuffer pixels. */
    private fun viewPx(dir: Vec3): Float = dir.dot(right) / dir.dot(forward) * FX

    @Test
    fun aCursorMoveOfTheSamePixels_movesTheLineByThatMany_atOneAndAHalf_twoAndAHalf_andThreeAndAHalfMetres() {
        val px = 150f
        for (depth in listOf(1.4f, 2.5f, 3.5f)) {
            // a line 20 degrees right and 30 degrees down, as a pin on the floor would be seen
            val dir = Vec3(0.34f, -0.47f, -0.81f).normalized()
            val turned = rules.turn(dir, systemTravel(px, depth), rules.equivalentDepth(depth, 0f), right, up, forward)
            assertEquals("depth $depth", px, viewPx(turned) - viewPx(dir), 0.05f)
            assertEquals("depth $depth, no vertical motion", dir.dot(up) / dir.dot(forward), turned.dot(up) / turned.dot(forward), 1e-5f)
            // and the pin lands under the cursor: the line through the pin at that depth, moved by the cursor's pixels
            val onPlane = turned * (depth / turned.dot(forward))
            val before = dir * (depth / dir.dot(forward))
            assertEquals("depth $depth", depth * px / FX, (onPlane - before).x, 0.001f)
        }
    }

    @Test
    fun theMoveIsIndependentOfTheDepth_onlyTheSystemsTravelGrowsWithIt() {
        // the same cursor move of 100 px is reported as (0.74 + 1.31 d) metres per unit of view-plane motion: more travel at 3.5 m
        val near = systemTravel(100f, 1.4f)
        val far = systemTravel(100f, 3.5f)
        assertTrue(far.x > near.x * 1.6f)
        val dir = Vec3(0f, 0f, -1f)
        assertEquals(
            viewPx(rules.turn(dir, near, rules.equivalentDepth(1.4f, 0f), right, up, forward)),
            viewPx(rules.turn(dir, far, rules.equivalentDepth(3.5f, 0f), right, up, forward)),
            0.01f,
        )
        // the rule is the measured emulator model: level 2.33 m at 1.40 m, 3.64 m at 2.5 m, 4.82 m at 3.5 m; 0.15 % more per degree of head pitch
        assertEquals(DragPinUseCase.HAND_OFFSET_M + DragPinUseCase.HAND_PER_M * 1.4f, rules.equivalentDepth(1.4f, 0f), 1e-5f)
        assertEquals(2.33f, rules.equivalentDepth(1.40f, 0f), 0.05f)
        assertEquals(3.64f, rules.equivalentDepth(2.50f, 0f), 0.05f)
        assertEquals(4.82f, rules.equivalentDepth(3.50f, 0f), 0.05f)
        assertEquals(rules.equivalentDepth(2.5f, 0f) * 1.06f, rules.equivalentDepth(2.5f, -40f), 1e-4f)
        assertEquals(rules.equivalentDepth(2.5f, -20f), rules.equivalentDepth(2.5f, 20f), 1e-5f)
        assertEquals(0f, rules.equivalentDepth(0f, -10f), 0f)
    }

    @Test
    fun theStartLine_goesThroughTheCap_movedByTheAlongDragPartOfThePressOffset() {
        val head = Vec3(0f, 1.65f, 0f)
        val cap = Vec3(0f, 0.88f, -1.2f)
        // the system recognised the drag 3 cm to the right of the cap and 2 cm nearer (the front of the touch sphere), the hand goes right
        val press = Vec3(0.03f, 0.89f, -1.18f)
        val line = rules.startLine(head, cap, press, Vec3(0.01f, 0f, 0f), right, up, forward)
        val capLine = (cap - head).normalized()
        val pressLine = (press - head).normalized()
        // along: the press offset's x, less the 13 px the system's travel starts before the reported point
        assertEquals("along", viewPx(pressLine) - DragPinUseCase.START_SHORT_U * FX, viewPx(line), 0.3f)
        // a press offset smaller than that: the line stays at the cap
        val near = Vec3(0.005f, 0.89f, -1.19f)
        assertEquals(viewPx(capLine), viewPx(rules.startLine(head, cap, near, Vec3(0.01f, 0f, 0f), right, up, forward)), 0.3f)
        assertEquals("across: the cap's row", capLine.dot(up) / capLine.dot(forward), line.dot(up) / line.dot(forward), 1e-4f)
        // no hand yet: the line goes through the cap
        assertEquals(0f, rules.angleDeg(capLine, rules.startLine(head, cap, press, Vec3.ZERO, right, up, forward)), 1e-3f)
    }

    @Test
    fun turn_keepsTheDirectionUnit_andIgnoresAZeroStepOrABadDepth() {
        val dir = Vec3(0.3f, -0.4f, -0.866f).normalized()
        assertEquals(0f, rules.angleDeg(dir, rules.turn(dir, Vec3.ZERO, 3f, right, up, forward)), 1e-3f)
        assertEquals(0f, rules.angleDeg(dir, rules.turn(dir, Vec3(0.2f, 0f, 0f), 0f, right, up, forward)), 1e-3f)
        assertEquals(1f, rules.turn(dir, Vec3(0.2f, 0.1f, 0f), 3f, right, up, forward).length, 1e-5f)
    }

    @Test
    fun gain_isOneWhileTheHandMoves_andFineOnlyAfter400msWithoutAnyRawStep() {
        val g = AdaptiveGain()
        var t = 1000L
        g.begin(t)
        repeat(10) {
            t += 35
            assertEquals(1f, g.sample(10f, true, t))
        }
        // slow raw steps (the emulator's tail, 1 px events) keep cancelling the countdown
        repeat(10) {
            t += 120
            assertEquals(1f, g.sample(0.4f, true, t))
        }
        // sub-pixel events are no motion: the countdown runs from the last real step
        t += 350
        assertEquals(1f, g.sample(0.1f, false, t))
        t += 40
        assertEquals(1f, g.tick(t))
        t += 20
        assertEquals(0.25f, g.tick(t))
        assertTrue(g.fineOn)
        assertEquals("still", g.lastChange)
    }

    @Test
    fun gain_engagesFineWithoutAnySample_whenNoEventArrivesFor400ms() {
        val g = AdaptiveGain()
        g.begin(0L)
        g.sample(12f, true, 40L)
        assertEquals(1f, g.tick(430L))
        assertEquals(0.25f, g.tick(440L))
    }

    @Test
    fun aPauseInsideAMove_doesNotEngageFine() {
        // the emulator pauses its drag events for 100 - 200 ms inside a move (QA R5b-2)
        val g = AdaptiveGain()
        var t = 0L
        g.begin(t)
        repeat(8) {
            t += 35
            g.sample(12f, true, t)
        }
        t += 200
        assertEquals(1f, g.tick(t))
        t += 150
        assertEquals(1f, g.tick(t))
        t += 35
        assertEquals(1f, g.sample(12f, true, t))
    }

    @Test
    fun gain_letsGoOnTheFirstFastSample_withNoDelay_andNotOnASlowOne() {
        val g = AdaptiveGain()
        g.begin(0L)
        assertEquals(0.25f, g.tick(450L))
        // slow samples keep fine (hysteresis band up to 4 deg/s)
        assertEquals(0.25f, g.sample(3.9f, true, 485L))
        assertEquals(0.25f, g.sample(0f, false, 520L))
        // the first sample above 4 deg/s is applied at 1 : 1
        assertEquals(1f, g.sample(4.1f, true, 555L))
        assertFalse(g.fineOn)
        assertEquals("fast", g.lastChange)
        // and it takes a new 400 ms without a raw step to engage fine again
        assertEquals(1f, g.sample(0f, false, 590L))
        assertEquals(1f, g.tick(950L))
        assertEquals(0.25f, g.tick(960L))
    }

    @Test
    fun aQuickMoveRightAfterAPause_isAppliedInFull_noMotionIsLost() {
        // the raw line keeps the whole travel; the aim line follows it with the gain of each event: after a pause and
        // a fine stretch of tiny motion, a quick 100 px move (fast samples) turns the aim line by the same angle
        val g = AdaptiveGain()
        val depth = 2.5f
        val e = rules.equivalentDepth(depth, 0f)
        var raw = Vec3(0f, 0f, -1f)
        var aim = raw
        var t = 0L
        g.begin(t)
        t += 450
        assertEquals(0.25f, g.tick(t)) // paused: fine
        // tiny nudges while fine: 2 px each (about 0.1 degree), applied at 1 : 4
        repeat(3) {
            t += 35
            val step = systemTravel(2f, depth)
            val turned = rules.turn(raw, step, e, right, up, forward)
            val speed = rules.angularSpeedDegPerS(raw, turned, 35L)
            val gain = g.sample(speed, true, t)
            assertEquals(0.25f, gain)
            raw = turned
            aim = rules.turn(aim, step * gain, e, right, up, forward)
        }
        val before = aim
        // the quick move: 4 events of 25 px (about 1.1 degrees each); the first comes 500 ms after the last nudge and is
        // judged over the 100 ms window (11 deg/s), not over the rest (2.2 deg/s): it goes 1 : 1 as well
        repeat(4) {
            t += if (it == 0) 500 else 35
            val step = systemTravel(25f, depth)
            val turned = rules.turn(raw, step, e, right, up, forward)
            val speed = rules.angularSpeedDegPerS(raw, turned, minOf(if (it == 0) 500L else 35L, AdaptiveGain.SPEED_WINDOW_MS))
            val gain = g.sample(speed, true, t)
            assertEquals("event $it", 1f, gain)
            raw = turned
            aim = rules.turn(aim, step * gain, e, right, up, forward)
        }
        assertEquals(100f, viewPx(aim) - viewPx(before), 0.05f)
        // the raw line is 6 px ahead of the aim line (the three nudges at 1 : 4 kept 1.5 of them)
        assertEquals(6f * 0.75f, viewPx(raw) - viewPx(aim), 0.05f)
    }

    @Test
    fun aReleaseWithinTenMillimetres_isNoMove() {
        assertTrue(rules.cancelled(Vec3(0f, 0f, -1f), Vec3(0.009f, 0f, -1.003f)))
        assertFalse(rules.cancelled(Vec3(0f, 0f, -1f), Vec3(0.011f, 0f, -1f)))
    }

    @Test
    fun aTargetOnTheSamePlane_isAnOffset_anotherPlaneReSeatsThePin() {
        val pos = Vec3(0f, 0f, -1f)
        assertTrue(rules.samePlane(pos, Vec3.UP, Vec3(0.3f, 0.004f, -1.2f), Vec3.UP))
        assertFalse(rules.samePlane(pos, Vec3.UP, Vec3(0.3f, 0.02f, -1.2f), Vec3.UP)) // 2 cm above the floor: a table top
        assertFalse(rules.samePlane(pos, Vec3.UP, Vec3(0.3f, 0f, -1.2f), Vec3(0f, 0f, 1f))) // a wall at the same height
    }

    @Test
    fun gradeOnRelease() {
        assertEquals(Grade.CONTACT, rules.gradeOnRelease(contact = true, fine = true))
        assertEquals(Grade.FINE, rules.gradeOnRelease(contact = false, fine = true))
        assertEquals(Grade.COARSE, rules.gradeOnRelease(contact = false, fine = false))
    }

    @Test
    fun angularSpeed() {
        assertEquals(90f, rules.angularSpeedDegPerS(Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f), 1000L), 0.01f)
        assertEquals(0f, rules.angularSpeedDegPerS(Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f), 0L), 0f)
        assertTrue(abs(rules.angleDeg(Vec3(0f, 0f, -1f), Vec3(0f, 0f, -1f))) < 1e-3f)
    }
}
