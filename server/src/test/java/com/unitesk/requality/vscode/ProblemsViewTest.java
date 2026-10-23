package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.views.MarkerView;

/**
 * The Problems view as Eclipse's ProblemsView ({@code org.eclipse.ui.views.ProblemView}, the problems generator of
 * org.eclipse.ui.ide): the problem markers grouped by severity (Errors, Warnings, Infos), the status line summary, the
 * columns, Group By Type and None, the configurations of the Show menu, Quick Fix, Go to Resource, Open Another and
 * Delete. The problem markers are created as the checkers create them (the Requality marker is a problem subtype).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProblemsViewTest {
  static final String PROBLEMS = "org.eclipse.ui.views.ProblemView";
  static final String REQUALITY = "com.unitesk.requality.marker";
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    File checked = new File(h.workspace, "Checked");
    new File(checked, "sub").mkdirs();
    Files.writeString(checked.toPath().resolve(".project"),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Checked</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(checked, "a.txt").toPath(), "alpha");
    Files.writeString(new File(checked, "sub/b.txt").toPath(), "bravo");
    h.initialize(h.workspace);
    h.await2("the checked project", () -> itemOf("Checked"));
    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("Checked");
    IFile a = project.getFile("a.txt");
    IFile b = project.getFile("sub/b.txt");
    problem(a, IMarker.PROBLEM, "undefined symbol", IMarker.SEVERITY_ERROR, 3);
    problem(a, IMarker.PROBLEM, "unused variable", IMarker.SEVERITY_WARNING, 5);
    problem(b, IMarker.PROBLEM, "missing semicolon", IMarker.SEVERITY_ERROR, 1);
    problem(b, IMarker.PROBLEM, "consider a rename", IMarker.SEVERITY_INFO, 2);
    problem(a, REQUALITY, "requality check failed", IMarker.SEVERITY_ERROR, 4);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static IMarker problem(IFile file, String type, String message, int severity, int line) throws Exception {
    IMarker m = file.createMarker(type);
    m.setAttribute(IMarker.MESSAGE, message);
    m.setAttribute(IMarker.SEVERITY, severity);
    m.setAttribute(IMarker.LINE_NUMBER, line);
    return m;
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

  static int markerCount(String type) {
    try {
      return markers(type).length;
    } catch (Exception e) {
      return -1;
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

  /** The shell of the Problems view shown in the VS Code view of the Requality panel (opened by views/open). */
  static String problemsShell() throws Exception {
    h.call("views/open", "view", PROBLEMS);
    return h.await2("the shell of the problems view", () -> {
      String shell = null;
      for (JsonObject o : h.notifications("ui/openShell"))
        if (o.has("host") && "view".equals(o.get("host").getAsString()) && o.has("view")
            && "requality.problems".equals(o.get("view").getAsString()))
          shell = o.get("shell").getAsString();
      return shell;
    });
  }

  /** The marker view of the id, as the workbench shows it. */
  static MarkerView part(String view) throws Exception {
    IViewPart p = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(view);
    assertNotNull(p, "the view " + view);
    return (MarkerView) p;
  }

  static String contentOf(String view) {
    try {
      return part(view).getContentDescription();
    } catch (Exception e) {
      return "";
    }
  }

  /** The tree items of a view shell (the group rows and the markers shown). */
  static List<JsonObject> rows(String shell) throws Exception {
    List<JsonObject> r = new ArrayList<>();
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("type") && "treeitem".equals(o.get("type").getAsString()))
        r.add(o);
    }
    return r;
  }

  static List<JsonObject> safeRows(String shell) {
    try {
      return rows(shell);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  static List<String> texts(JsonObject row) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : row.getAsJsonObject("props").getAsJsonArray("texts"))
      r.add(e.isJsonNull() ? "" : e.getAsString());
    return r;
  }

  /** The paths of the images of a row, by column (an empty path for a cell without an image). */
  static List<String> images(JsonObject row) {
    List<String> r = new ArrayList<>();
    if (!row.getAsJsonObject("props").has("images"))
      return r;
    for (JsonElement e : row.getAsJsonObject("props").getAsJsonArray("images"))
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

  static List<String> allTexts(String tree) {
    List<String> r = new ArrayList<>();
    for (JsonObject row : safeRows(tree))
      r.addAll(texts(row));
    return r;
  }

  static boolean hasRow(String tree, String description) {
    return allTexts(tree).contains(description);
  }

  /** The row (tree item) whose texts contain the description, or null. */
  static JsonObject rowObject(String tree, String description) {
    for (JsonObject row : safeRows(tree))
      if (texts(row).contains(description))
        return row;
    return null;
  }

  static String rowOf(String tree, String description) {
    JsonObject row = rowObject(tree, description);
    return row == null ? null : row.get("id").getAsString();
  }

  /** The id of the group row with the label, or null. */
  static String groupRow(String tree, String label) {
    for (JsonObject row : safeRows(tree))
      if (texts(row).contains(label))
        return row.get("id").getAsString();
    return null;
  }

  /** The items of the view menu or of the context menu (menu/items of the menu id). */
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

  /** Runs the item of the menu without waiting for a dialog it opens. */
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

  /** The panel of Open Another or of a view, with its title, or null. */
  static JsonObject panelTitled(String title) {
    for (JsonObject o : h.notifications("ui/openShell"))
      if (o.has("title") && title.equals(o.get("title").getAsString()))
        return o;
    return null;
  }

  @Test
  @Order(1)
  void theProblemsAreGroupedBySeverity() throws Exception {
    String tree = problemsShell();
    assertEquals("3 errors, 1 warning, 1 other", h.await2("the summary", () -> {
      String c = contentOf(PROBLEMS);
      return "3 errors, 1 warning, 1 other".equals(c) ? c : null;
    }));
    assertNotNull(h.await2("the Errors group", () -> groupRow(tree, "Errors (3 items)")));
    assertNotNull(groupRow(tree, "Warnings (1 item)"));
    assertNotNull(groupRow(tree, "Infos (1 item)"));
    // the problem markers of the workspace: the four problems and the Requality problem (a subtype of them)
    assertEquals(5, markers(IMarker.PROBLEM).length);
  }

  @Test
  @Order(2)
  void theRowsShowTheColumnsAndTheSeverityImage() throws Exception {
    String tree = problemsShell();
    String errors = h.await2("the Errors group", () -> groupRow(tree, "Errors (3 items)"));
    event(table(tree), "expand", "item", errors);
    h.await("the errors under the group", v -> hasRow(tree, "undefined symbol"));
    JsonObject row = rowObject(tree, "undefined symbol");
    // Description, Resource, Path, Location and Type: the Creation Time and the ID are hidden
    assertEquals(5, texts(row).size(), texts(row).toString());
    assertEquals("a.txt", texts(row).get(1));
    assertEquals("/Checked", texts(row).get(2));
    assertEquals("line 3", texts(row).get(3));
    assertEquals("Problem", texts(row).get(4));
    assertTrue(images(row).get(0).endsWith("error_tsk.png"), images(row).toString());
    JsonObject warning = rowObject(tree, "unused variable");
    assertNull(warning, "the warnings are in their own group, collapsed");
  }

  @Test
  @Order(3)
  void groupByTypeAndNone() throws Exception {
    String tree = problemsShell();
    run(PROBLEMS + ".viewMenu", "Type");
    assertNotNull(h.await2("the Problem group", () -> groupRow(tree, "Problem (4 items)")));
    assertNotNull(groupRow(tree, "Requality Problem (1 item)"));
    run(PROBLEMS + ".viewMenu", "None");
    h.await("the problems without groups", v -> hasRow(tree, "undefined symbol")
        && groupRow(tree, "Errors (3 items)") == null);
    run(PROBLEMS + ".viewMenu", "Severity");
    assertNotNull(h.await2("the Errors group again", () -> groupRow(tree, "Errors (3 items)")));
  }

  @Test
  @Order(4)
  void showConfigurationFiltersTheErrors() throws Exception {
    String tree = problemsShell();
    JsonArray viewMenu = menuItems(PROBLEMS + ".viewMenu");
    assertNotNull(find(viewMenu, "All Errors on Workspace"), viewMenu.toString());
    assertNotNull(find(viewMenu, "Errors/Warnings on Project"), viewMenu.toString());
    assertNotNull(find(viewMenu, "Errors/Warnings on Selection"), viewMenu.toString());
    assertNotNull(find(viewMenu, "Warnings on Selection"), viewMenu.toString());
    run(PROBLEMS + ".viewMenu", "All Errors on Workspace");
    h.await("the errors only", v -> "3 errors, 0 warnings, 0 others (Filter matched 3 of 5 items)"
        .equals(contentOf(PROBLEMS)));
    h.await("the warnings hidden", v -> !hasRow(tree, "unused variable"));
    // the configuration is checked: a second run unchecks it, and the summary is the one of all the problems
    run(PROBLEMS + ".viewMenu", "All Errors on Workspace");
    h.await("all the problems again", v -> "3 errors, 1 warning, 1 other".equals(contentOf(PROBLEMS)));
  }

  @Test
  @Order(5)
  void popupHasTheEclipseItemsAndQuickFixIsDisabledWithoutFixes() throws Exception {
    String tree = problemsShell();
    focus(tree);
    String row = h.await2("the undefined symbol row", () -> rowOf(tree, "undefined symbol"));
    selectRows(table(tree), row);
    JsonArray popup = menuItems(PROBLEMS + ".popup");
    for (String label : new String[] { "Go to Resource", "Copy", "Delete", "Select All", "Show In", "Properties" })
      assertNotNull(find(popup, label), label + ": " + popup);
    assertNull(find(popup, "Mark Completed"), "no task items in the problems: " + popup);
    JsonObject quickFix = item(PROBLEMS + ".popup", "Quick Fix");
    assertFalse(quickFix.get("enabled").getAsBoolean(), "no resolution is contributed: " + quickFix);
  }

  @Test
  @Order(6)
  void goToOpensTheFileAtTheLine() throws Exception {
    String tree = problemsShell();
    focus(tree);
    String row = h.await2("the undefined symbol row", () -> rowOf(tree, "undefined symbol"));
    selectRows(table(tree), row);
    int before = h.notifications("client/openFile").size();
    run(PROBLEMS + ".popup", "Go to Resource");
    h.await("the file opened", v -> h.notifications("client/openFile").size() > before);
    JsonObject open = h.notifications("client/openFile").get(before);
    assertTrue(open.get("path").getAsString().endsWith("a.txt"), open.toString());
    assertEquals(3, open.get("line").getAsInt());
  }

  @Test
  @Order(7)
  void openAnotherOpensANewProblemsViewInAPanel() throws Exception {
    problemsShell();
    assertNotNull(item(PROBLEMS + ".viewMenu", "New Problems View"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(PROBLEMS + ".viewMenu", "New Problems View");
    JsonObject dialog = h.awaitPanelShell(before);
    assertEquals("New Problems View", dialog.get("title").getAsString(), dialog.toString());
    String sid = dialog.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "Second problems");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    JsonObject panel = h.await2("the new view", () -> panelTitled("Second problems"));
    assertEquals("panel", panel.get("host").getAsString(), panel.toString());
    String shell = panel.get("shell").getAsString();
    h.await("the problems of the new view", v -> hasRow(shell, "undefined symbol") || hasRow(shell, "Errors (3 items)"));
    assertNotNull(find(menuItems(PROBLEMS + "#1.viewMenu"), "Group By"));
  }

  @Test
  @Order(8)
  void deleteRemovesTheProblem() throws Exception {
    String tree = problemsShell();
    focus(tree);
    String group = h.await2("the Infos group", () -> groupRow(tree, "Infos (1 item)"));
    event(table(tree), "expand", "item", group);
    String row = h.await2("the rename row", () -> rowOf(tree, "consider a rename"));
    selectRows(table(tree), row);
    int before = markerCount(IMarker.PROBLEM);
    run(PROBLEMS + ".popup", "Delete");
    h.await("the problem deleted", v -> markerCount(IMarker.PROBLEM) == before - 1);
    h.await("the problem gone from the view", v -> !hasRow(tree, "consider a rename"));
  }

  @Test
  @Order(9)
  void filtersWithTheSeverityAreaHideTheErrors() throws Exception {
    String tree = problemsShell();
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runAsync(PROBLEMS + ".viewMenu", "Filters...");
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Filters", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    // SeverityAndDescriptionConfigurationArea: "Where severity is:" Error, Warning, Info
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    assertTrue(snap.contains("Where severit&y is:") && snap.contains("\"Error\"") && snap.contains("\"Warning\"")
        && snap.contains("\"Info\""), snap);
    event(widget(sid, "button", "Show all items"), "selection", "selection", false);
    event(widget(sid, "button", "New"), "selection", null, null);
    event(widget(sid, "button", "Error"), "selection", "selection", false);
    event(widget(sid, "button", "Info"), "selection", "selection", false);
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("only the warnings shown", v -> {
      List<String> t = allTexts(tree);
      return t.stream().anyMatch(x -> x.startsWith("Warnings")) && t.stream().noneMatch(x -> x.startsWith("Errors"));
    });
  }
}
