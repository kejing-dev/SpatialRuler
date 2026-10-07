#!/usr/bin/env python
"""K. Persistence: 3 pins + 2 tapes (one pin nudged), unit ft-in, language zh -> force-stop -> relaunch -> compare."""
import sys, os, re, time, json, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
show = lambda m: [rt.msg(l)[:150] for l in rt.logs(m) if re.search(r"TAP |PIN |TAPE |STATE|ANCHOR|SNACK|UI |UNIT|LANG", l)]
def tap(x, y, pat):
    m, out = rt.click(x, y); rt.wait(pat, m, 10); time.sleep(0.8); return show(m)
d = rt.dump()
if d["state"] != "pulling": sys.exit("expected the armed pin from test L")
print("B:", tap(1800, 1750, r"ANCHOR saved"))
print("C:", tap(1233, 1425, r"ANCHOR saved"))
d = rt.dump(); B = d["pins"][1]; hp = rt.pose2(); pr = rt.project(rt.add(B["pos"], rt.mul(B["normal"], 0.018)), hp)
print("connect C-B:", tap(pr[0], pr[1], r"TAPE add"))
for n, s in (("tap_pin", d["pins"][0]["id"]),): rt.cmd(n, s=s); time.sleep(0.5)
for n in ("pin_tune", "step_v_plus", "step_v_plus", "step_u_minus", "pin_close"): rt.cmd("ui", s=n); time.sleep(0.5)
b = rt.bounds(); hp = rt.pose2(); x, y = rt.ctrl_px(b, hp, "body", "unit_ftin")
print("unit ft-in (real pinch):", tap(x, y, r"UNIT"))
m = rt.cmd("lang", s="zh"); time.sleep(1.0); print("lang zh:", show(m))
time.sleep(1.0)
before = rt.dump(); print(rt.brief(before))
print(rt.shot("k01_before_stop"))
rt.adb("shell", "am", "force-stop", rt.PKG); time.sleep(1.5); print("pid:", rt.adb("shell", "pidof", rt.PKG).strip() or "none")
m0 = rt.now()
r = subprocess.run(["pico-cli", "app", "launch", rt.PKG, "--device", rt.DEV], capture_output=True, text=True, shell=True)
seen = False; n = 0; t0 = time.time()
while time.time() - t0 < 80:
    lines = rt.logs(m0)
    if any("SNACK restored" in l for l in lines) and not seen:
        seen = True; ts0 = time.time()
        for i in range(5): rt.emu("shot", f"k02_restore_{i}"); n += 1
    if any(re.search(r"STATE idle$", rt.msg(l)) for l in lines): break
    time.sleep(0.5)
time.sleep(1.0)
for ln in rt.logs(m0):
    t = rt.msg(ln)
    if re.search(r"STATE |RESTORE|SNACK|ANCHOR load|OVERLAY|PLANES n=", t): print(f"  +{rt.tsec(rt.ts(ln)) - rt.tsec(m0):6.1f}s {t[:170]}")
after = rt.dump(); print(rt.brief(after))
P0 = {p["id"]: p for p in before["pins"]}; P1 = {p["id"]: p for p in after["pins"]}
print("pin ids kept:", sorted(P0) == sorted(P1))
for pid in P0:
    if pid in P1:
        print(f"  {pid}: moved {1000*rt.dist(P0[pid]['pos'], P1[pid]['pos']):.2f} mm (Stage coords) grade {P0[pid]['grade']}->{P1[pid]['grade']} offset {P0[pid]['offset_mm']}->{P1[pid]['offset_mm']} save {P1[pid]['save']} uuid same {P0[pid]['uuid']==P1[pid]['uuid']}")
T0 = {t["id"]: t for t in before["tapes"]}; T1 = {t["id"]: t for t in after["tapes"]}
for tid in T0:
    if tid in T1: print(f"  {tid}: len {T0[tid]['len_m']:.4f} -> {T1[tid]['len_m']:.4f} (diff {1000*(T1[tid]['len_m']-T0[tid]['len_m']):+.2f} mm) text '{T0[tid]['text']}' -> '{T1[tid]['text']}' tol '{T1[tid]['tol_text']}' ends {T1[tid]['a']}-{T1[tid]['b']}")
    else: print(f"  {tid}: MISSING after relaunch")
print("unit", before["unit"], "->", after["unit"], "| lang", before["lang"], "->", after["lang"], "| snap", before["snap"], "->", after["snap"], "| history", after["history"])
print(rt.shot("k03_after_restore"))
