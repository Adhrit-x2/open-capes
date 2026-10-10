"""Generates HD (40x64) animated capes in a clean "premium cosmetic" style:
dark bases, glowing edges, emblems and smooth gradients. All original designs.

Usage: python3 tools/generate_hd_capes.py   (writes capes/*.gif)
"""
import colorsys
import math
import os
import random

from PIL import Image

W, H = 40, 64          # 4x cape resolution
OUT = os.path.join(os.path.dirname(__file__), "..", "capes")
TAU = 2 * math.pi


# ---- helpers ---------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(v)))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def add(a, b, k=1.0):
    return tuple(a[i] + b[i] * k for i in range(3))


def hsv(h, s, v):
    return tuple(c * 255 for c in colorsys.hsv_to_rgb(h % 1, s, v))


def new_frame(fn):
    return [[fn(x, y) for x in range(W)] for y in range(H)]


def glow_layer(points, radius):
    """points: list of (x, y, intensity). Returns a W x H intensity map with soft falloff."""
    m = [[0.0] * W for _ in range(H)]
    r = int(math.ceil(radius * 2.5))
    for px, py, k in points:
        for y in range(max(0, int(py) - r), min(H, int(py) + r + 1)):
            for x in range(max(0, int(px) - r), min(W, int(px) + r + 1)):
                d2 = (x - px) ** 2 + (y - py) ** 2
                m[y][x] += k * math.exp(-d2 / (2 * radius * radius))
    return m


def apply_glow(f, m, color, strength=1.0):
    for y in range(H):
        for x in range(W):
            if m[y][x] > 0.002:
                f[y][x] = add(f[y][x], color, min(1.6, m[y][x]) * strength)


def border_path():
    """Points walking clockwise around the cape edge, 1px in."""
    pts = [(x, 1) for x in range(1, W - 1)]
    pts += [(W - 2, y) for y in range(2, H - 1)]
    pts += [(x, H - 2) for x in range(W - 3, 0, -1)]
    pts += [(1, y) for y in range(H - 3, 1, -1)]
    return pts


def save(name, frames, fps):
    os.makedirs(OUT, exist_ok=True)
    imgs = []
    for f in frames:
        img = Image.new("RGB", (W, H))
        img.putdata([tuple(clamp(c) for c in f[y][x]) for y in range(H) for x in range(W)])
        imgs.append(img)
    imgs[0].save(os.path.join(OUT, name + ".gif"), save_all=True, append_images=imgs[1:],
                 duration=round(1000 / fps), loop=0, optimize=False, disposal=1)


def dark_base(top=(14, 14, 24), bottom=(4, 4, 10)):
    return lambda x, y: mix(top, bottom, y / H)


# ---- designs -------------------------------------------------------------

def neon_edge(n=40):
    """Black cape whose edge glows, with two comets chasing around the border in shifting neon."""
    path = border_path()
    L = len(path)
    frames = []
    for i in range(n):
        t = i / n
        f = new_frame(lambda x, y: add(dark_base()(x, y), (30, 30, 50),
                                       0.25 if (x + y) % 8 == 0 or (x - y) % 8 == 0 else 0))
        cx, cy = (W - 1) / 2, H * 0.42
        diamond = []
        for k in range(160):
            a = k / 160 * TAU
            dx, dy = math.cos(a), math.sin(a)
            r = 1 / (abs(dx) / 8 + abs(dy) / 12)  # diamond: |x|/8 + |y|/12 = 1
            diamond.append((cx + dx * r, cy + dy * r, 0.12))
        pulse = 0.6 + 0.4 * math.sin(t * TAU * 2)
        apply_glow(f, glow_layer(diamond, 0.7), hsv(0.5 + t * 0.35, 0.85, 1), pulse)
        apply_glow(f, glow_layer([(cx, cy, 1.0)], 1.6), hsv(0.85 - t * 0.35, 0.7, 1), 0.8 * pulse)
        # dim always-on rim
        rim = [(x, y, 0.18) for x, y in path]
        apply_glow(f, glow_layer(rim, 0.8), hsv(0.55 + 0.3 * math.sin(t * TAU), 0.8, 1))
        for c in range(2):
            head = (t + c * 0.5) * L
            pts = []
            for k in range(22):
                x, y = path[int(head - k) % L]
                pts.append((x, y, (1 - k / 22) ** 1.5))
            col = hsv(0.5 + c * 0.35 + t * 0.2, 0.9, 1)
            apply_glow(f, glow_layer(pts, 1.1), col)
        frames.append(f)
    return frames


