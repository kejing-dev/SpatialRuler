package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPointerTest {

    // ------------------------------------------------------------------ source selection

    @Test
    fun source_isTheController_thenTheHand_thenTheSystemPointer() {
        val select = SelectPointerSourceUseCase()
        assertEquals(PointerChannel.CONTROLLER, select.select(null, controller = true, hand = true, synthetic = false))
        assertEquals(PointerChannel.HAND, select.select(null, controller = false, hand = true, synthetic = false))
        assertEquals(PointerChannel.SYSTEM, select.select(null, controller = false, hand = false, synthetic = false))
        // a test ray goes first while it is set
        assertEquals(PointerChannel.SYNTHETIC, select.select(null, controller = true, hand = true, synthetic = true))
    }

    @Test
    fun forcedSource_isUsedWhenItDelivers_otherwiseTheSystemPointerStaysInCharge() {
        val select = SelectPointerSourceUseCase()
        assertEquals(PointerChannel.HAND, select.select(PointerChannel.HAND, controller = true, hand = true, synthetic = true))
        assertEquals(PointerChannel.SYSTEM, select.select(PointerChannel.HAND, controller = true, hand = false, synthetic = true))
        assertEquals(PointerChannel.SYSTEM, select.select(PointerChannel.CONTROLLER, controller = false, hand = true, synthetic = false))
        assertEquals(PointerChannel.SYSTEM, select.select(PointerChannel.SYSTEM, controller = true, hand = true, synthetic = true))
        assertEquals(PointerChannel.SYNTHETIC, select.select(PointerChannel.SYNTHETIC, controller = true, hand = true, synthetic = true))
        assertEquals(PointerChannel.CONTROLLER, PointerChannel.fromTag("controller"))
        assertNull(PointerChannel.fromTag("auto"))
    }

    // ------------------------------------------------------------------ stability

    @Test
    fun aim_isSteady_afterItHasStayedWithinItsCircleForThirtyFiveHundredths() {
        val aim = SteadyAim()
        val p = Vec3(0f, 0f, -2f)
        assertNear(0f, aim.update(p, 2f, 1_000))
        assertNear(0.5f, aim.update(p + Vec3(0.003f, 0f, 0f), 2f, 1_175), 1e-3f) // 3 mm at 2 m: inside 5.2 mm
        assertFalse(aim.steady)
        assertNear(1f, aim.update(p, 2f, 1_350))
        assertTrue(aim.steady)
        // it stays steady while it stays there
        aim.update(p, 2f, 5_000)
        assertTrue(aim.steady)
    }

    @Test
    fun aimThatMoves_startsOver_andAnAimAtNothingIsNotSteady() {
        val aim = SteadyAim()
        val p = Vec3(0f, 0f, -2f)
        aim.update(p, 2f, 1_000)
        aim.update(p, 2f, 1_400)
        assertTrue(aim.steady)
        // 8 mm at 2 m is outside the circle
        assertNear(0f, aim.update(p + Vec3(0.008f, 0f, 0f), 2f, 1_420))
        assertFalse(aim.steady)
        aim.update(p + Vec3(0.008f, 0f, 0f), 2f, 1_800)
        assertTrue(aim.steady)
        assertNear(0f, aim.update(null, 0f, 1_900))
        assertFalse(aim.steady)
        // close up the circle is never smaller than 2 mm
        val near = SteadyAim()
        near.update(Vec3(0f, 0f, -0.2f), 0.2f, 0)
        assertTrue(near.update(Vec3(0.0015f, 0f, -0.2f), 0.2f, 100) > 0f)
    }

    // ------------------------------------------------------------------ commit window

    @Test
    fun press_commitsTheMedianOfTheAim_fortyToHundredAndTwentyMillisecondsBefore() {
        val window = CommitWindow()
        // 90 Hz: the aim rests at x = 0, then the trigger pull drags it away during the last 40 ms
        var t = 0L
        while (t <= 960) {
            window.add(Vec3(0f, 0f, -2f), t)
            t += 11
        }
        window.add(Vec3(0.02f, 0f, -2f), 975)
        window.add(Vec3(0.05f, 0f, -2f), 990)
        assertNear(Vec3(0f, 0f, -2f), window.commit(1_000)!!)
        // one outlier inside the window does not move the median
        val noisy = CommitWindow()
        listOf(0.001f, 0.002f, 0.3f, 0.003f, 0.002f).forEachIndexed { i, x -> noisy.add(Vec3(x, 0f, -2f), 900L + i * 11) }
        assertNear(0.002f, noisy.commit(1_000)!!.x)
    }

    @Test
    fun commitWindow_atASlowFrameRate_takesTheLastAimBeforeThePress() {
        val window = CommitWindow()
        assertNull(window.commit(1_000))
        window.add(Vec3(1f, 0f, 0f), 400)
        window.add(Vec3(2f, 0f, 0f), 700)
        // nothing between 880 and 960: the newest sample older than 40 ms
        assertNear(Vec3(2f, 0f, 0f), window.commit(1_000)!!)
        // only a sample from the last 40 ms: better than nothing
        val late = CommitWindow()
        late.add(Vec3(3f, 0f, 0f), 990)
        assertNear(Vec3(3f, 0f, 0f), late.commit(1_000)!!)
        window.clear()
        assertNull(window.commit(1_000))
    }

    // ------------------------------------------------------------------ fine aim

    @Test
    fun halfPress_turnsTheRayAQuarterAsFar_fromWhereItPointedWhenItBegan() {
        val fine = FineAim()
        val start = PointerRay(Vec3(0f, 1f, 0f), Vec3(0f, 0f, -1f))
        assertEquals(start, fine.apply(start, fine = false))
        assertFalse(fine.active)
        assertEquals(start, fine.apply(start, fine = true))
        assertTrue(fine.active)
        // the controller turns 0.04 rad to the right and moves 4 cm up
        val moved = PointerRay(Vec3(0f, 1.04f, 0f), Vec3(0.04f, 0f, -1f).normalized())
        val shown = fine.apply(moved, fine = true)
        assertNear(0.01f, shown.direction.x / -shown.direction.z, 1e-4f)
        assertNear(Vec3(0f, 1.01f, 0f), shown.origin, 1e-5f)
        // released: the ray is the controller's own again
        assertEquals(moved, fine.apply(moved, fine = false))
        assertFalse(fine.active)
    }

    // ------------------------------------------------------------------ trigger and pinch

    @Test
    fun trigger_hasThreeStages_andCommitsOnceWhenItBecomesFullyPressed() {
        val trigger = TriggerStages()
        assertFalse(trigger.update(0.05f))
        assertEquals(TriggerStage.RELEASED, trigger.stage)
        assertFalse(trigger.update(0.3f))
        assertEquals(TriggerStage.HALF, trigger.stage)
        assertTrue(trigger.update(0.9f))
        assertEquals(TriggerStage.FULL, trigger.stage)
        assertFalse(trigger.update(1f)) // held: no second commit
        // hysteresis: 70 % is still fully pressed, 50 % is half, 10 % is still half, 5 % is released
        trigger.update(0.7f)
        assertEquals(TriggerStage.FULL, trigger.stage)
        trigger.update(0.5f)
        assertEquals(TriggerStage.HALF, trigger.stage)
        trigger.update(0.1f)
        assertEquals(TriggerStage.HALF, trigger.stage)
        trigger.update(0.05f)
        assertEquals(TriggerStage.RELEASED, trigger.stage)
        // a controller that only says "pressed" commits without a half stage
        assertTrue(trigger.update(0f, pressed = true))
    }

    @Test
    fun pinch_commitsOnceWhenItCloses_andOnlyWhileTheHandIsReady() {
        val pinch = PinchLatch()
        assertFalse(pinch.update(0.5f, ready = true))
        assertTrue(pinch.update(0.85f, ready = true))
        assertFalse(pinch.update(0.9f, ready = true))
        assertFalse(pinch.update(0.6f, ready = true)) // still closed: above 55 %
        assertTrue(pinch.pressed)
        pinch.update(0.5f, ready = true)
        assertFalse(pinch.pressed)
        // a hand that is not ready does not press, however closed the fingers are
        assertFalse(pinch.update(1f, ready = false))
        assertFalse(pinch.pressed)
        assertTrue(pinch.update(1f, ready = true))
    }

    // ------------------------------------------------------------------ grade and contact

    @Test
    fun grade_isContact_thenFineWhenSteadyOrHalfPressed_elseCoarse() {
        val grade = GradeAimUseCase()
        assertEquals(Grade.CONTACT, grade.grade(contact = true, steady = false, halfPress = false))
        assertEquals(Grade.FINE, grade.grade(contact = false, steady = true, halfPress = false))
        assertEquals(Grade.FINE, grade.grade(contact = false, steady = false, halfPress = true))
        assertEquals(Grade.COARSE, grade.grade(contact = false, steady = false, halfPress = false))
    }

    @Test
    fun toolTip_isInContact_withinTwelveCentimetresOfASurface() {
        val contact = FindContactUseCase()
        val near = contact.find(Vec3(0.5f, 0.11f, -2f), Room.HEAD, Room.all)!!
        assertEquals(Room.floor.id, near.plane.id)
        assertNear(0.11f, near.distance, 1e-5f)
        assertNear(Vec3.UP, near.normal)
        assertNull(contact.find(Vec3(0.5f, 0.13f, -2f), Room.HEAD, Room.all))
        // a tip a little behind the fitted wall still belongs to it, and the normal faces the viewer
        val wall = contact.find(Vec3(0f, 1f, -4.01f), Room.HEAD, Room.all)!!
        assertEquals(Room.wall.id, wall.plane.id)
        assertNear(Vec3(0f, 0f, 1f), wall.normal)
        // beside the table top (more than 5 cm off its edge) the table is not in reach; of two surfaces the nearer wins
        assertNull(contact.find(Vec3(0.6f, 0.8f, -1.2f), Room.HEAD, listOf(Room.table)))
        val corner = contact.find(Vec3(0f, 0.03f, -3.95f), Room.HEAD, Room.all)!!
        assertEquals(Room.floor.id, corner.plane.id)
    }
}
