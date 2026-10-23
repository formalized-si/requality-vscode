import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import { call, create, createHarness, dispose, Harness, set } from './rswtHarness';

// SWT style bits used by the renderer (media/rswt/renderer.js, SWT constants).
const SWT = {
  MULTI: 1 << 1,
  PUSH: 1 << 3,
  FULL_SELECTION: 1 << 16,
  SHIFT: 1 << 17,
};

/** A shell "s1" with a single grid composite "c1" (the layout most dialogs use). */
function gridComposite(h: Harness, numColumns: number, extra: Record<string, unknown> = {}): void {
  h.snapshot([
    create('s1', 'shell', null, { title: 'Test' }),
    create('c1', 'composite', 's1', {
      layout: { type: 'grid', numColumns, equal: false, mt: 0, mr: 0, mb: 0, ml: 0, hs: 5, vs: 5 },
      ...extra,
    }),
  ]);
}

describe('renderer: snapshot and GridLayout', () => {
  test('GridLayout places children by SWT rules: spans, wrapping, and excluded children', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null, { title: 'Test' }),
      create('c1', 'composite', 's1', { layout: { type: 'grid', numColumns: 2, equal: false, mt: 0, mr: 0, mb: 0, ml: 0, hs: 5, vs: 5 } }),
      create('l1', 'label', 'c1', { text: 'Name', layoutData: { type: 'grid', ha: 'start', va: 'center', hspan: 1, vspan: 1 } }),
      // Does not fit in the rest of row 0 (one cell left): wraps to row 1 and spans both columns.
      create('l2', 'label', 'c1', { text: 'Wide', layoutData: { type: 'grid', ha: 'fill', hspan: 2, vspan: 1 } }),
      create('hid', 'label', 'c1', { text: 'Hidden', layoutData: { type: 'grid', exclude: true, hspan: 1, vspan: 1 } }),
      create('t1', 'text', 'c1', { text: '', layoutData: { type: 'grid', ha: 'fill', gh: true, hspan: 1, vspan: 1 } }),
    ]);
    const l1 = h.el('l1');
    const l2 = h.el('l2');
    const hid = h.el('hid');
    const t1 = h.el('t1');
    assert.equal(l1.style.gridColumn, '1 / span 1');
    assert.equal(l1.style.gridRow, '1 / span 1');
    assert.equal(l2.style.gridColumn, '1 / span 2');
    assert.equal(l2.style.gridRow, '2 / span 1');
    // The excluded child takes no cell and is hidden.
    assert.equal(hid.style.gridColumn, '');
    assert.equal(hid.style.gridRow, '');
    assert.ok(hid.classList.contains('excluded'));
    // The text starts row 2 (after the spanning label) and grabs the excess horizontal space.
    assert.equal(t1.style.gridColumn, '1 / span 1');
    assert.equal(t1.style.gridRow, '3 / span 1');
    assert.equal(h.el('c1').style.display, 'grid');
    assert.equal(h.el('c1').style.gridTemplateColumns, 'minmax(0, 1fr) auto');
    assert.equal(l1.style.justifySelf, 'start');
    assert.equal(l1.style.alignSelf, 'center');
  });

  test('grabExcessHorizontalSpace makes its column minmax(0, 1fr); columns without grab stay auto', () => {
    const h = createHarness();
    gridComposite(h, 2);
    h.update([
      create('a', 'label', 'c1', { text: 'a', layoutData: { type: 'grid', hspan: 1, vspan: 1 } }),
      create('b', 'text', 'c1', { text: '', layoutData: { type: 'grid', gh: true, hspan: 1, vspan: 1 } }),
    ]);
    // Column 0 holds no grab, column 1 does.
    assert.equal(h.el('c1').style.gridTemplateColumns, 'auto minmax(0, 1fr)');
    assert.equal(h.el('b').style.gridColumn, '2 / span 1');
    assert.equal(h.el('a').style.gridColumn, '1 / span 1');
  });

  test('grabExcessHorizontalSpace on a spanning child grabs its last column', () => {
    const h = createHarness();
    gridComposite(h, 2);
    h.update([
      create('w', 'label', 'c1', { text: 'span', layoutData: { type: 'grid', gh: true, hspan: 2, vspan: 1 } }),
    ]);
    assert.equal(h.el('c1').style.gridTemplateColumns, 'auto minmax(0, 1fr)');
  });

  test('equal-width GridLayout: equal shares, a column without a grabbing control not narrower than its controls', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('c1', 'composite', 's1', { layout: { type: 'grid', numColumns: 3, equal: true } }),
      create('a', 'label', 'c1', { text: 'a' }),
      create('b', 'text', 'c1', { text: '', layoutData: { type: 'grid', ha: 'fill', gh: true, hspan: 1, vspan: 1 } }),
      create('c', 'button', 'c1', { text: 'Complete', layoutData: { type: 'grid', ha: 'beginning', w: 80, hspan: 1, vspan: 1 } }, SWT.PUSH),
    ]);
    // SWT: the controls that do not grab keep their width (widthHint 80) and are cut by the parent when it is too narrow
    assert.equal(h.el('c1').style.gridTemplateColumns, 'minmax(min-content, 1fr) minmax(0, 1fr) minmax(min-content, 1fr)');
  });

  test('layoutData set after creation re-places the child', () => {
    const h = createHarness();
    gridComposite(h, 2);
    h.update([create('a', 'label', 'c1', { text: 'a', layoutData: { type: 'grid', hspan: 1, vspan: 1 } })]);
    assert.equal(h.el('a').style.gridColumn, '1 / span 1');
    h.update([set('a', { layoutData: { type: 'grid', exclude: true, hspan: 1, vspan: 1 } })]);
    assert.ok(h.el('a').classList.contains('excluded'));
    assert.equal(h.el('a').style.gridColumn, '');
  });

  test('a fresh snapshot replaces the previous widget tree', () => {
    const h = createHarness();
    gridComposite(h, 1);
    h.snapshot([create('s2', 'shell', null, { title: 'Second' }), create('lone', 'label', 's2', { text: 'x' })]);
    assert.equal(h.find('c1'), null);
    assert.equal(h.find('s1'), null);
    assert.ok(h.find('lone'));
    assert.equal(h.document.getElementById('root')!.firstElementChild, h.el('s2'));
  });
});

