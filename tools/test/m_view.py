#!/usr/bin/env python
"""M. Viewpoint analysis at the current camera pose: every visible panel's distance, scale vs the constant-angular rule
(scale = clamp(distance, 0.45, 3) / 0.8), facing error, uprightness, on-screen size, digit height measured in the screenshot;
where the body sits in the view. usage: m_view.py NAME"""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import numpy as np
from PIL import Image
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
name = sys.argv[1]
hp = rt.pose2(); b = rt.bounds(); d = rt.dump()
path = rt.shot(name)
hp2 = rt.pose2()
if hp2["line"] != hp["line"]: print("WARNING pose changed during capture")
print(hp["line"]); print(f"yaw {math.degrees(hp['yaw']):.2f} pitch {math.degrees(hp['pitch']):.2f}")
im = np.asarray(Image.open(path).convert("RGB")).astype(int)
res = dict(name=name, pose=hp["line"], panels=[])
for pid, p in b.items():
    if p["popup"]: continue
    c = p["pos"]; dist = rt.dist(c, hp["pos"])
    n = rt.qrot(p["q"], [0, 0, 1]); up = rt.qrot(p["q"], [0, 1, 0])
    to_head = rt.norm(rt.sub(hp["pos"], c))
    face = math.degrees(math.acos(max(-1, min(1, rt.dot(n, to_head)))))
    nh = [n[0], 0, n[2]]; th = [to_head[0], 0, to_head[2]]
    yaw_err = math.degrees(math.acos(max(-1, min(1, rt.dot(rt.norm(nh), rt.norm(th)))))) if rt.dot(nh, nh) > 1e-9 else float("nan")
    tilt = math.degrees(math.asin(max(-1, min(1, n[1]))))          # 0 = vertical panel
    roll = math.degrees(math.asin(max(-1, min(1, rt.qrot(p["q"], [1, 0, 0])[1]))))  # 0 = horizontal top edge
    exp = min(3.0, max(0.45, dist)) / 0.8
    corners = [rt.project(rt.panel_point(p, x, y), hp) for (x, y) in ((0, 0), (p["w"], 0), (p["w"], p["h"]), (0, p["h"]))]
    row = dict(id=pid, dist=dist, scale=p["scale"], exp_scale=exp, face_err=face, yaw_err=yaw_err, tilt=tilt, roll=roll, ctrls=list(p["ctrls"]))
    if all(corners):
        xs = [q[0] for q in corners]; ys = [q[1] for q in corners]
        row.update(px_w=max(xs) - min(xs), px_h=max(ys) - min(ys), cx=sum(xs) / 4, cy=sum(ys) / 4)
        x0, x1, y0, y1 = int(max(0, min(xs))), int(min(2159, max(xs))), int(max(0, min(ys))), int(min(2157, max(ys)))
        if pid.startswith("plate") and x1 - x0 > 8 and y1 - y0 > 4:
            sub = im[y0:y1, x0:x1]
            bright = (sub.min(axis=2) > 150)
            rows = bright.sum(axis=1)
            band = []; cur = None
            for i, v in enumerate(rows):
                if v > 0 and cur is None: cur = i
                if v == 0 and cur is not None: band.append((cur, i - 1)); cur = None
            if cur is not None: band.append((cur, len(rows) - 1))
            band = [bb for bb in band if bb[1] - bb[0] >= 2]
            if band:
                row["digit_px"] = band[0][1] - band[0][0] + 1
                row["tol_px"] = (band[1][1] - band[1][0] + 1) if len(band) > 1 else None
    res["panels"].append(row)
    extra = f" on-screen {row.get('px_w', 0):.0f}x{row.get('px_h', 0):.0f} px at ({row.get('cx', 0):.0f},{row.get('cy', 0):.0f})" if "px_w" in row else " (off screen)"
    dg = f" digits {row['digit_px']} px tol {row.get('tol_px')} px" if "digit_px" in row else ""
    print(f"{pid:10s} dist {dist:5.2f} m scale {p['scale']:.3f} (rule {exp:.3f}) facing err {face:5.1f} deg (yaw {yaw_err:4.1f}) tilt {tilt:5.1f} roll {roll:4.1f}{extra}{dg} {','.join(p['ctrls'])[:40]}")
bd = b.get("body")
if bd:
    dd = rt.sub(bd["pos"], hp["pos"]); depth = rt.dot(dd, hp["fwd"])
    print(f"body in view: left {math.degrees(math.atan2(-rt.dot(dd, hp['right']), depth)):.1f} deg, down {math.degrees(math.atan2(-rt.dot(dd, hp['up']), depth)):.1f} deg, distance {rt.dist(bd['pos'], hp['pos']):.3f} m")
os.makedirs(os.path.join(rt.ROOT, "tools", "test", "out"), exist_ok=True)
json.dump(res, open(os.path.join(rt.ROOT, "tools", "test", "out", f"m_{name}.json"), "w", encoding="utf-8"), indent=1)
