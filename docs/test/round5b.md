# Spatial Ruler: round 5b QA (re-check of R5-1 .. R5-4, pin drag feel)

QA agent, 2026-10-06, 01:12 – 01:50 (device clock).

**Build under test:** the debug APK installed at 01:08:55 by the dev's gate run (`.scratch/gates_r5b.log`: 253 JVM tests, design-style verifier PASS, machine gates clean). QA did not rebuild.

**Device:** `emulator-5556`, AVD `PICO_6.1_Ruler`, checked before any device work. `emulator-5554` was never addressed.

**Input:** real input in eye mode, posted through the harness:
- 33 scripted pinch-drags with Stopwatch-timed glides (`tools/test/r5drag.ps1` via `r5bqa.run`). None was primed: the window was minimised all session, so a press lands where it is posted.
- 22 primed `clicks` sequences. In 21 of them the catcher was on; in one it was off by mistake (§6).
- Right-button camera drags.
- Debug broadcasts were used only for setup and read-back: `place`, `connect`, `tap_pin` (select / deselect), `clear`, `undo` (tidying), `fps`, `coach reset`, `calib`, `dump`, `pose`, `bounds`, `planes`, `rig`.

**Work pose:** floor pins at 1.4, 2.5 and 3.5 m are only in view with the head turned and pitched down. Every item except the control run (§4) was tested from yaw +88°, pitch −38° (`r3t rot 88 -38`: looking left and down at the floor, shot 06). The camera was turned back to the start pose before the relaunch tests.

**Scripts** (new, in `tools/test/`):
- `r5bqa.py`: the drag runner and its analysis. It lines up the device clock with the host (adb round trip ±20 ms), the cursor timeline against every `DRAG move` target, the release error along and across the drag, the trail, and the settle time.
- `r5bqa_pose.py`: finds clear floor points and drag paths.
- `r5bqa_t1.py`, `r5bqa_t23.py`, `r5bqa_t4.py`: items 1, 2 + 3, and 4.
- `r5bqa_reg.py`: fps and the regression.

**Raw output** (`tools/test/out/round5b/`):
- `logcat_session.txt` (01:12:35 – 01:47) and `app_log.txt` (3 289 lines).
- `r5b_runs.jsonl`: every drag with its timeline.
- `drags.jsonl`, `presses.jsonl`, `t4_a.json`, `t4_b.json`.
- `planes_start.json`, `dump_before_relaunch.json`, `dump_final.json`.

## 1 · Verdict: FAIL, narrowly. R5-3 and R5-4 are fixed and the gain no longer sticks, but the pin still overshoots

The pin overshoots the cursor on fast drags made with the head pitched down. Long drags miss the ≤ 10 px target (R5b-1, S2). From the level start pose, the same 250 px drag rests within 7.1 px, so the round-5b fix holds where it was calibrated.

## 2 · Verdict table

