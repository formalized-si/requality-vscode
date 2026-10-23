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
import com.unitesk.requality.vscode.ui.ide.undo.MoveResourcesOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * The undoable workspace operations of the Requality Explorer (org.eclipse.ui.ide.undo): the copy, paste, rename,
 * delete and move of files, folders and projects are undone and redone by Edit > Undo and Redo (the workbench operation
 * history), with the labels of Eclipse ("Undo Copy Resources"...). A project deleted with its contents is not undoable.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UndoOperationsTest {
  static ServerHarness h;
  static File plain;
  static String plainItem;
  static File copyDir;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    // a plain Eclipse project (not a catalog) with files, as the NonReqContentProvider lists them
    plain = project("Plain");
    new File(plain, "sub/deep").mkdirs();
    Files.writeString(new File(plain, "a.txt").toPath(), "alpha");
    Files.writeString(new File(plain, "sub/b.txt").toPath(), "bravo");
    Files.writeString(new File(plain, "sub/deep/c.txt").toPath(), "charlie");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    plainItem = h.await2("the plain project", () -> itemOf("Plain"));
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** An Eclipse project folder with a .project file of the workspace. */
  static File project(String name) throws Exception {
    File dir = new File(h.workspace, name);
    dir.mkdirs();
    Files.writeString(new File(dir, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>" + name
            + "</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures>"
            + "</projectDescription>\n");
    return dir;
  }

  static JsonObject state(String command, String item) throws Exception {
    JsonObject p = new JsonObject();
    JsonArray cmds = new JsonArray();
    cmds.add(command);
    p.add("commands", cmds);
    select(item);
    return h.call("command/state", p).getAsJsonObject().getAsJsonObject(command);
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

  /** The label of the next undo (or redo) of the workbench history, as the Edit menu shows it. */
  static String label(String command, String item) throws Exception {
    return state(command, item).get("label").getAsString();
  }

  /** The widget of a shell with the type and text, or null. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
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

  static JsonObject workspaceState() throws Exception {
    // the state file of the workspace (the other metadata files are named after it with a suffix)
    File[] fs = new File(h.storage, "workspaces").listFiles((d, n) -> n.matches("[0-9a-f]+\\.json"));
    assertNotNull(fs);
    assertEquals(1, fs.length);
    return com.google.gson.JsonParser.parseString(Files.readString(fs[0].toPath())).getAsJsonObject();
  }

  static boolean contains(JsonArray a, File f) throws Exception {
    for (JsonElement e : a)
      if (e.getAsString().equals(f.getCanonicalPath()))
        return true;
    return false;
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The explorer item of a project (a catalog or another project of the workspace), or null. */
  static String listedItem(String name) {
    try {
      return itemOf(name);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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
  void copyAFileIsUndoneAndRedone() throws Exception {
    String a = child(plainItem, "a.txt");
    String sub = child(plainItem, "sub");
    execute("org.eclipse.ui.edit.copy", a).get(60, TimeUnit.SECONDS);
    execute("org.eclipse.ui.edit.paste", sub).get(60, TimeUnit.SECONDS);
    File copy = new File(plain, "sub/a.txt");
    h.await("the copy", v -> copy.isFile());
    assertEquals("Undo Copy Resources", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the copy removed by the undo", v -> !copy.exists());
    assertEquals("alpha", read(new File(plain, "a.txt")), "the source stays");
    assertEquals("Redo Copy Resources", label("org.eclipse.ui.edit.redo", plainItem));

    execute("org.eclipse.ui.edit.redo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the copy restored by the redo", v -> copy.isFile());
    assertEquals("alpha", read(copy));
  }

  @Test
  @Order(2)
  void renameAFileIsUndoneAsARenameResource() throws Exception {
    String a = child(plainItem, "a.txt");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.rename", a);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Rename Resource", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "renamed.txt");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    File renamed = new File(plain, "renamed.txt");
    h.await("renamed", v -> renamed.isFile() && !new File(plain, "a.txt").exists());
    assertEquals("Undo Rename Resource", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the name restored", v -> new File(plain, "a.txt").isFile() && !renamed.exists());
    assertEquals("alpha", read(new File(plain, "a.txt")));
  }

  @Test
  @Order(3)
  void deleteAFileIsUndoneWithItsContents() throws Exception {
    String sub = child(plainItem, "sub");
    String b = child(sub, "b.txt");
    File file = new File(plain, "sub/b.txt");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", b);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Delete Resources", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(h.call("ui/snapshot", "shell", sid).toString()
        .contains("Delete 'b.txt' from the file system?"));
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("deleted", v -> !file.exists());
    assertEquals("Undo Delete Resources", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("restored", v -> file.isFile());
    assertEquals("bravo", read(file), "the contents are restored");
    assertEquals("Redo Delete Resources", label("org.eclipse.ui.edit.redo", plainItem));

    execute("org.eclipse.ui.edit.redo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("deleted again", v -> !file.exists());
  }

  @Test
  @Order(4)
  void moveAFolderIsUndoneAndRedone() throws Exception {
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    IResource deep = root.findMember(Path.fromPortableString("/Plain/sub/deep"));
    assertNotNull(deep);
    MoveResourcesOperation move = new MoveResourcesOperation(new IResource[] { deep },
        root.getProject("Plain").getFullPath(), "Move Resources");
    WorkspaceUndoUtil.execute(move, new NullProgressMonitor(), null);
    File moved = new File(plain, "deep/c.txt");
    assertTrue(moved.isFile() && !new File(plain, "sub/deep").exists());
    h.await("the folder in the tree", v -> {
      try {
        return child(plainItem, "deep") != null;
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals("Undo Move Resources", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("moved back", v -> new File(plain, "sub/deep/c.txt").isFile() && !moved.exists());
    assertEquals("charlie", read(new File(plain, "sub/deep/c.txt")));

    execute("org.eclipse.ui.edit.redo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("moved again", v -> moved.isFile());
  }

  @Test
  @Order(5)
  void copyAProjectIsNotUndoable() throws Exception {
    String undoBefore = label("org.eclipse.ui.edit.undo", plainItem);
    execute("org.eclipse.ui.edit.copy", plainItem).get(60, TimeUnit.SECONDS);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.paste", plainItem);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Copy Project", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(h.call("ui/snapshot", "shell", sid).toString().contains("Copy of Plain"));
    String useDefault = widget(sid, "button", "Use default location");
    if (useDefault != null)
      event(useDefault, "selection", "selection", true);
    // the default location follows the name of the copy when the name changes (the name is typed in the dialog)
    String nameField = widget(sid, "text", "Copy of Plain");
    event(nameField, "modify", "text", "Copy of Plain.");
    event(nameField, "modify", "text", "Copy of Plain");
    try {
      h.await("a valid location", v -> {
        try {
          return !h.call("ui/snapshot", "shell", sid).toString().contains("Location is the current location");
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + h.call("ui/snapshot", "shell", sid), e);
    }
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    copyDir = new File(h.workspace, "Copy of Plain");
    h.await("the copy on disk", v -> new File(copyDir, ".project").isFile());
    h.await("the copy listed", v -> listedItem("Copy of Plain") != null);
    // Eclipse's CopyProjectOperation executes the copy outside the operation history (bug 219901)
    assertEquals(undoBefore, label("org.eclipse.ui.edit.undo", plainItem));
    assertTrue(new File(plain, "a.txt").isFile(), "the original project stays");
  }

  @Test
  @Order(6)
  void removingAProjectWithoutContentsIsUndoneByAddingItBack() throws Exception {
    File removable = project("Removable");
    Files.writeString(new File(removable, "x.txt").toPath(), "x");
    h.call("workspace/rescan", null);
    String item = h.await2("Removable", () -> itemOf("Removable"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", item);
    JsonObject shell = h.awaitPanelShell(before);
    String sid = shell.get("shell").getAsString();
    assertTrue(h.call("ui/snapshot", "shell", sid).toString()
        .contains("Remove project 'Removable' from the workspace?"));
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    h.await("removed from the explorer", v -> listedItem("Removable") == null);
    assertTrue(removable.isDirectory(), "the content stays on disk");
    assertEquals("Undo Delete Resources", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(120, TimeUnit.SECONDS);
    h.await("back in the explorer", v -> listedItem("Removable") != null);
    assertEquals("x", read(new File(removable, "x.txt")));
    assertFalse(contains(workspaceState().getAsJsonArray("removed"), removable), workspaceState().toString());

    execute("org.eclipse.ui.edit.redo", plainItem).get(120, TimeUnit.SECONDS);
    h.await("removed again", v -> listedItem("Removable") == null);
    assertTrue(contains(workspaceState().getAsJsonArray("removed"), removable), workspaceState().toString());
  }

  @Test
  @Order(7)
  void deletingTheContentsOfAProjectIsNotUndoable() throws Exception {
    String before = label("org.eclipse.ui.edit.undo", plainItem);
    String item = listedItem("Copy of Plain");
    assertNotNull(item);
    int shells = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", item);
    JsonObject shell = h.awaitPanelShell(shells);
    String sid = shell.get("shell").getAsString();
    String check = widget(sid, "button", "Delete project contents on disk (cannot be undone)");
    assertNotNull(check, h.call("ui/snapshot", "shell", sid).toString());
    event(check, "selection", "selection", true);
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    h.await("the folder deleted", v -> !copyDir.exists());
    h.await("unlisted", v -> listedItem("Copy of Plain") == null);
    // deleting the contents of a project is executed without the history: the undo stays the one before
    assertEquals(before, label("org.eclipse.ui.edit.undo", plainItem));
  }
}
