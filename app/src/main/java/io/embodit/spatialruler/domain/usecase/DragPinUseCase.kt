package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.Grade
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.acos

/**
 * Gain of a pin drag by the system pointer (round 5 spec 2, reworked in rounds 5b / 5c / 5d after QA R5-2,
 * R5b-2 and R5c).
 *
 * 1 : 1 in apparent screen motion while the pointing line moves, so that the pin stays under the cursor.
 * 1 : 4 ("fine") only once the hand has been at rest: no drag event with a raw step of a pixel or more for
 * [noMotionMs]; any such step cancels the countdown (tail-safe: the emulator keeps sending 1 px events for
 * 1.5 - 2 s after a move, so fine starts only when that tail has ended). The last millimetres are then nudged
 * by tiny motions. Fine lets go on the FIRST event above [fastDegPerS], with no delay, and the gain of every
 * event is decided from that event's own speed (its step over its own window, see `RulerScene.movePinDrag`),
 * never from the state at the end of the previous rest: a quick move's first event goes 1 : 1.
 */
class AdaptiveGain(
    private val fastDegPerS: Float = FAST_DEG_PER_S,
    private val fine: Float = FINE,
    private val noMotionMs: Long = NO_MOTION_MS,
) {
    var gain = 1f
        private set

    /** When the hand last moved at all (a drag event with a raw step of a pixel or more). */
    private var motionMs = NEVER

    /** Why the gain last changed: `still` (fine engaged) or `fast` (fine let go). */
    var lastChange = ""
        private set

    /** The drag begins: the hand has just moved. */
    fun begin(nowMs: Long) {
        gain = 1f
        motionMs = nowMs
        lastChange = ""
    }

    /**
     * One drag event: the pointing line's angular speed over the event's own window, and whether the event
     * carried a raw step of a pixel or more. Returns the gain for this event's motion.
     */
    fun sample(speedDegPerS: Float, moved: Boolean, nowMs: Long): Float {
        if (moved) motionMs = nowMs
        if (speedDegPerS > fastDegPerS && gain < 1f) {
            gain = 1f
            lastChange = "fast"
        }
        return gain
    }

    /** Time passes (a frame): fine engages once the hand has been at rest for [noMotionMs]. */
    fun tick(nowMs: Long): Float {
        if (gain == 1f && motionMs != NEVER && nowMs - motionMs >= noMotionMs) {
            gain = fine
            lastChange = "still"
        }
        return gain
    }

    val fineOn: Boolean
        get() = gain < 1f

    fun reset() {
        gain = 1f
        motionMs = NEVER
        lastChange = ""
    }

    companion object {
        const val NEVER = Long.MIN_VALUE / 4
        const val NO_MOTION_MS = 400L
        const val FAST_DEG_PER_S = 4f
        const val FINE = 0.25f

        /** An event's speed is its step over at most this window: the first event after a rest is not averaged over the rest. */
        const val SPEED_WINDOW_MS = 100L

        /** A raw step below this (view pixels) is no motion. */
        const val MOTION_PX = 1f
    }
}

/**
 * The pure rules of a pin drag: how a hand step turns the pointing line, where the drag is cancelled, when
 * a target re-seats the pin, and the grade on release.
 */
class DragPinUseCase {

    /**
     * The pointing line after one step of the hand (round 5b, QA R5-1). The system reports a drag as a travel
     * in the view's plane: the cursor's displacement on a plane parallel to the view through the point where
     * the drag was recognised, times [equivalentDepth] of that point's depth (see [equivalentDepth]). So the
     * line's view-plane coordinates (at unit depth) move by the step over that depth, and a cursor move of x
     * pixels moves the line by x pixels at any distance: the pin stays under the cursor. [right], [up] and
     * [forward] are the view's axes (unit vectors); [direction] and the result are unit vectors.
     */
    fun turn(direction: Vec3, step: Vec3, equivalentDepth: Float, right: Vec3, up: Vec3, forward: Vec3): Vec3 {
        val depth = direction.dot(forward)
        if (equivalentDepth <= 1e-3f || depth <= 1e-3f) return direction
        val x = direction.dot(right) / depth + step.dot(right) / equivalentDepth
        val y = direction.dot(up) / depth + step.dot(up) / equivalentDepth
        val to = right * x + up * y + forward
        return if (to.length < 1e-6f) direction else to.normalized()
    }

