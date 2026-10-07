#!/usr/bin/env python
"""Print the API-reference section(s) for class names. Usage: apiref.py <file-substring> <ClassName> [<ClassName>...]
   or: apiref.py --list <file-substring>   (list class names in a file)"""
import sys, os, re, glob
BASE = r"C:\Users\Admin\AppData\Local\PICO\sdk\6.1\agent-vault\spatial\api-reference"
def sections(path):
    txt = open(path, encoding="utf-8", errors="replace").read()
    # sections start with "# <package>\n# <Name>\npackage"
    parts = re.split(r"(?m)^# (com\.pico[^\n]*)\n# ([^\n]+)\n", txt)
    # parts: [pre, pkg, name, body, pkg, name, body...]
    out = []
    for i in range(1, len(parts) - 2, 3):
        out.append((parts[i], parts[i + 1].strip(), parts[i + 2]))
    return out
args = sys.argv[1:]
if args[0] == "--list":
    for f in glob.glob(os.path.join(BASE, ("*" + args[1]) if args[1].endswith(".md") else ("*" + args[1] + "*.md"))):
        print("==", os.path.basename(f))
        print(", ".join(n for _, n, _ in sections(f)))
    sys.exit(0)
sub, names = args[0], args[1:]
strip = "--nodoc" in names
names = [n for n in names if n != "--nodoc"]
for f in glob.glob(os.path.join(BASE, ("*" + sub) if sub.endswith(".md") else ("*" + sub + "*.md"))):
    for pkg, name, body in sections(f):
        if name in names:
            if strip:
                body = re.sub(r"(?s)/\*\*.*?\*/\s*", "", body)
                body = re.sub(r"\n\s*\n", "\n", body)
            print(f"##### {pkg}.{name}")
            print(body.rstrip())
