#!/usr/bin/env python
"""Round 3, T11: lazy-follow body. Head turns by right-button drags (r3t.rot), BODY lines and `rig` body centre,
then real pinches on body keys after the slide. usage: r3_t11.py"""
import sys, os, re, time, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r3t
sys.stdout.reconfigure(encoding="utf-8")
rt.check()


def body():
    m = rt.cmd("rig"); rt.wait(r"RIG summary", m, 10); time.sleep(0.3)
    line = next((rt.msg(l) for l in rt.logs(m) if "RIG body center" in l), "")
    viol = [rt.msg(l) for l in rt.logs(m) if "RIG violation" in l]
    mm = re.search(r"center=\(([^)]*)\)", line)
    return (rt.vec(mm.group(1)) if mm else None), line, viol


def view_angles(P):
    hp = rt.pose2(); d = rt.sub(P, hp["pos"]); rng = math.sqrt(rt.dot(d, d))
    x = rt.dot(d, hp["right"]); y = rt.dot(d, hp["up"]); z = rt.dot(d, hp["fwd"])
    return math.degrees(math.atan2(-x, z)), math.degrees(math.atan2(-y, z)), rng, math.degrees(math.acos(max(-1, min(1, z / rng))))


def turn(yaw, label):
    m = rt.now(); t0 = time.time()
    r3t.rot(yaw, 0.0)
    t1 = time.time()
    time.sleep(1.5)
    ls = [l for l in rt.logs(m) if re.search(r"BODY (follow|park)", l)]
    for l in ls: print("    ", rt.ts(l), rt.msg(l)[:200])
    c, line, viol = body()
    a = view_angles(c)
    print(f"  [{label}] body centre {['%.3f' % v for v in c]} -> left {a[0]:.1f} deg, down {a[1]:.1f} deg, range {a[2]:.3f} m, off-axis {a[3]:.1f} deg; violations {len(viol)}; host turn took {t1 - t0:.1f} s")
    print("    ", line[:220])
    return c, ls


c0, line, viol = body(); a = view_angles(c0)
print(f"start: body centre {['%.3f' % v for v in c0]} left {a[0]:.1f} down {a[1]:.1f} range {a[2]:.3f}; {line[:160]}")
c20, l20 = turn(-20.0, "turned 20 deg right")
print("  moved by", round(1000 * rt.dist(c0, c20), 1), "mm (expect 0: stays in the room)")
c90, l90 = turn(-90.0, "turned 70 deg more")
fol = [l for l in l90 if "BODY follow" in l]; par = [l for l in l90 if "BODY park reason=slide" in l]
if fol and par:
    print("  follow -> park:", round(rt.tsec(rt.ts(par[-1])) - rt.tsec(rt.ts(fol[0])), 3), "s")
# the body must not overlap a surface: distance of its centre to every plane quad near it
pls = rt.planes()
near = []
for p in pls:
    n = rt.plane_normal(p); dd = rt.dot(rt.sub(c90, p["verts"][0]), n)
    o, dirn = c90, [-v for v in n]
    h = rt.ray_plane(c90, [-v for v in n] if dd > 0 else n, p)
    if h and h[0] < 0.25: near.append((p["label"], round(h[0], 3)))
print("  planes within 0.25 m of the body centre:", near or "none")
r3t.step("pc ctl:body:unit_mm ctl:body:snap ctl:body:snap ctl:body:unit_cm gap=800 exp=UI.unit_cm to=12".split())
r3t.shot("r3_10_body_after_slide", (0, 900, 2160, 2158), 0.5)
