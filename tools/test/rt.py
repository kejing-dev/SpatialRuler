#!/usr/bin/env python
"""Round-1 acceptance test library for Spatial Ruler (test agent). Pinned to emulator-5556 / AVD PICO_6.1_Ruler.

Host input only goes through tools/harness/emu.sh (PostMessage to the Ruler emulator's qemu PID).
Everything here reads the app through its debug broadcast + logcat; nothing edits product code.
"""
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time

DEV = "emulator-5556"
AVD = "PICO_6.1_Ruler"
PKG = "io.embodit.spatialruler"
TAG = "SpatialRuler"
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
EMU = os.path.join(ROOT, "tools", "harness", "emu.sh").replace("\\", "/")
SHOTS = os.path.join(ROOT, ".scratch", "shots", "harness")
R1 = os.path.join(ROOT, ".scratch", "shots", "round1")
FX = 1286.8
CX, CY = 1080.0, 1079.0
EYE = 0.032

os.environ.update(ANDROID_SERIAL=DEV, ADB_SERIAL=DEV, PICO_CLI_DEVICE=DEV)


def _bash():
    for cand in (os.environ.get("GITBASH"), r"C:\Program Files\Git\usr\bin\bash.exe", r"C:\Program Files\Git\bin\bash.exe", shutil.which("bash")):
        if cand and os.path.exists(cand) and "system32" not in cand.lower():
            return cand
    return "bash"


BASH = _bash()


def adb(*args, timeout=60):
    r = subprocess.run(["adb", "-s", DEV, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (r.stdout or "") + (r.stderr or "")


def check():
    avd = (adb("emu", "avd", "name").splitlines() or [""])[0].strip()
    if avd != AVD:
        sys.exit(f"WRONG AVD on {DEV}: '{avd}' (expected {AVD})")


def emu(*args, timeout=120):
    r = subprocess.run([BASH, EMU, *[str(a) for a in args]], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout, cwd=ROOT)
    return ((r.stdout or "") + (r.stderr or "")).strip()


def now():
    return adb("shell", "date '+%m-%d %H:%M:%S.%3N'").strip()


def logs(mark, all_tags=False):
    if all_tags:
        out = adb("logcat", "-d", "-v", "time", "-T", mark)
    else:
        out = adb("logcat", "-d", "-v", "time", "-T", mark, "-s", f"{TAG}:V")
    return [ln for ln in out.replace("\r", "").splitlines() if ln and not ln.startswith("-----")]


def msg(line):
    """Message part of a logcat line of our tag."""
    m = re.search(r"\): ?(.*)$", line)
    return m.group(1) if m else line


def ts(line):
    m = re.match(r"(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)", line)
    return m.group(1) if m else ""


def tsec(stamp):
    m = re.match(r"\d\d-\d\d (\d\d):(\d\d):(\d\d\.\d+)", stamp)
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))


def wait(pattern, mark, timeout=15.0, all_tags=False):
    rx = re.compile(pattern)
    end = time.time() + timeout
    while True:
        hits = [ln for ln in logs(mark, all_tags) if rx.search(ln)]
        if hits or time.time() >= end:
            return hits
        time.sleep(0.3)


def cmd(name, a=None, s=None):
    extra = ""
    if a:
        extra += f" --es a '{a}'"
    if s:
        extra += f" --es s '{s}'"
    m = now()
    adb("shell", f"am broadcast -a {PKG}.DEBUG -p {PKG} --es cmd {name}{extra}")
    return m


def dump():
    m = cmd("dump")
    hit = wait(r"DUMP", m, 10)
    if not hit:
        raise RuntimeError("no DUMP line")
    one = [ln for ln in hit if "DUMP {" in ln]
    if one:
        return json.loads(msg(one[-1]).split("DUMP ", 1)[1])
    time.sleep(0.5)
    parts = {}
    total = 0
    for ln in logs(m):
        pm = re.search(r"DUMP\+ (\d+)/(\d+) (.*)$", ln)
        if pm:
            parts[int(pm.group(1))] = pm.group(3)
            total = int(pm.group(2))
    if total and len(parts) == total:
        try:
            return json.loads("".join(parts[i] for i in range(1, total + 1)))
        except Exception:
            pass
    text = adb("shell", f"cat /sdcard/Android/data/{PKG}/files/dump.json")
    return json.loads(text)


