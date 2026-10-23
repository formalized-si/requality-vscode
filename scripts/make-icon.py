"""Draws the Marketplace icon (media/icon.png, 256x256): the symbol of media/requality.svg, white on a blue square."""
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..')
SIZE = 256
SCALE = 4  # drawn at 4x, then reduced for smooth edges
BACKGROUND = (57, 73, 171, 255)
WHITE = (255, 255, 255, 255)


def main():
    s = SIZE * SCALE
    unit = s / 32  # the 24x24 symbol in a 32x32 grid (a margin of 4 units)
    width = round(1.9 * unit)
    image = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=round(5 * unit), fill=BACKGROUND)

    def p(x, y):
        return (round((x + 4) * unit), round((y + 4) * unit))

    # the document
    draw.rounded_rectangle([p(3, 2.5), p(17, 21.5)], radius=round(1.5 * unit), outline=WHITE, width=width)
    # the lines of text
    for y, end in ((7, 13.5), (11, 13.5), (15, 10.5)):
        draw.line([p(6.5, y), p(end, y)], fill=WHITE, width=width)
    # the check mark: a white disc with a gap around it, the mark in the color of the square
    cx, cy = p(17.5, 18)
    r = round(4.6 * unit)
    gap = round(1.4 * unit)
    draw.ellipse([cx - r - gap, cy - r - gap, cx + r + gap, cy + r + gap], fill=BACKGROUND)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
    draw.line([p(15.4, 18.1), p(16.9, 19.6), p(19.7, 16.6)], fill=BACKGROUND, width=round(1.6 * unit), joint='curve')
    image.resize((SIZE, SIZE), Image.LANCZOS).save(os.path.join(ROOT, 'media', 'icon.png'), optimize=True)


if __name__ == '__main__':
    main()
