# Spatial Ruler — round 1 acceptance test (milestone 1)

Test agent, 2026-10-02 01:55 – 03:12 (device clock = host clock). Build under test: the debug APK that was already
installed (`io.embodit.spatialruler`, not rebuilt). Device: `emulator-5556`, AVD `PICO_6.1_Ruler` (checked before
every device call; `emulator-5554` / `-5558` were never addressed). Input: real pinches through
`tools/harness/emu.sh` (about 230 clicks), debug broadcasts only for setup and assertions. Expected behaviour is taken
from `design/design-doc.md` §3; milestone-2 items were not judged.

Scripts: `tools/test/*.py` (`rt.py` = library, one script per test letter, `do.py` = single real-pinch steps).
Raw results: `tools/test/out/`. Screenshots: `.scratch/shots/round1/` (curated 01–14) and `…/round1/evidence/`.
The logcat ring buffer rotated during the session, so the log lines quoted below are the ones captured at test time.

State left behind: app running, scene cleared, unit mm, language English, snap on (as found, minus the two smoke pins
of the dev, which test A cleared). Camera back at pos (-0.002, 1.468, 0.017), q(-0.058, 0, 0, 0.998)
(start: (0.000, 1.465, 0.000), same q). Emulator throttled again (`fast off`), no emulator setting touched.

## 1 · Verdict A – P

| | Area | Verdict | Notes |
|---|---|---|---|
| A | Cold start | **pass** | starting → idle-scanning → idle, no ANR / crash. D8, D9 |
| B | Tap – tap | **pass** | pins, tape, plate, body "Last · ±3 cm"; tape length = pin distance |
| C | Placement accuracy | **pass** | 11 unsnapped taps: pin within 2 px of the clicked pixel; app-side conversion ≤ 0.08 mm. Q1 (corner snap) |
| D | Any two pins | **pass** | chips = geometric distances, chip → tape, pin → pin, duplicate refused, head pin again = cancel |
| E | Pin tag | **pass with defects** | badge, 1 mm steps, readings, Remove, Undo all correct on a pin whose tag sat above it; D2, D4, D10 |
| F | Tape tag | **fail** | works for a wall-to-wall tape and from a low viewpoint; for a floor tape seen standing the tag is under the floor and cannot be pressed (D2) |
| G | Undo | **pass** | 8 actions undone one by one, model equal to every earlier snapshot; Undo on an empty stack is a no-op (key disabled) |
| H | Units | **pass** | m / cm / mm / ft·in on body, plates, chips; edge cases correct; D3 concerns the tolerance value, not the format |
| I | Language | **pass** | zh / en / System switch at runtime, all strings follow; one clipped English line (D6) |
| J | Clear all | **pass** | Cancel changes nothing; Clear all removes 13 pins, 13 tapes, 13 anchors; relaunch restores 0 |
| K | Persistence | **pass** | `RESTORE pins=3 tapes=2`, same ids, lengths identical, ft·in + 中文 kept, snackbar shown |
| L | Failure paths | **pass** | unsaved pin + Retry → saved; backstop tap → "No surface detected here", no pin. D11 |
| M | Viewpoints | **pass with defects** | plates upright, facing, on the constant-angular rule; body fixed lower-left; tag placement is the weak point (D2, D4, D5), Q2, Q3 |
| N | Pointer blocking | **fail** | D1 (body upper half let pinches through for part of one session), D2 (tags behind a surface), D13 (body below a surface). Pins / plates / chips take the pinch correctly; double tap creates one pin |
| O | Load 12 + 8 | **pass with defects** | all 8 plates shown, no log errors; D5 (overlapping plates), D7 (13th tape has no plate) |
| P | Exit / relaunch | **pass** | `STATE exited`, Stage disposed, process kept; relaunch restores in 1.6 s |

## 2 · Product defects

Severity: S1 blocks the core flow / wrong measurement, S2 major, S3 minor, S4 cosmetic.

