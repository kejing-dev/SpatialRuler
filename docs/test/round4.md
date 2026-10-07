# Spatial Ruler: round 4 QA (simplify spec, icons over words)

QA agent, 2026-10-05, 19:26 – 20:12 (device clock).

**Build under test:** the debug APK installed by the dev at 19:24:03 (not rebuilt). The dev's gate run of 19:24 (`.scratch/gates_r4.log`) passed: 237 JVM tests, design-style verifier PASS. QA did not re-run the gates.

**Device:** `emulator-5556`, AVD `PICO_6.1_Ruler`, checked before any device work. `emulator-5554` was never addressed.

**Input:**
- 74 primed `clicks` sequences with 135 real target presses (eye mode, PostMessage through `tools/harness/emu.sh`).
- 2 primed real drags.
- 1 controller-mode trigger click.
- Debug broadcasts only for setup: `place`, `connect`, `fail_next_anchor`, `elsewhere`, `tracking`, `blocked`, `snack long`, `coach reset`, the synthetic pointer, `lang` for the zh shots, and read-back.

**H1:** not present this round. The real cursor rested at physical (886, 933), outside the emulator window, and the emulator was not foreground. The catcher (`calib 0.6,1,80,0.069,-0.425 on`) still primed every sequence. It took exactly the 74 priming presses and no hijacked press. Every press was checked against its log line (`PTR … ev=Press`, `UI …`, `TAP … stage=` reprojected to the intended pixel, within 2 px).

**Scripts:** `tools/test/r4t.py` (r3t runner plus `pshot`, `ptshot`, `fshot`, `pcache`, `words`) and `tools/test/r4_relaunch.py` (launch, shot at body drawn, shot after `RESTORE`).

**Raw output:** `tools/test/out/round4/`:
- `app_log.txt`: 4 435 `SpatialRuler` lines.
- `app_pid_errors.txt`: every E line of the app's pids.
- `presses.jsonl`.
- `planes_*.json`.

**Screenshots:** 12 curated shots in `.scratch/shots/round4qa/`; full frames and extra crops in `round4qa/evidence/`.

## 1 · Verdict: PASS, with 3 S4 findings

There are no S1, S2 or S3 defects.
- Every icon control acted on a real pinch.
- Every surface is within its word budget in English and Chinese.
- The core flows are unchanged.
- There were no crashes and no `RIG violation`.

