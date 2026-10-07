#!/usr/bin/env python
"""Downscaled / cropped copy of a harness screenshot for viewing.
usage: python tools/test/view.py NAME [x0 y0 x1 y1] [--scale S] [--out PATH]
NAME is a file stem in .scratch/shots/harness (or a path). Writes .scratch/shots/harness/view/NAME[_crop].jpg"""
import os, sys
from PIL import Image
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
H = os.path.join(ROOT, ".scratch", "shots", "harness")
args = [a for a in sys.argv[1:]]
scale = None; out = None
if "--scale" in args:
    i = args.index("--scale"); scale = float(args[i + 1]); del args[i:i + 2]
if "--out" in args:
    i = args.index("--out"); out = args[i + 1]; del args[i:i + 2]
name = args[0]
path = name if os.path.exists(name) else os.path.join(H, name + ".png")
im = Image.open(path).convert("RGB")
suffix = ""
if len(args) >= 5:
    box = tuple(int(float(v)) for v in args[1:5]); im = im.crop(box); suffix = "_crop%d_%d" % (box[0], box[1])
if scale is None:
    scale = min(1.0, 1100.0 / max(im.size))
if scale != 1.0:
    im = im.resize((max(1, int(im.size[0] * scale)), max(1, int(im.size[1] * scale))), Image.LANCZOS)
os.makedirs(os.path.join(H, "view"), exist_ok=True)
if out is None:
    out = os.path.join(H, "view", os.path.splitext(os.path.basename(path))[0] + suffix + ".jpg")
im.save(out, quality=88)
print(out, im.size)
