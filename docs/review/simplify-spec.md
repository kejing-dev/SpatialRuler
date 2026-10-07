# Simplify spec: icons over words (round 4)

2026-10-05 · product design · for the one dev round after round 3 · binding over `design/design-doc.md` §3 where they disagree (Layout; Surface sizing rows of body, plate, tags, menu, coachmark, dialog, snackbar, reticle tag; Components; 文案) · everything not named here stays as built.

User direction: 「要简单一点 UI UX，能用图标的 UI 不用文字，简单好用，争取一两次迭代就做到可用的产品」. Scope for one round: no new panels, no new 3D, no new tokens; panels shrink and some go. Review-2 items touched: L7 and F17 become moot, F15 is done in B, M3 is withdrawn in C.

## A · Principle

Text may stay only as:
1. Figures: digits, `±`, `>`, `×4`, unit symbols (m cm mm ft·in ′ ″), "— —".
2. Language names: 中文, EN.
3. One sentence where saved data is destroyed or the app cannot run (clear dialog, blocked dialog).
4. A snackbar message of ≤ 6 words (zh ≤ 12 characters), always after an icon.
5. One word on a button, only in a dialog, a snackbar and the menu's Clear all, always with its icon.
6. The first-run coachmark: ≤ 8 words (zh ≤ 12 characters) beside its pictogram.

Everything else is a vector icon (K) or a text glyph the design already defines: ◆ ● ○ grade, ▲ far or unsaved (`alert`), ⦸ frozen or unavailable, dimmed digits for precision. World panels (plates, chips, strips, reticle tag) carry no words at all. Removed copy survives only as content descriptions (L).

| Surface (en words; figures not counted) | Before | After |
| --- | --- | --- |
| Body panel | 1–4 (context line) | 0 |
| Reading plate | 0–1 ("Unsaved", "Frozen") | 0 |
| Pin tag | 6–9; tuning 12–15 | 0 |
| Tape tag | 7 | 0 |
| Reticle tag | 1–3 | 0 |
| More menu + language submenu | 8 | 2 |
| Clear dialog | 23 | 6 |
| Blocked dialog | 22 | 13 |
| Snackbars | 1–19 (Unsaved: 19) | 0–5 |
| Coachmark | 15, then 21 | 7, one step |

## B · Body panel

Panel unchanged: 340 × 136 dp, scale 0.75, `Material.Regular`, padding 12, content 316 × 112.
- Row 1, 56 high: readout 196 × 56 · 4 · Undo `IconButton` 56 × 48 · 4 · More `IconButton` 56 × 48.
- 8 gap. Row 2, 48 high: unit `SegmentControl` 316 × 48, four items of 79 dp, symbols as now.
- Snap leaves the body for the More menu (F). It is changed rarely, yet as a solid yellow key it was the loudest thing on the instrument. Its 60 dp go to the number.

Readout window 196 × 56, `fillPrimary`, radius 12, padding start 12 / end 10, one row, centred vertically:
- Value, left-aligned: `ReadingValue` with `headlineLarge` overridden to 32 / 38 sp, weight 600, tabular figures; unit `titleMedium` 16 sp; digits dim as built (≥ 1 cm the last digit, ≥ 10 cm the last two); `labelPrimaryLight`, `onInkSecondary` while lost; "— —" without a reading. The widest value ("12′ 11.4″") is about 125 of the 146 dp available.
- Annunciator at the right end: a 20 dp box, empty in idle states, so the value never moves.

| `LcdContext` | Annunciator (20 dp) | Value |
| --- | --- | --- |
| BLANK | spinner 18 dp, `onInkSecondary` | nothing |
| STARTING, SCANNING | spinner | — — |
| blocked (phase BLOCKED) | none (F15) | — — |
| READY | none | — — |
| ARMED, PULLING | `ic_tape`, `tapeYellow` | — — / the live length |
| TUNING | `ic_move`, `labelPrimaryLight` | live length of the pin's latest tape, or — — |
| LAST, SELECTED | none | that tape's reading |
| LOST | `ic_no_surface` (⦸), `error` | the frozen reading |

