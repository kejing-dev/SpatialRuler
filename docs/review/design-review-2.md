# Design review 2: milestone 2 (rounds 2a and 2b)

2026-10-02 · product design, review turn · build under review: rounds 2a and 2b on `emulator-5556` · binding design: `design/design-doc.md` (§ references below point into it) and `docs/review/design-review-1.md` with its addendum

**Reviewed.** The crops named for this turn in `.scratch/shots/round2a/` and `round2b/`, plus their full frames (2160 × 2158) wherever a crop was ambiguous. Positions given as `file.png (x, y)` are full-frame pixels. Sizes in degrees use the camera model of `tools/ctrlpx.py` (fx = fy = 1286.8 px), which gives 22.5 px per degree at the centre and more towards the edges. Contrast is measured as in review 1: the WCAG ratio between the median background and the 3 % of glyph pixels farthest from it, so thin strokes read lower than their tokens would on paper. For current values only, I read `BodyPanel.kt`, `ReticleTag.kt`, `Overlays.kt`, `RulerScene.kt` (class `Reticle` and the constants), `BodyView.kt`, `PinViews.kt` and `SceneKit.kt`.

**Not judged (not visible in stills).**
- Motion. Pin drop and rebound, tape run-out, lock sway and stub sway are all in the code but cannot be seen in a still.
- Sound and haptics.
- The stability ring while it is open.
- "Still mapping".
- Pending and unsaved rings on the new washer.
- The restore rise.
- A contact grade on a tag.
- Plates at 0.45 m.
- The widest ft·in pill.

The tester's parallel viewpoints were not available.

IDs continue review 1's numbering: T = physical objects, L = legibility, F = layout and fidelity. M is new and covers the milestone-2 elements (reticle, live tape).

## Verdict: changes requested. There are three P1 items, all small; once they are done, this passes for the user demo

The instrument now reads as an instrument:
- Each pin is a steel head on a printed washer.
- The blade has rims, true-scale ticks, a red metre band, and a hook that visibly clips over the tail pin.
- Each reading stands on a rider and stem beside its blade.
- The body is a yellow housing with a sunken glass, a bezel and ribbed bumpers, and it rests on surfaces instead of sinking into them.

The captures show all eight review-1 P1 items and all eleven addendum rulings as specified. R4's plane and distance cap can only be judged from logs. Of the review-1 P2 and P3 items, only L3 is not met.

Three things keep it from the user:
1. **Chips are hidden.** The chips that make "any two pins" work sit behind the plates of unrelated tapes, even in a four-pin scene. A pinch there selects the wrong tape.
2. **The reticle is lost.** The tracked-pointer reticle is a ring about one pixel wide, and its label is bigger and brighter than the ring. "No surface" is also drawn in the same orange as "far".
3. **The body is head-locked.** The one standing object therefore behaves like a HUD, not like a thing in the room.

## 1 · Taste and physicality

| Part | What it reads as now | Evidence | Items |
| --- | --- | --- | --- |
| Pin | A survey pin, life size from 0.6 to 2.5 m. It has a dark base rim, a yellow washer with four index marks, and a steel flange and dome with a centre punch. The shank shows under the flange. Its contact shadow is faint: the floor is 10–14 % darker next to the base, against 30 % designed. | `close1_crop`; close1.png (847, 1209) and (1355, 1085) | T13 |
| Blade | A printed tape lying on the floor. Both edges carry a black rim of 1–2 px. There are three tick lengths and a 10 mm red metre band (close1.png (1180, 260)). The red zero end and the hook are at the tail. Its shadow is faint, about 9 % darker. | `close1_crop`, `_crop_close_pin2` | T13 |
| Hook | On a locked tape, the steel tongue and lip hang over the tail pin's flange, so the tape reads as hooked onto the pin. On the stub it is the free end. | close1.png (1260, 580); `sel_pin_crop` | n/a |
| Reading plate | A sign on a post: a content-width pill, a rider on the blade, a steel stem to the pill's near edge, pitched to the eye. The 22 mm `bumper` rider is the darkest thing on the blade, a black square over 2.2 cm of ticks at 0.6 m. During a pull it sits on the tail and hides the hook. | `close1_crop`, close1.png (1110, 1145); live_blade.png (1834, 1494) | M2, T14 |
| Body | A yellow instrument. It is level and tilted back, its top face shows, the glass sits in a dark bezel, and the bumpers stand 3 mm proud with ribs. With the head low, it rests on the rug (`d13_low_crop`). Two problems: the tape mouth is a red square and a grey square seen face-on at mid-height, which reads as a flag icon rather than a tape leaving its case; and the body is head-locked. | `lcd_last_crop`, lcd_last.png (1045, 1630) | T12, T11 |
| Reticle | A thin ellipse: 30 × 17 px at 2.3 m and 12 × 9 px at 4.5 m. Its stroke is about one pixel. It sits next to a dark pill 82 × 46 px, so the eye finds the label, not the tool tip. | `reticle_steady_crop`, `reticle_snap_corner_crop` | M1 |
| Tags, dialog, snackbar | Tags are solid dark cards with a thin rim. The dialog and snackbar read as SpatialUI surfaces. One snackbar came up as an empty black pill. | `sel_tape_crop`, `en_dialog2_crop`, `snack_retry_crop`, `elsewhere_crop` | F10, F11 |
| Plates in dense scenes | At 5 or more tapes per square metre, the black pills dominate. They cover chips (`pintag_tune_crop`) and each other (`twenty5_crop`). | | F9, F12 |