describe('renderer: FillLayout', () => {
  test('FillLayout makes children flex items in a row with spacing and margins', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('c1', 'composite', 's1', { layout: { type: 'fill', vertical: false, spacing: 4, mw: 3, mh: 2 } }),
      create('a', 'label', 'c1', { text: 'a' }),
      create('b', 'label', 'c1', { text: 'b' }),
    ]);
    const box = h.el('c1');
    assert.equal(box.style.display, 'flex');
    assert.equal(box.style.flexDirection, 'row');
    assert.equal(box.style.gap, '4px');
    assert.equal(box.style.padding, '2px 3px');
    for (const id of ['a', 'b']) {
      assert.equal(h.el(id).style.flex, '1 1 0px');
      assert.equal(h.el(id).style.minWidth, '0');
    }
  });

  test('vertical FillLayout stacks children in a column', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('c1', 'composite', 's1', { layout: { type: 'fill', vertical: true, spacing: 0 } }),
      create('a', 'label', 'c1', { text: 'a' }),
    ]);
    assert.equal(h.el('c1').style.flexDirection, 'column');
    assert.equal(h.el('a').style.flex, '1 1 0px');
  });
});

describe('renderer: widget events posted to the host', () => {
  test('clicking a push button posts a selection event for its id', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('ok', 'button', 's1', { text: 'OK' }, SWT.PUSH),
    ]);
    assert.equal(h.el('ok').textContent, 'OK');
    h.el('ok').click();
    assert.deepEqual(h.events(), [{ type: 'event', id: 'ok', eventType: 'selection', data: {} }]);
  });

  test('typing in a text field posts a modify event carrying the new text', () => {
    const h = createHarness();
    h.snapshot([create('s1', 'shell', null), create('name', 'text', 's1', { text: '' })]);
    const input = h.el('name') as HTMLInputElement;
    input.value = 'hello';
    input.dispatchEvent(new h.window.Event('input'));
    const last = h.events().at(-1);
    assert.ok(last);
    assert.equal(last.id, 'name');
    assert.equal(last.eventType, 'modify');
    assert.equal(last.data.text, 'hello');
    assert.equal(typeof last.data.selStart, 'number');
    assert.equal(typeof last.data.selEnd, 'number');
  });

  test('a DragSource text carries its id; a drop on a DropTarget posts the source, the URIs and the text', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('from', 'text', 's1', { text: '/arch/x.txt', dragSource: true, dropTarget: true }),
      create('to', 'text', 's1', { text: '', dropTarget: true }),
    ]);
    const from = h.el('from') as HTMLInputElement;
    assert.equal(from.draggable, true);
    assert.equal((h.el('to') as HTMLInputElement).draggable, false);
    const data = new Map<string, string>();
    const dataTransfer = {
      setData: (k: string, v: string) => data.set(k, v),
      getData: (k: string) => data.get(k) ?? '',
      effectAllowed: '',
      dropEffect: 'none',
    };
    const fire = (target: HTMLElement, type: string): Event => {
      const ev = new h.window.Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(ev, 'dataTransfer', { value: dataTransfer });
      target.dispatchEvent(ev);
      return ev;
    };
    fire(from, 'dragstart');
    assert.equal(data.get('application/x-rswt-source'), 'from');
    assert.equal(data.get('text/plain'), '/arch/x.txt');
    assert.equal(fire(h.el('to'), 'dragover').defaultPrevented, true, 'a drop target accepts the drag');
    fire(h.el('to'), 'drop');
    dataTransfer.dropEffect = 'copy';
    fire(from, 'dragend');
    assert.deepEqual(h.events(), [
      { type: 'event', id: 'from', eventType: 'dragstart', data: {} },
      { type: 'event', id: 'to', eventType: 'drop', data: { source: 'from', uris: [], text: '/arch/x.txt', stateMask: 0 } },
      { type: 'event', id: 'from', eventType: 'dragend', data: { effect: 'copy' } },
    ]);
    // a file dragged from a VS Code view
    data.clear();
    data.set('application/vnd.code.uri-list', 'file:///w/a.txt\r\nfile:///w/b.txt');
    fire(h.el('to'), 'drop');
    assert.deepEqual(h.events().at(-1), {
      type: 'event', id: 'to', eventType: 'drop', data: { source: null, uris: ['file:///w/a.txt', 'file:///w/b.txt'], text: '', stateMask: 0 },
    });
  });

  test('a drag in a tree selects the row it starts on; a drop on a row posts that item and the modifier keys', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('tr', 'tree', 's1', { dragSource: true, dropTarget: true }),
      create('ti1', 'treeitem', 'tr', { text: 'First' }),
      create('ti2', 'treeitem', 'tr', { text: 'Second' }),
    ]);
    const data = new Map<string, string>();
    const dataTransfer = { setData: (k: string, v: string) => data.set(k, v), getData: (k: string) => data.get(k) ?? '', effectAllowed: '', dropEffect: 'none' };
    const fire = (target: Element, type: string, init: Record<string, unknown> = {}): Event => {
      const ev = new h.window.Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(ev, 'dataTransfer', { value: dataTransfer });
      for (const [k, v] of Object.entries(init)) {
        Object.defineProperty(ev, k, { value: v });
      }
      target.dispatchEvent(ev);
      return ev;
    };
    fire(h.el('ti1').querySelector('.treerow')!, 'dragstart');
    assert.equal(data.get('text/plain'), 'First');
    // Shift: insert after the target in the Requality Explorer (CNFDropAssistant)
    fire(h.el('ti2').querySelector('.treerow')!, 'drop', { shiftKey: true });
    assert.deepEqual(h.events(), [
      { type: 'event', id: 'tr', eventType: 'selection', data: { selection: ['ti1'], item: 'ti1' } },
      { type: 'event', id: 'tr', eventType: 'dragstart', data: {} },
      { type: 'event', id: 'tr', eventType: 'drop', data: { source: 'tr', uris: [], text: 'First', stateMask: SWT.SHIFT, item: 'ti2' } },
    ]);
  });

  test('Escape closes the shown popup menu, as in SWT', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('m1', 'menu', 's1', {}),
      create('mi1', 'menuitem', 'm1', { text: 'Copy' }),
    ]);
    h.update([call('m1', 'show', [10, 10])]);
    assert.ok(h.el('m1').classList.contains('shown'));
    h.window.document.dispatchEvent(new h.window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    assert.ok(!h.el('m1').classList.contains('shown'));
    assert.deepEqual(h.events(), []);
  });

  test('a set op changes the text of a text field without posting a modify event', () => {
    const h = createHarness();
    h.snapshot([create('s1', 'shell', null), create('name', 'text', 's1', { text: '' })]);
    h.update([set('name', { text: 'from server' })]);
    assert.equal((h.el('name') as HTMLInputElement).value, 'from server');
    assert.deepEqual(h.events(), []);
  });

  test('an editable combo posts modify when typed, modify and selection when an item is picked', () => {
    const h = createHarness();
    h.snapshot([create('s1', 'shell', null), create('cb', 'combo', 's1', { items: ['alpha', 'beta'], text: '' })]);
    const input = h.el('cb').querySelector('input') as HTMLInputElement;
    input.value = 'beta';
    input.dispatchEvent(new h.window.InputEvent('input', { inputType: 'insertText' }));
    assert.deepEqual(h.events().map(e => e.eventType), ['modify']);
    input.value = 'alpha';
    input.dispatchEvent(new h.window.InputEvent('input', { inputType: 'insertReplacementText' }));
    assert.deepEqual(h.events().slice(1).map(e => [e.eventType, e.data]), [['modify', { text: 'alpha' }], ['selection', { index: 0 }]]);
  });

  test('Enter in a single-line text posts a default event', () => {
    const h = createHarness();
    h.snapshot([create('s1', 'shell', null), create('name', 'text', 's1', { text: '' })]);
    h.el('name').dispatchEvent(new h.window.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    assert.deepEqual(h.events(), [{ type: 'event', id: 'name', eventType: 'default', data: {} }]);
  });
});

