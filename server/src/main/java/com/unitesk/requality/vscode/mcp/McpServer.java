package com.unitesk.requality.vscode.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;

/**
 * Model Context Protocol server over a byte stream with the stdio framing of MCP: one JSON-RPC 2.0 message per line.
 * It answers {@code initialize}, {@code ping}, {@code tools/list} and {@code tools/call}; requests run on the workers,
 * so a long tool call does not block a ping.
 */
public final class McpServer {
  /** Protocol versions this server can speak; the last one is its own. */
  static final List<String> PROTOCOL_VERSIONS = Arrays.asList("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25");
  /** The answer to a version the server does not know: the newest one it speaks. */
  static final String DEFAULT_PROTOCOL_VERSION = PROTOCOL_VERSIONS.get(PROTOCOL_VERSIONS.size() - 1);

  /** A failure of a tool: the result of the call has {@code isError} and this text (not a JSON-RPC error). */
  public static final class ToolError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ToolError(String message) {
      super(message);
    }
  }

  /** Invalid arguments of a tool call (JSON-RPC error -32602). */
  public static final class InvalidParams extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidParams(String message) {
      super(message);
    }
  }

  public interface Handler {
    Object call(JsonObject arguments) throws Exception;
  }

  /** A prompt: a ready-made request for a job of the user (a slash command in Claude Code). */
  public static final class Prompt {
    final String name;
    final String title;
    final String description;
    /** name, description and required (true/false) of each argument. */
    final String[][] arguments;
    final java.util.function.Function<JsonObject, String> text;

    public Prompt(String name, String title, String description, String[][] arguments, java.util.function.Function<JsonObject, String> text) {
      this.name = name;
      this.title = title;
      this.description = description;
      this.arguments = arguments;
      this.text = text;
    }

    JsonObject describe() {
      JsonObject p = new JsonObject();
      p.addProperty("name", name);
      p.addProperty("title", title);
      p.addProperty("description", description);
      JsonArray args = new JsonArray();
      for (String[] a : arguments) {
        JsonObject o = new JsonObject();
        o.addProperty("name", a[0]);
        o.addProperty("description", a[1]);
        o.addProperty("required", Boolean.parseBoolean(a[2]));
        args.add(o);
      }
      p.add("arguments", args);
      return p;
    }
  }

  /** A tool: its name, description, JSON schema of the arguments, hints and handler. */
  public static final class Tool {
    final String name;
    final String title;
    final String description;
    final JsonObject inputSchema;
    final boolean readOnly;
    final boolean destructive;
    final Handler handler;

    public Tool(String name, String title, String description, JsonObject inputSchema, boolean readOnly, boolean destructive,
        Handler handler) {
      this.name = name;
      this.title = title;
      this.description = description;
      this.inputSchema = inputSchema;
      this.readOnly = readOnly;
      this.destructive = destructive;
      this.handler = handler;
    }

    JsonObject describe() {
      JsonObject t = new JsonObject();
      t.addProperty("name", name);
      t.addProperty("title", title);
      t.addProperty("description", description);
      t.add("inputSchema", inputSchema);
      JsonObject a = new JsonObject();
      a.addProperty("title", title);
      a.addProperty("readOnlyHint", readOnly);
      a.addProperty("destructiveHint", destructive);
      a.addProperty("openWorldHint", false);
      t.add("annotations", a);
      return t;
    }
  }

  private final InputStream in;
  private final OutputStream out;
  private final ExecutorService workers;
  private final String version;
  private final String instructions;
  private final Map<String, Tool> tools = new LinkedHashMap<>();
  private final Map<String, Prompt> prompts = new LinkedHashMap<>();

  public McpServer(InputStream in, OutputStream out, ExecutorService workers, String version, String instructions) {
    this.in = in;
    this.out = out;
    this.workers = workers;
    this.version = version;
    this.instructions = instructions;
  }

  public void add(Tool tool) {
    tools.put(tool.name, tool);
  }

  public void add(Prompt prompt) {
    prompts.put(prompt.name, prompt);
  }

  /** The requests that run now, by id: true when the client cancelled one (its result is not sent). */
  private final Map<String, Boolean> runningIds = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
  /**
   * Read-only tools run side by side; a tool that changes something runs alone. A change runs in a transaction of the
   * core that the other threads would see before its commit.
   */
  private final java.util.concurrent.locks.ReadWriteLock toolsLock = new java.util.concurrent.locks.ReentrantReadWriteLock(true);
  /** The client sent initialize: before it, the server answers only initialize and ping. */
  private volatile boolean initialized;

  /** Reads messages until the input ends. A malformed message gets an error and the server continues. */
  public void listen() throws IOException {
    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    while ((line = r.readLine()) != null) {
      if (line.isBlank())
        continue;
      try {
        handleLine(line);
      } catch (Throwable t) {
        // no message may stop the server
        t.printStackTrace(System.err);
      }
    }
  }

  private void handleLine(String line) {
    JsonElement msg;
    try {
      msg = JsonParser.parseString(line);
    } catch (RuntimeException e) {
      send(error(null, -32700, "Parse error: " + e.getMessage()));
      return;
    }
    if (msg.isJsonArray() && msg.getAsJsonArray().size() > 0) {
      // a batch (protocol 2025-03-26): one array with the responses, after all requests of the batch ran
      List<java.util.concurrent.CompletableFuture<JsonObject>> results = new java.util.ArrayList<>();
      for (JsonElement m : msg.getAsJsonArray())
        results.add(m.isJsonObject() ? dispatch(m.getAsJsonObject())
            : java.util.concurrent.CompletableFuture.completedFuture(error(null, -32600, "Invalid request: not an object")));
      java.util.concurrent.CompletableFuture.allOf(results.toArray(new java.util.concurrent.CompletableFuture[0])).thenRun(() -> {
        JsonArray out = new JsonArray();
        for (java.util.concurrent.CompletableFuture<JsonObject> f : results) {
          JsonObject r = f.join();
          if (r != null)
            out.add(r);
        }
        if (out.size() > 0)
          send(out);
      });
    } else if (msg.isJsonObject()) {
      dispatch(msg.getAsJsonObject()).thenAccept(r -> {
        if (r != null)
          send(r);
      });
    } else {
      send(error(null, -32600, "Invalid request"));
    }
  }

  private static boolean isString(JsonElement e) {
    return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
  }

  /** Runs a message; the future gives its response (null: none, for a notification or a cancelled request). */
  private java.util.concurrent.CompletableFuture<JsonObject> dispatch(JsonObject msg) {
    java.util.concurrent.CompletableFuture<JsonObject> done = new java.util.concurrent.CompletableFuture<>();
    JsonElement idE = msg.get("id");
    JsonElement id = idE == null || idE.isJsonNull() ? null : idE;
    if (id != null && !(id.isJsonPrimitive() && (id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber()))) {
      done.complete(error(null, -32600, "Invalid request: the id must be a string or a number"));
      return done;
    }
    JsonElement m = msg.get("method");
    if (m == null) {
      done.complete(null); // a response: this server sends no requests
      return done;
    }
    if (!isString(m)) {
      done.complete(id != null ? error(id, -32600, "Invalid request: the method must be a string") : null);
      return done;
    }
    String method = m.getAsString();
    JsonElement p = msg.get("params");
    if (p != null && !p.isJsonNull() && !p.isJsonObject()) {
      done.complete(id != null ? error(id, -32602, "Invalid params: params must be an object") : null);
      return done;
    }
    JsonObject params = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
    if (id == null) {
      // notifications: initialized, cancelled (a running core operation is not stopped; its result is not sent)
      if (method.equals("notifications/cancelled") && params.has("requestId"))
        runningIds.computeIfPresent(params.get("requestId").toString(), (k, v) -> true);
      done.complete(null);
      return done;
    }
    if (!initialized && !method.equals("initialize") && !method.equals("ping")) {
      done.complete(error(id, -32002, "Server not initialized: send initialize first"));
      return done;
    }
    running.incrementAndGet();
    runningIds.put(id.toString(), false);
    workers.execute(() -> {
      JsonObject out;
      try {
        out = result(id, handle(method, params));
      } catch (InvalidParams e) {
        out = error(id, -32602, e.getMessage());
      } catch (UnsupportedOperationException e) {
        out = error(id, -32601, "Method not found: " + method);
      } catch (Throwable t) {
        t.printStackTrace(System.err);
        out = error(id, -32603, t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""));
      } finally {
        running.decrementAndGet();
      }
      // the cancellation and the end of the request do not race: the entry is removed once
      Boolean cancelled = runningIds.remove(id.toString());
      done.complete(Boolean.TRUE.equals(cancelled) ? null : out);
    });
    return done;
  }

  /** Runs the task when no tool runs, and lets no tool start until it ends. */
  public void exclusive(Runnable task) {
    toolsLock.writeLock().lock();
    try {
      task.run();
    } finally {
      toolsLock.writeLock().unlock();
    }
  }

  /** Waits until no request runs, at most the time given; true when none runs. */
  public boolean awaitIdle(long millis) throws InterruptedException {
    long end = System.currentTimeMillis() + millis;
    while (running.get() > 0) {
      if (System.currentTimeMillis() > end)
        return false;
      Thread.sleep(50);
    }
    return true;
  }

  private JsonElement handle(String method, JsonObject params) throws Exception {
    switch (method) {
      case "initialize": {
        if (params.has("protocolVersion") && !isString(params.get("protocolVersion")))
          throw new InvalidParams("Invalid params: protocolVersion must be a string");
        String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : null;
        initialized = true;
        JsonObject r = new JsonObject();
        r.addProperty("protocolVersion", PROTOCOL_VERSIONS.contains(requested) ? requested : DEFAULT_PROTOCOL_VERSION);
        JsonObject caps = new JsonObject();
        JsonObject t = new JsonObject();
        t.addProperty("listChanged", false);
        caps.add("tools", t);
        JsonObject pc = new JsonObject();
        pc.addProperty("listChanged", false);
        caps.add("prompts", pc);
        r.add("capabilities", caps);
        JsonObject info = new JsonObject();
        info.addProperty("name", "requality");
        info.addProperty("title", "Requality");
        info.addProperty("version", version);
        r.add("serverInfo", info);
        r.addProperty("instructions", instructions);
        return r;
      }
      case "ping":
        return new JsonObject();
      case "tools/list": {
        JsonArray list = new JsonArray();
        for (Tool t : tools.values())
          list.add(t.describe());
        JsonObject r = new JsonObject();
        r.add("tools", list);
        return r;
      }
      case "tools/call":
        return callTool(params);
      case "prompts/list": {
        JsonArray list = new JsonArray();
        for (Prompt p : prompts.values())
          list.add(p.describe());
        JsonObject r = new JsonObject();
        r.add("prompts", list);
        return r;
      }
      case "prompts/get": {
        String name = isString(params.get("name")) ? params.get("name").getAsString() : null;
        Prompt p = name == null ? null : prompts.get(name);
        if (p == null)
          throw new InvalidParams("Unknown prompt: " + name);
        if (params.has("arguments") && !params.get("arguments").isJsonNull() && !params.get("arguments").isJsonObject())
          throw new InvalidParams("Invalid params: arguments must be an object");
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments")
            : new JsonObject();
        for (String[] a : p.arguments)
          if (Boolean.parseBoolean(a[2]) && (!isString(args.get(a[0])) || args.get(a[0]).getAsString().isBlank()))
            throw new InvalidParams("Missing argument " + a[0]);
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", p.text.apply(args));
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.add("content", content);
        JsonArray msgs = new JsonArray();
        msgs.add(msg);
        JsonObject r = new JsonObject();
        r.addProperty("description", p.description);
        r.add("messages", msgs);
        return r;
      }
      default:
        throw new UnsupportedOperationException(method);
    }
  }

  private JsonObject callTool(JsonObject params) {
    if (!isString(params.get("name")))
      throw new InvalidParams("Invalid params: name must be the name of a tool");
    String name = params.get("name").getAsString();
    Tool tool = tools.get(name);
    if (tool == null)
      throw new InvalidParams("Unknown tool: " + name);
    JsonElement given = params.get("arguments");
    if (given != null && !given.isJsonNull() && !given.isJsonObject())
      throw new InvalidParams("Invalid params: arguments must be an object");
    JsonObject args = given != null && given.isJsonObject() ? given.getAsJsonObject() : new JsonObject();
    JsonObject r = new JsonObject();
    JsonArray content = new JsonArray();
    JsonObject text = new JsonObject();
    text.addProperty("type", "text");
    java.util.concurrent.locks.Lock lock = tool.readOnly ? toolsLock.readLock() : toolsLock.writeLock();
    try {
      checkArguments(tool, args);
      Object value;
      lock.lock();
      try {
        value = tool.handler.call(args);
      } finally {
        lock.unlock();
      }
      JsonElement json = JsonRpcConnection.toJson(value);
      // compact JSON text (no structuredContent: the tools declare no output schema, a client would pass both)
      text.addProperty("text", json.isJsonPrimitive() ? json.getAsString() : JsonRpcConnection.GSON.toJson(json));
      r.addProperty("isError", false);
    } catch (InvalidParams | ToolError e) {
      // invalid arguments too are a result of the tool: the model reads the message and corrects the call
      text.addProperty("text", e.getMessage());
      r.addProperty("isError", true);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      text.addProperty("text", e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
      r.addProperty("isError", true);
    }
    content.add(text);
    r.add("content", content);
    return r;
  }

  /**
   * The arguments against the schema of the tool: an unknown name or a missing required argument is an error that names
   * the arguments the tool takes (an agent that guessed a name corrects it with the next call).
   */
  static void checkArguments(Tool tool, JsonObject args) {
    JsonObject props = tool.inputSchema.has("properties") ? tool.inputSchema.getAsJsonObject("properties") : new JsonObject();
    List<String> unknown = new java.util.ArrayList<>();
    for (String k : args.keySet())
      if (!props.has(k))
        unknown.add(k);
    String takes = props.size() == 0 ? "no arguments" : "the arguments " + String.join(", ", props.keySet());
    if (!unknown.isEmpty())
      throw new InvalidParams("Unknown argument" + (unknown.size() > 1 ? "s " : " ") + String.join(", ", unknown) + "; "
          + tool.name + " takes " + takes);
    if (tool.inputSchema.has("required"))
      for (JsonElement e : tool.inputSchema.getAsJsonArray("required")) {
        String k = e.getAsString();
        if (!args.has(k) || args.get(k).isJsonNull()) {
          JsonObject p = props.has(k) ? props.getAsJsonObject(k) : null;
          String d = p != null && p.has("description") ? " (" + p.get("description").getAsString() + ")" : "";
          throw new InvalidParams("Missing argument " + k + d + "; " + tool.name + " takes " + takes);
        }
      }
  }

  private static JsonObject result(JsonElement id, JsonElement result) {
    JsonObject m = new JsonObject();
    m.addProperty("jsonrpc", "2.0");
    m.add("id", id);
    m.add("result", result);
    return m;
  }

  private static JsonObject error(JsonElement id, int code, String message) {
    JsonObject m = new JsonObject();
    m.addProperty("jsonrpc", "2.0");
    m.add("id", id);
    JsonObject e = new JsonObject();
    e.addProperty("code", code);
    e.addProperty("message", message);
    m.add("error", e);
    return m;
  }

  private void send(JsonElement msg) {
    byte[] line = (JsonRpcConnection.GSON.toJson(msg) + "\n").getBytes(StandardCharsets.UTF_8);
    synchronized (out) {
      try {
        out.write(line);
        out.flush();
      } catch (IOException e) {
        System.err.println("[mcp] cannot send: " + e);
      }
    }
  }
}
