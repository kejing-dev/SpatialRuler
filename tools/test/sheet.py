#!/usr/bin/env python
"""Contact sheet of one crop region over several screenshots.
usage: sheet.py OUT x0 y0 x1 y1 NAME [NAME...] [--cols N] [--scale S]"""
import os, sys
from PIL import Image, ImageDraw
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
H = os.path.join(ROOT, ".scratch", "shots", "harness")
args = sys.argv[1:]
cols = 3; scale = 1.0
if "--cols" in args:
    i = args.index("--cols"); cols = int(args[i + 1]); del args[i:i + 2]
if "--scale" in args:
    i = args.index("--scale"); scale = float(args[i + 1]); del args[i:i + 2]
out = args[0]; box = tuple(int(v) for v in args[1:5]); names = args[5:]
tiles = []
for n in names:
    p = n if os.path.exists(n) else os.path.join(H, n + ".png")
    im = Image.open(p).convert("RGB").crop(box)
    if scale != 1.0:
        im = im.resize((int(im.size[0] * scale), int(im.size[1] * scale)), Image.LANCZOS)
    ImageDraw.Draw(im).text((4, 2), os.path.basename(n), fill=(255, 0, 255))
    tiles.append(im)
w, h = tiles[0].size
rows = (len(tiles) + cols - 1) // cols
sheet = Image.new("RGB", (cols * w, rows * h), (30, 30, 30))
for i, t in enumerate(tiles):
    sheet.paste(t, ((i % cols) * w, (i // cols) * h))
os.makedirs(os.path.join(H, "view"), exist_ok=True)
dst = out if os.path.isabs(out) else os.path.join(H, "view", out + ".jpg")
sheet.save(dst, quality=88); print(dst, sheet.size)
