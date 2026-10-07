#!/usr/bin/env python
"""H. Units: real pinch on each unit segment, then compare every reading / tolerance text with the design's format table."""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
tag = sys.argv[1] if len(sys.argv) > 1 else "h"
units = sys.argv[2].split(",") if len(sys.argv) > 2 else ["m", "cm", "ftin", "mm"]
real = "--cmd" not in sys.argv
LADDER = [2, 3, 5, 8, 10, 15, 20, 30, 50]

def exp_len(L, unit):
    if unit == "m": return f"{L:.3f} m"
    if unit == "cm": return f"{L*100:.1f} cm"
    if unit == "mm": return f"{round(L*1000)} mm"
    inches = round(L / 0.0254, 1)
    if inches < 12.0: return f"{inches:.1f}″"
    ft = int(inches // 12); rest = round(inches - 12 * ft, 1)
    if rest >= 12.0: ft += 1; rest = 0.0
    return f"{ft}′ {rest:.1f}″"

def exp_tol(tol_mm, unit):
    if unit == "ftin":
        return f"±{math.ceil(tol_mm / 25.4 * 10 - 1e-9) / 10:.1f}″"
    if tol_mm < 10: return f"±{tol_mm:g} mm"
    return f"±{tol_mm/10:g} cm"

rows = []
for u in units:
    if real:
        b = rt.bounds(); hp = rt.pose2(); x, y = rt.ctrl_px(b, hp, "body", "unit_" + u)
        m, out = rt.click(x, y); hit = rt.wait(r"UNIT ", m, 8); time.sleep(0.5)
        print(f"unit_{u} px ({x:.0f},{y:.0f}):", [rt.msg(l) for l in rt.logs(m) if re.search(r"PTR|UI |UNIT|TAP", l)])
    else:
        m = rt.cmd("unit", s=u); rt.wait(r"UNIT ", m, 8); time.sleep(0.4)
    d = rt.dump(); P = {p["id"]: p for p in d["pins"]}
    print(f"  dump unit={d['unit']}")
    for t in d["tapes"]:
        geo = rt.dist(P[t["a"]]["pos"], P[t["b"]]["pos"])
        rss = math.hypot(P[t["a"]]["sigma_mm"], P[t["b"]]["sigma_mm"])
        lad = next((v for v in LADDER if v >= rss - 1e-6), None)
        el, et = exp_len(geo, u), exp_tol(t["tol_mm"], u)
        ok = (t["text"].replace(" ", " ") == el) and (t["tol_text"].replace(" ", " ") == et)
        print(f"   {t['id']:4s} geom {geo:.5f} m  app '{t['text']}' '{t['tol_text']}'  expected '{el}' '{et}'  rss {rss:.1f} -> ladder {lad} app tol {t['tol_mm']}  {'ok' if ok else 'MISMATCH'}")
        rows.append(dict(unit=u, id=t["id"], geom=geo, text=t["text"], tol_text=t["tol_text"], exp=el, exp_tol=et, rss=rss, tol_mm=t["tol_mm"], ok=ok))
    for p in d["pins"]:
        pass
    name = f"{tag}_unit_{u}"; rt.shot(name); print("  shot", name)
json.dump(rows, open(os.path.join(rt.ROOT, "tools", "test", "out", f"{tag}_units.json"), "w", encoding="utf-8"), indent=1, ensure_ascii=False)
