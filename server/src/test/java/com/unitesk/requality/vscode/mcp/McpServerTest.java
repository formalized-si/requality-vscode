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
    assertEquals(List.of("list_projects", "get_tree", "get_node", "search_nodes", "read_document", "apply_changes", "import_document",
        "update_document_version", "trace_coverage", "review_changes", "revert_change", "undo", "redo", "list_report_templates", "generate_report",
        "open_project", "create_project", "server_messages"), names);
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
    JsonObject wrong = callError("read_document", "project", P, "node", "/Documents/x.xhtml");
    assertTrue(wrong.get("error").getAsString().startsWith("Unknown argument node; read_document takes the arguments project, document"),
        wrong.toString());
    JsonObject absent = callError("read_document", "project", P);
    assertTrue(absent.get("error").getAsString().startsWith("Missing argument document (The document: "), absent.toString());
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
    assertTrue(childPaths(tree).containsAll(List.of("/Documents", "/Requirements", "/Reports")), tree.toString());
    // the old catalog format was converted when it opened (the message the core showed is kept)
    assertTrue(call("server_messages").toString().contains("Conversion of"), "conversion message");
    JsonObject r12 = call("get_node", "project", P, "node", R12);
    assertEquals("Requirement", r12.get("type").getAsString());
    assertEquals(2, r12.getAsJsonArray("locations").size(), r12.toString());
    JsonObject found = call("search_nodes", "project", P, "text", "AID-SSP", "type", "Requirement");
    assertTrue(paths(found.getAsJsonArray("matches")).contains(R12), found.toString());
    // a document lists its locations as a count: read_document shows them with their text
    JsonObject docNode = call("get_node", "project", P, "node", "/Documents/ts_103097v010201p.xhtml");
    assertEquals(131, docNode.get("locations_count").getAsInt(), docNode.toString());
    assertEquals(0, docNode.getAsJsonArray("children").size(), docNode.toString());
    JsonObject docs = call("get_tree", "project", P, "node", "/Documents", "depth", 3);
    JsonObject etsiDoc = docs.getAsJsonArray("nodes").get(0).getAsJsonObject();
    assertEquals(131, etsiDoc.get("locations").getAsInt(), docs.toString());
    assertFalse(etsiDoc.has("nodes"), "the locations of a document are a number: " + docs);
    JsonObject missing = callError("get_node", "project", P, "node", "/Requirements/NoSuchNode");
    assertTrue(missing.get("error").getAsString().startsWith("No node"), missing.toString());
    // the paragraphs of the ETSI document: the existing locations cover the text they mark
    JsonObject etsi = call("read_document", "project", P, "document", "/Documents/ts_103097v010201p.xhtml", "count", 5000);
    JsonObject itsAid = paragraph(etsi, "For each ITS-AID only one ItsAidSsp shall be used.");
    assertTrue(itsAid != null && itsAid.getAsJsonArray("requirements").toString().contains(R12), String.valueOf(itsAid));
    assertTrue(etsi.get("covered_text_percent").getAsDouble() > 10, etsi.get("covered_text_percent").toString());
  }

  @Test
  @Order(3)
  void anAgentTurnsADocumentIntoRequirements() throws Exception {
    File html = new File(root, "spec.html");
    Files.writeString(html.toPath(), "<html><body><h1>Spec</h1><p>The system shall log every access.</p>"
        + "<p>The system shall encrypt\n   stored data.</p><p>The system shall keep logs for one year.</p></body></html>");
    JsonObject doc = call("import_document", "project", P, "file", html.getAbsolutePath());
    assertEquals("/Documents/spec.xhtml", doc.get("path").getAsString(), doc.toString());
    assertEquals(4, doc.get("paragraphs").getAsInt(), doc.toString());
    JsonObject read = call("read_document", "project", P, "document", "/Documents/spec.xhtml");
    assertEquals("none", paragraph(read, "log every access").get("coverage").getAsString());
    int encrypt = paragraph(read, "encrypt").get("p").getAsInt();

    // a check changes nothing
    JsonObject dry = call("apply_changes", "project", P, "dry_run", true, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"DRY\"}]"));
    assertTrue(dry.get("dry_run").getAsBoolean());
    callError("get_node", "project", P, "node", "/Requirements/DRY");
    // all operations or none: the last one fails, the first one is not made
    JsonObject failed = callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"HALF\"},{\"op\":\"delete\",\"node\":\"/Requirements\"}]"));
    assertTrue(failed.get("error").getAsString().startsWith("Operation 1 (delete)"), failed.toString());
    callError("get_node", "project", P, "node", "/Requirements/HALF");

    JsonObject attrs = JsonParser.parseString("{\"priority\":2,\"status\":\"draft\",\"tags\":[\"a\",\"b\"]}").getAsJsonObject();
    JsonArray batch = ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"SPEC\",\"name\":\"Specification\",\"as\":\"spec\"},"
        + "{\"op\":\"create\",\"parent\":\"$spec\",\"id\":\"LOG\",\"name\":\"Access log\",\"description\":\"First line.\\n\\nSecond paragraph.\",\"as\":\"log\"},"
        + "{\"op\":\"create\",\"parent\":\"$spec\",\"id\":\"ENC\",\"name\":\"Encryption\",\"as\":\"enc\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$log\",\"document\":\"/Documents/spec.xhtml\",\"quote\":\"The system shall log every access.\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$enc\",\"document\":\"/Documents/spec.xhtml\",\"paragraph\":" + encrypt + "},"
        + "{\"op\":\"link\",\"from\":\"$enc\",\"to\":\"" + R12 + "\"},"
        + "{\"op\":\"create\",\"parent\":\"$log\",\"type\":\"TestPurpose\",\"name\":\"Check the log\"}]");
    batch.get(1).getAsJsonObject().add("attributes", attrs);
    JsonObject applied = call("apply_changes", "project", P, "summary", "Requirements of spec.html", "operations", batch);
    assertEquals(7, applied.getAsJsonArray("results").size(), applied.toString());
    JsonObject log = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG");
    assertEquals("First line.\n\nSecond paragraph.", log.get("description").getAsString());
    assertEquals(2, log.getAsJsonObject("attributes").getAsJsonObject("priority").get("value").getAsInt());
    assertEquals("The system shall log every access.", log.getAsJsonArray("locations").get(0).getAsJsonObject().get("text").getAsString());
    JsonObject enc = call("get_node", "project", P, "node", "/Requirements/SPEC/ENC");
    assertEquals("The system shall encrypt stored data.", enc.getAsJsonArray("locations").get(0).getAsJsonObject().get("text").getAsString());
    assertEquals(R12, enc.getAsJsonObject("links").getAsJsonArray("elaborates").get(0).getAsString(), enc.toString());
    // the text no requirement covers yet
    JsonObject uncovered = call("read_document", "project", P, "document", "/Documents/spec.xhtml", "filter", "uncovered");
    List<String> left = new ArrayList<>();
    for (JsonElement e : uncovered.getAsJsonArray("paragraphs"))
      left.add(e.getAsJsonObject().get("text").getAsString());
    assertEquals(List.of("Spec", "The system shall keep logs for one year."), left);
  }

  @Test
  @Order(4)
  void thePersonReviewsAndUndoesTheChanges() throws Exception {
    JsonObject review = call("review_changes", "project", P);
    JsonObject last = review.getAsJsonArray("changes").get(0).getAsJsonObject();
    assertEquals("apply_changes", last.get("tool").getAsString());
    assertEquals("Requirements of spec.html", last.get("summary").getAsString());
    assertEquals("done", last.get("state").getAsString());
    JsonObject update = call("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"name\":\"Access logging\",\"attributes\":{\"status\":\"approved\",\"tags\":null}}]"));
    JsonObject res = update.getAsJsonArray("results").get(0).getAsJsonObject();
    assertEquals("Access log", res.getAsJsonObject("before").get("name").getAsString());
    assertEquals("Access logging", res.getAsJsonObject("after").get("name").getAsString());
    assertFalse(res.getAsJsonObject("after").getAsJsonObject("attributes").has("tags"), res.toString());
    JsonObject undo = call("undo");
    assertEquals(update.get("change").getAsInt(), undo.get("change").getAsInt(), undo.toString());
    assertEquals("Access log", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
    assertEquals("undone", call("review_changes", "project", P).getAsJsonArray("changes").get(0).getAsJsonObject().get("state").getAsString());
    call("redo");
    assertEquals("Access logging", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
    // a move and a delete, undone
    call("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"move\",\"node\":\"/Requirements/SPEC/ENC\",\"parent\":\"/Requirements/SEC_ITSS\"},{\"op\":\"delete\",\"node\":\"/Requirements/SEC_ITSS/ENC\"}]"));
    callError("get_node", "project", P, "node", "/Requirements/SPEC/ENC");
    call("undo");
    call("get_node", "project", P, "node", "/Requirements/SPEC/ENC");
    JsonObject cyc = callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"move\",\"node\":\"/Requirements/SPEC\",\"parent\":\"/Requirements/SPEC/LOG\"}]"));
    assertTrue(cyc.get("error").getAsString().contains("own subtree"), cyc.toString());
  }

  @Test
  @Order(5)
  void anyChangeCanBeRevertedLater() throws Exception {
    call("apply_changes", "project", P, "summary", "Revert fixture", "operations", ops("["
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"REV\",\"as\":\"rev\"},"
        + "{\"op\":\"create\",\"parent\":\"$rev\",\"id\":\"A\",\"name\":\"Alpha\",\"attributes\":{\"status\":\"draft\"}},"
        + "{\"op\":\"create\",\"parent\":\"$rev\",\"id\":\"B\",\"name\":\"Beta\"},"
        + "{\"op\":\"create\",\"parent\":\"$rev\",\"id\":\"C\",\"name\":\"Gamma\",\"description\":\"Gamma text.\",\"attributes\":{\"priority\":3},\"as\":\"c\"},"
        + "{\"op\":\"create\",\"parent\":\"$c\",\"type\":\"TestPurpose\",\"id\":\"C_T1\",\"name\":\"Check gamma\"}]"));
    JsonObject change = call("apply_changes", "project", P, "summary", "Changes to revert", "operations", ops("["
        + "{\"op\":\"update\",\"node\":\"/Requirements/REV/A\",\"name\":\"Alpha 2\",\"attributes\":{\"status\":\"approved\",\"owner\":\"QA\"}},"
        + "{\"op\":\"move\",\"node\":\"/Requirements/REV/B\",\"parent\":\"/Requirements/SEC_ITSS\"},"
        + "{\"op\":\"link\",\"from\":\"/Requirements/REV/A\",\"to\":\"" + R12 + "\"},"
        + "{\"op\":\"locate\",\"requirement\":\"/Requirements/REV/A\",\"document\":\"/Documents/spec.xhtml\",\"quote\":\"keep logs for one year\"},"
        + "{\"op\":\"delete\",\"node\":\"/Requirements/REV/C\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"D\"}]"));
    int n = change.get("change").getAsInt();
    // the saved values for a revert are not part of the review
    assertFalse(call("review_changes", "project", P, "last", 1).toString().contains("\"raw\""));
    JsonObject rev = call("revert_change", "change", n);
    for (JsonElement e : rev.getAsJsonArray("operations"))
      assertTrue(e.getAsJsonObject().get("reverted").getAsBoolean(), rev.toString());
    JsonObject a = call("get_node", "project", P, "node", "/Requirements/REV/A");
    assertEquals("Alpha", a.get("name").getAsString());
    assertEquals("draft", a.getAsJsonObject("attributes").getAsJsonObject("status").get("value").getAsString());
    assertFalse(a.getAsJsonObject("attributes").has("owner"), a.toString());
    assertEquals(0, a.getAsJsonArray("locations").size(), a.toString());
    assertFalse(a.getAsJsonObject("links").has("elaborates"), a.toString());
    JsonObject c = call("get_node", "project", P, "node", "/Requirements/REV/C");
    assertEquals("Gamma text.", c.get("description").getAsString());
    assertEquals(3, c.getAsJsonObject("attributes").getAsJsonObject("priority").get("value").getAsInt());
    assertEquals("C_T1", c.getAsJsonArray("children").get(0).getAsJsonObject().get("id").getAsString());
    JsonObject group = call("get_node", "project", P, "node", "/Requirements/REV");
    List<String> order = new ArrayList<>();
    for (JsonElement e : group.getAsJsonArray("children"))
      order.add(e.getAsJsonObject().get("id").getAsString());
    assertEquals(List.of("A", "B", "C"), order);
    JsonObject review = call("review_changes", "project", P, "last", 2);
    assertEquals("reverted", review.getAsJsonArray("changes").get(1).getAsJsonObject().get("state").getAsString(), review.toString());
    JsonObject again = callError("revert_change", "change", n);
    assertTrue(again.get("error").getAsString().contains("reverted already"), again.toString());
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"node\":\"/Requirements/REV\"}]"));
  }

  @Test
  @Order(6)
  void theCoverageIsData() throws Exception {
    JsonObject cov = call("trace_coverage", "project", P, "under", "/Requirements/SPEC", "refresh", true);
    JsonObject reqs = cov.getAsJsonObject("requirements");
    assertEquals(3, reqs.get("total").getAsInt(), cov.toString());
    assertEquals(2, reqs.get("leaves").getAsInt(), cov.toString());
    assertEquals(0, reqs.getAsJsonObject("leaves_without_locations").get("total").getAsInt(), cov.toString());
    assertTrue(reqs.getAsJsonObject("leaves_without_test_purposes").toString().contains("/Requirements/SPEC/ENC"), cov.toString());
    JsonObject spec = null;
    for (JsonElement e : cov.getAsJsonArray("documents"))
      if (e.getAsJsonObject().get("document").getAsString().equals("/Documents/spec.xhtml"))
        spec = e.getAsJsonObject();
    assertEquals(2, spec.get("uncovered_paragraphs").getAsInt(), spec.toString());
    assertEquals(2, spec.get("covered_paragraphs").getAsInt(), spec.toString());
  }

  @Test
  @Order(7)
  void aNewSpecificationVersionKeepsTheTraceability() throws Exception {
    File v2 = new File(root, "spec_v2.html");
    Files.writeString(v2.toPath(), "<html><body><h1>Spec</h1><p>The system shall log every access.</p>"
        + "<p>The system shall encrypt all stored data with AES.</p><p>The system shall keep logs for two years.</p>"
        + "<p>The system must alert the operator.</p></body></html>");
    JsonObject up = call("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "file", v2.getAbsolutePath(),
        "detach_old", true);
    assertEquals("/Documents/spec_v2.xhtml", up.get("new_document").getAsString(), up.toString());
    Map<String, JsonObject> byReq = new java.util.HashMap<>();
    for (JsonElement e : up.getAsJsonArray("requirements"))
      byReq.put(e.getAsJsonObject().get("requirement").getAsString(), e.getAsJsonObject());
    assertEquals("unchanged", byReq.get("/Requirements/SPEC/LOG").get("status").getAsString(), up.toString());
    // the sentence of ENC changed: its location moves to the most similar sentence, whole, with the old text to compare
    JsonObject enc = byReq.get("/Requirements/SPEC/ENC");
    assertEquals("changed", enc.get("status").getAsString(), up.toString());
    assertEquals("/Requirements/SPEC/ENC", up.getAsJsonArray("requirements").get(0).getAsJsonObject().get("requirement").getAsString(),
        "the ones to review come first: " + up);
    JsonObject loc = enc.getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("The system shall encrypt stored data.", loc.getAsJsonArray("old_text").get(0).getAsString(), up.toString());
    assertEquals("", enc.get("description").getAsString(), "ENC has no own description: " + up);
    assertEquals("The system shall encrypt all stored data with AES.", loc.get("new_text").getAsString(), up.toString());
    assertTrue(loc.get("similarity").getAsDouble() >= 0.6, up.toString());
    // the new statements no requirement marks
    JsonArray fresh = up.getAsJsonArray("uncovered_statements");
    List<String> quotes = new ArrayList<>();
    for (JsonElement e : fresh)
      quotes.add(e.getAsJsonObject().get("quote").getAsString());
    assertEquals(List.of("The system shall keep logs for two years.", "The system must alert the operator."), quotes);
    JsonObject newDoc = call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml");
    assertTrue(paragraph(newDoc, "log every access").getAsJsonArray("requirements").toString().contains("/Requirements/SPEC/LOG"),
        newDoc.toString());
    assertEquals("full", paragraph(newDoc, "encrypt all stored").get("coverage").getAsString(), newDoc.toString());
    // detach_old: the requirements trace to the new version only; the old document stays
    JsonArray logLocs = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations");
    assertEquals(1, logLocs.size(), logLocs.toString());
    assertEquals("/Documents/spec_v2.xhtml", logLocs.get(0).getAsJsonObject().get("document").getAsString());
    call("get_node", "project", P, "node", "/Documents/spec.xhtml");
    // the update is one undo step
    String undone = call("undo").get("undone").getAsString();
    assertEquals("Update document version", undone);
    assertEquals(0, call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml").get("locations").getAsInt());
    call("redo");
    // a revert of the update (as in a later session): the new locations go, the old ones come back
    JsonObject rev = call("revert_change", "change", up.get("change").getAsInt());
    assertTrue(rev.getAsJsonArray("operations").get(0).getAsJsonObject().get("reverted").getAsBoolean(), rev.toString());
    JsonArray back = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations");
    assertEquals("/Documents/spec.xhtml", back.get(0).getAsJsonObject().get("document").getAsString(), back.toString());
    assertEquals(0, call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml").get("locations").getAsInt());
    // the undo of the revert brings the two new locations back (LOG and ENC)
    call("undo");
    assertEquals(2, call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml").get("locations").getAsInt());
    // a review item of an update made in the GUI is resolved like Complete of the To-Do List view
    JsonObject item = callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"resolve\",\"item\":\"/Requirements/SPEC/LOG\"}]"));
    assertTrue(item.get("error").getAsString().contains("not a review item"), item.toString());
  }

  @Test
  @Order(8)
  void reportsAndProjects() throws Exception {
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
    assertEquals("AgentProject", call("create_project", "name", "AgentProject").get("project").getAsString());
    call("apply_changes", "project", "AgentProject", "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"R1\",\"name\":\"First\"}]"));
    assertEquals("First", call("get_node", "project", "AgentProject", "node", "/Requirements/R1").get("name").getAsString());
  }
}
