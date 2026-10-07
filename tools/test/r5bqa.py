#!/usr/bin/env python
"""Round-5b QA (test agent): re-verification of R5-1 .. R5-4 with REAL injected pinch-drags (PostMessage through
tools/test/r5drag.ps1, Stopwatch-timed glides, no catcher priming: the emulator window is minimised, so a press lands
where it is posted), plus helpers for the regression. Debug broadcasts only for setup and read-back.
Outputs: tools/test/out/round5b/ (r5b_runs.jsonl, drags.jsonl), screenshots .scratch/shots/round5bqa/evidence/."""
import sys, os, re, time, json, math, shutil, subprocess, threading
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import r5t
import r3t
sys.stdout.reconfigure(encoding="utf-8")
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round5b")
SH = os.path.join(rt.ROOT, ".scratch", "shots", "round5bqa", "evidence")
FINAL = os.path.join(rt.ROOT, ".scratch", "shots", "round5bqa")
for _d in (OUT, SH, FINAL):
    os.makedirs(_d, exist_ok=True)
r5t.OUT = OUT; r5t.SH = SH; r5t.TMPSHOTS = os.path.join(OUT, "_drag_shots"); os.makedirs(r5t.TMPSHOTS, exist_ok=True)
r3t.OUT = OUT; r3t.SH = SH; r3t.CACHE = os.path.join(OUT, "pxcache.json")
RUNS = os.path.join(OUT, "r5b_runs.jsonl")
V3 = r"\(([-\d.]+), ([-\d.]+), ([-\d.]+)\)"


def record(kind, **kw):
    with open(RUNS, "a", encoding="utf-8") as f:
        f.write(json.dumps(dict(kind=kind, **kw), default=str) + "\n")


# ------------------------------------------------------------------ clocks: device seconds-of-day vs host epoch

def clock_offset(n=6):
    """device seconds-of-day minus (host epoch mod 86400), from the adb round trip with the smallest RTT."""
    best = None
    for _ in range(n):
        t0 = time.time()
        out = rt.adb("shell", "date +%H:%M:%S.%N").strip()
        t1 = time.time()
        h, m, s = out.split(":")
        dev = int(h) * 3600 + int(m) * 60 + float(s)
        off = dev - ((t0 + t1) / 2) % 86400
        if best is None or (t1 - t0) < best[0]:
            best = (t1 - t0, off)
    return best[1], best[0]


def host_sod(ms):
    return (ms / 1000.0) % 86400


# ------------------------------------------------------------------ setup helpers (debug broadcasts)

def c(name, a=None, s=None, pat=None, timeout=6, settle=0.3):
    m = rt.cmd(name, a, s)
    if pat:
        rt.wait(pat, m, timeout)
    time.sleep(settle)
    return [rt.msg(l) for l in rt.logs(m)]


def place(P, wait_saved=True):
    lines = c("place", "%.4f,%.4f,%.4f" % tuple(P), pat=r"ANCHOR (saved|failed)" if wait_saved else r"PIN add", timeout=10)
    pid = None
    for l in lines:
        mm = re.search(r"PIN add id=(\S+)", l)
        if mm:
            pid = mm.group(1)
    return pid, lines


def deselect():
    d = rt.dump()
    if d.get("armed"):
        c("tap_pin", None, d["armed"], pat=r"STATE|TAP", settle=0.5)


def cap_of(pin, hp, armed):
    k = max(1.0, min(2.0, rt.dist(pin["pos"], hp["eye"]) / 2.5))
    return rt.add(pin["pos"], rt.mul(pin["normal"], 0.0145 * k + (0.006 if armed else 0.0))), k


# ------------------------------------------------------------------ one real drag with its analysis

