package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.Room
import io.embodit.spatialruler.assertNear
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTagUseCaseTest {
    private val tags = PlaceTagUseCase()
    private val frame = ViewFrame(ViewPose(Room.HEAD, Quat4.IDENTITY))
    private val noPlanes = emptyList<io.embodit.spatialruler.domain.model.PlaneSurface>()

    private fun slot(name: String) = PlaceTagUseCase.SLOT_NAMES.indexOf(name)

    /** A tag of 15 x 9.4 degrees around an anchor of +-1.5 degrees, 3.2 degrees of clearance. */
    private fun scene(
        kind: TagKind = TagKind.PIN,
        yaw: Float = 0f,
        pitch: Float = 0f,
        range: Float = 2f,
        hard: List<ViewRect> = emptyList(),
        soft: List<ViewRect> = emptyList(),
        behind: List<ViewObject> = emptyList(),
        blades: List<ViewSegment> = emptyList(),
    ) = TagScene(kind, ViewRect(yaw, pitch, 1.5f, 1.5f), range, 7.5f, 4.7f, 3.2f, 3.2f, hard, soft, behind, blades)

    @Test
    fun nothingInTheWay_theTagStandsAboveItsAnchor_atTheAnchorsDistance() {
        val p = tags.choose(frame, scene(), noPlanes)
        assertEquals(slot("N"), p.slot)
        assertEquals(0, p.steps)
        assertNear(0f, p.yaw)
        assertNear(7.9f, p.pitch, 1e-4f) // clearance 3.2 + half height 4.7
        assertNear(2f, p.range)
        assertFalse(p.leader)
    }

    @Test
    fun slotThatTouchesTheHardSet_isSkipped_inTheOrderOfTheTagKind() {
        val onTop = ViewRect(0f, 7.9f, 1f, 1f)
        val pin = tags.choose(frame, scene(hard = listOf(onTop)), noPlanes)
        assertEquals(slot("NE"), pin.slot)
        assertNear(10.7f, pin.yaw, 1e-4f)
        assertNear(7.9f, pin.pitch, 1e-4f)
        // the whole upper row is taken: the pin tag goes to the side, the tape tag below
        val row = ViewRect(0f, 9f, 20f, 4f)
        assertEquals(slot("E"), tags.choose(frame, scene(TagKind.PIN, hard = listOf(row)), noPlanes).slot)
        assertEquals(slot("S"), tags.choose(frame, scene(TagKind.TAPE, hard = listOf(row)), noPlanes).slot)
    }

    @Test
    fun slotThatLeavesTheView_isSkipped() {
        // the anchor is 25 degrees up: the row above it would reach beyond 34 degrees
        val p = tags.choose(frame, scene(pitch = 25f), noPlanes)
        assertEquals(slot("E"), p.slot)
        assertNear(25f, p.pitch, 1e-4f)
    }

    @Test
    fun softSet_mayBeCoveredUpToFifteenPercentOfTheTag() {
        // another plate exactly where the tag would stand above the anchor
        val other = ViewRect(0f, 7.9f, 7.5f, 4.7f)
        // N covers it fully, NE and NW by 29 %, E by under 5 %
        assertEquals(slot("E"), tags.choose(frame, scene(soft = listOf(other)), noPlanes).slot)
        // when every slot covers too much, the one that covers least wins: here everything is covered once,
        // and the slots on either side and below a second time
        val everywhere = ViewRect(0f, 0f, 60f, 60f)
        val right = ViewRect(33.8f, 0f, 26.2f, 60f)
        val left = ViewRect(-33.8f, 0f, 26.2f, 60f)
        val below = ViewRect(0f, -30f, 60f, 28f)
        assertEquals(slot("N"), tags.choose(frame, scene(soft = listOf(everywhere, right, left, below)), noPlanes).slot)
    }

    @Test
    fun tagStandsFiveCentimetresInFrontOfWhatItOverlaps_andGetsALeaderWhenThatBringsItMuchNearer() {
        val plate = ViewObject(ViewRect(0f, 7.9f, 1f, 1f), 1.5f)
        val p = tags.choose(frame, scene(behind = listOf(plate)), noPlanes)
        assertEquals(slot("N"), p.slot)
        assertNear(1.45f, p.range, 1e-4f)
        assertTrue(p.leader) // nearer than 0.8 x the anchor's 2 m
        // an overlapped thing that is too near (the tag may not come nearer than half its anchor's range): another slot
        val near = ViewObject(ViewRect(0f, 7.9f, 1f, 1f), 0.9f)
        val q = tags.choose(frame, scene(behind = listOf(near)), noPlanes)
        assertEquals(slot("NE"), q.slot)
        assertNear(2f, q.range)
    }

    @Test
    fun tagStandsInFrontOfThePartOfABladeItOverlaps() {
        // a blade across the place above the anchor, 3 m away on the left and 1.5 m on the right
        val blade = ViewSegment(-10f, 7.9f, 3f, 10f, 7.9f, 1.5f, 0.2f)
        val p = tags.choose(frame, scene(blades = listOf(blade)), noPlanes)
        assertEquals(slot("N"), p.slot)
        // the nearest overlapped part is at its right edge: about 1.67 m, minus 5 cm
        assertNear(1.62f, p.range, 2e-2f)
        // a blade that passes beside the tag does not matter
        val beside = ViewSegment(9f, -10f, 3f, 9f, 20f, 1.5f, 0.2f)
        assertNear(2f, tags.choose(frame, scene(blades = listOf(beside)), noPlanes).range)
    }

    @Test
    fun everySlotBlocked_theTagIsPushedOutwards_andTiedToItsAnchor() {
        val block = ViewRect(0f, 0f, 13f, 13f)
        val p = tags.choose(frame, scene(hard = listOf(block)), noPlanes)
        assertEquals(slot("NE"), p.slot) // the first of the slots with the least overlap
        assertEquals(5, p.steps)
        assertNear(20.7f, p.yaw, 1e-3f)
        assertNear(17.9f, p.pitch, 1e-3f)
        assertTrue(p.leader)
    }

    @Test
    fun keptSlot_holdsWhileItIsClear_andIsGivenUpWhenSomethingComesToLieUnderIt() {
        val first = tags.choose(frame, scene(), noPlanes)
        val kept = tags.keep(frame, scene(), noPlanes, first.slot, first.steps)
        assertNotNull(kept)
        assertNear(first.pitch, kept!!.pitch)
        assertNull(tags.keep(frame, scene(hard = listOf(ViewRect(0f, 7.9f, 1f, 1f))), noPlanes, first.slot, first.steps))
        // the place it had is still known, for the moment it keeps showing there
        assertNear(7.9f, tags.at(frame, scene(hard = listOf(ViewRect(0f, 7.9f, 1f, 1f))), noPlanes, first.slot, first.steps).pitch, 1e-4f)
        // three degrees of slack at the edge of the view
        assertNull(tags.keep(frame, scene(pitch = 30f), noPlanes, slot("N"), 0))
        assertNotNull(tags.keep(frame, scene(pitch = 24f), noPlanes, slot("N"), 0))
    }

    @Test
    fun tagThatWouldHangBelowTheFloor_isPulledTowardsTheHead_andKeepsItsPlaceInView() {
        // a plate on the floor 3.5 m ahead; the row above it is taken, so the tape tag goes below it
        val onFloor = frame.see(Vec3(0f, 0f, -3.5f))!!
        val anchor = ViewRect(onFloor.yaw, onFloor.pitch, 2.6f, 1.5f)
        val row = ViewRect(0f, onFloor.pitch + 2f + 3f, 30f, 1f)
        val s = TagScene(TagKind.TAPE, anchor, onFloor.range, 6f, 3f, 4f, 2f, listOf(row), emptyList(), emptyList())
        val p = tags.choose(frame, s, Room.all)
        assertEquals(slot("S"), p.slot)
        assertNear(onFloor.pitch - 5f, p.pitch, 1e-3f)
        assertTrue("pulled in: ${p.range} of ${onFloor.range}", p.range < onFloor.range - 0.3f && p.range > onFloor.range / 2f)
        assertTrue(p.leader)
        // every corner is at least 2 cm above the floor
        val center = frame.point(p.yaw, p.pitch, p.range)
        val f = FacePanelUseCase().facing(center, Room.HEAD, null)
        val halfW = tan(ViewFrame.radians(6f)) * p.range
        val halfH = tan(ViewFrame.radians(3f)) * p.range
        val lowest = listOf(-1f, 1f).flatMap { sx -> listOf(-1f, 1f).map { sy -> (center + f.right * (sx * halfW) + f.up * (sy * halfH)).y } }.min()
        assertTrue("lowest corner $lowest", lowest >= 0.019f)
    }
}
