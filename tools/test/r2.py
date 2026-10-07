#!/usr/bin/env python
"""Round-2 step runner (test agent). REAL pinches / drags through tools/harness/emu.sh; debug broadcasts for setup.
Steps are separated by " :: ".
  at X Y Z              pinch at a Stage point (pixel from the pose read just before)
  floor X Z             pinch at (X, floor_y, Z)  (floor_y read from `planes`)
  dragat X1 Y1 Z1 X2 Y2 Z2 [STEPS STEPMS ENDHOLDMS]   pinch-drag between two Stage points' pixels
  dragpx X1 Y1 X2 Y2 [STEPS STEPMS ENDHOLDMS]
  move X Y              harness move (gaze / controller aim) to a console pixel
  moveat X Y Z          move to the pixel of a Stage point
  pin ID [lift]         pinch on a pin (cap projected from dump + pose)
  ctl PANEL NAME | ctlpt PANEL CX CY | chip PINID | plate TAPEID | px X Y
  dump | bounds | pose | rig | shot NAME | keep SHOTNAME NEWNAME | cmd NAME [a|-] [s...] | sleep S | waitlog REGEX [S]
"""
import sys, os, re, time, json, shutil, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
WAIT = r"TAP |UI |PTR panel=\S+ ev=Press|AIM commit|DRAG end|PULL end"
R2 = os.path.join(rt.ROOT, ".scratch", "shots", "round2")
NOISE = re.compile(r"^(PANEL |CTRL |BOUNDS |POSE |DUMP|CMD recv|PLANE |PLANES listed|RIG slot id=\S+ (stowed|shown|parked|kick))")
_floor = None


def landed_px(lines, hp):
    """Console pixel where the system put the pinch (TAP surface/backstop/pin stage=), or None."""
    for l in lines:
        mm = re.match(r"TAP (surface|backstop|pin|other).*?stage=\(([^)]*)\)", l)
        if mm:
            pr = rt.project(rt.vec(mm.group(2)), hp)
            if pr: return (pr[0], pr[1])
    return None


def sclick(x, y, pat=WAIT, timeout=8, tries=3, extra_wait=0.8):
    """Real pinch with a hijack check: the emulator window is foreground and the REAL cursor rests on it, so some posted
    clicks are taken at the real cursor position. A click whose TAP lands > 45 px from the intended pixel (or that logs
    nothing) is undone (debug undo when it added a pin) and sent again."""
    for k in range(tries):
        hp = rt.pose()
        m, out = rt.click(x, y)
        if "ERROR" in out: print("    HARNESS:", out[-200:])
        hit = rt.wait(pat, m, timeout); time.sleep(extra_wait)
        lines = [rt.msg(l) for l in rt.logs(m)]
        lp = landed_px(lines, hp)
        bad = lp is not None and ((lp[0] - x) ** 2 + (lp[1] - y) ** 2) ** 0.5 > 45
        calib = any(l.startswith("CALIB ev=") for l in lines) and not any(l.startswith(("TAP ", "UI ", "PTR panel")) for l in lines)
        if calib:
            print(f"    .. click {k + 1} taken by the calib catcher (real-cursor hijack): retrying")
            HIJACKS.append(dict(px=[x, y], landed="calib", t=m))
            continue
        if bad:
            print(f"    !! HIJACKED click {k + 1}: intended ({x:.0f},{y:.0f}) landed ({lp[0]:.0f},{lp[1]:.0f}):", " | ".join(l[:120] for l in lines if l.startswith(("TAP", "PIN add", "TAPE add"))))
            HIJACKS.append(dict(px=[x, y], landed=lp, t=m))
            if any(l.startswith("PIN add") for l in lines):
                rt.cmd("undo"); time.sleep(0.8)
            elif any(re.match(r"TAP pin", l) for l in lines):
                print("    !! hijack selected a pin; state may have changed")
            continue
        return m, lines, hit
    return m, lines, hit


HIJACKS = []
CATCH = (1297, 1990)   # console pixel of the real cursor = where the calib catcher panel sits


def floor_y():
    global _floor
    if _floor is None:
        fl = [p for p in rt.planes() if p["kind"] == "FLOOR"]
        _floor = fl[0]["center"][1] if fl else 0.4
    return _floor


def report(m, extra_wait=0.8, pat=WAIT, timeout=8, quiet=False):
    hit = rt.wait(pat, m, timeout); time.sleep(extra_wait)
    lines = [rt.msg(l) for l in rt.logs(m)]
    if not quiet:
        for l in lines:
            if not NOISE.match(l): print("    ", l[:260])
        if not hit: print("     (no TAP/UI/PTR line: the input reached nothing the app logs)")
    return lines


def find_ctrl(b, name):
    for pid, p in b.items():
        if name in p["ctrls"] and not p["popup"]:
            return pid
    return None


def px_of(P):
    hp = rt.pose(); pr = rt.project(P, hp)
    return pr, hp


