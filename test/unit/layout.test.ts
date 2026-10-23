import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import Module from 'node:module';

// src/features/workbench imports 'vscode': a stub is enough for the containers of the regions.
const load = (Module as unknown as { _load: (request: string, ...rest: unknown[]) => unknown })._load;
(Module as unknown as { _load: (request: string, ...rest: unknown[]) => unknown })._load = (request: string, ...rest: unknown[]) =>
  request === 'vscode' ? { version: '1.106.0' } : load(request, ...rest);
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { hasSecondarySidebar, regionContainer } = require('../../src/features/workbench') as typeof import('../../src/features/workbench');

describe('the layout of a perspective in VS Code', () => {
  test('left of the editor area is the primary side bar, above and below the panel', () => {
    assert.equal(regionContainer('left'), 'workbench.view.extension.requality');
    assert.equal(regionContainer('top'), 'workbench.view.extension.requality-panel');
    assert.equal(regionContainer('bottom'), 'workbench.view.extension.requality-panel');
  });
  test('right of the editor area is the secondary side bar from VS Code 1.106 on, before the primary side bar', () => {
    assert.equal(hasSecondarySidebar('1.106.0'), true);
    assert.equal(hasSecondarySidebar('1.143.1'), true);
    assert.equal(hasSecondarySidebar('2.0.0'), true);
    assert.equal(hasSecondarySidebar('1.105.1'), false);
    assert.equal(hasSecondarySidebar('1.95.0'), false);
    assert.equal(regionContainer('right', '1.106.0'), 'workbench.view.extension.requality-right');
    assert.equal(regionContainer('right', '1.95.0'), 'workbench.view.extension.requality');
  });
});
