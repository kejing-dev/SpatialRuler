package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Pin
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.SurfaceHit
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.acos

/**
 * Where a placement ends up after snapping. [plane] is the surface the pin will sit on.
 * [directionA] / [directionB] describe the seat for the mark that is drawn when the pin lands: the
 * two plane edges that leave a corner, the edge that was snapped to, or the direction from the point
 * towards the tape head (plumb / level).
 */
data class SnapResult(
    val point: Vec3,
    val plane: PlaneSurface,
    val kind: SnapKind,
    val directionA: Vec3? = null,
    val directionB: Vec3? = null,
)

/**
 * Which rule decides the seats.
 *
 * [POINTER]: a system tap (gaze and pinch, emulator mouse). No reticle announces a seat before the
 * pinch, so only seats on the tapped plane count, and only within a short metric distance of the
 * tapped point (40 mm for a corner, 30 mm for an edge, plumb or level) on top of the angular radius.
 *
 * [TRACKED]: a tracked ray or press-and-pull. A reticle shows the seat before the press, so the
 * angular rule alone decides, including seats on other planes that the hit surface does not hide.
 */
enum class SnapChannel { POINTER, TRACKED }

/**
 * Snapping of the design (can be switched off on the instrument body). Priority: corner of a detected
 * surface, then its edge, then plumb / level relative to the tape head. Seats are judged by visual
 * angle from the viewer and only offered when they are not hidden behind the surface that was hit.
 *
 * Plumb = the tape stands vertical: the point of a level surface straight below or above the tape
 * head, or the point of an upright surface straight above or below a tape head on that same surface.
 * Level = the tape lies horizontal: the point of an upright surface at the height of the tape head.
 * Picking an existing pin is not snapping: it is pointing at the pin, see [pickPin].
 */
class SnapPointUseCase(private val hits: HitSurfaceUseCase = HitSurfaceUseCase()) {

    fun snap(
        raw: SurfaceHit,
        viewOrigin: Vec3,
        planes: Collection<PlaneSurface>,
        tapeHead: Vec3?,
        channel: SnapChannel = SnapChannel.TRACKED,
    ): SnapResult {
        val toRaw = raw.point - viewOrigin
        val rawRange = toRaw.length
        if (rawRange < 1e-4f) return SnapResult(raw.point, raw.plane, SnapKind.NONE)
        val rawDir = toRaw / rawRange
        val pointer = channel == SnapChannel.POINTER
        fun visible(q: Vec3) = q.distanceTo(viewOrigin) <= rawRange + OCCLUSION_SLACK_M
        fun angleTo(q: Vec3) = angleDeg(rawDir, (q - viewOrigin).normalized())
        fun within(q: Vec3, capM: Float) = !pointer || q.distanceTo(raw.point) <= capM
        val candidates: Collection<PlaneSurface> = if (pointer) listOf(raw.plane) else planes

        // 1. corners of detected surfaces
        var bestAngle = CORNER_DEG
        var best: SnapResult? = null
        for (plane in candidates) {
            val v = plane.vertices
            for (i in v.indices) {
                val corner = v[i]
                val a = angleTo(corner)
                if (a < bestAngle && visible(corner) && within(corner, POINTER_CORNER_MAX_M)) {
                    bestAngle = a
                    val next = (v[(i + 1) % v.size] - corner).normalized()
                    val previous = (v[(i + v.size - 1) % v.size] - corner).normalized()
                    best = SnapResult(corner, ownerOf(corner, raw.plane, plane), SnapKind.CORNER, next, previous)
                }
            }
        }
        if (best != null) return best

        // 2. edges of detected surfaces
        bestAngle = EDGE_DEG
        for (plane in candidates) {
            for ((a, b) in plane.edges) {
                val q = closestOnSegment(raw.point, a, b)
                val angle = angleTo(q)
                if (angle < bestAngle && visible(q) && within(q, POINTER_GUIDE_MAX_M)) {
                    bestAngle = angle
                    best = SnapResult(q, ownerOf(q, raw.plane, plane), SnapKind.EDGE, (b - a).normalized())
                }
            }
        }
        if (best != null) return best

        // 3. plumb / level relative to the tape head, inside the surface that was hit
        if (tapeHead != null) {
            val n = raw.plane.normal
            if (abs(n.y) > HORIZONTAL_NY && abs(tapeHead.y - raw.point.y) > 0.02f) {
                // the point of the surface straight below / above the tape head
                val y = raw.point.y - (n.x * (tapeHead.x - raw.point.x) + n.z * (tapeHead.z - raw.point.z)) / n.y
                val q = Vec3(tapeHead.x, y, tapeHead.z)
                if (hits.contains(raw.plane, q) &&
                    angleTo(q) < GUIDE_DEG &&
                    q.distanceTo(raw.point) < (if (pointer) POINTER_GUIDE_MAX_M else PLUMB_MAX_M)
                ) {
                    return SnapResult(q, raw.plane, SnapKind.PLUMB, (tapeHead - q).normalized())
                }
            } else if (abs(n.y) < VERTICAL_NY) {
                // plumb on an upright surface: straight above or below a tape head that sits on the same surface
                if (abs(tapeHead.y - raw.point.y) > 0.02f && hits.distanceToPlane(raw.plane, tapeHead) < ON_SURFACE_M) {
                    val over = Vec3(tapeHead.x, raw.point.y, tapeHead.z)
                    val q = over - n * (over - raw.point).dot(n)
                    if (hits.contains(raw.plane, q) &&
                        angleTo(q) < GUIDE_DEG &&
                        q.distanceTo(raw.point) < (if (pointer) POINTER_GUIDE_MAX_M else PLUMB_MAX_M)
                    ) {
                        return SnapResult(q, raw.plane, SnapKind.PLUMB, (tapeHead - q).normalized())
                    }
                }
                val dy = tapeHead.y - raw.point.y
                if (abs(dy) < (if (pointer) POINTER_GUIDE_MAX_M else LEVEL_MAX_M) && abs(dy) > 0.0005f) {
                    val upInPlane = Vec3.UP.rejectFrom(n).normalized()
                    if (upInPlane.y > 0.5f) {
                        val q = raw.point + upInPlane * (dy / upInPlane.y)
                        val allowed = !pointer || (angleTo(q) < GUIDE_DEG && q.distanceTo(raw.point) <= POINTER_GUIDE_MAX_M)
                        if (allowed && hits.contains(raw.plane, q)) {
                            return SnapResult(q, raw.plane, SnapKind.LEVEL, (tapeHead - q).normalized())
                        }
                    }
                }
            }
        }
        return SnapResult(raw.point, raw.plane, SnapKind.NONE)
    }

