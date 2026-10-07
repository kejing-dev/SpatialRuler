#!/usr/bin/env python
"""L. Failure paths with real pinches: (1) anchor failure -> unsaved pin + Retry snackbar -> Retry -> saved;
(2) a pinch where no plane lies on the ray -> backstop -> 'no surface' feedback, no pin."""
import sys, os, re, time, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
rt.cmd("clear"); time.sleep(1.0); rt.cmd("unit", s="mm"); time.sleep(0.5)
show = lambda m: [rt.msg(l)[:170] for l in rt.logs(m) if re.search(r"TAP |PIN |TAPE |STATE|ANCHOR|SNACK|UI |PTR .*Press|OVERLAY", l)]
# (1)
m = rt.cmd("fail_next_anchor"); time.sleep(0.5); print("fail_next_anchor:", [rt.msg(l)[:100] for l in rt.logs(m)])
m, out = rt.click(1300, 1900)
rt.wait(r"SNACK|ANCHOR", m, 8)
t0 = time.time()
b = rt.bounds(); hp = rt.pose2()
print("after tap:", show(m))
print("panels:", {k: list(v["ctrls"]) for k, v in b.items() if k != "body"})
print(rt.emu("shot", "l01_unsaved_snack")[-30:], f"{time.time()-t0:.1f}s")
if "snackPanel" in b and "snackbar_action" in b["snackPanel"]["ctrls"]:
    x, y = rt.ctrl_px(b, hp, "snackPanel", "snackbar_action")
    m2, out = rt.click(x, y); print(f"Retry pinch at ({x:.0f},{y:.0f}) {time.time()-t0:.1f}s after bounds")
    rt.wait(r"ANCHOR saved|TAP ", m2, 8); time.sleep(1.0); print("after Retry:", show(m2))
    print(rt.emu("shot", "l02_after_retry")[-30:])
else:
    print("snackbar action not visible in bounds")
d = rt.dump(); print(rt.brief(d)); print([(p["id"], p["save"], p["grade"], p["tol_mm"]) for p in d["pins"]])
# (2) pixel whose ray meets no plane
hp = rt.pose2(); pls = rt.planes()
cands = []
for py in range(120, 1400, 40):
    for px in range(120, 2080, 40):
        o, dd = rt.ray(px, py, hp)
        if rt.first_hit(o, dd, pls) is None:
            # keep a margin: neighbours must be free too
            if all(rt.first_hit(*rt.ray(px + ax, py + ay, hp), pls) is None for ax, ay in ((-25, 0), (25, 0), (0, -25), (0, 25))):
                cands.append((px, py))
print("pixels with no plane on the ray:", len(cands), cands[:12])
if cands:
    px, py = cands[len(cands) // 2]
    before = len(rt.dump()["pins"])
    m3, out = rt.click(px, py); rt.wait(r"TAP |SNACK", m3, 8)
    for i in range(2): print(rt.emu("shot", f"l03_no_surface_{i}")[-30:])
    time.sleep(0.8); print(f"pinch at ({px},{py}) where no plane lies:", show(m3))
    d = rt.dump(); print("pins before/after:", before, len(d["pins"]), "state", d["state"], "snack", d["snack"])
