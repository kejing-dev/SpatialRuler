# Spatial Ruler (空间尺)

MR distance-measuring app for PICO OS 6. Kotlin, PICO Spatial SDK 6.1.9 (template 6.1), one Mixed Stage
(Full Space), package `io.embodit.spatialruler`. You set pins on real surfaces and pull a tape between any
two of them; the tape is printed at true scale and carries a reading plate with its tolerance.

Design package: `design/design-doc.md` (binding: tokens, entities, sizes, interaction, states, copy),
`docs/review/design-review-1.md` (punch list T*/L*/F*, Appendix A tag placement, and the addendum R1 - R11, which
wins where they disagree) and `design/preview.html` (logic and layout reference only; not rebuilt after the review).
Test reports: `docs/test/`. Feasibility facts: `../spatial-ruler-spike/FEASIBILITY.md`.

Built: the system-pointer path (gaze + pinch, emulator mouse): tap - tap, press-and-pull, true-up drag, pin and
tape tags, fine-tune, snapping, undo, persistence, the designed states (tracking lost, blocked, pins elsewhere).
Round 2b: tracked pointers - controller ray (analog trigger: half press = 4 : 1 fine aim, full press = commit at
the median of the aim 40 - 120 ms before), hand ray (pinch with hysteresis), contact channel (tool tip within
12 cm of a surface), reticle with stability ring and reticle tag, live tape from the armed pin, sounds and
controller haptics.
Round 3 (last fix round before the user demo): lazy-follow body (T11), reticle casing / 0.9 scale / 60 degree lean
(M1), chips in front of overlapping plates and plates of other tapes at 55 % while a pin is selected (F9), menu
hosted on an attachment panel (R2-3), pinch that closes a menu is consumed (R2-2), "raise a hand" snackbar (R2-1),
anchor rebase instead of a 50 mm cap (R2-4), first-run coachmark, context line in `labelPrimaryLight` (L7),
tag soft rule per item (F10), body mouth (T12), darker shadows (T13), rider 45 mm back on a running tape (M2),
live plate tolerance line (M3), 150 ms fade-in of snackbar / dialog / coachmark (F11).
Round 4 (simplify spec, `docs/review/simplify-spec.md`, binding over design-doc §3 where they disagree): icons
instead of words everywhere a glyph can do the job. Body: readout at 32 sp with a 20 dp annunciator glyph, undo +
more keys, unit segments; snap moved into the More menu. Plates: one line (mark + value). Pin tag -> pin strip
(badge + fine-tune / remove / close icons, chevron steppers), tape tag -> tape strip (badge + retract, no close).
More menu: language segment (globe / 中文 / EN) + snap toggle, "Clear all" (the only worded button) + Exit; the
language sub-menu and the SpatialUI `Menu` path are gone. Dialogs: icon header + one sentence + icon buttons.
Coachmark: one step, pictogram + seven words + ✓. Snackbars: icon + <= 6 words (+ icon + word action). Reticle
tag: "×4", "▲ > 3 m", ⦸ or a spinner only; no words. Sixteen vector icons in `res/drawable/ic_*.xml` (K).
Round 5: pins move by direct manipulation (press a pin, drag, release); the move toggle and the true-up-by-hand-delta
drag are gone; the pin strip (304 x 128, id `pinTag`) always has its steppers; the tape-head stub has its own touch
target (`stub:<pinId>`: a press-and-drag on it pulls a new tape from the pin; round 5b: the sphere is the hook end
only, 15 mm at the last 3 cm of the stub, and a press inside the pin's touch sphere moves the pin even when the system
names the stub). QA round 4 S4: EN segment described
"English" / "英文", segment items 60 x 48 (language) and 79 x 48 (unit), `COACH dismissed reason=first_tape|got_it`.
Not built: pin hover ring, plane-outline flash, pin settle wobble, short-tape leader, press-and-hold pulling with
a tracked ray.

## Structure and why

```
app/src/main/java/io/embodit/spatialruler/
  Main.kt                      thin entry: DefaultStage { SpatialRulerTheme { RulerScreen() } }
  domain/model                 Vec3/Quat4, Pin, Tape, PlaneSurface, RulerState/Action/Effect   (no SDK types)
  domain/usecase               ReduceRulerUseCase (the whole state machine, pure); aiming: HitSurface, SnapPoint,
                               ResolvePlacement, PullLine, TrackPointer (source selection, steady aim, commit window,
                               fine aim, trigger stages, pinch latch, grade, contact); reading: ComputeTolerance, FormatReading, SelectTickLevel,
                               LayoutTape, ScaleForDistance; layout as seen from the head: ViewFrame, PlacePlate,
                               PlaceTag, FacePanel + ClearSurface (surface rule), RestBody
  domain/port                  MeasureEnvironment, RulerLogger (what the domain needs from the platform)
  data/repository              AnchorGateway + SessionRepository interfaces, PrefsSessionRepository
  data/local                   SessionJsonCodec
  platform/                    SDK adapters: PlaneTracker, HeadTracker, WorldAnchorGateway, PointerSource (controller,
                               hand and synthetic sources), SystemPointer (what is known about the system pointer's ray), RulerGraph
  ui/theme                     16-role ColorScheme, brand tokens (RulerColors), Noto Sans typography
  ui/ruler                     RulerViewModel + RulerUiState / RulerEvent / RulerAim + RulerScreen
  ui/ruler/components          SpatialUI panels: BodyPanel (+ BodyKeys), ReadingPlate, ReadingValue, ObjectTags,
                               ReticleTag, MoreMenu, Overlays (dialog, snackbar), RulerStage (the SpatialView and the panel pool)
  ui/ruler/scene               ECS: RulerScene (+ RulerFrameSystem), PinViews, TapeViews, BodyView, PanelRig,
                               PlaneColliders, SceneKit (materials, meshes)
  ui/ruler/debug               RulerProbe: seams for the test harness (no-ops unless the app is debuggable)
app/src/debug/                 DebugCommandReceiver, DebugCommands, CalibPanel (debug builds only)
app/src/test/                  218 JVM tests: every use case, the reducer, the JSON codec, the ViewModel (fakes)
tools/                         dev.sh, cap.sh, smoke.py, ctrlpx.py (screen pixel of a panel control or point),
                               harness/ (emulator input, calibration, interaction mode), test/ (the tester's scripts,
                               rt.py is their library), subset_cjk_font.py
```