def vec(text):
    return [float(v) for v in text.split(",")]


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def mul(a, k):
    return [c * k for c in a]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def norm(v):
    n = math.sqrt(dot(v, v))
    return [c / n for c in v]


def dist(a, b):
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


def qrot(q, v):
    x, y, z, w = q
    c = (y * v[2] - z * v[1], z * v[0] - x * v[2], x * v[1] - y * v[0])
    t = [2 * a for a in c]
    c2 = (y * t[2] - z * t[1], z * t[0] - x * t[2], x * t[1] - y * t[0])
    return [v[i] + w * t[i] + c2[i] for i in range(3)]


def pose(timeout=10):
    m = cmd("pose")
    hit = wait(r"POSE hmd", m, timeout)
    if not hit:
        raise RuntimeError("no POSE line")
    line = msg(hit[-1])
    hm = re.search(r"pos=\(([^)]*)\) q\(([^)]*)\) fwd=\(([^)]*)\) up=\(([^)]*)\) right=\(([^)]*)\)", line)
    p = dict(pos=vec(hm.group(1)), q=vec(hm.group(2)), fwd=vec(hm.group(3)), up=vec(hm.group(4)), right=vec(hm.group(5)), line=line)
    # the log has 3 decimals: re-orthonormalise (fwd is kept, right/up are made perpendicular)
    f = norm(p["fwd"])
    r = norm(sub(p["right"], mul(f, dot(p["right"], f))))
    u = cross(r, f)
    if dot(u, p["up"]) < 0:
        u = mul(u, -1)
    p["fwd"], p["right"], p["up"] = f, r, u
    p["eye"] = add(p["pos"], mul(r, -EYE))
    return p


def project(P, hp):
    d = sub(P, hp["pos"])
    depth = dot(d, hp["fwd"])
    if depth <= 0.01:
        return None
    px = CX + FX * (dot(d, hp["right"]) + EYE) / depth
    py = CY - FX * dot(d, hp["up"]) / depth
    return (px, py, depth)


def ray(px, py, hp):
    d = add(add(hp["fwd"], mul(hp["right"], (px - CX) / FX)), mul(hp["up"], (CY - py) / FX))
    return hp["eye"], norm(d)


def planes():
    m = cmd("planes")
    wait(r"PLANES listed", m, 10)
    res = []
    for ln in logs(m):
        pm = re.search(r"PLANE (\S+) kind=(\S+) center=\(([^)]*)\) normal=\(([^)]*)\) verts=(.*)$", ln)
        if pm:
            verts = [vec(v) for v in re.findall(r"\(([^)]*)\)", pm.group(5))]
            res.append(dict(label=pm.group(1), kind=pm.group(2), center=vec(pm.group(3)), normal=vec(pm.group(4)), verts=verts))
    return res


def plane_normal(pl):
    v = pl["verts"]
    n = cross(sub(v[1], v[0]), sub(v[3], v[0]))
    return norm(n)


def ray_plane(o, d, pl, inside=True):
    """Hit of the ray with the plane's quad (two-sided). Returns (t, point) or None."""
    n = plane_normal(pl)
    den = dot(d, n)
    if abs(den) < 1e-6:
        return None
    t = dot(sub(pl["verts"][0], o), n) / den
    if t <= 0:
        return None
    p = add(o, mul(d, t))
    if inside:
        v = pl["verts"]
        sign = 0
        for i in range(4):
            a, b = v[i], v[(i + 1) % 4]
            c = dot(cross(sub(b, a), sub(p, a)), n)
            if abs(c) < 1e-9:
                continue
            s = 1 if c > 0 else -1
            if sign == 0:
                sign = s
            elif s != sign:
                return None
    return (t, p)


def first_hit(o, d, pls):
    best = None
    for pl in pls:
        h = ray_plane(o, d, pl)
        if h and (best is None or h[0] < best[0]):
            best = (h[0], h[1], pl)
    return best


