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
    # Original pixel art in the style of the modern plain shield: vertical oak planks in an iron frame.
    img = blank(64, 64)
    wood = [(162, 123, 76, 255), (150, 112, 68, 255), (172, 132, 83, 255), (141, 105, 63, 255)]
    seam = (99, 72, 42, 255)
    grain = (128, 94, 56, 255)
    iron = (186, 186, 190, 255)
    iron_mid = (148, 148, 154, 255)
    iron_dark = (96, 96, 102, 255)

    def plank(x, y):
        # Planks 3 px wide with a dark seam, a few grain marks.
        if x % 3 == 2:
            return seam
        col = wood[(x // 3) % 4]
        if (y * 5 + (x // 3) * 7) % 11 == 0 and x % 3 == 0:
            return grain
        return col

    def fill(x0, y0, w, h, fn):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                img[y][x] = fn(x - x0, y - y0)

    def framed(x, y, w, h, inner):
        # 1 px iron frame: light on top/left, dark on bottom/right, rivets at the corners.
        if (x, y) in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            return iron_dark
        if y == 0 or x == 0:
            return iron
        if y == h - 1 or x == w - 1:
            return iron_dark
        return inner(x, y)

    # Plate faces: front (1,1), back (14,1); top/bottom edges (1,0)/(13,0); left/right edges (0,1)/(13,1).
    fill(1, 1, 12, 22, lambda x, y: framed(x, y, 12, 22, plank))
    fill(14, 1, 12, 22, lambda x, y: framed(x, y, 12, 22, lambda a, b: plank(11 - a, b)))
    fill(1, 0, 12, 1, lambda x, y: iron_mid)
    fill(13, 0, 12, 1, lambda x, y: iron_dark)
    fill(0, 1, 1, 22, lambda x, y: iron_mid)
    fill(13, 1, 1, 22, lambda x, y: iron_mid)
    # Handle: plain dark oak.
    fill(26, 0, 16, 12, lambda x, y: (112, 80, 46, 255) if (x + 2 * y) % 5 else (96, 68, 39, 255))
    write_png(os.path.join(ROOT, "entity", "shield.png"), 64, 64, img)

    # Flat icon (only used where the 3D model can't be drawn): the front of the shield.
    icon = blank(16, 16)
    for y in range(1, 15):
        inset = 0 if y < 11 else (y - 10)
        x0, x1 = 3 + inset, 12 - inset
        for x in range(x0, x1 + 1):
            if y == 1 or x == x0:
                icon[y][x] = iron
            elif y == 14 or x == x1:
                icon[y][x] = iron_dark
            else:
                icon[y][x] = plank(x - 4, y)
    write_png(os.path.join(ROOT, "items", "shield.png"), 16, 16, icon)


if __name__ == "__main__":
    hud()
    sweep()
    shield()
    print("Textures written to", os.path.normpath(ROOT))
