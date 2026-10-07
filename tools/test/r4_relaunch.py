"""Round-4 QA: (re)launch after Exit (or force-stop with --stop), try to catch the start-up spinner and the restore
snackbar on the console screenshot; prints the start-up lines. usage: r4_relaunch.py NAME [--stop]"""
import sys, os, re, time, subprocess, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
name = sys.argv[1]
SH = os.path.join(rt.ROOT, ".scratch", "shots", "round4qa", "evidence")
if "--stop" in sys.argv:
    rt.adb("shell", "am", "force-stop", rt.PKG); time.sleep(1.5)
print("waitfast", rt.emu("waitfast")[-10:])
m0 = rt.now()
rt.adb("shell", "am", "start", "-n", "io.embodit.spatialruler/.platform.LaunchActivity")
hit = rt.wait(r"SCENE body panel drawn", m0, 40)
time.sleep(0.6)
out = rt.emu("shot", name + "_spin")
if out.startswith("OK"): shutil.copyfile(os.path.join(rt.SHOTS, name + "_spin.png"), os.path.join(SH, name + "_spin.png"))
hit = rt.wait(r"RESTORE ", m0, 90)
print("waitfast", rt.emu("waitfast")[-10:]); time.sleep(0.8)
out = rt.emu("shot", name + "_restored")
if out.startswith("OK"): shutil.copyfile(os.path.join(rt.SHOTS, name + "_restored.png"), os.path.join(SH, name + "_restored.png"))
rt.wait(r"STATE idle$", m0, 30); time.sleep(0.5)
for ln in rt.logs(m0):
    t = rt.msg(ln)
    if re.search(r"STATE |PLANES n=|SCENE |RESTORE|STAGE enter|SNACK|COACH|BODY park|ANCHOR load", t):
        print(f"  +{rt.tsec(rt.ts(ln)) - rt.tsec(m0):6.2f}s {t[:170]}")
d = rt.dump(); print(rt.brief(d)); print("  snap=", d.get("snap"), "lang=", d.get("lang"), "snack_text=", d.get("snack_text"), "counts=", d.get("snack_counts"))
