"""Render excerpts of a recorded MCP session, with no invented client UI or server answers."""
import json
import sys
import textwrap
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

recording = json.loads(Path(sys.argv[1]).read_text())
out = Path(sys.argv[2])
font_paths = [Path('/System/Library/Fonts/Menlo.ttc'),
              Path('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf')]
font_path = next((p for p in font_paths if p.exists()), None)
if font_path is None:
    raise SystemExit('Install Menlo or DejaVu Sans Mono to render the demo.')


def font(size):
    return ImageFont.truetype(str(font_path), size)


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(', ', ': '))


def excerpt(index, result):
    if index == 0:
        return [f'{k}: {v}' for k, v in result.items()]
    if index == 1:
        return [f'{k}: {v}' for k, v in result.items()]
    if index == 2:
        paragraphs = result['paragraphs']
        return [f'[{p.get("p", i)}] {p.get("text", compact(p))}' for i, p in enumerate(paragraphs)]
    if index == 3:
        return [f'change: {result["change"]}', f'requirement: {result["requirement"]}',
                f'source: {result["source"]["document"]}', f'paragraph: {result["source"]["p"]}',
                f'quote: {result["source"]["text"]}']
    if index == 4:
        lines = []
        for doc in result.get('documents', []):
            lines.extend([f'document: {doc["document"]}',
                          f'covered paragraphs: {doc["covered_paragraphs"]} / {doc["paragraphs"]}',
                          f'uncovered paragraphs: {doc["uncovered_paragraphs"]}'])
        reqs = result.get('requirements', {})
        for key, label in [('leaves_without_locations', 'requirements without sources'),
                           ('leaves_without_test_purposes', 'requirements without test purposes')]:
            if key in reqs:
                lines.append(f'{label}: {reqs[key]["total"]}')
        return lines or [compact(result)]
    if index == 5:
        summary = result['summary']
        lines = [f'unchanged: {summary["unchanged"]}   changed: {summary["changed"]}   lost: {summary["lost"]}',
                 f'new statements: {summary["new_sentences"]}']
        for req in result.get('requirements', []):
            lines.append(req['requirement'] + '  →  ' + req['status'])
            for loc in req.get('locations', []):
                for key in ['old_text', 'new_text', 'numbers_changed']:
                    if key in loc:
                        value = loc[key]
                        if isinstance(value, list):
                            value = ' '.join(str(v) for v in value)
                        lines.append(f'  {key}: {value}')
        for sentence in result.get('new_sentences', []):
            lines.append('untraced: ' + sentence['quote'])
        return lines
    if index == 6:
        lines = [f'change: {result["change"]}   state: {result["state"]}', f'tool: {result["tool"]}',
                 f'summary: {result["summary"]}']
        for op in result.get('operations', []):
            lines.append(op['op'] + ': ' + op.get('path', ''))
            for req in op.get('requirements', []):
                if req['status'] != 'unchanged':
                    lines.append(req['requirement'] + ': ' + req['status'])
        return lines
    return [f'{key}: {value}' for key, value in result.items()]


W, H = 1280, 800
bg, card = '#101416', '#181e21'
muted, fg, accent, cyan = '#9eaaa9', '#eff2eb', '#ff774f', '#9fe4d8'
frames = []
for index, scene in enumerate(recording['scenes']):
    image = Image.new('RGB', (W, H), bg)
    draw = ImageDraw.Draw(image)
    draw.text((40, 28), 'REQUALITY  /  AGENT WORKFLOW', font=font(29), fill=fg)
    draw.text((40, 72), 'Scripted MCP client · real server calls · responses excerpted', font=font(18), fill=muted)
    draw.line((40, 111, 1240, 111), fill='#34403f', width=1)
    for step, name in enumerate(['Connect', 'Import', 'Read', 'Trace', 'Coverage', 'Update', 'Review', 'Revert']):
        y = 155 + step * 62
        active = step == index
        if active:
            draw.rounded_rectangle((30, y - 8, 245, y + 37), radius=9, fill='#2e2922')
        draw.text((45, y), f'{step + 1:02}', font=font(20), fill=accent if active else '#667371')
        draw.text((88, y), name, font=font(22), fill=fg if active else muted)
    draw.rounded_rectangle((275, 137, 1240, 710), radius=15, fill=card, outline='#34403f')
    draw.text((304, 159), scene['title'], font=font(30), fill=fg)
    intent_lines = textwrap.wrap(scene['intent'], width=67)
    for row, line in enumerate(intent_lines):
        draw.text((305, 209 + row * 25), line, font=font(19), fill=muted)
    y = 278
    for call in scene['calls']:
        draw.text((305, y), '> ' + call, font=font(22), fill=cyan)
        y += 29
    draw.line((305, y + 5, 1210, y + 5), fill='#34403f', width=1)
    y += 23
    # Limit the displayed excerpt, keeping the full response in the adjacent JSON transcript.
    available = max(1, (675 - y) // 26)
    lines = []
    for line in excerpt(index, scene['result']):
        lines.extend(textwrap.wrap(line, width=67, subsequent_indent='  ', break_long_words=True) or [''])
    if len(lines) > available:
        lines = lines[:available - 1] + ['… full response in agent-flow.json']
    for line in lines:
        draw.text((305, y), line, font=font(21), fill=fg)
        y += 26
    draw.text((40, 746), 'Import → requirements → coverage → changed specification → verified revert',
              font=font(18), fill=muted)
    draw.rectangle((40, 784, 1240, 788), fill='#34403f')
    draw.rectangle((40, 784, 40 + int(1200 * (index + 1) / len(recording['scenes'])), 788), fill=accent)
    frames.append(image)

# A shared palette keeps static areas stable between frames. Each frame is a complete, readable tool step.
palette = Image.new('RGB', (W, H * len(frames)))
for i, frame in enumerate(frames):
    palette.paste(frame, (0, H * i))
palette = palette.quantize(colors=128)
encoded = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in frames]
encoded[0].save(out / 'agent-flow.gif', save_all=True, append_images=encoded[1:],
                duration=[4200] * (len(frames) - 1) + [6000], loop=0, disposal=2, optimize=True)
frames[0].save(out / 'agent-flow.png', optimize=True)
print(f'Rendered {len(frames)} real MCP steps to {out}')
