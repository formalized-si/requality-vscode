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
    /** A read-only tool that changes state of the server with these arguments: it runs alone (null: never). */
    java.util.function.Predicate<JsonObject> alone;
    /** The same call again changes nothing more (a read-only tool is). */
    boolean idempotent;

    /** The same call again changes nothing more. */
    public Tool idempotent() {
      idempotent = true;
      return this;
    }

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

    /** The tool runs alone (as a tool that changes something) when the arguments match. */
    public Tool alone(java.util.function.Predicate<JsonObject> when) {
      alone = when;
      return this;
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
      a.addProperty("idempotentHint", readOnly || idempotent);
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
    // the jobs of the workbench (and the work of its display) run only while no tool runs: a tool does not read a node
    // that a job loads or changes at the same time
    com.unitesk.requality.vscode.ui.runtime.Background.lock(toolsLock.writeLock());
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
  /** The server stops: no tool call starts. */
  private volatile boolean stopping;
  /** The protocol version and the capabilities of the client (initialize). */
  private volatile String protocol;
  private volatile JsonObject clientCapabilities = new JsonObject();
  /** The requests this server sent to the client, by id, waiting for their responses. */
  private final Map<String, java.util.concurrent.CompletableFuture<JsonObject>> pending = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicInteger requestIds = new java.util.concurrent.atomic.AtomicInteger();

  /** True when the client can ask its user (elicitation, protocol 2025-06-18 and later). */
  public boolean canAskUser() {
    String p = protocol;
    return p != null && p.compareTo("2025-06-18") >= 0 && clientCapabilities.has("elicitation");
  }

  /**
   * Asks the user of the client a yes-or-no question (elicitation/create); true when the user accepted and said yes.
   * False when the client cannot ask, the user declined or cancelled, or no answer came in {@code timeoutMs}.
   */
  public boolean askUser(String message, String yes, long timeoutMs) {
    if (!canAskUser())
      return false;
    JsonObject params = new JsonObject();
    params.addProperty("message", message);
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();
    JsonObject allow = new JsonObject();
    allow.addProperty("type", "boolean");
    allow.addProperty("title", yes);
    allow.addProperty("default", false);
    props.add("allow", allow);
    schema.add("properties", props);
    JsonArray req = new JsonArray();
    req.add("allow");
    schema.add("required", req);
    params.add("requestedSchema", schema);
    JsonObject r = request("elicitation/create", params, timeoutMs);
    if (r == null || !r.has("result") || !r.get("result").isJsonObject())
      return false;
    JsonObject res = r.getAsJsonObject("result");
    if (!res.has("action") || !"accept".equals(res.get("action").getAsString()))
      return false;
    JsonObject content = res.has("content") && res.get("content").isJsonObject() ? res.getAsJsonObject("content") : null;
    return content != null && content.has("allow") && content.get("allow").isJsonPrimitive()
        && content.get("allow").getAsJsonPrimitive().isBoolean() && content.get("allow").getAsBoolean();
  }

  /** Sends a request to the client and waits for its response (null: none in time). */
  private JsonObject request(String method, JsonObject params, long timeoutMs) {
    String id = "requality-" + requestIds.incrementAndGet();
    java.util.concurrent.CompletableFuture<JsonObject> f = new java.util.concurrent.CompletableFuture<>();
    pending.put(id, f);
    JsonObject m = new JsonObject();
    m.addProperty("jsonrpc", "2.0");
    m.addProperty("id", id);
    m.addProperty("method", method);
    m.add("params", params);
    send(m);
    try {
      return f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
      return null;
    } finally {
      pending.remove(id);
    }
  }

  /** No tool call starts from now on (the running ones end). */
  public void stop() {
    stopping = true;
    unanswered();
  }

  /** The questions to the client that wait for an answer get none (the client ends). */
  private void unanswered() {
    for (java.util.concurrent.CompletableFuture<JsonObject> f : pending.values())
      f.complete(null);
  }

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
    // the input ended: no answer comes
    unanswered();
  }

  private void handleLine(String line) {
    JsonElement msg;
    try {
      msg = JsonParser.parseString(line);
    } catch (RuntimeException e) {
      send(error(null, -32700, "Parse error: " + e.getMessage()));
      return;
    }
    String pv = protocol;
    if (msg.isJsonArray() && msg.getAsJsonArray().size() > 0 && pv != null && pv.compareTo("2025-06-18") >= 0) {
      // the protocol versions from 2025-06-18 have no batches
      send(error(null, -32600, "Invalid request: protocol version " + pv + " has no batches"));
    } else if (msg.isJsonArray() && msg.getAsJsonArray().size() > 0) {
      // a batch (protocol 2025-03-26): one array with the responses, after all requests of the batch ran (the request
      // that ends last sends it)
      JsonArray batch = msg.getAsJsonArray();
      JsonObject[] responses = new JsonObject[batch.size()];
      java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(batch.size());
      for (int i = 0; i < batch.size(); i++) {
        int k = i;
        java.util.function.Consumer<JsonObject> reply = r -> {
          responses[k] = r;
          if (left.decrementAndGet() > 0)
            return;
          JsonArray out = new JsonArray();
          for (JsonObject x : responses)
            if (x != null)
              out.add(x);
          if (out.size() > 0)
            send(out);
        };
        if (batch.get(i).isJsonObject())
          dispatch(batch.get(i).getAsJsonObject(), reply);
        else
          reply.accept(error(null, -32600, "Invalid request: not an object"));
      }
    } else if (msg.isJsonObject()) {
      dispatch(msg.getAsJsonObject(), r -> {
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

  /**
   * Runs a message; {@code done} takes its response (null: none, for a notification or a cancelled request), before the
   * request counts as ended.
   */
  private void dispatch(JsonObject msg, java.util.function.Consumer<JsonObject> done) {
    JsonElement idE = msg.get("id");
    JsonElement id = idE == null || idE.isJsonNull() ? null : idE;
    if (id != null && !(id.isJsonPrimitive() && (id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber()))) {
      done.accept(error(null, -32600, "Invalid request: the id must be a string or a number"));
      return;
    }
    JsonElement m = msg.get("method");
    if (m == null) {
      // a response to a request of this server (elicitation)
      java.util.concurrent.CompletableFuture<JsonObject> f = id == null ? null : pending.get(id.getAsString());
      if (f != null)
        f.complete(msg);
      done.accept(null);
      return;
    }
    if (!isString(m)) {
      done.accept(id != null ? error(id, -32600, "Invalid request: the method must be a string") : null);
      return;
    }
    String method = m.getAsString();
    JsonElement p = msg.get("params");
    if (p != null && !p.isJsonNull() && !p.isJsonObject()) {
      done.accept(id != null ? error(id, -32602, "Invalid params: params must be an object") : null);
      return;
    }
    JsonObject params = p != null && p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
    if (id == null) {
      // notifications: initialized, cancelled (a running core operation is not stopped; its result is not sent)
      if (method.equals("notifications/cancelled") && params.has("requestId"))
        runningIds.computeIfPresent(params.get("requestId").toString(), (k, v) -> true);
      done.accept(null);
      return;
    }
    if (!initialized && !method.equals("initialize") && !method.equals("ping")) {
      done.accept(error(id, -32002, "Server not initialized: send initialize first"));
      return;
    }
    if (stopping && method.equals("tools/call")) {
      done.accept(error(id, -32000, "The server is stopping"));
      return;
    }
    running.incrementAndGet();
    runningIds.put(id.toString(), false);
    workers.execute(() -> {
      try {
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
        }
        // the cancellation and the end of the request do not race: the entry is removed once
        Boolean cancelled = runningIds.remove(id.toString());
        // the response is sent here
        done.accept(Boolean.TRUE.equals(cancelled) ? null : out);
      } finally {
        // a request ends when its response was sent: a server that stops after it sends the response first
        running.decrementAndGet();
      }
    });
  }

  /** Called before a tool call (not under the lock of the tools): a notice for its result, or null. */
  private volatile java.util.function.BiFunction<String, JsonObject, String> beforeCall;

  /** Sets what runs before each tool call: it returns a notice the result gets ("notice"), or null. */
  public void beforeCall(java.util.function.BiFunction<String, JsonObject, String> f) {
    beforeCall = f;
  }

  /** Runs the task while no tool that changes something runs (read-only tools may run at the same time). */
  public <T> T shared(java.util.function.Supplier<T> task) {
    toolsLock.readLock().lock();
    try {
      return com.unitesk.requality.vscode.ui.runtime.Background.holding(task);
    } finally {
      toolsLock.readLock().unlock();
    }
  }

  /** Runs the task when no tool runs, and lets no tool start until it ends. */
  public void exclusive(Runnable task) {
    toolsLock.writeLock().lock();
    try {
      com.unitesk.requality.vscode.ui.runtime.Background.holding(() -> {
        task.run();
        return null;
      });
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
        protocol = PROTOCOL_VERSIONS.contains(requested) ? requested : DEFAULT_PROTOCOL_VERSION;
        clientCapabilities = params.has("capabilities") && params.get("capabilities").isJsonObject()
            ? params.getAsJsonObject("capabilities") : new JsonObject();
        r.addProperty("protocolVersion", protocol);
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
    try {
      checkArguments(tool, args);
      // (an argument of the wrong type is an error result, as in the tool)
      java.util.concurrent.locks.Lock lock = tool.readOnly && (tool.alone == null || !tool.alone.test(args))
          ? toolsLock.readLock() : toolsLock.writeLock();
      java.util.function.BiFunction<String, JsonObject, String> before = beforeCall;
      String notice = before == null ? null : before.apply(tool.name, args);
      Object value;
      lock.lock();
      try {
        // the jobs that the tool starts while it runs do not wait for it
        value = com.unitesk.requality.vscode.ui.runtime.Background.holdingCall(() -> tool.handler.call(args));
      } finally {
        lock.unlock();
      }
      JsonElement json = JsonRpcConnection.toJson(value);
      if (notice != null && json.isJsonObject())
        json.getAsJsonObject().addProperty("notice", notice);
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
