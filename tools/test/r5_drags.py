#!/usr/bin/env python
"""Round-5 QA: the pin-drag acceptance runs (real primed pinch-drags through r5drag.ps1) and their analysis.

usage: python r5_drags.py <test> [args...]   (see the functions at the end; every run appends to out/round5/r5_runs.jsonl)
"""
import sys, os, re, time, json, math, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import r5t
sys.stdout.reconfigure(encoding="utf-8")
RUNS = os.path.join(r5t.OUT, "r5_runs.jsonl")


def host_sod(ms):
    t = datetime.datetime.fromtimestamp(ms / 1000.0)
    return t.hour * 3600 + t.minute * 60 + t.second + t.microsecond / 1e6


def clock_offset(evs, raw):
    """device seconds-of-day minus host seconds-of-day, from the catcher press (host click-down vs CALIB ev=Press)."""
    hd = [e for e in evs if e["what"].startswith("click-down")]
    dv = [l for l in raw if "CALIB ev=Press" in l]
    if not hd or not dv:
        return None
    return rt.tsec(rt.ts(dv[0])) - host_sod(hd[0]["host"])


def pin_of(d, pid):
    return rt.pin_by_id(d, pid)


def record(kind, **kw):
    with open(RUNS, "a", encoding="utf-8") as f:
        f.write(json.dumps(dict(kind=kind, **kw), default=str) + "\n")


def analyse(name, hp, evs, raw, cursor_final, press_px, glide_end_host=None, plane_n=None):
    p = r5t.parse_drag(raw)
    off = clock_offset(evs, raw)
    out = dict(name=name)
    out["dstart"] = p.get("dstart", {}).get("line")
    out["begin"] = p["begin"]["line"] if p["begin"] else None
    out["start"] = p["start"]["line"] if p["start"] else None
    out["release"] = p["release"]["line"] if p["release"] else None
    out["end"] = p["end"]["line"] if p["end"] else None
    out["gains"] = [(round(g["t"], 3), g["gain"], g["speed"]) for g in p["gain"]]
    out["anchors"] = [a["line"] for a in p["anchors"]]
    # where the target was drawn against the cursor
    errs = []
    for mv in p["move"]:
        pr = rt.project(mv["to"], hp)
        if pr:
            errs.append(dict(t=mv["t"], px=(round(pr[0], 1), round(pr[1], 1)), snap=mv["snap"], gain=mv["gain"]))
    out["moves"] = errs
    if p["release"] and p["release"]["to"]:
        pr = rt.project(p["release"]["to"], hp)
        out["release_px"] = (round(pr[0], 1), round(pr[1], 1))
        out["release_err_px"] = round(r5t.ang_px(pr, cursor_final), 1)
        out["release_err_deg"] = round(out["release_err_px"] / r5t.DEG_PX, 3)
        out["release_dist_m"] = round(rt.dist(p["release"]["to"], hp["pos"]), 3)
        out["moved_mm"] = round(1000 * rt.dist(p["release"]["frm"], p["release"]["to"]), 1)
        out["cursor_travel_px"] = round(r5t.ang_px(press_px, cursor_final), 1)
        fr = rt.project(p["release"]["frm"], hp)
        out["pin_travel_px"] = round(r5t.ang_px(fr, pr), 1)
    # where the pin would be had the whole hand travel been applied 1 : 1 (no 1 : 4 tail): head -> press + hand * d / 4
    try:
        ds = re.search(r"stage=\(([-\d.]+), ([-\d.]+), ([-\d.]+)\)", p["dstart"]["line"])
        de = re.search(r"hand=\(([-\d.]+), ([-\d.]+), ([-\d.]+)\)", p["dend"]["line"])
        P0 = [float(ds.group(i)) for i in (1, 2, 3)]; H = [float(de.group(i)) for i in (1, 2, 3)]
        head = hp["pos"]; app = rt.dist(P0, head) / 4.0
        Q = rt.add(P0, rt.mul(H, app)); dirv = rt.sub(Q, head)
        frm = p["release"]["frm"]; n = plane_n if plane_n else [0.0, 1.0, 0.0]
        t = rt.dot(rt.sub(frm, head), n) / rt.dot(dirv, n)
        F = rt.add(head, rt.mul(dirv, t)); prf = rt.project(F, hp); pp0 = rt.project(P0, hp)
        out["full_gain_px"] = (round(prf[0], 1), round(prf[1], 1))
        out["full_gain_err_px"] = round(r5t.ang_px(prf, cursor_final), 1)
        out["press_point_px"] = (round(pp0[0], 1), round(pp0[1], 1))
        out["press_offset_px"] = round(r5t.ang_px(pp0, press_px), 1)
        inpl = math.hypot(rt.dot(H, hp["right"]), rt.dot(H, hp["up"]))
        trav = r5t.ang_px(pp0, cursor_final) / rt.FX
        out["hand_inplane_m"] = round(inpl, 4)
        out["hand_equiv_depth_m"] = round(inpl / trav, 2) if trav > 1e-4 else None
        out["press_dist_m"] = round(rt.dist(P0, head), 3)
    except Exception as e:
        out["full_gain_err"] = repr(e)
    if off is not None and glide_end_host is not None and p["move"]:
        # first logged target within 3 px of the final one after the last move was sent
        fin = errs[-1]["px"] if errs else None
        t_end = host_sod(glide_end_host) + off
        after = [e for e in errs if e["t"] >= t_end - 0.05]
        out["clock_offset_s"] = round(off, 3)
        out["glide_end_dev"] = round(t_end, 3)
    return out, p