What works and must stay:
- the riders and stems, which make the plate part of the instrument;
- the hook over the tail pin;
- the 55 % dimming of unrelated blades;
- the yellow outline on a selected plate;
- the compact "▲ 39.5 cm" plate beyond 6 m;
- the seat bars after a pointer snap;
- one standing panel, with everything else appearing on selection.

## 2 · Fidelity to §3: milestone-2 elements

| Element | §3 | Build | Evidence | Result |
| --- | --- | --- | --- | --- |
| Reticle, steady | Inner ring and stability ring merge into one thick green ring; tag "稳 / Steady" | Merged green ring, "Steady" in `passable` | `reticle_steady_crop` | matches; too small → M1 |
| Reticle, aiming | White stability ring closes from 72 to 33 mm within 0.35 s | — | not captured | not visible |
| Fine aim | Tag "细瞄 ×4 / Fine ×4" | White "Fine ×4"; ring green once steady | `reticle_fine_crop` | matches |
| Snap to edge or corner | Reticle jumps to the seat and shows the seat figure (edge = bar, corner = box), in `interaction` | Yellow bar across the ring with "Edge". At 4.5 m the corner ring is 12 × 9 px and the box cannot be resolved; tag "Corner" | `reticle_snap_edge_crop`, `reticle_snap_corner_crop` | matches; figure too small → M1 |
| Far (over 3 m) | Dashed orange ring; "Over 3 m · less precise" | As specified | `reticle_far_crop` | matches; ring 2.4 : 1 → M1 |
| No surface | Hollow dashed ring and tag in `onInkSecondary` | Dashed ring and tag in `alert`. In code, `ReticleTag` maps NO_SURFACE and MAPPING to `alert`, and `Reticle.showNone` reuses `farRing`. | `reticle_none_crop` | **deviation** → M1.5 |
| Contact | Solid diamond with a tight ring; "触测 / Contact" | White diamond, tight ring turns green when steady, "Contact" | `reticle_contact_crop` (synthetic tip about 2 m away, so it is small) | matches |
| Reticle tag | Lower right of the reticle; one or two words in the colour of their meaning; solid `plateInk` | As specified | all reticle crops | matches |
| Live blade while aiming | Live tape from the armed pin, printed segments, hook at the tail, plate riding the tail with "…" | Printed blade; plate at the tail with "…"; "Steady" tag. The rider covers the red end and the hook. | `live_blade_crop`, `controller_mode_live_blade_crop` | partial → M2; design gap → M3 |
| Grades on tags | Pin tag badge "● 细瞄 · ±x"; tape tag "起 / 止" on two lines | "○ Coarse · ±2 cm", "● Fine · ±8 mm", "Start ○ Coarse / End ○ Coarse", zh "起 / 止 ○ 粗瞄" | `sel_pin_crop`, `pintag_tune_crop`, `sel_tape_crop`, `zh_tape_tag_crop` | matches ("◆ Contact" not captured) |
| Feedback colours | Green = steady; yellow = snap and actions; orange = far and unsaved; white = grade | As specified, except "no surface" | the rows above | one deviation (M1.5) |
| Frozen | Blade and pins desaturated **and dashed**; "⦸" on the plate | Grey with ticks kept, not dashed; "⦸ ±2 cm · Frozen" | `lost_crop` | accept the build and drop "dashed" (design change 9) |

## 3 · Legibility over the passthrough

| Element | Glyph on background (sampled) | Ratio | Result |
| --- | --- | --- | --- |
| Readout value (24 sp) | `#F3F3F3` on `#201D19` | 15.1 | pass |
| Readout context line (14 sp), en / zh | `#838280` / `#807F7D` on `#211F1A`, strokes peak at `#969594` | 4.3 / 4.1 | **fails L3's acceptance** → L7 |
| Unit segment, unselected / selected | `#161515` on `#898784` / `#2F2F2E` on `#A7A6A3` | 5.1 / 5.5 | pass (unselected was 3.0) |
| Body key icon | `#F6F6F6` on `#211F1B` | 15.3 | pass |
| Plate value, floor at 2.4 m | `#FDFDFD` on `#141618` | 17.9 | pass |
| Plate tolerance line | `#9F9FA1` on `#141618` | 6.9 | pass |
| Plate dimmed digits (24 sp, dim on purpose) | `#7D7E7F` / `#78797B` | 4.4 / 4.1 | pass for large text |
| Far plate "▲ ±10 cm" | `#FF8A1F` on `#16181A` | 7.5 | pass |
| Frozen plate value / line | `#AFAFB1` / `#999A9C` on `#141618` | 8.3 / 6.4 | pass |
| Live plate value | `#FEFEFE` on `#141618` | 18.0 | pass |
| Candidate chip | `#DEDEDE` on `#141518` | 13.6 | pass, when it is not covered (F9) |
| Pin tag title / badge | `#E3E3E3` on `#1A1C1F` / `#CCCCCD` on `#3B3C3F` | 13.3 / 6.9 | pass |
| "Fine-tune", off / on | `#E5E5E5` on `#3B3C3F` / `#191C1E` on `#FFC72C` | 8.7 / 11.0 | pass |
| Stepper row label / "1 mm / step" / value | `#D7D7D8` / `#999A9B` / `#E4E4E4` on `#1A1C1F` and `#3B3C3E` | 11.9 / 6.0 / 8.7 | pass (zh 12.5 / 6.3) |
| Tape tag title / grade lines / "Retract this tape" | `#E4E4E4` / `#949596` / `#E1E1E2` | 13.3 / 5.6 / 8.4 | pass (zh grade line 6.0) |
| Reticle tag: Steady / Fine ×4 / Contact | `#5FCF80` / `#ECECEC` / `#EDEDEE` on `#141618` | 9.2 / 15.3 / 15.6 | pass |
| Reticle tag: Edge / Corner / Over 3 m | `#F7C12C` / `#F8C22C` / `#E47C1E` | 9.6 / 11.1 / 6.2 | pass |
| Reticle tag: No surface | `#EA7F1E` on `#151719` | 6.5 | legible, wrong colour → M1.5 |
| Reticle ring, steady, on wood | `#65B975` on `#615244` | 3.1 | weak → M1 |
| Reticle ring, far / no surface | `#C07C3D` on `#465435` / `#C17338` on `#4B4C46` | 2.4 / 2.4 | **fail** → M1 |
| Snackbar message / description | `#222222` / `#3C3C3C` on `#B3B3B2` | 7.6 / 5.3 | pass (description was 4.35) |
| Snackbar "Retry" / "Clear them" | `#51421F` / `#5C4A1F` on `#FFC72D` | 6.3 / 5.5 | pass |
| Dialog title / body | `#0B0B0B` / `#333333` on `#B1B1B0` | 9.2 / 5.8 | pass (body was 4.4) |
| Dialog "Clear all" | `#452224` on `#FF5F52` | 4.6 | pass (the token pair is 5.9; it was 2.7) |
| Blocked dialog body / "Retry" | `#383837` on `#B0B0AE` / `#3D321D` on `#FFC72D` | 5.4 / 8.1 | pass |