def step(words):
    w = words[0]
    if w in ("at", "floor"):
        P = [float(words[1]), float(words[2]), float(words[3])] if w == "at" else [float(words[1]), floor_y(), float(words[2])]
        pr, hp = px_of(P)
        print(f"  target ({P[0]:.3f},{P[1]:.3f},{P[2]:.3f}) -> px ({pr[0]:.1f},{pr[1]:.1f}) depth {pr[2]:.2f}")
        m, lines, hit = sclick(pr[0], pr[1], pat=r"ANCHOR (saved|failed)|TAP (pin|other|backstop|blocked|ignored)|UI |PTR panel=\S+ ev=Press|AIM commit|SNACK", timeout=10)
        report(m, extra_wait=0.2)
    elif w in ("dragat", "dragpx"):
        if w == "dragat":
            A = [float(v) for v in words[1:4]]; B = [float(v) for v in words[4:7]]; rest = words[7:]
            hp = rt.pose(); pa = rt.project(A, hp); pb = rt.project(B, hp)
            x1, y1, x2, y2 = pa[0], pa[1], pb[0], pb[1]
        else:
            x1, y1, x2, y2 = (float(v) for v in words[1:5]); rest = words[5:]
        steps = rest[0] if len(rest) > 0 else "12"; stepms = rest[1] if len(rest) > 1 else "60"; endhold = rest[2] if len(rest) > 2 else "200"
        print(f"  drag px ({x1:.0f},{y1:.0f}) -> ({x2:.0f},{y2:.0f}) steps {steps} x {stepms} ms, end hold {endhold} ms")
        m = rt.now()
        out = rt.emu("drag", int(round(x1)), int(round(y1)), int(round(x2)), int(round(y2)), "-Steps", steps, "-StepMs", stepms, "-EndHoldMs", endhold)
        if "ERROR" in out: print("    HARNESS:", out[-200:])
        report(m, pat=r"DRAG (end|cancel)|TAP ", timeout=10, extra_wait=1.2)
    elif w in ("pdragat", "pdragpx"):
        # primed drag: a click on the calib catcher first (eats the real-cursor hijack of the first press after a
        # re-arm), then the drag straight through emu.ps1 with -NoRearm inside the same 60 fps period
        if w == "pdragat":
            A = [float(v) for v in words[1:4]]; B = [float(v) for v in words[4:7]]; rest = words[7:]
            hp = rt.pose(); pa = rt.project(A, hp); pb = rt.project(B, hp)
            x1, y1, x2, y2 = pa[0], pa[1], pb[0], pb[1]
        else:
            x1, y1, x2, y2 = (float(v) for v in words[1:5]); rest = words[5:]
        steps = rest[0] if len(rest) > 0 else "12"; stepms = rest[1] if len(rest) > 1 else "60"; endhold = rest[2] if len(rest) > 2 else "200"
        print(f"  primed drag px ({x1:.0f},{y1:.0f}) -> ({x2:.0f},{y2:.0f}) steps {steps} x {stepms} ms, end hold {endhold} ms")
        print("   waitfast:", rt.emu("waitfast")[-20:])
        m = rt.now()
        r = subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", os.path.join(rt.ROOT, "tools", "test", "pdrag.ps1"),
                            "-CatchX", str(CATCH[0]), "-CatchY", str(CATCH[1]), "-Action", "drag", "-ArgLine",
                            f"{int(round(x1))} {int(round(y1))} {int(round(x2))} {int(round(y2))} -Steps {steps} -StepMs {stepms} -EndHoldMs {endhold}"],
                           capture_output=True, text=True)
        print("   drag:", (r.stdout or r.stderr).strip()[-120:])
        report(m, pat=r"DRAG (end|cancel)|TAP ", timeout=10, extra_wait=1.2)
    elif w == "prime":
        print("   prime:", rt.emu("click", CATCH[0], CATCH[1])[-80:])
    elif w == "move":
        print(rt.emu("move", int(float(words[1])), int(float(words[2]))))
    elif w == "moveat":
        pr, hp = px_of([float(v) for v in words[1:4]])
        print(f"  move to px ({pr[0]:.1f},{pr[1]:.1f})"); print(rt.emu("move", int(round(pr[0])), int(round(pr[1]))))
    elif w == "pin":
        d = rt.dump(); p = rt.pin_by_id(d, words[1]); hp = rt.pose()
        if not p: print("no such pin", words[1]); return
        lift = float(words[2]) if len(words) > 2 else 0.012
        head = rt.add(p["pos"], rt.mul(p["normal"], lift))
        pr = rt.project(head, hp); print(f"  pin {words[1]} cap at px ({pr[0]:.0f},{pr[1]:.0f}) depth {pr[2]:.2f}")
        m, lines, hit = sclick(pr[0], pr[1])
        report(m, extra_wait=0.2)
    elif w in ("ctl", "ctlpt", "chip", "plate"):
        b = rt.bounds(); hp = rt.pose()
        if w == "chip": panel, ctrl = find_ctrl(b, "chip:" + words[1]), "chip:" + words[1]
        elif w == "plate": panel, ctrl = find_ctrl(b, "plate:" + words[1]), "plate:" + words[1]
        elif w == "ctlpt": panel, ctrl = words[1], (float(words[2]), float(words[3]))
        else: panel, ctrl = words[1], words[2]
        if panel not in b or (isinstance(ctrl, str) and ctrl not in b[panel]["ctrls"]):
            print(f"  NOT AVAILABLE: {panel}/{ctrl}; visible: " + "; ".join(f"{k}:{','.join(v['ctrls'])}" for k, v in b.items())); return
        x, y = rt.ctrl_px(b, hp, panel, ctrl); print(f"  {panel}/{ctrl} at px ({x:.0f},{y:.0f})")
        m, lines, hit = sclick(x, y)
        report(m, extra_wait=0.2)
    elif w == "px":
        m, lines, hit = sclick(float(words[1]), float(words[2]))
        report(m, extra_wait=0.2)
    elif w == "dump":
        d = rt.dump(); print(rt.brief(d))
        extra = {k: d.get(k) for k in ("snack_text", "snack_desc", "snack_action", "snack_remaining_ms", "pull", "truing", "tune", "settings_known", "tracking_lost")}
        print("  ", extra)
        for p in d["pins"]:
            print(f"   pin {p['id']} sigma={p.get('sigma_mm')} tol={p.get('tol_mm')} grade={p.get('grade')} snapped={p.get('snapped')} via={p.get('via')} far={p.get('far')} save={p.get('save')} off={p.get('offset_mm', p.get('offset'))}")
        for t in d["tapes"]:
            print(f"   tape {t['id']} {t['a']}-{t['b']} len={t['len_m']:.4f} text='{t['text']}' tol='{t['tol_text']}' rss={t.get('rss_mm', t.get('tol_mm'))} plate={t.get('plate_slot')} at={t.get('plate_at', t.get('plate_pos'))}")
    elif w == "bounds":
        b = rt.bounds(); hp = rt.pose()
        for pid, p in b.items():
            if p["popup"]: print(f"  {pid}: popup {p['w']}x{p['h']} ctrls {p['ctrls']}"); continue
            c = rt.project(p["pos"], hp)
            cs = f"centre px ({c[0]:.0f},{c[1]:.0f}) depth {c[2]:.2f}" if c else "behind"
            print(f"  {pid}: pos ({p['pos'][0]:.3f},{p['pos'][1]:.3f},{p['pos'][2]:.3f}) scale {p['scale']:.3f} size {p['w']}x{p['h']} stretch {p['stretch']:.2f} {cs}")
            for n, cc in p["ctrls"].items():
                x, y = rt.ctrl_px(b, hp, pid, n); print(f"       {n}: panel ({cc['cx']:.0f},{cc['cy']:.0f}) {cc['w']:.0f}x{cc['h']:.0f} -> px ({x:.0f},{y:.0f})")
    elif w == "pose":
        print(" ", rt.pose()["line"])
    elif w == "rig":
        m = rt.cmd("rig"); rt.wait(r"RIG summary", m, 10); time.sleep(0.3)
        for l in rt.logs(m):
            t = rt.msg(l)
            if re.match(r"RIG (summary|violation|body)", t) or (re.match(r"RIG slot", t) and "shown=true" in t): print("    ", t[:300])
    elif w == "shot":
        print(" ", rt.shot(words[1]))
    elif w == "shotnow":
        print(" ", rt.emu("shot", words[1]))
    elif w == "keep":
        os.makedirs(R2, exist_ok=True)
        src = os.path.join(rt.SHOTS, words[1] + ".png"); dst = os.path.join(R2, words[2] + ".png")
        shutil.copyfile(src, dst); print("  kept", dst)
    elif w == "cmd":
        a = words[2] if len(words) > 2 and words[2] != "-" else None
        s = " ".join(words[3:]) if len(words) > 3 else None
        m = rt.cmd(words[1], a, s); time.sleep(1.0)
        for l in rt.logs(m):
            t = rt.msg(l)
            if not re.match(r"RIG slot id=\S+ (stowed|shown)", t): print("    ", t[:260])
    elif w == "sleep":
        time.sleep(float(words[1]))
    elif w == "waitlog":
        m = rt.now(); hits = rt.wait(words[1], m, float(words[2]) if len(words) > 2 else 10)
        print("   ", [rt.msg(l)[:200] for l in hits][-3:])
    else:
        print("unknown step", words)


if __name__ == "__main__":
    steps = " ".join(sys.argv[1:]).split(" :: ")
    for s in steps:
        if not s.strip(): continue
        print(">>", s); step(s.split())
    if HIJACKS:
        print(f"## hijacked clicks this run: {len(HIJACKS)}")
        with open(os.path.join(rt.ROOT, "tools", "test", "out", "round2", "hijacks.jsonl"), "a") as f:
            for h in HIJACKS: f.write(json.dumps(h) + "\n")