describe('renderer: list type-ahead', () => {
  test('a letter selects the next item starting with it and posts the selection', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('l', 'list', 's1', { items: ['Apple', 'Banana', 'Cherry', 'Avocado'], selection: [0] }),
    ]);
    const list = h.el('l');
    assert.equal(list.children.length, 4);

    // 'c' from item 0: Banana does not match, Cherry does.
    list.dispatchEvent(new h.window.KeyboardEvent('keydown', { key: 'c', bubbles: true }));
    assert.deepEqual(h.events().at(-1), { type: 'event', id: 'l', eventType: 'selection', data: { indices: [2] } });
    assert.ok(list.children[2].classList.contains('selected'));
    assert.ok(!list.children[0].classList.contains('selected'));

    // 'a' from Cherry: the next item starting with 'a' is Avocado.
    list.dispatchEvent(new h.window.KeyboardEvent('keydown', { key: 'a', bubbles: true }));
    assert.deepEqual(h.events().at(-1), { type: 'event', id: 'l', eventType: 'selection', data: { indices: [3] } });
  });

  test('a letter with no matching item posts nothing', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('l', 'list', 's1', { items: ['Apple', 'Banana'], selection: [0] }),
    ]);
    h.el('l').dispatchEvent(new h.window.KeyboardEvent('keydown', { key: 'z', bubbles: true }));
    assert.deepEqual(h.events(), []);
  });

  test('clicking a list item selects it and posts its index', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('l', 'list', 's1', { items: ['a', 'b', 'c'], selection: [] }),
    ]);
    (h.el('l').children[1] as HTMLElement).click();
    assert.deepEqual(h.events().at(-1), { type: 'event', id: 'l', eventType: 'selection', data: { indices: [1] } });
  });
});

