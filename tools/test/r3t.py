#!/usr/bin/env python
"""Round-3 test driver (test agent). REAL pinches through tools/harness/emu.sh, always PRIMED: one `clicks` call whose
first press goes to the calib catcher panel at the real cursor (H1), then the real targets, all inside one 60 fps period.
Steps are separated by " :: ".
  pc T1 [T2 ...] [gap=MS] [exp=REGEX] [to=S]    primed clicks; targets resolved from ONE pose + bounds + dump read:
        ctl:PANEL:NAME | at:X,Y,Z | floor:X,Z | pin:ID | chip:PINID | plate:TAPEID | px:X,Y | menu:NAME (cached row px)
  menucache                                     read bounds of the open menuPanel/langPanel/dialogPanel into the cache
  rot YAW PITCH                                 camera to absolute yaw / pitch (deg, yaw left positive), right-button drags
  shot NAME [x0 y0 x1 y1 [k]]                   console shot (after waitfast) -> .scratch/shots/round3test/NAME.png (+crop)
  everything else is handed to r2.step (dump, bounds, pose, rig, cmd, sleep, waitlog, ...)
"""
import sys, os, re, time, json, math, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import r2
sys.stdout.reconfigure(encoding="utf-8")
OUT = os.path.join(rt.ROOT, "tools", "test", "out", "round3")
SH = os.path.join(rt.ROOT, ".scratch", "shots", "round3test")
os.makedirs(OUT, exist_ok=True); os.makedirs(SH, exist_ok=True)
CACHE = os.path.join(OUT, "pxcache.json")
CATCH = (1296, 1990)
NOISE = re.compile(r"^(PANEL |CTRL |BOUNDS |POSE |DUMP|CMD recv|PLANE |PLANES listed|RIG slot id=\S+ (stowed|shown|parked|kick)|CALIB ev=(Move|Enter|Exit)|PTR panel=\S+ ev=(Enter|Exit|Move))")


def load_cache():
    try: return json.load(open(CACHE))
    except Exception: return {}


def save_cache(c): json.dump(c, open(CACHE, "w"), indent=1)


def resolve(targets):
    need_b = any(t.split(":")[0] in ("ctl", "chip", "plate") for t in targets)
    need_d = any(t.split(":")[0] == "pin" for t in targets)
    hp = rt.pose(); b = rt.bounds() if need_b else {}; d = rt.dump() if need_d else None
    cache = load_cache(); res = []
    for t in targets:
        k, _, rest = t.partition(":")
        if k == "ctl":
            panel, name = rest.split(":", 1)
            if panel not in b or name not in b[panel]["ctrls"]:
                raise RuntimeError(f"NOT AVAILABLE {panel}/{name}; visible: " + "; ".join(f"{p}:{','.join(v['ctrls'])}" for p, v in b.items()))
            x, y = rt.ctrl_px(b, hp, panel, name)
        elif k in ("chip", "plate"):
            name = f"{k}:{rest}"; panel = r2.find_ctrl(b, name)
            if not panel: raise RuntimeError(f"NOT AVAILABLE {name}; visible: " + "; ".join(f"{p}:{','.join(v['ctrls'])}" for p, v in b.items()))
            x, y = rt.ctrl_px(b, hp, panel, name)
        elif k in ("at", "floor"):
            v = [float(s) for s in rest.split(",")]
            P = v if k == "at" else [v[0], r2.floor_y(), v[1]]
            x, y, _ = rt.project(P, hp)
        elif k == "pin":
            p = rt.pin_by_id(d, rest)
            if not p: raise RuntimeError("no pin " + rest)
            x, y, _ = rt.project(rt.add(p["pos"], rt.mul(p["normal"], 0.012)), hp)
        elif k == "px":
            x, y = (float(s) for s in rest.split(","))
        elif k == "menu":
            if rest not in cache: raise RuntimeError("menu row not cached: " + rest)
            x, y = cache[rest]
        else:
            raise RuntimeError("bad target " + t)
        res.append((t, x, y))
    return res, hp


def landed(lines, hp):
    out = []
    for l in lines:
        mm = re.match(r"TAP (surface|backstop|pin|other)\S*.*?stage=\(([^)]*)\)", l)
        if mm:
            pr = rt.project(rt.vec(mm.group(2)), hp)
            if pr: out.append((l[:60], round(pr[0]), round(pr[1])))
    return out