One direction of data: events (scene callbacks, panel controls, test hooks) -> `RulerViewModel.onEvent` ->
`ReduceRulerUseCase` (state + effects) -> effects (anchor store, storage, log) -> `RulerUiState` ->
`RulerScene.render` (entities) and the panels. Per-frame work lives in `RulerScene.onFrame` only (one registered
`System`). It never goes through Compose state, except three things written at most 10 times a second: the
count-up length of a tape that is pulled out, which pooled panels are in view, and how wide a plate is. While a
drag is under way the scene asks the ViewModel directly (`RulerAim.dragMode()`, `RulerAim.preview(ray)`: the same
aiming and snapping code that sets the pin on release) and draws the live tape itself; only start, end and the
10 Hz true-up moves become events. Tracked pointers work the same way: `RulerScene.aimPointer` samples the
active `PointerSource` every frame, previews through `RulerAim`, draws reticle, tag and live tape, and sends one
`PointerTriggered` / `ContactTriggered` per press edge. Sound and touch are `RulerEffect.Feedback` from the reducer
(pin drop, snap, tape lock, retract, fail) played by `RulerScreen` through `LocalAudioEffectPlayer` /
`LocalHandControllerHaptic`. A pin is its spatial anchor plus a local offset; distances are always computed
from current positions. Everything "as seen from the head" (which side a plate stands on, where a tag fits, what a
panel may cover) is computed in the domain on `ViewFrame` (yaw / pitch / range), unit-tested, and only applied by
the scene.

## Runtime facts (all observed on the emulator, SDK 6.1.9, 2026-10-01 / 02)

- Stage coordinates: an entity added to the full-size `SpatialView` sits 0.256 m off in z. `RulerScene` moves one
  root so that its local space equals Stage metres; everything hangs below it. The engine's `convertPositionTo`
  does not include the pose of a camera anchor: where head-mounted things are is computed from the head pose.
- System taps: `detectSpatialTapGesture` + `TargetEntity.any()`. `SpatialTapValue.position` is view pixels:
  `content.convertPosition(..Local..)` then `contentProbe.convertPositionTo(local, null)` gives Stage metres.
  Surfaces get 6 mm two-sided box colliders (`plane:<SEMANTIC>:<uuid8>`), so a tap lands 3 mm off the plane and is
  put back along the viewing ray (`ResolvePlacementUseCase.onTappedSurface`). A shell of slabs (`backstop:*`)
  catches taps that hit no surface. Tap and drag recognizers are in separate `pointerInput` blocks.
- System drags: `detectSpatialDragGesture`. `dragAmount` is incremental and in view pixels (0.4 mm each); summed up
  it is the hand's travel, which on the emulator is about the cursor's travel at the depth of the target. The tail
  of a pulled tape follows the pointing-line rule of the design (`PullLineUseCase`), so it moves about four times
  as far as the cursor at 2 m. A drag can start on the surface behind a pin although the press lies inside the
  pin's touch sphere; so can a quick second tap on a pin that was just set (its collider is not up yet). Both are
  given to the pin (`RulerScene.pinTouching`); the reducer also treats a new point within 10 mm of a pin as that pin.
- Pointer ranking: the system pointer does not take the nearest hit on its ray. It ranks an attachment panel by the
  distance from the head to the panel's centre and a collider by the distance to the hit point. One box collider
  5 - 12 mm behind the body's glass took presses in the upper right part of the panel when the body was seen from
  the side. The body's collider is therefore a frame around the glass with nothing behind it (`BodyView`).