### D1 · S1 (intermittent, seen once, not reproduced) — the upper part of the body stops taking pinches; they land on whatever is behind it
- **Seen**: first app session (launched 01:57). Body keys worked with real pinches until 02:21 (`PTR panel=body ev=Press
  x=601.0 y=81.0`, `UI more`). From the next body press at about 02:31 until that process was restarted a few minutes later, every pinch on
  the top ~40 % of the body panel (panel y < ~110 of 272: readout window, Undo, Snap, More) and on the upper housing
  (top rail, both end bumpers) went through. The lower part kept working (`unit_cm` → `UNIT cm`; panel point (340,140)
  and (600,120) → `PTR panel=body …`; bottom rail (340,284) → `TAP other target=body`).
- **Actual** (real pinches, head at the start pose):
  - readout window (130,80) → `TAP surface plane=FLOOR… / PIN add id=p47 … / TAPE add id=t19` ; (60,40) → `PIN add p48`, `TAPE add t20`; (200,110) → `PIN add p49`
  - top rail (340,-20) → `PIN add id=p46`; bumpers (-13,136), (693,136) → `PIN add p50`, `PIN add p51`, `TAPE add t22`
  - More (600,80) → `TAP surface … PIN add id=p52`; again → `TAP pin id=p52 … STATE pin-selected`
  - Snap (480,80) → `PTR panel=plate11 ev=Press x=338.0 y=108.0 / UI plate:t22 / STATE tape-selected` (a plate 2 m behind the body)
  - Undo (360,80) → `PTR panel=plate11 … UI plate:t22`
  Result: 9 stray pins and 5 stray tapes hidden behind the body, Undo / Snap / More unreachable, so the user can neither
  undo them nor open Clear all. Screenshots `evidence/n01_body_before.png`, `evidence/n02_body_after.png`.
- **Expected**: design §3 实物 / 尺身 and the task's rule "a pinch on the body housing or any panel must not drop a pin
  on the surface behind it"; AGENTS.md runtime facts ("the body's collider swallows pinches on the housing").
- **What it is not** (each tried in a fresh session, body keys kept working): more than 12 tapes (pool exhausted),
  camera rotation by right-drag, camera translation by keys, choosing a language in the native sub-menu, content lying
  behind the body, a pin tag and a tape tag shown and closed at the close pose, the body dipped below the floor and back
  (D13). After a relaunch the readout window, top rail and bumpers blocked correctly (`PTR panel=body ev=Press
  x=129.0 y=80.0`, `TAP other target=body`) and stayed correct for the remaining 25 minutes.
- **Only things done between the last good and the first bad press**: harness camera moves to three poses (down to
  0.19 m above the floor, pitch −40°, yaw 105°, 4 m away) and a pin tag (0.70 m from the head) and a tape tag (0.57 m)
  shown and closed at the close pose. The symptom matches the "dead pointer ray" described in AGENTS.md (finding 2).
  Not isolated further (time box).

### D2 · S2 — pin tag and tape tag are placed through / below the surface, so the surface (or a plate in front) takes the pinch
- **Repro a (tape tag; the one floor tape tried from the standing pose, t17)**: two pins on the floor 1.5 – 2.8 m ahead, tape between them, head
  at (0, 1.465, 0). Pinch the plate → `STATE tape-selected`; `bounds` → `tapeTag pos (1.040, 0.162, -2.177) scale 3.244
  size 528x368` (floor is y = 0.4006: the whole tag hangs below the floor). Pinch "Retract this tape" →
  `TAP surface plane=FLOOR:dc233687 … / STATE idle`: the tape is deselected, nothing is retracted; × is equally unreachable.
- **Repro b (pin tag)**: pins at about (0.21,-1.32), (0.92,-1.59), (0.30,-2.72) with tapes 1–2 and 3–1; pinch pin 2 →
  `pinTag pos (0.523, 0.460, -1.588) scale 2.623 size 528x328` (left of the pin, centre 6 cm above the floor, lower half
  below it). Pinch "Fine-tune" 3.2 s later at 60 fps → `TAP surface plane=FLOOR… / PIN add id=p58 … / TAPE add id=t31
  a=p56 b=p58`: a stray pin and tape instead of the fine-tune panel. Remove is in the same row.
