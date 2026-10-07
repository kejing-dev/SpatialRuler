# Spatial Ruler: round 5 QA (pin drag by direct manipulation)

QA agent, 2026-10-05, 22:56 – 23:50 (device clock).

**Build under test:** the debug APK the dev installed at 22:47:04 (process started 22:47:03). The dev's gate run of 22:47 (`.scratch/gates_r5.log`) passed: 247 JVM tests, design-style verifier PASS. QA did not rebuild or re-run the gates.

**Device:** `emulator-5556`, AVD `PICO_6.1_Ruler` (checked before any device work). `emulator-5554` was never addressed.

**Input (all real, eye mode, PostMessage through the harness):**
- 41 primed `clicks` sequences with 53 target presses (body keys, menu, dialog, strips, chips, plates, pins, stub, surfaces).
- 31 scripted pinch-drags (`tools/test/r5drag.ps1`), 25 of which the system recognised as drags: 9 hold-then-release pin drags (7 floor pins at 1.43–3.03 m, 1 wall pin, 1 with 12 tapes), 1 wall re-seat, 2 snap runs, 2 gain runs, 2 out-and-back runs, 6 start-threshold runs, 5 runs on selected pins / the stub, 1 press-and-pull on the floor.
- Debug broadcasts only for setup and read-back: `place`, `tap_pin` (deselect), `undo` (tidying), `marks long`, `fps`, `coach reset`, the synthetic tracked pointer for one supplementary check (R5-6), `calib`, `dump`, `pose`, `bounds`, `planes`, `rig`.

**H1:** not possible this round. The emulator main window was minimised (iconic) all session and was never foreground. Posted input and console screenshots still worked. The catcher (`calib 0.6,1,80,-0.4143,0.3819`, console pixel 260, 260, upper left, out of the work area) primed every sequence. It took exactly one press per sequence, no stray press. Every target press was checked in the log (`PTR … ev=Press`, `UI …`, `TAP … stage=` reprojected within 2 px).

**Scripts:** new in `tools/test/`:
- `r5drag.ps1`: catcher click, press on the pin cap, Stopwatch-timed glides with the button held, holds, background console screenshots mid-drag, release.
- `r5t.py`: r4t runner plus `run_path`, drag log parsing, catcher at CATCH.
- `r5_drags.py`: drag runs and their analysis (cursor vs target pixel, the full-gain estimate from the logged hand travel, a timeline per 100 ms).

**Raw output:** `tools/test/out/round5/`:
- `logcat_session.txt`: app tag, PxrMetric, ActivityManager, AndroidRuntime and every W/E line, 23:01:57 – 23:47.
- `app_log.txt`: 4 116 `SpatialRuler` lines.
- `presses.jsonl`, `drags.jsonl`, `r5_runs.jsonl`.
- `planes_*.json`, `dump_before_exit.json`.

**Screenshots:** 12 curated shots in `.scratch/shots/round5qa/`; full frames and crops in `round5qa/evidence/`.

## 1 · Verdict: FAIL – drag mechanics work, but the drag's feel and readings do not

What works with real input:
- A press on a pin plus a drag starts `pin-drag`.
- The ghost ring stays at the origin, and the reticle with its seat marks stands at the target.
- The strip stays open with its steppers.
- A release beyond 50 mm rebases the anchor.
- One Undo restores the position exactly.
- A drag onto the wall re-seats the pin, and Undo puts it back on the floor.
- The stub pulls a new tape, and an edge snap works.
- The regression passes, and there was no crash.

What fails:
- **R5-1:** the pin does not stay under the cursor. Its gain grows with distance, from 0.67 at 1.4 m to 1.20 at 3.0 m.
- **R5-2:** after any pause the drag is stuck at 1 : 4.
- **R5-3:** the plates of the dragged pin's tapes show stale lengths.
- **R5-4:** on a selected pin at about 1.8 m or less, the stub swallows the press on the pin's head, and a drag pulls a new tape instead of moving the pin.

