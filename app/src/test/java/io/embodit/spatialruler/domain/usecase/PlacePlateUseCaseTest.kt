package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacePlateUseCaseTest {
    private val plates = PlacePlateUseCase()
    private val frame = ViewFrame(ViewPose(Room.HEAD, Quat4.IDENTITY))

    /** A pill 92 x 40 dp: half sizes at scale 1. */
    private val halfW = 0.0368f
    private val halfH = 0.016f

    /** A level tape at eye height, 2 m ahead: horizontal in view from -14 to +14 degrees. */
    private val level = PlateTape("t1", Vec3(-0.5f, 1.5f, -2f), Vec3(0.5f, 1.5f, -2f), halfW, halfH)
    private val ends = listOf(frame.box(level.a, PlacePlateUseCase.PIN_BOX_DEG)!!, frame.box(level.b, PlacePlateUseCase.PIN_BOX_DEG)!!)

    @Test
    fun candidates_areFivePlacesOnBothSides_theUpperSideFirst() {
        assertEquals(10, plates.candidates.size)
        assertEquals(listOf(0.50f, 0.36f, 0.64f, 0.24f, 0.76f), plates.candidates.take(5).map { it.fraction })
        assertTrue(plates.candidates.take(5).all { it.side == 1 })
        assertTrue(plates.candidates.drop(5).all { it.side == -1 })
    }

    @Test
    fun freeTape_getsItsPlateAtTheMiddle_onTheUpperSide_halfADegreeClearOfTheBlade() {
        assertEquals(PlateChoice(0.5f, 1), plates.choose(frame, level, ends, emptyList()))
        val pose = plates.pose(frame, level, PlateChoice(0.5f, 1))!!
        assertNear(Vec3(0f, 1.5f, -2f), pose.rider, 1e-5f)
        assertNear(0f, pose.rect.yaw, 1e-3f)
        assertTrue("above the blade: ${pose.rect.pitch}", pose.rect.pitch > 0f)
        // the blade is 0.25 degrees thick each way at 2 m; the pill keeps half a degree of air above it
        val gap = pose.rect.pitch - pose.rect.halfPitch - 0.25f
        assertTrue("gap $gap", gap >= PlacePlateUseCase.GAP_DEG && gap < PlacePlateUseCase.GAP_DEG + 0.1f)
        // the pill keeps its size in view: 92 dp at the 2 m scale of 2.5
        assertNear(ViewFrame.angle(halfW * 2.5f, 2f), pose.rect.halfYaw, 1e-3f)
        assertNear(2f, pose.range, 1e-4f)
        assertNear(2f, pose.center.distanceTo(Room.HEAD), 1e-3f)
    }

    @Test
    fun placeTakenByAnotherPill_movesThePlateAlongTheTape() {
        val taken = plates.pose(frame, level, PlateChoice(0.5f, 1))!!.rect
        // 36 % and 64 % still touch the pill in the middle; 24 % clears it and the end pin
        assertEquals(PlateChoice(0.24f, 1), plates.choose(frame, level, ends, listOf(taken)))
    }

    @Test
    fun upperSideFull_putsThePlateOnTheOtherSide() {
        val above = ViewRect(0f, 4f, 30f, 3.5f)
        val choice = plates.choose(frame, level, ends, listOf(above))
        assertEquals(PlateChoice(0.5f, -1), choice)
        assertTrue(plates.pose(frame, level, choice)!!.rect.pitch < 0f)
    }

    @Test
    fun nothingFree_putsThePlateWhereItCoversTheLeast() {
        // covered alike everywhere: the first place in the order
        val everywhere = ViewRect(0f, 0f, 60f, 60f)
        assertEquals(PlateChoice(0.5f, 1), plates.choose(frame, level, ends, listOf(everywhere)))
        // a pin in the way of the middle places: the first place that clears it
        val pinInTheWay = ends + ViewRect(0f, 2.5f, 1.5f, 1.5f)
        assertEquals(PlateChoice(0.24f, 1), plates.choose(frame, level, pinInTheWay, listOf(everywhere)))
        // other plates over the whole upper side and over most of the lower side: the lower place that covers the least of them
        val above = ViewRect(0f, 4f, 30f, 3.5f)
        val below = ViewRect(-10f, -3f, 16f, 2.5f)
        assertEquals(PlateChoice(0.76f, -1), plates.choose(frame, level, ends, listOf(above, below)))
        // a sliver of a pin's box is better than the whole of another plate
        val plateOnEveryFreePlace = listOf(ViewRect(0f, -2.5f, 30f, 2.2f), ViewRect(-7.4f, 2.5f, 3.2f, 2.2f), ViewRect(7.4f, 2.5f, 3.2f, 2.2f))
        val sliver = ends + ViewRect(0f, 4.9f, 1.5f, 1.5f)
        assertEquals(PlateChoice(0.5f, 1), plates.choose(frame, level, sliver, plateOnEveryFreePlace))
    }

    @Test
    fun pinsEverywhere_putThePlateWhereItCoversTheLeastOfThem() {
        // pins closer together than a pill is wide: above the middle and both upper neighbours, and all along the lower side
        val crowd = ArrayList(ends)
        for (yaw in intArrayOf(-12, -8, -4, 0, 4)) crowd += ViewRect(yaw.toFloat(), 2.5f, 1.5f, 1.5f)
        for (yaw in -14..14 step 2) crowd += ViewRect(yaw.toFloat(), -2.5f, 1.5f, 1.5f)
        // no place clears the pins: the upper place at 76 % only touches the pin at +4 degrees
        assertEquals(PlateChoice(0.76f, 1), plates.choose(frame, level, crowd, emptyList()))
    }

    @Test
    fun tapeThatIsVerticalInView_getsItsPlateOnTheRight() {
        val upright = PlateTape("t2", Vec3(0f, 0.5f, -2f), Vec3(0f, 2.5f, -2f), halfW, halfH)
        val pose = plates.pose(frame, upright, PlateChoice(0.5f, 1))!!
        assertTrue("right of the blade: ${pose.rect.yaw}", pose.rect.yaw > 0f)
        assertNear(0f, pose.rect.pitch, 1e-3f)
        assertTrue(plates.pose(frame, upright, PlateChoice(0.5f, -1))!!.rect.yaw < 0f)
    }

    @Test
    fun tapeSeenEndOn_getsItsPlateBesideIt() {
        val away = PlateTape("t3", Vec3(0f, 1.5f, -2f), Vec3(0f, 1.5f, -3f), halfW, halfH)
        val pose = plates.pose(frame, away, PlateChoice(0.5f, 1))!!
        assertNear(1f, pose.acrossYaw)
        assertNear(0f, pose.acrossPitch)
    }

    @Test
    fun plateStopsHolding_whenItsBladeOrAPinComesToLieUnderIt() {
        val pose = plates.pose(frame, level, PlateChoice(0.5f, 1))!!
        assertTrue(plates.holds(frame, level, pose, ends))
        // the same pill with the blade running through it (the tape seen from another place)
        val raised = level.copy(a = Vec3(-0.5f, 1.586f, -2f), b = Vec3(0.5f, 1.586f, -2f))
        assertFalse(plates.holds(frame, raised, pose, ends))
        assertFalse(plates.holds(frame, level, pose, ends + ViewRect(0f, pose.rect.pitch, 1.5f, 1.5f)))
    }

    /** Air, in degrees, between the pill of [pose] and the nearest point of [tape]'s blade (its width included). */
    private fun airToBlade(view: ViewFrame, tape: PlateTape, pose: PlatePose): Float {
        val scales = ScaleForDistanceUseCase()
        var least = Float.MAX_VALUE
        for (i in 0..200) {
            val v = view.see(tape.a.lerp(tape.b, i / 200f))!!
            val half = ViewFrame.angle(LayoutTapeUseCase.WIDTH_M / 2f * scales.tapeWidth(v.range), v.range)
            val dy = kotlin.math.abs(v.yaw - pose.rect.yaw) - pose.rect.halfYaw - half
            val dp = kotlin.math.abs(v.pitch - pose.rect.pitch) - pose.rect.halfPitch - half
            least = minOf(least, maxOf(dy, dp))
        }
        return least
    }

    @Test
    fun floorTapeSeenStanding_takesTheUpperSide_thoughItSagsInTheView() {
        // round 2a on the emulator: a 70 cm tape on a low table, seen standing; in the (yaw, pitch) chart
        // the blade sags below the straight line between its ends, which used to fail the upper side
        val standing = ViewFrame(ViewPose(Vec3(0f, 1.4586f, -0.0227f), Quat4.axisAngle(Vec3.RIGHT, ViewFrame.radians(-6.6f))))
        val tape = PlateTape("t5", Vec3(0.5139f, 0.4006f, -1.5582f), Vec3(1.1785f, 0.4006f, -1.7920f), halfW, halfH)
        val pins = listOf(standing.box(tape.a, PlacePlateUseCase.PIN_BOX_DEG)!!, standing.box(tape.b, PlacePlateUseCase.PIN_BOX_DEG)!!)
        assertEquals(PlateChoice(0.5f, 1), plates.choose(standing, tape, pins, emptyList()))
        val upper = plates.pose(standing, tape, PlateChoice(0.5f, 1))!!
        val lower = plates.pose(standing, tape, PlateChoice(0.5f, -1))!!
        assertTrue("upper side: ${upper.acrossPitch}", upper.acrossPitch > 0.8f)
        assertTrue(upper.rect.pitch > lower.rect.pitch)
        for (pose in listOf(upper, lower)) {
            assertTrue(plates.holds(standing, tape, pose, pins))
            val air = airToBlade(standing, tape, pose)
            assertTrue("air $air", air >= PlacePlateUseCase.GAP_DEG && air < PlacePlateUseCase.GAP_DEG + 0.6f)
        }
    }

    @Test
    fun bladeThatCurvesTowardsThePill_pushesItOut_soTheGapIsKept() {
        // a long tape on the floor right in front of the feet: a deep sag in the view, the upper side is inside the curve
        val down = ViewFrame(ViewPose(Vec3(0f, 1.5f, 0f), Quat4.axisAngle(Vec3.RIGHT, ViewFrame.radians(-40f))))
        val tape = PlateTape("t6", Vec3(-1.2f, 0f, -0.9f), Vec3(1.2f, 0f, -0.9f), halfW, halfH)
        for (choice in plates.candidates) {
            val pose = plates.pose(down, tape, choice)!!
            assertTrue("$choice holds", plates.holds(down, tape, pose, emptyList()))
            val air = airToBlade(down, tape, pose)
            assertTrue("$choice air $air", air >= PlacePlateUseCase.GAP_DEG)
        }
        assertEquals(PlateChoice(0.5f, 1), plates.choose(down, tape, emptyList(), emptyList()))
    }

    @Test
    fun slantedTape_keepsTheGapOnBothAxes() {
        // 30 degrees off level in view: the pill's corner, not its edge, is nearest to the blade
        val slanted = PlateTape("t7", Vec3(-0.5f, 1.2f, -2f), Vec3(0.5f, 1.8f, -2f), halfW, halfH)
        for (side in intArrayOf(1, -1)) {
            val pose = plates.pose(frame, slanted, PlateChoice(0.5f, side))!!
            assertTrue(plates.holds(frame, slanted, pose, emptyList()))
            assertTrue(airToBlade(frame, slanted, pose) >= PlacePlateUseCase.GAP_DEG)
        }
        assertEquals(PlateChoice(0.5f, 1), plates.choose(frame, slanted, emptyList(), emptyList()))
    }

    @Test
    fun tapeBehindTheViewer_hasNoPlate() {
        val behind = PlateTape("t4", Vec3(-0.5f, 1.5f, 2f), Vec3(0.5f, 1.5f, 2f), halfW, halfH)
        assertNull(plates.pose(frame, behind, PlateChoice(0.5f, 1)))
        // the choice still answers
        assertEquals(PlateChoice(0.5f, 1), plates.choose(frame, behind, emptyList(), emptyList()))
    }
}
