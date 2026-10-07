"""Round-5c QA (test agent): short final check of the round-5c pin-drag refit (travel model with head pitch, tighter x4
rule, no plate hover during a drag) with REAL injected pinch-drags (r5bqa.run -> r5t.run_path -> r5drag.ps1: PostMessage
to the PICO_6.1_Ruler qemu window only, Stopwatch-timed glides) and REAL primed pinches (r3t.pc, catcher on).
Debug broadcasts only for setup and read-back (clear, place, connect, tap_pin, ui coach_got_it, fps, dump, pose, bounds,
rig, coach reset). Same drag profiles as round 5b: 250 px = 12 x 60 ms + 2.0 s hold, 150 px = 10 x 30 ms + 1.5 s hold.
usage: python r5cqa.py level | rot88 | pitched | gain | plates | fps | reg1 | rot0 | reg2 | relaunch | final
Outputs: tools/test/out/round5c/ (r5c_runs.jsonl, drags.jsonl, *.json), shots .scratch/shots/round5cqa/ (<= 5)."""
import sys, os, time, json, math, re, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r5bqa, r5t, r3t, r2
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round5c")
EVID = os.path.join(OUT, "evidence")
FINAL = os.path.join(rt.ROOT, ".scratch", "shots", "round5cqa")
for _d in (OUT, EVID, FINAL):
    os.makedirs(_d, exist_ok=True)
r5bqa.OUT = OUT; r5bqa.SH = EVID; r5bqa.FINAL = FINAL; r5bqa.RUNS = os.path.join(OUT, "r5c_runs.jsonl")
r5t.OUT = OUT; r5t.SH = EVID; r5t.TMPSHOTS = os.path.join(OUT, "_drag_shots"); os.makedirs(r5t.TMPSHOTS, exist_ok=True)
r3t.OUT = OUT; r3t.SH = EVID; r3t.CACHE = os.path.join(OUT, "pxcache.json")
r3t.CATCH = r5t.CATCH; r2.CATCH = r5t.CATCH
SUM = os.path.join(OUT, "summary.jsonl")

# start points (Stage metres). Level start pose: bed top / floor right of the bed. Work pose yaw 88, pitch -38: floor.
PTS = {
    "LA_side": [0.008, 0.866, -1.293],    # bed top 1.51 m, room for 250 px to the right
    "LA_away": [0.048, 0.866, -1.293],    # bed top 1.51 m, room for 250 px up
    "LB_side": [0.942, 0.4006, -1.958],   # floor 2.52 m, right
    "LB_away": [0.967, 0.4006, -1.785],   # floor 2.40 m, up
    "PA_side": [-0.81, 0.4006, -0.20],    # pitched: floor 1.5 m, right
    "PA_away": [-0.76, 0.4006, -0.34],    # pitched: floor 1.5 m, up
    "PB": [-2.13, 0.4006, 0.38],          # pitched: floor 2.5 m, right and up
}


def save(kind, **kw):
    with open(SUM, "a", encoding="utf-8") as f:
        f.write(json.dumps(dict(kind=kind, t=time.strftime("%H:%M:%S"), **kw), default=str) + "\n")


def run_fps(o):
    """Compositor FPS (PxrMetric) from 1 s before the press to 2.5 s after the last glide: a run is valid when all >= 30."""
    t0 = o.get("t_press")
    if t0 is None:
        return []
    t1 = t0 + (o["glides"][-1]["t1"] if o.get("glides") else 0) + 2.5
    out = rt.adb("logcat", "-d", "-v", "time", "-t", "3000", "-s", "PxrMetric:I")
    f = []
    for ln in out.splitlines():
        mm = re.match(r"\d\d-\d\d (\d\d):(\d\d):(\d\d\.\d+).*FPS=(\d+)", ln)
        if mm:
            t = int(mm.group(1)) * 3600 + int(mm.group(2)) * 60 + float(mm.group(3))
            if t0 - 1.0 <= t <= t1:
                f.append(int(mm.group(4)))
    return f