## 2 · Verdict table

| # | Item | Result | Evidence |
|---|---|---|---|
| 1 | S4 re-checks | **partial** (R5-7) | EN item: `uiautomator` shows desc `English`, and `英文` in zh. `COACH dismissed reason=got_it` (real ✓, 23:03), `reason=first_tape` (real tap–tap, 23:04:39). Sizes: the accessibility nodes of the clickable segment items are 48 dp high (unit 74 × 48, language 54.5 × 48), but `bounds` CTRL still reports 74 × 40 and 54.5 × 40, and the language items are not 60 dp wide. |
| 2 | Pin drag, real | **fail** (R5-1, R5-3, R5-6) | 9 drags on floor pins at 1.43–3.03 m plus 1 wall pin: `DRAG begin mode=pin`, `PIN drag start/release/end`, ghost ring and reticle with seat marks (shots 01, 04, 06), strip open with steppers after release (shot 03, `bounds` pinTag with `step_*`). Rebase beyond 50 mm on every one (`ANCHOR rebase … reason=drag … shift_mm=0`). One real Undo: p367 back at (1.3000, 0.4006, −1.0000) exactly, tapes 84.9 / 67.1 cm, `reason=undo`. Below 50 mm: offset only, no anchor lines. Cursor-to-pin error and live plates: see R5-1, R5-3. Release within 10 mm: not reachable by real input (R5-6); the rule itself works (tracked synthetic drag back onto the base: `cancelled=true`, no ANCHOR line). |
| 3 | Slow drag / gain | **fail** (R5-2) | `PIN drag gain … gain=1:4 speed=1.1` and "×4" in the reticle tag (shot 02). It switches 0.6 s after the slow segment starts: the emulator's smoothing tail of the fast move keeps the line above 2°/s until then. Back to 1 : 1 only after 0.50 s of a sustained ~15°/s move, never after a short flick (R5-2). Ratio: internal 0.249; apparent 0.19 at 2.2 m (§4). |
| 4 | Re-seat | **pass** | Floor pin D dragged onto the wall: `PIN drag end … plane=new`, `ANCHOR rebase start … reason=drag-plane`, old anchor removed, new one saved in 119 ms. Both tapes followed (shot 04). Real Undo: `reason=undo-plane`, D back at (1.7566, 0.4006, −0.4830) exactly, tapes back to 49.8 / 91.8 cm. |
| 5 | Stub | **pass** (but see R5-4) | A drag on `stub:p367`: `DRAG start target=stub:p367`, `PULL start head=p367`, `PIN add p375 via=pull`, `TAPE add t283`; p367 unmoved (shot 05). A tap on the stub: system `SELECT_ENTERED touch down stub:p367`, app `TAP pin id=p367`, `STATE idle` (same as a tap on the selected pin). |
| 6 | Snapping during a drag | **partial** (R5-5) | Edge: wall pin to the wall's bottom edge, `FEEDBACK snap`, `PIN drag move … snap=edge`, `PIN drag end … snapped=edge` (shot 06). Pin: no snap (R5-5). Corner: not tested; no same-plane corner had a clear line of sight from the work pose. |
| 7 | Regression | **pass** | §5 |
| 8 | Controller mode | **not testable** | `emu_mode.ps1 -Mode right` failed twice (`ERROR: ctrl_dropdown_button not found`); `-Mode status` finds no tool-bar button. The window is minimised. Skipped as instructed; the emulator stayed in eye mode (guest: both controllers offline). |
| 9 | Stability | **pass** | No ANR, FATAL or AndroidRuntime line; no `RIG violation`; no W/E line of the app tag. `FPS app=58.2–60.0` during a drag with 12 tapes (8 required). `RIG summary slots=35 … violations=0` with 14 pins / 13 tapes, and again at the end. |
| 10 | Screenshots | **pass** | 12 in `.scratch/shots/round5qa/` (§7). |

