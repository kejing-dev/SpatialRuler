#!/usr/bin/env python
"""Round 2, test C: press-and-pull with a REAL drag (harness), screenshots while the pinch is held.
usage: r2_pull.py X1 Y1 X2 Y2 NAME [--steps 20] [--stepms 150] [--endhold 2500] [--shots 2] [--shot-delay 1.2]
Pixels are console pixels. The drag is primed with a click on the calib catcher (real-cursor hijack, see round2.md)."""
import sys, os, re, time, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
a = sys.argv[1:]
def opt(name, default):
    if name in a:
        i = a.index(name); v = a[i + 1]; del a[i:i + 2]; return v
    return default
steps = opt("--steps", "20"); stepms = opt("--stepms", "150"); endhold = opt("--endhold", "2500")
nshots = int(opt("--shots", "2")); delay = float(opt("--shot-delay", "1.2"))
x1, y1, x2, y2 = (int(round(float(v))) for v in a[:4]); name = a[4]
CATCH = (1297, 1990)
print("prime:", rt.emu("click", *CATCH)[-70:])
m = rt.now()
p = subprocess.Popen(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", os.path.join(rt.ROOT, "tools", "harness", "emu.ps1"),
                      "drag", str(x1), str(y1), str(x2), str(y2), "-Steps", steps, "-StepMs", stepms, "-EndHoldMs", endhold, "-NoRearm",
                      "-Avd", "PICO_6.1_Ruler"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
time.sleep(delay)
shots = []
for i in range(nshots):
    t = time.time()
    out = rt.emu("shot", f"{name}_{i}")
    shots.append((f"{name}_{i}", round(time.time() - t, 1)))
out, _ = p.communicate(timeout=60)
print("drag:", out.strip()[-150:])
rt.wait(r"DRAG (end|cancel)", m, 10); time.sleep(1.5)
for l in rt.logs(m):
    t = rt.msg(l)
    if re.match(r"(DRAG (start|begin|end|cancel)|PULL|PIN add|TAPE|STATE|TAP |SNAP|FEEDBACK|UNDO|AIM)", t) or (t.startswith("DRAG move") and re.search(r"n=(1|5|10|15|20|25|30)\b", t)):
        print("  ", rt.ts(l)[6:], t[:230])
print("shots:", shots)
