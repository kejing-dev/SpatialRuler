package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.PlaneSurface
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.max

/*
 * The pure parts of aiming with a tracked pointer (controller ray, hand ray, tool tip): which source
 * is used, when an aim counts as steady, what the half-pressed trigger does to the ray, where a press
 * commits, and what grade the pin gets. The scene samples the sources once per frame and feeds these.
 */

/** The pointer the app aims with. [tag] is what the log says (`SRC active=`, `PIN add ... via=`). */
enum class PointerChannel(val tag: String) {
    CONTROLLER("controller"),
    HAND("hand"),
    SYNTHETIC("synthetic"),
    SYSTEM("system");

    companion object {
        fun fromTag(tag: String?): PointerChannel? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Source selection of the design: the controller when its provider is supported and delivers poses,
 * else the hand when one is present, else the system pointer (gaze + pinch, emulator mouse) as before.
 * A test ray (debug builds) goes first while it is set. [forced] pins one source; a forced tracked
 * source that delivers nothing leaves the system pointer in charge.
 */
class SelectPointerSourceUseCase {
    fun select(forced: PointerChannel?, controller: Boolean, hand: Boolean, synthetic: Boolean): PointerChannel =
        when (forced) {
            PointerChannel.SYSTEM -> PointerChannel.SYSTEM
            PointerChannel.CONTROLLER -> if (controller) PointerChannel.CONTROLLER else PointerChannel.SYSTEM
            PointerChannel.HAND -> if (hand) PointerChannel.HAND else PointerChannel.SYSTEM
            PointerChannel.SYNTHETIC -> if (synthetic) PointerChannel.SYNTHETIC else PointerChannel.SYSTEM
            null ->
                when {
                    synthetic -> PointerChannel.SYNTHETIC
                    controller -> PointerChannel.CONTROLLER
                    hand -> PointerChannel.HAND
                    else -> PointerChannel.SYSTEM
                }
        }
}

/**
 * Stability of an aim. The pointer is steady when its hit point has stayed inside a small circle
 * (0.15 degrees of the distance, at least 2 mm) for [holdMs] (0.35 s in the design). [progress] runs
 * from 0 to 1 over that time: it closes the stability ring of the reticle.
 */
class SteadyAim(private val holdMs: Long = HOLD_MS, private val tan: Float = STEADY_TAN, private val floorM: Float = 0.002f) {
    private var anchor: Vec3? = null
    private var sinceMs = 0L

    var progress = 0f
        private set

    val steady: Boolean
        get() = progress >= 1f

    /** One aim sample; null [point]: the pointer hits nothing. Returns [progress]. */
    fun update(point: Vec3?, distance: Float, nowMs: Long): Float {
        if (point == null) {
            reset()
            return 0f
        }
        val a = anchor
        if (a == null || a.distanceTo(point) > max(floorM, distance * tan)) {
            anchor = point
            sinceMs = nowMs
        }
        progress = if (holdMs <= 0L) 1f else ((nowMs - sinceMs).toFloat() / holdMs).coerceIn(0f, 1f)
        return progress
    }

    fun reset() {
        anchor = null
        progress = 0f
    }

    companion object {
        const val HOLD_MS = 350L
        const val STEADY_TAN = 0.0026f
    }
}

/**
 * Where a press commits: the median of the aim points 40 - 120 ms before the press edge. Pulling the
 * trigger (or closing the pinch) jerks the pointer; the aim just before it is what the user meant.
 * When no sample lies in that window (a slow frame rate), the newest sample older than 40 ms is
 * taken, and failing that the newest one.
 */
class CommitWindow(private val oldestMs: Long = OLDEST_MS, private val newestMs: Long = NEWEST_MS, capacity: Int = 64) {
    private val times = LongArray(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val zs = FloatArray(capacity)
    private var next = 0
    private var count = 0

    fun add(point: Vec3, nowMs: Long) {
        times[next] = nowMs
        xs[next] = point.x
        ys[next] = point.y
        zs[next] = point.z
        next = (next + 1) % times.size
        if (count < times.size) count++
    }

    fun clear() {
        next = 0
        count = 0
    }

    fun commit(pressMs: Long): Vec3? {
        if (count == 0) return null
        val px = ArrayList<Float>()
        val py = ArrayList<Float>()
        val pz = ArrayList<Float>()
        var fallback = -1
        var newest = -1
        for (k in 0 until count) {
            val i = (next - 1 - k + times.size * 2) % times.size
            val age = pressMs - times[i]
            if (age < 0L) continue
            if (newest < 0) newest = i
            if (age < newestMs) continue
            if (fallback < 0) fallback = i
            if (age <= oldestMs) {
                px += xs[i]
                py += ys[i]
                pz += zs[i]
            }
        }
        if (px.isNotEmpty()) return Vec3(median(px), median(py), median(pz))
        val i = if (fallback >= 0) fallback else newest
        return if (i < 0) null else Vec3(xs[i], ys[i], zs[i])
    }

    private fun median(values: MutableList<Float>): Float {
        values.sort()
        val n = values.size
        return if (n % 2 == 1) values[n / 2] else (values[n / 2 - 1] + values[n / 2]) / 2f
    }

    companion object {
        const val OLDEST_MS = 120L
        const val NEWEST_MS = 40L
    }
}

/**
 * Half-press fine aim. While it is on, the ray turns and moves a quarter as far as the controller
 * does (4 : 1), starting from where it pointed when the trigger was half pressed. When it goes off the
 * ray is the controller's own again.
 */
class FineAim(private val gain: Float = GAIN) {
    private var rawAt: PointerRay? = null
    private var shownAt: PointerRay? = null

    val active: Boolean
        get() = rawAt != null

    fun apply(raw: PointerRay, fine: Boolean): PointerRay {
        if (!fine) {
            rawAt = null
            shownAt = null
            return raw
        }
        val r0 = rawAt
        val s0 = shownAt
        if (r0 == null || s0 == null) {
            rawAt = raw
            shownAt = raw
            return raw
        }
        val direction = (s0.direction + (raw.direction - r0.direction) * gain).normalized()
        val origin = s0.origin + (raw.origin - r0.origin) * gain
        return PointerRay(origin, direction)
    }

    companion object {
        const val GAIN = 0.25f
    }
}

enum class TriggerStage { RELEASED, HALF, FULL }

/**
 * The analog trigger of a controller in three stages, with hysteresis: half pressed (fine aim) from
 * 15 %, fully pressed (commit) from 85 %; it falls back to half below 60 % and to released below 8 %.
 * A controller that only reports "pressed" counts as fully pressed.
 */
class TriggerStages(
    private val halfOn: Float = 0.15f,
    private val halfOff: Float = 0.08f,
    private val fullOn: Float = 0.85f,
    private val fullOff: Float = 0.60f,
) {
    var stage = TriggerStage.RELEASED
        private set

    /** One sample. True in the sample in which the trigger becomes fully pressed: the commit edge. */
    fun update(value: Float, pressed: Boolean = false): Boolean {
        val v = if (pressed) 1f else value
        val before = stage
        stage =
            when (before) {
                TriggerStage.RELEASED -> if (v >= fullOn) TriggerStage.FULL else if (v >= halfOn) TriggerStage.HALF else TriggerStage.RELEASED
                TriggerStage.HALF -> if (v >= fullOn) TriggerStage.FULL else if (v <= halfOff) TriggerStage.RELEASED else TriggerStage.HALF
                TriggerStage.FULL -> if (v <= halfOff) TriggerStage.RELEASED else if (v <= fullOff) TriggerStage.HALF else TriggerStage.FULL
            }
        return stage == TriggerStage.FULL && before != TriggerStage.FULL
    }

    fun reset() {
        stage = TriggerStage.RELEASED
    }
}

/**
 * The pinch of a hand as a button, with hysteresis: pressed from 80 %, released below 55 %. It only
 * counts while the hand reports that the gesture is ready; a hand that is not ready is not pressed.
 */
class PinchLatch(private val on: Float = 0.80f, private val off: Float = 0.55f) {
    var pressed = false
        private set

    /** One sample. True in the sample in which the pinch closes: the commit edge. */
    fun update(value: Float, ready: Boolean): Boolean {
        val before = pressed
        pressed = ready && (if (before) value > off else value >= on)
        return pressed && !before
    }

    fun reset() {
        pressed = false
    }
}

/** The grade a pin gets when it lands: contact; fine when the aim was steady or the trigger half pressed; else coarse. */
class GradeAimUseCase {
    fun grade(contact: Boolean, steady: Boolean, halfPress: Boolean): Grade =
        when {
            contact -> Grade.CONTACT
            steady || halfPress -> Grade.FINE
            else -> Grade.COARSE
        }
}

/** A tool tip within reach of a surface: [normal] faces the viewer's side, [distance] is how far the tip is from the plane. */
data class ContactHit(val plane: PlaneSurface, val normal: Vec3, val distance: Float)

/**
 * Contact channel: the controller tip or the pinch point is within 12 cm of a recognised surface (over
 * its quad, grown by 5 cm). The nearest such surface is the one the pin will belong to; the pin's
 * position is the tip itself.
 */
class FindContactUseCase(private val hits: HitSurfaceUseCase = HitSurfaceUseCase()) {
    fun find(tip: Vec3, viewOrigin: Vec3, planes: Collection<PlaneSurface>, reachM: Float = REACH_M): ContactHit? {
        var best: PlaneSurface? = null
        var bestDistance = reachM
        for (plane in planes) {
            if (plane.vertices.isEmpty()) continue
            val d = abs((tip - plane.vertices[0]).dot(plane.normal))
            if (d <= bestDistance && hits.contains(plane, tip, margin = EDGE_MARGIN_M)) {
                bestDistance = d
                best = plane
            }
        }
        val plane = best ?: return null
        return ContactHit(plane, ResolvePlacementUseCase.facing(plane.normal, viewOrigin - tip), bestDistance)
    }

    companion object {
        const val REACH_M = 0.12f
        private const val EDGE_MARGIN_M = 0.05f
    }
}