def drag(pin_id, dx, dy, name, steps=10, stepms=30, hold1=1500, hold2=900, shot=True, extra=(), quiet=False):
    """Primed 1:1 drag of a pin's cap by (dx, dy) console px, with a mid-drag screenshot after hold1 ms of stillness."""
    hp = rt.pose()
    d = rt.dump()
    pin = pin_of(d, pin_id)
    cap = r5t.cap_point(pin, hp, armed=(d.get("armed") == pin_id))
    cx, cy = r5t.px(cap, hp)
    tx, ty = cx + dx, cy + dy
    toks = [f"p:{cx:.1f},{cy:.1f}", f"m:{tx:.1f},{ty:.1f},{steps},{stepms}", *extra]
    if shot:
        toks += [f"w:{hold1}", f"s:{name}_mid", f"w:{hold2}"]
    else:
        toks += [f"w:{hold1}"]
    toks += ["r"]
    print(f"== {name}: pin {pin_id} at {pin['pos']} dist {rt.dist(pin['pos'], hp['pos']):.2f} m, cap px ({cx:.0f},{cy:.0f}) -> ({tx:.0f},{ty:.0f})")
    m, evs, raw = r5t.run_path(toks, name=name, quiet=quiet)
    ge = [e for e in evs if e["what"].startswith("glide-end")]
    res, p = analyse(name, hp, evs, raw, (tx, ty), (cx, cy), ge[-1]["host"] if ge else None, plane_n=pin["normal"])
    res.update(pin=pin_id, pin_before=pin["pos"], cap_px=(round(cx, 1), round(cy, 1)), cursor_final=(round(tx, 1), round(ty, 1)), steps=steps, stepms=stepms,
               shot=[e.get("file") for e in evs if e.get("file")])
    record("drag", **res)
    for k in ("dstart", "begin", "start", "release", "end", "release_px", "release_err_px", "release_err_deg", "release_dist_m", "moved_mm", "cursor_travel_px", "pin_travel_px", "full_gain_px", "full_gain_err_px", "press_point_px", "press_offset_px", "hand_inplane_m", "hand_equiv_depth_m", "press_dist_m", "gains", "anchors", "clock_offset_s"):
        if k in res: print(f"   {k}: {res[k]}")
    if res.get("moves"):
        print("   moves(px):", [(m_["px"], m_["snap"], m_["gain"]) for m_ in res["moves"]][-6:])
    return res, hp, evs, raw, p


if __name__ == "__main__":
    rt.check()
    fn = sys.argv[1]
    args = sys.argv[2:]
    if fn == "drag":
        kw = {}
        pos = []
        for a in args:
            if "=" in a:
                k, v = a.split("=", 1); kw[k] = int(v) if v.lstrip("-").isdigit() else v
            else:
                pos.append(a)
        drag(pos[0], float(pos[1]), float(pos[2]), pos[3], **kw)