- **Expected**: §3 Surface sizing — 钉牌 "选中钉的上方，离面 3 cm … 钉靠近视野上缘时改到下方"; 尺牌 "选中读数牌的下方；
  下方放不下时改到上方". Below the floor is "no room".
- **Works**: tag above the pin (first pin of a pair: `pinTag pos (0.211, 0.684, -1.306)`); tape tag of a wall-to-wall
  tape; tape tag from 0.5 m above the floor (it flips above the plate there).
- Evidence: `07_tape_tag_floor_tape.png`, `evidence/g02_pintag_low.png` (plate "75.2 cm" and the tapes also cover
  the badge and both buttons), `evidence/m1_close_pintag.png`.

### D3 · S2 — tolerance above 5 cm is shown as "±5 cm"
- **Repro**: two system-pointer pins both more than about 3.5 m away, tape between them.
- **Actual**: `dump`: p40 `sigma_mm 57.39 tol_mm 50`, p41 `52.47 → 50`, p42 `66.78 → 50`; tapes t14 (4.472 m) root-sum-square
  77.8 mm → `±5 cm`, t15 67.9 mm → `±5 cm`, t18 61.1 mm → `±5 cm`.
- **Expected**: §3 "怎么知道量得多准": "两端平方和开根，**向上**取到 2、3、5、8 mm、1、1.5、2、3、5 cm". Rounding 78 mm down
  to 5 cm understates the uncertainty on exactly the tapes marked ▲ Far. The ladder has no step above 5 cm: needs a
  design decision (more steps, or "> 5 cm").

### D4 · S3 — tags and plates cover each other
- Fine-tune open: the tag covers the plates of the tapes connected to the pin being moved, and the body readout shows
  "Pull to next point / — —", so the changing reading (the focus of the tuning state, §3 State graph 微调) cannot be
  seen. `06_pin_tag_finetune_open.png`.
- A plate nearer to the viewer lies over the tag's badge and buttons (`09_zh_pin_tag_and_body.png`: plate "742 mm" over
  "删除"). Not pressed in that layout; by depth the plate would take the pinch.
- The tape tag is crossed by its own tape (`evidence/f02_tape_tag_wall.png`). Partly listed as a known gap in AGENTS.md.

### D5 · S3 — reading plates overlap, no staggering
- Far view (4 m): plate "1378 mm" behind "1237 mm" (`14_far_view_4m.png`); 13 tapes: "169.1 cm" over "137.8 cm"
  (`evidence/j01_dialog_en.png`); parallel tapes 14 cm apart: every plate covers the next tape (`evidence/h2_edges_m.png`).
- **Expected**: §3 Layout "互相遮挡时沿各自的尺带错开（中点 → 36% / 64% → 24% / 76%）". The ">8 plates → value line only" rule is implemented.

### D6 · S3 — English tape tag: end-grade line clipped
"Start ○ Coarse · Far End ○ Coarse · Fa" is cut at the right edge when both ends are far (`evidence/f02_tape_tag_wall.png`).
The Chinese line fits (`evidence/i06_zh_tapetag.png`). §3 文案 尺牌.

### D7 · S3 — the 13th tape has no reading plate
`dump`: `t23 plate_slot -1` (pool `plate0..11`), no message. Beyond the designed 1–8 tapes, design gap 5 (§6); the
body readout still shows it as "Last".

### D8 · S3 — nothing is visible during `starting` on a cold start at idle frame rate
`STATE starting` +3.4 s, `SCENE attached build_ms=8919` +12.4 s (6823 ms in a second run); the body only appears after
that, so for 9 – 12 s the user sees plain passthrough (`evidence/a1_start_02.png`). §3 State graph: 准备中 "约 3 s",
focus "尺身读数窗的转圈". At 60 fps the build takes 0.6 – 0.9 s, so this may be emulator-only.

