package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.SWTException;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;

/**
 * {@code org.eclipse.swt.widgets.Widget}. The server holds the widget state ({@link #props}); every change is sent to
 * the shell's webview as a {@code set} operation and client events update the state before listeners run.
 */
public abstract class Widget {
  final Display display;
  final String id;

  /** The id of the widget in the renderer (protocol helper, not SWT API). */
  public String getWidgetId() {
    return id;
  }
  protected int style;
  private final String kind;
  private boolean disposed;
  private boolean createSent;
  private List<JsonObject> callsBeforeCreate;
  private Object data;
  private Map<String, Object> keyedData;
  private final Map<Integer, List<Listener>> listeners = new HashMap<>();
  /** Client-visible state; serialized in create operations and snapshots. */
  final Map<String, JsonElement> props = new LinkedHashMap<>();

  protected Widget(Widget parent, int style, String kind) {
    this.display = parent != null ? parent.display : Display.getDefault();
    this.style = style;
    this.kind = kind;
    this.id = display.register(this);
  }

  /** Emits the create operation; called by subclasses at the end of their constructor. */
  protected final void created() {
    createSent = true;
    display.op(getShellInternal(), createOp());
    if (callsBeforeCreate != null) {
      for (JsonObject c : callsBeforeCreate)
        display.op(getShellInternal(), c);
      callsBeforeCreate = null;
    }
  }

  JsonObject createOp() {
    JsonObject o = new JsonObject();
    o.addProperty("op", "create");
    o.addProperty("id", id);
    o.addProperty("type", kind);
    Widget p = getParentWidget();
    o.addProperty("parent", p == null ? null : p.id);
    o.addProperty("style", style);
    JsonObject pr = new JsonObject();
    synchronized (display) {
      for (Map.Entry<String, JsonElement> e : props.entrySet())
        pr.add(e.getKey(), e.getValue());
    }
    o.add("props", pr);
    return o;
  }

  /** Owned widgets in display order, for snapshots (children, columns, rows, tab items, menus). */
  java.util.List<Widget> snapshotChildren() {
    return java.util.Collections.emptyList();
  }

  /** The widget this one is rendered inside (parent control or owning item/control). */
  abstract Widget getParentWidget();

  abstract Shell getShellInternal();

  public String getId() {
    return id;
  }

  public String getKind() {
    return kind;
  }

  /** Sets a client-visible property; sends it when the value changed. */
  protected final void set(String prop, Object value) {
    JsonElement v = value == null ? JsonNull.INSTANCE : JsonRpcConnection.toJson(value);
    synchronized (display) {
      if (Objects.equals(props.get(prop), v))
        return;
      props.put(prop, v);
    }
    if (disposed || !createSent)
      return;
    JsonObject o = new JsonObject();
    o.addProperty("op", "set");
    o.addProperty("id", id);
    JsonObject pr = new JsonObject();
    pr.add(prop, v);
    o.add("props", pr);
    display.op(getShellInternal(), o);
  }

  /** Updates state from the client without echoing it back. */
  protected final void setQuiet(String prop, Object value) {
    synchronized (display) {
      props.put(prop, value == null ? JsonNull.INSTANCE : JsonRpcConnection.toJson(value));
    }
  }

  protected final JsonElement prop(String prop) {
    synchronized (display) {
      return props.get(prop);
    }
  }

  /** Asks the renderer to perform an action (focus, scroll, cell editing). */
  protected final void call(String method, Object args) {
    if (disposed)
      return;
    JsonObject o = new JsonObject();
    o.addProperty("op", "call");
    o.addProperty("id", id);
    o.addProperty("method", method);
    o.add("args", JsonRpcConnection.toJson(args));
    if (!createSent) {
      if (callsBeforeCreate == null)
        callsBeforeCreate = new ArrayList<>();
      callsBeforeCreate.add(o);
      return;
    }
    display.op(getShellInternal(), o);
  }

  /** Handles an event sent by the renderer. Runs on the UI thread. */
  protected void onClientEvent(String type, JsonObject data) {
  }

  protected void checkWidget() {
    if (disposed)
      throw new SWTException("Widget is disposed");
  }

  public Display getDisplay() {
    return display;
  }

  public int getStyle() {
    return style;
  }