def fast_now():
    """Open a fresh 60 fps period and let the gaze settle before the next real drag (avoids the stale-gaze press)."""
    for _ in range(2):
        if "fast" in rt.emu("waitfast", "1"):
            break
    time.sleep(0.3)


def fresh(P):
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.5)
    pid, lines = r5bqa.place(P)
    r5bqa.deselect(); time.sleep(0.3)
    return pid


def segs_for(L, dirn):
    dx, dy = {"side": (L, 0), "left": (-L, 0), "away": (0, -L)}[dirn]
    if L >= 250:
        return [("m", dx, dy, 12, 60), ("w", 2000)]
    return [("m", dx, dy, 10, 30), ("w", 1500)]


def brief_run(o):
    return (f"{o['name']:22s} mode={o.get('mode')} d={o.get('dist_start')}->{o.get('dist_end')} cursor={o.get('cursor_px')} pin={o.get('pin_px')} "
            f"err={o.get('err_px')} (along {o.get('err_along')}, across {o.get('err_across')}) trail={o.get('trail_max_px')} "
            f"settle8={o.get('settle_8px_s')} gains={o.get('gains')}")


def gain_vs_motion(o, p):
    """Every gain switch against the host glides and the raw drag events around it."""
    tp = o.get("t_press") or 0.0
    glides = o.get("glides") or []
    dm = p["dmoves"]
    res = []
    for (t, g, sp, why) in o.get("gains") or []:
        inside = any(gl["t0"] - 0.02 <= t <= gl["t1"] + 0.02 for gl in glides)
        after_end = min([t - gl["t1"] for gl in glides if t > gl["t1"]], default=None)
        # raw hand motion of the drag events in the 0.5 s after the switch (m) and the last event before it
        nxt = [d for d in dm if 0 < d["t"] - tp - t <= 0.5]
        prev = [d for d in dm if d["t"] - tp <= t]
        raw_after = 0.0
        last = prev[-1]["hand"] if prev else None
        for d in nxt:
            if last is not None:
                raw_after += rt.dist(d["hand"], last)
            last = d["hand"]
        res.append(dict(t=t, gain=g, speed=sp, reason=why, inside_glide=inside, s_after_glide_end=round(after_end, 3) if after_end is not None else None,
                        raw_hand_next_0_5s_mm=round(1000 * raw_after, 1)))
    return res


def target_at(rows, t):
    if not rows:
        return None
    if t <= rows[0]["t"]:
        return rows[0]["target"]
    for a, b in zip(rows, rows[1:]):
        if a["t"] <= t <= b["t"]:
            f = 0 if b["t"] <= a["t"] else (t - a["t"]) / (b["t"] - a["t"])
            return (a["target"][0] + (b["target"][0] - a["target"][0]) * f, a["target"][1] + (b["target"][1] - a["target"][1]) * f)
    return rows[-1]["target"]


def drag_case(name, key, L, dirn):
    pid = fresh(PTS[key])
    o, raw, p = r5bqa.run(pid, segs_for(L, dirn), name)
    gv = gain_vs_motion(o, p)
    save("drag", name=name, key=key, L=L, dirn=dirn, err=o.get("err_px"), along=o.get("err_along"), across=o.get("err_across"),
         d0=o.get("dist_start"), d1=o.get("dist_end"), cursor=o.get("cursor_px"), pin=o.get("pin_px"), trail=o.get("trail_max_px"),
         settle8=o.get("settle_8px_s"), gains=o.get("gains"), gain_vs_motion=gv, mode=o.get("mode"), release=o.get("release"), hp=o.get("hp"))
    print("   ", brief_run(o))
    for g in gv:
        print("      gain:", g)
    return o, p


def shot_to(name, final_name=None):
    path = r3t.shot(name)
    if path and final_name:
        dst = os.path.join(FINAL, final_name + ".png"); shutil.copyfile(path, dst); return dst
    return path