## 3 · Defects

| ID | Sev. | Repro | Expected (spec) | Actual | Evidence |
|---|---|---|---|---|---|
| R5-1 | **S2** | Press a pin's head and drag it about 150 px fast, hold still, release. Repeat at 1.4–3.0 m. | Spec: system drag "fast = apparent 1 : 1 (the pin stays under the cursor)". | The pin's apparent travel against the cursor's grows with distance, gain ≈ d / 2.5 m: 0.67 at 1.43 m, 0.71 at 1.61, 0.82 at 2.14, 0.85 at 2.02, 0.94 at 2.42, 1.04 at 2.71, 1.16 at 2.80 (wall), 1.20 at 3.03. Release error 1.4–37.7 px (0.06°–1.68°), median 24 px (1.1°). The pin lags short of the cursor below about 2.4 m and runs ahead beyond 2.6 m. Likely cause: on the emulator the system's hand travel already grows with the target's distance (equivalent depth 2.66 m at 1.43 m … 4.81 m at 3.03 m). The app then multiplies by d / `APPARENT_DEPTH_M` (4.0), which was calibrated at one distance, 2.67 m. | `r5_runs.jsonl` d1–d7, f2; table §4; shot 01 |
| R5-2 | **S2** | Drag a pin 100 px, pause 300 ms, drag back 100 px in 240 ms, release. | Spec: "slow … = 1 : 4 …, hysteresis back above 6°/s". | After the pause the drag stays at 1 : 4: the pin came back 14 px instead of about 100 (c2), and no `gain=1:1` line was logged in 5.7 s. A sustained 297 px move at ~15°/s switched back only after 0.50 s (s1). The travel lost before the switch stays as an 84 px (3.7°) offset between cursor and pin. Cause: the way back needs > 6°/s on every frame for 100 ms, but system drag events arrive every 30–40 ms (2–3 frames), and a frame without an event counts as 0°/s and resets the timer. Side effect: every drag that rests ≥ 150 ms before release is graded `fine` (18 of 18; the only 2 `coarse` drags were released while still moving). | `r5_runs.jsonl` s1_slowfast, c2_cancel; `logcat_session.txt` 23:17:49–55 |
| R5-3 | **S2** | Drag a pin that has two or more tapes and hold mid-drag (screenshot). | Spec: "connected tapes, plates and the body readout update live". | The tapes and the body's TUNING readout count live, but the plates keep the pre-drag length. d1: A-B plate live 99.2, C-A plate stuck at 67.1 cm while the body read 51.2 (the live C-A length). f2 (12 tapes): all three of D's plates stuck at 49.8 / 9.8 / 63.4 cm against true 34.5 / 14.7 / 77.3 cm, while the body read 14.6. After release the plates are right. Likely cause: the per-tape loop in `RulerScene` (around line 1087) removes `liveLengths[tape]` every frame for a tape that is not growing, racing the 10 Hz writes of `stepPinDrag` (around line 2325). | shots 01, 09, 10 |
| R5-4 | **S2** | Select a pin at 1.5–1.8 m (a tap, or the selection left by a drag release), then press its head and drag. | Spec: "press a pin + drag … → pin-drag"; only "dragging the tape-head stub pulls a new tape". | 3 of 4 real drags from the visible cap started on `stub:<pin>` and pulled a new tape and pin (p372 at 1.56 m; p369 at 1.76 m twice); 1 moved the pin. Taps on a selected pin's head went to the stub 2 of 2 at 1.56–1.76 m and 0 of 2 at 2.21–3.00 m. The stub's touch sphere (45 mm × k, centred about 52 mm above and 28 mm right of the pin foot, with sway) covers the line of sight to the cap. A corrective second drag after a release is exactly this case. | `DRAG start target=stub:p372 … retarget=pin:p372` 23:23:39, `stub:p369` 23:26:47 and 23:28:04; `r5_runs.jsonl` selected_pin_drag, cap_press_targets |
| R5-5 | S3 | Drag a pin onto another, unconnected pin (aim 1/gain beyond it so the line reaches it), hold, release. | Spec: "approaching another pin snaps (pin seat mark); releasing while snapped sets `snapped`". | The target freezes at the edge of the other pin's pick radius and moves no further. It was released 48.9 mm from pin C with `snapped=none`. A candidate chip ("33.6 cm") covered the reticle there. The code ignores previews with a `pinId` (`stepPinDrag`), and the release resolves the last non-pin ray. | sn2 log 23:33:00–02; shot 11 |
| R5-6 | S3 | Press a pin's head, drag out 100 px, come back to the same pixel, release. | Spec: "releasing within 10 mm of the ghost = no change". | The pin moves 26–38 mm (2 of 2: 38.0 mm at 1.76 m, 26.3 mm at 2.07 m), because the drag's press point is where the system recognised the drag, 13–29 px along the drag from the press. Landing within 10 mm is not reachable by returning the hand. The rule itself works: the tracked synthetic drag back onto the base gave `cancelled=true` and no ANCHOR line. | c1_outback, cancel_a1, synthetic run 23:29:30 |
| R5-7 | S4 | Open More; `bounds`, and `uiautomator dump` after a press in the panel. | QA R4-2: language items 60 × 48 dp; unit items ≥ 48 high (AGENTS: 79 × 48). | Heights are 48 dp in the accessibility tree, but the language items are 54.5 dp wide (not 60) and the unit items 74 dp (not 79). `bounds` CTRL still reports 40 dp high (55 × 40, 74 × 40): the probe sits inside the item's 40 dp content, so harness hit-target checks see 40. | `bounds` 23:05; `out/round5/ui_menu_r5b.xml`, `ui_menu_r5.xml` |
| R5-8 | S4 | Mid-drag screenshot with other pins in view. | Candidate chips show the length a new tape would have. | During the drag the chips keep the distances from the ghost (94.3 / 163.0 / 121.3 cm) while their threads are drawn from the dragged pin. A chip can also stand on a pin 10 cm away: pinches aimed at pin D and at pin A went to the chip of D (`PTR panel=chip0`; `TAPE add t288`). | shot 01, f1_fps |

