package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitSurfaceUseCaseTest {
    private val hits = HitSurfaceUseCase()

    @Test
    fun rayFromAbove_hitsTheFloorAtTheExpectedPoint() {
        val direction = (Vec3(0.5f, 0f, -2f) - Room.HEAD).normalized()
        val hit = hits.onSurface(Room.floor, Room.HEAD, direction)
        assertNotNull(hit)
        assertNear(Vec3(0.5f, 0f, -2f), hit!!.point)
        assertNear(Room.HEAD.distanceTo(Vec3(0.5f, 0f, -2f)), hit.distance)
        assertNear(Vec3.UP, hit.normal)
    }

    @Test
    fun wallThatWindsOutwards_isHitFromInsideTheRoom_andItsNormalFacesTheViewer() {
        // the quad's own normal points away from the viewer: a back-face-culling test would miss it
        assertTrue(Room.wall.normal.z < 0f)
        val hit = hits.onSurface(Room.wall, Room.HEAD, Vec3.AHEAD)
        assertNotNull(hit)
        assertNear(Vec3(0f, 1.5f, -4f), hit!!.point)
        assertNear(4f, hit.distance)
        assertNear(Vec3(0f, 0f, 1f), hit.normal)
    }

    @Test
    fun sameQuad_isHitFromBothSides() {
        val fromBelow = hits.onSurface(Room.floor, Vec3(0f, -1f, -2f), Vec3.UP)
        val fromAbove = hits.onSurface(Room.floor, Vec3(0f, 1f, -2f), -Vec3.UP)
        assertNotNull(fromBelow)
        assertNotNull(fromAbove)
        assertNear(Vec3(0f, -1f, 0f), fromBelow!!.normal)
        assertNear(Vec3.UP, fromAbove!!.normal)
    }

    @Test
    fun rayOutsideThePolygon_parallelToIt_orPointingAway_misses() {
        assertNull(hits.onSurface(Room.floor, Vec3(3f, 1f, -2f), -Vec3.UP)) // beside the floor
        assertNull(hits.onSurface(Room.floor, Room.HEAD, Vec3.AHEAD)) // parallel
        assertNull(hits.onSurface(Room.floor, Room.HEAD, Vec3.UP)) // floor is behind the ray
        assertNull(hits.onSurface(Room.wall, Room.HEAD, Vec3.AHEAD, maxDistance = 3f)) // beyond the range
    }

    @Test
    fun nearest_returnsTheClosestSurfaceAlongTheRay() {
        // steep ray: reaches the floor before the wall
        val down = (Vec3(0f, 0f, -1f) - Room.HEAD).normalized()
        assertEquals(Room.floor.id, hits.nearest(Room.all, Room.HEAD, down)!!.plane.id)
        // a table in front of the floor point wins
        val towardsTable = (Vec3(0f, 0.75f, -1.2f) - Room.HEAD).normalized()
        assertEquals(Room.table.id, hits.nearest(Room.all + Room.table, Room.HEAD, towardsTable)!!.plane.id)
        // level ray: only the wall
        assertEquals(Room.wall.id, hits.nearest(Room.all, Room.HEAD, Vec3.AHEAD)!!.plane.id)
        assertNull(hits.nearest(Room.all, Room.HEAD, Vec3.UP))
    }

    @Test
    fun infinitePlane_isHitOutsideThePolygonToo() {
        val origin = Vec3(5f, 1f, -2f)
        assertNull(hits.onSurface(Room.floor, origin, -Vec3.UP))
        val hit = hits.onInfinitePlane(Room.floor, origin, -Vec3.UP)
        assertNear(Vec3(5f, 0f, -2f), hit!!.point)
        assertNear(Vec3.UP, hit.normal)
        assertNull(hits.onInfinitePlane(Room.floor, origin, Vec3.RIGHT))
    }

    @Test
    fun projection_distanceAndContainment() {
        val above = Vec3(1f, 0.003f, -1f)
        assertNear(Vec3(1f, 0f, -1f), hits.project(Room.floor, above))
        assertNear(0.003f, hits.distanceToPlane(Room.floor, above))
        assertTrue(hits.contains(Room.floor, above))
        assertTrue(hits.contains(Room.floor, Vec3(2.001f, 0f, -1f))) // inside the 2 mm margin
        assertFalse(hits.contains(Room.floor, Vec3(2.01f, 0f, -1f)))
        assertTrue(hits.contains(Room.floor, Vec3(2.008f, 0f, -1f), margin = 0.01f))
    }
}
