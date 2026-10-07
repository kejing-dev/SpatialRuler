package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a reading plate stands on its tape: [fraction] of the way from end A to end B, on one [side] across it. */
data class PlateChoice(val fraction: Float, val side: Int)

/** A tape as the plate layout needs it: its two blade ends and the size of its plate's pill, metres at scale 1. */
data class PlateTape(val id: String, val a: Vec3, val b: Vec3, val pillHalfWidthM: Float, val pillHalfHeightM: Float)

/**
 * A plate standing at one candidate place.
 *
 * @param rider where the plate is tied to the blade
 * @param center centre of the pill, Stage metres
 * @param rect the pill in the view
 * @param acrossYaw with [acrossPitch]: unit vector, in the view chart (yaw, pitch), from the blade towards the pill
 */
data class PlatePose(
    val choice: PlateChoice,
    val rider: Vec3,
    val center: Vec3,
    val range: Float,
    val rect: ViewRect,
    val acrossYaw: Float,
    val acrossPitch: Float,
)

/**
 * Reading-plate layout of the design (review 1, F7 as amended by R9).
 *
 * Candidates are the five positions 50, 36, 64, 24, 76 % of the tape on both sides across it: the side
 * that is upper in view first, and for a tape within 20 degrees of vertical in view the right side
 * first. A candidate passes when the pill, grown by half a degree, meets neither its own blade as seen
 * from the head, nor any pin's ±1.5 degree box, nor another pill. The first that passes is taken. If
 * none passes (more plates and pins in view than there is room for), the plate goes where it clears
 * its own blade and covers the least: square degrees of pins' boxes, counted double, plus square
 * degrees of other pills; the earlier place in the order when two tie. The design's wording for that
 * case is "the first that clears its own blade and the pins"; taken literally, the plates of a dense
 * group all go to the same pin-free spot and hide each other (round 2a, 20 tapes on 1 m2 of floor).
 * The pill keeps at least half a degree of air between itself and the blade.
 *
 * "As seen from the head": a straight blade is a curve on the (yaw, pitch) chart (a tape on the floor
 * sags towards its nearest point), and its width in view changes along it. The blade is therefore
 * traced as [SAMPLES] pieces, the plate stands across the blade's direction at its rider, and it is
 * pushed out as far as the traced blade needs within the pill's own length.
 *
 * Not thread-safe: the last trace is kept, because the scene asks for several poses of one tape in
 * one frame. Use one instance per caller.
 */
class PlacePlateUseCase(private val scales: ScaleForDistanceUseCase = ScaleForDistanceUseCase()) {

    /** All candidate places in the order of preference. */
    val candidates: List<PlateChoice> =
        buildList {
            for (side in intArrayOf(1, -1)) for (f in FRACTIONS) add(PlateChoice(f, side))
        }

    // the blade of the last tape asked about, as the viewer sees it
    private val traceYaw = FloatArray(SAMPLES + 1)
    private val tracePitch = FloatArray(SAMPLES + 1)
    private val traceHalf = FloatArray(SAMPLES + 1)
    private var tracedFrame: ViewFrame? = null
    private var tracedA: Vec3? = null
    private var tracedB: Vec3? = null
    private var traced = false

