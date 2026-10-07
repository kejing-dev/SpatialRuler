#!/usr/bin/env python
"""Calibrate framebuffer-pixel -> emulator-window-pixel mapping and prove it with clicks.

Needs an app that implements the calibration contract (the spike does):
  debug cmd  `calib` s=on  a="dist,scale,sizeDp"   -> head-locked opaque square, 9 magenta (255,0,255) fiducials
                                                       centred at 10/50/90 % of its size; logs `CALIB panel attached`
  debug cmd  `calib` s=off | s=clear
  log line   `CALIB ev=Press x=<panel px> y=<panel px> ... size=<W>x<H>` for every pointer event on the panel

Steps
  1. show the panel, take a console screenshot (framebuffer pixels) and a PrintWindow capture (window pixels)
  2. find the 9 fiducials in both, fit  window = a * fb + b  per axis, write calib.json
  3. click N points spread over the panel through the new mapping, read where each press landed (panel px),
     convert to framebuffer px with the fiducials, report intended vs. landed

usage: calibrate.py [--dist 0.9] [--scale 3.3] [--size 480] [--clicks 7] [--no-fit] [--keep] [--tag NAME]
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
EMU = os.path.join(HERE, "emu.sh").replace("\\", "/")
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
SHOTS = os.environ.get("SHOTS", os.path.join(ROOT, ".scratch", "shots", "harness"))
CALIB = os.path.join(HERE, "calib.json")
FRAC = (0.1, 0.5, 0.9)


def emu(*args, check=True):
    r = subprocess.run(["bash", EMU, *[str(a) for a in args]], capture_output=True, text=True)
    out = (r.stdout or "") + (r.stderr or "")
    if check and r.returncode != 0:
        raise SystemExit(f"emu.sh {' '.join(map(str, args))} failed ({r.returncode}): {out.strip()}")
    return out.strip()


def blobs(path):
    """Centres of solid magenta squares: list of (cx, cy, w, h)."""
    im = np.asarray(Image.open(path).convert("RGB")).astype(np.int16)
    mask = (im[..., 0] > 200) & (im[..., 2] > 200) & (im[..., 1] < 90)
    ys, xs = np.nonzero(mask)
    todo = set(zip(ys.tolist(), xs.tolist()))
    res = []
    while todo:
        seed = todo.pop()
        stack = [seed]
        py, px = [seed[0]], [seed[1]]
        while stack:
            y, x = stack.pop()
            for n in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if n in todo:
                    todo.remove(n)
                    stack.append(n)
                    py.append(n[0])
                    px.append(n[1])
        w = max(px) - min(px) + 1
        h = max(py) - min(py) + 1
        if len(px) < 150:
            continue
        if not (0.85 < w / h < 1.18) or len(px) < 0.9 * w * h:
            print(f"  skip non-square magenta blob at ({np.mean(px):.0f},{np.mean(py):.0f}) {w}x{h}")
            continue
        res.append(((min(px) + max(px) + 1) / 2.0, (min(py) + max(py) + 1) / 2.0, w, h))
    return res


def grid(bl):
    if len(bl) != 9:
        raise SystemExit(f"expected 9 fiducials, found {len(bl)}: {bl}")
    bl = sorted(bl, key=lambda b: b[1])
    rows = [sorted(bl[i:i + 3], key=lambda b: b[0]) for i in (0, 3, 6)]
    return np.array([[(b[0], b[1]) for b in row] for row in rows])  # [row][col] -> (x, y)


def fit(u, v):
    a, b = np.polyfit(np.asarray(u, float), np.asarray(v, float), 1)
    res = np.asarray(v, float) - (a * np.asarray(u, float) + b)
    return float(a), float(b), float(np.max(np.abs(res)))


def geometry():
    info = emu("info")
    m = re.search(r"geom main_client=(\d+)x(\d+) sub_client=(\d+)x(\d+) sub_offset=\((-?\d+),(-?\d+)\)", info)
    if not m:
        raise SystemExit("cannot parse emu.sh info:\n" + info)
    return [int(x) for x in m.groups()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dist", type=float, default=0.9)
    ap.add_argument("--scale", type=float, default=3.3)
    ap.add_argument("--size", type=int, default=480)
    ap.add_argument("--clicks", type=int, default=7)
    ap.add_argument("--no-fit", action="store_true", help="keep the existing calib.json, only verify with clicks")
    ap.add_argument("--keep", action="store_true", help="leave the panel on")
    ap.add_argument("--tag", default="calib")
    ap.add_argument("--hold", type=int, default=150)
    ap.add_argument("--settle", type=int, default=400)
    ap.add_argument("--slow", action="store_true", help="click at 2-4 fps with a 2 s press (-NoFast)")
    args = ap.parse_args()

    # always start from a fresh panel: resizing a live AttachmentPanel left stale rows in its surface
    emu("cmd", "calib", "", "off")
    emu("waitlog", "CALIB off", 15)
    time.sleep(1.0)
    emu("cmd", "calib", f"{args.dist},{args.scale},{args.size}", "on")
    print(emu("waitlog", "CALIB panel attached", 30).splitlines()[-1][-110:])
    # a new panel can take many seconds to show up at 2-3 fps: poll the screenshot instead of sleeping
    for attempt in range(25):
        emu("shot", f"{args.tag}_console")
        found = blobs(os.path.join(SHOTS, f"{args.tag}_console.png"))
        if len(found) == 9:
            break
        print(f"  waiting for the panel to render ({len(found)} fiducials visible)")
        time.sleep(1.0)
    time.sleep(1.0)
    emu("shot", f"{args.tag}_console")
    fb = grid(blobs(os.path.join(SHOTS, f"{args.tag}_console.png")))
    # the host window is repainted later than the guest framebuffer: poll it as well
    for attempt in range(25):
        emu("winshot", f"{args.tag}_window")
        found = blobs(os.path.join(SHOTS, f"{args.tag}_window.png"))
        if len(found) == 9:
            break
        print(f"  waiting for the host window to repaint ({len(found)} fiducials visible)")
        time.sleep(1.0)
    win = grid(found)

    ax, bx, ex = fit(fb[..., 0].ravel(), win[..., 0].ravel())
    ay, by, ey = fit(fb[..., 1].ravel(), win[..., 1].ravel())
    print(f"fiducials fb (console screenshot px):\n{np.round(fb, 1).tolist()}")
    print(f"fiducials window (main-window client px):\n{np.round(win, 1).tolist()}")
    print(f"window_x = {ax:.5f} * fb_x + {bx:.2f}   (max residual {ex:.2f} window px)")
    print(f"window_y = {ay:.5f} * fb_y + {by:.2f}   (max residual {ey:.2f} window px)")

    # panel px -> fb px (panel is head-locked and fronto-parallel: per-axis linear)
    S = args.size * 2.0  # density 2.0
    pu = np.array([[f * S for f in FRAC]] * 3).ravel()
    pv = np.array([[f * S] * 3 for f in FRAC]).ravel()
    px_, qx, epx = fit(pu, fb[..., 0].ravel())
    py_, qy, epy = fit(pv, fb[..., 1].ravel())
    print(f"panel->fb: fb_x = {px_:.5f} * u + {qx:.2f} (res {epx:.2f}), fb_y = {py_:.5f} * v + {qy:.2f} (res {epy:.2f})")
    f_px = px_ / args.scale * args.dist / 0.0004  # 1 panel px = 0.4 mm at scale 1
    print(f"pinhole estimate: focal = {f_px:.1f} fb px, head-forward axis at fb ({px_ * S / 2 + qx:.1f}, {py_ * S / 2 + qy:.1f}) for depth {args.dist} m")

    g = geometry()
    cal = {
        "ax": round(ax, 6), "bx": round(bx, 3), "ay": round(ay, 6), "by": round(by, 3),
        "main_w": g[0], "main_h": g[1], "sub_w": g[2], "sub_h": g[3], "sub_off_x": g[4], "sub_off_y": g[5],
        "fit_residual_window_px": [round(ex, 2), round(ey, 2)],
        "visible_fb": [round((g[4] + g[2] - bx) / ax, 1), round((g[5] + g[3] - by) / ay, 1)],
        "focal_fb_px": round(f_px, 1),
        "panel": {"dist": args.dist, "scale": args.scale, "sizeDp": args.size},
        "when": time.strftime("%Y-%m-%d %H:%M:%S"),
    }
    if args.no_fit and os.path.exists(CALIB):
        old = json.load(open(CALIB))
        print(f"--no-fit: keeping calib.json (ax={old['ax']} bx={old['bx']} ay={old['ay']} by={old['by']})")
        cal = old
    else:
        json.dump(cal, open(CALIB, "w"), indent=2)
        print(f"wrote {CALIB}")

    pts = [(0.07, 0.07), (0.93, 0.08), (0.30, 0.34), (0.70, 0.62), (0.06, 0.93), (0.94, 0.94), (0.50, 0.22), (0.22, 0.60), (0.80, 0.30)][: args.clicks]
    rows = []
    for fx, fy in pts:
        ix, iy = px_ * fx * S + qx, py_ * fy * S + qy
        if ix > cal["visible_fb"][0] - 4 or iy > cal["visible_fb"][1] - 4:
            print(f"  skip ({ix:.0f},{iy:.0f}): outside the part of the framebuffer the window shows")
            continue
        if args.slow:
            emu("click", f"{ix:.1f}", f"{iy:.1f}", "-NoFast", "-HoldMs", 2000, "-SettleMs", 700)
        else:
            emu("click", f"{ix:.1f}", f"{iy:.1f}", "-HoldMs", args.hold, "-SettleMs", args.settle)
        out = emu("waitlog", "CALIB ev=Press", 12, check=False)
        m = re.search(r"CALIB ev=Press x=([-\d.]+) y=([-\d.]+)", out.splitlines()[-1] if out else "")
        if not m:
            print(f"  ({ix:.1f},{iy:.1f}) -> NO PRESS LOGGED ({out[-120:]})")
            rows.append({"intended": [round(ix, 1), round(iy, 1)], "landed": None})
            continue
        lx, ly = px_ * float(m.group(1)) + qx, py_ * float(m.group(2)) + qy
        rows.append({"intended": [round(ix, 1), round(iy, 1)], "landed": [round(lx, 1), round(ly, 1)], "err": [round(lx - ix, 1), round(ly - iy, 1)]})
        print(f"  intended fb ({ix:7.1f},{iy:7.1f})  landed fb ({lx:7.1f},{ly:7.1f})  err ({lx - ix:+5.1f},{ly - iy:+5.1f})")
    ok = [r for r in rows if r["landed"]]
    if ok:
        e = np.array([r["err"] for r in ok])
        print(f"clicks {len(ok)}/{len(rows)}: mean err ({e[:, 0].mean():+.1f},{e[:, 1].mean():+.1f}) fb px, max |err| ({np.abs(e[:, 0]).max():.1f},{np.abs(e[:, 1]).max():.1f}) fb px")
    time.sleep(1.5)
    emu("shot", f"{args.tag}_clicks")
    if not args.no_fit:
        cal["verify"] = rows
        json.dump(cal, open(CALIB, "w"), indent=2)
    else:
        json.dump({"panel": {"dist": args.dist, "scale": args.scale, "sizeDp": args.size}, "verify": rows}, open(os.path.join(HERE, f"verify_{args.tag}.json"), "w"), indent=2)
    if not args.keep:
        emu("cmd", "calib", "", "off")


if __name__ == "__main__":
    main()
