# Spatial Ruler: round 5c QA (final check of the pitched travel model, ×4 rule, plate hover)

QA agent, 2026-10-06, 02:53 – 03:21 (device clock).

**Build under test:** the debug APK installed at 02:51:27 by the dev's gate run (`.scratch/gates_r5c.log`: 254 JVM tests, gates PASS). QA did not rebuild. App pid 7018 for the whole session.

**Device:** `emulator-5556`, AVD `PICO_6.1_Ruler`, checked first (`adb -s emulator-5556 emu avd name`). `emulator-5554` was never addressed. The emulator window stayed minimised, and no emulator setting was changed.

**Input:**
- 22 real pinch-drags (19 counted; `r5bqa.run` → `r5drag.ps1`, Stopwatch-timed glides). Profiles as in round 5b:
  - 250 px: 12 × 60 ms, then a 2.0 s hold.
  - 150 px: 10 × 30 ms, then a 1.5 s hold.
- 16 primed real-pinch sequences (`r3t.pc`, catcher on).
- Right-button camera drags.
- Debug broadcasts only for setup and read-back: `clear`, `place`, `connect`, `tap_pin`, `ui coach_got_it` (the coachmark covered drag paths), `fps`, `dump`, `pose`, `bounds`, `rig`, `planes`, `coach reset`.

**Script:** `tools/test/r5cqa.py` (new; reuses `r5bqa.run` and adds an fps check for each run). Run it as `python r5cqa.py level | rot88 | pitched | gain | gain2 | plates | fps8 | reg1 | rot0 | reg2 | relaunch`.

**Raw output** is in `tools/test/out/round5c/`:
- `logcat_session.txt` (02:54:45 – 03:18:07) and `app_log.txt` (2 620 lines).
- `r5c_runs.jsonl`: every drag with its timeline.
- `drags.jsonl`.
- `summary.jsonl`: per-check numbers.
- `planes_start.json`: identical to round 5b.
- `evidence/`.

**Validity:** a run counts only if the compositor stayed ≥ 38 fps from the press until 2.5 s after the last glide. Three runs were discarded:
- `g_pause_quick_1`: the fps fell to 23 and then 2 mid-drag (9 drag events).
- `plates_two_tapes`: 11 / 2 / 2 fps; the press never reached the app.
- `fps_8tapes`: the press hit a stale gaze point 160 px away (`retarget=pin:p598`).

All three were repeated (see O1).

## 1 · Verdict: **not ship**

| # | Check | Result |
|---|---|---|
| 1 | Looking down (yaw 88°, pitch −37.9°) | **FAIL** (R5c-1): 250 px **1 / 4** ≤ 10 px; 150 px **2 / 4** ≤ 8 px. The pin now rests *short* of the cursor; round 5b overshot. |
| 1 | Level start pose | **pass, marginal:** 250 px 2 / 2 ≤ 10 px; 150 px 1 / 2 ≤ 8 px (the other is 8.4). |
| 2 | Pause 1 s, then a quick 100 px | **FAIL** (R5c-2): the pin moved **77.5 / 76.7** px in 0.5 s (a third valid run gave 74.2); target ≥ 85. |
| 2 | ×4 during a continuous move | **pass:** none during a 2.6 s continuous move at 2°/s. **But** ×4 engages 0.8 – 1.0 s after a sideways stop while the emulator's tail is still delivering travel (R5c-2). |
| 3 | Own-tape plates never dimmed | **FAIL after release** (R5c-3, cosmetic). Mid-drag both plates are solid (fixed). After release, the 44.1 cm plate (a tape on the dragged pin) turns light grey. |
| 4 | Regression, real pinches | **pass** (§4) |
| 5 | Stability, fps | **pass:** no ANR / FATAL / crash / `RIG violation` / app W/E line. 8 tapes mid-drag: compositor 60 / 49, app 37.8 / 52.5. |
| 6 | Screenshots | 4 in `.scratch/shots/round5cqa/` |