    /** Geometry of the plate at [choice], or null when the tape is not in front of the viewer. */
    fun pose(frame: ViewFrame, tape: PlateTape, choice: PlateChoice): PlatePose? {
        if (!trace(frame, tape)) return null
        val rider = tape.a.lerp(tape.b, choice.fraction)
        val vp = frame.see(rider) ?: return null

        // which side comes first is judged on the whole tape; the direction across it, where the plate is tied
        val cy = traceYaw[SAMPLES] - traceYaw[0]
        val cp = tracePitch[SAMPLES] - tracePitch[0]
        val chord = hypot(cy, cp)
        var ny: Float
        var np: Float
        if (chord < END_ON_DEG) {
            // seen end-on: beside it, to the right
            ny = 1f
            np = 0f
        } else {
            var firstYaw = -cp / chord
            var firstPitch = cy / chord
            val steep = abs(cy) < chord * SIN_20
            // within 20 degrees of vertical in view: the right side first; otherwise the upper side
            if ((steep && firstYaw < 0f) || (!steep && firstPitch < 0f)) {
                firstYaw = -firstYaw
                firstPitch = -firstPitch
            }
            val near = (choice.fraction * SAMPLES).roundToInt().coerceIn(0, SAMPLES)
            val i0 = max(0, near - 1)
            val i1 = min(SAMPLES, near + 1)
            val ty = traceYaw[i1] - traceYaw[i0]
            val tp = tracePitch[i1] - tracePitch[i0]
            val length = hypot(ty, tp)
            if (length < 1e-4f) {
                ny = firstYaw
                np = firstPitch
            } else {
                ny = -tp / length
                np = ty / length
                if (ny * firstYaw + np * firstPitch < 0f) {
                    ny = -ny
                    np = -np
                }
            }
        }
        ny *= choice.side
        np *= choice.side

        val k = scales.panel(vp.range)
        val halfYaw = ViewFrame.angle(tape.pillHalfWidthM * k, vp.range)
        val halfPitch = ViewFrame.angle(tape.pillHalfHeightM * k, vp.range)
        // reach of the pill with the air around it, across the blade and along it
        val slant = abs(ny) + abs(np)
        val across = (halfYaw + GAP_DEG) * abs(ny) + (halfPitch + GAP_DEG) * abs(np)
        val along = (halfYaw + GAP_DEG) * abs(np) + (halfPitch + GAP_DEG) * abs(ny)
        val base = bladeHalfDeg(vp.range) * slant
        val rise = rise(vp.yaw, vp.pitch, ny, np, along, slant)
        // a blade that comes further towards this side than a stem should span is a blade in the way
        val top = if (rise - base > MAX_PUSH_DEG) base else max(base, rise)
        val offset = top + GAP_EPS_DEG + across
        val yaw = vp.yaw + ny * offset
        val pitch = vp.pitch + np * offset
        return PlatePose(choice, rider, frame.point(yaw, pitch, vp.range), vp.range, ViewRect(yaw, pitch, halfYaw, halfPitch), ny, np)
    }

    /** True when the plate at [pose] lies over neither its own blade nor a pin. */
    fun holds(frame: ViewFrame, tape: PlateTape, pose: PlatePose, pins: List<ViewRect>): Boolean =
        clearsBlade(frame, tape, pose) && clearsAll(pose, pins)

    /**
     * Chooses the place of [tape]'s plate. [pins] are the ±1.5 degree boxes of all pins, [pills] the
     * pills of the plates that are already placed.
     */
    fun choose(frame: ViewFrame, tape: PlateTape, pins: List<ViewRect>, pills: List<ViewRect>): PlateChoice {
        var best: PlateChoice? = null
        var leastCost = Float.MAX_VALUE
        for (choice in candidates) {
            val pose = pose(frame, tape, choice) ?: continue
            if (!clearsBlade(frame, tape, pose)) continue
            val cost = PIN_WEIGHT * overlap(pose, pins) + overlap(pose, pills)
            if (cost == 0f) return choice
            if (cost < leastCost - TIE_SQ_DEG) {
                leastCost = cost
                best = choice
            }
        }
        return best ?: candidates.first()
    }

    /** Area, square degrees, of the pill of [pose] (with its air) that lies over [others]. */
    private fun overlap(pose: PlatePose, others: List<ViewRect>): Float {
        val grown = pose.rect.inflated(GAP_DEG)
        var sum = 0f
        for (o in others) sum += grown.overlap(o)
        return sum
    }

