"""Draws the Open Capes icon as 32x32 pixel art and saves it scaled up (branding/)."""
import math
from PIL import Image

N = 32
img = Image.new("RGB", (N, N))
px = img.load()

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# background: deep night gradient with a soft glow behind the cape
for y in range(N):
    for x in range(N):
        c = mix((24, 20, 52), (58, 26, 88), (x + y) / (2 * N))
        d = math.hypot(x - 15.5, y - 15) / 16
        c = mix(c, (130, 80, 200), max(0.0, 0.6 - d) * 0.8)
        px[x, y] = c

# cape outline: top edge from the shoulder bar, flaring out, wavy hem
top, bottom_base = 6, 26
def half_width(y):
    t = (y - top) / (bottom_base - top)
    return 7.5 + 2.5 * t

CENTER = 16  # the cape is symmetric around this column

def hem(x):
    # scalloped hem: two-pixel dips, mirrored around the centre
    return bottom_base + (1 if (abs(x - CENTER) + 1) % 4 in (0, 1) else 0)

GOLD, GOLD_DARK, GOLD_LIGHT = (242, 196, 72), (176, 126, 36), (255, 236, 150)
cape_cells = set()
for y in range(top, 30):
    hw = half_width(min(y, bottom_base))
    for x in range(N):
        if abs(x - CENTER) <= hw and y <= hem(x):
            cape_cells.add((x, y))

for (x, y) in cape_cells:
    # body: diagonal teal -> violet gradient, darker toward the edges, with fold lines
    t = (y - top) / (bottom_base - top + 2)
    c = mix((40, 200, 210), (150, 70, 230), min(1, t * 0.8 + (x / N) * 0.4))
    edge = abs(x - CENTER) / half_width(min(y, bottom_base))
    c = mix(c, (20, 16, 50), edge ** 3 * 0.55)
    if abs(x - CENTER) == 4 and y > top + 3:
        c = mix(c, (20, 16, 50), 0.3)          # folds
    if abs(x - CENTER) == 3 and y > top + 3:
        c = mix(c, (255, 255, 255), 0.12)      # fold highlights
    px[x, y] = c

# gold trim on the cape's outer border
for (x, y) in cape_cells:
    if any((x + dx, y + dy) not in cape_cells for dx, dy in ((1, 0), (-1, 0), (0, 1))):
        px[x, y] = GOLD if y < 24 else GOLD_DARK

# emblem: a 4-point sparkle in the middle
cx, cy = 16, 15
for dx, dy, c in [(0, 0, (255, 255, 255)), (0, -1, GOLD_LIGHT), (0, 1, GOLD_LIGHT), (-1, 0, GOLD_LIGHT), (1, 0, GOLD_LIGHT),
                  (0, -2, GOLD), (0, 2, GOLD), (-2, 0, GOLD), (2, 0, GOLD), (0, -3, GOLD_DARK), (0, 3, GOLD_DARK)]:
    px[cx + dx, cy + dy] = c

# shoulder bar with clasps
for x in range(CENTER - 9, CENTER + 10):
    px[x, top - 1] = GOLD_DARK
    px[x, top - 2] = GOLD if abs(x - CENTER) <= 8 else GOLD_DARK
for x in (CENTER - 8, CENTER + 8):
    px[x, top - 2] = GOLD_LIGHT
    px[x, top - 3] = GOLD

# a few stars in the background
for x, y in ((4, 4), (27, 6), (5, 20), (28, 22), (25, 28), (3, 12)):
    if (x, y) not in cape_cells:
        px[x, y] = (230, 230, 255)

for size in (512, 128):
    img.resize((size, size), Image.NEAREST).save(f"branding/open-capes-icon-{size}.png")
img.save("branding/open-capes-icon-32.png")
print("saved branding/open-capes-icon-512.png")