### D9 · S4 — the body shows defaults before the saved preferences
First frames after a start: "Getting ready…" in English with segment "m" selected although 中文 and ft·in are stored
(`evidence/k02_restore_0.png`; `evidence/a1_start_05.png` shows "m" with mm stored).

### D10 · S4 — stepper value wraps ("+2" over "mm") — known gap in AGENTS.md (`06_pin_tag_finetune_open.png`).

### D11 · S4 — "Retry" on the unsaved snackbar is a dark grey button
§3 Visual tokens: `interaction` #FFC72C is the "「重试」按钮底", content `tapeInk`. `11_snackbar_unsaved_retry.png`.

### D12 · S4 — a tape seen end-on runs across the tolerance line of its own plate ("±3 [tape] m", `evidence/m0_plates.jpg`).

### D13 · S2 — with the head low over a surface the body sinks below it; body keys then set pins
- **Repro**: head 0.19 m above the floor, pitch −9° (harness pose (0.236, 0.589, -0.484)); `bounds` → `body pos (0.070, 0.302,
  -0.950)`, i.e. 10 cm under the floor (y = 0.4006). Real pinches: Snap → `TAP surface plane=FLOOR… / PIN add id=p83`;
  `unit_cm` → `PIN add id=p84 / TAPE add id=t46 len_m=0.0632`; `unit_mm` → `PIN add id=p85`. No body key can be pressed there.
- At 0.52 m above the floor with pitch −40° the body centre is at y = 0.426 and the keys still work; lower than that they do not.
  The same geometry applies to a table top when the user bends over it (body is 0.58 m away, 23° below the view axis).
- **Expected**: the body is the one place for Undo / units / menu (§3 尺身); the design gives no rule for keeping it in front
  of surfaces, so this also needs a design decision. Back at the standing pose the keys work again.

## 3 · Design questions (the build follows the letter of the design)

- **Q1 · corner snap in the pointer channel.** Tap on the cabinet top at 5.9 m (`TAP surface plane=CABINET:5304833b
  stage=(0.6884, 0.9312, -5.8822)`) → `PIN add id=p43 pos=(0.6943, 0.9205, -5.4467) plane=CABINET:95895b72 snapped=corner`:
  the pin jumped to a corner of another plane 0.78° away in view but 0.44 m nearer and 18 px from the tapped pixel. §3 吸附
  allows it (0.9° radius, seat not hidden by the hit surface), but this channel has no reticle to announce a snap. With
  snap on, a far surface seen at a grazing angle is always within 0.6° of an edge.
- **Q2 · tolerance line beyond 3 m.** Digits measure 22 – 25 px (1.0°) between 0.45 and 3 m, 17 px at 4.3 m, 12 px at
  5.8 m; the "▲ ±5 cm" line is 5 – 6 px there and unreadable (`04_tape_plate_far_crop.png`). §1 asks for readings readable to 5 m.
- **Q3 · upright plates seen from above.** At 0.62 m looking down 47° the plate is squashed to 243×56 px, digits 16 px
  (`03_tape_plate_close.png`).
- Dimming of unrelated tapes while a pin is selected (§3 Layout, 55 %) is not visible in `05_pin_selected_candidates.png`
  (judged by eye only).

## 4 · Accuracy (C)