- A mesh costs about one rendered frame to create (0.4 s each at the emulator's idle 2 - 4 fps). Hence: shared
  meshes for pins, the blade while it moves (printed 10 cm / 1 ft segments), outlines, hooks, riders, stems and
  guides; one mesh per tape face (palette texture); one mesh build per frame (`MeshBudget`); the start-up build
  yields after every mesh. Blocking the main thread for the whole build raised a system ANR.
- Start-up order: `RulerScene.attach` returns at once with the body panel in place; the build waits until that
  panel has drawn (`SCENE body panel drawn`) and then makes the meshes (14 - 23 s at idle fps, 22 - 26 meshes).
- Materials: unlit colours are exact only with `setApplyToneMapping(false)` (`#FFC72D` sampled for `tapeYellow`,
  `#D1B132` with tone mapping). Lit materials are always tone-mapped (no switch on `PhysicallyBasedMaterial`): the
  housing gets an emissive term and samples `#E2C346`; steel at metallic 0.6 with 20 % of its colour emitted samples
  `#BABDBF`. A transparent unlit material multiplies base-colour alpha and `setOpacity`: use alpha 1 and the opacity.
- Resources are released when their last user entity is destroyed. Anything shared is `toGlobal()` and closed in
  `RulerScene.detach` (`SharedResources`). Without it, removing one pin closes the materials of all pins.
- Attachment panels: declared once with a fixed size (max 720 dp high), 1 dp = 0.8 mm at scale 1, shown and hidden
  through `PanelRig`. These findings shape it; do not undo any of them without re-testing with real pinches:
  1. A panel parented under a disabled holder never appears. A slot stays enabled, parked out of view at 1/1000
     size, until its panel is bound and has had 6 frames / 0.6 s to come up.
  2. A panel must not stay parked. A parked panel below a head anchor takes the pointer away from every panel on
     the line of sight through that anchor (a pinch on a body key went through to the floor behind). Hidden =
     holder disabled and stowed where no line of sight goes: room panels 30 m behind the viewer, head-mounted ones
     40 m below (`PanelRig.HideMode.AUTO`; `hidemode park` reproduces the dead ray; `rig` and the `RIG violation`
     check watch the rule).
  3. What a panel draws while it is not in place stays broken (black glass, a few rows). A pooled panel composes
     its content only after it is in view, and "after" means two engine frames later (`RulerScene.panelShown`):
     reported in the same frame, the snackbar sometimes came up black at idle fps.
  4. No `LookAtComponent` on a panel holder: the panel is drawn turned but the pointer hits it as if it were not.
     `PanelRig.faceViewer` writes yaw and pitch (clamped to 60 degrees, no roll) into the holder every frame.
  5. A panel takes the pointer over its whole rectangle, also where it draws nothing; such a pinch reaches nothing
     at all. A panel whose content is narrower than the panel (plates, snackbar) is squeezed to the content's width
     (`Slot.widthFactor`, `stretch_x` in `bounds`) and the content is drawn stretched by the inverse
     (`graphicsLayer { scaleX }`), so the picture is unchanged and no dead margin is left. A pointer modifier on the
     root of such a panel would make the whole panel take presses: the watcher (`panelPointer`) sits on the drawn
     part.
- The body (T11, lazy follow): placed by the app in Stage space, not by a camera anchor. It rests where it is
  in the room; once its centre has been more than 45 degrees off the view direction, or nearer than 0.35 m or
  farther than 0.9 m, for 0.5 s, it slides (0.35 s, ease-in-out cubic) to the rest pose of that moment: 18
  degrees left, 22 degrees down, 0.6 m, level, tilted back 12 degrees, rule R2 applied to the target
  (`BodyView.follow`; `BODY park reason=start|slide`, `BODY follow reason=angle|near|far|debug`). It holds
  still while a body key is pressed or was pressed within 0.6 s and while a menu or dialog is open. The first
  park waits for a head pose that is not the origin (the tracker's first poses are the origin). Panels mounted
  on the body (readout, menu, coachmark) and the collider move with it. It rests on
  surfaces (`RestBodyUseCase`: pushed out along the plane normal, never nearer than 0.35 m to the head). A pinch
  whose line of sight runs through the body but arrives on something behind it is dropped and, where the pointer's
  origin is known (emulator: left eye), the body key under the line of sight is pressed instead
  (`TAP blocked by=body ... key=`). This guard has not fired in normal use since the collider became a frame.
- Overlay hosting (design assumption 9), see `OverlayHosting`: `Menu` / `SubMenu` compose in a Stage attachment
  panel, but a real pinch on a row of the native popup only dismissed it in 2 of 3 tries, also with the cursor
  hijack excluded by a primed sequence (round 3, R2-3; the popup takes every press first, so a catcher panel
  cannot exclude H1 while it is open). The menu is therefore the pre-built attachment panel `MenuPanel` (id
  `menuPanel`; since round 4 its only host: language segment, snap toggle, Clear all, Exit - no popup carries
  those). `AlertDialog` composes nothing in a Stage and `SnackbarHost`
  answers `Failure("Snack is not supported in Stage. Use WindowContainer instead.")`: the product hosts are the
  pre-built attachment panels `DialogPanel` and `SnackPanel`, built to the components' metrics.
- Menu pinches (R2-2): a pinch at the scene, at a floating control or at a body key other than More while the
  menu is open only closes the menu (`TAP consumed reason=menu`, reducer); a scene tap within 0.4 s of a menu or
  dialog closing was the pinch that closed it and is dropped by the ViewModel (`TAP consumed reason=menu-closing`).
  The menu's own controls (language segment, snap toggle) act in place and leave the menu open; Clear all and
  Exit close it. A dialog is modal as before (its buttons only).
- Anchor rebase (R2-4): the fine-tune offset has no cap (a 2 m sanity bound only, logged when hit). When the
  fine-tune has rested 0.6 s after a drag or a key step, or the pin's tag closes, and the offset is beyond 50 mm,
  a new anchor is created at the pin's position (`RulerAction.RebaseDue` -> `RulerEffect.RebaseAnchor`); the pin
  shows "pinning" meanwhile and the old anchor still holds it; on success the pin takes the new anchor with a
  zero offset and the old anchor is removed (`ANCHOR rebase start id= uuid_old= du= dv=`, `ANCHOR rebase id=
  uuid_old= uuid_new= shift_mm=`); on failure the old anchor and the offset stay (`ANCHOR rebase failed`).
  Tapes name their pins by id and are untouched. Undo across a rebase (R3-1): the undo step's offsets belong to
  the old anchor's frame, so `undo` puts the pin back at the step's Stage position expressed in the new frame
  and at once re-creates the anchor there (`ANCHOR rebase start ... reason=undo`, then `ANCHOR rebase ...
  shift_mm=0`): the position comes back exactly, offset zero, connected tapes follow. `AnchorRebased` keeps the
  pin where it is (whatever the new anchor missed it by stays as the offset, normally zero).
- Deferred placement (round 5e, user report "tap did not place the first pin" right after a relaunch): a system tap
  that hits only the backstop is kept as the pending aim (`RulerEvent.UnmappedTapped(ray)` -> the ViewModel first
  tries the analytic planes, else `RulerAction.TapUnmapped(PendingTap(origin, direction, atMs))`): the latest one
  only, "Still mapping" snackbar (`NOT_MAPPED`), the body's spinner (`LcdUi.busy`), no `FEEDBACK fail`. The
  ViewModel re-tries the line of sight against `environment.surfaces()` on every `PlanesChanged` and every 300 ms
  (`PENDING_TAP_POLL_MS`); a hit places the pin through the normal path (`PendingTapResolved` -> `PIN add ...
  via=system deferred=true`, `TAP pending resolved after_ms=`), any pin placed another way ends it, and after
  `PENDING_TAP_MS` (15 s) without a surface it is dropped with `NO_SURFACE` (`TAP pending expired`). Orphan
  anchors found at launch go out as one `RulerEffect.RemoveOrphans` batch, removed one at a time 5 s after the
  launch (`ORPHAN_CLEANUP_DELAY_MS`), off the window in which the scene and the planes come up.
- Pin drag (round 5, `DragPinUseCase`, `RulerScene.drawSystemPinDrag` / `stepPinDrag`, reducer `StartPinDrag` /
  `EndPinDrag`): a press on a pin that moves past the system's drag slop starts `pin-drag` (the pin is selected,
  its strip opens). Target each frame = the pointer's hit on the surfaces (`RulerAim.preview(ray, exclude = the
  pin)`, no plumb / level snapping to the dragged pin itself): system drag (round 5b) -> a line of sight through
  the pin's cap, moved by the along-drag part of the press offset (`DragPinUseCase.startLine`: the system reports
  its press point where it recognised the drag, a degree or so along the way, on the touch sphere), then moved in
  the view's plane by every drag event's hand step (`RulerScene.movePinDrag`, `DragPinUseCase.turn`); the ray is
  lowered by the cap's height so that the cap, not the foot, stays under the cursor. The system's travel is the
  cursor's view-plane displacement times `equivalentDepth(distance, pitch)` = (0.67 + 1.186 x distance of the
  recognised press point from the head) x (1 + 0.0015 x |head pitch in degrees|), fixed for the drag, and it
  counts from `START_SHORT_U` (about 13 px) before the reported press point (round 5c: fitted jointly on the
  emulator over 36 drags of 150 / 250 px at 1.4 / 2.5 / 3.5 m, level and pitched 20 - 40 degrees down, rms
  5.9 px; the emulator's own run-to-run spread is about 4 %; emulator-only calibration: its virtual hand moves
  with the gaze), so a cursor move of x pixels moves the pin by x pixels at any distance. `AdaptiveGain`:
  1 : 1 while the line moves; 1 : 4 ("fine", the reticle tag says "×4") only after no drag event has carried a
  raw step of a pixel or more for 400 ms (round 5d, QA R5c: tail-safe, any step cancels the countdown, so on the
  emulator ×4 starts only when its 1.5 - 2 s tail of 1 px events has ended), let go on the FIRST event over
  4 deg/s with no delay; every event's gain comes from that event's own speed, its step over at most a 100 ms
  window (`AdaptiveGain.SPEED_WINDOW_MS`), never averaged over a rest, so a quick move's first event goes 1 : 1;
  the raw line keeps the whole travel and the aim line gets each step times the gain, so nothing is lost; every
  change is logged `PIN drag gain id= gain= speed= reason=still|fast`; tracked ray ->
  the aim ray with the fine aim at half press; contact -> the tip looked down onto its surface. No hit: the last
  valid target stays. The pin is drawn at the target with no smoothing of its own on the system path (round 5c;
  a tracked pointer's pin lags 30 ms) and lifted 6 mm (`PinViews.setDrag`), a hollow ghost ring (`pinGhost`:
  labelPrimaryLight 70 % over a 1 mm `reticleCasing` ring, depth test off) marks where it was, the reticle with
  its seat marks stands at the target lifted above the pin's top (`RETICLE_OVER_PIN_M`), no candidate chips while
  a pin is dragged, plates take no hover effect while a pin is dragged and until their next pointer enter after
  it (`RulerUiState.pinDragging`, `RulerScene.plateHover` armed by the panel watcher's Enter; a plate the captured
  pointer crosses keeps the SpatialUI hover tint and reads as dimmed, QA R5b-3 / R5c), the tapes on the
  pin count live (plates and the body's TUNING readout, `liveLengths`; `drawTapes` leaves the dragged pin's tapes
  alone). Release: the ViewModel resolves the last ray / tip to a placement;
  within 10 mm of the start nothing changes; on the same plane the move is an offset (anchor rebase at release
  beyond 50 mm, `reason=drag`); on another plane the pin re-seats with a fresh anchor (`reason=drag-plane`, PENDING
  meanwhile). Grade: contact / fine (×4 or half press at release) / coarse; snapped kept; one undo step restores
  (across a plane change the pin is set afresh at its old place, `reason=undo-plane`; an anchor answered for a
  place the pin has left is `ANCHOR stale` and remade). A tracked press that does not move the pin is a tap on
  it. Logs: `PIN drag start|move|end|release|gain|refused`, `DRAG begin mode=pin`. Known: at rest the reticle sits
  under the pin; the strip may stand over the ghost ring when every slot around the pin is taken.
- No tracked source (R2-1, R3-2): three seconds after the last tracked pointer source (controller, hand,
  synthetic) went away, when one had been active and the system pointer has delivered nothing since (a tap, a
  drag, a panel pointer event: `RulerScene.noteSystemInput`), the scene reports `SourceChanged(lost)` and the
  sticky snackbar "Raise a hand or pick up a controller to continue" stands until any source delivers input
  again - a tracked one, or the system pointer (gaze + pinch) with its first pinch or panel hover (`SOURCE
  lost|back`). On the emulator this shows after `hand_lost` (synthetic) or after controller mode was left,
  until the first gaze pinch; it never shows while gaze + pinch are in use.
- First-run coachmark: `CoachPanel`, a 360 x 80 dp glass panel mounted on the body 52 mm right of its right
  face (clear of the tape mouth), scale 0.75: pictogram (pin · tape · pin), "Tap two points, or hold and pull",
  ✓ (`coach_got_it`). One step, shown until the first tape locks or ✓ (`settings.coachSeen`, persisted; `COACH
  step=1|0`, `COACH dismissed|reset`, debug `coach reset`). Hidden while the menu is open, while tracking is lost
  and while a dialog shows.
- Reticle (M1): strokes 3 mm (ring 13 - 16 mm radius, steady 12.5 - 16.5, stability 33.5 - 36, cross 3.2 mm,
  dot 5 mm across), each with a casing of the same mesh 2.2 times as wide in `bladeEdge` 70 %, 0.3 mm towards
  the surface, depth test off; k = max(1, distance * 0.9); leans at most 60 degrees away from the viewer; the
  no-surface / still-mapping ring is hollow and dashed in `onInkSecondary`, its tag text too; `alert` only for far.
- Chips (F9): a candidate chip stands in front of every reading plate its rectangle (inflated 0.5 degrees)
  overlaps, 5 cm nearer, never nearer than 0.45 m (the tag rule R3.3); while a pin is selected the plates of
  tapes that do not end on it recede to 55 % like their blades (`PlateUi.dimmed`). Tags stay in front of chips.
- Live plate (M3): its second line is the tolerance the tape would get if it were set now (head sigma (+) aim
  sigma, root sum of squares, on the ladder; aim: fine when steady or half-pressed, 6 mm on a corner / edge, 2 mm
  (8 mm pinch) in contact, doubled beyond 3 m, "▲" in `alert` when far), "…" while that is not known and during a
  tap - tap run-out. The rider of a running tape sits 45 mm back from the tail (M2); the plate stays at the tail.
- Anchors: `WorldTrackingManager.createAnchor` at the Stage position, about 1 s at idle fps, 30 - 80 ms at 60 fps;
  `loadAnchor` error -7 means "none stored". Exit closes the Stage (`closeStage()`), the process stays alive;
  the next launch restores from storage + anchors.
- `HMDTrackingProvider.supportState` is `NOT_IN_FULL_SPACE` (and `start()` answers `PENDING`) while the Stage is
  still opening, e.g. when the app is started right after an install replaced the running one; it turns to
  `SUPPORTED` by itself. `HeadTracker.blockReason()` therefore reads it live, and the ViewModel gives a reason two
  seconds to go away before the session is blocked. Asked once at provider start, a healthy launch came up as
  "spatial features unavailable" (seen once, after the validation script's install + launch).
- Emulator frame rate: idle 1 - 4 fps, what you see lags by several seconds. `tools/harness/emu.sh waitfast` gives
  about 10 s of 60 fps (posted focus messages, no input). A press is only a spatial tap at 60 fps.
- The emulator's room has shelves, cabinets and a sideboard right behind the start position: the camera can be
  flown into furniture, where the body rests on whatever plane it is in. The plane quads are not the same from
  one launch to the next (the bed platform's corner moved 15 cm between two launches): a test takes plane
  vertices from `planes` at run time, never from an earlier log.
- Tracked pointers on the emulator. In eye mode `ControllerTrackingProvider` starts `PENDING` with
  `DEVICE_NOT_SUPPORTED`; `HandTrackingProvider` is `SUPPORTED` but delivers no hand - except right after it
  turned from `NOT_IN_FULL_SPACE` to `SUPPORTED` (a launch straight after an install), when `latestData.rightAim` is
  an aim at the Stage origin with `isReady=false`: `HandPointerSource` treats that placeholder as no hand, otherwise
  the mouse could no longer set pins (one-press rule). Right after that launch the guest's hand tracking stayed on
  (`SPR-HandTrackingDevice ... isHandTracking:true palmPos:0 0 0`) and eye-mode pinches stopped reaching any app
  (no `PTR`, no system `HOVER_ENTERED`), through app restarts, mode toggles and the tool bar's interaction button;
  only an emulator restart is left to try. `HandPointerSource` therefore does not start on the emulator at all
  (`wanted = !SystemPointer.onEmulator`); the synthetic pointer stands in for a hand there. In controller mode
  (`tools/harness/emu_mode.ps1 -Mode right`) the provider turns `SUPPORTED` as soon as the mouse moves; the
  controller ray then starts at the left eye and goes through the mouse cursor, a posted mouse button is the
  trigger (`triggerPressed` with `triggerValue` 0 or 1: no half press), and the system pointer follows the same
  ray, so a click also arrives as a spatial tap of kind `RayBasedPinch` (dropped by the one-press rule). Verified:
  reticle follows the mouse, a click sets `PIN add ... via=controller`, a click on a pin acts once, a click on a
  body key is left to the panel (`AIM commit skipped reason=body`, then `UI unit_mm`).
- One press, one action (`RulerScene.aimPointer`, `onSpatialTap`, `onSpatialDrag`): while a tracked source is
  active, its press edge is what sets a pin on a surface; a system tap on a surface or backstop collider is dropped
  (`TAP surface ... ignored reason=tracked-source`), a system tap on a pin within 0.7 s of a tracked commit is the
  same press (`ignored reason=tracked-commit`), and a system drag only trues the pin whose fine-tune is open. A
  press whose ray meets the body or a panel with controls first does nothing on the tracked side
  (`AIM commit skipped reason=body|panel:<id>`): the panel's own click handles it.
- Other AI sessions own `emulator-5554` and `emulator-5558`. Only ever use `emulator-5556` (AVD `PICO_6.1_Ruler`).

## UI rules

- All 2D UI is SpatialUI inside `PicoTheme` (`SpatialRulerTheme`). No Material / Material3.
- Icons over words (simplify spec A): text may stay only as figures (digits, ±, >, ×4, unit symbols, "— —"),
  language names, one sentence in the two dialogs, a snackbar message of <= 6 words after its icon, one word on a
  dialog / snackbar button and the menu's "Clear all", and the coachmark's seven words. Everything else is an
  `ic_*` vector (24 x 24 viewport, stroke 2.2, round caps, tinted where used; the stepper chevron is one drawable
  turned with `Modifier.rotate`) or a glyph the design defines (◆ ● ○ grade, ▲ far / unsaved in `alert`, ⦸ frozen
  / unavailable). Every icon-only control and status glyph carries a content description from `rs()`; the JVM
  test `StringsResourceTest` checks the keys, the zh mirror and the word budgets on the resource files.
- Colours come from `PicoTheme.colorScheme` roles or the brand tokens in `ui/theme/RulerColors.kt`; no colour
  literals elsewhere. Text on glass uses `labelPrimary`; text on solid dark faces (plate, tag card, readout) uses
  `labelPrimaryLight` and the on-ink tokens; content on the four semantic fills (`error`, `alert`, `passable`,
  `interaction`) is `tapeInk`, passed explicitly. Every `Text` passes `color` or carries
  `// design-style: inherited-content-color <Provider>`.
