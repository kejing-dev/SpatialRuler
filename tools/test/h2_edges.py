#!/usr/bin/env python
"""H edge cases: exact lengths set up with the debug `place` command (setup only), readings checked in all four units."""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
def exp_len(L, unit):
    if unit == "m": return f"{L:.3f} m"
    if unit == "cm": return f"{L*100:.1f} cm"
    if unit == "mm": return f"{round(L*1000)} mm"
    inches = round(L / 0.0254, 1)
    if inches < 12.0: return f"{inches:.1f}″"
    ft = int(inches // 12); rest = round(inches - 12 * ft, 1)
    return f"{ft}′ {rest:.1f}″"
def exp_tol(tol_mm, unit):
    if unit == "ftin": return f"±{math.ceil(tol_mm / 25.4 * 10 - 1e-9) / 10:.1f}″"
    return f"±{tol_mm:g} mm" if tol_mm < 10 else f"±{tol_mm/10:g} cm"
m = rt.cmd("clear"); time.sleep(1.0)
L = [0.3030, 0.3040, 0.3050, 0.6095, 0.9994, 0.9996, 0.0500, 0.12345]
x0 = 0.15
for i, l in enumerate(L):
    z = -1.25 - 0.14 * i
    for x in (x0, x0 + l):
        m = rt.cmd("place", a=f"{x:.5f},0.4006,{z:.5f}"); time.sleep(0.45)
time.sleep(1.0)
d = rt.dump(); print(d["state"], len(d["pins"]), "pins", len(d["tapes"]), "tapes")
if len(d["tapes"]) != len(L): print("unexpected tape count; pins:", [(p["id"], p["pos"], p["snapped"]) for p in d["pins"]])
# make the last-but-one tape fine/fine: nudge both ends +1 then -1 (position unchanged, grade fine)
t = d["tapes"][2]
for pid in (t["a"], t["b"]):
    for c in ("tap_pin",):
        rt.cmd("tap_pin", s=pid); time.sleep(0.5)
    for n in ("pin_tune", "step_u_plus", "step_u_minus", "pin_close"):
        rt.cmd("ui", s=n); time.sleep(0.5)
bad = 0
for u in ("m", "cm", "mm", "ftin"):
    rt.cmd("unit", s=u); time.sleep(0.8)
    d = rt.dump(); P = {p["id"]: p for p in d["pins"]}
    for t in d["tapes"]:
        geo = rt.dist(P[t["a"]]["pos"], P[t["b"]]["pos"])
        el, et = exp_len(geo, u), exp_tol(t["tol_mm"], u)
        ok = t["text"] == el and t["tol_text"] == et
        bad += 0 if ok else 1
        print(f"  {u:4s} {t['id']:4s} geom {geo:.5f}  app '{t['text']}' '{t['tol_text']}'  expected '{el}' '{et}'  grades {P[t['a']]['grade']}/{P[t['b']]['grade']} rss {math.hypot(P[t['a']]['sigma_mm'], P[t['b']]['sigma_mm']):.1f}  {'ok' if ok else 'MISMATCH'}")
    if u in ("m", "ftin"): print("  ", rt.shot(f"h2_edges_{u}"))
print("mismatches:", bad)
