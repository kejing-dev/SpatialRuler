"""Round-5b QA item 4 (R5-4): on a SELECTED floor pin at <= 1.8 m, real press-drags on the pin's head must move the pin
(5 of 5, no stub pull) and real press-drags on the tape-head stub's hook end must pull a new tape (2 of 2).
Setup by debug broadcasts (clear, place, tap_pin to select, undo to tidy a pulled tape); presses are real (r5bqa.run)."""
import sys, os, time, json, re, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r5bqa
rt.check()
args = sys.argv[1:]
P = [float(v) for v in args[0].split(",")] if args else [-0.816, 0.4006, -0.144]
tag = args[1] if len(args) > 1 else "a"
r5bqa.c("clear", pat="CLEAR|STATE", settle=0.8)
pid, _ = r5bqa.place(P)
r5bqa.deselect(); time.sleep(0.3)


def select():
    d = rt.dump()
    if d.get("armed") != pid or d.get("state") not in ("pin-selected", "pin-selected-tuning"):
        if d.get("armed") and d.get("armed") != pid:
            r5bqa.c("tap_pin", None, d["armed"], pat=r"STATE|TAP", settle=0.4)
        r5bqa.c("tap_pin", None, pid, pat=r"STATE pin-selected", settle=0.8)
    d = rt.dump()
    return d.get("state"), d.get("armed")


def stub_hook_px():
    hp = rt.pose(); d = rt.dump(); pin = rt.pin_by_id(d, pid)
    k = max(1.0, min(2.0, rt.dist(pin["pos"], hp["eye"]) / 2.5))
    n = pin["normal"]
    base = rt.add(pin["pos"], rt.mul(n, (0.0145 + 0.006) * k))
    side = hp["right"] if abs(n[1]) > 0.5 else [0.0, 1.0, 0.0]
    dirv = rt.norm(rt.add(rt.mul(n, 0.75), rt.mul(side, 0.66)))   # sway 0 (it sways +-0.14 around this)
    centre = rt.add(base, rt.mul(dirv, (0.085 - 0.015) * k))
    pr = rt.project(centre, hp)
    return pr[0], pr[1], rt.dist(pin["pos"], hp["eye"])


res = []
dirs = [(80, 0), (-80, 0), (0, -70), (60, 50), (-60, -50)]
for i, (dx, dy) in enumerate(dirs):
    st = select()
    out, raw, p = r5bqa.run(pid, [("m", dx, dy, 8, 30), ("w", 900)], f"r4{tag}_head{i + 1}")
    res.append(dict(kind="head", i=i + 1, state_before=st, dstart=out["dstart"], begin=out["begin"], mode=out["mode"], dist=out.get("dist_start"), other=out["other"]))
for i in range(2):
    st = select()
    x, y, dist = stub_hook_px()
    out, raw, p = r5bqa.run(pid, [("at", x, y), ("m", 140, -40, 10, 30), ("w", 900)], f"r4{tag}_hook{i + 1}")
    res.append(dict(kind="hook", i=i + 1, state_before=st, px=(round(x), round(y)), dist=round(dist, 3), dstart=out["dstart"], begin=out["begin"], mode=out["mode"], other=out["other"]))
    time.sleep(0.6)
    d = rt.dump()
    print("   after hook pull:", rt.brief(d))
    # tidy: undo the pulled tape and its new pin (debug), then the pin is selected again by select()
    lines = r5bqa.c("undo", pat=r"UNDO", settle=0.8)
    print("   undo:", [l for l in lines if l.startswith(("UNDO", "TAPE remove", "PIN remove"))])
print("\nSUMMARY R5-4", tag)
for r in res:
    print(f"  {r['kind']}{r['i']} dist={r.get('dist')} state={r['state_before']} mode={r['mode']} | {r['dstart']} | {[o for o in r['other'] if o.startswith(('PULL', 'PIN add', 'TAPE add'))]}")
json.dump(res, open(os.path.join(r5bqa.OUT, f"t4_{tag}.json"), "w"), indent=1)
