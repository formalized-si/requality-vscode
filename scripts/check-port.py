#!/usr/bin/env python3
"""Review aid for ported classes: shows how each ported class differs from the mechanical port of its original
(scripts/port-class.py), i.e. exactly the hand-made adaptations, and flags suspicious patterns.

usage: python3 scripts/check-port.py [--root <dir>] [--diff] [<class paths relative to eclipse-reference/java>...]
       (no classes: every ported class under server/src/main/java/.../vscode/port)
"""
import difflib
import importlib.util
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_spec = importlib.util.spec_from_file_location('port_class', os.path.join(ROOT, 'scripts', 'port-class.py'))
pc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(pc)

SUSPICIOUS = [
    (re.compile(r'UnsupportedOperationException'), 'UnsupportedOperationException'),
    (re.compile(r'\bTODO\b.*(port|vscode)', re.I), 'TODO left by porting'),
    (re.compile(r'^\s*import org\.eclipse\.', re.M), 'org.eclipse import left'),
]


def norm(lines):
    out = []
    for l in lines:
        s = l.rstrip()
        if s.startswith('import '):
            continue
        out.append(s)
    return out


def check(rel, root, show_diff):
    orig = os.path.join(ROOT, 'server', 'upstream', 'eclipse-reference', 'java', rel)
    tgt = pc.target_path(rel)
    tgt = os.path.join(root, os.path.relpath(tgt, ROOT))
    if not os.path.exists(tgt):
        return None
    with open(orig, encoding='utf-8') as f:
        mech = pc.port_text(f.read()).split('\n')
    with open(tgt, encoding='utf-8') as f:
        cur_text = f.read()
    cur = cur_text.split('\n')
    a, b = norm(mech), norm(cur)
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    removed = added = 0
    hunks = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == 'equal':
            continue
        removed += i2 - i1
        added += j2 - j1
        hunks.append((i1, i2, j1, j2))
    vscode = len(re.findall(r'//\s*VSCODE:', cur_text))
    flags = [msg for rx, msg in SUSPICIOUS if rx.search(cur_text)]
    # Removed lines that carried code (not blank/comment) without a VSCODE marker nearby.
    unmarked = 0
    for i1, i2, j1, j2 in hunks:
        code_removed = [l for l in a[i1:i2] if l.strip() and not l.strip().startswith('//')]
        window = '\n'.join(b[max(0, j1 - 3):j2 + 3])
        if code_removed and 'VSCODE' not in window:
            unmarked += 1
    print('%-80s -%-4d +%-4d VSCODE:%-3d unmarked-removals:%-3d %s' % (
        os.path.relpath(tgt, root), removed, added, vscode, unmarked, ' '.join(flags)))
    if show_diff and hunks:
        for line in difflib.unified_diff(a, b, 'mechanical', 'ported', n=1, lineterm=''):
            print('    ' + line)
    return removed, added, unmarked, flags


def all_ported(root):
    out = []
    base = os.path.join(ROOT, 'server', 'upstream', 'eclipse-reference', 'java')
    for d, _, fs in os.walk(base):
        for f in fs:
            if f.endswith('.java'):
                rel = os.path.relpath(os.path.join(d, f), base)
                if os.path.exists(os.path.join(root, os.path.relpath(pc.target_path(rel), ROOT))):
                    out.append(rel)
    return sorted(out)


def main():
    args = sys.argv[1:]
    root = ROOT
    show = False
    if '--root' in args:
        i = args.index('--root')
        root = args[i + 1]
        del args[i:i + 2]
    if '--diff' in args:
        show = True
        args.remove('--diff')
    rels = args or all_ported(root)
    for rel in rels:
        check(rel, root, show)


if __name__ == '__main__':
    main()
