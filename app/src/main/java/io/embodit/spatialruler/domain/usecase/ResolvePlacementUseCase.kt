package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.Pin
import io.embodit.spatialruler.domain.model.Placement
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.SurfaceHit
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs

/** Outcome of aiming at the room. */
sealed interface AimResult {
    /** The ray points at an existing pin. */
    data class OnPin(val pin: Pin) : AimResult

    /** The ray lands on a detected surface. */
    data class OnSurface(val placement: Placement) : AimResult

    /** Nothing detected along the ray. */
    data object Nothing : AimResult
}

/**
 * Turns a pointing ray into a placement: surface hit, optional snapping, the pin's surface frame and
 * its range. Every aiming channel goes through here, so a test ray and a real pointer behave alike.
 */
class ResolvePlacementUseCase(
    private val hits: HitSurfaceUseCase = HitSurfaceUseCase(),
    private val snapper: SnapPointUseCase = SnapPointUseCase(hits),
    private val contacts: FindContactUseCase = FindContactUseCase(hits),
) {

    /**
     * A ray whose aim is shown before the press (tracked pointer, press-and-pull, test hook): nearest
     * surface along it; a pin in front of that surface wins. [exclude] is a pin the ray must not pick
     * (the head of the tape that is being pulled).
     */
    fun aim(
        ray: PointerRay,
        planes: Collection<PlaneSurface>,
        pins: List<Pin>,
        snapEnabled: Boolean,
        tapeHead: Pin?,
        grade: Grade,
        pinHeadHeightM: Float = PIN_HEAD_M,
        exclude: String? = null,
        via: String = "synthetic",
    ): AimResult {
        val raw = hits.nearest(planes, ray.origin, ray.direction)
        val pickable = if (exclude == null) pins else pins.filter { it.id != exclude }
        val pin = snapper.pickPin(ray, pickable, pinHeadHeightM, raw?.distance)
        if (pin != null) return AimResult.OnPin(pin)
        if (raw == null) return AimResult.Nothing
        return AimResult.OnSurface(resolve(raw, ray.origin, planes, snapEnabled, tapeHead, grade, SnapChannel.TRACKED).copy(via = via))
    }

    /**
     * Contact channel: the tool tip itself is the point; it is not projected onto the fitted plane. A
     * pin whose head or foot is within its touch radius of the tip is picked instead (the only snap of
     * this channel). The new pin belongs to the recognised surface within reach; none in reach: nothing.
     */
    fun contact(
        tip: Vec3,
        viewOrigin: Vec3?,
        planes: Collection<PlaneSurface>,
        pins: List<Pin>,
        pinch: Boolean,
        pinHeadHeightM: Float = PIN_HEAD_M,
    ): AimResult {
        val origin = viewOrigin ?: (tip + Vec3.UP)
        var picked: Pin? = null
        var nearest = SnapPointUseCase.PIN_TOUCH_RADIUS_M
        for (pin in pins) {
            if (pin.elsewhere) continue
            val d = minOf(pin.position.distanceTo(tip), (pin.position + pin.normal * pinHeadHeightM).distanceTo(tip))
            if (d <= nearest) {
                nearest = d
                picked = pin
            }
        }
        if (picked != null) return AimResult.OnPin(picked)
        val hit = contacts.find(tip, origin, planes) ?: return AimResult.Nothing
        val (u, v) = surfaceAxes(hit.normal, tip - origin)
        return AimResult.OnSurface(
            Placement(tip, hit.normal, u, v, hit.plane.kind, hit.plane.label, Grade.CONTACT, SnapKind.NONE, tip.distanceTo(origin), via = "contact", pinch = pinch)
        )
    }

    /**
     * A system tap that already landed on [plane]'s collider at [stagePoint]. The point is put back
     * onto the mathematical plane along the viewing ray when the viewer position is known. Nothing
     * showed where the tap would land, so snapping follows the pointer-channel rule.
     */
    fun onTappedSurface(
        plane: PlaneSurface,
        stagePoint: Vec3,
        viewOrigin: Vec3?,
        planes: Collection<PlaneSurface>,
        snapEnabled: Boolean,
        tapeHead: Pin?,
        grade: Grade,
    ): Placement {
        val origin = viewOrigin ?: (stagePoint + plane.normal)
        val toPoint = stagePoint - origin
        val alongRay =
            if (viewOrigin != null && toPoint.length > 1e-3f) {
                hits.onInfinitePlane(plane, origin, toPoint.normalized())
            } else null
        val raw =
            alongRay?.takeIf { it.point.distanceTo(stagePoint) < MAX_REPROJECTION_M }
                ?: SurfaceHit(
                    hits.project(plane, stagePoint),
                    facing(plane.normal, origin - stagePoint),
                    toPoint.length,
                    plane,
                )
        return resolve(raw, origin, planes, snapEnabled, tapeHead, grade, SnapChannel.POINTER)
    }

    /** A point given directly (test hook): it is attached to the nearest detected surface, if close. */
    fun atPoint(point: Vec3, viewOrigin: Vec3?, planes: Collection<PlaneSurface>, grade: Grade): Placement {
        val origin = viewOrigin ?: (point + Vec3.UP)
        var nearest: PlaneSurface? = null
        var nearestDistance = ATTACH_MAX_M
        for (plane in planes) {
            val d = hits.distanceToPlane(plane, point)
            if (d < nearestDistance && hits.contains(plane, point, margin = 0.01f)) {
                nearestDistance = d
                nearest = plane
            }
        }
        val range = point.distanceTo(origin)
        if (nearest == null) {
            val normal = Vec3.UP
            val (u, v) = surfaceAxes(normal, point - origin)
            return Placement(point, normal, u, v, SurfaceKind.OTHER, "FREE", grade, SnapKind.NONE, range, via = "debug")
        }
        val onPlane = hits.project(nearest, point)
        val normal = facing(nearest.normal, origin - onPlane)
        val (u, v) = surfaceAxes(normal, onPlane - origin)
        return Placement(onPlane, normal, u, v, nearest.kind, nearest.label, grade, SnapKind.NONE, range, via = "debug")
    }

    private fun resolve(
        raw: SurfaceHit,
        viewOrigin: Vec3,
        planes: Collection<PlaneSurface>,
        snapEnabled: Boolean,
        tapeHead: Pin?,
        grade: Grade,
        channel: SnapChannel,
    ): Placement {
        val snapped =
            if (snapEnabled) snapper.snap(raw, viewOrigin, planes, tapeHead?.position, channel)
            else SnapResult(raw.point, raw.plane, SnapKind.NONE)
        val point = snapped.point
        val normal = facing(snapped.plane.normal, viewOrigin - point)
        val (u, v) = surfaceAxes(normal, point - viewOrigin)
        return Placement(
            point = point,
            normal = normal,
            uAxis = u,
            vAxis = v,
            surface = snapped.plane.kind,
            planeLabel = snapped.plane.label,
            grade = grade,
            snapped = snapped.kind,
            rangeM = point.distanceTo(viewOrigin),
            seatA = snapped.directionA,
            seatB = snapped.directionB,
        )
    }

    companion object {
        /** Height above its surface at which a tape is attached to a pin: under the flange of its head. */
        const val PIN_HEAD_M = 0.0145f
        private const val MAX_REPROJECTION_M = 0.05f
        private const val ATTACH_MAX_M = 0.05f

        /** [normal] or its opposite, whichever points to the side of [towards]. */
        fun facing(normal: Vec3, towards: Vec3): Vec3 = if (normal.dot(towards) < 0f) -normal else normal

        /**
         * The two nudge directions on a surface. U is "to the viewer's right along the surface"; V is
         * "away from the viewer" on a horizontal surface and "up" on a vertical one.
         */
        fun surfaceAxes(normal: Vec3, viewDirection: Vec3): Pair<Vec3, Vec3> {
            val n = normal.normalized()
            if (abs(n.y) > 0.5f) {
                val upSide = if (n.y < 0f) -n else n
                var right = viewDirection.cross(Vec3.UP).rejectFrom(upSide)
                if (right.length < 1e-3f) right = Vec3.RIGHT.rejectFrom(upSide)
                val u = right.normalized()
                return u to upSide.cross(u).normalized()
            }
            var u = Vec3.UP.cross(n)
            if (u.length < 1e-3f) u = Vec3.RIGHT
            u = u.normalized()
            return u to n.cross(u).normalized()
        }
    }
}