def click(x, y, *extra):
    """Real pinch-tap at console-screenshot pixel. Returns the device time mark taken just before."""
    m = now()
    out = emu("click", int(round(x)), int(round(y)), *extra)
    return m, out


def shot(name, wait_fast=True, settle=1.5):
    if wait_fast:
        emu("waitfast")
        time.sleep(settle)
    out = emu("shot", name)
    return os.path.join(SHOTS, name + ".png") if out.startswith("OK") else None


def bounds():
    m = cmd("bounds")
    wait(r"BOUNDS panels", m, 10)
    panels = {}
    for ln in logs(m):
        t = msg(ln)
        pm = re.search(r"PANEL id=(\S+) pos=\(([^)]*)\) q\(([^)]*)\) scale=([\d.eE+-]+) size_px=(\d+)x(\d+)", t)
        if pm:
            # stretch_x (round 2a): the panel is that much narrower than scale x size_px (plates, snackbar); 1 when absent
            sx = re.search(r"stretch_x=([\d.eE+-]+)", t)
            panels[pm.group(1)] = dict(id=pm.group(1), pos=vec(pm.group(2)), q=vec(pm.group(3)), scale=float(pm.group(4)), w=int(pm.group(5)), h=int(pm.group(6)), stretch=float(sx.group(1)) if sx else 1.0, ctrls={}, popup=False)
            continue
        pp = re.search(r"PANEL id=(\S+) popup=true .*size_px=(\d+)x(\d+)", t)
        if pp:
            panels[pp.group(1)] = dict(id=pp.group(1), pos=None, q=None, scale=None, w=int(pp.group(2)), h=int(pp.group(3)), ctrls={}, popup=True)
            continue
        cm = re.search(r"CTRL panel=(\S+) name=(\S+) cx=([\d.-]+) cy=([\d.-]+) w=([\d.-]+) h=([\d.-]+)", t)
        if cm and cm.group(1) in panels:
            panels[cm.group(1)]["ctrls"][cm.group(2)] = dict(cx=float(cm.group(3)), cy=float(cm.group(4)), w=float(cm.group(5)), h=float(cm.group(6)))
    return panels


def panel_point(panel, cx, cy):
    k = panel["scale"]
    local = [(cx - panel["w"] / 2) * 0.0004 * k * panel.get("stretch", 1.0), (panel["h"] / 2 - cy) * 0.0004 * k, 0.0]
    return add(panel["pos"], qrot(panel["q"], local))


def ctrl_px(panels, hp, panel_id, control):
    """Console pixel of a control (name) or raw panel point (cx, cy)."""
    p = panels[panel_id]
    if isinstance(control, (tuple, list)):
        cx, cy = control
    else:
        c = p["ctrls"][control]
        cx, cy = c["cx"], c["cy"]
    pr = project(panel_point(p, cx, cy), hp)
    return (pr[0], pr[1])


def press(panel_id, control, expect=None, timeout=8, extra=()):
    """Real pinch on a panel control. Returns (ok, lines)."""
    b = bounds()
    hp = pose()
    if panel_id not in b or (not isinstance(control, (tuple, list)) and control not in b[panel_id]["ctrls"]):
        return False, [f"no such control {panel_id}/{control}; panels={list(b)}", *(f"{k}:{list(v['ctrls'])}" for k, v in b.items())]
    x, y = ctrl_px(b, hp, panel_id, control)
    m, out = click(x, y, *extra)
    pat = expect or r"UI |PTR panel"
    hit = wait(pat, m, timeout)
    time.sleep(0.2)
    lines = [msg(l) for l in logs(m)]
    return bool(hit), [f"click ({x:.0f},{y:.0f}) {out[-60:]}"] + lines


def tap(x, y, expect=r"TAP |PTR panel|UI ", timeout=8, settle=0.4):
    m, out = click(x, y)
    hit = wait(expect, m, timeout)
    time.sleep(settle)
    return m, [msg(l) for l in logs(m)]


def pin_by_id(d, pid):
    for p in d["pins"]:
        if p["id"] == pid:
            return p
    return None