Plates, chips, tags and reticle tags are solid `plateInk` and hold 4.1–18 : 1 everywhere; the values in the 4s are the deliberately dimmed 24 sp digits. Everything on glass now passes. Two things do not:
- **The body's context line.** It is the only place outside a tag where the tolerance of the last reading is shown (L7).
- **The reticle rings.** They have no casing (M1).

3D colours (T9):
- Unlit blade and washer sample `#FFC72D`, which is the token exactly.
- The lit housing samples `#E6C958` on top and `#DAB02F` at the bottom.
- The steel dome has a median of `#8F9395`, with highlights at `#AFB4B9`–`#B6BBBF`; the target of `#A9AEB5` or brighter is met.
- Bumpers sample `#0E0F12`; the glass samples `#92918D`.

## 4 · What the real build proves wrong in the design

1. **Chips have no depth rule against plates.** R3.3 makes tags stand in front of what they overlap; chips were left out. At 24 % of a thread, a chip routinely lands on another tape's plate (F9).
2. **Appendix A's soft limit is a share of the tag's area,** so a 264 × 184 dp tag can bury a whole plate and still pass (F10).
3. **F5 is too short.** It puts the chip's centre 2.5° from the far pin, but an 86 dp chip is about 4.9° wide at constant angle. On a near-horizontal thread the chip reaches its pin (F16).
4. **The reticle is too thin and too small.** A reticle of 1.1° with 1.5 mm strokes is about one pixel wide at 2 m and has no casing. Next to a 3° × 1.7° tag it loses the hierarchy (M1).
5. **"…" on the live plate wastes the second line** at exactly the moment the stability ring asks the user to hold still (M3).
6. **The mouth stub reads as a flag.** It sits at mid-height with its face to the viewer (T12).
7. **The rider hides the hook during a pull,** because it sits on the tail (M2).
8. **The contact shadows are too faint.** A 30 % black shadow reads as about 12 % darker; this is consistent with the engine blending in linear light. The designed opacities were never checked on a render (T13).
9. **66 % white is too dim at this size.** At 14 sp on a panel 0.6 m away it never reaches its colour; the strokes peak at `#969594` (L7, amends L3).
10. **Two specified features are redundant.** The short-tape leader is not needed, because every plate already has a rider and stem. Dashed frozen blades would turn a dead instrument back into a graphic (design changes 9 and 10).

## 5 · Milestone-2 extras: what the first user demo needs

| Extra | For the demo | Reason |
| --- | --- | --- |
| Lazy follow | **needed** (T11, P1) | A head-locked body is the one element that still behaves like a HUD. Lazy follow is also how §3 and T8a define the body. |
| Settle wobble | **already in code**; the rest can wait | Tape lock sway (5 mm, two periods, 0.4 s; `SETTLE_SWAY_*`) and the pin drop with rebound and ripple (`PinViews`) are built, but I cannot judge them from stills; I want a 3-frame burst of a lock. The pin-level wobble listed as not built adds nothing the user will miss. |
| Coachmark | **only if the user drives the demo alone** | The readout already says "Ready to pin" and "Pull to next point". Press-and-pull, the signature, cannot be found without the coachmark. If we drive, it can wait. Build it as a pre-built panel like the snackbar, because its host in a Stage is unverified (assumption 9). |
| Pin hover ring | can wait | The pin's touch sphere is about 2.5° across beyond 1.3 m (radius 28 mm × distance ÷ 1.3), and a tap gets an immediate answer: lift, yellow ring, stub, tag. |
| Short-tape leader | drop it | The rider and stem already are the leader (design change 10). |
| Plane-outline flash (also not built) | can wait | "Scanning · n" on the readout already says that surfaces are arriving. |

## Punch list

