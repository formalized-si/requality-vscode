package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.views.MarkerView;

/**
 * The Bookmarks and Tasks views as Eclipse's marker views (org.eclipse.ui.ide markerSupport: the generators of the
 * bookmarks and the tasks, the View menu of {@code menu:org.eclipse.ui.views.TaskList} and
 * {@code menu:org.eclipse.ui.views.BookmarkView}, the context menu of {@code popup:org.eclipse.ui.ide.MarkersView}).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MarkerViewsTest {
  static final String BOOKMARKS = "org.eclipse.ui.views.BookmarkView";
  static final String TASKS = "org.eclipse.ui.views.TaskList";
  static ServerHarness h;
  static String plainItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    File plain = new File(h.workspace, "Plain");
    new File(plain, "sub").mkdirs();
    Files.writeString(plain.toPath().resolve(".project"),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "a.txt").toPath(), "alpha");
    Files.writeString(new File(plain, "sub/b.txt").toPath(), "bravo");
    h.initialize(h.workspace);
    plainItem = h.await2("the plain project", () -> itemOf("Plain"));
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    IFile a = project.getFile("a.txt");
    IFile b = project.getFile("sub/b.txt");
    a.createMarker(IMarker.BOOKMARK).setAttribute(IMarker.MESSAGE, "alpha note");
    b.createMarker(IMarker.BOOKMARK).setAttribute(IMarker.MESSAGE, "bravo note");
    IMarker write = a.createMarker(IMarker.TASK);
    write.setAttribute(IMarker.MESSAGE, "write the report");
    write.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
    IMarker call = b.createMarker(IMarker.TASK);
    call.setAttribute(IMarker.MESSAGE, "call bob");
    call.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_NORMAL);
    call.setAttribute(IMarker.DONE, true);
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

  static IMarker[] markers(String type) throws Exception {
    return ResourcesPlugin.getWorkspace().getRoot().findMarkers(type, true, IResource.DEPTH_INFINITE);
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

  /** The shell of the view shown in the VS Code view {@code view} (opened by views/open), as the server opened it. */
  static String viewShell(String view) throws Exception {
    h.call("views/open", "view", view);
    String vscode = view.equals(BOOKMARKS) ? "requality.bookmarks" : "requality.tasks";
    return h.await2("the shell of " + view, () -> {
      String shell = null;
      for (JsonObject o : h.notifications("ui/openShell"))
        if (o.has("host") && "view".equals(o.get("host").getAsString()) && o.has("view")
            && vscode.equals(o.get("view").getAsString()))
          shell = o.get("shell").getAsString();
      return shell;
    });
  }

  static IMarker[] markersNow(String type) {
    try {
      return markers(type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String contentOf(String view) {
    try {
      return part(view).getContentDescription();
    } catch (Exception e) {
      return "";
    }
  }

  static List<JsonObject> safeRows(String shell) {
    try {
      return rows(shell);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  /** The marker view of the id, as the workbench shows it. */
  static MarkerView part(String view) throws Exception {
    IViewPart p = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(view);
    assertNotNull(p, "the view " + view);
    return (MarkerView) p;
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

  static List<String> texts(JsonObject row) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : row.getAsJsonObject("props").getAsJsonArray("texts"))
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

  static String rowOf(String shell, String description) throws Exception {
    for (JsonObject row : rows(shell))
      if (texts(row).contains(description))
        return row.get("id").getAsString();
    return null;
  }

  /** The items of the view menu or the context menu (the menu/items of the id). */
  static JsonArray menuItems(String menu) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", menu);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** An item of the menu or of its submenus, by label. */
  static JsonObject find(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()))
        return o;
    }
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("children")) {
        JsonObject c = find(o.getAsJsonArray("children"), label);
        if (c != null)
          return c;
      }
    }
    return null;
  }

  static JsonObject item(String menu, String label) throws Exception {
    JsonObject found = find(menuItems(menu), label);
    assertNotNull(found, "No item " + label + " in " + menu + ": " + menuItems(menu));
    return found;
  }

  /** Runs the item of the menu (menu/run of the last menu/items of the menu) without waiting for a dialog it opens. */
  static CompletableFuture<JsonElement> runAsync(String menu, String label) throws Exception {
    JsonObject it = item(menu, label);
    JsonObject p = new JsonObject();
    p.addProperty("menu", menu);
    p.addProperty("index", it.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  static void run(String menu, String label) throws Exception {
    runAsync(menu, label).get(60, TimeUnit.SECONDS);
  }

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

  /** The id of the group row of the Group By type whose label is {@code label}, or null. */
  static String groupRow(String tree, String label) {
    for (JsonObject row : safeRows(tree))
      if (texts(row).contains(label))
        return row.get("id").getAsString();
    return null;
  }

  /** The row of the description (the one with the text among its texts), or null. */
  static JsonObject rowObject(String tree, String description) {
    for (JsonObject row : safeRows(tree))
      if (texts(row).contains(description))
        return row;
    return null;
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

  /** The texts of all the rows of a tree (the group rows and the markers). */
  static List<String> allTexts(String tree) {
    List<String> r = new ArrayList<>();
    for (JsonObject row : safeRows(tree))
      r.addAll(texts(row));
    return r;
  }

  /** A task of the file, with its priority and completion. */
  static IMarker newTask(IFile file, String message, int priority, boolean done) throws Exception {
    IMarker m = file.createMarker(IMarker.TASK);
    m.setAttribute(IMarker.MESSAGE, message);
    m.setAttribute(IMarker.PRIORITY, priority);
    m.setAttribute(IMarker.DONE, done);
    return m;
  }

  /** Whether the view shows a row with the description (false while the view is not shown). */
  static boolean hasRow(String tree, String description) {
    return allTexts(tree).contains(description);
  }

  /** The shell of a panel opened with the title, or null. */
  static JsonObject panelTitled(String title) {
    for (JsonObject o : h.notifications("ui/openShell"))
      if (o.has("title") && title.equals(o.get("title").getAsString()))
        return o;
    return null;
  }

  @Test
  @Order(1)
  void theContentDescriptionCountsTheItemsOfTheView() throws Exception {
    viewShell(BOOKMARKS);
    viewShell(TASKS);
    assertEquals("2 items", part(BOOKMARKS).getContentDescription());
    assertEquals("2 items", part(TASKS).getContentDescription());
    assertEquals(2, markers(IMarker.BOOKMARK).length);
    assertEquals(2, markers(IMarker.TASK).length);
  }

  @Test
  @Order(2)
  void theViewMenusAreTheEclipseOnes() throws Exception {
    JsonArray tasks = menuItems(TASKS + ".viewMenu");
    assertNotNull(find(tasks, "Sort By"), tasks.toString());
    assertNotNull(find(tasks, "Group By"), tasks.toString());
    assertNotNull(find(tasks, "Filters..."), tasks.toString());
    assertNotNull(find(tasks, "Configure Columns..."), tasks.toString());
    assertNotNull(find(tasks, "Type"), "Group By > Type: " + tasks);
    assertNotNull(find(tasks, "Ascending"), tasks.toString());
    // the bookmarks have no grouping, and no configuration yet: no Show menu
    JsonArray bookmarks = menuItems(BOOKMARKS + ".viewMenu");
    assertNull(find(bookmarks, "Group By"), bookmarks.toString());
    assertNull(find(bookmarks, "Show"), bookmarks.toString());
    assertNotNull(find(bookmarks, "Sort By"), bookmarks.toString());
  }

  @Test
  @Order(3)
  void filtersAddsAConfigurationWhoseDescriptionHidesABookmark() throws Exception {
    String bookmarks = viewShell(BOOKMARKS);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(BOOKMARKS + ".viewMenu", "Filters...");
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Filters", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    // "Show all items" is checked at first: the configurations are made when it is unchecked
    event(widget(sid, "button", "Show all items"), "selection", "selection", false);
    event(widget(sid, "button", "New"), "selection", null, null);
    event(widget(sid, "text", null), "modify", "text", "alpha");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);

    Thread.sleep(1000);
    assertEquals("Filter matched 1 of 2 items", contentOf(BOOKMARKS), h.call("ui/snapshot", "shell", bookmarks).toString());
    List<String> shown = new ArrayList<>();
    for (JsonObject row : rows(bookmarks))
      shown.add(texts(row).get(0));
    assertEquals(List.of("alpha note"), shown);
    // the configuration is in the Show menu
    assertNotNull(find(menuItems(BOOKMARKS + ".viewMenu"), "New Configuration"), "the Show menu");
  }

  @Test
  @Order(4)
  void configureColumnsHidesAColumn() throws Exception {
    String tasks = viewShell(TASKS);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(TASKS + ".viewMenu", "Configure Columns...");
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Configure Columns", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    // the Location column is hidden: the list of the shown columns has it at index 5
    JsonObject p = new JsonObject();
    p.addProperty("id", widget(sid, "list", null));
    p.addProperty("type", "selection");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(5);
    d.add("indices", sel);
    p.add("data", d);
    h.notify("ui/event", p);
    event(widget(sid, "button", "<- Hide "), "selection", null, null);
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    // the task rows have the columns shown: without Location (six columns)
    h.await("the location column hidden", v -> {
      try {
        return rows(tasks).size() == 2 && texts(rows(tasks).get(0)).size() == 6;
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(5)
  void sortByChangesTheOrderOfTheTasks() throws Exception {
    String tasks = viewShell(TASKS);
    run(TASKS + ".viewMenu", "Description");
    h.await("the tasks sorted by description", v -> {
      try {
        return rows(tasks).size() == 2 && "call bob".equals(texts(rows(tasks).get(0)).get(2));
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(6)
  void groupByTypeGroupsTheTasks() throws Exception {
    String tasks = viewShell(TASKS);
    run(TASKS + ".viewMenu", "Type");
    String group = h.await2("the group row", () -> groupRow(tasks, "Task (2 items)"));
    assertNotNull(group);
    event(table(tasks), "expand", "item", group);
    h.await("the markers under the group row", v -> hasRow(tasks, "write the report"));
    run(TASKS + ".viewMenu", "None");
  }

  @Test
  @Order(7)
  void copyDetailsDescriptionPutsTheDescriptionOnTheClipboard() throws Exception {
    String tasks = viewShell(TASKS);
    focus(tasks);
    String row = rowOf(tasks, "write the report");
    assertNotNull(row);
    selectRows(table(tasks), row);
    int before = h.notifications("client/clipboard").size();
    run(TASKS + ".popup", "Description");
    h.await("the clipboard", v -> h.notifications("client/clipboard").size() > before);
    String text = h.notifications("client/clipboard").get(before).get("text").getAsString();
    assertEquals("write the report", text);
  }

  @Test
  @Order(8)
  void propertiesShowsTheMarkerProperties() throws Exception {
    String tasks = viewShell(TASKS);
    focus(tasks);
    String row = rowOf(tasks, "write the report");
    selectRows(table(tasks), row);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(TASKS + ".popup", "Properties");
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Properties", shell.get("title").getAsString(), shell.toString());
    event(widget(shell.get("shell").getAsString(), "button", "Cancel"), "selection", null, null);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(9)
  void markCompletedAndDeleteCompletedTasks() throws Exception {
    String tasks = viewShell(TASKS);
    focus(tasks);
    String row = rowOf(tasks, "write the report");
    // CompletionEditingSupport: a click on the completion cell (the first column of the Tasks view) toggles it
    JsonObject click = new JsonObject();
    click.addProperty("id", table(tasks));
    click.addProperty("type", "cellclick");
    JsonObject cd = new JsonObject();
    cd.addProperty("item", row);
    cd.addProperty("column", 0);
    click.add("data", cd);
    h.notify("ui/event", click);
    h.await("the task completed by its cell", v -> {
      for (IMarker m : markersNow(IMarker.TASK))
        if ("write the report".equals(m.getAttribute(IMarker.MESSAGE, "")))
          return m.getAttribute(IMarker.DONE, false);
      return false;
    });
    // the rows are made again after the change: the click goes to the new row of the task
    cd.addProperty("item", rowOf(tasks, "write the report"));
    h.notify("ui/event", click);
    h.await("the task not completed again", v -> {
      for (IMarker m : markersNow(IMarker.TASK))
        if ("write the report".equals(m.getAttribute(IMarker.MESSAGE, "")))
          return !m.getAttribute(IMarker.DONE, false);
      return false;
    });
    // Mark Completed of the selected row; the rows are made again after each marker change, so a row taken just before
    // that refresh may be gone when it is selected: the row of the task is selected again (Mark Completed only sets DONE)
    h.await("the task completed", v -> {
      for (IMarker m : markersNow(IMarker.TASK))
        if ("write the report".equals(m.getAttribute(IMarker.MESSAGE, "")) && !m.getAttribute(IMarker.DONE, false)) {
          try {
            selectRows(table(tasks), rowOf(tasks, "write the report"));
            Thread.sleep(200);
            run(TASKS + ".popup", "Mark Completed");
            Thread.sleep(300);
          } catch (Exception e) {
            // the row went away meanwhile: try again
          }
          return false;
        }
      return true;
    });

    assertTrue(item(TASKS + ".popup", "Delete Completed Tasks").get("enabled").getAsBoolean());
    // the question of the deletion is a native message: yes
    h.answer("client/showMessage", p -> "Yes");
    run(TASKS + ".popup", "Delete Completed Tasks");
    h.await("the completed tasks deleted", v -> markersNow(IMarker.TASK).length == 0);
  }

  @Test
  @Order(10)
  void groupRowsAreParentsOfTheirMarkers() throws Exception {
    IFile a = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").getFile("a.txt");
    newTask(a, "group one", IMarker.PRIORITY_NORMAL, false);
    String tasks = viewShell(TASKS);
    run(TASKS + ".viewMenu", "Type");
    String tree = table(tasks);
    String group = h.await2("the group row", () -> {
      for (JsonObject row : safeRows(tasks))
        if (texts(row).get(0).startsWith("Task ("))
          return row.get("id").getAsString();
      return null;
    });
    // expanded by the user: the markers are the children of the group row; collapsed again: they are not shown
    event(tree, "expand", "item", group);
    h.await("the markers of the group", v -> hasRow(tasks, "group one"));
    event(tree, "collapse", "item", group);
    run(TASKS + ".viewMenu", "None");
    h.await("the flat list", v -> hasRow(tasks, "group one"));
  }

  @Test
  @Order(11)
  void completedAndPriorityAreTheEclipseIcons() throws Exception {
    IFile a = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").getFile("a.txt");
    newTask(a, "icon high", IMarker.PRIORITY_HIGH, false);
    newTask(a, "icon done", IMarker.PRIORITY_NORMAL, true);
    String tasks = viewShell(TASKS);
    h.await("the icon rows", v -> rowObject(tasks, "icon high") != null && rowObject(tasks, "icon done") != null);
    JsonObject high = rowObject(tasks, "icon high");
    JsonObject done = rowObject(tasks, "icon done");
    assertTrue(images(high).get(0).endsWith("icons/ide/incomplete_tsk.png"), images(high).toString());
    assertTrue(images(high).get(1).endsWith("icons/ide/hprio_tsk.png"), images(high).toString());
    assertTrue(images(done).get(0).endsWith("icons/ide/complete_tsk.png"), images(done).toString());
    assertEquals("", images(done).get(1), images(done).toString());
    assertEquals("", texts(high).get(0));
    assertEquals("", texts(high).get(1));
  }

  @Test
  @Order(12)
  void newBookmarksViewOpensAnotherViewInAPanel() throws Exception {
    viewShell(BOOKMARKS);
    assertNotNull(item(BOOKMARKS + ".viewMenu", "New Bookmarks View"));
    assertNotNull(item(TASKS + ".viewMenu", "New Tasks View"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(BOOKMARKS + ".viewMenu", "New Bookmarks View");
    JsonObject dialog = h.awaitPanelShell(before);
    assertEquals("New Bookmarks View", dialog.get("title").getAsString(), dialog.toString());
    String sid = dialog.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "Second bookmarks");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    JsonObject panel = h.await2("the new view", () -> panelTitled("Second bookmarks"));
    assertEquals("panel", panel.get("host").getAsString(), panel.toString());
    // the new view shows the markers, and has the menus of its own id
    String shell = panel.get("shell").getAsString();
    h.await("the markers of the new view", v -> allTexts(shell).contains("alpha note"));
    assertNotNull(find(menuItems(BOOKMARKS + "#1.viewMenu"), "Sort By"));
    // the panel has the title bar of the view: its toolbar and the View Menu button, which shows the view menu
    String viewMenuButton = h.await2("the View Menu button", () -> {
      for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
        JsonObject o = e.getAsJsonObject();
        if ("button".equals(o.has("type") ? o.get("type").getAsString() : null) && o.toString().contains("View Menu"))
          return o.get("id").getAsString();
      }
      return null;
    });
    event(viewMenuButton, "selection", null, null);
    h.await("the view menu shown in the panel", v -> {
      try {
        return h.call("ui/snapshot", "shell", shell).toString().contains("Configure Columns...");
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(13)
  void theScopeOfAConfigurationIsTheSelectionOfTheWorkbench() throws Exception {
    IFile a = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").getFile("a.txt");
    IFile b = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").getFile("sub/b.txt");
    newTask(a, "scope a", IMarker.PRIORITY_NORMAL, false);
    newTask(b, "scope b", IMarker.PRIORITY_NORMAL, false);
    String tasks = viewShell(TASKS);
    h.await("the tasks of the files", v -> allTexts(tasks).contains("scope b"));
    // the file b is selected in the workbench (the selection of another part); the markers view of the bookmarks
    // refreshes first when the markers change, so the selection is given after that
    Thread.sleep(1000);
    Workbench.partActivated(part(BOOKMARKS), new StructuredSelection(List.of(b)));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(TASKS + ".viewMenu", "Filters...");
    JsonObject shell = h.awaitPanelShell(before);
    String sid = shell.get("shell").getAsString();
    event(widget(sid, "button", "Show all items"), "selection", "selection", false);
    event(widget(sid, "button", "New"), "selection", null, null);
    event(widget(sid, "button", "On selected elements only"), "selection", "selection", true);
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("only the tasks of the selected file", v -> {
      List<String> t = allTexts(tasks);
      return t.contains("scope b") && !t.contains("scope a");
    });
    // the configurations are off again
    run(TASKS + ".viewMenu", "Show All");
    h.await("all the tasks shown", v -> allTexts(tasks).contains("scope a"));
  }

  @Test
  @Order(14)
  void showTextFilterFiltersTheMarkersByTheirText() throws Exception {
    IFile a = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").getFile("a.txt");
    newTask(a, "filter alpha", IMarker.PRIORITY_NORMAL, false);
    newTask(a, "filter gamma", IMarker.PRIORITY_NORMAL, false);
    String tasks = viewShell(TASKS);
    run(TASKS + ".viewMenu", "Show Text Filter");
    String text = h.await2("the text filter", () -> widget(tasks, "text", null));
    event(text, "modify", "text", "GAMMA");
    h.await("only the matching tasks", v -> {
      List<String> t = allTexts(tasks);
      return t.contains("filter gamma") && !t.contains("filter alpha");
    });
    event(text, "modify", "text", "");
    h.await("the tasks shown again", v -> allTexts(tasks).contains("filter alpha"));
    run(TASKS + ".viewMenu", "Show Text Filter");
  }
}
