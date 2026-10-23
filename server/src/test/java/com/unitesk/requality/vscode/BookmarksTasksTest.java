package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.MarkerStore;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/**
 * The Bookmarks and Tasks of the Requality Explorer (org.eclipse.ui.navigator.resources WorkManagementActionProvider:
 * Add Bookmark and Add Task on a resource) and the undoable marker operations of their dialogs, with the markers kept
 * in the server storage across restarts.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookmarksTasksTest {
  static ServerHarness h;
  static File plain;
  static String plainItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "Plain");
    new File(plain, "sub").mkdirs();
    Files.writeString(plain.toPath().resolve(".project"),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "a.txt").toPath(), "alpha");
    Files.writeString(new File(plain, "sub/b.txt").toPath(), "bravo");
    h.initialize(h.workspace);
    plainItem = h.await2("the plain project", () -> itemOf("Plain"));
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String select(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.call("selection/set", p);
    return item;
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
    select(item);
    return h.call("command/state", p).getAsJsonObject().getAsJsonObject(command).get("label").getAsString();
  }

  /** The markers of the type in the workspace. */
  static IMarker[] markers(String type) {
    try {
      return ResourcesPlugin.getWorkspace().getRoot().findMarkers(type, true, IResource.DEPTH_INFINITE);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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

  @Test
  @Order(1)
  void addBookmarkCreatesABookmarkNamedAfterTheResourceAndIsUndoneAndRedone() throws Exception {
    String a = child(plainItem, "a.txt");
    select(a);
    execute("org.eclipse.ui.edit.addBookmark", a).get(60, TimeUnit.SECONDS);

    h.await("the bookmark", v -> markers(IMarker.BOOKMARK).length == 1);
    IMarker m = markers(IMarker.BOOKMARK)[0];
    assertEquals("a.txt", m.getAttribute(IMarker.MESSAGE, ""));
    assertEquals("a.txt", m.getResource().getName());
    assertEquals("Undo Add Bookmark", label("org.eclipse.ui.edit.undo", plainItem));

    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the bookmark removed by the undo", v -> markers(IMarker.BOOKMARK).length == 0);
    assertEquals("Redo Add Bookmark", label("org.eclipse.ui.edit.redo", plainItem));

    execute("org.eclipse.ui.edit.redo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the bookmark restored by the redo", v -> markers(IMarker.BOOKMARK).length == 1);
    assertEquals("a.txt", markers(IMarker.BOOKMARK)[0].getAttribute(IMarker.MESSAGE, ""));
  }

  @Test
  @Order(2)
  void addTaskOpensTheTaskDialogAndCreatesATaskWithItsPriority() throws Exception {
    String a = child(plainItem, "a.txt");
    select(a);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.addTask", a);
    JsonObject shell = h.awaitPanelShell(before);
    // the properties of the new task (Eclipse AddTaskAction: a dialog without a title is "Properties")
    assertEquals("Properties", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "write the report");
    event(widget(sid, "combo", null), "modify", "text", "High");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);

    h.await("the task", v -> markers(IMarker.TASK).length == 1);
    IMarker t = markers(IMarker.TASK)[0];
    assertEquals("write the report", t.getAttribute(IMarker.MESSAGE, ""));
    assertEquals(IMarker.PRIORITY_HIGH, t.getAttribute(IMarker.PRIORITY, -1));
    assertEquals(false, t.getAttribute(IMarker.DONE, true));
    assertEquals("Undo Add Task", label("org.eclipse.ui.edit.undo", plainItem));
  }

  @Test
  @Order(3)
  void markersOfThePersistentTypesAreReadAgainFromTheStorage() throws Exception {
    IMarker bookmark = markers(IMarker.BOOKMARK)[0];
    IMarker task = markers(IMarker.TASK)[0];
    long bookmarkId = bookmark.getId(), taskId = task.getId();
    long created = task.getCreationTime();
    // kept with the workspace metadata, next to the workspace state file
    File[] files = new File(h.storage, "workspaces").listFiles((d, n) -> n.endsWith("-markers.json"));
    assertNotNull(files, "the markers file in the storage");
    assertEquals(1, files.length);

    // a restart: the markers in memory are forgotten and read again from the file
    MarkerStore.reload();
    IMarker[] tasks = markers(IMarker.TASK);
    assertEquals(1, tasks.length);
    assertEquals(taskId, tasks[0].getId());
    assertEquals(created, tasks[0].getCreationTime());
    assertEquals("write the report", tasks[0].getAttribute(IMarker.MESSAGE, ""));
    assertEquals(IMarker.PRIORITY_HIGH, tasks[0].getAttribute(IMarker.PRIORITY, -1));
    IMarker[] bookmarks = markers(IMarker.BOOKMARK);
    assertEquals(1, bookmarks.length);
    assertEquals(bookmarkId, bookmarks[0].getId());
    assertEquals("Plain", bookmarks[0].getResource().getFullPath().segment(0));
  }

  /** The shell of the view shown in the VS Code view {@code view} (opened by views/open), as the server opened it. */
  static String viewShell(String view) throws Exception {
    h.call("views/open", "view", view);
    return h.await2("the shell of " + view, () -> {
      String shell = null;
      for (JsonObject o : h.notifications("ui/openShell"))
        if (o.has("host") && "view".equals(o.get("host").getAsString()) && o.has("view")
            && view.equals(o.get("view").getAsString()))
          shell = o.get("shell").getAsString();
      return shell;
    });
  }

  /** The rows (tree items) of a view shell: the id and the texts of every row. */
  static List<JsonObject> rows(String shell) throws Exception {
    List<JsonObject> r = new ArrayList<>();
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("type") && "treeitem".equals(o.get("type").getAsString()))
        r.add(o);
    }
    return r;
  }

  /** The paths of the images of a row, by column (empty for a cell without an image). */
  static List<String> images(JsonObject row) {
    List<String> r = new ArrayList<>();
    if (!row.getAsJsonObject("props").has("images"))
      return r;
    for (JsonElement e : row.getAsJsonObject("props").getAsJsonArray("images"))
      // the image paths in portable form (Windows separates the names of a path with a backslash)
      r.add(e.isJsonNull() ? "" : e.getAsString().replace('\\', '/'));
    return r;
  }

  static List<String> texts(JsonObject row) {
    List<String> r = new ArrayList<>();
    JsonObject props = row.getAsJsonObject("props");
    for (JsonElement e : props.getAsJsonArray("texts"))
      r.add(e.isJsonNull() ? "" : e.getAsString());
    return r;
  }

  static String table(String shell) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("type") && "tree".equals(o.get("type").getAsString()))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The row of the view whose description is the text, or null. */
  static String rowOf(String shell, String description) throws Exception {
    for (JsonObject row : rows(shell))
      if (texts(row).contains(description))
        return row.get("id").getAsString();
    return null;
  }

  /** The user activates the view (its webview gets the focus). */
  static void focus(String shell) {
    JsonObject p = new JsonObject();
    p.addProperty("shell", shell);
    h.notify("ui/shellFocused", p);
  }

  static void selectRows(String table, String... rows) {
    JsonObject p = new JsonObject();
    p.addProperty("id", table);
    p.addProperty("type", "selection");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    for (String r : rows)
      sel.add(r);
    d.add("selection", sel);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  @Test
  @Order(4)
  void theViewsListTheBookmarksAndTheTasks() throws Exception {
    String bookmarks = viewShell("requality.bookmarks");
    List<JsonObject> b = rows(bookmarks);
    assertEquals(1, b.size(), h.call("ui/snapshot", "shell", bookmarks).toString());
    // the Path is the container of the resource ("/Plain") and a bookmark without a line has the Location Unknown
    assertEquals(java.util.List.of("a.txt", "a.txt", "/Plain", "Unknown"), texts(b.get(0)).subList(0, 4));

    String tasks = viewShell("requality.tasks");
    List<JsonObject> t = rows(tasks);
    assertEquals(1, t.size(), h.call("ui/snapshot", "shell", tasks).toString());
    List<String> cols = texts(t.get(0));
    assertTrue(images(t.get(0)).get(1).endsWith("icons/ide/hprio_tsk.png"), images(t.get(0)).toString());
    assertEquals("write the report", cols.get(2));
    assertEquals("Task", cols.get(6));
  }

  @Test
  @Order(5)
  void doubleClickGoesToTheFileAndMarkCompletedUpdatesTheTask() throws Exception {
    String tasks = viewShell("requality.tasks");
    focus(tasks);
    String table = table(tasks);
    String row = rowOf(tasks, "write the report");
    assertNotNull(row);

    // a double click on a task: the file of the task is opened by the client
    int before = h.notifications("client/openFile").size();
    selectRows(table, row);
    JsonObject p = new JsonObject();
    p.addProperty("id", table);
    p.addProperty("type", "default");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(row);
    d.add("selection", sel);
    d.addProperty("item", row);
    p.add("data", d);
    h.notify("ui/event", p);
    h.await("the file opened", v -> h.notifications("client/openFile").size() > before);
    JsonObject open = h.notifications("client/openFile").get(before);
    assertTrue(open.get("path").getAsString().endsWith(File.separator + "Plain" + File.separator + "a.txt"), open.toString());
    assertEquals("default", open.get("editor").getAsString());

    // the checkbox of the task marks it completed
    JsonObject check = new JsonObject();
    check.addProperty("id", table);
    check.addProperty("type", "check");
    JsonObject cd = new JsonObject();
    cd.addProperty("item", row);
    cd.addProperty("checked", true);
    check.add("data", cd);
    h.notify("ui/event", check);
    h.await("the task completed", v -> markers(IMarker.TASK)[0].getAttribute(IMarker.DONE, false));
    assertEquals(IMarker.PRIORITY_HIGH, markers(IMarker.TASK)[0].getAttribute(IMarker.PRIORITY, -1));
  }

  @Test
  @Order(6)
  void deleteInTheBookmarksViewDeletesTheBookmark() throws Exception {
    String bookmarks = viewShell("requality.bookmarks");
    focus(bookmarks);
    String row = rowOf(bookmarks, "a.txt");
    assertNotNull(row);
    selectRows(table(bookmarks), row);
    h.call("command/execute", "command", "org.eclipse.ui.edit.delete");
    h.await("the bookmark deleted", v -> markers(IMarker.BOOKMARK).length == 0);
    h.await("the bookmark row gone", v -> {
      try {
        return rows(bookmarks).isEmpty();
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals("Undo Delete Bookmark", label("org.eclipse.ui.edit.undo", plainItem));
  }

  @Test
  @Order(7)
  void addTaskInTheTasksViewAddsATaskToTheWorkspace() throws Exception {
    String tasks = viewShell("requality.tasks");
    focus(tasks);
    String row = rowOf(tasks, "write the report");
    assertNotNull(row);
    selectRows(table(tasks), row);
    h.call("command/execute", "command", "org.eclipse.ui.edit.delete");
    h.await("the task deleted", v -> markers(IMarker.TASK).length == 0);

    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = h.callAsync("command/execute", addTaskParams());
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Add Task", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "workspace task");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("the workspace task", v -> markers(IMarker.TASK).length == 1);
    IMarker m = markers(IMarker.TASK)[0];
    assertEquals(IResource.ROOT, m.getResource().getType());
    assertEquals("workspace task", m.getAttribute(IMarker.MESSAGE, ""));
  }

  static JsonObject addTaskParams() {
    JsonObject p = new JsonObject();
    p.addProperty("command", "org.eclipse.ui.edit.addTask");
    return p;
  }
}