- Design tag -> component: segment-control -> `SegmentControl`/`SegmentItem`, icon-button -> `IconButton`,
  snap toggle -> `ToggleIconButton`, toggle-button -> `ToggleButton`, button -> `Button`, stepper -> `Stepper`,
  badge -> `Badge`, chip -> `ButtonChip`, menu -> `Menu`/`SubMenu`/`MenuItem`, progress -> `CircularProgressIndicator`,
  divider -> `Divider`. `sui-augment` -> AttachmentPanel root with `clip(shape)` then `backgroundMaterial(Material.Regular)`.
- `Button`: a size from `ButtonDefaults.buttonSize(...)` or `Regular.copy(...)` has no capsule shape (square
  corners); pass `shape` yourself. `buttonSize(...)` also has a small label; `Regular.copy(...)` keeps the text style.
- Copy lives in `res/values` (en) and `res/values-zh`; panels read it through `rs()` / `rq()` (`LocalRulerResources`),
  which is what makes the in-app language switch work without restarting the Stage.
- Nothing in the system-pointer path may need a long press.

## Build, test, run (Git Bash; device commands are pinned to emulator-5556)

```bash
tools/dev.sh build        # assembleDebug   (JAVA_HOME / PICO_HOME are set inside the script)
tools/dev.sh test         # 218 JVM unit tests
tools/dev.sh fg           # which app is in front. A Full Space app in front blocks the launch ("result code=100")
tools/dev.sh install && tools/dev.sh relaunch
tools/dev.sh log 'STATE|PIN|TAPE' 40
tools/dev.sh crash
python tools/smoke.py     # pose + planes -> two synthetic placements -> dump -> .scratch/shots/smoke.png
tools/cap.sh NAME         # console screenshot -> .scratch/shots/NAME.png
tools/dev.sh gates        # plugin machine gates (receipts, scan, build, launch, architecture, tests, design-style)
tools/harness/emu.sh ...  # host input for the test agent: see tools/harness/TESTING.md
```