    /** Traces the blade of [tape] in the view; false when a part of it is not in front of the viewer. */
    private fun trace(frame: ViewFrame, tape: PlateTape): Boolean {
        if (frame === tracedFrame && tape.a == tracedA && tape.b == tracedB) return traced
        tracedFrame = frame
        tracedA = tape.a
        tracedB = tape.b
        traced = false
        for (i in 0..SAMPLES) {
            val v = frame.see(tape.a.lerp(tape.b, i / SAMPLES.toFloat())) ?: return false
            traceYaw[i] = v.yaw
            tracePitch[i] = v.pitch
            traceHalf[i] = bladeHalfDeg(v.range)
        }
        traced = true
        return true
    }

    /**
     * How far the traced blade, with its width, reaches towards the side ([ny], [np]) of the place
     * ([yaw], [pitch]), within [along] degrees of it along the blade. The width is measured the way
     * [clearsBlade] measures it: the thicker end of each piece, on the axes of the chart ([slant]).
     */
    private fun rise(yaw: Float, pitch: Float, ny: Float, np: Float, along: Float, slant: Float): Float {
        var top = Float.NEGATIVE_INFINITY
        var s0 = (traceYaw[0] - yaw) * np - (tracePitch[0] - pitch) * ny
        var h0 = (traceYaw[0] - yaw) * ny + (tracePitch[0] - pitch) * np
        for (i in 1..SAMPLES) {
            val s1 = (traceYaw[i] - yaw) * np - (tracePitch[i] - pitch) * ny
            val h1 = (traceYaw[i] - yaw) * ny + (tracePitch[i] - pitch) * np
            val thick = max(traceHalf[i - 1], traceHalf[i]) * slant
            val reach = along + thick
            if (max(s0, s1) >= -reach && min(s0, s1) <= reach) {
                val ds = s1 - s0
                if (abs(ds) < 1e-6f) {
                    top = max(top, max(h0, h1) + thick)
                } else {
                    // the height is linear along a piece: its highest point in reach is at one end of the part in reach
                    val u0 = ((-reach - s0) / ds).coerceIn(0f, 1f)
                    val u1 = ((reach - s0) / ds).coerceIn(0f, 1f)
                    top = max(top, max(h0 + (h1 - h0) * u0, h0 + (h1 - h0) * u1) + thick)
                }
            }
            s0 = s1
            h0 = h1
        }
        return top
    }

    private fun clearsBlade(frame: ViewFrame, tape: PlateTape, pose: PlatePose): Boolean {
        if (!trace(frame, tape)) return true
        val grown = pose.rect.inflated(GAP_DEG)
        for (i in 0 until SAMPLES) {
            val thick = max(traceHalf[i], traceHalf[i + 1])
            if (grown.meetsSegment(traceYaw[i], tracePitch[i], traceYaw[i + 1], tracePitch[i + 1], thick)) return false
        }
        return true
    }

    private fun clearsAll(pose: PlatePose, others: List<ViewRect>): Boolean {
        val grown = pose.rect.inflated(GAP_DEG)
        for (o in others) if (grown.intersects(o)) return false
        return true
    }

    private fun bladeHalfDeg(range: Float): Float =
        ViewFrame.angle(LayoutTapeUseCase.WIDTH_M / 2f * scales.tapeWidth(range), range)

    companion object {
        val FRACTIONS = floatArrayOf(0.50f, 0.36f, 0.64f, 0.24f, 0.76f)

        /** Air between the pill and its blade, and around the pill when it is tested against others. */
        const val GAP_DEG = 0.5f

        /** Pieces the blade is traced in. */
        const val SAMPLES = 12

        /** The most a plate is pushed out, beyond the gap, by a blade that curves or widens towards it. */
        const val MAX_PUSH_DEG = 1.5f
        private const val GAP_EPS_DEG = 0.05f
        private const val END_ON_DEG = 0.3f
        private const val SIN_20 = 0.34202f

        /** Half size of a pin's keep-clear box. */
        const val PIN_BOX_DEG = 1.5f

        /** When no place is free: how much worse it is to cover a pin than to cover another plate. */
        private const val PIN_WEIGHT = 2f

        /** Places that differ by less than this, square degrees, tie: the earlier one in the order is kept. */
        private const val TIE_SQ_DEG = 0.05f
    }
}