## 2 · Section M checklist

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Word budgets, en and zh | **pass** | Table in §3. My own poses were used (start pose plus selections), not the r3_01–r3_10 poses. |
| 2 | Body: controls; B table; value position | **pass** (PULLING not shot) | `bounds` lists exactly CTRL `more undo unit_m unit_cm unit_mm unit_ftin` on `body`. Rows seen with their annunciators: STARTING (spinner, "— —", cold start `r4_cold_spinner`), READY (none, `r4qa_01`), ARMED (`ic_tape` yellow, `r4qa_02`), TUNING (`ic_move` + live length, `r4qa_03`), LAST (`r4qa_05`), SELECTED (`r4qa_04`), LOST (red ⦸, value greyed, `r4qa_12`), BLOCKED (none, "— —", `r4_19_blocked_en`). The value starts at the same x in every shot. |
| 3 | Plates | **pass** (live plate not shot) | One line everywhere. `bounds`: 368×80 px = 184×40 dp. ▲ on the far tape t225 (`r4qa_04`) and on the unsaved end after `fail_next_anchor` (`r4_12b`, "▲ 87.8 cm"). ⦸ on every plate under `tracking s=lost`. Selected plate: yellow outline, and the strip shows "▲ ±8 cm". |
| 4 | Pin strip | **pass** | 608×140 / 608×256 px = 304×70 / 304×128 dp. Badges seen: "○ ±2 cm", "● ±8 mm", "◆ ±2 mm". Chevrons on a floor-level bed, head at identity, each step 1.0 mm: › = +x (right), ‹ = −x (left), ˄ = −z (away), ˅ = +z (nearer). Tape t224 changed 0.7119 → 0.7123 m (dump). A 1 mm change is below the body's 0.1 cm display step, so the live length was checked in `dump`, not on screen. |
| 5 | Tape strip | **pass** | One control (`tape_retract`, 166×70 dp). Retract 3/3 → `TAPE remove … reason=retract`. A second plate tap gives `STATE idle`; a bed tap gives `TAP surface` + `STATE idle`. Neither gives `PIN add`. |
| 6 | Menu | **pass** | Segments switch system / 中文 / EN live; the panel re-renders in place and stays open (`state=more-menu` after 13 switches). Snap logs `SNAP on/off`; off survived Exit + relaunch and on survived the final relaunch. Clear all is dimmed with no pins (press → no dialog) and opens the dialog with pins. Exit → `STATE exited` 3/3. |
| 7 | Reticle (synthetic) | **pass** (partly) | No tag when steady, or when snapped to pin, corner or edge (`reticleTag` absent from `bounds`). Half trigger → "×4" (`r4_20`). Wall at 4.45 m → "▲ > 3 m" (`r4qa_11`). No surface → tag present (32 dp icon pill) per `bounds`, but it fell just outside the console frame, so the ⦸ was **not seen**. **Not tested:** plumb, level, still mapping (scanning ended before a probe could reach it). |
| 8 | Coachmark | **pass** (see R4-3) | `coach reset` shows one panel: pictogram, 7 words, ✓. ✓ 3/3 → `COACH dismissed`. The first locked tape hid it (`COACH step=0`), and it stayed hidden through 3 relaunches. |
| 9 | Descriptions | **pass**, with R4-1 | Static check: undo, more, snap, globe, exit, tune / remove / close, steppers, retract, ✓, annunciators, reticle tag (on its container) and count groups carry `rs()` text. Null only on icons beside a word and on decorative header / pictogram icons. **Exception (R4-1):** the EN segment has no "English" description. Key mirror: the dev's `StringsResourceTest` passed. |
| 10 | Hit targets | **pass** (see R4-2) | Keys and buttons are 56×48 dp (Clear all 180×48, dialog buttons 120×48, snack action 48 high). Centre spacing: undo↔more 60 dp, strip 60 dp, steppers 56 dp, segments 58–78 dp. Steppers are 40×40 dp. |
| 11 | Real pinches 3/3 | **pass** | Counts in §5. |
| 12 | 模拟器验收路径 1–8 | **pass** | Covered by the regression in §5, with step 6 as in J. Not run as one scripted pass. |
| 13 | Glyphs without tofu | **pass** | ▲ ⦸ ◆ ● ○ ± × ′ ″ and > appear in en and zh. All 16 icons were seen: undo, more (body); snap, globe, trash, exit (menu); move, close, chevron (strips); retract; check (coach, restored); warning, retry (snack); no_surface (LOST, blocked); pin, tape (counts, coach). |
| 14 | `rig` / gates | **pass** (see O1) | `RIG summary slots=36 … violations=0`, or 37 with the debug catcher. 0 `RIG violation` in the session. Gates: the dev's 19:24 run. |

## 3 · Visible words per surface

Words counted from screenshots; snackbar text from `dump`. Figures, ±, unit symbols and glyphs are not counted.

