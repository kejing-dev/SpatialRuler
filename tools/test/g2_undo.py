#!/usr/bin/env python
"""G (continued). The scene already holds: place A, place B(+tape), place C, connect C-A, stray pin+tape (fall-through).
Adds nudge / remove / retract through the debug `ui` controls (their real-pinch paths are covered in E and F), then
presses the real Undo key step by step and compares the model with each earlier snapshot."""
import sys, os, re, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
rt.check()

def snap():
    d = rt.dump()
    return dict(state=d["state"], hist=d["history"],
                pins=sorted((p["id"], tuple(round(c, 4) for c in p["pos"]), p["grade"], tuple(p["offset_mm"]), p["save"], p["tol_mm"]) for p in d["pins"]),
                tapes=sorted((t["id"], t["a"], t["b"], round(t["len_m"], 4), t["tol_mm"]) for t in d["tapes"])), d
def model(s): return json.dumps([s["pins"], s["tapes"]])
def ui(name):
    m = rt.cmd("ui", s=name); time.sleep(0.7); return [rt.msg(l)[:110] for l in rt.logs(m) if re.search(r"PIN |TAPE |STATE|UI |CMD ui", l)]
def dbg(name, s=None):
    m = rt.cmd(name, s=s); time.sleep(0.7); return [rt.msg(l)[:110] for l in rt.logs(m) if re.search(r"PIN |TAPE |STATE|TAP ", l)]

s, d = snap()
if d["armed"]: print("cancel selection:", dbg("tap_pin", d["armed"]))
S = []
s, d = snap(); S.append(("S5 (5 actions: A, B+tape, C, connect, stray pin+tape)", s)); print("S5 hist", s["hist"], [p[0] for p in s["pins"]], [t[0] for t in s["tapes"]])
print("nudge:", dbg("tap_pin", "p55"), ui("pin_tune"), ui("step_u_plus"), ui("pin_close")); s, d = snap(); S.append(("S6 nudge p55", s))
print("remove:", dbg("tap_pin", "p57"), ui("pin_remove")); s, d = snap(); S.append(("S7 remove p57", s))
print("retract:", ui("plate:t29"), ui("tape_retract")); s, d = snap(); S.append(("S8 retract t29", s))
for name, st in S: print(f"   {name}: hist={st['hist']} state={st['state']} pins={[(p[0], p[2], p[3]) for p in st['pins']]} tapes={[(t[0], t[3]) for t in st['tapes']]}")
ok_all = True
b = rt.bounds(); hp = rt.pose2(); ux, uy = rt.ctrl_px(b, hp, "body", "undo")
def undo():
    m, out = rt.click(ux, uy); rt.wait(r"UNDO|TAP |UI undo", m, 8); time.sleep(1.2)
    return [rt.msg(l)[:120] for l in rt.logs(m) if re.search(r"PTR .*Press|UNDO|TAP |PIN add|UI ", l)]
for i in range(len(S) - 2, -1, -1):
    r = undo(); s, d = snap(); same = model(s) == model(S[i][1]); ok_all &= same
    print(f"undo -> expect {S[i][0]}: {'MATCH' if same else 'MISMATCH'} hist={s['hist']} state={s['state']} {r}")
    if not same: print("    got     ", s["pins"], s["tapes"]); print("    expected", S[i][1]["pins"], S[i][1]["tapes"])
# back through the 5 earlier actions to empty
exp = [(4, 2), (3, 2), (3, 1), (2, 1), (1, 0), (0, 0)]   # (pins, tapes) after undoing: stray, connect, place C, place B+tape, place A
for k in range(5):
    r = undo(); s, d = snap()
    print(f"undo #{k+1} of the first five: pins={len(s['pins'])} tapes={len(s['tapes'])} hist={s['hist']} state={s['state']} armed={d['armed']} {[l for l in r if 'UNDO' in l]}")
r = undo(); s, d = snap(); print("undo on the empty stack:", r, "| hist", s["hist"], "pins", len(s["pins"]), "tapes", len(s["tapes"]), "state", s["state"])
print("ALL MATCH" if ok_all else "SOME MISMATCH")
print(rt.shot("g03_after_undo_all"))
