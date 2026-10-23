
/* ---- requality-vscode bridge (appended to the upstream varStorage.js at build time) ----
 * The Requality pages run in an iframe inside a VS Code webview. Webviews block modal dialogs and pop-up windows,
 * so alert() and window.open() are forwarded to the extension, which shows a notification or opens the browser. */
(function () {
  if (window.parent === window || window.__requalityBridge) {
    return;
  }
  window.__requalityBridge = true;
  var post = function (msg) {
    try {
      window.parent.postMessage(msg, '*');
    } catch (e) {
      /* the page keeps working without the bridge */
    }
  };
  window.alert = function (text) {
    post({ requality: 'alert', text: String(text) });
  };
  // Browser widget of the server (rswt): scripts run in the page, values go back to the server.
  window.addEventListener('message', function (ev) {
    if (ev.source !== window.parent || !ev.data || typeof ev.data !== 'object') {
      return;
    }
    var d = ev.data;
    if (typeof d.requalityExecute === 'string') {
      try {
        (0, eval)(d.requalityExecute);
      } catch (e) {
        /* same as SWT Browser.execute returning false */
      }
    } else if (typeof d.requalityEvaluate === 'string') {
      var value = null;
      try {
        value = new Function(d.requalityEvaluate)();
        if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
          value = String(value);
        }
      } catch (e) {
        value = null;
      }
      post({ requality: 'evaluated', id: d.id, value: value === undefined ? null : value });
    }
  });
  // Link navigation is reported to the server first (SWT LocationListener.changing may cancel it).
  document.addEventListener('click', function (ev) {
    if (ev.defaultPrevented || ev.button !== 0 || ev.ctrlKey || ev.metaKey || ev.shiftKey) {
      return;
    }
    var a = ev.target && ev.target.closest ? ev.target.closest('a[href]') : null;
    if (!a || a.target === '_blank') {
      return;
    }
    var href = a.getAttribute('href');
    if (!href || href.charAt(0) === '#' || /^javascript:/i.test(href)) {
      return;
    }
    ev.preventDefault();
    post({ requality: 'navigate', url: a.href });
  }, true);
  var nativeOpen = window.open;
  window.open = function (url, target, features) {
    if (typeof url === 'string' && /^(https?|mailto):/i.test(url) && url.indexOf(location.origin) !== 0) {
      post({ requality: 'open-external', url: url });
      return null;
    }
    return nativeOpen.apply(window, arguments);
  };
})();
