package com.unitesk.requality.vscode.rpc;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * JSON-RPC 2.0 over a byte stream with LSP-style {@code Content-Length} framing, compatible with the
 * {@code vscode-jsonrpc} client used by the extension.
 *
 * <p>Incoming requests run on the worker executor, so a handler may block while it waits for a
 * response to a request it sent to the client (for example a modal dialog).
 */
public final class JsonRpcConnection {
  public interface RequestHandler {
    Object handle(JsonObject params) throws Exception;
  }

  public interface NotificationHandler {
    void handle(JsonObject params) throws Exception;
  }

  public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

  private final InputStream in;
  private final OutputStream out;
  private final ExecutorService workers;
  private final Map<String, RequestHandler> requestHandlers = new ConcurrentHashMap<>();
  private final Map<String, NotificationHandler> notificationHandlers = new ConcurrentHashMap<>();
  private final java.util.Set<String> orderedNotifications = ConcurrentHashMap.newKeySet();
  private final Map<String, CompletableFuture<JsonElement>> pending = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong(1);
  private final Object writeLock = new Object();
  private volatile boolean closed;

  public JsonRpcConnection(InputStream in, OutputStream out, ExecutorService workers) {
    this.in = in;
    this.out = out;
    this.workers = workers;
  }

  public void onRequest(String method, RequestHandler handler) {
    if (requestHandlers.putIfAbsent(method, handler) != null)
      throw new IllegalStateException("Duplicate request handler for " + method);
  }

  public void onNotification(String method, NotificationHandler handler) {
    if (notificationHandlers.putIfAbsent(method, handler) != null)
      throw new IllegalStateException("Duplicate notification handler for " + method);
  }

  /**
   * Registers a notification handler that runs on the reader thread, so notifications keep their order. The handler
   * must not block (it should only enqueue work).
   */
  public void onNotificationOrdered(String method, NotificationHandler handler) {
    onNotification(method, handler);
    orderedNotifications.add(method);
  }

  public boolean hasHandler(String method) {
    return requestHandlers.containsKey(method) || notificationHandlers.containsKey(method);
  }

  /** Sends a request to the client. */
  public CompletableFuture<JsonElement> request(String method, Object params) {
    String id = "s" + nextId.getAndIncrement();
    CompletableFuture<JsonElement> future = new CompletableFuture<>();
    if (closed) {
      future.completeExceptionally(new IOException("Connection closed"));
      return future;
    }
    pending.put(id, future);
    JsonObject msg = new JsonObject();
    msg.addProperty("jsonrpc", "2.0");
    msg.addProperty("id", id);
    msg.addProperty("method", method);
    msg.add("params", toJson(params));
    try {
      write(msg);
    } catch (IOException e) {
      pending.remove(id);
      future.completeExceptionally(e);
    }
    return future;
  }

  /** Sends a notification to the client. Failures are reported on stderr only. */
  public void notify(String method, Object params) {
    if (closed)
      return;
    JsonObject msg = new JsonObject();
    msg.addProperty("jsonrpc", "2.0");
    msg.addProperty("method", method);
    msg.add("params", toJson(params));
    try {
      write(msg);
    } catch (IOException e) {
      System.err.println("[rpc] cannot send notification " + method + ": " + e);
    }
  }

  /** Reads messages until the stream ends. */
  public void listen() throws IOException {
    try {
      while (true) {
        String body = readMessage();
        if (body == null)
          break;
        JsonObject msg;
        try {
          msg = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
          System.err.println("[rpc] malformed message: " + e);
          continue;
        }
        dispatch(msg);
      }
    } finally {
      closed = true;
      for (CompletableFuture<JsonElement> f : pending.values())
        f.completeExceptionally(new IOException("Connection closed"));
      pending.clear();
    }
  }

  public boolean isClosed() {
    return closed;
  }

