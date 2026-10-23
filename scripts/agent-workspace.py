#!/usr/bin/env python3
"""Isolated workspaces for porting agents (see docs/PORTING-GUIDE.md).

  agent-workspace.py create <name> <classes.txt>   copy of the server sources with the agent's classes pre-ported
  agent-workspace.py compile <name>                compile the agent's classes (and the shims it changed)
  agent-workspace.py collect <name>                bring the agent's classes and shim changes into the repository

<classes.txt> lists classes of the Eclipse layer, relative to server/upstream/eclipse-reference/java, one per line
(e.g. com/unitesk/requality/eclipse/handlers/CopyNodeQId.java). A workspace compiles the agent's classes with javac
against a snapshot of the repository build, so agents do not interfere with each other. Shim changes are merged
back with a three-way merge against the snapshot taken at creation.
"""
import filecmp
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVER = os.path.join(ROOT, 'server')
JAVA_REL = os.path.join('src', 'main', 'java')
VSC_REL = os.path.join(JAVA_REL, 'com', 'unitesk', 'requality', 'vscode')
BASE = os.environ.get('REQUALITY_AGENTS', os.path.join(os.path.dirname(ROOT), 'requality-agents'))

sys.path.insert(0, os.path.join(ROOT, 'scripts'))
import importlib.util  # noqa: E402

_spec = importlib.util.spec_from_file_location('port_class', os.path.join(ROOT, 'scripts', 'port-class.py'))
port_class = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(port_class)


def ws_dir(name):
    return os.path.join(BASE, name)


def own_targets(ws):
    with open(os.path.join(ws, 'OWN.txt')) as f:
        rels = [l.strip() for l in f if l.strip() and not l.startswith('#')]
    out = []
    for rel in rels:
        t = port_class.target_path(rel)
        out.append(os.path.relpath(t, ROOT))
    return rels, out


def run(cmd, **kw):
    print('+ ' + ' '.join(cmd))
    return subprocess.run(cmd, check=True, **kw)


def classpath():
    out = subprocess.run([os.path.join(SERVER, 'gradlew'), '-q', '--offline', 'printClasspath'], cwd=SERVER,
                         check=True, capture_output=True, text=True).stdout.strip().splitlines()
    return out[-1]


def create(name, classes_file):
    ws = ws_dir(name)
    if os.path.exists(ws):
        sys.exit('workspace exists: ' + ws)
    run([os.path.join(SERVER, 'gradlew'), '-q', '--offline', 'compileJava'], cwd=SERVER)
    os.makedirs(ws)
    # Sources of the repository (without builds), scripts and docs.
    shutil.copytree(os.path.join(SERVER, 'src'), os.path.join(ws, 'server', 'src'))
    shutil.copytree(os.path.join(SERVER, 'upstream', 'eclipse-reference'),
                    os.path.join(ws, 'server', 'upstream', 'eclipse-reference'))
    shutil.copytree(os.path.join(ROOT, 'scripts'), os.path.join(ws, 'scripts'))
    shutil.copytree(os.path.join(ROOT, 'docs'), os.path.join(ws, 'docs'))
    # Snapshot for the three-way merge of shim changes.
    shutil.copytree(os.path.join(SERVER, VSC_REL), os.path.join(ws, '.base', 'vscode'))
    # Compile against the current build.
    cp_dir = os.path.join(ws, '.cp')
    shutil.copytree(os.path.join(SERVER, 'build', 'classes', 'java', 'main'), os.path.join(cp_dir, 'classes'))
    cp = classpath().split(os.pathsep)
    cp = [os.path.join(cp_dir, 'classes') if p.endswith(os.path.join('build', 'classes', 'java', 'main')) else p
          for p in cp]
    with open(os.path.join(cp_dir, 'classpath.txt'), 'w') as f:
        f.write(os.pathsep.join(cp))
    shutil.copy(classes_file, os.path.join(ws, 'OWN.txt'))
    rels, targets = own_targets(ws)
    # Mechanical first step for the agent's classes.
    for rel in rels:
        src = os.path.join(ROOT, 'server', 'upstream', 'eclipse-reference', 'java', rel)
        with open(src, encoding='utf-8') as f:
            text = port_class.port_text(f.read())
        dst = os.path.join(ws, os.path.relpath(port_class.target_path(rel), ROOT))
        if os.path.exists(os.path.join(ROOT, os.path.relpath(port_class.target_path(rel), ROOT))):
            print('already ported in the repository, kept: ' + rel)
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8') as f:
            f.write(text)
    with open(os.path.join(ws, 'compile.sh'), 'w') as f:
        f.write('#!/bin/sh\nREQUALITY_AGENTS="%s" exec python3 "%s" compile "%s"\n'
                % (BASE, os.path.join(ROOT, 'scripts', 'agent-workspace.py'), name))
    os.chmod(os.path.join(ws, 'compile.sh'), 0o755)
    print('workspace: ' + ws)
    for t in targets:
        print('  own: ' + t)


