#!/usr/bin/env python
"""Round-5 QA driver (test agent): pin drag by direct manipulation, with REAL injected input.

Builds on r4t / r3t / r2 / rt (primed real pinches through tools/harness/emu.sh, debug broadcasts for setup and
read-back) and adds a scripted, primed pinch-DRAG (tools/test/r5drag.ps1: catcher click, press on the pin cap, timed
glides with the button held, holds, background console screenshots mid-drag, release), plus the analysis of the
`PIN drag ...` / `DRAG ...` lines: where the pin was drawn against where the cursor was, gain switches, ratios.

CLI: steps separated by " :: " (every r4t / r3t / r2 step, plus):
  catch on|off                        head-locked calib catcher at CATCH (default upper left, out of the work area)
  drag PIN X,Y,Z [hold=MS] [shot=NAME] [steps=N] [stepms=MS]   primed 1:1 drag of PIN's cap to the pixel of Stage X,Y,Z
"""
import sys, os, re, time, json, math, shutil, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import r2
import r3t
import r4t
sys.stdout.reconfigure(encoding="utf-8")

OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round5")
SH = os.path.join(rt.ROOT, ".scratch", "shots", "round5qa", "evidence")
FINAL = os.path.join(rt.ROOT, ".scratch", "shots", "round5qa")
TMPSHOTS = os.path.join(OUT, "_drag_shots")
for d in (OUT, SH, FINAL, TMPSHOTS):
    os.makedirs(d, exist_ok=True)
r3t.OUT = OUT; r3t.SH = SH; r3t.CACHE = os.path.join(OUT, "pxcache.json")
r4t.OUT = OUT; r4t.SH = SH
R5DRAG = os.path.join(rt.ROOT, "tools", "test", "r5drag.ps1")

# The catcher sits where nothing is tested: upper left of the console frame (wall / ceiling in every pose used).
CATCH = (260, 260)
r3t.CATCH = CATCH
r2.CATCH = CATCH
DEG_PX = 1286.8 * math.pi / 180.0   # console px per degree at the centre


def catch_args():
    offx = (CATCH[0] - rt.CX) / rt.FX * 0.6 - rt.EYE
    offy = (rt.CY - CATCH[1]) / rt.FX * 0.6
    return f"0.6,1,80,{offx:.4f},{offy:.4f}"


def catcher(on=True):
    if on:
        m = rt.cmd("calib", a=catch_args(), s="on")
        rt.wait(r"CALIB", m, 6)
    else:
        m = rt.cmd("calib", s="off")
        rt.wait(r"CALIB off", m, 6)
    return [rt.msg(l) for l in rt.logs(m)]


# ------------------------------------------------------------------------------------------------ geometry

def pin_scale(pos, hp):
    d = rt.dist(pos, hp["pos"])
    return min(2.0, max(1.0, d / 2.5))


def cap_point(pin, hp, armed=None):
    k = pin_scale(pin["pos"], hp)
    lift = 0.006 if (armed if armed is not None else pin.get("armed")) else 0.0
    return rt.add(pin["pos"], rt.mul(pin["normal"], (0.0145 + lift) * k))


def px(P, hp):
    pr = rt.project(P, hp)
    return (pr[0], pr[1]) if pr else None


def floor_hit(x, y, hp, pls):
    """Stage point the line of sight through console pixel (x, y) meets first among the planes."""
    o, d = rt.ray(x, y, hp)
    h = rt.first_hit(o, d, pls)
    return (h[1], h[2]["label"], h[0]) if h else (None, None, None)


def clear_to(P, hp, pls, tol=0.03):
    """True when the eye sees Stage point P (on a plane) without another plane in front of it."""
    o = hp["eye"]
    d = rt.norm(rt.sub(P, o))
    h = rt.first_hit(o, d, pls)
    return h is not None and rt.dist(h[1], P) <= tol, (h[2]["label"] if h else None)


