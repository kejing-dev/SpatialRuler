#!/usr/bin/env python
"""G. Mixed sequence with real pinches (place, connect, nudge, remove, retract), then Undo step by step to empty."""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
LOG = []

def snap():
    d = rt.dump()
    return dict(state=d["state"], hist=d["history"],
                pins=sorted((p["id"], tuple(round(c, 4) for c in p["pos"]), p["grade"], tuple(p["offset_mm"]), p["save"], p["tol_mm"]) for p in d["pins"]),
                tapes=sorted((t["id"], t["a"], t["b"], round(t["len_m"], 4), t["tol_mm"]) for t in d["tapes"])), d

def model(s): return json.dumps([s["pins"], s["tapes"]])

def click(x, y, pat=r"TAP |UI |PTR panel=\S+ ev=Press", settle=0.9):
    m, out = rt.click(x, y); rt.wait(pat, m, 8); time.sleep(settle)
    lines = [rt.msg(l)[:140] for l in rt.logs(m) if re.search(r"PTR .*Press|UI |TAP |PIN |TAPE |STATE|UNDO|ANCHOR saved|SNACK", l)]
    return lines

def pinpx(d, pid):
    hp = rt.pose2(); p = rt.pin_by_id(d, pid); pr = rt.project(rt.add(p["pos"], rt.mul(p["normal"], 0.018)), hp); return pr[0], pr[1]

def ctl(panel, name):
    b = rt.bounds(); hp = rt.pose2()
    if panel is None:
        panel = next((k for k, v in b.items() if name in v["ctrls"] and not v["popup"]), None)
    if panel not in b or name not in b[panel]["ctrls"]:
        return None
    return rt.ctrl_px(b, hp, panel, name)

S = []
s, d = snap(); S.append(("S0 empty", s)); print("S0", s["hist"], s["pins"], s["tapes"])
print("1 place A:", click(1300, 1900, r"ANCHOR saved")); s, d = snap(); S.append(("S1 place A", s))
print("2 place B (+tape):", click(1800, 1750, r"ANCHOR saved")); s, d = snap(); S.append(("S2 place B + tape", s))
print("3 place C:", click(1233, 1425, r"ANCHOR saved")); s, d = snap(); S.append(("S3 place C (armed)", s))
ids = [p["id"] for p in d["pins"]]; A, B, C = ids[0], ids[1], ids[2]
x, y = pinpx(d, A); print("4 connect C-A (tap pin A):", click(x, y, r"TAPE add")); s, d = snap(); S.append(("S4 connect C-A", s))
x, y = pinpx(d, B); print("5a select B:", click(x, y))
xy = ctl("pinTag", "pin_tune"); print("5b tune:", click(*xy))
xy = ctl("pinTagTune", "step_u_plus"); print("5c step_u_plus:", click(*xy, pat=r"PIN nudge"))
xy = ctl("pinTagTune", "pin_close"); print("5d close:", click(*xy)); s, d = snap(); S.append(("S5 nudge B", s))
x, y = pinpx(d, C); print("6a select C:", click(x, y))
xy = ctl("pinTag", "pin_remove"); print("6b remove C:", click(*xy, pat=r"PIN remove")); s, d = snap(); S.append(("S6 remove C", s))
tid = d["tapes"][0]["id"]
xy = ctl(None, "plate:" + tid); print("7a select tape", tid, ":", click(*xy))
xy = ctl("tapeTag", "tape_retract"); r = click(*xy, pat=r"TAPE remove|TAP ")
print("7b retract (real pinch):", r)
if not any("TAPE remove" in l for l in r):
    s7, d7 = snap(); print("   real pinch did not retract; state", s7["state"], "pins", len(s7["pins"]), "tapes", len(s7["tapes"]))
    # undo whatever the fallen-through pinch did is not possible to know in general: report and fall back to the debug control
    if s7["state"] != "tape-selected":
        m = rt.cmd("ui", s="plate:" + tid); time.sleep(0.8)
    m = rt.cmd("ui", s="tape_retract"); time.sleep(1.0); print("   debug ui tape_retract:", [rt.msg(l)[:100] for l in rt.logs(m) if re.search(r"TAPE|STATE", l)])
s, d = snap(); S.append(("S7 retract", s))
for name, st in S: print(f"   {name}: hist={st['hist']} pins={[p[0] for p in st['pins']]} tapes={[(t[0], t[3]) for t in st['tapes']]}")
# undo back
ok_all = True
for i in range(len(S) - 2, -1, -1):
    xy = ctl("body", "undo"); r = click(*xy, pat=r"UNDO|TAP ")
    s, d = snap()
    same = model(s) == model(S[i][1])
    ok_all &= same
    print(f"undo -> expect {S[i][0]}: {'MATCH' if same else 'MISMATCH'} hist={s['hist']} state={s['state']} {[l for l in r if 'UNDO' in l or 'TAP' in l or 'PIN' in l]}")
    if not same:
        print("    got     ", s["pins"], s["tapes"]); print("    expected", S[i][1]["pins"], S[i][1]["tapes"])
xy = ctl("body", "undo"); r = click(*xy, pat=r"UNDO|TAP |UI ")
s, d = snap(); print("undo on the empty stack:", r, "hist", s["hist"], "pins", len(s["pins"]), "tapes", len(s["tapes"]))
print("ALL MATCH" if ok_all else "SOME MISMATCH")
rt.shot("g01_after_undo_all")
