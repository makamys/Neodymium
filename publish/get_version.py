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

print(ver.strip())