| ID | Pri | Item |
| --- | --- | --- |
| F9 | P1 | Chips stand in front of plates; unrelated plates recede |
| M1 | P1 | Reticle: size, strokes, casing, lean limit, "no surface" colour |
| T11 | P1 | Lazy follow for the body |
| L7 | P2 | Readout context line in `labelPrimaryLight` (L3 not met) |
| F10 | P2 | Soft overlap measured on the item that is covered |
| T12 | P2 | Body tape mouth |
| T13 | P2 | Contact shadows at a visible strength |
| M2 | P2 | Hook and red end stay visible at the reticle during a pull |
| M3 | P2 | Live plate shows the tolerance the tape would get |
| F11 | P2 | No black snackbar |
| F12 | P3 | Dense scenes: the newest reading on top |
| F13 | P3 | Leader when a tag ends more than 3° from its anchor |
| F14 | P3 | Body keep-clear box includes the tape mouth |
| F15 | P3 | Readout while blocked |
| F16 | P3 | Chip clears its far pin |
| F17 | P3 | Tape tag height fits its content |
| T14 | P3 | Rider as a steel clip |
| T15 | P3 | Threads start at the washer rim |

### F9 · P1 · Chips stand in front of plates

- **Wrong.**
  - `pintag_tune_crop`, pintag_tune.png (1560–2160, 1880–2158): of the selected pin's chips, "49.9 cm" lies wholly behind the "111.8 cm" plate and shows through its 6 % transparency. "48.8 cm" is cut by the same plate, and "70.7 cm" sits behind a "26.2 cm" plate.
  - `zh_pin_tune_crop`, zh_pin_tune.png (1275–1390, 1620–1665): in a scene of four pins and two tapes, the chip on the long thread ("110.7 cm") is behind the "42.7 cm" plate of an unrelated tape.
  - The plate is nearer, so a pinch there selects that tape instead of pulling the new one.
  - Chips are the primary focus of 选钉 (§3 State graph), and neither §3 nor R3 keeps them in front of plates.
- **Change.**
  1. Range rule for chips: R3.3, applied to chips. While a pin is selected, a chip's range is at most the range of the nearest plate pill whose rectangle in view overlaps the chip's rectangle (inflated by 0.5°), minus 5 cm. It is never nearer than 0.45 m. Its scale follows the range, so its place and size in view do not change. The surface rule (R3.1) still applies.
  2. While a pin is selected, plates of tapes that do not end on that pin go to 55 % opacity together with their blades. This extends §3 Layout's 55 % rule to the plates; the blades already do it.
  3. Tags still stand in front of chips (R3.3 names chips).
- **Acceptance.** In the `pintag_tune` and `zh_pin_tune` poses, every chip's text is fully visible. A real pinch on each chip logs `TAPE add` for that pair and never `STATE tape-selected`.
- **Ref.** §3 Surface sizing → 候选距离片, 表面规则; §3 Layout (55 %); R3.

### M1 · P1 · Reticle: size, strokes, casing, lean limit, "no surface" colour

- **Wrong.**
  - `reticle_steady_crop`, reticle_steady.png (1460, 1591), floor at 2.3 m: the ring is 30 × 17 px. Its stroke is about one pixel: 1.5 mm × k 1.38 = 2.1 mm, or 0.05°. Green on wood measures 3.1 : 1. The "Steady" pill next to it is 82 × 46 px, so the eye finds the label rather than the tool tip.
  - `reticle_far_crop`, wall at about 4 m: the ring is 19 × 27 px at 2.4 : 1.
  - `reticle_snap_corner_crop`, cabinet top at about 4.5 m: the ring is 12 × 9 px, and §3's corner box cannot be seen.
  - `reticle_none_crop`: the dashed ring and the tag are in `alert` orange. §3 Semantic colors gives 没有表面 `onInkSecondary`. Orange now means both "over 3 m" and "nothing will be set here".
- **Change.** Mesh values below are before the distance factor k (`Reticle`, `RulerScene.kt`).
  1. Change the distance factor k from max(1, distance × 0.6) to max(1, distance × 0.9). The inner ring then stays at 1.65° beyond 1.1 m (it was 1.1°).
  2. Strokes:
     - inner ring 1.5 → 3.0 mm (radii 13.0–16.0 mm);
     - merged steady ring 3.0 → 4.0 mm (12.5–16.5 mm);
     - stability ring 1.5 → 2.5 mm (33.5–36.0 mm);
     - dashed far and no-surface rings: same as the inner ring;
     - cross 0.7 → 1.6 mm;
     - centre dot Ø 2.4 → 5.0 mm;
     - contact diamond unchanged.
  3. Casing: under each ring, the cross and the dot, draw the same mesh in `bladeEdge` at 70 %, 2.2 times the stroke width, 0.3 mm towards the surface. This is the L4 recipe for threads; depth test off like the reticle.
  4. Lean limit: change `MIN_FACING` from 0.309 to 0.5, so the reticle is at most 60° away from the viewer. The ellipse's short axis then stays at least half its long axis.
  5. No surface and Still mapping: the ring is dashed in `onInkSecondary` (`#FFFFFF` 66 %) with the casing, and the tag text is `onInkSecondary`. `alert` remains for "Over 3 m · less precise" only.
- **Acceptance.** At the round-2b poses, the floor at 2.3 m gives a short axis of at least 24 px and a stroke of at least 3 px. The wall at 4 m gives a ring at least 34 px across. With the casing, ring against surroundings is at least 3 : 1 on the wood floor and on the white wall.
- **Ref.** §3 实物 → 准星; Semantic colors (没有表面).

### T11 · P1 · Lazy follow for the body

