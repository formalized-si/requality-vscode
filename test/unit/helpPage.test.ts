import { describe, test } from 'node:test';
import * as assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { helpPageHtml } from '../../src/features/helpPage';

const PAGES: Record<string, string> = {
  'R/docs/a.html': '<html><head><title>Alpha</title></head><body><p>alpha beta</p></body></html>',
  'R/docs/b.html': '<html><head><title>Beta</title></head><body><p>beta gamma delta</p></body></html>',
};

/** The help page in a window whose VS Code API and fetch are stubs; the posted messages are collected. */
function open(bookmarks: Array<{ href: string; title: string }> = []): { dom: JSDOM; posted: Array<Record<string, unknown>> } {
  const posted: Array<Record<string, unknown>> = [];
  const html = helpPageHtml({
    cspSource: 'csp', nonce: 'n', root: 'R/', home: 'R/docs/a.html', first: 'R/docs/a.html', bookmarks,
    toc: '<ul><li><a href="#" data-src="R/docs/a.html">Alpha topic</a></li><li><a href="#" data-src="R/docs/b.html">Beta topic</a></li></ul>',
  });
  const dom = new JSDOM(html, {
    runScripts: 'dangerously',
    beforeParse(window) {
      (window as unknown as Record<string, unknown>).acquireVsCodeApi = () => ({ postMessage: (m: Record<string, unknown>) => posted.push(m) });
      (window as unknown as Record<string, unknown>).fetch = async (url: string) => ({ text: async () => PAGES[url] ?? '' });
      // jsdom does not lay out pages: scrolling an element into view does nothing
      window.HTMLElement.prototype.scrollIntoView = function () { /* no layout */ };
    },
  });
  return { dom, posted };
}

/** A plain copy of an object of the window (another realm). */
function plain(o: unknown): unknown {
  return JSON.parse(JSON.stringify(o));
}

async function until(what: string, probe: () => boolean): Promise<void> {
  for (let i = 0; i < 200; i++) {
    if (probe()) return;
    await new Promise(r => setTimeout(r, 5));
  }
  assert.fail('timed out: ' + what);
}

describe('help page (the Eclipse help window)', () => {
  test('has the Contents, Search Results and Bookmarks tabs and the help toolbar', () => {
    const { dom } = open();
    const d = dom.window.document;
    assert.deepEqual([...d.querySelectorAll('.tabs button')].map(b => b.textContent), ['Contents', 'Search Results', 'Bookmarks']);
    assert.deepEqual([...d.querySelectorAll('.toolbar button')].map(b => b.getAttribute('title')),
      ['Go Back', 'Go Forward', 'Home', 'Show in Table of Contents', 'Bookmark Document', 'Print Page']);
    assert.equal(d.querySelector('.search label')?.textContent, 'Search:');
  });

  test('Search lists the topics whose pages contain all the words', async () => {
    const { dom } = open();
    const d = dom.window.document;
    (d.getElementById('q') as HTMLInputElement).value = 'beta gamma';
    (d.getElementById('go') as HTMLButtonElement).click();
    await until('the search results', () => d.querySelectorAll('#results a').length > 0);
    assert.deepEqual([...d.querySelectorAll('#results a')].map(a => a.textContent), ['Beta topic']);
    assert.equal(d.getElementById('results')?.hidden, false, 'the Search Results tab is shown');
    (d.getElementById('q') as HTMLInputElement).value = 'beta';
    (d.getElementById('go') as HTMLButtonElement).click();
    await until('two results', () => d.querySelectorAll('#results a').length === 2);
  });

  test('Go Back and Go Forward follow the pages shown; Show in Table of Contents selects the topic', async () => {
    const { dom } = open();
    const d = dom.window.document;
    const back = d.getElementById('back') as HTMLButtonElement;
    const fwd = d.getElementById('fwd') as HTMLButtonElement;
    assert.equal(back.disabled, true);
    (d.querySelectorAll('#contents a')[1] as HTMLAnchorElement).click();
    await until('back enabled', () => !back.disabled);
    (d.getElementById('sync') as HTMLButtonElement).click();
    assert.equal(d.querySelector('#contents a.current')?.textContent, 'Beta topic');
    back.click();
    await until('forward enabled', () => !fwd.disabled);
    (d.getElementById('sync') as HTMLButtonElement).click();
    assert.equal(d.querySelector('#contents a.current')?.textContent, 'Alpha topic');
  });

  test('Bookmark Document posts the page; the bookmarks are listed and deleted', () => {
    const { dom, posted } = open([{ href: 'docs/b.html', title: 'Beta' }]);
    const d = dom.window.document;
    assert.deepEqual([...d.querySelectorAll('#bookmarks a')].map(a => a.textContent), ['Beta']);
    (d.getElementById('mark') as HTMLButtonElement).click();
    assert.deepEqual(plain(posted.find(m => m.type === 'bookmark')), { type: 'bookmark', href: 'docs/a.html', title: 'R/docs/a.html' });
    dom.window.dispatchEvent(new dom.window.MessageEvent('message', { data: { type: 'bookmarks', list: [{ href: 'docs/a.html', title: 'Alpha' }, { href: 'docs/b.html', title: 'Beta' }] } }));
    assert.deepEqual([...d.querySelectorAll('#bookmarks a')].map(a => a.textContent), ['Alpha', 'Beta']);
    (d.querySelector('#bookmarks .del') as HTMLElement).click();
    assert.deepEqual(plain(posted.at(-1)), { type: 'removeBookmark', href: 'docs/a.html' });
  });
});
