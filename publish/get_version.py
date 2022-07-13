import subprocess
import sys
import os

ver = None

versionPath = os.path.dirname(sys.argv[0]) + "/version.txt"
if os.path.exists(versionPath):
    with open(versionPath, "r", encoding="utf8") as fp:
        ver = fp.read()
else:
    ver = subprocess.run(["git", "describe", "--tags", "--dirty"], capture_output=True, text=True).stdout or "UNKNOWN-" + subprocess.run(["git", "describe", "--always", "--dirty"], capture_output=True, text=True).stdout or "UNKNOWN"

ver = ver.strip()
if ver[0] == "v":
    ver = ver[1:]

print(ver)
