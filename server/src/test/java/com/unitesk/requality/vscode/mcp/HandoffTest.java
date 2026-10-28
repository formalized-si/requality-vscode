package com.unitesk.requality.vscode.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.TestCatalogs;

/**
 * Two requality-mcp processes on one folder of catalogs (two sessions of an agent, or one with VS Code): one process
 * holds a catalog at a time, also during and after a format conversion, and an idle process gives a catalog up when the
 * other one waits for it.
 */
class HandoffTest {
  /** A requality-mcp process and its standard input and output. */
  static final class Mcp implements AutoCloseable {
    final Process process;
    final OutputStream in;
    final BufferedReader out;
    int id;

    Mcp(File folder, File storage, File locks, long handoffMs, long lockWaitMs) throws Exception {
      List<String> cmd = new ArrayList<>(List.of(new File(System.getProperty("java.home"), "bin/java").getPath(),
          "-Drequality.locks=" + locks.getPath(), "-Drequality.handoff=" + handoffMs, "-Drequality.lockWait=" + lockWaitMs,
          "-cp", System.getProperty("java.class.path"), McpMain.class.getName(), "--storage", storage.getPath(), "--resources",
          System.getProperty("requality.resources"), folder.getPath()));
      process = new ProcessBuilder(cmd).redirectError(new File(storage.getPath() + ".log")).start();
      in = process.getOutputStream();
      out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
      JsonObject p = new JsonObject();
      p.addProperty("protocolVersion", "2025-06-18");
      p.add("capabilities", new JsonObject());
      rpc("initialize", p);
    }

    JsonObject rpc(String method, JsonObject params) throws Exception {
      JsonObject m = new JsonObject();
      m.addProperty("jsonrpc", "2.0");
      m.addProperty("id", ++id);
      m.addProperty("method", method);
      m.add("params", params);
      in.write((m + "\n").getBytes(StandardCharsets.UTF_8));
      in.flush();
      String line;
      while ((line = out.readLine()) != null) {
        JsonObject r = JsonParser.parseString(line).getAsJsonObject();
        if (r.has("id") && r.get("id").isJsonPrimitive() && r.get("id").getAsJsonPrimitive().isNumber() && r.get("id").getAsInt() == id)
          return r;
      }
      throw new AssertionError("the server ended");
    }

    /** A tool call: the text of its result, with "ERROR: " before it when it is an error. */
    String call(String tool, String... kv) throws Exception {
      JsonObject a = new JsonObject();
      for (int i = 0; i < kv.length; i += 2)
        a.addProperty(kv[i], kv[i + 1]);
      JsonObject p = new JsonObject();
      p.addProperty("name", tool);
      p.add("arguments", a);
      JsonObject r = rpc("tools/call", p).getAsJsonObject("result");
      String text = r.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
      return r.get("isError").getAsBoolean() ? "ERROR: " + text : text;
    }

    /** The state of a project in list_projects. */
    String state(String project) throws Exception {
      for (JsonElement e : JsonParser.parseString(call("list_projects")).getAsJsonObject().getAsJsonArray("projects"))
        if (e.getAsJsonObject().get("name").getAsString().equals(project))
          return e.getAsJsonObject().get("state").getAsString();
      return null;
    }

    /** Waits until the project has one of the states. */
    String await(String project, long ms, String... states) throws Exception {
      long end = System.currentTimeMillis() + ms;
      String s;
      do {
        s = state(project);
        for (String x : states)
          if (x.equals(s))
            return s;
        Thread.sleep(300);
      } while (System.currentTimeMillis() < end);
      return s;
    }

    @Override
    public void close() throws Exception {
      in.close();
      if (!process.waitFor(60, TimeUnit.SECONDS))
        process.destroyForcibly();
    }
  }

  @Test
  void anIdleSessionGivesACatalogToTheOther() throws Exception {
    File root = Files.createTempDirectory("requality-handoff").toFile();
    File ws = new File(root, "ws");
    ws.mkdirs();
    File locks = new File(root, "locks");
    try (Mcp a = new Mcp(ws, new File(root, "a"), locks, 1000, 30_000)) {
      assertTrue(a.call("create_project", "name", "Shared").contains("Shared"));
      String ops = "[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"R1\",\"name\":\"First\"}]";
      JsonObject args = new JsonObject();
      args.addProperty("project", "Shared");
      args.add("operations", JsonParser.parseString(ops));
      JsonObject p = new JsonObject();
      p.addProperty("name", "apply_changes");
      p.add("arguments", args);
      assertTrue(a.rpc("tools/call", p).toString().contains("\"isError\":false"));
      try (Mcp b = new Mcp(ws, new File(root, "b"), locks, 1000, 30_000)) {
        // b waits for the catalog and asks a, which gives it up when idle
        String node = b.call("get_node", "project", "Shared", "node", "/Requirements/R1");
        assertTrue(node.contains("\"name\":\"First\""), node);
        assertEquals("released", a.state("Shared"));
        // and back: b gives it up when a waits
        String back = a.call("get_node", "project", "Shared", "node", "/Requirements/R1");
        assertTrue(back.contains("\"name\":\"First\""), back);
        assertEquals("released", b.await("Shared", 10_000, "released"));
        // the undo steps of a ended with the load: the error names the change to revert
        String undo = a.call("undo");
        assertTrue(undo.startsWith("ERROR: ") && undo.contains("revert_change"), undo);
      }
    }
  }

  @Test
  void aConvertedCatalogStaysLocked() throws Exception {
    File root = Files.createTempDirectory("requality-convert-lock").toFile();
    File ws = new File(root, "ws");
    ws.mkdirs();
    TestCatalogs.copyEtsi(ws, "TS103096");
    File locks = new File(root, "locks");
    String project = "TS103096-STF517";
    // no hand-off in this test: the holder keeps the catalog
    try (Mcp a = new Mcp(ws, new File(root, "a"), locks, 600_000, 2_000)) {
      assertEquals("needs_conversion", a.await(project, 180_000, "needs_conversion"));
      String converted = a.call("convert_project", "project", project);
      assertTrue(converted.contains("backup"), converted);
      try (Mcp b = new Mcp(ws, new File(root, "b"), locks, 600_000, 2_000)) {
        // the catalog stayed locked during and after the conversion: the other process does not load it
        assertEquals("locked", b.await(project, 60_000, "locked", "ready"));
        String refused = b.call("get_tree", "project", project);
        assertTrue(refused.startsWith("ERROR: ") && refused.contains("open in another Requality process"), refused);
      }
    }
  }
}