def plate_luma(png, b, hp):
    """Median luminance of every visible plate's panel rectangle (centre 60 % of it) in a console screenshot."""
    from PIL import Image
    im = Image.open(png).convert("L")
    res = {}
    for pid, p in b.items():
        if not pid.startswith("plate") or p["pos"] is None:
            continue
        pts = [rt.project(rt.panel_point(p, cx, cy), hp) for cx, cy in ((p["w"] * 0.2, p["h"] * 0.2), (p["w"] * 0.8, p["h"] * 0.8))]
        if None in pts:
            continue
        x0, x1 = sorted([int(pts[0][0]), int(pts[1][0])]); y0, y1 = sorted([int(pts[0][1]), int(pts[1][1])])
        vals = sorted(im.crop((x0, y0, max(x1, x0 + 1), max(y1, y0 + 1))).getdata())
        name = next((n for n in p["ctrls"] if n.startswith("plate:")), "?")
        res[pid] = dict(tape=name, box=(x0, y0, x1, y1), median=vals[len(vals) // 2], p25=vals[len(vals) // 4], p75=vals[3 * len(vals) // 4])
    return res


which = sys.argv[1:]

if "coach_off" in which:
    print(r5bqa.c("ui", None, "coach_got_it", pat=r"COACH|UI", settle=0.4))

if "level" in which:
    print(">> level start pose:", rt.pose()["line"][:90])
    drag_case("lvl_250_bedA_side", "LA_side", 250, "side")
    drag_case("lvl_250_floorB_away", "LB_away", 250, "away")
    drag_case("lvl_150_bedA_away", "LA_away", 150, "away")
    drag_case("lvl_150_floorB_side", "LB_side", 150, "side")

if "rot88" in which:
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.5)
    r3t.rot(88, -38)

if "rot0" in which:
    r3t.rot(0, 0)

if "pitched" in which:
    print(">> work pose:", rt.pose()["line"][:90])
    drag_case("p_250_A_side", "PA_side", 250, "side")
    drag_case("p_250_A_away", "PA_away", 250, "away")
    drag_case("p_250_B_side", "PB", 250, "side")
    drag_case("p_250_B_away", "PB", 250, "away")
    drag_case("p_150_A_side", "PA_side", 150, "side")
    drag_case("p_150_A_away", "PA_away", 150, "away")
    drag_case("p_150_B_side", "PB", 150, "side")
    drag_case("p_150_B_away", "PB", 150, "away")

if "gain" in which:
    for i in (1, 2):
        pid = fresh(PTS["PB"])
        name = f"g_pause_quick_{i}"
        o, raw, p = r5bqa.run(pid, [("m", 150, 0, 6, 60), ("w", 1000), ("m", 250, 0, 4, 60), ("w", 1500)], name)
        gl = o["glides"]
        q0 = gl[1]["t0"] if len(gl) > 1 else None
        a = target_at(o["rows"], q0) if q0 is not None else None
        bb = target_at(o["rows"], q0 + 0.5) if q0 is not None else None
        moved = round(bb[0] - a[0], 1) if a and bb else None
        gv = gain_vs_motion(o, p)
        save("pause_quick", name=name, moved_in_0_5s=moved, quick_t0=q0, glides=gl, gains=o.get("gains"), gain_vs_motion=gv, err=o.get("err_px"),
             along=o.get("err_along"), rows=o["rows"])
        print("   ", brief_run(o)); print(f"    quick 100 px: pin moved {moved} px in 0.5 s (target px {a} -> {bb})")
        for g in gv: print("      gain:", g)
    # continuous slow-moderate move: 40 px fast, then 160 px at ~2 deg/s (44 x 60 ms), hold 2.5 s
    pid = fresh(PTS["PB"])
    o, raw, p = r5bqa.run(pid, [("m", 40, 0, 4, 60), ("m", 160, 0, 44, 60), ("w", 2500)], "g_continuous_2dps")
    gv = gain_vs_motion(o, p)
    dm = [(round(d["t"] - o["t_press"], 3), d["n"]) for d in p["dmoves"]]
    save("continuous", name="g_continuous_2dps", glides=o["glides"], gains=o.get("gains"), gain_vs_motion=gv, err=o.get("err_px"), along=o.get("err_along"),
         dmove_times=dm, rows=o["rows"])
    print("   ", brief_run(o)); print("    glides:", o["glides"])
    for g in gv: print("      gain:", g)
    gaps = [round(b[0] - a[0], 3) for a, b in zip(dm, dm[1:])]
    print("    drag-event gaps (s):", gaps)

if "plates" in which:
    B = [-2.06, 0.4006, 0.67]; B2 = [-1.71, 0.4006, 0.67]; B3 = [-2.36, 0.4006, 0.40]
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.6)
    pb, _ = r5bqa.place(B); r5bqa.deselect()
    pb2, _ = r5bqa.place(B2); r5bqa.deselect()
    pb3, _ = r5bqa.place(B3); r5bqa.deselect()
    r5bqa.c("connect", None, f"{pb} {pb2}", pat="TAPE add"); r5bqa.c("connect", None, f"{pb} {pb3}", pat="TAPE add")
    time.sleep(0.6); r5bqa.deselect(); time.sleep(0.6)
    d0 = rt.dump(); print(rt.brief(d0))
    hp = rt.pose()
    midb = {}

    def mid_bounds():
        b = rt.bounds(); midb["b"] = b
        return {k: (v["w"], v["h"], sorted(v["ctrls"])) for k, v in b.items()}
    fast_now()
    o, raw, p = r5bqa.run(pb, [("m", -150, 0, 10, 30), ("w", 1000), ("s", "r5c_plates_mid"), ("w", 1600)], "plates_two_tapes_b", mid=(1.6, mid_bounds))
    print("    fps:", run_fps(o))
    print("   ", brief_run(o)); print("    mid bounds:", o.get("mid"))
    mid_png = next((s for s in o.get("shots") or [] if s), None)
    time.sleep(1.0)
    b_after = rt.bounds(); d1 = rt.dump(); print(rt.brief(d1))
    after_png = shot_to("r5c_plates_after")
    res = dict(pins=dict(B=pb, B2=pb2, B3=pb3), err=o.get("err_px"), mid_bounds=o.get("mid"), lines=[l for l in o.get("other") or []])
    if mid_png and "b" in midb:
        res["mid_luma"] = plate_luma(mid_png, midb["b"], hp); shutil.copyfile(mid_png, os.path.join(FINAL, "r5cqa_02_mid_drag_two_tapes.png"))
    if after_png:
        res["after_luma"] = plate_luma(after_png, b_after, hp); shutil.copyfile(after_png, os.path.join(FINAL, "r5cqa_03_after_release_two_tapes.png"))
    res["after_state"] = dict(state=d1["state"], armed=d1["armed"], tapes=[(t["id"], t["a"], t["b"], t["text"]) for t in d1["tapes"]])
    print(json.dumps({k: v for k, v in res.items() if k in ("mid_luma", "after_luma", "after_state")}, indent=1))
    save("plates", **res)

