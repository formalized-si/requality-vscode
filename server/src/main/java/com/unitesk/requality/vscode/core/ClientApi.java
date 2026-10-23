package com.unitesk.requality.vscode.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.rpc.RpcException;

/**
 * Calls into the extension host. Blocking calls wait for the user's answer; they must not be made from the RPC
 * reader thread (request handlers run on worker threads, so they may).
 */
public final class ClientApi {
  public enum Severity {
    error, warning, info
  }

  private final JsonRpcConnection rpc;
  private final Map<String, Function<String, String>> inputValidators = new ConcurrentHashMap<>();

  public ClientApi(JsonRpcConnection rpc) {
    this.rpc = rpc;
    rpc.onRequest("dialog/validateInput", p -> {
      Function<String, String> v = inputValidators.get(p.get("session").getAsString());
      return v == null ? null : v.apply(p.get("text").getAsString());
    });
  }

  public JsonRpcConnection rpc() {
    return rpc;
  }

  /** Sends a request and waits for the result. */
  public JsonElement call(String method, Object params) {
    try {
      return rpc.request(method, params).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RpcException(RpcException.CANCELLED, "Interrupted while waiting for " + method);
    } catch (ExecutionException e) {
      Throwable c = e.getCause();
      if (c instanceof RpcException)
        throw (RpcException) c;
      throw new RpcException(RpcException.INTERNAL_ERROR, method + " failed: " + c);
    }
  }

  public void notify(String method, Object params) {
    rpc.notify(method, params);
  }

  /**
   * Sets a VS Code context key (when-clause), e.g. the state of a view toolbar toggle that Eclipse kept in a
   * ToolItem; {@code value} is a boolean, number or string.
   */
  public void setContext(String key, Object value) {
    JsonObject p = new JsonObject();
    p.addProperty("key", key);
    if (value instanceof Boolean)
      p.addProperty("value", (Boolean) value);
    else if (value instanceof Number)
      p.addProperty("value", (Number) value);
    else
      p.addProperty("value", value == null ? null : value.toString());
    rpc.notify("client/setContext", p);
  }

  /**
   * Shows a message. With {@code wait} the call blocks and returns the chosen item (or null when dismissed);
   * otherwise it returns null immediately.
   */
  public String showMessage(Severity severity, String title, String message, String detail, boolean modal, boolean wait,
      String... items) {
    JsonObject p = new JsonObject();
    p.addProperty("severity", severity.name());
    p.addProperty("title", title);
    p.addProperty("message", message);
    p.addProperty("detail", detail);
    p.addProperty("modal", modal);
    p.add("items", JsonRpcConnection.toJson(items));
    if (!wait) {
      rpc.request("client/showMessage", p);
      return null;
    }
    JsonElement r = call("client/showMessage", p);
    return r == null || r.isJsonNull() ? null : r.getAsString();
  }

  /** Shows a long, scrollable text (upstream BigMessageDialog). Returns the chosen button. */
  public String showLongMessage(String title, String message, boolean wait, String... buttons) {
    JsonObject p = new JsonObject();
    p.addProperty("title", title);
    p.addProperty("message", message);
    p.add("buttons", JsonRpcConnection.toJson(buttons));
    if (!wait) {
      rpc.request("client/showLongMessage", p);
      return null;
    }
    JsonElement r = call("client/showLongMessage", p);
    return r == null || r.isJsonNull() ? null : r.getAsString();
  }

  /**
   * Shows an input box. The validator (may be null) runs on the server for every change, exactly like an SWT
   * IInputValidator. Returns null when cancelled.
   */
  public String inputBox(String title, String prompt, String value, Function<String, String> validator) {
    String session = UUID.randomUUID().toString();
    if (validator != null)
      inputValidators.put(session, validator);
    try {
      JsonObject p = new JsonObject();
      p.addProperty("session", session);
      p.addProperty("title", title);
      p.addProperty("prompt", prompt);
      p.addProperty("value", value);
      p.addProperty("validate", validator != null);
      JsonElement r = call("client/inputBox", p);
      return r == null || r.isJsonNull() ? null : r.getAsString();
    } finally {
      inputValidators.remove(session);
    }
  }
}
