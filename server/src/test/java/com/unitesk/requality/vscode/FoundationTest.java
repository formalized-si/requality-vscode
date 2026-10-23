package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

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

/**
 * Start-up, project loading (with the Eclipse format-conversion question), Explorer content and the UniEditor page,
 * on the real ETSI catalog written by the Eclipse plug-in.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FoundationTest {
  static ServerHarness h;
  static String projectItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    // Folder name differs from the .project name, as in the published catalog.
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    JsonObject init = h.initialize(h.workspace);
    assertEquals("1.4.0", init.get("version").getAsString());
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  @Test
  @Order(1)
  void oldFormatIsConvertedAfterConfirmation() throws Exception {
    JsonArray ps = h.awaitProjects(1);
    JsonObject p = ps.get(0).getAsJsonObject();
    assertEquals("TS103096", p.get("localName").getAsString());
    assertEquals("TS103096-STF517", p.get("name").getAsString());
    assertEquals("ready", p.get("state").getAsString());
    // AbstractResTracker asks exactly like Eclipse before converting storage 0.15.
    List<JsonObject> msgs = h.requests("client/showMessage");
    assertTrue(msgs.stream().anyMatch(m -> m.get("message").getAsString().contains("format is out of date")), msgs.toString());
    String versions = Files.readString(new File(h.workspace, "TS103096/.reqversions").toPath(), StandardCharsets.UTF_8);
    assertEquals("com.unitesk.nodegroup.requality|0.21.3", versions.trim());
  }

  @Test
  @Order(2)
  void explorerShowsProjectAndRootNodes() throws Exception {
    JsonArray roots = h.call("explorer/roots", null).getAsJsonArray();
    assertEquals(1, roots.size());
    JsonObject proj = roots.get(0).getAsJsonObject();
    projectItem = proj.get("id").getAsString();
    assertEquals("project:TS103096", projectItem);
    assertEquals("TS103096-STF517", proj.get("label").getAsString());
    assertTrue(proj.get("contextValue").getAsString().contains("is.IProject"));
    JsonArray ch = h.children(projectItem);
    // Types are hidden by the Types filter, active by default.
    assertEquals(List.of("Documents", "Reports", "Requirements"), labels(ch));
    JsonObject reqs = ServerHarness.find(ch, "label", "Requirements");
    assertEquals("Requirement", reqs.get("type").getAsString());
    assertTrue(reqs.get("contextValue").getAsString().matches(".*\\bis\\.Requirement\\b.*\\bis\\.TreeNode\\b.*"));
    for (JsonElement e : ch)
      assertTrue(new File(e.getAsJsonObject().get("icon").getAsString()).isFile(), e.toString());
    JsonObject reports = ServerHarness.find(ch, "label", "Reports");
    assertTrue(reports.get("icon").getAsString().endsWith("data.gif"));
    // ReportFolder is a registered node class (plugin.xml nodetype), not a generic TreeNode.
    assertTrue(reports.get("contextValue").getAsString().contains("is.ReportFolder"), reports.toString());
  }

  @Test
  @Order(3)
  void filtersChangeVisibleTypes() throws Exception {
    JsonObject f = new JsonObject();
    f.addProperty("types", false);
    h.call("explorer/setFilters", "filters", f);
    // NodeType.toString() is the Explorer label in Eclipse too (RequalityWorkbenchAdapter.getLabel).
    List<String> withTypes = labels(h.children(projectItem));
    assertEquals(4, withTypes.size());
    assertEquals(List.of("Documents", "Reports", "Requirements"), withTypes.subList(0, 3));
    assertTrue(withTypes.get(3).startsWith("TypeNode 'Types'"), withTypes.get(3));
    f.addProperty("types", true);
    f.addProperty("requirements", true);
    h.call("explorer/setFilters", "filters", f);
    assertEquals(List.of("Documents", "Reports"), labels(h.children(projectItem)));
    h.call("explorer/setFilters", "filters", new JsonObject());
    assertEquals(List.of("Documents", "Reports", "Requirements"), labels(h.children(projectItem)));
  }

  @Test
  @Order(4)
  void parentsAndItemsResolve() throws Exception {
    JsonObject reqs = ServerHarness.find(h.children(projectItem), "label", "Requirements");
    String reqsId = reqs.get("id").getAsString();
    assertEquals(projectItem, h.call("explorer/parent", "id", reqsId).getAsString());
    JsonObject child = h.children(reqsId).get(0).getAsJsonObject();
    assertEquals(reqsId, h.call("explorer/parent", "id", child.get("id").getAsString()).getAsString());
    assertEquals(child.get("label"), h.call("explorer/item", "id", child.get("id").getAsString()).getAsJsonObject().get("label"));
  }

  /** The URL the Browser of an editor shell shows (JSEditor.createPartControl: page/?inTool=1&port=..&session=..). */
  static String browserUrl(String shell) throws Exception {
    return browserUrl(h, shell);
  }

  static String browserUrl(ServerHarness h, String shell) throws Exception {
    String[] url = new String[1];
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("http://127\\.0\\.0\\.1:\\d+/[^\"\\s]*session=[^&\"]+&");
    h.await("editor url", v -> {
      try {
        java.util.regex.Matcher m = p.matcher(h.call("ui/snapshot", "shell", shell).toString());
        if (m.find())
          url[0] = m.group();
        return url[0] != null;
      } catch (Exception e) {
        return false;
      }
    });
    return url[0];
  }

  static String query(String url, String key) {
    for (String kv : url.substring(url.indexOf('?') + 1).split("&"))
      if (kv.startsWith(key + "="))
        return kv.substring(key.length() + 1);
    return null;
  }

  @Test
  @Order(5)
  void uniEditorPageServesNodesOverWebSocket() throws Exception {
    JsonObject reqs = ServerHarness.find(h.children(projectItem), "label", "Requirements");
    // UniEditorOpen (Edit in UniEditor): the ported UniEditor part shows the page of the resources server.
    int before = h.notifications("ui/openShell").size();
    JsonObject ex = new JsonObject();
    ex.addProperty("command", "com.unitesk.requality.commands.UniEditorOpen");
    JsonArray nodes = new JsonArray();
    nodes.add(reqs.get("id").getAsString());
    ex.add("nodes", nodes);
    h.call("command/execute", ex);
    JsonObject shell = h.awaitPanelShell(before);
    String shellId = shell.get("shell").getAsString();
    // JSEditor.updateTabLabel (a UIJob): "UniEditor - Requirements (Requirement)".
    h.await("editor title", v -> h.notifications("ui/update").stream().anyMatch(u -> u.get("shell").getAsString().equals(shellId)
        && u.toString().contains("UniEditor - Requirements (Requirement)")));
    String url = browserUrl(shellId);
    HttpClient http = HttpClient.newHttpClient();
    String html = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString()).body();
    assertTrue(html.contains("jseditor.js"), html);
    String bridge = http.send(HttpRequest.newBuilder(URI.create(url.substring(0, url.indexOf("?")) + "varStorage.js")).build(),
        HttpResponse.BodyHandlers.ofString()).body();
    assertTrue(bridge.contains("requality-vscode bridge"));

    // The page protocol (sockets.js): init, then loadNodeTreeByUUID.
    List<String> received = new CopyOnWriteArrayList<>();
    CompletableFuture<Void> gotTree = new CompletableFuture<>();
    String session = query(url, "session");
    WebSocket ws = http.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + query(url, "port")),
        new WebSocket.Listener() {
          final StringBuilder buf = new StringBuilder();

          @Override
          public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
              String msg = buf.toString();
              buf.setLength(0);
              received.add(msg);
              if (msg.contains("\"sendLoadNodeTreeByUUIDResult\"") || msg.contains("loadNodeTreeByUUID"))
                gotTree.complete(null);
            }
            w.request(1);
            return null;
          }
        }).get(30, TimeUnit.SECONDS);
    String doc = reqs.get("id").getAsString().split("::")[1];
    ws.sendText("{\"operation\":\"init\",\"location\":\"" + url + "\",\"session\":\"" + session + "\",\"document\":\"" + doc + "\"}", true).get();
    long end = System.currentTimeMillis() + 30000;
    while (received.stream().noneMatch(m -> m.contains("\"init\"")) && System.currentTimeMillis() < end)
      Thread.sleep(50);
    ws.sendText("{\"operation\":\"sendInited\",\"location\":\"" + url + "\",\"session\":\"" + session + "\"}", true).get();
    ws.sendText("{\"operation\":\"loadNodeTreeByUUID\",\"node\":\"" + doc + "\",\"location\":\"" + url + "\",\"session\":\"" + session + "\"}", true).get();
    gotTree.get(30, TimeUnit.SECONDS);
    String tree = received.stream().filter(m -> m.contains(doc) && m.contains("\"_type\"")).findFirst().orElse(null);
    assertNotNull(tree, "node data with _type: " + received);
    JsonObject msg = JsonParser.parseString(tree).getAsJsonObject();
    assertFalse(msg.getAsJsonObject("data").getAsJsonArray("data").isEmpty());
    // The page focuses a node (setRequalityFocus): the editor's selection is the workbench selection, which the
    // Properties view (PropertySheet) shows.
    h.call("properties/open", null);
    ws.sendText("{\"operation\":\"setRequalityFocus\",\"node\":\"" + doc + "\",\"location\":\"" + url + "\",\"session\":\"" + session + "\"}", true).get();
    h.await("properties follow the editor", v -> {
      Object sel = com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncCall(() -> {
        com.unitesk.requality.vscode.ui.jface.viewers.ISelection s = com.unitesk.requality.vscode.services.PropertiesService.page().getCurrentSelection();
        return s instanceof com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection
            ? ((com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) s).getFirstElement() : null;
      });
      return sel instanceof com.unitesk.requality.core.model.TreeNode
          && ((com.unitesk.requality.core.model.TreeNode) sel).getUUId().toString().equals(doc);
    });
    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
    h.closeShell(shellId);
  }

  @Test
  @Order(6)
  void explorerOpenRunsTheOpenAction() throws Exception {
    // CNFActionProvider's OPEN action (OpenAction -> OpenNode): a document opens the Document Editor (ReqMarker).
    JsonArray docs = h.call("nodes/list", "project", "TS103096", "types", List.of("Document")).getAsJsonArray();
    assertEquals(1, docs.size());
    int before = h.notifications("ui/openShell").size();
    JsonArray nodes = new JsonArray();
    nodes.add(docs.get(0).getAsJsonObject().get("id").getAsString());
    JsonObject p = new JsonObject();
    p.add("nodes", nodes);
    assertTrue(h.call("explorer/open", p).getAsBoolean());
    JsonObject shell = h.awaitPanelShell(before);
    String url = browserUrl(shell.get("shell").getAsString());
    assertTrue(url.contains("/documents/"), url);
    h.closeShell(shell.get("shell").getAsString());
  }

  static List<String> labels(JsonArray items) {
    List<String> r = new java.util.ArrayList<>();
    for (JsonElement e : items)
      r.add(e.getAsJsonObject().get("label").getAsString());
    return r;
  }
}
