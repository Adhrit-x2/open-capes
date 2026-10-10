"""Generates animated cape GIFs for Cape Creator. Drag one onto the in-game editor to use it.

Usage: python3 tools/generate_capes.py   (writes capes/*.gif)
"""
import colorsys
import math
import os
import random

from PIL import Image

W, H = 20, 32          # 2x cape resolution
OUT = os.path.join(os.path.dirname(__file__), "..", "capes")


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(stops, t):
    """stops: list of (position, rgb); t in 0..1"""
    t = max(0.0, min(1.0, t))
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            return lerp(c0, c1, (t - p0) / (p1 - p0) if p1 > p0 else 0)
    return stops[-1][1]


def save(name, frames, fps):
    os.makedirs(OUT, exist_ok=True)
    imgs = []
    for f in frames:
        img = Image.new("RGB", (W, H))
        img.putdata([f[y][x] for y in range(H) for x in range(W)])
        imgs.append(img)
    imgs[0].save(os.path.join(OUT, name + ".gif"), save_all=True, append_images=imgs[1:],
                 duration=round(1000 / fps), loop=0, optimize=False, disposal=1)
    return imgs


def blank(c=(0, 0, 0)):
    return [[c] * W for _ in range(H)]


# ---- designs -------------------------------------------------------------

def fire(n=24):
    """Rising flames (the classic DOOM fire algorithm), warmed up so it starts mid-burn."""
    rng = random.Random(7)
    pal = [gradient([(0, (8, 0, 0)), (0.2, (90, 5, 0)), (0.45, (210, 50, 0)),
                     (0.7, (255, 150, 10)), (0.9, (255, 230, 90)), (1, (255, 255, 220))], i / 36) for i in range(37)]
    heat = [[0] * W for _ in range(H)]
    heat[H - 1] = [36] * W

    def step():
        for y in range(H - 1):
            for x in range(W):
                r = rng.randint(0, 3)
                src = heat[y + 1][min(W - 1, max(0, x - r + 1))]
                heat[y][x] = max(0, src - rng.randint(0, 3))

    for _ in range(60):
        step()
    frames = []
    for _ in range(n):
        step()
        frames.append([[pal[heat[y][x]] for x in range(W)] for y in range(H)])
    return frames


def rainbow(n=24):
    """Diagonal rainbow bands that scroll forever, with a white shimmer line."""
    frames = []
    for i in range(n):
        t = i / n
        f = blank()
        for y in range(H):
            for x in range(W):
                hue = ((x + y) / 24 - t) % 1
                r, g, b = colorsys.hsv_to_rgb(hue, 0.85, 1)
                c = (int(r * 255), int(g * 255), int(b * 255))
                shimmer = ((x - y * 0.5) / 30 + t) % 1
                if shimmer < 0.04:
                    c = lerp(c, (255, 255, 255), 0.7)
                f[y][x] = c
        frames.append(f)
    return frames


def galaxy(n=32):
    """Deep space nebula with twinkling stars and a shooting star."""
    rng = random.Random(3)
    stars = [(rng.randrange(W), rng.randrange(H), rng.random()) for _ in range(28)]
    frames = []
    for i in range(n):
        t = i / n
        f = blank()
        for y in range(H):
            for x in range(W):
                neb = (math.sin(x * 0.45 + y * 0.2 + t * 2 * math.pi) +
                       math.sin(y * 0.33 - x * 0.15 - t * 2 * math.pi) + 2) / 4
                f[y][x] = gradient([(0, (6, 4, 22)), (0.5, (40, 12, 80)), (0.8, (120, 30, 140)),
                                    (1, (220, 90, 200))], neb * 0.9)
        for sx, sy, phase in stars:
            b = (math.sin((t + phase) * 2 * math.pi) + 1) / 2
            f[sy][sx] = lerp(f[sy][sx], (255, 255, 230), 0.3 + 0.7 * b)
        # shooting star crosses during the first half of the loop
        if t < 0.5:
            p = t / 0.5
            hx, hy = int(-4 + p * (W + 8)), int(2 + p * 12)
            for k in range(5):
                x, y = hx - k, hy - k // 2
                if 0 <= x < W and 0 <= y < H:
                    f[y][x] = lerp(f[y][x], (255, 255, 255), 1 - k / 5)
        frames.append(f)
    return frames


