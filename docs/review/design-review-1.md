# Design review 1 — milestone 1 (system-pointer path)

2026-10-02 · product design · build under review: milestone 1 on `emulator-5556` · binding design: `design/design-doc.md` (§ references below point into it)

**Reviewed.** The twelve captures named in the request, all under `.scratch/shots/`; full frames were read at their native 2160 × 2158 where a crop was too small. For *current values only* I also read the scene and panel sources (`ui/ruler/scene/*`, `ui/ruler/components/*`, `LayoutTapeUseCase`, `ScaleForDistanceUseCase`). Colours were sampled from the captures. Contrast figures are WCAG ratios between the median background and the darkest (or brightest) 3 % of the glyph pixels, so thin strokes read lower than their token would on paper.

**Not reviewed** (milestone 2 by agreement): tracked rays, contact, press-and-pull, reticle and stability ring, lazy follow, sound and haptics, pin hover, coachmark, plane outlines, settle wobble, short-tape leader.

## Verdict: changes requested

Milestone 1 does the job of the brief: two taps give a tape and a reading in place, any two pins can be joined through the candidate threads, units and both languages work, the session comes back after a relaunch. Structure, sizes, tokens and copy follow §3 closely.

It does not yet clear the user's taste bar. At working distance the instrument reads as flat yellow graphics with black labels: pins are yellow blobs with a grey mushroom, the blade is a line that hovers with nothing under it, the body is a panel in a bumper case, the plates are loose black bars. Four layout and legibility defects sit on top of that. Eight items are P1.

Most of the P1 and P2 items trace back to numbers in my own §3, not to the build. Those rows are corrected in the design doc (Appendix C).

## 1 · Brief and taste

| Part | What it reads as now | Evidence | Items |
| --- | --- | --- | --- |
| Pin | A borderless yellow disc 50–60 mm across with a thick post and a flat cap with a drawn cross: a mushroom on a puddle, 1.5–1.9× life size at 2–2.5 m, not seated on anything | `m1_lit_compare.png`, `m1_final_smoke.png` | T1 |
| Blade | A yellow strip with ticks, no visible edge, hanging 18 mm above the floor with no contact cue; its ends just lie on the pin caps; it turns to face the head even at arm's length | `m1_lit_compare.png`, `m1_fix_chip_tape_crop.png` | T2, T3, T5 |
| Ticks and red bands | Ticks are right and readable. The whole-metre band on the 1.200 m tape is a 3 px red hairline that is lost in any reduced view. While the blade runs out it is plain yellow | `m1_lit_on.png`, `m1_lit_compare.png`, `m1_pull_sheet.png` | T4, T10 |
| Body | A glass panel in a black frame. It is turned exactly at the eye, so no side or top face shows; the yellow housing is two 9 mm strips; the glass floats 5.6 mm in front of the housing; in the frame it looks rolled by about 6° | `m1_final_smoke.png`, `m1_fix_menu_native_crop.png` | T8a, T8b |
| Reading plate | A 184 dp black bar, 40 % of it empty, floating free above the blade | every capture with a plate | T6, T7 |
| Colour | 3D yellow lands as mustard `#D1B132` next to the `#FFC72D` snap key; steel caps render as mid-grey plastic | `m1_final_smoke.png` | T9 |

What already works for the taste: the pull-out idea (the plate rides the tail and counts up, then slides to the middle), the tape head stub with its hook on the selected pin, the unsaved pin (dashed ring, dark cap), the dimming of unrelated tapes while a pin is selected, and the panel count: one standing panel, everything else appears only on selection.

## 2 · Fidelity to §3

Matches: 16-role scheme and brand tokens in the 2D UI (snap key samples `#FFC72D`); plate layout (index bar, value 24 / 600 tabular, unit 16, tolerance 12, last digit dimmed from ±1 cm: `m1_fix_lang_zh_crop.png`); body panel layout (readout, three keys, four equal segments); pin tag and its expanded form; chips a quarter of the way from the far pin; native `Menu` / `SubMenu`; copy in both languages including plurals; bundled Noto Sans and Noto Sans SC subset; restore; dimming of unrelated tapes (blade samples `#A48C34` against `#D1B132`, so this open point in `AGENTS.md` is confirmed).

Deviations: stepper value carries the unit and wraps (F2); dialog type roles and padding (F3); snackbar type roles (F4); plate stagger from §3 Layout is not built (F7); twelve plate slots, so a thirteenth tape has no plate (F6).

## 3 · Legibility over the simulated passthrough

| Element | Glyph / background (sampled) | Ratio | Result |
| --- | --- | --- | --- |
| Readout value | `#FBFBFB` on `#1D1C1A` | 16.5 | pass |
| Readout context line (12 sp × 0.75) | `#807F7E` on `#1D1C1A` | 4.3 | too small → L3 |
| Unit segment, selected | `#1D1D1D` on `#A5A4A3` | 6.8 | pass |
| Unit segment, unselected | `#3F3F3D` on `#8B8B8A` | 3.0 | **fail** → L1 |
| Plate tolerance line | `#9D9D9F` on `#131517` | 6.8 | pass |
| Candidate chip | `#DCDCDC` on `#131517` | 13.4 | pass |
| Pin tag title | `#D6D6D6` on `#302B25` | 9.7 | pass over the floor; the card is translucent → L5 |
| Pin tag badge | `#BDBDBD` on `#3A3835` | 6.2 | pass |
| Dialog title (20 sp) | `#232323` on `#B5B5B4` | 7.7 | pass |
| Dialog body (14 sp) | `#484847` on `#B4B4B2` | 4.4 | weak → F3 |
| "Clear all" label | `#FFF0F0` on `#FF5F52` | 2.7 | **fail** → L2 |
| Snackbar message (14 sp) | `#404040` on `#B8B8B7` | 5.2 | pass, small → F4 |
| Snackbar description (12 sp) | `#4B4B4A` on `#B7B7B6` | 4.35 | weak → F4 |
| Candidate thread | `#727271` on wood `#3C3934` / `#999898` on rug `#3E3C36` | 2.4 / 3.8 | weak, will vanish on the white walls → L4 |
| Blade on the floor | `#D1B132` on `#554337` | 4.5 | fine on dark floors, no edge for light ones → T2 |