  private void dispatch(JsonObject msg) {
    boolean hasId = msg.has("id") && !msg.get("id").isJsonNull();
    if (msg.has("method")) {
      String method = msg.get("method").getAsString();
      JsonObject params = msg.has("params") && msg.get("params").isJsonObject() ? msg.getAsJsonObject("params") : new JsonObject();
      if (hasId) {
        JsonElement id = msg.get("id");
        RequestHandler handler = requestHandlers.get(method);
        if (handler == null) {
          sendError(id, -32601, "Method not found: " + method, null);
          return;
        }
        workers.execute(() -> {
          try {
            Object result = handler.handle(params);
            sendResult(id, result);
          } catch (RpcException e) {
            sendError(id, e.getCode(), e.getMessage(), e.getData());
          } catch (Throwable t) {
            System.err.println("[rpc] request " + method + " failed:");
            t.printStackTrace(System.err);
            sendError(id, -32603, t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : ""), trace(t));
          }
        });
      } else {
        NotificationHandler handler = notificationHandlers.get(method);
        if (handler == null)
          return; // includes $/cancelRequest and $/setTrace
        if (orderedNotifications.contains(method)) {
          try {
            handler.handle(params);
          } catch (Throwable t) {
            System.err.println("[rpc] notification " + method + " failed:");
            t.printStackTrace(System.err);
          }
          return;
        }
        workers.execute(() -> {
          try {
            handler.handle(params);
          } catch (Throwable t) {
            System.err.println("[rpc] notification " + method + " failed:");
            t.printStackTrace(System.err);
          }
        });
      }
    } else if (hasId) {
      String id = msg.get("id").getAsString();
      CompletableFuture<JsonElement> future = pending.remove(id);
      if (future == null)
        return;
      if (msg.has("error") && !msg.get("error").isJsonNull()) {
        JsonObject err = msg.getAsJsonObject("error");
        future.completeExceptionally(new RpcException(err.has("code") ? err.get("code").getAsInt() : -32603,
            err.has("message") ? err.get("message").getAsString() : "error", err.get("data")));
      } else {
        future.complete(msg.has("result") ? msg.get("result") : JsonNull.INSTANCE);
      }
    }
  }

  private void sendResult(JsonElement id, Object result) {
    JsonObject msg = new JsonObject();
    msg.addProperty("jsonrpc", "2.0");
    msg.add("id", id);
    msg.add("result", toJson(result));
    try {
      write(msg);
    } catch (IOException e) {
      System.err.println("[rpc] cannot send result: " + e);
    }
  }

  private void sendError(JsonElement id, int code, String message, Object data) {
    JsonObject msg = new JsonObject();
    msg.addProperty("jsonrpc", "2.0");
    msg.add("id", id);
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message == null ? "error" : message);
    if (data != null)
      err.add("data", toJson(data));
    msg.add("error", err);
    try {
      write(msg);
    } catch (IOException e) {
      System.err.println("[rpc] cannot send error: " + e);
    }
  }

  public static JsonElement toJson(Object o) {
    if (o == null)
      return JsonNull.INSTANCE;
    if (o instanceof JsonElement)
      return (JsonElement) o;
    return GSON.toJsonTree(o);
  }

  private static String trace(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  private void write(JsonObject msg) throws IOException {
    byte[] body = GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);
    byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    synchronized (writeLock) {
      out.write(header);
      out.write(body);
      out.flush();
    }
  }

  private String readMessage() throws IOException {
    int contentLength = -1;
    while (true) {
      String line = readHeaderLine();
      if (line == null)
        return null;
      if (line.isEmpty())
        break;
      int colon = line.indexOf(':');
      if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length"))
        contentLength = Integer.parseInt(line.substring(colon + 1).trim());
    }
    if (contentLength < 0)
      throw new IOException("Missing Content-Length header");
    byte[] body = in.readNBytes(contentLength);
    if (body.length != contentLength)
      throw new EOFException("Truncated message");
    return new String(body, StandardCharsets.UTF_8);
  }

  private String readHeaderLine() throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream(64);
    while (true) {
      int b = in.read();
      if (b < 0)
        return buf.size() == 0 ? null : buf.toString(StandardCharsets.US_ASCII);
      if (b == '\n') {
        String s = buf.toString(StandardCharsets.US_ASCII);
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
      }
      buf.write(b);
    }
  }
}
