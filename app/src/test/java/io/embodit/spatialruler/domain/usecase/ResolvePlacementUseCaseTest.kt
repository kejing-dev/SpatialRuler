package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvePlacementUseCaseTest {
    private val resolve = ResolvePlacementUseCase()

    private fun rayTo(point: Vec3) = PointerRay(Room.HEAD, (point - Room.HEAD).normalized())

    @Test
    fun rayOntoTheFloor_givesAPlacementWithSurfaceGradeAndRange() {
        val target = Vec3(0.5f, 0f, -2f)
        val aim = resolve.aim(rayTo(target), Room.all, emptyList(), snapEnabled = true, tapeHead = null, grade = Grade.FINE)
        val p = (aim as AimResult.OnSurface).placement
        assertNear(target, p.point)
        assertNear(Vec3.UP, p.normal)
        assertEquals(SurfaceKind.FLOOR, p.surface)
        assertEquals("FLOOR:f1000000", p.planeLabel)
        assertEquals(Grade.FINE, p.grade)
        assertEquals(SnapKind.NONE, p.snapped)
        assertNear(Room.HEAD.distanceTo(target), p.rangeM)
    }

    @Test
    fun rayAtAPin_selectsThePin_andRayIntoNothing_isNothing() {
        val existing = pin("p1", Vec3(0.5f, 0f, -2f))
        val aim = resolve.aim(rayTo(Vec3(0.5f, 0.018f, -2f)), Room.all, listOf(existing), true, null, Grade.COARSE)
        assertEquals("p1", (aim as AimResult.OnPin).pin.id)
        assertEquals(AimResult.Nothing, resolve.aim(PointerRay(Room.HEAD, Vec3.UP), Room.all, emptyList(), true, null, Grade.COARSE))
    }

    @Test
    fun snapping_canBeSwitchedOff() {
        val nearCorner = rayTo(Vec3(1.97f, 0f, -3.97f))
        val on = resolve.aim(nearCorner, Room.all, emptyList(), snapEnabled = true, tapeHead = null, grade = Grade.COARSE) as AimResult.OnSurface
        assertEquals(SnapKind.CORNER, on.placement.snapped)
        assertNear(Vec3(2f, 0f, -4f), on.placement.point)
        val off = resolve.aim(nearCorner, Room.all, emptyList(), snapEnabled = false, tapeHead = null, grade = Grade.COARSE) as AimResult.OnSurface
        assertEquals(SnapKind.NONE, off.placement.snapped)
        assertNear(Vec3(1.97f, 0f, -3.97f), off.placement.point, 1e-3f)
    }

    @Test
    fun systemTapOnTheFloorCollider_isPutBackOntoThePlaneAlongTheViewingRay() {
        // the collider is a thin box: the tap lands 3 mm above the floor
        val p = resolve.onTappedSurface(Room.floor, Vec3(0.5f, 0.003f, -2f), Room.HEAD, Room.all, snapEnabled = false, tapeHead = null, grade = Grade.COARSE)
        assertNear(0f, p.point.y, 1e-5f)
        assertNear(Vec3(0.501002f, 0f, -2.004008f), p.point, 1e-4f)
        assertNear(Vec3.UP, p.normal)
        assertEquals(Grade.COARSE, p.grade)
        // without a head pose the point is projected straight onto the plane
        val blind = resolve.onTappedSurface(Room.floor, Vec3(0.5f, 0.003f, -2f), null, Room.all, false, null, Grade.COARSE)
        assertNear(Vec3(0.5f, 0f, -2f), blind.point)
    }

    @Test
    fun theBladeIsAttachedUnderTheFlangeOfThePin() {
        assertNear(0.0145f, ResolvePlacementUseCase.PIN_HEAD_M, 0f)
    }

    @Test
    fun systemTap_snapsByThePointerRule_aTrackedRayByTheAngularRule() {
        // 49 mm from the floor corner at 4.7 m (35 mm from either edge): within 0.9 degrees, beyond the caps
        val target = Vec3(1.965f, 0f, -3.965f)
        val tapped = resolve.onTappedSurface(Room.floor, target, Room.HEAD, Room.all, snapEnabled = true, tapeHead = null, grade = Grade.COARSE)
        assertEquals(SnapKind.NONE, tapped.snapped)
        assertNear(target, tapped.point, 1e-4f)
        assertEquals(null, tapped.seatA)
        val aimed = resolve.aim(rayTo(target), Room.all, emptyList(), snapEnabled = true, tapeHead = null, grade = Grade.COARSE) as AimResult.OnSurface
        assertEquals(SnapKind.CORNER, aimed.placement.snapped)
        assertNear(Vec3(2f, 0f, -4f), aimed.placement.point)
        // a tap 3 cm from the corner is taken to it, and the placement says along which edges the seat lies
        val close = resolve.onTappedSurface(Room.floor, Vec3(1.98f, 0.003f, -3.98f), Room.HEAD, Room.all, true, null, Grade.COARSE)
        assertEquals(SnapKind.CORNER, close.snapped)
        assertNear(Vec3(2f, 0f, -4f), close.point)
        assertTrue(close.seatA != null && close.seatB != null)
    }

    @Test
    fun aimCanLeaveOutOnePin_theHeadOfTheTapeThatIsBeingPulled() {
        val head = pin("p1", Vec3(0.5f, 0f, -2f))
        val ray = rayTo(Vec3(0.5f, 0.0145f, -2f))
        assertEquals("p1", (resolve.aim(ray, Room.all, listOf(head), true, null, Grade.COARSE) as AimResult.OnPin).pin.id)
        val past = resolve.aim(ray, Room.all, listOf(head), true, null, Grade.COARSE, exclude = "p1")
        assertTrue(past is AimResult.OnSurface)
    }

    @Test
    fun systemTapOnAWall_facesTheViewer_whateverTheQuadWinding() {
        assertTrue(Room.wall.normal.z < 0f)
        val p = resolve.onTappedSurface(Room.wall, Vec3(0.3f, 1.2f, -3.997f), Room.HEAD, Room.all, false, null, Grade.COARSE)
        assertNear(-4f, p.point.z, 1e-5f)
        assertNear(Vec3(0f, 0f, 1f), p.normal)
        assertEquals(SurfaceKind.WALL, p.surface)
        // nudge axes on a wall: left / right along it, and up
        assertNear(Vec3.RIGHT, p.uAxis)
        assertNear(Vec3.UP, p.vAxis)
    }

    @Test
    fun pointGivenDirectly_attachesToASurfaceWithinFiveCentimetres() {
        val onFloor = resolve.atPoint(Vec3(1f, 0.02f, -1f), Room.HEAD, Room.all, Grade.COARSE)
        assertNear(Vec3(1f, 0f, -1f), onFloor.point)
        assertEquals(SurfaceKind.FLOOR, onFloor.surface)
        val inTheAir = resolve.atPoint(Vec3(0f, 1f, -1f), Room.HEAD, Room.all, Grade.COARSE)
        assertNear(Vec3(0f, 1f, -1f), inTheAir.point)
        assertEquals("FREE", inTheAir.planeLabel)
        assertEquals(SurfaceKind.OTHER, inTheAir.surface)
    }

    @Test
    fun surfaceAxes_onAHorizontalSurface_areRightAndAwayFromTheViewer() {
        val (u, v) = ResolvePlacementUseCase.surfaceAxes(Vec3.UP, Vec3.AHEAD)
        assertNear(Vec3.RIGHT, u)
        assertNear(Vec3.AHEAD, v)
        // seen from below (a ceiling), the axes stay on the surface
        val (cu, cv) = ResolvePlacementUseCase.surfaceAxes(Vec3(0f, -1f, 0f), Vec3.AHEAD)
        assertNear(0f, cu.y)
        assertNear(0f, cv.y)
        assertNear(Vec3(0f, 0f, 1f), ResolvePlacementUseCase.facing(Vec3(0f, 0f, -1f), Vec3(0f, 0f, 3f)))
    }
}
