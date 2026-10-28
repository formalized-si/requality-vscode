// Makes the packages of the Requality MCP server (Requality without a window, for AI agents) in release/: one archive
// for each platform with a Java runtime in jre/ (the latest Eclipse Temurin 21 JRE of the platform, its SHA-256
// checked), and one archive without a runtime for the other platforms (it uses the Java of the system).
// An archive has bin/requality-mcp(.cmd), lib/, resources/ and the license files. The server is built first.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const { TARGETS, jrePackage, download, unpack, run } = require('./package-platforms');

const ROOT = path.join(__dirname, '..');
const { version } = require('../package.json');
const OUT = path.join(ROOT, 'release');
const STAGE = path.join(OUT, 'mcp-stage');
const SERVER = path.join(ROOT, 'dist', 'server');

function copy(from, to) {
  fs.cpSync(from, to, { recursive: true, preserveTimestamps: true });
}

/** The files of the package without a runtime, in a new folder. */
function stage(name) {
  const dir = path.join(STAGE, name);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  for (const f of ['bin', 'lib', 'resources', 'LICENSE-requality-upstream.txt', 'PROVENANCE.md']) {
    copy(path.join(SERVER, f), path.join(dir, f));
  }
  copy(path.join(ROOT, 'LICENSE'), path.join(dir, 'LICENSE'));
  copy(path.join(ROOT, 'docs', 'MCP.md'), path.join(dir, 'README.md'));
  fs.chmodSync(path.join(dir, 'bin', 'requality-mcp'), 0o755);
  return dir;
}

/** A zip archive for Windows (and the package without a runtime), a tar.gz archive for the other systems. */
function archive(dir, zip) {
  const name = path.basename(dir);
  const file = path.join(OUT, name + (zip ? '.zip' : '.tar.gz'));
  fs.rmSync(file, { force: true });
  if (!zip) {
    execFileSync('tar', ['-czf', file, '-C', STAGE, name], { stdio: 'inherit' });
  } else if (process.platform === 'linux') {
    execFileSync('zip', ['-qr', '-y', file, name], { cwd: STAGE, stdio: 'inherit' });
  } else {
    // bsdtar of macOS and Windows writes zip archives
    execFileSync('tar', ['-a', '-cf', file, name], { cwd: STAGE, stdio: 'inherit' });
  }
  console.log(file);
  return file;
}

async function main() {
  const only = process.argv.slice(2).filter(a => !a.startsWith('--'));
  // --no-runtime: only the archive without a Java runtime (quick, for tests)
  const noRuntime = process.argv.includes('--no-runtime');
  const targets = noRuntime ? [] : only.length ? only : Object.keys(TARGETS);
  for (const t of targets) {
    if (!TARGETS[t]) {
      throw new Error(`unknown target ${t}: ${Object.keys(TARGETS).join(', ')}`);
    }
  }
  if (!process.argv.includes('--no-build')) {
    run('npm', ['run', 'build:server']);
  }
  fs.mkdirSync(OUT, { recursive: true });
  try {
    for (const target of targets) {
      const pkg = await jrePackage(...TARGETS[target]);
      console.log(`${target}: ${pkg.name} (${pkg.release})`);
      const dir = stage(`requality-mcp-${version}-${target}`);
      unpack(await download(pkg), path.join(dir, 'jre'));
      archive(dir, target.startsWith('win32'));
    }
    if (!only.length || noRuntime) {
      archive(stage(`requality-mcp-${version}`), true);
    }
  } finally {
    fs.rmSync(STAGE, { recursive: true, force: true });
  }
}

main().catch(e => {
  console.error(e instanceof Error ? e.message : e);
  process.exit(1);
});
