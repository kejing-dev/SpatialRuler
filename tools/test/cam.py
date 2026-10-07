#!/usr/bin/env python
"""Drive the emulator camera to a pose with harness keys / right-drags and pose feedback.
usage: cam.py X Y Z YAW_DEG PITCH_DEG [--tol 0.03]      (yaw: left positive, pitch: up positive)"""
import sys, os, re, time, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
args = [a for a in sys.argv[1:] if not a.startswith("--")]
tx, ty, tz, tyaw, tpitch = (float(a) for a in args[:5])
tol = float(sys.argv[sys.argv.index("--tol") + 1]) if "--tol" in sys.argv else 0.03
atol = 0.25


def getpose():
    for _ in range(3):
        a = rt.pose2(); time.sleep(0.5); b = rt.pose2()
        if a["line"] == b["line"]: return b
    return b


def rotate():
    for it in range(8):
        hp = getpose()
        dyaw = (tyaw - math.degrees(hp["yaw"]) + 180) % 360 - 180
        dpitch = tpitch - math.degrees(hp["pitch"])
        if abs(dyaw) <= atol and abs(dpitch) <= atol: return hp
        # one drag: at most 900 px horizontally, 600 px vertically
        sx = max(-900, min(900, dyaw / 0.044 * rot_sign[0])); sy = max(-600, min(600, dpitch / 0.044 * rot_sign[1]))
        x0, y0 = 1080 - sx / 2, 1000 - sy / 2
        out = rt.emu("drag", int(x0), int(y0), int(x0 + sx), int(y0 + sy), "-Button", "right", "-Steps", 12)
        time.sleep(0.8)
        hp2 = getpose()
        ddy = (math.degrees(hp2["yaw"]) - math.degrees(hp["yaw"]) + 180) % 360 - 180; ddp = math.degrees(hp2["pitch"]) - math.degrees(hp["pitch"])
        print(f"  rot drag ({sx:.0f},{sy:.0f}) px -> dyaw {ddy:+.2f} dpitch {ddp:+.2f} (wanted {dyaw:+.2f},{dpitch:+.2f})")
        if abs(sx) > 50 and abs(ddy) > 0.5 and (ddy * dyaw) < 0: rot_sign[0] *= -1
        if abs(sy) > 50 and abs(ddp) > 0.5 and (ddp * dpitch) < 0: rot_sign[1] *= -1
    return getpose()


rot_sign = [-1.0, 1.0]     # drag right -> yaw decreases (turn right); drag down -> pitch ... (corrected on the fly)
speed = {"w": 1.1, "s": 1.1, "a": 0.9, "d": 0.9, "q": 1.0, "e": 1.0}
for it in range(14):
    hp = getpose()
    delta = [tx - hp["pos"][0], ty - hp["pos"][1], tz - hp["pos"][2]]
    f, r, u = rt.dot(delta, hp["fwd"]), rt.dot(delta, hp["right"]), rt.dot(delta, hp["up"])
    err = math.sqrt(rt.dot(delta, delta))
    print(f"it{it} pos ({hp['pos'][0]:.3f},{hp['pos'][1]:.3f},{hp['pos'][2]:.3f}) err {err:.3f} m  (fwd {f:+.3f} right {r:+.3f} up {u:+.3f})")
    if err <= tol: break
    comp = max((("w" if f > 0 else "s", abs(f)), ("d" if r > 0 else "a", abs(r)), ("e" if u > 0 else "q", abs(u))), key=lambda kv: kv[1])
    key, amount = comp
    if amount < 0.05 and key in ("w", "s"):
        n = max(1, int(round(amount / 0.01)))
        rt.emu("wheel", 1080, 1000, "-Notches", n if key == "w" else -n); time.sleep(0.8); continue
    hold = int(max(30, min(900, amount / speed[key] * 1000 * 0.8)))
    rt.emu("key", key, "-HoldMs", hold); time.sleep(1.0)
hp = rotate()
print("final:", hp["line"])
print(f"final yaw {math.degrees(hp['yaw']):.2f} pitch {math.degrees(hp['pitch']):.2f}")
