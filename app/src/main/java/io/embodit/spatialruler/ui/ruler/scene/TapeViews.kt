package io.embodit.spatialruler.ui.ruler.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector3
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.UnitSystem
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.LayoutTapeUseCase
import io.embodit.spatialruler.domain.usecase.ScaleForDistanceUseCase
import io.embodit.spatialruler.domain.usecase.SelectTickLevelUseCase
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlinx.coroutines.yield

/**
 * Meshes shared by every tape and guide of the scene. A mesh costs a frame to create, so whatever can
 * be a scaled or repeated copy of one shared mesh is: the blade while it is pulled out or retracted
 * (printed pieces laid end to end), the outline of a selected tape, the hook, the rider and stem of a
 * reading plate, and the dashed candidate threads.
 */
internal class TapeMeshes
private constructor(
    val band: MeshResource,
    private val hookTongue: MeshResource,
    private val hookLip: MeshResource,
    val rider: MeshResource,
    val stem: MeshResource,
    private val metricFine: MeshResource,
    private val metricCoarse: MeshResource,
    private val imperial: MeshResource,
    private val shared: SharedResources,
) {
    private val dashes = HashMap<Int, MeshResource>()

    /** Dashed unit strip with about [wanted] dashes (one of two shared meshes, made on first use). */
    fun dashes(wanted: Float): MeshResource {
        val count = if (wanted < DASH_SWITCH) DASHES_SHORT else DASHES_LONG
        return dashes.getOrPut(count) { shared.keep(MeshFactory.unitDashes("guide_dashes_$count", count)) }
    }

    /**
     * The printed piece for a moving blade: 10 cm with 1 cm ticks when the finest level is drawn
     * ([step] 1), 10 cm with 5 cm ticks otherwise; one foot with inch ticks in feet and inches.
     */
    fun segment(system: UnitSystem, step: Int): MeshResource =
        when {
            system == UnitSystem.IMPERIAL -> imperial
            step <= 1 -> metricFine
            else -> metricCoarse
        }

    /**
     * The steel hook of a blade whose zero end is at the origin of [parent] and that runs towards +X:
     * a tongue that continues the blade past the zero end, and a lip at its end, bent to the back face.
     */
    fun hook(parent: Entity, materials: SceneMaterials): List<Entity> {
        val tongue = ModelEntity(hookTongue, materials.steel)
        tongue.setLocal(position = Vec3(-HOOK_TONGUE_M / 2f, 0f, -HOOK_THICKNESS_M / 2f))
        parent.addChild(tongue)
        val lip = ModelEntity(hookLip, materials.steel)
        lip.setLocal(position = Vec3(-HOOK_TONGUE_M - HOOK_THICKNESS_M / 2f, 0f, -HOOK_LIP_M / 2f))
        parent.addChild(lip)
        return listOf(tongue, lip)
    }

    companion object {
        private const val DASHES_SHORT = 32
        private const val DASHES_LONG = 128
        private const val DASH_SWITCH = 64f

        /** Tongue 9 x 20 x 1.2 mm, lip 1.2 x 20 x 7 mm. */
        const val HOOK_TONGUE_M = 0.009f
        private const val HOOK_WIDTH_M = 0.020f
        private const val HOOK_THICKNESS_M = 0.0012f
        private const val HOOK_LIP_M = 0.007f

        /** The rider is a box 22 mm along the tape and 6 mm through it; across it is scaled to the blade. */
        const val RIDER_ALONG_M = 0.022f
        const val RIDER_ACROSS_M = 0.022f
        private const val RIDER_THROUGH_M = 0.006f

        /** The stem is a cylinder of unit height, 3 mm thick; it is scaled to its length. */
        private const val STEM_RADIUS_M = 0.0015f

        /** The plain band and the hook: everything the instrument body and the first pin need. */
        suspend fun build(shared: SharedResources, layout: LayoutTapeUseCase): TapeMeshes {
            val band = shared.keep(MeshFactory.unitBand("tape_band"))
            yield()
            val tongue = shared.keep(MeshResource.createBox(Vector3(HOOK_TONGUE_M, HOOK_WIDTH_M, HOOK_THICKNESS_M), 0f))
            yield()
            val lip = shared.keep(MeshResource.createBox(Vector3(HOOK_THICKNESS_M, HOOK_WIDTH_M, HOOK_LIP_M), 0f))
            yield()
            val rider = shared.keep(MeshResource.createBox(Vector3(RIDER_ALONG_M, RIDER_ACROSS_M, RIDER_THROUGH_M), 0.0015f))
            yield()
            val stem = shared.keep(MeshResource.createCylinder(1f, STEM_RADIUS_M))
            yield()
            val half = LayoutTapeUseCase.WIDTH_M / 2f
            fun piece(system: UnitSystem, step: Int, name: String): MeshResource =
                shared.keep(MeshFactory.faceMesh(layout.segment(system, step), LayoutTapeUseCase.segmentLength(system), half, name))
            val fine = piece(UnitSystem.METRIC, 1, "seg_metric_fine")
            yield()
            val coarse = piece(UnitSystem.METRIC, 5, "seg_metric_coarse")
            yield()
            val imperial = piece(UnitSystem.IMPERIAL, 1, "seg_imperial")
            yield()
            return TapeMeshes(band, tongue, lip, rider, stem, fine, coarse, imperial, shared)
        }
    }
}

