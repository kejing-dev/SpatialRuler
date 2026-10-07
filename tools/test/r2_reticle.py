#!/usr/bin/env python
"""Round 2: aim the controller ray (controller mode: the ray follows posted mouse moves) at Stage points or pixels,
read the RETICLE line and take a screenshot of each state.
usage: r2_reticle.py NAME:x,y,z | NAME:@px,py  [...]  [--noshot]"""
import sys, os, re, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
args = [a for a in sys.argv[1:] if not a.startswith("--")]
noshot = "--noshot" in sys.argv
for spec in args:
    name, where = spec.split(":", 1)
    hp = rt.pose()
    if where.startswith("@"):
        x, y = (float(v) for v in where[1:].split(","))
    else:
        P = [float(v) for v in where.split(",")]
        pr = rt.project(P, hp); x, y = pr[0], pr[1]
    rt.emu("waitfast")
    m = rt.now()
    rt.emu("move", int(round(x)), int(round(y)))
    time.sleep(1.0)
    rt.emu("move", int(round(x)) + 1, int(round(y)))
    time.sleep(0.9)
    ret = [rt.msg(l) for l in rt.logs(m) if "RETICLE" in l]
    print(f"{name} px ({x:.0f},{y:.0f}):", (ret[-1] if ret else "no RETICLE line")[:200])
    if not noshot:
        out = rt.emu("shot", "r2_ret_" + name)
        print("   ", out[-80:])
