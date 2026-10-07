package io.embodit.spatialruler.debug

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.embodit.spatialruler.platform.RulerLog
import java.util.Locale

/** Colours of the calibration target. They are a test pattern, not product UI. */
private val CalibBackground = Color(0xFF1C1C1C) // design-style: fixed-figma-color harness calibration target
private val CalibGrid = Color(0xFF9A9A9A) // design-style: fixed-figma-color harness calibration target
private val CalibFiducial = Color(0xFFFF00FF) // design-style: fixed-figma-color harness calibration target (magenta fiducial)
private val CalibPress = Color(0xFF00FF00) // design-style: fixed-figma-color harness calibration target
private val CalibHover = Color(0xFFFFFF00) // design-style: fixed-figma-color harness calibration target

/** State of the calibration target: where the pointer is and where it pressed, in panel pixels. */
internal object CalibState {
    val presses = mutableStateListOf<Offset>()
    val hover = mutableStateOf<Offset?>(null)
    var dist = 0.9f
    var scale = 1f
    private var lastMoveMs = 0L

    fun clear() {
        presses.clear()
        hover.value = null
    }

    /** Pointer events of the target in panel-local pixels. Moves are limited to 25 log lines a second. */
    fun onPointer(type: String, x: Float, y: Float, pressed: Boolean, w: Int, h: Int) {
        val now = SystemClock.uptimeMillis()
        val isMove = type == "Move"
        if (isMove && now - lastMoveMs < 40) return
        if (isMove) lastMoveMs = now
        if (type == "Press") presses.add(Offset(x, y))
        hover.value = Offset(x, y)
        RulerLog.i(String.format(Locale.US, "CALIB ev=%s x=%.1f y=%.1f pressed=%s size=%dx%d dist=%.3f scale=%.3f", type, x, y, pressed, w, h, dist, scale))
    }
}

/**
 * Test-harness calibration target (same contract as the feasibility spike, so `tools/harness/calibrate.py`
 * works unchanged): an opaque square with nine magenta fiducials centred at 10 / 50 / 90 % of its size.
 * Every pointer event is logged in panel-local pixels (`CALIB ev=...`); presses are drawn as green dots.
 */
@Composable
internal fun CalibPanel(sizeDp: Int) {
    val hover by CalibState.hover
    val pressSnapshot = CalibState.presses.toList()
    Box(
        modifier =
            Modifier
                .size(sizeDp.dp)
                .background(CalibBackground)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: continue
                            CalibState.onPointer(event.type.toString(), change.position.x, change.position.y, change.pressed, size.width, size.height)
                        }
                    }
                }
                .drawBehind {
                    val s = size.width
                    for (i in 0..10) {
                        val t = s * i / 10f
                        drawLine(CalibGrid, Offset(t, 0f), Offset(t, s), strokeWidth = 2f)
                        drawLine(CalibGrid, Offset(0f, t), Offset(s, t), strokeWidth = 2f)
                    }
                    val m = s * 0.04f
                    for (fy in FRACTIONS) for (fx in FRACTIONS) {
                        drawRect(CalibFiducial, topLeft = Offset(fx * s - m / 2f, fy * s - m / 2f), size = Size(m, m))
                    }
                    pressSnapshot.forEach { p -> drawCircle(CalibPress, radius = s * 0.008f, center = p) }
                    hover?.let { h -> drawCircle(CalibHover, radius = s * 0.012f, center = h, style = Stroke(width = 3f)) }
                }
    )
}

private val FRACTIONS = listOf(0.1f, 0.5f, 0.9f)
