"""Round-5b QA items 2 + 3 (R5-2 gain, R5-3 live plates) at about 2.5 m on the floor, work pose yaw 88 / pitch -38.
Setup by debug broadcasts (clear, place, connect, tap_pin); every drag is a real pinch-drag (r5bqa.run)."""
import sys, os, time, json, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r5bqa
rt.check()
which = set(sys.argv[1:])
B = [-2.06, 0.4006, 0.67]; B2 = [-1.71, 0.4006, 0.67]; B3 = [-2.36, 0.4006, 0.40]


def bounds_brief():
    b = rt.bounds()
    return {k: (v["w"], v["h"], sorted(v["ctrls"])) for k, v in b.items()}


if "setup" in which:
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.8)
    pb, _ = r5bqa.place(B); r5bqa.deselect()
    pb2, _ = r5bqa.place(B2); r5bqa.deselect()
    pb3, _ = r5bqa.place(B3); r5bqa.deselect()
    r5bqa.c("connect", None, f"{pb} {pb2}", pat="TAPE add"); r5bqa.c("connect", None, f"{pb} {pb3}", pat="TAPE add")
    time.sleep(0.6); r5bqa.deselect(); time.sleep(0.6)
    json.dump(dict(B=pb, B2=pb2, B3=pb3), open(os.path.join(r5bqa.OUT, "t23_ids.json"), "w"))
    print(rt.brief(rt.dump()))
ids = json.load(open(os.path.join(r5bqa.OUT, "t23_ids.json")))
pb = ids["B"]

if "plates" in which:
    # R5-3: drag B 150 px left, hold 2.6 s; console shot 1.0 s into the hold, bounds read-back 1.6 s into it
    r5bqa.deselect()
    out, raw, p = r5bqa.run(pb, [("m", -150, 0, 10, 30), ("w", 1000), ("s", "r3_plates_mid"), ("w", 1600)], "r3_plates",
                            mid=(1.6, bounds_brief))
    d = rt.dump()
    print("after release:", rt.brief(d))
if "pausequick" in which:
    # R5-2 (a): 150 px in 6 x 60 ms, rest 1000 ms, then 100 px in 4 x 60 ms, hold 1.5 s
    r5bqa.deselect()
    r5bqa.run(pb, [("m", -150, 0, 6, 60), ("w", 1000), ("m", -250, 0, 4, 60), ("w", 1500)], "r2_pause_quick", quiet=False)
if "fast250" in which:
    # R5-2 (b): sustained fast 250 px (12 x 60 ms), hold 2 s
    r5bqa.deselect()
    r5bqa.run(pb, [("m", 250, 0, 12, 60), ("w", 2000)], "r2_fast250", quiet=False)
if "nudge" in which:
    # R5-2 (c, d): fast 100 px, slow nudge 24 px at 12 px/s (0.5 deg/s), shot during it, flick 80 px in 120 ms, hold 1 s
    r5bqa.deselect()
    r5bqa.run(pb, [("m", -100, 0, 5, 40), ("m", -112, 0, 12, 80), ("s", "r2_nudge_x4"), ("m", -124, 0, 12, 80), ("m", -204, 0, 4, 30), ("w", 1000)],
              "r2_nudge_flick", quiet=False)
if "back" in which:
    # R5-5 original repro (round 5 c2): 100 px, rest 300 ms, back 100 px in 240 ms, release
    r5bqa.deselect()
    r5bqa.run(pb, [("m", 100, 0, 5, 40), ("w", 300), ("m", 0, 0, 4, 60), ("w", 1200)], "r2_out_back", quiet=False)
