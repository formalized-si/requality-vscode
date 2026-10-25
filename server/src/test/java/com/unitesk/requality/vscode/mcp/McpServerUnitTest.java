package com.unitesk.requality.vscode.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.mcp.McpServer.Tool;
import com.unitesk.requality.vscode.rpc.MemoryPipe;

/** The protocol parts of the MCP server, with tools of the test: the locks of the tools, the end, the questions. */
class McpServerUnitTest {
  ExecutorService pool;
  MemoryPipe toServer;
  McpServer mcp;
  final BlockingQueue<JsonObject> received = new LinkedBlockingQueue<>();
  final AtomicInteger ids = new AtomicInteger();
  /** The tools that run now, and the most that ran at once. */
  final AtomicInteger now = new AtomicInteger();
  final AtomicInteger most = new AtomicInteger();

  @BeforeEach
  void start() throws Exception {
    pool = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mcp-unit");
      t.setDaemon(true);
      return t;
    });
    toServer = new MemoryPipe();
    MemoryPipe fromServer = new MemoryPipe();
    mcp = new McpServer(toServer.in, fromServer.out, pool, "test", "test");
    McpServer.Handler slow = a -> {
      most.accumulateAndGet(now.incrementAndGet(), Math::max);
      try {
        Thread.sleep(300);
      } finally {
        now.decrementAndGet();
      }
      return new JsonObject();
    };
    mcp.add(new Tool("read", "Read", "A read-only tool.", RequalityTools.Schema.object()
        .prop("alone", "boolean", "Run alone.", false).build(), true, false, slow)
        .alone(a -> a.has("alone") && a.get("alone").getAsBoolean()));
    mcp.add(new Tool("ask", "Ask", "Asks the user.", RequalityTools.Schema.object().build(), true, false,
        a -> mcp.askUser("May I?", "Allow", 10_000)));
    pool.execute(() -> {
      try {
        mcp.listen();
      } catch (Exception e) {
        // closed
      }
    });
    pool.execute(() -> {
      try (BufferedReader r = new BufferedReader(new InputStreamReader(fromServer.in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = r.readLine()) != null)
          received.add(JsonParser.parseString(line).getAsJsonObject());
      } catch (Exception e) {
        // closed
      }
    });
  }

  @AfterEach
  void stop() throws Exception {
    toServer.out.close();
    pool.shutdownNow();
  }

  void send(JsonObject m) throws Exception {
    toServer.out.write((m + "\n").getBytes(StandardCharsets.UTF_8));
  }

  int request(String method, JsonObject params) throws Exception {
    int id = ids.incrementAndGet();
    JsonObject m = new JsonObject();
    m.addProperty("jsonrpc", "2.0");
    m.addProperty("id", id);
    m.addProperty("method", method);
    m.add("params", params);
    send(m);
    return id;
  }

  JsonObject response(int id) throws Exception {
    long end = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < end) {
      JsonObject m = received.poll(100, TimeUnit.MILLISECONDS);
      if (m != null && m.has("id") && m.get("id").isJsonPrimitive() && m.get("id").getAsJsonPrimitive().isNumber()
          && m.get("id").getAsInt() == id)
        return m;
      if (m != null)
        received.add(m);
    }
    throw new AssertionError("no response " + id);
  }

  void initialize(boolean elicitation) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("protocolVersion", "2025-06-18");
    JsonObject caps = new JsonObject();
    if (elicitation)
      caps.add("elicitation", new JsonObject());
    p.add("capabilities", caps);
    response(request("initialize", p));
  }

  int call(String tool, JsonObject args) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("name", tool);
    p.add("arguments", args);
    return request("tools/call", p);
  }

  @Test
  void readToolsRunTogetherUnlessOneRunsAlone() throws Exception {
    initialize(false);
    int a = call("read", new JsonObject());
    int b = call("read", new JsonObject());
    response(a);
    response(b);
    assertEquals(2, most.get(), "two read-only calls ran at once");
    most.set(0);
    JsonObject alone = new JsonObject();
    alone.addProperty("alone", true);
    int c = call("read", alone);
    int d = call("read", new JsonObject());
    response(c);
    response(d);
    assertEquals(1, most.get(), "a call that runs alone ran with another");
  }

  @Test
  void aStoppingServerAnswersItsCallsAndStartsNoNewOne() throws Exception {
    initialize(false);
    int a = call("read", new JsonObject());
    // the response is sent before the call counts as ended
    CompletableFuture<Boolean> idle = CompletableFuture.supplyAsync(() -> {
      try {
        Thread.sleep(50);
        return mcp.awaitIdle(10_000);
      } catch (InterruptedException e) {
        return false;
      }
    });
    assertTrue(idle.get(20, TimeUnit.SECONDS));
    boolean sent = false;
    for (JsonObject m : received)
      sent |= m.has("id") && m.get("id").getAsInt() == a;
    assertTrue(sent, "the response was sent when the server was idle");
    mcp.stop();
    JsonObject refused = response(call("read", new JsonObject()));
    assertEquals(-32000, refused.getAsJsonObject("error").get("code").getAsInt(), refused.toString());
  }

  /** Answers the next question of the server (elicitation/create) with the action and the allow value. */
  JsonObject answer(String action, Boolean allow) throws Exception {
    long end = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < end) {
      JsonObject m = received.poll(100, TimeUnit.MILLISECONDS);
      if (m != null && m.has("method") && m.get("method").getAsString().equals("elicitation/create")) {
        JsonObject r = new JsonObject();
        r.addProperty("jsonrpc", "2.0");
        r.add("id", m.get("id"));
        JsonObject res = new JsonObject();
        res.addProperty("action", action);
        if (allow != null) {
          JsonObject content = new JsonObject();
          content.addProperty("allow", allow);
          res.add("content", content);
        }
        r.add("result", res);
        send(r);
        return m;
      }
      if (m != null)
        received.add(m);
    }
    throw new AssertionError("no question");
  }

  boolean asked(int id) throws Exception {
    JsonObject r = response(id).getAsJsonObject("result");
    return JsonParser.parseString(r.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()).getAsBoolean();
  }

  @Test
  void theUserAnswersAQuestionOfTheServer() throws Exception {
    initialize(false);
    assertFalse(asked(call("ask", new JsonObject())), "a client without elicitation is not asked");
    initialize(true);
    int a = call("ask", new JsonObject());
    JsonObject q = answer("accept", true);
    assertEquals("May I?", q.getAsJsonObject("params").get("message").getAsString());
    assertTrue(q.getAsJsonObject("params").getAsJsonObject("requestedSchema").getAsJsonObject("properties").has("allow"));
    assertTrue(asked(a));
    int b = call("ask", new JsonObject());
    answer("accept", false);
    assertFalse(asked(b));
    int c = call("ask", new JsonObject());
    answer("decline", null);
    assertFalse(asked(c));
  }
}
