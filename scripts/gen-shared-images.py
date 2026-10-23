#!/usr/bin/env python3
"""Draws the workbench shared images (ISharedImages) as 16x16 PNGs into server/src/main/web/shared-images.

PNG (not SVG) because the server composes explorer icons with decorator overlays (Java2D reads PNG/GIF only).
"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'server', 'src', 'main', 'web', 'shared-images')
S = 4  # supersampling


def canvas():
    im = Image.new('RGBA', (16 * S, 16 * S), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def save(im, name):
    im.resize((16, 16), Image.LANCZOS).save(os.path.join(OUT, name + '.png'))


def P(*xy):
    return [v * S for v in xy]


def file_shape(d):
    d.polygon(P(4, 1.5, 9.5, 1.5, 13, 5, 13, 14.5, 4, 14.5), fill=(244, 244, 244), outline=(122, 122, 122), width=S)
    d.line(P(9.5, 1.5, 9.5, 5, 13, 5), fill=(122, 122, 122), width=S)


def folder_shape(d):
    d.polygon(P(1.5, 3.5, 6.5, 3.5, 8, 5, 14.5, 5, 14.5, 13.5, 1.5, 13.5), fill=(232, 184, 74), outline=(181, 135, 42), width=S)


def x_mark(d, color, w=1.8):
    d.line(P(4, 4, 12, 12), fill=color, width=int(w * S))
    d.line(P(12, 4, 4, 12), fill=color, width=int(w * S))


def main():
    os.makedirs(OUT, exist_ok=True)
    im, d = canvas(); x_mark(d, (197, 56, 59)); save(im, 'remove')
    im, d = canvas(); x_mark(d, (197, 56, 59), 2.2); save(im, 'delete')
    im, d = canvas(); d.rounded_rectangle(P(3, 3, 13, 13), radius=2 * S, fill=(78, 143, 214)); save(im, 'element')
    im, d = canvas(); file_shape(d); save(im, 'file')
    im, d = canvas(); folder_shape(d); save(im, 'folder')
    im, d = canvas(); folder_shape(d); d.rectangle(P(5, 7.5, 11, 11.5), fill=(78, 143, 214)); save(im, 'project')
    im, d = canvas(); d.ellipse(P(1.5, 1.5, 14.5, 14.5), fill=(217, 83, 79)); d.line(P(5.5, 5.5, 10.5, 10.5), fill='white', width=int(1.6 * S)); d.line(P(10.5, 5.5, 5.5, 10.5), fill='white', width=int(1.6 * S)); save(im, 'error')
    im, d = canvas(); d.polygon(P(8, 1.5, 15, 14.5, 1, 14.5), fill=(240, 173, 78)); d.line(P(8, 6, 8, 10), fill='white', width=int(1.6 * S)); d.ellipse(P(7.1, 11.3, 8.9, 13.1), fill='white'); save(im, 'warning')
    im, d = canvas(); d.ellipse(P(1.5, 1.5, 14.5, 14.5), fill=(58, 135, 173)); d.line(P(8, 7, 8, 11.5), fill='white', width=int(1.6 * S)); d.ellipse(P(7.1, 3.9, 8.9, 5.7), fill='white'); save(im, 'info')
    im, d = canvas(); d.ellipse(P(1.5, 1.5, 14.5, 14.5), fill=(58, 135, 173)); d.arc(P(5.4, 3.6, 10.6, 8.8), start=180, end=90, fill='white', width=int(1.6 * S)); d.line(P(8, 8.6, 8, 10.2), fill='white', width=int(1.6 * S)); d.ellipse(P(7.1, 11.3, 8.9, 13.1), fill='white'); save(im, 'question')
    for name, pts in [('back', (10, 3, 5, 8, 10, 13)), ('forward', (6, 3, 11, 8, 6, 13)), ('up', (3, 10, 8, 5, 13, 10))]:
        im, d = canvas(); d.line(P(*pts), fill=(78, 143, 214), width=2 * S, joint='curve'); save(im, name)
    im, d = canvas(); file_shape(d); d.line(P(11, 9, 11, 14), fill=(60, 154, 60), width=int(1.6 * S)); d.line(P(8.5, 11.5, 13.5, 11.5), fill=(60, 154, 60), width=int(1.6 * S)); save(im, 'new')
    im, d = canvas(); d.rectangle(P(2.5, 3.5, 13.5, 12.5), fill=(244, 244, 244), outline=(122, 122, 122), width=S); d.line(P(5.5, 6, 10.5, 10), fill=(197, 56, 59), width=int(1.4 * S)); d.line(P(10.5, 6, 5.5, 10), fill=(197, 56, 59), width=int(1.4 * S)); save(im, 'clear')
    im, d = canvas(); d.rectangle(P(2.5, 2.5, 13.5, 13.5), outline=(122, 122, 122), width=S); d.line(P(5, 8, 11, 8), fill=(78, 143, 214), width=int(1.8 * S)); save(im, 'collapseall')
    im, d = canvas(); d.arc(P(4, 4, 12, 12), 180, 330, fill=(78, 143, 214), width=int(1.6 * S)); d.arc(P(4, 4, 12, 12), 0, 150, fill=(78, 143, 214), width=int(1.6 * S)); save(im, 'synced')


if __name__ == '__main__':
    main()
