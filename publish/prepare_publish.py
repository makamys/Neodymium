import json

open("gameVersions.txt", "w").write(" ".join(json.load(open("gameVersions.json", "r")).keys()))