describe('renderer: tree and table items', () => {
  test('tree items appear with their text and selecting one posts its item id', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('tr', 'tree', 's1', {}),
      create('ti1', 'treeitem', 'tr', { text: 'Root' }),
      create('ti2', 'treeitem', 'tr', { text: 'Second' }),
    ]);
    assert.equal(h.el('ti1').querySelector('.treetext')!.textContent, 'Root');
    assert.equal(h.el('ti2').querySelector('.treetext')!.textContent, 'Second');
    (h.el('ti2').querySelector('.treerow') as HTMLElement).click();
    assert.deepEqual(h.events().at(-1), {
      type: 'event', id: 'tr', eventType: 'selection', data: { selection: ['ti2'], item: 'ti2' },
    });
  });

  test('table rows appear with cell text and clicking one posts its item id', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('tb', 'table', 's1', {}, SWT.FULL_SELECTION),
      create('col', 'column', 'tb', { text: 'Name', width: 150 }),
      create('r1', 'row', 'tb', { texts: ['alpha'] }),
      create('r2', 'row', 'tb', { texts: ['beta'] }),
    ]);
    assert.equal(h.el('r1').querySelector('td')!.textContent, 'alpha');
    assert.equal(h.el('r2').querySelector('td')!.textContent, 'beta');
    h.el('r2').dispatchEvent(new h.window.MouseEvent('mousedown', { bubbles: true, button: 0 }));
    assert.deepEqual(h.events().at(-1), {
      type: 'event', id: 'tb', eventType: 'selection', data: { selection: ['r2'], item: 'r2' },
    });
    assert.ok(h.el('r2').classList.contains('selected'));
  });
});

