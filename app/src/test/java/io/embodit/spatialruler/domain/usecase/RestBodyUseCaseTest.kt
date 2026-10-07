package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import org.junit.Assert.assertTrue
import org.junit.Test

class RestBodyUseCaseTest {
    private val rest = RestBodyUseCase()

    /** Half sizes of the instrument body with its bumpers: 242 x 122 x 38 mm. */
    private val half = listOf(0.121f, 0.061f, 0.019f)
    private val upright = listOf(Vec3.RIGHT, Vec3.UP, Vec3(0f, 0f, 1f))

    @Test
    fun bodyThatWouldSinkIntoTheFloor_restsOnIt_threeCentimetresClear() {
        val head = Vec3(0f, 0.2f, 0f) // the head is low over the floor
        val designed = Vec3(-0.17f, -0.10f, -0.50f) // 10 cm under the floor
        val rested = rest.rest(designed, upright, half, head, Room.all)
        // half height 61 mm along the floor normal + 3 cm
        assertNear(Vec3(-0.17f, 0.061f + RestBodyUseCase.GAP_M, -0.50f), rested, 1e-5f)
    }

    @Test
    fun tiltedBody_usesItsExtentAlongTheSurfaceNormal() {
        val turn = Quat4.axisAngle(Vec3.RIGHT, Math.toRadians(-30.0).toFloat())
        val axes = listOf(turn.rotate(Vec3.RIGHT), turn.rotate(Vec3.UP), turn.rotate(Vec3(0f, 0f, 1f)))
        val rested = rest.rest(Vec3(0f, 0f, -0.5f), axes, half, Vec3(0f, 0.3f, 0f), Room.all)
        // cos30 * 61 mm + sin30 * 19 mm = 62.3 mm
        assertNear(0.0623f + RestBodyUseCase.GAP_M, rested.y, 2e-4f)
    }

    @Test
    fun bodyThatIsClearOfEverySurface_staysWhereItWasDesigned() {
        val designed = Vec3(-0.17f, 1.2f, -0.5f)
        assertNear(designed, rest.rest(designed, upright, half, Room.HEAD, Room.all), 0f)
    }

    @Test
    fun surfaceTheHeadIsNotFiveCentimetresClearOf_isIgnored() {
        val head = Vec3(0f, 0.03f, 0f)
        val designed = Vec3(0f, -0.1f, -0.5f)
        assertNear(designed, rest.rest(designed, upright, half, head, Room.all), 0f)
    }

    @Test
    fun surfaceThatDoesNotReachUnderTheBody_isIgnored() {
        // the table top spans x -0.5..0.5, z -1.5..-1: a body 30 cm beside it (more than the 15 cm margin) is not above it
        val designed = Vec3(0.8f, 0.7f, -1.2f)
        assertNear(designed, rest.rest(designed, upright, half, Room.HEAD, listOf(Room.table)), 0f)
        // 10 cm beside it counts as above it
        val near = rest.rest(Vec3(0.6f, 0.7f, -1.2f), upright, half, Room.HEAD, listOf(Room.table))
        assertNear(0.75f + 0.061f + RestBodyUseCase.GAP_M, near.y, 1e-5f)
    }

    @Test
    fun bodyPushedUnderTheChin_slidesAlongTheSurface_awayFromTheHead_toThirtyFiveCentimetres() {
        val head = Vec3(0f, 0.25f, 0f)
        val rested = rest.rest(Vec3(0f, -0.05f, -0.10f), upright, half, head, Room.all)
        assertNear(0.091f, rested.y, 1e-5f)
        assertNear(0f, rested.x, 1e-5f)
        assertNear(RestBodyUseCase.MIN_RANGE_M, rested.distanceTo(head), 1e-4f)
        assertTrue("slid away from the head: ${rested.z}", rested.z < -0.3f)
    }

    @Test
    fun floorAndWallTogether_areBothCleared() {
        // in the corner of the floor and the far wall (z = -4), sunk into both
        val head = Vec3(0f, 1.0f, -3.4f)
        val rested = rest.rest(Vec3(0f, 0.0f, -4.05f), upright, half, head, Room.all)
        assertTrue("above the floor: ${rested.y}", rested.y >= 0.061f + RestBodyUseCase.GAP_M - 1e-4f)
        assertTrue("in front of the wall: ${rested.z}", rested.z >= -4f + 0.019f + RestBodyUseCase.GAP_M - 1e-4f)
    }
}
