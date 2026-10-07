#!/usr/bin/env python
"""A. Cold start from a cleared state: state sequence, plane timing, body readout, ANR/crash check."""
import sys, time, re, subprocess, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
tag = sys.argv[1] if len(sys.argv) > 1 else "a"
fast = "--fast" in sys.argv
if "--noclear" not in sys.argv:
    m = rt.cmd("clear"); print("clear:", [rt.msg(l) for l in rt.wait(r"CLEAR|STATE", m, 8)])
    time.sleep(1.0)
    d = rt.dump(); print(rt.brief(d))
print("force-stop"); rt.adb("shell", "am", "force-stop", rt.PKG); time.sleep(1.5)
print("pid after stop:", rt.adb("shell", "pidof", rt.PKG).strip() or "none")
m0 = rt.now(); t0 = time.time()
r = subprocess.run(["pico-cli", "app", "launch", rt.PKG, "--device", rt.DEV], capture_output=True, text=True, shell=True)
print("launch:", (r.stdout or r.stderr).strip().splitlines()[-1:], "mark", m0)
seen = set(); shots = []; n = 0
idle_at = None
while time.time() - t0 < 150:
    lines = rt.logs(m0)
    for ln in lines:
        t = rt.msg(ln)
        if re.search(r"STATE |PLANES n=|SCENE |RESTORE|OVERLAY|STAGE |SNACK", t) and ln not in seen:
            seen.add(ln); print(f"  +{rt.tsec(rt.ts(ln)) - rt.tsec(m0):6.1f}s {t[:160]}")
            if re.search(r"STATE idle$", t) and idle_at is None:
                idle_at = time.time()
    if fast:
        rt.emu("waitfast")
    name = f"{tag}_start_{n:02d}"; out = rt.emu("shot", name); n += 1
    shots.append((round(time.time() - t0, 1), name))
    if idle_at and time.time() - idle_at > 12:
        break
    time.sleep(1.5)
print("shots:", shots)
allrows = rt.logs(m0, all_tags=True)
bad = [l for l in allrows if re.search(r"ANR in|FATAL|AndroidRuntime|Application Not Responding|am_anr|am_crash", l)]
print("ANR/FATAL/AndroidRuntime lines since launch:", len(bad))
for l in bad[:20]: print("   ", l[:300])
open(os.path.join(rt.ROOT, "tools", "test", "out", f"{tag}_coldstart.log"), "w", encoding="utf-8").write("\n".join(rt.logs(m0)))
print(rt.brief(rt.dump()))
