# Spatial Ruler: round 3 test (last fix round before the user demo)

Test agent, 2026-10-02, 18:12 – 19:13 (the device clock matches the host clock).

**Build under test:** the debug APK installed at 18:08:20 (not rebuilt).

**Device:** `emulator-5556`, AVD `PICO_6.1_Ruler`. The AVD name was checked before device work. `-5554` and `-5558` were never addressed.

**Input**
- 33 primed `clicks` sequences with 60 real target presses: every eye-mode pinch went through `tools/harness/emu.sh` (PostMessage).
- 1 primed real drag.
- 3 controller-mode trigger clicks.
- Head turns by right-button drags.
- Debug broadcasts were used only for setup, synthetic channels, read-back, the 8-tape load and cleanup.
- Every press was checked against its log line (`PTR panel=… ev=Press`, `UI …`, `TAP … stage=` reprojected to the intended pixel).

**Scripts** (new, in `tools/test/`):
- `r3t.py`: primed step runner; targets come from live `pose`, `bounds`, `dump` and `planes`.
- `r3_overlap.py`: chip, plate and tag rectangles in view, with their ranges.
- `r3_synth.py`: M1 and R2-1.
- `r3_t11.py`: lazy follow. It has no main guard, so do not import it.
- `r3_ctrl.py`: controller placements.

**Raw output** (`tools/test/out/round3/`):
- `app_log.txt`: every `SpatialRuler` line, 2 987 lines.
- `warn_log_app_pid.txt`: every W/E line of the app's pids.
- `presses.jsonl`: every primed sequence with its log lines.
- `ctrl.jsonl`, `overlap_last.json`, `pxcache.json`.

**Screenshots:** `.scratch/shots/round3test/` holds 10 curated crops; full frames and extras are in `round3test/evidence/`.

**H1 is still present.** The real cursor rests on the Ruler window at fb (1296, 1990) and that window is foreground.

**How every eye-mode press was made.**
- The head-locked catcher (`calib 0.6,1,80,0.069,-0.425 on`) was on.
- Each sequence started with a press on the catcher; 36 presses were absorbed there.
- In controller mode the catcher was off, because it would block the controller ray.

## 1 · Verdict

