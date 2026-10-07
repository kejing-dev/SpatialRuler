package io.embodit.spatialruler.ui.ruler.scene

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.LayoutTapeUseCase
import io.embodit.spatialruler.domain.usecase.RestBodyUseCase
import io.embodit.spatialruler.platform.tracking.HeadPose
import io.embodit.spatialruler.platform.RulerLog
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.yield

/**
 * The instrument body: a 23.6 x 11.6 x 3.2 cm yellow tape-measure housing with dark bumpers at both
 * ends, a dark bezel that the glass panel sits in, and the tape mouth with its steel hook on the
 * right. It is a real object that carries the only standing panel of the app.
 *
 * It is head-locked at the lower left of the view (`AnchorTarget.createCameraTarget`, 18 degrees
 * left, 22 degrees down, 0.6 m away). Its long edges are level, it is not turned towards the eye and
 * it leans back 12 degrees, so that its right end and its top face show. The next step replaces the
 * anchor by a lazy follow computed per frame; the geometry hangs below [frame] so that only the
 * entities above it change.
 *
 * [anchor] is the anchor entity (the anchor component owns its transform). [mount] hangs below it and
 * carries the correction that keeps the body out of real surfaces ([rest]). [frame] is the body's own
 * frame: +X right, +Y up, +Z out of the face. Metres. The panels of the body are mounted next to
 * [frame] below [mount], see `PanelRig.onBody`.
 */
internal class BodyView(stageRoot: Entity) {
    val anchor = Entity()
    val mount = Entity()
    val frame = Entity()
    private val grip = Entity()
    private val restRule = RestBodyUseCase()
    private var materials: SceneMaterials? = null
    private var shell: ModelEntity? = null
    private var dark: List<ModelEntity> = emptyList()
    private var steel: List<Entity> = emptyList()

    /** Centre of the housing in Stage metres, as of the last [rest]. */
    var center: Vec3 = Vec3.ZERO
        private set

    /** Rotation of the housing in Stage space, as of the last [rest]. */
    var rotation: Quat4 = Quat4.IDENTITY
        private set

    /** The correction that is applied right now, Stage metres (zero when the body is where it was designed to be). */
    var applied: Vec3 = Vec3.ZERO
        private set
    private var from = Vec3.ZERO
    private var goal = Vec3.ZERO
    private var easeStartMs = 0L
    private var writtenLocal = Vec3.ZERO

    init {
        // lazy follow (T11): the anchor is placed by the app in Stage space, not by a camera anchor
        runCatching { anchor.setName("bodyAnchor") }
        stageRoot.addChild(anchor)
        runCatching { mount.setName("bodyMount") }
        anchor.addChild(mount)
        runCatching { frame.setName("bodyFrame") }
        mount.addChild(frame)
        frame.setLocal(rotation = HEAD_ROTATION)

        runCatching { grip.setName("body") }
        frameCollider()
        grip.components.set(InteractableComponent())
        frame.addChild(grip)
    }

    /** How the collider is built right now: "frame", or the clearance of the test box in millimetres. */
    var colliderName = "frame"
        private set

