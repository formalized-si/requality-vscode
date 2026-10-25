package com.unitesk.requality.vscode.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.document.SAXTextExtractor;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.TestCatalogs;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.rpc.MemoryPipe;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The headless Requality with its MCP server, as an agent uses it: the protocol (initialize, tools/list, tools/call)
 * over the stdio framing, and the tools on the ETSI TS103096 catalog, which has the format of an older Requality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpServerTest {
  static final String P = "TS103096-STF517";
  static final String R12 = "/Requirements/SEC_ITSS/SEC_ITSS_RCV/SEC_ITSS_RCV_CERT/SEC_ITSS_RCV_CERT_12";

  static File root;
  static File storage;
  static HeadlessClient headless;
  static ExecutorService pool;
  static MemoryPipe toServer;
  static McpServer mcp;
  static final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
  /** The responses no test waits for by id (errors with a null id, responses of raw lines). */
  static final BlockingQueue<JsonObject> others = new LinkedBlockingQueue<>();
  static final AtomicInteger ids = new AtomicInteger();

  @BeforeAll
  static void start() throws Exception {
    root = Files.createTempDirectory("requality-mcp-test").toFile();
    File workspace = new File(root, "workspace");
    storage = new File(root, "storage");
    workspace.mkdirs();
    storage.mkdirs();
    TestCatalogs.copyEtsi(workspace, "TS103096");
    // as requality-mcp without --convert: a catalog of an older format waits for convert_project
    headless = new HeadlessClient(new File(System.getProperty("requality.resources")), storage, false);
    headless.initialize(List.of(workspace));
    pool = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "mcp-test");
      t.setDaemon(true);
      return t;
    });
    toServer = new MemoryPipe();
    MemoryPipe fromServer = new MemoryPipe();
    mcp = new McpServer(toServer.in, fromServer.out, pool, RequalityServer.VERSION, McpMain.INSTRUCTIONS);
    // the folders of the server: the workspace and the test folder (the files to import, the report folders)
    RequalityTools.register(mcp, headless, new Policy(List.of(workspace, root)));
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
          JsonElement id = m.get("id");
          CompletableFuture<JsonObject> f = id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isNumber()
              ? pending.remove(id.getAsInt()) : null;
          if (f != null)
            f.complete(m);
          else
            others.add(m);
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

  static void send(String line) throws Exception {
    toServer.out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
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
    assertFalse(m.toString().contains("\n"));
    send(m.toString());
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

  /** Calls a tool that must fail; returns its message. */
  static String callError(String tool, Object... kv) throws Exception {
    return call(tool, true, kv).get("error").getAsString();
  }

  static List<String> paths(JsonArray nodes) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : nodes)
      r.add(e.getAsJsonObject().get("path").getAsString());
    return r;
  }

  /** The paths of the children in a get_tree result (the parent path, "/" and the id). */
  static List<String> childPaths(JsonObject tree) {
    String base = tree.get("path").getAsString();
    List<String> r = new ArrayList<>();
    for (JsonElement e : tree.getAsJsonArray("nodes")) {
      assertFalse(e.getAsJsonObject().has("uuid"), "a nested node has no uuid: " + e);
      r.add((base.equals("/") ? "" : base) + "/" + e.getAsJsonObject().get("id").getAsString());
    }
    return r;
  }

  static JsonArray ops(String json) {
    return JsonParser.parseString(json).getAsJsonArray();
  }

  static JsonObject paragraph(JsonObject doc, String textPart) {
    for (JsonElement e : doc.getAsJsonArray("paragraphs"))
      if (e.getAsJsonObject().get("text").getAsString().contains(textPart))
        return e.getAsJsonObject();
    return null;
  }

  static String state(String project) throws Exception {
    for (JsonElement e : call("list_projects").getAsJsonArray("projects"))
      if (e.getAsJsonObject().get("name").getAsString().equals(project))
        return e.getAsJsonObject().get("state").getAsString();
    return null;
  }

  static void assertContains(String text, String part) {
    assertTrue(text.contains(part), "expected «" + part + "» in: " + text);
  }

  @Test
  @Order(1)
  void theProtocolIsRobust() throws Exception {
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
      String name = o.get("name").getAsString();
      names.add(name);
      assertEquals("object", o.getAsJsonObject("inputSchema").get("type").getAsString(), o.toString());
      JsonObject an = o.getAsJsonObject("annotations");
      if (List.of("undo", "redo", "revert_change", "apply_changes", "convert_project", "update_document_version").contains(name))
        assertTrue(an.get("destructiveHint").getAsBoolean(), o.toString());
      if (name.equals("apply_changes"))
        assertEquals(9, o.getAsJsonObject("inputSchema").getAsJsonObject("properties").getAsJsonObject("operations")
            .getAsJsonObject("items").getAsJsonArray("oneOf").size(), "one schema for each operation");
    }
    assertEquals(List.of("list_projects", "get_tree", "get_node", "search_nodes", "read_document", "apply_changes", "import_document",
        "update_document_version", "trace_coverage", "test_coverage", "review_changes", "revert_change", "undo", "redo", "list_report_templates",
        "generate_report", "open_project", "close_project", "convert_project", "create_project", "server_messages"), names);
    // the prompts: the jobs of the person as ready-made requests
    List<String> prompts = new ArrayList<>();
    for (JsonElement e : rpc("prompts/list", null).getAsJsonObject("result").getAsJsonArray("prompts"))
      prompts.add(e.getAsJsonObject().get("name").getAsString());
    assertEquals(List.of("requirements_from_document", "update_specification", "coverage_audit", "review_agent_changes"), prompts);
    JsonObject pg = new JsonObject();
    pg.addProperty("name", "requirements_from_document");
    JsonObject pa = new JsonObject();
    pa.addProperty("project", P);
    pa.addProperty("document", "/tmp/spec.docx");
    pg.add("arguments", pa);
    String text = rpc("prompts/get", pg).getAsJsonObject("result").getAsJsonArray("messages").get(0).getAsJsonObject()
        .getAsJsonObject("content").get("text").getAsString();
    assertTrue(text.contains(P) && text.contains("/tmp/spec.docx") && text.contains("read_document"), text);
    pa.remove("document");
    assertEquals(-32602, rpc("prompts/get", pg).getAsJsonObject("error").get("code").getAsInt());
    // a wrong argument name: the error names the arguments of the tool, so the agent corrects the call
    assertTrue(callError("read_document", "project", P, "node", "/Documents/x.xhtml")
        .startsWith("Unknown argument node; read_document takes the arguments project, document"));
    assertTrue(callError("read_document", "project", P).startsWith("Missing argument document (The document: "));
    JsonObject unknown = new JsonObject();
    unknown.addProperty("name", "no_such_tool");
    assertEquals(-32602, rpc("tools/call", unknown).getAsJsonObject("error").get("code").getAsInt());
    JsonObject badName = new JsonObject();
    badName.addProperty("name", 5);
    assertEquals(-32602, rpc("tools/call", badName).getAsJsonObject("error").get("code").getAsInt());
    assertEquals(-32601, rpc("no/such/method", null).getAsJsonObject("error").get("code").getAsInt());

    // malformed messages get errors and the server goes on
    others.clear();
    send("{not json");
    assertEquals(-32700, others.poll(30, TimeUnit.SECONDS).getAsJsonObject("error").get("code").getAsInt());
    send("{\"jsonrpc\":\"2.0\",\"id\":{\"a\":1},\"method\":\"ping\"}");
    assertEquals(-32600, others.poll(30, TimeUnit.SECONDS).getAsJsonObject("error").get("code").getAsInt());
    send("[]");
    assertEquals(-32600, others.poll(30, TimeUnit.SECONDS).getAsJsonObject("error").get("code").getAsInt());
    send("5");
    assertEquals(-32600, others.poll(30, TimeUnit.SECONDS).getAsJsonObject("error").get("code").getAsInt());
    CompletableFuture<JsonObject> f = new CompletableFuture<>();
    pending.put(9001, f);
    send("{\"jsonrpc\":\"2.0\",\"id\":9001,\"method\":5}");
    assertEquals(-32600, f.get(30, TimeUnit.SECONDS).getAsJsonObject("error").get("code").getAsInt());
    f = new CompletableFuture<>();
    pending.put(9002, f);
    send("{\"jsonrpc\":\"2.0\",\"id\":9002,\"method\":\"ping\",\"params\":[1]}");
    assertEquals(-32602, f.get(30, TimeUnit.SECONDS).getAsJsonObject("error").get("code").getAsInt());
    // a request cancelled while it runs gets no response (a tool of this test runs 1.5 s)
    mcp.add(new McpServer.Tool("slow_test", "Slow", "A tool of the test.", RequalityTools.Schema.object().build(), true, false, a -> {
      Thread.sleep(1500);
      return "done";
    }));
    f = new CompletableFuture<>();
    pending.put(9003, f);
    send("{\"jsonrpc\":\"2.0\",\"id\":9003,\"method\":\"tools/call\",\"params\":{\"name\":\"slow_test\"}}");
    Thread.sleep(300);
    send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":9003}}");
    Thread.sleep(2500);
    assertFalse(f.isDone(), "the cancelled request was answered");
    pending.remove(9003);
    // the cancellation of a request that does not run is ignored: a later request with that id gets its response
    send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":9004}}");
    f = new CompletableFuture<>();
    pending.put(9004, f);
    send("{\"jsonrpc\":\"2.0\",\"id\":9004,\"method\":\"ping\"}");
    assertEquals(0, f.get(30, TimeUnit.SECONDS).getAsJsonObject("result").size());
  }

  @Test
  @Order(2)
  void aCatalogOfAnOlderFormatIsConvertedAfterABackup() throws Exception {
    // the conversion question is cancelled: the project waits for convert_project
    long end = System.currentTimeMillis() + 180_000;
    while (!"needs_conversion".equals(state(P)) && System.currentTimeMillis() < end)
      Thread.sleep(200);
    assertEquals("needs_conversion", state(P));
    assertContains(callError("get_tree", "project", P), "convert_project");
    JsonObject conv = call("convert_project", "project", P);
    File backup = new File(conv.get("backup").getAsString());
    assertTrue(backup.toPath().startsWith(new File(storage, "backups").toPath()), conv.toString());
    assertTrue(new File(backup, ".project").isFile(), "the backup has the catalog: " + backup);
    assertFalse(new File(backup, ".reqcache").exists(), "the backup has no caches");
    assertEquals("ready", state(P));
    assertContains(call("server_messages").toString(), "Conversion of");
    assertContains(callError("convert_project", "project", P), "needs no conversion");
  }

  @Test
  @Order(3)
  void theConvertedProjectIsReadable() throws Exception {
    JsonArray projects = call("list_projects").getAsJsonArray("projects");
    assertEquals(P, projects.get(0).getAsJsonObject().get("name").getAsString(), projects.toString());
    JsonObject tree = call("get_tree", "project", P, "depth", 1);
    assertTrue(childPaths(tree).containsAll(List.of("/Documents", "/Requirements", "/Reports")), tree.toString());
    JsonObject r12 = call("get_node", "project", P, "node", R12);
    assertEquals("Requirement", r12.get("type").getAsString());
    assertEquals(2, r12.getAsJsonArray("locations").size(), r12.toString());
    JsonObject found = call("search_nodes", "project", P, "text", "AID-SSP", "type", "requirement");
    assertTrue(paths(found.getAsJsonArray("matches")).contains(R12), found.toString());
    // a document lists its locations as a count: read_document shows them with their text
    JsonObject docNode = call("get_node", "project", P, "node", "/Documents/ts_103097v010201p.xhtml");
    assertEquals(131, docNode.get("locations_count").getAsInt(), docNode.toString());
    assertEquals(0, docNode.getAsJsonArray("children").size(), docNode.toString());
    JsonObject docs = call("get_tree", "project", P, "node", "/Documents", "depth", 3);
    JsonObject etsiDoc = docs.getAsJsonArray("nodes").get(0).getAsJsonObject();
    assertEquals(131, etsiDoc.get("locations").getAsInt(), docs.toString());
    assertFalse(etsiDoc.has("nodes"), "the locations of a document are a number: " + docs);
    assertTrue(callError("get_node", "project", P, "node", "/Requirements/NoSuchNode").startsWith("No node"));
    // the paragraphs of the ETSI document: the existing locations cover the text they mark
    JsonObject etsi = call("read_document", "project", P, "document", "/Documents/ts_103097v010201p.xhtml", "count", 5000);
    JsonObject itsAid = paragraph(etsi, "For each ITS-AID only one ItsAidSsp shall be used.");
    assertTrue(itsAid != null && itsAid.getAsJsonArray("requirements").toString().contains(R12), String.valueOf(itsAid));
    assertTrue(etsi.get("covered_text_percent").getAsDouble() > 10, etsi.get("covered_text_percent").toString());
    // a search finds the text of locations: each one with its document, text and requirements
    JsonObject locs = call("search_nodes", "project", P, "text", "ItsAidSsp", "type", "Location");
    JsonObject hit = null;
    for (JsonElement e : locs.getAsJsonArray("matches"))
      if (e.getAsJsonObject().getAsJsonArray("requirements").toString().contains(R12))
        hit = e.getAsJsonObject();
    assertNotNull(hit, locs.toString());
    assertEquals("/Documents/ts_103097v010201p.xhtml", hit.get("document").getAsString(), locs.toString());
    assertContains(hit.get("text").getAsString(), "only one ItsAidSsp shall be used");
  }

  @Test
  @Order(4)
  void theAgentReachesOnlyTheFoldersOfTheServer() throws Exception {
    File outside = Files.createTempFile("requality-outside", ".html").toFile();
    outside.deleteOnExit();
    Files.writeString(outside.toPath(), "<html><body><p>The system shall not be imported.</p></body></html>");
    String e = callError("import_document", "project", P, "file", outside.getAbsolutePath());
    assertContains(e, "outside the folders");
    assertContains(e, "--allow");
    assertContains(callError("import_document", "project", P, "file", "spec.html"), "absolute path");
    File script = new File(root, "tool.sh");
    Files.writeString(script.toPath(), "echo");
    assertContains(callError("import_document", "project", P, "file", script.getAbsolutePath()), "is no document");
    assertContains(callError("import_document", "project", P, "file", new File(root, "a/../../x.html").getPath()), "outside");
    File full = new File(root, "full");
    full.mkdirs();
    Files.writeString(new File(full, "keep.txt").toPath(), "keep");
    assertContains(callError("generate_report", "project", P, "template", "com.unitesk.requality.reports.tableview", "output",
        full.getAbsolutePath()), "is not empty");
    assertContains(callError("generate_report", "project", P, "template", "com.unitesk.requality.reports.tableview", "output",
        new File(outside.getParentFile(), "requality-report-" + System.nanoTime()).getAbsolutePath()), "outside");
  }

  @Test
  @Order(5)
  void anAgentTurnsADocumentIntoRequirements() throws Exception {
    File html = new File(root, "spec.html");
    Files.writeString(html.toPath(), "<html><body><h1>Spec</h1><p>The system shall log every access.</p>"
        + "<p>The system shall encrypt\n   stored data.</p><p>The system shall keep logs for one year.</p>"
        + "<p>The system shall lock the account after 3 failed attempts.</p></body></html>");
    JsonObject doc = call("import_document", "project", P, "file", html.getAbsolutePath());
    assertEquals("/Documents/spec.xhtml", doc.get("path").getAsString(), doc.toString());
    assertEquals(5, doc.get("paragraphs").getAsInt(), doc.toString());
    JsonObject read = call("read_document", "project", P, "document", "/Documents/spec.xhtml");
    assertEquals(1, paragraph(read, "Spec").get("heading").getAsInt(), read.toString());
    assertFalse(paragraph(read, "log every access").has("heading"), read.toString());
    assertEquals("none", paragraph(read, "log every access").get("coverage").getAsString());
    int encrypt = paragraph(read, "encrypt").get("p").getAsInt();

    // a check changes nothing
    JsonObject dry = call("apply_changes", "project", P, "dry_run", true, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"DRY\"}]"));
    assertTrue(dry.get("dry_run").getAsBoolean());
    callError("get_node", "project", P, "node", "/Requirements/DRY");
    // all operations or none: the last one fails, the first one is not made
    assertTrue(callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"HALF\"},{\"op\":\"delete\",\"node\":\"/Requirements\"}]"))
        .startsWith("Operation 1 (delete)"));
    callError("get_node", "project", P, "node", "/Requirements/HALF");
    // the keys and the values of the operations are checked before anything is made
    assertContains(callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"create\",\"parnet\":\"/Requirements\"}]")), "unknown key 'parnet'; create takes parent, type");
    assertContains(callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"type\":\"Folder\"}]")), "must be one of Requirement, TestPurpose");
    assertContains(callError("apply_changes", "project", P, "operations", ops("[{\"op\":\"locate\",\"requirement\":\"" + R12
        + "\",\"document\":\"/Documents/spec.xhtml\",\"paragraph\":\"two\"}]")), "Argument paragraph must be an integer");
    // a quote that does not occur: the error shows the most similar text
    assertContains(callError("apply_changes", "project", P, "operations", ops("[{\"op\":\"locate\",\"requirement\":\"" + R12
        + "\",\"document\":\"/Documents/spec.xhtml\",\"quote\":\"The system shall encrypt all the data.\"}]")),
        "The most similar text is in paragraph " + encrypt + ": \"The system shall encrypt stored data.\"");

    JsonObject attrs = JsonParser.parseString("{\"priority\":2,\"status\":\"draft\",\"tags\":[\"a\",\"b\"]}").getAsJsonObject();
    JsonArray batch = ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"SPEC\",\"name\":\"Specification\",\"as\":\"spec\"},"
        + "{\"op\":\"create\",\"parent\":\"$spec\",\"id\":\"LOG\",\"name\":\"Access log\",\"description\":\"First line.\\n\\nSecond paragraph.\",\"as\":\"log\"},"
        + "{\"op\":\"create\",\"parent\":\"$spec\",\"id\":\"ENC\",\"name\":\"Encryption\",\"as\":\"enc\"},"
        + "{\"op\":\"create\",\"parent\":\"$spec\",\"id\":\"LOCKOUT\",\"name\":\"Lockout\",\"as\":\"lock\"},"
        // letter case does not matter in a quote
        + "{\"op\":\"locate\",\"requirement\":\"$log\",\"document\":\"/Documents/spec.xhtml\",\"quote\":\"the SYSTEM shall log every access.\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$enc\",\"document\":\"/Documents/spec.xhtml\",\"paragraph\":" + encrypt + "},"
        + "{\"op\":\"locate\",\"requirement\":\"$lock\",\"document\":\"/Documents/spec.xhtml\",\"quote\":\"lock the account after 3 failed attempts.\"},"
        + "{\"op\":\"link\",\"from\":\"$enc\",\"to\":\"" + R12 + "\"},"
        + "{\"op\":\"create\",\"parent\":\"$log\",\"type\":\"testpurpose\",\"name\":\"Check the log\"}]");
    batch.get(1).getAsJsonObject().add("attributes", attrs);
    JsonObject applied = call("apply_changes", "project", P, "summary", "Requirements of spec.html", "operations", batch);
    assertEquals(9, applied.getAsJsonArray("results").size(), applied.toString());
    JsonObject log = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG");
    assertEquals("First line.\n\nSecond paragraph.", log.get("description").getAsString());
    assertEquals(2, log.getAsJsonObject("attributes").getAsJsonObject("priority").get("value").getAsInt());
    assertEquals("TestPurpose", log.getAsJsonArray("children").get(0).getAsJsonObject().get("type").getAsString());
    assertEquals("The system shall log every access.", log.getAsJsonArray("locations").get(0).getAsJsonObject().get("text").getAsString());
    JsonObject enc = call("get_node", "project", P, "node", "/Requirements/SPEC/ENC");
    assertEquals("The system shall encrypt stored data.", enc.getAsJsonArray("locations").get(0).getAsJsonObject().get("text").getAsString());
    assertEquals(R12, enc.getAsJsonObject("links").getAsJsonArray("elaborates").get(0).getAsString(), enc.toString());
    // a link keeps its target when the target gets another name or id
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"LX\",\"as\":\"x\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"LY\",\"name\":\"Old name\",\"as\":\"y\"},"
        + "{\"op\":\"link\",\"from\":\"$x\",\"to\":\"$y\"}]"));
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/LY\",\"id\":\"LZ\",\"name\":\"New name\"}]"));
    assertEquals("/Requirements/LZ", call("get_node", "project", P, "node", "/Requirements/LX").getAsJsonObject("links")
        .getAsJsonArray("elaborates").get(0).getAsString());
    assertEquals(0, call("trace_coverage", "project", P, "under", "/Requirements/LX").getAsJsonObject("requirements")
        .getAsJsonObject("broken_links").get("total").getAsInt());
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"node\":\"/Requirements/LX\"},{\"op\":\"delete\",\"node\":\"/Requirements/LZ\"}]"));
    // a requirement of the legacy catalog whose location reference has the old path form can be deleted
    assertTrue(call("apply_changes", "project", P, "dry_run", true, "operations", ops("[{\"op\":\"delete\",\"node\":"
        + "\"/Requirements/SEC_ITSS/SEC_ITSS_SND/SEC_ITSS_SND_GENMSG/SEC_ITSS_SND_GENMSG_01\"}]")).get("dry_run").getAsBoolean());
    // the text no requirement covers yet (the filter in any case)
    JsonObject uncovered = call("read_document", "project", P, "document", "/Documents/spec.xhtml", "filter", "UNCOVERED");
    List<String> left = new ArrayList<>();
    for (JsonElement e : uncovered.getAsJsonArray("paragraphs"))
      left.add(e.getAsJsonObject().get("text").getAsString());
    assertEquals(List.of("Spec", "The system shall keep logs for one year."), left);
    // unlocate with a requirement that does not have the location changes nothing
    String logLocation = log.getAsJsonArray("locations").get(0).getAsJsonObject().get("uuid").getAsString();
    assertContains(callError("apply_changes", "project", P, "operations", ops("[{\"op\":\"unlocate\",\"location\":\""
        + logLocation + "\",\"requirement\":\"/Requirements/SPEC/ENC\"}]")), "has no location");
    assertEquals(1, call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations").size());

    // typographic quotes and dashes in the document, ASCII ones in the quote
    File notes = new File(root, "notes.html");
    Files.writeString(notes.toPath(), "<html><body><h2>Notes</h2><p>The operator’s console shall show “alarms” – always.</p>"
        + "<pre>\nfirst line of code</pre><p>The system shall not stop.</p></body></html>", StandardCharsets.UTF_8);
    call("import_document", "project", P, "file", notes.getAbsolutePath());
    JsonObject nd = call("read_document", "project", P, "document", "/Documents/notes.xhtml", "ranges", true);
    assertEquals(2, paragraph(nd, "Notes").get("heading").getAsInt(), nd.toString());
    JsonObject quoted = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"NOTES\",\"as\":\"n\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$n\",\"document\":\"/Documents/notes.xhtml\",\"quote\":\"The operator's console shall show \\\"alarms\\\" - always.\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$n\",\"document\":\"/Documents/notes.xhtml\",\"quote\":\"The system shall not stop.\"}]"));
    assertEquals("The operator’s console shall show “alarms” – always.", quoted.getAsJsonArray("results").get(1)
        .getAsJsonObject().getAsJsonObject("location").get("text").getAsString());
    JsonObject last = quoted.getAsJsonArray("results").get(2).getAsJsonObject().getAsJsonObject("location");
    JsonObject stop = paragraph(nd, "shall not stop");
    assertEquals(stop.get("start").getAsInt(), last.getAsJsonArray("ranges").get(0).getAsJsonArray().get(0).getAsInt(), nd.toString());
    // a DOCX file is imported without LibreOffice (Apache Tika is in the server)
    File docx = new File(root, "rules.docx");
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(docx))) {
      String[][] parts = { { "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
          + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
          + "<Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" "
          + "ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>" },
          { "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
              + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
              + "Target=\"word/document.xml\"/></Relationships>" },
          { "word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
              + "<w:body><w:p><w:r><w:t>The door shall close in 15 s.</w:t></w:r></w:p><w:p><w:r><w:t>The door shall not close on a person.</w:t></w:r></w:p>"
              + "</w:body></w:document>" } };
      for (String[] part : parts) {
        z.putNextEntry(new java.util.zip.ZipEntry(part[0]));
        z.write(part[1].getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
      }
    }
    JsonObject rules = call("import_document", "project", P, "file", docx.getAbsolutePath());
    JsonObject rd = call("read_document", "project", P, "document", rules.get("path").getAsString());
    assertNotNull(paragraph(rd, "The door shall not close on a person."), rd.toString());

    // the text the offsets count is the text the core counts (its SAX text extractor), also after a <pre>
    TreeDB db = Nodes.findDB("TS103096");
    for (String d : new String[] { "/Documents/notes.xhtml", "/Documents/spec.xhtml", "/Documents/ts_103097v010201p.xhtml",
        rules.get("path").getAsString() }) {
      Document document = (Document) Core.node(db, d);
      assertEquals(coreText(document), DocumentText.of(document).text, d);
    }
  }

  /** The text of a document as the Document Editor of the core counts the offsets of locations. */
  static String coreText(Document doc) throws Exception {
    SAXTextExtractor ste = new SAXTextExtractor();
    ste.setNoTransactionsFlag(true);
    ste.setShowMessages(false, false);
    ste.process(doc, doc.getTreeDB().getNode(Requirement.getTypeRootQId()), null, null, null);
    return ste.getText().replace("\r\n", "\n").replace('\r', '\n');
  }

  @Test
  @Order(6)
  void thePersonReviewsAndUndoesTheChanges() throws Exception {
    JsonObject review = call("review_changes", "project", P);
    JsonObject first = null;
    for (JsonElement e : review.getAsJsonArray("changes"))
      if ("Requirements of spec.html".equals(e.getAsJsonObject().has("summary") ? e.getAsJsonObject().get("summary").getAsString() : null))
        first = e.getAsJsonObject();
    assertNotNull(first, review.toString());
    assertEquals("apply_changes", first.get("tool").getAsString());
    assertEquals("done", first.get("state").getAsString());
    assertEquals(5, first.getAsJsonObject("kinds").get("create").getAsInt(), first.toString());
    assertTrue(first.getAsJsonArray("nodes").toString().contains("/Requirements/SPEC"), first.toString());
    assertFalse(first.has("operations") && first.get("operations").isJsonArray(), "a summary without the operations");
    JsonObject full = call("review_changes", "project", P, "change", first.get("change").getAsInt());
    assertEquals(9, full.getAsJsonArray("operations").size(), full.toString());

    JsonObject update = call("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"name\":\"Access logging\",\"attributes\":{\"status\":\"approved\",\"tags\":null}}]"));
    JsonObject res = update.getAsJsonArray("results").get(0).getAsJsonObject();
    assertEquals("Access log", res.getAsJsonObject("before").get("name").getAsString());
    assertEquals("Access logging", res.getAsJsonObject("after").get("name").getAsString());
    assertFalse(res.getAsJsonObject("after").getAsJsonObject("attributes").has("tags"), res.toString());
    JsonObject undo = call("undo");
    assertEquals(update.get("change").getAsInt(), undo.get("undone").getAsInt(), undo.toString());
    assertEquals("Access log", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
    assertEquals("undone", call("review_changes", "project", P).getAsJsonArray("changes").get(0).getAsJsonObject().get("state").getAsString());
    assertEquals(update.get("change").getAsInt(), call("redo").get("redone").getAsInt());
    assertEquals("Access logging", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
    // a move and a delete, undone
    call("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"move\",\"node\":\"/Requirements/SPEC/ENC\",\"parent\":\"/Requirements/SEC_ITSS\"},{\"op\":\"delete\",\"node\":\"/Requirements/SEC_ITSS/ENC\"}]"));
    callError("get_node", "project", P, "node", "/Requirements/SPEC/ENC");
    call("undo");
    call("get_node", "project", P, "node", "/Requirements/SPEC/ENC");
    assertContains(callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"move\",\"node\":\"/Requirements/SPEC\",\"parent\":\"/Requirements/SPEC/LOG\"}]")), "own subtree");

    // undo and redo know a change by its undo step, also when two changes have the same label
    int u1 = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"name\":\"n1\"}]"))
        .get("change").getAsInt();
    int u2 = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"name\":\"n2\"}]"))
        .get("change").getAsInt();
    assertEquals(u2, call("undo").get("undone").getAsInt());
    assertEquals(u1, call("undo").get("undone").getAsInt());
    assertEquals(u1, call("redo").get("redone").getAsInt());
    assertEquals("n1", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
    assertEquals("done", call("review_changes", "project", P, "change", u1).get("state").getAsString());
    assertEquals("undone", call("review_changes", "project", P, "change", u2).get("state").getAsString());
    call("undo");
    // parallel changes of one project run one after the other
    List<CompletableFuture<JsonObject>> parallel = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      String id = "PAR" + i;
      parallel.add(CompletableFuture.supplyAsync(() -> {
        try {
          return call("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"" + id + "\"}]"));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }, pool));
    }
    List<Integer> numbers = new ArrayList<>();
    for (CompletableFuture<JsonObject> f : parallel)
      numbers.add(f.get(120, TimeUnit.SECONDS).get("change").getAsInt());
    assertEquals(4, numbers.stream().distinct().count(), numbers.toString());
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"node\":\"/Requirements/PAR0\"},"
        + "{\"op\":\"delete\",\"node\":\"/Requirements/PAR1\"},{\"op\":\"delete\",\"node\":\"/Requirements/PAR2\"},"
        + "{\"op\":\"delete\",\"node\":\"/Requirements/PAR3\"}]"));

    // undo does not undo a change that is not one of the agent
    TreeDB db = Nodes.findDB("TS103096");
    TreeNode logNode = Core.node(db, "/Requirements/SPEC/LOG");
    Core.write(db, "A change in VS Code", () -> {
      logNode.setName("Changed elsewhere");
      logNode.commitAttributes();
      return null;
    });
    assertContains(callError("undo"), "is not a change of the agent");
    assertTrue(Display.getDefault().syncCall(UndoRedoMech::callUndo));
    assertEquals("Access logging", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
  }

  @Test
  @Order(7)
  void anyChangeCanBeRevertedLater() throws Exception {
    call("apply_changes", "project", P, "summary", "Revert fixture", "operations", ops("["
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"REV\",\"as\":\"rev\"},"
        + "{\"op\":\"create\",\"parent\":\"$rev\",\"id\":\"A\",\"name\":\"Alpha\",\"attributes\":{\"status\":\"draft\"},\"as\":\"a\"},"
        + "{\"op\":\"create\",\"parent\":\"$rev\",\"id\":\"B\",\"name\":\"Beta\"},"
        + "{\"op\":\"create\",\"parent\":\"$rev\",\"id\":\"C\",\"name\":\"Gamma\",\"description\":\"Gamma text.\",\"attributes\":{\"priority\":3},\"as\":\"c\"},"
        + "{\"op\":\"create\",\"parent\":\"$c\",\"type\":\"TestPurpose\",\"id\":\"C_T1\",\"name\":\"Check gamma\"},"
        + "{\"op\":\"link\",\"from\":\"$a\",\"to\":\"$c\",\"attribute\":\"depends\"}]"));
    // the undo of a delete brings the node back with the links to it
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"node\":\"/Requirements/REV/C\"}]"));
    assertFalse(call("get_node", "project", P, "node", "/Requirements/REV/A").getAsJsonObject("links").has("depends"));
    call("undo");
    assertEquals("/Requirements/REV/C", call("get_node", "project", P, "node", "/Requirements/REV/A").getAsJsonObject("links")
        .getAsJsonArray("depends").get(0).getAsString());
    JsonObject c0 = call("get_node", "project", P, "node", "/Requirements/REV/C");
    String cUuid = c0.get("uuid").getAsString();
    String tUuid = c0.getAsJsonArray("children").get(0).getAsJsonObject().get("uuid").getAsString();
    JsonObject change = call("apply_changes", "project", P, "summary", "Changes to revert", "operations", ops("["
        + "{\"op\":\"update\",\"node\":\"/Requirements/REV/A\",\"name\":\"Alpha 2\",\"attributes\":{\"status\":\"approved\",\"owner\":\"QA\"}},"
        + "{\"op\":\"move\",\"node\":\"/Requirements/REV/B\",\"parent\":\"/Requirements/SEC_ITSS\"},"
        + "{\"op\":\"link\",\"from\":\"/Requirements/REV/A\",\"to\":\"" + R12 + "\"},"
        + "{\"op\":\"locate\",\"requirement\":\"/Requirements/REV/A\",\"document\":\"/Documents/spec.xhtml\",\"quote\":\"keep logs for one year\"},"
        + "{\"op\":\"delete\",\"node\":\"/Requirements/REV/C\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"D\"}]"));
    int n = change.get("change").getAsInt();
    JsonObject brief = call("review_changes", "project", P, "last", 1).getAsJsonArray("changes").get(0).getAsJsonObject();
    assertEquals(n, brief.get("change").getAsInt());
    assertEquals(1, brief.getAsJsonObject("kinds").get("delete").getAsInt(), brief.toString());
    // a dry run tells what the revert does
    JsonObject dry = call("revert_change", "project", P, "change", n, "dry_run", true);
    assertTrue(dry.get("dry_run").getAsBoolean());
    assertContains(dry.getAsJsonArray("nodes").toString(), cUuid);
    call("get_node", "project", P, "node", "/Requirements/REV/D");

    JsonObject rev = call("revert_change", "project", P, "change", n);
    JsonObject a = call("get_node", "project", P, "node", "/Requirements/REV/A");
    assertEquals("Alpha", a.get("name").getAsString());
    assertEquals("draft", a.getAsJsonObject("attributes").getAsJsonObject("status").get("value").getAsString());
    assertFalse(a.getAsJsonObject("attributes").has("owner"), a.toString());
    assertEquals(0, a.getAsJsonArray("locations").size(), a.toString());
    assertFalse(a.getAsJsonObject("links").has("elaborates"), a.toString());
    // the deleted nodes come back with their UUIDs: the link to C resolves again
    assertEquals("/Requirements/REV/C", a.getAsJsonObject("links").getAsJsonArray("depends").get(0).getAsString(), a.toString());
    JsonObject c = call("get_node", "project", P, "node", "/Requirements/REV/C");
    assertEquals(cUuid, c.get("uuid").getAsString());
    assertEquals("Gamma text.", c.get("description").getAsString());
    assertEquals(3, c.getAsJsonObject("attributes").getAsJsonObject("priority").get("value").getAsInt());
    assertEquals(tUuid, c.getAsJsonArray("children").get(0).getAsJsonObject().get("uuid").getAsString());
    callError("get_node", "project", P, "node", "/Requirements/REV/D");
    JsonObject group = call("get_node", "project", P, "node", "/Requirements/REV");
    List<String> order = new ArrayList<>();
    for (JsonElement e : group.getAsJsonArray("children"))
      order.add(e.getAsJsonObject().get("id").getAsString());
    assertEquals(List.of("A", "B", "C"), order);
    assertEquals("reverted", call("review_changes", "project", P, "change", n).get("state").getAsString());
    assertContains(callError("revert_change", "project", P, "change", n), "reverted already");

    // a revert of the revert makes the change again, with the same nodes
    call("revert_change", "project", P, "change", rev.get("change").getAsInt());
    assertEquals("Alpha 2", call("get_node", "project", P, "node", "/Requirements/REV/A").get("name").getAsString());
    callError("get_node", "project", P, "node", "/Requirements/REV/C");
    assertEquals("done", call("review_changes", "project", P, "change", n).get("state").getAsString());
    assertEquals("reverted", call("review_changes", "project", P, "change", rev.get("change").getAsInt()).get("state").getAsString());

    // a node of the change was changed later: the revert is refused and lists it; force reverts anyway
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/REV/A\",\"name\":\"Alpha 3\"}]"));
    String conflict = callError("revert_change", "project", P, "change", n);
    assertContains(conflict, "cannot be reverted");
    assertContains(conflict, "/Requirements/REV/A was changed later (attributes name)");
    assertContains(callError("revert_change", "project", P, "change", n, "dry_run", true), "was changed later");
    assertEquals("Alpha 3", call("get_node", "project", P, "node", "/Requirements/REV/A").get("name").getAsString());
    call("revert_change", "project", P, "change", n, "force", true);
    assertEquals("Alpha", call("get_node", "project", P, "node", "/Requirements/REV/A").get("name").getAsString());
    assertEquals(cUuid, call("get_node", "project", P, "node", "/Requirements/REV/C").get("uuid").getAsString());
    assertEquals("/Requirements/REV/B", call("get_node", "project", P, "node", "/Requirements/REV/B").get("path").getAsString());

    // an undone change is not reverted
    int e = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"E\"}]"))
        .get("change").getAsInt();
    call("undo");
    assertContains(callError("revert_change", "project", P, "change", e), "is undone");
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"node\":\"/Requirements/REV\"}]"));
    // the history of the catalog is in the storage of the server
    File journals = new File(storage, "journals");
    File[] dirs = journals.listFiles();
    assertTrue(dirs != null && dirs.length >= 1, "journals");
    assertTrue(new File(dirs[0], "journal.jsonl").isFile());
    assertTrue(new File(dirs[0], "changes/" + n + ".json").isFile());
  }

  @Test
  @Order(8)
  void theCoverageIsData() throws Exception {
    JsonObject cov = call("trace_coverage", "project", P, "under", "/Requirements/SPEC", "refresh", true);
    JsonObject reqs = cov.getAsJsonObject("requirements");
    assertEquals(4, reqs.get("total").getAsInt(), cov.toString());
    assertEquals(3, reqs.get("leaves").getAsInt(), cov.toString());
    assertEquals(0, reqs.getAsJsonObject("leaves_without_locations").get("total").getAsInt(), cov.toString());
    assertTrue(reqs.getAsJsonObject("leaves_without_test_purposes").toString().contains("/Requirements/SPEC/ENC"), cov.toString());
    JsonObject spec = null;
    for (JsonElement e : cov.getAsJsonArray("documents"))
      if (e.getAsJsonObject().get("document").getAsString().equals("/Documents/spec.xhtml"))
        spec = e.getAsJsonObject();
    assertEquals(2, spec.get("uncovered_paragraphs").getAsInt(), spec.toString());
    assertEquals(2, spec.get("covered_paragraphs").getAsInt(), spec.toString());
    // LOCKOUT marks a part of its sentence
    assertEquals(1, spec.get("partly_covered_paragraphs").getAsInt(), spec.toString());
  }

  @Test
  @Order(9)
  void theTestCoverageComesFromACoverageFile() throws Exception {
    File xml = new File(root, "coverage.xml");
    Files.writeString(xml.toPath(), "<?xml version=\"1.0\"?><coverage>"
        + "<reqcoverage qid=\"/Requirements/SPEC/LOG\"><covered_by uri=\"tests/log_test.py\" label=\"test_log\" hits=\"3\"/>"
        + "<covered_by uri=\"tests/audit_test.py\" label=\"test_audit\" hits=\"1\"/></reqcoverage>"
        + "<reqcoverage qid=\"/Requirements/GONE\"><covered_by uri=\"tests/gone.py\" label=\"test_gone\"/></reqcoverage>"
        + "<error name=\"enc_fail\" testuri=\"tests/enc_test.py\"><violates qid=\"/Requirements/SPEC/ENC\"/>"
        + "<description format=\"plain\">AES key is too short</description></error></coverage>");
    JsonObject tc = call("test_coverage", "project", P, "file", xml.getAbsolutePath(), "under", "/Requirements/SPEC");
    assertEquals(3, tc.get("leaves").getAsInt(), tc.toString());
    JsonObject log = tc.getAsJsonObject("covered").getAsJsonArray("items").get(0).getAsJsonObject();
    assertEquals("/Requirements/SPEC/LOG", log.get("path").getAsString());
    assertEquals(2, log.get("tests").getAsInt());
    assertEquals(4, log.get("hits").getAsInt());
    assertEquals(2, tc.getAsJsonObject("uncovered").get("total").getAsInt(), tc.toString());
    JsonObject fail = tc.getAsJsonObject("failures").getAsJsonArray("items").get(0).getAsJsonObject();
    assertEquals("AES key is too short", fail.get("description").getAsString());
    assertEquals("/Requirements/SPEC/ENC", fail.getAsJsonArray("violates").get(0).getAsString());
    assertEquals("/Requirements/GONE", tc.getAsJsonObject("unknown_requirements").getAsJsonArray("items").get(0).getAsString());
    assertContains(callError("test_coverage", "project", P, "file", new File(root, "spec.html").getAbsolutePath()), "is no coverage file");
  }

  @Test
  @Order(10)
  void aNewSpecificationVersionKeepsTheTraceability() throws Exception {
    File v2 = new File(root, "spec_v2.html");
    Files.writeString(v2.toPath(), "<html><body><h1>Spec</h1><p>The system shall log every access.</p>"
        + "<p>The system shall encrypt all stored data with AES.</p><p>The system shall keep logs for two years.</p>"
        + "<p>The system may lock the account after 5 failed attempts.</p>"
        + "<p>The system must alert the operator.</p><p>Система должна хранить журнал.</p></body></html>", StandardCharsets.UTF_8);
    JsonObject up = call("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "file", v2.getAbsolutePath());
    assertEquals("/Documents/spec_v2.xhtml", up.get("new_document").getAsString(), up.toString());
    Map<String, JsonObject> byReq = new java.util.HashMap<>();
    for (JsonElement e : up.getAsJsonArray("requirements"))
      byReq.put(e.getAsJsonObject().get("requirement").getAsString(), e.getAsJsonObject());
    assertEquals("unchanged", byReq.get("/Requirements/SPEC/LOG").get("status").getAsString(), up.toString());
    // the sentence of ENC changed: its location moves to the most similar sentence, whole, with the old text to compare
    JsonObject enc = byReq.get("/Requirements/SPEC/ENC");
    assertEquals("changed", enc.get("status").getAsString(), up.toString());
    JsonObject loc = enc.getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("The system shall encrypt stored data.", loc.getAsJsonArray("old_text").get(0).getAsString(), up.toString());
    assertEquals("", enc.get("description").getAsString(), "ENC has no own description: " + up);
    assertEquals("The system shall encrypt all stored data with AES.", loc.get("new_text").getAsString(), up.toString());
    assertTrue(loc.get("similarity").getAsDouble() >= 0.6, up.toString());
    assertFalse(loc.has("modality_changed"), up.toString());
    // shall became may and 3 became 5: the flags tell it
    JsonObject lock = byReq.get("/Requirements/SPEC/LOCKOUT").getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("changed", lock.get("status").getAsString(), up.toString());
    assertTrue(lock.get("modality_changed").getAsBoolean(), up.toString());
    assertTrue(lock.get("numbers_changed").getAsBoolean(), up.toString());
    // the new statements no requirement marks, in English and Russian
    List<String> quotes = new ArrayList<>();
    for (JsonElement e : up.getAsJsonArray("uncovered_statements"))
      quotes.add(e.getAsJsonObject().get("quote").getAsString());
    assertEquals(List.of("The system shall keep logs for two years.", "The system must alert the operator.",
        "Система должна хранить журнал."), quotes);
    JsonObject newDoc = call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml");
    assertTrue(paragraph(newDoc, "log every access").getAsJsonArray("requirements").toString().contains("/Requirements/SPEC/LOG"),
        newDoc.toString());
    assertEquals("full", paragraph(newDoc, "encrypt all stored").get("coverage").getAsString(), newDoc.toString());
    // detach_old by default: the requirements trace to the new version only; the old document stays
    JsonArray logLocs = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations");
    assertEquals(1, logLocs.size(), logLocs.toString());
    assertEquals("/Documents/spec_v2.xhtml", logLocs.get(0).getAsJsonObject().get("document").getAsString());
    call("get_node", "project", P, "node", "/Documents/spec.xhtml");
    // the same update again is refused
    assertContains(callError("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "file", v2.getAbsolutePath()),
        "exists already");
    assertContains(callError("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "new_document",
        "/Documents/spec_v2.xhtml"), "has locations already");
    // the import and the update are one change: one undo step
    JsonObject undone = call("undo");
    assertEquals(up.get("change").getAsInt(), undone.get("undone").getAsInt(), undone.toString());
    assertEquals("Update document version", undone.get("label").getAsString());
    callError("get_node", "project", P, "node", "/Documents/spec_v2.xhtml");
    assertEquals("/Documents/spec.xhtml", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations")
        .get(0).getAsJsonObject().get("document").getAsString());
    call("redo");
    assertEquals(3, call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml").get("locations").getAsInt());
    // a revert of the update (as in a later session): the new version goes, the old locations come back
    call("revert_change", "project", P, "change", up.get("change").getAsInt());
    callError("get_node", "project", P, "node", "/Documents/spec_v2.xhtml");
    JsonArray back = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations");
    assertEquals("/Documents/spec.xhtml", back.get(0).getAsJsonObject().get("document").getAsString(), back.toString());
    // the undo of the revert brings the new version with its three locations back
    call("undo");
    assertEquals(3, call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml").get("locations").getAsInt());
    // a review item of an update made in the GUI is resolved like Complete of the To-Do List view
    assertContains(callError("apply_changes", "project", P, "operations", ops("[{\"op\":\"resolve\",\"item\":\"/Requirements/SPEC/LOG\"}]")),
        "not a review item");
  }

  @Test
  @Order(12)
  void reportsAndProjects() throws Exception {
    JsonArray templates = call("list_report_templates", "project", P).getAsJsonArray("templates");
    assertTrue(templates.toString().contains("com.unitesk.requality.reports.tableview"), templates.toString());
    assertTrue(templates.toString().contains("\"type\":\"boolean\""), templates.toString());
    File out = new File(root, "report");
    assertContains(callError("generate_report", "project", P, "template", "com.unitesk.requality.reports.tableview", "output",
        out.getAbsolutePath(), "parameters", JsonParser.parseString("{\"no_such\":1}")), "has no parameter no_such");
    JsonObject rep = call("generate_report", "project", P, "template", "com.unitesk.requality.reports.tableview", "output",
        out.getAbsolutePath(), "root", "/Requirements/SEC_ITSS/SEC_ITSS_RCV");
    JsonArray files = rep.getAsJsonArray("files");
    assertTrue(files.size() >= 1, rep.toString());
    for (JsonElement f : files)
      assertTrue(new File(f.getAsString()).toPath().startsWith(out.getCanonicalFile().toPath()), rep.toString());
    assertTrue(Files.size(new File(files.get(0).getAsString()).toPath()) > 1000, rep.toString());
    // the temporary report settings are gone and not in the undo history
    assertEquals(1, call("get_tree", "project", P, "node", "/Reports", "depth", 1).getAsJsonArray("nodes").size());
    assertEquals("AgentProject", call("create_project", "name", "AgentProject").get("project").getAsString());
    assertContains(callError("create_project", "name", "AgentProject"), "exists already");
    call("apply_changes", "project", "AgentProject", "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"R1\",\"name\":\"First\"}]"));
    assertEquals("First", call("get_node", "project", "AgentProject", "node", "/Requirements/R1").get("name").getAsString());
    // a closed project is released and opens again
    call("close_project", "project", "AgentProject");
    assertEquals("closed", state("AgentProject"));
    assertContains(callError("get_node", "project", "AgentProject", "node", "/Requirements/R1"), "closed");
    call("open_project", "project", "AgentProject");
    assertEquals("First", call("get_node", "project", "AgentProject", "node", "/Requirements/R1").get("name").getAsString());
  }
}
