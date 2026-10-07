package io.embodit.spatialruler.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The design's exact colours (design-doc §3 "Visual tokens"). The palette comes from a tape measure:
 * blade yellow, tick ink, steel, whole-metre red.
 *
 * This is the only file with colour literals. The five [ColorScheme] roles below are injected into
 * `PicoTheme` by [SpatialRulerTheme]; the brand tokens are used directly (3D materials and the solid
 * dark faces of the instrument, where adaptive Vibrant colours must not land).
 */
object RulerColors {
    // ---- ColorScheme roles with a fixed design value (the other eleven inherit SpatialUI Vibrant)
    val LabelPrimaryLight = Color(0xFFFFFFFF) // design-style: fixed-figma-color labelPrimaryLight: text and icons on solid dark faces
    val Error = Color(0xFFFF5F52) // design-style: fixed-figma-color error: "Clear all" confirm button
    val Alert = Color(0xFFFF8A1F) // design-style: fixed-figma-color alert: far / unsaved marks (orange, clear of the blade yellow)
    val Passable = Color(0xFF66E08A) // design-style: fixed-figma-color passable: steady ring, plumb / level guide, saved pulse
    val Interaction = Color(0xFFFFC72C) // design-style: fixed-figma-color interaction: selected snap key, fine-tune on, retry

    // ---- brand / decorative tokens outside ColorScheme
    val TapeYellow = Color(0xFFFFC72C) // design-style: fixed-figma-color tapeYellow: blade, pin collar, body shell
    val TapeInk = Color(0xFF16181C) // design-style: fixed-figma-color tapeInk: ticks; text and icons on yellow
    val BladeEdge = Color(0xFF0E0F12) // design-style: fixed-figma-color bladeEdge: outline of blade and pin
    val MeterRed = Color(0xFFE23B2E) // design-style: fixed-figma-color meterRed: whole metre / foot band, zero end
    val Steel = Color(0xFFC9CED6) // design-style: fixed-figma-color steel: pin head, hook
    val SteelShade = Color(0xFF8A9099) // design-style: fixed-figma-color steelShade: pin stem
    val PlateInk = Color(0xF0121417) // design-style: fixed-figma-color plateInk #121417 at 94 %: reading plate, candidate chip
    val Bumper = Color(0xFF1D1F24) // design-style: fixed-figma-color bumper: body end caps, panel recess
    val OnInkFill = Color(0x24FFFFFF) // design-style: fixed-figma-color onInkFill white at 14 %: controls on dark inner cards
    val OnInkSecondary = Color(0xA8FFFFFF) // design-style: fixed-figma-color onInkSecondary white at 66 %: secondary text on dark faces
    val StaleGrey = Color(0xFF9A9A96) // design-style: fixed-figma-color staleGrey: frozen tape and pins (tracking lost)
}