Observations (no defect):
- **O1 – dead zone (H3, measured).** A press with 1.1°, 1.6° or 2.0° of motion is neither a tap nor a drag: nothing is logged at all. At 0.7° it is a tap; drags start at 2.3° and 2.7°. This is the system gesture recognisers, not the app's 1 cm threshold.
- **O2 – Stage yaw at launch.** Every launch re-centres the Stage yaw on the head, so pin Stage coordinates rotate by the head's yaw (up to 2.53 m between launches). Anchors restore every pin to the same place: tape lengths were identical to 0.0 mm.
- **O3 – lag.** The emulator delivers the drag's hand travel with a long tail. At the end of a 300 ms move about 77 % has arrived; the rest takes 1.5–2 s, and the last of it reaches the app only at release when the 60 fps period has ended.

## 4 · Measured numbers

| Run | Pin | Press dist (m) | Cursor (px) | Pin (px) | Release error px (deg) | Full-gain error px | Apparent gain |
|---|---|---|---|---|---|---|---|
| d6 | E p372 | 1.43 | 161.6 | 129.4 | 37.7 (1.68) | 32.2 | 0.67 |
| d2 | C p369 | 1.61 | 155.2 | 121.5 | 37.7 (1.68) | 31.8 | 0.71 |
| d1 | A p367 | 2.02 | 170.9 | 142.8 | 25.9 (1.15) | 21.5 | 0.85 |
| f2 | D p370 (12 tapes) | 2.14 | 134.2 | 119.5 | 16.4 (0.73) | 12.7 | 0.82 |
| d4 | D p370 | 2.42 | 170.0 | 161.0 | 4.2 (0.19) | 1.7 | 0.94 |
| d3 | B p368 | 2.71 | 151.3 | 151.1 | 1.4 (0.06) | 6.0 | 1.04 |
| d5 | W p371 (wall) | 2.80 | 126.5 | 159.5 | 27.9 (1.24) | 36.4 | 1.16 |
| d7 | F p373 | 3.03 | 153.0 | 173.7 | 21.8 (0.97) | 27.6 | 1.20 |

