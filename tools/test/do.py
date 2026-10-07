#!/usr/bin/env python
"""Interactive test steps with REAL pinches (harness clicks), one or more per call, separated by " :: ".
  pin ID            pinch on a pin (cap centre projected from dump + pose)
  ctl PANEL NAME    pinch on a panel control (bounds + pose)        ctlpt PANEL CX CY   raw panel point
  chip PINID | plate TAPEID     pinch on the chip / plate that carries this control
  px X Y            pinch at a console pixel
  dump | bounds | pose | shot NAME | cmd NAME [a] [s] | sleep S | waitlog REGEX [S]
"""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
WAIT = r"TAP |UI |PTR panel=\S+ ev=Press"


def report(m, extra_wait=0.8, pat=WAIT, timeout=8):
    hit = rt.wait(pat, m, timeout); time.sleep(extra_wait)
    lines = [rt.msg(l) for l in rt.logs(m)]
    for l in lines:
        if not re.match(r"PANEL |CTRL |BOUNDS |POSE |DUMP|CMD recv", l): print("    ", l[:230])
    if not hit: print("     (no TAP/UI/PTR line: the pinch reached nothing the app logs)")
    return lines


def find_ctrl(b, name):
    for pid, p in b.items():
        if name in p["ctrls"] and not p["popup"]:
            return pid
    return None


def step(words):
    w = words[0]
    if w == "pin":
        d = rt.dump(); p = rt.pin_by_id(d, words[1]); hp = rt.pose2()
        if not p: print("no such pin", words[1]); return
        lift = float(words[2]) if len(words) > 2 else 0.018
        head = rt.add(p["pos"], rt.mul(p["normal"], lift))
        pr = rt.project(head, hp); print(f"pin {words[1]} cap at px ({pr[0]:.0f},{pr[1]:.0f}) depth {pr[2]:.2f}")
        m, out = rt.click(pr[0], pr[1]); report(m)
    elif w in ("ctl", "ctlpt", "chip", "plate"):
        b = rt.bounds(); hp = rt.pose2()
        if w == "chip": panel, ctrl = find_ctrl(b, "chip:" + words[1]), "chip:" + words[1]
        elif w == "plate": panel, ctrl = find_ctrl(b, "plate:" + words[1]), "plate:" + words[1]
        elif w == "ctlpt": panel, ctrl = words[1], (float(words[2]), float(words[3]))
        else: panel, ctrl = words[1], words[2]
        if panel not in b or (isinstance(ctrl, str) and ctrl not in b[panel]["ctrls"]):
            print(f"NOT AVAILABLE: {panel}/{ctrl}; visible: " + "; ".join(f"{k}:{','.join(v['ctrls'])}" for k, v in b.items())); return
        x, y = rt.ctrl_px(b, hp, panel, ctrl); print(f"{panel}/{ctrl} at px ({x:.0f},{y:.0f})")
        m, out = rt.click(x, y); report(m)
    elif w == "px":
        m, out = rt.click(float(words[1]), float(words[2])); report(m)
    elif w == "dump":
        print(rt.brief(rt.dump()))
    elif w == "bounds":
        b = rt.bounds(); hp = rt.pose2()
        for pid, p in b.items():
            if p["popup"]: print(f"{pid}: popup {p['w']}x{p['h']} ctrls {p['ctrls']}"); continue
            c = rt.project(p["pos"], hp)
            print(f"{pid}: pos ({p['pos'][0]:.3f},{p['pos'][1]:.3f},{p['pos'][2]:.3f}) scale {p['scale']:.3f} size {p['w']}x{p['h']} centre px ({c[0]:.0f},{c[1]:.0f}) depth {c[2]:.2f}")
            for n, cc in p["ctrls"].items():
                x, y = rt.ctrl_px(b, hp, pid, n); print(f"     {n}: panel ({cc['cx']:.0f},{cc['cy']:.0f}) {cc['w']:.0f}x{cc['h']:.0f} -> px ({x:.0f},{y:.0f})")
    elif w == "pose":
        print(rt.pose2()["line"])
    elif w == "shot":
        print(rt.shot(words[1]))
    elif w == "cmd":
        m = rt.cmd(words[1], words[2] if len(words) > 2 and words[2] != "-" else None, " ".join(words[3:]) if len(words) > 3 else None); time.sleep(1.0)
        for l in rt.logs(m): print("    ", rt.msg(l)[:230])
    elif w == "sleep":
        time.sleep(float(words[1]))
    elif w == "waitlog":
        m = rt.now(); print([rt.msg(l) for l in rt.wait(words[1], m, float(words[2]) if len(words) > 2 else 10)])
    else:
        print("unknown step", words)


steps = " ".join(sys.argv[1:]).split(" :: ")
for s in steps:
    print(">>", s); step(s.split())
