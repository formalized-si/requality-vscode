package com.unitesk.requality.vscode.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.TestCatalogs;
import com.unitesk.requality.vscode.rpc.MemoryPipe;

/**
 * The headless Requality with its MCP server, as an agent uses it: the protocol (initialize, tools/list, tools/call)
 * over the stdio framing, and the tools on the ETSI TS103096 catalog.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpServerTest {
  static final String P = "TS103096-STF517";
  static final String R12 = "/Requirements/SEC_ITSS/SEC_ITSS_RCV/SEC_ITSS_RCV_CERT/SEC_ITSS_RCV_CERT_12";

  static File root;
  static HeadlessClient headless;
  static ExecutorService pool;
  static MemoryPipe toServer;
  static final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
  static final AtomicInteger ids = new AtomicInteger();

  @BeforeAll
  static void start() throws Exception {
    root = Files.createTempDirectory("requality-mcp-test").toFile();
    File workspace = new File(root, "workspace");
    File storage = new File(root, "storage");
    workspace.mkdirs();
    storage.mkdirs();
    TestCatalogs.copyEtsi(workspace, "TS103096");
    headless = new HeadlessClient(new File(System.getProperty("requality.resources")), storage);
    headless.initialize(List.of(workspace));
    pool = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mcp-test");
      t.setDaemon(true);
      return t;
    });
    toServer = new MemoryPipe();
    MemoryPipe fromServer = new MemoryPipe();
    McpServer mcp = new McpServer(toServer.in, fromServer.out, pool, RequalityServer.VERSION, McpMain.INSTRUCTIONS);
    RequalityTools.register(mcp, headless);
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
        while ((line = r.readLine()) != null) {
          JsonObject m = JsonParser.parseString(line).getAsJsonObject();
          CompletableFuture<JsonObject> f = pending.remove(m.get("id").getAsInt());
          if (f != null)
            f.complete(m);
        }
      } catch (Exception e) {
        // closed
      }
    });
  }

  @AfterAll
  static void stop() throws Exception {
    toServer.out.close();
    headless.close();
    pool.shutdownNow();
  }

  static JsonObject rpc(String method, JsonObject params) throws Exception {
    int id = ids.incrementAndGet();
    JsonObject m = new JsonObject();
    m.addProperty("jsonrpc", "2.0");
    m.addProperty("id", id);
    m.addProperty("method", method);
    if (params != null)
      m.add("params", params);
    CompletableFuture<JsonObject> f = new CompletableFuture<>();
    pending.put(id, f);
    String line = m + "\n";
    assertFalse(m.toString().contains("\n"));
    toServer.out.write(line.getBytes(StandardCharsets.UTF_8));
    return f.get(300, TimeUnit.SECONDS);
  }

  /** Calls a tool; returns the JSON result (fails on an error result unless {@code error}). */
  static JsonObject call(String tool, boolean error, Object... kv) throws Exception {
    JsonObject args = new JsonObject();
    for (int i = 0; i < kv.length; i += 2) {
      Object v = kv[i + 1];
      if (v instanceof JsonElement)
        args.add((String) kv[i], (JsonElement) v);
      else if (v instanceof Number)
        args.addProperty((String) kv[i], (Number) v);
      else if (v instanceof Boolean)
        args.addProperty((String) kv[i], (Boolean) v);
      else
        args.addProperty((String) kv[i], (String) v);
    }
    JsonObject p = new JsonObject();
    p.addProperty("name", tool);
    p.add("arguments", args);
    JsonObject res = rpc("tools/call", p);
    JsonObject result = res.getAsJsonObject("result");
    String text = result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    assertEquals(error, result.get("isError").getAsBoolean(), tool + ": " + text);
    if (error) {
      JsonObject e = new JsonObject();
      e.addProperty("error", text);
      return e;
    }
    return JsonParser.parseString(text).getAsJsonObject();
  }

  static JsonObject call(String tool, Object... kv) throws Exception {
    return call(tool, false, kv);
  }

  /** Calls a tool that must fail; returns {"error": text}. */
  static JsonObject callError(String tool, Object... kv) throws Exception {
    return call(tool, true, kv);
  }

  static List<String> paths(JsonArray nodes) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : nodes)
      r.add(e.getAsJsonObject().get("path").getAsString());
    return r;
  }

  @Test
  @Order(1)
  void initializeNegotiatesTheVersionAndListsTheTools() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("protocolVersion", "2025-06-18");
    p.add("capabilities", new JsonObject());
    JsonObject r = rpc("initialize", p).getAsJsonObject("result");
    assertEquals("2025-06-18", r.get("protocolVersion").getAsString());
    assertEquals("requality", r.getAsJsonObject("serverInfo").get("name").getAsString());
    assertTrue(r.getAsJsonObject("capabilities").has("tools"));
    p.addProperty("protocolVersion", "1999-01-01");
    assertEquals(McpServer.DEFAULT_PROTOCOL_VERSION, rpc("initialize", p).getAsJsonObject("result").get("protocolVersion").getAsString());
    assertEquals(0, rpc("ping", null).getAsJsonObject("result").size());
    List<String> names = new ArrayList<>();
    for (JsonElement t : rpc("tools/list", null).getAsJsonObject("result").getAsJsonArray("tools")) {
      JsonObject o = t.getAsJsonObject();
      names.add(o.get("name").getAsString());
      assertEquals("object", o.getAsJsonObject("inputSchema").get("type").getAsString(), o.toString());
      assertTrue(o.getAsJsonObject("annotations").has("readOnlyHint"), o.toString());
    }
    for (String n : new String[] { "list_projects", "get_tree", "get_node", "search_nodes", "get_problems", "get_document_text",
        "list_report_templates", "server_messages", "create_node", "update_node", "delete_node", "move_node", "add_link",
        "remove_link", "create_location", "import_document", "generate_report", "undo", "redo", "open_project", "create_project" })
      assertTrue(names.contains(n), n + " in " + names);
    JsonObject unknown = new JsonObject();
    unknown.addProperty("name", "no_such_tool");
    assertEquals(-32602, rpc("tools/call", unknown).getAsJsonObject("error").get("code").getAsInt());
    assertEquals(-32601, rpc("no/such/method", null).getAsJsonObject("error").get("code").getAsInt());
  }

  @Test
  @Order(2)
  void theConvertedProjectIsReadable() throws Exception {
    JsonArray projects = call("list_projects").getAsJsonArray("projects");
    assertEquals(P, projects.get(0).getAsJsonObject().get("name").getAsString(), projects.toString());
    JsonObject tree = call("get_tree", "project", P, "depth", 1);
    assertTrue(paths(tree.getAsJsonArray("nodes")).containsAll(List.of("/Documents", "/Requirements", "/Reports")), tree.toString());
    // the old catalog format was converted when it opened (the message the core showed is kept)
    assertTrue(call("server_messages").toString().contains("Conversion of"), "conversion message");
    JsonObject r12 = call("get_node", "project", P, "node", R12);
    assertEquals("Requirement", r12.get("type").getAsString());
    assertEquals(2, r12.getAsJsonArray("locations").size(), r12.toString());
    JsonObject found = call("search_nodes", "project", P, "text", "AID-SSP", "type", "Requirement");
    assertTrue(paths(found.getAsJsonArray("matches")).contains(R12), found.toString());
    JsonObject missing = callError("get_node", "project", P, "node", "/Requirements/NoSuchNode");
    assertTrue(missing.get("error").getAsString().startsWith("No node"), missing.toString());
  }

  @Test
  @Order(3)
  void anAgentCreatesAndLinksARequirement() throws Exception {
    JsonObject attrs = JsonParser.parseString("{\"priority\":2,\"status\":\"draft\",\"tags\":[\"a\",\"b\"]}").getAsJsonObject();
    JsonObject n = call("create_node", "project", P, "parent", "/Requirements", "id", "MCP_1", "name", "Agent requirement",
        "description", "First line.\n\nSecond paragraph.", "attributes", attrs);
    assertEquals("/Requirements/MCP_1", n.get("path").getAsString());
    assertEquals("First line.\n\nSecond paragraph.", n.get("description").getAsString());
    assertEquals(2, n.getAsJsonObject("attributes").getAsJsonObject("priority").get("value").getAsInt());
    JsonObject upd = JsonParser.parseString("{\"status\":\"approved\",\"tags\":null}").getAsJsonObject();
    n = call("update_node", "project", P, "node", "/Requirements/MCP_1", "attributes", upd);
    assertEquals("approved", n.getAsJsonObject("attributes").getAsJsonObject("status").get("value").getAsString());
    assertFalse(n.getAsJsonObject("attributes").has("tags"), n.toString());
    JsonObject l = call("add_link", "project", P, "from", "/Requirements/MCP_1", "to", R12);
    assertEquals(R12, l.getAsJsonObject("links").getAsJsonArray("elaborates").get(0).getAsString(), l.toString());
    JsonObject target = call("get_node", "project", P, "node", R12);
    assertEquals("/Requirements/MCP_1", target.getAsJsonObject("linked_from").getAsJsonArray("elaborates").get(0).getAsString());
    JsonObject tp = call("create_node", "project", P, "parent", "/Requirements/MCP_1", "type", "TestPurpose", "name", "Check it");
    assertEquals("TestPurpose", tp.get("type").getAsString());
    call("remove_link", "project", P, "from", "/Requirements/MCP_1", "to", R12);
    assertFalse(call("get_node", "project", P, "node", "/Requirements/MCP_1").getAsJsonObject("links").has("elaborates"));
    JsonObject bad = callError("create_node", "project", P, "parent", "/Requirements", "id", "a/b");
    assertTrue(bad.get("error").getAsString().contains("not allowed"), bad.toString());
  }

  @Test
  @Order(4)
  void anImportedDocumentGetsALocation() throws Exception {
    File html = new File(root, "spec.html");
    Files.writeString(html.toPath(), "<html><body><h1>Spec</h1><p>The system shall log every access.</p>"
        + "<p>The system shall encrypt\n   stored data.</p></body></html>");
    JsonObject doc = call("import_document", "project", P, "file", html.getAbsolutePath());
    assertEquals("/Documents/spec.xhtml", doc.get("path").getAsString(), doc.toString());
    JsonObject text = call("get_document_text", "project", P, "document", "/Documents/spec.xhtml");
    assertTrue(text.get("text").getAsString().contains("The system shall log every access."), text.toString());
    // the white space of the quote does not have to be the one of the document
    JsonObject loc = call("create_location", "project", P, "document", "/Documents/spec.xhtml", "requirement", "/Requirements/MCP_1",
        "quote", "The system shall encrypt stored data.");
    assertEquals("The system shall encrypt stored data.", loc.get("text").getAsString(), loc.toString());
    JsonObject req = call("get_node", "project", P, "node", "/Requirements/MCP_1");
    JsonArray locs = req.getAsJsonArray("locations");
    assertEquals(1, locs.size(), req.toString());
    assertEquals("/Documents/spec.xhtml", locs.get(0).getAsJsonObject().get("document").getAsString());
    // the ranges of the existing ETSI locations are ranges of the same text
    JsonObject etsi = call("get_document_text", "project", P, "document", "/Documents/ts_103097v010201p.xhtml", "offset", 171850,
        "length", 200);
    boolean seen = false;
    for (JsonElement e : etsi.getAsJsonArray("locations"))
      seen |= e.getAsJsonObject().get("text").getAsString().equals("For each ITS-AID only one ItsAidSsp shall be used.");
    assertTrue(seen, etsi.toString());
  }

  @Test
  @Order(5)
  void moveDeleteAndUndo() throws Exception {
    call("move_node", "project", P, "node", "/Requirements/MCP_1", "parent", "/Requirements/SEC_ITSS");
    call("get_node", "project", P, "node", "/Requirements/SEC_ITSS/MCP_1");
    JsonObject cyc = callError("move_node", "project", P, "node", "/Requirements/SEC_ITSS", "parent", "/Requirements/SEC_ITSS/MCP_1");
    assertTrue(cyc.get("error").getAsString().contains("own subtree"), cyc.toString());
    call("delete_node", "project", P, "node", "/Requirements/SEC_ITSS/MCP_1");
    callError("get_node", "project", P, "node", "/Requirements/SEC_ITSS/MCP_1");
    assertEquals("Delete Requirement", call("undo").get("undone").getAsString());
    call("get_node", "project", P, "node", "/Requirements/SEC_ITSS/MCP_1");
    JsonObject top = callError("delete_node", "project", P, "node", "/Requirements");
    assertTrue(top.get("error").getAsString().contains("cannot be deleted"), top.toString());
  }

  @Test
  @Order(6)
  void reportsAndProblems() throws Exception {
    JsonArray templates = call("list_report_templates", "project", P).getAsJsonArray("templates");
    assertTrue(templates.toString().contains("com.unitesk.requality.reports.tableview"), templates.toString());
    File out = new File(root, "report");
    JsonObject rep = call("generate_report", "project", P, "template", "com.unitesk.requality.reports.tableview", "output",
        out.getAbsolutePath(), "root", "/Requirements/SEC_ITSS/SEC_ITSS_RCV");
    JsonArray files = rep.getAsJsonArray("files");
    assertEquals(1, files.size(), rep.toString());
    assertTrue(Files.size(new File(files.get(0).getAsString()).toPath()) > 1000, rep.toString());
    // the temporary report settings are gone and not in the undo history
    assertEquals(1, call("get_tree", "project", P, "node", "/Reports", "depth", 1).getAsJsonArray("nodes").size());
    JsonObject probs = call("get_problems", "project", P, "node", "/Requirements/SEC_ITSS", "refresh", true);
    assertTrue(probs.get("checked_nodes").getAsInt() > 10, probs.toString());
  }

  @Test
  @Order(7)
  void anAgentCreatesAProject() throws Exception {
    assertEquals("AgentProject", call("create_project", "name", "AgentProject").get("project").getAsString());
    JsonObject tree = call("get_tree", "project", "AgentProject", "depth", 1);
    assertTrue(paths(tree.getAsJsonArray("nodes")).contains("/Requirements"), tree.toString());
    call("create_node", "project", "AgentProject", "parent", "/Requirements", "id", "R1", "name", "First");
    assertEquals("First", call("get_node", "project", "AgentProject", "node", "/Requirements/R1").get("name").getAsString());
  }
}