def aurora(n=40):
    """Night sky with drifting aurora curtains and a faint starfield."""
    rng = random.Random(21)
    stars = [(rng.randrange(W), rng.randrange(H), rng.random()) for _ in range(45)]
    frames = []
    for i in range(n):
        t = i / n * TAU
        f = new_frame(lambda x, y: mix((6, 10, 28), (2, 4, 12), y / H))
        for sx, sy, ph in stars:
            b = 0.35 + 0.35 * math.sin(t + ph * TAU)
            f[sy][sx] = add(f[sy][sx], (220, 230, 255), b)
        for band, (hue, off) in enumerate([(0.38, 0.0), (0.47, 2.1), (0.78, 4.0)]):
            for x in range(W):
                cy = H * (0.35 + 0.12 * band) + 6 * math.sin(x * 0.16 + t + off) + 3 * math.sin(x * 0.07 - t * 2 + off)
                for y in range(H):
                    d = y - cy
                    if -14 < d < 4:
                        # curtains: sharp bottom edge, long soft fade upward, vertical rays
                        k = math.exp(-(d * d) / 6) if d > 0 else math.exp(d / 7)
                        rays = 0.6 + 0.4 * math.sin(x * 1.3 + t * 3 + band)
                        f[y][x] = add(f[y][x], hsv(hue + d * 0.004, 0.75, 1), 0.55 * k * rays)
        frames.append(f)
    return frames


def emblem_pulse(n=32):
    """Deep navy cape with a geometric star emblem that breathes, plus a light sweep."""
    cx, cy = (W - 1) / 2, H * 0.42
    frames = []
    for i in range(n):
        t = i / n
        pulse = 0.5 + 0.5 * math.sin(t * TAU)
        f = new_frame(lambda x, y: mix((16, 22, 52), (6, 8, 22), y / H))
        # thin gold trim
        for x in range(W):
            for y in range(H):
                if x in (2, W - 3) or y in (2, H - 3):
                    if 2 <= x <= W - 3 and 2 <= y <= H - 3:
                        f[y][x] = (190, 150, 70)
        # 8-point star emblem drawn as a glow outline
        pts = []
        for k in range(400):
            a = k / 400 * TAU
            r = 11 - 6.5 * abs(math.sin(a * 4))
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a), 0.11))
        ring = [(cx + 15 * math.cos(a / 120 * TAU), cy + 15 * math.sin(a / 120 * TAU), 0.06) for a in range(120)]
        apply_glow(f, glow_layer(pts, 0.7), (255, 205, 110), 0.7 + 0.6 * pulse)
        apply_glow(f, glow_layer(ring, 0.7), (120, 170, 255), 0.5 + 0.5 * pulse)
        apply_glow(f, glow_layer([(cx, cy, 1.0)], 3.0 + 1.5 * pulse), (255, 230, 170), 0.8)
        # diagonal light sweep
        s = (t * 1.6 % 1.6 - 0.3) * (W + H)
        for y in range(H):
            for x in range(W):
                d = abs((x + y * 0.6) - s)
                if d < 3:
                    f[y][x] = add(f[y][x], (255, 255, 255), 0.12 * (1 - d / 3))
        frames.append(f)
    return frames


def gradient_flow(n=40):
    """Smooth sunset-to-violet gradient that flows downward, with soft light sweeps."""
    stops = [(255, 94, 98), (255, 170, 80), (255, 210, 120), (190, 90, 255), (80, 60, 220), (255, 94, 98)]

    def grad(p):
        p = p % 1 * (len(stops) - 1)
        i = min(int(p), len(stops) - 2)
        return mix(stops[i], stops[i + 1], p - i)

    frames = []
    for i in range(n):
        t = i / n
        f = new_frame(lambda x, y: grad(y / H * 0.6 + x / W * 0.15 - t))
        for y in range(H):
            for x in range(W):
                # dark vignette towards edges keeps it classy
                v = 1 - 0.35 * (abs(x - W / 2) / (W / 2)) ** 2
                f[y][x] = tuple(c * v for c in f[y][x])
                d = abs(((x - y * 0.8) / (W + H) - t * 2) % 1 - 0.5)
                if d < 0.03:
                    f[y][x] = add(f[y][x], (255, 255, 255), 0.35 * (1 - d / 0.03))
        frames.append(f)
    return frames


def crystal(n=32):
    """Icy faceted cape (Voronoi shards) with glints that sparkle across it."""
    rng = random.Random(9)
    seeds = [(rng.uniform(0, W), rng.uniform(0, H), rng.random()) for _ in range(26)]
    owner = [[min(range(len(seeds)), key=lambda s: (x - seeds[s][0]) ** 2 + (y - seeds[s][1]) ** 2)
              for x in range(W)] for y in range(H)]
    glints = [(rng.randrange(2, W - 2), rng.randrange(2, H - 2), rng.random()) for _ in range(9)]
    frames = []
    for i in range(n):
        t = i / n
        f = [[None] * W for _ in range(H)]
        for y in range(H):
            for x in range(W):
                s = owner[y][x]
                shade = seeds[s][2]
                edge = any(0 <= x + dx < W and 0 <= y + dy < H and owner[y + dy][x + dx] != s
                           for dx, dy in ((1, 0), (0, 1)))
                shimmer = 0.5 + 0.5 * math.sin((shade + t) * TAU)
                c = mix((30, 70, 130), (140, 210, 255), 0.25 + 0.5 * shade + 0.2 * shimmer)
                f[y][x] = mix(c, (230, 250, 255), 0.6) if edge else c
        pts = []
        for gx, gy, ph in glints:
            b = max(0.0, math.sin((t + ph) * TAU)) ** 6
            if b > 0.02:
                pts.append((gx, gy, 1.4 * b))
                for k in range(1, 4):  # 4-point sparkle arms
                    for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
                        pts.append((gx + dx, gy + dy, b * (1 - k / 4)))
        apply_glow(f, glow_layer(pts, 0.6), (255, 255, 255), 1.0)
        frames.append(f)
    return frames