| # | Item | Result | Numbers |
|---|---|---|---|
| 1 | R5-1: pin under the cursor, 1.4 / 2.5 / 3.5 m floor pins, sideways + away | **partial** (R5b-1) | Release error at 150 px drags: 7.9 / 11.6 px (1.4 m side / away), 7.2 / 10.5 (2.5 m), 8.0 / 5.4 (3.5 m). Extra runs: 8.5 (2.5 m), 10.3 (1.6 m). 4 of 8 are ≤ 8 px; median 8.25. Apparent gain 0.95 – 1.08 (round 5: 0.67 – 1.20, median error 24 px). The distance dependence is gone, but at ≤ 2.9 m every error is an overshoot. Mid-drag: the target trails by up to 62 – 98 px at the end of a 300 ms glide (500 px/s). It comes within 8 px 0.26 – 0.42 s after the stop (0.85 s / never at 3.5 m), then creeps 2 – 8 px past the cursor until ×4 engages 0.7 – 0.9 s after the stop. |
| 2 | R5-2: gain | **partial** (R5b-1, R5b-2) | Pause 1 s, then 100 px in 240 ms: the pin moved **86 px** within 0.5 s in the clean run (pass) and **41 px** in run 1 (fail; see R5b-2); run 2 is not counted (frame-edge caveat). Sustained 250 px in 720 ms: the gap is 4.8 – 7.5 px 0.16 – 0.3 s after the stop, but the pin rests **17.2 / 25.5 px** past the cursor (fail, target ≤ 10). Slow nudge (12.5 px/s = 0.56°/s): `gain=1:4 reason=still` 0.82 s into the slow motion (pass); "×4" shows whenever the gain is 1 : 4 (shots 02, 03). First fast sample: `gain=1:1 reason=fast` on every quick move, 126 – 191 ms after the host move began (speeds 4.2, 4.5, 5.1, 13.7, 28.2°/s); round 5 needed 0.50 s of sustained motion (pass). Original R5-2 repro (100 px out, 300 ms rest, 100 px back): the pin came back 79.7 of 100 px (round 5: 14) with the gain at 1 : 1 throughout. The 20.5 px left is R5-6. |
| 3 | R5-3: live plates mid-drag | **pass** (R5b-3 cosmetic) | Pin p441 with two tapes, 35.0 / 40.4 cm before. Mid-drag frame, 1 s into the hold after a 150 px move: plates **46.1** and **62.7 cm**, body TUNING readout 62.7. After release, `dump` gives 46.2 / 62.9 cm (the 1 – 2 mm is the tail creep after the shot). Mid-drag `bounds`: body, plate0 (`plate:t301`), plate1 (`plate:t302`), pinTag, reticleTag, coachPanel; **no chip panel**, so chips are hidden. |
| 4 | R5-4: selected pin, head vs hook | **pass** | Selected floor pin (STATE pin-selected-tuning, strip open, stub out) at 1.50 – 1.56 m: 5 / 5 head press-drags gave `DRAG start target=pin:p445` → `DRAG begin mode=pin`, and 0 stub pulls. 2 / 2 hook-end presses gave `target=stub:p445` → `mode=pull`, `PULL start head=p445`, `PIN add … via=pull`, `TAPE add`. At 1.75 – 1.86 m (p448): 5 / 5 pin drags and 2 / 2 pulls. In 10 / 10 the system named the pin itself; no `retarget` was needed. |
| 5 | Drag feel | see §5 | |
| 6 | Regression (real pinches) | **pass** | §6 |
| 7 | Stability | **pass** | No ANR, FATAL, AndroidRuntime or crash-buffer entry, no `RIG violation`, and no W/E line of the app tag over the session. `RIG summary … violations=0` with 9 pins / 8 tapes and at the end. FPS during a real drag with 8 tapes: app 54.6 / 42.0 / 46.3, compositor 57 / 54 / 53. The seconds after the drag show the same (app 46.7 – 56.7), so the host was loaded: the compositor itself was under 60. Round 5 had 58 – 60 with 12 tapes. |
| 8 | Screenshots | **pass** | 7 in `.scratch/shots/round5bqa/` (§8) |

## 3 · Defects

