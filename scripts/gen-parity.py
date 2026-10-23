#!/usr/bin/env python3
"""Generates docs/PARITY.md: the Eclipse -> VS Code parity document of the Requality port.

Sections: summary counts, classes not ported (with reasons), ported classes with hand adaptations (lines -/+ vs the
mechanical port and their VSCODE comments), upstream bugs fixed in the port, command map (docs/command-map.json),
and the plugin.xml extension points used.

usage: python3 scripts/gen-parity.py            (writes docs/PARITY.md, deterministic output)
"""
import contextlib
import importlib.util
import io
import json
import os
import re
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPSTREAM = os.path.join(ROOT, 'server', 'upstream', 'eclipse-reference')
JAVA = os.path.join(UPSTREAM, 'java')
PORT_DIR = os.path.join(ROOT, 'server', 'src', 'main', 'java', 'com', 'unitesk', 'requality', 'vscode', 'port')
PLUGIN_XML = os.path.join(UPSTREAM, 'plugin.xml')
COMMAND_MAP = os.path.join(ROOT, 'docs', 'command-map.json')
OUT = os.path.join(ROOT, 'docs', 'PARITY.md')
COMMENT_MAX = 300
PLATFORM_XML = os.path.join(ROOT, 'server', 'src', 'main', 'resources', 'com', 'unitesk', 'requality', 'vscode', 'ui',
                            'runtime', 'platform-plugin.xml')
JAVA_SRC = os.path.join(ROOT, 'server', 'src', 'main', 'java', 'com', 'unitesk', 'requality', 'vscode', 'ui', 'runtime',
                        'ExtensionRegistry.java')


def ported_class_name(name):
    """ExtensionRegistry.portedClassName: the prefix rules of the Java method (read from its source)."""
    src = open(JAVA_SRC, encoding='utf-8').read()
    body = src[src.index('public static String portedClassName'):]
    body = body[:body.index('\n  }\n')]
    if name.startswith('com.unitesk.requality.vscode.'):
        return name
    # name.startsWith("A") ... return "B" + name.substring("A".length()) / equals ... return "B..."
    for m in re.finditer(r'name\.startsWith\("([^"]+)"\)[^;]*?\)\s*return "([^"]+)"\s*\+\s*name\.substring', body, re.S):
        if name.startswith(m.group(1)):
            return m.group(2) + name[len(m.group(1)):]
    # if (name.startsWith("A") || name.startsWith("B")) return "C" + name.substring(name.lastIndexOf('.') + 1)
    for m in re.finditer(r'if \(((?:\s*\|\|\s*)?name\.startsWith\("[^"]+"\)(?:\s*\|\|\s*name\.startsWith\("[^"]+"\))*)\)\s*'
                         r'return "([^"]+)"\s*\+\s*name\.substring\(name\.lastIndexOf\(\'\.\'\)\s*\+\s*1\)', body):
        if any(name.startswith(pfx) for pfx in re.findall(r'"([^"]+)"', m.group(1))):
            return m.group(2) + name[name.rindex('.') + 1:]
    for m in re.finditer(r'name\.equals\("([^"]+)"\)', body):
        if name == m.group(1):
            rest = body[m.end():]
            r = re.search(r'return "([^"]+)"(\s*\+\s*name\.substring\("([^"]+)"\.length\(\)\))?;', rest)
            if r:
                return r.group(1) + (name[len(r.group(3)):] if r.group(3) else '')
    if name.startswith('org.eclipse.core.resources.') and name.count('.') == 4:
        return 'com.unitesk.requality.vscode.ui.resources.' + name[len('org.eclipse.core.resources.'):]
    return name

# Upstream classes intentionally not ported, keyed by simple class name (none: every class of the plug-in is ported).
NOT_PORTED = {
}

BUG_RX = re.compile(r'upstream', re.I)
BUG_WORDS_RX = re.compile(r'\b(bug\w*|npe|never|editable|wrong|fix\w*|NullPointerException)\b', re.I)
VSCODE_RX = re.compile(r'(?://|/\*)\s*VSCODE:')


def load_check_port():
    spec = importlib.util.spec_from_file_location('check_port', os.path.join(ROOT, 'scripts', 'check-port.py'))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def upstream_classes():
    """Relative paths (under java/) of every upstream .java file, sorted."""
    out = []
    for d, _, fs in os.walk(JAVA):
        for f in fs:
            if f.endswith('.java'):
                out.append(os.path.relpath(os.path.join(d, f), JAVA).replace(os.sep, '/'))
    return sorted(out)


