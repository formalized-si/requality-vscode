import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { runTests } from '@vscode/test-electron';

/**
 * Runs the integration tests in a real VS Code (downloaded by @vscode/test-electron) on a copy of the ETSI TS103096
 * catalog (the server tests download it to server/build/test-catalogs).
 */
async function main(): Promise<void> {
  const root = path.resolve(__dirname, '..', '..');
  const catalog = path.join(root, 'server', 'build', 'test-catalogs', 'etsi', 'TS103096');
  if (!fs.existsSync(catalog)) {
    throw new Error(`${catalog} is missing: run "npm run test:server" first (it downloads the test catalog).`);
  }
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-it-'));
  // The workspace starts empty: the tests add the catalog once the extension runs (a new catalog in the workspace).
  const workspace = path.join(work, 'workspace');
  fs.mkdirSync(workspace, { recursive: true });
  // The tests start with the native tree of the Requality Explorer; the last ones switch to the Eclipse view.
  const userSettings = path.join(work, 'user-data', 'User');
  fs.mkdirSync(userSettings, { recursive: true });
  fs.writeFileSync(path.join(userSettings, 'settings.json'), JSON.stringify({ 'requality.explorer.view': 'native' }, null, 2));
  try {
    await runTests({
      version: process.env.VSCODE_TEST_VERSION ?? 'stable',
      // REQUALITY_IT_EXTENSION_PATH: run the tests on another copy of the extension (the unpacked VSIX).
      extensionDevelopmentPath: process.env.REQUALITY_IT_EXTENSION_PATH ?? root,
      extensionTestsPath: path.join(__dirname, 'integration', 'index'),
      launchArgs: [workspace, '--disable-extensions', '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes',
        '--user-data-dir', path.join(work, 'user-data')],
      extensionTestsEnv: {
        REQUALITY_IT_WORKSPACE: workspace, REQUALITY_IT_CATALOG: catalog,
        // the whole extension log of the run (REQUALITY_IT_LOG keeps it; by default it is removed with the run)
        REQUALITY_IT_LOG: process.env.REQUALITY_IT_LOG ?? path.join(work, 'extension.log'),
        // a subset of the tests (a regular expression on their names)
        ...(process.env.REQUALITY_IT_GREP ? { REQUALITY_IT_GREP: process.env.REQUALITY_IT_GREP } : {}),
      },
    });
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
}

main().catch(e => {
  console.error(e instanceof Error ? e.message : e);
  process.exit(1);
});
