# Spatial Ruler — round 2 test (rounds 2a + 2b)

Test agent, 2026-10-02 15:11 – 16:58 (device clock = host clock). Build under test: the debug APK installed at 15:04:57
(not rebuilt). Device `emulator-5556`, AVD `PICO_6.1_Ruler` (checked before every device call; `-5554` / `-5558` never
addressed). Input: real pinches / drags / controller clicks through `tools/harness/emu.sh` + `emu.ps1` (about 150 host
actions), debug broadcasts for setup, synthetic channels and assertions. Expected behaviour: `design/design-doc.md` §3
and `docs/review/design-review-1.md` (addendum R1 – R11).

Scripts (new, `tools/test/`): `r2.py` (step runner with hijack guard, primed drags), `r2_tapmap.py`, `r2_pull.py`,
`r2_ctrl.py`, `r2_reticle.py`, `r2_rot.py`, `r2_aimpose.py`, `r2_synth.py`, `r2_load.py`, `pdrag.ps1`. Raw output:
`tools/test/out/round2/` (`app_log.txt` = every `SpatialRuler` line of the session, `warn_log.txt` = all tags ≥ W,
`ctrl_acc.json`, `synth.json`, `hijacks.jsonl`). Screenshots: `.scratch/shots/round2/` (curated 01–16) and `…/round2/evidence/`.

**Read H1 first** (harness section): the real mouse cursor rests on the Ruler emulator window, which is the foreground
window, and the emulator takes some presses at the real cursor position. Every result below was checked against the
log line of the press; hijacked presses were caught and repeated.

State left behind: app running, scene cleared, unit cm, language English, snap on, calib catcher off, **eye mode**
(body-key pinch `UI snap` verified after the switch back). Camera at pos (0, 1.636, 0), yaw 0, pitch −0.1°
(found: (0, 1.65, 0) level). No emulator setting other than the interaction mode was touched.

## 1 · Verdict A – G

| | Area | Verdict | Notes |
|---|---|---|---|
| A | Regression of round 1 | **pass with defects** | cold start, tap–tap, chips, pin tag + steppers, tape tag + Retract (floor tape seen standing), undo, units, language (native submenu), clear all (dialog cancel + confirm), unsaved + Retry, persistence, 12 / 8 load, 20-tape limit, body housing blocks (`TAP other target=body`). R2-2, R2-3 |
| B | Round-1 defects | **pass** (11 fixed, D5 partial, D1 not reproduced) | table §3 |
| C | Press-and-pull | **pass** | from a surface, from an existing pin, one undo, true-up 4 : 1 (R2-4 cap at 50 mm) |
| D | Controller mode | **pass with limits** | reticle, steady ring, snaps, far, live blade, `via=controller`, one press = one action; accuracy §4. Body-key clicks and two reticle states not testable (H1) |
| E | Synthetic channels | **pass with defect** | hand ray, steady on/off, half press 4 : 1 (0.2525), contact; `hand_lost` gives no snackbar (R2-1) |
| F | Visual states | **partial** | 16 curated captures; missing list in §5 |
| G | Stability | **pass** | 0 ANR / FATAL, 0 `RIG violation` in 6 300 app lines, 0 W/E lines of the app; app 60.0 fps with 12 pins / 8 tapes; PSS 265.1 → 264.7 MB after 3 × (20 tapes + clear) |

## 2 · Defects found in round 2

Severity: S1 blocks the core flow / wrong measurement, S2 major, S3 minor, S4 cosmetic.

### R2-1 · S3 — no "Raise a hand or pick up a controller" snackbar after the tracked source is lost
- **Repro**: `source hand`, `pointer …`, trigger (pins set `via=hand`), then `hand_lost`; wait 4.5 s and 30 s.
- **Expected**: §3 State graph, overlay 等待输入 "3 秒没有手或手柄：准星淡出，提示条提醒"; §3 文案 提示条
  "抬起手或拿起手柄继续 / Raise a hand or pick up a controller to continue"; task spec "`hand_lost` → the designed snackbar after 3 s".
- **Actual**: `16:33:00.517 POINTER lost`, `SRC active=system`, no `SNACK` line; `dump` `snack=None` 4.5 s and 30 s later.
- Evidence: `tools/test/out/round2/app_log.txt` 16:33:00 – 16:33:35.