/**
 * A tape blade: a flat 16 mm band, printed at true scale (ticks, red whole-unit bands, red zero end,
 * dark rim), with its steel hook.
 *
 * Local frame: the origin is the **zero end**, the end with the hook, which lies on the tail pin (or
 * is the free end while the blade moves). +X runs along the blade to where it comes out of the head
 * pin, +Y across it, +Z out of the printed face.
 *
 * Which way the face looks: within 1 m of the head the blade lies flat (on the surface both pins sit
 * on, otherwise face up); beyond 2.2 m it turns about its own axis to face the viewer; in between it
 * blends from one to the other.
 *
 * The printed face is one mesh (palette-textured, see [SceneMaterials.tapeFace]) that is built once
 * for the full length and rebuilt only when the length, the unit system or the tick level changes,
 * within the scene's per-frame [MeshBudget]. While the blade is pulled out or retracted, and until its
 * face has been built, it is drawn with the shared printed pieces laid end to end from the zero end
 * and the plain band for the rest: motion never builds a mesh.
 */
internal class TapeBlade(
    parent: Entity,
    name: String,
    private val materials: SceneMaterials,
    private val layout: LayoutTapeUseCase,
    private val ticks: SelectTickLevelUseCase,
    private val scales: ScaleForDistanceUseCase,
    private val meshes: TapeMeshes,
    private val budget: MeshBudget,
) {
    val root = Entity()
    private val plain: Entity = ModelEntity(meshes.band, materials.yellowTwoSided)
    private val zeroCap: Entity = ModelEntity(meshes.band, materials.red)
    private val faceHolder = Entity()
    private val face = DynamicMesh(faceHolder, materials.tapeFace, "${name}_face")
    private val pieceHolder = Entity()
    private val pieces = ArrayList<Entity>()
    private var pieceMesh: MeshResource? = null
    private var piecesShown = 0
    private val outlineLow: Entity = ModelEntity(meshes.band, materials.edge)
    private val outlineHigh: Entity = ModelEntity(meshes.band, materials.edge)
    private val hookHolder = Entity()
    private val hookParts: List<Entity>

    /** The frame the blade was last drawn in: what rides on it (rider, shadow) follows it. */
    var rotation: Quat4 = Quat4.IDENTITY
        private set
    var across: Vec3 = Vec3.UP
        private set
    var faceNormal: Vec3 = Vec3(0f, 0f, 1f)
        private set
    var widthScale = 1f
        private set

    private var builtLength = -1f
    private var builtSystem: UnitSystem? = null
    private var builtStep = -1
    private var lastFacing: Vec3 = Vec3(0f, 0f, 1f)
    private var stale = false
    private var rootOn = true
    private var faceOn = true
    private var plainOn = true
    private var capOn = true
    private var outlineOn = true
    private var plainFrom = -1f
    private var plainLength = -1f
    private var plainWidth = -1f
    private var capLength = -1f
    private var capWidth = -1f
    private var faceWidth = -1f
    private var pieceWidth = -1f
    private var hookWidth = -1f
    private var outlineLength = -1f
    private var outlineWidth = -1f
    private var opacity = 1f

    init {
        runCatching { root.setName(name) }
        parent.addChild(root)
        root.addChild(plain)
        root.addChild(zeroCap)
        root.addChild(faceHolder)
        root.addChild(pieceHolder)
        root.addChild(outlineLow)
        root.addChild(outlineHigh)
        root.addChild(hookHolder)
        hookParts = meshes.hook(hookHolder, materials)
        showFace(false)
        showPlain(false)
        showCap(false)
        showOutline(false)
    }

    /**
     * Draws the blade between [zeroEnd] (hook) and [outEnd] (where it comes out of the head pin).
     *
     * @param settled false while the length is changing: printed pieces are drawn, no mesh is built
     * @param reference where the face looks at close range (unit vector; made square to the blade);
     *   null: up
     * @param widen true to widen the band with the viewing distance (constant visual angle from 1.5 m);
     *   false for the stub on a pin, which follows the pin's own scale through [uniformScale]
     * @param fixedStep tick level to print whatever the viewing distance (the stub on a pin)
     * @param sway sideways offset across the blade, metres (the settling wobble)
     * @return false when there is nothing to draw
     */
    fun draw(
        zeroEnd: Vec3,
        outEnd: Vec3,
        viewer: Vec3,
        system: UnitSystem,
        selected: Boolean,
        settled: Boolean,
        reference: Vec3? = null,
        uniformScale: Float = 1f,
        widen: Boolean = true,
        fixedStep: Int? = null,
        sway: Float = 0f,
    ): Boolean {
        val full = outEnd - zeroEnd
        val length = full.length
        if (length < LayoutTapeUseCase.MIN_LENGTH_M) {
            showRoot(false)
            return false
        }
        showRoot(true)
        val dir = full / length
        val view = viewer - (zeroEnd + dir * (length / 2f))
        val distance = view.length
        var facing = view.rejectFrom(dir)
        facing = if (facing.length < 1e-3f) lastFacing.rejectFrom(dir).let { if (it.length < 1e-3f) Vec3.UP.rejectFrom(dir) else it } else facing
        if (facing.length < 1e-3f) facing = Vec3.RIGHT.rejectFrom(dir)
        facing = facing.normalized()
        lastFacing = facing
        var flat = (reference ?: Vec3.UP).rejectFrom(dir)
        flat = if (flat.length < 1e-3f) facing else flat.normalized()
        val axisZ = turn(flat, facing, dir, smoothStep(FLAT_UNTIL_M, FACING_FROM_M, distance))
        val axisY = axisZ.cross(dir).normalized()
        val turned = Quat4.fromBasis(dir, axisY, axisZ)
        rotation = turned
        across = axisY
        faceNormal = axisZ
        root.setLocal(position = zeroEnd + axisY * sway, rotation = turned, scale = Vec3(uniformScale, uniformScale, uniformScale))

        val width = if (widen) scales.tapeWidth(distance) else 1f
        widthScale = width
        val local = length / uniformScale

        var faceVisible = false
        val step = fixedStep ?: ticks.finestStepWithHysteresis(distance, system, if (builtSystem == system) builtStep.takeIf { it > 0 } else null)
        if (settled) {
            val lengthStale = abs(local - builtLength) > LENGTH_EPS_M
            val styleStale = system != builtSystem || step != builtStep
            if ((lengthStale || styleStale) && budget.take()) rebuild(local, system, step)
            // a face with another tick level is still a correct drawing; one of another length is not
            faceVisible = builtLength > 0f && abs(local - builtLength) <= LENGTH_EPS_M && builtSystem == system
        }
        showFace(faceVisible)
        if (faceVisible && abs(width - faceWidth) > WIDTH_EPS) {
            faceHolder.setLocal(scale = Vec3(1f, width, 1f))
            faceWidth = width
        }
        if (faceVisible) {
            showPieces(0)
            showPlain(false)
            showCap(false)
        } else {
            drawMoving(local, width, system, fixedStep ?: ticks.finestStep(distance, system))
        }

        val outline = selected && settled
        showOutline(outline)
        if (outline && (abs(local - outlineLength) > 1e-5f || abs(width - outlineWidth) > WIDTH_EPS)) {
            val offset = (LayoutTapeUseCase.WIDTH_M / 2f + OUTLINE_M / 2f) * width
            val scale = Vec3(local, OUTLINE_M * width, 1f)
            outlineLow.setLocal(position = Vec3(0f, -offset, 0f), scale = scale)
            outlineHigh.setLocal(position = Vec3(0f, offset, 0f), scale = scale)
            outlineLength = local
            outlineWidth = width
        }
        if (abs(width - hookWidth) > WIDTH_EPS) {
            hookHolder.setLocal(scale = Vec3(1f, width, 1f))
            hookWidth = width
        }
        return true
    }

    /** The blade while its length changes: printed pieces from the zero end, plain band for the rest. */
    private fun drawMoving(length: Float, width: Float, system: UnitSystem, step: Int) {
        val mesh = meshes.segment(system, step)
        val pieceLength = LayoutTapeUseCase.segmentLength(system)
        if (mesh !== pieceMesh) {
            pieces.forEach { runCatching { it.destroy(true) } }
            pieces.clear()
            piecesShown = 0
            pieceMesh = mesh
        }
        val count = minOf(MAX_PIECES, (length / pieceLength + 1e-4f).toInt())
        while (pieces.size < count) {
            val piece = ModelEntity(mesh, if (stale) materials.tapeFaceStale else materials.tapeFace)
            piece.setLocal(position = Vec3(pieces.size * pieceLength, 0f, 0f))
            pieceHolder.addChild(piece)
            pieces += piece
        }
        showPieces(count)
        if (count > 0 && abs(width - pieceWidth) > WIDTH_EPS) {
            pieceHolder.setLocal(scale = Vec3(1f, width, 1f))
            pieceWidth = width
        }
        val from = count * pieceLength
        val rest = length - from
        showPlain(rest > 1e-4f)
        if (rest > 1e-4f && (abs(from - plainFrom) > 1e-5f || abs(rest - plainLength) > 1e-5f || abs(width - plainWidth) > WIDTH_EPS)) {
            plain.setLocal(position = Vec3(from, 0f, 0f), scale = Vec3(rest, LayoutTapeUseCase.WIDTH_M * width, 1f))
            plainFrom = from
            plainLength = rest
            plainWidth = width
        }
        // the red zero end lies over the first piece
        val cap = minOf(length, LayoutTapeUseCase.ZERO_END_M)
        showCap(true)
        if (abs(cap - capLength) > 1e-5f || abs(width - capWidth) > WIDTH_EPS) {
            zeroCap.setLocal(position = Vec3(0f, 0f, CAP_LIFT_M), scale = Vec3(cap, (LayoutTapeUseCase.WIDTH_M - 2f * LayoutTapeUseCase.EDGE_M) * width, 1f))
            capLength = cap
            capWidth = width
        }
    }

    fun hide() {
        showRoot(false)
    }

    fun setOpacity(value: Float) {
        if (value == opacity) return
        opacity = value
        root.setOpacity(value)
    }

    /** Colours off (tracking lost) or on again. */
    fun setStale(on: Boolean) {
        if (on == stale) return
        stale = on
        face.setMaterial(if (on) materials.tapeFaceStale else materials.tapeFace)
        plain.setMaterial(if (on) materials.stale else materials.yellowTwoSided)
        zeroCap.setMaterial(if (on) materials.stale else materials.red)
        pieces.forEach { it.setMaterial(if (on) materials.tapeFaceStale else materials.tapeFace) }
    }

    /** Re-applies materials after the lit / unlit choice changed. */
    fun refreshMaterials() {
        hookParts.forEach { it.setMaterial(materials.steel) }
    }

    private fun rebuild(length: Float, system: UnitSystem, step: Int) {
        val geometry = layout.layout(length, system, step, LayoutTapeUseCase.EDGE_M)
        face.update(MeshFactory.faceModel(geometry), MeshFactory.rectBounds(length, LayoutTapeUseCase.WIDTH_M / 2f))
        builtLength = length
        builtSystem = system
        builtStep = step
    }

    private fun showRoot(on: Boolean) {
        if (on != rootOn) {
            root.enabled = on
            rootOn = on
        }
    }

    private fun showFace(on: Boolean) {
        if (on != faceOn) {
            faceHolder.enabled = on
            faceOn = on
        }
    }

    private fun showPlain(on: Boolean) {
        if (on != plainOn) {
            plain.enabled = on
            plainOn = on
        }
    }

    private fun showCap(on: Boolean) {
        if (on != capOn) {
            zeroCap.enabled = on
            capOn = on
        }
    }

    private fun showPieces(count: Int) {
        if (count == piecesShown) return
        for (i in pieces.indices) {
            val on = i < count
            if (on != (i < piecesShown)) pieces[i].enabled = on
        }
        piecesShown = count
    }

    private fun showOutline(on: Boolean) {
        if (on != outlineOn) {
            outlineLow.enabled = on
            outlineHigh.enabled = on
            outlineOn = on
        }
    }

    fun destroy() {
        runCatching { root.destroy(true) }
    }

    companion object {
        /** A selected tape gets a wider dark rim: 1.2 mm more on each side (1.6 mm -> 2.8 mm). */
        const val OUTLINE_M = LayoutTapeUseCase.EDGE_SELECTED_M - LayoutTapeUseCase.EDGE_M

        /** Within this distance of the head the blade lies flat; from [FACING_FROM_M] it faces the viewer. */
        const val FLAT_UNTIL_M = 1.0f
        const val FACING_FROM_M = 2.2f

        /** A moving blade is drawn with at most this many printed pieces (6 m in metric). */
        const val MAX_PIECES = 60
        private const val CAP_LIFT_M = 0.00015f
        private const val LENGTH_EPS_M = 0.0004f
        private const val WIDTH_EPS = 0.01f

        fun smoothStep(from: Float, to: Float, x: Float): Float {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** [a] turned about [axis] towards [b] by the share [t] of the angle between them (both square to the axis). */
        fun turn(a: Vec3, b: Vec3, axis: Vec3, t: Float): Vec3 {
            if (t <= 0f) return a
            if (t >= 1f) return b
            val angle = atan2(axis.dot(a.cross(b)), a.dot(b))
            return Quat4.axisAngle(axis, angle * t).rotate(a).normalized()
        }
    }
}

/**
 * A dashed guide between two points (candidate threads from the selected pin): white dashes on a dark
 * casing 2.2 times as wide, so that it also shows on a white wall. It faces the viewer like a blade
 * and gets wider with distance. It is the shared dashed unit strip scaled to the length, so showing a
 * guide builds no mesh.
 */
internal class GuideThread(
    parent: Entity,
    name: String,
    private val materials: SceneMaterials,
    private val scales: ScaleForDistanceUseCase,
    private val meshes: TapeMeshes,
) {
    val root = Entity()
    private var strip: Entity? = null
    private var casing: Entity? = null
    private var stripMesh: MeshResource? = null

    init {
        runCatching { root.setName(name) }
        parent.addChild(root)
        root.enabled = false
    }

    fun draw(from: Vec3, to: Vec3, head: Vec3) {
        val full = to - from
        val length = full.length
        if (length < 0.01f) {
            root.enabled = false
            return
        }
        val dir = full / length
        val mid = from + dir * (length / 2f)
        val view = head - mid
        var axisZ = view.rejectFrom(dir)
        if (axisZ.length < 1e-3f) axisZ = Vec3.UP.rejectFrom(dir)
        if (axisZ.length < 1e-3f) axisZ = Vec3.RIGHT.rejectFrom(dir)
        axisZ = axisZ.normalized()
        val width = scales.guideWidthM(view.length)
        // dashes about five widths apart
        val mesh = meshes.dashes(length / (width * 5f))
        if (mesh !== stripMesh) {
            strip?.let { runCatching { it.destroy(true) } }
            casing?.let { runCatching { it.destroy(true) } }
            casing =
                ModelEntity(mesh, materials.threadCasing).also {
                    // the root is scaled to (length, width): the casing is 2.2 widths wide and 0.2 mm behind
                    it.setLocal(position = Vec3(0f, 0f, -0.0002f), scale = Vec3(1f, CASING_WIDTHS, 1f))
                    root.addChild(it)
                }
            strip = ModelEntity(mesh, materials.white).also { root.addChild(it) }
            stripMesh = mesh
        }
        root.setLocal(position = from, rotation = Quat4.fromBasis(dir, axisZ.cross(dir).normalized(), axisZ), scale = Vec3(length, width, 1f))
        root.enabled = true
    }

    fun hide() {
        root.enabled = false
    }

    fun destroy() {
        runCatching { root.destroy(true) }
    }

    private companion object {
        const val CASING_WIDTHS = 2.2f
    }
}

/**
 * What ties a reading plate to its blade: a dark rider that straddles the blade where the plate stands,
 * and a thin steel stem from the rider to the middle of the nearest edge of the plate.
 */
internal class PlateMount(parent: Entity, private val meshes: TapeMeshes, private val materials: SceneMaterials) {
    private val rider: Entity = ModelEntity(meshes.rider, materials.bumper)
    private val stem: Entity = ModelEntity(meshes.stem, materials.steelShade)
    private var on = true
    private var opacity = 1f

    init {
        parent.addChild(rider)
        parent.addChild(stem)
        hide()
    }

    /**
     * @param at where the rider sits on the blade axis
     * @param bladeRotation frame of the blade (X along it, Y across, Z through)
     * @param bladeWidth how wide the blade is drawn, metres
     * @param plateEdge middle of the edge of the plate that is nearest to the rider
     * @param plateScale scale of the plate's panel
     */
    fun place(at: Vec3, bladeRotation: Quat4, bladeWidth: Float, plateEdge: Vec3, plateScale: Float) {
        rider.setLocal(position = at, rotation = bladeRotation, scale = Vec3(1f, (bladeWidth + RIDER_MARGIN_M) / TapeMeshes.RIDER_ACROSS_M, 1f))
        val span = plateEdge - at
        val length = span.length
        if (length < 1e-4f) {
            stem.enabled = false
        } else {
            stem.setLocal(position = at + span * 0.5f, rotation = Quat4.fromTo(Vec3.UP, span / length), scale = Vec3(plateScale, length, plateScale))
            stem.enabled = true
        }
        if (!on) {
            rider.enabled = true
            on = true
        }
    }

    fun setOpacity(value: Float) {
        if (value == opacity) return
        opacity = value
        rider.setOpacity(value)
        stem.setOpacity(value)
    }

    fun hide() {
        if (on) {
            rider.enabled = false
            stem.enabled = false
            on = false
        }
    }

    /** Re-applies materials after the lit / unlit choice changed. */
    fun refreshMaterials() {
        rider.setMaterial(materials.bumper)
        stem.setMaterial(materials.steelShade)
    }

    fun destroy() {
        runCatching { rider.destroy(true) }
        runCatching { stem.destroy(true) }
    }

    private companion object {
        /** The rider is 6 mm wider than the blade. */
        const val RIDER_MARGIN_M = 0.006f
    }
}

/** Easing of the tape pull-out: quick at first, settling at the tail. */
internal fun easeOutPull(q: Float): Float = 1f - (1f - q.coerceIn(0f, 1f)).pow(2.2f)

internal fun easeOutCubic(q: Float): Float = 1f - (1f - q.coerceIn(0f, 1f)).pow(3f)
