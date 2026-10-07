#!/usr/bin/env python
"""Round 3, controller mode (emu_mode.ps1 -Mode right): placements at the real-cursor pixel (H1), pitch changed by one
right-button drag between them; reticle vs commit; controller-mode frame with the live blade. usage: r3_ctrl.py PITCH_STEPS..."""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r3t
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
CUR = (1296, 1990)
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round3", "ctrl.jsonl")
steps = [float(v) for v in sys.argv[1:]] or [0.0, 10.0, 8.0]
for i, dp in enumerate(steps):
    if dp:
        sy = -dp / 0.044
        rt.emu("drag", 1080, int(1079 - sy / 2), 1080, int(1079 + sy / 2), "-Button", "right", "-Steps", 10)
        time.sleep(0.5)
    hp = rt.pose2()
    m0 = rt.now()
    print(f"== placement {i + 1}: pitch {math.degrees(hp['pitch']):.1f}  aim {rt.emu('move', *CUR)[-30:]}")
    time.sleep(1.4)
    ret = [rt.msg(l) for l in rt.logs(m0) if "RETICLE" in l]
    print("   reticle before:", ret[-1][:170] if ret else "none")
    if i == 1:
        r3t.shot("r3_09_controller_mode_live_blade", (700, 1350, 1900, 2158), 0.8, settle=0.6)
    m = rt.now(); out = rt.emu("click", *CUR)
    rt.wait(r"PIN add|AIM commit|TAPE add", m, 10); time.sleep(1.0)
    ls = [rt.msg(l) for l in rt.logs(m)]
    for l in ls:
        if re.match(r"(TAP|PIN add|TAPE add|AIM|STATE|RETICLE)", l): print("    ", l[:200])
    tap = next((l for l in ls if l.startswith("TAP pointer")), "")
    pre = [l for l in ls if l.startswith("RETICLE")]
    rpos = re.search(r"stage=\(([^)]*)\)", (pre[-1] if pre else (ret[-1] if ret else "")))
    tpos = re.search(r"stage=\(([^)]*)\)", tap)
    if rpos and tpos:
        dmm = 1000 * rt.dist(rt.vec(rpos.group(1)), rt.vec(tpos.group(1)))
        print(f"   commit vs last reticle: {dmm:.1f} mm")
    with open(OUT, "a") as f: f.write(json.dumps(dict(i=i, pose=hp["line"], reticle=ret[-1] if ret else None, lines=ls)) + "\n")
