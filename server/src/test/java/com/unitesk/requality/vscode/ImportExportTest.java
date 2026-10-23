package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
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

/**
 * The Import... and Export... actions of the Requality Explorer (org.eclipse.ui.navigator.resources.PortingActions) and
 * the Import and Export wizard dialogs of the IDE: the categorized wizard selection, and the File System, Archive File
 * and Existing Projects into Workspace wizards on a plain project.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportExportTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static ServerHarness h;
  static String plainItem;
  static String fileItem;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "plain");
    new File(plain, "docs/sub").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "docs/readme.txt").toPath(), "text");
    Files.writeString(new File(plain, "docs/sub/a.txt").toPath(), "a");
    Files.writeString(new File(plain, "top.txt").toPath(), "top");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    plainItem = itemOf("plain");
    assertNotNull(plainItem);
    fileItem = null;
    for (JsonElement e : h.children(plainItem))
      if ("top.txt".equals(e.getAsJsonObject().get("label").getAsString()))
        fileItem = e.getAsJsonObject().get("id").getAsString();
    assertNotNull(fileItem);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** The explorer item whose label starts with the name, or null. */
  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /**
   * The created widget of a shell with the type and text (first match), or null. The text of a tree item or a table row is
   * one of its {@code texts} (the column texts); the text of the other widgets is their {@code text}.
   */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (text == null)
        return o.get("id").getAsString();
      List<String> texts = new java.util.ArrayList<>();
      if (props.has("text") && !props.get("text").isJsonNull())
        texts.add(props.get("text").getAsString());
      if (props.has("texts"))
        for (JsonElement t : props.getAsJsonArray("texts"))
          if (!t.isJsonNull())
            texts.add(t.getAsString());
      for (String t : texts)
        if (text.equals(t) || text.equals(t.replace("&", "")))
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

  /** The wizard dialog shell with the title, opened after the given count of opened shells. */
  static JsonObject awaitWizardShell(int before, String title) throws Exception {
    JsonObject[] found = new JsonObject[1];
    h.await("the " + title + " dialog", v -> {
      List<JsonObject> l = h.notifications("ui/openShell");
      for (int k = before; k < l.size(); k++)
        if (title.equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null)) {
          found[0] = l.get(k);
          return true;
        }
      return false;
    });
    return found[0];
  }

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  static void select(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  static Map<String, Boolean> popup(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    Map<String, Boolean> r = new java.util.LinkedHashMap<>();
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && o.has("enabled"))
        r.put(o.get("label").getAsString(), o.get("enabled").getAsBoolean());
    }
    return r;
  }

  /** The text of every widget of the shell, for the checks of the content. */
  static String text(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** Closes the wizard dialog like the user closing it. */
  static void close(String shell) {
    h.closeShell(shell);
  }

  @Test
  @Order(1)
  void popupsOfAProjectAndAFileHaveImportAndExport() throws Exception {
    for (String item : new String[] { plainItem, fileItem }) {
      Map<String, Boolean> m = popup(item);
      assertTrue(m.containsKey("Import...") && m.get("Import..."), item + " " + m);
      assertTrue(m.containsKey("Export...") && m.get("Export..."), item + " " + m);
    }
  }

  @Test
  @Order(2)
  void importCommandOpensTheWizardSelectionWithTheCategories() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.import", plainItem);
    JsonObject shell = awaitWizardShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    String snap = text(sid);
    for (String s : new String[] { "General", "Requality", "Archive File", "Existing Projects into Workspace",
        "File System", "Import from ReqIF", "Document" })
      assertTrue(snap.contains(s), s + " in the Import dialog");
    close(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(3)
  void exportCommandOpensTheWizardSelectionWithTheCategories() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.export", plainItem);
    JsonObject shell = awaitWizardShell(before, "Export");
    String sid = shell.get("shell").getAsString();
    String snap = text(sid);
    for (String s : new String[] { "General", "Requality", "Archive File", "File System", "Export to ReqIF" })
      assertTrue(snap.contains(s), s + " in the Export dialog");
    close(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(4)
  void theFilterTextNarrowsTheTree() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.import", plainItem);
    JsonObject shell = awaitWizardShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    String filter = widget(sid, "text", null);
    assertNotNull(filter);
    event(filter, "modify", "text", "archive");
    h.await("the filtered tree", v -> {
      try {
        String s = text(sid);
        return s.contains("Archive File") && !s.contains("File System") && !s.contains("Export to ReqIF");
      } catch (Exception e) {
        return false;
      }
    });
    close(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  /** A tree or table item event with the data of the item (check and selection). */
  static void eventData(String id, String type, JsonObject data) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    p.add("data", data);
    h.notify("ui/event", p);
  }

  /** Checks or unchecks a tree item (CheckboxTreeViewer) of a tree widget. */
  static void checkItem(String tree, String item, boolean checked) {
    JsonObject d = new JsonObject();
    d.addProperty("item", item);
    d.addProperty("checked", checked);
    eventData(tree, "check", d);
  }

  /** Selects one item of a tree widget. */
  static void selectItem(String tree, String item) {
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    eventData(tree, "selection", d);
  }

  /** Presses a button of the shell by its text. */
  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button");
    awaitEnabled(shell, b);
    event(b, "selection", null, null);
  }

  /**
   * Waits (up to 10 s) until the button is enabled: a running operation of the wizard (WizardDialog.run) disables the
   * buttons until it ends, as in Eclipse. A button that stays disabled is pressed anyway (the press is then ignored).
   */
  static void awaitEnabled(String shell, String id) throws Exception {
    long end = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < end) {
      boolean enabled = false;
      for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
        JsonObject o = e.getAsJsonObject();
        if (id.equals(o.get("id").getAsString()))
          enabled = !(o.has("props") && o.getAsJsonObject("props").has("enabled")
              && !o.getAsJsonObject("props").get("enabled").getAsBoolean());
      }
      if (enabled)
        return;
      Thread.sleep(50);
    }
  }

  /** Waits until the shell has a widget of the type and text (the failure shows the snapshot of the shell). */
  static void awaitWidget(String shell, String type, String text) throws Exception {
    try {
      h.await(type + " " + text, v -> {
        try {
          return widget(shell, type, text) != null;
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + h.call("ui/snapshot", "shell", shell), e);
    }
  }

  /** The last created widget of the type in the shell (the widgets of the current page), or null. */
  static String lastWidget(String shell, String type) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    String last = null;
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (type.equals(o.has("type") ? o.get("type").getAsString() : null))
        last = o.get("id").getAsString();
    }
    return last;
  }

  @Test
  @Order(5)
  void fileSystemImportCopiesTheSelectedFiles() throws Exception {
    File source = new File(h.root, "fs-source");
    new File(source, "nested").mkdirs();
    Files.writeString(new File(source, "a.txt").toPath(), "alpha");
    Files.writeString(new File(source, "nested/b.txt").toPath(), "beta");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.import", plainItem);
    JsonObject shell = awaitWizardShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "File System"));
    press(sid, "Next >");
    awaitWidget(sid, "combo", null);
    h.answer("client/directoryDialog", p -> source.getAbsolutePath());
    press(sid, "Browse...");
    awaitWidget(sid, "row", "a.txt");

    // destination: the plain project
    event(lastWidget(sid, "text"), "modify", "text", "/plain");
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    assertEquals("alpha", Files.readString(new File(plain, "a.txt").toPath()));
    assertEquals("beta", Files.readString(new File(plain, "nested/b.txt").toPath()));
  }

  @Test
  @Order(6)
  void archiveExportWritesTheCheckedResources() throws Exception {
    File out = new File(h.root, "plain-out.zip");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.export", plainItem);
    JsonObject shell = awaitWizardShell(before, "Export");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Archive File"));
    press(sid, "Next >");
    awaitWidget(sid, "treeitem", "plain");
    checkItem(lastWidget(sid, "tree"), widget(sid, "treeitem", "plain"), true);
    h.answer("client/fileDialog", p -> {
      JsonArray a = new JsonArray();
      a.add(out.getAbsolutePath());
      return a;
    });
    press(sid, "Browse...");
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    java.util.Set<String> entries = new java.util.TreeSet<>();
    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(out)) {
      zip.stream().forEach(e -> entries.add(e.getName()));
    }
    assertTrue(entries.contains("plain/top.txt"), entries.toString());
    assertTrue(entries.contains("plain/docs/readme.txt"), entries.toString());
    assertTrue(entries.contains("plain/docs/sub/a.txt"), entries.toString());
  }

  @Test
  @Order(7)
  void archiveImportExtractsTheCheckedEntries() throws Exception {
    File zip = new File(h.root, "plain-out.zip");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.import", plainItem);
    JsonObject shell = awaitWizardShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Archive File"));
    press(sid, "Next >");
    awaitWidget(sid, "label", "From archive file:");
    h.answer("client/fileDialog", p -> {
      JsonArray a = new JsonArray();
      a.add(zip.getAbsolutePath());
      return a;
    });
    press(sid, "Browse...");
    awaitWidget(sid, "treeitem", "plain");
    press(sid, "Select All");
    event(lastWidget(sid, "text"), "modify", "text", "/plain/imported");
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    assertEquals("top", Files.readString(new File(plain, "imported/plain/top.txt").toPath()));
    assertEquals("a", Files.readString(new File(plain, "imported/plain/docs/sub/a.txt").toPath()));
  }

  @Test
  @Order(8)
  void fileSystemExportCopiesTheCheckedResourcesOut() throws Exception {
    File out = new File(h.root, "fs-out");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.export", plainItem);
    JsonObject shell = awaitWizardShell(before, "Export");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "File System"));
    press(sid, "Next >");
    awaitWidget(sid, "treeitem", "plain");
    checkItem(lastWidget(sid, "tree"), widget(sid, "treeitem", "plain"), true);
    h.answer("client/directoryDialog", p -> out.getAbsolutePath());
    press(sid, "Browse...");
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    assertEquals("top", Files.readString(new File(out, "plain/top.txt").toPath()));
    assertEquals("text", Files.readString(new File(out, "plain/docs/readme.txt").toPath()));
  }

  @Test
  @Order(9)
  void existingProjectsOutsideTheWorkspaceAreAdded() throws Exception {
    File ext = new File(h.root, "external/extproj");
    new File(ext, "src").mkdirs();
    Files.writeString(new File(ext, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>extproj</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(ext, "src/x.txt").toPath(), "x");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.import", plainItem);
    JsonObject shell = awaitWizardShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Existing Projects into Workspace"));
    press(sid, "Next >");
    awaitWidget(sid, "button", "Select root directory:");
    h.answer("client/directoryDialog", p -> new File(h.root, "external").getAbsolutePath());
    press(sid, "Browse...");
    h.await("the project list", v -> {
      try {
        return widget(sid, "row", null) != null;
      } catch (Exception e) {
        return false;
      }
    });
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    assertNotNull(h.server().workspace().project(ext));
  }

  @Test
  @Order(10)
  void preferencesExportAndImportRoundTrip() throws Exception {
    com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore prefs =
        com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore.getPlugin();
    prefs.setValue("export.test.key", "exported value");
    File epf = new File(h.root, "requality.epf");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.export", plainItem);
    JsonObject shell = awaitWizardShell(before, "Export");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Preferences"));
    press(sid, "Next >");
    awaitWidget(sid, "label", "To preference file:");
    event(lastWidget(sid, "text"), "modify", "text", epf.getAbsolutePath());
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    String written = Files.readString(epf.toPath());
    assertTrue(written.contains("<entry key=\"export.test.key\" value=\"exported value\"/>"), written);

    prefs.setValue("export.test.key", "changed");
    before = h.notifications("ui/openShell").size();
    f = execute("org.eclipse.ui.file.import", plainItem);
    shell = awaitWizardShell(before, "Import");
    sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Preferences"));
    press(sid, "Next >");
    awaitWidget(sid, "label", "From preference file:");
    event(lastWidget(sid, "text"), "modify", "text", epf.getAbsolutePath());
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    assertEquals("exported value", prefs.getString("export.test.key"));
  }
}
