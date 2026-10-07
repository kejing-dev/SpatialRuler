#!/usr/bin/env python
"""Round-4 QA driver (test agent). Wraps r3t.py (primed REAL pinches through tools/harness/emu.sh, catcher first)
with round-4 output paths and adds:
  pshot NAME PANEL[,PANEL...] [pad=PX] [k=F]   console shot (after waitfast), crop to the union of the panels' rectangles
  ptshot NAME X,Y,Z [half=PX] [k=F]            console shot, crop around a Stage point
  fshot NAME [k=F]                             whole frame (scaled) for a look
  uidump NAME                                  uiautomator dump of the active window: every text / content-desc node
  words                                        dump -> snackbar text + word / CJK-character count
  every r3t / r2 step (pc, menucache, rot, dump, bounds, pose, rig, cmd, sleep, waitlog, ...)
Steps are separated by " :: ".
"""
import sys, os, re, time, json, shutil, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import r3t
import r2
sys.stdout.reconfigure(encoding="utf-8")
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round4")
SH = os.path.join(rt.ROOT, ".scratch", "shots", "round4qa", "evidence")
os.makedirs(OUT, exist_ok=True); os.makedirs(SH, exist_ok=True)
r3t.OUT = OUT; r3t.SH = SH; r3t.CACHE = os.path.join(OUT, "pxcache.json")


def kv(words, key, default):
    for w in words:
        if w.startswith(key + "="): return w.split("=", 1)[1]
    return default


def grab(name, settle=1.2):
    rt.emu("waitfast"); time.sleep(settle)
    out = rt.emu("shot", name)
    if not out.startswith("OK"): print("  shot failed", out); return None
    src = os.path.join(rt.SHOTS, name + ".png"); dst = os.path.join(SH, name + ".png")
    shutil.copyfile(src, dst); return dst


def crop(path, box, k, name):
    from PIL import Image
    im = Image.open(path).convert("RGB")
    x0, y0, x1, y1 = (int(round(v)) for v in box)
    x0, y0 = max(0, x0), max(0, y0); x1, y1 = min(im.width, x1), min(im.height, y1)
    im = im.crop((x0, y0, x1, y1))
    if k != 1.0: im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
    cp = os.path.join(SH, name + "_crop.png"); im.save(cp); print("  crop", cp, im.size); return cp


def panel_box(b, hp, pid):
    p = b[pid]; xs, ys = [], []
    for cx, cy in ((0, 0), (p["w"], 0), (0, p["h"]), (p["w"], p["h"])):
        pr = rt.project(rt.panel_point(p, cx, cy), hp)
        if pr: xs.append(pr[0]); ys.append(pr[1])
    return (min(xs), min(ys), max(xs), max(ys)) if xs else None


def pshot(words):
    name, pids = words[0], words[1].split(",")
    pad = float(kv(words, "pad", "40")); k = float(kv(words, "k", "1.0"))
    b = rt.bounds(); hp = rt.pose()
    boxes = [panel_box(b, hp, p) for p in pids if p in b]
    missing = [p for p in pids if p not in b]
    if missing: print("  not visible:", missing, "visible:", list(b))
    path = grab(name)
    if not path or not boxes: return
    box = (min(x[0] for x in boxes) - pad, min(x[1] for x in boxes) - pad, max(x[2] for x in boxes) + pad, max(x[3] for x in boxes) + pad)
    crop(path, box, k, name)


def ptshot(words):
    name = words[0]; P = [float(v) for v in words[1].split(",")]
    half = float(kv(words, "half", "260")); k = float(kv(words, "k", "1.0"))
    hp = rt.pose(); pr = rt.project(P, hp)
    path = grab(name)
    if path and pr: crop(path, (pr[0] - half, pr[1] - half, pr[0] + half, pr[1] + half), k, name)


def fshot(words):
    name = words[0]; k = float(kv(words, "k", "0.5"))
    path = grab(name)
    if path: crop(path, (0, 0, 2160, 2158), k, name)


def uidump(words):
    name = words[0] if words else "ui"
    rt.adb("shell", "uiautomator dump /sdcard/r4ui.xml", timeout=90)
    x = rt.adb("shell", "cat /sdcard/r4ui.xml")
    open(os.path.join(OUT, f"ui_{name}.xml"), "w", encoding="utf-8").write(x)
    seen = set()
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?class="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?clickable="(\w+)"[^>]*?bounds="([^"]*)"', x):
        t, c, d, cl, bd = m.groups()
        key = (t, d, bd)
        if (t or d or cl == "true") and key not in seen:
            seen.add(key); print(f"   text={t!r} desc={d!r} {c.split('.')[-1]} clickable={cl} {bd}")
    root = re.search(r'bounds="(\[0,0\]\[\d+,\d+\])"', x)
    print("   window", root.group(1) if root else "?")


def wc(text):
    if not text: return (0, 0)
    t = re.sub(r"[\d.,±×▲⦸◆●○—′″>·%:]+", " ", text)
    t = re.sub(r"\b(m|cm|mm|ft|in)\b", " ", t)
    cjk = len(re.findall(r"[一-鿿]", t))
    en = len([w for w in re.findall(r"[A-Za-z']+", t)])
    return (en, cjk)


def words_step():
    d = rt.dump()
    for k in ("snack", "snack_text", "snack_desc", "snack_action", "snack_counts", "snack_remaining_ms"):
        print(f"   {k} = {d.get(k)!r}")
    st = d.get("snack_text"); a = d.get("snack_action")
    print(f"   words(text)={wc(st)} words(action)={wc(a)}  (en words, zh chars)")


def pcache(words):
    """pcache PANEL PREFIX: cache the console pixel of every control of PANEL as PREFIX<name> (target menu:PREFIX<name>)."""
    pid, pre = words[0], words[1]
    b = rt.bounds(); hp = rt.pose(); c = r3t.load_cache()
    if pid not in b: print("  not visible:", pid, list(b)); return
    for n in b[pid]["ctrls"]:
        x, y = rt.ctrl_px(b, hp, pid, n); c[pre + n] = [x, y]; print(f"  cached {pre}{n} px ({x:.0f},{y:.0f})")
    r3t.save_cache(c)


def step(words):
    w = words[0]
    if w == "pcache": pcache(words[1:])
    elif w == "pshot": pshot(words[1:])
    elif w == "ptshot": ptshot(words[1:])
    elif w == "fshot": fshot(words[1:])
    elif w == "uidump": uidump(words[1:])
    elif w == "words": words_step()
    else: r3t.step(words)


if __name__ == "__main__":
    rt.check()
    for s in " ".join(sys.argv[1:]).split(" :: "):
        if not s.strip(): continue
        print(">>", s)
        try: step(s.split())
        except Exception as e: print("   STEP FAILED:", repr(e))
