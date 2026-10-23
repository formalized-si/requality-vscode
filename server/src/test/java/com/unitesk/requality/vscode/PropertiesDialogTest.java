package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
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
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/**
 * The Properties action of the Requality Explorer (org.eclipse.ui.navigator.resources PropertiesActionProvider) and
 * the property pages of the IDE that apply to resources: the Resource page of a file, the Project References page of
 * a project of the workspace (kept in its {@code .project}), and the Requality pages of the ETSI catalog. The RPC
 * {@code properties/openProject} opens the same dialog.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PropertiesDialogTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static final String PROPERTIES = "org.eclipse.ui.file.properties";
  static final String REFERENCES_PAGE = "org.eclipse.ui.propertypages.project.reference";
  static ServerHarness h;
  static String catalogItem;
  static String plainItem;
  static File plainDir;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    catalogItem = h.call("explorer/roots", null).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();

    // An Eclipse project without the catalog nature, with a file and a folder.
    plainDir = new File(h.workspace, "Plain");
    new File(plainDir, "sub").mkdirs();
    Files.writeString(new File(plainDir, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plainDir, "a.txt").toPath(), "a");
    Files.writeString(new File(plainDir, "sub/b.txt").toPath(), "b");
    h.call("workspace/rescan", null);
    plainItem = h.await2("the plain project", () -> itemOf("Plain"));
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The child of an explorer item with the label, or null. */
  static String child(String parent, String label) throws Exception {
    for (JsonElement e : h.children(parent))
      if (label.equals(e.getAsJsonObject().get("label").getAsString()))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** Label → enabled of the explorer popup for the items. */
  static Map<String, Boolean> popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    Map<String, Boolean> r = new java.util.LinkedHashMap<>();
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label"))
        r.put(o.get("label").getAsString(), o.get("enabled").getAsBoolean());
    }
    return r;
  }

  static void select(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  /** The Properties dialog of the project, through the RPC, on the page of pageId (or the first one). */
  static CompletableFuture<JsonElement> openProjectProperties(String item, String pageId) {
    JsonObject p = new JsonObject();
    p.addProperty("project", item);
    if (pageId != null)
      p.addProperty("page", pageId);
    return h.callAsync("properties/openProject", p);
  }

  /** The first widget of the type in the shell, or null. */
  static String firstOfType(String shell, String type) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("op") && "create".equals(o.get("op").getAsString()) && type.equals(o.get("type").getAsString()))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The table row with the text in its first column, or null. */
  static String row(String shell, String text) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("row".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props")
          && o.getAsJsonObject("props").has("texts") && text.equals(o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString()))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The created widget of the type with the text (ampersands ignored), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      String t = props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
      if (text == null || text.equals(t) || t != null && text.equals(t.replace("&", "")))
        return o.get("id").getAsString();
    }
    return null;
  }

  static void event(String id, String type, String key, Object value) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    JsonObject d = new JsonObject();
    if (key != null)
      d.add(key, com.unitesk.requality.vscode.rpc.JsonRpcConnection.toJson(value));
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** The dialog opened after {@code before} openShell notifications: its shell id and title. */
  static JsonObject awaitDialog(int before, CompletableFuture<JsonElement> call) throws Exception {
    try {
      return h.awaitPanelShell(before);
    } catch (AssertionError e) {
      if (call.isDone())
        throw new AssertionError("the call ended without a dialog: " + call.getNow(null), e);
      throw e;
    }
  }

  /** The charset names in the order of the encoding list of the page. */
  static List<String> charsetNames() {
    return new ArrayList<>(new TreeSet<>(Charset.availableCharsets().keySet()));
  }

  @Test
  @Order(1)
  void propertiesIsInThePopupOfProjectsAndFiles() throws Exception {
    String file = child(plainItem, "a.txt");
    assertNotNull(file, "a.txt is listed in the plain project");
    Map<String, Boolean> project = popup(plainItem);
    assertTrue(project.containsKey("Properties") && project.get("Properties"), project.toString());
    Map<String, Boolean> f = popup(file);
    assertTrue(f.containsKey("Properties") && f.get("Properties"), f.toString());
    // the catalog project too
    assertTrue(popup(catalogItem).containsKey("Properties"));
  }

  @Test
  @Order(2)
  void theResourcePageShowsThePathTypeAndLocationOfAFile() throws Exception {
    String file = child(plainItem, "a.txt");
    int before = h.notifications("ui/openShell").size();
    select(file);
    CompletableFuture<JsonElement> f = execute(PROPERTIES, file);
    JsonObject shell = awaitDialog(before, f);
    assertEquals("Properties for a.txt", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    assertTrue(snap.contains("/Plain/a.txt"), snap);
    assertTrue(snap.contains("modified") && snap.contains("File"), snap);
    File a = new File(plainDir, "a.txt");
    assertTrue(snap.contains(ServerHarness.jsonText(a.getCanonicalPath())) || snap.contains(ServerHarness.jsonText(a.getAbsolutePath())), snap);
    // the text file encoding: Other, and ISO-8859-1 (a charset of the list)
    String other = widget(sid, "button", "Other:");
    assertNotNull(other, snap);
    event(other, "selection", "selection", true);
    String combo = firstOfType(sid, "combo");
    assertNotNull(combo, snap);
    event(combo, "selection", "index", charsetNames().indexOf("ISO-8859-1"));
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    File prefs = new File(plainDir, ".settings/org.eclipse.core.resources.prefs");
    h.await("the encoding stored", v -> prefs.isFile());
    assertTrue(Files.readString(prefs.toPath()).contains("encoding/a.txt=ISO-8859-1"), Files.readString(prefs.toPath()));
    IProject plain = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    assertEquals("ISO-8859-1", plain.getFile("a.txt").getCharset());
    // the folder inherits the project's default: the project has none
    assertEquals("UTF-8", plain.getFile("sub/b.txt").getCharset());
  }

  @Test
  @Order(3)
  void aProjectReferenceIsStoredInTheProjectFile() throws Exception {
    File other = new File(h.workspace, "Other");
    other.mkdirs();
    Files.writeString(new File(other, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Other</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    h.call("workspace/rescan", null);
    h.await2("the other project", () -> itemOf("Other"));

    // The RPC opens the dialog of the project on its Project References page.
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openProjectProperties(plainItem, REFERENCES_PAGE);
    JsonObject shell = awaitDialog(before, f);
    assertEquals("Properties for Plain", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    String table = firstOfType(sid, "table");
    assertNotNull(table, h.call("ui/snapshot", "shell", sid).toString());
    String row = row(sid, "Other");
    assertNotNull(row, h.call("ui/snapshot", "shell", sid).toString());
    JsonObject check = new JsonObject();
    check.addProperty("item", row);
    check.addProperty("checked", true);
    JsonObject p = new JsonObject();
    p.addProperty("id", table);
    p.addProperty("type", "check");
    p.add("data", check);
    h.notify("ui/event", p);
    h.pressDefaultButton(sid);
    assertTrue(f.get(60, TimeUnit.SECONDS).getAsBoolean(), "OK closes the dialog");

    String project = Files.readString(new File(plainDir, ".project").toPath());
    assertTrue(project.contains("<project>Other</project>"), project);
    List<String> refs = new ArrayList<>();
    for (IProject ref : ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").getDescription().getReferencedProjects())
      refs.add(ref.getName());
    assertEquals(List.of("Other"), refs);
  }

  @Test
  @Order(4)
  void theRequalityProjectHasTheResourcePageAndTheRequalityPages() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openProjectProperties(catalogItem, null);
    JsonObject shell = awaitDialog(before, f);
    assertEquals("Properties for TS103096-STF517", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    assertTrue(snap.contains("Resource"), snap);
    assertTrue(snap.contains("Requality"), snap);
    assertTrue(snap.contains("Project References"), snap);
    h.closeShell(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(5)
  void aNodeHasNoPropertyPages() throws Exception {
    // PropertyDialogHandler, the default handler of the command: the explorer's nodes are not IAdaptable, the
    // PropertiesActionProvider does not apply to them, and the dialog tells that there are no property pages.
    // (MessageDialog.openInformation is a modal message of VS Code: client/showMessage.)
    JsonObject node = h.children(catalogItem).get(0).getAsJsonObject();
    int before = h.requests("client/showMessage").size();
    execute(PROPERTIES, node.get("id").getAsString()).get(60, TimeUnit.SECONDS);
    List<JsonObject> asked = h.requests("client/showMessage");
    assertEquals(before + 1, asked.size(), asked.toString());
    JsonObject m = asked.get(asked.size() - 1);
    assertEquals("Property Pages", m.get("title").getAsString(), m.toString());
    assertEquals("No property pages for " + node.get("label").getAsString() + ".", m.get("message").getAsString(), m.toString());
  }

}