The ± is not on the body: precision shows as dimmed digits, the exact ± is on the selection strips (D, E). Delete `ContextLine`, its 14 → 11 sp shrink logic and the context sentences (L keeps four as descriptions). The plane count is no longer shown.

## C · Reading plates

- One line, always: [mark] value unit. Marks: ▲ in `alert` (far or unsaved end), ⦸ in `onInkSecondary` (frozen). This is today's compact form (beyond 6 m), now the only form. The tolerance line, "…", "· Unsaved" and "· Frozen" go, and so do `plate.compact` and the "more than 8 plates" rule.
- The live plate is one line too: the counting length, ▲ while the aim is far. M3's live ± is withdrawn: the stability ring already asks for stillness, and a second changing number beside a counting one is noise. Keep computing `LiveTolerance` (its `far` drives ▲).
- Selected plate: yellow outline as built; its ± is shown in the tape strip (E).
- `PlateWidth` 184 dp (pill 96–184 as built), `PlateHeight` 58 → 40 dp, pill 40 high (5 + 30 + 5). The pill fills the panel's height, so no empty band can swallow a pinch (AGENTS finding 5). Plate placement, stems and overlap tests read the new height; `plateLive` likewise. Chips unchanged.

## D · Pin tag → pin strip

`pinTag` 304 × 70 dp, `pinTagTune` 304 × 128 dp (were 264 × 164 / 264 × 272). `TagCard` as built (3 dp `Material.Regular` rim, `plateInk` card, radius 15), card padding 8. Icons 24 dp.
- Row, 48 high: badge box 98 · 8 · Fine-tune `ToggleIconButton` 56 × 48 (`ic_move`; checked `interaction` + `tapeInk`, unchecked `onInkFill` + `labelPrimaryLight`) · 4 · Remove `IconButton` 56 × 48 (`ic_trash`) · 4 · Close `IconButton` 56 × 48 (`ic_close`, was `Small`). Remove and Close: `onInkFill` + `labelPrimaryLight`.
- Badge: `Badge` (Regular), `onInkFill`; text = grade glyph (◆ contact, ● fine, ○ coarse) + " ▲" when far or unsaved + " ±x", e.g. "○ ▲ ±8 cm"; `labelLarge` 14 / 18 · 600, tabular; `labelPrimaryLight`, `alert` when warned. "Snapped·Corner / Edge" and "Pinning" are dropped: the ± already includes the snap, and the pin's collar shows pinning.
- The title "Pin · Floor" is dropped. The strip stands beside its pin, and the surface name does not help to measure.
- Close stays: while a pin is selected, a tap on a surface pulls a tape to it, so there is no neutral tap-away.

Tuning (`pinTagTune`): the row, then 8 · `Divider` (`onInkFill`) · 8 · one row of two `Stepper`s, 133 × 40 each, 16 apart (neighbouring buttons 56 dp centre to centre).
- Left stepper: decrease = chevron left ‹, increase = chevron right ›. Right stepper: decrease = chevron down ˅ (nearer; down on a vertical surface), increase = chevron up ˄ (farther; up). Stepper icons 16 dp.
- Value: the signed integer in mm ("+2", "0", "−1"), `titleMedium` tabular, as built.
- No row labels, no "1 mm / step": the chevrons are the axes, and the body shows the live length.
- Chevrons, not ◀ ▶ ▲ ▼, because a filled ▲ already means "warning" on plates and badges.
- Check on the emulator that › moves the pin right and ˄ away or up as seen from the head; if not, swap the sign in the composable, not in the domain.

## E · Tape tag → tape strip

`tapeTag` 166 × 70 dp (was 264 × 184): `TagCard`; row: badge box 80 (`Badge` as in D, "±x", or "▲ ±x" when warned) · 8 · Retract `IconButton` 56 × 48 (`ic_retract`, 24 dp).
- No close: a second tap on the plate, or a tap on a surface or on nothing, deselects with no side effect (`tapPlate` toggles; `placePin` and `tapNothing` only clear `selectedTapeId`).
- No end grades: each pin's strip shows its own.
- Placement: Appendix A as built (slot N first), fed the new size.

