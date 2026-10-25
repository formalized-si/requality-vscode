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
  static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

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

  /** Reads messages until the input ends. */
  public void listen() throws IOException {
    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    while ((line = r.readLine()) != null) {
      if (line.isBlank())
        continue;
      JsonElement msg;
      try {
        msg = JsonParser.parseString(line);
      } catch (RuntimeException e) {
        send(error(null, -32700, "Parse error: " + e.getMessage()));
        continue;
      }
      if (msg.isJsonArray()) {
        for (JsonElement m : msg.getAsJsonArray())
          if (m.isJsonObject())
            dispatch(m.getAsJsonObject());
      } else if (msg.isJsonObject()) {
        dispatch(msg.getAsJsonObject());
      } else {
        send(error(null, -32600, "Invalid request"));
      }
    }
  }

  private void dispatch(JsonObject msg) {
    if (!msg.has("method"))
      return; // a response: this server sends no requests
    String method = msg.get("method").getAsString();
    JsonElement id = msg.has("id") && !msg.get("id").isJsonNull() ? msg.get("id") : null;
    JsonObject params = msg.has("params") && msg.get("params").isJsonObject() ? msg.getAsJsonObject("params") : new JsonObject();
    if (id == null)
      return; // notifications/initialized, notifications/cancelled
    workers.execute(() -> {
      try {
        send(result(id, handle(method, params)));
      } catch (InvalidParams e) {
        send(error(id, -32602, e.getMessage()));
      } catch (UnsupportedOperationException e) {
        send(error(id, -32601, "Method not found: " + method));
      } catch (Throwable t) {
        t.printStackTrace(System.err);
        send(error(id, -32603, t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "")));
      }
    });
  }

  private JsonElement handle(String method, JsonObject params) throws Exception {
    switch (method) {
      case "initialize": {
        String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : null;
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
        String name = params.has("name") ? params.get("name").getAsString() : null;
        Prompt p = name == null ? null : prompts.get(name);
        if (p == null)
          throw new InvalidParams("Unknown prompt: " + name);
        JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments")
            : new JsonObject();
        for (String[] a : p.arguments)
          if (Boolean.parseBoolean(a[2]) && (!args.has(a[0]) || args.get(a[0]).getAsString().isBlank()))
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
    String name = params.has("name") ? params.get("name").getAsString() : null;
    Tool tool = name == null ? null : tools.get(name);
    if (tool == null)
      throw new InvalidParams("Unknown tool: " + name);
    JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments")
        : new JsonObject();
    JsonObject r = new JsonObject();
    JsonArray content = new JsonArray();
    JsonObject text = new JsonObject();
    text.addProperty("type", "text");
    try {
      checkArguments(tool, args);
      Object value = tool.handler.call(args);
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

  private void send(JsonObject msg) {
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