| # | Area | Verdict | Notes |
|---|---|---|---|
| 1 | Round-2 defects | **pass**, with one new defect from R2-4 | R2-1, R2-2, R2-3 and R2-4 fixed (§3). Undo across an anchor rebase does not restore the pin (**R3-1**, S2). |
| 2 | Design review 2, P1 | **pass** | F9, M1 and T11 meet their acceptance. T11 has a design consequence (R3-3). |
| 3 | Core flow, real pinches, eye mode | **pass** | Details in §1.3. |
| 4 | Controller mode | **pass** | 3 / 3 placements committed exactly at the steady reticle (0 mm, grade fine). Live blade and live plate. Body key (`AIM commit skipped reason=body` → `UI unit_cm`). Eye mode afterwards: `UI snap`. Half press is not testable (the emulator's trigger is binary). |
| 5 | Stability | **pass** | 0 ANR / FATAL / AndroidRuntime, 0 `RIG violation`, 0 W/E lines of the app's own tag. `FPS app=60.0` with 7 pins / 8 tapes. One E group at Exit is not new (§4). |
| 6 | Captures | **done** | 10 curated (§6), plus the no-surface reticle and the no-source snackbar in `evidence/`. |

### 1.3 · Core flow details (real pinches, eye mode)

- **Cold start:** body drawn 108 ms after `STATE starting`, build 1.46 s, then `COACH step=1`.
- **Coachmark:**
  - Step 1 changed to step 2 after the first pin.
  - "Got it" pressed with a real pinch: `UI coach_got_it`, `COACH dismissed`.
  - It stayed dismissed through relaunches.
- **Tap–tap:** t209 (241.7 cm ±8 cm) and t210 (120.2 cm ±5 cm).
- **Tape tag:** plate pinched, then Retract: `TAPE remove reason=retract`.
- **Pin → chip → tape:** t212 p275–p276.
- **Pin tag:** Fine-tune, then the steppers (`PIN nudge du=1`, `dv=-1`). The badge became "● Fine · ±8 mm".
- **Undo × 3:**
  - First undo removed a pin and its tape (`UNDO depth=5`).
  - Second undo came after the rebase; see R3-1.
  - Third undo removed the controller-stand-in pin (`UNDO depth=0`).
- **Units:** m, mm, ft·in and cm all logged `UNIT`.
- **Language:** zh via More → Language → 中文 (screenshot 06), then back to "system".
- **Clear all:** dialog opened 3 / 3; Cancel twice, then Confirm: `CLEAR pins=7 tapes=8`.
- **Exit and relaunch:** Exit 3 / 3. The relaunch after Exit #1 restored 4 pins / 2 tapes with the "restored" snackbar. Both relaunches after the clear restored 0 / 0.

## 2 · Defects found in round 3

Severity: S1 blocks the core flow or gives a wrong measurement, S2 major, S3 minor, S4 cosmetic.

### R3-1 · S2: Undo after an anchor rebase leaves the pin where the drag put it

**Repro.** Select p276 → Fine-tune → steppers (offset [1, −1]). Then a real primed drag on the pin, hand travel 0.855 m.

**What the drag did (correct).**
- `PIN true … du=201 dv=74` moved the pin from (0.7496, 0.4006, −1.9958) to (0.9632, 0.4006, −1.9958). That is 214 mm, exactly hand ÷ 4.
- `ANCHOR rebase start` came 0.60 s after `DRAG end`.
- Then `ANCHOR rebase … uuid_new=6de94657… shift_mm=0` and `ANCHOR removed uuid=ca114cd1…`.
- `dump` showed `off=[0, 0]`, the pin unmoved by the rebase, and t212 at 242.7 cm, equal to the distance between the pins.

**Then:** a real pinch on Undo.

**Expected.** The pin returns to (0.7496, 0.4006, −1.9958) and t212 to 241.8 cm. AGENTS.md says of this edge: "the position is right, the anchor id is reconciled to the live one".

**Actual.**
- The log shows `UNDO depth=7 pins=4 tapes=2`.
- `dump`: p276 at (0.9638, 0.4006, −1.9945), `off=[1, -1]`. The pre-drag offset was re-applied to the **new** anchor, so the pin stays 214 mm from where it was.
- t212 reads 242.8 cm.
- Nothing tells the user that Undo did (almost) nothing. The state survived Exit and relaunch.

**Scope.** Only after a fine-tune of more than 50 mm, which triggers the rebase, followed by Undo. Undo of a fine-tune without a rebase was not re-tested.

**Evidence:** `app_log.txt` 18:33:49 – 18:34:46.

### R3-2 · S3 (demo risk): "Raise a hand or pick up a controller" stays up on the emulator in eye mode

**Repro.** Any of these, then stay in eye mode:
- switch to controller mode and back (`emu_mode.ps1 -Mode right`, then `-Mode eye`);
- `source …` + `pointer …` followed by `hand_lost` or `pointer` (clear).

**Actual.**
- 3 s later: `SOURCE lost`, then `SNACK no_source … ms=sticky`. The text "Raise a hand or pick up a controller to continue" (zh: 抬起手或拿起手柄继续) sits at the top centre of the view.
- It stays there while gaze + pinch keep working.
- Only a new Stage clears it (relaunch).
- Seen 3 times: 18:42:45, after controller mode at 19:01, and after the final synthetic check at 19:06.

**Expected.** Not decided. This is the rule as built (AGENTS.md: "only shows after hand_lost (synthetic) or in controller mode"). But on the emulator, eye mode is a working input, and a demo that shows controller mode first will carry a permanent, untrue prompt afterwards.

**Recommendation.** Count the system pointer as a source in eye mode (`SystemPointer.onEmulator`), or drop the snackbar after the first system-pointer pinch.

### R3-3 · S3 (design question): after a long head turn the lazy-follow body can rest in front of the aim

**Repro.** Turn the head 90° right, then back to 0.

**Actual.**
- The body slides mid-turn and parks at the rest pose of that moment (`BODY park reason=slide … yaw=-24.2`).
- At the final heading it is 6° right of centre and 22° down. That is inside the 45° window, so it stays (as specified).
- With the head pitched up 10°, the line of sight to the floor 35° below centre runs into it:
  - `RETICLE state=blocked:body`;
  - two controller presses gave `AIM commit skipped reason=body`;
  - one of them pressed `unit_cm` instead of setting a pin.
- `body park` fixes it.

**Design question.** Re-park when the body has covered the aim or the view centre for ~1 s, or compute the slide target when the turn has settled (head velocity ≈ 0) instead of mid-turn.

**Evidence:** `evidence/r3_09_controller_mode_live_blade.png` (first take, body in the middle), `app_log.txt` 18:51:56 – 18:54:32.

### R3-4 · S4 (open P3 F14, not in round-3 scope): the body's tape mouth covers the pin tag's badge

**Where.** zh, pin tag in slot W next to the body.

**What.** The mouth stub (yellow, red, steel) is drawn over "● 细瞄"; only "±8 mm" is readable (`r3_06_zh_ui_crop.png`).

F14 (body keep-clear box including the mouth) is listed as P3 and is not built.

### Observations

**O1 · F9 margin.** A pinch at (1470, 1652) was not taken by either panel. That pixel lies 6 px inside the computed top edge of chip→p276 and 5 px inside the computed bottom edge of the t210 plate. The drawn pills do not actually overlap; there is a ~10 px gap between the drawn chip and the drawn plate.
- The pinch went to the floor: `TAP surface`.
- With p275 selected, that pulled a tape to a new pin (p279, t211).
- So neither "plate steals the chip's pinch" nor a dead margin occurred. The press fell through, which disagrees with the "a panel takes the pointer over its whole rectangle" note, unless my rectangles are off by ≥ 6 px.
- A real pinch on the chip's centre then connected exactly p275–p276.

**O2 · WindowLeaked at Exit is not new.** `E/WindowManager … WindowLeaked … LaunchActivity has leaked window DecorView{… 680,272 …}` appears once per pooled panel at each Exit (38× at 18:39:58). Round 2 had 37× at its Exit (`out/round2/warn_log.txt` 16:49:25). Relaunch is unaffected.

**O3 · No-surface reticle on a visible wall.** The ray found at fb (200, 400) of the start pose meets the white wall (no plane is detected there), yet the reticle says "No surface here". This is plane coverage, not the app.

## 3 · Round-2 defect status

| ID | Status | Evidence (all real pinches unless noted) |
|---|---|---|
| R2-1 no "raise a hand" snackbar | **fixed** | Synthetic `source controller` + `pointer` + `trigger` → `PIN add p280 via=controller`. `hand_lost` at 18:42:03.948, then `SOURCE lost` + `SNACK no_source … ms=sticky` at 18:42:06.943 (2.995 s). `dump` text "Raise a hand or pick up a controller to continue". `pointer` again → `SOURCE back`, `dump snack=None`. See R3-2 for the eye-mode side effect. |
| R2-2 pinch outside the open menu acts behind it | **fixed** (3 / 3) | Two floor pinches: `TAP surface …` then `TAP consumed reason=menu-open event=surface`, `STATE idle`, no `PIN add`. Plate t210: `UI plate:t210`, `TAP consumed reason=menu action=TapPlate`, no tape selection. Pin count unchanged throughout. |
| R2-3 menu rows only dismiss | **fixed** (3 / 3 and 3 / 3) | Default attachment-panel menu. "Clear all…": `PTR panel=menuPanel` → `UI menu_clear` → `STATE confirm-clear` (Cancel, Cancel, Confirm → `CLEAR pins=7 tapes=8`). "Exit Spatial Ruler": `UI menu_exit` → `STATE exited`, `STAGE close requested`, `STAGE disposed`, then relaunch with `RESTORE`. |
| R2-4 true-up capped at 50 mm | **fixed** (rebase); **new R3-1** on undo | Offset to 214 mm (du 201 / dv 74 in the line-of-sight frame). `ANCHOR rebase start` 0.60 s after rest, `shift_mm=0`, old anchor removed, `off=[0, 0]`, reading continuous (242.7 cm = pin distance), both tapes intact. |

## 4 · Results per item (design review 2 P1, controller, stability)

**F9: chips in front of plates, unrelated plates recede.**
- p275 selected. `r3_overlap.py` shows two chips overlapping the unrelated t210 plate (2.952 m):
  - chip→p278 at 2.902 m;
  - chip→p276 at 2.841 m.
- Both chips are nearer than the plate, by 5.0 and 11.1 cm, so the chips are in front.
- The pin tag stays in front of a chip (it covers 18 % of chip→p275, tag at 2.458 m vs. chip at 3.903 m).
- Chip texts are fully readable (`r3_03b`).
- The unrelated plate is see-through.
- Blade t210: `#FFC72D` with nothing selected, (205, 161, 59) with an unrelated pin selected.
- A real pinch on chip→p276 gave `UI chip:p276`, `TAPE add t212 a=p275 b=p276`, `STATE idle`, and never `tape-selected`.
- Only one chip was pinched (time), and see O1.

**M1: reticle.** Synthetic ray from a hand point, `source controller`.
- **Floor at 2.2 m:** steady ring about 54 × 37 px (short axis ≥ 24 required), stroke about 5 px, dark casing, cross and dot. Readable green "Steady" tag (`r3_07`, zoom in `evidence/r3_07_close_zoom.png`).
- **Floor at 4.5 m (`state=far`):** dashed orange ring with casing, about 37 × 20 px (≥ 34 px across required at 4 m). Orange "Over 3 m · less precise". The live tape's hook sits in its centre (`r3_08`, `evidence/r3_08_far_zoom.png`).
- **No surface:** hollow white dashed ring with casing and a neutral "No surface here" tag, no longer orange (M1.5 fixed; `evidence/r3_08b_reticle_none_crop.png`).
- **Controller mode:** the same steady ring, and the commit equals the last `RETICLE … stage=` exactly.

**T11: lazy follow.**
- **20° turn right:** no `BODY` line; body centre unchanged (0.0 mm), now 43.1° off-axis.
- **70° more:**
  - `BODY follow reason=angle angle=76.6`, then `BODY park reason=slide` 0.349 s later.
  - A second follow/park, 0.350 s, after the corrective drag.
  - End state: 0.600 m away, 22.2° left and 23.6° down (rest pose of the slide moment).
- **Surfaces:** no plane within 0.25 m of the body; 0 `RIG violation`.
- **Keys after the slide:** real pinches `UI unit_mm`, `UI snap`, `UI unit_cm`, all `PTR panel=body`, no `PIN add` (`r3_10`).
- The time from the turn's end to the slide's end was not isolated: the turn is a 1.9 s drag. Each slide is 0.35 s as specified.

**Controller mode** (`emu_mode.ps1 -Mode right`). Under H1, presses go to the real cursor, so placements were aimed at fb (1296, 1990) with the head turned between them.

| Pin | Head yaw / pitch | Reticle before the press | Commit | Commit − reticle |
|---|---|---|---|---|
| p281 | 0 / 0 | steady, stable 1.00, (0.2602, 0.4006, −1.7369) | `TAP pointer kind=controller steady=true grade=fine`, `PIN add … via=controller` | 0 mm |
| p282 | −20 / 0 | steady, (0.8361, 0.4006, −1.5519) | `PIN add`, `TAPE add t213 p281–p282 0.6048 m ±8 mm` | 0 mm |
| p283 | −35 / 0 | steady, (1.2092, 0.4006, −1.2826) | `PIN add`, armed | 0 mm |

- Every system tap of the same click was dropped: `TAP surface … RayBasedPinch ignored reason=tracked-source`.
- **Live blade** (`r3_09`): blade from the armed pin to the reticle, live plate "60.5 cm ±8 mm" (M3; not "…"), hook visible at the reticle (M2).
- **Body key** (18:54:31): `AIM commit skipped reason=body` → `UI unit_cm`.
- **Back in eye mode:** real pinch `UI snap` / `SNAP on`.

**Stability.**
- 0 ANR / FATAL / AndroidRuntime / `RIG violation` in 2 987 app lines.
- 0 W/E lines of the app's own tag.
- `FPS app=60.0 frames=60` with 8 tapes (19:02:11 – 14).
- The app pid's W lines are platform noise seen before: `libc` property access, `PXR-HWUI window already create`.
- WindowLeaked at Exit: O2.

## 5 · Harness notes (not product)

**H1 is unchanged** (cursor at fb 1296, 1990, emulator foreground). The catcher plus a primed `clicks` sequence (catcher, then targets, gap 300 – 1300 ms) made every eye-mode press land; 36 presses were absorbed by the catcher.
- A catcher press does **not** close the open attachment-panel menu, so "More → row" can be primed.
- **Repeated point in one `clicks` call:** the second press at the same pixel (no move in between) goes to the real cursor. This happened twice: `step_u_plus` × 2 and `snap` × 2. Snap was left off once and fixed with a later real pinch. **Fix:** `emu.ps1 clicks` should move 1 px before a repeated press.
- **In controller mode the catcher must be off** (it would take the controller ray), and targets must be brought under the real cursor by turning the head.

**`r3_t11.py` runs on import.** My own slip (`import r3_t11` in a read-back command) turned the head 20° before I killed it. No press was sent (checked in the log), and the pose was restored.

**`logcat '*:W'` for a whole session is about 90 MB** on this emulator. It was trimmed to `app_log.txt` and `warn_log_app_pid.txt`.

## 6 · Curated screenshots (`.scratch/shots/round3test/`)

`r3_01_start_coachmark_crop` · `r3_02_tape_with_plate_crop` · `r3_03b_chip_over_plate_crop` (selected pin, chips in front of the receded plate) · `r3_04_pin_tag_tuning_crop` · `r3_05_menu_panel_crop` · `r3_06_zh_ui_crop` (with the sticky zh snackbar and R3-4) · `r3_07_reticle_close_crop` · `r3_08_reticle_far_live_tape_crop` · `r3_09_controller_mode_live_blade_crop` · `r3_10_body_after_slide_crop`.

`evidence/` holds the full frames, the first selection (`r3_03`), the no-surface reticle, the no-source snackbar, and the reticle zooms.

## 7 · State left behind

- App running: fresh launch at 19:06:50 (pid 30044), `RESTORE pins=0 tapes=0`. The scene is cleared and nothing persisted (relaunches after the clear restored 0 / 0).
- Unit cm, language system, snap on.
- Coachmark re-armed (`COACH reset`, `COACH step=1` showing).
- No snackbar; synthetic pointer gone (`source auto`).
- Catcher off (calib off + new Stage).
- **Eye mode:** `emu_mode.ps1 -Mode status` reports both controllers offline. The last real pinch before the final relaunch was in eye mode (`UI dialog_confirm`, `UI menu_exit`).
- Head at the start pose: pos (0, 1.636, 0), yaw 0.00°, pitch 0.00°. The body is parked at its start rest pose.
- No emulator setting other than the interaction mode was touched (right → eye).
- The log capture was stopped.
