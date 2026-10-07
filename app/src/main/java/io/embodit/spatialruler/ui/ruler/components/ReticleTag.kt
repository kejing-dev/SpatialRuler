package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.CircularProgressDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import io.embodit.spatialruler.R
import io.embodit.spatialruler.ui.ruler.ReticleLabel
import io.embodit.spatialruler.ui.theme.RulerColors
import kotlin.math.abs

/** The reticle tag is a pre-built panel; the pill inside takes the width of its figure (32 - 96 dp). */
internal val ReticleTagWidth = 96.dp
internal val ReticleTagHeight = 24.dp

/**
 * A figure beside the reticle of a tracked pointer, only where the reticle has no figure of its own
 * (simplify spec I): "×4" while the aim is slowed, "▲ > 3 m" beyond three metres, ⦸ where the ray
 * meets no surface, a spinner while the surface is still being mapped. It is the face of a 3D object in
 * front of arbitrary passthrough: a solid dark pill. Not a control. Like a reading plate it is drawn
 * stretched over its whole panel, which the scene has made as narrow as the pill ([applied]; see
 * `PanelRig`, finding 5).
 */
@Composable
internal fun ReticleTag(label: ReticleLabel?, applied: Float, onWidth: (Float) -> Unit) {
    Box(modifier = Modifier.size(ReticleTagWidth, ReticleTagHeight), contentAlignment = Alignment.Center) {
        if (label == null) return@Box
        val density = LocalDensity.current
        val panelPx = with(density) { ReticleTagWidth.toPx() }
        val description =
            rs(
                when (label) {
                    ReticleLabel.FINE -> R.string.reticle_fine_gear
                    ReticleLabel.FAR -> R.string.reticle_far
                    ReticleLabel.NO_SURFACE -> R.string.reticle_none
                    ReticleLabel.MAPPING -> R.string.reticle_mapping
                }
            )
        Row(
            modifier =
                Modifier
                    .widthIn(min = 32.dp, max = ReticleTagWidth)
                    .height(ReticleTagHeight)
                    .onSizeChanged { onWidth(it.width / density.density) }
                    .graphicsLayer {
                        val own = size.width / panelPx
                        scaleX = 1f / applied
                        alpha = if (abs(applied - own) <= own * 0.12f) 1f else 0f
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(RulerColors.PlateInk)
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when (label) {
                ReticleLabel.FINE -> Figure(rs(R.string.reticle_gear), PicoTheme.colorScheme.labelPrimaryLight)
                ReticleLabel.FAR -> Figure(rs(R.string.reticle_far_figure), PicoTheme.colorScheme.alert)
                ReticleLabel.NO_SURFACE ->
                    Icon(
                        painter = painterResource(R.drawable.ic_no_surface),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = RulerColors.OnInkSecondary,
                    )
                ReticleLabel.MAPPING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = RulerColors.OnInkSecondary,
                        progressSize = CircularProgressDefaults.circleProgressSize(14.dp),
                    )
            }
        }
    }
}

@Composable
private fun Figure(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        style = PicoTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum"),
        maxLines = 1,
        softWrap = false,
    )
}
