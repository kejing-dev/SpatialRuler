#!/usr/bin/env python
"""Round 3: M1 reticle shots and R2-1 (no tracked source -> snackbar) with the synthetic pointer standing in for a
controller (`source controller`). Debug broadcasts only; screenshots to .scratch/shots/round3test."""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r3t
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
HAND = (0.25, 1.20, -0.20)
FL = 0.4006


def c(name, a=None, s=None, pat=None, to=6):
    m = rt.cmd(name, a, s)
    if pat: rt.wait(pat, m, to)
    time.sleep(0.4)
    ls = [rt.msg(l) for l in rt.logs(m)]
    for l in ls:
        if re.match(r"(SRC|RETICLE|AIM|TAP|PIN add|TAPE|POINTER|SOURCE|SNACK|STATE|FEEDBACK|CMD)", l): print("    ", l[:230])
    return m, ls


def point(P):
    d = [P[i] - HAND[i] for i in range(3)]
    return c("pointer", "%.4f,%.4f,%.4f,%.5f,%.5f,%.5f" % (HAND + tuple(d)), pat="POINTER")


def crop_at(name, P, half=230, k=1.6):
    hp = rt.pose(); x, y, _ = rt.project(P, hp)
    return r3t.shot(name, (int(x - half), int(y - half), int(x + half), int(y + half)), k, settle=0.6)


pls = rt.planes(); hp = rt.pose()
# a pointing direction that meets no plane and is in the frame (through the open part of the room)
none_dir = None
for tx, ty, tz in [(2.2, 2.0, -3.0), (2.5, 1.6, -2.4), (1.9, 2.6, -3.5), (-1.0, 3.3, -3.0), (2.0, 1.2, -1.6)]:
    o = HAND; d = rt.norm([tx - o[0], ty - o[1], tz - o[2]])
    if rt.first_hit(o, d, pls) is None and rt.project([tx, ty, tz], hp):
        none_dir = (tx, ty, tz); break
print("no-surface target:", none_dir)

print("== source controller, aim close")
c("source", None, "controller", pat="SRC")
close = (0.55, FL, -1.75)
point(close); time.sleep(1.2)
m, _ = c("dump")
crop_at("r3_07_reticle_close", close)
print("== trigger: commit a pin via the stand-in controller")
c("trigger", pat="PIN add|TAP pointer|AIM commit", to=8)
time.sleep(0.8)
print("== aim far (live tape from the armed pin)")
far = (0.25, FL, -4.3)
point(far); time.sleep(1.2)
crop_at("r3_08_reticle_far_live_tape", far, half=330, k=1.2)
if none_dir:
    print("== aim at nothing")
    point(none_dir); time.sleep(1.2)
    crop_at("r3_08b_reticle_none", none_dir, half=230, k=1.2)
print("== R2-1: hand_lost")
m0, _ = c("hand_lost")
hits = rt.wait(r"SNACK (no_source|source|raise)|SOURCE lost", m0, 6.0)
time.sleep(1.0)
ls = rt.logs(m0)
for l in ls:
    t = rt.msg(l)
    if re.match(r"(SOURCE|SNACK|POINTER|SRC|STATE)", t): print("    ", rt.ts(l), t[:200])
d = rt.dump(); print("   dump snack:", d.get("snack"), "|", d.get("snack_text"), "|", d.get("snack_remaining_ms"))
r3t.shot("r3_x_no_source_snack", (500, 900, 1700, 1900), 0.7, settle=0.6)
print("== source back")
m1, _ = point(far)
rt.wait(r"SOURCE back", m1, 6.0); time.sleep(1.0)
for l in rt.logs(m1):
    t = rt.msg(l)
    if re.match(r"(SOURCE|SNACK)", t): print("    ", rt.ts(l), t[:200])
d = rt.dump(); print("   dump snack after back:", d.get("snack"), "|", d.get("snack_text"))
print("== cleanup: release, source auto, pointer off")
c("source", None, "auto", pat="SRC")
m2, _ = c("pointer", pat="POINTER")
time.sleep(4.0)
for l in rt.logs(m2):
    t = rt.msg(l)
    if re.match(r"(SOURCE|SNACK|SRC)", t): print("    ", rt.ts(l), t[:200])
d = rt.dump(); print("   final:", rt.brief(d)); print("   snack:", d.get("snack"), d.get("snack_text"))