## 2 · Check 1: pin under the cursor (error = the resting cap pixel against the cursor's end pixel; along > 0 means beyond the cursor)

| Pose | Run | Distance (m) | Drag | Error px (along / across) | Target | Max trail px | Gain switches (s after the stop) |
|---|---|---|---|---|---|---|---|
| level | bed top A, sideways | 1.51 → 1.54 | 250 | 6.2 (+0.7 / −6.2) | ✓ | 207 | none |
| level | floor B, away | 2.40 → 3.06 | 250 | 7.9 (−4.7 / −6.3) | ✓ | 90 | none |
| level | bed top A, away | 1.51 → 1.77 | 150 | **8.4** (−6.0 / −5.8) | ✗ (marginal) | 80 | none |
| level | floor B, sideways | 2.52 → 2.62 | 150 | 7.5 (+6.1 / −4.3) | ✓ | 83 | none |
| pitched | floor A, sideways | 1.51 → 1.56 | 250 | **11.2** (−10.9 / −2.9) | ✗ | 82 | ×4 @ 0.88 |
| pitched | floor A, away | 1.51 → 1.71 | 250 | **14.9** (−14.5 / −3.4) | ✗ | 88 | none |
| pitched | floor B, sideways | 2.49 → 2.46 | 250 | **13.2** (−13.0 / −2.5) | ✗ | 92 | ×4 @ 0.90, then 11.2 mm of raw travel at 1 : 4 |
| pitched | floor B, away | 2.49 → 3.69 | 250 | 5.3 (+0.9 / −5.2) | ✓ | 80 | none |
| pitched | floor A, sideways | 1.51 → 1.54 | 150 | 4.7 (+3.6 / −2.9) | ✓ | 70 | ×4 @ 0.99 |
| pitched | floor A, away | 1.51 → 1.61 | 150 | **12.6** (−12.5 / −1.6) | ✗ | 73 | none |
| pitched | floor B, sideways | 2.49 → 2.46 | 150 | **12.1** (−11.8 / −2.5) | ✗ | 80 | ×4 @ 0.86, then 10.7 mm at 1 : 4 |
| pitched | floor B, away | 2.49 → 3.08 | 150 | 4.2 (−2.1 / −3.6) | ✓ | 75 | none |

Points (Stage m):
- Level: A (0.008 / 0.048, 0.866, −1.293); B (0.942, 0.4006, −1.958) and (0.967, 0.4006, −1.785).
- Pitched: A (−0.81, 0.4006, −0.20) / (−0.76, 0.4006, −0.34); B (−2.13, 0.4006, 0.38).
- Every path stays ≥ 150 px from the frame edge, and the floor lies under the path and 30 px either side of it.

Medians at the pitched pose: 250 px **12.2** (round 5b: 21.4, all overshoot); 150 px **8.4** (round 5b: 8.25, all overshoot). Other drags at the pitched pose, not counted against the targets, rest 3.5 – 10.0 px from the cursor:
- 150 px left with two tapes: 10.0 (−9.6).
- 120 px away: 5.4.
- 120 px with 8 tapes: 5.1.
- 160 px slow: 3.5.

## 3 · Check 2: gain

| Run (pitched, floor B, 150 px in 6 × 60 ms, 1 s rest, 100 px in 4 × 60 ms) | fps | Gain switches (s from press, speed °/s) | Pin in 0.5 s of the quick 100 px | Rest error px |
|---|---|---|---|---|
| pause-quick 2 | 49 – 52 | 1:4 @1.718 (rest, 1.02 s after stop), **1:1 @1.853 (6.3)**, 1:4 @2.785 | **77.5** | 14.9 (−14.6) |
| pause-quick 3 | 60 | 1:4 @1.696, **1:1 @1.830 (5.4)**, 1:4 @2.729 | **76.7** | 16.9 (−16.7) |
| pause-quick 4 | 60 | 1:4 @1.673, **1:1 @1.823 (8.0)**, 1:4 @2.740 | 74.2 | 21.2 (−21.1) |
| continuous: 40 px fast, then 160 px at 2°/s (44 × 60 ms) | 47 – 54 | only 1:4 @3.961 (0.77 s after the stop, no travel after it) | — | 3.5 (−1.9) |

