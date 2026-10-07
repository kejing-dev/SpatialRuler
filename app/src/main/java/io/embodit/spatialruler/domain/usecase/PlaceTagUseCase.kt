package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

enum class TagKind { PIN, TAPE }

/** Something that lies in the view at [range] metres: a tag that overlaps it has to stand in front of it. */
data class ViewObject(val rect: ViewRect, val range: Float)

/**
 * What the placement of a tag depends on, all as seen from the head.
 *
 * @param anchor the selected pin (±1.5 degrees) or the pill of the selected plate
 * @param clearYaw / clearPitch angle between the anchor's centre and the near edge of the tag
 * @param hard rectangles the tag may not cover at all
 * @param soft rectangles the tag should not cover (at most 15 % of each one's own area)
 * @param behind plates, chips and pins with their range: the tag stands in front of those it overlaps
 * @param blades the blades in view: the tag also stands in front of the part of a blade it overlaps
 */
data class TagScene(
    val kind: TagKind,
    val anchor: ViewRect,
    val anchorRange: Float,
    val halfYaw: Float,
    val halfPitch: Float,
    val clearYaw: Float,
    val clearPitch: Float,
    val hard: List<ViewRect>,
    val soft: List<ViewRect>,
    val behind: List<ViewObject>,
    val blades: List<ViewSegment> = emptyList(),
)

/**
 * Where a tag stands: the slot around its anchor (see [PlaceTagUseCase.SLOT_NAMES]), its centre in the
 * view, its distance from the head, and whether a leader has to tie it to its anchor.
 */
data class TagPlacement(
    val slot: Int,
    val steps: Int,
    val yaw: Float,
    val pitch: Float,
    val range: Float,
    val leader: Boolean,
) {
    fun rect(scene: TagScene): ViewRect = ViewRect(yaw, pitch, scene.halfYaw, scene.halfPitch)
}

/**
 * Tag placement of the design (review 1, appendix A, with the surface rule of the addendum).
 *
 * Eight slots around the anchor. A slot is skipped when the tag would leave the comfortable part of
 * the view, touch the hard set, or could not be kept clear of real surfaces and in front of what it
 * overlaps without coming nearer than its limit (0.45 m, and half the range of its anchor). The first
 * remaining slot that covers at most 15 % of each soft item wins, else the one whose largest share of a
 * soft item is smallest. With no slot left, the in-view slot with the smallest hard overlap is pushed
 * outwards in 2 degree steps, at most five.
 */
