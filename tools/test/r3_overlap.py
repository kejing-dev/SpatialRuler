#!/usr/bin/env python
"""Round 3, F9: view-space rectangles of every chip / plate / tag panel from `bounds` + `pose` (left-eye camera model),
their range from the head, and every overlapping pair (which one is nearer = drawn in front)."""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
b = rt.bounds(); hp = rt.pose()
rects = {}
for pid, p in b.items():
    if p["popup"] or not (pid.startswith(("chip", "plate", "pinTag", "tapeTag"))): continue
    pts = [rt.project(rt.panel_point(p, cx, cy), hp) for cx, cy in ((0, 0), (p["w"], 0), (0, p["h"]), (p["w"], p["h"]))]
    if any(q is None for q in pts): continue
    xs = [q[0] for q in pts]; ys = [q[1] for q in pts]
    rng = rt.dist(p["pos"], hp["pos"])
    rects[pid] = (min(xs), min(ys), max(xs), max(ys), rng, list(p["ctrls"]))
    print(f"{pid:9s} {','.join(p['ctrls']):14s} px x[{min(xs):.0f},{max(xs):.0f}] y[{min(ys):.0f},{max(ys):.0f}] range {rng:.3f} m scale {p['scale']:.3f}")
res = []
for a, ra in rects.items():
    for c, rc in rects.items():
        if a >= c: continue
        ix = min(ra[2], rc[2]) - max(ra[0], rc[0]); iy = min(ra[3], rc[3]) - max(ra[1], rc[1])
        if ix > 0 and iy > 0:
            front = a if ra[4] < rc[4] else c
            area_a = (ra[2] - ra[0]) * (ra[3] - ra[1]); area_c = (rc[2] - rc[0]) * (rc[3] - rc[1])
            print(f"OVERLAP {a} ({ra[4]:.3f} m) x {c} ({rc[4]:.3f} m): {ix:.0f}x{iy:.0f} px = {100*ix*iy/area_a:.0f}% of {a}, {100*ix*iy/area_c:.0f}% of {c}; in front: {front}")
            res.append(dict(a=a, b=c, ra=ra[4], rb=rc[4], front=front, ctrls_a=ra[5], ctrls_b=rc[5]))
json.dump(dict(rects=rects, overlaps=res, pose=hp["line"]), open(os.path.join(rt.ROOT, "tools", "test", "out", "round3", "overlap_last.json"), "w"), indent=1)