- **Wrong.** In every capture the body sits exactly 18° left and 22° down, whatever the head does (`lcd_*`, `standing_tape3_crop`, `zh_*`, `d13_low_crop`; AGENTS.md: lazy follow not built). With emulator mouse-look or on a headset it stays glued to the corner of the view. The one standing object reads as a HUD, which contradicts the brief that the instrument should be a tangible object in the room.
- **Change.** This is the §3 behaviour in its smallest form.
  1. At rest, hold the body in Stage space at its last settled pose. Orientation as in T8a: long edge level, X parallel to the head's X at the moment it settled, face tilted back 12°.
  2. It moves when any of these holds for 0.5 s without a break:
     - its centre is more than 45° from the view direction (the designed offset is 28°);
     - it is nearer than 0.35 m to the head;
     - it is farther than 0.9 m from the head.
  3. When it moves, it slides to the designed pose of that moment (18° left, 22° down, 0.6 m) in 0.35 s, with ease-in-out cubic, time-based. R2 is applied to the target before the slide. R2 is applied again whenever a plane is added or updated while the body rests.
  4. It does not move while a body key is pressed, while the More menu, the language menu or a dialog is open, or for 0.6 s after a body key was pressed.
  5. The panel and collider move with the body in the same frame; the holder is written every frame during a slide.
- **Acceptance.**
  - Turn the head 20° to the right: the body stays where it is in the room.
  - Turn 70° to the right: within 0.85 s the body is back at 18° / 22°.
  - After a slide, real pinches on Undo and on `unit_cm` log `PTR panel=body` and `UI …`, and never `PIN add`.
- **Ref.** §3 实物 → 尺身 (懒跟随); T8a (last sentence); assumption 7. The head-locked fallback in assumption 7 is for a lazy follow that has failed; it is not the default.

### L7 · P2 · Readout context line in `labelPrimaryLight` (L3 not met)

- **Wrong.** `lcd_last_crop`, lcd_last.png (503–614, 1557–1577): "Last · ±3 cm" is 14 sp SemiBold as L3 asked (cap height 15 px). But its strokes peak at `#969594` on `#211F1A`, giving 4.3 : 1, the same as in review 1 (`zh_last_crop` gives 4.1 : 1). L3's acceptance ("brightest glyph pixels ≥ `#A0A0A0`") is not met. A 66 % white stroke about one and a half pixels wide never reaches its colour on a panel 0.6 m away. This line carries the tolerance on the only standing panel.
- **Change.** Change the context line colour from `onInkSecondary` to `labelPrimaryLight`. Size, weight and position stay; the 14 sp against 24 sp sizes carry the hierarchy. The value line in LOST keeps `onInkSecondary`, as built.
- **Acceptance.** Peak glyph pixels at `#D0D0D0` or brighter, and at least 7 : 1 by this review's method.
- **Ref.** §3 Type (读数窗的语境行); amends L3.

### F10 · P2 · Soft overlap measured on the item that is covered

- **Wrong.** `zh_tape_tag_crop`: the tape tag took slot NE and covers the whole "70.4 cm" plate of the other tape; the plate's edge peeks out at the tag's right side. Appendix A step 7 measures soft overlap as a share of the *tag's* area, so a 264 × 184 dp tag can hide a whole 110 × 58 dp plate (about 13 % of the tag) and still pass.
- **Change.** In Appendix A step 7, a slot qualifies when its overlap with each soft item is at most 15 % of *that item's* own area: for a pin, its ±1.5° box; for a plate, its pill. If no slot qualifies, take the slot whose largest per-item share is smallest. Steps 1–6, 8 and 9 stay.
- **Ref.** Appendix A step 7; §3 Surface sizing → 钉牌, 尺牌.

### T12 · P2 · Body tape mouth

- **Wrong.** `lcd_last_crop`, lcd_last.png (1045, 1630), `sel_pin_hook.png`, `close1_crop`. Out of the right bumper sticks 14 mm of blade at mid-height (`MOUTH_LENGTH` 14 mm, `MOUTH_Y` −6 mm), face to the viewer: 12 mm of red and 2 mm of yellow, then the hook tongue seen face-on. At 0.6 m it reads as a red-and-grey flag icon, not as the end of a tape coming out of its case.
- **Change.**
  1. The stub outside the bumper grows from 14 to 26 mm. Going from the bumper outwards, the first 14 mm is printed yellow blade with rims; the 1 cm and 2 cm ticks are measured from the hook. The last 12 mm, at the tip, is the red zero end.
  2. Its centre line moves from y = −6 mm to y = −HALF_Y + 12 mm (−49 mm), so the mouth is at the bottom of the right end, where a tape measure has it. The blade's face stays towards the viewer.
  3. Add a mouth frame where the blade leaves the bumper: `bumper` bars 2 mm thick around the blade's section, outer 22 mm high × 6 mm deep, standing 2 mm proud of the bumper face.
  4. Bend the hook's lip towards the viewer (+Z of the body) instead of away from it. Its 7 mm then show as a steel edge at the tip from the user's position (18° to the right of and 10° above the face normal). This is an exception to T3 for the body only.
- **Ref.** §3 实物 → 尺身 (右侧有出尺口、一小段尺带和钢钩); T3.

### T13 · P2 · Contact shadows at a visible strength

- **Wrong.** Measured in close1.png:
  - Next to the washer (column x 840–866 below y 1256, row y 1226–1240 left of x 818), the wood floor drops from luminance 76 to 65–69, which is 10–14 % darker.
  - Under the blade (x 1000 and x 1500, y 1230–1245) it drops to about 69, which is 9 % darker.
  - The design asks for 30 % and 24 % black. The materials are set as designed (`sheer(Black, 0.30 / 0.24)` in `SceneKit.kt`). The result is consistent with blending in linear light, where a 30 % black reads as about a sixth darker on a mid-dark floor.
  - Pins and blades are now seated mostly by the dark base rim.
