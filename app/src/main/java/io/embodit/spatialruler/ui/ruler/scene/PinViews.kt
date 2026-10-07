package io.embodit.spatialruler.ui.ruler.scene

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.SaveState
import io.embodit.spatialruler.domain.model.SeatMark
import io.embodit.spatialruler.domain.model.SnapKind
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.ResolvePlacementUseCase
import io.embodit.spatialruler.domain.usecase.ScaleForDistanceUseCase
import io.embodit.spatialruler.ui.ruler.PinUi
import io.embodit.spatialruler.ui.ruler.debug.RulerProbe
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.yield

/**
 * The pins: a survey nail. From the surface up: a dark base disc, a yellow washer with four index
 * marks, a thin steel shank, and a head made of a flange and a low dome with a centre punch. A soft
 * contact shadow ties it to the surface. Local +Y is the surface normal; the origin is the measured
 * point. The blade is attached under the flange, 14.5 mm above the surface.
 *
 * Meshes are created once and shared by all pins; a pin is a handful of entities. It is drawn life
 * size up to 2.5 m and grows beyond (twice at 5 m); what can be touched is a sphere around its head
 * on an entity of its own, which grows from 1.3 m so that it stays about 1.2 degrees wide.
 *
 * Motion is driven by time stamps, not by frame counts, and no state needs an animation to be read:
 * saved = solid washer, pinning = dashed turning ring, unsaved = white dashed ring and a dark head.
 */