    /**
     * The pin the ray points at, if any: its head or foot is within about one degree of the ray (and
     * never less than its 28 mm touch radius). [maxRange] hides pins behind the surface that was hit.
     */
    fun pickPin(ray: PointerRay, pins: List<Pin>, headHeightM: Float, maxRange: Float?): Pin? {
        var best: Pin? = null
        var bestAngle = Float.MAX_VALUE
        for (pin in pins) {
            if (pin.elsewhere) continue
            val foot = pin.position
            val head = foot + pin.normal * headHeightM
            val range = (head - ray.origin).length
            if (range < 1e-3f) continue
            if (maxRange != null && range > maxRange + OCCLUSION_SLACK_M) continue
            val limit = maxOf(PIN_DEG, Math.toDegrees(kotlin.math.atan2(PIN_TOUCH_RADIUS_M, range).toDouble()).toFloat())
            val angle =
                minOf(
                    angleDeg(ray.direction, (head - ray.origin).normalized()),
                    angleDeg(ray.direction, (foot - ray.origin).normalized()),
                )
            if (angle <= limit && angle < bestAngle) {
                bestAngle = angle
                best = pin
            }
        }
        return best
    }

    /** A seat that lies on the surface that was hit stays on it; otherwise it belongs to its own surface. */
    private fun ownerOf(q: Vec3, hitPlane: PlaneSurface, seatPlane: PlaneSurface): PlaneSurface =
        if (hits.distanceToPlane(hitPlane, q) < 1e-3f) hitPlane else seatPlane

    companion object {
        const val PIN_DEG = 1.0f
        const val CORNER_DEG = 0.9f
        const val EDGE_DEG = 0.6f
        const val GUIDE_DEG = 0.85f
        const val PIN_TOUCH_RADIUS_M = 0.028f

        /** Pointer channel: a seat farther than this from the tapped point is not taken. */
        const val POINTER_CORNER_MAX_M = 0.040f
        const val POINTER_GUIDE_MAX_M = 0.030f
        private const val OCCLUSION_SLACK_M = 0.05f
        private const val PLUMB_MAX_M = 0.10f
        private const val LEVEL_MAX_M = 0.05f

        /** A tape head counts as sitting on a surface within this distance of it. */
        private const val ON_SURFACE_M = 0.01f
        private const val HORIZONTAL_NY = 0.9f
        private const val VERTICAL_NY = 0.1f

        fun angleDeg(unitA: Vec3, unitB: Vec3): Float =
            Math.toDegrees(acos(unitA.dot(unitB).coerceIn(-1f, 1f)).toDouble()).toFloat()

        fun closestOnSegment(p: Vec3, a: Vec3, b: Vec3): Vec3 {
            val ab = b - a
            val len2 = ab.dot(ab)
            if (len2 < 1e-12f) return a
            val k = ((p - a).dot(ab) / len2).coerceIn(0f, 1f)
            return a + ab * k
        }
    }
}