| ID | Sev. | Repro | Expected | Actual | Evidence |
|---|---|---|---|---|---|
| R5b-1 | **S2** | Turn the camera to yaw +88°, pitch −38° (`r3t rot 88 -38`). Place a floor pin at (−2.06, 0.4006, 0.67), 2.5 m, and deselect it. Press its cap, drag 250 px sideways in 12 × 60 ms, hold 2 s, release. | Spec: fast = apparent 1 : 1. Gap ≤ 10 px at the end; ≤ 8 px on 150 px drags. | The pin rests **17.2 / 25.5 px** beyond the cursor (+7 – 10 %). On 150 px drags at 1.4 – 2.5 m it rests 7.2 – 11.6 px beyond (+5 – 8 %); at 3.5 m it falls 4.5 – 7.8 px short. Most of it arrives after the cursor stops: the emulator's tail is applied at 1 : 1 for 0.7 – 0.9 s, so the pin visibly creeps past the cursor. The system travel against `DragPinUseCase.equivalentDepth` (E_actual / E_model) is 1.08 – 1.16 at 1.4 – 2.9 m and 0.97 at 3.5 m from this pose. The model was calibrated with a level head. **Control:** the same 250 px drag from the level start pose (floor 2.52 m) rests 7.1 px off (−5.5 along). The dev measured 7.8 there. | `r5b_runs.jsonl` r1_*, r2_fast250, r2_fast250_3, r2_pause_quick_3, r2_fast250_startpose; table §4 |
| R5b-2 | S3 | Run 1 of the pause test, as in R5b-1: 150 px in 6 × 60 ms, rest 1 s, then 100 px in 4 × 60 ms. | "Let go on the FIRST event over 4°/s … a quick move right after a pause is applied in full." | Fine lets go correctly, 139 ms into the quick move. Then the emulator's drag events paused for 125 – 200 ms mid-move, the line counted as still, and `gain=1:4 reason=still` came back at 01:25:30.035 while the hand travel was still arriving (+0.046 m in the next 2 events). That travel was applied at 1/4, about 12 px, until `gain=1:1 speed=4.2` at 30.266. Related: the first event after a gap is timed over the whole gap, so it reads as slow. The first event of the 80 px flick after the nudge was applied at 1 : 4 (about 7.5 px lost). Net effect, together with the emulator delivering only 42 % of that move's hand travel in 0.5 s: the pin moved 41 px in 0.5 s (the clean run gave 86). | logcat 01:25:29.432 – 30.266 (`DRAG move … hand=`, `PIN drag gain`); 01:27:16.459 – 16.599 |
| R5b-3 | S4 | Drag a pin that has two tapes (as R5-3), or select it after the drag. | Plates of tapes that end on the selected pin are at full opacity; only the other tapes' plates recede to 55 %. | t301's plate (p441 – p442) is drawn dimmed (grey), both mid-drag (46.1) and after release (61.2), while t302's plate (p441 – p443) is solid. Both tapes end on p441, the armed pin. The rule `dimmed = explicit && !t.touches(armed.id)` (RulerViewModel ~671) would not dim it; cause not identified. With unrelated tapes, the dimming is correct (shot 05). The dim plate reads as "not attached". | shots 02, 04 |
| R5-6 | S3 (open, known) | Press a pin's head, drag 100 px out, come back to the same pixel, release. | Back on the cursor; within 10 mm = no change. | The pin is 20.5 px (39 mm at 2.74 m) from the start, then 28.5 – 32.9 px in two slower runs among 8 tapes. The system's net hand travel returns to −0.005 m: the 16 – 41 px press offset counted at the start is never undone. | r2_out_back, r7_fps_8tapes(_b) |

Observations (no defect):
- **O1:** the emulator does not send drag events for very slow motion. The 24 px nudge (0.56°/s) arrived as one event after a 1.08 s gap. ×4 then applied it as about 6 px, as designed, but on the emulator a nudge moves in steps.
- **O2:** short drags (60 – 80 px) on the selected pins of item 4 rested 3.2 – 13.8 px past the cursor (median 9.0). This is the same overshoot as R5b-1.
- **O3:** a console screenshot taken inside a drag can stall the app for a second (`FPS app=8.1`) and land after the release (the nudge shot did). Use mid-drag shots with a hold of ≥ 2 s.

## 4 · Measured numbers

Item 1, from the work pose. The error is the pin's resting cap pixel, `pos + n (14.5 mm · k + 6 mm)`, against the cursor's end pixel; *along* is positive when the pin is beyond the cursor.

| Run | Distance (m) | Cursor (px) | Pin (px) | Gain | Release error px (along / across) | Max trail px | Within 8 px after stop (s) | E_act / E_model |
|---|---|---|---|---|---|---|---|---|
| A sideways | 1.41 → 1.44 | 150 | 157.5 | 1.05 | 7.9 (+7.4 / −2.6) | 62.0 | 0.42 | 1.10 |
| A away | 1.44 → 1.53 | 150 | 161.4 | 1.08 | **11.6** (+11.4 / −2.3) | 76.8 | 0.26, then drifted to 10.4 | 1.12 |
| B sideways | 2.49 → 2.42 | 150 | 156.7 | 1.04 | 7.2 (+6.7 / −2.4) | 73.5 | 0.41 | 1.08 |
| B away | 2.42 → 3.03 | 150 | 159.9 | 1.07 | **10.5** (+9.9 / −3.5) | 81.5 | 0.29 | 1.09 |
| C sideways | 3.50 → 3.43 | 150 | 142.3 | 0.95 | 8.0 (−7.8 / −2.1) | 98.3 | — (8.7 at 1.26) | 0.97 |
| C away | 3.43 → 4.61 | 150 | 145.6 | 0.97 | 5.4 (−4.5 / −3.1) | 85.4 | 0.85 | 0.98 |
| R5-3 run | 2.49 → 2.59 | 150 | 157.9 | 1.05 | 8.5 (+7.8) | 92.6 | 0.41 | 1.11 |
| ghost run | 1.61 | 150 | — | — | 10.3 (+10.0) | — | — | 1.16 |