internal class PinViews(
    private val root: Entity,
    private val materials: SceneMaterials,
    private val shared: SharedResources,
    private val scales: ScaleForDistanceUseCase,
    private val band: MeshResource,
) {

    /**
     * Shared by all pins. Each mesh costs a frame to create, so what every pin needs is made once, before
     * the first pin; what only shows now and then (rings of the pinning, unsaved and selected states, the
     * ripple and the pulse) is made when it is first needed.
     */
    private inner class Meshes(
        val shadow: MeshResource,
        val base: MeshResource,
        val washer: MeshResource,
        val marks: MeshResource,
        val shank: MeshResource,
        val flange: MeshResource,
        val dome: MeshResource,
        val punch: MeshResource,
    ) {
        val dashed: MeshResource by lazy { shared.keep(MeshFactory.ring(0.0124f, WASHER_R, "pin_dashed", dashes = 12, dashFill = 0.58f)) }
        val armedRing: MeshResource by lazy { shared.keep(MeshFactory.ring(0.0105f, 0.0125f, "pin_armed_ring")) }
        val pulse: MeshResource by lazy { shared.keep(MeshFactory.ring(0.0085f, 0.0098f, "pin_pulse")) }
        val ripple: MeshResource by lazy { shared.keep(MeshFactory.ring(0.0152f, 0.0164f, "pin_ripple")) }
        val headRim: MeshResource by lazy { shared.keep(MeshFactory.ring(FLANGE_R - 0.0018f, FLANGE_R + 0.0002f, "pin_head_rim")) }
    }

    private enum class Birth { NONE, DROP, RISE }

    private inner class View(val id: String) {
        val root = Entity()
        val touch = Entity()
        val washer: Entity
        val marks: Entity
        val lifted = Entity()
        val shank: ModelEntity
        val flange: ModelEntity
        val dome: ModelEntity
        val punch: Entity
        var pendingRing: Entity? = null
        var unsavedRing: Entity? = null
        var headRim: Entity? = null
        var armedRing: Entity? = null
        var ripple: Entity? = null
        var pulse: Entity? = null
        var seatMark: SeatFigure? = null

        var saveState: SaveState? = null
        var armed = false
        var stale = false
        var birth = Birth.NONE
        var bornAtMs = 0L
        var riseDelayMs = 0L
        var savedAtMs = NEVER
        var removedAtMs = NEVER
        var position = Vec3.ZERO
        var normal = Vec3.UP
        var scale = 1f
        var touchStep = -1
        var lit = materials.lit

        private var lastPos: Vec3? = null
        private var lastNormal: Vec3? = null
        private var lastScale = -1f
        private var lastLift = Float.NaN
        private var lastTouchAt: Vec3? = null

        init {
            runCatching { root.setName("pinView:$id") }
            // touch target: a sphere around the head on its own entity, so that it can grow on its own
            runCatching { touch.setName("pin:$id") }
            touch.components.set(InteractableComponent())

            // the contact shadow stays on the surface when the pin is lifted
            val shadow = ModelEntity(meshes.shadow, materials.pinShadow)
            shadow.setLocal(position = Vec3(0f, SHADOW_Y, 0f))
            root.addChild(shadow)
            val base = ModelEntity(meshes.base, materials.edge)
            base.setLocal(position = Vec3(0f, BASE_H / 2f, 0f))
            root.addChild(base)
            washer = ModelEntity(meshes.washer, materials.yellow)
            washer.setLocal(position = Vec3(0f, BASE_H + WASHER_H / 2f, 0f))
            root.addChild(washer)
            marks = ModelEntity(meshes.marks, materials.ink)
            marks.setLocal(position = Vec3(0f, WASHER_TOP + 0.0002f, 0f))
            root.addChild(marks)

            shank = ModelEntity(meshes.shank, materials.steelShade)
            shank.setLocal(position = Vec3(0f, WASHER_TOP + SHANK_H / 2f, 0f))
            flange = ModelEntity(meshes.flange, materials.steel)
            flange.setLocal(position = Vec3(0f, ResolvePlacementUseCase.PIN_HEAD_M + FLANGE_H / 2f, 0f))
            dome = ModelEntity(meshes.dome, materials.steel)
            // a sphere pressed flat: 14 mm across, 6 mm high, sitting on the flange
            dome.setLocal(position = Vec3(0f, FLANGE_TOP + DOME_H / 2f, 0f), scale = Vec3(1f, DOME_H / (2f * DOME_R), 1f))
            punch = ModelEntity(meshes.punch, materials.ink)
            punch.setLocal(position = Vec3(0f, FLANGE_TOP + DOME_H + 0.0002f, 0f))
            lifted.addChild(shank)
            lifted.addChild(flange)
            lifted.addChild(dome)
            lifted.addChild(punch)
            root.addChild(lifted)
        }

        fun place(force: Boolean) {
            if (force || lastPos != position || lastNormal != normal || lastScale != scale) {
                root.setLocal(position = position, rotation = Quat4.fromTo(Vec3.UP, normal), scale = Vec3(scale, scale, scale))
                lastPos = position
                lastNormal = normal
                lastScale = scale
            }
        }

        /** The touch sphere: at the head, with its own reach. The shape is replaced when the reach changes by a step. */
        fun placeTouch(reach: Float) {
            val at = position + normal * (ResolvePlacementUseCase.PIN_HEAD_M * scale)
            if (at != lastTouchAt) {
                touch.setLocal(position = at)
                lastTouchAt = at
            }
            val step = (reach * TOUCH_STEPS).roundToInt()
            if (step != touchStep) {
                touchStep = step
                touch.components.set(
                    CollisionComponent(
                        collisionShape = listOf(ShapeResource.createSphere(TOUCH_RADIUS_M * step / TOUCH_STEPS)),
                        physicsMaterial = PhysicsMaterialResource(),
                    )
                )
            }
        }

        fun lift(y: Float) {
            if (y != lastLift) {
                lifted.setLocal(position = Vec3(0f, y, 0f))
                lastLift = y
            }
        }

        fun applyLook(state: SaveState, frozen: Boolean) {
            if (state == saveState && lit == materials.lit && frozen == stale) return
            val previous = saveState
            saveState = state
            lit = materials.lit
            stale = frozen
            val saved = state == SaveState.SAVED
            washer.setVisible(saved)
            marks.setVisible(saved)
            washer.setMaterial(if (frozen) materials.stale else materials.yellow)
            if (state == SaveState.PENDING) {
                if (pendingRing == null) pendingRing = ring(meshes.dashed, materials.yellowTwoSided, BASE_H + 0.0003f)
            }
            pendingRing?.enabled = state == SaveState.PENDING
            if (state == SaveState.UNSAVED) {
                if (unsavedRing == null) unsavedRing = ring(meshes.dashed, materials.white, BASE_H + 0.0003f)
                if (headRim == null) {
                    headRim = ModelEntity(meshes.headRim, materials.white).also {
                        it.setLocal(position = Vec3(0f, FLANGE_TOP + 0.0003f, 0f))
                        lifted.addChild(it)
                    }
                }
            }
            unsavedRing?.enabled = state == SaveState.UNSAVED
            headRim?.enabled = state == SaveState.UNSAVED
            // an unsaved pin has a dark, hollow-looking head; a saved one is steel
            val head = if (state == SaveState.UNSAVED) materials.darkPlate else materials.steel
            flange.setMaterial(head)
            dome.setMaterial(head)
            shank.setMaterial(materials.steelShade)
            punch.setVisible(state != SaveState.UNSAVED)
            if (saved && previous == SaveState.PENDING) savedAtMs = clockMs
        }

        fun applyArmed(on: Boolean) {
            if (on == armed) return
            armed = on
            if (on && armedRing == null) {
                armedRing = ModelEntity(meshes.armedRing, materials.yellowTwoSided).also {
                    it.setLocal(position = Vec3(0f, ResolvePlacementUseCase.PIN_HEAD_M + FLANGE_H / 2f, 0f))
                    lifted.addChild(it)
                }
            }
            armedRing?.enabled = on
        }

        fun ring(mesh: MeshResource, material: Material, y: Float): Entity {
            val e = ModelEntity(mesh, material)
            e.setLocal(position = Vec3(0f, y, 0f))
            root.addChild(e)
            return e
        }

        fun dropTouch() {
            runCatching { touch.destroy(true) }
        }

        fun destroy() {
            seatMark?.destroy()
            seatMark = null
            runCatching { touch.destroy(true) }
            runCatching { root.destroy(true) }
        }
    }

    private lateinit var meshes: Meshes
    private val views = LinkedHashMap<String, View>()
    private val ghosts = ArrayList<View>()
    private var clockMs = 0L

    val ids: Set<String>
        get() = views.keys

    /** Builds the shared meshes, giving the main thread back between two of them. Call once, before [sync]. */
    suspend fun prepare() {
        val shadow = shared.keep(MeshFactory.disc(SHADOW_R, "pin_shadow"))
        yield()
        val base = shared.keep(MeshResource.createCylinder(BASE_H, BASE_R))
        yield()
        val washer = shared.keep(MeshResource.createCylinder(WASHER_H, WASHER_R))
        yield()
        val marks = shared.keep(MeshFactory.radialMarks(WASHER_R - 0.002f, 0.005f, 0.0012f, "pin_marks"))
        yield()
        val shank = shared.keep(MeshResource.createCylinder(SHANK_H, SHANK_R))
        yield()
        val flange = shared.keep(MeshResource.createCylinder(FLANGE_H, FLANGE_R))
        yield()
        val dome = shared.keep(MeshResource.createSphere(DOME_R))
        yield()
        val punch = shared.keep(MeshFactory.disc(0.0015f, "pin_punch", segments = 16))
        yield()
        meshes = Meshes(shadow, base, washer, marks, shank, flange, dome, punch)
    }

    /** Pin id for a tapped entity (the collider is on the pin's touch entity). */
    fun pinIdOf(entity: Entity): String? {
        val name = runCatching { entity.getName() }.getOrNull() ?: return null
        return if (name.startsWith("pin:")) name.removePrefix("pin:") else null
    }

    /** Where a tape is attached: under the flange of the pin, at the pin's current scale. */
    fun headPoint(id: String): Vec3? {
        val v = views[id] ?: return null
        return v.position + v.normal * (ResolvePlacementUseCase.PIN_HEAD_M * v.scale)
    }

    fun position(id: String): Vec3? = views[id]?.position

    fun normal(id: String): Vec3? = views[id]?.normal

    fun scale(id: String): Float = views[id]?.scale ?: 1f

    /** Makes the entities match the model. New pins drop in (or rise, when restoring); gone pins pop out. */
    fun sync(pins: List<PinUi>, nowMs: Long, restoring: Boolean, frozen: Boolean) {
        clockMs = nowMs
        val wanted = HashSet<String>(pins.size)
        pins.forEachIndexed { index, pin ->
            wanted += pin.id
            var view = views[pin.id]
            if (view == null) {
                view = View(pin.id)
                view.birth = if (restoring) Birth.RISE else Birth.DROP
                view.bornAtMs = nowMs
                view.riseDelayMs = if (restoring) index * RISE_STAGGER_MS else 0L
                root.addChild(view.root)
                root.addChild(view.touch)
                views[pin.id] = view
                // a pin that was snapped lands with the mark of its seat instead of the ripple
                val seat = pin.seat
                if (seat != null && !restoring) view.seatMark = SeatFigure(root, band, materials).also { it.set(seat.kind, pin.position, pin.normal, seat.a, seat.b) }
            }
            view.position = pin.position
            view.normal = pin.normal
            view.applyLook(pin.saveState, frozen)
            view.applyArmed(pin.armed)
        }
        val gone = views.keys.filter { it !in wanted }
        for (id in gone) {
            val view = views.remove(id) ?: continue
            view.removedAtMs = nowMs
            // the touch target goes at once: a pin that is popping out cannot be touched
            view.dropTouch()
            view.seatMark?.destroy()
            view.seatMark = null
            ghosts += view
        }
    }

    private var dragId: String? = null
    private var dragPos = Vec3.ZERO
    private var dragNormal = Vec3.UP

    /** The pin being dragged (round 5) is drawn at [position] on [normal], lifted [DRAG_LIFT_M], until [clearDrag]. */
    fun setDrag(id: String, position: Vec3, normal: Vec3) {
        dragId = id
        dragPos = position
        dragNormal = normal
    }

    fun clearDrag() {
        dragId = null
    }

    /** Re-applies materials after the lit / unlit choice changed. */
    fun refreshMaterials() {
        views.values.forEach { v -> v.saveState?.let { v.applyLook(it, v.stale) } }
    }

    /** Per-frame: distance scaling and the time-based motion. */
    fun onFrame(nowMs: Long, viewer: Vec3) {
        clockMs = nowMs
        for (view in views.values) {
            val dragged = view.id == dragId
            if (dragged) {
                view.position = dragPos
                view.normal = dragNormal
            }
            val distance = view.position.distanceTo(viewer)
            view.scale = scales.pin(distance)
            var lift = if (view.armed) ARMED_LIFT_M else 0f
            if (dragged) lift += DRAG_LIFT_M
            when (view.birth) {
                Birth.DROP -> {
                    val t = (nowMs - view.bornAtMs).toFloat()
                    if (t < DROP_MS) {
                        val q = t / DROP_MS
                        lift += DROP_HEIGHT_M * (1f - q * q)
                    } else if (t < DROP_MS + SETTLE_MS) {
                        val q = (t - DROP_MS) / SETTLE_MS
                        lift += REBOUND_M * sin(PI.toFloat() * q) * (1f - q)
                        if (view.seatMark == null) ripple(view, q)
                    } else {
                        view.ripple?.enabled = false
                        view.birth = Birth.NONE
                    }
                }
                Birth.RISE -> {
                    val t = (nowMs - view.bornAtMs - view.riseDelayMs).toFloat()
                    val q = (t / RISE_MS).coerceIn(0f, 1f)
                    lift += RISE_HEIGHT_M * (1f - q)
                    if (q >= 1f) view.birth = Birth.NONE
                    view.root.setOpacity(q)
                }
                Birth.NONE -> Unit
            }
            view.lift(lift)
            view.place(force = false)
            view.placeTouch(scales.pinTouch(distance))
            view.pendingRing?.takeIf { view.saveState == SaveState.PENDING }?.let {
                val angle = (nowMs % TURN_PERIOD_MS).toFloat() / TURN_PERIOD_MS * 2f * PI.toFloat()
                it.setLocal(rotation = Quat4.axisAngle(Vec3.UP, angle))
            }
            pulse(view, nowMs)
            view.seatMark?.let { mark ->
                val q = (nowMs - view.bornAtMs).toFloat() / (SEAT_MARK_MS * RulerProbe.markStretch)
                if (q >= 1f) {
                    mark.destroy()
                    view.seatMark = null
                } else {
                    mark.draw(view.scale, viewer, opacity = 1f - q * q)
                }
            }
        }
        if (ghosts.isNotEmpty()) {
            val it = ghosts.iterator()
            while (it.hasNext()) {
                val ghost = it.next()
                val q = (nowMs - ghost.removedAtMs).toFloat() / POP_MS
                if (q >= 1f) {
                    ghost.destroy()
                    it.remove()
                } else {
                    // pops out along the normal and fades
                    ghost.lift(POP_HEIGHT_M * q)
                    ghost.root.setOpacity(1f - q)
                    ghost.place(force = false)
                }
            }
        }
    }

    private fun ripple(view: View, q: Float) {
        val e = view.ripple ?: view.ring(meshes.ripple, materials.yellowTwoSided, BASE_H + 0.0004f).also { view.ripple = it }
        e.enabled = true
        val k = 1f + 2.1f * q // 16 mm -> 50 mm
        e.setLocal(scale = Vec3(k, 1f, k))
        e.setOpacity(1f - q)
    }

    private fun pulse(view: View, nowMs: Long) {
        if (view.savedAtMs == NEVER) return
        val q = (nowMs - view.savedAtMs).toFloat() / PULSE_MS
        if (q >= 1f) {
            view.pulse?.enabled = false
            view.savedAtMs = NEVER
            return
        }
        val e =
            view.pulse
                ?: ModelEntity(meshes.pulse, materials.green).also {
                    it.setLocal(position = Vec3(0f, ResolvePlacementUseCase.PIN_HEAD_M, 0f))
                    view.lifted.addChild(it)
                    view.pulse = it
                }
        e.enabled = true
        val k = 1f + 1.45f * q // 9 mm -> 22 mm
        e.setLocal(scale = Vec3(k, 1f, k))
        e.setOpacity(1f - q)
    }

    fun clear() {
        views.values.forEach { it.destroy() }
        views.clear()
        ghosts.forEach { it.destroy() }
        ghosts.clear()
    }

    companion object {
        private const val NEVER = Long.MIN_VALUE

        // heights above the surface: base 0 - 1, washer 1 - 3.5, shank 3.5 - 14.5, flange 14.5 - 16.5, dome to 22.5 mm
        private const val SHADOW_R = 0.023f
        private const val SHADOW_Y = 0.0004f
        private const val BASE_R = 0.0165f
        private const val BASE_H = 0.001f
        const val WASHER_R = 0.015f
        private const val WASHER_H = 0.0025f
        private const val WASHER_TOP = BASE_H + WASHER_H
        private const val SHANK_R = 0.0025f
        private const val SHANK_H = 0.011f
        const val FLANGE_R = 0.008f
        private const val FLANGE_H = 0.002f
        private const val FLANGE_TOP = ResolvePlacementUseCase.PIN_HEAD_M + FLANGE_H
        private const val DOME_R = 0.007f
        private const val DOME_H = 0.006f

        /** Total height of a pin, metres. */
        const val HEIGHT_M = FLANGE_TOP + DOME_H
        const val TOUCH_RADIUS_M = 0.028f

        /** The reach of the touch sphere changes in quarter steps. */
        private const val TOUCH_STEPS = 4f
        /** A pin that is being dragged floats this much above its surface (round 5). */
        const val DRAG_LIFT_M = 0.006f
        const val ARMED_LIFT_M = 0.006f
        private const val DROP_MS = 140f
        private const val SETTLE_MS = 200f
        private const val DROP_HEIGHT_M = 0.03f
        private const val REBOUND_M = 0.0016f
        private const val RISE_MS = 400f
        private const val RISE_HEIGHT_M = 0.02f
        private const val RISE_STAGGER_MS = 70L
        private const val PULSE_MS = 300f
        private const val POP_MS = 300f
        private const val POP_HEIGHT_M = 0.05f
        private const val TURN_PERIOD_MS = 2400L
        private const val SEAT_MARK_MS = 800f
    }
}