def tape_by_id(d, tid):
    for t in d["tapes"]:
        if t["id"] == tid:
            return t
    return None


def brief(d):
    pins = " ".join(f"{p['id']}({p['pos'][0]:.4f},{p['pos'][1]:.4f},{p['pos'][2]:.4f}|{p['grade']}|{p['save']}{'|far' if p.get('far') else ''})" for p in d["pins"])
    tapes = " ".join(f"{t['id']}[{t['a']}-{t['b']} {t['len_m']:.4f} '{t['text']}' '{t['tol_text']}']" for t in d["tapes"])
    return f"state={d['state']} unit={d['unit']} lang={d['lang']} armed={d['armed']} selTape={d['selectedTape']} hist={d['history']} menu={d['menu']} dialog={d['dialog']} snack={d['snack']}\n  pins: {pins}\n  tapes: {tapes}"


def keep(name, new=None):
    """Copy a harness screenshot into round1/ under a descriptive name."""
    os.makedirs(R1, exist_ok=True)
    src = os.path.join(SHOTS, name + ".png")
    dst = os.path.join(R1, (new or name) + ".png")
    shutil.copyfile(src, dst)
    return dst


if __name__ == "__main__":
    check()
    print("bash:", BASH)
    print(brief(dump()))
    print(pose()["line"])


# ---- refined head orientation -------------------------------------------------------------------------------
# The POSE line prints 3 decimals. The emulator camera has yaw + pitch only (no roll), so the 13 logged numbers
# (q + fwd + up + right) over-determine 2 angles: take the centre of the set of (yaw, pitch) whose rounded values
# reproduce the log. Half-extent of that set = orientation uncertainty (rad).
def _axes(yaw, pitch):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    fwd = [-sy * cp, sp, -cy * cp]
    right = [cy, 0.0, -sy]
    up = cross(right, fwd)
    q = [math.cos(yaw / 2) * math.sin(pitch / 2), math.sin(yaw / 2) * math.cos(pitch / 2), -math.sin(yaw / 2) * math.sin(pitch / 2), math.cos(yaw / 2) * math.cos(pitch / 2)]
    return fwd, up, right, q


def refine(hp_line):
    hm = re.search(r"pos=\(([^)]*)\) q\(([^)]*)\) fwd=\(([^)]*)\) up=\(([^)]*)\) right=\(([^)]*)\)", hp_line)
    pos, q, f, u, r = (vec(hm.group(i)) for i in range(1, 6))
    yaw0 = math.atan2(-f[0], -f[2])
    pitch0 = math.asin(max(-1.0, min(1.0, f[1])))
    if q[3] < 0:
        q = [-c for c in q]
    logged = f + u + r + q
    best = None
    feas = []
    n = 60
    step = 0.00005
    for i in range(-n, n + 1):
        for j in range(-n, n + 1):
            yaw, pitch = yaw0 + i * step, pitch0 + j * step
            ff, uu, rr, qq = _axes(yaw, pitch)
            if qq[3] < 0:
                qq = [-c for c in qq]
            vals = ff + uu + rr + qq
            err = max(abs(vals[k] - logged[k]) for k in range(13))
            if err <= 0.00052:
                feas.append((yaw, pitch))
            if best is None or err < best[0]:
                best = (err, yaw, pitch)
    if feas:
        yaw = sum(a for a, _ in feas) / len(feas)
        pitch = sum(b for _, b in feas) / len(feas)
        unc = max(max(abs(a - yaw) for a, _ in feas), max(abs(b - pitch) for _, b in feas))
        ok = True
    else:
        yaw, pitch, unc, ok = best[1], best[2], best[0], False
    ff, uu, rr, _ = _axes(yaw, pitch)
    return dict(pos=pos, fwd=ff, up=uu, right=rr, eye=add(pos, mul(rr, -EYE)), yaw=yaw, pitch=pitch, unc=unc, feasible=ok, line=hp_line)


def pose2(timeout=10):
    m = cmd("pose")
    hit = wait(r"POSE hmd", m, timeout)
    if not hit:
        raise RuntimeError("no POSE line")
    return refine(msg(hit[-1]))