def path_run(pin_id, segs, name, quiet=True):
    """segs: list of ('m', dx, dy, steps, stepms) relative to the cap pixel (cumulative), ('w', ms), ('s', shotname).
    Prints a timeline of DRAG move lines (every 100 ms): device time from the press, commanded cursor px, target px, gain."""
    hp = rt.pose(); d = rt.dump(); pin = pin_of(d, pin_id)
    cap = r5t.cap_point(pin, hp, armed=(d.get("armed") == pin_id)); cx, cy = r5t.px(cap, hp)
    toks = [f"p:{cx:.1f},{cy:.1f}"]
    for s in segs:
        if s[0] == "m": toks.append(f"m:{cx + s[1]:.1f},{cy + s[2]:.1f},{s[3]},{s[4]}")
        elif s[0] == "w": toks.append(f"w:{s[1]}")
        elif s[0] == "s": toks.append(f"s:{s[1]}")
    toks.append("r")
    print(f"== {name}: pin {pin_id} at {pin['pos']} dist {rt.dist(pin['pos'], hp['pos']):.2f} m, cap px ({cx:.0f},{cy:.0f})")
    m, evs, raw = r5t.run_path(toks, name=name, quiet=quiet)
    off = clock_offset(evs, raw)
    p = r5t.parse_drag(raw)
    # commanded cursor (host -> device time) as a piecewise-linear function
    knots = []
    cur = (cx, cy)
    for e in evs:
        w = e["what"]
        if w.startswith("press"):
            knots.append((host_sod(e["host"]) + off, cur))
        elif w.startswith("glide-start"):
            knots.append((host_sod(e["host"]) + off, cur))
        elif w.startswith("glide-end"):
            mm = re.search(r"fb=\(([-\d.]+),([-\d.]+)\)", w); cur = (float(mm.group(1)), float(mm.group(2)))
            knots.append((host_sod(e["host"]) + off, cur))
        elif w.startswith("release"):
            knots.append((host_sod(e["host"]) + off, cur))

    def cursor_at(t):
        if not knots: return None
        if t <= knots[0][0]: return knots[0][1]
        for (t0, c0), (t1, c1) in zip(knots, knots[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 <= t0 else (t - t0) / (t1 - t0)
                return (c0[0] + (c1[0] - c0[0]) * f, c0[1] + (c1[1] - c0[1]) * f)
        return knots[-1][1]
    t_press = knots[0][0] if knots else 0
    gains = [(g["t"], g["gain"], g["speed"]) for g in p["gain"]]
    rows = []
    for dm in p["dmove"]:
        sm = re.search(r"stage=\(([-\d.]+), ([-\d.]+), ([-\d.]+)\)", dm["line"])
        tgt = [float(sm.group(i)) for i in (1, 2, 3)] if sm else None
        pr = rt.project(tgt, hp) if tgt else None
        c = cursor_at(dm["t"])
        g = "1:1"
        for (tg, gg, sp) in gains:
            if tg <= dm["t"]: g = gg
        rows.append(dict(t=round(dm["t"] - t_press, 3), cursor=(round(c[0], 1), round(c[1], 1)) if c else None,
                         target=(round(pr[0], 1), round(pr[1], 1)) if pr else None, gain=g, n=dm["n"],
                         err=round(r5t.ang_px(pr, c), 1) if (pr and c) else None))
    print("   gains:", [(round(t - t_press, 3), gg, sp) for (t, gg, sp) in gains])
    print("   knots:", [(round(t - t_press, 3), (round(c[0]), round(c[1]))) for (t, c) in knots])
    for r_ in rows:
        print(f"   t={r_['t']:6.3f} n={r_['n']:3d} cursor={r_['cursor']} target={r_['target']} err={r_['err']} gain={r_['gain']}")
    for k in ("release", "end"):
        if p[k]: print("  ", p[k]["line"][:220])
    for a in p["anchors"]: print("  ", a["line"][:200])
    record("path", name=name, pin=pin_id, toks=toks, rows=rows, gains=[(round(t - t_press, 3), gg, sp) for (t, gg, sp) in gains],
           release=p["release"]["line"] if p["release"] else None, end=p["end"]["line"] if p["end"] else None, offset=off,
           shots=[e.get("file") for e in evs if e.get("file")])
    return rows, p, hp, evs, raw
