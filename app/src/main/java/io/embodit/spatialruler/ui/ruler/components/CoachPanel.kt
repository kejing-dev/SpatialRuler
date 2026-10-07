package io.embodit.spatialruler.ui.ruler.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.spatialruler.R
import io.embodit.spatialruler.ui.ruler.RulerEvent
import io.embodit.spatialruler.ui.ruler.debug.probe

internal val CoachPanelWidth = 360.dp
internal val CoachPanelHeight = 80.dp

/**
 * First-run coachmark (simplify spec H), one step: a pictogram (pin · tape · pin), seven words, and ✓.
 * Glass beside the instrument body, scaled like the body; shown from the start until the first tape
 * locks or ✓ (`settings.coachSeen`). SpatialUI's `CoachmarkBox` is not used: it anchors to a
 * composable inside one panel, and the body is a 3D object here. Fades in over 150 ms (F11).
 */
@Composable
internal fun CoachPanel(step: Int, onEvent: (RulerEvent) -> Unit, watch: Modifier = Modifier) {
    Box(modifier = Modifier.size(CoachPanelWidth, CoachPanelHeight), contentAlignment = Alignment.Center) {
        if (step <= 0) return@Box
        val fade = remember(step) { Animatable(0f) }
        LaunchedEffect(step) { fade.animateTo(1f, tween(FADE_MS)) }
        val shape = RoundedCornerShape(24.dp)
        Row(
            modifier =
                watch
                    .size(CoachPanelWidth, CoachPanelHeight)
                    .graphicsLayer { alpha = fade.value }
                    .clip(shape)
                    .backgroundMaterial(enable = true, style = Material.Thick)
                    .probe("coach")
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Pictogram(R.drawable.ic_pin)
                Pictogram(R.drawable.ic_tape)
                Pictogram(R.drawable.ic_pin)
            }
            Text(
                text = rs(R.string.coach_hint),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.bodyLargeMultiline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            IconButton(
                onClick = { onEvent(RulerEvent.CoachDismissed) },
                modifier = Modifier.probe("coach_got_it"),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = PicoTheme.colorScheme.fillPrimary,
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                size = IconButtonDefaults.iconButtonSize(width = 56.dp, height = 48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = rs(R.string.coach_got_it),
                    modifier = Modifier.size(24.dp),
                    tint = PicoTheme.colorScheme.labelPrimaryLight,
                )
            }
        }
    }
}

@Composable
private fun Pictogram(resId: Int) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = PicoTheme.colorScheme.labelPrimary,
    )
}

private const val FADE_MS = 150
