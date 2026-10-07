#!/usr/bin/env python
"""Round 2, test D: controller-mode placements (emu_mode.ps1 -Mode right). For each console pixel: aim (harness move),
let the reticle settle, read the RETICLE line, click (= trigger), then compare the pin with the camera-model ray from
the left eye through the clicked pixel on the logged plane. Appends to tools/test/out/round2/ctrl_acc.json.
usage: r2_ctrl.py X1 Y1 [X2 Y2 ...] [--label L]"""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.argv = [sys.argv[0]] + sys.argv[1:]
import rt
import r2
sys.stdout.reconfigure(encoding="utf-8")
args = sys.argv[1:]
label = ""
if "--label" in args:
    i = args.index("--label"); label = args[i + 1]; del args[i:i + 2]
pts = [(float(args[i]), float(args[i + 1])) for i in range(0, len(args) - 1, 2)]
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round2", "ctrl_acc.json")
pls = rt.planes()
data = json.load(open(OUT)) if os.path.exists(OUT) else []
for (x, y) in pts:
    hp = rt.pose()
    o, d = rt.ray(x, y, hp)
    fh = rt.first_hit(o, d, pls)
    m0 = rt.now()
    print(" aim:", rt.emu("move", int(round(x)), int(round(y)))[-40:])
    time.sleep(1.2)
    ret = [rt.msg(l) for l in rt.logs(m0) if "RETICLE" in l]
    m, lines, hit = r2.sclick(x, y, pat=r"PIN add|AIM commit|TAP pin|UI ", timeout=10, extra_wait=1.0)
    tapp = next((l for l in lines if l.startswith("TAP pointer")), "")
    pin = next((l for l in lines if l.startswith("PIN add")), "")
    ign = [l for l in lines if "ignored" in l or l.startswith("AIM commit")]
    rec = dict(label=label, px=[x, y], pose=hp["line"], reticle=ret[-1] if ret else None, tap=tapp, pin=pin, other=ign, t=m)
    print(f"--- px ({x:.0f},{y:.0f}) predicted {fh[2]['label'] if fh else None} {[round(c, 4) for c in fh[1]] if fh else ''}")
    print("    reticle:", (ret[-1] if ret else "none")[:170])
    for l in lines:
        if re.match(r"(TAP|PIN add|TAPE add|AIM|STATE|SNAP|FEEDBACK)", l): print("    ", l[:200])
    pm = re.search(r"pos=\(([^)]*)\) plane=(\S+) grade=(\S+) snapped=(\S+) range_m=([\d.]+)", pin)
    if pm and fh:
        pp = rt.vec(pm.group(1))
        plane = next(p for p in pls if p["label"] == pm.group(2))
        hitp = rt.ray_plane(o, d, plane, inside=False)
        model = hitp[1] if hitp else fh[1]
        pr = rt.project(pp, hp)
        rec.update(id=re.search(r"id=(\S+)", pin).group(1), plane=pm.group(2), grade=pm.group(3), snapped=pm.group(4), range_m=float(pm.group(5)),
                   err_mm=1000 * rt.dist(pp, model), reproj=[pr[0] - x, pr[1] - y], mm_per_px=1000 * float(pm.group(5)) / rt.FX)
        print(f"    => {rec['id']} {rec['plane']} grade {rec['grade']} snap {rec['snapped']} range {rec['range_m']:.3f} m | pin vs eye ray: {rec['err_mm']:.1f} mm | reproj ({rec['reproj'][0]:+.1f},{rec['reproj'][1]:+.1f}) px | 1 px = {rec['mm_per_px']:.1f} mm")
    data.append(rec)
json.dump(data, open(OUT, "w"), indent=1)
if r2.HIJACKS: print("hijacks:", len(r2.HIJACKS))
