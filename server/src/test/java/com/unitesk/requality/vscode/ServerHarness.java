package com.unitesk.requality.vscode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;

/**
 * Runs a Requality server in-process and talks to it over piped streams exactly like the extension does. The core
 * keeps global state, so use one harness per test class (Gradle forks a JVM per class).
 */
public final class ServerHarness implements AutoCloseable {
  public final File root;
  public final File workspace;
  public final File storage;
  // Threads are kept: a piped stream breaks ("Write end dead") when the thread that last wrote to it has ended.
  private final ExecutorService pool = new java.util.concurrent.ThreadPoolExecutor(0, Integer.MAX_VALUE, 1,
      java.util.concurrent.TimeUnit.HOURS, new java.util.concurrent.SynchronousQueue<>(), r -> {
        Thread t = new Thread(r, "harness");
        t.setDaemon(true);
        return t;
      });
  private final JsonRpcConnection client;
  private final RequalityServer server;
  /** Every request the server sent to the client: method and parameters. */
  public final List<Map.Entry<String, JsonObject>> clientRequests = new CopyOnWriteArrayList<>();
  public final List<Map.Entry<String, JsonObject>> notifications = new CopyOnWriteArrayList<>();
  /** The ui/openShell notifications of the progress dialogs (title "Progress Information"), not in {@link #notifications}. */
  public final List<JsonObject> progressShells = new CopyOnWriteArrayList<>();
  private final java.util.Set<String> progressShellIds = ConcurrentHashMap.newKeySet();
  private final Map<String, Function<JsonObject, Object>> answers = new ConcurrentHashMap<>();

  public ServerHarness() throws IOException {
    // The question about converting an older catalog format (asked while a test catalog loads) is accepted.
    autoAnswer.add("Project Loading");
    root = Files.createTempDirectory("requality-test").toFile();
    workspace = new File(root, "workspace");
    storage = new File(root, "storage");
    workspace.mkdirs();
    storage.mkdirs();
    Pipe up = new Pipe(), down = new Pipe();
    java.io.OutputStream toServer = up.out;
    java.io.InputStream serverIn = up.in;
    java.io.OutputStream toClient = down.out;
    java.io.InputStream clientIn = down.in;
    Main.ServerOptions options = new Main.ServerOptions();
    options.resources = new File(System.getProperty("requality.resources"));
    options.storage = storage;
    server = new RequalityServer(new JsonRpcConnection(serverIn, toClient, pool), options);
    server.registerServices();
    client = new JsonRpcConnection(clientIn, toServer, pool);
    // Default answers of a user who confirms everything.
    answer("client/showMessage", p -> {
      JsonArray items = p.getAsJsonArray("items");
      return items != null && items.size() > 0 ? items.get(0).getAsString() : null;
    });
    answer("client/showLongMessage", p -> {
      JsonArray b = p.getAsJsonArray("buttons");
      return b != null && b.size() > 0 ? b.get(0).getAsString() : null;
    });
    answer("client/inputBox", p -> p.has("value") && !p.get("value").isJsonNull() ? p.get("value").getAsString() : null);
    // The system clipboard: the last text the server put on it (client/clipboard).
    answer("client/readClipboard", p -> {
      List<JsonObject> l = notifications("client/clipboard");
      return l.isEmpty() ? "" : l.get(l.size() - 1).get("text").getAsString();
    });
    for (String m : new String[] { "client/showMessage", "client/showLongMessage", "client/inputBox", "client/openView",
        "client/openEditor", "client/selectNode", "client/readClipboard", "client/directoryDialog",
        "client/fileDialog", "client/activeTextEditor", "client/replaceText", "client/dirtyDocuments" }) {
      client.onRequest(m, p -> {
        clientRequests.add(Map.entry(m, p));
        Function<JsonObject, Object> a = answers.get(m);
        return a == null ? null : a.apply(p);
      });
    }
    for (String n : new String[] { "workspace/projectsChanged", "explorer/refresh", "progress/begin", "progress/report",
        "progress/end", "editor/closed", "editor/selectionChanged", "settings/update", "ui/openShell", "ui/closeShell",
        "ui/update", "client/clipboard", "client/setContext", "client/showView", "client/openExternal", "client/showHelp",
        "problems/set", "command/changed", "explorer/reveal", "explorer/frames", "client/openFile", "client/updateSetting",
        "client/showIn", "client/openDiff", "client/openContent", "client/viewDescription", "client/windowTitle",
        "client/resetViewLocations", "client/hideView", "client/viewLayout", "explorer/selection" }) {
      client.onNotification(n, p -> {
        // The progress dialogs (ProgressMonitorDialog, "Progress Information") are kept apart: tests wait for their own
        // shells, not for the dialogs of the operations they run.
        if (n.equals("ui/openShell") && isProgressDialog(p)) {
          progressShells.add(p);
          progressShellIds.add(p.get("shell").getAsString());
          return;
        }
        if (n.equals("ui/closeShell") && progressShellIds.contains(p.get("shell").getAsString()))
          return;
        notifications.add(Map.entry(n, p));
        if (n.equals("ui/openShell") && p.has("title") && autoAnswer.contains(p.get("title").getAsString()))
          answerer.execute(() -> pressDefaultButton(p.get("shell").getAsString()));
      });
    }
    pool.execute(() -> {
      try {
        server.rpc().listen();
      } catch (IOException e) {
        // closed
      }
    });
    pool.execute(() -> {
      try {
        client.listen();
      } catch (IOException e) {
        // closed
      }
    });
  }

