#!/usr/bin/env python
"""Reprojection check for C: one screenshot, a 4x crop around every clicked pixel (cyan cross = clicked pixel,
magenta dot = camera-model projection of the logged pin position, green dot = centre of the yellow collar blob)."""
import sys, os, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rt
import numpy as np
from PIL import Image, ImageDraw
sys.stdout.reconfigure(encoding="utf-8")
rt.check()
name = sys.argv[1] if len(sys.argv) > 1 else "c_reproj"
recs = [r for r in json.load(open(os.path.join(rt.ROOT, "tools", "test", "out", "c_taps.json"), encoding="utf-8")) if r.get("id")]
if len(sys.argv) > 2:
    ids = sys.argv[2].split(","); recs = [r for r in recs if r["id"] in ids]
hp = rt.pose2()
path = rt.shot(name)
im = Image.open(path).convert("RGB"); a = np.asarray(im).astype(int)
tiles = []
for r in recs:
    if r["pose"] != hp["line"]:
        print(f"{r['id']}: pose differs from the tap pose; clicked pixel recomputed is not valid -> using projection only")
    x, y = r["px"]
    pr = rt.project(r["pin"], hp)
    half = 40
    x0, y0 = int(pr[0]) - half, int(pr[1]) - half
    crop = im.crop((x0, y0, x0 + 2 * half, y0 + 2 * half)).resize((8 * half, 8 * half), Image.NEAREST)
    sub = a[max(0, y0):y0 + 2 * half, max(0, x0):x0 + 2 * half]
    yel = (sub[:, :, 0] > 190) & (sub[:, :, 1] > 140) & (sub[:, :, 1] < 225) & (sub[:, :, 2] < 110)
    # collar radius in px (16 mm on the surface)
    rad = max(3.0, 0.016 * rt.FX / pr[2] * 1.6)
    ys, xs = np.nonzero(yel)
    cen = None
    if len(xs):
        gx, gy = xs + x0, ys + y0
        near = (gx - pr[0]) ** 2 + (gy - pr[1]) ** 2 <= (rad * 1.8) ** 2
        if near.sum() >= 3:
            bx0, bx1, by0, by1 = gx[near].min(), gx[near].max(), gy[near].min(), gy[near].max()
            cen = ((bx0 + bx1) / 2 + 0.5, (by0 + by1) / 2 + 0.5, int(near.sum()))
    dr = ImageDraw.Draw(crop)
    def P(px, py): return ((px - x0) * 4, (py - y0) * 4)
    cx, cy = P(x + 0.5, y + 0.5)
    dr.line((cx - 30, cy, cx - 6, cy), fill=(0, 255, 255)); dr.line((cx + 6, cy, cx + 30, cy), fill=(0, 255, 255))
    dr.line((cx, cy - 30, cx, cy - 6), fill=(0, 255, 255)); dr.line((cx, cy + 6, cx, cy + 30), fill=(0, 255, 255))
    mx, my = P(pr[0] + 0.5, pr[1] + 0.5); dr.ellipse((mx - 3, my - 3, mx + 3, my + 3), outline=(255, 0, 255))
    if cen:
        gx_, gy_ = P(cen[0], cen[1]); dr.ellipse((gx_ - 2, gy_ - 2, gx_ + 2, gy_ + 2), fill=(0, 255, 0))
    dr.text((4, 2), f"{r['id']} {r['label']}", fill=(255, 255, 255))
    tiles.append(crop)
    print(f"{r['id']:4s} {r['label']:24s} clicked ({x},{y})  pin-projection offset ({pr[0]-x:+.1f},{pr[1]-y:+.1f}) px  collar-blob centre offset " + (f"({cen[0]-x-0.5:+.1f},{cen[1]-y-0.5:+.1f}) px n={cen[2]}" if cen else "n/a") + f"  collar radius ~{0.016*rt.FX/pr[2]:.1f} px")
cols = 5; w = tiles[0].size[0]; rows = (len(tiles) + cols - 1) // cols
sheet = Image.new("RGB", (cols * w, rows * w), (20, 20, 20))
for i, t in enumerate(tiles): sheet.paste(t, ((i % cols) * w, (i // cols) * w))
dst = os.path.join(rt.SHOTS, "view", name + "_sheet.png"); os.makedirs(os.path.dirname(dst), exist_ok=True); sheet.save(dst); print(dst, sheet.size)
