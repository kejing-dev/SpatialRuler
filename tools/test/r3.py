"""Round 3 helpers: shots go to .scratch/shots/round3."""
import sys, os, time, math
sys.path.insert(0, r"C:\Users\Admin\Downloads\spatial-ruler\tools\test")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # needs t2a.py / aim.py beside it (dev-agent scratchpad helpers)
import t2a, rt, aim
t2a.SH = os.path.join(t2a.ROOT, ".scratch", "shots", "round3")
os.makedirs(t2a.SH, exist_ok=True)
HAND = aim.HAND
point = aim.point
c = aim.c
since = aim.since
say = aim.say


def shot_at(name, P, half=260, k=2.0, settle=0.4):
    hp = rt.pose()
    x, y, z = rt.project(P, hp)
    return t2a.shot(name, (int(x - half), int(y - half), int(x + half), int(y + half)), k, settle=settle)


def view(name, k=0.5):
    """Whole-frame screenshot scaled down for a look."""
    p = t2a.shot(name, settle=0.8)
    from PIL import Image
    im = Image.open(p).convert("RGB")
    im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
    out = os.path.join(t2a.SH, "_view.jpg")
    im.save(out, quality=80)
    return out


def floor_point(x, z, planes=None):
    """A point on the floor plane at (x, z) (y from the floor quad)."""
    pls = planes or rt.planes()
    fl = [p for p in pls if p.get("label", "").startswith("FLOOR")]
    y = fl[0]["center"][1] if fl else 0.0
    return (x, y, z)