if "fps" in which:
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.8)
    pts = [(x, z) for x in (-1.5, -1.8, -2.1) for z in (0.75, 0.45, 0.15)]
    ids = []
    for (x, z) in pts:   # place while the previous pin is armed: a chain of 8 tapes
        pid, _ = r5bqa.place([x, 0.4006, z]); ids.append(pid)
    time.sleep(0.6); r5bqa.deselect()
    d = rt.dump(); print(rt.brief(d))
    mid = ids[4]
    r5bqa.c("fps", None, "on", pat="FPS", settle=0.2)
    o, raw, p = r5bqa.run(mid, [("m", -120, 0, 20, 40), ("m", -40, 0, 20, 40), ("w", 1500)], "fps_8tapes")
    r5bqa.c("fps", None, "off", settle=0.2)
    allr = rt.adb("logcat", "-d", "-v", "time", "-t", "6000", "-s", "SpatialRuler:V", "PxrMetric:I")
    seg = []; on = False
    for ln in allr.splitlines():
        if "DRAG start target=pin:" + mid in ln: on = True; seg = []
        if on and ("FPS app=" in ln or "PxrMetric" in ln): seg.append(ln.strip()[6:140])
        if on and "DRAG end" in ln: on = False; seg.append(ln.strip()[6:90])
    print("   ", brief_run(o)); print("    fps during the drag:"); [print("      ", s) for s in seg]
    rig = r5bqa.c("rig", pat="RIG summary", settle=0.3)
    rs = [l for l in rig if l.startswith(("RIG summary", "RIG violation"))]
    print("   ", rs)
    save("fps", tapes=len(d["tapes"]), pins=len(d["pins"]), fps_lines=seg, rig=rs, err=o.get("err_px"))

