package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
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
 * The LTK refactoring wizards of the resource actions of the navigator (org.eclipse.ltk.ui.refactoring): "Rename
 * Resource", "Delete Resources" and "Move Resources" open their dialogs through the LTK commands, show the Preview
 * (Preview >), accept or cancel the change, and their changes are undone with the labels of Eclipse.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LtkResourceWizardsTest {
  static ServerHarness h;
  static File plain;
  static String plainItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = project("Plain");
    new File(plain, "sub/deep").mkdirs();
    Files.writeString(new File(plain, "a.txt").toPath(), "alpha");
    Files.writeString(new File(plain, "sub/b.txt").toPath(), "bravo");
    Files.writeString(new File(plain, "sub/deep/c.txt").toPath(), "charlie");
    // a project with a nested project (DeleteResourcesWizard.computeNestedProjects)
    File outer = project("Outer");
    File inner = new File(outer, "Inner");
    inner.mkdirs();
    Files.writeString(new File(inner, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Inner</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    plainItem = h.await2("the plain project", () -> itemOf("Plain"));
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static File project(String name) throws Exception {
    File dir = new File(h.workspace, name);
    dir.mkdirs();
    Files.writeString(new File(dir, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>" + name
            + "</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures>"
            + "</projectDescription>\n");
    return dir;
  }

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  static String label(String command, String item) throws Exception {
    JsonObject p = new JsonObject();
    JsonArray cmds = new JsonArray();
    cmds.add(command);
    p.add("commands", cmds);
    JsonObject sel = new JsonObject();
    sel.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    sel.add("nodes", nodes);
    h.call("selection/set", sel);
    return h.call("command/state", p).getAsJsonObject().getAsJsonObject(command).get("label").getAsString();
  }

  /** The widget of a shell with the type and text (the mnemonics are ignored), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      String t = props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
      // a tree item keeps its texts (one per column)
      if (t == null && props.has("texts") && props.getAsJsonArray("texts").size() > 0)
        t = props.getAsJsonArray("texts").get(0).isJsonNull() ? null : props.getAsJsonArray("texts").get(0).getAsString();
      if (text == null || text.equals(t) || t != null && text.equals(t.replace("&", "")))
        return o.get("id").getAsString();
    }
    return null;
  }

  static String text(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
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

  static void press(String shell, String button) throws Exception {
    String b = widget(shell, "button", button);
    assertNotNull(b, button + " button of " + text(shell));
    h.awaitEnabled(shell, b);
    event(b, "selection", null, null);
  }

  /** Selects an item of a tree widget by the id of the item. */
  static void selectItem(String tree, String item) {
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  static void awaitWidget(String shell, String type, String label) throws Exception {
    try {
      h.await("the " + type + " " + label, v -> {
        try {
          return widget(shell, type, label) != null;
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + text(shell), e);
    }
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static String child(String parent, String label) throws Exception {
    for (JsonElement e : h.children(parent))
      if (label.equals(e.getAsJsonObject().get("label").getAsString()))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static String read(File f) throws Exception {
    return Files.readString(f.toPath());
  }

  @Test
  @Order(1)
  void renameResourceShowsThePreviewAndUndoRestoresTheName() throws Exception {
    String a = child(plainItem, "a.txt");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.rename", a);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Rename Resource", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("New name:"), text(sid));
    assertNotNull(widget(sid, "button", "OK"), text(sid));
    assertNotNull(widget(sid, "button", "Cancel"), text(sid));
    assertNotNull(widget(sid, "button", "Preview >"), text(sid));

    // an invalid name is reported on the page
    String name = widget(sid, "text", null);
    event(name, "modify", "text", "x/y.txt");
    h.await("the invalid name message", v -> {
      try {
        return text(sid).contains("This is an invalid name for a file or folder");
      } catch (Exception e) {
        return false;
      }
    });

    // the preview lists the rename
    event(name, "modify", "text", "renamed.txt");
    press(sid, "Preview >");
    awaitWidget(sid, "treeitem", "Rename 'a.txt' to 'renamed.txt'");
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    File renamed = new File(plain, "renamed.txt");
    h.await("renamed", v -> renamed.isFile() && !new File(plain, "a.txt").exists());
    assertEquals("alpha", read(renamed));

    assertEquals("Undo Rename Resource", label("org.eclipse.ui.edit.undo", plainItem));
    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the name restored", v -> new File(plain, "a.txt").isFile() && !renamed.exists());
  }

  @Test
  @Order(2)
  void renameResourceCancelKeepsTheName() throws Exception {
    String a = child(plainItem, "a.txt");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.rename", a);
    JsonObject shell = h.awaitPanelShell(before);
    String sid = shell.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "canceled.txt");
    press(sid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
    assertTrue(new File(plain, "a.txt").isFile(), "the name is kept");
    assertFalse(new File(plain, "canceled.txt").exists());
  }

  @Test
  @Order(3)
  void deleteResourcesPreviewsTheDeletionAndUndoRestoresIt() throws Exception {
    String sub = child(plainItem, "sub");
    String b = child(sub, "b.txt");
    File file = new File(plain, "sub/b.txt");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", b);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Delete Resources", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("Delete 'b.txt' from the file system?"), text(sid));
    assertFalse(widget(sid, "button", "Delete project contents on disk (cannot be undone)") != null, text(sid));
    press(sid, "Preview >");
    awaitWidget(sid, "treeitem", "Delete 'b.txt'");
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("deleted", v -> !file.exists());
    assertEquals("Undo Delete Resources", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("restored", v -> file.isFile());
    assertEquals("bravo", read(file), "the contents are restored");
  }

  @Test
  @Order(4)
  void deleteProjectOffersTheContentsOnDiskAndCancelKeepsIt() throws Exception {
    File scratch = project("Scratch");
    Files.writeString(new File(scratch, "x.txt").toPath(), "x");
    h.call("workspace/rescan", null);
    String item = h.await2("Scratch", () -> itemOf("Scratch"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", item);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Delete Resources", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("Remove project 'Scratch' from the workspace?"), text(sid));
    assertNotNull(widget(sid, "button", "Delete project contents on disk (cannot be undone)"), text(sid));
    press(sid, "Cancel");
    f.get(120, TimeUnit.SECONDS);
    assertTrue(scratch.isDirectory() && new File(scratch, "x.txt").isFile(), "the project stays");
    assertTrue(itemOf("Scratch") != null, "the project stays listed");
  }

  @Test
  @Order(5)
  void moveResourcesChoosesADestinationAndUndoRestoresIt() throws Exception {
    String sub = child(plainItem, "sub");
    String b = child(sub, "b.txt");
    File moved = new File(plain, "b.txt");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.move", b);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Move Resources", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("Choose destination for 'b.txt':"), text(sid));
    awaitWidget(sid, "treeitem", "Plain");
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Plain"));
    Thread.sleep(1500);
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("moved to the project root", v -> moved.isFile() && !new File(plain, "sub/b.txt").exists());
    assertEquals("Undo Move Resources", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("moved back", v -> new File(plain, "sub/b.txt").isFile() && !moved.exists());
    assertEquals("bravo", read(new File(plain, "sub/b.txt")));
  }

  @Test
  @Order(6)
  void anUncheckedChangeOfThePreviewIsNotPerformed() throws Exception {
    File x = new File(plain, "x.txt");
    File y = new File(plain, "y.txt");
    Files.writeString(x.toPath(), "x");
    Files.writeString(y.toPath(), "y");
    String xi = h.await2("x.txt", () -> child(plainItem, "x.txt"));
    String yi = h.await2("y.txt", () -> child(plainItem, "y.txt"));
    JsonObject p = new JsonObject();
    p.addProperty("command", "org.eclipse.ui.edit.delete");
    JsonArray nodes = new JsonArray();
    nodes.add(xi);
    nodes.add(yi);
    p.add("nodes", nodes);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = h.callAsync("command/execute", p);
    String sid = h.awaitPanelShell(before).get("shell").getAsString();
    press(sid, "Preview >");
    awaitWidget(sid, "treeitem", "Delete 'y.txt'");
    // ChangeElement.setEnabled: the change unchecked in the preview tree is not performed
    JsonObject d = new JsonObject();
    d.addProperty("item", widget(sid, "treeitem", "Delete 'y.txt'"));
    d.addProperty("checked", false);
    JsonObject check = new JsonObject();
    check.addProperty("id", widget(sid, "tree", null));
    check.addProperty("type", "check");
    check.add("data", d);
    h.notify("ui/event", check);
    h.call("ui/snapshot", "shell", sid);
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("x.txt deleted", v -> !x.exists());
    assertTrue(y.isFile(), "the unchecked file is kept");
  }

  @Test
  @Order(7)
  void aFileWithUnsavedChangesIsAWarning() throws Exception {
    File z = new File(plain, "z.txt");
    Files.writeString(z.toPath(), "z");
    String zi = h.await2("z.txt", () -> child(plainItem, "z.txt"));
    // the VS Code editor of z.txt has unsaved changes
    h.answer("client/dirtyDocuments", p -> java.util.List.of(z.getAbsolutePath()));
    try {
      int before = h.notifications("ui/openShell").size();
      CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", zi);
      String sid = h.awaitPanelShell(before).get("shell").getAsString();
      int problems = h.notifications("ui/openShell").size();
      press(sid, "OK");
      // DeleteResourcesProcessor.checkDirtyResources: "Found problems" with the warning, Continue deletes
      String pid = h.await2("the problems page", () -> {
        java.util.List<JsonObject> l = h.notifications("ui/openShell");
        for (int k = problems; k < l.size(); k++)
          return l.get(k).get("shell").getAsString();
        return null;
      });
      awaitWidget(pid, "row", null);
      assertTrue(text(pid).contains("File 'Plain/z.txt' has unsaved changes."), text(pid));
      press(pid, "Continue");
      f.get(60, TimeUnit.SECONDS);
      h.await("z.txt deleted", v -> !z.exists());
    } finally {
      h.answer("client/dirtyDocuments", p -> java.util.List.of());
    }
  }

  @Test
  @Order(8)
  void deleteProjectOffersItsNestedProjects() throws Exception {
    String outer = h.await2("the outer project", () -> itemOf("Outer"));
    h.await2("the nested project", () -> itemOf("Inner"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", outer);
    String sid = h.awaitPanelShell(before).get("shell").getAsString();
    awaitWidget(sid, "button", "Delete 1 nested project");
    assertTrue(text(sid).contains("Project location:"), text(sid));
    event(widget(sid, "button", "Delete 1 nested project"), "selection", "selection", true);
    h.await("the locations of the nested project too", v -> {
      try {
        String t = text(sid).replace("&", "");
        return t.contains("2 project locations:") && t.contains("Inner");
      } catch (Exception e) {
        return false;
      }
    });
    press(sid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
    assertTrue(new File(h.workspace, "Outer/Inner/.project").isFile());
  }
}
