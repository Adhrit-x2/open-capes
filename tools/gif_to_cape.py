"""Converts any GIF into a cape-ready GIF for Cape Creator.

It crops the middle of the GIF to the cape's 10:16 shape, resizes it (default 160x256, the mod's
highest quality; 40x64 suits chunky pixel art), and drops
frames evenly so it stays under the mod's 64-frame limit. The loop keeps its original speed.

Usage: python3 tools/gif_to_cape.py input.gif output.gif [--size 160|80|40|20|10] [--align top|center|bottom] [--fit]
  --size   cape width in pixels (height is 1.6x); default 160
  --align  which part of a tall image to keep (default: center)
  --fit    fit the whole image inside the cape instead of cropping (pads with the edge colour)
"""
import argparse
import math

from PIL import Image, ImageSequence

W, H = 160, 256
MAX_FRAMES = 64


def to_cape(frame, align, fit):
    frame = frame.convert("RGB")
    fw, fh = frame.size
    target = W / H
    if fit:
        scale = min(W / fw, H / fh)
        small = frame.resize((max(1, round(fw * scale)), max(1, round(fh * scale))), Image.BOX)
        canvas = Image.new("RGB", (W, H), small.getpixel((0, 0)))
        canvas.paste(small, ((W - small.width) // 2, (H - small.height) // 2))
        return canvas
    if fw / fh > target:            # too wide: crop the sides
        cw = round(fh * target)
        box = ((fw - cw) // 2, 0, (fw - cw) // 2 + cw, fh)
    else:                           # too tall: crop top/bottom
        ch = round(fw / target)
        top = {"top": 0, "center": (fh - ch) // 2, "bottom": fh - ch}[align]
        box = (0, top, fw, top + ch)
    return frame.crop(box).resize((W, H), Image.LANCZOS if (box[2] - box[0]) > W else Image.NEAREST)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--align", choices=["top", "center", "bottom"], default="center")
    ap.add_argument("--fit", action="store_true")
    ap.add_argument("--size", type=int, choices=[10, 20, 40, 80, 160], default=160)
    args = ap.parse_args()
    global W, H
    W, H = args.size, args.size * 16 // 10

    src = Image.open(args.input)
    frames, durations = [], []
    for f in ImageSequence.Iterator(src):
        frames.append(f.copy())
        durations.append(f.info.get("duration", 100) or 100)

    step = max(1, math.ceil(len(frames) / MAX_FRAMES))
    kept = frames[::step]
    # each kept frame lasts as long as the frames it replaces, so the loop keeps its speed
    total = sum(durations[: len(kept) * step])
    duration = max(50, round(total / len(kept) / 10) * 10)   # GIFs store hundredths of a second

    out = [to_cape(f, args.align, args.fit) for f in kept]
    out[0].save(args.output, save_all=True, append_images=out[1:], duration=duration, loop=0, disposal=1)
    print(f"{len(frames)} frames -> {len(out)} frames at {1000 / duration:.0f} fps, {W}x{H}: {args.output}")


if __name__ == "__main__":
    main()