Plates and chips are solid `plateInk` and hold everywhere. Everything that fails sits on the light `Material.Regular` / `Thick` glass.

## 4 · What the real build proves wrong in the design

1. Pin dimensions and its "never below 1°" growth produce a toy at 2 m (T1).
2. A 0.8 mm blade rim is below one pixel at working distance, and a 4 mm whole-metre band is a hairline (T2, T4).
3. Zero end and hook were specified at opposite ends of the blade (T3).
4. "Always turned to the viewer" (blade) and "upright only" (panels) are wrong at close range and from above (T5, L6).
5. Fixed-width, free-floating plates (T6, T7).
6. "The body faces the face": a billboard hides its thickness, and the panel covers the housing (T8a, T8b).
7. Glass premise. `Material.Regular` is a light sheet whatever lies behind it, so the dark backing does not make it dark. `labelTertiary` and `labelSecondary` are too weak on it for text of 16 sp and below, and white on `error` fails (L1, L2, F3, F4).
8. Tag inner card on Vibrant `fillPrimary` is translucent (L5). White threads without a casing (L4).
9. Tag placement only looked at the view and the body, and "tape tag below its plate" puts the tag on the tape itself (F1).
10. "L / R mm" style labels do not fit next to the stepper (F2).
11. No tape limit was designed; the plate pool makes one necessary (F6).
12. Assumption 9: `AlertDialog` and `SnackbarHost` cannot be used in a Stage, so the product hosts are plain attachment panels built to the component's metrics (F3, F4). Assumption 8: `LookAtComponent` cannot be used for panels that take the pointer.

## Rulings

- **a. Tag covering a pin or a plate** → F1 and Appendix A. Eight slots around the anchor, a hard keep-clear set that may not be covered at all, a soft set limited to 15 % of the tag area, the pin tag tries above first, the tape tag now also tries **above its plate** first.
- **b. Stepper value wraps** → F2. The value is the signed number only. The row label becomes two lines: axis, then "1 mm / step". Stepper width stays 136 dp.
- **c. Body looks rolled** → T8a. Constrain it: long edge level, no turn towards the eye about the vertical, tilted back 12°. Its top and bottom edges are then horizontal in the frame and the right end and the top face show.
- **d. Plain blade while it moves** → T10. Not acceptable as the final look. Use shared, pre-built 10 cm segments with ticks, instanced along the moving blade; no mesh is built during motion. P2 now, P1 before press-and-pull ships.
- **e. Dialog and snackbar as custom panels** → F3, F4. They read as SpatialUI surfaces. Adjust type roles, padding, button row, label colours; keep `Material.Thick` on both.
- **f. Candidate chips as dark pills** → keep. They are solid `plateInk` like the plates and measure 13.4 : 1. Two additions: a dark casing under the threads (L4) and a minimum distance between a chip and the pin it leads to (F5).

## Punch list

| ID | Pri | Item |
| --- | --- | --- |
| T1 | P1 | Pin: proportions, rim, true size, contact shadow |
| T2 | P1 | Blade: visible rim, contact shadow, hook stays after lock |
| T6 | P1 | Plate hugs its content |
| T8a | P1 | Body pose: level, unyawed, tilted back 12° (ruling c) |
| L1 | P1 | Unit segments: unselected labels |
| L2 | P1 | Label colour on `error` and the other semantic fills |
| F1 | P1 | Tag placement rule (ruling a) |
| F2 | P1 | Stepper value and row label (ruling b) |
| T3 | P2 | Zero end and hook at the same end |
| T5 | P2 | Blade lies flat at close range |
| T7 | P2 | Plate mounted on the blade (rider and stem) |
| T8b | P2 | Body housing: size, bezel, bumpers |
| T9 | P2 | 3D yellow and steel off-token |
| T10 | P2 → P1 with press-and-pull | Ticks while the blade moves (ruling d) |
| L3 | P2 | Readout context line size |
| L4 | P2 | Candidate threads: casing and width |
| L5 | P2 | Tag inner card solid |
| L6 | P2 | World panels pitch towards the viewer |
| F3 | P2 | Dialog panel metrics (ruling e) |
| F4 | P2 | Snackbar panel metrics (ruling e) |
| F5 | P2 | Chip distance from its pin (ruling f) |
| F6 | P2 | Tape limit 20 and plate pool |
| F7 | P2 | Plate stagger along the tape |
| T4 | P3 | Whole-metre band 10 mm |
| F8 | P3 | Tag glass rim 6 → 3 dp |

### T1 · P1 · Pin