  /**
   * An in-memory pipe between the two connections. (A java.io piped stream breaks when the thread that last wrote to
   * it ends, e.g. a server job thread; the stdio of the real server has no such tie to threads.)
   */
  static final class Pipe {
    private final java.util.concurrent.LinkedBlockingQueue<byte[]> queue = new java.util.concurrent.LinkedBlockingQueue<>();
    private static final byte[] EOF = new byte[0];
    private byte[] current;
    private int pos;

    final java.io.OutputStream out = new java.io.OutputStream() {
      @Override
      public void write(int b) {
        queue.add(new byte[] { (byte) b });
      }

      @Override
      public void write(byte[] b, int off, int len) {
        if (len > 0)
          queue.add(java.util.Arrays.copyOfRange(b, off, off + len));
      }

      @Override
      public void close() {
        queue.add(EOF);
      }
    };

    final java.io.InputStream in = new java.io.InputStream() {
      @Override
      public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
      }

      @Override
      public synchronized int read(byte[] b, int off, int len) throws IOException {
        if (len == 0)
          return 0;
        while (current == null || pos >= current.length) {
          if (current == EOF)
            return -1;
          try {
            current = queue.take();
          } catch (InterruptedException e) {
            throw new java.io.InterruptedIOException();
          }
          pos = 0;
          if (current == EOF)
            return -1;
        }
        int n = Math.min(len, current.length - pos);
        System.arraycopy(current, pos, b, off, n);
        pos += n;
        return n;
      }
    };
  }

  /** Sets how the simulated user answers a client request. */
  public void answer(String method, Function<JsonObject, Object> f) {
    answers.put(method, f);
  }

  public JsonElement call(String method, Object params) throws Exception {
    return client.request(method, params == null ? new JsonObject() : params).get(120, TimeUnit.SECONDS);
  }

  /**
   * Titles of dialogs the harness answers like a user pressing their default button (for example the question of
   * the core about converting a catalog of an older format, asked while the project loads).
   */
  public final java.util.Set<String> autoAnswer = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private final ExecutorService answerer = Executors.newSingleThreadExecutor();

  /**
   * Presses the default button of a shell (else its first button), as a click in the webview would, once the button is
   * enabled (see {@link #awaitEnabled}).
   */
  public void pressDefaultButton(String shell) {
    try {
      JsonObject snap = call("ui/snapshot", "shell", shell).getAsJsonObject();
      String button = null, first = null;
      for (JsonElement e : snap.getAsJsonArray("ops")) {
        JsonObject o = e.getAsJsonObject();
        JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
        if (props.has("defaultButton") && !props.get("defaultButton").isJsonNull())
          button = props.get("defaultButton").getAsString();
        if (first == null && "button".equals(o.has("type") ? o.get("type").getAsString() : null))
          first = o.get("id").getAsString();
      }
      String id = button != null ? button : first;
      awaitEnabled(shell, id);
      JsonObject ev = new JsonObject();
      ev.addProperty("id", id);
      ev.addProperty("type", "selection");
      ev.add("data", new JsonObject());
      notify("ui/event", ev);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Waits until a widget of the shell is enabled: a running operation of a wizard (WizardDialog.run) disables the
   * buttons until it ends, as in Eclipse, and a disabled button ignores a press. A widget that stays disabled for 30
   * seconds is left as it is (the press of the test is then ignored, as the press of a user).
   */
  public void awaitEnabled(String shell, String id) throws Exception {
    long end = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < end) {
      boolean enabled = false;
      for (JsonElement e : call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
        JsonObject o = e.getAsJsonObject();
        if (o.has("id") && o.get("id").getAsString().equals(id))
          enabled = !(o.has("props") && o.getAsJsonObject("props").has("enabled")
              && !o.getAsJsonObject("props").get("enabled").getAsBoolean());
      }
      if (enabled)
        return;
      Thread.sleep(50);
    }
  }

  /** Sends a request without waiting (for calls that block until a dialog closes). */
  public java.util.concurrent.CompletableFuture<JsonElement> callAsync(String method, Object params) {
    return client.request(method, params == null ? new JsonObject() : params);
  }

  /** Waits for the next ui/openShell after {@code before} notifications and returns it. */
  /** The first editor/dialog shell (a webview panel, not a view) opened after {@code before} openShell notifications. */
  public JsonObject awaitPanelShell(int before) throws Exception {
    JsonObject[] found = new JsonObject[1];
    await("a panel shell to open", v -> {
      List<JsonObject> l = notifications("ui/openShell");
      for (int k = before; k < l.size(); k++)
        if (!"view".equals(l.get(k).has("host") && !l.get(k).get("host").isJsonNull() ? l.get(k).get("host").getAsString() : null)) {
          found[0] = l.get(k);
          return true;
        }
      return false;
    });
    return found[0];
  }

  public JsonObject awaitShell(int before) throws Exception {
    await("a shell to open", v -> notifications("ui/openShell").size() > before);
    return notifications("ui/openShell").get(before);
  }

  /** Closes a shell like the user closing its tab. */
  public void closeShell(String shell) {
    JsonObject p = new JsonObject();
    p.addProperty("id", shell);
    p.addProperty("type", "close");
    p.add("data", new JsonObject());
    notify("ui/event", p);
  }

  public JsonElement call(String method, String k1, Object v1) throws Exception {
    JsonObject p = new JsonObject();
    p.add(k1, JsonRpcConnection.toJson(v1));
    return call(method, p);
  }

  public JsonElement call(String method, String k1, Object v1, String k2, Object v2) throws Exception {
    JsonObject p = new JsonObject();
    p.add(k1, JsonRpcConnection.toJson(v1));
    p.add(k2, JsonRpcConnection.toJson(v2));
    return call(method, p);
  }

  /** The server running in this JVM (for in-process checks such as closing and loading a project tree). */
  public RequalityServer server() {
    return server;
  }

  public void notify(String method, Object params) {
    client.notify(method, params);
  }

  /** The title of the shells of {@code ProgressMonitorDialog} ({@code ProgressMonitorDialog.title}). */
  public static final String PROGRESS_TITLE = "Progress Information";

  static boolean isProgressDialog(JsonObject openShell) {
    return openShell.has("title") && !openShell.get("title").isJsonNull()
        && PROGRESS_TITLE.equals(openShell.get("title").getAsString());
  }

  /** The ui/openShell notifications of the progress dialogs opened so far, in arrival order. */
  public List<JsonObject> progressShells() {
    return new ArrayList<>(progressShells);
  }

  /** Notifications of one method, in arrival order. */
  public List<JsonObject> notifications(String method) {
    List<JsonObject> r = new ArrayList<>();
    for (Map.Entry<String, JsonObject> e : notifications)
      if (e.getKey().equals(method))
        r.add(e.getValue());
    return r;
  }

  public JsonObject initialize(File... folders) throws Exception {
    return initializeWith(null, folders);
  }

  /** Initializes the server with the Requality Explorer shown as {@code explorerView} ("eclipse" or "native"). */
  public JsonObject initializeWith(String explorerView, File... folders) throws Exception {
    JsonObject p = new JsonObject();
    if (explorerView != null)
      p.addProperty("explorerView", explorerView);
    JsonArray f = new JsonArray();
    for (File x : folders)
      f.add(x.getAbsolutePath());
    p.add("workspaceFolders", f);
    p.add("settings", new JsonObject());
    return call("initialize", p).getAsJsonObject();
  }

  /** Waits until every discovered project is ready (or broken) and returns the project list. */
  public JsonArray awaitProjects(int expected) throws Exception {
    long end = System.currentTimeMillis() + 180_000;
    JsonArray ps = null;
    while (System.currentTimeMillis() < end) {
      ps = call("workspace/projects", null).getAsJsonObject().getAsJsonArray("projects");
      boolean done = ps.size() == expected;
      for (JsonElement e : ps) {
        String st = e.getAsJsonObject().get("state").getAsString();
        if (!st.equals("ready") && !st.equals("broken"))
          done = false;
      }
      if (done)
        return ps;
      Thread.sleep(200);
    }
    throw new AssertionError("Projects not ready: " + ps);
  }

  public void await(String what, Predicate<Void> condition) throws Exception {
    await(what, 60_000, condition);
  }

  /** Waits until the condition holds, at most the given time. */
  public void await(String what, long timeoutMs, Predicate<Void> condition) throws Exception {
    long end = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < end) {
      if (condition.test(null))
        return;
      Thread.sleep(100);
    }
    throw timedOut(what);
  }

  /** Waits until the probe gives a value (exceptions count as no value yet) and returns it. */
  public <T> T await2(String what, java.util.concurrent.Callable<T> probe) throws Exception {
    long end = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < end) {
      try {
        T v = probe.call();
        if (v != null)
          return v;
      } catch (Exception e) {
        // not yet
      }
      Thread.sleep(100);
    }
    throw timedOut(what);
  }

  /** The error of a wait that timed out; the stacks of the threads go to the error output (in the test report). */
  static AssertionError timedOut(String what) {
    StringBuilder b = new StringBuilder("--- threads at the timeout of the wait for ").append(what).append('\n');
    for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
      b.append('"').append(e.getKey().getName()).append("\" ").append(e.getKey().getState()).append('\n');
      for (StackTraceElement f : e.getValue())
        b.append("    at ").append(f).append('\n');
    }
    System.err.print(b);
    return new AssertionError("Timed out waiting for " + what);
  }

  public List<JsonObject> requests(String method) {
    List<JsonObject> r = new ArrayList<>();
    for (Map.Entry<String, JsonObject> e : clientRequests)
      if (e.getKey().equals(method))
        r.add(e.getValue());
    return Collections.unmodifiableList(r);
  }

  public JsonArray children(String id) throws Exception {
    return call("explorer/children", "id", id).getAsJsonArray();
  }

  /**
   * A text as the JSON text of a snapshot contains it (the tests search snapshots as JSON text, where a backslash of a
   * Windows path is written twice).
   */
  public static String jsonText(String s) {
    String quoted = new com.google.gson.Gson().toJson(s);
    return quoted.substring(1, quoted.length() - 1);
  }

  public static JsonObject find(JsonArray items, String key, String value) {
    for (JsonElement e : items)
      if (value.equals(e.getAsJsonObject().get(key).getAsString()))
        return e.getAsJsonObject();
    throw new AssertionError("No item with " + key + "=" + value + " in " + items);
  }

  @Override
  public void close() throws Exception {
    try {
      call("shutdown", null);
    } catch (Exception e) {
      // already down
    }
    pool.shutdownNow();
    answerer.shutdownNow();
    TestCatalogs.deleteTree(root.toPath());
  }
}
