package io.embodit.spatialruler.domain.usecase

import io.embodit.spatialruler.domain.model.PointerRay
import io.embodit.spatialruler.domain.model.Vec3
import kotlin.math.roundToInt

/**
 * Press-and-pull without a tracked ray (gaze and pinch, emulator mouse).
 *
 * The system drag gesture only reports how far the hand has moved since the press. The design turns
 * that into an aim: at the press the line "head -> pressed point" is the pointing line; afterwards
 * every centimetre of hand movement turns the line about the head by about 1.2 degrees, as if the hand
 * held the end of a pointer rod half a metre long that pivots at the head. Where the line meets a
 * recognised surface is the reticle and the tail of the tape.
 *
 * Truing a pin by dragging is simpler: the hand movement, laid onto the pin's surface, moves the pin
 * a quarter as far.
 */
class PullLineUseCase {

    /** The pointing line after the hand has moved by [handDelta] (Stage metres) since the press. */
    fun ray(head: Vec3, pressedPoint: Vec3, handDelta: Vec3): PointerRay? {
        val to = pressedPoint - head
        if (to.length < 1e-4f) return null
        val direction = to.normalized() * ROD_M + handDelta
        if (direction.length < 1e-5f) return null
        return PointerRay(head, direction.normalized())
    }

    /**
     * Fine-tune offsets, whole millimetres along the pin's two surface axes, after the hand has moved
     * by [handDelta] since the drag began at the offsets ([startUMm], [startVMm]).
     */
    fun trueOffsets(handDelta: Vec3, uAxis: Vec3, vAxis: Vec3, startUMm: Int, startVMm: Int): Pair<Int, Int> {
        val du = handDelta.dot(uAxis) * TRUE_GEAR * 1000f
        val dv = handDelta.dot(vAxis) * TRUE_GEAR * 1000f
        return (startUMm + du.roundToInt()) to (startVMm + dv.roundToInt())
    }

    companion object {
        /** Length of the pointer rod: 1 cm of hand movement turns the line by atan(0.01 / 0.477) = 1.2 degrees. */
        const val ROD_M = 0.477f

        /** Hand to pin gearing while truing by drag: 4 : 1. */
        const val TRUE_GEAR = 0.25f
    }
}