def extract_vscode_comments(text):
    """Return the joined text of every VSCODE comment in a ported file, in file order."""
    lines = text.split('\n')
    comments = []
    i = 0
    while i < len(lines):
        line = lines[i]
        m = VSCODE_RX.search(line)
        if not m:
            i += 1
            continue
        rest = line[m.end():]
        if line[m.start():m.end()].startswith('/*'):
            # block comment: take everything up to the closing marker
            parts = [rest]
            j = i
            while '*/' not in parts[-1] and j + 1 < len(lines):
                j += 1
                parts.append(lines[j].strip())
            joined = ' '.join(p.strip() for p in parts)
            joined = joined.split('*/')[0]
            i = j + 1
        else:
            parts = [rest]
            j = i
            # continuation: following pure // comment lines that are not themselves VSCODE markers
            while j + 1 < len(lines):
                nxt = lines[j + 1].strip()
                if not nxt.startswith('//') or VSCODE_RX.search(nxt):
                    break
                parts.append(nxt[2:])
                j += 1
            joined = ' '.join(p.strip() for p in parts)
            i = j + 1
        joined = re.sub(r'\s+', ' ', joined).strip()
        comments.append(joined)
    return comments


def truncate(s, n=COMMENT_MAX):
    return s if len(s) <= n else s[:n - 3].rstrip() + '...'


def md_cell(s):
    return s.replace('|', '\\|').replace('\n', ' ')


def simple_name(rel):
    return rel.rsplit('/', 1)[-1][:-len('.java')]


