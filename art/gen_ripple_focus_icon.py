"""Generate the item texture for the 0.0.18 "Tuner Ripple Focus" (调律波纹聚晶).

Project convention: art assets are produced procedurally / re-assembled with PIL
(see the 0.0.10 and 0.0.15 art rounds). This script is byte-reproducible: run it and
you get exactly the committed PNG.

Design rationale
----------------
Goety's own focus icons are *flat, low-colour-count* shapes -- `empty_focus.png` is
literally one dark disc with a slightly darker outline (7 distinct colours). So this
icon follows that language instead of trying to be a detailed illustration:

    a near-black disc (the "focus blank")
      + one bold ring and a bright core dot, both white
        = the accent ripple, i.e. what this focus casts.

Colour scheme (0.0.19, user request): **greyscale black/white**. The 0.0.18 revision
used the Tuner's violet cape ramp; the user asked for "原本紫色的部分变为黑色"
(the parts that were purple should become black), so everything purple is now
black / dark grey and the rings are white.

Compositing is done layer by layer with the standard "over" operator -- NOT by
summing colour contributions, which saturates into one indistinguishable blob.

Output: src/main/resources/assets/goetytuner/textures/item/tuner_ripple_focus.png
        16x16 RGBA8, no interlace, no palette.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

SIZE = 16
SS = 8  # supersampling factor -> anti-aliasing without any external dependency

OUT = (
    Path(__file__).resolve().parent.parent
    / "src/main/resources/assets/goetytuner/textures/item/tuner_ripple_focus.png"
)

# Internally everything is float RGBA with alpha in 0..1; only the final
# supersample -> 16x16 step converts back to bytes.
FRGBA = tuple[float, float, float, float]

# Palette: 0.0.19 全灰黑 —— 用户要求"原本紫色的部分变为黑色"（+ 白色圆环/圆心）。
OUTLINE = (8, 8, 8)          # 最外圈：近黑
BODY_TOP = (58, 58, 58)      # 盘面上缘：深灰
BODY_BOTTOM = (22, 22, 22)   # 盘面下缘：近黑
RING = (240, 240, 240)       # 亮环：白
CORE = (255, 255, 255)       # 圆心：纯白

CX, CY = 7.5, 7.5
DISC_R = 6.6
OUTLINE_W = 1.1

# The ripple motif drawn on the disc -- a bullseye: bright core dot, dark gap,
# bright ring, dark gap, dark outline. At 16x16 anything finer than this aliases
# into mush, so the band widths below are chosen to be >= 2px wide (half-width >= 1).
RING_R = 3.9
RING_HALF = 1.15
CORE_R = 1.5

TRANSPARENT: FRGBA = (0.0, 0.0, 0.0, 0.0)


def norm(colour: tuple[int, int, int], alpha: float) -> FRGBA:
    return (float(colour[0]), float(colour[1]), float(colour[2]),
            max(0.0, min(1.0, alpha)))


def over(fg: FRGBA, bg: FRGBA) -> FRGBA:
    """Standard source-over compositing (fg on top of bg), alpha in 0..1."""
    fa = fg[3]
    if fa <= 0.0:
        return bg
    ba = bg[3]
    oa = fa + ba * (1.0 - fa)
    if oa <= 0.0:
        return TRANSPARENT
    return (
        (fg[0] * fa + bg[0] * ba * (1.0 - fa)) / oa,
        (fg[1] * fa + bg[1] * ba * (1.0 - fa)) / oa,
        (fg[2] * fa + bg[2] * ba * (1.0 - fa)) / oa,
        oa,
    )


def disc(x: float, y: float) -> FRGBA:
    """The focus blank: a near-black disc with an even darker outline."""
    r = math.hypot(x - CX, y - CY)
    if r > DISC_R:
        return TRANSPARENT
    # 1px anti-aliased outer edge
    edge = min(1.0, (DISC_R - r) / 0.9)
    if DISC_R - r <= OUTLINE_W:
        return norm(OUTLINE, edge)
    # body: top-to-bottom gradient, so the disc is not flat
    t = max(0.0, min(1.0, (y - (CY - DISC_R)) / (2.0 * DISC_R)))
    body = tuple(BODY_TOP[i] + (BODY_BOTTOM[i] - BODY_TOP[i]) * t for i in range(3))
    return (body[0], body[1], body[2], edge)


def motif(x: float, y: float) -> FRGBA:
    """Bright core dot + one bold ring: the ripple this focus releases."""
    r = math.hypot(x - CX, y - CY)
    d = abs(r - RING_R)
    if d < RING_HALF:
        return norm(RING, (1.0 - d / RING_HALF) * 0.98)
    if r < CORE_R:
        # 0.0.19：圆心由"白→青"的渐变改成纯白（灰黑配色下不再引入第三色）
        return norm(CORE, (1.0 - (r / CORE_R) ** 2 * 0.12) * 0.98)
    return TRANSPARENT


def sample(x: float, y: float) -> FRGBA:
    return over(motif(x, y), disc(x, y))


def to_byte(c: FRGBA) -> tuple[int, int, int, int]:
    r, g, b, a = c
    ai = max(0, min(255, int(round(a * 255.0))))
    if ai == 0:
        return (0, 0, 0, 0)
    return (max(0, min(255, int(round(r)))), max(0, min(255, int(round(g)))),
            max(0, min(255, int(round(b)))), ai)


def main() -> None:
    big = Image.new("RGBA", (SIZE * SS, SIZE * SS), (0, 0, 0, 0))
    px = big.load()
    for sy in range(SIZE * SS):
        y = (sy + 0.5) / SS
        for sx in range(SIZE * SS):
            x = (sx + 0.5) / SS
            px[sx, sy] = to_byte(sample(x, y))

    img = big.resize((SIZE, SIZE), Image.LANCZOS)
    # Snap near-transparent pixels to fully transparent: keeps the PNG small and the
    # result deterministic across Pillow versions.
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            px[x, y] = (0, 0, 0, 0) if a < 16 else (r, g, b, a)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, format="PNG", optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes, {img.size[0]}x{img.size[1]} {img.mode})")


if __name__ == "__main__":
    main()