Item 2:

| Run | Distance (m) | Gain switches (s from press, speed °/s) | Pin in 0.5 s of the quick move | Rest error px |
|---|---|---|---|---|
| pause-quick run 1 | 2.59 | 1:4 @1.33, **1:1 @1.82 (13.7)**, 1:4 @2.41, 1:1 @2.64 (4.2), 1:4 @3.37 | **41 px** | 17.1 (+16.6) |
| pause-quick run 3 | 2.49 | 1:4 @1.40, **1:1 @1.83 (4.5)**, 1:4 @2.68 | **86 px** | 17.9 (+17.6) |
| pause-quick run 2 (ends 121 px from the frame edge, not counted) | 2.72 | 1:4 @1.45, 1:1 @1.88 (5.1), 1:4 @2.74 | about 80 px | 27.0 |
| fast 250 px run 1 | 2.82 | 1:4 @1.81 | — | **25.6** (+25.5) |
| fast 250 px run 3 | 2.67 | 1:4 @1.72 | — | **17.3** (+17.2) |
| fast 250 px, **level start pose** (control) | 2.52 | 1:4 @1.71 | — | **7.1** (−5.5) |
| nudge + flick | 2.58 | 1:4 @1.34 (still, 0.82 s into the slow motion), **1:1 @2.66 (28.2)**, 1:4 @3.19 | — | 5.8 (−4.7) |
| out-back 100 px | 2.74 | 1:4 @1.82 (after the return) | back 79.7 of 100 px | 20.5 (R5-6) |

## 5 · Drag feel

Fast drags feel attached but elastic:
- **Lag:** while the hand moves at about 500 px/s, the pin trails the cursor by 60 – 100 px. The emulator delivers the hand travel late.
- **Overshoot:** the pin catches up 0.25 – 0.4 s after the cursor stops. Then it keeps sliding 5 – 25 px past the cursor for about a second, until ×4 engages and it settles. It reads as a rubber band that overshoots, and it is stronger on long and "away" drags with the head pitched down.
- **Fine control:** good. Once you are still, ×4 takes over and small corrections are calm. A flick lets go of ×4 at once (about 0.13 s), and the "stuck at ×4" of round 5 is gone.
- **Ghost ring:** clearly legible on the dark rug as a thin white hollow ring. It is small at 1.6 m (shot 03), and in the two-tape case it was hidden behind a live plate.
- **Reticle:** the reticle above the lifted pin works well. The yellow ring and crosshair sit on the cap, the grey stability ring frames the whole pin, and the pin is never hidden.
- **×4 tag:** readable, a few centimetres below and to the right.
- **Confusion:**
  - the dimmed plate on one of the pin's own tapes (R5b-3);
  - the strip opens beside the pin during the drag and can sit near the drag path;
  - after release the pin keeps its last few millimetres of creep, so it lands slightly beyond where you let go.

## 6 · Regression (real pinches unless marked)

