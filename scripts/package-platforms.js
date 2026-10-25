// Makes the packages of a release in release/: one VSIX for each platform with a Java runtime in jre/ (the Eclipse
// Temurin 21 JRE of scripts/jre-lock.json, its SHA-256 checked against the lock), and one VSIX without a runtime for the
// other platforms (it uses the Java of the system). The server and the extension are built once, first.
//   node scripts/package-platforms.js --update-jre   writes the newest Temurin 21 JRE release into the lock
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

/** The pinned JRE: the Temurin release and the SHA-256 of the package of each platform. */
const LOCK = path.join(__dirname, 'jre-lock.json');

async function json(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url}: HTTP ${response.status}`);
  }
  return response.json();
}

/** The JRE package of the newest Java release for an operating system and architecture: name, link and checksum. */
async function latestJrePackage(osName, arch) {
  const [asset] = await json(`https://api.adoptium.net/v3/assets/latest/${JAVA}/hotspot?image_type=jre&os=${osName}&architecture=${arch}&vendor=eclipse`);
  if (!asset) {
    throw new Error(`no Java ${JAVA} runtime for ${osName} ${arch}`);
  }
  return { release: asset.release_name, ...asset.binary.package };
}

/**
 * The JRE package of the pinned release (scripts/jre-lock.json) for an operating system and architecture. Its SHA-256
 * must be the one of the lock, not only the one of the download site.
 */
async function jrePackage(osName, arch) {
  const lock = JSON.parse(fs.readFileSync(LOCK, 'utf8'));
  const target = Object.keys(TARGETS).find(t => TARGETS[t][0] === osName && TARGETS[t][1] === arch);
  const pinned = lock.packages[target];
  if (!pinned) {
    throw new Error(`${LOCK} has no package for ${target}`);
  }
  const release = await json(`https://api.adoptium.net/v3/assets/release_name/eclipse/${encodeURIComponent(pinned.release)}?image_type=jre&os=${osName}&architecture=${arch}&jvm_impl=hotspot`);
  const binary = (release.binaries || []).find(b => b.image_type === 'jre' && b.os === osName && b.architecture === arch);
  if (!binary) {
    throw new Error(`no JRE ${pinned.release} for ${osName} ${arch}`);
  }
  if (binary.package.name !== pinned.name || binary.package.checksum !== pinned.sha256) {
    throw new Error(`${binary.package.name} ${binary.package.checksum} is not the package of ${LOCK}: ${pinned.name} ${pinned.sha256}`);
  }
  return { release: pinned.release, ...binary.package };
}

/** Writes the newest Temurin release of each platform into the lock (the platforms can have different releases). */
async function updateLock() {
  const packages = {};
  for (const [target, [osName, arch]] of Object.entries(TARGETS)) {
    const pkg = await latestJrePackage(osName, arch);
    packages[target] = { release: pkg.release, name: pkg.name, sha256: pkg.checksum };
    console.log(`${target}: ${pkg.release}`);
  }
  fs.writeFileSync(LOCK, JSON.stringify({ java: JAVA, packages }, null, 2) + '\n');
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
  if (process.argv.includes('--update-jre')) {
    await updateLock();
    return;
  }
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