if "gain2" in which:
    for i in (3, 4):
        pid = fresh(PTS["PB"]); fast_now()
        name = f"g_pause_quick_{i}"
        o, raw, p = r5bqa.run(pid, [("m", 150, 0, 6, 60), ("w", 1000), ("m", 250, 0, 4, 60), ("w", 1500)], name)
        f = run_fps(o)
        gl = o["glides"]
        q0 = gl[1]["t0"] if len(gl) > 1 else None
        a = target_at(o["rows"], q0) if q0 is not None else None
        bb = target_at(o["rows"], q0 + 0.5) if q0 is not None else None
        moved = round(bb[0] - a[0], 1) if a and bb else None
        gv = gain_vs_motion(o, p)
        save("pause_quick", name=name, fps=f, moved_in_0_5s=moved, quick_t0=q0, glides=gl, gains=o.get("gains"), gain_vs_motion=gv, err=o.get("err_px"),
             along=o.get("err_along"), rows=o["rows"])
        print("   ", brief_run(o)); print(f"    fps={f} quick 100 px: pin moved {moved} px in 0.5 s (target px {a} -> {bb})")
        for g in gv: print("      gain:", g)

if "fps8" in which:
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.8)
    pts = [(x, z) for x in (-1.5, -1.8, -2.1) for z in (0.75, 0.45, 0.15)]
    ids = []
    for (x, z) in pts:
        pid, _ = r5bqa.place([x, 0.4006, z]); ids.append(pid)
        r5bqa.deselect()
    for a_, b_ in zip(ids, ids[1:]):
        r5bqa.c("connect", None, f"{a_} {b_}", pat="TAPE add|CMD", settle=0.2)
    time.sleep(0.6); r5bqa.deselect()
    d = rt.dump(); print(rt.brief(d))
    mid = ids[4]
    r5bqa.c("fps", None, "on", pat="FPS", settle=0.2)
    fast_now()
    o, raw, p = r5bqa.run(mid, [("m", -120, 0, 20, 40), ("w", 1500)], "fps_8tapes_b")
    r5bqa.c("fps", None, "off", settle=0.2)
    f = run_fps(o)
    app = [rt.msg(l) for l in raw if "FPS app=" in l]
    print("   ", brief_run(o)); print("    compositor fps:", f, " app:", app)
    rig = r5bqa.c("rig", pat="RIG summary", settle=0.3)
    rs = [l for l in rig if l.startswith(("RIG summary", "RIG violation"))]
    print("   ", rs)
    save("fps8", tapes=len(d["tapes"]), pins=len(d["pins"]), compositor=f, app=app, rig=rs, err=o.get("err_px"), dstart=o.get("dstart"))