    /**
     * Metres of system drag travel per unit of the cursor's view-plane displacement (at unit depth) for a drag
     * recognised at a point [distance] metres from the head while the head is pitched [pitchDeg] degrees off
     * level: ([HAND_OFFSET_M] + [HAND_PER_M] * distance) * (1 + [HAND_PER_PITCH_DEG] * |pitch|), fixed for
     * the whole drag (the system keeps the plane of the first hit). Fitted on the emulator (round 5c, 36 drags
     * of 150 and 250 px at 1.4 / 2.5 / 3.5 m, level and pitched -20 / -40 degrees, sideways and away, jointly
     * with [START_SHORT_U]: rms 5.9 px; see TESTING.md). The round-5b rule in the head's own frame missed by up
     * to 30 % once the head was pitched. Emulator-only calibration (its virtual hand moves with the gaze); a
     * real device may differ.
     */
    fun equivalentDepth(distance: Float, pitchDeg: Float): Float {
        if (distance <= 1e-3f) return 0f
        return (HAND_OFFSET_M + HAND_PER_M * distance) * (1f + HAND_PER_PITCH_DEG * abs(pitchDeg))
    }

    /**
     * The line of sight a system pin drag starts from: through the pin's cap, moved in the view's plane by the
     * part of the press offset that lies along the first hand step. The system reports its press point where it
     * recognised the drag (a degree or so along the way), on the pin's touch sphere: the along-drag part is the
     * travel the system has already used up, less [START_SHORT_U] (its travel counts from a little before the
     * point it reports: the emulator's smoothed hand lags the cursor); the rest is where the sphere was hit and
     * would offset the pin from the cursor for the whole drag. [right], [up], [forward]: view axes.
     */
    fun startLine(head: Vec3, cap: Vec3, press: Vec3, hand: Vec3, right: Vec3, up: Vec3, forward: Vec3): Vec3 {
        val c = cap - head
        val q = press - head
        val cz = c.dot(forward)
        val qz = q.dot(forward)
        if (cz <= 1e-3f) return q.normalized()
        if (qz <= 1e-3f) return c.normalized()
        val cu = c.dot(right) / cz
        val cv = c.dot(up) / cz
        var u = cu
        var v = cv
        val hx = hand.dot(right)
        val hy = hand.dot(up)
        val h = kotlin.math.sqrt(hx * hx + hy * hy)
        if (h > 1e-6f) {
            val du = q.dot(right) / qz - cu
            val dv = q.dot(up) / qz - cv
            val along = ((du * hx + dv * hy) / h - START_SHORT_U).coerceAtLeast(0f)
            u += along * hx / h
            v += along * hy / h
        }
        val to = right * u + up * v + forward
        return if (to.length < 1e-6f) c.normalized() else to.normalized()
    }

    /** A release within [CANCEL_M] of the pin's original place is no move (and no anchor churn). */
    fun cancelled(from: Vec3, to: Vec3): Boolean = from.distanceTo(to) <= CANCEL_M

    /**
     * The target lies in the pin's own surface plane: the move is an offset in the pin's frame. Otherwise
     * (another plane, or a plane with another normal) the pin re-seats there with a new anchor.
     */
    fun samePlane(pinPosition: Vec3, pinNormal: Vec3, target: Vec3, targetNormal: Vec3): Boolean =
        abs((target - pinPosition).dot(pinNormal)) <= PLANE_M && pinNormal.dot(targetNormal) >= SAME_NORMAL_COS

    /** Contact when the tip was within reach of the surface; fine when the slowed gain or a half press was on at release; else coarse. */
    fun gradeOnRelease(contact: Boolean, fine: Boolean): Grade =
        when {
            contact -> Grade.CONTACT
            fine -> Grade.FINE
            else -> Grade.COARSE
        }

    /** Angular speed of the pointing line between two samples, degrees per second (0 for a bad interval). */
    fun angularSpeedDegPerS(previous: Vec3, current: Vec3, dtMs: Long): Float {
        if (dtMs <= 0L) return 0f
        return angleDeg(previous, current) * 1000f / dtMs
    }

    /** The angle between two directions, degrees. */
    fun angleDeg(a: Vec3, b: Vec3): Float {
        val cos = (a.dot(b) / (a.length * b.length)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    companion object {
        const val CANCEL_M = 0.010f
        const val PLANE_M = 0.005f
        const val SAME_NORMAL_COS = 0.99f

        /** A tracked pointer's pin follows the target with this time constant (settles within 80 ms); the system drag has no lag. */
        const val LAG_MS = 30f

        /** The system's drag travel per unit of view-plane cursor displacement, see [equivalentDepth]. */
        const val HAND_OFFSET_M = 0.67f
        const val HAND_PER_M = 1.186f
        const val HAND_PER_PITCH_DEG = 0.0015f

        /** The system's travel counts from this much (view-plane units at unit depth, about 13 px / 0.6 degrees) before the press point it reports. */
        const val START_SHORT_U = 0.0101f
    }
}
