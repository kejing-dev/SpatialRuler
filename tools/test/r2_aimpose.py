#!/usr/bin/env python
"""Head yaw / pitch (deg) that puts Stage point P at console pixel (PX, PY) with the head at its current position.
usage: r2_aimpose.py x y z [PX PY]   -> prints "YAW PITCH" """
import sys, os, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
P = [float(v) for v in sys.argv[1:4]]
px = float(sys.argv[4]) if len(sys.argv) > 5 else 1297.0
py = float(sys.argv[5]) if len(sys.argv) > 5 else 1990.0
hp = rt.pose2()
pos = hp["pos"]
yaw, pitch = hp["yaw"], hp["pitch"]
for _ in range(60):
    f, u, r, _q = rt._axes(yaw, pitch)
    d = rt.sub(P, pos)
    dep = rt.dot(d, f)
    x = 1080 + 1286.8 * (rt.dot(d, r) + 0.032) / dep
    y = 1079 - 1286.8 * rt.dot(d, u) / dep
    ex, ey = px - x, py - y
    if abs(ex) < 0.05 and abs(ey) < 0.05:
        break
    # turning right (yaw down) moves points left; pitching down moves points up
    yaw += math.radians(ex * 0.044) * 0.9
    pitch += math.radians(ey * 0.044) * 0.9
print(f"{math.degrees(yaw):.2f} {math.degrees(pitch):.2f}")
