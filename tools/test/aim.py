"""Round 2b helpers: synthetic tracked pointer on emulator-5556."""
import sys, os, time, math
import t2a, rt
t2a.SH = os.path.join(t2a.ROOT, ".scratch", "shots", "round2b")
os.makedirs(t2a.SH, exist_ok=True)
HAND = (0.25, 1.20, -0.20)
def say(lines, keys=("SRC", "RETICLE", "AIM", "TAP ", "PIN ", "TAPE ", "POINTER", "TRIGGER", "CONTACT", "STEADY", "FEEDBACK", "STATE", "SNACK", "DRAG")):
    for l in lines:
        if any(k in l for k in keys): print("   ", l[:230])
def point(to, origin=HAND, pat="POINTER"):
    d = [to[i] - origin[i] for i in range(3)]
    return t2a.cmd("pointer", "%.4f,%.4f,%.4f,%.5f,%.5f,%.5f" % (origin[0], origin[1], origin[2], d[0], d[1], d[2]), None, pat=pat)
def c(name, a=None, s=None, pat=None, wait=0.0):
    lines = t2a.cmd(name, a, s, pat=pat)
    if wait: time.sleep(wait); lines = lines  # logs already read
    return lines
def since(mark):
    return [rt.msg(l) for l in rt.logs(mark)]
def shot_at(name, P, half=260, k=2.0):
    hp = rt.pose(); x, y, z = rt.project(P, hp)
    return t2a.shot(name, (int(x - half), int(y - half), int(x + half), int(y + half)), k, settle=0.4)