/**
 * The figure of a seat a pin snapped to, drawn on the surface: two bars along the plane edges that
 * leave a corner, one bar along an edge, or a length of guide line towards the tape head (plumb /
 * level). Corner and edge are blade yellow (`interaction`), plumb and level green (`passable`); each
 * bar lies on a dark casing one millimetre wider on every side. It is shown for a moment when a
 * snapped pin lands, and while a tape is pulled towards a seat.
 */
internal class SeatFigure(parent: Entity, band: MeshResource, private val materials: SceneMaterials) {
    private class Bar(parent: Entity, band: MeshResource, casing: Material, colour: Material) {
        val under = Strip(parent, band, casing, "seat_casing")
        val over = Strip(parent, band, colour, "seat_bar")
        var colour: Material = colour

        fun hide() {
            under.hide()
            over.hide()
        }

        fun destroy() {
            under.destroy()
            over.destroy()
        }
    }

    private val bars = listOf(Bar(parent, band, materials.edge, materials.yellowTwoSided), Bar(parent, band, materials.edge, materials.yellowTwoSided))
    private var kind = SnapKind.NONE
    private var point = Vec3.ZERO
    private var normal = Vec3.UP
    private var a: Vec3? = null
    private var b: Vec3? = null

    /** What to draw: [a] and [b] are the directions of `SnapResult` (see there). */
    fun set(kind: SnapKind, point: Vec3, normal: Vec3, a: Vec3?, b: Vec3?) {
        val guide = kind == SnapKind.PLUMB || kind == SnapKind.LEVEL
        val colour = if (guide) materials.green else materials.yellowTwoSided
        if (colour !== bars[0].colour) {
            for (bar in bars) {
                bar.over.setMaterial(colour)
                bar.colour = colour
            }
        }
        this.kind = kind
        this.point = point
        this.normal = normal
        this.a = a
        this.b = b
    }

