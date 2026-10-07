"""Round-5b QA items 6 + 7: fps during a real drag with 8 tapes, then the condensed regression with REAL primed pinches
(r3t.pc: catcher click first, then the targets, one 60 fps period) and real drags. Setup / read-back by debug broadcasts.
usage: python r5bqa_reg.py fps | flows | relaunch | final"""
import sys, os, time, json, re, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r5bqa, r5t, r3t, r2
rt.check()
r3t.CATCH = r5t.CATCH
r2.CATCH = r5t.CATCH
OUT = r5bqa.OUT
which = sys.argv[1:]
IDS = os.path.join(OUT, "reg_ids.json")


def pc(*targets, exp=None, gap="300"):
    words = list(targets) + [f"gap={gap}"] + ([f"exp={exp}"] if exp else [])
    return r3t.pc(words)


def brief():
    d = rt.dump(); print("   ", rt.brief(d).replace("\n", "\n    ")); return d


if "fps" in which:
    r5bqa.c("clear", pat="CLEAR|STATE", settle=0.8)
    pts = [(x, z) for x in (-1.5, -1.8, -2.1) for z in (0.75, 0.45, 0.15)]
    ids = []
    for (x, z) in pts:   # place while the previous pin is armed: tap - tap, a chain of 8 tapes
        pid, _ = r5bqa.place([x, 0.4006, z]); ids.append(pid)
    time.sleep(0.6); r5bqa.deselect()
    d = brief()
    json.dump(dict(pins=ids), open(IDS, "w"))
    mid = ids[4]
    r5bqa.c("fps", None, "on", pat="FPS", settle=0.2)
    out, raw, p = r5bqa.run(mid, [("m", -100, 0, 20, 40), ("m", 0, 0, 20, 40), ("s", "r7_fps_8tapes_mid"), ("w", 1200)], "r7_fps_8tapes")
    m = rt.now(); time.sleep(0.2)
    lines = [rt.msg(l) for l in rt.logs(rt.now())]
    r5bqa.c("fps", None, "off", settle=0.2)
    # FPS lines between DRAG start and DRAG end (device times from the drag record)
    allr = rt.adb("logcat", "-d", "-v", "time", "-t", "4000", "-s", "SpatialRuler:V", "PxrMetric:I")
    seg = []
    on = False
    for ln in allr.splitlines():
        if "DRAG start target=pin:" + mid in ln: on = True; seg = []
        if on and ("FPS app=" in ln or "PxrMetric" in ln): seg.append(ln.strip()[6:120])
        if on and "DRAG end mode=pin" in ln: on = False; seg.append(ln.strip()[6:80])
    print("   fps during the drag:"); [print("     ", s) for s in seg]
    rig = r5bqa.c("rig", pat="RIG summary", settle=0.3)
    print("   ", [l for l in rig if l.startswith(("RIG summary", "RIG violation"))])
    # R5-6-free undo check: real pinch on the body's undo key restores the dragged pin exactly
    before = rt.pin_by_id(d, mid)["pos"]
    after_drag = rt.pin_by_id(rt.dump(), mid)["pos"]
    lines = pc("ctl:body:undo", exp=r"UNDO|ANCHOR rebase id")
    time.sleep(1.5)
    back = rt.pin_by_id(rt.dump(), mid)["pos"]
    print(f"   undo after drag: before {before} after-drag {after_drag} after-undo {back} off_mm={1000 * rt.dist(before, back):.2f}")
    r5bqa.record("undo_after_drag", pin=mid, before=before, after_drag=after_drag, after_undo=back, off_mm=1000 * rt.dist(before, back))

if "flows" in which:
    ids = json.load(open(IDS))["pins"]
    for l in r5t.catcher(True): pass
    print(">> tap-tap tape (real)")
    pc("floor:-1.35,0.05", "floor:-1.65,-0.15", exp=r"TAPE add", gap="700")
    d = brief()
    print(">> select pin -> chip -> tape (real)")
    pc(f"pin:{ids[0]}", exp=r"STATE pin-selected")
    time.sleep(1.2)
    b = rt.bounds()
    chips = [(pid, name) for pid, v in b.items() for name in v["ctrls"] if name.startswith("chip:")]
    print("    chips:", chips)
    target = next((n for _, n in chips if n != f"chip:{ids[1]}"), None)
    if target:
        pc(target, exp=r"TAPE add")
    print(">> steppers once each (real)")
    pc(f"pin:{ids[2]}", exp=r"STATE pin-selected")
    time.sleep(1.2)
    pc("ctl:pinTag:step_u_plus", "ctl:pinTag:step_u_minus", "ctl:pinTag:step_v_plus", "ctl:pinTag:step_v_minus", exp=r"PIN nudge", gap="900")
    time.sleep(1.0)
    print(">> tape strip retract (real)")
    d = rt.dump(); r5bqa.deselect(); time.sleep(0.6)
    t = d["tapes"][0]["id"]
    pc(f"plate:{t}", exp=r"STATE tape-selected")
    time.sleep(1.2)
    pc("ctl:tapeTag:tape_retract", exp=r"TAPE remove")
    d = brief()
    print(">> units x2 (real)")
    pc("ctl:body:unit_mm", "ctl:body:unit_cm", exp=r"UNIT cm", gap="900")
    print(">> language zh and back (real)")
    pc("ctl:body:more", exp=r"STATE more-menu")
    time.sleep(1.2)
    pc("ctl:menuPanel:lang_zh", exp=r"LANG zh")
    time.sleep(1.5)
    r4shot = r5bqa.SH
    pc("ctl:menuPanel:lang_system", exp=r"LANG system")
    time.sleep(0.8)
    pc("ctl:body:more", exp=r"STATE idle|STATE")
    d = brief()

if "relaunch" in which:
    d0 = rt.dump()
    json.dump(d0, open(os.path.join(OUT, "dump_before_relaunch.json"), "w"))
    m = rt.now()
    rt.adb("shell", "am force-stop io.embodit.spatialruler")
    time.sleep(1.0)
    print(rt.adb("shell", "am start -n io.embodit.spatialruler/.platform.LaunchActivity 2>&1 || true")[-200:])
    hit = rt.wait(r"RESTORE pins=", m, 90)
    print("   ", [rt.msg(h) for h in hit])
    rt.wait(r"SCENE attached", m, 90); time.sleep(2.0)
    d1 = rt.dump()
    worst = 0.0
    for p0 in d0["pins"]:
        p1 = rt.pin_by_id(d1, p0["id"])
        if p1 is None: print("   MISSING", p0["id"]); continue
    L0 = {t["id"]: t["len_m"] for t in d0["tapes"]}; L1 = {t["id"]: t["len_m"] for t in d1["tapes"]}
    dl = [abs(L0[k] - L1.get(k, -9)) * 1000 for k in L0]
    print(f"   pins {len(d0['pins'])} -> {len(d1['pins'])}, tapes {len(d0['tapes'])} -> {len(d1['tapes'])}, max tape length change {max(dl) if dl else 0:.2f} mm; unit={d1['unit']} lang={d1['lang']}")
    r5bqa.record("relaunch", pins=(len(d0["pins"]), len(d1["pins"])), tapes=(len(d0["tapes"]), len(d1["tapes"])), max_dlen_mm=max(dl) if dl else 0)
