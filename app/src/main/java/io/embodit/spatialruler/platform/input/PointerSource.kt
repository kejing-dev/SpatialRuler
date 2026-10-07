package io.embodit.spatialruler.platform.input

import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerActionData
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hand.HandTrackingProvider
import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.Quat4
import io.embodit.spatialruler.domain.model.Vec3
import io.embodit.spatialruler.domain.usecase.PointerChannel
import io.embodit.spatialruler.platform.RulerLog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** What a tracked pointer delivers in one frame, Stage space. */
data class PointerInput(
    val ray: PointerRay,
    /** Tool tip of the contact channel: the controller's (virtual) tip or the pinch point; null when unknown. */
    val tip: Vec3?,
    /** Trigger value or pinch value, 0..1. */
    val press: Float,
    /** The device's own "pressed" flag (controller trigger). */
    val pressed: Boolean,
    /** Hand: aim and pinch are ready. Always true for a controller. */
    val ready: Boolean,
    /** The right hand holds it (haptics go there). */
    val right: Boolean,
)

/**
 * A tracked pointing ray that the app reads every frame from the frame system (controller ray, hand
 * aim, test ray). The system pointer (gaze + pinch, emulator mouse) is not a PointerSource: the app
 * only gets its tap. Which source is used is decided by `SelectPointerSourceUseCase`; steadiness, fine
 * aim, the commit position and the grade are worked out from these samples by the scene with the pure
 * helpers in `domain/usecase/TrackPointer.kt`.
 */
interface PointerSource {
    val channel: PointerChannel

    fun start() {}

    fun stop() {}

    /** Called once per frame on the main thread. Null when the pointer is not there or delivers nothing. */
    fun sample(nowMs: Long): PointerInput?
}

/**
 * Controller ray: pose from `ControllerTrackingProvider.latestData` (read on the main thread), the
 * analog trigger from `ControllerActionListener`. That listener runs on the data source's thread: it
 * only copies the values into atomics, which the frame system reads.
 *
 * The ray leaves the controller pose along its -Z. The tool tip of the contact channel is a virtual
 * one, [TIP_M] ahead of the pose along the ray: the contact reticle is drawn exactly there, so what the
 * user sees is what a press commits, whatever the real offset of the housing is.
 *
 * With two controllers the one whose trigger was touched last aims (the right one at first).
 */
class ControllerPointerSource : PointerSource {
    override val channel = PointerChannel.CONTROLLER

    private var provider: ControllerTrackingProvider? = null
    private val rightValue = AtomicInteger(0)
    private val leftValue = AtomicInteger(0)
    private val rightPressed = AtomicBoolean(false)
    private val leftPressed = AtomicBoolean(false)
    private var useRight = true
    private var lastSupport: DataProvider.SupportState? = null

    private val listener =
        ControllerTrackingProvider.ControllerActionListener { action: ControllerActionData ->
            rightValue.set(java.lang.Float.floatToIntBits(action.right.triggerValue))
            rightPressed.set(action.right.triggerPressed)
            leftValue.set(java.lang.Float.floatToIntBits(action.left.triggerValue))
            leftPressed.set(action.left.triggerPressed)
        }

    override fun start() {
        if (provider != null) return
        val p = ControllerTrackingProvider()
        runCatching { p.addControllerActionListener(listener) }
        val result = runCatching { p.start() }
        lastSupport = runCatching { p.supportState }.getOrNull()
        RulerLog.i("TRACK controller start=${result.getOrNull() ?: result.exceptionOrNull()} support=$lastSupport")
        provider = p
    }

    override fun stop() {
        provider?.let { p ->
            runCatching { p.removeControllerActionListener(listener) }
            runCatching { p.stop() }
        }
        provider = null
    }

    override fun sample(nowMs: Long): PointerInput? {
        val p = provider ?: return null
        val support = runCatching { p.supportState }.getOrNull()
        if (support != lastSupport) {
            lastSupport = support
            RulerLog.i("TRACK controller support=$support")
        }
        if (support != DataProvider.SupportState.SUPPORTED) return null
        val data = runCatching { p.latestData }.getOrNull() ?: return null
        val rightV = java.lang.Float.intBitsToFloat(rightValue.get())
        val leftV = java.lang.Float.intBitsToFloat(leftValue.get())
        val rightDown = rightPressed.get()
        val leftDown = leftPressed.get()
        // the controller whose trigger is being touched aims
        if (useRight && (leftV > SWITCH_AT || leftDown) && rightV < REST_BELOW && !rightDown) useRight = false
        if (!useRight && (rightV > SWITCH_AT || rightDown) && leftV < REST_BELOW && !leftDown) useRight = true
        val right = data.right
        val left = data.left
        val pickRight = if (useRight) right != null else left == null
        val pose = (if (pickRight) right else left) ?: return null
        val position = Vec3(pose.position.x, pose.position.y, pose.position.z)
        val rotation = Quat4(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w)
        if (!position.isFinite()) return null
        val forward = rotation.rotate(Vec3.AHEAD)
        if (!forward.isFinite() || forward.length < 0.5f) return null
        return PointerInput(
            ray = PointerRay(position, forward.normalized()),
            tip = position + forward.normalized() * TIP_M,
            press = if (pickRight) rightV else leftV,
            pressed = if (pickRight) rightDown else leftDown,
            ready = true,
            right = pickRight,
        )
    }