class PlaceTagUseCase(
    private val surfaces: ClearSurfaceUseCase = ClearSurfaceUseCase(),
    private val facing: FacePanelUseCase = FacePanelUseCase(),
) {
    private class Candidate(val slot: Int, val steps: Int, val rect: ViewRect, val inView: Boolean, val hard: Float, val soft: Float, val range: Float, val wanted: Float, val feasible: Boolean)

    /** Chooses a slot from scratch. */
    fun choose(frame: ViewFrame, scene: TagScene, planes: Collection<PlaneSurface>): TagPlacement {
        val order = if (scene.kind == TagKind.PIN) PIN_ORDER else TAPE_ORDER
        val all = order.map { evaluate(frame, scene, planes, it, 0, 0f) }
        val free = all.filter { it.inView && it.hard <= 0f }
        val good = free.filter { it.feasible }
        val pick =
            good.firstOrNull { it.soft <= SOFT_SHARE }
                ?: good.minByOrNull { it.soft }
                // every slot would have to come too near: the one that needs the least pull, at its limit
                ?: free.maxByOrNull { it.wanted }
                ?: pushedOut(frame, scene, planes, all)
                ?: all.first()
        return placement(scene, pick)
    }

    /** The placement for a slot that was chosen earlier, or null when it does not hold any more. */
    fun keep(frame: ViewFrame, scene: TagScene, planes: Collection<PlaneSurface>, slot: Int, steps: Int): TagPlacement? {
        val c = evaluate(frame, scene, planes, slot, steps, KEEP_SLACK_DEG)
        return if (c.inView && c.hard <= 0f && c.feasible) placement(scene, c) else null
    }

    /** The placement of a slot whether or not it still holds (drawn while a kept slot is failing for a moment). */
    fun at(frame: ViewFrame, scene: TagScene, planes: Collection<PlaneSurface>, slot: Int, steps: Int): TagPlacement =
        placement(scene, evaluate(frame, scene, planes, slot, steps, KEEP_SLACK_DEG))

    private fun pushedOut(frame: ViewFrame, scene: TagScene, planes: Collection<PlaneSurface>, all: List<Candidate>): Candidate? {
        val start = all.filter { it.inView }.minByOrNull { it.hard } ?: return null
        var best = start
        for (steps in 1..MAX_PUSH_STEPS) {
            val c = evaluate(frame, scene, planes, start.slot, steps, 0f)
            if (!c.inView) break
            best = c
            if (c.hard <= 0f) break
        }
        return best
    }

    private fun evaluate(frame: ViewFrame, scene: TagScene, planes: Collection<PlaneSurface>, slot: Int, steps: Int, slack: Float): Candidate {
        val sx = SLOT_X[slot]
        val sy = SLOT_Y[slot]
        val dYaw = sx * (scene.clearYaw + scene.halfYaw + steps * PUSH_STEP_DEG)
        val dPitch = sy * (scene.clearPitch + scene.halfPitch + steps * PUSH_STEP_DEG)
        val rect = ViewRect(scene.anchor.yaw + dYaw, scene.anchor.pitch + dPitch, scene.halfYaw, scene.halfPitch)
        val inView = abs(rect.yaw) + rect.halfYaw <= VIEW_HALF_YAW_DEG + slack && abs(rect.pitch) + rect.halfPitch <= VIEW_HALF_PITCH_DEG + slack
        var hard = 0f
        for (h in scene.hard) hard += rect.overlap(h)
        // the largest share of any one soft item that the tag would cover (Appendix A step 7)
        var soft = 0f
        for (s in scene.soft) if (s.area > 1e-6f) soft = max(soft, rect.overlap(s) / s.area)

        // the tag never stands behind its anchor; it comes nearer to stay clear of surfaces and of what it overlaps
        var range = scene.anchorRange
        for (o in scene.behind) if (rect.intersects(o.rect)) range = min(range, o.range - IN_FRONT_M)
        for (b in scene.blades) {
            val over = rect.clipSegment(b.yaw0, b.pitch0, b.yaw1, b.pitch1, b.halfWidth) ?: continue
            val span = b.range1 - b.range0
            range = min(range, min(b.range0 + span * over.first, b.range0 + span * over.second) - IN_FRONT_M)
        }
        val lower = min(scene.anchorRange, max(MIN_RANGE_M, scene.anchorRange * MIN_RANGE_SHARE))
        if (range > lower) {
            range =
                surfaces.maxRange(
                    head = frame.pose.position,
                    toCenter = frame.direction(rect.yaw, rect.pitch),
                    range = range,
                    halfWidthPerM = tan(ViewFrame.radians(rect.halfYaw)),
                    halfHeightPerM = tan(ViewFrame.radians(rect.halfPitch)),
                    planes = planes,
                    facing = facing,
                )
        }
        val feasible = range >= lower - 1e-3f
        return Candidate(slot, steps, rect, inView, hard, soft, max(range, lower), range, feasible)
    }

    private fun placement(scene: TagScene, c: Candidate): TagPlacement {
        val gapYaw = abs(c.rect.yaw - scene.anchor.yaw) - c.rect.halfYaw - scene.anchor.halfYaw
        val gapPitch = abs(c.rect.pitch - scene.anchor.pitch) - c.rect.halfPitch - scene.anchor.halfPitch
        val gap = max(gapYaw, gapPitch)
        val leader = gap > LEADER_GAP_DEG || c.range < scene.anchorRange * LEADER_RANGE_SHARE
        return TagPlacement(c.slot, c.steps, c.rect.yaw, c.rect.pitch, c.range, leader)
    }

    companion object {
        /** Slot index -> compass name. The pin tag tries them in this order. */
        val SLOT_NAMES = listOf("N", "NE", "NW", "E", "W", "S", "SE", "SW")
        private val SLOT_X = floatArrayOf(0f, 1f, -1f, 1f, -1f, 0f, 1f, -1f)
        private val SLOT_Y = floatArrayOf(1f, 1f, 1f, 0f, 0f, -1f, -1f, -1f)
        private val PIN_ORDER = listOf(0, 1, 2, 3, 4, 5, 6, 7)

        /** The tape tag keeps to the vertical: N, NE, NW, S, SE, SW, E, W. */
        private val TAPE_ORDER = listOf(0, 1, 2, 5, 6, 7, 3, 4)

        /** The part of the view a tag should stay in (the emulator shows about 40 degrees each way). */
        const val VIEW_HALF_YAW_DEG = 36f
        const val VIEW_HALF_PITCH_DEG = 34f
        const val KEEP_SLACK_DEG = 3f
        const val SOFT_SHARE = 0.15f
        const val PUSH_STEP_DEG = 2f
        const val MAX_PUSH_STEPS = 5
        const val LEADER_GAP_DEG = 4f
        const val LEADER_RANGE_SHARE = 0.8f

        /** A tag stands this much in front of what it overlaps. */
        const val IN_FRONT_M = 0.05f
        const val MIN_RANGE_M = 0.45f
        const val MIN_RANGE_SHARE = 0.5f
    }
}
