"""Round-5b QA: which floor points at 1.4 / 2.5 / 3.5 m (eye distance) are visible from the start position (azimuth
sweep), and from a chosen camera pose which of them leave room for a 150 px sideways and a 150 px away drag
(floor under the whole path, >= 250 px from the frame edge, clear of the body / coachmark at the lower left)."""
import sys, os, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
pls = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "out", "round5b", "planes_start.json")))
floor = [p for p in pls if p["label"].startswith("FLOOR")][0]
FY = 0.4006
EYE0 = [0.0, 1.65014, 0.0]


def axes(yaw, pitch):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    fwd = [-sy * cp, sp, -cy * cp]; right = [cy, 0.0, -sy]; up = rt.cross(right, fwd)
    return fwd, up, right


def hp_of(yaw_d, pitch_d):
    f, u, r = axes(math.radians(yaw_d), math.radians(pitch_d))
    return dict(pos=EYE0, fwd=f, up=u, right=r, eye=rt.add(EYE0, rt.mul(r, -rt.EYE)))


def on_floor_px(x, y, hp):
    o, d = rt.ray(x, y, hp); h = rt.first_hit(o, d, pls)
    return h is not None and h[2]["label"] == floor["label"]


def ok_px(x, y, hp):
    if x < 250 or x > 1910 or y < 250 or y > 1900: return False
    if x < 1250 and y > 1300: return False
    return on_floor_px(x, y, hp)


def visible_az(D):
    h = math.sqrt(max(0.0, D * D - (EYE0[1] - FY) ** 2))
    vis = []
    for az in range(-180, 180, 3):
        a = math.radians(az)
        P = [-h * math.sin(a), FY, -h * math.cos(a)]   # az 0 = straight ahead (-z), + = left
        d = rt.norm(rt.sub(P, EYE0)); hit = rt.first_hit(EYE0, d, pls)
        if hit and hit[2]["label"] == floor["label"] and rt.dist(hit[1], P) < 0.02:
            vis.append(az)
    return vis


def check_pose(yaw, pitch, D, step=3):
    hp = hp_of(yaw, pitch)
    h = math.sqrt(max(0.0, D * D - (EYE0[1] - FY) ** 2))
    good = []
    for az in range(-180, 180, step):
        a = math.radians(az)
        P = [-h * math.sin(a), FY, -h * math.cos(a)]
        pr = rt.project(P, hp)
        if not pr: continue
        x, y = pr[0], pr[1]
        if not ok_px(x, y, hp): continue
        if all(ok_px(x + dx * f, y + dy * f, hp) for (dx, dy) in ((150, 0), (0, -150)) for f in (0.33, 0.66, 1.0)):
            good.append((az, round(x), round(y), [round(v, 2) for v in P]))
    return good


if __name__ == "__main__":
    if len(sys.argv) == 1:
        for D in (1.4, 2.5, 3.5):
            v = visible_az(D)
            print(D, "visible azimuths:", v)
    else:
        yaw, pitch = float(sys.argv[1]), float(sys.argv[2])
        for D in (1.4, 2.5, 3.5):
            print(D, check_pose(yaw, pitch, D))
