package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import io.embodit.spatialruler.ui.ruler.debug.probe
import java.util.concurrent.ConcurrentHashMap

/**
 * Where the keys of the body panel are, in pixels of that panel. The 3D scene uses it to tell which key
 * a line of sight ends on, when a pinch that was aimed at the instrument body arrives on something
 * behind it (see `RulerScene`, the guard in front of the body).
 */
internal object BodyKeys {
    private class Area(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private val keys = ConcurrentHashMap<String, Area>()

    @Volatile var panelWidthPx = 0f
        private set

    @Volatile var panelHeightPx = 0f
        private set

    fun report(name: String, left: Float, top: Float, right: Float, bottom: Float, panelWidth: Int, panelHeight: Int) {
        keys[name] = Area(left, top, right, bottom)
        panelWidthPx = panelWidth.toFloat()
        panelHeightPx = panelHeight.toFloat()
    }

    fun forget(name: String) {
        keys.remove(name)
    }

    /** The key at the panel point ([x], [y]), at least [margin] pixels inside its edge; null between keys. */
    fun at(x: Float, y: Float, margin: Float = 4f): String? {
        for ((name, a) in keys) {
            if (x >= a.left + margin && x <= a.right - margin && y >= a.top + margin && y <= a.bottom - margin) return name
        }
        return null
    }
}

/** Registers a key of the body panel under its stable control name (also the test harness' name for it). */
@Composable
internal fun Modifier.bodyKey(name: String): Modifier {
    DisposableEffect(name) { onDispose { BodyKeys.forget(name) } }
    return this
        .onGloballyPositioned { c ->
            if (c.isAttached) {
                val p = c.positionInRoot()
                val root = c.findRootCoordinates().size
                BodyKeys.report(name, p.x, p.y, p.x + c.size.width, p.y + c.size.height, root.width, root.height)
            }
        }
        .probe(name)
}
