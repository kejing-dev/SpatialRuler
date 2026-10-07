"""Round 2a dev helpers (emulator-5556 only). Uses the tester's library tools/test/rt.py."""
import sys, os, time, re, json, subprocess
ROOT = r"C:\Users\Admin\Downloads\spatial-ruler"
sys.path.insert(0, os.path.join(ROOT, "tools", "test"))
import rt
sys.stdout.reconfigure(encoding="utf-8")
SH = os.path.join(ROOT, ".scratch", "shots", "round2a")
os.makedirs(SH, exist_ok=True)

def shot(name, crop=None, k=1.0, settle=1.2):
    rt.emu("waitfast"); time.sleep(settle)
    out = rt.emu("shot", name)
    src = os.path.join(rt.SHOTS, name + ".png")
    dst = os.path.join(SH, name + ".png")
    if not out.startswith("OK"): print("shot failed", out); return None
    import shutil; shutil.copyfile(src, dst)
    if crop:
        from PIL import Image
        im = Image.open(dst).convert("RGB").crop(crop)
        if k != 1.0: im = im.resize((int(im.width*k), int(im.height*k)), Image.LANCZOS)
        c = os.path.join(SH, name + "_crop.png"); im.save(c); return c
    return dst

def press(panel, control, expect=None, timeout=8):
    ok, lines = rt.press(panel, control, expect, timeout)
    print(("OK  " if ok else "FAIL"), panel, control)
    for l in lines: print("   ", l[:230])
    return ok, lines

def tap(x, y, expect=r"TAP |PTR panel|UI ", timeout=8):
    m, lines = rt.tap(x, y, expect, timeout)
    for l in lines: print("   ", l[:230])
    return lines

def cmd(name, a=None, s=None, pat=None, timeout=6):
    m = rt.cmd(name, a, s)
    if pat:
        rt.wait(pat, m, timeout)
    time.sleep(0.3)
    lines = [rt.msg(l) for l in rt.logs(m)]
    return lines

def px(P):
    hp = rt.pose()
    return rt.project(P, hp)

if __name__ == "__main__":
    rt.check()
    a = sys.argv[1]
    if a == "press": press(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None)
    elif a == "tap": tap(float(sys.argv[2]), float(sys.argv[3]))
    elif a == "cmd":
        for l in cmd(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] != "-" else None, sys.argv[4] if len(sys.argv) > 4 else None, pat=sys.argv[5] if len(sys.argv) > 5 else None): print(l[:400])
    elif a == "shot":
        crop = tuple(int(v) for v in sys.argv[3:7]) if len(sys.argv) >= 7 else None
        print(shot(sys.argv[2], crop, float(sys.argv[7]) if len(sys.argv) > 7 else 1.0))
    elif a == "dump": print(rt.brief(rt.dump()))
    elif a == "pose": print(rt.pose()["line"])
    elif a == "bounds":
        b = rt.bounds()
        for k, v in b.items(): print(k, v["pos"], v["scale"], f'{v["w"]}x{v["h"]}', list(v["ctrls"]))

def pin_px(pid, up=0.0145):
    d = rt.dump(); p = rt.pin_by_id(d, pid); hp = rt.pose()
    P = [p["pos"][i] + p["normal"][i] * up for i in range(3)]
    return rt.project(P, hp)

def tap_pin(pid, expect=r"TAP |STATE|TAG"):
    x, y, _ = pin_px(pid)
    print("pin", pid, "px", round(x), round(y))
    return tap(x, y, expect)