Head (0, 1.465, 0), pitch −6.61°. "model" = pin position vs. the camera-model ray through the clicked pixel on the
logged plane; "app" = pin vs. the logged collider hit put back on the plane along the viewing ray (the app's own step);
"reproj" = camera-model projection of the pin minus the clicked pixel. Method resolution: clicks land within ~2 px
(TESTING.md), `POSE` prints 3 decimals (±0.5 mm, ±0.03°), so "model" is good to about 2 px × the mm/px column, and
several times that along a surface seen at a grazing angle.

| Pin | Surface | Range m | Clicked px | model mm | app mm | reproj px | 1 px = mm | Grade / snap / far |
|---|---|---|---|---|---|---|---|---|
| p34 | floor | 1.70 | 1300,1900 | 1.7 | 0.02 | +0.3, +1.1 | 1.3 | coarse / none / – |
| p35 | floor | 2.10 | 1800,1750 | 3.0 | 0.05 | −1.2, +0.8 | 1.6 | coarse / none / – |
| p36 | floor | 2.90 | 1233,1425 | 1.5 | 0.06 | −0.7, 0.0 | 2.3 | coarse / none / – |
| p37 | floor | 3.16 | 1537,1405 | 3.1 | 0.02 | +0.2, −0.4 | 2.5 | coarse / none / **Far** |
| p38 | bed top | 3.01 | 127,1255 | 6.5 | 0.04 | −0.4, −0.7 | 2.3 | coarse / none / **Far** |
| p39 | bed platform | 2.64 | 499,1420 | 4.4 | 0.04 | +0.9, +0.7 | 2.1 | coarse / none / – |
| p40 | white wall | 5.17 | 466,597 | 7.4 | 0.05 | +1.8, −1.1 | 4.0 | coarse / none / **Far** |
| p41 | teal wall | 4.56 | 1752,1015 | 2.4 | 0.05 | −0.8, 0.0 | 3.5 | coarse / none / **Far** |
| p42 | niche back wall | 6.35 | 1040,791 | 10.5 | 0.08 | +1.3, −1.5 | 4.9 | coarse / none / **Far** |
| p43 | cabinet top, snap on | 5.52 | 1237,1047 | (462.6) | – | +13.8, +11.7 | 4.3 | coarse / **corner** / Far — snapped exactly onto the plane vertex, see Q1 |
| p44 | ceiling, snap off | 5.49 | 1225,317 | 10.6 | 0.04 | +0.6, −1.4 | 4.3 | coarse / none / **Far** |
| p45 | shelf-unit top, snap off (5° grazing) | 6.18 | 1062,1043 | 33.8 | 0.03 | −0.3, −0.6 | 4.8 | coarse / none / **Far** |

- Every unsnapped pin reprojects within 1.8 px of the clicked pixel; in the screenshot each collar is centred on the click
  (`evidence/c_reproj1_sheet.png`, cyan cross = click). All pins lie on their plane (off-plane ≤ 0.07 mm).
- More than 3 m: the pin is set, `far=true`, sigma doubled (p37 at 3.16 m: 41.3 mm vs. p36 at 2.90 m: 19.6 mm), the plate
  shows an orange "▲ ±5 cm", the pin tag badge "○ Coarse · …", the tape tag "Coarse · Far". No table within reach was
  tapped (tables are behind the start pose); no wall nearer than 3 m was tapped.
- Tolerance model checked against §3: coarse 8 mm + 0.4 %, fine 3 mm + 0.1 %, far ×2, corner 6 mm ×2, root-sum-square,
  ladder up to 5 cm correct (e.g. 22.1 → 3 cm, 6.9 → 8 mm, 0.315″ → ±0.4″).

## 5 · Other measurements

- **A**: cold start at idle fps: starting +3.4 s, scene +12.4 s, idle-scanning +13.3 s, `PLANES n=57` +16.3 s (all 57
  in one batch 3.0 s after scanning begins), idle +22.3 s. At 60 fps: scene +4.1 s, planes +7.7 s, idle +13.9 s. Body
  context lines seen: Getting ready… / Scanning · 0 / Scanning · 57 / Ready to pin / Pull to next point / Last · ±x /
  Selected · ±x. `ANR|FATAL|AndroidRuntime`: 0 lines in the four starts that were checked.
- **E**: seven real stepper presses: each moved the pin 1.00 – 1.02 mm (+u = viewer's right, −v = nearer), grade coarse →
  fine (badge "● Fine · ±5 mm"), connected tapes always equal to the geometric distance. Remove: `TAPE remove ×2
  reason=pin`, `PIN remove`, `ANCHOR removed`; Undo: `UNDO depth=19 pins=10 tapes=7`, pin back with its offset.
- **H**: 0.3030 m → `11.9″`; 0.3040 → `1′ 0.0″`; 0.6095 → `2′ 0.0″`; 0.9996 → `1.000 m` / `100.0 cm` / `1000 mm`;
  0.9994 → `0.999 m`; 0.05 → `2.0″`; ±8 mm ↔ `±0.4″`, ±3 cm ↔ `±1.2″`; last digit faded only when the tolerance ≥ 1 cm.
- **M** (`tools/test/out/m_*.json`): plate scale = clamp(distance, 0.45, 3) / 0.8 within 0.4 % at 0.62 – 5.84 m in four
  poses; yaw error ≤ 0.2°, tilt 0°, roll 0°; body always 18.0° left, 23.0° down, 0.58 m (design 18° / 22° / 0.6 m);
  tapes turn their printed face to the viewer in all poses.
- **N**: double tap 0.6 s apart on the floor → `PIN add p59`, then `TAP pin id=p59 … STATE idle`: one pin.
- **O**: taps kept registering as spatial taps (60 fps gate of the harness) with 12 pins + 8 tapes and with 18 + 13;
  no W/E line of tag SpatialRuler. No frame-time measurement was made.
- **P / K**: warm relaunch after Exit: `SCENE attached build_ms=634`, `RESTORE pins=3 tapes=2` at +1.6 s.

## 6 · Not tested, and harness limitations

Not tested (product): tracking lost, capability blocked, pins "not in this space", default unit by locale on first run
(needs a data wipe), plates nearer than 0.45 m, a table within reach, a wall nearer than 3 m, more than 20 undo steps,
drag (logs only in M1). Offset persistence was seen across one relaunch (t11 0.7415 m before and after) but not in test K
itself (the nudge step there did not run, see below).

Harness limitations (not product defects):
- One click of ~230 was not sampled (02:53: the posted focus-in only reached 27 fps); it silently cancelled a step of K.
- `POSE` has 3 decimals: camera-model predictions are limited to about 1 – 2 px.
- The native More menu has no pose: its rows were aimed from a screenshot (head-locked, so the pixels were reused).
- Snackbars last 2 – 3 s: only burst screenshots catch them; at idle fps the picture lags the log by seconds.
- Two clicks cannot be closer than about 0.6 s; a pixel "with no surface" had to be searched (1 % of the view).
- G: nudge / remove / retract were issued through `ui …` because of D2; the eight Undo presses were real.

## 7 · Curated screenshots (`.scratch/shots/round1/`)

`01_empty_start` · `02_first_pin_tape_head` (crop) · `03_tape_plate_close` · `04_tape_plate_far_crop` ·
`05_pin_selected_candidates` · `06_pin_tag_finetune_open` (crop) · `07_tape_tag_floor_tape` (crop, D2) ·
`08_more_menu_language_submenu` (crop) · `09_zh_pin_tag_and_body` · `10_zh_clear_dialog` ·
`11_snackbar_unsaved_retry` · `12_load_12pins_8tapes` · `13_oblique_low_view` · `14_far_view_4m`.
More in `evidence/` (zh menu, zh tape tag, zh snackbar, English dialog, units, edge cases, restore snackbar, body
pass-through before / after, reprojection sheet).

## 8 · For the next round

1. `POSE` and `PANEL` with 5 decimals, and the eye used for the pointer ray.
2. A `rig` command: every pooled panel slot with enabled / parked / bound state and world pose, plus the body collider's
   pose and size (needed to chase D1); log a line whenever a slot is parked or released.
3. `bounds` for the native menu rows in screen or Stage coordinates.
4. `TAP` lines with the pressed pixel and, for a surface tap, the entity that was nearest but rejected.
5. Snackbar text and remaining time in `dump`; a debug switch for long snackbars.
6. A `fps` or frame-time line from the app, and hooks for tracking lost / blocked / pins elsewhere.
7. A harness `clicks X1 Y1 X2 Y2 …` action in one process (fast double taps), and a non-zero exit when the 50 fps gate fails.
