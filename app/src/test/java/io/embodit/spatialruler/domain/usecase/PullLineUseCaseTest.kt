package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullLineUseCaseTest {
    private val pull = PullLineUseCase()

    @Test
    fun atThePress_theLinePointsFromTheHeadAtThePressedPoint() {
        val pressed = Vec3(0.5f, 0f, -2f)
        val ray = pull.ray(Room.HEAD, pressed, Vec3.ZERO)!!
        assertNear(Room.HEAD, ray.origin, 0f)
        assertNear((pressed - Room.HEAD).normalized(), ray.direction, 1e-6f)
    }

    @Test
    fun oneCentimetreOfHandMovement_turnsTheLineByOnePointTwoDegrees() {
        val pressed = Vec3(0f, 1.5f, -2f) // straight ahead
        val before = pull.ray(Room.HEAD, pressed, Vec3.ZERO)!!
        val after = pull.ray(Room.HEAD, pressed, Vec3(0.01f, 0f, 0f))!!
        assertNear(1.2f, SnapPointUseCase.angleDeg(before.direction, after.direction), 2e-2f)
        // ten centimetres: about 11.8 degrees, to the side the hand moved
        val far = pull.ray(Room.HEAD, pressed, Vec3(0.10f, 0f, 0f))!!
        assertNear(11.84f, SnapPointUseCase.angleDeg(before.direction, far.direction), 5e-2f)
        assertTrue(far.direction.x > 0f)
    }

    @Test
    fun theLineFollowsTheHead_whenTheHeadMoves() {
        val pressed = Vec3(0f, 0f, -2f)
        val moved = Vec3(0.3f, 1.4f, 0.1f)
        assertNear(moved, pull.ray(moved, pressed, Vec3.ZERO)!!.origin, 0f)
    }

    @Test
    fun pressAtTheHeadItself_givesNoLine() {
        assertNull(pull.ray(Room.HEAD, Room.HEAD, Vec3(0.1f, 0f, 0f)))
    }

    @Test
    fun truing_movesThePinAQuarterAsFarAsTheHand_alongItsSurfaceAxes() {
        // 4 cm to the right and 2 cm away: 10 mm and 5 mm
        assertEquals(10 to 5, pull.trueOffsets(Vec3(0.04f, 0f, -0.02f), Vec3.RIGHT, Vec3.AHEAD, 0, 0))
        // on top of the offsets the pin had when the drag began; hand movement off the surface does not count
        assertEquals(13 to -4, pull.trueOffsets(Vec3(0.04f, 0.3f, 0f), Vec3.RIGHT, Vec3.AHEAD, 3, -4))
        assertEquals(-1 to 0, pull.trueOffsets(Vec3(-0.004f, 0f, 0f), Vec3.RIGHT, Vec3.AHEAD, 0, 0))
    }
}
