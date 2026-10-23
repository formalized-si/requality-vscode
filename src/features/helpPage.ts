// The page of the Requality manual in a webview (the Eclipse help window); no VS Code API so that it is unit tested.
/** The options of the help page: the TOC markup, the root of the help resources, the home and first pages, bookmarks. */
export interface HelpPageOptions {
  cspSource: string;
  nonce: string;
  toc: string;
  root: string;
  home: string;
  first: string;
  bookmarks: Array<{ href: string; title: string }>;
}

/**
 * The page of the help (the Eclipse help window): the Contents, Search Results and Bookmarks tabs with the search field,
 * the toolbar (Go Back, Go Forward, Home, Show in Table of Contents, Bookmark Document, Print Page) and the topic frame.
 */
export function helpPageHtml(o: HelpPageOptions): string {
  return `<!DOCTYPE html><html><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src ${o.cspSource} data:; connect-src ${o.cspSource}; frame-src 'self'; style-src ${o.cspSource} 'unsafe-inline'; script-src 'nonce-${o.nonce}';">
<style>
body{margin:0;display:flex;height:100vh;font-family:var(--vscode-font-family);font-size:var(--vscode-font-size);color:var(--vscode-foreground);background:var(--vscode-editor-background)}
#side{width:300px;display:flex;flex-direction:column;border-right:1px solid var(--vscode-panel-border)}
#main{flex:1;display:flex;flex-direction:column;min-width:0}
.search{display:flex;gap:4px;align-items:center;padding:6px}
.search input{flex:1;min-width:0;background:var(--vscode-input-background);color:var(--vscode-input-foreground);border:1px solid var(--vscode-input-border,transparent);padding:2px 4px}
button{background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground);border:0;padding:2px 8px;cursor:pointer}
button:disabled{opacity:.5;cursor:default}
.tabs{display:flex;border-bottom:1px solid var(--vscode-panel-border)}
.tabs button{background:transparent;color:var(--vscode-foreground);border-bottom:2px solid transparent}
.tabs button.active{border-bottom-color:var(--vscode-focusBorder)}
.pane{flex:1;overflow:auto;padding:6px 4px}
.pane ul{list-style:none;margin:0;padding-left:12px} .pane>ul{padding-left:2px}
.pane a{color:var(--vscode-textLink-foreground);text-decoration:none;line-height:20px} .pane a:hover{text-decoration:underline}
.pane a.current{font-weight:bold;background:var(--vscode-list-inactiveSelectionBackground)}
.toolbar{display:flex;gap:2px;padding:4px;border-bottom:1px solid var(--vscode-panel-border)}
.toolbar button{background:transparent;color:var(--vscode-foreground);font-size:14px}
.del{margin-left:6px;cursor:pointer;opacity:.7}
iframe{flex:1;border:0;background:#fff}
</style></head><body>
<div id="side">
  <div class="search"><label for="q">Search:</label><input id="q" type="text"><button id="go">Go</button></div>
  <div class="tabs"><button data-tab="contents" class="active">Contents</button><button data-tab="results">Search Results</button><button data-tab="bookmarks">Bookmarks</button></div>
  <nav id="contents" class="pane">${o.toc}</nav>
  <div id="results" class="pane" hidden></div>
  <div id="bookmarks" class="pane" hidden></div>
</div>
<div id="main">
  <div class="toolbar">
    <button id="back" title="Go Back" disabled>&#9664;</button><button id="fwd" title="Go Forward" disabled>&#9654;</button>
    <button id="home" title="Home">&#8962;</button><button id="sync" title="Show in Table of Contents">&#8644;</button>
    <button id="mark" title="Bookmark Document">&#9733;</button><button id="print" title="Print Page">&#9113;</button>
  </div>
  <iframe id="page"></iframe>
</div>
<script nonce="${o.nonce}">
const vscode = acquireVsCodeApi();
const ROOT = ${JSON.stringify(o.root)};
const HOME = ${JSON.stringify(o.home)};
let bookmarks = ${JSON.stringify(o.bookmarks)};
const frame = document.getElementById('page');
const history = [];
let position = -1;
function scrollTo(doc, anchor) {
  if (!anchor) return;
  const el = doc.getElementById(anchor) || doc.querySelector('a[name="' + CSS.escape(anchor) + '"]');
  if (el) el.scrollIntoView();
}
function fileOf(url) { const h = url.indexOf('#'); return h >= 0 ? url.slice(0, h) : url; }
function updateButtons() {
  document.getElementById('back').disabled = position <= 0;
  document.getElementById('fwd').disabled = position >= history.length - 1;
}
async function show(url, push) {
  if (push !== false) { history.splice(position + 1); history.push(url); position = history.length - 1; }
  updateButtons();
  const file = fileOf(url);
  const anchor = url.indexOf('#') >= 0 ? decodeURIComponent(url.slice(url.indexOf('#') + 1)) : '';
  if (frame.dataset.file === file && frame.contentDocument) { scrollTo(frame.contentDocument, anchor); return; }
  let text;
  try { text = await (await fetch(file)).text(); } catch (e) { text = '<p>Cannot load ' + file + '</p>'; }
  const base = file.slice(0, file.lastIndexOf('/') + 1);
  text = text.replace(/<\?xml[^>]*>/, '');
  text = /<head[^>]*>/i.test(text) ? text.replace(/<head([^>]*)>/i, '<head$1><base href="' + base + '">') : '<base href="' + base + '">' + text;
  frame.onload = () => {
    const doc = frame.contentDocument;
    if (!doc) return;
    vscode.postMessage({ type: 'loaded', title: doc.title });
    scrollTo(doc, anchor);
    doc.addEventListener('click', e => {
      const a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
      if (!a) return;
      const href = a.getAttribute('href');
      e.preventDefault();
      if (href.startsWith('#')) { scrollTo(doc, decodeURIComponent(href.slice(1))); return; }
      const target = new URL(href, base).toString();
      if (target.startsWith(base.slice(0, base.indexOf('/', 8)))) show(target);
      else vscode.postMessage({ type: 'external', url: target });
    });
  };
  frame.dataset.file = file;
  frame.srcdoc = text;
}
function current() { return position >= 0 ? history[position] : HOME; }
function showTab(name) {
  document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
  for (const id of ['contents', 'results', 'bookmarks']) document.getElementById(id).hidden = id !== name;
}
document.querySelectorAll('.tabs button').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));
function wire(container) {
  container.querySelectorAll('a[data-src]').forEach(a => a.addEventListener('click', e => { e.preventDefault(); show(a.dataset.src); }));
}
wire(document.getElementById('contents'));
// Show in Table of Contents: the topic of the current page is selected in the Contents tab
function syncToc() {
  showTab('contents');
  const file = fileOf(current());
  let found = null;
  document.querySelectorAll('#contents a[data-src]').forEach(a => { const hit = fileOf(a.dataset.src) === file && !found; a.classList.toggle('current', hit); if (hit) found = a; });
  if (found) found.scrollIntoView({ block: 'nearest' });
}
// Search: the topics whose pages contain all the words (a quoted phrase is one word), as the help search does by default
const pageText = new Map();
async function textOf(file) {
  if (!pageText.has(file)) {
    let t = '';
    try { t = new DOMParser().parseFromString(await (await fetch(file)).text(), 'text/html').body.textContent || ''; } catch (e) { t = ''; }
    pageText.set(file, t.toLowerCase().replace(/\\s+/g, ' '));
  }
  return pageText.get(file);
}
async function search() {
  const q = document.getElementById('q').value.trim();
  const results = document.getElementById('results');
  showTab('results');
  if (!q) { results.textContent = ''; return; }
  const words = (q.match(/"[^"]+"|\\S+/g) || []).map(w => w.replace(/^"|"$/g, '').toLowerCase());
  results.textContent = 'Searching...';
  const hits = [];
  const seen = new Set();
  for (const a of document.querySelectorAll('#contents a[data-src]')) {
    const file = fileOf(a.dataset.src);
    if (seen.has(a.dataset.src)) continue;
    seen.add(a.dataset.src);
    const text = await textOf(file);
    if (words.every(w => text.includes(w))) hits.push(a);
  }
  results.innerHTML = '';
  const head = document.createElement('div');
  head.textContent = hits.length + (hits.length === 1 ? ' match' : ' matches');
  results.appendChild(head);
  const ul = document.createElement('ul');
  for (const a of hits) { const li = document.createElement('li'); const l = document.createElement('a'); l.href = '#'; l.dataset.src = a.dataset.src; l.textContent = a.textContent; li.appendChild(l); ul.appendChild(li); }
  results.appendChild(ul);
  wire(results);
}
function renderBookmarks() {
  const pane = document.getElementById('bookmarks');
  pane.innerHTML = '';
  const all = document.createElement('button');
  all.textContent = 'Delete All';
  all.disabled = bookmarks.length === 0;
  all.addEventListener('click', () => vscode.postMessage({ type: 'removeBookmark' }));
  pane.appendChild(all);
  const ul = document.createElement('ul');
  for (const b of bookmarks) {
    const li = document.createElement('li');
    const l = document.createElement('a'); l.href = '#'; l.dataset.src = ROOT + b.href; l.textContent = b.title; li.appendChild(l);
    const d = document.createElement('span'); d.className = 'del'; d.title = 'Delete'; d.textContent = '\u2715';
    d.addEventListener('click', () => vscode.postMessage({ type: 'removeBookmark', href: b.href }));
    li.appendChild(d); ul.appendChild(li);
  }
  pane.appendChild(ul);
  wire(pane);
}
window.addEventListener('message', e => { if (e.data && e.data.type === 'bookmarks') { bookmarks = e.data.list; renderBookmarks(); } });
document.getElementById('go').addEventListener('click', search);
document.getElementById('q').addEventListener('keydown', e => { if (e.key === 'Enter') search(); });
document.getElementById('back').addEventListener('click', () => { if (position > 0) { position--; show(history[position], false); } });
document.getElementById('fwd').addEventListener('click', () => { if (position < history.length - 1) { position++; show(history[position], false); } });
document.getElementById('home').addEventListener('click', () => show(HOME));
document.getElementById('sync').addEventListener('click', syncToc);
document.getElementById('mark').addEventListener('click', () => {
  const url = current();
  if (!url.startsWith(ROOT)) return;
  const title = (frame.contentDocument && frame.contentDocument.title) || url;
  vscode.postMessage({ type: 'bookmark', href: url.slice(ROOT.length), title });
  showTab('bookmarks');
});
document.getElementById('print').addEventListener('click', () => { if (frame.contentWindow) frame.contentWindow.print(); });
renderBookmarks();
show(${JSON.stringify(o.first)});
</script></body></html>`;
}
