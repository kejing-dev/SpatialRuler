#!/usr/bin/env python
"""Screen pixel of a panel control, from the app's `bounds` + `pose` report and the emulator camera model.

usage: python tools/ctrlpx.py <panel id> <control name>      -> prints "X Y" (console-screenshot pixels)
       python tools/ctrlpx.py <panel id> @<cx>,<cy>          -> same for a raw point in panel pixels (may lie
                                                               outside the panel, e.g. body @340,-20 = the
                                                               housing rail 6 mm above the body panel)

Panel point (cx, cy) in panel pixels is at  pos + R(q) * ((cx - w/2) * stretch_x, (h/2 - cy), 0) * 0.0004 * scale  in
Stage metres (1 dp = 2 px = 0.8 mm at scale 1; stretch_x is 1 unless the PANEL line names it: plates and the snackbar
are as narrow as their content). Camera model (tools/harness/TESTING.md): left eye, fx = fy = 1286.8 px,
principal point (1080, 1079), eye 0.032 m to the left of the head pose.
"""
import re
import subprocess
import sys
import time

DEV = "emulator-5556"
PKG = "io.embodit.spatialruler"
TAG = "SpatialRuler"


def adb(*args):
    r = subprocess.run(["adb", "-s", DEV, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return r.stdout or ""


def nums(text):
    return [float(v) for v in text.split(",")]


def rotate(q, v):
    x, y, z, w = q
    c = (y * v[2] - z * v[1], z * v[0] - x * v[2], x * v[1] - y * v[0])
    t = [2 * a for a in c]
    c2 = (y * t[2] - z * t[1], z * t[0] - x * t[2], x * t[1] - y * t[0])
    return [v[i] + w * t[i] + c2[i] for i in range(3)]


def main():
    panel, control = sys.argv[1], sys.argv[2]
    mark = adb("shell", "date '+%m-%d %H:%M:%S.000'").strip()
    for name in ("bounds", "pose"):
        adb("shell", f"am broadcast -a {PKG}.DEBUG -p {PKG} --es cmd {name}")
    text = ""
    for _ in range(40):
        text = adb("logcat", "-d", "-v", "time", "-T", mark, "-s", f"{TAG}:V")
        if "BOUNDS panels" in text and "POSE hmd" in text:
            break
        time.sleep(0.3)
    pm = re.search(rf"PANEL id={re.escape(panel)} pos=\(([^)]*)\) q\(([^)]*)\) scale=([\d.]+) size_px=(\d+)x(\d+)(?: stretch_x=([\d.]+))?", text)
    if control.startswith("@"):
        cm = re.match(r"@(-?[\d.]+),(-?[\d.]+)$", control)
    else:
        cm = re.search(rf"CTRL panel={re.escape(panel)} name={re.escape(control)} cx=([\d.]+) cy=([\d.]+)", text)
    hm = re.search(r"POSE hmd pos=\(([^)]*)\).*fwd=\(([^)]*)\) up=\(([^)]*)\) right=\(([^)]*)\)", text)
    if not (pm and cm and hm):
        sys.exit(f"not found: panel={bool(pm)} control={bool(cm)} pose={bool(hm)}")
    pos, q, k = nums(pm.group(1)), nums(pm.group(2)), float(pm.group(3))
    w, h = int(pm.group(4)), int(pm.group(5))
    cx, cy = float(cm.group(1)), float(cm.group(2))
    head, fwd, up, right = (nums(hm.group(i)) for i in range(1, 5))
    stretch = float(pm.group(6)) if pm.group(6) else 1.0
    local = [(cx - w / 2) * 0.0004 * k * stretch, (h / 2 - cy) * 0.0004 * k, 0.0]
    r = rotate(q, local)
    p = [pos[i] + r[i] for i in range(3)]
    d = [p[i] - head[i] for i in range(3)]
    dot = lambda a, b: sum(a[i] * b[i] for i in range(3))
    depth = dot(d, fwd)
    px = 1080 + 1286.8 * (dot(d, right) + 0.032) / depth
    py = 1079 - 1286.8 * dot(d, up) / depth
    print(f"{px:.0f} {py:.0f}")


if __name__ == "__main__":
    main()
