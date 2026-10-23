package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * The Team, Compare With and Replace With submenus of the Requality Explorer (org.eclipse.team.ui and org.eclipse.compare
 * popupMenus contributions) on the ETSI catalog and a plain Eclipse project: Compare With &gt; Each Other of files and of
 * Requality nodes.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamCompareTest {
  static final String POPUP = ProjectOperationsTest.POPUP;
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "Plain");
    new File(plain, "sub").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "a.json").toPath(), "{\"a\": 1}\n");
    Files.writeString(new File(plain, "b.json").toPath(), "{\"b\": 2}\n");
    Files.writeString(new File(plain, "sub/c.json").toPath(), "{\"c\": 3}\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await2("the plain project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Plain"))
          return e;
      return null;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String file(String name) {
    return ExplorerService.RESOURCE_PREFIX + new File(plain, name).getAbsolutePath();
  }

  /** The explorer popup of the items: the top-level items (label, enabled, index, children). */
  static JsonArray popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The item with the label in the list (searching the submenus), or null. */
  static JsonObject item(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (label.equals(o.has("label") ? o.get("label").getAsString() : null))
        return o;
      if (o.has("children")) {
        JsonObject c = item(o.getAsJsonArray("children"), label);
        if (c != null)
          return c;
      }
    }
    return null;
  }

  static JsonObject submenu(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (label.equals(o.has("label") ? o.get("label").getAsString() : null) && o.has("children"))
        return o;
    }
    return null;
  }

  /** Runs the item of the last popup request (by its index). */
  static void run(JsonObject item) throws Exception {
    runAsync(item).get(60, java.util.concurrent.TimeUnit.SECONDS);
  }

  /** Runs the item; the answer comes when the action ends (a dialog of the action is answered by the test). */
  static java.util.concurrent.CompletableFuture<JsonElement> runAsync(JsonObject item) {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  @Test
  @Order(1)
  void fileHasTeamAndCompareWithMenus() throws Exception {
    JsonArray items = popup(file("a.json"));
    assertNotNull(submenu(items, "Team"), "Team submenu");
    assertNotNull(item(items, "Show Local History"), "Show Local History");
    JsonObject compare = submenu(items, "Compare With");
    assertNotNull(compare, "Compare With submenu");
    assertNotNull(item(compare.getAsJsonArray("children"), "Local History..."));
    // Each Other needs two elements (enablesFor="2+"): Eclipse shows it disabled for one (PluginAction.selectionChanged)
    JsonObject each = item(compare.getAsJsonArray("children"), "Each Other");
    assertNotNull(each, "Each Other of one file");
    assertFalse(each.get("enabled").getAsBoolean(), "Each Other of one file is disabled");
    JsonObject replace = submenu(items, "Replace With");
    assertNotNull(replace, "Replace With submenu");
    assertNotNull(item(replace.getAsJsonArray("children"), "Local History..."), "Replace With > Local History...");
    assertNotNull(item(replace.getAsJsonArray("children"), "Previous from Local History"), "Previous from Local History");
    assertNull(item(items, "Restore from Local History..."), "Restore from Local History... is for folders and projects");
  }

  @Test
  @Order(2)
  void twoFilesCompareEachOther() throws Exception {
    JsonArray items = popup(file("a.json"), file("b.json"));
    JsonObject each = item(submenu(items, "Compare With").getAsJsonArray("children"), "Each Other");
    assertNotNull(each);
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of two files");
    int before = h.notifications("ui/openShell").size();
    run(each);
    h.awaitPanelShell(before);
  }

  @Test
  @Order(3)
  void twoRequalityNodesCompareEachOther() throws Exception {
    JsonArray docs = h.call("nodes/list", "project", "TS103096", "types", List.of("Requirement")).getAsJsonArray();
    assertTrue(docs.size() >= 2, "two requirements of the catalog");
    String a = docs.get(0).getAsJsonObject().get("id").getAsString();
    String b = docs.get(1).getAsJsonObject().get("id").getAsString();
    JsonArray items = popup(a, b);
    JsonObject compare = submenu(items, "Compare With");
    assertNotNull(compare, "Compare With of the nodes");
    JsonObject each = item(compare.getAsJsonArray("children"), "Each Other");
    assertNotNull(each);
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of two nodes");
    int before = h.notifications("ui/openShell").size();
    run(each);
    h.awaitPanelShell(before);
  }

  /** Saves a state of the file in the local history: its contents are replaced with KEEP_HISTORY. */
  static void replaceKeepingHistory(File f, String contents) throws Exception {
    ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(f.getAbsolutePath()))
        .setContents(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), IResource.KEEP_HISTORY, null);
  }

  @Test
  @Order(4)
  void showLocalHistoryOpensTheHistoryView() throws Exception {
    Files.writeString(new File(plain, "a.json").toPath(), "{\"a\": 1}\n");
    replaceKeepingHistory(new File(plain, "a.json"), "{\"a\": 10}\n");
    JsonArray items = popup(file("a.json"));
    JsonObject show = item(items, "Show Local History");
    assertNotNull(show);
    assertTrue(show.get("enabled").getAsBoolean(), "Show Local History of a file");
    run(show);
    // the History view is a view of the Requality panel: the server creates its shell when the view is shown (views/open)
    h.call("views/open", "view", "requality.history");
    h.await("the History view", v -> h.notifications("ui/openShell").stream()
        .anyMatch(n -> "view".equals(n.has("host") ? n.get("host").getAsString() : null)
            && "requality.history".equals(n.has("view") ? n.get("view").getAsString() : null)));
  }

  @Test
  @Order(5)
  void replaceWithPreviousRestoresThePreviousState() throws Exception {
    Files.writeString(new File(plain, "b.json").toPath(), "{\"b\": 2}\n");
    replaceKeepingHistory(new File(plain, "b.json"), "{\"b\": 3}\n");
    assertEquals("{\"b\": 3}\n", Files.readString(new File(plain, "b.json").toPath()));
    JsonArray items = popup(file("b.json"));
    JsonObject previous = item(submenu(items, "Replace With").getAsJsonArray("children"), "Previous from Local History");
    assertNotNull(previous);
    assertTrue(previous.get("enabled").getAsBoolean());
    run(previous);
    assertEquals("{\"b\": 2}\n", Files.readString(new File(plain, "b.json").toPath()));
  }

  @Test
  @Order(6)
  void restoreFromLocalHistoryRestoresADeletedFile() throws Exception {
    File c = new File(plain, "sub/c.json");
    ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(c.getAbsolutePath()))
        .delete(IResource.KEEP_HISTORY, null);
    assertFalse(c.exists());
    JsonArray items = popup(ExplorerService.RESOURCE_PREFIX + new File(plain, "sub").getAbsolutePath());
    JsonObject restore = item(items, "Restore from Local History...");
    assertNotNull(restore, "Restore from Local History... of a folder");
    assertTrue(restore.get("enabled").getAsBoolean());
    int before = h.notifications("ui/openShell").size();
    java.util.concurrent.CompletableFuture<JsonElement> ran = runAsync(restore);
    String sid = h.awaitShell(before).get("shell").getAsString();
    // AddFromHistoryDialog: Restore is enabled once a deleted file is checked
    String[] table = new String[1], row = new String[1];
    h.await("the deleted file in the dialog", v -> {
      try {
        for (JsonElement e : h.call("ui/snapshot", "shell", sid).getAsJsonObject().getAsJsonArray("ops")) {
          JsonObject o = e.getAsJsonObject();
          String type = o.has("type") ? o.get("type").getAsString() : "";
          if (type.equals("table") && table[0] == null)
            table[0] = o.get("id").getAsString();
          if (type.equals("row") && o.toString().contains("c.json"))
            row[0] = o.get("id").getAsString();
        }
        return table[0] != null && row[0] != null;
      } catch (Exception ex) {
        return false;
      }
    });
    JsonObject check = new JsonObject();
    check.addProperty("id", table[0]);
    check.addProperty("type", "check");
    JsonObject d = new JsonObject();
    d.addProperty("item", row[0]);
    d.addProperty("checked", true);
    check.add("data", d);
    h.notify("ui/event", check);
    Thread.sleep(300);
    h.pressDefaultButton(sid);
    ran.get(60, java.util.concurrent.TimeUnit.SECONDS);
    h.await("the restored file", v -> c.exists());
    assertEquals("{\"c\": 3}\n", Files.readString(c.toPath()));
  }

  @Test
  @Order(7)
  void shareProjectOpensTheWizardWithoutRepositoryTypes() throws Exception {
    JsonArray items = popup(plainItem());
    JsonObject share = item(items, "Share Project...");
    assertNotNull(share, "Share Project... of a project");
    assertTrue(share.get("enabled").getAsBoolean(), "Share Project... of a project without a provider");
    int before = h.notifications("ui/openShell").size();
    java.util.concurrent.CompletableFuture<JsonElement> ran = runAsync(share);
    JsonObject shell = h.awaitShell(before);
    assertTrue(h.call("ui/snapshot", "shell", shell.get("shell").getAsString()).toString().contains("Share Project"));
    h.closeShell(shell.get("shell").getAsString());
    ran.get(60, java.util.concurrent.TimeUnit.SECONDS);
  }

  /** The explorer item of the plain project (the project itself, not a file). */
  static String plainItem() throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith("Plain"))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  @Test
  @Order(8)
  void applyPatchAppliesADiffToAFile() throws Exception {
    Files.writeString(new File(plain, "d.json").toPath(), "{\n  \"x\": 1\n}\n");
    Files.writeString(new File(plain, "d.patch").toPath(), "--- d.json\n+++ d.json\n@@ -1,3 +1,3 @@\n {\n-  \"x\": 1\n+  \"x\": 2\n }\n");
    JsonArray items = popup(file("d.patch"));
    JsonObject apply = item(items, "Apply Patch...");
    assertNotNull(apply, "Apply Patch... of a patch file");
    assertTrue(apply.get("enabled").getAsBoolean());
    int before = h.notifications("ui/openShell").size();
    java.util.concurrent.CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = h.awaitShell(before).get("shell").getAsString();
    // the Apply Patch wizard opens on the target page of the workspace patch file (its folder is the target):
    // Next > to the review page, then Finish
    h.await("the target page", v -> {
      try {
        return button(sid, "Next >") != null;
      } catch (Exception e) {
        return false;
      }
    });
    pressButton(sid, "Next >");
    h.await("the review page", v -> {
      try {
        return h.call("ui/snapshot", "shell", sid).toString().contains("Review Patch");
      } catch (Exception e) {
        return false;
      }
    });
    pressButton(sid, "Finish");
    ran.get(60, java.util.concurrent.TimeUnit.SECONDS);
    h.await("the patched file", v -> {
      try {
        return Files.readString(new File(plain, "d.json").toPath()).contains("\"x\": 2");
      } catch (java.io.IOException e) {
        return false;
      }
    });
  }

  /** The button of the shell with the text (its snapshot op), or null. */
  static JsonObject button(String shell, String text) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("button".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props")
          && o.getAsJsonObject("props").has("text")
          && text.equals(o.getAsJsonObject("props").get("text").getAsString().replace("&", "")))
        return o;
    }
    return null;
  }

  static void pressButton(String shell, String text) throws Exception {
    JsonObject b = button(shell, text);
    assertNotNull(b, text);
    JsonObject p = new JsonObject();
    p.addProperty("id", b.get("id").getAsString());
    p.addProperty("type", "selection");
    p.add("data", new JsonObject());
    h.notify("ui/event", p);
  }
}