def pc(words):
    gap, exp, to = "300", r"TAP |UI |PTR panel=\S+ ev=Press", 8.0
    targets = []
    for w in words:
        if w.startswith("gap="): gap = w[4:]
        elif w.startswith("exp="): exp = w[4:]
        elif w.startswith("to="): to = float(w[3:])
        else: targets.append(w)
    res, hp = resolve(targets)
    flat = [str(CATCH[0]), str(CATCH[1])]
    for t, x, y in res:
        flat += [str(int(round(x))), str(int(round(y)))]
        print(f"  {t} -> px ({x:.0f},{y:.0f})")
    for attempt in range(2):
        m = rt.now()
        out = rt.emu("clicks", *flat, "-GapMs", gap)
        if "50 fps gate failed" in out or "ERROR" in out:
            print("    HARNESS:", out[-160:]); time.sleep(1.0); continue
        break
    hit = rt.wait(exp, m, to); time.sleep(0.8)
    lines = [rt.msg(l) for l in rt.logs(m)]
    for l in lines:
        if not NOISE.match(l): print("    ", l[:250])
    calib = sum(1 for l in lines if l.startswith("CALIB ev=Press"))
    print(f"    [catcher presses: {calib}; landed: {landed(lines, hp)}; expect /{exp}/: {'HIT' if hit else 'MISS'}]")
    with open(os.path.join(OUT, "presses.jsonl"), "a") as f:
        f.write(json.dumps(dict(t=m, targets=[(t, round(x), round(y)) for t, x, y in res], catcher=calib, hit=bool(hit), exp=exp,
                                lines=[l for l in lines if re.match(r"(TAP|UI |PTR panel=\S+ ev=Press|PIN add|TAPE add|STATE|CALIB ev=Press)", l)])) + "\n")
    return lines


def menucache():
    b = rt.bounds(); hp = rt.pose(); c = load_cache()
    for pid in ("menuPanel", "langPanel", "dialogPanel", "coachPanel", "snackPanel"):
        if pid in b and not b[pid]["popup"]:
            for n in b[pid]["ctrls"]:
                x, y = rt.ctrl_px(b, hp, pid, n); c[n] = [x, y]
                print(f"  cached {pid}/{n} px ({x:.0f},{y:.0f})")
    save_cache(c)


def rot(ty, tp):
    for it in range(4):
        hp = rt.pose2()
        dy = (ty - math.degrees(hp["yaw"]) + 180) % 360 - 180
        dp = tp - math.degrees(hp["pitch"])
        if abs(dy) < 0.3 and abs(dp) < 0.3: break
        sx = -dy / 0.044; sy = -dp / 0.044
        sx = max(-1500, min(1500, sx)); sy = max(-1300, min(1300, sy))
        x0, y0 = 1080 - sx / 2, 1079 - sy / 2
        rt.emu("drag", int(x0), int(y0), int(x0 + sx), int(y0 + sy), "-Button", "right", "-Steps", 14)
        time.sleep(0.5)
    hp = rt.pose2()
    print(f"  pose yaw {math.degrees(hp['yaw']):.2f} pitch {math.degrees(hp['pitch']):.2f}  {hp['line'][:110]}")


def shot(name, crop=None, k=1.0, settle=1.2):
    rt.emu("waitfast"); time.sleep(settle)
    out = rt.emu("shot", name)
    if not out.startswith("OK"): print("  shot failed", out); return None
    src = os.path.join(rt.SHOTS, name + ".png"); dst = os.path.join(SH, name + ".png")
    shutil.copyfile(src, dst)
    if crop:
        from PIL import Image
        im = Image.open(dst).convert("RGB").crop(crop)
        if k != 1.0: im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
        cp = os.path.join(SH, name + "_crop.png"); im.save(cp); print("  shot", cp); return cp
    print("  shot", dst); return dst


def step(words):
    w = words[0]
    if w == "pc": pc(words[1:])
    elif w == "menucache": menucache()
    elif w == "rot": rot(float(words[1]), float(words[2]))
    elif w == "shot":
        crop = tuple(int(v) for v in words[2:6]) if len(words) >= 6 else None
        shot(words[1], crop, float(words[6]) if len(words) > 6 else 1.0)
    else: r2.step(words)


if __name__ == "__main__":
    rt.check()
    for s in " ".join(sys.argv[1:]).split(" :: "):
        if not s.strip(): continue
        print(">>", s)
        try: step(s.split())
        except Exception as e: print("   STEP FAILED:", e)