## Debug commands (debug build only)

`adb -s emulator-5556 shell "am broadcast -a io.embodit.spatialruler.DEBUG -p io.embodit.spatialruler --es cmd NAME [--es a 'numbers'] [--es s 'text']"`
or `tools/dev.sh cmd NAME [numbers] [text]`.

| cmd | arguments | effect |
|---|---|---|
| `pointer` | a=ox,oy,oz,dx,dy,dz (none: remove) | synthetic pointing ray, Stage metres; answers `POINTER set ... hit=...` |
| `trigger` | | trigger of the synthetic pointer (same aiming code as a tracked pointer; steady after 0.35 s = fine) |
| `place` | a=x,y,z | pin at a Stage point (attached to a surface within 5 cm), coarse; like a tap: the second one pulls a tape |
| `tap_pin` | s=id | same as a tap on that pin |
| `connect` | s="idA idB" | tape between two pins |
| `undo` / `delete` s=id / `clear` | | |
| `unit` s=m\|cm\|mm\|ftin, `lang` s=system\|zh\|en, `snap` s=on\|off | | settings |
| `fail_next_anchor` | | next anchor creation fails: unsaved pin + Retry snackbar (also fails the next anchor rebase) |
| `body` | s=park | the body slides to its rest pose now (`BODY follow reason=debug`) |
| `coach` | s=reset | the first-run coachmark shows again |
| `ui` | s=control name | press a control by its stable name (names below) |
| `source` | s=controller\|hand\|system\|synthetic\|auto | which pointer aims; `controller` / `hand`: that device, the synthetic pointer standing in while it delivers nothing |
| `trigger` | s=half\|full\|release (none: one full press) | the synthetic pointer's trigger: half = 4 : 1 fine aim, full = commit |
| `contact` | a=x,y,z (none: remove) | tool tip of the synthetic pointer (contact channel) |
| `hand_lost` | | the synthetic pointer disappears |
| `steady` | s=on\|off\|auto | the aim counts as steady / not / by its timing |
| `src` | | `SRC active= forced= trigger= synthetic= controller[...] hand[...]`: what every source delivers now |
| `tracking` | s=lost\|ok | the head tracker reports lost tracking / its real state |
| `blocked` | s=on\|off | on: the session starts again as "spatial features unavailable"; off: lifts it (then press Retry) |
| `elsewhere` | a=n | the last n pins count as "not in this space" |
| `snack` | s=long\|normal | timed snackbars stay 60 s |
| `marks` | s=long\|normal | the seat mark of a snapped pin stays 10 s instead of 0.8 s |
| `dump` | | `DUMP {json}` (also `files/dump.json`): state, pins, tapes, settings, snackbar text and remaining time |
| `pose` | | `POSE hmd pos=(..) q(..) fwd=(..) up=(..) right=(..) valid= pointer_eye= eye_pos=(..)`, 5 decimals |
| `bounds` | | `PANEL id=.. pos=(stage) q(..) scale=.. size_px=WxH stretch_x=..` + `CTRL panel=.. name=.. cx= cy= w= h=` |
| `rig` | | `RIG slot ...` for all 35 pooled panels, `RIG body ...`, `RIG violation ...`, `RIG summary ...` |
| `planes` | | `PLANE <label> kind=.. center=.. normal=.. verts=..` |
| `fps` | s=on\|off | `FPS app=.. frames=.. ms=..` once a second |
| `calib` | s=on\|off\|clear, a=dist,scale,sizeDp[,offX,offY[,facing]] | head-locked harness calibration target |
| `gest` | s=both\|tap\|drag\|none | spatial gesture detectors installed |
| `overlay` | s="[dialog\|snackbar] native\|panel" | host of the dialog / snackbar (the menu is always a panel since round 4) |
| `hidemode` | s=auto\|park\|holder\|panel | how hidden pooled panels are taken out of sight (default `auto`) |
| `lit` | s=on\|off, or a=metallic,steelGlow,r,g,b | physically based or unlit steel / body; tune the lit materials |
| `tonemap` | s=on\|off | tone mapping of the unlit materials (off is the product setting) |
| `ghost` | s=on\|off | hides the body panel and switches its collider off, to exercise the guard in front of the body |
| `collider` | [a=mm] | the body's collider: frame (product) or one box that far behind the glass |