| Surface | Budget (A / M1) | en | zh (characters) | Shots |
|---|---|---|---|---|
| Body, all states | 0 | 0 | 0 | r4qa_01–05, 09, 12 |
| Plates and chips | 0 | 0 | 0 | r4qa_04/05, r4_23 (zh) |
| Pin strip, collapsed | 0 | 0 | 0 | r4qa_02, r4_23/24 (zh) |
| Pin strip, tuning | 0 | 0 | 0 (same composable, no text) | r4qa_03 |
| Tape strip | 0 | 0 | 0 | r4qa_04 |
| Reticle tag | 0 | 0 ("×4", "▲ > 3 m") | 0 | r4_20, r4qa_11 |
| More menu | 2 + language names | 2 ("Clear all") | 4 (全部清除) | r4qa_06 / 07 |
| Clear dialog | ≤ 6 | 6 | 11 (清除后无法撤销。取消 清除) | r4qa_08, r4_17 |
| Blocked dialog | ≤ 13 | 13 | not seen live (see O3); 31 from strings | r4_19 |
| Coachmark | ≤ 8 / ≤ 12 | 7 | 9 | r4qa_01 / 09 |
| Snack: restored | ≤ 6 / ≤ 12 | 0 (✓ pin 3 · tape 2) | 0 | r4_10 |
| Snack: elsewhere | ≤ 6 / ≤ 12 | 4 + "Clear" | 5 + 清除 | r4_14 (zh) |
| Snack: unsaved | ≤ 6 / ≤ 12 | 3 + "Retry" | 5 + 重试 (strings) | r4qa_10 |
| Snack: saved / cleared / recovered | ≤ 6 / ≤ 12 | 1 / 2 / 2 | 3 (strings) / 5 / 5 | dump |
| Snack: no surface | ≤ 6 / ≤ 12 | 3 | 6 | r4_15 (zh) |
| Snack: still mapping | ≤ 6 / ≤ 12 | not reached; 2 from strings | 6 from strings | — |
| Snack: duplicate | ≤ 6 / ≤ 12 | 3 | 5 | dump |
| Snack: tape limit | ≤ 6 / ≤ 12 | 4 | 9 | r4_16 (zh) |
| Snack: lost (sticky) | ≤ 6 / ≤ 12 | 4 | 8 | r4qa_12, r4_11b |
| Snack: no source (sticky) | ≤ 6 / ≤ 12 | 5 | 8 | dump |

## 4 · Defects and observations

| ID | Sev. | Repro | Expected | Actual | Evidence |
|---|---|---|---|---|---|
| R4-1 | S4 | More → language segment, with a screen reader or uiautomator | L: the EN segment carries `lang_en` "English" | `MoreMenu.kt` renders `Text(lang_en_short)` only, so it is read as "EN". 中文 is self-describing. | `MoreMenu.kt:196` |
| R4-2 | S4 | `bounds` with the menu open | M10: ≥ 56×48 dp; F: language items 60 dp | Segment items are 40 dp high inside the 48 dp control: unit 74×40, language 55×40. All 21 segment pinches still landed. | `bounds` lines, §2 row 10 |
| R4-3 | S4 (log wording) | First tape with the coachmark showing | M8 / H: `COACH dismissed` | Only `COACH step=0` is logged on the tape path; ✓ logs `COACH dismissed`. The behaviour is correct and persisted. | `app_log.txt` 19:31:59 |

Observations (no defect):
- **O1.** The spec's M14 says 34 slots. The build has 36, matching AGENTS.md: 20 plates, plateLive, 6 chips, 8 named panels and the body. No extra panel.
- **O2.** After a restore with tapes, `idle-scanning` shows the last reading, not the scanning spinner. This is because `RulerViewModel` gives `shownTape` priority over `scanning` (as built, and B gives no priority).
- **O3.** A debug `lang zh` during BLOCKED logs `UI lang_zh` but no `LANG`, so the blocked dialog stays English. Users cannot do this (the dialog is modal).
- **O4.** The real press-and-pull (hand 0.41 / 0.61 m at 2 m) sent the tail to the ceiling (`via=pull`, 3.97 m). This is the pointing-line gain as designed.
- **O5.** The WindowLeaked group at each Exit (37 per Exit) is known O2 of round 3.

## 5 · Regression (real input unless marked)

