package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeToleranceUseCaseTest {
    private val tolerance = ComputeToleranceUseCase()

    @Test
    fun pinContribution_followsTheGradeAndTheRange() {
        assertNear(2f, tolerance.pinSigmaMm(Grade.CONTACT, SnapKind.NONE, false, 0.3f))
        assertNear(5f, tolerance.pinSigmaMm(Grade.FINE, SnapKind.NONE, false, 2f)) // 3 + 1.0 * 2
        assertNear(12f, tolerance.pinSigmaMm(Grade.COARSE, SnapKind.NONE, false, 1f)) // 8 + 4.0 * 1
    }

    @Test
    fun snappedSeat_isSixMillimetres_whateverTheGrade() {
        assertNear(6f, tolerance.pinSigmaMm(Grade.COARSE, SnapKind.CORNER, false, 2.5f))
        assertNear(6f, tolerance.pinSigmaMm(Grade.FINE, SnapKind.EDGE, false, 0.5f))
        // plumb / level are aiming aids, not seats
        assertNear(12f, tolerance.pinSigmaMm(Grade.COARSE, SnapKind.PLUMB, false, 1f))
    }

    @Test
    fun beyondThreeMetres_theContributionDoubles() {
        assertNear(48f, tolerance.pinSigmaMm(Grade.COARSE, SnapKind.NONE, true, 4f)) // (8 + 16) * 2
        assertNear(12f, tolerance.pinSigmaMm(Grade.COARSE, SnapKind.CORNER, true, 4f))
    }

    @Test
    fun roundUp_isAlwaysUpwards_onTheLadderFromTwoMillimetresToFiftyCentimetres() {
        val expected =
            mapOf(
                0.5f to 2, 2f to 2, 2.1f to 3, 3f to 3, 4.9f to 5, 5.5f to 8, 8f to 8, 9f to 10, 12f to 15, 16f to 20, 25f to 30, 45f to 50,
                50f to 50, 50.1f to 80, 78f to 80, 80f to 80, 81f to 100, 120f to 150, 151f to 200, 250f to 300, 301f to 500, 500f to 500,
            )
        for ((mm, step) in expected) assertEquals("roundUp($mm)", step, tolerance.roundUp(mm))
        assertEquals(15, ComputeToleranceUseCase.LADDER_MM.size)
        assertEquals(500, ComputeToleranceUseCase.LADDER_MM.last())
    }

    @Test
    fun aboveFiftyCentimetres_theToleranceIsOffTheLadder() {
        assertEquals(ComputeToleranceUseCase.OVER_LADDER_MM, tolerance.roundUp(500.5f))
        assertEquals(ComputeToleranceUseCase.OVER_LADDER_MM, tolerance.roundUp(4000f))
        assertTrue(ComputeToleranceUseCase.OVER_LADDER_MM > ComputeToleranceUseCase.LADDER_MM.last())
    }

    @Test
    fun farTapes_areNotRoundedDownToFiveCentimetres() {
        // the three tapes of test round 1 (D3): 77.8, 67.9 and 61.1 mm were all shown as "±5 cm"
        val a = pin("p40", Vec3.ZERO, grade = Grade.COARSE, far = true, rangeM = 5.17f) // (8 + 20.7) * 2 = 57.4
        val b = pin("p41", Vec3.ZERO, grade = Grade.COARSE, far = true, rangeM = 4.56f) // 52.5
        val c = pin("p37", Vec3.ZERO, grade = Grade.COARSE, far = true, rangeM = 3.16f) // 41.3
        assertEquals(80, tolerance.tapeToleranceMm(a, b)) // 77.8
        assertEquals(80, tolerance.tapeToleranceMm(a, c)) // 70.7
        assertEquals(80, tolerance.tapeToleranceMm(b, c)) // 66.8
        assertEquals(80, tolerance.pinToleranceMm(a))
        assertEquals(50, tolerance.pinToleranceMm(c))
        // two pins at the far end of a large room
        val d = pin("p", Vec3.ZERO, grade = Grade.COARSE, far = true, rangeM = 9f) // 88
        assertEquals(150, tolerance.tapeToleranceMm(d, d)) // 124.5
    }

    @Test
    fun tape_combinesBothEndsAsRootSumOfSquares() {
        val a = pin("p1", Vec3(0f, 0f, -1f), grade = Grade.COARSE, rangeM = 1f) // 12 mm
        val b = pin("p2", Vec3(1f, 0f, -1f), grade = Grade.COARSE, rangeM = 1f) // 12 mm
        assertEquals(20, tolerance.tapeToleranceMm(a, b)) // 16.97 -> 20
        val c = pin("p3", Vec3.ZERO, grade = Grade.CONTACT)
        val d = pin("p4", Vec3.ZERO, grade = Grade.CONTACT)
        assertEquals(3, tolerance.tapeToleranceMm(c, d)) // 2.83 -> 3
        assertEquals(15, tolerance.tapeToleranceMm(a, c)) // 12.17 -> 15
    }

    @Test
    fun pinTolerance_isTheContributionOnTheLadder() {
        assertEquals(15, tolerance.pinToleranceMm(pin("p1", Vec3.ZERO, grade = Grade.COARSE, rangeM = 1f)))
        assertEquals(5, tolerance.pinToleranceMm(pin("p2", Vec3.ZERO, grade = Grade.FINE, rangeM = 2f)))
        assertEquals(8, tolerance.pinToleranceMm(pin("p3", Vec3.ZERO, snapped = SnapKind.CORNER, saveState = SaveState.PENDING)))
    }
}
