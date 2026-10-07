#!/usr/bin/env python
"""Smoke check on emulator-5556: one synthetic placement pair through the debug receiver.

Nothing is hard-coded in world space: the head pose and the floor plane are read from the running
app (`pose`, `planes`), two points on the floor in front of the viewer are computed from them, and a
synthetic pointing ray is aimed at each (`pointer` + `trigger`, the same aiming code a tracked
pointer uses). Prints the log lines that prove each step and exits non-zero when one is missing.

usage: python tools/smoke.py [--shot NAME] [--ahead 1.4] [--left 0.3] [--right 0.5]
"""
import argparse
import math
import os
import re
import subprocess
import sys
import time

DEV = "emulator-5556"
AVD = "PICO_6.1_Ruler"
PKG = "io.embodit.spatialruler"
TAG = "SpatialRuler"
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))


def adb(*args):
    r = subprocess.run(["adb", "-s", DEV, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return (r.stdout or "") + (r.stderr or "")


def cmd(name, a=None, s=None):
    extra = ""
    if a:
        extra += f" --es a '{a}'"
    if s:
        extra += f" --es s '{s}'"
    adb("shell", f"am broadcast -a {PKG}.DEBUG -p {PKG} --es cmd {name}{extra}")


def device_now():
    return adb("shell", "date '+%m-%d %H:%M:%S.000'").strip()


def log_since(mark):
    return adb("logcat", "-d", "-v", "time", "-T", mark, "-s", f"{TAG}:V")


def wait_for(pattern, mark, timeout=20.0):
    end = time.time() + timeout
    rx = re.compile(pattern)
    while time.time() < end:
        hits = [ln for ln in log_since(mark).splitlines() if rx.search(ln)]
        if hits:
            return hits
        time.sleep(0.4)
    return []


def vec(text):
    return [float(v) for v in text.split(",")]


def norm(v):
    n = math.sqrt(sum(c * c for c in v))
    return [c / n for c in v]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shot", default="smoke")
    ap.add_argument("--ahead", type=float, default=1.4)
    ap.add_argument("--left", type=float, default=0.3)
    ap.add_argument("--right", type=float, default=0.5)
    ap.add_argument("--burst", type=int, default=0, help="screenshots taken right after the second trigger (pull-out motion)")
    args = ap.parse_args()

    avd = adb("emu", "avd", "name").splitlines()[0].strip()
    if avd != AVD:
        sys.exit(f"WRONG AVD on {DEV}: '{avd}' (expected {AVD})")

    mark = device_now()
    cmd("pose")
    pose = wait_for(r"POSE hmd", mark)
    if not pose:
        sys.exit("no POSE line: is the app running?")
    m = re.search(r"pos=\(([^)]*)\).*fwd=\(([^)]*)\).*right=\(([^)]*)\)", pose[-1])
    head, fwd, right = vec(m.group(1)), vec(m.group(2)), vec(m.group(3))
    print("pose   :", pose[-1].split("POSE ", 1)[1])

    mark = device_now()
    cmd("planes")
    if not wait_for(r"PLANES listed", mark):
        sys.exit("no PLANES listed line")
    floors = []
    for ln in log_since(mark).splitlines():
        fm = re.search(r"PLANE (\S+) kind=FLOOR center=\(([^)]*)\)", ln)
        if fm:
            floors.append((fm.group(1), vec(fm.group(2))))
    if not floors:
        sys.exit("no FLOOR plane detected yet")
    label, centre = floors[0]
    floor_y = centre[1]
    print(f"floor  : {label} y={floor_y:.4f}")

    flat = norm([fwd[0], 0.0, fwd[2]])
    side = norm([right[0], 0.0, right[2]])
    targets = []
    for offset in (-args.left, args.right):
        targets.append([head[0] + flat[0] * args.ahead + side[0] * offset, floor_y, head[2] + flat[2] * args.ahead + side[2] * offset])

    # The ray starts where a controller would be held: 0.25 m to the right of the head, 0.25 m below it,
    # 0.2 m ahead. From the head itself it would pass through the head-locked instrument body on the
    # way to the left point, and a press whose ray meets the body is left to the body's own panel.
    hand = [head[k] + right[k] * 0.25 - 0.25 * (1 if k == 1 else 0) + flat[k] * 0.2 for k in range(3)]
    ok = True
    for i, target in enumerate(targets):
        direction = norm([target[k] - hand[k] for k in range(3)])
        mark = device_now()
        cmd("pointer", ",".join(f"{v:.5f}" for v in hand + direction))
        hit = wait_for(r"POINTER set", mark)
        print("pointer:", hit[-1].split("POINTER ", 1)[1] if hit else "NO ANSWER")
        mark = device_now()
        cmd("trigger")
        if i == 1:
            for n in range(args.burst):
                subprocess.run(["bash", os.path.join(ROOT, "tools", "cap.sh").replace("\\", "/"), f"{args.shot}_burst{n + 1}"], capture_output=True, text=True)
        want = r"PIN add" if i == 0 else r"TAPE add"
        got = wait_for(want, mark, 25)
        for ln in log_since(mark).splitlines():
            if re.search(r"TAP |PIN add|ANCHOR |TAPE add|STATE ", ln):
                print("        ", ln.split(f"{TAG}", 1)[1].split("): ", 1)[1])
        if not got:
            print(f"MISSING: {want}")
            ok = False
        time.sleep(1.0)
    cmd("pointer")  # remove the synthetic ray and its reticle

    mark = device_now()
    cmd("dump")
    dump = wait_for(r"DUMP", mark)
    print("dump   :", dump[-1].split("DUMP", 1)[1].strip()[:1500] if dump else "NO DUMP")
    # The idle emulator renders 1-4 fps and its picture lags by seconds: ask the harness for a fast
    # period (a posted focus-in message, no guest input) so that the screenshot shows the final state.
    subprocess.run(["bash", os.path.join(ROOT, "tools", "harness", "emu.sh").replace("\\", "/"), "waitfast"], capture_output=True, text=True)
    time.sleep(2.5)
    shots = os.path.join(ROOT, ".scratch", "shots")
    r = subprocess.run(["bash", os.path.join(ROOT, "tools", "cap.sh").replace("\\", "/"), args.shot], capture_output=True, text=True)
    print((r.stdout or r.stderr).strip(), "->", os.path.join(shots, args.shot + ".png"))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