- **Wrong.** `m1_lit_compare.png` (right half), `m1_final_smoke.png`. The collar is a flat, borderless `tapeYellow` disc; the post is Ø 9 mm; the cap is a flat Ø 22 mm disc with a drawn cross. `ScaleForDistanceUseCase.pin` draws the whole pin at distance / 1.3, so at 2–2.5 m the collar is 50–60 mm across. Nothing ties it to the surface.
- **Change.**
  1. Visual scale = clamp(distance / 2.5 m, 1, 2): life size up to 2.5 m, twice at 5 m. The touch sphere keeps today's reach on its own entity: 28 mm × clamp(distance / 1.3 m, 1, 4).
  2. Base: `bladeEdge` disc Ø 33 × 1.0 mm on the surface. On it the washer: Ø 30 × 2.5 mm, `tapeYellow`. Four `tapeInk` index marks on the washer top at 0° / 90° / 180° / 270°: 5.0 × 1.2 mm, outer end 2.0 mm inside the washer edge.
  3. Shank: Ø 5 × 11 mm, `steelShade` (was Ø 9 × 18).
  4. Head: flange Ø 16 × 2 mm and on it a dome Ø 14 mm, 6 mm high (a sphere scaled 1 × 0.43 × 1), both `steel`; centre punch = `tapeInk` disc Ø 3 mm on top of the dome. Delete the cross.
  5. Heights above the surface: base 0–1, washer 1–3.5, shank 3.5–14.5, flange 14.5–16.5, dome up to 22.5 mm. The blade attaches under the flange: `PIN_HEAD_M` 18 → 14.5 mm.
  6. Contact shadow: disc Ø 46 mm, `#000000` at 30 %, unlit, transparent, no depth write, 0.4 mm above the surface. It stays on the surface when the selected pin lifts 6 mm.
  7. Pending and unsaved rings keep their meaning on the new washer: dashed ring from 12.4 to 15 mm radius.
- **Ref.** §3 实物 → 钉 Pin.

### T2 · P1 · Blade

- **Wrong.** `m1_lit_compare.png`, `m1_fix_chip_tape_crop.png`. The `bladeEdge` rim is 0.8 mm (`EDGE_M`), about 0.6 px at 2 m, so the yellow has no edge. Nothing lies under the blade. The hook is hidden once the tape locks (`showHook = growing`), so the ends just overlap the pin caps.
- **Change.**
  1. `EDGE_M` 0.8 → 1.6 mm; `EDGE_SELECTED_M` 1.8 → 2.8 mm.
  2. Contact shadow for every tape whose two pins sit on one plane (normals within 5°, both points within 10 mm of that plane): the shared unit band laid on the plane under the blade axis, width = blade width × `tapeWidth(distance)` + 10 mm, `#000000` at 24 %, unlit, transparent, no depth write, 0.4 mm above the surface. While the blade is pulled out or retracted the shadow has the length of the visible blade. No shadow for a tape between two planes.
  3. The hook stays after lock and sits over the tail pin (geometry in T3).
- **Ref.** §3 实物 → 尺带 Tape; token `bladeEdge`.

### T3 · P2 · Zero end and hook

- **Wrong.** `m1_fix_chips_crop.png`, `m1_fix_pin_tune_crop.png`: the stub is red at the pin and carries the hook at its free end. On a tape the zero is at the hook. This is my §3 error.
- **Change.** The blade is printed from the hook. In `LayoutTapeUseCase.layout`, x = 0 (the 12 mm `meterRed` zero end and the origin of ticks and whole-unit bands) is the hook end, which is the tail pin; the head pin is where the blade comes out and where it retracts to. Hook = a tongue 9 × 20 × 1.2 mm that continues the blade past the tail pin's axis, plus a lip 1.2 × 20 × 7 mm at its end, bent towards the blade's back face; `steel`; shared meshes; its width follows the blade's width factor. The same hook is used on the stub, on the moving blade and on the body's tape mouth.
- **Ref.** §3 实物 → 尺带, 尺头.

### T4 · P3 · Whole-metre band

- **Wrong.** `m1_lit_on.png`: on the 1.200 m tape the band at 1 m is a red line about 3 px wide (`WHOLE_BAND_M` = 4 mm), no stronger than a 10 cm tick, and it is gone in the reduced `m1_lit_compare.png`.
- **Change.** `WHOLE_BAND_M` 4 → 10 mm, full width between the rims, metric and imperial.
- **Ref.** §3 实物 → 尺带.

### T5 · P2 · Blade orientation at close range

- **Wrong.** `TapeBlade.draw` always turns the face to the head. At arm's length a blade that follows the head reads as a 2D line. This is not judged from the captures (the camera stays about 2 m from the tapes); it follows from the rule itself.
- **Change.** Reference normal: both pins on one plane → that plane's normal; otherwise the unit vector across the tape that is closest to world up (for a tape within 20° of vertical: closest to the direction from the tape to the head at the moment it locked). Blade normal = slerp(reference, view-facing, t) with t = smoothstep(1.0 m, 2.2 m, distance from the head to the tape's midpoint). Stub, pull-out and retract use the same rule.
- **Ref.** §3 实物 → 尺带.

### T6 · P1 · Plate width

- **Wrong.** `m1_final_smoke_crop.png` and every other plate: the pill is the full 184 dp slot; "800 mm" ends at 126 px of 265.
- **Change.** Pill width = content + paddings (start 7, end 12 as built), clamped to 96–184 dp, centred in the fixed 184 × 58 dp slot. The rest of the slot stays transparent, so the pointer passes through it. Tag placement and stagger use the pill width.
- **Ref.** §3 Surface sizing → 读数牌.

### T7 · P2 · Plate mounted on the blade

- **Wrong.** All captures: the plate hangs in the air above the blade. It reads as a HUD label, not as part of the instrument.
- **Change.** Two parts per plate, shared meshes, lit:
  1. Rider: a `bumper` box that straddles the blade where the plate stands: 22 mm along the tape × (blade width + 6 mm) across × 6 mm through the blade. It follows the blade's frame and width factor.
  2. Stem: `steelShade` cylinder Ø 3 mm × panel scale, from the rider to the middle of the pill's nearest edge.
  Both travel with the plate while it slides from the tail to its place.
- **Ref.** §3 实物 → 读数牌支架 (new row).

### T8a · P1 · Body pose (ruling c)

