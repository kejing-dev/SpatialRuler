#!/usr/bin/env python
"""E. Stepper presses with real pinches; dump before/after each press."""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
pid = sys.argv[1]; seq = sys.argv[2:]
def snap():
    d = rt.dump(); p = rt.pin_by_id(d, pid)
    tapes = {t["id"]: (t["len_m"], t["text"], t["tol_text"]) for t in d["tapes"] if pid in (t["a"], t["b"])}
    geo = {t["id"]: rt.dist(rt.pin_by_id(d, t["a"])["pos"], rt.pin_by_id(d, t["b"])["pos"]) for t in d["tapes"] if pid in (t["a"], t["b"])}
    return d, p, tapes, geo
d0, p0, t0, g0 = snap()
print("before:", p0["pos"], "offset", p0["offset_mm"], "grade", p0["grade"], "tol", p0["tol_mm"], t0, "hist", d0["history"], d0["state"])
hp = rt.pose2()
view = rt.norm([p0["pos"][0] - hp["pos"][0], 0.0, p0["pos"][2] - hp["pos"][2]])
right = rt.cross(view, [0, 1, 0])
print("viewer->pin (floor) dir", [round(c, 3) for c in view], "viewer right", [round(c, 3) for c in right])
prev = p0
for name in seq:
    b = rt.bounds(); hp = rt.pose2()
    panel = next((k for k, v in b.items() if name in v["ctrls"]), None)
    if not panel: print("control not visible:", name, list(b)); break
    x, y = rt.ctrl_px(b, hp, panel, name)
    m, out = rt.click(x, y); rt.wait(r"PIN nudge|UI |TAP ", m, 8); time.sleep(0.6)
    lines = [rt.msg(l) for l in rt.logs(m) if not re.search(r"PANEL |CTRL |BOUNDS|POSE|DUMP|CMD recv", l)]
    d, p, t, g = snap()
    dv = rt.sub(p["pos"], prev["pos"])
    print(f"{name}: {lines}")
    print(f"   pos {p['pos']} offset {p['offset_mm']} grade {p['grade']} tol {p['tol_mm']} | step = ({1000*dv[0]:+.2f},{1000*dv[1]:+.2f},{1000*dv[2]:+.2f}) mm |step|={1000*math.sqrt(rt.dot(dv,dv)):.2f} along-right {1000*rt.dot(dv,right):+.2f} along-far {1000*rt.dot(dv,view):+.2f}")
    for k in t: print(f"   {k}: len {t[k][0]:.4f} geom {g[k]:.4f} text '{t[k][1]}' '{t[k][2]}'")
    prev = p
print("history", d["history"], "state", d["state"])