Control names: `undo more unit_m unit_cm unit_mm unit_ftin` (body) · `lang_system lang_zh lang_en snap menu_clear
menu_exit` (menuPanel) · `pin_tune pin_remove pin_close step_u_minus step_u_plus step_v_minus step_v_plus`
(pinTag, 304 x 128, always with the steppers ‹ › and ˅ ˄; `pin_tune` and `pinTagTune` are gone in round 5) · `tape_retract` (tapeTag) · `coach_got_it` (coachPanel) ·
`dialog_cancel dialog_confirm blocked_exit blocked_retry snackbar snackbar_action chip:<pinId> plate:<tapeId>`.
Gone in round 4: `menu_language`, `tape_close` and the panel `langPanel`; `snap` and `lang_*` moved from the
body / language popup to `menuPanel`. The old line, for scripts that still carry it:
`undo snap more unit_m unit_cm unit_mm unit_ftin pin_tune pin_remove pin_close step_u_minus
step_u_plus step_v_minus step_v_plus tape_retract tape_close menu_language menu_clear menu_exit lang_system lang_zh
lang_en dialog_cancel dialog_confirm blocked_exit blocked_retry snackbar snackbar_action chip:<pinId> plate:<tapeId>`.
Panel ids: `body plate0..19 plateLive chip0..5 pinTag tapeTag reticleTag menuPanel coachPanel dialogPanel snackPanel`
(35 pooled slots; sizes: body 340 x 136, plates 184 x 40, chips 104 x 36, pinTag 304 x 128,
tapeTag 166 x 70, reticleTag 96 x 24, menuPanel 256 x 120, coachPanel 360 x 80, dialogPanel 480 x 280, snackPanel 560 x 72 dp).
Entity names: `pin:<id> pinView:<id> stub:<pinId> pinGhost plane:<SEMANTIC>:<uuid8> backstop:<side> body holder:<panelId> panel:<panelId>`.
The menu is the attachment panel `menuPanel` (round 3; its only host since round 4, `overlay menu ...` is a no-op). `python tools/ctrlpx.py <panel> <control | @cx,cy>` prints the
console-screenshot pixel of a control or of a raw panel point from `bounds` + `pose` + the camera model.

