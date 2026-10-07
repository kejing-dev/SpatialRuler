#!/usr/bin/env python
"""Real pinch at a console pixel; prints the predicted camera-model hit and everything the app logged.
usage: tapat.py X Y [--wait REGEX] [--shot NAME]"""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
args = sys.argv[1:]
shot = None; waitre = r"ANCHOR (saved|failed)|TAP (pin|other|backstop)|UI |SNACK"
if "--shot" in args:
    i = args.index("--shot"); shot = args[i + 1]; del args[i:i + 2]
if "--wait" in args:
    i = args.index("--wait"); waitre = args[i + 1]; del args[i:i + 2]
x, y = float(args[0]), float(args[1])
hp = rt.pose(); pls = rt.planes()
o, d = rt.ray(x, y, hp)
h = rt.first_hit(o, d, pls)
print("pose:", hp["line"][:120])
if h: print(f"predicted first hit: {h[2]['label']} at ({h[1][0]:.4f}, {h[1][1]:.4f}, {h[1][2]:.4f}) range {h[0]:.3f} m")
else: print("predicted: no plane on this ray")
m, out = rt.click(x, y); print(out)
hit = rt.wait(waitre, m, 12); time.sleep(0.5)
for ln in rt.logs(m): print("  ", rt.ts(ln)[6:], rt.msg(ln)[:220])
if shot:
    print(rt.shot(shot))