def changed_shims(ws):
    """Files under vscode/ (outside the agent's own classes) that differ from the snapshot or are new."""
    base = os.path.join(ws, '.base', 'vscode')
    cur = os.path.join(ws, 'server', VSC_REL)
    _, targets = own_targets(ws)
    own = set(os.path.normpath(os.path.join(ws, t)) for t in targets)
    out = []
    for d, _, fs in os.walk(cur):
        for fn in fs:
            if not fn.endswith('.java'):
                continue
            p = os.path.normpath(os.path.join(d, fn))
            if p in own:
                continue
            b = os.path.join(base, os.path.relpath(p, cur))
            if not os.path.exists(b) or not filecmp.cmp(p, b, shallow=False):
                out.append(p)
    return out


def compile_ws(name):
    ws = ws_dir(name)
    _, targets = own_targets(ws)
    files = [os.path.join(ws, t) for t in targets if os.path.exists(os.path.join(ws, t))]
    files += changed_shims(ws)
    out = os.path.join(ws, '.out')
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    with open(os.path.join(ws, '.cp', 'classpath.txt')) as f:
        cp = f.read().strip()
    cmd = ['javac', '-encoding', 'UTF-8', '-nowarn', '-proc:none', '-Xmaxerrs', '1000', '--release', '17',
           '-cp', cp, '-sourcepath', os.path.join(ws, 'server', JAVA_REL), '-implicit:none', '-d', out] + files
    r = subprocess.run(cmd, capture_output=True, text=True)
    sys.stdout.write(r.stdout)
    sys.stdout.write(r.stderr)
    errors = r.stderr.count(': error:')
    print('compiled %d files: %s' % (len(files), 'OK' if r.returncode == 0 else '%d errors' % errors))
    sys.exit(r.returncode)


def collect(name):
    ws = ws_dir(name)
    _, targets = own_targets(ws)
    for t in targets:
        src = os.path.join(ws, t)
        if not os.path.exists(src):
            print('missing (not ported?): ' + t)
            continue
        dst = os.path.join(ROOT, t)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy(src, dst)
        print('class: ' + t)
    base = os.path.join(ws, '.base', 'vscode')
    cur = os.path.join(ws, 'server', VSC_REL)
    conflicts = []
    for p in changed_shims(ws):
        rel = os.path.relpath(p, cur)
        repo = os.path.join(SERVER, VSC_REL, rel)
        b = os.path.join(base, rel)
        if not os.path.exists(repo):
            os.makedirs(os.path.dirname(repo), exist_ok=True)
            shutil.copy(p, repo)
            print('new: ' + rel)
            continue
        if not os.path.exists(b):
            conflicts.append(rel + ' (created by the agent and in the repository)')
            continue
        if filecmp.cmp(repo, b, shallow=False):
            shutil.copy(p, repo)
            print('shim: ' + rel)
            continue
        r = subprocess.run(['git', 'merge-file', '-L', 'repository', '-L', 'base', '-L', name, repo, b, p])
        if r.returncode != 0:
            conflicts.append(rel)
        else:
            print('merged: ' + rel)
    for c in conflicts:
        print('CONFLICT: ' + c)
    if conflicts:
        sys.exit(1)


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    cmd, name = sys.argv[1], sys.argv[2]
    if cmd == 'create':
        create(name, sys.argv[3])
    elif cmd == 'compile':
        compile_ws(name)
    elif cmd == 'collect':
        collect(name)
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
