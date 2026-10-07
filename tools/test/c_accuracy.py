#!/usr/bin/env python
"""C. Placement accuracy with real pinches. Targets are Stage points on detected planes; the pixel is computed from the
pose read just before each click. Results are appended to tools/test/out/c_taps.json.
usage: c_accuracy.py SET   (SET = name of a target list below)  |  c_accuracy.py px X Y LABEL"""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "c_taps.json")

SETS = {
    "shelf": [("niche cabinet top (far, snap off)", "CABINET:ef594c91", (-0.1196, 0.9285, -6.1158))],
    "snapoff": [("cabinet top (far, snap off)", "CABINET:5304833b", (0.6891, 0.9285, -5.9052)), ("ceiling (far, snap off)", "CEILING:b05f1120", (0.5, 3.6928, -5.0))],
    "floor_far": [("floor ~2.9 m", "FLOOR:dc233687", (0.30, 0.4006, -2.681)), ("floor ~3.16 m", "FLOOR:dc233687", (1.00, 0.4006, -2.80))],
    "bed": [("bed top", "BED:715584c8", (-1.798, 0.8656, -2.331)), ("bed platform", "BED:8e541a3d", (-1.085, 0.5821, -2.246))],
    "walls": [("white wall (far)", "WALL:7fb662a4", (-2.136, 2.6, -4.573)), ("teal wall (far)", "WALL:c90e55c3", (2.083, 1.2, -4.045))],
    "niche": [("niche back wall (far)", "WALL:1e600be8", (-0.2227, 2.1284, -6.3056)), ("cabinet top (far)", "CABINET:5304833b", (0.6891, 0.9285, -5.9052))],
}


def one(label, plane_label, target=None, pixel=None):
    hp = rt.pose2(); pls = rt.planes()
    if pixel is None:
        pr = rt.project(list(target), hp)
        if pr is None or not (40 < pr[0] < 2120 and 40 < pr[1] < 2118):
            print(f"SKIP {label}: target not in view {pr}"); return None
        x, y = round(pr[0]), round(pr[1])
    else:
        x, y = pixel
    o, d = rt.ray(x, y, hp)
    fh = rt.first_hit(o, d, pls)
    print(f"--- {label}: click ({x},{y}) predicted first hit {fh[2]['label'] if fh else None} range {fh[0] if fh else 0:.3f}")
    m, out = rt.click(x, y)
    hit = rt.wait(r"ANCHOR (saved|failed)|TAP (pin|other|backstop)|UI |PTR panel", m, 12); time.sleep(0.6)
    lines = [rt.msg(l) for l in rt.logs(m)]
    for l in lines: print("    ", l[:200])
    rec = dict(label=label, px=[x, y], pose=hp["line"], pose_unc=hp["unc"], lines=lines, time=m)
    tap = next((l for l in lines if l.startswith("TAP surface")), None)
    pin = next((l for l in lines if l.startswith("PIN add")), None)
    if tap and pin:
        tm = re.search(r"plane=(\S+) kind=(\S+) stage=\(([^)]*)\)", tap)
        pm = re.search(r"id=(\S+) pos=\(([^)]*)\) plane=(\S+) grade=(\S+) snapped=(\S+) range_m=([\d.]+)", pin)
        plane = next(p for p in pls if p["label"] == tm.group(1))
        tap_pt = rt.vec(tm.group(3)); pin_pt = rt.vec(pm.group(2))
        t, model_pt = rt.ray_plane(o, d, plane, inside=False)
        # the app's own conversion: collider hit put back onto the plane along the viewing ray (from the head)
        n = rt.plane_normal(plane)
        def back(origin):
            dd = rt.norm(rt.sub(tap_pt, origin)); den = rt.dot(dd, n)
            tt = rt.dot(rt.sub(plane["verts"][0], origin), n) / den
            return rt.add(origin, rt.mul(dd, tt))
        app_head = back(hp["pos"]); app_eye = back(hp["eye"])
        pr_pin = rt.project(pin_pt, hp)
        rec.update(id=pm.group(1), plane=tm.group(1), predicted_plane=fh[2]["label"] if fh else None, tap=tap_pt, pin=pin_pt, model=model_pt,
                   err_model_mm=1000 * rt.dist(pin_pt, model_pt), err_app_head_mm=1000 * rt.dist(pin_pt, app_head), err_app_eye_mm=1000 * rt.dist(pin_pt, app_eye),
                   off_plane_mm=1000 * rt.dot(rt.sub(pin_pt, plane["verts"][0]), n), range_m=float(pm.group(6)), grade=pm.group(4), snapped=pm.group(5),
                   reproj_px=[pr_pin[0] - x, pr_pin[1] - y], mm_per_px=1000 * float(pm.group(6)) / rt.FX,
                   saved=any(l.startswith("ANCHOR saved") for l in lines))
        print(f"    => {rec['id']} on {rec['plane']} range {rec['range_m']:.3f} m | pin vs camera-model ray: {rec['err_model_mm']:.1f} mm | pin vs app back-projection (head/eye): {rec['err_app_head_mm']:.2f}/{rec['err_app_eye_mm']:.2f} mm | off-plane {rec['off_plane_mm']:.2f} mm | reproj ({rec['reproj_px'][0]:+.1f},{rec['reproj_px'][1]:+.1f}) px | 1 px = {rec['mm_per_px']:.1f} mm")
    else:
        print("    => no surface pin from this tap")
    data = json.load(open(OUT, encoding="utf-8")) if os.path.exists(OUT) else []
    data.append(rec); json.dump(data, open(OUT, "w", encoding="utf-8"), indent=1)
    return rec


if sys.argv[1] == "px":
    one(sys.argv[4], None, pixel=(int(sys.argv[2]), int(sys.argv[3])))
else:
    for name in sys.argv[1:]:
        for (label, plane, target) in SETS[name]:
            one(label, plane, target=target)
print(rt.brief(rt.dump()))
