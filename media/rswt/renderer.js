// @ts-check
/* Renderer of the remote SWT widgets (server/src/main/java/.../ui/swt). The server owns the widget state and sends
 * create/set/dispose/call operations; user actions are posted back as widget events. */
(function () {
  'use strict';
  // eslint-disable-next-line no-undef
  const vscode = acquireVsCodeApi();
  const SWT = {
    MULTI: 1 << 1, SINGLE: 1 << 2, READ_ONLY: 1 << 3, WRAP: 1 << 6, PASSWORD: 1 << 22, BORDER: 1 << 11,
    PUSH: 1 << 3, CHECK: 1 << 5, RADIO: 1 << 4, TOGGLE: 1 << 1, ARROW: 1 << 2, SEPARATOR: 1 << 1,
    H_SCROLL: 1 << 8, V_SCROLL: 1 << 9, FULL_SELECTION: 1 << 16, VERTICAL: 1 << 9, HORIZONTAL: 1 << 8,
    DATE: 1 << 5, TIME: 1 << 7, CALENDAR: 1 << 10, CENTER: 1 << 24, RIGHT: 1 << 17, LEFT: 1 << 14,
    UP: 1 << 7, DOWN: 1 << 10,
    CTRL: 1 << 18, SHIFT: 1 << 17, ALT: 1 << 16, COMMAND: 1 << 22,
    KEYCODE_BIT: 1 << 24,
  };

  /** @typedef {{id:string,type:string,style:number,parent:string|null,props:any,el:HTMLElement,content?:HTMLElement,children:string[],row?:HTMLElement}} W */
  /** @type {Map<string, W>} */
  const widgets = new Map();
  let shellId = null;
  const dirtyLayouts = new Set();

  const has = (style, bit) => (style & bit) !== 0;
  const post = (id, eventType, data) => vscode.postMessage({ type: 'event', id, eventType, data: data || {} });

  function keyCodeOf(e) {
    const k = e.key;
    const map = { Enter: 13, Escape: 27, Delete: 127, Backspace: 8, Tab: 9, ' ': 32, ArrowUp: SWT.KEYCODE_BIT + 1,
      ArrowDown: SWT.KEYCODE_BIT + 2, ArrowLeft: SWT.KEYCODE_BIT + 3, ArrowRight: SWT.KEYCODE_BIT + 4,
      PageUp: SWT.KEYCODE_BIT + 5, PageDown: SWT.KEYCODE_BIT + 6, Home: SWT.KEYCODE_BIT + 7, End: SWT.KEYCODE_BIT + 8,
      Insert: SWT.KEYCODE_BIT + 9, Shift: SWT.KEYCODE_BIT + 0x20000, Control: SWT.KEYCODE_BIT + 0x40000,
      Alt: SWT.KEYCODE_BIT + 0x10000 };
    if (k in map) return map[k];
    const f = /^F(\d{1,2})$/.exec(k);
    if (f) return SWT.KEYCODE_BIT + 9 + Number(f[1]);
    if (k.length === 1) return k.toLowerCase().charCodeAt(0);
    return 0;
  }
  function stateMaskOf(e) {
    return (e.ctrlKey ? SWT.CTRL : 0) | (e.shiftKey ? SWT.SHIFT : 0) | (e.altKey ? SWT.ALT : 0) | (e.metaKey ? SWT.COMMAND : 0);
  }
  function keyData(e) {
    return { keyCode: keyCodeOf(e), character: e.key.length === 1 ? e.key : (e.key === 'Enter' ? '\r' : e.key === 'Escape' ? '\u001b' : e.key === 'Delete' ? '\u007f' : e.key === 'Backspace' ? '\b' : e.key === 'Tab' ? '\t' : ''), stateMask: stateMaskOf(e) };
  }

  /** Generic control events (only reported when the server listens). */
  function wireControlEvents(w, el) {
    el.addEventListener('keydown', e => { if (w.props.onkeydown) post(w.id, 'keydown', keyData(e)); });
    el.addEventListener('keyup', e => { if (w.props.onkeyup) post(w.id, 'keyup', keyData(e)); });
    el.addEventListener('focusin', e => { if (e.target === el || el.contains(/** @type {Node} */(e.target))) { if (w.props.onfocusin && !w._focused) { w._focused = true; post(w.id, 'focusin'); } } });
    el.addEventListener('focusout', e => {
      if (el.contains(/** @type {Node} */(e.relatedTarget))) return;
      if (w._focused) { w._focused = false; if (w.props.onfocusout) post(w.id, 'focusout'); }
    });
    // Mouse events carry SWT coordinates (relative to the control's client area) and the row item / table column under
    // the pointer: Tree.getItem(Point), Table.getItem(Point) and TableViewer.getCell(Point) hit-test with them.
    const mouse = (e, count) => {
      const r = el.getBoundingClientRect();
      const it = /** @type {HTMLElement} */ (e.target).closest('[data-wid]');
      const td = /** @type {HTMLElement} */ (e.target).closest('td');
      return {
        button: e.button + 1, x: Math.round(e.clientX - r.left + el.scrollLeft), y: Math.round(e.clientY - r.top + el.scrollTop),
        stateMask: stateMaskOf(e), count, item: it && it !== el ? it.dataset.wid : undefined,
        column: td && td.dataset.col !== undefined ? Number(td.dataset.col) : undefined,
      };
    };
    el.addEventListener('dblclick', e => { if (w.props.ondblclick) post(w.id, 'dblclick', mouse(e, 2)); });
    el.addEventListener('mousedown', e => { if (w.props.onmousedown) post(w.id, 'mousedown', mouse(e, e.detail)); });
    el.addEventListener('mouseup', e => { if (w.props.onmouseup) post(w.id, 'mouseup', mouse(e, e.detail)); });
    let moveTimer = 0, hoverTimer = 0, lastMove = null;
    el.addEventListener('mousemove', e => {
      lastMove = mouse(e, 0);
      if (w.props.onmousemove && !moveTimer) {
        moveTimer = setTimeout(() => { moveTimer = 0; post(w.id, 'mousemove', lastMove); }, 40);
      }
      if (w.props.onmousehover) {
        clearTimeout(hoverTimer);
        hoverTimer = setTimeout(() => post(w.id, 'mousehover', lastMove), 500);
      }
    });
    el.addEventListener('mouseenter', e => { if (w.props.onmouseenter) post(w.id, 'mouseenter', mouse(e, 0)); });
    el.addEventListener('mouseleave', e => { clearTimeout(hoverTimer); if (w.props.onmouseexit) post(w.id, 'mouseexit', mouse(e, 0)); });
    el.addEventListener('contextmenu', e => {
      if (w.props.menu || w.props.onmenudetect) {
        e.preventDefault();
        e.stopPropagation();
        post(w.id, 'menudetect', { x: e.clientX, y: e.clientY });
      }
    });
  }

  function el(tag, cls) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    return e;
  }

  // ------------------------------------------------------------------ creation

  function create(op) {
    const w = /** @type {W} */ ({ id: op.id, type: op.type, style: op.style || 0, parent: op.parent, props: {}, children: [], el: /** @type {any} */ (null) });
    const factory = factories[op.type] || factories.composite;
    factory(w);
    w.el.dataset.wid = w.id;
    widgets.set(w.id, w);
    if (op.type === 'shell') {
      shellId = w.id;
      const root = document.getElementById('root');
      root.innerHTML = '';
      root.appendChild(w.el);
    } else {
      attach(w, op.props && typeof op.props.position === 'number' ? op.props.position : -1);
    }
    setProps(w, op.props || {});
  }

  function attach(w, index) {
    const p = widgets.get(w.parent);
    if (!p) return;
    if (w.type === 'celleditor') return;
    if (index >= 0 && index < p.children.length) p.children.splice(index, 0, w.id);
    else p.children.push(w.id);
    const attachFn = attachers[p.type];
    if (attachFn) attachFn(p, w, index);
    else (p.content || p.el).appendChild(w.el);
    if (isControl(w)) markLayout(p);
  }

  function isControl(w) {
    return !['row', 'column', 'treeitem', 'tabitem', 'menu', 'menuitem', 'celleditor'].includes(w.type);
  }

  function insertAt(container, node, index, selector) {
    const siblings = selector ? Array.from(container.children).filter(c => c.matches(selector)) : Array.from(container.children);
    if (index >= 0 && index < siblings.length) container.insertBefore(node, siblings[index]);
    else container.appendChild(node);
  }

  /** Where children of special containers go. */
  const attachers = {
    table(p, w, index) {
      if (w.type === 'column') { insertAt(p.headRow, w.el, index + (has(p.style, SWT.CHECK) ? 1 : 0), 'th'); refreshTableColumns(p); return; }
      if (w.type === 'row') { insertAt(p.body, w.el, index, 'tr'); renderRow(p, w); return; }
      p.el.appendChild(w.el);
    },
    tree(p, w, index) {
      if (w.type === 'treeitem') { insertAt(p.list, w.el, index, 'li'); return; }
      if (w.type === 'column') { insertAt(p.head, w.el, index, 'th'); refreshTreeColumns(p); return; }
      p.el.appendChild(w.el);
    },
    treeitem(p, w, index) {
      if (w.type === 'treeitem') { insertAt(p.sub, w.el, index, 'li'); updateTwisty(p); }
    },
    tabfolder(p, w, index) {
      if (w.type === 'tabitem') { insertAt(p.tabs, w.el, index, '.tab'); return; }
      p.pages.appendChild(w.el);
      w.el.classList.add('tabpage');
      updateTabs(p);
    },
    scrolled(p, w) { p.content.appendChild(w.el); },
    menu(p, w, index) { if (w.type === 'menuitem') insertAt(p.list, w.el, index); },
    menuitem(p, w) { if (w.type === 'menu') p.el.appendChild(w.el); },
    group(p, w) { p.content.appendChild(w.el); },
  };

  const factories = {
    shell(w) { w.el = el('div', 'shell composite'); w.content = w.el; },
    composite(w) { w.el = el('div', 'composite'); w.content = w.el; wireControlEvents(w, w.el); },
    // The control of a JFace cell editor: not drawn, it receives the events of the inline field.
    celleditor(w) { w.el = el('span', 'celleditor'); },
    group(w) {
      w.el = el('fieldset', 'group');
      w.legend = el('legend');
      w.el.appendChild(w.legend);
      w.content = el('div', 'composite group-content');
      w.el.appendChild(w.content);
      wireControlEvents(w, w.el);
    },
    label(w) {
      if (has(w.style, SWT.SEPARATOR)) { w.el = el('div', has(w.style, SWT.VERTICAL) ? 'separator vertical' : 'separator'); return; }
      w.el = el('div', 'label');
      if (has(w.style, SWT.WRAP)) w.el.classList.add('wrap');
      if (has(w.style, SWT.CENTER)) w.el.style.textAlign = 'center';
      if (has(w.style, SWT.RIGHT)) w.el.style.textAlign = 'right';
      wireControlEvents(w, w.el);
    },
    text(w) {
      const multi = has(w.style, SWT.MULTI);
      const input = multi ? el('textarea', 'text') : el('input', 'text');
      if (!multi) /** @type {HTMLInputElement} */ (input).type = has(w.style, SWT.PASSWORD) ? 'password' : 'text';
      if (multi && !has(w.style, SWT.WRAP)) input.setAttribute('wrap', 'off');
      if (has(w.style, SWT.BORDER)) input.classList.add('border');
      w.el = input;
      const sendModify = () => {
        const i = /** @type {HTMLInputElement} */ (input);
        w.props.text = i.value;
        post(w.id, 'modify', { text: i.value, selStart: i.selectionStart, selEnd: i.selectionEnd });
      };
      input.addEventListener('input', sendModify);
      input.addEventListener('select', () => { const i = /** @type {HTMLInputElement} */ (input); post(w.id, 'selection', { selStart: i.selectionStart, selEnd: i.selectionEnd }); });
      input.addEventListener('keydown', e => {
        if (!multi && e.key === 'Enter') { post(w.id, 'default'); if (!w.props.onkeydown) e.preventDefault(); }
      });
      wireControlEvents(w, input);
    },
    // StyledText: text with background-colored ranges (read-only display; editable as plain text).
    styledtext(w) {
      const box = el('div', 'text styledtext');
      if (has(w.style, SWT.BORDER)) box.classList.add('border');
      box.style.whiteSpace = has(w.style, SWT.WRAP) ? 'pre-wrap' : 'pre';
      w.el = box;
      box.addEventListener('input', () => {
        w.props.text = box.innerText;
        post(w.id, 'modify', { text: box.innerText, selStart: 0, selEnd: 0 });
      });
      wireControlEvents(w, box);
    },
    button(w) {
      if (has(w.style, SWT.CHECK) || has(w.style, SWT.RADIO)) {
        w.el = el('label', 'check');
        const input = el('input');
        /** @type {HTMLInputElement} */ (input).type = has(w.style, SWT.CHECK) ? 'checkbox' : 'radio';
        w.input = input;
        w.textEl = el('span');
        w.el.appendChild(input);
        w.el.appendChild(w.textEl);
        input.addEventListener('change', () => {
          const i = /** @type {HTMLInputElement} */ (input);
          if (has(w.style, SWT.RADIO)) { i.checked = true; }
          w.props.selection = i.checked;
          post(w.id, 'selection', { selection: i.checked });
        });
        wireControlEvents(w, input);
        return;
      }
      w.el = el('button', 'button');
      w.textEl = w.el;
      if (has(w.style, SWT.ARROW)) {
        w.el.classList.add('arrow');
        w.el.textContent = has(w.style, SWT.UP) ? '▲' : has(w.style, SWT.DOWN) ? '▼' : has(w.style, SWT.LEFT) ? '◀' : '▶';
      }
      w.el.addEventListener('click', () => {
        if (has(w.style, SWT.TOGGLE)) { w.props.selection = !w.props.selection; w.el.classList.toggle('pressed', w.props.selection); post(w.id, 'selection', { selection: w.props.selection }); return; }
        post(w.id, 'selection');
      });
      wireControlEvents(w, w.el);
    },
    combo(w) {
      if (has(w.style, SWT.READ_ONLY)) {
        const sel = el('select', 'combo');
        w.el = sel;
        sel.addEventListener('change', () => post(w.id, 'selection', { index: /** @type {HTMLSelectElement} */ (sel).selectedIndex }));
        wireControlEvents(w, sel);
        return;
      }
      w.el = el('div', 'combo editable');
      const input = el('input', 'text border');
      /** @type {HTMLInputElement} */ (input).type = 'text';
      const listId = 'dl-' + w.id;
      input.setAttribute('list', listId);
      w.datalist = el('datalist');
      w.datalist.id = listId;
      w.input = input;
      w.el.appendChild(input);
      w.el.appendChild(w.datalist);
      // SWT Combo: Modify for every text change; picking an item of the drop-down (a datalist pick is an input
      // event without a typing inputType) also sends Selection.
      input.addEventListener('input', e => {
        const v = /** @type {HTMLInputElement} */ (input).value;
        const idx = (w.props.items || []).indexOf(v);
        const inputType = /** @type {InputEvent} */ (e).inputType;
        w.props.text = v;
        post(w.id, 'modify', { text: v });
        if (idx >= 0 && (!inputType || inputType === 'insertReplacementText')) post(w.id, 'selection', { index: idx });
      });
      input.addEventListener('keydown', e => { if (e.key === 'Enter') post(w.id, 'default'); });
      wireControlEvents(w, input);
    },
    list(w) {
      const box = el('div', 'list border');
      box.tabIndex = 0;
      w.el = box;
      w.anchor = -1;
      box.addEventListener('keydown', e => {
        const n = (w.props.items || []).length;
        if (!n) return;
        const cur = (w.props.selection || [])[0] ?? -1;
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault();
          const next = Math.max(0, Math.min(n - 1, cur + (e.key === 'ArrowDown' ? 1 : -1)));
          setListSelection(w, [next]);
          post(w.id, 'selection', { indices: [next] });
        } else if (e.key === 'Enter') {
          post(w.id, 'default', { indices: w.props.selection || [] });
        } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
          // Native list type-ahead: a letter selects the next item starting with it (and notifies the selection).
          const ch = e.key.toLowerCase();
          const items = w.props.items || [];
          for (let k = 1; k <= n; k++) {
            const i = (cur + k + n) % n;
            if (String(items[i]).toLowerCase().startsWith(ch)) {
              e.preventDefault();
              setListSelection(w, [i]);
              post(w.id, 'selection', { indices: [i] });
              break;
            }
          }
        }
      });
      wireControlEvents(w, box);
    },
    table(w) {
      w.el = el('div', 'table border');
      w.el.tabIndex = 0;
      const t = el('table');
      w.thead = el('thead');
      w.headRow = el('tr');
      w.thead.appendChild(w.headRow);
      if (has(w.style, SWT.CHECK)) w.headRow.appendChild(el('th', 'checkcol'));
      w.body = el('tbody');
      t.appendChild(w.thead);
      t.appendChild(w.body);
      w.el.appendChild(t);
      w.el.addEventListener('keydown', e => tableKey(w, e));
      wireControlEvents(w, w.el);
    },
    column(w) { w.el = el('th'); w.el.addEventListener('click', () => post(w.id, 'selection')); },
    row(w) { w.el = el('tr'); },
    tree(w) {
      w.el = el('div', 'tree border');
      w.el.tabIndex = 0;
      // the header of the columns (TreeColumn), shown when the tree has columns and its header is visible
      w.head = el('div', 'treehead');
      w.head.style.display = 'none';
      w.el.appendChild(w.head);
      w.list = el('ul');
      w.el.appendChild(w.list);
      w.el.addEventListener('keydown', e => treeKey(w, e));
      wireControlEvents(w, w.el);
    },
    treeitem(w) {
      w.el = el('li', 'treeitem');
      w.row = el('div', 'treerow');
      w.twisty = el('span', 'twisty');
      w.check = null;
      w.icon = el('img', 'icon');
      w.textEl = el('span', 'treetext');
      w.row.appendChild(w.twisty);
      w.row.appendChild(w.icon);
      w.row.appendChild(w.textEl);
      w.sub = el('ul');
      w.el.appendChild(w.row);
      w.el.appendChild(w.sub);
      w.twisty.addEventListener('click', e => { e.stopPropagation(); post(w.id === null ? '' : treeOf(w).id, w.props.expanded ? 'collapse' : 'expand', { item: w.id }); });
      w.row.addEventListener('click', e => treeClick(w, e));
      w.row.addEventListener('dblclick', () => {
        const tree = treeOf(w);
        post(tree.id, 'default', { selection: tree.props.selection || [], item: w.id });
        if (w.props.hasChildren) post(tree.id, w.props.expanded ? 'collapse' : 'expand', { item: w.id });
      });
    },
    tabfolder(w) {
      w.el = el('div', 'tabfolder');
      w.tabs = el('div', 'tabs');
      w.pages = el('div', 'pages');
      w.el.appendChild(w.tabs);
      w.el.appendChild(w.pages);
      w.content = w.pages;
    },
    tabitem(w) {
      w.el = el('button', 'tab');
      w.el.addEventListener('click', () => {
        const f = widgets.get(w.parent);
        if (!f) return;
        f.props.selection = w.id;
        updateTabs(f);
        post(f.id, 'selection', { item: w.id });
      });
    },
    scrolled(w) { w.el = el('div', 'scrolled'); w.content = el('div', 'scrolled-content'); w.el.appendChild(w.content); },
    datetime(w) {
      const input = el('input', 'text border');
      /** @type {HTMLInputElement} */ (input).type = has(w.style, SWT.TIME) ? 'time' : 'date';
      if (has(w.style, SWT.TIME)) input.setAttribute('step', '1');
      w.el = input;
      input.addEventListener('change', () => {
        const v = w.props.value ? w.props.value.slice() : [1970, 0, 1, 0, 0, 0];
        const s = /** @type {HTMLInputElement} */ (input).value;
        if (has(w.style, SWT.TIME)) { const p = s.split(':').map(Number); v[3] = p[0] || 0; v[4] = p[1] || 0; v[5] = p[2] || 0; }
        else { const p = s.split('-').map(Number); if (p.length === 3) { v[0] = p[0]; v[1] = p[1] - 1; v[2] = p[2]; } }
        w.props.value = v;
        post(w.id, 'selection', { value: v });
      });
      wireControlEvents(w, input);
    },
    browser(w) {
      w.el = el('iframe', 'browser');
      w.el.addEventListener('load', () => {
        let location = null;
        try { location = /** @type {HTMLIFrameElement} */ (w.el).contentWindow.location.href; } catch (e) { /* cross origin */ }
        post(w.id, 'loaded', { location });
      });
    },
    link(w) {
      w.el = el('div', 'link');
      w.el.addEventListener('click', e => {
        const a = /** @type {HTMLElement} */ (e.target).closest('a');
        if (!a) return;
        e.preventDefault();
        post(w.id, 'selection', { href: a.getAttribute('data-href') || a.textContent || '' });
      });
      wireControlEvents(w, w.el);
    },
    spinner(w) {
      const input = el('input', 'text border');
      /** @type {HTMLInputElement} */ (input).type = 'number';
      w.el = input;
      input.addEventListener('change', () => post(w.id, 'selection', { selection: Number(/** @type {HTMLInputElement} */ (input).value) }));
      wireControlEvents(w, input);
    },
    progress(w) { w.el = el('progress', 'progress'); },
    menu(w) { w.el = el('div', 'menu'); w.list = el('div', 'menu-items'); w.el.appendChild(w.list); },
    menuitem(w) {
      w.el = el('div', has(w.style, SWT.SEPARATOR) ? 'menu-separator' : has(w.style, SWT.RADIO) ? 'menu-item radio' : 'menu-item');
      w.el.addEventListener('click', e => { e.stopPropagation(); hideMenus(); post(w.id, 'selection'); });
    },
  };

  // ------------------------------------------------------------------ properties

  function imageUrl(path) {
    return path ? (window.RSWT_IMAGES && window.RSWT_IMAGES[path]) || '' : '';
  }

  function setProps(w, props) {
    if (!w) return;
    for (const k of Object.keys(props)) w.props[k] = props[k];
    const p = props;
    const e = w.el;
    if ('visible' in p) e.classList.toggle('invisible', p.visible === false);
    if ('enabled' in p) { e.classList.toggle('disabled', p.enabled === false); setDisabled(w, p.enabled === false); }
    if ('tooltip' in p) { if (p.tooltip) e.title = p.tooltip; else e.removeAttribute('title'); }
    if ('bg' in p) e.style.backgroundColor = p.bg || '';
    if ('fg' in p) e.style.color = p.fg || '';
    if ('font' in p) { e.style.fontWeight = p.font && p.font.bold ? 'bold' : ''; e.style.fontStyle = p.font && p.font.italic ? 'italic' : ''; }
    if ('layoutData' in p) { applyLayoutData(w); const par = widgets.get(w.parent); if (par) markLayout(par); }
    if ('decoration' in p) {
      const d = p.decoration;
      e.classList.toggle('decoration-error', !!d && d.kind === 'error');
      e.classList.toggle('decoration-warning', !!d && d.kind === 'warning');
      if (d && d.text) e.title = d.text; else if (!w.props.tooltip) e.removeAttribute('title');
    }
    if ('cls' in p) {
      if (w._cls) w._cls.forEach(c => e.classList.remove(c));
      w._cls = (p.cls || '').split(/\s+/).filter(x => x);
      w._cls.forEach(c => e.classList.add(c));
    }
    if ('layout' in p) markLayout(w);
    if ('dragSource' in p) setDragSource(w, !!p.dragSource);
    if ('dropTarget' in p) setDropTarget(w, !!p.dropTarget);
    const fn = propSetters[w.type];
    if (fn) fn(w, p);
  }

  // ------------------------------------------------------------------ drag and drop (SWT DragSource / DropTarget)

  const RSWT_SOURCE = 'application/x-rswt-source';

  /** A DragSource control: dragging it carries its widget id (and its text); the server asks it for the data. */
  function setDragSource(w, on) {
    const e = w.input || w.el;
    e.draggable = on;
    if (!on || w._dragWired) return;
    w._dragWired = true;
    e.addEventListener('dragstart', ev => {
      if (!w.props.dragSource || !ev.dataTransfer) return;
      // a tree selects the row a drag starts on, as SWT does on the mouse down (the drag carries the selection)
      let text = /** @type {any} */ (e).value !== undefined ? /** @type {any} */ (e).value : (e.textContent || '');
      if (w.type === 'tree') {
        const li = ev.target && ev.target.closest ? ev.target.closest('li.treeitem') : null;
        const id = li ? li.dataset.wid : undefined;
        if (id && !(w.props.selection || []).includes(id)) {
          w.props.selection = [id];
          updateTreeSelection(w);
          post(w.id, 'selection', { selection: [id], item: id });
        }
        const label = li ? li.querySelector('.treetext') : null;
        text = label ? label.textContent || '' : '';
      }
      ev.dataTransfer.setData(RSWT_SOURCE, w.id);
      ev.dataTransfer.setData('text/plain', text);
      ev.dataTransfer.effectAllowed = 'copyMove';
      post(w.id, 'dragstart', {});
    });
    e.addEventListener('dragend', ev => {
      if (!w.props.dragSource) return;
      post(w.id, 'dragend', { effect: ev.dataTransfer ? ev.dataTransfer.dropEffect : 'none' });
    });
  }

  /**
   * A DropTarget control: a drop sends the dragged source widget (a drag inside the webview), the URIs of the dragged
   * files (VS Code explorers: application/vnd.code.uri-list or text/uri-list) and the dragged text.
   */
  function setDropTarget(w, on) {
    const e = w.input || w.el;
    if (!on || w._dropWired) return;
    w._dropWired = true;
    e.addEventListener('dragover', ev => {
      if (!w.props.dropTarget || !ev.dataTransfer) return;
      ev.preventDefault();
      ev.dataTransfer.dropEffect = 'copy';
    });
    e.addEventListener('drop', ev => {
      if (!w.props.dropTarget || !ev.dataTransfer) return;
      ev.preventDefault();
      const dt = ev.dataTransfer;
      const uriList = dt.getData('application/vnd.code.uri-list') || dt.getData('text/uri-list') || '';
      const uris = uriList.split(/\r?\n/).map(x => x.trim()).filter(x => x && !x.startsWith('#'));
      // the item under the pointer (a tree row) and the modifier keys of the drop
      const li = ev.target && ev.target.closest ? ev.target.closest('li.treeitem') : null;
      const data = { source: dt.getData(RSWT_SOURCE) || null, uris, text: dt.getData('text/plain') || '', stateMask: stateMaskOf(ev) };
      if (li) data.item = li.dataset.wid;
      post(w.id, 'drop', data);
    });
  }

  function setDisabled(w, disabled) {
    const targets = w.input ? [w.input] : [w.el];
    for (const t of targets) {
      if ('disabled' in t) /** @type {any} */ (t).disabled = disabled;
    }
  }

  function textWithMnemonic(s) {
    return (s || '').replace(/&&/g, '\u0000').replace(/&/g, '').replace(/\u0000/g, '&');
  }

  const propSetters = {
    shell(w, p) { if ('title' in p) { document.title = p.title || ''; vscode.postMessage({ type: 'title', title: p.title || '' }); } if ('defaultButton' in p) w.defaultButton = p.defaultButton; },
    group(w, p) { if ('text' in p) w.legend.textContent = textWithMnemonic(p.text); w.legend.style.display = p.text ? '' : (w.props.text ? '' : 'none'); },
    label(w, p) {
      if ('text' in p || 'image' in p) {
        if (w.el.classList.contains('separator')) return;
        w.el.textContent = '';
        if (w.props.image) { const img = el('img', 'icon'); img.src = imageUrl(w.props.image); w.el.appendChild(img); }
        if (w.props.text) w.el.appendChild(document.createTextNode(textWithMnemonic(w.props.text)));
      }
    },
    text(w, p) {
      const i = /** @type {HTMLInputElement} */ (w.el);
      if ('text' in p && i.value !== p.text) i.value = p.text || '';
      const ro = has(w.style, SWT.READ_ONLY) || w.props.editable === false;
      i.readOnly = ro;
      i.classList.toggle('readonly', ro);
      if ('limit' in p) { if (p.limit) i.maxLength = p.limit; else i.removeAttribute('maxlength'); }
      if ('message' in p) i.placeholder = p.message || '';
    },
    styledtext(w, p) {
      if (!('text' in p) && !('styles' in p) && !('editable' in p)) return;
      const box = /** @type {HTMLElement} */ (w.el);
      const text = w.props.text || '';
      const styles = (w.props.styles || []).slice().sort((a, b) => a[0] - b[0]);
      box.innerHTML = '';
      let pos = 0;
      for (const [start, length, color] of styles) {
        if (start > pos) box.appendChild(document.createTextNode(text.substring(pos, start)));
        const span = el('span');
        span.textContent = text.substring(Math.max(start, pos), start + length);
        if (color) span.style.backgroundColor = color;
        box.appendChild(span);
        pos = Math.max(pos, start + length);
      }
      if (pos < text.length) box.appendChild(document.createTextNode(text.substring(pos)));
      const editable = !has(w.style, SWT.READ_ONLY) && w.props.editable !== false;
      box.contentEditable = editable ? 'plaintext-only' : 'false';
      box.classList.toggle('readonly', !editable);
    },
    button(w, p) {
      if ('text' in p && w.textEl && !has(w.style, SWT.ARROW)) w.textEl.textContent = p.text || '';
      if ('image' in p && !w.input && p.image) {
        const img = el('img', 'icon');
        img.src = imageUrl(p.image);
        w.el.insertBefore(img, w.el.firstChild);
      }
      if ('selection' in p) {
        if (w.input) /** @type {HTMLInputElement} */ (w.input).checked = !!p.selection;
        else w.el.classList.toggle('pressed', !!p.selection);
      }
      if ('grayed' in p && w.input) /** @type {HTMLInputElement} */ (w.input).indeterminate = !!p.grayed;
    },
    combo(w, p) {
      if (has(w.style, SWT.READ_ONLY)) {
        const sel = /** @type {HTMLSelectElement} */ (w.el);
        if ('items' in p) {
          sel.innerHTML = '';
          (p.items || []).forEach((t, i) => { const o = el('option'); o.textContent = t; o.value = String(i); sel.appendChild(o); });
        }
        sel.selectedIndex = typeof w.props.index === 'number' ? w.props.index : -1;
        return;
      }
      if ('items' in p) {
        w.datalist.innerHTML = '';
        (p.items || []).forEach(t => { const o = el('option'); /** @type {HTMLOptionElement} */ (o).value = t; w.datalist.appendChild(o); });
      }
      if ('text' in p && /** @type {HTMLInputElement} */ (w.input).value !== p.text) /** @type {HTMLInputElement} */ (w.input).value = p.text || '';
      if ('editable' in p) /** @type {HTMLInputElement} */ (w.input).readOnly = p.editable === false;
    },
    list(w, p) {
      if ('items' in p) {
        w.el.innerHTML = '';
        (p.items || []).forEach((t, i) => {
          const item = el('div', 'list-item');
          item.textContent = t;
          item.addEventListener('click', e => listClick(w, i, e));
          item.addEventListener('dblclick', () => post(w.id, 'default', { indices: w.props.selection || [] }));
          w.el.appendChild(item);
        });
      }
      setListSelection(w, w.props.selection || []);
    },
    table(w, p) {
      if ('sortColumn' in p || 'sortDirection' in p) updateSortIndicator(w);
      if ('headerVisible' in p) w.thead.style.display = p.headerVisible ? '' : 'none';
      if ('linesVisible' in p) w.el.classList.toggle('lines', !!p.linesVisible);
      if ('selection' in p) updateTableSelection(w);
      if (!('headerVisible' in w.props)) w.thead.style.display = 'none';
    },
    column(w, p) {
      if ('text' in p || 'image' in p) renderColumnHeader(w);
      if ('width' in p) w.el.style.width = p.width ? p.width + 'px' : '';
      if ('weight' in p) {
        const t0 = widgets.get(w.parent);
        if (t0) {
          const cols = tableColumns(t0);
          const total = cols.reduce((a, c) => a + (c.props.weight || 0), 0) || 1;
          cols.forEach(c => { if (c.props.weight) c.el.style.width = (c.props.weight / total * 100) + '%'; });
        }
      }
      if ('align' in p) w.el.style.textAlign = p.align || '';
      const t = widgets.get(w.parent);
      if (t && t.type === 'table') refreshTableColumns(t);
      if (t && t.type === 'tree') refreshTreeColumns(t);
      if (t) updateSortIndicator(t);
    },
    row(w, p) { const t = widgets.get(w.parent); if (t) renderRow(t, w); },
    tree(w, p) {
      if ('selection' in p) updateTreeSelection(w);
      if ('headerVisible' in p) refreshTreeColumns(w);
      if ('sortColumn' in p || 'sortDirection' in p) updateSortIndicator(w);
    },
    treeitem(w, p) {
      if ('texts' in p || 'text' in p) w.textEl.textContent = (w.props.texts && w.props.texts[0]) || w.props.text || '';
      if ('texts' in p || 'images' in p) renderTreeCells(w);
      if ('images' in p || 'image' in p) {
        const img = (w.props.images && w.props.images[0]) || w.props.image;
        w.icon.style.display = img ? '' : 'none';
        if (img) /** @type {HTMLImageElement} */ (w.icon).src = imageUrl(img);
      }
      if ('expanded' in p || 'hasChildren' in p) updateTwisty(w);
      if ('fg' in p) w.row.style.color = p.fg || '';
      if ('font' in p) w.row.style.fontWeight = p.font && p.font.bold ? 'bold' : '';
      if ('checked' in p && w.check) /** @type {HTMLInputElement} */ (w.check).checked = !!p.checked;
      const tree = treeOf(w);
      if (tree && has(tree.style, SWT.CHECK) && !w.check) {
        const c = el('input');
        /** @type {HTMLInputElement} */ (c).type = 'checkbox';
        /** @type {HTMLInputElement} */ (c).checked = !!w.props.checked;
        c.addEventListener('click', e => e.stopPropagation());
        c.addEventListener('change', () => post(tree.id, 'check', { item: w.id, checked: /** @type {HTMLInputElement} */ (c).checked }));
        w.row.insertBefore(c, w.icon);
        w.check = c;
      }
    },
    tabfolder(w, p) { if ('selection' in p) updateTabs(w); },
    tabitem(w, p) {
      if ('text' in p) w.el.textContent = textWithMnemonic(p.text);
      const f = widgets.get(w.parent);
      if (f) updateTabs(f);
    },
    scrolled(w, p) {
      if ('expandH' in p) w.content.classList.toggle('expand-h', !!p.expandH);
      if ('expandV' in p) w.content.classList.toggle('expand-v', !!p.expandV);
    },
    datetime(w, p) {
      if (!('value' in p)) return;
      const v = p.value || [1970, 0, 1, 0, 0, 0];
      const pad = n => String(n).padStart(2, '0');
      /** @type {HTMLInputElement} */ (w.el).value = has(w.style, SWT.TIME) ? `${pad(v[3])}:${pad(v[4])}:${pad(v[5])}` : `${v[0]}-${pad(v[1] + 1)}-${pad(v[2])}`;
    },
    browser(w, p) {
      const f = /** @type {HTMLIFrameElement} */ (w.el);
      // Pages of the Requality server need scripts (web socket editors); HTML text gets an opaque origin.
      if ('html' in p && p.html !== null && p.html !== undefined) {
        f.setAttribute('sandbox', 'allow-scripts allow-popups allow-modals allow-forms');
        f.removeAttribute('src');
        f.srcdoc = p.html;
      }
      if ('url' in p && p.url) {
        f.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-popups allow-modals allow-forms allow-downloads');
        f.removeAttribute('srcdoc');
        f.src = p.url;
      }
    },
    link(w, p) { if ('text' in p) w.el.innerHTML = linkHtml(p.text || ''); },
    spinner(w, p) {
      if (!('value' in p)) return;
      const i = /** @type {HTMLInputElement} */ (w.el);
      const v = p.value;
      i.value = String(v[0]); i.min = String(v[1]); i.max = String(v[2]); i.step = String(v[3] || 1);
    },
    progress(w, p) {
      if (!('value' in p)) return;
      const pr = /** @type {HTMLProgressElement} */ (w.el);
      pr.max = p.value[2] - p.value[1];
      pr.value = p.value[0] - p.value[1];
    },
    menuitem(w, p) {
      if ('text' in p && !has(w.style, SWT.SEPARATOR)) w.el.textContent = textWithMnemonic((p.text || '').split('\t')[0]);
      if ('enabled' in p) w.el.classList.toggle('disabled', p.enabled === false);
      if ('selection' in p) w.el.classList.toggle('checked', !!p.selection);
    },
  };

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  /** SWT Link text: plain text with <a> / <a href="x"> anchors; nothing else is interpreted. */
  function linkHtml(text) {
    let out = '';
    const re = /<a(?:\s+href\s*=\s*"([^"]*)")?\s*>(.*?)<\/a>/gi;
    let last = 0;
    let m;
    while ((m = re.exec(text))) {
      out += escapeHtml(text.slice(last, m.index));
      out += `<a href="#" data-href="${escapeHtml(m[1] !== undefined ? m[1] : m[2])}">${escapeHtml(m[2])}</a>`;
      last = re.lastIndex;
    }
    return out + escapeHtml(text.slice(last));
  }

  // ------------------------------------------------------------------ list

  function setListSelection(w, indices) {
    w.props.selection = indices;
    Array.from(w.el.children).forEach((c, i) => c.classList.toggle('selected', indices.includes(i)));
  }

  function listClick(w, i, e) {
    let sel = w.props.selection || [];
    if (has(w.style, SWT.MULTI) && (e.ctrlKey || e.metaKey)) {
      sel = sel.includes(i) ? sel.filter(x => x !== i) : sel.concat([i]).sort((a, b) => a - b);
    } else if (has(w.style, SWT.MULTI) && e.shiftKey && w.anchor >= 0) {
      sel = [];
      for (let k = Math.min(w.anchor, i); k <= Math.max(w.anchor, i); k++) sel.push(k);
    } else {
      sel = [i];
      w.anchor = i;
    }
    setListSelection(w, sel);
    post(w.id, 'selection', { indices: sel });
  }

  // ------------------------------------------------------------------ table

  function refreshTableColumns(t) {
    for (const rid of t.children) {
      const r = widgets.get(rid);
      if (r && r.type === 'row') renderRow(t, r);
    }
  }

  function tableColumns(t) {
    return t.children.map(id => widgets.get(id)).filter(c => c && c.type === 'column');
  }

  function renderRow(t, r) {
    const tr = r.el;
    tr.innerHTML = '';
    const cols = Math.max(1, tableColumns(t).length);
    const p = r.props;
    if (has(t.style, SWT.CHECK)) {
      const td = el('td', 'checkcol');
      const c = el('input');
      /** @type {HTMLInputElement} */ (c).type = 'checkbox';
      /** @type {HTMLInputElement} */ (c).checked = !!p.checked;
      if (p.grayed) /** @type {HTMLInputElement} */ (c).indeterminate = true;
      c.addEventListener('click', e => e.stopPropagation());
      c.addEventListener('change', () => { p.checked = /** @type {HTMLInputElement} */ (c).checked; post(t.id, 'check', { item: r.id, checked: p.checked }); });
      td.appendChild(c);
      tr.appendChild(td);
    }
    for (let i = 0; i < cols; i++) {
      const td = el('td');
      const img = p.images && p.images[i];
      if (img) { const im = el('img', 'icon'); /** @type {HTMLImageElement} */ (im).src = imageUrl(img); td.appendChild(im); }
      td.appendChild(document.createTextNode((p.texts && p.texts[i]) || ''));
      const col = tableColumns(t)[i];
      if (col && col.props.align) td.style.textAlign = col.props.align;
      if (p.fgs && p.fgs[i]) td.style.color = p.fgs[i];
      if (p.bgs && p.bgs[i]) td.style.backgroundColor = p.bgs[i];
      td.dataset.col = String(i);
      tr.appendChild(td);
    }
    if (p.fg) tr.style.color = p.fg;
    if (p.bg) tr.style.backgroundColor = p.bg;
    tr.style.fontWeight = p.font && p.font.bold ? 'bold' : '';
    tr.style.fontStyle = p.font && p.font.italic ? 'italic' : '';
    if (!r._wired) {
      r._wired = true;
      tr.addEventListener('mousedown', e => {
        if (e.button !== 0) return;
        if (/** @type {HTMLElement} */ (e.target).closest('.cell-editor')) return;
        tableSelect(t, r, e);
        const td = /** @type {HTMLElement} */ (e.target).closest('td');
        if (td && td.dataset.col !== undefined && t.props.cellEvents && !(e.ctrlKey || e.metaKey || e.shiftKey)) {
          post(t.id, 'cell', { item: r.id, column: Number(td.dataset.col), trigger: 'click' });
        }
      });
      tr.addEventListener('dblclick', e => {
        post(t.id, 'default', { selection: t.props.selection || [], item: r.id });
        const td = /** @type {HTMLElement} */ (e.target).closest('td');
        if (td && td.dataset.col !== undefined && t.props.cellEvents) post(t.id, 'cell', { item: r.id, column: Number(td.dataset.col), trigger: 'dblclick' });
      });
      tr.addEventListener('contextmenu', e => {
        if (!(t.props.selection || []).includes(r.id)) tableSelect(t, r, /** @type {any} */ ({ ctrlKey: false, metaKey: false, shiftKey: false }));
      });
    }
    updateTableSelection(t);
  }

  function rowsOf(t) {
    return t.children.map(id => widgets.get(id)).filter(r => r && r.type === 'row');
  }

  function tableSelect(t, r, e) {
    let sel = (t.props.selection || []).slice();
    const multi = has(t.style, SWT.MULTI);
    if (multi && (e.ctrlKey || e.metaKey)) {
      sel = sel.includes(r.id) ? sel.filter(x => x !== r.id) : sel.concat([r.id]);
    } else if (multi && e.shiftKey && t.anchor) {
      const rows = rowsOf(t).map(x => x.id);
      const a = rows.indexOf(t.anchor), b = rows.indexOf(r.id);
      sel = rows.slice(Math.min(a, b), Math.max(a, b) + 1);
    } else {
      sel = [r.id];
      t.anchor = r.id;
    }
    t.props.selection = sel;
    updateTableSelection(t);
    post(t.id, 'selection', { selection: sel, item: r.id });
  }

  function updateTableSelection(t) {
    const sel = new Set(t.props.selection || []);
    for (const r of rowsOf(t)) r.el.classList.toggle('selected', sel.has(r.id));
  }

  function tableKey(t, e) {
    const rows = rowsOf(t);
    if (!rows.length) return;
    const sel = t.props.selection || [];
    const cur = sel.length ? rows.findIndex(r => r.id === sel[sel.length - 1]) : -1;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      const next = rows[Math.max(0, Math.min(rows.length - 1, cur + (e.key === 'ArrowDown' ? 1 : -1)))];
      tableSelect(t, next, { ctrlKey: false, metaKey: false, shiftKey: false });
      next.el.scrollIntoView({ block: 'nearest' });
    } else if (e.key === 'Enter' && cur >= 0) {
      post(t.id, 'default', { selection: sel, item: rows[cur].id });
    } else if (e.key === 'F2' && cur >= 0 && t.props.cellEvents) {
      post(t.id, 'cell', { item: rows[cur].id, column: 0, trigger: 'key' });
    } else if (e.key === ' ' && cur >= 0 && has(t.style, SWT.CHECK)) {
      e.preventDefault();
      const r = rows[cur];
      r.props.checked = !r.props.checked;
      renderRow(t, r);
      post(t.id, 'check', { item: r.id, checked: r.props.checked });
    }
  }

  /** Inline cell editor requested by JFace (call "editCell"). */
  function editCell(t, args) {
    const r = widgets.get(args.item);
    if (!r) return;
    const tds = Array.from(r.el.children).filter(td => /** @type {HTMLElement} */ (td).dataset.col !== undefined);
    const td = /** @type {HTMLElement} */ (tds[args.column]);
    if (!td) return;
    const old = td.innerHTML;
    td.innerHTML = '';
    let editor;
    if (args.editor === 'combo') {
      editor = el('select', 'combo cell-editor');
      (args.items || []).forEach((s, i) => { const o = el('option'); o.textContent = s; o.value = String(i); editor.appendChild(o); });
      /** @type {HTMLSelectElement} */ (editor).selectedIndex = typeof args.value === 'number' ? args.value : -1;
    } else if (args.editor === 'check') {
      editor = el('input', 'cell-editor');
      /** @type {HTMLInputElement} */ (editor).type = 'checkbox';
      /** @type {HTMLInputElement} */ (editor).checked = !!args.value;
    } else {
      editor = args.multi ? el('textarea', 'text cell-editor') : el('input', 'text cell-editor');
      /** @type {HTMLInputElement} */ (editor).value = args.value == null ? '' : String(args.value);
    }
    td.appendChild(editor);
    let done = false;
    const finish = (apply) => {
      if (done) return;
      done = true;
      let value = null;
      if (args.editor === 'combo') value = /** @type {HTMLSelectElement} */ (editor).selectedIndex;
      else if (args.editor === 'check') value = /** @type {HTMLInputElement} */ (editor).checked;
      else value = /** @type {HTMLInputElement} */ (editor).value;
      td.innerHTML = old;
      post(t.id, apply ? 'cellApply' : 'cellCancel', { item: args.item, column: args.column, value });
      t.el.focus();
    };
    const current = () => args.editor === 'combo' ? /** @type {HTMLSelectElement} */ (editor).selectedIndex
      : args.editor === 'check' ? /** @type {HTMLInputElement} */ (editor).checked : /** @type {HTMLInputElement} */ (editor).value;
    const control = args.control ? widgets.get(args.control) : null;
    const toControl = (type, data) => { if (control && control.props['on' + type]) post(control.id, type, Object.assign({ value: current() }, data || {})); };
    editor.addEventListener('keydown', e => {
      e.stopPropagation();
      toControl('keydown', keyData(e));
      if (e.key === 'Enter' && !(args.multi && e.shiftKey)) { e.preventDefault(); finish(true); }
      else if (e.key === 'Escape') { e.preventDefault(); finish(false); }
      else if (e.key === 'Tab' && control && control.props.onkeydown) { e.preventDefault(); finish(true); }
    });
    editor.addEventListener('keyup', e => { e.stopPropagation(); toControl('keyup', keyData(e)); });
    editor.addEventListener('blur', () => { toControl('focusout'); finish(true); });
    if (args.editor === 'combo') editor.addEventListener('change', () => finish(true));
    editor.addEventListener('mousedown', e => e.stopPropagation());
    editor.addEventListener('dblclick', e => e.stopPropagation());
    /** @type {HTMLElement} */ (editor).focus();
    if (/** @type {any} */ (editor).select && args.editor !== 'combo') /** @type {any} */ (editor).select();
  }

  // ------------------------------------------------------------------ tree

  function treeOf(item) {
    let w = item;
    while (w && w.type === 'treeitem') w = widgets.get(w.parent);
    return w;
  }

  function updateTwisty(w) {
    const hasKids = w.props.hasChildren || w.children.some(id => { const c = widgets.get(id); return c && c.type === 'treeitem'; });
    w.twisty.textContent = hasKids ? (w.props.expanded ? '▾' : '▸') : '';
    w.sub.style.display = w.props.expanded ? '' : 'none';
  }

  function treeColumns(t) {
    return t.children.map(id => widgets.get(id)).filter(c => c && c.type === 'column');
  }

  /** The width of a column of a tree (its width, else an equal share). */
  function treeColumnWidth(c) {
    return c.props.width ? c.props.width : 120;
  }

  /** TreeColumn: the header of the columns and the cells of the items (the first column holds the tree). */
  function refreshTreeColumns(t) {
    const cols = treeColumns(t);
    t.head.style.display = cols.length > 0 && t.props.headerVisible ? '' : 'none';
    cols.forEach(c => { c.el.style.width = treeColumnWidth(c) + 'px'; c.el.style.minWidth = c.el.style.width; });
    const visit = (ids) => ids.forEach(id => {
      const it = widgets.get(id);
      if (it && it.type === 'treeitem') { renderTreeCells(it); visit(it.children); }
    });
    visit(t.children);
  }

  function itemDepth(w) {
    let d = 0;
    for (let p = widgets.get(w.parent); p && p.type === 'treeitem'; p = widgets.get(p.parent)) d++;
    return d;
  }

  /** The cells of the columns after the first one (texts[i], images[i]); the first cell is as wide as its column. */
  function renderTreeCells(w) {
    const tree = treeOf(w);
    if (!tree) return;
    const cols = treeColumns(tree);
    if (w.cells) { w.cells.forEach(c => c.remove()); w.cells = null; }
    if (cols.length < 2) { w.textEl.style.width = ''; w.textEl.style.flex = ''; return; }
    const indent = itemDepth(w) * 14 + 2;
    const first = treeColumnWidth(cols[0]) - indent;
    // the first column: the twisty, check box, icon and text of the item
    w.textEl.style.flex = '0 0 auto';
    const used = 16 + (w.check ? 18 : 0) + (w.icon.style.display === 'none' ? 0 : 18);
    w.textEl.style.width = Math.max(20, first - used) + 'px';
    w.textEl.style.overflow = 'hidden';
    w.textEl.style.textOverflow = 'ellipsis';
    w.cells = [];
    for (let i = 1; i < cols.length; i++) {
      const cell = el('span', 'treecell');
      cell.style.width = treeColumnWidth(cols[i]) + 'px';
      const img = w.props.images && w.props.images[i];
      if (img) { const im = el('img', 'icon'); /** @type {HTMLImageElement} */ (im).src = imageUrl(img); cell.appendChild(im); }
      cell.appendChild(document.createTextNode((w.props.texts && w.props.texts[i]) || ''));
      if (cols[i].props.align) cell.style.textAlign = cols[i].props.align;
      cell.dataset.col = String(i);
      w.row.appendChild(cell);
      w.cells.push(cell);
    }
  }

  /** The header of a column: its image, its text and the sort indicator of its table or tree. */
  function renderColumnHeader(c) {
    const t = widgets.get(c.parent);
    const arrow = t && t.props.sortColumn === c.id && t.props.sortDirection ? (t.props.sortDirection === 'up' ? ' ▲' : ' ▼') : '';
    c.el.textContent = '';
    if (c.props.image) { const im = el('img', 'icon'); /** @type {HTMLImageElement} */ (im).src = imageUrl(c.props.image); c.el.appendChild(im); }
    c.el.appendChild(document.createTextNode((c.props.text || '') + arrow));
  }

  /** Table/Tree.setSortColumn/setSortDirection: an arrow in the header of the sort column. */
  function updateSortIndicator(t) {
    t.children.map(id => widgets.get(id)).filter(c => c && c.type === 'column').forEach(renderColumnHeader);
  }

  function treeClick(w, e) {
    const tree = treeOf(w);
    let sel = (tree.props.selection || []).slice();
    if (has(tree.style, SWT.MULTI) && (e.ctrlKey || e.metaKey)) sel = sel.includes(w.id) ? sel.filter(x => x !== w.id) : sel.concat([w.id]);
    else sel = [w.id];
    tree.props.selection = sel;
    updateTreeSelection(tree);
    post(tree.id, 'selection', { selection: sel, item: w.id });
    // the cell clicked in a tree with columns (cell editing support of the viewer, e.g. the completion of a task)
    if (treeColumns(tree).length > 1) {
      const cell = e.target && e.target.closest ? e.target.closest('.treecell') : null;
      post(tree.id, 'cellclick', { item: w.id, column: cell ? Number(cell.dataset.col) : 0 });
    }
  }

  function updateTreeSelection(tree) {
    const sel = new Set(tree.props.selection || []);
    tree.el.querySelectorAll('.treerow').forEach(r => {
      const li = r.parentElement;
      r.classList.toggle('selected', !!li && sel.has(li.dataset.wid));
    });
  }

  function visibleTreeItems(tree) {
    const out = [];
    const walk = list => {
      for (const li of Array.from(list.children)) {
        const w = widgets.get(/** @type {HTMLElement} */ (li).dataset.wid);
        if (!w) continue;
        out.push(w);
        if (w.props.expanded) walk(w.sub);
      }
    };
    walk(tree.list);
    return out;
  }

  function treeKey(tree, e) {
    const items = visibleTreeItems(tree);
    if (!items.length) return;
    const sel = tree.props.selection || [];
    const cur = sel.length ? items.findIndex(i => i.id === sel[sel.length - 1]) : -1;
    const select = it => { tree.props.selection = [it.id]; updateTreeSelection(tree); post(tree.id, 'selection', { selection: [it.id], item: it.id }); it.row.scrollIntoView({ block: 'nearest' }); };
    if (e.key === 'ArrowDown') { e.preventDefault(); select(items[Math.min(items.length - 1, cur + 1)]); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); select(items[Math.max(0, cur - 1)]); }
    else if (e.key === 'ArrowRight' && cur >= 0 && !items[cur].props.expanded) post(tree.id, 'expand', { item: items[cur].id });
    else if (e.key === 'ArrowLeft' && cur >= 0 && items[cur].props.expanded) post(tree.id, 'collapse', { item: items[cur].id });
    else if (e.key === 'Enter' && cur >= 0) post(tree.id, 'default', { selection: sel, item: items[cur].id });
  }

  // ------------------------------------------------------------------ tabs, menus

  function updateTabs(f) {
    const selected = f.props.selection;
    const items = f.children.map(id => widgets.get(id)).filter(i => i && i.type === 'tabitem');
    let shownControl = null;
    for (const it of items) {
      const on = it.id === selected;
      it.el.classList.toggle('selected', on);
      if (on) shownControl = it.props.control;
    }
    for (const id of f.children) {
      const c = widgets.get(id);
      if (c && c.type !== 'tabitem') c.el.classList.toggle('hidden-page', c.id !== shownControl);
    }
  }

  function hideMenus() {
    document.querySelectorAll('.menu.shown').forEach(m => m.classList.remove('shown'));
  }

  function showMenu(m, x, y) {
    hideMenus();
    document.body.appendChild(m.el);
    m.el.classList.add('shown');
    m.el.style.left = Math.min(x, window.innerWidth - 220) + 'px';
    m.el.style.top = Math.min(y, window.innerHeight - m.el.offsetHeight - 4) + 'px';
  }
  document.addEventListener('mousedown', e => { if (!/** @type {HTMLElement} */ (e.target).closest('.menu')) hideMenus(); });
  // Escape closes the shown popup menu (SWT).
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && document.querySelector('.menu.shown')) { e.preventDefault(); hideMenus(); }
  });

  // ------------------------------------------------------------------ layout (SWT GridLayout placement)

  function markLayout(w) {
    if (w) dirtyLayouts.add(w.id);
  }

  function applyLayoutData(w) {
    const ld = w.props.layoutData;
    const s = w.el.style;
    s.justifySelf = s.alignSelf = s.gridColumn = s.gridRow = s.width = s.height = s.minWidth = s.minHeight = s.marginLeft = s.marginTop = '';
    w.el.classList.remove('excluded');
    if (!ld) return;
    if (ld.exclude) w.el.classList.add('excluded');
    if (ld.type === 'grid') {
      const map = { start: 'start', center: 'center', end: 'end', fill: 'stretch' };
      s.justifySelf = map[ld.ha] || 'start';
      s.alignSelf = map[ld.va] || 'center';
      if (ld.w) { if (ld.ha === 'fill') s.minWidth = ld.w + 'px'; else s.width = ld.w + 'px'; }
      if (ld.h) { if (ld.va === 'fill') s.minHeight = ld.h + 'px'; else s.height = ld.h + 'px'; }
      if (ld.minw) s.minWidth = ld.minw + 'px';
      if (ld.minh) s.minHeight = ld.minh + 'px';
      if (ld.hi) s.marginLeft = ld.hi + 'px';
      if (ld.vi) s.marginTop = ld.vi + 'px';
    } else if (ld.type === 'row') {
      if (ld.w) s.width = ld.w + 'px';
      if (ld.h) s.height = ld.h + 'px';
    }
  }

  function layoutChildren(w) {
    return w.children.map(id => widgets.get(id)).filter(c => c && isControl(c) && !c.el.classList.contains('tabpage-skip'));
  }

  function doLayout(w) {
    const box = w.content || w.el;
    const lay = w.props.layout;
    const s = box.style;
    s.display = s.gridTemplateColumns = s.gridTemplateRows = s.flexDirection = s.flexWrap = s.gap = s.padding = s.columnGap = s.rowGap = '';
    const kids = layoutChildren(w);
    if (w.type === 'tabfolder' || w.type === 'scrolled' || w.type === 'table' || w.type === 'tree' || w.type === 'combo' || w.type === 'browser' || w.type === 'datetime' || w.type === 'spinner') return;
    if (!lay) {
      s.display = 'flex';
      s.flexDirection = 'column';
      return;
    }
    if (lay.type === 'fill') {
      s.display = 'flex';
      s.flexDirection = lay.vertical ? 'column' : 'row';
      s.gap = (lay.spacing || 0) + 'px';
      s.padding = `${lay.mh || 0}px ${lay.mw || 0}px`;
      for (const c of kids) { c.el.style.flex = '1 1 0'; c.el.style.minWidth = '0'; c.el.style.minHeight = '0'; }
      return;
    }
    if (lay.type === 'row') {
      s.display = 'flex';
      s.flexDirection = lay.vertical ? 'column' : 'row';
      s.flexWrap = lay.wrap ? 'wrap' : 'nowrap';
      s.gap = (lay.spacing || 0) + 'px';
      s.padding = `${lay.mt}px ${lay.mr}px ${lay.mb}px ${lay.ml}px`;
      s.alignItems = lay.fill ? 'stretch' : (lay.center ? 'center' : 'flex-start');
      s.justifyContent = lay.justify ? 'space-between' : 'flex-start';
      return;
    }
    // GridLayout: simulate SWT placement (left to right, wrap at numColumns, honouring spans) so that column and row
    // growth follow grabExcess*Space like SWT.
    const n = Math.max(1, lay.numColumns || 1);
    s.display = 'grid';
    s.columnGap = (lay.hs ?? 5) + 'px';
    s.rowGap = (lay.vs ?? 5) + 'px';
    s.padding = `${lay.mt ?? 5}px ${lay.mr ?? 5}px ${lay.mb ?? 5}px ${lay.ml ?? 5}px`;
    const occupied = [];
    const isOcc = (r, c) => occupied[r] && occupied[r][c];
    const occ = (r, c) => { (occupied[r] = occupied[r] || [])[c] = true; };
    let row = 0, col = 0;
    const colGrab = new Array(n).fill(false);
    const rowGrab = [];
    let rows = 0;
    for (const c of kids) {
      const ld = c.props.layoutData && c.props.layoutData.type === 'grid' ? c.props.layoutData : null;
      if (ld && ld.exclude) { c._spanGrab = null; c.el.style.gridColumn = c.el.style.gridRow = ''; continue; }
      const hspan = Math.max(1, Math.min(n, ld ? ld.hspan || 1 : 1));
      const vspan = Math.max(1, ld ? ld.vspan || 1 : 1);
      // find the next free cell range
      for (;;) {
        if (col + hspan > n) { row++; col = 0; continue; }
        let free = true;
        for (let k = 0; k < hspan; k++) if (isOcc(row, col + k)) { free = false; break; }
        if (free) break;
        col++;
      }
      for (let r = 0; r < vspan; r++) for (let k = 0; k < hspan; k++) occ(row + r, col + k);
      c.el.style.gridColumn = `${col + 1} / span ${hspan}`;
      c.el.style.gridRow = `${row + 1} / span ${vspan}`;
      if (ld && ld.gh) {
        if (hspan === 1) colGrab[col] = true; else c._spanGrab = [col, hspan];
      } else c._spanGrab = null;
      if (ld && ld.gv) rowGrab[row + vspan - 1] = true;
      if (!ld) { c.el.style.justifySelf = 'start'; c.el.style.alignSelf = 'center'; }
      rows = Math.max(rows, row + vspan);
      col += hspan;
    }
    for (const c of kids) {
      if (c._spanGrab) {
        const [start, span] = c._spanGrab;
        let any = false;
        for (let k = start; k < start + span; k++) if (colGrab[k]) any = true;
        if (!any) colGrab[start + span - 1] = true;
      }
    }
    // equal columns (SWT: as wide as the widest control that does not grab, else an equal share): a column without a
    // grabbing control is not narrower than its controls, which are cut by the parent rather than drawn over each other
    s.gridTemplateColumns = lay.equal
      ? Array.from({ length: n }, (_, k) => (colGrab[k] ? 'minmax(0, 1fr)' : 'minmax(min-content, 1fr)')).join(' ')
      : colGrab.map(g => (g ? 'minmax(0, 1fr)' : 'auto')).join(' ');
    const rt = [];
    for (let r = 0; r < rows; r++) rt.push(rowGrab[r] ? 'minmax(0, 1fr)' : 'auto');
    s.gridTemplateRows = rt.join(' ');
  }

  function flushLayouts() {
    for (const id of dirtyLayouts) {
      const w = widgets.get(id);
      if (w) doLayout(w);
    }
    dirtyLayouts.clear();
  }

  // ------------------------------------------------------------------ operations

  function dispose(id) {
    const w = widgets.get(id);
    if (!w) return;
    for (const c of w.children.slice()) dispose(c);
    widgets.delete(id);
    if (w.el && w.el.parentElement) w.el.parentElement.removeChild(w.el);
    const p = widgets.get(w.parent);
    if (p) {
      p.children = p.children.filter(c => c !== id);
      if (p.type === 'table' && w.type === 'column') refreshTableColumns(p);
      if (p.type === 'table' && w.type === 'row') updateTableSelection(p);
      if (p.type === 'treeitem') updateTwisty(p);
      if (p.type === 'tabfolder') updateTabs(p);
      if (isControl(w)) markLayout(p);
    }
  }

  function call(op) {
    const w = widgets.get(op.id);
    if (!w) return;
    switch (op.method) {
      case 'focus': {
        const t = w.input || w.el;
        if (t && t.focus) setTimeout(() => t.focus(), 0);
        break;
      }
      case 'select':
        if (w.el.setSelectionRange) setTimeout(() => { try { w.el.setSelectionRange(op.args[0], op.args[1]); } catch (e) { /* not selectable */ } }, 0);
        break;
      case 'showItem': {
        const it = widgets.get(op.args);
        if (it) (it.row || it.el).scrollIntoView({ block: 'nearest' });
        break;
      }
      case 'editCell': editCell(w, op.args); break;
      case 'show': showMenu(w, op.args[0], op.args[1]); break;
      case 'order': {
        const box = w.content || w.el;
        w.children = op.args.slice();
        for (const id of op.args) { const c = widgets.get(id); if (c) box.appendChild(c.el); }
        markLayout(w);
        break;
      }
      case 'execute':
        try { /** @type {HTMLIFrameElement} */ (w.el).contentWindow.postMessage({ requalityExecute: op.args }, '*'); } catch (e) { /* cross origin */ }
        break;
      case 'evaluate':
        try {
          /** @type {HTMLIFrameElement} */ (w.el).contentWindow.postMessage({ requalityEvaluate: op.args.script, id: op.args.id }, '*');
        } catch (e) {
          post(w.id, 'evaluated', { id: op.args.id, value: null });
        }
        break;
      case 'refresh':
        if (w.el.src) w.el.src = w.el.src;
        break;
      case 'reveal':
        vscode.postMessage({ type: 'reveal' });
        break;
      default:
        break;
    }
  }

  function apply(ops) {
    for (const op of ops) {
      try {
        if (op.op === 'create') create(op);
        else if (op.op === 'set') setProps(widgets.get(op.id), op.props || {});
        else if (op.op === 'dispose') dispose(op.id);
        else if (op.op === 'call') call(op);
      } catch (err) {
        console.error('rswt op failed', op, err);
      }
    }
    flushLayouts();
  }

  // Enter activates the shell's default button (SWT behaviour), unless a multi-line text or a button has focus.
  document.addEventListener('keydown', e => {
    if (e.key !== 'Enter' || e.defaultPrevented) return;
    const shell = widgets.get(shellId);
    if (!shell || !shell.defaultButton) return;
    const t = /** @type {HTMLElement} */ (e.target);
    if (t.tagName === 'TEXTAREA' || t.tagName === 'BUTTON' || t.tagName === 'SELECT' || t.closest('.table') || t.closest('.tree') || t.closest('.list')) return;
    const b = widgets.get(shell.defaultButton);
    if (b && !b.el.disabled && !b.el.classList.contains('disabled')) { e.preventDefault(); post(b.id, 'selection'); }
  });

  /** Messages of the bridge script inside Browser iframes (vscode-bridge.js). */
  function browserMessage(ev) {
    const m = ev.data;
    if (!m || typeof m !== 'object' || typeof m.requality !== 'string') return false;
    for (const w of widgets.values()) {
      if (w.type !== 'browser' || /** @type {HTMLIFrameElement} */ (w.el).contentWindow !== ev.source) continue;
      if (m.requality === 'navigate') {
        if (w.props.onlocation) post(w.id, 'changing', { location: m.url, top: true });
        else /** @type {HTMLIFrameElement} */ (w.el).src = m.url;
      } else if (m.requality === 'evaluated') {
        post(w.id, 'evaluated', { id: m.id, value: m.value === undefined ? null : m.value });
      } else if (m.requality === 'alert' || m.requality === 'open-external') {
        vscode.postMessage({ type: m.requality, text: m.text, url: m.url });
      }
      return true;
    }
    return false;
  }

  window.addEventListener('message', ev => {
    if (browserMessage(ev)) return;
    const m = ev.data;
    if (!m || typeof m !== 'object') return;
    if (m.type === 'images') { window.RSWT_IMAGES = Object.assign(window.RSWT_IMAGES || {}, m.images); return; }
    if (m.type === 'snapshot') {
      widgets.clear();
      document.getElementById('root').innerHTML = '';
      if (m.images) window.RSWT_IMAGES = Object.assign(window.RSWT_IMAGES || {}, m.images);
      apply(m.ops || []);
      return;
    }
    if (m.type === 'update') {
      if (m.images) window.RSWT_IMAGES = Object.assign(window.RSWT_IMAGES || {}, m.images);
      apply(m.ops || []);
    }
    // SWT Display.post: a key event given to the focused control as the keyboard gives it (VS Code also gets it, as
    // every key of a webview, for its key bindings)
    if (m.type === 'postEvent' && m.event && /^key(down|up)$/.test(m.event.type)) {
      const target = document.activeElement || document.body;
      target.dispatchEvent(new KeyboardEvent(m.event.type, Object.assign({}, m.event, { bubbles: true, cancelable: true })));
    }
  });
  window.RSWT_IMAGES = {};
  // Focus of the webview activates the part shown in it (server Workbench).
  window.addEventListener('focus', () => vscode.postMessage({ type: 'focus' }));
  window.addEventListener('blur', () => vscode.postMessage({ type: 'blur' }));
  vscode.postMessage({ type: 'ready' });
})();