    /**
     * The collider of the housing: a pinch on the body next to the panel is swallowed (`TAP other
     * target=body`); it must not fall through to a surface behind the instrument and drop a pin there.
     *
     * It is a frame around the glass, as high as the bezel, and nothing behind the glass. Reason
     * (measured on the emulator, SDK 6.1.9): the system pointer does not take the nearest hit along
     * its ray. It ranks an attachment panel by the distance from the head to the panel's **centre**,
     * and a collider by the distance to the point that was hit. The body panel is seen from the side,
     * so its near half is closer to the head than its centre: a collider right behind the glass won
     * there (with one box 5 mm behind the glass, every press on Undo, Snap and More went to the
     * collider; the border of that region was the circle the rule predicts, at 5, 8 and 12 mm).
     *
     * The frame is a full-depth ring that starts 6 mm outside the glass, and a lip of only 4.5 mm
     * depth between the ring and the glass: a ray that comes through the glass near its edge runs
     * under the lip, and reaches the ring so far behind the glass that the panel still wins. With
     * nothing behind the glass the panel takes every press on it; a pinch that would still get through
     * is caught by the app (`RulerScene`, the guard in front of the body).
     */
    fun frameCollider() {
        val top = BEZEL_TOP_Z
        fun box(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float): ShapeResource =
            ShapeResource.createBox(Vector3(x1 - x0, y1 - y0, top - z0)).offsetByTranslation(Vector3((x0 + x1) / 2f, (y0 + y1) / 2f, (top + z0) / 2f))
        val wx = WINDOW_HALF_X
        val wy = WINDOW_HALF_Y
        val rx = wx + LIP_M
        val ry = wy + LIP_M
        val lip = top - LIP_DEPTH_M
        val back = -HALF_Z
        colliderName = "frame"
        grip.components.set(
            CollisionComponent(
                collisionShape =
                    listOf(
                        // ring: the housing and the bumpers, full depth
                        box(-HALF_X, HALF_X, ry, HALF_Y, back),
                        box(-HALF_X, HALF_X, -HALF_Y, -ry, back),
                        box(rx, HALF_X, -ry, ry, back),
                        box(-HALF_X, -rx, -ry, ry, back),
                        // lip: the bezel next to the glass
                        box(-rx, rx, wy, ry, lip),
                        box(-rx, rx, -ry, -wy, lip),
                        box(wx, rx, -wy, wy, lip),
                        box(-rx, -wx, -wy, wy, lip),
                    ),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
    }

    /** Debug only (command `ghost`): takes the collider out, so that pinches on the housing fall through. */
    fun setColliderEnabled(on: Boolean) {
        grip.enabled = on
    }

    /**
     * Debug only (command `collider`): one box over the whole housing with its front face [behindGlassM]
     * behind the glass, to see again which presses on the panel the collider takes.
     */
    fun boxCollider(behindGlassM: Float) {
        val front = PANEL_Z - behindGlassM
        val depth = front + HALF_Z
        if (depth <= 0.001f) return
        colliderName = String.format(java.util.Locale.US, "box %.1f mm behind the glass", behindGlassM * 1000f)
        grip.components.set(
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(2f * HALF_X, 2f * HALF_Y, depth)).offsetByTranslation(Vector3(0f, 0f, front - depth / 2f))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
    }

    /** Builds the housing (six meshes, a frame each), giving the main thread back in between. */
    suspend fun build(materials: SceneMaterials) {
        this.materials = materials
        val housing = Entity()
        runCatching { housing.setName("bodyHousing") }
        // the housing appears in one piece
        housing.enabled = false
        frame.addChild(housing)

        val shell = ModelEntity(MeshResource.createBox(Vector3(WIDTH, HEIGHT, DEPTH), CORNER), materials.shell)
        housing.addChild(shell)
        this.shell = shell
        yield()

        val darkParts = ArrayList<ModelEntity>()
        // bumpers: 3 mm proud of the housing on every side
        val bumperMesh = MeshResource.createBox(Vector3(BUMPER_W, BUMPER_H, BUMPER_D), 0.007f)
        for (side in SIDES) {
            darkParts += ModelEntity(bumperMesh, materials.bumper).also {
                it.setLocal(position = Vec3(side * BUMPER_X, 0f, 0f))
                housing.addChild(it)
            }
        }
        yield()

        // bezel around the glass: four bars, 3 mm high on the housing front; the glass sits 1.5 mm below their top
        val front = DEPTH / 2f
        val barZ = front + BEZEL_H / 2f
        val horizontal = MeshResource.createBox(Vector3(BEZEL_W, BEZEL_BAR, BEZEL_H), 0f)
        for (side in SIDES) {
            darkParts += ModelEntity(horizontal, materials.bumper).also {
                it.setLocal(position = Vec3(0f, side * (BEZEL_OUTER_H / 2f - BEZEL_BAR / 2f), barZ))
                housing.addChild(it)
            }
        }
        yield()
        val vertical = MeshResource.createBox(Vector3(BEZEL_BAR, BEZEL_OUTER_H - 2f * BEZEL_BAR, BEZEL_H), 0f)
        for (side in SIDES) {
            darkParts += ModelEntity(vertical, materials.bumper).also {
                it.setLocal(position = Vec3(side * (BEZEL_W / 2f - BEZEL_BAR / 2f), 0f, barZ))
                housing.addChild(it)
            }
        }
        yield()
        dark = darkParts

        // dark backing under the glass: the glass never lies over the yellow shell or raw passthrough
        val backing = ModelEntity(MeshResource.createBox(Vector3(BEZEL_W - 2f * BEZEL_BAR, BEZEL_OUTER_H - 2f * BEZEL_BAR, BACKING_H), 0f), materials.recess)
        backing.setLocal(position = Vec3(0f, 0f, front + BACKING_H / 2f))
        housing.addChild(backing)
        yield()

        // three grip ribs on the front of each bumper
        val rib = MeshResource.createBox(Vector3(0.0016f, 0.070f, 0.0012f), 0f)
        for (side in SIDES) for (i in -1..1) {
            ModelEntity(rib, materials.edge).also {
                it.setLocal(position = Vec3(side * BUMPER_X + i * 0.004f, 0f, BUMPER_D / 2f + 0.0006f))
                housing.addChild(it)
            }
        }
        yield()
        housing.enabled = true
    }

    /**
     * The tape mouth on the right end (T12): a 26 mm stub of blade (14 mm printed, 12 mm red zero end)
     * with the steel hook, its lip bent towards the viewer, through a frame of four bumper bars that
     * stand 2 mm proud of the right bumper around a 22 x 6 mm opening. The mouth sits 12 mm above the
     * body's lower edge.
     */
    fun addMouth(meshes: TapeMeshes) {
        val m = materials ?: return
        val y = MOUTH_Y
        // mouth frame: two bars above and below the blade, two in front of and behind it
        val proud = HALF_X + MOUTH_FRAME_BAR / 2f
        val acrossBar = MeshResource.createBox(Vector3(MOUTH_FRAME_BAR, MOUTH_FRAME_BAR, MOUTH_FRAME_D + 2f * MOUTH_FRAME_BAR), 0f)
        val throughBar = MeshResource.createBox(Vector3(MOUTH_FRAME_BAR, MOUTH_FRAME_H, MOUTH_FRAME_BAR), 0f)
        for (side in SIDES) {
            ModelEntity(acrossBar, m.bumper).also {
                it.setLocal(position = Vec3(proud, y + side * (MOUTH_FRAME_H / 2f + MOUTH_FRAME_BAR / 2f), 0f))
                frame.addChild(it)
            }
            ModelEntity(throughBar, m.bumper).also {
                it.setLocal(position = Vec3(proud, y, side * (MOUTH_FRAME_D / 2f + MOUTH_FRAME_BAR / 2f)))
                frame.addChild(it)
            }
        }
        val blade = ModelEntity(meshes.band, m.yellowTwoSided)
        blade.setLocal(position = Vec3(HALF_X, y, 0f), scale = Vec3(MOUTH_LENGTH, LayoutTapeUseCase.WIDTH_M, 1f))
        frame.addChild(blade)
        val zero = ModelEntity(meshes.band, m.red)
        zero.setLocal(
            position = Vec3(HALF_X + MOUTH_LENGTH - LayoutTapeUseCase.ZERO_END_M, y, 0.0002f),
            scale = Vec3(LayoutTapeUseCase.ZERO_END_M, LayoutTapeUseCase.WIDTH_M - 2f * LayoutTapeUseCase.EDGE_M, 1f),
        )
        frame.addChild(zero)
        // the hook is modelled for a blade that runs towards +X from its zero end with its lip bent to -Z; here the
        // blade runs the other way and the lip is bent towards the viewer (+Z): a half turn about the vertical
        val hook = Entity()
        hook.setLocal(position = Vec3(HALF_X + MOUTH_LENGTH, y, 0f), rotation = Quat4.axisAngle(Vec3.UP, Math.PI.toFloat()))
        frame.addChild(hook)
        steel = meshes.hook(hook, m)
    }

    // ---- lazy follow (T11): the body rests in the room and slides back when the head has turned away
    private var restHeadPos: Vec3? = null
    private var restYaw = 0f
    private var outSinceMs = NEVER
    private var slideStartMs = NEVER
    private var slideFromPos = Vec3.ZERO
    private var slideFromYaw = 0f
    private var slideFromApplied = Vec3.ZERO
    private var slideToPos = Vec3.ZERO
    private var slideToYaw = 0f
    private var slideToApplied = Vec3.ZERO
    private var anchorPos = Vec3.ZERO
    private var lastPlaneCount = -1

    /** The level turn of the body (about the vertical only); the housing adds its 12 degree lean. */
    var anchorRotation: Quat4 = Quat4.IDENTITY
        private set

    val sliding: Boolean
        get() = slideStartMs != NEVER

    /** Where the body would rest for [head]: 18 degrees left, 22 degrees down, 0.6 m, and the head's heading. */
    private fun designedFor(head: HeadPose): Pair<Vec3, Float> {
        val f = head.forward
        val yaw = if (hypot(f.x, f.z) < 0.05f) restYaw else atan2(-f.x, -f.z)
        return (head.position + head.rotation.rotate(HEAD_OFFSET)) to yaw
    }

    /** R2: the correction that keeps the body out of real surfaces at the designed place ([pos], [yaw]). */
    private fun correction(pos: Vec3, yaw: Float, headPos: Vec3, planes: Collection<PlaneSurface>): Vec3 {
        val turn = Quat4.axisAngle(Vec3.UP, yaw) * HEAD_ROTATION
        val axes = listOf(turn.rotate(Vec3.RIGHT), turn.rotate(Vec3.UP), turn.rotate(OUT))
        return restRule.rest(pos, axes, HALF_EXTENTS, headPos, planes) - pos
    }

    /**
     * Once per frame. The body stays where it settled. When its centre has been more than 45 degrees
     * off the view direction, or nearer than 0.35 m or farther than 0.9 m from the head, for 0.5 s
     * without a break, it slides to the designed pose of that moment in 0.35 s (ease-in-out, time
     * based), with the surface rule applied to the target first. It holds still while [hold] (a body key
     * is pressed or was pressed within 0.6 s, a menu or a dialog is open). The surface rule is applied
     * again whenever a plane was added or updated while the body rests.
     */
    fun follow(head: HeadPose, planes: Collection<PlaneSurface>, planeCount: Int, nowMs: Long, hold: Boolean) {
        if (restHeadPos == null) {
            val (pos, yaw) = designedFor(head)
            restHeadPos = head.position
            restYaw = yaw
            anchorPos = pos
            anchorRotation = Quat4.axisAngle(Vec3.UP, yaw)
            goal = correction(pos, yaw, head.position, planes)
            from = goal
            applied = goal
            easeStartMs = nowMs
            lastPlaneCount = planeCount
            write(force = true)
            RulerLog.i("BODY park reason=start pos=${RulerLog.pos(center)} yaw=${String.format(Locale.US, "%.1f", Math.toDegrees(yaw.toDouble()))}")
            return
        }
        if (sliding) {
            slide(nowMs)
            return
        }
        // the trigger: out of the window, or too near, or too far, for half a second
        val to = center - head.position
        val dist = to.length
        val away = if (dist < 1e-3f) 0f else degrees(acos((to / dist).dot(head.forward).coerceIn(-1f, 1f)))
        val reason =
            when {
                away > WINDOW_DEG -> "angle"
                dist < NEAR_M -> "near"
                dist > FAR_M -> "far"
                else -> null
            }
        if (reason == null || hold) {
            outSinceMs = NEVER
        } else if (outSinceMs == NEVER) {
            outSinceMs = nowMs
        } else if (nowMs - outSinceMs >= OUT_MS) {
            startSlide(head, planes, nowMs, reason, away, dist)
            return
        }
        // at rest: R2 again when the room changed, then the eased correction as before
        if (planeCount != lastPlaneCount) {
            lastPlaneCount = planeCount
            val wanted = correction(anchorPos, restYaw, head.position, planes)
            if ((wanted - goal).length > DEAD_BAND_M) {
                from = applied
                goal = wanted
                easeStartMs = nowMs
            }
        }
        applied = from.lerp(goal, easeOutCubic((nowMs - easeStartMs) / EASE_MS))
        write(force = false)
    }

    /** Test hook and the designed re-park: slides to the designed pose now. */
    fun park(head: HeadPose, planes: Collection<PlaneSurface>, nowMs: Long) {
        if (restHeadPos == null) return
        val to = center - head.position
        val dist = to.length
        val away = if (dist < 1e-3f) 0f else degrees(acos((to / dist).dot(head.forward).coerceIn(-1f, 1f)))
        startSlide(head, planes, nowMs, "debug", away, dist)
    }

    private fun startSlide(head: HeadPose, planes: Collection<PlaneSurface>, nowMs: Long, reason: String, away: Float, dist: Float) {
        val (pos, yaw) = designedFor(head)
        slideFromPos = anchorPos
        slideFromYaw = restYaw
        slideFromApplied = applied
        slideToPos = pos
        slideToYaw = yaw
        slideToApplied = correction(pos, yaw, head.position, planes)
        slideStartMs = nowMs
        outSinceMs = NEVER
        restHeadPos = head.position
        RulerLog.i(String.format(Locale.US, "BODY follow reason=%s angle=%.1f dist=%.2f to=%s", reason, away, dist, RulerLog.pos(pos + slideToApplied)))
        slide(nowMs)
    }

    private fun slide(nowMs: Long) {
        val q = ((nowMs - slideStartMs) / SLIDE_MS).coerceIn(0f, 1f)
        val t = easeInOutCubic(q)
        var dYaw = slideToYaw - slideFromYaw
        while (dYaw > PI.toFloat()) dYaw -= 2f * PI.toFloat()
        while (dYaw < -PI.toFloat()) dYaw += 2f * PI.toFloat()
        anchorPos = slideFromPos.lerp(slideToPos, t)
        restYaw = slideFromYaw + dYaw * t
        anchorRotation = Quat4.axisAngle(Vec3.UP, restYaw)
        applied = slideFromApplied.lerp(slideToApplied, t)
        write(force = true)
        if (q >= 1f) {
            slideStartMs = NEVER
            from = applied
            goal = applied
            easeStartMs = nowMs
            RulerLog.i("BODY park reason=slide pos=${RulerLog.pos(center)} yaw=${String.format(Locale.US, "%.1f", Math.toDegrees(restYaw.toDouble()))}")
        }
    }

    /** Writes the anchor and mount transforms; [center] and [rotation] follow. */
    private fun write(force: Boolean) {
        val local = anchorRotation.conjugate().rotate(applied)
        if (force) anchor.setLocal(position = anchorPos, rotation = anchorRotation)
        if (force || local.distanceTo(writtenLocal) > 0.0002f) {
            mount.setLocal(position = local)
            writtenLocal = local
        }
        center = anchorPos + applied
        rotation = anchorRotation * HEAD_ROTATION
    }

    /** Stage point of the place ([x], [y], [z]) in the body's own frame. */
    fun toStage(x: Float, y: Float, z: Float): Vec3 = center + rotation.rotate(Vec3(x, y, z))

    /** The eight corners of the body's bounding box (bumpers included), Stage metres. */
    fun corners(): List<Vec3> =
        buildList {
            for (sx in SIDES) for (sy in SIDES) for (sz in SIDES) add(toStage(sx * HALF_X, sy * HALF_Y, sz * HALF_Z))
        }

    /**
     * Where the line from [origin] to [target] enters the body's bounding box, as the fraction of the
     * way (0 at the origin, below 1 when the body lies in front of the target), or null when the line
     * misses the body.
     */
    fun crossing(origin: Vec3, target: Vec3): Float? {
        val inverse = rotation.conjugate()
        val o = inverse.rotate(origin - center)
        val d = inverse.rotate(target - origin)
        var tMin = 0f
        var tMax = 1f
        val os = floatArrayOf(o.x, o.y, o.z)
        val ds = floatArrayOf(d.x, d.y, d.z)
        val half = floatArrayOf(HALF_X, HALF_Y, HALF_Z)
        for (i in 0..2) {
            if (kotlin.math.abs(ds[i]) < 1e-9f) {
                if (kotlin.math.abs(os[i]) > half[i]) return null
            } else {
                var t0 = (-half[i] - os[i]) / ds[i]
                var t1 = (half[i] - os[i]) / ds[i]
                if (t0 > t1) {
                    val swap = t0
                    t0 = t1
                    t1 = swap
                }
                if (t0 > tMin) tMin = t0
                if (t1 < tMax) tMax = t1
                if (tMin > tMax) return null
            }
        }
        return tMin
    }

    /**
     * Where the line from [origin] to [target] meets the glass, in pixels of the body panel (origin
     * top left, [widthPx] x [heightPx]), or null when it does not come through the front.
     */
    fun panelPoint(origin: Vec3, target: Vec3, widthPx: Float, heightPx: Float): Pair<Float, Float>? {
        val inverse = rotation.conjugate()
        val o = inverse.rotate(origin - center)
        val d = inverse.rotate(target - origin)
        if (d.z > -1e-6f) return null
        val t = (PANEL_Z - o.z) / d.z
        if (t <= 0f || t >= 1f) return null
        val x = o.x + d.x * t
        val y = o.y + d.y * t
        val pixel = PanelRig.DP_M * PANEL_SCALE / 2f
        return (x / pixel + widthPx / 2f) to (heightPx / 2f - y / pixel)
    }

    /** Re-applies materials after the lit / unlit choice changed. */
    fun refreshMaterials() {
        val m = materials ?: return
        shell?.setMaterial(m.shell)
        dark.forEach { it.setMaterial(m.bumper) }
        steel.forEach { it.setMaterial(m.steel) }
    }

    companion object {
        const val WIDTH = 0.236f
        const val HEIGHT = 0.116f
        const val DEPTH = 0.032f
        private const val CORNER = 0.014f
        private const val BUMPER_W = 0.014f
        private const val BUMPER_H = 0.122f
        private const val BUMPER_D = 0.038f
        private const val BUMPER_X = 0.114f
        private const val BEZEL_W = 0.213f
        private const val BEZEL_OUTER_H = 0.0906f
        private const val BEZEL_BAR = 0.004f
        private const val BEZEL_H = 0.003f
        private const val BACKING_H = 0.0004f
        private const val MOUTH_LENGTH = 0.026f
        private const val MOUTH_FRAME_BAR = 0.002f
        private const val MOUTH_FRAME_H = 0.022f
        private const val MOUTH_FRAME_D = 0.006f
        private val SIDES = floatArrayOf(-1f, 1f)
        private val OUT = Vec3(0f, 0f, 1f)

        /** Half sizes of the bounding box, bumpers included. */
        const val HALF_X = BUMPER_X + BUMPER_W / 2f
        const val HALF_Y = BUMPER_H / 2f

        /** Centre line of the mouth: 12 mm above the body's lower edge (-49 mm). */
        private const val MOUTH_Y = -BUMPER_H / 2f + 0.012f
        const val HALF_Z = BUMPER_D / 2f
        val HALF_EXTENTS = listOf(HALF_X, HALF_Y, HALF_Z)

        /** The glass: 1.5 mm above the housing front, 1.5 mm below the top of the bezel. */
        const val PANEL_Z = DEPTH / 2f + 0.0015f

        /** Top of the bezel: 3 mm above the housing front. The collider frame is as high. */
        private const val BEZEL_TOP_Z = DEPTH / 2f + BEZEL_H

        /** The opening of the collider frame: the glass (204 x 81.6 mm) and 0.3 mm of air around it. */
        private const val WINDOW_HALF_X = 0.1023f
        private const val WINDOW_HALF_Y = 0.0411f
        private const val LIP_M = 0.006f
        private const val LIP_DEPTH_M = 0.0045f

        /** The body panel is 340 x 136 dp shown at 0.75: 20.4 x 8.2 cm. */
        const val PANEL_SCALE = 0.75f

        private const val DEAD_BAND_M = 0.01f
        private const val EASE_MS = 200f
        private const val NEVER = Long.MIN_VALUE

        /** Lazy follow: the window around the view direction, the distance band, the wait, the slide. */
        private const val WINDOW_DEG = 45f
        private const val NEAR_M = 0.35f
        private const val FAR_M = 0.9f
        private const val OUT_MS = 500L
        private const val SLIDE_MS = 350f

        private fun degrees(radians: Float): Float = Math.toDegrees(radians.toDouble()).toFloat()

        private fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

        /**
         * 18 degrees left and 22 degrees down, 0.6 m from the head, in the head frame
         * (x right, y up, -z ahead): 0.6 * (-cos22 sin18, -sin22, -cos22 cos18).
         */
        val HEAD_OFFSET = Vec3(-0.1719f, -0.2248f, -0.5291f)

        /** Long edges level, no turn towards the eye, the face leaning back 12 degrees. */
        val HEAD_ROTATION: Quat4 = Quat4.axisAngle(Vec3.RIGHT, -0.20944f)
    }
}