  public boolean isDisposed() {
    return disposed;
  }

  private boolean disposeSent;

  public void dispose() {
    if (disposed)
      return;
    // SWT Widget.release: the Dispose event first (children still alive), then the children.
    if (!disposeSent) {
      disposeSent = true;
      Event e = new Event();
      notifyListeners(SWT.Dispose, e);
    }
    releaseChildren();
    disposed = true;
    display.unregister(this);
    JsonObject o = new JsonObject();
    o.addProperty("op", "dispose");
    o.addProperty("id", id);
    display.op(getShellInternal(), o);
    releaseWidget();
  }

  /** Disposes owned widgets before this one. */
  void releaseChildren() {
  }

  /** Detaches from the parent after disposal. */
  void releaseWidget() {
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public Object getData(String key) {
    return keyedData == null ? null : keyedData.get(key);
  }

  public void setData(String key, Object value) {
    if (keyedData == null)
      keyedData = new HashMap<>();
    if (value == null)
      keyedData.remove(key);
    else
      keyedData.put(key, value);
  }

  public void addListener(int eventType, Listener listener) {
    if (listener == null)
      return;
    synchronized (listeners) {
      listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }
    listenersChanged(eventType);
  }

  /** Removes a typed listener (registered through a TypedListener). */
  protected void removeTyped(int eventType, Object listener) {
    synchronized (listeners) {
      java.util.List<Listener> l = listeners.get(eventType);
      if (l != null)
        l.removeIf(x -> x == listener || (x instanceof TypedListener && ((TypedListener) x).getEventListener() == listener));
    }
    listenersChanged(eventType);
  }

  public void removeListener(int eventType, Listener listener) {
    synchronized (listeners) {
      List<Listener> l = listeners.get(eventType);
      if (l != null)
        l.removeIf(x -> x == listener || (x instanceof TypedListener && ((TypedListener) x).getEventListener() == listener));
    }
    listenersChanged(eventType);
  }

  /** Lets the renderer know which events must be reported (key and mouse events are sent only when listened). */
  void listenersChanged(int eventType) {
    String name = clientEventName(eventType);
    if (name != null)
      set("on" + name, isListening(eventType));
  }

  private static String clientEventName(int type) {
    switch (type) {
      case SWT.KeyUp:
        return "keyup";
      case SWT.KeyDown:
        return "keydown";
      case SWT.MouseDoubleClick:
        return "dblclick";
      case SWT.MouseDown:
        return "mousedown";
      case SWT.MouseUp:
        return "mouseup";
      case SWT.MouseMove:
        return "mousemove";
      case SWT.MouseEnter:
        return "mouseenter";
      case SWT.MouseExit:
        return "mouseexit";
      case SWT.MouseHover:
        return "mousehover";
      case SWT.FocusIn:
        return "focusin";
      case SWT.FocusOut:
        return "focusout";
      case SWT.MenuDetect:
        return "menudetect";
      case SWT.Traverse:
        return "traverse";
      case SWT.Verify:
        return "verify";
      default:
        return null;
    }
  }

  public boolean isListening(int eventType) {
    synchronized (listeners) {
      List<Listener> l = listeners.get(eventType);
      return l != null && !l.isEmpty();
    }
  }

  public Listener[] getListeners(int eventType) {
    synchronized (listeners) {
      List<Listener> l = listeners.get(eventType);
      return l == null ? new Listener[0] : l.toArray(new Listener[0]);
    }
  }

  public void notifyListeners(int eventType, Event event) {
    if (event == null)
      event = new Event();
    event.type = eventType;
    event.widget = this;
    event.display = display;
    if (event.time == 0)
      event.time = (int) System.currentTimeMillis();
    for (Listener l : getListeners(eventType)) {
      if (disposed && eventType != SWT.Dispose)
        return;
      l.handleEvent(event);
    }
  }

  public void addDisposeListener(DisposeListener l) {
    addListener(SWT.Dispose, new TypedListener(l));
  }

  public void removeDisposeListener(DisposeListener l) {
    removeTyped(SWT.Dispose, l);
  }

  protected void addTyped(int type, Object listener) {
    addListener(type, new TypedListener(listener));
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + id + "}";
  }
}
