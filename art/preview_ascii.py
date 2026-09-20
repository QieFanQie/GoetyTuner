"""ASCII preview of an item texture, composited over a mid-grey background.

Standalone helper for the 0.0.18 art round: the authoring agent has no image input,
so this is how the icon is inspected. Usage:  python art/preview_ascii.py <png>
"""

from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

RAMP = " .:-=+*#%@"
BG = (128, 128, 128)


def main() -> None:
    path = Path(sys.argv[1])
    im = Image.open(path).convert("RGBA")
    px = im.load()
    print(f"{path.name}  {im.width}x{im.height} {im.mode}")
    for y in range(im.height):
        row = ""
        for x in range(im.width):
            r, g, b, a = px[x, y]
            f = a / 255.0
            cr = r * f + BG[0] * (1 - f)
            cg = g * f + BG[1] * (1 - f)
            cb = b * f + BG[2] * (1 - f)
            lum = (0.299 * cr + 0.587 * cg + 0.114 * cb) / 255.0
            row += RAMP[min(9, max(0, int(lum * 9 + 0.5)))] * 2
        print(f"{y:2d} |{row}|")
    opaque = sum(1 for y in range(im.height) for x in range(im.width) if px[x, y][3] > 0)
    print(f"non-transparent pixels: {opaque}")
    print(f"distinct colours: {len({px[x, y] for y in range(im.height) for x in range(im.width)})}")


if __name__ == "__main__":
    main()