def parse(raw):
    res = dict(dstart=None, begin=None, gains=[], dmoves=[], pmoves=[], release=None, dend=None, end=None, other=[])
    for ln in raw:
        st = rt.ts(ln)
        t = rt.tsec(st) if st else None
        s = rt.msg(ln)
        if s.startswith("DRAG start"):
            res["dstart"] = dict(t=t, line=s)
        elif s.startswith("DRAG begin"):
            res["begin"] = dict(t=t, line=s)
        elif s.startswith("PIN drag gain"):
            mm = re.search(r"gain=(\S+) speed=([-\d.]+) reason=(\S*)", s)
            res["gains"].append(dict(t=t, gain=mm.group(1), speed=float(mm.group(2)), reason=mm.group(3)))
        elif s.startswith("DRAG move"):
            mm = re.search(r"hand=" + V3 + r" stage=" + V3 + r" n=(\d+)", s)
            if mm:
                res["dmoves"].append(dict(t=t, hand=[float(mm.group(i)) for i in (1, 2, 3)], stage=[float(mm.group(i)) for i in (4, 5, 6)], n=int(mm.group(7))))
        elif s.startswith("PIN drag move"):
            mm = re.search(r"to=" + V3 + r".*gain=(\S+) snap=(\S+)", s)
            if mm:
                res["pmoves"].append(dict(t=t, to=[float(mm.group(i)) for i in (1, 2, 3)], gain=mm.group(4)))
        elif s.startswith("PIN drag release"):
            res["release"] = dict(t=t, line=s)
        elif re.match(r"DRAG (end|cancel)", s):
            res["dend"] = dict(t=t, line=s)
        elif s.startswith("PIN drag end"):
            res["end"] = dict(t=t, line=s)
        elif re.match(r"(PULL |PIN add|TAPE add|ANCHOR rebase|STATE |TAP )", s):
            res["other"].append(s[:200])
    return res