## Log (tag `SpatialRuler`, one line per event; positions are Stage metres, 4 decimals; `POSE` / `PANEL` / `RIG` 5)

`STATE <name>` (starting, restoring, idle-scanning, idle, pulling, pulling-drag, pin-selected, pin-selected-tuning,
truing, tape-selected, more-menu, language-menu, confirm-clear, tracking-lost, blocked, exited) · `PLANES n=` ·
`TAP surface|pin|backstop|pointer|other|blocked|ignored ... stage= view_px= near=` (`TAP pointer kind=controller|hand|synthetic steady= fine= grade= stage=` for a tracked commit, `... contact=true surface= off_mm=` for a contact) · `SRC active=` · `AIM fine=on|off`, `AIM commit skipped reason=` · `RETICLE state=aim|steady|fine|far|snap|contact|none|mapping|blocked:... stage= dist= stable= snap= src=` (on change, else every 2 s) · `FEEDBACK <kind> hand=` · `DRAG start|begin|move|end|cancel ... stage=` ·
`PULL start|end|cancel` · `PIN add ... via=system|controller|hand|synthetic|contact|pull|debug|remove|nudge|true|elsewhere|forget` · `ANCHOR saved|failed|removed|load` ·
`TAPE add|remove|duplicate|refused` · `TAG pin|tape key= slot= steps= leader= range= anchor_range=` · `UNIT` · `LANG` ·
`SNAP` · `UNDO` · `CLEAR` · `RESTORE pins= tapes= elsewhere=` · `SNACK <kind> id= ms=` · `TRACK lost|recovered` ·
`SOURCE lost|back` · `BODY park reason=start|slide pos= yaw=` · `BODY follow reason=angle|near|far|debug angle= dist= to=` ·
`TAP consumed reason=menu|menu-open|menu-closing` · `ANCHOR rebase start|failed`, `ANCHOR rebase id= uuid_old= uuid_new= shift_mm=` ·
`COACH step=|dismissed|reset` · `PIN true|nudge refused ... reason=beyond-2000mm` ·
`BLOCKED reason=` · `UI <control>` · `OVERLAY ...` · `SCENE body panel up|drawn`, `SCENE attached build_ms=` ·
`STAGE ...` · `CMD recv ...` · debug builds only: `PTR panel=<id> ev=Press|Release|Enter|Exit x= y=` (pointer events
on the drawn part of a panel, panel pixels), `RIG slot id= shown|parked|stowed|kick`, `RIG violation ...`, `FPS app=`.

## Next steps (round 3) and open points

Seams: `BodyView.follow` (lazy-follow tuning beyond the rule: window, times, easing are constants at the end
of the file), `SystemPointer` (the pointer's origin on a headset is unknown: the body guard then only drops the pinch and cannot
press the key), `RulerScene.aimPointer` (press-and-hold pulling with a tracked ray is not built: a tracked press
commits at its edge; the live tape to the next aim replaces it), `ControllerPointerSource.TIP_M` (the virtual tip
is 6 cm ahead of the controller pose; the contact reticle is drawn there, so what is seen is what commits).
Open points: D1 of test round 1 (top of the body dead after tags were shown and closed) was not reproduced; the
`RIG violation` check is the tripwire. Plates overlap when there are more of them than there is room for. Lit
yellow cannot be brought exactly onto the token (tone mapping). Hand tracking, analog triggers and haptics have
only run as code paths: the emulator has no hands and a binary trigger, and nothing here was run on a headset.
