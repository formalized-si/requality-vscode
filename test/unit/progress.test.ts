import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import Module from 'node:module';

// src/host/progress imports 'vscode' (only for the status bar item): a stub is enough for the text of the item.
const load = (Module as unknown as { _load: (request: string, ...rest: unknown[]) => unknown })._load;
(Module as unknown as { _load: (request: string, ...rest: unknown[]) => unknown })._load = (request: string, ...rest: unknown[]) =>
  request === 'vscode' ? {} : load(request, ...rest);
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { progressText } = require('../../src/host/progress') as typeof import('../../src/host/progress');

describe('the progress area of the status bar (Eclipse ProgressRegion)', () => {
  test('nothing while no operation runs', () => {
    assert.equal(progressText([]), '');
  });
  test('the last started operation with its progress and the number of the others', () => {
    assert.equal(progressText([{ title: 'Build' }]), '$(loading~spin) Build');
    assert.equal(progressText([{ title: 'Build' }, { title: 'Load project TS103096', percent: 42.4 }]), '$(loading~spin) Load project TS103096 42% (+1)');
    assert.equal(progressText([{ title: 'Copy', percent: 130 }]), '$(loading~spin) Copy 100%');
  });
});
