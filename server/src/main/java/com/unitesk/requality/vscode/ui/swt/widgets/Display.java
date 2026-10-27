package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.SWTException;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Cursor;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.graphics.RGB;
import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;

/**
 * {@code org.eclipse.swt.widgets.Display} of the remote SWT implementation: one UI thread for all shells, exactly
 * like SWT. Each {@link Shell} is rendered by a webview of the extension; widget changes are sent as operations
 * after every UI runnable, and client events are queued to the UI thread. Modal dialogs spin a nested event loop
 * ({@link #readAndDispatch()}), so code ported from SWT keeps its control flow.
 */
public final class Display {
  /** Sends operations to the extension and opens/closes shells there. */
  public interface Transport {
    /** Operations for a shell; {@code seq} increases with every update of that shell. */
    void update(String shell, long seq, JsonArray ops);

    void openShell(Shell shell, JsonObject options);

    void closeShell(Shell shell);
  }

  private static final Display DEFAULT = new Display();
  private static volatile Transport transport;

  private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
  private final Thread thread;
  private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "rswt-timers");
    t.setDaemon(true);
    return t;
  });
  /** All live widgets in creation order (parents before children). */
  final Map<String, Widget> widgets = new LinkedHashMap<>();
  private final Map<String, List<JsonObject>> pending = new LinkedHashMap<>();
  private final Map<String, Object> data = new HashMap<>();
  private final Map<String, Long> seqs = new HashMap<>();
  private final List<Runnable> disposeRunnables = new ArrayList<>();
  private long nextId;
  private Control focusControl;
  private Shell activeShell;

  private Display() {
    thread = new Thread(this::loop, "rswt-ui");
    thread.setDaemon(true);
    thread.start();
  }

  public static Display getDefault() {
    return DEFAULT;
  }

  /** The display if called on the UI thread, else null (like SWT). */
  public static Display getCurrent() {
    return Thread.currentThread() == DEFAULT.thread ? DEFAULT : null;
  }

  public static Display findDisplay(Thread t) {
    return t == DEFAULT.thread ? DEFAULT : null;
  }

  public static void setTransport(Transport t) {
    transport = t;
  }

  static Transport transport() {
    return transport;
  }

  public Thread getThread() {
    return thread;
  }

  public boolean isUIThread() {
    return Thread.currentThread() == thread;
  }

  private void loop() {
    while (true) {
      try {
        Runnable r = queue.take();
        runAndFlush(r);
      } catch (InterruptedException e) {
        return;
      } catch (Throwable t) {
        t.printStackTrace(System.err);
      }
    }
  }

  private void runAndFlush(Runnable r) {
    try {
      r.run();
    } catch (Throwable t) {
      System.err.println("[rswt] exception in UI runnable:");
      t.printStackTrace(System.err);
    } finally {
      flush();
    }
  }

  /** Processes one queued runnable if any (SWT readAndDispatch). Only on the UI thread. */
  public boolean readAndDispatch() {
    checkThread();
    Runnable r = queue.poll();
    if (r == null)
      return false;
    runAndFlush(r);
    return true;
  }

  /** Waits briefly until a runnable is queued (SWT sleep). */
  public boolean sleep() {
    checkThread();
    long end = System.currentTimeMillis() + 50;
    while (queue.isEmpty() && System.currentTimeMillis() < end) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return !queue.isEmpty();
  }

  /** Runs the nested event loop until the condition holds (modal dialogs). */
  public void runUntil(java.util.function.BooleanSupplier done) {
    checkThread();
    flush();
    while (!done.getAsBoolean()) {
      try {
        Runnable r = queue.poll(50, TimeUnit.MILLISECONDS);
        if (r != null)
          runAndFlush(r);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  public void wake() {
    queue.offer(() -> {
    });
  }

  /** {@code Display.addFilter}: VS Code sends no key events to the server, so the filter never runs. */
  // VSCODE: keyboard events are handled by the VS Code client; a filter on the display is never called here.
  public void addFilter(int eventType, Listener listener) {
  }

  public void asyncExec(Runnable r) {
    asyncExec(com.unitesk.requality.vscode.ui.runtime.Background.current(), r);
  }

  /** {@link #asyncExec(Runnable)} of work that {@code from} hands over (see {@code Background}). */
  public void asyncExec(com.unitesk.requality.vscode.ui.runtime.Background.Holder from, Runnable r) {
    if (r != null)
      queue.offer(guarded(from, r));
  }

  /**
   * The work in the queue: it runs when no call of the server runs (with the lock of the background work). The display
   * thread does not wait for the lock (a call can wait for the display): the work goes to the end of the queue again.
   */
  private Runnable guarded(com.unitesk.requality.vscode.ui.runtime.Background.Holder from, Runnable r) {
    return new Runnable() {
      @Override
      public void run() {
        if (!com.unitesk.requality.vscode.ui.runtime.Background.tryRun(from, r))
          timers.schedule(() -> queue.offer(this), 20, TimeUnit.MILLISECONDS);
      }
    };
  }

  public void syncExec(Runnable r) {
    if (r == null)
      return;
    if (isUIThread()) {
      r.run();
      return;
    }
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> err = new AtomicReference<>();
    queue.offer(guarded(com.unitesk.requality.vscode.ui.runtime.Background.current(), () -> {
      try {
        r.run();
      } catch (Throwable t) {
        err.set(t);
      } finally {
        done.countDown();
      }
    }));
    try {
      done.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (err.get() instanceof RuntimeException)
      throw (RuntimeException) err.get();
    if (err.get() instanceof Error)
      throw (Error) err.get();
    if (err.get() != null)
      throw new SWTException(String.valueOf(err.get()));
  }

  /** Runs a computation on the UI thread and returns its result. */
  public <T> T syncCall(java.util.function.Supplier<T> s) {
    AtomicReference<T> r = new AtomicReference<>();
    syncExec(() -> r.set(s.get()));
    return r.get();
  }

  public void timerExec(int milliseconds, Runnable r) {
    if (r == null || milliseconds < 0)
      return;
    timers.schedule(() -> asyncExec(r), milliseconds, TimeUnit.MILLISECONDS);
  }

  public boolean isDisposed() {
    return false;
  }

  public void update() {
    if (isUIThread())
      flush();
  }

  public void beep() {
  }

  public void disposeExec(Runnable r) {
    disposeRunnables.add(r);
  }

  public Object getData() {
    return data.get(null);
  }

  public void setData(Object o) {
    data.put(null, o);
  }

  public Object getData(String key) {
    return data.get(key);
  }

  public void setData(String key, Object value) {
    data.put(key, value);
  }

  public Shell getActiveShell() {
    return activeShell;
  }

  void setActiveShell(Shell s) {
    if (s != null)
      synchronized (activations) {
        activations.remove(s);
        activations.add(s);
      }
    activeShell = s;
  }

  /** The shells in the order of their activation (the last one activated last). */
  private final java.util.LinkedHashSet<Shell> activations = new java.util.LinkedHashSet<>();

  /**
   * A shell is disposed: when it was the active one, the last activated shell that is still open becomes active (the
   * activation returns to the window under it, as the window system does for SWT).
   */
  void shellDisposed(Shell s) {
    Shell next = null;
    synchronized (activations) {
      activations.remove(s);
      for (Shell x : activations)
        if (x.isOpened())
          next = x;
    }
    if (activeShell == s)
      activeShell = next;
  }

  public Shell[] getShells() {
    List<Shell> r = new ArrayList<>();
    synchronized (this) {
      for (Widget w : widgets.values())
        if (w instanceof Shell)
          r.add((Shell) w);
    }
    return r.toArray(new Shell[0]);
  }

  public Control getFocusControl() {
    return focusControl;
  }

  void setFocusControl(Control c) {
    focusControl = c;
  }

  private final java.util.Map<Integer, com.unitesk.requality.vscode.ui.swt.graphics.Image> systemImages = new java.util.HashMap<>();

  /** {@code Display.getSystemImage}: the error, information, question and warning icons of the platform. */
  public synchronized com.unitesk.requality.vscode.ui.swt.graphics.Image getSystemImage(int id) {
    String file;
    switch (id) {
      case SWT.ICON_ERROR:
        file = "error.png";
        break;
      case SWT.ICON_INFORMATION:
      case SWT.ICON_WORKING:
        file = "info.png";
        break;
      case SWT.ICON_QUESTION:
        file = "question.png";
        break;
      case SWT.ICON_WARNING:
        file = "warning.png";
        break;
      default:
        return null;
    }
    return systemImages.computeIfAbsent(id, k -> com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor
        .fromResource("icons/shared/" + file).createImage());
  }

  public Color getSystemColor(int id) {
    switch (id) {
      case SWT.COLOR_WHITE:
        return new Color(255, 255, 255);
      case SWT.COLOR_BLACK:
        return new Color(0, 0, 0);
      case SWT.COLOR_RED:
        return Color.theme("var(--vscode-errorForeground)", new RGB(255, 0, 0));
      case SWT.COLOR_DARK_RED:
        return new Color(128, 0, 0);
      case SWT.COLOR_GREEN:
        return new Color(0, 255, 0);
      case SWT.COLOR_DARK_GREEN:
        return new Color(0, 128, 0);
      case SWT.COLOR_YELLOW:
        return new Color(255, 255, 0);
      case SWT.COLOR_BLUE:
        return Color.theme("var(--vscode-textLink-foreground)", new RGB(0, 0, 255));
      case SWT.COLOR_GRAY:
        return Color.theme("var(--vscode-disabledForeground)", new RGB(192, 192, 192));
      case SWT.COLOR_DARK_GRAY:
        return Color.theme("var(--vscode-descriptionForeground)", new RGB(128, 128, 128));
      case SWT.COLOR_WIDGET_BACKGROUND:
      case SWT.COLOR_TITLE_INACTIVE_BACKGROUND:
        return Color.theme("var(--vscode-sideBar-background)", new RGB(240, 240, 240));
      case SWT.COLOR_LIST_BACKGROUND:
        return Color.theme("var(--vscode-input-background)", new RGB(255, 255, 255));
      case SWT.COLOR_INFO_BACKGROUND:
        return Color.theme("var(--vscode-editorHoverWidget-background)", new RGB(255, 255, 225));
      default:
        return Color.theme("var(--vscode-foreground)", new RGB(0, 0, 0));
    }
  }

  public Font getSystemFont() {
    return new Font(this, "", 10, SWT.NORMAL);
  }

  public Cursor getSystemCursor(int id) {
    return new Cursor(this, id);
  }

  public Point getCursorLocation() {
    return new Point(0, 0);
  }

  public Rectangle getBounds() {
    return new Rectangle(0, 0, 1920, 1080);
  }

  public Rectangle getClientArea() {
    return getBounds();
  }

  // ------------------------------------------------------------------ widget registry and operations

  synchronized String register(Widget w) {
    String id = "w" + (++nextId);
    widgets.put(id, w);
    return id;
  }

  synchronized void unregister(Widget w) {
    widgets.remove(w.id);
    if (focusControl == w)
      focusControl = null;
  }

  public synchronized Widget find(String id) {
    return widgets.get(id);
  }

  /** Queues an operation for the shell's webview. */
  synchronized void op(Shell shell, JsonObject op) {
    if (shell == null)
      return;
    pending.computeIfAbsent(shell.id, k -> new ArrayList<>()).add(op);
  }

  /** Sends queued operations; layout data are re-serialized because SWT code mutates GridData objects in place. */
  public void flush() {
    Map<String, List<JsonObject>> out;
    Map<String, Long> outSeq = new HashMap<>();
    synchronized (this) {
      for (Widget w : new ArrayList<>(widgets.values()))
        if (w instanceof Control && !w.isDisposed())
          ((Control) w).syncLayoutData();
      if (pending.isEmpty())
        return;
      out = new LinkedHashMap<>(pending);
      pending.clear();
      for (String shell : out.keySet())
        outSeq.put(shell, seqs.merge(shell, 1L, Long::sum));
    }
    Transport t = transport;
    if (t == null)
      return;
    for (Map.Entry<String, List<JsonObject>> e : out.entrySet()) {
      JsonArray arr = new JsonArray();
      e.getValue().forEach(arr::add);
      t.update(e.getKey(), outSeq.get(e.getKey()), arr);
    }
  }

  /**
   * All operations that recreate a shell's widget tree in display order (used when its webview (re)loads), and the
   * sequence number of the last update they include.
   */
  public synchronized JsonObject snapshot(Shell shell) {
    JsonArray arr = new JsonArray();
    appendSnapshot(shell, arr);
    pending.remove(shell.id);
    JsonObject r = new JsonObject();
    r.add("ops", arr);
    r.addProperty("seq", seqs.getOrDefault(shell.id, 0L));
    return r;
  }

  private void appendSnapshot(Widget w, JsonArray arr) {
    if (w.isDisposed())
      return;
    if (w instanceof Control)
      ((Control) w).syncLayoutData();
    arr.add(w.createOp());
    for (Widget c : w.snapshotChildren())
      appendSnapshot(c, arr);
  }

  /** Delivers a client event on the UI thread. */
  private static final java.util.Set<String> USER_INPUT = new java.util.HashSet<>(java.util.Arrays.asList("selection",
      "default", "modify", "check", "cell", "cellApply", "dblclick", "keydown", "keyup", "mousedown", "mouseup",
      "menudetect", "traverse", "expand", "collapse", "changing"));

  public void dispatchClientEvent(String widgetId, String type, JsonObject data) {
    asyncExec(() -> {
      Widget w = find(widgetId);
      if (w == null || w.isDisposed())
        return;
      // SWT delivers no user input to a disabled control (or a control in a disabled parent).
      if (w instanceof Control && !((Control) w).isEnabled() && USER_INPUT.contains(type))
        return;
      JsonObject d = data == null ? new JsonObject() : data;
      if (w instanceof Control && com.unitesk.requality.vscode.ui.swt.dnd.DragAndDrop.dispatch((Control) w, type, d))
        return;
      w.onClientEvent(type, d);
    });
  }

  void checkThread() {
    if (!isUIThread())
      throw new SWTException("Invalid thread access");
  }
}
