#!/usr/bin/env python
"""force-stop + launch, wait for STATE idle; prints the start-up log lines. usage: relaunch.py [--exit-first]"""
import sys, os, re, time, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
rt.adb("shell", "am", "force-stop", rt.PKG); time.sleep(1.5)
m0 = rt.now()
r = subprocess.run(["pico-cli", "app", "launch", rt.PKG, "--device", rt.DEV], capture_output=True, text=True, shell=True)
print("launch:", (r.stdout or r.stderr).strip().splitlines()[-1:])
hit = rt.wait(r"STATE idle$", m0, 90)
time.sleep(1.0)
for ln in rt.logs(m0):
    t = rt.msg(ln)
    if re.search(r"STATE |PLANES n=|SCENE |RESTORE|OVERLAY|STAGE |SNACK|ANCHOR load|LANG|UNIT", t):
        print(f"  +{rt.tsec(rt.ts(ln)) - rt.tsec(m0):6.1f}s {t[:170]}")
bad = [l for l in rt.logs(m0, all_tags=True) if re.search(r"ANR in|FATAL EXCEPTION|AndroidRuntime", l)]
print("ANR/FATAL lines:", len(bad))
d = rt.dump(); print(rt.brief(d))
