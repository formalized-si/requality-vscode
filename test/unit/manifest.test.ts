import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import * as fs from 'fs';
import * as path from 'path';

interface MenuItem { command?: string; submenu?: string }

const root = path.join(__dirname, '..', '..', '..');
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const contributes = pkg.contributes;

describe('package.json: menus of the Eclipse workbench', () => {
  test('every command of a menu is declared and every submenu exists', () => {
    const commands = new Set<string>((contributes.commands as Array<{ command: string }>).map(c => c.command));
    const submenus = new Set<string>((contributes.submenus as Array<{ id: string }>).map(s => s.id));
    for (const [menu, items] of Object.entries(contributes.menus as Record<string, MenuItem[]>)) {
      for (const item of items) {
        if (item.command) {
          assert.ok(commands.has(item.command), `${item.command} of ${menu} is declared`);
        }
        if (item.submenu) {
          assert.ok(submenus.has(item.submenu), `${item.submenu} of ${menu} exists`);
        }
      }
    }
  });

  test('the main menus of Eclipse (File, Edit, Navigate, Project, Window, Help) are in the explorer\'s menu', () => {
    const title = contributes.menus['view/title'] as MenuItem[];
    const main = title.filter(i => i.submenu?.startsWith('requality.mainMenu.')).map(i => i.submenu);
    assert.deepEqual(main, ['file', 'edit', 'navigate', 'project', 'window', 'help'].map(m => 'requality.mainMenu.' + m));
    const labels = new Map<string, string>((contributes.submenus as Array<{ id: string; label: string }>).map(s => [s.id, s.label]));
    assert.deepEqual(main.map(m => labels.get(m as string)), ['File', 'Edit', 'Navigate', 'Project', 'Window', 'Help']);
    const edit = (contributes.menus['requality.mainMenu.edit'] as MenuItem[]).map(i => i.command);
    assert.ok(edit.includes('requality.addBookmark') && edit.includes('requality.findNext'), edit.join());
    const showView = (contributes.menus['requality.mainMenu.window.showView'] as MenuItem[]).map(i => i.command);
    assert.ok(showView.includes('requality.showView.problems') && showView.at(-1) === 'requality.showView.other', showView.join());
    const window = (contributes.menus['requality.mainMenu.window'] as MenuItem[]).map(i => i.submenu ?? i.command);
    assert.deepEqual(window.slice(0, 2), ['requality.mainMenu.window.showView', 'requality.mainMenu.window.perspective']);
    const perspective = (contributes.menus['requality.mainMenu.window.perspective'] as MenuItem[]).map(i => i.submenu ?? i.command);
    for (const c of ['requality.mainMenu.window.perspective.open', 'requality.perspective.customize', 'requality.perspective.saveAs', 'requality.perspective.reset', 'requality.perspective.close', 'requality.perspective.closeAll']) {
      assert.ok(perspective.includes(c), `${c} in Window > Perspective`);
    }
  });

  test('the regions of a perspective have their containers (left, right, panel)', () => {
    const containers = contributes.viewsContainers as Record<string, Array<{ id: string }>>;
    assert.deepEqual(containers.activitybar.map(c => c.id), ['requality']);
    assert.deepEqual(containers.panel.map(c => c.id), ['requality-panel']);
    assert.deepEqual(containers.secondarySidebar.map(c => c.id), ['requality-right']);
    // the layout of the Requality perspective: the explorer left, Outline and the Links Explorer right
    assert.deepEqual((contributes.views.requality as Array<{ id: string }>).map(v => v.id), ['requality.explorerView', 'requality.explorer']);
    assert.deepEqual((contributes.views['requality-right'] as Array<{ id: string }>).map(v => v.id), ['requality.outline', 'requality.links']);
  });

  test('the Requality Explorer is the Eclipse view or the native tree, as the setting chooses', () => {
    const views = contributes.views.requality as Array<{ id: string; type?: string; when?: string }>;
    const eclipse = views.find(v => v.id === 'requality.explorerView')!;
    const native = views.find(v => v.id === 'requality.explorer')!;
    assert.equal(eclipse.type, 'webview');
    assert.equal(eclipse.when, 'config.requality.explorer.view != native');
    assert.equal(native.when, 'config.requality.explorer.view == native');
    const setting = contributes.configuration.properties['requality.explorer.view'];
    assert.deepEqual(setting.enum, ['eclipse', 'native']);
    assert.equal(setting.default, 'eclipse');
    // the toolbar of the Eclipse view: the plug-in's button, Collapse All and Link with Editor (CommonNavigator)
    const title = (contributes.menus['view/title'] as Array<MenuItem & { when: string }>)
      .filter(i => /^view (==|=~) .*requality\\?\.explorerView|explorer\(View\)/.test(i.when) && !/^view == requality\.explorer( |$)/.test(i.when));
    for (const c of ['requality.selectReferencedNode', 'requality.explorer.collapseAll', 'requality.linkWithEditor', 'requality.unlinkWithEditor']) {
      assert.ok(title.some(i => i.command === c), `${c} in the toolbar of the Eclipse view`);
    }
    for (const c of ['requality.newProject', 'requality.refresh']) {
      assert.ok(!title.some(i => i.command === c), `${c} is not a button of the Eclipse view`);
    }
  });

  test('the key bindings of the explorer work in the Eclipse view (its webview has the focus)', () => {
    const keys = contributes.keybindings as Array<{ key: string; command: string; when?: string }>;
    for (const k of keys.filter(x => x.when?.includes('focusedView == requality.explorer'))) {
      assert.ok(k.when!.includes('requality.focusedView == requality.explorerView'), `${k.key} ${k.command}: ${k.when}`);
    }
    const del = keys.find(k => k.key === 'delete' && k.command === 'requality.delete');
    assert.ok(del?.when?.includes('requality.focusedView == requality.explorerView'));
  });
});