During the continuous move the drag events came every 0.06 – 0.28 s, and ×4 never engaged; R5b-2 (×4 re-engaging mid-move) is fixed for a moving hand. During the 1 s rest ×4 does engage, at the end of the rest (contrary to the dev note that the tail keeps it off for about 2 s). The quick move's first event is then applied at 1 : 4 for 134 – 150 ms until `1:1 reason=fast`. This is the known "first event after a pause is timed over the pause" issue.

## 4 · Regression (real pinches, catcher on)

| Flow | Result |
|---|---|
| Tap–tap tape | **pass:** `PIN add p615` → `STATE pulling` → `PIN add p616` → `TAPE add t340` 36.0 cm; both presses within 1 px. |
| Drag that pin | **pass:** 120 px away, 340 mm moved, rest error 5.4 px; `ANCHOR rebase … shift_mm=0`. |
| Undo (exact) | **pass:** real `undo` key → `UNDO depth=2`, `ANCHOR rebase … reason=undo`, `shift_mm=0`; p616 back at (−1.6484, 0.4006, −0.1501), **0.00 mm**. |
| Tape strip retract | **pass:** `UI plate:t340` → `STATE tape-selected` → `UI tape_retract` → `TAPE remove … reason=retract`. |
| Units ×1 | **pass:** `UNIT mm`, then `UNIT cm` (cm was pressed after the relaunch, see O2). |
| zh and back | **pass:** `UI more` → `STATE more-menu` → `LANG zh`; after the relaunch `LANG system`, and More closes the menu. The relaunch restored mm / zh, so both settings persist. |
| Clear all → relaunch empty | **pass:** `UI menu_clear` → `STATE confirm-clear` → `UI dialog_confirm` → `CLEAR pins=2 tapes=0`. Real Exit: `UI menu_exit` → `STATE exited`, `STAGE disposed`. `am start` (no force-stop) → `RESTORE pins=0 tapes=0 elsewhere=0 orphans=0`. |

## 5 · Stability and fps

The session log (399 k lines) has no `ANR in`, `FATAL EXCEPTION`, `AndroidRuntime` or crash-buffer entry, no `RIG violation`, and no W/E line of the app tag.

Drag of p610 among 9 pins / **8 tapes** (03:11:08.1 – 10.0, 120 px):

| Window | Compositor fps | App fps |
|---|---|---|
| 08 – 09 s | 60 | 37.8 |
| 09 – 10 s | 49 | 52.5 |
| Before the drag | 57 | 57 – 60 |
| After the drag (anchor rebase) | 57 | 40.7 |

`RIG summary slots=35 shown=16 violations=0`. Round 5b: app 54.6 / 42.0 / 46.3 and compositor 57 / 54 / 53, the same range.

## 6 · Defects