def matrix(n=32):
    """Green digital rain."""
    rng = random.Random(11)
    cols = [(rng.random() * H, rng.choice([1, 1, 2]), rng.randint(6, 14)) for _ in range(W)]
    glyph = [[rng.random() for _ in range(W)] for _ in range(H)]
    frames = []
    for i in range(n):
        f = blank((0, 6, 0))
        for x, (start, speed, length) in enumerate(cols):
            # speed is in rows per frame; n * speed must be a multiple of the loop height to loop
            loop = H + 16
            head = (start + i * speed * loop / n) % loop
            for k in range(length):
                y = int(head) - k
                if 0 <= y < H:
                    fade = 1 - k / length
                    base = (20, int(80 + 175 * fade), 30) if glyph[y][x] > 0.3 else (0, int(40 * fade), 0)
                    f[y][x] = (200, 255, 200) if k == 0 else base
        frames.append(f)
    return frames


def ocean(n=24):
    """Rows of waves rolling under a sunset sky, each with a foamy crest."""
    sea = [(40, 160, 215), (25, 120, 190), (15, 85, 160), (8, 55, 120), (4, 30, 80)]
    frames = []
    for i in range(n):
        t = i / n * 2 * math.pi
        f = blank()
        for y in range(H):
            for x in range(W):
                c = gradient([(0, (40, 30, 90)), (0.6, (230, 100, 80)), (1, (255, 190, 90))], y / 11)
                if (x - 13) ** 2 + (y - 10) ** 2 < 10:
                    c = (255, 225, 130)
                f[y][x] = c
        # back to front: each wave layer covers everything below its surface
        for k, color in enumerate(sea):
            base = 11 + k * 4.5
            for x in range(W):
                surface = base + 1.6 * math.sin(x * 0.55 + t * (1 if k % 2 else -1) + k * 1.7)
                top = int(round(surface))
                for y in range(max(0, top), H):
                    f[y][x] = color
                if 0 <= top < H:
                    f[top][x] = lerp(color, (235, 250, 255), 0.75)
        frames.append(f)
    return frames


def portal(n=24):
    """Swirling nether-portal vortex."""
    cx, cy = (W - 1) / 2, (H - 1) / 2
    frames = []
    for i in range(n):
        t = i / n * 2 * math.pi
        f = blank()
        for y in range(H):
            for x in range(W):
                dx, dy = (x - cx) / W * 2, (y - cy) / H * 2
                r = math.hypot(dx, dy * 0.8)
                a = math.atan2(dy, dx)
                v = (math.sin(a * 3 + r * 9 - t) + math.sin(r * 14 - t * 2) + 2) / 4
                c = gradient([(0, (25, 0, 50)), (0.5, (110, 20, 200)), (0.85, (190, 90, 255)),
                              (1, (250, 210, 255))], v * (1.1 - r * 0.5))
                f[y][x] = c
        # obsidian frame
        for y in range(H):
            for x in range(W):
                if x < 2 or x >= W - 2 or y < 2 or y >= H - 2:
                    f[y][x] = (20, 12, 30) if (x + y) % 3 else (45, 30, 70)
        frames.append(f)
    return frames


def storm(n=32):
    """Dark rolling clouds with a lightning strike."""
    rng = random.Random(5)
    bolt = []
    x = W // 2
    for y in range(4, H):
        x = max(1, min(W - 2, x + rng.choice([-1, 0, 0, 1])))
        bolt.append((x, y))
    frames = []
    for i in range(n):
        t = i / n * 2 * math.pi
        flash = {10: 1.0, 11: 0.35, 12: 0.9, 13: 0.4, 14: 0.15}.get(i, 0)
        f = blank()
        for y in range(H):
            for x in range(W):
                cloud = (math.sin(x * 0.5 + t) + math.sin(y * 0.35 - x * 0.2 + t * 2) + 2) / 4
                c = gradient([(0, (12, 14, 26)), (1, (60, 66, 90))], cloud * (1 - y / H * 0.6))
                f[y][x] = lerp(c, (200, 210, 255), flash * 0.5)
        if flash >= 0.35:
            for bx, by in bolt:
                f[by][bx] = (255, 255, 255)
                for nx in (bx - 1, bx + 1):
                    f[by][nx] = lerp(f[by][nx], (150, 180, 255), 0.6)
        frames.append(f)
    return frames


DESIGNS = {
    "fire": (fire, 12),
    "rainbow": (rainbow, 12),
    "galaxy": (galaxy, 10),
    "matrix": (matrix, 12),
    "ocean": (ocean, 8),
    "portal": (portal, 12),
    "storm": (storm, 12),
}

if __name__ == "__main__":
    for name, (fn, fps) in DESIGNS.items():
        save(name, fn(), fps)
        print("wrote", os.path.normpath(os.path.join(OUT, name + ".gif")))