- *Release error* is where the pin landed against the final cursor pixel, after a hold of 1.5–2.4 s. *Full-gain error* is the same computed as if the whole logged hand travel had been applied 1 : 1 (no 1 : 4 tail). *Apparent gain* is the pin's motion over the cursor's, measured from the press point.
- **Mid-drag:** in the fast phase the target trails the cursor by 30–55 px while the hand travel arrives. In a steady 27 px/s glide the trail was 5–20 px.
- **1 : 4 ratio:** internal 0.249 (target against the raw pointing line over 72 px, s2). Apparent 0.19 at 2.2 m (pin 17.9 px for a 93.4 px vertical cursor move: 1 : 4 × that move's gain of 0.77). Apparent 0.29 at 2.75 m over a short window (s1).
- **Gain switch:** 1 : 4 engaged 0.6 s after the slow segment started (speed 1.1°/s at the switch). Back to 1 : 1 after 0.50 s at ~15°/s (speed 12.7). Never after a 240 ms flick.
- **Drag start threshold:** between 2.0° (45 px, no drag) and 2.3° (52 px, drag), on pins at 1.5 and 2.1 m.
- **Gesture cadence:** drag events every 30–40 ms; 18 per second on average.
- **fps:** app 58.2–60.0 during a drag with 12 tapes; the compositor 59–60 inside a fast period. A fast period lasts about 6 s from the focus-in.

## 5 · Regression (real pinches unless marked)

| Flow | Result |
|---|---|
| Tap–tap tape | **pass.** p365 on the bed, then p366: `TAPE add t275`, 157.3 cm, `COACH dismissed reason=first_tape`. Both landed within 1 px of the aimed pixel (my first aim hit the body's housing: `TAP other target=body`). |
| Pin → chip → tape | **pass.** `UI chip:p373` → `TAPE add t287 a=p367 b=p373`. |
| Steppers | **pass.** Each 1.0 mm: › +1.00 mm to the viewer's right, ‹ −1.00, ˄ +1.00 away, ˅ −1.00 nearer; tapes changed by about 1 mm (dump). |
| Tape strip retract | **pass.** `UI plate:t287` → `STATE tape-selected` → `UI tape_retract` → `TAPE remove … reason=retract`. |
| Undo ×3 | **pass.** After drag d1 (exact restore, rebase `reason=undo`), after the wall re-seat (`reason=undo-plane`, exact), after the retract (t287 back). |
| Units ×4 | **pass.** m, mm, ft·in, cm: `UNIT` each; ends on cm. |
| Language zh and back | **pass.** `LANG zh` / `LANG system` from the menu, twice. The zh strip has no words (shot 07). |
| Clear all → dialog → clear | **pass.** `UI menu_clear` → `STATE confirm-clear` → `UI dialog_confirm` → `CLEAR pins=14 tapes=13`, dump empty. |
| Relaunch restore | **pass.** force-stop + start (pid 503 → 19352): `RESTORE pins=14 tapes=13`, positions and lengths 0.0 mm off, cm / system / snap on kept, `SNACK restored`. |
| Exit + relaunch | **pass**, 2 / 2. `UI menu_exit` → `STATE exited`, `STAGE disposed`, same process. Relaunch restored 14 / 13 (see O2); the final one restored 0 / 0. |
| Press-and-pull on a surface | **pass.** Primed drag on the floor: `PIN add p379 via=pull`, `PULL start new=true`, tail `PIN add p380 via=pull` on the back wall (pointing-line gain, as in round 4 O4), `TAPE add t289`. |
| Controller mode | **not testable** (item 8). |

## 6 · Harness notes

- **Minimised window.** The emulator window is minimised (iconic, client 0 × 0). PostMessage input, `fast on` and console screenshots still work; `calib.json` still maps exactly (catcher presses landed at panel 80, 78 for 80, 80). `emu.sh info` prints a geometry WARNING that is harmless here. `emu_mode.ps1` cannot find the tool bar, so controller mode cannot be reached.
- **60 fps period.** About 6 s from the focus-in, and `waitfast` returns 1–3 s into it. Keep a scripted run to 4 s or less after `waitfast`. With the default 5 s budget two quick runs can share a dying period (one drag went to the catcher at 2 fps), so `r5t.run_path` uses `waitfast 1`.
- **Mid-drag screenshots.** `adb emu screenrecord screenshot` takes about 1 s and can run in the background from inside the drag (`s:` token). After a 1.1–1.5 s hold it shows the held state.
- **Reading descriptions.** `uiautomator dump` shows the window of the panel pressed last. Press a harmless control in a panel first (`lang_system` for the menu); then its content descriptions can be read.
- **Pressing a pin.** Aim at the cap of an *unselected* pin (R5-4). Mind candidate chips near pins (R5-8). A selected pin plus a surface tap pulls a tape: deselect with a tap on the pin itself.
- **Stage re-centring (O2).** Rotate the camera back physically before the final relaunch: `rot` to the old yaw, then a 5 px right-button drag for the last 0.2°.

## 7 · Screenshots (`.scratch/shots/round5qa/`)

1. `r5qa_01_mid_drag_ghost_reticle`: mid-drag, with:
   - the ghost ring at the origin;
   - the reticle and its seat at the target, with ×4;
   - the stretched tapes, with the A-B plate live at 99.2 and the C-A plate stale at 67.1;
   - the body live at 51.2;
   - the strip.
2. `r5qa_02_slow_x4_tag`: slow drag, "×4" tag; magenta = cursor, cyan = origin.
3. `r5qa_03_release_strip`: after release, with the strip and steppers, the plates committed, the stub showing, and "● ±5 mm".
4. `r5qa_04_reseat_on_wall`: the floor pin re-seated on the wall mid-drag, ghost on the floor, tapes following.
5. `r5qa_05_stub_pull_new_tape`: a new tape pulled from the stub, the pin unmoved, the live plate at 103.7 cm.
6. `r5qa_06_snapped_edge_seat`: the edge seat on the wall's bottom edge, ghost above.
7. `r5qa_07_zh_strip`: the strip in zh: no words.
8. `r5qa_08_menu_segments`: menu segments, items 40 dp drawn in a 48 dp control.
9. `r5qa_09_stale_plates_12_tapes`: 12 tapes mid-drag, D's three plates stale.
10. `r5qa_10_body_live_14_6`: the body's live readout in the same frame.
11. `r5qa_11_pin_approach_no_snap`: the pin frozen short of pin C, and a chip over the reticle.
12. `r5qa_12_final_state`: final state.

## 8 · State left behind

- App relaunched at 23:47: `RESTORE pins=0 tapes=0`, `STATE idle`, scene empty.
- Unit cm, language system, snap on, no snackbar.
- Coachmark re-armed: `COACH reset`, then `COACH step=1`, showing.
- Catcher off (`CALIB off` in the new Stage; `bounds` lists only `body` and `coachPanel`). Synthetic pointer gone.
- Eye mode: the mode was never changed, since the switch failed; guest controllers offline.
- Head at the start pose: `POSE` pos (0, 1.65014, 0), q (0, 0, 0, 1). The camera was turned back physically to the original view, with the bed in front (shot 12).
- `RIG summary slots=35 shown=2 violations=0`. The emulator window is still minimised and not foreground.
- No emulator setting changed. Log capture stopped.
