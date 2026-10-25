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
  static Core core;
  static File workspace;
  static final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
  /** The responses no test waits for by id (errors with a null id, responses of raw lines). */
  static final BlockingQueue<JsonObject> others = new LinkedBlockingQueue<>();
  static final AtomicInteger ids = new AtomicInteger();

  @BeforeAll
  static void start() throws Exception {
    // the catalog files are compared with the tree before each call: the server writes nothing a call takes for an
    // outside change
    System.setProperty("requality.diskWatch", "0");
    ScriptSandbox.install();
    root = Files.createTempDirectory("requality-mcp-test").toFile();
    workspace = new File(root, "workspace");
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
    core = RequalityTools.register(mcp, headless, new Policy(List.of(workspace, root)));
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
          JsonElement parsed = JsonParser.parseString(line);
          if (parsed.isJsonArray()) {
            // the response to a batch
            JsonObject batch = new JsonObject();
            batch.add("batch", parsed);
            others.add(batch);
            continue;
          }
          JsonObject m = parsed.getAsJsonObject();
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
    // before initialize the server answers only initialize and ping
    assertEquals(-32002, rpc("tools/list", null).getAsJsonObject("error").get("code").getAsInt());
    assertEquals(0, rpc("ping", null).getAsJsonObject("result").size());
    JsonObject badVersion = new JsonObject();
    badVersion.addProperty("protocolVersion", 5);
    assertEquals(-32602, rpc("initialize", badVersion).getAsJsonObject("error").get("code").getAsInt());
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
      if (List.of("undo", "redo", "revert_change", "apply_changes", "convert_project", "update_document_version",
          "change_project_settings").contains(name))
        assertTrue(an.get("destructiveHint").getAsBoolean(), o.toString());
      if (name.equals("apply_changes")) {
        JsonArray variants = o.getAsJsonObject("inputSchema").getAsJsonObject("properties").getAsJsonObject("operations")
            .getAsJsonObject("items").getAsJsonArray("oneOf");
        assertEquals(12, variants.size(), "one schema for each operation");
        // the keys each operation needs, and a description of each key
        JsonObject locate = variants.get(9).getAsJsonObject();
        assertEquals("locate", locate.getAsJsonObject("properties").getAsJsonObject("op").get("const").getAsString());
        assertEquals("[\"op\",\"requirement\",\"document\"]", locate.getAsJsonArray("required").toString());
        assertTrue(locate.getAsJsonObject("properties").getAsJsonObject("quote").has("description"), locate.toString());
      }
    }
    assertEquals(List.of("list_projects", "get_tree", "get_node", "search_nodes", "read_document", "apply_changes", "import_document",
        "import_reqif", "update_document_version", "trace_coverage", "test_coverage", "review_changes", "compare_with_revision", "revert_change", "undo",
        "redo", "get_project_settings", "change_project_settings", "list_report_templates", "generate_report", "open_project",
        "close_project", "convert_project", "create_project", "server_messages"), names);
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
    JsonObject badArgs = new JsonObject();
    badArgs.addProperty("name", "list_projects");
    badArgs.add("arguments", JsonParser.parseString("[1,2]"));
    assertEquals(-32602, rpc("tools/call", badArgs).getAsJsonObject("error").get("code").getAsInt());
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
    // a batch gets one array with the responses (protocol 2025-03-26; the later versions have no batches)
    JsonObject older = new JsonObject();
    older.addProperty("protocolVersion", "2025-03-26");
    rpc("initialize", older);
    String batchLine = "[{\"jsonrpc\":\"2.0\",\"id\":9101,\"method\":\"ping\"},{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},"
        + "{\"jsonrpc\":\"2.0\",\"id\":9102,\"method\":\"no/such\"}]";
    send(batchLine);
    JsonArray batch = others.poll(30, TimeUnit.SECONDS).getAsJsonArray("batch");
    assertEquals(2, batch.size(), batch.toString());
    assertEquals(9101, batch.get(0).getAsJsonObject().get("id").getAsInt());
    assertEquals(-32601, batch.get(1).getAsJsonObject().getAsJsonObject("error").get("code").getAsInt());
    JsonObject newer = new JsonObject();
    newer.addProperty("protocolVersion", "2025-06-18");
    rpc("initialize", newer);
    send(batchLine);
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
    // a secret outside the folders of the server
    File secret = Files.createTempFile("requality-secret", ".txt").toFile();
    secret.deleteOnExit();
    Files.writeString(secret.toPath(), "TOP-SECRET-TOKEN-12345");
    // an HTML document whose references the importer would copy or download is refused, with the references
    File refs = new File(root, "refs.html");
    Files.writeString(refs.toPath(), "<html><body><p>The system shall work.</p><img src=\"" + secret.getAbsolutePath() + "\"/>"
        + "<img src=\"http://127.0.0.1:1/x.png\"/><img alt=\"Figure 1\" src=\"data:image/png;base64,AA==\"/></body></html>");
    String r = callError("import_document", "project", P, "file", refs.getAbsolutePath());
    assertContains(r, secret.getAbsolutePath());
    assertContains(r, "http://127.0.0.1:1/x.png");
    assertFalse(r.contains("data:image"), r);
    // a picture next to the document is fine
    File pics = new File(root, "pics");
    pics.mkdirs();
    Files.write(new File(pics, "a.png").toPath(), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
    File withPic = new File(pics, "pic.html");
    Files.writeString(withPic.toPath(), "<html><body><p>The system shall show a picture.</p><img src=\"a.png\" alt=\"a\"/></body></html>");
    JsonObject pic = call("import_document", "project", P, "file", withPic.getAbsolutePath());
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"" + pic.get("path").getAsString() + "\"}]"));
    // the XFA form of a PDF with an external entity does not read the secret (Tika 3)
    File pdf = new File(root, "form.pdf");
    Files.write(pdf.toPath(), xfaPdf(secret));
    JsonObject form = call("import_document", "project", P, "file", pdf.getAbsolutePath());
    JsonObject formText = call("read_document", "project", P, "document", form.get("path").getAsString());
    assertContains(formText.toString(), "The system shall work.");
    assertFalse(formText.toString().contains("TOP-SECRET"), formText.toString());
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"" + form.get("path").getAsString() + "\"}]"));
    // a coverage file with a DOCTYPE is not read (no external entity)
    File xxe = new File(root, "xxe-coverage.xml");
    Files.writeString(xxe.toPath(), "<?xml version=\"1.0\"?><!DOCTYPE c [<!ENTITY s SYSTEM \"" + secret.toURI() + "\">]>"
        + "<coverage><error name=\"e\" testuri=\"t\"><violates qid=\"/Requirements\"/><description format=\"plain\">&s;</description>"
        + "</error></coverage>");
    String cov = callError("test_coverage", "project", P, "coverage_file", xxe.getAbsolutePath());
    assertFalse(cov.contains("TOP-SECRET"), cov);
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
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"HALF\"},{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements\"}]"))
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
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/LX\"},{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/LZ\"}]"));
    // a requirement of the legacy catalog whose location reference has the old path form can be deleted
    assertTrue(call("apply_changes", "project", P, "dry_run", true, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":"
        + "\"/Requirements/SEC_ITSS/SEC_ITSS_SND/SEC_ITSS_SND_GENMSG/SEC_ITSS_SND_GENMSG_01\"}]")).get("dry_run").getAsBoolean());
    // the text no requirement covers yet (the filter in any case): also a paragraph whose sentence is covered in part
    JsonObject uncovered = call("read_document", "project", P, "document", "/Documents/spec.xhtml", "filter", "UNCOVERED");
    List<String> left = new ArrayList<>();
    for (JsonElement e : uncovered.getAsJsonArray("paragraphs"))
      left.add(e.getAsJsonObject().get("text").getAsString());
    assertEquals(List.of("Spec", "The system shall keep logs for one year.", "The system shall lock the account after 3 failed attempts."),
        left);
    JsonObject lockP = paragraph(uncovered, "lock the account");
    assertEquals("partial", lockP.get("coverage").getAsString());
    assertEquals("[\"The system shall lock the account after 3 failed attempts.\"]", lockP.getAsJsonArray("uncovered_text").toString());
    // the results of a change are short: the values before and after are in the history
    for (JsonElement e : applied.getAsJsonArray("results"))
      assertFalse(e.getAsJsonObject().has("before") || e.getAsJsonObject().has("after"), e.toString());
    // a locate made again (a retried call) changes nothing
    JsonObject again = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"locate\",\"requirement\":\"/Requirements/SPEC/LOG\","
        + "\"document\":\"/Documents/spec.xhtml\",\"quote\":\"The system shall log every access.\"}]"));
    assertTrue(again.getAsJsonArray("results").get(0).getAsJsonObject().get("unchanged").getAsBoolean(), again.toString());
    assertEquals(1, call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations").size());
    // a node only where the tree takes its type; the keys an operation needs
    assertContains(callError("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Documents\",\"id\":\"X\"}]")),
        "a Requirement cannot be a child of /Documents (a DocFolder; it takes: [DocFolder])");
    assertContains(callError("apply_changes", "project", P, "operations", ops("[{\"op\":\"locate\",\"requirement\":\"/Requirements/SPEC/LOG\"}]")),
        "missing key 'document'");
    // the nodes of a type; the attributes that matched by their names
    JsonObject tps = call("search_nodes", "project", P, "type", "TestPurpose", "under", "/Requirements/SPEC");
    assertEquals(1, tps.get("total").getAsInt(), tps.toString());
    JsonObject byName = call("search_nodes", "project", P, "text", "Access log", "under", "/Requirements/SPEC");
    for (JsonElement m : byName.getAsJsonArray("matches"))
      for (JsonElement k : m.getAsJsonObject().getAsJsonArray("matched"))
        assertFalse(k.getAsString().startsWith("_") || k.getAsString().startsWith("%"), byName.toString());
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
    // verbose: the location with its text and ranges
    JsonObject quoted = call("apply_changes", "project", P, "verbose", true, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"NOTES\",\"as\":\"n\"},"
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

  /** A PDF with one line of text and an XFA form whose external entity names a file. */
  static byte[] xfaPdf(File secret) {
    String xfa = "<?xml version=\"1.0\"?>\n<!DOCTYPE xdp:xdp [ <!ENTITY e SYSTEM \"" + secret.toURI() + "\"> ]>\n"
        + "<xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\"><template xmlns=\"http://www.xfa.org/schema/xfa-template/3.3/\">"
        + "<subform name=\"form1\"><field name=\"f1\"><caption><value><text>Cap &e;</text></value></caption></field></subform>"
        + "</template><datasets xmlns:xfa=\"http://www.xfa.org/schema/xfa-data/1.0/\"><xfa:data><form1><f1>Val &e;</f1></form1>"
        + "</xfa:data></datasets></xdp:xdp>";
    String content = "BT /F1 12 Tf 72 712 Td (The system shall work.) Tj ET";
    String[] objects = { "<< /Type /Catalog /Pages 2 0 R /AcroForm 4 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R /Resources << /Font << /F1 6 0 R >> >> >>",
        "<< /Fields [] /XFA 7 0 R >>", "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        "<< /Length " + xfa.getBytes(StandardCharsets.UTF_8).length + " >>\nstream\n" + xfa + "\nendstream" };
    StringBuilder b = new StringBuilder("%PDF-1.7\n");
    List<Integer> offsets = new ArrayList<>();
    for (int i = 0; i < objects.length; i++) {
      offsets.add(b.toString().getBytes(StandardCharsets.UTF_8).length);
      b.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
    }
    int xref = b.toString().getBytes(StandardCharsets.UTF_8).length;
    b.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
    for (int o : offsets)
      b.append(String.format("%010d 00000 n \n", o));
    b.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
    return b.toString().getBytes(StandardCharsets.UTF_8);
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
    JsonObject res = call("review_changes", "project", P, "change", update.get("change").getAsInt()).getAsJsonArray("operations").get(0)
        .getAsJsonObject();
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
        ops("[{\"op\":\"move\",\"node\":\"/Requirements/SPEC/ENC\",\"parent\":\"/Requirements/SEC_ITSS\"},{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/SEC_ITSS/ENC\"}]"));
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
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/PAR0\"},"
        + "{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/PAR1\"},{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/PAR2\"},"
        + "{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/PAR3\"}]"));

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
    // a dry run of a delete names the links that would break, and changes nothing (the link index too)
    JsonObject dryDel = call("apply_changes", "project", P, "dry_run", true, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/REV/C\"}]"));
    assertContains(dryDel.getAsJsonArray("results").get(0).getAsJsonObject().getAsJsonArray("broken_links").toString(), "/Requirements/REV/A");
    // the result tells what goes with the node; without recursive a node with nodes under it stays
    assertEquals(1, dryDel.getAsJsonArray("results").get(0).getAsJsonObject().get("nodes_under").getAsInt(), dryDel.toString());
    assertContains(callError("apply_changes", "project", P, "dry_run", true, "operations",
        ops("[{\"op\":\"delete\",\"node\":\"/Requirements/REV/C\"}]")), "has 1 node under it (1 TestPurpose): give recursive true");
    assertEquals("/Requirements/REV/C", call("get_node", "project", P, "node", "/Requirements/REV/A").getAsJsonObject("links")
        .getAsJsonArray("depends").get(0).getAsString());
    // the undo of a delete brings the node back with the links to it
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/REV/C\"}]"));
    JsonObject aBroken = call("get_node", "project", P, "node", "/Requirements/REV/A");
    assertFalse(aBroken.getAsJsonObject("links").has("depends"), aBroken.toString());
    assertEquals("depends", aBroken.getAsJsonArray("broken_links").get(0).getAsJsonObject().get("attribute").getAsString(), aBroken.toString());
    assertEquals(1, call("trace_coverage", "project", P, "under", "/Requirements/REV").getAsJsonObject("requirements")
        .getAsJsonObject("broken_links").get("total").getAsInt());
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
        + "{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/REV/C\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"D\"}]"));
    int n = change.get("change").getAsInt();
    JsonObject brief = call("review_changes", "project", P, "last", 1).getAsJsonArray("changes").get(0).getAsJsonObject();
    assertEquals(n, brief.get("change").getAsInt());
    assertEquals(1, brief.getAsJsonObject("kinds").get("delete").getAsInt(), brief.toString());
    // a dry run tells what the revert does
    JsonObject dry = call("revert_change", "project", P, "change", n, "dry_run", true);
    assertTrue(dry.get("dry_run").getAsBoolean());
    assertContains(dry.getAsJsonArray("nodes").toString(), cUuid);
    // the plan names the type of each node, and the text of a location
    boolean locationText = false;
    for (JsonElement x : dry.getAsJsonArray("nodes")) {
      assertTrue(x.getAsJsonObject().has("type"), x.toString());
      locationText |= "Location".equals(x.getAsJsonObject().get("type").getAsString())
          && x.getAsJsonObject().get("text").getAsString().contains("keep logs for one year");
    }
    assertTrue(locationText, dry.toString());
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
    int rr = call("revert_change", "project", P, "change", rev.get("change").getAsInt()).get("change").getAsInt();
    assertEquals("Alpha 2", call("get_node", "project", P, "node", "/Requirements/REV/A").get("name").getAsString());
    callError("get_node", "project", P, "node", "/Requirements/REV/C");
    assertEquals("done", call("review_changes", "project", P, "change", n).get("state").getAsString());
    assertEquals("reverted", call("review_changes", "project", P, "change", rev.get("change").getAsInt()).get("state").getAsString());
    // its undo and redo keep the states of the chain
    call("undo");
    assertEquals("reverted", call("review_changes", "project", P, "change", n).get("state").getAsString());
    assertEquals("done", call("review_changes", "project", P, "change", rev.get("change").getAsInt()).get("state").getAsString());
    assertEquals("undone", call("review_changes", "project", P, "change", rr).get("state").getAsString());
    call("redo");
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

    // a rename and a new node with the old id; a delete and a new node with the same id: the revert gives each node back
    // its id and UUID
    String bUuid = call("get_node", "project", P, "node", "/Requirements/REV/B").get("uuid").getAsString();
    int swap = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/REV/B\",\"id\":\"B2\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"B\",\"name\":\"New B\"},"
        + "{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/REV/C\"},{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"C\"}]"))
        .get("change").getAsInt();
    call("revert_change", "project", P, "change", swap);
    assertEquals(bUuid, call("get_node", "project", P, "node", "/Requirements/REV/B").get("uuid").getAsString());
    assertEquals(cUuid, call("get_node", "project", P, "node", "/Requirements/REV/C").get("uuid").getAsString());
    callError("get_node", "project", P, "node", "/Requirements/REV/B2");
    // the files of the catalog are the ones of the tree: B.json has the UUID of B
    File bFile = new File(workspace, "TS103096/root/Requirements/REV/B.json");
    assertContains(Files.readString(bFile.toPath()), bUuid);

    // an undone change is not reverted
    int e = call("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements/REV\",\"id\":\"E\"}]"))
        .get("change").getAsInt();
    call("undo");
    assertContains(callError("revert_change", "project", P, "change", e), "is undone");
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"/Requirements/REV\"}]"));
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
    JsonObject tc = call("test_coverage", "project", P, "coverage_file", xml.getAbsolutePath(), "under", "/Requirements/SPEC");
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
    assertContains(callError("test_coverage", "project", P, "coverage_file", new File(root, "spec.html").getAbsolutePath()), "is no coverage file");
  }

  @Test
  @Order(10)
  void aNewSpecificationVersionKeepsTheTraceability() throws Exception {
    File v2 = new File(root, "spec_v2.html");
    Files.writeString(v2.toPath(), "<html><body><h1>Spec</h1><p>The system shall log every access.</p>"
        + "<p>The system shall encrypt all stored data with AES.</p><p>The system shall keep logs for two years.</p>"
        + "<p>The system may lock the account after 5 failed attempts.</p>"
        + "<p>The system must alert the operator.</p><p>Система должна хранить журнал.</p></body></html>", StandardCharsets.UTF_8);
    JsonObject up = call("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "file", v2.getAbsolutePath(),
        "include_unchanged", true);
    assertEquals("/Documents/spec_v2.xhtml", up.get("new_document").getAsString(), up.toString());
    assertEquals(1, up.getAsJsonObject("summary").get("unchanged").getAsInt(), up.toString());
    assertEquals(3, up.getAsJsonObject("summary").get("new_sentences").getAsInt(), up.toString());
    Map<String, JsonObject> byReq = new java.util.HashMap<>();
    for (JsonElement e : up.getAsJsonArray("requirements"))
      byReq.put(e.getAsJsonObject().get("requirement").getAsString(), e.getAsJsonObject());
    assertEquals("unchanged", byReq.get("/Requirements/SPEC/LOG").get("status").getAsString(), up.toString());
    // the sentence of ENC changed: its location moves to the most similar sentence, whole, with the old text to compare
    JsonObject enc = byReq.get("/Requirements/SPEC/ENC");
    assertEquals("changed", enc.get("status").getAsString(), up.toString());
    JsonObject loc = enc.getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("The system shall encrypt stored data.", loc.getAsJsonArray("old_text").get(0).getAsString(), up.toString());
    assertFalse(enc.has("description"), "ENC has no own description: " + up);
    assertEquals("The system shall encrypt all stored data with AES.", loc.get("new_text").getAsString(), up.toString());
    // the words that came and went: a changed word shows even at a high similarity
    assertEquals("[\"all\",\"aes\"]", loc.getAsJsonArray("words_added").toString(), up.toString());
    assertEquals("[]", loc.getAsJsonArray("words_removed").toString(), up.toString());
    assertTrue(loc.get("similarity").getAsDouble() >= 0.6, up.toString());
    assertFalse(loc.has("modality_changed"), up.toString());
    // shall became may and 3 became 5: the flags tell it
    JsonObject lock = byReq.get("/Requirements/SPEC/LOCKOUT").getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("changed", lock.get("status").getAsString(), up.toString());
    assertTrue(lock.get("modality_changed").getAsBoolean(), up.toString());
    assertTrue(lock.get("numbers_changed").getAsBoolean(), up.toString());
    // the new sentences no requirement marks, statements in English and Russian
    List<String> quotes = new ArrayList<>();
    for (JsonElement e : up.getAsJsonArray("new_sentences")) {
      quotes.add(e.getAsJsonObject().get("quote").getAsString());
      assertTrue(e.getAsJsonObject().get("statement").getAsBoolean(), e.toString());
    }
    assertEquals(List.of("The system shall keep logs for two years.", "The system must alert the operator.",
        "Система должна хранить журнал."), quotes);
    // the review of the change has what the update did to each requirement
    JsonObject reviewed = call("review_changes", "project", P, "change", up.get("change").getAsInt());
    assertContains(reviewed.toString(), "/Requirements/SPEC/LOCKOUT");
    assertContains(reviewed.toString(), "new_sentences");
    JsonObject newDoc = call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml");
    assertTrue(paragraph(newDoc, "log every access").getAsJsonArray("requirements").toString().contains("/Requirements/SPEC/LOG"),
        newDoc.toString());
    assertEquals("full", paragraph(newDoc, "encrypt all stored").get("coverage").getAsString(), newDoc.toString());
    // detach_old by default: the requirements trace to the new version only; the old document stays
    JsonArray logLocs = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").getAsJsonArray("locations");
    assertEquals(1, logLocs.size(), logLocs.toString());
    assertEquals("/Documents/spec_v2.xhtml", logLocs.get(0).getAsJsonObject().get("document").getAsString());
    call("get_node", "project", P, "node", "/Documents/spec.xhtml");
    // the versions know each other: the old one is named, not measured, by the coverage
    assertEquals("/Documents/spec_v2.xhtml", call("read_document", "project", P, "document", "/Documents/spec.xhtml")
        .get("superseded_by").getAsString());
    assertEquals("/Documents/spec.xhtml", call("read_document", "project", P, "document", "/Documents/spec_v2.xhtml")
        .get("previous_version").getAsString());
    JsonObject cov = call("trace_coverage", "project", P);
    assertContains(cov.getAsJsonArray("documents").toString(), "\"superseded_by\":\"/Documents/spec_v2.xhtml\"");
    // the same update again is refused: the old version names the new one
    assertContains(callError("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "file", v2.getAbsolutePath()),
        "was updated to /Documents/spec_v2.xhtml already");
    assertContains(callError("update_document_version", "project", P, "document", "/Documents/spec.xhtml", "new_document",
        "/Documents/spec_v2.xhtml"), "was updated to /Documents/spec_v2.xhtml already");
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
    // a paragraph that got a sentence, and a short text: neither is "unchanged"
    File c1 = new File(root, "pump.html");
    Files.writeString(c1.toPath(), "<html><body><p>The pump shall stop at 5 bar. It shall alarm.</p>"
        + "<table><tr><td>Limit</td><td>5</td></tr></table><p>Version 2024.</p></body></html>");
    JsonObject pump = call("import_document", "project", P, "file", c1.getAbsolutePath());
    call("apply_changes", "project", P, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"PUMP\",\"as\":\"p\"},"
        + "{\"op\":\"create\",\"parent\":\"$p\",\"id\":\"STOP\",\"as\":\"s\"},{\"op\":\"create\",\"parent\":\"$p\",\"id\":\"LIM\",\"as\":\"l\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$s\",\"document\":\"" + pump.get("path").getAsString() + "\",\"quote\":\"The pump shall stop at 5 bar.\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$l\",\"document\":\"" + pump.get("path").getAsString() + "\",\"quote\":\"5\",\"occurrence\":2}]"));
    File c2 = new File(root, "pump_v2.html");
    Files.writeString(c2.toPath(), "<html><body><p>The pump shall stop at 5 bar. It shall alarm. In service mode the limit is 7 bar.</p>"
        + "<table><tr><td>Limit</td><td>7</td></tr></table><p>Version 2025.</p></body></html>");
    JsonObject pu = call("update_document_version", "project", P, "document", pump.get("path").getAsString(), "file", c2.getAbsolutePath());
    Map<String, JsonObject> pr = new java.util.HashMap<>();
    for (JsonElement e : pu.getAsJsonArray("requirements"))
      pr.put(e.getAsJsonObject().get("requirement").getAsString(), e.getAsJsonObject());
    JsonObject stop = pr.get("/Requirements/PUMP/STOP");
    assertEquals("context_changed", stop.get("status").getAsString(), pu.toString());
    JsonObject stopLoc = stop.getAsJsonArray("locations").get(0).getAsJsonObject();
    assertFalse(stopLoc.has("similarity"), pu.toString());
    assertEquals("[\"In service mode the limit is 7 bar.\"]", stopLoc.getAsJsonArray("paragraph_added").toString(), pu.toString());
    assertEquals("lost", pr.get("/Requirements/PUMP/LIM").get("status").getAsString(), pu.toString());
    // the new sentence with no word of a requirement is listed too
    boolean service = false;
    for (JsonElement e : pu.getAsJsonArray("new_sentences"))
      if (e.getAsJsonObject().get("quote").getAsString().equals("In service mode the limit is 7 bar."))
        service = !e.getAsJsonObject().get("statement").getAsBoolean();
    assertTrue(service, pu.toString());

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
    // saved report settings of the project generate their report: the ones of the ETSI catalog use a template that is
    // not installed; saved settings of the table view template work
    JsonObject saved = call("search_nodes", "project", P, "type", "ReportSettings", "under", "/Reports");
    String settings = saved.getAsJsonArray("matches").get(0).getAsJsonObject().get("path").getAsString();
    assertContains(callError("generate_report", "project", P, "settings", settings, "output", new File(root, "report-x").getAbsolutePath()),
        "com.unitesk.requality.reports.tps, which is not installed");
    TreeDB db = Nodes.findDB("TS103096");
    TreeNode reportsNode = Core.node(db, "/Reports");
    String table = Core.write(db, "Saved settings", false, () -> {
      com.unitesk.requality.nodetype.ReportSettings rs = (com.unitesk.requality.nodetype.ReportSettings) reportsNode.createChild("Table",
          com.unitesk.requality.nodetype.ReportSettings.TYPE_NAME);
      rs.attrs().setConstAttribute(com.unitesk.requality.nodetype.ReportSettings.ATTR_TEMPLATE, "com.unitesk.requality.reports.tableview", 0);
      rs.attrs().setConstAttribute(com.unitesk.requality.nodetype.ReportSettings.ATTR_ROOT_REQUIREMENT, "/Requirements/SPEC", 0);
      rs.commitAttributes();
      return rs.getQualifiedId();
    });
    JsonObject fromSaved = call("generate_report", "project", P, "settings", table, "output", new File(root, "report-saved").getAbsolutePath());
    assertTrue(fromSaved.getAsJsonArray("files").size() >= 1, fromSaved.toString());
    call("get_node", "project", P, "node", table);
    // the coverage report with the test coverage of a coverage file
    File cov = new File(root, "coverage.xml");
    JsonObject covRep = call("generate_report", "project", P, "template", "com.unitesk.requality.reports.coverage", "output",
        new File(root, "report-coverage").getAbsolutePath(), "root", "/Requirements/SPEC", "coverage_file", cov.getAbsolutePath());
    StringBuilder html = new StringBuilder();
    for (JsonElement f : covRep.getAsJsonArray("files"))
      if (f.getAsString().endsWith(".html"))
        html.append(Files.readString(new File(f.getAsString()).toPath()));
    assertContains(html.toString(), "test_log");

    // a file changed on disk (git pull, another editor) between two looks at the catalog is not written over: the change
    // is refused until a load
    System.setProperty("requality.diskWatch", "3600000");
    File logFile = new File(workspace, "TS103096/root/Requirements/SPEC/LOG.json");
    String json = Files.readString(logFile.toPath());
    String current = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString();
    assertContains(json, current);
    Files.writeString(logFile.toPath(), json.replace(current, "Renamed on disk"));
    String refused = callError("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"attributes\":{\"status\":\"checked\"}}]"));
    assertContains(refused, "changed on disk");
    assertContains(Files.readString(logFile.toPath()), "Renamed on disk");
    assertEquals("loaded again", call("open_project", "project", P).get("state").getAsString());
    assertEquals("Renamed on disk", call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").get("name").getAsString());
    call("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"attributes\":{\"status\":\"checked\"}}]"));
    // with a look before each call, a read after an outside change loads the project again and tells it
    System.setProperty("requality.diskWatch", "0");
    call("get_node", "project", P, "node", "/Requirements/SPEC/LOG");
    String again = Files.readString(logFile.toPath());
    Files.writeString(logFile.toPath(), again.replace("Renamed on disk", "Renamed again"));
    JsonObject seen = call("get_node", "project", P, "node", "/Requirements/SPEC/LOG");
    assertEquals("Renamed again", seen.get("name").getAsString(), seen.toString());
    assertContains(seen.get("notice").getAsString(), "changed on disk");
    // the files the server writes are no outside change
    JsonObject own = call("apply_changes", "project", P, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/SPEC/LOG\",\"attributes\":{\"status\":\"rechecked\"}}]"));
    assertFalse(own.has("notice"), own.toString());
    assertFalse(call("get_node", "project", P, "node", "/Requirements/SPEC/LOG").has("notice"));
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
    // an idle catalog is released (--release-after) and loads again when a tool needs it
    Thread.sleep(5);
    mcp.exclusive(() -> core.releaseIdle(1, Long.MAX_VALUE));
    assertEquals("released", state("AgentProject"));
    assertEquals("First", call("get_node", "project", "AgentProject", "node", "/Requirements/R1").get("name").getAsString());
    assertEquals("ready", state("AgentProject"));
  }

  /** The project of the scenarios below (made in reportsAndProjects). */
  static final String A = "AgentProject";

  @Test
  @Order(13)
  void theAgentEditsTheProjectSettings() throws Exception {
    JsonObject before = call("get_project_settings", "project", A);
    for (String k : new String[] { "attributes", "enums", "required_attributes", "checker_rules", "checkers", "id_prefix",
        "default_link_attribute", "templates" })
      assertTrue(before.has(k), before.toString());
    JsonObject changed = call("change_project_settings", "project", A,
        "enums", JsonParser.parseString("{\"Status\":{\"type\":\"string\",\"values\":[\"draft\",{\"value\":\"approved\",\"comment\":\"by the board\"}]}}"),
        "attributes", JsonParser.parseString("{\"owner\":{\"type\":\"string\",\"value\":\"team\"},\"level\":{\"type\":\"enum:Status\",\"value\":\"draft\"}}"),
        "required_attributes", JsonParser.parseString("{\"Requirement\":[{\"attribute\":\"reviewed\",\"default\":false}]}"),
        "checker_rules", JsonParser.parseString("[{\"name\":\"has a name\",\"formula\":\"one.attr('%Name') != null\"}]"),
        "id_prefix", "AG", "default_link_attribute", "refines", "summary", "Settings of the team");
    int settingsChange = changed.get("change").getAsInt();
    JsonObject after = call("get_project_settings", "project", A);
    assertEquals("[{\"value\":\"draft\"},{\"value\":\"approved\",\"comment\":\"by the board\"}]",
        after.getAsJsonObject("enums").getAsJsonObject("Status").getAsJsonArray("values").toString(), after.toString());
    assertEquals("enum:Status", after.getAsJsonObject("attributes").getAsJsonObject("level").get("type").getAsString());
    assertEquals("AG", after.get("id_prefix").getAsString());
    assertEquals("refines", after.get("default_link_attribute").getAsString());
    assertEquals("has a name", after.getAsJsonArray("checker_rules").get(0).getAsJsonObject().get("name").getAsString());
    // the requirements get the mandatory attribute with its default
    JsonObject r1 = call("get_node", "project", A, "node", "/Requirements/R1");
    assertTrue(r1.getAsJsonObject("attributes").has("reviewed"), r1.toString());
    // an attribute of an enumeration takes only its values
    call("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/R1\",\"attributes\":{\"level\":\"approved\"}}]"));
    assertContains(callError("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/R1\",\"attributes\":{\"level\":\"bogus\"}}]")),
        "is not a value of the enumeration Status");
    // an enumeration in use stays
    assertContains(callError("change_project_settings", "project", A, "enums", JsonParser.parseString("{\"Status\":null}")),
        "is used by");
    assertContains(callError("change_project_settings", "project", A, "checker_rules",
        JsonParser.parseString("[{\"name\":\"broken\",\"formula\":\"getNodeName( ==\"}]")), "no JavaScript expression");
    // a settings change is one change: revert_change takes it back
    int prefixChange = call("change_project_settings", "project", A, "id_prefix", "XY").get("change").getAsInt();
    assertEquals("XY", call("get_project_settings", "project", A).get("id_prefix").getAsString());
    call("revert_change", "project", A, "change", prefixChange);
    assertEquals("AG", call("get_project_settings", "project", A).get("id_prefix").getAsString());
    assertTrue(settingsChange < prefixChange);
    // the JavaScript of a catalog (here a checker rule) reaches no file or program: a rule that tries fails on its trial
    File marker = new File(root, "sandbox-marker");
    for (String escape : new String[] { "new java.io.File('" + marker.getAbsolutePath().replace('\\', '/') + "').createNewFile() || true",
        "new java.util.Formatter('" + marker.getAbsolutePath().replace('\\', '/') + "').close() || true" })
      assertContains(callError("change_project_settings", "project", A, "checker_rules", JsonParser.parseString(
          "[{\"name\":\"escape\",\"formula\":\"" + escape + "\"}]")), "fails on");
    assertFalse(marker.exists(), "a checker rule created a file");
    // a rule of the node: true for a correct node, a problem for the others
    call("change_project_settings", "project", A, "checker_rules",
        JsonParser.parseString("[{\"name\":\"not second\",\"formula\":\"one.attr('%Name') != 'First'\"}]"));
    assertContains(call("trace_coverage", "project", A, "refresh", true).toString(), "not second");
    assertContains(callError("trace_coverage", "project", A, "refresh", "yes"), "must be true or false");
    javax.script.ScriptEngine js = com.unitesk.requality.tool.CoreUtils.getJSEngine();
    assertEquals(3.0, ((Number) js.eval("'abc'.length")).doubleValue());
    try {
      js.eval("java.lang.Runtime.getRuntime().exec('true')");
      throw new AssertionError("a script ran a program");
    } catch (javax.script.ScriptException | RuntimeException e) {
      // refused
    }
    assertTrue(ScriptSandbox.visible("java.util.ArrayList"));
    assertFalse(ScriptSandbox.visible("java.lang.Runtime"));
    assertFalse(ScriptSandbox.visible("java.io.File"));
    assertFalse(ScriptSandbox.visible("java.lang.Class"));
    assertFalse(ScriptSandbox.visible("java.util.logging.FileHandler"));
    assertFalse(ScriptSandbox.visible("com.unitesk.requality.tool.CoreUtils"));
    assertFalse(ScriptSandbox.visible("com.unitesk.requality.nodetype.Document"));
    assertFalse(ScriptSandbox.visible("java.util.Formatter"));
    assertFalse(ScriptSandbox.visible("java.util.Scanner"));
    assertFalse(ScriptSandbox.visible("com.unitesk.requality.core.model.TreeDB"));
    assertFalse(ScriptSandbox.visible("java.util.Locale"));
    assertFalse(ScriptSandbox.visible("java.util.TimeZone"));
    assertFalse(ScriptSandbox.visible("java.time.zone.ZoneRulesProvider"));
    assertTrue(ScriptSandbox.visible("com.unitesk.requality.core.script.Element"));
    assertTrue(ScriptSandbox.visible("java.util.HashMap$Node"));
    call("change_project_settings", "project", A, "checker_rules", new JsonArray());
  }

  @Test
  @Order(14)
  void applyChangesMakesEveryNodeTypeAndManyNodes() throws Exception {
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"R2\",\"name\":\"Second\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements/R1\",\"type\":\"VirtualNode\",\"id\":\"V1\",\"target\":\"/Requirements/R2\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"BARE\",\"template\":\"none\"}]"));
    assertEquals("VirtualNode", call("get_node", "project", A, "node", "/Requirements/R1/V1").get("type").getAsString());
    assertContains(callError("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"type\":\"VirtualNode\",\"id\":\"V2\"}]")), "needs 'target'");
    assertContains(callError("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"type\":\"Glossary\"}]")), "VirtualNode");
    // a dry run reports every operation that fails, and the ones that use the node of a failed one
    String dry = callError("apply_changes", "project", A, "dry_run", true, "operations", ops("["
        + "{\"op\":\"create\",\"parent\":\"/Nowhere\",\"id\":\"X\",\"as\":\"x\"},"
        + "{\"op\":\"update\",\"node\":\"$x\",\"name\":\"X\"},"
        + "{\"op\":\"update\",\"node\":\"/Requirements/R2\",\"name\":\"fine\"},"
        + "{\"op\":\"delete\",\"node\":\"/Requirements/Missing\"}]"));
    assertContains(dry, "3 of 4 operations fail");
    assertContains(dry, "Operation 0 (create)");
    assertContains(dry, "$x is the node of operation 0, which failed");
    assertContains(dry, "Operation 3 (delete)");
    assertEquals("Second", call("get_node", "project", A, "node", "/Requirements/R2").get("name").getAsString());
    // one operation for many nodes, and the rename of an attribute
    JsonObject all = call("apply_changes", "project", A, "operations", ops("[{\"op\":\"update_all\",\"under\":\"/Requirements\","
        + "\"type\":\"Requirement\",\"attributes\":{\"checked\":\"yes\"}}]"));
    assertTrue(all.getAsJsonArray("results").get(0).getAsJsonObject().get("nodes").getAsInt() >= 3, all.toString());
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"rename_attribute\",\"under\":\"/Requirements\","
        + "\"attribute\":\"checked\",\"new_name\":\"verified\"}]"));
    JsonObject r2 = call("get_node", "project", A, "node", "/Requirements/R2");
    assertEquals("yes", r2.getAsJsonObject("attributes").getAsJsonObject("verified").get("value").getAsString(), r2.toString());
    assertFalse(r2.getAsJsonObject("attributes").has("checked"), r2.toString());
    // a copy of a subtree: new nodes with the next free ids
    JsonObject copied = call("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"copy\",\"node\":\"/Requirements/R1\",\"parent\":\"/Requirements/R2\",\"as\":\"c\"},"
            + "{\"op\":\"update\",\"node\":\"$c\",\"name\":\"Copy of first\"}]"));
    JsonObject copy = copied.getAsJsonArray("results").get(0).getAsJsonObject();
    assertEquals("/Requirements/R1", copy.get("from").getAsString(), copied.toString());
    String copyPath = copy.get("path").getAsString();
    assertTrue(copyPath.startsWith("/Requirements/R2/"), copied.toString());
    JsonObject cn = call("get_node", "project", A, "node", copyPath);
    assertEquals("Copy of first", cn.get("name").getAsString());
    assertFalse(cn.get("uuid").getAsString().equals(call("get_node", "project", A, "node", "/Requirements/R1").get("uuid").getAsString()));
    assertContains(callError("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"copy\",\"node\":\"/Requirements/R2\",\"parent\":\"" + copyPath + "\"}]")), "into itself");
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"delete\",\"recursive\":true,\"node\":\"" + copyPath + "\"}]"));
    // a node made twice is named
    JsonObject twice = call("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"R3\",\"name\":\"Second\"}]"));
    assertContains(twice.getAsJsonArray("warnings").get(0).getAsJsonObject().get("warning").getAsString(), "/Requirements/R2");
    // a node by its id alone, when one node has it
    assertEquals("/Requirements/R2", call("get_node", "project", A, "node", "R2").get("path").getAsString());
    // search by the name of an attribute as the editor shows it; an unknown attribute is an error
    JsonObject byName = call("search_nodes", "project", A, "attribute", "name", "value", "Second", "operator", "EQUALS");
    assertEquals(2, byName.getAsJsonArray("matches").size(), byName.toString());
    assertContains(callError("search_nodes", "project", A, "attribute", "nosuch", "value", "x"), "has the attribute nosuch");
  }

  @Test
  @Order(15)
  void aRevertNamesWhatItBreaks() throws Exception {
    int made = call("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"TARGET\",\"name\":\"Target\"}]")).get("change").getAsInt();
    int linked = call("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"link\",\"from\":\"/Requirements/R2\",\"to\":\"/Requirements/TARGET\",\"attribute\":\"depends\"}]"))
        .get("change").getAsInt();
    String refused = callError("revert_change", "project", A, "change", made);
    assertContains(refused, "/Requirements/R2 links to /Requirements/TARGET (depends), which the revert deletes");
    assertContains(refused, "Later changes of these nodes: [" + linked + "]");
    JsonObject forced = call("revert_change", "project", A, "change", made, "force", true);
    assertEquals(1, forced.getAsJsonObject("counts").get("deleted").getAsInt(), forced.toString());
    assertEquals(1, forced.get("broken_link_count").getAsInt(), forced.toString());
    assertEquals("/Requirements/R2", forced.getAsJsonArray("broken_links").get(0).getAsJsonObject().get("from").getAsString());
  }

  @Test
  @Order(16)
  void importsAndReportsStayInTheirFolders() throws Exception {
    // a reference that is not next to the document, but a file of the folder the server runs in, is refused
    File spec = new File(root, "cwd-ref.html");
    Files.writeString(spec.toPath(), "<html><body><p>The system shall work.</p><img src=\"build.gradle\"/></body></html>");
    assertTrue(new File("build.gradle").isFile(), "the tests run in the folder of the server project");
    assertContains(callError("import_document", "project", A, "file", spec.getAbsolutePath()), "build.gradle");
    // the decodings of the importer: + is a plus or a space
    List<File> c = Policy.candidates(root, "a+b.png");
    assertTrue(c.contains(new File(root, "a+b.png")) && c.contains(new File(root, "a b.png")), c.toString());
    // a document without text is no import: nothing stays
    File empty = new File(root, "empty.pdf");
    Files.write(empty.toPath(), pdf());
    int changes = call("review_changes", "project", A).get("total").getAsInt();
    assertContains(callError("import_document", "project", A, "file", empty.getAbsolutePath()), "has no text");
    assertEquals(changes, call("review_changes", "project", A).get("total").getAsInt());
    assertFalse(call("get_tree", "project", A, "node", "/Documents").toString().contains("empty"));
    // a report folder in a catalog is refused
    assertContains(callError("generate_report", "project", A, "template", "com.unitesk.requality.reports.tableview", "output",
        new File(workspace, "AgentProject/report").getAbsolutePath()), "inside the catalog");
    // saved settings: a change of the agent, and a report from them
    JsonObject saved = call("generate_report", "project", A, "template", "com.unitesk.requality.reports.tableview", "output",
        new File(root, "report-saveas").getAbsolutePath(), "save_as", "Overview");
    assertEquals("/Reports/Overview", saved.get("settings").getAsString(), saved.toString());
    assertTrue(saved.has("change"), saved.toString());
    call("generate_report", "project", A, "settings", "/Reports/Overview", "output", new File(root, "report-saved2").getAbsolutePath());
    // the coverage source of report settings is set by generate_report only, and only from a file of the folders
    assertContains(callError("apply_changes", "project", A, "operations", ops("[{\"op\":\"update\",\"node\":\"/Reports/Overview\","
        + "\"attributes\":{\"coverageFilePath\":\"http://127.0.0.1:9/x\"}}]")), "coverage_file");
    TreeDB db = Nodes.findDB("AgentProject");
    Core.write(db, "Coverage URL", false, () -> {
      TreeNode n = Core.node(db, "/Reports/Overview");
      n.attrs().setConstAttribute("coverageFilePath", "http://127.0.0.1:9/exfil", 0);
      n.commitAttributes();
      return null;
    });
    assertContains(callError("generate_report", "project", A, "settings", "/Reports/Overview", "output",
        new File(root, "report-url").getAbsolutePath()), "which is no file");
    // a template of the catalog cannot run a program
    File marker = new File(root, "template-marker");
    File tpl = new File(workspace, "AgentProject/.templates/escape");
    tpl.mkdirs();
    Files.writeString(new File(tpl, "template.xml").toPath(), "<?xml version=\"1.0\" ?>\n<report start=\"escape.ftl\" id=\"test.escape\" "
        + "name=\"Escape\" read-only=\"true\" group=\"extraction\"><description>test</description><template source=\"escape.ftl\" "
        + "location=\"escape.html\"/></report>");
    Files.writeString(new File(tpl, "escape.ftl").toPath(), "<#assign ex = \"freemarker.template.utility.Execute\"?new()>"
        + "${ex(\"touch " + marker.getAbsolutePath() + "\")}");
    call("open_project", "project", A);
    boolean listed = call("list_report_templates", "project", A).toString().contains("test.escape");
    assertTrue(listed, "the template of the catalog is listed");
    // the report fails or is made without the program: the template cannot make an Execute object
    JsonObject args = new JsonObject();
    args.addProperty("project", A);
    args.addProperty("template", "test.escape");
    args.addProperty("output", new File(root, "report-escape").getAbsolutePath());
    JsonObject params = new JsonObject();
    params.addProperty("name", "generate_report");
    params.add("arguments", args);
    rpc("tools/call", params);
    assertFalse(marker.exists(), "a template ran a program");
  }

  /** A PDF with the lines (text and the y position of each). */
  static byte[] pdf(Object... lines) {
    StringBuilder content = new StringBuilder();
    for (int i = 0; i < lines.length; i += 2)
      content.append("BT /F1 11 Tf 72 ").append(lines[i + 1]).append(" Td (").append(lines[i]).append(") Tj ET\n");
    String[] objects = { "<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        "<< /Length " + content.length() + " >>\nstream\n" + content + "endstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>" };
    StringBuilder b = new StringBuilder("%PDF-1.7\n");
    List<Integer> offsets = new ArrayList<>();
    for (int i = 0; i < objects.length; i++) {
      offsets.add(b.toString().getBytes(StandardCharsets.UTF_8).length);
      b.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
    }
    int xref = b.toString().getBytes(StandardCharsets.UTF_8).length;
    b.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
    for (int o : offsets)
      b.append(String.format("%010d 00000 n \n", o));
    b.append("trailer\n<< /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
    return b.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Test
  @Order(17)
  void aPdfKeepsItsHeadings() throws Exception {
    File f = new File(root, "brakes.pdf");
    Files.write(f.toPath(), pdf("1 Scope", 712, "The brake shall stop the car.", 698, "2 Limits", 640, "2.1 Pressure", 626,
        "The pressure shall stay below 180 bar.", 612));
    JsonObject doc = call("import_document", "project", A, "file", f.getAbsolutePath());
    JsonObject read = call("read_document", "project", A, "document", doc.get("path").getAsString());
    JsonObject scope = paragraph(read, "Scope");
    assertNotNull(scope, read.toString());
    assertEquals(1, scope.get("heading").getAsInt(), read.toString());
    assertEquals(2, paragraph(read, "2.1 Pressure").get("heading").getAsInt(), read.toString());
    assertFalse(paragraph(read, "stay below").has("heading"), read.toString());
    assertEquals(0, PdfHeadings.level("5 bar.", Map.of()));
    assertEquals(0, PdfHeadings.level("3 the pump shall stop", Map.of()));
    assertEquals(3, PdfHeadings.level("4.2.1 Wet road", Map.of()));
    assertEquals(2, PdfHeadings.level("Annex text", Map.of("annex text", 2)));
  }

  @Test
  @Order(18)
  void aReqIfFileBecomesRequirements() throws Exception {
    File reqif = new File(root, "brakes.reqif");
    try (java.io.InputStream in = McpServerTest.class.getResourceAsStream("/mcp/brakes.reqif")) {
      Files.copy(in, reqif.toPath());
    }
    call("create_project", "name", "ReqifProject");
    JsonObject imported = call("import_reqif", "project", "ReqifProject", "file", reqif.getAbsolutePath());
    assertEquals(3, imported.get("requirements").getAsInt(), imported.toString());
    assertEquals(1, imported.get("objects_left_out").getAsInt(), imported.toString());
    JsonObject tree = call("get_tree", "project", "ReqifProject", "node", "/Requirements", "depth", 3);
    assertContains(tree.toString(), "Wet road");
    JsonObject braking = call("search_nodes", "project", "ReqifProject", "attribute", "name", "value", "Braking", "operator", "EQUALS");
    String path = braking.getAsJsonArray("matches").get(0).getAsJsonObject().get("path").getAsString();
    JsonObject n = call("get_node", "project", "ReqifProject", "node", path);
    assertContains(n.get("description").getAsString(), "The brake shall stop the car within 40 m");
    assertEquals("high", n.getAsJsonObject("attributes").getAsJsonObject("Priority").get("value").getAsString(), n.toString());
    // a value the file does not give is no value (not the first value of the enumeration)
    JsonObject parking = call("search_nodes", "project", "ReqifProject", "attribute", "name", "value", "Parking brake", "operator", "EQUALS");
    JsonObject p = call("get_node", "project", "ReqifProject", "node",
        parking.getAsJsonArray("matches").get(0).getAsJsonObject().get("path").getAsString());
    assertFalse(p.getAsJsonObject("attributes").has("Priority"), p.toString());
    assertTrue(call("get_project_settings", "project", "ReqifProject").getAsJsonObject("enums").has("Priority"));
    // the import makes /Requirements the specification: a project with requirements needs replace
    assertContains(callError("import_reqif", "project", "ReqifProject", "file", reqif.getAbsolutePath()), "replace true");
    call("revert_change", "project", "ReqifProject", "change", imported.get("change").getAsInt());
    assertEquals(0, call("get_tree", "project", "ReqifProject", "node", "/Requirements").get("children").getAsInt());
    // a file that is no ReqIF, and an external entity, are refused
    File bad = new File(root, "bad.reqif");
    Files.writeString(bad.toPath(), "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><REQ-IF>&e;</REQ-IF>");
    assertContains(callError("import_reqif", "project", "ReqifProject", "file", bad.getAbsolutePath()), "no XML file");
  }

  static String git(File dir, String... args) throws Exception {
    List<String> cmd = new ArrayList<>(List.of("git", "-C", dir.getAbsolutePath(), "-c", "user.name=test", "-c",
        "user.email=test@example.com", "-c", "commit.gpgsign=false"));
    cmd.addAll(List.of(args));
    Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, pr.waitFor(), out);
    return out;
  }

  @Test
  @Order(19)
  void aChangeIsComparedWithAGitRevision() throws Exception {
    File catalog = new File(workspace, "AgentProject");
    assertContains(callError("compare_with_revision", "project", A), "not in a Git repository");
    git(catalog, "init", "-q");
    git(catalog, "add", "-A");
    git(catalog, "commit", "-q", "-m", "base");
    JsonObject none = call("compare_with_revision", "project", A);
    assertEquals(0, none.getAsJsonObject("counts").get("changed").getAsInt() + none.getAsJsonObject("counts").get("added").getAsInt(),
        none.toString());
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/R2\",\"name\":\"Second, renamed\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"NEW\",\"name\":\"New one\"},"
        + "{\"op\":\"move\",\"node\":\"/Requirements/R3\",\"parent\":\"/Requirements/R2\"},"
        + "{\"op\":\"delete\",\"node\":\"/Requirements/BARE\"}]"));
    JsonObject d = call("compare_with_revision", "project", A, "revision", "HEAD");
    assertContains(d.getAsJsonArray("added").toString(), "/Requirements/NEW");
    assertContains(d.getAsJsonArray("deleted").toString(), "/Requirements/BARE");
    String changed = d.getAsJsonArray("changed").toString();
    assertContains(changed, "\"before\":\"Second\",\"after\":\"Second, renamed\"");
    assertContains(changed, "\"path\":\"/Requirements/R2/R3\",");
    assertContains(changed, "\"was\":\"/Requirements/R3\"");
    assertContains(callError("compare_with_revision", "project", A, "revision", "--output=/tmp/x"), "Invalid revision");
    assertContains(callError("compare_with_revision", "project", A, "revision", "nosuchbranch"), "No revision nosuchbranch");
  }

  @Test
  @Order(20)
  void aCatalogWithoutALockIsNotChangedAndAnInterruptedImportIsCleaned() throws Exception {
    com.unitesk.requality.vscode.core.Workspace.Project p = core.project(A);
    p.unlocked = "test: no folder for the lock files";
    try {
      assertContains(callError("apply_changes", "project", A, "operations",
          ops("[{\"op\":\"update\",\"node\":\"/Requirements/R2\",\"name\":\"x\"}]")), "is not locked");
      assertContains(call("list_projects").toString(), "not locked");
    } finally {
      p.unlocked = null;
    }
    // the files an import left when the server stopped during it go when the project is used next
    TreeDB db = core.db(A);
    File docs = RequalityTools.childrenFolder(db, Core.node(db, "/Documents"));
    java.util.Set<String> before = RequalityTools.list(docs);
    File marker = RequalityTools.markImport(History.of(storage, p.dir), Core.node(db, "/Documents"), docs, before);
    File orphan = new File(docs, "orphan.xhtml.json");
    Files.writeString(orphan.toPath(), "{}");
    File orphanRes = new File(docs, "orphan.xhtml_resources");
    orphanRes.mkdirs();
    core.recovered.remove(p.localName);
    call("get_tree", "project", A);
    assertFalse(orphan.exists());
    assertFalse(orphanRes.exists());
    assertFalse(marker.exists());
  }

  /** Answers the next question of the server (elicitation/create); returns it. */
  static JsonObject answerQuestion(String action, Boolean allow) throws Exception {
    long end = System.currentTimeMillis() + 60_000;
    while (System.currentTimeMillis() < end) {
      JsonObject m = others.poll(200, TimeUnit.MILLISECONDS);
      if (m == null || !m.has("method") || !m.get("method").getAsString().equals("elicitation/create"))
        continue;
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
      send(r.toString());
      return m;
    }
    throw new AssertionError("the server asked nothing");
  }

  @Test
  @Order(21)
  void theUserAllowsAFolderOutsideTheServer() throws Exception {
    File outside = Files.createTempDirectory("requality-outside").toFile();
    File doc = new File(outside, "outside.html");
    Files.writeString(doc.toPath(), "<html><body><p>The system shall be allowed.</p></body></html>");
    // a client that cannot ask: the folder is refused, with the option that allows it
    assertContains(callError("import_document", "project", A, "file", doc.getAbsolutePath()), "--allow");
    JsonObject init = new JsonObject();
    init.addProperty("protocolVersion", "2025-06-18");
    JsonObject caps = new JsonObject();
    caps.add("elicitation", new JsonObject());
    init.add("capabilities", caps);
    rpc("initialize", init);
    others.clear();
    try {
      CompletableFuture<JsonObject> allowed = CompletableFuture.supplyAsync(() -> {
        try {
          return call("import_document", "project", A, "file", doc.getAbsolutePath());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      JsonObject q = answerQuestion("accept", true);
      assertContains(q.getAsJsonObject("params").get("message").getAsString(), outside.getCanonicalPath());
      assertTrue(allowed.get(120, TimeUnit.SECONDS).has("path"));
      // the folder stays allowed for the session: no second question
      File doc2 = new File(outside, "second.html");
      Files.writeString(doc2.toPath(), "<html><body><p>The system shall be allowed again.</p></body></html>");
      assertTrue(call("import_document", "project", A, "file", doc2.getAbsolutePath()).has("path"));
      // a declined question refuses
      File other = Files.createTempDirectory("requality-outside2").toFile();
      File doc3 = new File(other, "declined.html");
      Files.writeString(doc3.toPath(), "<html><body><p>No.</p></body></html>");
      CompletableFuture<String> declined = CompletableFuture.supplyAsync(() -> {
        try {
          return callError("import_document", "project", A, "file", doc3.getAbsolutePath());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      answerQuestion("decline", null);
      String refused = declined.get(120, TimeUnit.SECONDS);
      assertContains(refused, "The user did not allow the folder");
      assertContains(refused, "--allow");
      // the home folder, a top folder and the hidden folders of the user are never offered
      assertFalse(Policy.askable(new File(System.getProperty("user.home"))));
      assertFalse(Policy.askable(new File(System.getProperty("user.home")).getParentFile()));
      assertFalse(Policy.askable(new File(System.getProperty("user.home"), ".ssh")));
      assertFalse(Policy.askable(new File(System.getProperty("user.home"), ".config/app")));
      assertTrue(Policy.askable(outside));
    } finally {
      JsonObject plain = new JsonObject();
      plain.addProperty("protocolVersion", "2025-06-18");
      plain.add("capabilities", new JsonObject());
      rpc("initialize", plain);
    }
  }

  @Test
  @Order(22)
  void theSameDocumentAgainChangesNothingAndKeepsItsLetters() throws Exception {
    // a UTF-8 XHTML file without a meta charset and with few letters outside ASCII keeps its letters
    File cafe = new File(root, "cafe.xhtml");
    StringBuilder text = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>");
    for (int i = 0; i < 40; i++)
      text.append("<p>The system shall log event ").append(i).append(" in the journal of the operator.</p>");
    text.append("<p>The café shall open at 7 °C.</p></body></html>");
    Files.writeString(cafe.toPath(), text.toString(), StandardCharsets.UTF_8);
    JsonObject imported = call("import_document", "project", A, "file", cafe.getAbsolutePath());
    JsonObject read = call("read_document", "project", A, "document", imported.get("path").getAsString(), "from", 40);
    assertContains(read.toString(), "The café shall open at 7 °C.");
    // the ETSI document of the catalog, as a new version of itself, with the same file name: nothing changed, also for
    // the locations of the converted catalog without character ranges
    // (the idle release of reportsAndProjects released the catalog: it loads again)
    TreeDB db = core.db(P);
    String docPath = "/Documents/ts_103097v010201p.xhtml";
    Document etsi = (Document) Core.node(db, docPath);
    File source = new File(RequalityTools.childrenFolder(db, Core.node(db, "/Documents")), "ts_103097v010201p.xhtml_resources/ts_103097v010201p.xhtml");
    assertTrue(source.isFile(), source.toString());
    File copyDir = new File(root, "etsi-v2");
    copyDir.mkdirs();
    File copy = new File(copyDir, "ts_103097v010201p.xhtml");
    Files.copy(source.toPath(), copy.toPath());
    JsonObject up = call("update_document_version", "project", P, "document", docPath, "file", copy.getAbsolutePath());
    JsonObject sum = up.getAsJsonObject("summary");
    assertEquals(0, sum.get("lost").getAsInt(), up.toString());
    assertEquals(0, sum.get("changed").getAsInt(), up.toString());
    assertEquals(0, sum.get("context_changed").getAsInt(), up.toString());
    assertEquals(0, sum.get("new_sentences").getAsInt(), up.toString());
    assertEquals(sum.get("requirements").getAsInt(), sum.get("unchanged").getAsInt(), up.toString());
    assertTrue(up.toString().length() < 4000, "a short result: " + up.toString().length());
    assertTrue(etsi.getUUId() != null);
  }

  @Test
  @Order(23)
  void theFourthReviewsAreFixed() throws Exception {
    // a reference inside an element that the parser of the check could read as text is refused too
    File outside = Files.createTempDirectory("requality-outside-ref").toFile();
    File secret = new File(outside, "secret.png");
    Files.writeString(secret.toPath(), "secret");
    File hidden = new File(root, "hidden-ref.html");
    Files.writeString(hidden.toPath(), "<html><body><p>The system shall hide.</p><textarea><img src=\"" + secret.getAbsolutePath()
        + "\"/></textarea><title><img src=\"" + secret.getAbsolutePath() + "\"/></title></body></html>");
    assertContains(callError("import_document", "project", A, "file", hidden.getAbsolutePath()), "secret.png");
    // a table: each paragraph of a cell has its table, row and column; a picture its alternative text
    File table = new File(root, "table.html");
    Files.writeString(table.toPath(), "<html><body><p>Limits <img src=\"curve.png\" alt=\"Brake curve\"/></p><table>"
        + "<tr><th>ID</th><th>Requirement</th></tr><tr><td>T1</td><td>The door shall close in 2 s.</td></tr></table></body></html>");
    JsonObject tdoc = call("import_document", "project", A, "file", table.getAbsolutePath());
    JsonObject tread = call("read_document", "project", A, "document", tdoc.get("path").getAsString());
    JsonObject cell = paragraph(tread, "The door shall close");
    assertEquals(0, cell.get("table").getAsInt(), tread.toString());
    assertEquals(1, cell.get("row").getAsInt(), tread.toString());
    assertEquals(1, cell.get("col").getAsInt(), tread.toString());
    assertEquals("[\"Brake curve\"]", paragraph(tread, "Limits").getAsJsonArray("images").toString());
    // a node file written with other line ends stays as it is after a dry run (the rollback writes no other bytes)
    File r2 = new File(workspace, "AgentProject/root/Requirements/R2.json");
    byte[] crlf = Files.readString(r2.toPath()).replace("\r\n", "\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    Files.write(r2.toPath(), crlf);
    call("apply_changes", "project", A, "dry_run", true, "operations",
        ops("[{\"op\":\"update\",\"node\":\"/Requirements/R2\",\"name\":\"Dry\"}]"));
    assertTrue(java.util.Arrays.equals(crlf, Files.readAllBytes(r2.toPath())), "the dry run rewrote the file");
    // a change on disk before the first look at a catalog is seen (the look of the load)
    com.unitesk.requality.vscode.core.Workspace.Project ap = core.project(A);
    TreeDB adb = core.db(A);
    DiskWatch watch = new DiskWatch();
    watch.loaded(ap.dir);
    String r2text = Files.readString(r2.toPath());
    Files.writeString(r2.toPath(), r2text.replace("Second", "Second on disk"));
    assertTrue(watch.outdated(ap.dir, adb, ap.loadedAt), "a change after the look of the load");
    assertTrue(new DiskWatch().outdated(ap.dir, adb, System.currentTimeMillis() - 60_000), "a change after the load, no look");
    Files.write(r2.toPath(), crlf);
    // the coverage by a plain id and by the id with the prefix of the project
    File cov = new File(root, "coverage-ids.xml");
    String prefix = com.unitesk.requality.core.model.IDMechanics.getShownIdPrefix(adb);
    Files.writeString(cov.toPath(), "<coverage><reqcoverage qid=\"R2\"><covered_by uri=\"t\" label=\"t\" hits=\"1\"/></reqcoverage>"
        + "<reqcoverage qid=\"" + prefix + "-R1\"><covered_by uri=\"u\" label=\"u\" hits=\"1\"/></reqcoverage></coverage>");
    JsonObject tc = call("test_coverage", "project", A, "coverage_file", cov.getAbsolutePath());
    // (R2 has nodes under it: the coverage lists the leaf requirements)
    assertEquals(0, tc.getAsJsonObject("unknown_requirements").get("total").getAsInt(), tc.toString());
    assertContains(tc.getAsJsonObject("covered").toString(), "/Requirements/R1");
    // a bare id that two nodes have is an error
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"TWICE\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements/R2\",\"id\":\"TWICE\"}]"));
    assertContains(callError("get_node", "project", A, "node", "TWICE"), "Several nodes have the id TWICE");
    // a change of the values of an attribute to another type
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/R2\",\"attributes\":{\"points\":\"3\"}}]"));
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"rename_attribute\",\"under\":\"/Requirements\",\"attribute\":\"points\",\"value_type\":\"integer\"}]"));
    JsonObject points = call("get_node", "project", A, "node", "/Requirements/R2").getAsJsonObject("attributes").getAsJsonObject("points");
    assertEquals("INT", points.get("type").getAsString(), points.toString());
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"update\",\"node\":\"/Requirements/R2\",\"attributes\":{\"label\":\"abc\"}}]"));
    assertContains(callError("apply_changes", "project", A, "operations",
        ops("[{\"op\":\"rename_attribute\",\"under\":\"/Requirements\",\"attribute\":\"label\",\"value_type\":\"integer\"}]")), "is no integer");
    // many operations: counts, the named nodes and the first results; the warnings together
    StringBuilder many = new StringBuilder("[");
    for (int i = 0; i < 55; i++)
      many.append(i == 0 ? "" : ",").append("{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"M").append(i)
          .append("\",\"name\":\"Many\",\"as\":\"m").append(i).append("\"}");
    JsonObject big = call("apply_changes", "project", A, "operations", ops(many.append("]").toString()));
    assertEquals(55, big.getAsJsonObject("counts").get("create").getAsInt(), big.toString());
    assertEquals("/Requirements/M54", big.getAsJsonObject("named").get("m54").getAsString());
    assertTrue(big.getAsJsonArray("warnings").size() > 0, big.toString());
    assertEquals(10, big.getAsJsonArray("results").size());
    JsonObject listed = call("review_changes", "project", A, "verbose", true, "last", 1);
    assertEquals(55, listed.getAsJsonArray("changes").get(0).getAsJsonObject().get("operations_total").getAsInt());
    // undo of another project than the one of the newest step
    assertContains(callError("undo", "project", "ReqifProject"), "of project " + A);
    // the markup report of the converted catalog works
    JsonObject em = call("generate_report", "project", P, "template", "com.unitesk.requality.reports.em", "output",
        new File(root, "report-em").getAbsolutePath());
    assertTrue(em.getAsJsonArray("files").size() >= 1, em.toString());
    // annotations
    for (JsonElement t : rpc("tools/list", null).getAsJsonObject("result").getAsJsonArray("tools")) {
      JsonObject o = t.getAsJsonObject();
      String name = o.get("name").getAsString();
      JsonObject an = o.getAsJsonObject("annotations");
      if (name.equals("get_tree") || name.equals("close_project"))
        assertTrue(an.get("idempotentHint").getAsBoolean(), name);
      if (name.equals("apply_changes"))
        assertFalse(an.get("idempotentHint").getAsBoolean());
    }
  }

  @Test
  @Order(24)
  void aVersionUpdateKeepsRepeatedTextsApart() throws Exception {
    File v1 = new File(root, "limits.html");
    Files.writeString(v1.toPath(), "<html><body><h1>1 Speed</h1><p>The speed limit is 50. Values above 10 shall not be used.</p>"
        + "<h1>2 Height</h1><p>The height limit is 4. Values above 10 shall not be used.</p>"
        + "<p>The controller shall stop.</p></body></html>");
    JsonObject d1 = call("import_document", "project", A, "file", v1.getAbsolutePath());
    String doc = d1.get("path").getAsString();
    call("apply_changes", "project", A, "operations", ops("[{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"SPD\",\"as\":\"s\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"HGT\",\"as\":\"h\"},"
        + "{\"op\":\"create\",\"parent\":\"/Requirements\",\"id\":\"CTL\",\"as\":\"c\"},"
        + "{\"op\":\"locate\",\"requirement\":\"$s\",\"document\":\"" + doc + "\",\"quote\":\"Values above 10 shall not be used.\",\"occurrence\":1},"
        + "{\"op\":\"locate\",\"requirement\":\"$h\",\"document\":\"" + doc + "\",\"quote\":\"Values above 10 shall not be used.\",\"occurrence\":2},"
        + "{\"op\":\"locate\",\"requirement\":\"$c\",\"document\":\"" + doc + "\",\"quote\":\"The controller shall stop.\"}]"));
    File v2dir = new File(root, "limits-v2");
    v2dir.mkdirs();
    File v2 = new File(v2dir, "limits.html");
    Files.writeString(v2.toPath(), "<html><body><h1>1 Speed</h1><p>The speed limit is 50. Values above 10 should not be used.</p>"
        + "<h1>2 Height</h1><p>The height limit is 4. Values above 10 shall not be used.</p>"
        + "<p>2.1 The controller shall stop at once.</p></body></html>");
    JsonObject up = call("update_document_version", "project", A, "document", doc, "file", v2.getAbsolutePath(), "include_unchanged", true);
    Map<String, JsonObject> by = new java.util.HashMap<>();
    for (JsonElement e : up.getAsJsonArray("requirements"))
      by.put(e.getAsJsonObject().get("requirement").getAsString(), e.getAsJsonObject());
    assertEquals("unchanged", by.get("/Requirements/HGT").get("status").getAsString(), up.toString());
    // the changed copy is not given the place of the copy that stayed
    JsonObject spd = by.get("/Requirements/SPD").getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("changed", spd.get("status").getAsString(), up.toString());
    assertTrue(spd.get("modality_changed").getAsBoolean(), up.toString());
    assertEquals("Values above 10 should not be used.", spd.get("new_text").getAsString(), up.toString());
    assertTrue(spd.has("p"), up.toString());
    // the clause number of the new sentence is not in the location, and numbers are no changed words
    JsonObject ctl = by.get("/Requirements/CTL").getAsJsonArray("locations").get(0).getAsJsonObject();
    assertEquals("The controller shall stop at once.", ctl.get("new_text").getAsString(), up.toString());
    assertEquals("[\"once\"]", ctl.getAsJsonArray("words_added").toString(), up.toString());
    assertFalse(ctl.has("numbers_changed"), up.toString());
  }

  @Test
  @Order(25)
  void aReqIfFileImportsAgainIntoTheSameRequirements() throws Exception {
    File reqif = new File(root, "brakes.reqif");
    String text = Files.readString(reqif.toPath());
    // a file without ReqIF.ForeignID: the identifiers of the objects match the requirements
    String noFid = text.replaceAll("(?s)<ATTRIBUTE-VALUE-STRING THE-VALUE=\"BRK-\\d\">\\s*<DEFINITION><ATTRIBUTE-DEFINITION-STRING-REF>ad-fid"
        + "</ATTRIBUTE-DEFINITION-STRING-REF></DEFINITION>\\s*</ATTRIBUTE-VALUE-STRING>", "");
    assertFalse(noFid.contains("THE-VALUE=\"BRK-1\""));
    File first = new File(root, "nofid.reqif");
    Files.writeString(first.toPath(), noFid);
    call("create_project", "name", "ReqifAgain");
    call("import_reqif", "project", "ReqifAgain", "file", first.getAbsolutePath());
    String wet = call("search_nodes", "project", "ReqifAgain", "attribute", "name", "value", "Wet road", "operator", "EQUALS")
        .getAsJsonArray("matches").get(0).getAsJsonObject().get("path").getAsString();
    String wetUuid = call("get_node", "project", "ReqifAgain", "node", wet).get("uuid").getAsString();
    call("apply_changes", "project", "ReqifAgain", "operations",
        ops("[{\"op\":\"create\",\"parent\":\"" + wet + "\",\"type\":\"TestPurpose\",\"id\":\"TP1\"}]"));
    File second = new File(root, "nofid2.reqif");
    Files.writeString(second.toPath(), noFid.replace("within 60 m", "within 65 m"));
    JsonObject again = call("import_reqif", "project", "ReqifAgain", "file", second.getAbsolutePath(), "replace", true);
    assertFalse(again.has("deleted"), again.toString());
    JsonObject w = call("get_node", "project", "ReqifAgain", "node", wetUuid);
    assertContains(w.get("description").getAsString(), "within 65 m");
    assertContains(w.getAsJsonArray("children").toString(), "TP1");
    // a requirement that is not in the file goes, with its test purpose only with force
    String withoutWet = noFid.replaceAll("(?s)<SPEC-HIERARCHY IDENTIFIER=\"sh-2\".*?</SPEC-HIERARCHY>", "");
    File third = new File(root, "nofid3.reqif");
    Files.writeString(third.toPath(), withoutWet);
    assertContains(callError("import_reqif", "project", "ReqifAgain", "file", third.getAbsolutePath(), "replace", true),
        "Give force true");
    JsonObject forced = call("import_reqif", "project", "ReqifAgain", "file", third.getAbsolutePath(), "replace", true, "force", true);
    assertEquals(2, forced.get("deleted").getAsInt(), forced.toString());
  }
}