- **Change.** Raise the pin shadow opacity from 0.30 to 0.45 and the tape shadow opacity from 0.24 to 0.36. Size, colour and lift stay.
- **Acceptance.** In the `close1` pose, the floor within 5 px of the base disc is at most 0.80 × the floor 40 px away; under the blade, at most 0.85 ×.
- **Ref.** §3 实物 → 钉 (接触影), 尺带 (接触影); T1.6, T2.2.

### M2 · P2 · Hook and red end stay visible at the reticle during a pull

- **Wrong.** live_blade.png (1834, 1494) and `controller_mode_live_blade_crop`: the plate's rider sits on the last 22 mm of the live blade. The red zero end and the hook, the part that says "the tape stops here", are under a black block at the reticle.
- **Change.** While a tape is live or running out, the rider's centre is 45 mm back from the tail along the blade. "Live" covers a tracked aim and pulling-drag; "running out" is the pull-out after a tap. That position is behind the 12 mm red end and the 9 mm hook tongue, and the stem starts there. The plate keeps its place at the tail. When the tape locks, the rider slides with the plate to its chosen position, as built.
- **Ref.** §3 实物 → 读数牌支架, 尺带 (实时形态：一端跟着准星，带钢钩).

### M3 · P2 · Live plate shows the tolerance the tape would get

- **Wrong.** `live_blade_crop`, `controller_mode_live_blade_crop`, `pull_live_crop`: while the user aims, the plate's second line is "…". The stability ring and the "Steady" tag ask the user to hold still, but the reading does not say what holding still buys.
- **Change** (a design change; see below).
  - While a tape is live (tracked ray, contact, or press-and-pull), the second line is the tolerance the tape would get if committed now. It combines the head pin's grade with the current aim's grade, root-sum-square, using the R1 ladder and format, with an `alert` "▲" when the aim is far. The current aim's grade is:
    - fine when the aim is steady or the trigger is half pressed;
    - 6 mm when it is snapped to a corner or an edge;
    - 2 mm in contact;
    - doubled beyond 3 m.
  - The line updates when the reticle changes state, for example from ±8 mm to ±3 mm as the ring closes.
  - During the 0.6–1.1 s run-out after a tap–tap, the line stays "…".
  - No new copy is needed.
- **Ref.** §3 Components → 读数牌 (拉出中); 交互模型 → 怎么知道量得多准.

### F11 · P2 · No black snackbar

- **Wrong.** In `elsewhere_crop` (06:36), the "pins not in this space" snackbar shows as an empty black pill. `elsewhere3_2_crop` (06:38) shows it drawn. At the emulator's idle rate a black pill stays up for seconds; on a headset it is a flash.
- **Change.** No frame may show a snackbar or dialog pill without its content. Keep the slot invisible until its content has drawn a frame, which is the same signal `SCENE body panel drawn` gives for the body. Then fade it in over 150 ms, time-based. How to achieve this is the build's choice; AGENTS.md findings 1–3 constrain it.
- **Acceptance.** In a 10-frame burst after `elsewhere 2` plus a relaunch, and after `fail_next_anchor` plus a tap, no frame shows a black or empty pill.
- **Ref.** §3 反馈、持久化与边界 (面板池); AGENTS.md finding 3.

### F12 · P3 · Dense scenes: the newest reading on top

- **Wrong.** `twenty5_crop`: with 20 tapes on 12 pins, "56.6 cm" lies under "42.0 cm", and "38…" under "56.6 cm". Beyond the designed 8 tapes, overlap is accepted (§3 Layout), but which plate ends up on top is arbitrary.
- **Change.** When pills still overlap after R9's search, one pill stands in front: the selected tape's, otherwise the most recently locked tape's. Its range is at most the nearest overlapping pill's range minus 5 cm (R3.2 limits). Nothing else changes.
- **Ref.** §3 Layout; R9.

### F13 · P3 · Leader when a tag ends more than 3° from its anchor

- **Wrong.** `sel_pin_crop` and `tuning_nudged_crop`: the pin tag took slot NW. Its nearest corner is about 5.5° from the pin, and there is no leader. Appendix A step 8 draws one only when no slot is left, and R3.5 only when the tag has been pulled in.
- **Change.** Draw the Appendix A leader whenever the gap between the tag's nearest edge and the anchor box exceeds 3°, whichever slot was taken.
- **Ref.** Appendix A step 8; R3.5.

### F14 · P3 · Body keep-clear box includes the tape mouth

- **Wrong.** `sel_pin_hook.png` and `tuning_nudged_crop`: the pin tag's lower-left corner lies over the hook of the body's tape mouth.
- **Change.** The `BODY_*` box used for tag placement grows by 30 mm on the right, or 40 mm once T12 is done.
- **Ref.** Appendix A step 4.

### F15 · P3 · Readout while blocked

- **Wrong.** `blocked_crop`: behind "Spatial features unavailable" the body says "Getting ready…" with a turning spinner. It shows progress where there is none.
- **Change.** In `blocked` the readout shows an empty context line and "— —", with no spinner. The keys behave as in `starting`.
- **Ref.** §3 State graph → 不可用.

### F16 · P3 · Chip clears its far pin

- **Wrong.** `pintag_tune_crop`: the left end of the "26.3 cm" chip touches its far pin. F5 puts the chip's centre 2.5° from the pin, but an 86 dp chip is about 4.9° wide, so on a near-horizontal thread half the chip reaches the pin.
- **Change.** Start from max(24 % of the thread, 2.5°) and slide the chip towards the selected pin until its rectangle, inflated by 0.5°, clears the far pin's ±1.5° box. Cap the position at 50 % of the thread.
- **Ref.** §3 Surface sizing → 候选距离片; F5.