    companion object {
        /** The virtual tip: 6 cm ahead of the controller pose. */
        const val TIP_M = 0.06f
        private const val SWITCH_AT = 0.15f
        private const val REST_BELOW = 0.05f
    }
}

/**
 * Hand ray: the aim pose of `HandTrackingProvider` is the ray, the pinch value the button (hysteresis
 * and readiness are applied by `PinchLatch`), the pinch point the tool tip of the contact channel. A
 * hand is "present" while its aim pose is delivered and is not the placeholder at the origin that the
 * provider hands out before any hand was seen. The right hand aims when both are there.
 */
class HandPointerSource : PointerSource {
    override val channel = PointerChannel.HAND

    private companion object {
        /** An aim pose this close to the Stage origin, and not ready, is the provider's placeholder (seen on the emulator). */
        const val PLACEHOLDER_M = 0.001f
    }

    private var provider: HandTrackingProvider? = null
    private var lastSupport: DataProvider.SupportState? = null

    /**
     * The provider is only started where hands can exist. On the emulator it is not: after a launch in
     * which it went from `NOT_IN_FULL_SPACE` to `SUPPORTED`, the guest's hand tracking stayed on
     * (`SPR-HandTrackingDevice ... isHandTracking:true`) and the emulator's gaze + pinch simulation
     * stopped delivering taps to any app until the emulator was restarted (2026-10-02; not proven to be
     * the cause, but the only change at that moment). Hands do not exist on the emulator anyway.
     */
    var wanted: Boolean = !SystemPointer.onEmulator

    override fun start() {
        if (provider != null || !wanted) return
        val p = HandTrackingProvider()
        val result = runCatching { p.start() }
        lastSupport = runCatching { p.supportState }.getOrNull()
        RulerLog.i("TRACK hand start=${result.getOrNull() ?: result.exceptionOrNull()} support=$lastSupport")
        provider = p
    }

    override fun stop() {
        runCatching { provider?.stop() }
        provider = null
    }

    override fun sample(nowMs: Long): PointerInput? {
        val p = provider ?: return null
        val support = runCatching { p.supportState }.getOrNull()
        if (support != lastSupport) {
            lastSupport = support
            RulerLog.i("TRACK hand support=$support")
        }
        if (support != DataProvider.SupportState.SUPPORTED) return null
        val data = runCatching { p.latestData }.getOrNull() ?: return null
        val isRight = data.rightAim != null
        val aim = data.rightAim ?: data.leftAim ?: return null
        val pinch = if (isRight) data.rightPinch else data.leftPinch
        val position = Vec3(aim.aimPose.position.x, aim.aimPose.position.y, aim.aimPose.position.z)
        val rotation = Quat4(aim.aimPose.rotation.x, aim.aimPose.rotation.y, aim.aimPose.rotation.z, aim.aimPose.rotation.w)
        if (!position.isFinite()) return null
        // a provider that has just become supported hands out an aim at the origin that is not ready: no hand
        if (!aim.isReady && position.length < PLACEHOLDER_M) return null
        val forward = rotation.rotate(Vec3.AHEAD)
        if (!forward.isFinite() || forward.length < 0.5f) return null
        val tip = pinch?.pinchPose?.position?.let { Vec3(it.x, it.y, it.z) }?.takeIf { it.isFinite() }
        return PointerInput(
            ray = PointerRay(position, forward.normalized()),
            tip = tip,
            press = pinch?.pinchValue ?: 0f,
            pressed = false,
            ready = aim.isReady && (pinch?.isReady ?: false),
            right = isRight,
        )
    }
}

/**
 * A pointer set from the outside (debug builds): a ray, a tool tip and a trigger value. It runs through
 * the same code as a real one. With `source controller` or `source hand` it stands in for that device
 * while the device itself delivers nothing, so that both paths can be exercised without hardware.
 */
class SyntheticPointerSource : PointerSource {
    override val channel = PointerChannel.SYNTHETIC

    private var ray: PointerRay? = null
    private var tip: Vec3? = null
    private var press = 0f
    private var pulse = false

    fun set(ray: PointerRay?) {
        this.ray = ray
        if (ray == null) {
            press = 0f
            pulse = false
        }
    }

    /** The tool tip of the contact channel; null takes it away. */
    fun tip(point: Vec3?) {
        tip = point
    }

    /** Holds the trigger (or pinch) at [value] until it is set again. */
    fun hold(value: Float) {
        press = value.coerceIn(0f, 1f)
        pulse = false
    }

    /** One full press that lets go by itself with the sample after it. */
    fun trigger() {
        if (active) {
            press = 1f
            pulse = true
        }
    }

    /** The pointer disappears (a hand that leaves the view). */
    fun lose() {
        ray = null
        tip = null
        press = 0f
        pulse = false
    }

    val active: Boolean
        get() = ray != null || tip != null

    override fun sample(nowMs: Long): PointerInput? {
        val t = tip
        val r = ray ?: t?.let { PointerRay(it, Vec3.AHEAD) } ?: return null
        val value = press
        if (pulse) {
            // the press of a pulse is seen by exactly one sample
            press = 0f
            pulse = false
        }
        return PointerInput(ray = r, tip = t, press = value, pressed = false, ready = true, right = true)
    }
}
