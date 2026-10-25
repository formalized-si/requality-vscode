// Makes the packages of a release in release/: one VSIX for each platform with a Java runtime in jre/ (the latest
// Eclipse Temurin 21 JRE of the platform, its SHA-256 checked), and one VSIX without a runtime for the other platforms
// (it uses the Java of the system). The server and the extension are built once, first.
const { execFileSync } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const { version } = require('../package.json');
// VS Code convention: an odd minor version is a pre-release (1.5.x), an even one a release (1.4.x, 1.6.x)
const PRE_RELEASE = Number(version.split('.')[1]) % 2 === 1 ? ['--pre-release'] : [];
const JAVA = 21;
/** The VS Code targets and the operating systems and architectures of Adoptium. */
const TARGETS = {
  'win32-x64': ['windows', 'x64'],
  'win32-arm64': ['windows', 'aarch64'],
  'linux-x64': ['linux', 'x64'],
  'linux-arm64': ['linux', 'aarch64'],
  'darwin-x64': ['mac', 'x64'],
  'darwin-arm64': ['mac', 'aarch64'],
};
const CACHE = path.join(ROOT, '.upstream-cache', 'jre');
const OUT = path.join(ROOT, 'release');
const JRE = path.join(ROOT, 'jre');

function run(command, args, env) {
  execFileSync(command, args, { cwd: ROOT, stdio: 'inherit', shell: process.platform === 'win32', env: { ...process.env, ...env } });
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

/** The JRE package of the latest Java release for an operating system and architecture: name, link and checksum. */
async function jrePackage(osName, arch) {
  const url = `https://api.adoptium.net/v3/assets/latest/${JAVA}/hotspot?image_type=jre&os=${osName}&architecture=${arch}&vendor=eclipse`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url}: HTTP ${response.status}`);
  }
  const [asset] = await response.json();
  if (!asset) {
    throw new Error(`no Java ${JAVA} runtime for ${osName} ${arch}`);
  }
  return { release: asset.release_name, ...asset.binary.package };
}

async function download(pkg) {
  fs.mkdirSync(CACHE, { recursive: true });
  const file = path.join(CACHE, pkg.name);
  if (!fs.existsSync(file) || sha256(file) !== pkg.checksum) {
    const response = await fetch(pkg.link);
    if (!response.ok) {
      throw new Error(`${pkg.link}: HTTP ${response.status}`);
    }
    fs.writeFileSync(file, Buffer.from(await response.arrayBuffer()));
  }
  const sum = sha256(file);
  if (sum !== pkg.checksum) {
    throw new Error(`${pkg.name}: SHA-256 ${sum}, expected ${pkg.checksum}`);
  }
  return file;
}

/** Unpacks a runtime into a folder (default jre/), with bin/java directly in it (the macOS archive has it in Contents/Home). */
function unpack(archive, dest = JRE) {
  fs.rmSync(dest, { recursive: true, force: true });
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-jre-'));
  if (archive.endsWith('.zip') && process.platform === 'linux') {
    execFileSync('unzip', ['-q', archive, '-d', tmp]);
  } else {
    execFileSync('tar', ['-xf', archive, '-C', tmp]);
  }
  const [top] = fs.readdirSync(tmp).filter(f => !f.startsWith('.'));
  const home = fs.existsSync(path.join(tmp, top, 'Contents', 'Home')) ? path.join(tmp, top, 'Contents', 'Home') : path.join(tmp, top);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.renameSync(home, dest);
  fs.rmSync(tmp, { recursive: true, force: true });
}

async function main() {
  const only = process.argv.slice(2).filter(a => !a.startsWith('--'));
  const targets = only.length ? only : Object.keys(TARGETS);
  for (const t of targets) {
    if (!TARGETS[t]) {
      throw new Error(`unknown target ${t}: ${Object.keys(TARGETS).join(', ')}`);
    }
  }
  if (!process.argv.includes('--no-build')) {
    run('npm', ['run', 'build']);
  }
  fs.mkdirSync(OUT, { recursive: true });
  // vscode:prepublish builds nothing: the build above made the files of the packages
  const env = { REQUALITY_PREBUILT: '1' };
  try {
    for (const target of targets) {
      const pkg = await jrePackage(...TARGETS[target]);
      console.log(`${target}: ${pkg.name} (${pkg.release})`);
      unpack(await download(pkg));
      run('npx', ['--no-install', 'vsce', 'package', '--target', target, '--githubBranch', 'main', ...PRE_RELEASE,
        '--out', path.join(OUT, `requality-vscode-${version}-${target}.vsix`)], env);
    }
  } finally {
    fs.rmSync(JRE, { recursive: true, force: true });
  }
  if (!only.length) {
    run('npx', ['--no-install', 'vsce', 'package', '--githubBranch', 'main', ...PRE_RELEASE,
      '--out', path.join(OUT, `requality-vscode-${version}.vsix`)], env);
  }
}

module.exports = { TARGETS, jrePackage, download, unpack, run };

if (require.main === module) {
  main().catch(e => {
    console.error(e instanceof Error ? e.message : e);
    process.exit(1);
  });
}
