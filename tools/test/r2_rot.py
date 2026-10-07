#!/usr/bin/env python
"""Rotate the emulator camera (right-button drags, 0.044 deg per px; drag down = pitch down, drag right = turn right)
to YAW PITCH (degrees, yaw left positive), two corrective iterations. usage: r2_rot.py YAW PITCH"""
import sys, os, math, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
sys.stdout.reconfigure(encoding="utf-8")
ty, tp = float(sys.argv[1]), float(sys.argv[2])
for it in range(3):
    hp = rt.pose2()
    dy = (ty - math.degrees(hp["yaw"]) + 180) % 360 - 180
    dp = tp - math.degrees(hp["pitch"])
    if abs(dy) < 0.3 and abs(dp) < 0.3:
        break
    sx = -dy / 0.044; sy = -dp / 0.044
    sx = max(-1500, min(1500, sx)); sy = max(-1300, min(1300, sy))
    x0, y0 = 1080 - sx / 2, 1079 - sy / 2
    rt.emu("drag", int(x0), int(y0), int(x0 + sx), int(y0 + sy), "-Button", "right", "-Steps", 14)
    time.sleep(0.6)
hp = rt.pose2()
print(f"pose yaw {math.degrees(hp['yaw']):.2f} pitch {math.degrees(hp['pitch']):.2f}  {hp['line'][:120]}")
