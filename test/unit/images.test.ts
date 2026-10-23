import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import { imageUrl } from '../../src/ui/images';

describe('the images of the webviews', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'requality-images-'));
  const root = path.join(dir, 'resources');
  const catalog = path.join(dir, 'catalog');
  fs.mkdirSync(root);
  fs.mkdirSync(catalog);
  const png = Buffer.from('89504e470d0a1a0a', 'hex');
  fs.writeFileSync(path.join(root, 'folder.png'), png);
  fs.writeFileSync(path.join(catalog, 'requirement.png'), png);
  const uri = (f: string) => `https://file+.vscode-resource.vscode-cdn.net${f}`;

  test('an image in a local resource root is a webview URI', () => {
    assert.equal(imageUrl(path.join(root, 'folder.png'), [root], uri), uri(path.join(root, 'folder.png')));
  });

  test('an image outside the roots (a node type image of a catalog) is a data URI of its contents', () => {
    assert.equal(imageUrl(path.join(catalog, 'requirement.png'), [root], uri), `data:image/png;base64,${png.toString('base64')}`);
  });

  test('a folder next to a root is not in the root', () => {
    fs.mkdirSync(path.join(dir, 'resources-other'));
    fs.writeFileSync(path.join(dir, 'resources-other', 'x.png'), png);
    assert.ok(imageUrl(path.join(dir, 'resources-other', 'x.png'), [root], uri).startsWith('data:image/png;base64,'));
  });

  test('a missing file or a file that is not an image stays a webview URI', () => {
    assert.equal(imageUrl(path.join(catalog, 'missing.png'), [root], uri), uri(path.join(catalog, 'missing.png')));
    fs.writeFileSync(path.join(catalog, 'notes.txt'), 'text');
    assert.equal(imageUrl(path.join(catalog, 'notes.txt'), [root], uri), uri(path.join(catalog, 'notes.txt')));
  });
});
