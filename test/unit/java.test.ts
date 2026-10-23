import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { findJava, parseJavaVersion } from '../../src/server/java';

/** A Java home whose bin/java prints the version line of the given release (not executable, as an unpacked zip may be). */
function fakeJavaHome(release: string): string {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-java-'));
  fs.mkdirSync(path.join(home, 'bin'));
  fs.mkdirSync(path.join(home, 'lib'));
  fs.writeFileSync(path.join(home, 'bin', 'java'), `#!/bin/sh\necho 'openjdk version "${release}" 2024-10-15' >&2\n`, { mode: 0o644 });
  fs.writeFileSync(path.join(home, 'lib', 'jspawnhelper'), '', { mode: 0o644 });
  return home;
}

describe('the Java runtime of the server', () => {
  test('the feature release is read from java -version', () => {
    assert.equal(parseJavaVersion('openjdk version "21.0.5" 2024-10-15'), 21);
    assert.equal(parseJavaVersion('java version "1.8.0_402"'), 8);
    assert.equal(parseJavaVersion('openjdk version "17" 2021-09-14'), 17);
    assert.equal(parseJavaVersion('no version here'), undefined);
  });

  test('the runtime in the package is used and made executable', { skip: process.platform === 'win32' }, async () => {
    const bundled = fakeJavaHome('21.0.5');
    const saved = process.env.JAVA_HOME;
    delete process.env.JAVA_HOME;
    try {
      const java = await findJava(undefined, bundled);
      assert.equal(java.source, 'bundled');
      assert.equal(java.version, 21);
      assert.equal(fs.statSync(path.join(bundled, 'bin', 'java')).mode & 0o111, 0o111);
      assert.equal(fs.statSync(path.join(bundled, 'lib', 'jspawnhelper')).mode & 0o111, 0o111);
    } finally {
      if (saved !== undefined) {
        process.env.JAVA_HOME = saved;
      }
    }
  });

  test('the requality.java.home setting comes before the runtime in the package', { skip: process.platform === 'win32' }, async () => {
    const configured = fakeJavaHome('17.0.9');
    fs.chmodSync(path.join(configured, 'bin', 'java'), 0o755);
    const java = await findJava(configured, fakeJavaHome('21.0.5'));
    assert.equal(java.source, 'requality.java.home');
    assert.equal(java.version, 17);
  });
});
