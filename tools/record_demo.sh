#!/usr/bin/env bash
# record_demo.sh - records the ~42 s showcase take of Spatial Ruler (空间尺) on this project's own emulator.
#
#   bash tools/record_demo.sh [OUT.mp4]      record   (default: docs/media/SpatialRuler-demo.mp4)
#   bash tools/record_demo.sh --rehearse     run the same scenario without recording (checks every pixel target)
#   add --keep to leave the end state on screen (default: restore the demo state afterwards)
#
# Device: pinned to emulator-5556 = AVD PICO_6.1_Ruler; the script refuses to run when that serial is another AVD.
# emulator-5554 / 5558 belong to other sessions and are never addressed. Host input goes only through
# tools/harness/emu.ps1 (PostMessage to the windows of the Ruler emulator's qemu PID; the real cursor and the real
# foreground window are never touched). Debug broadcasts are used for setup only, never inside the take.
#
# Needs: the debug build of io.embodit.spatialruler installed (v1.0.0), the emulator in eye mode
# (tools/harness/emu_mode.ps1 -Mode status), the camera at the start pose (head (0, 1.65, 0), looking along -z),
# the calib catcher off, Git Bash + Python 3 with tools/test/rt.py. No ffmpeg: the guest records straight into the
# final format (H.264, 1080 x 1080, 6 Mbit/s) and the file is only pulled.
#
# Scenario (seconds into the video, as measured on the take of 2026-10-06 20:44; all scene actions are real
# injected gaze + pinch input):
#    0    the freshly launched app: instrument body + first-run coachmark ("Tap two points, or hold and pull")
#    3.8  tap floor point A (right of the bed, 2.9 m); 6.2 tap floor point B (3.9 m): tape 1 runs out between
#         them, its plate counts up, the body's readout shows the length, the coachmark goes away
#    9.4  tap pin A: it becomes the tape head (pin strip); 11.8 tap a point on the mattress (C): tape 2 from A to C
#   15.2  press pin C and drag it ~29 cm along the mattress: live blade, plate and body readout count, ghost ring,
#         reticle; the pinch is then held still: 19.8 the reticle tag shows x4 (1:4 fine); 21.5 release (grade
#         fine; pin strip + candidate chip)
#   25.4  close the pin strip (x); 27.9 units ft-in; 30.3 back to cm
#   32.7  More (...); 35.2 中文 in the language segment (the menu stays open); 37.6 More again closes the menu
#   41.6  end: the two tapes with their plates (the recording is stopped with SIGINT)
# Known product detail seen in every take: on the drag release the moved tape's plate shows its pre-drag length
# for 2 - 4 frames (< 0.1 s) before the new one.
# Every step is checked in the app log (PIN add, TAPE add, TAG pin, PIN drag start / gain=1:4 / end, UI ..., UNIT,
# LANG, STATE). If one fails, the recording is stopped and discarded and the script exits 1: run it again.
#
# Frame rate: the emulator renders at 2-4 fps while Qt believes its window is unfocused. Every emu.ps1 call starts
# with a posted WM_KILLFOCUS + WM_SETFOCUS (emu.ps1 "Rearm", real focus unchanged), i.e. the take re-arms 60 fps at
# every action (at most ~10 s apart) and never while a press is held. Do not take console screenshots during a take
# (they stall the compositor for a moment).
#
# Pixel targets: the Stage points A, B, C are projected with the head pose read at run time and checked against the
# room planes read at run time (the planes move a little between launches; a target that would land on another plane
# or beyond 3 m stops the script); pins are pressed at the pixel of their head (14.5 mm above the target point); panel
# controls come from the app's `bounds` + `pose` (tools/test/rt.py, same camera model as tools/ctrlpx.py).
set -u
DEV=emulator-5556
AVD=PICO_6.1_Ruler
PKG=io.embodit.spatialruler
TAG=SpatialRuler
export ANDROID_SERIAL=$DEV ADB_SERIAL=$DEV PICO_CLI_DEVICE=$DEV
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
EMU_PS1="$(cygpath -w "$HERE/harness/emu.ps1")"