if "reg1" in which:
    # pitched pose: tap-tap tape, drag that pin, undo (exact), tape strip retract -- real primed pinches / real drag
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.8)
    for l in r5t.catcher(True): pass
    print(">> tap-tap tape (real)")
    lines = r3t.pc(["floor:-1.35,0.05", "floor:-1.65,-0.15", "gap=700", "exp=TAPE add"])
    d = rt.dump(); print(rt.brief(d))
    tape = d["tapes"][-1] if d["tapes"] else None
    res = dict(taptap=[l for l in lines if l.startswith(("PIN add", "TAPE add", "STATE", "TAP "))])
    if tape:
        pid = tape["b"]
        r5bqa.deselect(); time.sleep(0.4)
        before = rt.pin_by_id(rt.dump(), pid)["pos"]
        print(">> drag that pin (real)")
        fast_now()
        o, raw, p = r5bqa.run(pid, [("m", 0, -120, 10, 30), ("w", 1500)], "reg_drag")
        print("   ", brief_run(o), "fps", run_fps(o))
        time.sleep(0.8)
        after = rt.pin_by_id(rt.dump(), pid)["pos"]
        print(">> undo (real pinch on the body key)")
        lines = r3t.pc(["ctl:body:undo", "exp=UNDO|ANCHOR rebase id"])
        time.sleep(1.5)
        back = rt.pin_by_id(rt.dump(), pid)["pos"]
        off = 1000 * rt.dist(before, back)
        print(f"    undo: before {before} after-drag {after} after-undo {back} off_mm={off:.2f}")
        res.update(drag=dict(pin=pid, before=before, after=after, back=back, undo_off_mm=round(off, 3), moved_mm=round(1000 * rt.dist(before, after), 1),
                             err=o.get("err_px"), undo_lines=[l for l in lines if l.startswith(("UNDO", "ANCHOR", "UI "))]))
        print(">> tape strip retract (real)")
        d = rt.dump(); r5bqa.deselect(); time.sleep(0.6)
        t = d["tapes"][0]["id"]
        l1 = r3t.pc([f"plate:{t}", "exp=STATE tape-selected"])
        time.sleep(1.2)
        l2 = r3t.pc(["ctl:tapeTag:tape_retract", "exp=TAPE remove"])
        res["retract"] = [l for l in l1 + l2 if l.startswith(("UI ", "STATE", "TAPE remove"))]
        d = rt.dump(); print(rt.brief(d))
    save("reg1", **res)

if "reg2" in which:
    # start pose: units, zh and back, clear all, exit -- real primed pinches
    for l in r5t.catcher(True): pass
    res = {}
    print(">> units (real)")
    l = r3t.pc(["ctl:body:unit_mm", "ctl:body:unit_cm", "gap=900", "exp=UNIT cm"]); res["units"] = [x for x in l if x.startswith(("UI ", "UNIT"))]
    print(">> language zh and back (real)")
    l1 = r3t.pc(["ctl:body:more", "exp=STATE more-menu"]); time.sleep(1.2)
    l2 = r3t.pc(["ctl:menuPanel:lang_zh", "exp=LANG zh"]); time.sleep(1.5)
    l3 = r3t.pc(["ctl:menuPanel:lang_system", "exp=LANG system"]); time.sleep(0.8)
    res["lang"] = [x for x in l1 + l2 + l3 if x.startswith(("UI ", "LANG", "STATE"))]
    print(">> clear all (real)")
    l4 = r3t.pc(["ctl:menuPanel:menu_clear", "exp=STATE confirm-clear"]); time.sleep(1.2)
    l5 = r3t.pc(["ctl:dialogPanel:dialog_confirm", "exp=CLEAR"]); time.sleep(1.0)
    res["clear"] = [x for x in l4 + l5 if x.startswith(("UI ", "CLEAR", "STATE"))]
    d = rt.dump(); print(rt.brief(d))
    print(">> exit (real)")
    l6 = r3t.pc(["ctl:body:more", "exp=STATE more-menu"]); time.sleep(1.2)
    l7 = r3t.pc(["ctl:menuPanel:menu_exit", "exp=STATE exited|STAGE"]); time.sleep(1.0)
    res["exit"] = [x for x in l6 + l7 if x.startswith(("UI ", "STATE", "STAGE"))]
    save("reg2", **res)

if "relaunch" in which:
    # after a real Exit the process stays alive: start the launcher activity again (no force-stop)
    m = rt.now()
    print(rt.adb("shell", "am start -n io.embodit.spatialruler/.platform.LaunchActivity 2>&1 || true")[-200:])
    hit = rt.wait(r"RESTORE pins=", m, 90); print("   ", [rt.msg(h) for h in hit])
    rt.wait(r"SCENE attached", m, 90); time.sleep(2.0)
    d1 = rt.dump(); print(rt.brief(d1))
    save("relaunch", restore=[rt.msg(h) for h in hit], state=d1["state"], pins=len(d1["pins"]), tapes=len(d1["tapes"]), unit=d1["unit"], lang=d1["lang"])