def run(pid, segs, name, mid=None, quiet=True, hold=300):
    """A real drag starting on pin `pid` cap pixel (or at an explicit start pixel via segs[0] == ('at', x, y)).
    segs: ('m', dx, dy, steps, stepms) cumulative offsets from the start pixel, ('w', ms), ('s', name), ('r',).
    mid: (delay_s, fn) called in a thread after the start (e.g. a bounds read-back mid-drag)."""
    hp = rt.pose(); d = rt.dump()
    pin = rt.pin_by_id(d, pid) if pid else None
    armed = d.get("armed") == pid
    if segs and segs[0][0] == "at":
        sx, sy = segs[0][1], segs[0][2]; segs = segs[1:]
    else:
        cap, k = cap_of(pin, hp, armed); sx, sy, _ = rt.project(cap, hp)
    toks = [f"p:{sx:.1f},{sy:.1f}"]
    for sgm in segs:
        if sgm[0] == "m": toks.append(f"m:{sx + sgm[1]:.1f},{sy + sgm[2]:.1f},{sgm[3]},{sgm[4]}")
        elif sgm[0] == "w": toks.append(f"w:{sgm[1]}")
        elif sgm[0] == "s": toks.append(f"s:{sgm[1]}")
        elif sgm[0] == "r": toks.append("r")
    if toks[-1] != "r":
        toks.append("r")
    off, rtt = clock_offset()
    th = None
    midres = {}
    if mid:
        m0 = rt.now()

        def later():
            # mid[0] seconds after the app logged DRAG begin
            if rt.wait(r"DRAG begin", m0, 25):
                time.sleep(mid[0]); midres["v"] = mid[1]()
        th = threading.Thread(target=later); th.start()
    m, evs, raw = r5t.run_path(toks, name=name, quiet=True, prime=False, hold=hold)
    if th:
        th.join()
    p = parse(raw)
    knots = []; cur = (sx, sy); t_press = None; glides = []
    for e in evs:
        w = e["what"]; td = host_sod(e["host"]) + off
        if w.startswith("press"):
            knots.append((td, cur)); t_press = td
        elif w.startswith("glide-start"):
            knots.append((td, cur)); glides.append(dict(t0=td, frm=cur))
        elif w.startswith("glide-end"):
            mm = re.search(r"fb=\(([-\d.]+),([-\d.]+)\)", w); cur = (float(mm.group(1)), float(mm.group(2)))
            knots.append((td, cur)); glides[-1].update(t1=td, to=cur)
        elif w.startswith("release"):
            knots.append((td, cur))

    def cursor_at(t):
        if not knots: return None
        if t <= knots[0][0]: return knots[0][1]
        for (t0, c0), (t1, c1) in zip(knots, knots[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 <= t0 else (t - t0) / (t1 - t0)
                return (c0[0] + (c1[0] - c0[0]) * f, c0[1] + (c1[1] - c0[1]) * f)
        return knots[-1][1]
    cap_h = 0.0
    if pin is not None:
        k0 = max(1.0, min(2.0, rt.dist(pin["pos"], hp["eye"]) / 2.5)); cap_h = 0.0145 * k0 + 0.006
    rows = []
    for dm in p["dmoves"]:
        tgt = dm["stage"]
        P = rt.add(tgt, rt.mul(pin["normal"], cap_h)) if pin is not None else tgt
        pr = rt.project(P, hp); cpos = cursor_at(dm["t"])
        if pr is None or cpos is None: continue
        rows.append(dict(t=round(dm["t"] - (t_press or 0), 3), n=dm["n"], cursor=(round(cpos[0], 1), round(cpos[1], 1)), target=(round(pr[0], 1), round(pr[1], 1)),
                         err=round(r5t.ang_px(pr, cpos), 1)))
    out = dict(name=name, pin=pid, toks=toks, clock_rtt=round(rtt, 3), t_press=t_press, dstart=p["dstart"]["line"] if p["dstart"] else None,
               begin=p["begin"]["line"] if p["begin"] else None, gains=[(round(g["t"] - (t_press or 0), 3), g["gain"], g["speed"], g["reason"]) for g in p["gains"]],
               release=p["release"]["line"] if p["release"] else None, dend=p["dend"]["line"] if p["dend"] else None, other=p["other"], rows=rows,
               glides=[dict(t0=round(g["t0"] - t_press, 3), t1=round(g.get("t1", 0) - t_press, 3), frm=g["frm"], to=g.get("to")) for g in glides] if t_press else [],
               shots=[e.get("file") for e in evs if e.get("file")], start_px=(round(sx, 1), round(sy, 1)), cursor_end=cur, hp=hp["line"], mid=midres.get("v"))
    mode = re.search(r"mode=(\S+)", out["begin"] or "")
    out["mode"] = mode.group(1) if mode else "none"
    if pin is not None and out["mode"] == "pin":
        time.sleep(0.6)
        d2 = rt.dump(); pin2 = rt.pin_by_id(d2, pid)
        capf, kf = cap_of(pin2, hp, True)
        pr = rt.project(capf, hp)
        last = glides[-1] if glides else None
        u = None
        dx, dy = cur[0] - sx, cur[1] - sy; L = math.hypot(dx, dy)
        if L > 0: u = (dx / L, dy / L)
        ex, ey = pr[0] - cur[0], pr[1] - cur[1]
        out.update(final_pos=pin2["pos"], final_cap_px=(round(pr[0], 1), round(pr[1], 1)), err_px=round(math.hypot(ex, ey), 1),
                   err_along=round(ex * u[0] + ey * u[1], 1) if u else None, err_across=round(-ex * u[1] + ey * u[0], 1) if u else None,
                   dist_start=round(rt.dist(pin["pos"], hp["eye"]), 3), dist_end=round(rt.dist(pin2["pos"], hp["eye"]), 3),
                   moved_mm=round(1000 * rt.dist(pin["pos"], pin2["pos"]), 1), cursor_px=round(r5t.ang_px((sx, sy), cur), 1),
                   pin_px=round(r5t.ang_px(rt.project(cap_of(pin, hp, armed)[0], hp), pr), 1))
        if last and t_press is not None:
            t_end = last["t1"] - t_press
            during = [r for r in rows if r["t"] <= t_end]
            after = [r for r in rows if r["t"] > t_end]
            out["trail_max_px"] = max([r["err"] for r in during], default=None)
            settle = next((r["t"] - t_end for r in after if r["err"] <= 8.0), None)
            out["settle_8px_s"] = round(settle, 3) if settle is not None else None
            out["err_at_end_plus"] = [(round(r["t"] - t_end, 2), r["err"]) for r in after][:8]
    record("drag", **out)
    if not quiet:
        for r in rows:
            print(f"   t={r['t']:6.3f} n={r['n']:3d} cursor={r['cursor']} target={r['target']} err={r['err']}")
    keys = ("dstart", "begin", "mode", "gains", "release", "dend", "other", "err_px", "err_along", "err_across", "dist_start", "dist_end", "moved_mm",
            "cursor_px", "pin_px", "trail_max_px", "settle_8px_s", "err_at_end_plus", "glides", "shots", "clock_rtt", "mid")
    print(f"== {name}")
    for kk in keys:
        if kk in out and out[kk] not in (None, [], ""):
            print(f"   {kk}: {out[kk]}")
    return out, raw, p


if __name__ == "__main__":
    rt.check()
    print(clock_offset())
