#!/usr/bin/env python3
"""Generates the original placeholder textures shipped with Modern Combat.

Pure standard library. Run from the repository root:
    python3 tools/generate_textures.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                    "modern-combat", "src", "main", "resources", "assets", "moderncombat", "textures")


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a)."""
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def blank(w, h):
    return [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]


def hud():
    out = os.path.join(ROOT, "gui", "sprites", "hud")
    # Crosshair indicator: drawn with an inverting blend, so white inverts and black leaves the screen as is.
    bg = blank(16, 4)
    for y in range(4):
        for x in range(16):
            edge = y in (0, 3) or x in (0, 15)
            bg[y][x] = (90, 90, 90, 255) if edge else (40, 40, 40, 255)
    write_png(os.path.join(out, "crosshair_attack_indicator_background.png"), 16, 4, bg)

    prog = blank(16, 4)
    for y in range(4):
        for x in range(16):
            prog[y][x] = (255, 255, 255, 255) if 0 < y < 3 or x in (0, 15) else (200, 200, 200, 255)
    write_png(os.path.join(out, "crosshair_attack_indicator_progress.png"), 16, 4, prog)

    # "Ready" icon: a small diagonal blade with a cross guard.
    full = blank(16, 16)
    for i in range(4, 12):
        full[15 - i][i] = (255, 255, 255, 255)
        full[15 - i][i - 1] = (200, 200, 200, 255)
    for d in range(-2, 3):
        x, y = 5 + d, 10 + d
        if 0 <= x < 16 and 0 <= y < 16:
            full[y][x] = (255, 255, 255, 255)
    full[12][3] = (220, 220, 220, 255)
    full[13][2] = (220, 220, 220, 255)
    write_png(os.path.join(out, "crosshair_attack_indicator_full.png"), 16, 16, full)

    # Hotbar indicator: a framed slot that fills from the bottom.
    hbg = blank(18, 18)
    for y in range(18):
        for x in range(18):
            edge = y in (0, 17) or x in (0, 17)
            hbg[y][x] = (20, 20, 20, 220) if edge else (60, 60, 60, 160)
    write_png(os.path.join(out, "hotbar_attack_indicator_background.png"), 18, 18, hbg)

    hprog = blank(18, 18)
    for y in range(18):
        for x in range(18):
            edge = y in (0, 17) or x in (0, 17)
            hprog[y][x] = (20, 20, 20, 220) if edge else (235, 235, 235, 230)
    write_png(os.path.join(out, "hotbar_attack_indicator_progress.png"), 18, 18, hprog)


def sweep():
    out = os.path.join(ROOT, "particle")
    size = 32
    cx, cy = size / 2.0, size * 1.05
    for frame in range(8):
        t = frame / 7.0
        img = blank(size, size)
        # A crescent swinging from right to left, widening then fading out.
        span = math.pi * (0.35 + 0.55 * t)
        start = math.pi * 1.5 - span / 2
        radius = size * 0.6
        thickness = 2.5 + 3.0 * math.sin(math.pi * min(1.0, t * 1.3))
        fade = 1.0 - 0.85 * max(0.0, (t - 0.55) / 0.45)
        for y in range(size):
            for x in range(size):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                r = math.hypot(dx, dy)
                ang = math.atan2(dy, dx) % (2 * math.pi)
                rel = (ang - start) % (2 * math.pi)
                if rel > span:
                    continue
                along = rel / span
                local = thickness * math.sin(math.pi * along)
                dist = abs(r - radius)
                if dist > local:
                    continue
                edge = 1.0 - dist / max(local, 0.01)
                a = int(255 * fade * min(1.0, edge * 1.8))
                v = int(180 + 75 * edge)
                if a > 0:
                    img[y][x] = (v, v, v, a)
        write_png(os.path.join(out, "sweep_%d.png" % frame), size, size, img)


def shield():
    # Model texture, same box layout as modern Minecraft (64x64): plate 12x22x1 at (0,0), handle 2x6x6 at (26,0).
    img = blank(64, 64)
    wood = [(143, 104, 62, 255), (126, 91, 54, 255), (158, 117, 71, 255)]
    dark = (88, 62, 35, 255)
    iron = (170, 170, 176, 255)
    iron_dark = (118, 118, 124, 255)

    def plank(x, y):
        col = wood[(x // 3) % 3]
        if x % 3 == 0 or (y * 7 + x * 3) % 23 == 0:
            return dark
        return col

    def fill(x0, y0, w, h, fn):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img[y][x] = fn(x - x0, y - y0)

    # Plate faces: front (1,1) 12x22 with an iron rim and boss, back (14,1) plain planks, edges in iron.
    def front(x, y):
        if x == 0 or x == 11 or y == 0 or y == 21:
            return iron_dark if (x + y) % 2 else iron
        if 4 <= x <= 7 and 9 <= y <= 12:
            return iron if (x in (5, 6) and y in (10, 11)) else iron_dark
        return plank(x, y)
    fill(1, 1, 12, 22, front)
    fill(14, 1, 12, 22, lambda x, y: plank(x + 1, y + 3))
    fill(1, 0, 12, 1, lambda x, y: iron)
    fill(13, 0, 12, 1, lambda x, y: iron_dark)
    fill(0, 1, 1, 22, lambda x, y: iron_dark)
    fill(13, 1, 1, 22, lambda x, y: iron)
    # Handle: dark wood all around.
    fill(26, 0, 16, 12, lambda x, y: dark if (x + y) % 4 else (104, 74, 42, 255))
    write_png(os.path.join(ROOT, "entity", "shield.png"), 64, 64, img)

    # Inventory icon: the front of the shield.
    icon = blank(16, 16)
    for y in range(1, 15):
        inset = 0 if y < 11 else (y - 10)
        for x in range(3 + inset, 13 - inset):
            edge = x in (3 + inset, 12 - inset) or y in (1, 14 - 0) or (y >= 11 and (x == 3 + inset or x == 12 - inset))
            if y == 1 or edge:
                icon[y][x] = iron if (x + y) % 2 else iron_dark
            elif 6 <= x <= 9 and 6 <= y <= 9:
                icon[y][x] = iron if x in (7, 8) and y in (7, 8) else iron_dark
            else:
                icon[y][x] = plank(x, y)
    write_png(os.path.join(ROOT, "items", "shield.png"), 16, 16, icon)


if __name__ == "__main__":
    hud()
    sweep()
    shield()
    print("Textures written to", os.path.normpath(ROOT))