- **Wrong.** `m1_final_smoke.png`: `BodyView.HEAD_ROTATION = facingHead(HEAD_OFFSET)` turns the body exactly at the eye. In the frame its top edge rises about 6° to the right, and no side or top face is ever visible, so it has no thickness.
- **Change.** `HEAD_ROTATION` = a turn about the head's X axis only: body X = head X (level), face normal 12° above the head's backward axis (top edge tilted away from the viewer). No turn about the vertical. The viewer then sees the face from 18° to the right and 10° above its normal: the right end with the tape mouth and the top face show, and the top and bottom edges are horizontal in the frame. Acceptance: in a console capture with a level head, both long edges are within ±1° of horizontal. Milestone 2 keeps this pose in world space when the lazy follow settles (X level and parallel to the head's X at that moment, tilted back 12°).
- **Ref.** §3 实物 → 尺身 Body; Surface sizing → 尺身面板.

### T8b · P2 · Body housing

- **Wrong.** `m1_fix_menu_native_crop.png`, `m1_pull_sheet.png`. The panel (204 × 81.6 mm) covers a 220 × 100 mm housing: 9 mm of yellow above and below, bumpers hidden behind the panel, and the glass floats 5.6 mm in front of the housing (`PANEL_Z`).
- **Change.** Panel size and scale stay (340 × 136 dp at 0.75).
  1. Housing 236 × 116 × 32 mm (was 220 × 100 × 30), corner radius 14 mm, lit `tapeYellow`.
  2. Bezel: four `bumper` bars around the glass, 4 mm wide, outer size 213 × 90.6 mm, top 3.0 mm above the housing front. Glass plane 1.5 mm above the housing front, so it sits 1.5 mm below the bezel. Dark backing under the glass as today.
  3. Bumpers: 14 × 122 × 38 mm each, corner radius 7 mm, at x = ±114 mm (3 mm proud of the housing on every side, clear of the bezel). Three `bladeEdge` grip ribs on each bumper front: 1.6 × 70 × 1.2 mm, 4 mm pitch.
  4. Tape mouth as built, with the hook of T3.
  5. Collider: its front face stays behind the glass plane. Verify with a real pinch on `undo` (`PTR panel=body`); if presses are swallowed (`TAP other target=body`), move only the collider back until the 5 mm clearance of today is restored.
- **Ref.** §3 实物 → 尺身 Body.

### T9 · P2 · 3D colours

- **Wrong.** `m1_final_smoke.png`: unlit `tapeYellow` (`#FFC72C`) arrives in the frame as `#D1B132` on the collar, the blade and the mouth stub alike, while the same token on the 2D snap key samples `#FFC72D`. Lit `steel` (`#C9CED6`, metallic 1.0) samples `#6B6C6E`–`#868C91` on the cap.
- **Change.**
  1. Yellow. Target for a flat unlit `tapeYellow` face in a console capture: R ≥ 240, G 185–205, B ≤ 60. The Stage appears to tone-map unlit colours. Try a base colour above 1.0, or an emissive term if the material API has one. If neither moves the sample, report it and keep the token; do not change the 2D colours.
  2. Steel. `steelLit` and `steelShadeLit`: metallic 1.0 → 0.6, roughness as built. With nothing to reflect, a fully metallic surface renders dark. Target: cap top face ≥ `#A9AEB5`.
- **Ref.** §3 brand tokens `tapeYellow`, `steel`.

### T10 · P2, P1 before press-and-pull ships · Ticks while the blade moves (ruling d)

- **Wrong.** `TapeBlade` draws the plain shared band while the length animates and prints the face at lock. For 0.6–1.1 s the signature shows a yellow line, then the ticks pop in.
- **Change.** Three shared meshes built once at start-up: `seg_metric_fine` = 100 mm of printed blade (1 cm, 5 cm and 10 cm ticks, rims); `seg_metric_coarse` = 100 mm with the 5 cm and 10 cm ticks; `seg_imperial` = 1 ft with 1 in and 6 in ticks. While the length animates, draw ⌊length / segment⌋ instances from the zero end and the plain band for the remainder. Fine when `SelectTickLevelUseCase` gives step 1, coarse otherwise. At most 60 instances. Swap to the printed face in the frame the length settles. No mesh is built during motion; whole-unit bands may be missing while it moves.
- **Ref.** §3 Signature element; 实物 → 尺带.

### L1 · P1 · Unit segments

- **Wrong.** `m1_final_smoke_crop.png`: "m", "cm", "ft·in" sample `#3F3F3D` on `#8B8B8A`, 3.0 : 1. `itemContentColor = labelTertiary`.
- **Change.** `itemContentColor = labelPrimary`. The selected segment keeps the `fillSecondary` pill.
- **Ref.** §3 Visual tokens → `labelPrimary`, `labelTertiary`.

### L2 · P1 · Label on semantic fills

- **Wrong.** `m1_fix_dialog_crop.png`: "Clear all" is `labelPrimaryLight` on `error`, 2.7 : 1.
- **Change.** Content colour on `error` = `tapeInk` `#16181C` (5.9 : 1). Rule for all four semantic fills (`error`, `alert`, `passable`, `interaction`): content is `tapeInk`.
- **Ref.** §3 Visual tokens, rule under the role table.

### L3 · P2 · Readout context line

- **Wrong.** `m1_final_smoke_crop.png`: "Last · ±8 mm" is `RulerType.figure` (12 sp) at panel scale 0.75; its glyphs sample `#807F7E`, 4.3 : 1. This line carries the state and the tolerance.
- **Change.** Context line → `labelLarge` (14 sp), weight 600, tabular figures, colour `onInkSecondary` unchanged. Readout paddings top 4 / bottom 4 so that 18 + 30 dp fit the 56 dp window. Acceptance: brightest glyph pixels ≥ `#A0A0A0`.
- **Ref.** §3 Type.

### L4 · P2 · Candidate threads

- **Wrong.** `m1_fix_chips_crop.png`: white dashes 2 px wide, 2.4 : 1 on the wood floor; on the white walls they will not show.
- **Change.** Under each white strip draw the same dashed mesh in `bladeEdge` at 70 % opacity, 2.2× as wide, 0.2 mm behind. White width = max(2.0 mm, 0.0016 × distance) (today 1.5 mm and 0.0012).
- **Ref.** §3 实物 → 候选线.

### L5 · P2 · Tag inner card

- **Wrong.** `m1_fix_chips_crop.png`: the card is Vibrant `fillPrimary` and samples `#302B25`, the floor tinted through it. In front of a white wall it will lift towards mid-grey under white text.
- **Change.** Tag inner card = `plateInk` (`#121417` at 94 %), the same face as plates and chips. Controls on it keep `onInkFill`.
- **Ref.** §3 Materials / depth.

### L6 · P2 · World panels face the viewer in pitch too

- **Wrong.** `PanelRig.face` turns room panels about the vertical only. The 801 mm plate already loses about a tenth of its height at this camera (`m1_fix_chips_crop.png`); a plate on the floor 0.6 m ahead of a standing user is seen 68° from above and keeps 37 %.
- **Change.** Rotation = yaw towards the head, then pitch towards the head clamped to ±60° (R6 in the addendum; this line first said ±40°), roll 0. Written to the holder as today. Re-check that presses land within 3 px of the aimed point (finding 4 in `AGENTS.md`).
- **Ref.** §3 Surface sizing, facing rule above the table.

### F1 · P1 · Tag placement (ruling a)

- **Wrong.** `m1_fix_chips_crop.png` (the stub's hook touches the tag), and the open point in `AGENTS.md`: `TagSpot` only checks the view and the body, so a tag can cover the other end pin of a tape or a reading plate. During fine-tune the plates of the pin's tapes are what the user watches.
- **Change.** Appendix A.
- **Ref.** §3 Surface sizing → 钉牌, 尺牌.

### F2 · P1 · Stepper (ruling b)

- **Wrong.** `m1_fix_pin_tune_crop.png`: value `"$value mm"` wraps to "+1" over "mm" in the 136 dp stepper.
- **Change.**
  1. Value = signed integer only: "+1", "0", "−1" (U+2212). Style `titleMedium` with tabular figures. Width stays 136 dp.
  2. Row label, two lines in the 80 dp column: line 1 = "↔ L / R", "↕ Near / far", "↕ Up / down" (zh 左右 / 远近 / 上下), `labelMedium` 600, `labelPrimaryLight`; line 2 = new string `step_hint`: "1 mm / step" (zh "每步 1 mm"), `labelMedium`, `onInkSecondary`.
- **Ref.** §3 文案 → 钉牌; Layout wireframe.

### F3 · P2 · Dialog panel (ruling e)

- **Wrong.** `m1_fix_dialog_crop.png`, `DialogPanel`: title `titleLarge` (about 20 sp), body `bodyMedium` in `labelSecondary` (4.4 : 1), padding 24, two full-width buttons.
- **Change.** Follow the `AlertDialog` metrics: padding 32 dp; title `headlineLarge` 24 / 30 in `labelPrimary`; 12 dp gap; body `bodyLargeMultiline` 16 / 24 in `labelPrimary`; buttons right-aligned, 8 dp apart, each at least 120 dp wide, 48 dp high; "Clear all" = `error` fill with `tapeInk` label; "Cancel" as built. Keep 480 × 224 dp, `Material.Thick`, the 32 dp corners. While it is open, taps on surfaces, pins, plates, chips and body keys do nothing; it closes only through its two buttons.
- **Ref.** §3 Type; SpatialUI component map → 清除确认.

### F4 · P2 · Snackbar panel (ruling e)

- **Wrong.** `m1_fix_snack_crop.png`, `m1_final_restore_crop.png`: message `bodyMedium` (14 sp), description in `labelSecondary` (4.35 : 1).
- **Change.** Message `titleMedium` 16 / 20, weight 600, `labelPrimary`; description `bodySmall` 12 / 16, `labelPrimary`; action button `interaction` fill with `tapeInk` label (R11 in the addendum; this line first said "as built"). Keep `Material.Thick`, the 28 dp corners, 320–560 dp.
- **Ref.** §3 Type; Materials / depth.

### F5 · P2 · Chip distance from its pin (ruling f)

- **Wrong.** Not in the captures; follows from `placeCandidates` (24 % of the thread from the far pin). For two pins 0.3 m apart at 2 m the chip lands 2° from the pin and covers it.
- **Change.** Position along the thread from the far pin = max(24 % of its length, the length that subtends 2.5° at the head), capped at 50 %.
- **Ref.** §3 Surface sizing → 候选距离片.

### F6 · P2 · Tape limit and plate pool

- **Wrong.** `PLATE_SLOTS = 12`; a thirteenth tape gets no plate (`firstOrNull ?: continue`), so it has no number and cannot be selected.
- **Change.** `PLATE_SLOTS` 12 → 20 (about 35 panels in all, inside the 42 verified in the spike). A twenty-first tape is not created; the tail action otherwise proceeds (a new pin is still set and becomes the tape head). New snackbar `tape_limit`: "Limit of 20 tapes reached" / "Retract one before pulling another." (zh "最多 20 条尺" / "先收回一条，再拉新的。").
- **Ref.** §3 Layout; 文案 → 提示条.

### F7 · P2 · Plate stagger

- **Wrong.** `drawTapes` always puts the plate at the midpoint. §3 Layout asks for a stagger when plates cover each other.
- **Change.** When a tape locks, a pin moves or a tape is removed, choose for each plate the first of 50 %, 36 %, 64 %, 24 %, 76 % of its tape at which its pill (inflated by 0.5°) meets no other pill and no pin's 1.5° square. Keep the choice until one of those events.
- **Ref.** §3 Layout.

### F8 · P3 · Tag rim

- **Wrong.** `m1_fix_pin_tune_crop.png`: the 6 dp glass margin shows as a heavy light outline around the dark card.
- **Change.** `TagCard` glass padding 6 → 3 dp; inner radius 12 → 15 dp.
- **Ref.** §3 Materials / depth.

## Appendix A — tag placement (F1)

Applies to the pin tag and the tape tag. Everything is measured as seen from the head: for a point P, yaw and pitch in the head frame as `TagSpot.clear` already computes them; a rectangle is a centre (yaw, pitch) with half-sizes atan(half-extent / range).

1. **Anchor box.** Pin tag: the selected pin, ±1.5°. Tape tag: the pill of the selected plate.
2. **Slots.** Eight positions of the tag centre around the anchor. N, S, E, W as built (`clearX` / `clearY` unchanged). NE, NW, SE, SW = the vertical offset of N or S plus the horizontal offset of E or W.
3. **Order.** Pin tag: N, NE, NW, E, W, S, SE, SW. Tape tag: N, NE, NW, S, SE, SW, E, W.
4. **Hard set** (the tag may not overlap any of it):
   - pin tag: the selected pin and its stub (bounding box of the stub, inflated 0.5°); every pin joined to it by a tape or a candidate thread (±1.5° each); the pill of every plate on its tapes (inflated 0.5°); every candidate chip (inflated 0.5°); the body (today's `BODY_*` box);
   - tape tag: both end pins of the tape (±1.5°); the body.
5. **Soft set:** every other pin (±1.5°) and every other plate pill.
6. **View:** today's limits (36° yaw, 34° pitch).
7. **Choice.** Walk the slots in order. Skip a slot that leaves the view or touches the hard set. Take the first remaining slot whose overlap with the soft set is at most 15 % of the tag's area; if none qualifies, the remaining slot with the least soft overlap.
8. **No slot left.** Take the in-view slot with the smallest hard overlap and move the tag outward along that slot's direction in 2° steps, at most five, until the hard overlap is zero. When the gap between tag and anchor exceeds 4°, draw a leader from the anchor to the middle of the tag's nearest edge: 1.5 mm line, white at 66 % (`onInkSecondary`), unlit.
9. **Stability.** Keep the chosen slot while it stays in view (3° slack as today) and clear of the hard set. Choose again when the tag opens, when fine-tune expands or collapses it, and when the kept slot has failed for 0.4 s. The tag reappears at its new place; no animation is needed.

## Appendix B — evidence wanted next round

1. Pin tag (collapsed and expanded) in front of the white wall and of the teal wall.
2. A tape and its pins on the white wall and on the bed cover: rim, shadow, threads.
3. Close view, 0.5–0.7 m from a floor tape: flat blade (T5), plate pitch (L6), pin detail (T1).
4. Far view, 4–5 m: pin and blade size, whole-metre band (T4).
5. Twelve pins with 20 tapes, then one more: stagger (F7), limit snackbar (F6).
6. Selected pin with two tapes and three candidates: tag slot chosen (F1), chips (F5).
7. Tape tag on a short tape (0.3 m) and on a vertical tape.
8. `ft·in` plate with an unsaved end ("▲ ±0.6″ · Unsaved"): widest pill (T6).
9. Body after T8a / T8b with a level head and with the head pitched 20° down.
10. Dialog and snackbar in Chinese after F3 / F4.
11. Pull-out with segments (T10): three frames of the moving blade.

## Appendix C — design-doc rows changed in this round

`design/design-doc.md`, dated note under §6. `preview.html` was not rebuilt and no longer matches these rows.

- §3 Visual tokens: `labelPrimary`, `labelSecondary`, `labelTertiary` uses; rule on content colour for semantic fills.
- §3 Type: dialog title, snackbar message, readout context line.
- §3 Materials / depth: tag inner card `plateInk`; snackbar `Thick`; glass premise corrected.
- §3 实物: 钉, 尺带, 尺头, 尺身, 候选线; new row 读数牌支架.
- §3 Surface sizing: facing rule; 尺身面板, 读数牌, 钉牌, 尺牌, 候选距离片, 提示条, 对话框.
- §3 Layout: tape limit 20.
- §3 文案: 钉牌 stepper label; new snackbar `tape_limit`.
- §3 SpatialUI component map: product hosts of dialog and snackbar.
- §3 assumptions 1, 2, 8, 9: emulator results.

## Addendum — rulings after test round 1

2026-10-02 · input: `docs/test/round1.md` (defects D1–D13, questions Q1–Q3, measurements) and `.scratch/shots/round1/` (curated 01–14, `evidence/`). These are the points where the build follows the letter of the design and the design had no answer. Where a ruling touches an item above, it amends it. `T10` is P1 in the next dev round, because press-and-pull is being built.

| ID | Pri | Answers | Ruling |
| --- | --- | --- | --- |
| R1 | P1 | D3 | Tolerance ladder continues to 50 cm; never rounded down |
| R2 | P1 | D13 | Body rests on a surface instead of sinking into it |
| R3 | P1 | D2, D4, F1 | World panels stay on the viewer's side of real surfaces; tags stand in front of what they overlap |
| R4 | P1 | Q1 | Pointer channel snaps only on the tapped plane, with a metric cap |
| R5 | P2 | Q2 | Constant visual angle up to 6 m |
| R6 | P2 | Q3, L6 | L6 confirmed, pitch clamp ±60° |
| R7 | P2 | D4 | Live reading on the body readout while tuning |
| R8 | P2 | D8, D9 | Body panel first; nothing drawn before the preferences are known |
| R9 | P2 | D12, D5, F7 | A plate never lies over its own blade: two sides tried |
| R10 | P3 | D6 | Tape tag: end grades on two lines |
| R11 | P3 | D11 | Snackbar action button in `interaction` |

### R1 · P1 · Tolerance ladder (D3)

- **Wrong.** `04_tape_plate_far_crop.png`, `dump` in D3: root-sum-square 78, 68 and 61 mm are all shown as "±5 cm". The ladder in §3 stopped at 5 cm, so the build rounds down on exactly the tapes marked far.
- **Change.**
  1. Ladder, always rounded **up** to the next step: 2, 3, 5, 8 mm · 1, 1.5, 2, 3, 5, 8 cm · 10, 15, 20, 30, 50 cm. Above 50 cm the text is "> 50 cm" (no ± sign). The same ladder applies to a single pin's tolerance on the tag badge.
  2. Metric format, the same in m, cm and mm: below 1 cm in millimetres ("±8 mm"); from 1 cm in centimetres ("±1.5 cm", "±8 cm", "±10 cm", "±50 cm").
  3. Imperial format: the metric step converted to inches and rounded up, to 0.1″ for steps up to 8 cm and to a whole inch from 10 cm. The full series: ±0.1″, ±0.2″, ±0.2″, ±0.4″ · ±0.4″, ±0.6″, ±0.8″, ±1.2″, ±2.0″, ±3.2″ · ±4″, ±6″, ±8″, ±12″, ±20″; above: "> 20″".
  4. Dimmed digits: from ±1 cm the last digit, as built. From ±10 cm the last two digits in metric (4.4**72** m, 44**7.2** cm, 44**72** mm) and the whole inch figure in imperial (14′ **8.1″**).
- **Ref.** §3 交互模型 → 怎么知道量得多准; 读数格式.

### R2 · P1 · Body and real surfaces (D13)

- **Wrong.** D13: with the head 0.19 m above the floor the body centre is 10 cm under the floor and its keys set pins (`13_oblique_low_view.png`). The same happens over a table. The design had no rule.
- **Change.** The body rests on a surface instead of sinking into it.
  1. Compute the pose as designed (18° left, 22° down, 0.6 m; orientation of T8a).
  2. For every recognised plane that has the head at least 5 cm on one side, and whose quad grown by 15 cm contains the foot point of the body centre: s = half-extent of the body's bounding box (bumpers included) along the plane normal. The centre must be at least s + 3 cm from the plane on the head's side. If it is not, move the centre along the plane normal by the deficit. Run at most three passes over the planes.
  3. If the centre is then nearer than 0.35 m to the head, slide it along that plane, straight away from the head's foot point, until it is 0.35 m away.
  4. Dead band 1 cm; a correction is eased over 0.2 s, time-based. Panels and collider move with the body.
  5. Milestone 2: the same test when the lazy follow settles, and again whenever a plane is added or updated while the body is at rest.
- **Acceptance.** Head at the D13 pose: body centre ≥ floor + s + 3 cm; a real pinch on `unit_cm` logs `UNIT cm` and no `PIN add`.
- **Ref.** §3 实物 → 尺身 Body.

### R3 · P1 · World panels and real surfaces (D2, D4; amends F1 and Appendix A)

- **Wrong.** `07_tape_tag_floor_tape.png`, `evidence/g02_pintag_low.png`: tags hang through or below the floor, and plates and blades nearer to the viewer lie over their buttons, so the pinch goes to the floor or to a plate. Appendix A only looked at overlap in view.
- **Change.** For every world panel (plate, chip, tag, reticle tag), after it has been turned to the viewer:
  1. **Surface rule.** Each of its four corners is at least 2 cm on the head's side of every recognised plane whose quad, grown by 5 cm, contains the corner's foot point.
  2. **How to satisfy it.** Move the panel towards the head along the ray head → panel centre until rule 1 holds. Its scale follows the distance, so its place and size in view stay the same. Limits: not nearer than 0.45 m; a tag not nearer than half the range of its anchor.
  3. **Tags stand in front.** A tag's range is at most the range of the nearest plate, chip, blade or pin whose rectangle in view overlaps the tag, minus 5 cm (same limits). Nothing then lies over a tag's controls.
  4. **Slot choice.** In Appendix A step 7, also skip a slot that cannot meet rules 2 and 3 within the limits. If every slot is skipped, take the one that needs the least pull and place it at its limit.
  5. **Leader.** When a tag ends nearer than 0.8 × its anchor's range, draw the leader of Appendix A step 8.
- **Ref.** §3 Surface sizing → 表面规则; 钉牌, 尺牌.

### R4 · P1 · Snapping in the pointer channel (Q1)

- **Wrong.** Q1: a tap on a cabinet top at 5.9 m became a pin on the corner of another plane 0.44 m nearer, 18 px from the tapped pixel. The angular rule allows it, and this channel has no reticle to show a seat before the pinch.
- **Change.**
  1. **Pointer channel** (system tap: gaze and pinch, emulator mouse). Seats are only corners and edges of the **tapped plane**, and plumb / level relative to the tape head within the tapped plane. Never a seat on another plane. The angular radii stay (`CORNER_DEG` 0.9, `EDGE_DEG` 0.6, `GUIDE_DEG` 0.85) and a metric cap is added on the distance between the tapped point and the seat: 40 mm for a corner, 30 mm for an edge, plumb or level. Both must hold.
  2. **Shown after the fact** (P2). A snapped pin lands with its seat mark instead of the ripple, for 0.8 s, fading by time: corner = two bars 40 × 2.5 mm along the two plane edges from the corner; edge = one bar 80 × 2.5 mm along the edge, centred on the pin; plumb / level = 60 mm of guide line from the pin towards the head pin. `interaction` for corner and edge, `passable` for plumb and level, each over a `bladeEdge` casing 1 mm wider on every side.
  3. **Tracked rays and press-and-pull** (a reticle shows the seat before the press). Keep the angular rule as built, including seats on other planes that the hit surface does not hide. No metric cap.
  4. Contact channel: pins only, unchanged.
- **Ref.** §3 交互模型 → 吸附.

### R5 · P2 · Readable to 6 m (Q2)

- **Wrong.** `14_far_view_4m.png`, `04_tape_plate_far_crop.png`: beyond the 3 m clamp digits shrink to 17 px at 4.3 m and 12 px at 5.8 m, and the tolerance line to 5–6 px. §1 promises readings to 5 m.
- **Change.**
  1. `PANEL_MAX_DISTANCE_M` 3.0 → 6.0 for all world panels: scale = distance / 0.8 from 0.45 m to 6 m. Plates, chips and tags then keep their size in view over the whole measuring range.
  2. Beyond 6 m a plate keeps its 6 m size and takes the compact form: value line only, with "▲" in `alert` in front of the value when the tape is far or unsaved. The tolerance stays on the body readout and in the tape tag.
- **Ref.** §3 Surface sizing (definition of 恒定视角, Max column).

### R6 · P2 · Plates seen from above (Q3; amends L6)

- **Wrong.** `03_tape_plate_close.png`: at 0.62 m, looking down 47°, the upright plate is squashed to 243 × 56 px.
- **Change.** L6 covers it. Clamp the pitch at ±60°, not ±40°: at 47° the plate is then seen square on, and a plate at the user's feet (75° down) still keeps 97 % of its height. When the head is less than 0.1 m from the vertical through the panel, keep the last yaw.
- **Ref.** §3 Surface sizing → 朝向规则.

### R7 · P2 · Live reading while tuning (D4)

- **Wrong.** `06_pin_tag_finetune_open.png`: the expanded tag covers the plates of the pin's tapes and the readout shows "Pull to next point / — —", so the changing reading cannot be seen.
- **Change.** Two places.
  1. Plates of the pin's tapes are in the hard set of Appendix A and stay uncovered (F1, R3).
  2. Body readout while fine-tune is open (`pin-selected-tuning`, and while the pin is dragged): context "Tuning · {tolerance}" (zh "微调 · {公差}"), value = live length of the reference tape = the most recently created tape on this pin, with the same dimmed digits as its plate. A pin without a tape: "Tuning" (zh "微调") over "— —". On closing fine-tune the readout returns to "Pull to next point".
- **Ref.** §3 文案 → 读数窗语境; State graph → 微调.

### R8 · P2 · What is on screen first (D8, D9)

- **Wrong.** `evidence/a1_start_02.png`: plain passthrough for 9–12 s at the emulator's idle frame rate, because the body appears only after the scene is built. `evidence/k02_restore_0.png`: "Getting ready…" in English with "m" selected while 中文 and ft·in are stored.
- **Change.**
  1. Build order: the body panel first. It needs no mesh and must be visible before any mesh is built (within 1 s of the Stage opening at 60 fps). Then the housing, then the rest of the scene. The readout shows the spinner and "Getting ready…" until the scene is attached.
  2. Language, unit and snap are read before any panel text is composed. Until they are known the panel shows the spinner only: no readout text, no selected unit segment, keys disabled. A blank readout with the spinner is acceptable; text in the default language or a default unit is not.
  3. During `starting`: Undo disabled; Snap, More (language, exit) and the unit segments work.
- **Ref.** §3 State graph → 准备中; 反馈、持久化与边界 → 持久化.

### R9 · P2 · Plate and its own blade (D12, D5; amends F7)

- **Wrong.** `evidence/m0_plates.jpg` ("±3 cm" cut by the blade), `03_tape_plate_close.png`: a tape seen end-on runs across its own plate. T7 does not cover this: the rider and stem tie the plate to the blade, they do not choose its side.
- **Change.** F7 becomes: candidates are the five positions 50, 36, 64, 24, 76 % of the tape on **both sides** across it; the side that is upper in view first, and for a tape within 20° of vertical in view the right side first. A candidate passes when the pill, inflated by 0.5°, meets neither its own blade as seen from the head, nor any pin's ±1.5° box, nor another pill. Take the first that passes; if none passes, the first that clears its own blade and the pins. The gap between the pill and the blade is at least 0.5°; the stem of T7 spans it.
- **Ref.** §3 Layout; Surface sizing → 读数牌.

### R10 · P3 · Tape tag end grades (D6)

- **Wrong.** `evidence/f02_tape_tag_wall.png`: "Start ○ Coarse · Far End ○ Coarse · Fa" is clipped.
- **Change.** Two lines in both languages: "Start ○ Coarse · Far" over "End ○ Coarse · Far" (zh "起 ○ 粗瞄 · 偏远" over "止 ○ 粗瞄 · 偏远"), `bodySmall`, one line each, 184 dp tag height unchanged.
- **Ref.** §3 文案 → 尺牌.

### R11 · P3 · Snackbar action button (D11; amends F4)

- **Wrong.** `11_snackbar_unsaved_retry.png`: "Retry" is a dark button. §3 gives `interaction` as the fill of 重试, and F4 wrongly said "as built".
- **Change.** Snackbar action buttons ("Retry", "Clear them"): `interaction` fill, `tapeInk` label.
- **Ref.** §3 Visual tokens → `interaction`.

### Not ruled here

- D1 (body upper half lets pinches through, seen once) is a build defect; no design question in it. R2 removes one way to get there.
- D5 and D7 are F7 (now R9) and F6. D10 is F2.
- Dimming of unrelated tapes (question in §3 of the test report) is in the build: the blade samples `#A48C34` against `#D1B132` in `m1_fix_chips_crop.png`.