### F17 · P3 · Tape tag height fits its content

- **Wrong.** `sel_tape_crop` and `zh_tape_tag_crop`: after R10's two grade lines, about 32 dp of the 184 dp tag are empty below "Retract this tape". At constant angle the tag is 15° × 10.5°, the largest panel that appears on selection.
- **Change.** Change the tape tag from 264 × 184 dp to 264 × 152 dp: content plus 16 dp bottom padding. Slots and rules stay.
- **Ref.** §3 Surface sizing → 尺牌.

### T14 · P3 · Rider as a steel clip

- **Wrong.** close1.png (1110, 1145): the 22 × 22 mm `bumper` rider is the darkest, most saturated thing on the blade: a black square over 2.2 cm of ticks right next to the reading.
- **Change.**
  - Size: from 22 mm along × (blade + 6) mm across × 6 mm through, to 16 mm along × (blade + 4) mm across × 5 mm through.
  - Material: `steelShade`, lit like the stem (metallic 0.6 with T9's steel glow).
  - The plate then hangs from a small steel clip in the same metal as the stem, the hook and the pin heads.
- **Ref.** §3 实物 → 读数牌支架.

### T15 · P3 · Threads start at the washer rim

- **Wrong.** pintag_tune.png (1800, 1769): the white dashes of every candidate thread run across the selected pin's washer and steel head.
- **Change.** Each thread starts at the rim of the selected pin's base disc (16.5 mm × the pin's visual scale from its axis) and ends at the far pin's rim. Threads are depth-tested against pins.
- **Ref.** §3 实物 → 候选线.

## Design changes requested (for the design lead)

`design-doc.md` and `preview.html` were not edited. These rows need rewriting:

1. **§3 实物 → 准星** (M1).
   - Minimum visual angle 1.1° → about 1.65°, with distance factor max(1, distance × 0.9).
   - Strokes: inner ring 3.0 mm, merged ring 4.0 mm, stability ring 2.5 mm, cross 1.6 mm, centre dot Ø 5 mm.
   - Every stroke sits on a `bladeEdge` 70 % casing 2.2 times as wide.
   - The reticle leans at most 60° away from the viewer.
2. **§3 Components → 读数牌, and 交互模型 → 怎么知道量得多准** (M3). While a tape is live, the second line is the tolerance it would get if committed now. "…" stays only for the run-out after a tap–tap.
3. **§3 实物 → 读数牌支架** (M2, T14). During a pull the rider sits 45 mm behind the tail. As P3: 16 × (blade + 4) × 5 mm in `steelShade`.
4. **§3 实物 → 尺身** (T12, T11).
   - Mouth: 26 mm stub at the bottom of the right end, mouth frame, hook lip towards the viewer.
   - Lazy follow: window 45° from the view direction; 0.5 s outside the window, or outside 0.35–0.9 m from the head, before it moves; 0.35 s slide; no movement while a key is pressed or a menu or dialog is open.
5. **§3 Type and Visual tokens** (L7). The readout context line uses `labelPrimaryLight`. This amends L3.
6. **§3 Surface sizing → 候选距离片** (F9, F16).
   - Chips stand in front of overlapping plates (range of the nearest overlapping pill minus 5 cm; limit 0.45 m). Tags stand in front of chips.
   - A chip's rectangle, inflated by 0.5°, clears its far pin's ±1.5° box, up to 50 % of the thread.
7. **§3 Layout** (F9, F12).
   - While a pin is selected, plates of unrelated tapes also go to 55 %.
   - Where pills overlap, the selected tape's pill, otherwise the most recently locked one, stands in front.
8. **Appendix A** (F10, F13, F14).
   - Step 7: the soft share is per covered item.
   - Step 8: draw a leader whenever the gap exceeds 3°.
   - Step 4: the body box includes the tape mouth (+30 mm, or +40 mm after T12).
9. **§3 Semantic colors → 定位丢失.** Blade and pins in `staleGrey` without dashes; the plate's "⦸" and "Frozen" carry the state. This accepts the build (`lost_crop`): the grey printed blade reads as a stopped instrument, and dashes would make it a graphic again.
10. **§3 Components → 读数牌 forms, and coverage manifest C3: short-tape leader.** Drop it. Every plate already has a rider and stem, which is the leader.
11. **§3 State graph → 不可用** (F15). The readout shows an empty context line and "— —", with no spinner.
12. **§3 实物 → 钉 / 尺带 contact shadows** (T13). Opacity 45 % and 36 %. Add a note that the engine blends in linear light.
13. **§3 Surface sizing → 尺牌** (F17). 264 × 152 dp.

Build deviations that are not design changes: M1.5 ("no surface" in `alert`) and F11 (black snackbar).

## Verification of the review-1 items