| Flow | Result |
|---|---|
| Cold start | **pass.** Body drawn 0.9 s after `STATE starting`, spinner and "— —". Build 17.9 s, then `RESTORE`, `STATE idle`, `COACH step=1`. |
| Tap–tap | **pass.** p294 / p295 on the bed, t224 71.2 cm ±3 cm. Landed (1151,1450) and (1501,1501) for intended (1150,1450) and (1500,1500). One-line plate. |
| Pin → chip → tape | **pass.** `UI chip:p296` → `TAPE add t226`. The unrelated plate receded (r4_23). |
| Tape strip retract | **pass**, 3/3, each undone. |
| Undo | **pass**, 8/8. Removed pin and tape restored 3×, stray pin removed 1×. |
| Units | **pass.** m 3, cm 4, mm 2, ft·in 1. Last one or two digits dim by tolerance. No ± on plates. ′ ″ render. |
| Language zh and back | **pass**, live in place. |
| Clear all | **pass.** Cancel 3/3, Clear 4/4 (`CLEAR pins=8 tapes=20` …). Dialog: 6 words en, 11 characters zh. |
| Relaunch restore | **pass.** `RESTORE pins=3 tapes=2` → snackbar "✓ pin 3 · tape 2" (r4_10). Second restore 4 / 2. |
| Exit + relaunch | **pass**, 3/3. `STATE exited`, `STAGE disposed`, same process. |
| Press-and-pull | **pass.** Primed drag from p297: `PULL start`, `PIN add p300 via=pull`, `TAPE add t229`. |
| Fine-tune beyond 50 mm → rebase → undo | **pass.** Drag `PIN true du=161`, rebase 0.6 s later, `shift_mm=0`. Undo returns p294 exactly to (0.1169, 0.8656, −2.7187), t224 back to 71.2 cm, rebase `reason=undo shift_mm=0`. |
| Controller mode | **pass.** `-Mode right`, mouse aim. Far aim (3.14 m) shows the "▲ > 3 m" tag. Click → `TAP pointer kind=controller steady=true grade=fine` → p309 exactly at the reticle (0 mm); the `RayBasedPinch` tap was ignored. Back in eye mode, the no-source snackbar appeared and the first gaze pinch cleared it (`SOURCE back`, `UI unit_m`, `UI unit_cm`). |
| Stability | **pass.** 0 ANR, FATAL or AndroidRuntime. 0 `RIG violation`. 0 W/E lines of the app tag. `FPS app=59.7–60.3` with 8 tapes; 55 with 20 tapes. |

Real-pinch counts (`presses.jsonl` and logcat):
- **Body:** Undo 8, More 14, unit segments 10.
- **Menu:** snap 4, lang_zh 3, lang_en 3, lang_system 5, Clear all 7, Exit 3.
- **Dialog:** Cancel 3, Clear 4.
- **Pin strip:** tune 3, remove 3, close 5. Chevrons ‹ 3, › 3, ˄ 3, ˅ 3.
- **Other:** Retract 3, plate 6, chip 1, coach ✓ 3, snackbar Retry 3, elsewhere Clear 1, blocked Retry 1.

## 6 · Harness notes

- **H1 absent.** The cursor was off the window and the emulator was not foreground, so the catcher took only the priming presses.
- **Sequence length.** The 5th target of a primed sequence was lost twice, and one 6th: the 60 fps period ended. Keep sequences to 4 targets.
- **Fresh strip.** One press on a freshly re-bound strip logged nothing (content not up yet). Repeated presses fixed it.
- **My own mis-aims.**
  - A pixel on the body's yellow housing gave `TAP other target=body`.
  - A cached strip pixel used while the strip was closed set a pin on the wall (p296, kept and used as the far pin).
  - (200, 400) now hits a CEILING plane (round 3's no-plane pixel is no longer one). The stray pin was undone by a real Undo. No-plane pixels must come from `planes` at run time.
- **`uiautomator dump`** sees only the active window (the coach, the catcher or the Stage view): useless for panel text.
- **Coach panel.** It needs more than 2 s after `coach reset` before `bounds` lists it.

## 7 · State left behind

- App relaunched at about 20:11 (new pid): `RESTORE pins=0 tapes=0`, so the scene is empty after relaunch.
- Unit cm, language system, snap on.
- Coachmark re-armed: `COACH reset`, then `COACH step=1` showing.
- No snackbar. Synthetic pointer gone (new process). Catcher off (`calib off` + new Stage).
- **Eye mode:** `emu_mode.ps1 -Mode status` reports both controllers offline. The last real pinches were eye-mode (Clear, Exit).
- Head at the start pose: pos (0, 1.65, 0), identity. Body at its start rest pose. `RIG summary slots=36 violations=0`.
- No emulator setting was changed other than the interaction mode (right → eye). The log capture was stopped.
