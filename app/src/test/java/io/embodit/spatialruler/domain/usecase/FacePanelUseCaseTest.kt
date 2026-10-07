package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.PI
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

class FacePanelUseCaseTest {
    private val facing = FacePanelUseCase()
    private val surfaces = ClearSurfaceUseCase()

    @Test
    fun panelAtEyeHeight_turnsAboutTheVerticalOnly() {
        val ahead = facing.facing(Vec3(0f, 1f, -2f), Vec3(0f, 1f, 0f), null)
        assertNear(0f, ahead.yaw)
        assertNear(0f, ahead.pitch)
        assertNear(Vec3(0f, 0f, 1f), ahead.normal)
        val side = facing.facing(Vec3(0f, 1f, -2f), Vec3(2f, 1f, -2f), null)
        assertNear((PI / 2).toFloat(), side.yaw, 1e-5f)
        assertNear(Vec3.RIGHT, side.normal, 1e-5f)
        assertNear(Vec3(0f, 0f, -1f), side.right, 1e-5f)
    }

    @Test
    fun panelBelowTheHead_pitchesTowardsIt() {
        val f = facing.facing(Vec3(0f, 0f, -1f), Vec3(0f, 1f, 0f), null)
        assertNear(0f, f.yaw)
        assertNear((PI / 4).toFloat(), f.pitch, 1e-5f)
        val k = 1f / sqrt(2f)
        assertNear(Vec3(0f, k, k), f.normal, 1e-5f)
        assertNear(Vec3(0f, k, -k), f.up, 1e-5f)
        // the edges stay level: no roll
        assertNear(0f, f.right.y)
    }

    @Test
    fun pitch_isClampedAtSixtyDegrees() {
        val f = facing.facing(Vec3(0f, 0f, -1f), Vec3(0f, 5f, -0.5f), null)
        assertNear(FacePanelUseCase.MAX_PITCH_RAD, f.pitch, 1e-6f)
        val below = facing.facing(Vec3(0f, 3f, -1f), Vec3(0f, 0f, -0.5f), null)
        assertNear(-FacePanelUseCase.MAX_PITCH_RAD, below.pitch, 1e-6f)
    }

    @Test
    fun headAlmostStraightAbove_keepsTheLastYaw() {
        val panel = Vec3(0f, 0f, -1f)
        val head = Vec3(0.05f, 2f, -1f) // 5 cm from the vertical through the panel
        assertNear(1f, facing.facing(panel, head, lastYaw = 1f).yaw)
        // no yaw yet: the direction decides
        assertNear((PI / 2).toFloat(), facing.facing(panel, head, lastYaw = null).yaw, 1e-4f)
        // 20 cm away: the yaw follows the head again
        assertNear((PI / 2).toFloat(), facing.facing(panel, Vec3(0.2f, 2f, -1f), lastYaw = 1f).yaw, 1e-4f)
    }

    @Test
    fun rotation_carriesThePanelAxesOntoRightUpAndNormal() {
        val f = facing.facing(Vec3(0.4f, 0.2f, -1.5f), Vec3(-0.3f, 1.5f, 0.2f), null)
        assertNear(f.right, f.rotation.rotate(Vec3.RIGHT), 1e-5f)
        assertNear(f.up, f.rotation.rotate(Vec3.UP), 1e-5f)
        assertNear(f.normal, f.rotation.rotate(Vec3(0f, 0f, 1f)), 1e-5f)
    }

    // ------------------------------------------------------------------ surface rule

