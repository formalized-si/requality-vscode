package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.browser.LocationEvent;
import com.unitesk.requality.vscode.ui.swt.browser.LocationListener;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressEvent;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressListener;

/**
 * {@code org.eclipse.swt.browser.Browser}: an iframe showing HTML text or a URL. Pages of the Requality resources
 * server carry the VS Code bridge (vscode-bridge.js), which reports link navigation ({@code changing}, cancellable),
 * runs scripts and returns {@link #evaluate} results.
 */
public class Browser extends Composite {
  private static final long EVALUATE_TIMEOUT_MS = 10000;
  private String text = "";
  private String url = "";
  private final List<LocationListener> locationListeners = new CopyOnWriteArrayList<>();
  private volatile int loads;
  private final List<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();
  private final Map<Integer, JsonElement> results = new HashMap<>();
  private int nextEval;

  public Browser(Composite parent, int style) {
    super(parent, style, "browser");
    created();
  }

  public boolean setText(String html) {
    text = html == null ? "" : html;
    url = "about:blank";
    set("url", null);
    set("html", text);
    return true;
  }

  public boolean setText(String html, boolean trusted) {
    return setText(html);
  }

  public String getText() {
    return text;
  }

  public boolean setUrl(String url) {
    this.url = url;
    set("html", null);
    set("url", url);
    return true;
  }

  public boolean setUrl(String url, String postData, String[] headers) {
    return setUrl(url);
  }

  public String getUrl() {
    return url;
  }

  public boolean execute(String script) {
    call("execute", script);
    return true;
  }

  /** Runs the script in the page and waits for its value (the UI thread keeps dispatching meanwhile). */
  public Object evaluate(String script) {
    return evaluate(script, false);
  }

  public Object evaluate(String script, boolean trusted) {
    int id;
    synchronized (results) {
      id = ++nextEval;
    }
    JsonObject args = new JsonObject();
    args.addProperty("id", id);
    args.addProperty("script", script);
    call("evaluate", args);
    long deadline = System.currentTimeMillis() + EVALUATE_TIMEOUT_MS;
    Display d = getDisplay();
    if (d.isUIThread()) {
      d.runUntil(() -> {
        synchronized (results) {
          return results.containsKey(id) || isDisposed() || System.currentTimeMillis() > deadline;
        }
      });
    }
    JsonElement v;
    synchronized (results) {
      v = results.remove(id);
    }
    return toJava(v);
  }

  private static Object toJava(JsonElement v) {
    if (v == null || v.isJsonNull())
      return null;
    if (v.isJsonPrimitive()) {
      if (v.getAsJsonPrimitive().isBoolean())
        return v.getAsBoolean();
      if (v.getAsJsonPrimitive().isNumber())
        return v.getAsDouble();
      return v.getAsString();
    }
    if (v.isJsonArray()) {
      Object[] r = new Object[v.getAsJsonArray().size()];
      for (int i = 0; i < r.length; i++)
        r[i] = toJava(v.getAsJsonArray().get(i));
      return r;
    }
    return v.toString();
  }

  public void refresh() {
    call("refresh", null);
  }

  public boolean back() {
    return false;
  }

  public boolean forward() {
    return false;
  }

  public boolean isBackEnabled() {
    return false;
  }

  public boolean isForwardEnabled() {
    return false;
  }

  public void stop() {
  }

  /** {@code org.eclipse.swt.browser.Browser.close()}: closes the browser's window through the page ({@code window.close()}). */
  public void close() {
    execute("window.close();");
  }

  public String getBrowserType() {
    return "webkit";
  }

  public void addLocationListener(LocationListener l) {
    locationListeners.add(l);
    set("onlocation", true);
  }

  public void removeLocationListener(LocationListener l) {
    locationListeners.remove(l);
  }

  public void addProgressListener(ProgressListener l) {
    progressListeners.add(l);
  }

  public void removeProgressListener(ProgressListener l) {
    progressListeners.remove(l);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    switch (type) {
      case "changing": {
        LocationEvent e = new LocationEvent(this);
        e.location = d.has("location") ? d.get("location").getAsString() : "";
        e.top = !d.has("top") || d.get("top").getAsBoolean();
        for (LocationListener l : locationListeners)
          l.changing(e);
        if (e.doit && e.top)
          setUrl(e.location);
        break;
      }
      case "loaded": {
        loads++;
        LocationEvent e = new LocationEvent(this);
        e.location = d.has("location") && !d.get("location").isJsonNull() ? d.get("location").getAsString() : url;
        if (d.has("location") && !d.get("location").isJsonNull() && !"about:srcdoc".equals(e.location))
          url = e.location;
        for (LocationListener l : locationListeners)
          l.changed(e);
        ProgressEvent p = new ProgressEvent(this);
        p.current = p.total = 100;
        for (ProgressListener l : progressListeners)
          l.completed(p);
        break;
      }
      case "evaluated": {
        synchronized (results) {
          results.put(d.get("id").getAsInt(), d.get("value"));
        }
        break;
      }
      default:
        super.onClientEvent(type, d);
    }
  }

  /** How many times the page of the widget finished loading in the webview (diagnostics of the web editors). */
  public int getLoadCount() {
    return loads;
  }
}
