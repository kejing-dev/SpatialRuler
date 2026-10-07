#!/usr/bin/env python
"""Round 2: real pinches at console pixels; for each, the camera-model prediction (first plane on the eye ray) vs. the
surface point the system reported (TAP stage=) and the pin. Each new pin is removed again with a debug undo unless --keep.
usage: r2_tapmap.py X1 Y1 [X2 Y2 ...] [--keep] [--out NAME]"""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
args = sys.argv[1:]
keep = "--keep" in args
args = [a for a in args if a != "--keep"]
out = None
if "--out" in args:
    i = args.index("--out"); out = args[i + 1]; del args[i:i + 2]
pts = [(float(args[i]), float(args[i + 1])) for i in range(0, len(args) - 1, 2)]
pls = rt.planes()
res = []
for (x, y) in pts:
    hp = rt.pose()
    o, d = rt.ray(x, y, hp)
    fh = rt.first_hit(o, d, pls)
    m, o2 = rt.click(x, y)
    if "ERROR" in o2: print("HARNESS", o2[-150:])
    rt.wait(r"ANCHOR (saved|failed)|TAP (pin|other|backstop|blocked|ignored)|UI |PTR panel", m, 10); time.sleep(0.5)
    lines = [rt.msg(l) for l in rt.logs(m)]
    tap = next((l for l in lines if l.startswith("TAP ")), "")
    pin = next((l for l in lines if l.startswith("PIN add")), "")
    tm = re.search(r"stage=\(([^)]*)\)", tap)
    rec = dict(px=[x, y], pred=fh[2]["label"] if fh else None, pred_pt=fh[1] if fh else None, tap=tap, pin=pin)
    if tm and fh:
        tp = rt.vec(tm.group(1)); rec["err_m"] = rt.dist(tp, fh[1])
        pr = rt.project(tp, hp); rec["landed_px"] = [pr[0], pr[1]] if pr else None
    print(f"px ({x:.0f},{y:.0f}) pred {rec['pred']} {[round(c, 3) for c in fh[1]] if fh else ''} | {tap[:150]}")
    if "err_m" in rec:
        print(f"      err {rec['err_m'] * 1000:.0f} mm, landed at px ({rec['landed_px'][0]:.0f},{rec['landed_px'][1]:.0f})")
    for l in lines:
        if l.startswith(("PIN add", "TAPE add", "SNACK", "TAP blocked", "AIM")): print("     ", l[:200])
    res.append(rec)
    if pin and not keep:
        rt.cmd("undo"); time.sleep(0.6)
if out:
    json.dump(res, open(os.path.join(rt.ROOT, "tools", "test", "out", "round2", out + ".json"), "w"), indent=1)
