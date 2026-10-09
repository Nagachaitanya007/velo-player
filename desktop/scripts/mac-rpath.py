#!/usr/bin/env python3
"""Point bundled VLC libraries at each other so a Mac can load them without an installed VLC."""
import os
import subprocess
import sys

root = sys.argv[1]
lib_dir = os.path.join(root, "lib")
core = os.path.join(lib_dir, "libvlccore.dylib")
vlc = os.path.join(lib_dir, "libvlc.dylib")


def retarget(path: str, old: str, new: str) -> None:
    subprocess.run(["install_name_tool", "-change", old, new, path], check=False)


if os.path.exists(core):
    subprocess.run(["install_name_tool", "-id", "@loader_path/libvlccore.dylib", core], check=False)
if os.path.exists(vlc):
    subprocess.run(["install_name_tool", "-id", "@loader_path/libvlc.dylib", vlc], check=False)
    retarget(vlc, "@rpath/libvlccore.dylib", "@loader_path/libvlccore.dylib")

plugins = os.path.join(root, "plugins")
for dirpath, _, files in os.walk(plugins):
    for name in files:
        if not name.endswith(".dylib"):
            continue
        path = os.path.join(dirpath, name)
        rel_core = os.path.relpath(core, dirpath)
        rel_vlc = os.path.relpath(vlc, dirpath)
        retarget(path, "@rpath/libvlccore.dylib", "@loader_path/" + rel_core)
        retarget(path, "@rpath/libvlc.dylib", "@loader_path/" + rel_vlc)
