package io.embodit.spatialruler.domain.model

/**
 * A detected surface: a planar polygon (a quad on the emulator) with its vertices already in Stage
 * metres. Triangle winding is not trusted (wall quads wind outwards on the emulator), so every hit
 * test is two-sided and normals are derived from the vertices.
 */
data class PlaneSurface(
    val id: String,
    val kind: SurfaceKind,
    val semanticName: String,
    val vertices: List<Vec3>,
    val indices: List<Int>,
) {
    /** Unit normal of the first non-degenerate triangle; its sign is arbitrary. */
    val normal: Vec3 by lazy {
        var n = Vec3.UP
        var i = 0
        while (i + 2 < indices.size) {
            val v0 = vertices[indices[i]]
            val c = (vertices[indices[i + 1]] - v0).cross(vertices[indices[i + 2]] - v0)
            if (c.length > 1e-9f) {
                n = c.normalized()
                break
            }
            i += 3
        }
        n
    }

    val centroid: Vec3 by lazy {
        if (vertices.isEmpty()) Vec3.ZERO
        else vertices.fold(Vec3.ZERO) { acc, v -> acc + v } / vertices.size.toFloat()
    }

    /** `SEMANTIC:uuid8`, the name used for the plane's collider entity and in logs. */
    val label: String
        get() = "$semanticName:${id.take(8)}"

    /** Boundary of the polygon as closed segments (vertex order as reported). */
    val edges: List<Pair<Vec3, Vec3>>
        get() = vertices.indices.map { vertices[it] to vertices[(it + 1) % vertices.size] }
}

/** Result of a ray / surface intersection. [normal] faces the ray origin. */
data class SurfaceHit(
    val point: Vec3,
    val normal: Vec3,
    val distance: Float,
    val plane: PlaneSurface,
)

/** A fully resolved place to put a pin. */
data class Placement(
    val point: Vec3,
    /** Faces the viewer's side of the surface. */
    val normal: Vec3,
    val uAxis: Vec3,
    val vAxis: Vec3,
    val surface: SurfaceKind,
    val planeLabel: String,
    val grade: Grade,
    val snapped: SnapKind,
    /** Viewer-to-point distance, metres. */
    val rangeM: Float,
    /** Directions that describe the seat it was snapped to (see `SnapResult`); null when not snapped. */
    val seatA: Vec3? = null,
    val seatB: Vec3? = null,
    /** Which path set it, for the log (`PIN add ... via=`): system, controller, hand, synthetic, contact, pull, debug. */
    val via: String = "system",
    /** Set by touching with the pinch point of a hand (contact with the fingers is coarser than with a controller tip). */
    val pinch: Boolean = false,
)

/** A pointing ray in Stage space. Every aiming channel (system tap, tracked ray, test hook) ends here. */
data class PointerRay(val origin: Vec3, val direction: Vec3)
