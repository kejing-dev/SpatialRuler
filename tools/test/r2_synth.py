#!/usr/bin/env python
"""Round 2, test E: synthetic tracked channels through the debug receiver (no hands on the emulator)."""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
FL = 0.4006


def run(name, a=None, s=None, wait=1.0, pat=None):
    m = rt.cmd(name, a, s)
    if pat: rt.wait(pat, m, 6)
    time.sleep(wait)
    out = [rt.msg(l) for l in rt.logs(m)]
    for l in out:
        if not re.match(r"(RIG slot|CMD recv|PANEL |CTRL |BOUNDS|POSE )", l): print("    ", l[:220])
    return out


hp = rt.pose()
head = hp["pos"]; fwd = hp["fwd"]; right = hp["right"]
flat = rt.norm([fwd[0], 0, fwd[2]]); side = rt.norm([right[0], 0, right[2]])
hand = [head[k] + right[k] * 0.25 + (-0.25 if k == 1 else 0) + flat[k] * 0.2 for k in range(3)]


def ray_to(P):
    d = rt.norm(rt.sub(P, hand))
    return ",".join(f"{v:.5f}" for v in hand + d)


def floor_pt(ahead, off):
    return [head[0] + flat[0] * ahead + side[0] * off, FL, head[2] + flat[2] * ahead + side[2] * off]


print("== clear, source hand, pointer, steady trigger")
run("clear")
run("source", s="hand", pat=r"SRC")
P1 = floor_pt(1.6, 0.2)
run("pointer", ray_to(P1), pat=r"POINTER set", wait=1.5)
run("trigger", pat=r"PIN add|AIM", wait=1.5)
print("== steady off -> coarse")
run("steady", s="off")
P2 = floor_pt(2.0, 0.6)
run("pointer", ray_to(P2), pat=r"POINTER set", wait=1.0)
run("trigger", pat=r"PIN add|AIM", wait=1.5)
run("steady", s="auto")
print("== half press: 4:1 fine aim")
P3 = floor_pt(1.8, -0.1)
run("pointer", ray_to(P3), pat=r"POINTER set", wait=1.2)
o1 = run("trigger", s="half", wait=1.2)
m = rt.now(); rt.cmd("src"); time.sleep(0.8)
r0 = [rt.msg(l) for l in rt.logs(m) if "RETICLE" in l or "SRC" in l]
P4 = [P3[0] + side[0] * 0.20, FL, P3[2] + side[2] * 0.20]   # ray target moved 20 cm across
m = rt.cmd("pointer", ray_to(P4)); time.sleep(2.5)
lines = [rt.msg(l) for l in rt.logs(m)]
for l in lines:
    if "RETICLE" in l or "POINTER" in l or "AIM" in l: print("    ", l[:220])
rets = [l for l in lines if l.startswith("RETICLE")]
run("trigger", s="full", pat=r"PIN add|AIM", wait=1.5)
run("trigger", s="release")
print("== contact")
P5 = floor_pt(0.9, 0.3)
tip = [P5[0], FL + 0.05, P5[2]]
run("contact", ",".join(f"{v:.4f}" for v in tip), wait=1.5)
run("trigger", pat=r"PIN add|AIM", wait=1.5)
run("contact")
print("== hand_lost")
m = run("hand_lost", wait=4.5)
d = rt.dump()
print("   snack:", d.get("snack"), d.get("snack_text"), d.get("snack_desc"), d.get("snack_remaining_ms"))
for p in d["pins"]:
    print(f"   pin {p['id']} grade={p['grade']} sigma={p.get('sigma_mm')} tol={p.get('tol_mm')} via={p.get('via')} pos={p['pos']}")
for t in d["tapes"]:
    print(f"   tape {t['id']} {t['a']}-{t['b']} {t['len_m']:.4f} '{t['text']}' '{t['tol_text']}'")
json.dump(dict(P3=P3, P4=P4, rets=rets, hand=hand), open(os.path.join(rt.ROOT, "tools", "test", "out", "round2", "synth.json"), "w"), indent=1)
