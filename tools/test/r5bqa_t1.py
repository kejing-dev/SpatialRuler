"""Round-5b QA item 1 (R5-1): real drags of floor pins at about 1.4 / 2.5 / 3.5 m, sideways (150 px right) and away
(150 px up), from the work pose yaw 88 / pitch -38 (looking left and down at the floor). Pins are placed by the debug
`place` (setup) and deselected (`tap_pin`); the drags themselves are real pinch-drags (r5bqa.run)."""
import sys, os, time, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt, r5bqa
rt.check()
PTS = {"A14": [-0.59, 0.4006, -0.23], "B25": [-2.06, 0.4006, 0.67], "C35": [-3.20, 0.4006, 0.68]}
which = sys.argv[1:] or list(PTS)
res = []
for key in which:
    P = PTS[key]
    pid, lines = r5bqa.place(P)
    print(key, "placed", pid, [l for l in lines if l.startswith(("PIN add", "ANCHOR"))][:2])
    r5bqa.deselect(); time.sleep(0.4)
    out, raw, p = r5bqa.run(pid, [("m", 150, 0, 10, 30), ("w", 1500)], f"r1_{key}_side")
    res.append(out)
    time.sleep(0.5); r5bqa.deselect(); time.sleep(0.4)
    out, raw, p = r5bqa.run(pid, [("m", 0, -150, 10, 30), ("w", 1500)], f"r1_{key}_away")
    res.append(out)
    time.sleep(0.5); r5bqa.deselect()
print("\nSUMMARY R5-1")
for o in res:
    print(f"  {o['name']:16s} mode={o['mode']} d={o.get('dist_start')}->{o.get('dist_end')} m cursor={o.get('cursor_px')} px pin={o.get('pin_px')} px "
          f"err={o.get('err_px')} (along {o.get('err_along')}, across {o.get('err_across')}) trail_max={o.get('trail_max_px')} settle8={o.get('settle_8px_s')} gains={o.get('gains')}")
