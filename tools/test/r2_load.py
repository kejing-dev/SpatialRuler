#!/usr/bin/env python
"""Round 2, tests O/G/F6/F7: 12 pins + 8 tapes (fps), then up to 20 tapes and one more (limit snackbar), memory over
3 x (20 tapes + clear). Debug placements only (setup), screenshots via the harness.
usage: r2_load.py [load|limit|mem]"""
import sys, os, re, time, json, itertools, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
FL = 0.4006


def grid12():
    hp = rt.pose(); head = hp["pos"]; f = rt.norm([hp["fwd"][0], 0, hp["fwd"][2]]); s = rt.norm([hp["right"][0], 0, hp["right"][2]])
    pts = []
    for row, ahead in enumerate((1.5, 2.0, 2.5)):
        for col, off in enumerate((-0.15, 0.25, 0.65, 1.05)):
            pts.append([head[0] + f[0] * ahead + s[0] * off, FL, head[2] + f[2] * ahead + s[2] * off])
    return pts


def build(n_tapes):
    rt.cmd("clear"); time.sleep(1.5)
    pts = grid12()
    for p in pts:
        rt.cmd("place", ",".join(f"{v:.4f}" for v in p)); time.sleep(0.35)
        rt.cmd("undo") if False else None
    time.sleep(1.0)
    d = rt.dump()
    ids = [p["id"] for p in d["pins"]]
    have = {tuple(sorted((t["a"], t["b"]))) for t in d["tapes"]}
    pairs = [pr for pr in itertools.combinations(ids, 2) if tuple(sorted(pr)) not in have]
    i = 0
    while len(have) < n_tapes and i < len(pairs):
        a, b = pairs[i]; i += 1
        rt.cmd("connect", None, f"{a} {b}"); time.sleep(0.4)
        have.add(tuple(sorted((a, b))))
    time.sleep(1.5)
    return rt.dump(), pairs[i:]


def meminfo():
    out = rt.adb("shell", "dumpsys meminfo io.embodit.spatialruler")
    m = re.search(r"TOTAL PSS:\s+(\d+)", out) or re.search(r"TOTAL\s+(\d+)", out)
    j = re.search(r"Java Heap:\s+(\d+)", out); n = re.search(r"Native Heap:\s+(\d+)", out); g = re.search(r"Graphics:\s+(\d+)", out)
    return dict(total_pss_kb=int(m.group(1)) if m else None, java_kb=int(j.group(1)) if j else None, native_kb=int(n.group(1)) if n else None, graphics_kb=int(g.group(1)) if g else None)


mode = sys.argv[1] if len(sys.argv) > 1 else "load"
if mode == "load":
    d, rest = build(8)
    print(f"pins={len(d['pins'])} tapes={len(d['tapes'])}")
    m = rt.cmd("fps", None, "on")
    rt.emu("waitfast"); time.sleep(4.0)
    fl = [rt.msg(l) for l in rt.logs(m) if "FPS app=" in l]
    rt.cmd("fps", None, "off")
    print("FPS lines:", fl[-6:])
    print(rt.emu("fps"))
elif mode == "limit":
    d, rest = build(20)
    print(f"pins={len(d['pins'])} tapes={len(d['tapes'])}")
    a, b = rest[0]
    m = rt.cmd("connect", None, f"{a} {b}"); time.sleep(1.5)
    for l in rt.logs(m): print("   ", rt.msg(l)[:200])
    d = rt.dump()
    print("after 21st:", len(d["tapes"]), "tapes; snack:", d.get("snack"), d.get("snack_text"), "|", d.get("snack_desc"))
    slots = sorted((t.get("plate_slot"), t["id"]) for t in d["tapes"])
    print("plate slots:", slots)
elif mode == "mem":
    before = meminfo(); print("before:", before)
    for k in range(3):
        d, rest = build(20); print(f" round {k + 1}: pins={len(d['pins'])} tapes={len(d['tapes'])}")
        rt.cmd("clear"); time.sleep(2.0)
    time.sleep(3.0)
    after = meminfo(); print("after:", after)
