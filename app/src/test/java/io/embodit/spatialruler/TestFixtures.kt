package io.embodit.spatialruler

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.Pin
import io.embodit.spatialruler.domain.model.Placement
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.SurfaceKind
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import org.junit.Assert.assertTrue

/** A small room in Stage metres; the viewer stands at the origin, 1.5 m tall, looking towards -Z. */
object Room {
    val HEAD = Vec3(0f, 1.5f, 0f)

    /** Floor y = 0, x in -2..2, z in -4..0. Its triangles wind so that the normal is +Y. */
    val floor =
        PlaneSurface(
            id = "f1000000-aaaa-bbbb-cccc-000000000001",
            kind = SurfaceKind.FLOOR,
            semanticName = "FLOOR",
            vertices = listOf(Vec3(-2f, 0f, 0f), Vec3(2f, 0f, 0f), Vec3(2f, 0f, -4f), Vec3(-2f, 0f, -4f)),
            indices = listOf(0, 1, 2, 0, 2, 3),
        )

    /**
     * Far wall z = -4, x in -2..2, y in 0..2.6. It winds outwards (normal -Z, away from the viewer),
     * like the wall quads of the emulator: a one-sided test would miss it from inside the room.
     */
    val wall =
        PlaneSurface(
            id = "a2000000-aaaa-bbbb-cccc-000000000002",
            kind = SurfaceKind.WALL,
            semanticName = "WALL",
            vertices = listOf(Vec3(2f, 0f, -4f), Vec3(-2f, 0f, -4f), Vec3(-2f, 2.6f, -4f), Vec3(2f, 2.6f, -4f)),
            indices = listOf(0, 1, 2, 0, 2, 3),
        )

    /** Table top y = 0.75, x in -0.5..0.5, z in -1.5..-1. */
    val table =
        PlaneSurface(
            id = "7ab1e000-aaaa-bbbb-cccc-000000000003",
            kind = SurfaceKind.TABLE,
            semanticName = "TABLE",
            vertices = listOf(Vec3(-0.5f, 0.75f, -1f), Vec3(0.5f, 0.75f, -1f), Vec3(0.5f, 0.75f, -1.5f), Vec3(-0.5f, 0.75f, -1.5f)),
            indices = listOf(0, 1, 2, 0, 2, 3),
        )

    val all = listOf(floor, wall)
}

fun placement(
    point: Vec3,
    normal: Vec3 = Vec3.UP,
    grade: Grade = Grade.COARSE,
    snapped: SnapKind = SnapKind.NONE,
    rangeM: Float = 1f,
    surface: SurfaceKind = SurfaceKind.FLOOR,
): Placement = Placement(point, normal, Vec3.RIGHT, Vec3.AHEAD, surface, "FLOOR:f1000000", grade, snapped, rangeM)

fun pin(
    id: String,
    at: Vec3,
    grade: Grade = Grade.COARSE,
    snapped: SnapKind = SnapKind.NONE,
    far: Boolean = false,
    rangeM: Float = 1f,
    saveState: SaveState = SaveState.SAVED,
    normal: Vec3 = Vec3.UP,
): Pin =
    Pin(
        id = id,
        anchorId = if (saveState == SaveState.SAVED) "anchor-$id" else null,
        base = at,
        normalLocal = normal,
        uLocal = Vec3.RIGHT,
        vLocal = Vec3.AHEAD,
        grade = grade,
        snapped = snapped,
        far = far,
        rangeM = rangeM,
        saveState = saveState,
    )

fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f) {
    assertTrue("expected $expected but was $actual (eps $eps)", abs(expected - actual) <= eps)
}

fun assertNear(expected: Vec3, actual: Vec3, eps: Float = 1e-4f) {
    assertTrue("expected $expected but was $actual (eps $eps)", expected.distanceTo(actual) <= eps)
}