def ang_px(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


# ------------------------------------------------------------------------------------------------ log parsing

V3 = r"\(([-\d.]+), ([-\d.]+), ([-\d.]+)\)"


def v3(m, i=1):
    return [float(m.group(i)), float(m.group(i + 1)), float(m.group(i + 2))]


def parse_drag(lines_raw):
    """lines_raw: logcat lines (with time stamps). Returns dict of parsed events with device seconds."""
    res = dict(move=[], gain=[], dmove=[], release=None, end=None, start=None, begin=None, anchors=[], other=[])
    for ln in lines_raw:
        t = rt.tsec(rt.ts(ln)) if rt.ts(ln) else None
        s = rt.msg(ln)
        if s.startswith("PIN drag move"):
            m = re.search(r"to=" + V3 + r".*gain=(\S+) snap=(\S+)", s)
            if m: res["move"].append(dict(t=t, to=v3(m), gain=m.group(4), snap=m.group(5)))
        elif s.startswith("PIN drag gain"):
            m = re.search(r"gain=(\S+) speed=([-\d.]+)", s)
            if m: res["gain"].append(dict(t=t, gain=m.group(1), speed=float(m.group(2))))
        elif s.startswith("PIN drag release"):
            m = re.search(r"from=" + V3 + r" to=" + V3 + r".*gain=(\S+) grade=(\S+) cancelled=(\S+) tap=(\S+)", s)
            res["release"] = dict(t=t, line=s, frm=v3(m) if m else None, to=v3(m, 4) if m else None,
                                  gain=m.group(7) if m else None, grade=m.group(8) if m else None, cancelled=m.group(9) if m else None, tap=m.group(10) if m else None)
        elif s.startswith("PIN drag end"):
            res["end"] = dict(t=t, line=s)
        elif s.startswith("PIN drag start"):
            res["start"] = dict(t=t, line=s)
        elif s.startswith("DRAG begin"):
            res["begin"] = dict(t=t, line=s)
        elif s.startswith("DRAG move"):
            m = re.search(r"total_px=" + V3 + r" hand=" + V3 + r" stage=(\S+, \S+, \S+\)|null) n=(\d+)", s)
            if m: res["dmove"].append(dict(t=t, total=v3(m), hand=v3(m, 4), n=int(m.group(8)), line=s))
        elif s.startswith("ANCHOR"):
            res["anchors"].append(dict(t=t, line=s))
        elif s.startswith("DRAG start"):
            res["dstart"] = dict(t=t, line=s)
        elif re.match(r"DRAG (end|cancel)", s):
            res["dend"] = dict(t=t, line=s)
    return res


NOISE = re.compile(r"^(PANEL |CTRL |BOUNDS |POSE |DUMP|CMD recv|PLANE |PLANES listed|RIG slot id=\S+ (stowed|shown|parked|kick)|CALIB ev=(Move|Enter|Exit)|PTR panel=\S+ ev=(Enter|Exit|Move)|DRAG move|PIN drag move|RETICLE )")


def show(lines, keep=None):
    for l in lines:
        s = rt.msg(l)
        if keep is not None:
            if re.search(keep, s): print("    ", rt.ts(l)[6:], s[:260])
        elif not NOISE.match(s):
            print("    ", rt.ts(l)[6:], s[:260])


# ------------------------------------------------------------------------------------------------ the drag itself

def run_path(tokens, name="drag", hold=300, settle=400, waitpat=r"DRAG (end|cancel)", timeout=12, quiet=False, prime=True):
    """tokens: r5drag.ps1 path tokens (without the catcher click: added here when prime). Returns (mark, events, raw lines)."""
    toks = ([f"c:{CATCH[0]},{CATCH[1]}"] if prime else []) + list(tokens)
    for attempt in range(2):
        wf = rt.emu("waitfast", "1")
        if "fast" in wf:
            break
        print("   waitfast:", wf[-120:])
    m = rt.now()
    shotdir = os.path.join(TMPSHOTS, name)
    r = subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", R5DRAG, "-Path", ";".join(toks),
                        "-ShotDir", shotdir, "-HoldMs", str(hold), "-SettleMs", str(settle)], capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (r.stdout or "") + (r.stderr or "")
    evs = []
    for ln in out.splitlines():
        mm = re.match(r"EV t=(\d+) host=(\d+) (.*)$", ln.strip())
        if mm: evs.append(dict(t=int(mm.group(1)), host=int(mm.group(2)), what=mm.group(3)))
        elif ln.strip(): print("   ps:", ln.strip()[:200])
    rt.wait(waitpat, m, timeout)
    time.sleep(1.2)
    raw = rt.logs(m)
    # collect screenshots taken in the background
    for e in evs:
        if e["what"].startswith("shot "):
            nm = e["what"].split(" ", 1)[1]
            d = os.path.join(shotdir, nm)
            for i in range(40):
                fs = sorted([f for f in os.listdir(d) if f.startswith("Screenshot_")]) if os.path.isdir(d) else []
                if fs:
                    f = os.path.join(d, fs[-1]); s1 = os.path.getsize(f); time.sleep(0.3)
                    if s1 == os.path.getsize(f) and s1 > 1000:
                        shutil.copyfile(f, os.path.join(SH, nm + ".png")); e["file"] = os.path.join(SH, nm + ".png"); break
                time.sleep(0.25)
    if not quiet:
        show(raw)
    with open(os.path.join(OUT, "drags.jsonl"), "a", encoding="utf-8") as f:
        f.write(json.dumps(dict(t=m, name=name, tokens=toks, events=evs, lines=[rt.msg(l) for l in raw if not NOISE.match(rt.msg(l)) or rt.msg(l).startswith(("PIN drag move", "DRAG move"))])) + "\n")
    return m, evs, raw


def crop_around(path, center, half, name, k=1.0, marks=()):
    """Crop a screenshot around a pixel; marks = [(x, y, colour)] drawn as small crosses (for the report evidence)."""
    from PIL import Image, ImageDraw
    im = Image.open(path).convert("RGB")
    if marks:
        dr = ImageDraw.Draw(im)
        for (x, y, c) in marks:
            dr.line((x - 9, y, x - 3, y), fill=c, width=2); dr.line((x + 3, y, x + 9, y), fill=c, width=2)
            dr.line((x, y - 9, x, y - 3), fill=c, width=2); dr.line((x, y + 3, x, y + 9), fill=c, width=2)
    x0, y0 = max(0, int(center[0] - half)), max(0, int(center[1] - half))
    x1, y1 = min(im.width, int(center[0] + half)), min(im.height, int(center[1] + half))
    im = im.crop((x0, y0, x1, y1))
    if k != 1.0:
        im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
    out = os.path.join(SH, name + ".png")
    im.save(out)
    return out


def step(words):
    w = words[0]
    if w == "catch":
        for l in catcher(words[1] != "off"): print("    ", l[:200])
    else:
        r4t.step(words)


if __name__ == "__main__":
    rt.check()
    for s in " ".join(sys.argv[1:]).split(" :: "):
        if not s.strip(): continue
        print(">>", s)
        try: step(s.split())
        except Exception as e: print("   STEP FAILED:", repr(e))