REHEARSE=0; KEEP=0; OUT=""
for a in "$@"; do
  case "$a" in
    --rehearse) REHEARSE=1 ;;
    --keep) KEEP=1 ;;
    *) OUT="$a" ;;
  esac
done
OUT="${OUT:-$ROOT/docs/media/SpatialRuler-demo.mp4}"
REMOTE=/sdcard/spatialruler-demo.mp4

TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"' EXIT

# ---------------------------------------------------------------- helpers
check() {
  local avd
  avd="$(adb -s $DEV emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  if [ "$avd" != "$AVD" ]; then echo "WRONG AVD on $DEV: '$avd' (expected $AVD)" >&2; exit 2; fi
}
cmd() {  # cmd NAME [s]  - debug broadcast (setup only)
  adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd $1 ${2:+--es s '$2'}" >/dev/null
}
devnow() { adb -s $DEV shell "date '+%m-%d %H:%M:%S.%3N'" | tr -d '\r'; }
logsince() { adb -s $DEV logcat -d -v time -T "$1" -s "$TAG:V" 2>/dev/null | tr -d '\r'; }
waitlog() {  # waitlog MARK REGEX TIMEOUT_S -> 0 when REGEX appeared in the app log since MARK
  local end=$(( $(date +%s) + $3 ))
  while :; do
    logsince "$1" | grep -qE -- "$2" && return 0
    [ "$(date +%s)" -ge "$end" ] && return 1
    sleep 0.25
  done
}
inp() { MSYS_NO_PATHCONV=1 powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$EMU_PS1" "$@" -Avd "$AVD" | tr -d '\r'; }
tap() { inp click "$1" "$2" -FastWaitMs 300 -SettleMs 400 >/dev/null; }   # re-arm 60 fps, gaze, pinch 150 ms
nowf() { date +%s.%N; }
since_rec() { python -c "print(f'{$(nowf) - $T0:5.1f}')"; }
at() {  # at SECONDS: wait until that time after the start of the recording
  local wait
  wait="$(python -c "print(max(0.0, $T0 + $1 - $(nowf)))")"
  sleep "$wait"
}
PY() { python "$(cygpath -w "$TMPD/rd.py")" "$@"; }

cat > "$TMPD/rd.py" <<'PYEOF'
import math, os, struct, sys
ROOT = os.environ["RD_ROOT"]
sys.path.insert(0, os.path.join(ROOT, "tools", "test"))
import rt  # noqa: E402

# Stage targets (metres) for the start pose; heights are taken from the planes at run time
A = (1.40, -2.25)        # floor, right of the bed, 2.9 m from the eye (under 3 m: no far mark)
B = (1.55, -3.40)        # floor, right of the bed, 3.9 m (tape 1 about 1.16 m, unlike tape 2)
C = (0.35, -2.60)        # mattress top, right half (the drag takes it ~29 cm towards the middle)
CAP_M = 0.0145           # pin head above its foot (ResolvePlacementUseCase.PIN_HEAD_M)

def pixel_ok(p):
    return p and 60 <= p[0] <= 2100 and 60 <= p[1] <= 2100

def cmd_pose():
    hp = rt.pose()
    pos, fwd = hp["pos"], hp["fwd"]
    yaw = math.degrees(math.atan2(-fwd[0], -fwd[2])); pitch = math.degrees(math.asin(max(-1, min(1, fwd[1]))))
    dpos = math.dist(pos, [0.0, 1.65, 0.0])
    print(f"pose pos=({pos[0]:.3f},{pos[1]:.3f},{pos[2]:.3f}) yaw={yaw:.2f} pitch={pitch:.2f} off_start={dpos:.3f}m")
    sys.exit(0 if dpos < 0.03 and abs(yaw) < 1.0 and abs(pitch) < 1.0 else 3)

def cmd_targets():
    hp = rt.pose()
    pls = rt.planes()
    floor = [p for p in pls if p["kind"] == "FLOOR"]
    tops = [p for p in pls if p["kind"] == "BED" and abs(rt.plane_normal(p)[1]) > 0.9]
    if not floor or not tops:
        sys.exit("planes missing: floor=%d bed=%d" % (len(floor), len(tops)))
    fl = max(floor, key=lambda p: (max(v[0] for v in p["verts"]) - min(v[0] for v in p["verts"])))
    mat = max(tops, key=lambda p: p["verts"][0][1])          # the mattress top is the highest bed plane
    fy = fl["verts"][0][1]; my = mat["verts"][0][1]
    xs = [v[0] for v in mat["verts"]]; zs = [v[2] for v in mat["verts"]]
    cx = min(max(C[0], min(xs) + 0.25), max(xs) - 0.25); cz = min(max(C[1], min(zs) + 0.25), max(zs) - 0.25)
    out = []
    for name, P, want, maxrange in (("A", (A[0], fy, A[1]), fl["label"], 2.98), ("B", (B[0], fy, B[1]), fl["label"], 9.0),
                                    ("C", (cx, my, cz), mat["label"], 2.98)):
        p = rt.project(list(P), hp)
        if not pixel_ok(p):
            sys.exit(f"{name} {P} is not in view: {p}")
        o, d = rt.ray(p[0], p[1], hp)
        h = rt.first_hit(o, d, pls)
        if not h or h[2]["label"] != want or rt.dist(h[1], list(P)) > 0.03 or h[0] > maxrange:
            sys.exit(f"{name} {P} px=({p[0]:.0f},{p[1]:.0f}) first hit {h[2]['label'] if h else None} t={h[0] if h else 0:.2f} (want {want})")
        out.append(f"{p[0]:.0f} {p[1]:.0f}")
    # cap pixels (pin head) of the pins that will be set at A and C: pressed later in the take
    for P in ((A[0], fy + CAP_M, A[1]), (cx, my + CAP_M, cz)):
        p = rt.project(list(P), hp)
        out.append(f"{p[0]:.0f} {p[1]:.0f}")
    print(" ".join(out))

def cmd_ctrl(panel, name):
    b = rt.bounds(); hp = rt.pose()
    if panel not in b or name not in b[panel]["ctrls"]:
        sys.exit(f"no control {panel}/{name}")
    x, y = rt.ctrl_px(b, hp, panel, name)
    print(f"{x:.0f} {y:.0f}")

def boxes(buf, s, e):
    i = s
    while i + 8 <= e:
        size, typ = struct.unpack(">I4s", buf[i:i + 8]); hdr = 8
        if size == 1:
            size = struct.unpack(">Q", buf[i + 8:i + 16])[0]; hdr = 16
        elif size == 0:
            size = e - i
        yield typ.decode("latin1"), i + hdr, i + size
        i += size

def find(buf, s, e, path):
    for t, cs, ce in boxes(buf, s, e):
        if t == path[0]:
            if len(path) == 1:
                yield cs, ce
            else:
                yield from find(buf, cs, ce, path[1:])

def cmd_mp4(path):
    buf = open(path, "rb").read()
    ms, me = next(find(buf, 0, len(buf), ["moov"]))
    for ts_, te in find(buf, ms, me, ["trak"]):
        hs, he = next(find(buf, ts_, te, ["mdia", "hdlr"]))
        if buf[hs + 8:hs + 12] != b"vide":
            continue
        ss, se = next(find(buf, ts_, te, ["mdia", "minf", "stbl", "stsd"]))
        codec = buf[ss + 12:ss + 16].decode("latin1")
        w, h = struct.unpack(">HH", buf[ss + 8 + 24 + 8:ss + 8 + 24 + 12])
        mds, mde = next(find(buf, ts_, te, ["mdia", "mdhd"]))
        scale, dur = struct.unpack(">II", buf[mds + 12:mds + 20])
        st, _ = next(find(buf, ts_, te, ["mdia", "minf", "stbl", "stts"]))
        n = struct.unpack(">I", buf[st + 4:st + 8])[0]
        deltas = []
        for k in range(n):
            c, dd = struct.unpack(">II", buf[st + 8 + 8 * k:st + 16 + 8 * k]); deltas += [dd] * c
        gaps = [d * 1000.0 / scale for d in deltas]
        long_ = sum(1 for g in gaps if g >= 250)
        print(f"{os.path.basename(path)}: {codec} {w}x{h} {dur / scale:.2f} s, {len(deltas)} frames, "
              f"longest frame {max(gaps):.0f} ms, frames >= 250 ms: {long_}")
        sys.exit(0 if codec == "avc1" and (w, h) == (1080, 1080) and max(gaps) < 600 else 4)
    sys.exit("no video track")

c = sys.argv[1]
{"pose": cmd_pose, "targets": cmd_targets, "ctrl": cmd_ctrl, "mp4": cmd_mp4}[c](*sys.argv[2:])
PYEOF
export RD_ROOT="$(cygpath -w "$ROOT")"

demo_state() {  # the state the emulator is left in: scene cleared, cm, system language, snap on, coachmark due
  cmd clear; sleep 0.3; cmd unit cm; sleep 0.3; cmd lang system; sleep 0.3; cmd snap on; sleep 0.3
  cmd coach reset; sleep 0.5
}
relaunch() {
  adb -s $DEV shell am force-stop $PKG; sleep 1
  local m; m="$(devnow)"
  adb -s $DEV shell am start -n $PKG/.platform.LaunchActivity >/dev/null
  inp fast on >/dev/null
  waitlog "$m" "SCENE attached" 90 || { echo "scene did not attach" >&2; return 1; }
  waitlog "$m" "PLANES n=[1-9]" 60 || { echo "no room planes" >&2; return 1; }
  waitlog "$m" "COACH step=1" 30 || { echo "coachmark not shown" >&2; return 1; }
  sleep 4   # orphan-anchor cleanup and the first fps settle
}
wait60() {  # posted focus-in, then wait for the compositor to report >= 50 fps
  local m i f; m="$(adb -s $DEV shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
  inp fast on >/dev/null
  for i in $(seq 1 40); do
    f="$(adb -s $DEV logcat -d -v time -T "$m" 2>/dev/null | grep PxrMetric | tail -1 | sed -E 's/.*FPS=([0-9]+).*/\1/')"
    [ -n "$f" ] && [ "$f" -ge 50 ] && return 0
    sleep 0.25
  done
  echo "WARN: emulator not at 60 fps" >&2; return 1
}

FAILED=""
step() {  # step NAME MARK REGEX TIMEOUT
  if waitlog "$2" "$3" "$4"; then echo "  [$(since_rec)] ok   $1"
  else echo "  [$(since_rec)] FAIL $1 (no /$3/ in the log)"; FAILED="${FAILED:-$1}"; fi
}
abort_take() {
  echo "take failed at: $FAILED" >&2
  if [ $REHEARSE -eq 0 ]; then
    adb -s $DEV shell "pkill -INT screenrecord" >/dev/null 2>&1; wait "$REC" 2>/dev/null
    adb -s $DEV shell "rm -f $REMOTE" >/dev/null
  fi
  exit 1
}

# ---------------------------------------------------------------- setup (not recorded)
check
echo "setup: demo state + relaunch"
demo_state
relaunch || exit 1
PY pose || { echo "camera is not at the start pose (head (0, 1.65, 0), looking -z): restore it first" >&2; exit 3; }
read -r AX AY BX BY CX CY PAX PAY PCX PCY <<<"$(PY targets)" || exit 1
[ -n "${PCY:-}" ] || { echo "target check failed" >&2; exit 1; }
read -r FTX FTY <<<"$(PY ctrl body unit_ftin)"
read -r CMX CMY <<<"$(PY ctrl body unit_cm)"
read -r MOX MOY <<<"$(PY ctrl body more)"
cmd ui more; sleep 1.5                                            # open the menu once to read its control
read -r ZHX ZHY <<<"$(PY ctrl menuPanel lang_zh)"
cmd ui more; sleep 1.5
[ -n "${ZHY:-}" ] && [ -n "${MOY:-}" ] && [ -n "${CMY:-}" ] && [ -n "${FTY:-}" ] || { echo "control lookup failed" >&2; exit 1; }
echo "targets: A=($AX,$AY) B=($BX,$BY) C=($CX,$CY) capA=($PAX,$PAY) capC=($PCX,$PCY) ft-in=($FTX,$FTY) cm=($CMX,$CMY) more=($MOX,$MOY) zh=($ZHX,$ZHY)"
wait60

# ---------------------------------------------------------------- the take
REC=""
if [ $REHEARSE -eq 0 ]; then
  adb -s $DEV shell "rm -f $REMOTE" >/dev/null
  adb -s $DEV shell "screenrecord --size 1080x1080 --bit-rate 6000000 --time-limit 45 $REMOTE" &
  REC=$!
  sleep 0.4
fi
T0="$(nowf)"
echo "take started"

at 1.2;  m="$(devnow)"; tap "$AX" "$AY"; step "tap A (floor)" "$m" "PIN add id=" 3
m="$(devnow)"; tap "$BX" "$BY"; step "tap B (floor) -> tape 1" "$m" "TAPE add id=" 3
[ -n "$FAILED" ] && abort_take
at 6.8;  m="$(devnow)"; tap "$PAX" "$PAY"; step "tap pin A -> head + strip" "$m" "TAG pin key=" 3
m="$(devnow)"; tap "$CX" "$CY"; step "tap C (mattress) -> tape 2" "$m" "TAPE add id=" 3
[ -n "$FAILED" ] && abort_take
m="$(devnow)"
# 140 px in 35 steps of 60 ms (about 3 deg/s: 1:1 while it moves), then the pinch is held still for 4.8 s: the
# emulator's motion tail ends after about 2.5 s, 0.4 s later the gain turns 1:4 and the reticle tag shows x4; the
# release at rest keeps 1:4 (grade fine). Moving on after the rest is avoided on purpose: the emulator often batches
# the first step after a rest into one fast event, which lets go of x4.
inp drag "$PCX" "$PCY" $((PCX - 140)) "$PCY" -Steps 35 -StepMs 60 -HoldMs 300 -EndHoldMs 4800 -FastWaitMs 300 -SettleMs 400 >/dev/null &
DRAGP=$!
# the pin strip opens where the drag starts and stays there: read its close button during the rest (read-only query)
sleep 5; PY ctrl pinTag pin_close > "$TMPD/close.txt" 2>/dev/null
wait $DRAGP
step "drag pin C" "$m" "PIN drag start id=" 2
step "x4 during the rest" "$m" "PIN drag gain id=.* gain=1:4" 1
step "release at x4" "$m" "PIN drag release id=.*gain=1:4 grade=fine cancelled=false" 3
read -r PXX PXY < "$TMPD/close.txt"
[ -n "${PXY:-}" ] || { echo "  pin strip close button not found"; FAILED="${FAILED:-pin strip lookup}"; }
[ -n "$FAILED" ] && abort_take
m="$(devnow)"; tap "$PXX" "$PXY"; step "close the pin strip" "$m" "UI pin_close" 3
m="$(devnow)"; tap "$FTX" "$FTY"; step "unit ft-in" "$m" "UNIT ftin" 3
m="$(devnow)"; tap "$CMX" "$CMY"; step "unit cm" "$m" "UNIT cm" 3
[ -n "$FAILED" ] && abort_take
m="$(devnow)"; tap "$MOX" "$MOY"; step "open More" "$m" "STATE more-menu" 3
m="$(devnow)"; tap "$ZHX" "$ZHY"; step "中文" "$m" "LANG zh" 3
m="$(devnow)"; tap "$MOX" "$MOY"; step "close More" "$m" "menuPanel stowed|STATE idle" 3
[ -n "$FAILED" ] && abort_take
inp fast on >/dev/null
sleep 2.4                                                         # end on the two tapes with their plates
echo "take ended at $(since_rec) s"

if [ $REHEARSE -eq 0 ]; then
  adb -s $DEV shell "pkill -INT screenrecord" >/dev/null 2>&1
  wait "$REC" 2>/dev/null
  sleep 1
  mkdir -p "$(dirname "$OUT")"
  # remote paths start with // so that Git Bash does not rewrite them
  adb -s $DEV pull "/$REMOTE" "$(cygpath -w "$OUT")" >/dev/null && adb -s $DEV shell "rm -f $REMOTE" >/dev/null
  PY mp4 "$(cygpath -w "$OUT")" || echo "WARN: check the file (codec, size or a long frame gap)" >&2
fi

if [ $KEEP -eq 0 ]; then
  echo "restoring the demo state"
  demo_state
  relaunch >/dev/null || echo "WARN: relaunch for the demo state did not finish" >&2
fi
echo done
