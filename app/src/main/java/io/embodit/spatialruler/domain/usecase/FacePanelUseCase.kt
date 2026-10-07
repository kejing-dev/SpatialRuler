package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How a room panel is turned: yaw about the vertical and pitch towards the viewer, radians; no roll. */
data class PanelFacing(val yaw: Float, val pitch: Float) {
    val rotation: Quat4
        get() = Quat4.axisAngle(Vec3.UP, yaw) * Quat4.axisAngle(Vec3.RIGHT, -pitch)

    /** Unit vector along the panel's width (always horizontal). */
    val right: Vec3
        get() = Vec3(cos(yaw), 0f, -sin(yaw))

    /** Unit vector along the panel's height. */
    val up: Vec3
        get() = Vec3(-sin(yaw) * sin(pitch), cos(pitch), -cos(yaw) * sin(pitch))

    /** Unit vector out of the panel's face, towards the viewer. */
    val normal: Vec3
        get() = Vec3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))
}

/**
 * Facing rule for panels that stand in the room (plates, chips, tags): turn about the vertical towards
 * the head, then pitch towards the head, the pitch clamped to ±60 degrees, never roll (the horizontal
 * edges stay level). When the head is almost straight above or below the panel the yaw is undefined:
 * the last yaw is kept.
 */
class FacePanelUseCase {

    fun facing(panel: Vec3, head: Vec3, lastYaw: Float?): PanelFacing {
        val to = head - panel
        val flat = hypot(to.x, to.z)
        val yaw = if (flat < KEEP_YAW_WITHIN_M && lastYaw != null) lastYaw else if (flat < 1e-4f) 0f else atan2(to.x, to.z)
        val pitch = atan2(to.y, flat).coerceIn(-MAX_PITCH_RAD, MAX_PITCH_RAD)
        return PanelFacing(yaw, pitch)
    }

    companion object {
        /** 60 degrees. */
        const val MAX_PITCH_RAD = 1.0471976f
        const val KEEP_YAW_WITHIN_M = 0.1f
    }
}

/**
 * Surface rule for room panels: every corner of a panel stays at least 2 cm on the head's side of
 * each recognised plane whose quad, grown by 5 cm, contains the foot point of that corner. A panel
 * that breaks the rule is pulled towards the head along the ray head -> panel centre; its scale
 * follows the distance, so it keeps its place and size in the view.
 *
 * It is pulled no further than the rule needs. A corner stops breaking the rule either where its ray
 * comes to the head's side of the plane, or, earlier, where its foot point leaves the quad: a plate on
 * a wall above a shelf only has to come in front of the shelf's edge, not down to where its ray passes
 * the height of the shelf (which, in a niche with shelves, brought far plates up to the viewer's face).
 */
class ClearSurfaceUseCase(private val hits: HitSurfaceUseCase = HitSurfaceUseCase()) {

    /**
     * The largest range, not above [range], at which a panel in the direction [toCenter] (unit vector
     * from the head) keeps the rule. [halfWidthPerM] / [halfHeightPerM] are the panel's half sizes per
     * metre of range (its size follows the distance). The result may be below any lower limit the
     * caller has: the caller decides what to do then.
     */
    fun maxRange(
        head: Vec3,
        toCenter: Vec3,
        range: Float,
        halfWidthPerM: Float,
        halfHeightPerM: Float,
        planes: Collection<PlaneSurface>,
        facing: FacePanelUseCase,
        lastYaw: Float? = null,
    ): Float {
        var r = range
        repeat(MAX_PASSES) {
            val center = head + toCenter * r
            val f = facing.facing(center, head, lastYaw)
            val right = f.right
            val up = f.up
            var limit = r
            for (sx in SIGNS) for (sy in SIGNS) {
                // the corner moves along its own ray from the head as the range changes
                val perM = toCenter + right * (sx * halfWidthPerM) + up * (sy * halfHeightPerM)
                val corner = head + perM * r
                for (plane in planes) {
                    if (plane.vertices.isEmpty()) continue
                    var n = plane.normal
                    var headSide = (head - plane.vertices[0]).dot(n)
                    if (headSide < 0f) {
                        n = -n
                        headSide = -headSide
                    }
                    if (headSide < CLEARANCE_M) continue // the head itself is not clear of this plane
                    val depth = (corner - plane.vertices[0]).dot(n)
                    if (depth >= CLEARANCE_M) continue
                    if (!hits.contains(plane, corner, margin = GROW_M)) continue
                    val slope = perM.dot(n)
                    // where the corner's ray comes to the head's side of the plane (never, if it runs along or away from it)
                    val clears = if (slope >= -1e-6f) 0f else (headSide - CLEARANCE_M) / -slope
                    // where, coming from the panel, its foot point leaves the quad
                    val leaves = leavesQuadAt(plane, head, perM, r)
                    if (clears <= 0f && leaves <= 0f) continue // pulling in does not help for this plane
                    val allowed = if (clears > leaves) clears else leaves - EDGE_STEP_M
                    if (allowed < limit) limit = allowed
                }
            }
            if (limit >= r - 1e-4f) return r
            r = limit
        }
        return r
    }

    /**
     * The corner on the ray head + [perM] * range has its foot point inside the grown quad of [plane]
     * at [from]: the range, nearer to the head, at which the foot point leaves the quad (0 when it
     * stays inside all the way to the head).
     */
    private fun leavesQuadAt(plane: PlaneSurface, head: Vec3, perM: Vec3, from: Float): Float {
        if (hits.contains(plane, head + perM * NEAR_M, margin = GROW_M)) return 0f
        var outside = NEAR_M
        var inside = from
        repeat(BISECTIONS) {
            val mid = (outside + inside) / 2f
            if (hits.contains(plane, head + perM * mid, margin = GROW_M)) inside = mid else outside = mid
        }
        return outside
    }

    companion object {
        const val CLEARANCE_M = 0.02f
        const val GROW_M = 0.05f
        private const val MAX_PASSES = 6
        private const val NEAR_M = 0.01f
        private const val BISECTIONS = 14
        private const val EDGE_STEP_M = 0.002f
        private val SIGNS = floatArrayOf(-1f, 1f)
    }
}