| ID | Sev. | Repro | Expected | Actual | Evidence |
|---|---|---|---|---|---|
| R5c-1 | **S2** | `r5cqa.py rot88 pitched`: head yaw 88°, pitch −38°; fresh floor pin at (−0.81, 0.4006, −0.20) / (−0.76, 0.4006, −0.34) at 1.5 m and (−2.13, 0.4006, 0.38) at 2.5 m, deselected. Press its cap, drag 250 px (12 × 60 ms, hold 2 s) or 150 px (10 × 30 ms, hold 1.5 s) sideways or away, release. | Rest ≤ 10 px (250), ≤ 8 px (150) | 5 of 8 miss, all **short**: −10.9 / −14.5 / −13.0 (250 px), −12.5 / −11.8 (150 px). The pin approaches from behind and is still 12 – 18 px short 0.6 – 1.9 s after the stop (away drags are still creeping at release). Part of it, about 3 – 4 px, is R5c-2's tail at 1 : 4. Level pose: 3 of 4 are within target. Round 5b overshot at this pose (+7 to +25); the sign has flipped. | `r5c_runs.jsonl` `p_*` (`rows`, `err_at_end_plus`), §2 |
| R5c-2 | S3 | (a) As R5c-1, sideways. (b) Pitched, floor B: 150 px in 6 × 60 ms, rest 1 s, then 100 px in 4 × 60 ms (`r5cqa.py gain2`). | ×4 only once the hand is really still; a quick move after a pause is applied in full (≥ 85 px in 0.5 s). | (a) Sideways tail events are sparse at this pose (13 – 37 events per drag, against 53 – 92 for away or level drags). `gain=1:4 reason=still` comes 0.79 – 0.99 s after the stop in 4 of 4 sideways drags and 3 of 3 pause runs. In 5 of these 7, the tail's next step (10.7 – 11.6 mm) is then applied at 1 : 4. (b) ×4 is on when the quick move starts. Its first event reads slow (timed over the rest), so `1:1 fast` comes 134 – 150 ms late, and the pin covers **77.5 / 76.7 / 74.2 px** of the 100 px in 0.5 s (round 5b clean run: 86). | logcat 03:05:24 – 03:10:27 (`g_pause_quick_2/3/4`), 03:02:09 and 03:03:16 (`p_*_B_side`); `summary.jsonl` `gain_vs_motion` |
| R5c-3 | S4 | Pin p601 with two tapes (t330 → p602, t331 → p603), pitched pose. Drag p601 150 px left across its own 44 cm plate, hold 2.6 s, release (`r5cqa.py plates`). | The pin's own plates are at full opacity after release. | Mid-drag, both plates are solid (luma median 22 / 22): the in-drag fix works. 1 s after release the 44.1 cm plate (t330) is **light grey** (median 102, against 22 for t331). The SpatialUI hover tint seems to return once hover is re-enabled after the drag. Same look as R5b-3, now only after release. | shots 01 – 03 |
| R5-6 | S3 | not retested | | open (known) | |

Observations:
- **O1 (harness):** `emu.sh waitfast` can pass on a stale `PxrMetric FPS=60` line, because `devnow` truncates to the second. A drag then runs at 1 – 2 fps, or its press pinches a stale gaze point. `r5cqa.fast_now()` and `run_fps()` were added; the 19 counted drags ran at 38 – 61 fps.
- **O2 (test sequencing):** after turning from yaw 88° back to 0°, the body stayed about 20° left (R3-3, known). `unit_cm` and `lang_system` then projected off frame (x < 0), and those two pinches were never delivered. They were redone after the relaunch.
- **O3:** at the level pose the across part of the error is −4 to −6 px (round 5b: −2 to −3.5).

## 7 · Screenshots (`.scratch/shots/round5cqa/`)

1. `r5cqa_01_mid_drag_two_tapes`: mid-drag, both live plates (59.9 / 44.0) solid, with the reticle over the pin.
2. `r5cqa_02_after_release_two_tapes`: after release, the 44.1 plate is grey and the 60.1 plate is solid.
3. `r5cqa_03_plates_mid_vs_after_zoom`: zoom of 1 and 2 side by side.
4. `r5cqa_04_final_state`: final state.

## 8 · State left behind

- The app was relaunched after a real Exit: `RESTORE pins=0 tapes=0`, `STATE idle`, scene empty.
- Unit cm, language system, snap on, set with real pinches.
- `COACH reset`: step 1, coachPanel shown.
- Catcher off: `bounds` lists only `body` and `coachPanel`. `RIG summary slots=35 shown=2 violations=0`.
- Eye mode: never changed.
- Head at the start pose: `POSE` pos (0, 1.65014, 0), q (0, 0, 0, 1).
- The emulator window is still minimised and not foreground. Log capture stopped.