## F · More menu

`menuPanel` 256 × 120 dp, scale 0.75, `Material.Thick`, radius 24, padding 8, opening upwards from More as built. `langPanel`, `MenuState.LANGUAGE` and the `language-menu` state are deleted.
- Row 1, 48 high: language `SegmentControl` 180 × 48 with three items of 60 dp: [`ic_globe` 20 dp] = follow system, [中文], [EN], styled like the unit segments · 4 · Snap `ToggleIconButton` 56 × 48 (`ic_snap`, today's body colours).
- 8 gap. Row 2, 48 high: Clear all `Button` 180 × 48, `CircleShape`, `fillTertiary`, `ic_trash` 20 dp + "全部清除 / Clear all" in `labelPrimary` (disabled, `labelQuaternary`, without pins) · 4 · Exit `IconButton` 56 × 48, `fillPrimary`, `ic_exit` in `labelPrimaryLight`.
- Language and snap change in place and the menu stays open, so the result is seen (the panel re-renders in the new language). Clear all closes the menu and opens the dialog. Exit as built.
- Labels: Clear all keeps two words. It is the only irreversible action, and its trash glyph also means "remove this pin" on the strip; only a word says "all". Exit is an icon only: nothing is lost, and everything comes back on reopen. Snap is an icon whose fill is its state.
- Delete the native `Menu` / `SubMenu` path (`MoreMenu`, `overlay menu native`): the panel has been the product host since round 3.

## G · Clear dialog

Card 400 dp wide in the unchanged 480 × 280 `dialogPanel`, hugging its content (about 212 dp), `Material.Thick`, radius 32, padding 32, modal as built.
- Row 1, 40 high: `ic_trash` 32 dp · 16 · `ic_pin` 24 + n · 16 · `ic_tape` 24 + m. Figures `headlineLarge` 24 / 30, tabular; all `labelPrimary`. The count group has one description: "4 颗钉、2 条尺 / 4 pins, 2 tapes".
- 12 gap. One sentence, `bodyLargeMultiline` 16 / 24: 清除后无法撤销。/ This can't be undone.
- 24 gap. Buttons at the right, 8 apart, 48 high, ≥ 120 wide, capsule: [`ic_close` + 取消 / Cancel] `fillTertiary`, `labelPrimary`; [`ic_trash` + 清除 / Clear] `error` fill, `tapeInk` text and icon.

Blocked dialog, same card: row 1 = `ic_no_surface` 32 dp + the title (24 / 30, may wrap to two lines); one sentence; buttons [`ic_exit` + 退出 / Exit] `fillTertiary` and [`ic_retry` + 重试 / Retry] `interaction` + `tapeInk`. Title and sentence stay: the fix is in system settings, which no glyph can name.

## H · Coachmark and snackbars

Coachmark: kept, one step. It is the only place where press-and-pull, the signature move, is taught, and it now costs 7 words once per install.
- `coachPanel` 360 × 80 dp (was 148), `Material.Thick`, radius 24, padding 16, mounted beside the body as built.
- Row: pictogram (`ic_pin` · `ic_tape` · `ic_pin`, 24 dp, 2 apart, `labelPrimary`) · 12 · text `bodyLargeMultiline` 16 / 24, ≤ 2 lines, `labelPrimary` · 8 · ✓ `IconButton` 56 × 48 (`ic_check`).
- Shown from the start until the first tape locks or ✓. "1 / 2" and step 2 go; the log keeps `COACH step=1` and `COACH dismissed`.

Snackbar row: leading icon 24 dp (or spinner 20 dp) · 12 · message `titleMedium` 16 / 20 · 600, one line · 16 · optional action `Button` 40 high, `interaction` fill, `tapeInk`, icon 20 + one word. No description line. `widthIn(min = 240, max = 560)`, 56 high. Icons on the glass are `labelPrimary`: orange or red on the light glass falls below 3 : 1.

| Kind | Icon | After the icon (en; zh in L) | Action |
| --- | --- | --- | --- |
| RESTORED | `ic_check` | `ic_pin` n · `ic_tape` m (no words) | — |
| ELSEWHERE | `ic_pin` | k + "not in this space" | `ic_trash` Clear |
| UNSAVED | `ic_warning` | "Pin not saved" | `ic_retry` Retry |
| SAVED / CLEARED / RECOVERED | `ic_check` | "Saved" / "All cleared" / "Tracking recovered" | — |
| NO_SURFACE | `ic_no_surface` | "No surface here" | — |
| NOT_MAPPED | spinner | "Still mapping" | — |
| DUPLICATE | `ic_tape` | "Tape already exists" | — |
| TAPE_LIMIT | `ic_tape` | "Max 20 tapes · retract one" | — |
| LOST (sticky) | `ic_no_surface` | "Tracking lost · look around" | — |
| NO_SOURCE (sticky) | `ic_warning` | "Raise a hand or controller" | — |

## I · Reticle tag

The tag shows only where the reticle has no figure of its own. No word remains: "×4" and "> 3 m" are figures.

| Aim | Tag | Colour |
| --- | --- | --- |
| fine (half press) | ×4 | `labelPrimaryLight` |
| far (> 3 m) | ▲ > 3 m | `alert` |
| no surface | `ic_no_surface` 16 dp | `onInkSecondary` |
| still mapping | spinner 14 dp | `onInkSecondary` |
| steady, corner, edge, pin, plumb, level, contact | none | already said by the merged green ring, the seat bars (yellow corner / edge, green plumb / level), the pin itself and the diamond |

- Label rule in the tracked-aim path of `RulerScene`: fine, else far, else none. Snap kinds no longer win, so a far snapped aim shows ▲.
- `reticleTag` 96 × 24 dp (was 150), pill 32–96, drawn stretched as built. Pointer-channel seat marks stay unlabelled 3D figures.

## J · Flow simplifications

| Change | Removed |
| --- | --- |
| Snap: body → menu | one body key |
| Language submenu → segment | `language-menu` state, `langPanel` (pool 35 → 34), `menu_language` |
| Tape tag → ± + retract | close, end grades, `tape_close` |
| Plates one line | the tolerance line on every plate, the compact rule |
| Pin tag → strip | title, row labels, step hint |
| Coachmark one step | step 2 |
| Reticle tag in 4 of 11 states | 7 labels |

Kept on purpose:
- the pin strip's Close (D);
- Exit: a Full Space app covers the room, so the way out should be one glance away, and it costs one icon;
- two strips, because pin and tape have different actions;
- the tune toggle: it also arms drag truing, and always-on steppers would double the strip during "connect any two";
- Retry on an unsaved pin; chips and threads.

Core job check: placing pins, reading distances (body number 24 → 32 sp), connecting any two, undo, units, language and persistence are all unchanged; snap and language stay persisted. 模拟器验收路径 step 6 now reads: units on the body; More → language segment, snap toggle, Clear all → dialog, Exit.

Debug names: `snap`, `lang_system|lang_zh|lang_en`, `menu_clear` and `menu_exit` now live on `menuPanel`. `menu_language`, `tape_close` and `langPanel` are gone. Stepper names stay (`step_u_minus` ‹, `step_u_plus` ›, `step_v_minus` ˅, `step_v_plus` ˄); `coach_got_it` is the ✓. Update `tools/test`, `tools/harness/TESTING.md` and AGENTS.md.

## K · Icon inventory (16)

Rules: viewport 24 × 24, stroke 2.2, round caps and joins, white, tinted where used; drawing within 2–22 (20 dp optical). Existing `ic_close`, `ic_check` and `ic_no_surface` move to stroke 2.2. Delete `ic_minus` and `ic_plus`. The chevron is one drawable turned with `Modifier.rotate` (0 ›, 180 ‹, 90 ˅, 270 ˄).

| Icon | Meaning | Used in (display dp) | Geometry (`pathData`; stroked unless "fill") |
| --- | --- | --- | --- |
| `ic_undo` | undo | body (24) | existing: `M9,4L4,9l5,5` · `M4,9h9.5a5.5,5.5 0 0 1 0,11H10` |
| `ic_more` | more | body (24) | existing: fill, dots r 2 at (5,12) (12,12) (19,12) |
| `ic_snap` | snap | menu toggle (24) | existing: `M6,3v9a6,6 0 0 0 12,0V3` · `M3.5,7.5h5M15.5,7.5h5` |
| `ic_close` | close, cancel | pin strip (24), dialog Cancel (20) | existing: `M6,6L18,18M18,6L6,18` |
| `ic_check` | done | coach ✓ (24); snack Restored, Saved, Cleared, Recovered (24) | existing: `M5,12.5l4.5,4.5L19,7` |
| `ic_chevron_right` | one step that way | steppers (16), rotated | existing: `M9,5l7,7l-7,7` |
| `ic_no_surface` | ⦸ unavailable, frozen | annunciator LOST (20), reticle tag (16), blocked dialog (32), snack No surface / Lost (24) | existing: circle r 8 at (12,12) · `M6.5,17.5L17.5,6.5` |
| `ic_warning` | ▲ attention | snack Unsaved, No source (24) | existing: fill `M12,3.5L22,20.5H2Z` |
| `ic_move` | fine-tune: move this pin | pin strip toggle (24), annunciator TUNING (20) | `M12,3V21M3,12H21` · `M9.5,5.5L12,3l2.5,2.5` · `M9.5,18.5L12,21l2.5,-2.5` · `M5.5,9.5L3,12l2.5,2.5` · `M18.5,9.5L21,12l-2.5,2.5` |
| `ic_trash` | remove, clear | pin strip (24), menu Clear all (20), dialog header (32) and Clear (20), snack Elsewhere action (20) | lid `M4,7H20` · handle `M9.5,7V5a1,1 0 0 1 1,-1h3a1,1 0 0 1 1,1v2` · can `M6.5,7l1,12.5a1.5,1.5 0 0 0 1.5,1.5h6a1.5,1.5 0 0 0 1.5,-1.5l1,-12.5` · ribs `M10,11v6M14,11v6` |
| `ic_retract` | retract this tape into its case | tape strip (24) | case `M4.5,5V19` · shaft `M20,12H8.5` · head `M12,8.5L8.5,12L12,15.5` |
| `ic_pin` | pin(s) | counts in dialog, Restored, Elsewhere (24); coach pictogram (24) | head, fill: `M8.5,7.5a3.5,3.5 0 1 0 7,0a3.5,3.5 0 1 0 -7,0` · shank and washer `M12,11V20M6.5,20H17.5` |
| `ic_tape` | tape(s); a tape is out | annunciator ARMED / PULLING (20), counts (24), snack Duplicate / Tape limit (24), coach (24) | band `M4,8H20a1.5,1.5 0 0 1 1.5,1.5v5a1.5,1.5 0 0 1 -1.5,1.5H4a1.5,1.5 0 0 1 -1.5,-1.5v-5A1.5,1.5 0 0 1 4,8Z` · ticks `M6.5,8v3M10.5,8v4.5M14.5,8v3M18.5,8v4.5` |
| `ic_globe` | follow the system language | language segment (20) | `M3,12a9,9 0 1 0 18,0a9,9 0 1 0 -18,0` · `M12,3a4,9 0 1 0 0,18a4,9 0 1 0 0,-18` · `M3,12H21` |
| `ic_exit` | exit Spatial Ruler | menu (24), blocked dialog Exit (20) | door `M11,4H6a1,1 0 0 0 -1,1v14a1,1 0 0 0 1,1h5` · arrow `M10,12H20` · head `M16.5,8.5L20,12l-3.5,3.5` |
| `ic_retry` | try again (↻) | snack Retry (20), blocked dialog Retry (20) | arc `M19.24,10.06A7.5,7.5 0 1 1 15.75,5.5` · head `M12.3,6.1L15.75,5.5L14.55,2.2` |

## L · Copy (zh / en)

Visible strings. Unit symbols, "— —", "×4", "▲ > 3 m", 中文 and EN (`lang_en_short`, new) are the same in both languages and not translatable.

| Key | zh | en |
| --- | --- | --- |
| `menu_clear` | 全部清除 | Clear all |
| `dialog_clear_body` | 清除后无法撤销。 | This can't be undone. |
| `action_cancel` / `action_clear` (new) | 取消 / 清除 | Cancel / Clear |
| `dialog_blocked_title` | 空间能力不可用 | Spatial features unavailable |
| `dialog_blocked_body` | 请在系统设置中允许透视、平面识别和空间锚点。 | Allow passthrough, plane detection and anchors in Settings. |
| `action_retry` / `action_exit` | 重试 / 退出 | Retry / Exit |
| `coach_hint` (new) | 点两个点，或按住拉出 | Tap two points, or hold and pull |
| `snack_elsewhere` (now a string, after "k") | 不在此空间 | not in this space |
| `snack_unsaved` | 钉没能保存 | Pin not saved |
| `snack_saved` / `snack_cleared` / `snack_recovered` | 已保存 / 已全部清除 / 定位已恢复 | Saved / All cleared / Tracking recovered |
| `snack_no_surface` / `snack_not_mapped` | 这里没有表面 / 表面还在识别 | No surface here / Still mapping |
| `snack_duplicate` | 已有这条尺 | Tape already exists |
| `snack_tape_limit` | 最多 20 条尺，先收回一条 | Max 20 tapes · retract one |
| `snack_lost` | 定位丢失 · 慢慢环顾 | Tracking lost · look around |
| `snack_no_source` | 抬起手或拿起手柄 | Raise a hand or controller |

Content descriptions (never shown; required on every icon-only control and status glyph):

| Where | Key | zh | en |
| --- | --- | --- | --- |
| Undo, More, Snap | `action_undo`, `action_more`, `action_snap` | 撤销, 更多, 吸附 | Undo, More, Snap |
| globe segment, EN segment | `lang_system`, `lang_en` | 跟随系统, English | System default, English |
| Exit | `menu_exit` | 退出空间尺 | Exit Spatial Ruler |
| Fine-tune, Remove, Close | `pin_tune`, `pin_remove` (reworded), `action_close` | 微调, 删除钉, 关闭 | Fine-tune, Remove pin, Close |
| ‹ / › | `step_left`, `step_right` (new) | 向左 1 mm, 向右 1 mm | Left 1 mm, Right 1 mm |
| ˅ / ˄ on a horizontal surface | `step_near`, `step_far` (new) | 靠近 1 mm, 远离 1 mm | Nearer 1 mm, Farther 1 mm |
| ˅ / ˄ on a vertical surface | `step_down`, `step_up` (new) | 向下 1 mm, 向上 1 mm | Down 1 mm, Up 1 mm |
| Retract, coach ✓ | `tape_retract`, `coach_got_it` | 收回这条尺, 知道了 | Retract this tape, Got it |
| Annunciators: spinner, tape, move, ⦸ | `lcd_starting`, `lcd_armed`, `lcd_tuning_plain`, `lcd_lost` | 正在准备…, 拉到下一点, 微调, 定位丢失 · 已冻结 | Getting ready…, Pull to next point, Tuning, Tracking lost · frozen |
| Reticle tag, badges, count groups | `reticle_fine_gear/far/none/mapping`, `grade_contact/fine/coarse/far/unsaved`, `count_pins`, `count_tapes` | as today | as today |

Delete: `lcd_scanning`, `lcd_ready`, `lcd_live`, `lcd_last`, `lcd_selected`, `lcd_tuning`, `menu_language`, `pin_title`, `surface_*`, `axis_*`, `step_hint`, `step_decrease`, `step_increase`, `grade_snapped_*`, `grade_pending`, `grade_frozen`, `tape_start`, `tape_end`, `reticle_steady|corner|edge|pin|plumb|level|contact`, `dialog_clear_title`, `action_clear_all`, `snack_restored`, every `snack_*_desc`, `snack_input`, `snack_elsewhere_action`, `coach_1`, `coach_2`, `action_set_pin`, `menu_snap_on`, `menu_snap_off`.

## M · QA acceptance

1. Words, en and zh, at the poses of `round3test` r3_01–r3_10: body 0, plates and chips 0, pin strip 0 (both forms), tape strip 0, reticle tag 0, menu 2 plus language names, clear dialog ≤ 6, blocked dialog ≤ 13, each of the 12 snackbar kinds ≤ 6 words / ≤ 12 zh characters (text from `dump`), coachmark ≤ 8 / ≤ 12. Figures, ±, unit symbols and ▲ ⦸ ◆ ● ○ × — ′ ″ do not count.
2. Body: `bounds` lists exactly CTRL `undo`, `more`, `unit_m|cm|mm|ftin` on `body`. Each row of the B table is captured with its annunciator (fresh launch, `coach reset`, `place`, `tap_pin`, `ui pin_tune`, a drag, `tracking s=lost`, `blocked s=on`), and the value does not move sideways between states.
3. Plates: every unselected and live plate is one line, and `bounds` gives 184 × 40 panels. ▲ shows on a far plate and after `fail_next_anchor`; ⦸ shows under `tracking s=lost`. A selected plate has the yellow outline and the tape strip with its ±.
4. Pin strip: 304 × 70 / 304 × 128; badge = grade glyph + ± (▲ when far). Each chevron moves the pin 1 mm in its own direction as seen (› right, ‹ left, ˄ away or up, ˅ nearer or down), and the body's live length follows.
5. Tape strip: one icon; Retract logs `TAPE remove`; a second tap on the plate and a tap on the floor both deselect without `PIN add`.
6. Menu: the segments switch system / 中文 / EN live and the menu stays open; the snap toggle logs `SNAP` and survives a relaunch; Clear all is disabled without pins and opens the dialog; Exit gives `STATE exited`.
7. Reticle (synthetic pointer): `steady s=on` and snaps to corner, edge, pin, plumb and level show no tag but their figure; `trigger s=half` shows "×4"; an aim beyond 3 m shows "▲ > 3 m"; no surface shows ⦸; still mapping shows the spinner.
8. Coachmark: `coach reset` shows one panel (pictogram, ≤ 8 words, ✓); the first locked tape or ✓ hides it for good (`COACH dismissed`).
9. Every `IconButton`, `ToggleIconButton`, icon-only segment, stepper icon, annunciator and reticle glyph has a non-empty description from `rs()`, and each key exists in `values/` and `values-zh/` (grep check in `tools/dev.sh gates`, or a JVM test over the resource files).
10. Hit targets from `bounds`: keys and buttons ≥ 56 × 48 dp, stepper buttons the component's 40 dp, and ≥ 56 dp centre to centre between neighbouring controls on `body`, `menuPanel`, `pinTag`, `pinTagTune`, `tapeTag`, `coachPanel`, `dialogPanel` and `snackPanel`.
11. Real pinches (emulator mouse, 3 / 3 each): Undo, More, one unit, snap toggle, all three language segments, Clear all → Cancel and → Clear, Exit, pin strip tune / remove / close, all four chevrons, Retract, coach ✓, snackbar Retry.
12. 模拟器验收路径 steps 1–8 pass, with step 6 as in J.
13. Glyphs render without tofu in en and zh: ▲ ⦸ ◆ ● ○ ± × ′ ″ and all 16 icons at their display sizes.
14. `rig` shows 34 slots and no `RIG violation`; `tools/dev.sh gates` passes (SpatialUI only, colours from tokens); the JVM tests pass after updating those that assert removed strings, coach step 2 or `language-menu`.
