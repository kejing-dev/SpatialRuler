package io.embodit.spatialruler.domain.port

import io.embodit.spatialruler.domain.model.BlockReason
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.Vec3

/**
 * What the measuring logic needs to know about the room right now. Implemented by the platform layer
 * (head tracking + plane detection); a fake stands in for it in unit tests.
 */
interface MeasureEnvironment {
    /** Position of the viewer's head in Stage metres, or null while tracking has no pose. */
    fun viewOrigin(): Vec3?

    /** Surfaces detected so far. */
    fun surfaces(): Collection<PlaneSurface>

    fun surface(id: String): PlaneSurface?

    /** Null when the spatial features the app needs can be used; otherwise why they cannot. */
    fun availability(): BlockReason? = null
}

/** Sink for the one-line-per-event log (tag `SpatialRuler`). */
fun interface RulerLogger {
    fun log(line: String)
}