| ID | Item | Status | Evidence |
| --- | --- | --- | --- |
| T1 | Pin proportions, rim, true size, shadow | verified; shadow weak → T13 | `close1_crop`. At close1.png (847, 1209): base rim, washer marks, flange, dome, punch. Shank under the flange at (1260, 580). Pending and unsaved rings not visible. |
| T2 | Blade rim, shadow, hook after lock | verified; shadow weak → T13 | `_crop_close_pin2`: rims on both edges. close1.png (1260, 580): hook over a locked tape's tail pin. Shadow 9 % darker under the blade. |
| T3 | Zero end and hook at the same end | verified | Stub (`sel_pin_crop`): red end and hook at the free end. Locked tapes: red end and hook at the tail (`_crop_close_pin2`). |
| T4 | Whole-metre band 10 mm | verified | close1.png (1180, 260): full-width red band on the 180.3 cm tape |
| T5 | Blade lies flat at close range | verified at about 0.6 m | `close1_crop` |
| T6 | Plate width fits its content | verified | every plate; compare "26.2 cm" and "▲ 1803 mm" in `d13_low_crop` |
| T7 | Rider and stem | verified; → M2, T14 | `close1_crop`, `_crop_close_pin2` |
| T8a | Body pose | verified | `lcd_last_crop`: long edges level, top face visible |
| T8b | Housing, bezel, bumpers | verified; mouth → T12 | `lcd_last_crop`; `lit_base_crop` (ribs) |
| T9 | 3D colours | verified | unlit yellow `#FFC72D`; steel highlights at least `#A9AEB5`; lit housing off-token and accepted (tone mapping, AGENTS.md) |
| T10 | Ticks while the blade moves | verified in one frame | `pull_live_crop` |
| L1 | Unit segments | verified | 5.1 : 1 (was 3.0) |
| L2 | Content on semantic fills | verified | "Clear all" 4.6 : 1 measured (was 2.7); "Retry", "Clear them" and "Fine-tune" on yellow 5.5–11.0 |
| L3 | Readout context line | **not met** | 14 sp as asked; strokes peak at `#969594`, below `#A0A0A0`; 4.3 : 1 → L7 |
| L4 | Candidate threads with casing | verified | `pintag_tune_crop`, `zh_pin_tune_crop` |
| L5 | Tag inner card solid | verified | card `#1C1D20` over the floor (`sel_tape_crop`) and `#1D1E21` over the wall (`zh_tape_tag_crop`) |
| L6 | World panels pitch towards the viewer | verified | `close1_crop`, `standing_tape3_crop` |
| F1 | Tag placement | verified; one design flaw → F10, F13, F14 | Pin tag keeps its own tapes' plates clear (`pintag_tune_crop`, `tuning_nudged_crop`). Tape tag takes slot N (`sel_tape_crop`, `tapetag1_crop`). |
| F2 | Stepper | verified | "+1" / "−1"; two-line labels in en and zh |
| F3 | Dialog panel | verified | `en_dialog2_crop`, `dialog_blocked_crop` (the card grows to its content). `zh_dialog2_crop` (06:33) still shows square buttons from an earlier build; the code now passes `CircleShape`. Recapture. |
| F4 | Snackbar panel | verified; → F11 | `snack_retry_crop`, `snack_stretch_lost_crop` |
| F5 | Chip distance from its pin | verified, marginal → F16 | `pintag_tune_crop`: "26.3 cm" touches its pin |
| F6 | Tape limit 20 | verified | `twenty_limit_crop`: snackbar, and a new pin with its stub |
| F7 | Plate stagger | verified for sparse layouts; dense overlap remains (accepted above 8 tapes) → F12 | `close1_crop` ("26.2" above, "50.0" below); `twenty5_crop` |
| F8 | Tag rim 3 dp | verified | `sel_tape_crop` |
| R1 | Tolerance ladder | verified | `far_plate_crop`: "▲ ±10 cm"; "3" bright, "9.5" dim |
| R2 | Body rests on surfaces | verified visually | `d13_low_crop`; the pinch acceptance belongs to the tester |
| R3 | World panels and real surfaces | verified | Tags above the floor and in front of what they overlap (`sel_tape_crop`, `zh_tape_tag_crop`) |
| R4 | Pointer-channel snapping | seat marks verified; plane and distance cap only in logs | `seat_edge2_crop`, `seat_corner_crop` |
| R5 | Readable to 6 m | verified | digits 24–27 px from 1 to 5 m; compact "▲ 39.5 cm" beyond 6 m (`far_plate_6m_crop`) |
| R6 | Pitch clamp ±60° | verified | `close1_crop` |
| R7 | Live reading while tuning | verified | `tuning_nudged_crop` ("Tuning · ±2 cm / 70.4 cm"); `zh_pin_tune_crop` ("微调 · ±2 cm") |
| R8 | Body panel first | verified | `_start_0..2`: passthrough. `_start_3`: the panel alone with the spinner, housing later; "Getting read…" is clipped there, but the current layout fits (`blocked_crop`). |
| R9 | Plate on either side of its blade | verified | `close1_crop`; a near-vertical tape puts its plate on the right (`far_plate_crop`) |
| R10 | Tape tag grades on two lines | verified | `sel_tape_crop`, `zh_tape_tag_crop` |
| R11 | Snackbar action in `interaction` | verified | `snack_retry_crop`, `elsewhere3_2_crop` |

## Evidence wanted next round

1. Three-frame bursts of a tape locking (sway), a pin dropping (rebound, ripple), and a pull after M2 (rider behind the hook).
2. The reticle after M1 at the round-2b poses, on the white wall, and while the stability ring is open.
3. Pin selected in the `pintag_tune` and `zh_pin_tune` poses after F9, with real pinches on each chip.
4. Lazy follow: head turned 20° and 70°, then real pinches on body keys after a slide.
5. The body mouth after T12 at the `lcd_last` pose.
6. The zh clear dialog with capsule buttons from the current build.
7. Still open from review 1: a tape tag on a 0.3 m tape and on a vertical tape (Appendix B 7), and an ft·in plate with an unsaved end (B8).
8. Contact on a table within reach, at real contact range.
9. A pin and a tape on the white wall after T13 (shadow, rims).
