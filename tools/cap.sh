#!/usr/bin/env bash
# Screenshot of emulator-5556 (AVD PICO_6.1_Ruler).
#
# Default: host-side emulator console capture (`adb emu screenrecord screenshot <dir>`): always returns a
# complete frame, even while the emulator compositor idles at 2-3 fps (unfocused window).
# `--guest`: guest-side `pico-cli capture screenshot` (adb screencap). At 2-3 fps this returns frames whose
# lower part is transparent; it is retried until the alpha channel is fully opaque (max 8 tries).
#
# Usage: tools/cap.sh <name> [--guest]   -> .scratch/shots/<name>.png
set -u
DEV=emulator-5556
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.scratch/shots/$1.png"
mkdir -p "$ROOT/.scratch/shots/emu"
avd="$(adb -s $DEV emu avd name 2>/dev/null | head -1 | tr -d '\r')"
if [ "$avd" != "PICO_6.1_Ruler" ]; then echo "WRONG AVD on $DEV: '$avd'"; exit 2; fi

if [ "${2:-}" != "--guest" ]; then
  WINDIR="$(cygpath -w "$ROOT/.scratch/shots/emu")"
  before="$(ls -1 "$ROOT/.scratch/shots/emu" 2>/dev/null | wc -l)"
  adb -s $DEV emu screenrecord screenshot "$WINDIR" >/dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.5
    after="$(ls -1 "$ROOT/.scratch/shots/emu" 2>/dev/null | wc -l)"
    [ "$after" -gt "$before" ] && break
  done
  newest="$(ls -1t "$ROOT/.scratch/shots/emu"/Screenshot_*.png 2>/dev/null | head -1)"
  if [ -z "$newest" ]; then echo "console screenshot failed"; exit 1; fi
  sleep 0.5
  mv -f "$newest" "$OUT"
  echo "OK $OUT"
  exit 0
fi

for i in 1 2 3 4 5 6 7 8; do
  pico-cli capture screenshot --device $DEV --out "$OUT" >/dev/null 2>&1
  res="$(python - "$OUT" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1])
a = im.getchannel('A').getextrema() if 'A' in im.mode else (255, 255)
if 'A' in im.mode:
    h = im.getchannel('A').histogram()
    frac = h[0] / float(im.size[0] * im.size[1])
else:
    frac = 0.0
print(f"{im.size[0]}x{im.size[1]} alpha={a} transparent={frac:.3f}")
sys.exit(0 if a[0] == 255 else 1)
PY
)"
  rc=$?
  echo "try $i: $res"
  if [ $rc -eq 0 ] && [ $i -ge 2 ]; then echo "OK $OUT"; exit 0; fi
  sleep 1
done
echo "GAVE UP (last capture kept) $OUT"; exit 1
