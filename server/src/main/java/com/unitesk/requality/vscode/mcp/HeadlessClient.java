package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.Main;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.rpc.MemoryPipe;

/**
 * The Requality server without VS Code: the server runs in this JVM and an in-process client takes the place of the
 * extension. The client confirms a message with one button (OK), cancels every question with a choice, input box and
 * file dialog, and closes the SWT dialogs that open. The one question it may confirm is the conversion of a catalog in
 * an old format, for the catalogs it is allowed for (--convert, or convert_project after a backup). Every message and
 * answer is kept in a log ({@link #messages}) and printed to standard error.
 */
public final class HeadlessClient implements AutoCloseable {
  /** Title of the progress dialogs (ProgressMonitorDialog): they close by themselves. */
  private static final String PROGRESS_TITLE = "Progress Information";
  private static final int LOG_SIZE = 200;

  private final RequalityServer server;
  private final JsonRpcConnection client;
  private final ExecutorService pool;
  private final Deque<JsonObject> log = new ArrayDeque<>();
  private final Set<String> progressShells = ConcurrentHashMap.newKeySet();
  /** Convert every catalog in an old format when it opens (--convert). */
  private final boolean convertAll;
  /** The catalogs (folder names) whose conversion is allowed, and the ones that wait for it. */
  private final Set<String> conversionAllowed = ConcurrentHashMap.newKeySet();
  private final Set<String> needsConversion = ConcurrentHashMap.newKeySet();
  /** The catalogs whose conversion this client confirmed (a conversion closes the project and opens it again). */
  private final Set<String> converted = ConcurrentHashMap.newKeySet();

  public HeadlessClient(File resources, File storage) {
    this(resources, storage, true);
  }

  public HeadlessClient(File resources, File storage, boolean convertAll) {
    this.convertAll = convertAll;
    AtomicInteger threadId = new AtomicInteger();
    pool = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "requality-headless-" + threadId.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
    MemoryPipe up = new MemoryPipe();
    MemoryPipe down = new MemoryPipe();
    Main.ServerOptions options = new Main.ServerOptions();
    options.resources = resources;
    options.storage = storage;
    server = new RequalityServer(new JsonRpcConnection(up.in, down.out, pool), options);
    server.registerServices();
    client = new JsonRpcConnection(down.in, up.out, pool);
    answer("client/showMessage", p -> {
      String a = choose(p, p.getAsJsonArray("items"));
      record("message", p, a == null ? "cancel" : a);
      return a;
    });
    answer("client/showLongMessage", p -> {
      String a = choose(p, p.getAsJsonArray("buttons"));
      record("message", p, a == null ? "cancel" : a);
      return a;
    });
    answer("client/inputBox", p -> {
      record("input", p, "cancel");
      return null;
    });
    for (String m : new String[] { "client/fileDialog", "client/directoryDialog" })
      answer(m, p -> {
        record("dialog", p, null);
        return null;
      });
    answer("client/readClipboard", p -> "");
    answer("client/dirtyDocuments", p -> new JsonArray());
    answer("client/activeTextEditor", p -> null);
    answer("client/replaceText", p -> false);
    for (String m : new String[] { "client/openView", "client/openEditor", "client/selectNode" })
      answer(m, p -> null);
    client.onNotification("ui/openShell", p -> {
      String shell = p.get("shell").getAsString();
      if (p.has("title") && !p.get("title").isJsonNull() && PROGRESS_TITLE.equals(p.get("title").getAsString())) {
        progressShells.add(shell);
        return;
      }
      // A workbench part (a view, an editor) stays open; a dialog would wait for a user: it is closed (Cancel).
      if (!p.has("modal") || !p.get("modal").getAsBoolean())
        return;
      record("dialog", p, "closed");
      JsonObject ev = new JsonObject();
      ev.addProperty("id", shell);
      ev.addProperty("type", "close");
      ev.add("data", new JsonObject());
      client.notify("ui/event", ev);
    });
    client.onNotification("ui/closeShell", p -> progressShells.remove(p.get("shell").getAsString()));
    pool.execute(() -> listen(server.rpc()));
    pool.execute(() -> listen(client));
  }

  /** "Project 'X' format is out of date ...": the question of the conversion of the catalog X. */
  private static final java.util.regex.Pattern CONVERSION = java.util.regex.Pattern.compile("Project '([^']+)' format is out of date");

  /** The answer to a message: its only button; for a question with a choice none (cancel), except an allowed conversion. */
  private String choose(JsonObject p, JsonArray items) {
    if (items == null || items.size() == 0)
      return null;
    if (items.size() == 1)
      return items.get(0).getAsString();
    String text = p.has("message") && !p.get("message").isJsonNull() ? p.get("message").getAsString() : "";
    java.util.regex.Matcher m = CONVERSION.matcher(text);
    if (m.find()) {
      String name = m.group(1);
      if (convertAll || conversionAllowed.contains(name)) {
        needsConversion.remove(name);
        converted.add(name);
        return items.get(0).getAsString();
      }
      needsConversion.add(name);
    }
    return null;
  }

  /** Allows the conversion of the catalog in the folder of this name (after a backup). */
  public void allowConversion(String folderName) {
    conversionAllowed.add(folderName);
  }

  /** True when this client confirmed the conversion of the catalog in the folder of this name. */
  public boolean converted(String folderName) {
    return converted.contains(folderName);
  }

  /** True when the catalog in the folder of this name waits for a conversion that is not allowed. */
  public boolean needsConversion(String folderName) {
    return needsConversion.contains(folderName);
  }

  private static void listen(JsonRpcConnection c) {
    try {
      c.listen();
    } catch (IOException e) {
      // closed
    }
  }

  private void answer(String method, Function<JsonObject, Object> a) {
    client.onRequest(method, a::apply);
  }

  private void record(String kind, JsonObject request, String answer) {
    JsonObject e = new JsonObject();
    e.addProperty("time", Instant.now().toString());
    e.addProperty("kind", kind);
    for (String k : new String[] { "severity", "title", "message", "detail", "prompt" })
      if (request.has(k) && !request.get(k).isJsonNull())
        e.add(k, request.get(k));
    if (answer != null)
      e.addProperty("answer", answer);
    synchronized (log) {
      log.addLast(e);
      while (log.size() > LOG_SIZE)
        log.removeFirst();
    }
    System.err.println("[headless] " + e);
  }

  /** The messages and dialogs of the server so far (oldest first), with the answers the client gave. */
  public List<JsonObject> messages() {
    synchronized (log) {
      return new ArrayList<>(log);
    }
  }

  public RequalityServer server() {
    return server;
  }

  /** Sends a request to the server as the extension does and waits for the result. */
  public JsonElement call(String method, JsonObject params) throws Exception {
    return client.request(method, params).get();
  }

  /** Initializes the server with the folders that contain the Requality projects. */
  public JsonObject initialize(List<File> folders) throws Exception {
    JsonObject p = new JsonObject();
    JsonArray f = new JsonArray();
    for (File x : folders)
      f.add(x.getAbsolutePath());
    p.add("workspaceFolders", f);
    p.add("settings", new JsonObject());
    return call("initialize", p).getAsJsonObject();
  }

  @Override
  public void close() {
    try {
      // the core closes the catalogs and releases their locks; a stuck shutdown does not keep the process
      client.request("shutdown", null).get(30, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Exception e) {
      // already stopped, or too slow
    }
    pool.shutdownNow();
  }
}
