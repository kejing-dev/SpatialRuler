package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.SurfaceHit
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapPointUseCaseTest {
    private val hits = HitSurfaceUseCase()
    private val snapper = SnapPointUseCase(hits)

    private fun hitAt(point: Vec3, plane: PlaneSurface = Room.floor): SurfaceHit {
        val direction = (point - Room.HEAD).normalized()
        return hits.onSurface(plane, Room.HEAD, direction)!!
    }

    private fun rayTo(point: Vec3) = PointerRay(Room.HEAD, (point - Room.HEAD).normalized())

    @Test
    fun pointNearACorner_snapsToTheCorner() {
        val result = snapper.snap(hitAt(Vec3(1.97f, 0f, -3.97f)), Room.HEAD, Room.all, tapeHead = null)
        assertEquals(SnapKind.CORNER, result.kind)
        assertNear(Vec3(2f, 0f, -4f), result.point)
        assertEquals(Room.floor.id, result.plane.id) // the corner lies on the surface that was hit
    }

    @Test
    fun pointNearAnEdge_snapsOntoTheEdge() {
        val result = snapper.snap(hitAt(Vec3(1.98f, 0f, -2f)), Room.HEAD, Room.all, tapeHead = null)
        assertEquals(SnapKind.EDGE, result.kind)
        assertNear(Vec3(2f, 0f, -2f), result.point)
        assertNear(Vec3(0f, 0f, -1f), result.directionA!!)
    }

    @Test
    fun cornerSeat_carriesTheTwoEdgesThatLeaveIt() {
        val result = snapper.snap(hitAt(Vec3(1.97f, 0f, -3.97f)), Room.HEAD, Room.all, tapeHead = null)
        // the floor corner (2, 0, -4): one edge runs to (-2, 0, -4), the other to (2, 0, 0)
        val along = setOf(result.directionA!!, result.directionB!!)
        assertTrue(along.any { it.distanceTo(Vec3(-1f, 0f, 0f)) < 1e-4f })
        assertTrue(along.any { it.distanceTo(Vec3(0f, 0f, 1f)) < 1e-4f })
    }

    // ---- pointer channel (system tap): nothing announced the seat, so it must be on the tapped plane and close

    @Test
    fun pointerChannel_snapsToACornerOfTheTappedPlane_withinFortyMillimetres() {
        val near = snapper.snap(hitAt(Vec3(1.975f, 0f, -3.975f)), Room.HEAD, Room.all, null, SnapChannel.POINTER) // 35 mm
        assertEquals(SnapKind.CORNER, near.kind)
        assertNear(Vec3(2f, 0f, -4f), near.point)
        // 45 mm from the corner is still within 0.9 degrees at this range, but beyond the 40 mm cap:
        // the corner is refused; the edges, 32 mm away, are beyond their 30 mm cap as well
        val raw = hitAt(Vec3(1.968f, 0f, -3.968f))
        assertEquals(SnapKind.CORNER, snapper.snap(raw, Room.HEAD, Room.all, null, SnapChannel.TRACKED).kind)
        val capped = snapper.snap(raw, Room.HEAD, Room.all, null, SnapChannel.POINTER)
        assertEquals(SnapKind.NONE, capped.kind)
        assertNear(raw.point, capped.point, 0f)
    }

    @Test
    fun pointerChannel_snapsToAnEdgeOfTheTappedPlane_withinThirtyMillimetres() {
        val near = snapper.snap(hitAt(Vec3(1.975f, 0f, -3f)), Room.HEAD, Room.all, null, SnapChannel.POINTER) // 25 mm
        assertEquals(SnapKind.EDGE, near.kind)
        assertNear(Vec3(2f, 0f, -3f), near.point)
        val raw = hitAt(Vec3(1.965f, 0f, -3f)) // 35 mm: inside 0.6 degrees, beyond the cap
        assertEquals(SnapKind.EDGE, snapper.snap(raw, Room.HEAD, Room.all, null, SnapChannel.TRACKED).kind)
        assertEquals(SnapKind.NONE, snapper.snap(raw, Room.HEAD, Room.all, null, SnapChannel.POINTER).kind)
    }

    @Test
    fun pointerChannel_neverTakesASeatOnAnotherPlane() {
        // a cabinet top whose corner is seen 0.3 degrees from a tap on the floor behind it (round 1, Q1)
        val cabinet =
            PlaneSurface(
                "cab00000-0000", SurfaceKind.CABINET, "CABINET",
                listOf(Vec3(-0.5f, 0.9f, -1.7f), Vec3(0f, 0.9f, -1.7f), Vec3(0f, 0.9f, -1.2f), Vec3(-0.5f, 0.9f, -1.2f)),
                listOf(0, 1, 2, 0, 2, 3),
            )
        val planes = listOf(Room.floor, cabinet)
        val corner = Vec3(0f, 0.9f, -1.2f)
        // the floor point on the line of sight just past that corner
        val direction = (corner + Vec3(0.008f, 0f, 0f) - Room.HEAD).normalized()
        val raw = hits.onSurface(Room.floor, Room.HEAD, direction)!!
        assertTrue(raw.point.distanceTo(corner) > 1f)
        val tracked = snapper.snap(raw, Room.HEAD, planes, null, SnapChannel.TRACKED)
        assertEquals(SnapKind.CORNER, tracked.kind)
        assertEquals(cabinet.id, tracked.plane.id)
        val pointer = snapper.snap(raw, Room.HEAD, planes, null, SnapChannel.POINTER)
        assertEquals(SnapKind.NONE, pointer.kind)
        assertEquals(Room.floor.id, pointer.plane.id)
        assertNear(raw.point, pointer.point, 0f)
    }

    @Test
    fun pointerChannel_plumbAndLevel_areCappedAtThirtyMillimetres() {
        val above = Vec3(0.5f, 1.2f, -3f)
        val close = snapper.snap(hitAt(Vec3(0.52f, 0f, -3.01f)), Room.HEAD, Room.all, above, SnapChannel.POINTER) // 22 mm
        assertEquals(SnapKind.PLUMB, close.kind)
        assertNear(Vec3(0.5f, 0f, -3f), close.point)
        assertNear(Vec3.UP, close.directionA!!, 1e-4f) // towards the tape head
        val raw = hitAt(Vec3(0.53f, 0f, -3.02f)) // 36 mm
        assertEquals(SnapKind.PLUMB, snapper.snap(raw, Room.HEAD, Room.all, above, SnapChannel.TRACKED).kind)
        assertEquals(SnapKind.NONE, snapper.snap(raw, Room.HEAD, Room.all, above, SnapChannel.POINTER).kind)

        val beside = Vec3(-1f, 1.2f, -4f)
        val level = snapper.snap(hitAt(Vec3(0.5f, 1.22f, -4f), Room.wall), Room.HEAD, Room.all, beside, SnapChannel.POINTER)
        assertEquals(SnapKind.LEVEL, level.kind)
        assertNear(Vec3(0.5f, 1.2f, -4f), level.point)
        val off = hitAt(Vec3(0.5f, 1.24f, -4f), Room.wall) // 40 mm off level
        assertEquals(SnapKind.LEVEL, snapper.snap(off, Room.HEAD, Room.all, beside, SnapChannel.TRACKED).kind)
        assertEquals(SnapKind.NONE, snapper.snap(off, Room.HEAD, Room.all, beside, SnapChannel.POINTER).kind)
    }

    @Test
    fun pointInTheOpen_isLeftAlone() {
        val raw = hitAt(Vec3(0.5f, 0f, -2f))
        val result = snapper.snap(raw, Room.HEAD, Room.all, tapeHead = null)
        assertEquals(SnapKind.NONE, result.kind)
        assertNear(raw.point, result.point, 0f)
    }

    @Test
    fun cornerHiddenBehindTheSurfaceThatWasHit_isNotOffered() {
        val near =
            PlaneSurface(
                "near0000-0000", SurfaceKind.WALL, "WALL",
                listOf(Vec3(-2f, 0f, -1f), Vec3(2f, 0f, -1f), Vec3(2f, 3f, -1f), Vec3(-2f, 3f, -1f)),
                listOf(0, 1, 2, 0, 2, 3),
            )
        val behind =
            PlaneSurface(
                "far00000-0000", SurfaceKind.WALL, "WALL",
                listOf(Vec3(0.001f, 1.5f, -3f), Vec3(1f, 1.5f, -3f), Vec3(1f, 2.5f, -3f), Vec3(0.001f, 2.5f, -3f)),
                listOf(0, 1, 2, 0, 2, 3),
            )
        val raw = hits.onSurface(near, Room.HEAD, Vec3.AHEAD)!!
        val result = snapper.snap(raw, Room.HEAD, listOf(near, behind), tapeHead = null)
        assertEquals(SnapKind.NONE, result.kind)
    }

    @Test
    fun plumb_putsThePointStraightBelowTheTapeHead() {
        val tapeHead = Vec3(0.5f, 1.2f, -3f)
        val result = snapper.snap(hitAt(Vec3(0.53f, 0f, -3.02f)), Room.HEAD, Room.all, tapeHead)
        assertEquals(SnapKind.PLUMB, result.kind)
        assertNear(Vec3(0.5f, 0f, -3f), result.point)
        // too far from the plumb point: no snap
        val far = snapper.snap(hitAt(Vec3(0.8f, 0f, -3f)), Room.HEAD, Room.all, tapeHead)
        assertEquals(SnapKind.NONE, far.kind)
    }

    @Test
    fun plumbOnAWall_putsThePointStraightAboveATapeHeadOnThatWall() {
        val tapeHead = Vec3(-1f, 1.2f, -4f)
        val result = snapper.snap(hitAt(Vec3(-0.96f, 2.0f, -4f), Room.wall), Room.HEAD, Room.all, tapeHead)
        assertEquals(SnapKind.PLUMB, result.kind)
        assertNear(Vec3(-1f, 2.0f, -4f), result.point)
        assertNear(Vec3(0f, -1f, 0f), result.directionA!!, 1e-4f) // towards the tape head
        // pointer channel: 20 mm off the plumb line snaps, 36 mm does not (the tracked channel takes it)
        val close = hitAt(Vec3(-0.98f, 2.0f, -4f), Room.wall)
        assertEquals(SnapKind.PLUMB, snapper.snap(close, Room.HEAD, Room.all, tapeHead, SnapChannel.POINTER).kind)
        val wide = hitAt(Vec3(-0.964f, 2.0f, -4f), Room.wall)
        assertEquals(SnapKind.PLUMB, snapper.snap(wide, Room.HEAD, Room.all, tapeHead, SnapChannel.TRACKED).kind)
        assertEquals(SnapKind.NONE, snapper.snap(wide, Room.HEAD, Room.all, tapeHead, SnapChannel.POINTER).kind)
        // more than 10 cm off the plumb line: no snap
        assertEquals(SnapKind.NONE, snapper.snap(hitAt(Vec3(-0.8f, 2.0f, -4f), Room.wall), Room.HEAD, Room.all, tapeHead).kind)
        // a tape head that is not on this wall has no plumb line in it
        val inTheRoom = Vec3(-1f, 1.2f, -3f)
        assertEquals(SnapKind.NONE, snapper.snap(close, Room.HEAD, Room.all, inTheRoom).kind)
    }

    @Test
    fun level_putsThePointAtTheHeightOfTheTapeHead() {
        val tapeHead = Vec3(-1f, 1.2f, -4f)
        val result = snapper.snap(hitAt(Vec3(0.5f, 1.23f, -4f), Room.wall), Room.HEAD, Room.all, tapeHead)
        assertEquals(SnapKind.LEVEL, result.kind)
        assertNear(Vec3(0.5f, 1.2f, -4f), result.point)
        // more than 5 cm off level: no snap
        val off = snapper.snap(hitAt(Vec3(0.5f, 1.3f, -4f), Room.wall), Room.HEAD, Room.all, tapeHead)
        assertEquals(SnapKind.NONE, off.kind)
    }

    @Test
    fun pickPin_takesThePinWithinOneDegreeOfTheRay() {
        val a = pin("p1", Vec3(0f, 0f, -2f))
        val b = pin("p2", Vec3(0.6f, 0f, -2f))
        val head = ResolvePlacementUseCase.PIN_HEAD_M
        assertEquals("p1", snapper.pickPin(rayTo(Vec3(0f, head, -2f)), listOf(a, b), head, null)?.id)
        assertEquals("p2", snapper.pickPin(rayTo(Vec3(0.61f, head, -2f)), listOf(a, b), head, null)?.id)
        // the foot counts as well as the head
        assertEquals("p1", snapper.pickPin(rayTo(Vec3(0.01f, 0f, -2f)), listOf(a, b), head, null)?.id)
        // 15 cm beside a pin at 2.5 m is more than three degrees
        assertNull(snapper.pickPin(rayTo(Vec3(0.3f, 0f, -2f)), listOf(a, b), head, null))
    }

    @Test
    fun pickPin_ignoresPinsBehindTheSurfaceAndPinsOfAnotherSpace() {
        val a = pin("p1", Vec3(0f, 0f, -2f))
        val head = ResolvePlacementUseCase.PIN_HEAD_M
        val ray = rayTo(Vec3(0f, head, -2f))
        assertNull(snapper.pickPin(ray, listOf(a), head, maxRange = 1f)) // something was hit 1 m away
        assertEquals("p1", snapper.pickPin(ray, listOf(a), head, maxRange = 2.5f)?.id)
        assertNull(snapper.pickPin(ray, listOf(a.copy(elsewhere = true)), head, null))
    }

    @Test
    fun helpers() {
        assertNear(90f, SnapPointUseCase.angleDeg(Vec3.RIGHT, Vec3.UP))
        assertNear(0f, SnapPointUseCase.angleDeg(Vec3.UP, Vec3.UP))
        assertNear(Vec3(1f, 0f, 0f), SnapPointUseCase.closestOnSegment(Vec3(5f, 2f, 0f), Vec3.ZERO, Vec3.RIGHT))
        assertNear(Vec3(0.4f, 0f, 0f), SnapPointUseCase.closestOnSegment(Vec3(0.4f, 2f, 0f), Vec3.ZERO, Vec3.RIGHT))
    }
}