### R2-2 · S3 (design question) — a pinch outside the open More menu closes it **and** acts on what is behind it
- **Repro** (zh, native menu): More → pinch just left of the narrower zh popup (fb 721, 1420).
- **Actual**: `STATE idle` (menu closed), then `TAP surface plane=FLOOR… / PIN add id=p189`; the next action pulled a stray
  tape t109 from it. The user wanted to close the menu and got a pin.
- **Expected**: §3 State graph 更多 "选择或点外面退出" — the design does not say whether the dismissing tap is consumed.
  Recommendation: consume it (dismiss only), as native menus do. Evidence: `app_log.txt` 15:54:23.

### R2-3 · S2 if confirmed (root cause not isolated) — native menu row "Exit Spatial Ruler" only dismisses the menu
- **Repro**: More (real pinch, `UI more`) → real pinch on the Exit row (fb 760, 1484–1495, from screenshot `evidence/r2_menu.png`).
  3 attempts (one single click, two primed `clicks` sequences with 1.5 s and 2.5 s gap after More).
- **Actual**: the press reaches the popup window (`SELECT_ENTERED touch down 1998:…LaunchActivity#315`), the app logs
  `STATE idle` only — no `UI menu_exit`. "Clear all…" behaved the same in 1 of 3 attempts (15:57:58, popup window
  `#277`), worked in 2 (`UI menu_clear`). "Language" worked 1 / 1. Exit through `ui menu_exit` works.
- **Confound**: H1 (real-cursor hijack) can produce exactly this (a press taken outside the popup dismisses it). The
  primed sequences should have excluded H1 for the Exit press, so please re-check on a clean harness (cursor off the
  window) before treating it as product. Round 1 passed "Exit" (P).

### R2-4 · S4 (design question) — the true-up drag stops at an offset of 50 mm
- **Repro**: pin tag → Fine-tune → real drag on the pin, hand travel 0.237 m.
- **Actual**: `PIN true … du=` follows hand × 0.25 (4 : 1 confirmed: du 14 / 23 / 34 / 45 for hand 57 / 98 / 137 / 183 mm),
  then stays at `du=50` while the hand moves on; nothing tells the user. §3 says only "毫米到几厘米". Decide whether 5 cm
  is the limit and whether it needs a cue.

### Observations (not defects)
- O1 contact channel: the pin is set at the tool tip itself, 50 mm above the floor (`CONTACT … off_mm=50.0`,
  `PIN add … pos=(0.7620, 0.4506, -0.5652) plane=FLOOR grade=contact`). Matches "工具尖就是真值" and TESTING.md; with a
  12 cm capture radius a pin can float up to 12 cm off the surface. Design may want a tighter radius for the commit.
- O2 hand source ignores `trigger half` (no `AIM fine=on`): consistent with "hands pinch, controllers half-press".
- O3 plates overlap with 20 tapes on 1.2 m² (`15_…sheet.jpg`, right half); with 4 – 8 tapes none overlapped. Known open point.
- O4 a ~48 px (2°) drag that starts on a pin 3 m away delivered no drag callback at all (system `SELECT_ENTERED pin:p182`,
  2 s pinch, no `DRAG start`) twice; a 98 px drag on the same pin worked. System drag threshold, not the app.

## 3 · Round-1 defect status