def main():
    cp = load_check_port()
    pc = cp.pc

    upstream = upstream_classes()
    assert len(upstream) == 320, 'expected 320 upstream classes, found %d' % len(upstream)

    # map simple names of the not-ported list to upstream paths
    by_simple = {}
    for rel in upstream:
        by_simple.setdefault(simple_name(rel), []).append(rel)
    missing = [n for n in NOT_PORTED if n not in by_simple]
    assert not missing, 'not-ported names not found upstream: %s' % missing
    ambiguous = [n for n in NOT_PORTED if len(by_simple[n]) != 1]
    assert not ambiguous, 'not-ported names are ambiguous: %s' % ambiguous
    not_ported_rels = {by_simple[n][0]: n for n in NOT_PORTED}

    ported, identical, adapted = [], [], []
    rows = []
    comment_count = 0
    upstream_bugs = []
    no_comment_adapted = []
    inconsistent = []
    for rel in upstream:
        if rel in not_ported_rels:
            # not ported: must not exist in the port tree
            tgt = pc.target_path(rel)
            if os.path.exists(tgt):
                inconsistent.append('%s is listed as not ported but exists at %s' % (rel, tgt))
            continue
        tgt = pc.target_path(rel)
        if not os.path.exists(tgt):
            inconsistent.append('%s is neither ported nor in the not-ported list' % rel)
            continue
        ported.append(rel)
        with contextlib.redirect_stdout(io.StringIO()):
            res = cp.check(rel, ROOT, False)
        removed, added = res[0], res[1]
        with open(tgt, encoding='utf-8') as f:
            text = f.read()
        comments = extract_vscode_comments(text)
        comment_count += len(comments)
        for c in comments:
            if BUG_RX.search(c) and BUG_WORDS_RX.search(c):
                upstream_bugs.append((rel, c))
        if removed == 0 and added == 0:
            if comments:
                inconsistent.append('%s shows -0 +0 but has %d VSCODE comment(s)' % (rel, len(comments)))
            identical.append(rel)
        else:
            adapted.append(rel)
            if not comments:
                no_comment_adapted.append(rel)
            rows.append((rel, removed, added, comments))

    total = len(upstream)
    assert len(ported) + len(not_ported_rels) == total, 'ported + not ported != 320'

    lines = []
    w = lines.append
    w('# Eclipse to VS Code parity')
    w('')
    w('Parity document of the Requality port: the Eclipse plug-in in `server/upstream/eclipse-reference` against the '
      'VS Code port in `server/src/main/java/com/unitesk/requality/vscode/port`.')
    w('')
    w('Generated by `scripts/gen-parity.py`; do not edit by hand. Regenerate with:')
    w('')
    w('```')
    w('python3 scripts/gen-parity.py')
    w('```')
    w('')
    w('Line counts (-/+) are the differences between each ported file and its mechanical port, as reported by '
      '`scripts/check-port.py`. `VSCODE:` comments mark the hand changes.')
    w('')

    w('## Summary')
    w('')
    w('| Item | Count |')
    w('|---|---:|')
    w('| Upstream classes | %d |' % total)
    w('| Ported | %d |' % len(ported))
    w('| Not ported | %d |' % len(not_ported_rels))
    w('| Ported, identical to the mechanical port (-0 +0) | %d |' % len(identical))
    w('| Ported, with hand adaptations | %d |' % len(adapted))
    w('| `VSCODE:` comments in ported classes | %d |' % comment_count)
    w('| Upstream bugs fixed (see below) | %d |' % len(upstream_bugs))
    w('')

    w('## Not ported')
    w('')
    w('| Class | Upstream path | Reason |')
    w('|---|---|---|')
    for name in sorted(NOT_PORTED):
        rel = by_simple[name][0]
        w('| %s | `%s` | %s |' % (name, rel, md_cell(NOT_PORTED[name])))
    if not NOT_PORTED:
        w('| (none) | | every class of the Eclipse plug-in is ported |')
    w('')

    w('## Ported classes with hand adaptations')
    w('')
    w('Lines -/+ are removed/added lines versus the mechanical port. Comments are the text of each `VSCODE:` marker '
      'in the ported file (continuation lines joined, truncated to %d characters).' % COMMENT_MAX)
    w('')
    w('| Class | Upstream path | -/+ | VSCODE comments |')
    w('|---|---|---:|---|')
    for rel, removed, added, comments in sorted(rows, key=lambda r: r[0]):
        if comments:
            cell = '<br>'.join(md_cell(truncate(c)) for c in comments)
        else:
            cell = '(no VSCODE comment)'
        w('| %s | `%s` | -%d +%d | %s |' % (simple_name(rel), rel, removed, added, cell))
    w('')

    w('## Upstream bugs fixed')
    w('')
    w('`VSCODE:` comments that mention an upstream defect (bug, NPE, never, editable, wrong, fix).')
    w('')
    if upstream_bugs:
        w('| Class | Upstream path | Comment |')
        w('|---|---|---|')
        for rel, c in sorted(upstream_bugs):
            w('| %s | `%s` | %s |' % (simple_name(rel), rel, md_cell(truncate(c))))
    else:
        w('None found.')
    w('')

    w('## Command map')
    w('')
    with open(COMMAND_MAP, encoding='utf-8') as f:
        commands = json.load(f)
    commands = sorted(commands, key=lambda c: c['command'])
    w('%d VS Code commands from `docs/command-map.json`.' % len(commands))
    w('')
    w('| VS Code command | Title | Eclipse origin |')
    w('|---|---|---|')
    for c in commands:
        w('| `%s` | %s | %s |' % (c['command'], md_cell(c.get('title', '')), md_cell(c.get('eclipse', '') or '')))
    w('')

    w('## Extension points used')
    w('')
    tree = ET.parse(PLUGIN_XML).getroot()
    used = {}
    for ext in tree.findall('extension'):
        point = ext.get('point')
        used[point] = used.get(point, 0) + 1
    declared = [e.get('id') for e in tree.findall('extension-point')]
    w('`plugin.xml` contains %d `<extension>` elements over %d distinct extension points. The plug-in also declares '
      '%d extension points of its own.' % (sum(used.values()), len(used), len(declared)))
    w('')
    w('| Extension point | Uses |')
    w('|---|---:|')
    for point in sorted(used):
        w('| `%s` | %d |' % (point, used[point]))
    w('')

    w('## Eclipse platform contributions')
    w('')
    w('The Requality Explorer binds contributions of Eclipse platform plug-ins (`viewerActionBinding` '
      '`org.eclipse.ui.navigator.resources.*`, `viewerContentBinding` `org.eclipse.ui.navigator.resources.filters.*`, '
      'the IDE property pages, wizards and commands); they are declared as the platform declares them in '
      '`server/src/main/resources/com/unitesk/requality/vscode/ui/runtime/platform-plugin.xml`, and the Requality '
      '`plugin.xml` names platform classes too. Each class is ported to the compatibility layer '
      '(`ExtensionRegistry.portedClassName`).')
    w('')
    w('| Extension point | Element | Id | Eclipse class | Port |')
    w('|---|---|---|---|---|')
    platform_rows = []
    for source in (PLATFORM_XML, PLUGIN_XML):
        root = ET.parse(source).getroot()
        for ext in root.findall('extension'):
            for el in ext.iter():
                for attr in ('class', 'contentProvider', 'labelProvider', 'defaultHandler'):
                    cls = el.get(attr)
                    if not cls or not (cls.startswith('org.eclipse.') or cls.startswith('com.unitesk.requality.vscode.')):
                        continue
                    port_name = ported_class_name(cls)
                    path = os.path.join(ROOT, "server", "src", "main", "java", *port_name.split(".")) + ".java"
                    path = path.split('$')[0] + ('.java' if '$' in path else '')
                    ok = os.path.isfile(path)
                    if not ok:
                        inconsistent.append('platform class %s (%s) has no port %s' % (cls, ext.get('point'), port_name))
                    platform_rows.append((ext.get('point'), el.tag, el.get('id') or el.get('commandId') or '', cls,
                                          ('`%s`' % port_name.replace('com.unitesk.requality.vscode.', '')) if ok else '**missing**'))
    for row in platform_rows:
        w('| `%s` | %s | %s | `%s` | %s |' % (row[0], row[1], md_cell(row[2]), row[3], row[4]))
    w('')

    with open(OUT, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))

    print('upstream %d, ported %d, not ported %d, identical %d, adapted %d, VSCODE comments %d, upstream bugs %d' % (
        total, len(ported), len(not_ported_rels), len(identical), len(adapted), comment_count, len(upstream_bugs)))
    print('extension points: %d used, %d <extension> elements' % (len(used), sum(used.values())))
    for msg in inconsistent:
        print('INCONSISTENT:', msg)
    for rel in no_comment_adapted:
        print('NOTE adapted without VSCODE comment:', rel)


if __name__ == '__main__':
    main()