describe('renderer: tree columns', () => {
  test('a tree with columns shows the header and a cell per column of each item', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('tr', 'tree', 's1', { headerVisible: true }),
      create('c0', 'column', 'tr', { text: 'Description', width: 200 }),
      create('c1', 'column', 'tr', { text: 'Resource', width: 100 }),
      create('ti1', 'treeitem', 'tr', { texts: ['first', 'a.txt'] }),
      create('ti2', 'treeitem', 'ti1', { texts: ['nested', 'b.txt'] }),
    ]);
    const head = h.el('tr').querySelector('.treehead') as HTMLElement;
    assert.notEqual(head.style.display, 'none');
    assert.equal(head.textContent, 'DescriptionResource');
    assert.equal(h.el('ti1').querySelector('.treetext')!.textContent, 'first');
    assert.equal(h.el('ti1').querySelector('.treerow .treecell')!.textContent, 'a.txt');
    assert.equal(h.el('ti2').querySelector('.treerow .treecell')!.textContent, 'b.txt');
    // the cells of the second column line up: the first cell of a nested item is narrower by its indentation
    const w1 = parseInt((h.el('ti1').querySelector('.treetext') as HTMLElement).style.width, 10);
    const w2 = parseInt((h.el('ti2').querySelector('.treetext') as HTMLElement).style.width, 10);
    assert.equal(w1 - w2, 14);
  });

  test('the sort column of a tree and of a table shows its direction', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('tr', 'tree', 's1', { headerVisible: true }),
      create('c0', 'column', 'tr', { text: 'Description', width: 200 }),
      create('tb', 'table', 's1', {}),
      create('t0', 'column', 'tb', { text: 'Name', width: 100 }),
    ]);
    h.update([{ op: 'set', id: 'tr', props: { sortColumn: 'c0', sortDirection: 'up' } }, { op: 'set', id: 'tb', props: { sortColumn: 't0', sortDirection: 'down' } }]);
    assert.equal(h.el('c0').textContent, 'Description ▲');
    assert.equal(h.el('t0').textContent, 'Name ▼');
  });
});

describe('renderer: dispose', () => {
  test('disposing a widget removes its DOM', () => {
    const h = createHarness();
    h.snapshot([
      create('s1', 'shell', null),
      create('lbl', 'label', 's1', { text: 'bye' }),
    ]);
    assert.ok(h.find('lbl'));
    h.update([dispose('lbl')]);
    assert.equal(h.find('lbl'), null);
    assert.equal(h.document.body.textContent!.includes('bye'), false);
  });

  test('disposing a container removes its children too', () => {
    const h = createHarness();
    gridComposite(h, 1);
    h.update([create('inner', 'label', 'c1', { text: 'inner' })]);
    h.update([dispose('c1')]);
    assert.equal(h.find('c1'), null);
    assert.equal(h.find('inner'), null);
    assert.ok(h.find('s1'));
  });

  test('operations on a disposed widget are ignored', () => {
    const h = createHarness();
    gridComposite(h, 1);
    h.update([dispose('c1')]);
    assert.doesNotThrow(() => h.update([set('c1', { text: 'x' }), call('c1', 'focus')]));
  });
});

describe('renderer: shell title', () => {
  test('setting a shell title sets the document title and posts a title message', () => {
    const h = createHarness();
    h.snapshot([create('s1', 'shell', null, { title: 'Requality Dialog' })]);
    assert.equal(h.document.title, 'Requality Dialog');
    assert.ok(h.posted.some(m => m.type === 'title' && m.title === 'Requality Dialog'));

    h.update([set('s1', { title: 'Renamed' })]);
    assert.equal(h.document.title, 'Renamed');
    assert.deepEqual(h.posted.at(-1), { type: 'title', title: 'Renamed' });
  });

  test('the renderer announces readiness to the host on load', () => {
    const h = createHarness();
    assert.deepEqual(h.posted[0], { type: 'ready' });
  });
});

describe('renderer: regressions', () => {
  // A grid child that was grabbing excess space (gh, hspan 2) and is then excluded must not leave its span grab,
  // otherwise a column grows although no visible child asks for it.
  test('excluding a spanning grab child does not leave its column growing', () => {
    const h = createHarness();
    gridComposite(h, 2);
    h.update([create('a', 'label', 'c1', { text: 'a', layoutData: { type: 'grid', gh: true, hspan: 2, vspan: 1 } })]);
    h.update([set('a', { layoutData: { type: 'grid', exclude: true, gh: true, hspan: 2, vspan: 1 } })]);
    assert.equal(h.el('c1').style.gridTemplateColumns, 'auto auto');
  });
});