| ID | Ruling | Status | Evidence |
|---|---|---|---|
| D1 body upper half lets pinches through | — | **not reproduced** | 0 `RIG violation` lines all session (low pose, tags shown/closed, controller mode, 20 tapes); every body key pressed with real pinches at the standing and the low pose. The full round-1 sequence (far pose 4 m + tags at 0.6 m) was only partly repeated (time) |
| D2 tags through / below a surface | R3 | **fixed** (floor; wall not tried) | floor tape seen standing: `TAG tape slot=NW range=2.204 anchor_range=2.588`, tag centre y 0.959 > floor 0.401; real pinch "Retract this tape" → `TAPE remove reason=retract` (`03_…`). Pin tags E / NE / SW above the floor, leader when pulled in (`11_…`) |
| D3 tolerance > 5 cm shown ±5 cm | R1 | **fixed** | pins σ 64.2 / 51.3 mm → tol 80; tape RSS 82 mm → "±10 cm" / "±4″"; two dim digits 83**95** mm, 8.3**95** m, 83**9.5** cm (`14_…`). "> 50 cm" not reachable in this room |
| D4 tag covers plates / readout "— —" | R7 | **fixed** | body "Tuning · ±5 cm 114.**3** cm" while fine-tune open, tag in slot E clear of the plate (`04_…`) |
| D5 plates overlap | F7 / R9 | **partial** | no overlap with 4 – 8 tapes; overlaps at 20 tapes in 1.2 m² (O3) |
| D6 tape tag grade line clipped | R10 | **fixed** | "Start ○ Coarse / End ○ Coarse · Far" on two lines (`03_…`), zh "起 ● 细瞄 / 止 ○ 粗瞄" (`10_…`) |
| D7 13th tape without plate | F6 | **fixed** | 20 slots; 21st → `TAPE refused reason=limit max=20`, `SNACK tape_limit` "Limit of 20 tapes reached / Retract one before pulling another." |
| D8 nothing visible during start-up | R8 | **fixed** | body drawn 0.3 s after `STATE starting` (`SCENE body panel drawn ms=98`), housing + spinner (`01_…`, `02_…`) |
| D9 defaults before preferences | R8 | **fixed** | zh + ft·in stored: first body frame "正在准备…" with ft·in selected, Undo disabled; no English / "m" frame in 9 frames (spinner-only phase too short to catch) |
| D10 stepper value wraps | F2 | **fixed** | value "0" / signed integer, row label "↔ L / R · 1 mm / step" (`04_…`) |
| D11 Retry dark | R11 | **fixed** | "重试" yellow fill, dark label (`13_…`); real pinch → `ANCHOR saved`, `SNACK saved` |
| D12 plate crossed by its own blade | R9 | **fixed** (as observed) | plates beside the blade on a stem in every layout up to 8 tapes |
| D13 body sinks below the floor | R2 | **fixed** | head 0.19 m above the floor (pos y 0.592): `RIG body center y=0.494 rest_offset=(0, 0.207, 0)`; real pinches unit_cm, unit_mm, snap × 2, undo, more → all `UI …`, no `PIN add` (`16_…`) |

## 4 · Controller-mode accuracy (D)

Controller mode, ray from the left eye through the cursor. Because of H1 the trigger press is taken at the **real**
cursor position, so the head was rotated (`r2_rot.py`) to put each target under it and the click was sent at that pixel
(fb ≈ 1297, 1990; the real cursor is known only to ±2 – 3 fb px: Windows DPI rounding). "eye-ray mm" = pin vs. the
camera-model ray through (1297, 1990) on the logged plane; "reproj" = pin projected minus (1297, 1990).

| Pin | Head yaw / pitch | Surface | Range m | eye-ray mm | reproj px | 1 px = mm | Reticle before press | Commit |
|---|---|---|---|---|---|---|---|---|
| p192 | −35 / 0 | floor | 2.18 | 7.4 | −5.4, +0.2 | 1.69 | steady | coarse, `via=controller`, system tap `ignored reason=tracked-source` |
| p193 | −35 / +15 | floor | 3.63 | 12.5 | −5.4, +0.2 | 2.82 | far | coarse, far |
| p194 | −35 / −15 | floor | 1.64 | 5.6 | −5.4, +0.2 | 1.28 | steady | coarse |
| p195 | 0 / +10 | bed platform | 2.51 | 8.6 | −5.4, +0.2 | 1.95 | steady | coarse |
| p196 | −70 / +35 | teal wall | 3.35 | 11.5 | −5.4, +0.2 | 2.60 | far | coarse, far |
| p197 | −50 / +5 | floor | 2.51 | 8.6 | −5.4, +0.2 | 1.95 | steady | coarse |

- The six pins reproject to the same pixel within 0.1 px: the whole "eye-ray" error is one constant 5.4 px offset, i.e.
  the uncertainty of where the real cursor sits, not scatter of the app. Removing it, the error is < 0.2 mm.
- In every case `TAP pointer … stage=` equals the last `RETICLE … stage=` exactly: what the reticle shows is what commits.
- Grade coarse although the reticle was `steady` 1 s before: the harness click posts a 4-px move burst 0–400 ms before the
  press, which resets stability (H4). One earlier click without that burst committed `steady=true grade=fine` (p191).
- One press, one action: click on the armed pin → `TAP pointer` (reticle `snap=pin`) cancels the head, system tap
  `TAP pin … ignored reason=tracked-commit`; click on a plate → `RETICLE state=blocked:panel:plate2`,
  `AIM commit skipped reason=panel:plate2`, `STATE tape-selected` once.
