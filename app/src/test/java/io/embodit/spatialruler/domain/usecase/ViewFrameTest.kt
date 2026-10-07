package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewFrameTest {
    private val level = ViewFrame(ViewPose(Room.HEAD, Quat4.IDENTITY))

    @Test
    fun pointStraightAhead_isAtTheCentreOfTheView() {
        val v = level.see(Vec3(0f, 1.5f, -2f))!!
        assertNear(0f, v.yaw)
        assertNear(0f, v.pitch)
        assertNear(2f, v.range)
    }

    @Test
    fun yawIsToTheRight_pitchIsUpwards_inDegrees() {
        assertNear(45f, level.see(Vec3(1f, 1.5f, -1f))!!.yaw, 1e-3f)
        assertNear(-45f, level.see(Vec3(-1f, 1.5f, -1f))!!.yaw, 1e-3f)
        assertNear(45f, level.see(Vec3(0f, 2.5f, -1f))!!.pitch, 1e-3f)
        assertNear(-36.87f, level.see(Vec3(0f, 0f, -2f))!!.pitch, 1e-2f)
    }

    @Test
    fun pointBehindTheViewer_orAtTheEye_isNotSeen() {
        assertNull(level.see(Vec3(0f, 1.5f, 1f)))
        assertNull(level.see(Room.HEAD))
    }

    @Test
    fun directionAndPoint_areTheInverseOfSee() {
        val p = level.point(30f, -20f, 2f)
        val v = level.see(p)!!
        assertNear(30f, v.yaw, 1e-3f)
        assertNear(-20f, v.pitch, 1e-3f)
        assertNear(2f, v.range, 1e-4f)
        assertNear(Vec3.AHEAD, level.direction(0f, 0f))
    }

    @Test
    fun aTurnedHead_seesWhatIsInFrontOfIt() {
        // turned a quarter to the left: looking along -X
        val turned = ViewFrame(ViewPose(Room.HEAD, Quat4.axisAngle(Vec3.UP, (Math.PI / 2).toFloat())))
        assertNear(Vec3(-1f, 0f, 0f), turned.pose.forward)
        val v = turned.see(Vec3(-2f, 1.5f, 0f))!!
        assertNear(0f, v.yaw, 1e-3f)
        assertNear(2f, v.range)
        // what lies towards -Z is now on its right
        assertNear(45f, turned.see(Vec3(-2f, 1.5f, -2f))!!.yaw, 1e-3f)
        assertNull(turned.see(Vec3(2f, 1.5f, 0f)))
    }

    @Test
    fun visualAngle_ofALengthSeenSquareOn() {
        assertNear(5.7106f, ViewFrame.angle(0.1f, 1f), 1e-3f)
        assertNear(45f, ViewFrame.angle(2f, 2f), 1e-3f)
    }

    @Test
    fun rect_isCentredOnThePoint_withHalfSizesAsAngles() {
        val r = level.rect(Vec3(0f, 1.5f, -2f), 0.2f, 0.1f)!!
        assertNear(0f, r.yaw)
        assertNear(ViewFrame.angle(0.2f, 2f), r.halfYaw)
        assertNear(ViewFrame.angle(0.1f, 2f), r.halfPitch)
        val box = level.box(Vec3(0f, 1.5f, -2f), 1.5f)!!
        assertNear(1.5f, box.halfYaw)
        assertNear(1.5f, box.halfPitch)
        assertNull(level.rect(Vec3(0f, 1.5f, 2f), 0.2f, 0.1f))
    }

    @Test
    fun bounds_coverBothEndsOfASegment() {
        val b = level.bounds(Vec3(-1f, 1.5f, -2f), Vec3(1f, 1.5f, -2f), 0.5f)!!
        assertNear(0f, b.yaw, 1e-3f)
        assertNear(26.565f + 0.5f, b.halfYaw, 1e-2f)
        assertNear(0.5f, b.halfPitch, 1e-3f)
    }

    @Test
    fun rectangles_overlapByArea_touchingIsNotOverlapping() {
        val a = ViewRect(0f, 0f, 2f, 1f)
        assertNear(8f, a.area)
        assertNear(2f, a.overlap(ViewRect(3f, 0f, 2f, 1f))) // 1 wide, 2 high
        assertFalse(a.intersects(ViewRect(4f, 0f, 2f, 1f))) // edges touch
        assertTrue(a.inflated(0.5f).intersects(ViewRect(4f, 0f, 2f, 1f)))
        assertNear(2f, a.movedBy(3f, 0f).overlap(a))
    }

    @Test
    fun segment_isClippedToTheRectangle() {
        val r = ViewRect(0f, 0f, 2f, 1f)
        val through = r.clipSegment(-5f, 0f, 5f, 0f, 0f)!!
        assertNear(0.3f, through.first, 1e-4f)
        assertNear(0.7f, through.second, 1e-4f)
        assertNull(r.clipSegment(-5f, 3f, 5f, 3f, 0f))
        // a thick segment reaches it
        assertNotNull(r.clipSegment(-5f, 3f, 5f, 3f, 2.5f))
        assertTrue(r.meetsSegment(-5f, -5f, 5f, 5f, 0f))
        assertFalse(r.meetsSegment(3f, -5f, 3f, 5f, 0.5f))
        // a segment that ends inside
        val inside = r.clipSegment(0f, 0f, 4f, 0f, 0f)!!
        assertEquals(0f, inside.first)
        assertNear(0.5f, inside.second, 1e-4f)
    }
}