def ember_runes(n=40):
    """Charcoal cape with a column of runes that ignite one after another, shedding embers."""
    rng = random.Random(4)
    # each rune: a few line segments in a 10x10 box
    def rune():
        segs = [((7, 0), (7, 12))]  # every rune has a spine
        for _ in range(3):
            a = (rng.randint(0, 2) * 7, rng.randint(0, 2) * 6)
            b = (rng.randint(0, 2) * 7, rng.randint(0, 2) * 6)
            if a != b:
                segs.append((a, b))
        return segs or [((0, 0), (10, 10))]
    runes = [rune() for _ in range(4)]
    embers = [(rng.uniform(4, W - 4), rng.random(), rng.uniform(0.6, 1.4)) for _ in range(16)]
    frames = []
    for i in range(n):
        t = i / n
        f = new_frame(lambda x, y: mix((26, 22, 24), (10, 8, 10), y / H))
        for r, segs in enumerate(runes):
            ox, oy = W / 2 - 7, 5 + r * 14.5
            phase = (t * 4 - r) % 4  # each rune lights in turn
            heat = max(0.3, math.exp(-((phase - 0.5) ** 2) * 2))
            pts = []
            for (ax, ay), (bx, by) in segs:
                steps = int(max(abs(bx - ax), abs(by - ay))) * 2 + 1
                for s in range(steps + 1):
                    u = s / steps
                    pts.append((ox + ax + (bx - ax) * u, oy + ay + (by - ay) * u, 0.35))
            apply_glow(f, glow_layer(pts, 0.6), (255, 120, 30), heat * 1.4)
            apply_glow(f, glow_layer(pts, 0.35), (255, 230, 150), heat * 0.8)
        pts = []
        for ex, ph, sp in embers:
            p = (ph + t * sp) % 1
            ey = H - p * H
            pts.append((ex + 2 * math.sin(p * 9 + ph * 6), ey, 0.8 * (1 - p)))
        apply_glow(f, glow_layer(pts, 0.5), (255, 150, 40), 1.0)
        frames.append(f)
    return frames


def sakura(n=48):
    """Dusky plum cape with a blossom branch and petals drifting down."""
    rng = random.Random(12)
    petals = [(rng.uniform(0, W), rng.random(), rng.uniform(0.7, 1.3), rng.random()) for _ in range(18)]
    branch = []
    x, y = W - 1.0, 4.0
    for _ in range(46):
        branch.append((x, y))
        x -= 0.75
        y += 0.8 + 0.4 * math.sin(x * 0.35)
    blossoms = [(bx + ox, by + oy) for (bx, by), (ox, oy) in
                zip((branch[k] for k in (7, 17, 27, 38)), ((0, -3), (-1, 3), (1, -3), (0, 3)))]
    frames = []
    for i in range(n):
        t = i / n
        f = new_frame(lambda x, y: mix((70, 30, 70), (20, 8, 30), y / H))
        for bx, by in branch:
            for dy in (0, 1):
                if 0 <= int(by) + dy < H and 0 <= int(bx) < W:
                    f[int(by) + dy][int(bx)] = (110, 62, 60) if dy == 0 else (70, 36, 40)
        sway = 0.4 * math.sin(t * TAU)
        pts = [(bx + 2.6 * math.cos(k / 5 * TAU + sway), by + 2.6 * math.sin(k / 5 * TAU + sway), 0.55)
               for bx, by in blossoms for k in range(5)]
        apply_glow(f, glow_layer(pts, 0.75), (255, 150, 195), 1.4)
        apply_glow(f, glow_layer([(bx, by, 0.6) for bx, by in blossoms], 0.5), (255, 240, 200), 0.8)
        for px, ph, sp, sw in petals:
            p = (ph + t * sp) % 1
            yy = p * (H + 4) - 2
            xx = (px - p * 10 + 2.5 * math.sin(p * TAU * 2 + sw * TAU)) % W
            for dx, dy, k in ((0, 0, 1), (1, 0, 0.6), (0, 1, 0.4)):
                X, Y = int(xx + dx) % W, int(yy + dy)
                if 0 <= Y < H:
                    f[Y][X] = mix(f[Y][X], (255, 185, 215), k)
        frames.append(f)
    return frames


DESIGNS = {
    "neon_edge": (neon_edge, 20),
    "aurora": (aurora, 12),
    "emblem_pulse": (emblem_pulse, 12),
    "gradient_flow": (gradient_flow, 12),
    "crystal": (crystal, 12),
    "ember_runes": (ember_runes, 12),
    "sakura": (sakura, 12),
}

if __name__ == "__main__":
    for name, (fn, fps) in DESIGNS.items():
        save(name, fn(), fps)
        print("wrote", os.path.normpath(os.path.join(OUT, name + ".gif")))
