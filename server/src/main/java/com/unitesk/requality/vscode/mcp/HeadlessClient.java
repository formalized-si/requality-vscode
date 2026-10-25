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
 * extension. The client answers what the server asks the user as a user who confirms everything (the first item of a
 * message, the default value of an input box), cancels file dialogs and closes the SWT dialogs that open. Every
 * message and answer is kept in a log ({@link #messages}) and printed to standard error.
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

  public HeadlessClient(File resources, File storage) {
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
      JsonArray items = p.getAsJsonArray("items");
      String a = items != null && items.size() > 0 ? items.get(0).getAsString() : null;
      record("message", p, a);
      return a;
    });
    answer("client/showLongMessage", p -> {
      JsonArray b = p.getAsJsonArray("buttons");
      String a = b != null && b.size() > 0 ? b.get(0).getAsString() : null;
      record("message", p, a);
      return a;
    });
    answer("client/inputBox", p -> {
      String a = p.has("value") && !p.get("value").isJsonNull() ? p.get("value").getAsString() : null;
      record("input", p, a);
      return a;
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
      client.request("shutdown", null).get();
    } catch (Exception e) {
      // already stopped
    }
    pool.shutdownNow();
  }
}