- Reticle (moves only, `07_…`, `08_…`): aim → `steady` after ~0.36 s (`stable=1.00`, green "Steady"); `snap=corner` on the bed
  platform corner (seat exact: (1.0178, 0.5821, −1.6392)), `snap=edge`, `snap=pin`, `state=far` "Over 3 m · less precise";
  live blade with segment ticks and a counting plate from the armed pin. Aiming at the wall's base gave `snap=edge`
  (priority edge > level, as designed).

## 5 · Not tested / not captured, and why

- Body-key clicks in controller mode: the trigger press goes to the real cursor (H1), which never lies on the head-locked body.
- Reticle states `none` (no surface), `plumb`, `level`, `fine` and `contact` as screenshots (fine and contact were seen in
  the log with the synthetic pointer: `RETICLE state=fine`, `state=contact`); chip click in controller mode.
- F: pin + blade close-up at 0.5 m, far view from 4 m as such, oblique view, pin-tag badges "● Fine" / "◆ Contact"
  (grades checked in `dump`), standing tape on a wall, tape tag of a wall tape.
- A: rapid double taps, backstop tap ("No surface detected here"), tracking lost / blocked / elsewhere hooks (round 1 ok).
- D1: the full round-1 sequence with the far 4 m pose and tags at 0.6 m.
- Exit with a real pinch (R2-3); exit/relaunch was run with `ui menu_exit` → `STATE exited`, relaunch `RESTORE pins=3 tapes=2`, `SNACK restored`.

## 6 · Harness issues (not product defects)

- **H1 (blocking, needs the user): real-cursor hijack.** The Ruler emulator window is the foreground window and the real
  mouse cursor rests on it (logical 685, 1021 → physical 1199, 1787 → fb ≈ 1296, 1990). Eye mode: the first posted press
  after each 60 fps re-arm is taken at the real cursor (3 / 3 with `clicks`; about half of single clicks; the pin always
  landed at Stage (0.4564, 0.4036, −0.3800) in the start pose, identical `view_px`). Controller mode: 18 of 19 trigger
  presses aimed at the real cursor. Workaround used: a head-locked catcher panel at the cursor's direction
  (`emu.sh cmd calib 0.6,1,80,0.069,-0.425 on`, presses log `CALIB ev=Press`), retry in `r2.py`, primed drags
  (`tools/test/pdrag.ps1`: catcher click + drag in one PowerShell process), head rotation for controller placements.
  Fix: move the real cursor off the emulator window (or un-focus it) before the next round.
- H2 screenshots lag the log by 1 – 3 s at 60 fps (first two frames of a pull showed the old scene).
- H3 a drag shorter than ~2° may not start at all (O4).
- H4 the click's move burst (x−4 … x within 400 ms) resets the controller stability ring; a controller test wants a
  press without the burst.
- H5 with the head 0.59 m high the bottom fifth of the console frame renders solid blue (`16_…`): emulator, not app.
- H6 `cam.py` assumes "drag down = pitch up"; it is the opposite (it self-corrects, but overshoots). `r2_rot.py` uses the right sign.
- The guest log carries `SPR-HandTrackingDevice … isHandTracking:true palmPos:0 0 0` (the string from the AGENTS.md
  incident), but eye-mode pinches reached the app until the end (last real pinch `PTR panel=dialogPanel` / `UI dialog_confirm`
  at 16:50:56, body keys after the switch back from controller mode): **no restart needed**.

## 7 · Curated screenshots (`.scratch/shots/round2/`)

`01_startup_sequence_zh_ftin_sheet` · `02_startup_body_first_getting_ready_zh` · `03_tape_tag_floor_tape_standing_R3_R10` ·
`04_pin_tag_finetune_body_tuning_R7_F2` · `05_selected_pin_candidates_chips` · `06_press_and_pull_live_blade_plate` ·
`07_reticle_states_controller_sheet` · `08_controller_mode_reticle_corner_live_blade` · `09_menu_language_submenu` ·
`10_zh_tape_tag_body` · `11_zh_pin_tag_leader_chip` · `12_zh_clear_dialog` · `13_zh_snackbar_retry_R11` ·
`14_far_tape_tol_10cm_two_dim_digits_R1` · `15_load_12pins_8tapes_and_20tapes_sheet` · `16_low_view_body_rests_on_floor_R2`.
`evidence/`: full frames of the load and limit states, single reticle frames, the English menu, the start pose with the
hijack marks (`v_pose1_marks.jpg`: magenta = clicked, cyan = where the system put it).