    private fun corners(head: Vec3, toCenter: Vec3, range: Float, halfWidthPerM: Float, halfHeightPerM: Float): List<Vec3> {
        val center = head + toCenter * range
        val f = facing.facing(center, head, null)
        val out = ArrayList<Vec3>()
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) out += center + f.right * (sx * halfWidthPerM * range) + f.up * (sy * halfHeightPerM * range)
        return out
    }

    @Test
    fun panelThatWouldSinkIntoTheFloor_isPulledTowardsTheHead_untilItsCornersAreTwoCentimetresClear() {
        // aimed at the floor point 2 m ahead: its centre would lie on the floor
        val toCenter = (Vec3(0f, 0f, -2f) - Room.HEAD).normalized()
        val range = surfaces.maxRange(Room.HEAD, toCenter, 2.5f, 0.05f, 0.03f, Room.all, facing)
        assertTrue("pulled in: $range", range < 2.5f)
        val lowest = corners(Room.HEAD, toCenter, range, 0.05f, 0.03f).minOf { it.y }
        assertTrue("lowest corner $lowest", lowest >= ClearSurfaceUseCase.CLEARANCE_M - 1e-3f)
        assertTrue("not pulled further than needed: $lowest", lowest <= ClearSurfaceUseCase.CLEARANCE_M + 0.01f)
    }

    @Test
    fun panelClearOfEverySurface_staysWhereItIs() {
        val toCenter = (Vec3(0f, 1f, -2f) - Room.HEAD).normalized()
        assertNear(2f, surfaces.maxRange(Room.HEAD, toCenter, 2f, 0.05f, 0.03f, Room.all, facing))
    }

    @Test
    fun surfaceWhoseQuadDoesNotReachUnderThePanel_isIgnored() {
        // the floor ends at z = -4: a panel 6 m ahead and below floor level is not above that quad
        val target = Vec3(0f, -0.2f, -6f)
        val range = target.distanceTo(Room.HEAD)
        val toCenter = (target - Room.HEAD).normalized()
        assertNear(range, surfaces.maxRange(Room.HEAD, toCenter, range, 0.01f, 0.01f, listOf(Room.floor), facing))
    }

    @Test
    fun surfaceTheHeadIsNotClearOf_isIgnored() {
        // the head lies in the plane of this "floor": nothing can be on the head's side of it
        val head = Vec3(0f, 0.01f, 0f)
        val toCenter = Vec3(0f, -0.2f, -1f).normalized()
        assertNear(1.5f, surfaces.maxRange(head, toCenter, 1.5f, 0.05f, 0.03f, listOf(Room.floor), facing))
    }

    @Test
    fun panelAboveAShelf_onlyComesInFrontOfTheShelf() {
        // a shelf 0.3 m deep on the far wall, 2 m up; the panel hangs on the wall above it, the head is below
        val shelf =
            PlaneSurface(
                "5he1f000-0000", SurfaceKind.TABLE, "CABINET",
                listOf(Vec3(-1f, 2f, -4f), Vec3(1f, 2f, -4f), Vec3(1f, 2f, -3.7f), Vec3(-1f, 2f, -3.7f)),
                listOf(0, 1, 2, 0, 2, 3),
            )
        val target = Vec3(0f, 2.3f, -3.97f)
        val wanted = target.distanceTo(Room.HEAD)
        val toCenter = (target - Room.HEAD).normalized()
        val range = surfaces.maxRange(Room.HEAD, toCenter, wanted, 0.02f, 0.02f, Room.all + shelf, facing)
        // its corners' foot points have left the shelf (grown by 5 cm): 0.35 m in front of the wall, and no more
        val nearest = corners(Room.HEAD, toCenter, range, 0.02f, 0.02f).minOf { it.z }
        assertTrue("in front of the shelf: $nearest", nearest > -3.65f)
        assertTrue("not pulled further than needed: $nearest", nearest < -3.55f)
        // (where its ray passes the height of the shelf would be 2.5 m from the head)
        assertTrue("range $range", range > 3.5f)
    }

    @Test
    fun wallBehindThePanel_stopsItTwoCentimetresInFront() {
        // straight at the far wall (z = -4), 5 cm into it
        val toCenter = Vec3.AHEAD
        val range = surfaces.maxRange(Room.HEAD, toCenter, 4.05f, 0.02f, 0.02f, Room.all, facing)
        assertNear(3.98f, range, 2e-3f)
    }
}