| Flow | Result |
|---|---|
| Tap–tap tape | **pass.** Nothing selected; floor, then floor: `PIN add p462`, `STATE pulling`, `PIN add p463`, `TAPE add t318` 56.0 cm. Both presses landed within 2 px. The first attempt's first press hit the open pin strip of p455 (left selected by the fps test); that was test sequencing. |
| Select pin → chip → tape | **pass.** `TAP pin id=p451` → `STATE pin-selected-tuning`, chips chip0 – 5 → `UI chip:p454` → `TAPE add t317 a=p451 b=p454`. |
| Steppers once each | **pass.** `step_u_plus` du=1, `step_u_minus` du=0, `step_v_plus` dv=1, `step_v_minus` dv=0 (`PIN nudge` each). `step_v_minus` was redone in its own sequence: the 4th press of a 5-press sequence fell after the 60 fps period. |
| Tape strip retract | **pass.** `UI plate:t307` → `STATE tape-selected` → `UI tape_retract` → `TAPE remove id=t307 reason=retract`. |
| Undo after a drag (exact) | **pass.** Real undo key after the drag of p455: `ANCHOR rebase … reason=undo shift_mm=0`, p455 back at (−1.8000, 0.4006, 0.4500), 0.0 mm. The first try undid a stray tape instead: a priming click went to the scene while the catcher was off (QA error). |
| Units ×2 | **pass.** `UNIT mm`, `UNIT cm`. |
| Language zh and back | **pass.** `UI more` → `STATE more-menu`, `LANG zh`, `LANG system`, menu closed by More. |
| Relaunch restore | **pass.** force-stop + start at the start pose: `RESTORE pins=13 tapes=10`, all 13 pins present, largest tape-length change 0.00 mm, cm / system kept. |
| Clear all | **pass.** `UI menu_clear` → `STATE confirm-clear` → `UI dialog_confirm` → `CLEAR pins=13 tapes=10`. |
| Exit + relaunch | **pass.** `UI menu_exit` → `STATE exited`, `STAGE disposed`. Relaunch: `RESTORE pins=0 tapes=0`. |

## 7 · Harness notes

- **Minimised window:** the emulator window was iconic all session. Posted input, `fast on` and console screenshots work. Unprimed drags land where they are posted. Primed `clicks` need the catcher **on**, or the priming press lands in the scene.
- **60 fps period:** sequences longer than about 5 s lose their last press.
- **Pitched poses:** the floor at 1.4 / 2.5 / 3.5 m is only reachable from a turned and pitched head. The pose `rot 88 -38` shows clear floor at all three distances (`r5bqa_pose.py`).
- **Restoring the pose:** after `rot 0 0`, a 3 fb px right-button drag removed the last 0.1° of pitch, giving `POSE` q (0, 0, 0, 1) exactly.
- **Frame edge:** keep pin paths 130 px from the frame edge. Two R5-2 runs were discarded for it.

## 8 · Screenshots (`.scratch/shots/round5bqa/`, evidence in `evidence/`)

1. `r5bqa_01_mid_drag_live_plates`: mid-drag, with:
   - both plates live (46.1 / 62.7);
   - the body at 62.7;
   - ×4, the strip, and no chips.
2. `r5bqa_02_mid_drag_zoom_plates_x4`: zoom on the same frame (reticle around the lifted pin, ×4, the dim 46.1 plate).
3. `r5bqa_03_ghost_ring_reticle_x4`: lone pin mid-drag (ghost ring on the rug, reticle over the pin, ×4).
4. `r5bqa_04_after_release_strip_dim_plate`: after release (strip "● ±8 mm", plates committed, one of the pin's plates dim).
5. `r5bqa_05_drag_among_tapes`: drag among other tapes; their plates dimmed correctly, the dragged tape's plate live.
6. `r5bqa_06_work_pose`: the work pose yaw +88°, pitch −38°.
7. `r5bqa_07_final_state`: final state.

## 9 · State left behind

- App relaunched after a real Exit: `RESTORE pins=0 tapes=0`, `STATE idle`, scene empty.
- Unit cm, language system, snap on. Coachmark re-armed (`COACH reset`, coachPanel showing).
- Catcher off: `bounds` lists only `body` and `coachPanel`. `RIG summary slots=35 shown=2 violations=0`.
- Eye mode: the mode was never changed; guest controllers are offline. `emu_mode.ps1 -Mode status` finds no tool bar while the window is minimised.
- Head at the start pose: `POSE` pos (0, 1.65014, 0), q (0, 0, 0, 1).
- The emulator window is still minimised and not foreground. No emulator setting changed. Log capture stopped.
