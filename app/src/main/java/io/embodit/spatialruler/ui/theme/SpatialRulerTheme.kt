package io.embodit.spatialruler.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app theme: the complete 16-role colour scheme of the design and the Noto Sans type family.
 *
 * Five roles carry exact design values; the eleven adaptive grey roles are forwarded from the system
 * (Vibrant) scheme on purpose, each one named so that the contract is visible. Rule of the design:
 * Vibrant `label*` roles are only for text that sits directly on glass; on solid dark faces use
 * `labelPrimaryLight` and the on-ink tokens of [RulerColors].
 */
@Composable
fun SpatialRulerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val system = systemColorScheme(context)
    val colors =
        remember(system) {
            system.copy(
                fillPrimary = system.fillPrimary,
                fillSecondary = system.fillSecondary,
                fillTertiary = system.fillTertiary,
                fillLight = system.fillLight,
                labelPrimaryLight = RulerColors.LabelPrimaryLight,
                labelPrimary = system.labelPrimary,
                labelSecondary = system.labelSecondary,
                labelTertiary = system.labelTertiary,
                labelQuaternary = system.labelQuaternary,
                lightenHover = system.lightenHover,
                lightenPressed = system.lightenPressed,
                error = RulerColors.Error,
                alert = RulerColors.Alert,
                passable = RulerColors.Passable,
                interaction = RulerColors.Interaction,
                dividerLine = system.dividerLine,
            )
        }
    val family = rememberRulerFontFamily()
    val base = PicoTheme.typography
    val typography = remember(base, family) { base.withFamily(family) }
    PicoTheme(colorScheme = colors, typography = typography, content = content)
}
