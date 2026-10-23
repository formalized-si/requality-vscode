import { execFile } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

/** Minimum Java feature release required by the Requality server (compiled with --release 17). */
export const MIN_JAVA = 17;

export interface JavaRuntime {
  executable: string;
  version: number;
  source: string;
}

function javaExecutable(home: string): string {
  return path.join(home, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
}

/**
 * The programs of a Java runtime that the server runs (the launcher, and jspawnhelper with which Java starts processes
 * on macOS and Linux) are made executable when the extension package did not keep their mode.
 */
function makeExecutable(home: string): void {
  if (process.platform === 'win32') {
    return;
  }
  const programs = [path.join(home, 'lib', 'jspawnhelper')];
  const bin = path.join(home, 'bin');
  if (fs.existsSync(bin)) {
    programs.push(...fs.readdirSync(bin).map(f => path.join(bin, f)));
  }
  for (const p of programs) {
    try {
      const mode = fs.statSync(p).mode;
      if ((mode & 0o111) !== 0o111) {
        fs.chmodSync(p, mode | 0o755);
      }
    } catch {
      // not in this runtime
    }
  }
}

/** Parses the feature release from `java -version` output ("1.8.0_x" -> 8, "17.0.2" -> 17). */
export function parseJavaVersion(output: string): number | undefined {
  const m = /version "([^"]+)"/.exec(output) ?? /openjdk (\d+[^\s]*)/i.exec(output);
  if (!m) {
    return undefined;
  }
  const parts = m[1].split(/[._+-]/);
  const first = Number(parts[0]);
  if (!Number.isFinite(first)) {
    return undefined;
  }
  return first === 1 ? Number(parts[1]) : first;
}

function probe(executable: string): Promise<number | undefined> {
  return new Promise(resolve => {
    execFile(executable, ['-version'], { timeout: 15000 }, (err, stdout, stderr) => {
      if (err && !stderr) {
        resolve(undefined);
        return;
      }
      resolve(parseJavaVersion(String(stderr) + String(stdout)));
    });
  });
}

/**
 * Finds a Java runtime: the `requality.java.home` setting, then the runtime in the extension package (the packages for
 * a platform have one), then JAVA_HOME, then `java` on PATH, then the macOS java_home tool. Throws with an actionable
 * message when none is suitable.
 */
export async function findJava(configuredHome: string | undefined, bundledHome?: string): Promise<JavaRuntime> {
  const candidates: Array<{ executable: string; source: string }> = [];
  if (configuredHome && configuredHome.trim()) {
    candidates.push({ executable: javaExecutable(configuredHome.trim()), source: 'requality.java.home' });
  }
  if (bundledHome && fs.existsSync(javaExecutable(bundledHome))) {
    makeExecutable(bundledHome);
    candidates.push({ executable: javaExecutable(bundledHome), source: 'bundled' });
  }
  if (process.env.JAVA_HOME) {
    candidates.push({ executable: javaExecutable(process.env.JAVA_HOME), source: 'JAVA_HOME' });
  }
  candidates.push({ executable: process.platform === 'win32' ? 'java.exe' : 'java', source: 'PATH' });
  if (process.platform === 'darwin' && fs.existsSync('/usr/libexec/java_home')) {
    const home = await new Promise<string | undefined>(resolve => {
      execFile('/usr/libexec/java_home', ['-v', `${MIN_JAVA}+`], { timeout: 15000 }, (err, stdout) =>
        resolve(err ? undefined : String(stdout).trim() || undefined));
    });
    if (home) {
      candidates.push({ executable: javaExecutable(home), source: 'java_home' });
    }
  }
  const rejected: string[] = [];
  for (const c of candidates) {
    if (c.source !== 'PATH' && !fs.existsSync(c.executable)) {
      rejected.push(`${c.source}: ${c.executable} does not exist`);
      continue;
    }
    const version = await probe(c.executable);
    if (version === undefined) {
      rejected.push(`${c.source}: ${c.executable} could not be run`);
      continue;
    }
    if (version < MIN_JAVA) {
      rejected.push(`${c.source}: ${c.executable} is Java ${version}`);
      continue;
    }
    return { executable: c.executable, version, source: c.source };
  }
  throw new Error(`Requality needs Java ${MIN_JAVA} or newer. Set "requality.java.home" to a JDK/JRE folder. `
    + `Checked: ${rejected.join('; ')}`);
}