    /** Draws the figure at [scale] (the pin's own size factor); the guide line turns its face to [viewer]. */
    fun draw(scale: Float, viewer: Vec3, opacity: Float = 1f) {
        val first = a
        val second = b
        val lifted = point + normal * LIFT_M
        when {
            kind == SnapKind.CORNER && first != null && second != null -> {
                bar(0, lifted, lifted + first * (CORNER_BAR_M * scale), normal, scale, opacity)
                bar(1, lifted, lifted + second * (CORNER_BAR_M * scale), normal, scale, opacity)
            }
            kind == SnapKind.EDGE && first != null -> {
                val half = first * (EDGE_BAR_M / 2f * scale)
                bar(0, lifted - half, lifted + half, normal, scale, opacity)
                bars[1].hide()
            }
            (kind == SnapKind.PLUMB || kind == SnapKind.LEVEL) && first != null -> {
                bar(0, point, point + first * (GUIDE_M * scale), viewer - point, scale, opacity)
                bars[1].hide()
            }
            else -> hide()
        }
    }

    private fun bar(index: Int, from: Vec3, to: Vec3, face: Vec3, scale: Float, opacity: Float) {
        val bar = bars[index]
        val along = (to - from).normalized()
        var out = face.rejectFrom(along)
        if (out.length < 1e-4f) out = Vec3.UP.rejectFrom(along)
        out = if (out.length < 1e-4f) Vec3.RIGHT else out.normalized()
        // the casing is a millimetre longer at both ends and lies just below the bar
        bar.under.draw(from - along * (CASING_M * scale) - out * 0.0002f, to + along * (CASING_M * scale) - out * 0.0002f, (BAR_WIDTH_M + 2f * CASING_M) * scale, out)
        bar.over.draw(from, to, BAR_WIDTH_M * scale, out)
        bar.under.setOpacity(opacity)
        bar.over.setOpacity(opacity)
    }

    fun hide() {
        bars.forEach { it.hide() }
    }

    fun destroy() {
        bars.forEach { it.destroy() }
    }

    private companion object {
        const val CORNER_BAR_M = 0.040f
        const val EDGE_BAR_M = 0.080f
        const val GUIDE_M = 0.060f
        const val BAR_WIDTH_M = 0.0025f
        const val CASING_M = 0.001f
        const val LIFT_M = 0.0008f
    }
}
