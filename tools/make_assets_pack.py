#!/usr/bin/env python3
"""Builds a resource pack with the original modern attack sounds and sprites for Modern Combat.

Mojang's assets cannot be redistributed with the mod, so this script copies them from YOUR OWN
Minecraft installation (any version 1.20.2 or newer that you have launched at least once with the
official launcher).

Usage:
    python3 make_assets_pack.py                       # auto-detect .minecraft and the newest version
    python3 make_assets_pack.py --version 1.21.1
    python3 make_assets_pack.py --minecraft-dir "D:/Games/.minecraft" --output ModernCombat-Assets.zip

Put the resulting zip into the resourcepacks folder of your 1.7.10 instance and enable it.
"""
import argparse
import json
import os
import re
import sys
import zipfile

SOUND_EVENTS = ["crit", "knockback", "nodamage", "strong", "sweep", "weak"]
SOUND_PREFIX = "minecraft/sounds/entity/player/attack/"

JAR_TEXTURES = {
    "assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_background.png":
        "assets/moderncombat/textures/gui/sprites/hud/crosshair_attack_indicator_background.png",
    "assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png":
        "assets/moderncombat/textures/gui/sprites/hud/crosshair_attack_indicator_progress.png",
    "assets/minecraft/textures/gui/sprites/hud/crosshair_attack_indicator_full.png":
        "assets/moderncombat/textures/gui/sprites/hud/crosshair_attack_indicator_full.png",
    "assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_background.png":
        "assets/moderncombat/textures/gui/sprites/hud/hotbar_attack_indicator_background.png",
    "assets/minecraft/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png":
        "assets/moderncombat/textures/gui/sprites/hud/hotbar_attack_indicator_progress.png",
}
for _i in range(8):
    JAR_TEXTURES["assets/minecraft/textures/particle/sweep_%d.png" % _i] = \
        "assets/moderncombat/textures/particle/sweep_%d.png" % _i


def default_minecraft_dir():
    if sys.platform.startswith("win"):
        return os.path.join(os.environ.get("APPDATA", ""), ".minecraft")
    if sys.platform == "darwin":
        return os.path.expanduser("~/Library/Application Support/minecraft")
    return os.path.expanduser("~/.minecraft")


def version_key(name):
    nums = [int(n) for n in re.findall(r"\d+", name)]
    return nums


def find_version(mc_dir, wanted):
    versions_dir = os.path.join(mc_dir, "versions")
    if not os.path.isdir(versions_dir):
        sys.exit("No 'versions' folder in %s. Use --minecraft-dir." % mc_dir)
    candidates = []
    for name in os.listdir(versions_dir):
        jar = os.path.join(versions_dir, name, name + ".jar")
        meta = os.path.join(versions_dir, name, name + ".json")
        if os.path.isfile(jar) and os.path.isfile(meta) and re.fullmatch(r"[\d.]+", name):
            candidates.append(name)
    if wanted:
        if wanted not in candidates:
            sys.exit("Version %s not found. Available: %s" % (wanted, ", ".join(sorted(candidates, key=version_key))))
        return wanted
    modern = [v for v in candidates if version_key(v) >= [1, 20, 2]]
    if not modern:
        sys.exit("No Minecraft 1.20.2+ installation found. Launch a modern version once in the official launcher.")
    return max(modern, key=version_key)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--minecraft-dir", default=default_minecraft_dir())
    parser.add_argument("--version", default=None)
    parser.add_argument("--output", default="ModernCombat-Assets.zip")
    args = parser.parse_args()

    mc_dir = args.minecraft_dir
    version = find_version(mc_dir, args.version)
    print("Using Minecraft %s from %s" % (version, mc_dir))

    with open(os.path.join(mc_dir, "versions", version, version + ".json"), encoding="utf-8") as f:
        asset_index = json.load(f)["assetIndex"]["id"]
    index_path = os.path.join(mc_dir, "assets", "indexes", asset_index + ".json")
    with open(index_path, encoding="utf-8") as f:
        objects = json.load(f)["objects"]

    sounds = {event: [] for event in SOUND_EVENTS}
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as out:
        out.writestr("pack.mcmeta", json.dumps(
            {"pack": {"pack_format": 1, "description": "Modern Combat: attack sounds and sprites from Minecraft " + version}},
            indent=2))

        for key, info in sorted(objects.items()):
            if not key.startswith(SOUND_PREFIX) or not key.endswith(".ogg"):
                continue
            file_name = key[len(SOUND_PREFIX):]
            event = re.sub(r"\d+\.ogg$", "", file_name)
            if event not in sounds:
                continue
            digest = info["hash"]
            src = os.path.join(mc_dir, "assets", "objects", digest[:2], digest)
            if not os.path.isfile(src):
                print("  missing sound object for %s (launch %s once to download it)" % (key, version))
                continue
            out.write(src, "assets/moderncombat/sounds/attack/" + file_name)
            sounds[event].append("attack/" + file_name[:-4])

        sounds_json = {"attack." + event: {"category": "player", "sounds": files}
                       for event, files in sounds.items() if files}
        out.writestr("assets/moderncombat/sounds.json", json.dumps(sounds_json, indent=2))

        copied = 0
        with zipfile.ZipFile(os.path.join(mc_dir, "versions", version, version + ".jar")) as jar:
            names = set(jar.namelist())
            for src, dst in JAR_TEXTURES.items():
                if src in names:
                    out.writestr(dst, jar.read(src))
                    copied += 1
                else:
                    print("  texture not found in jar: " + src)

    print("Sounds: %d events, textures: %d files" % (len(sounds_json), copied))
    print("Written %s - copy it to <instance>/resourcepacks and enable it." % os.path.abspath(args.output))


if __name__ == "__main__":
    main()
