package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.team.history.LocalHistoryPage;
import com.unitesk.requality.vscode.ui.team.preferences.IPreferenceIds;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.team.history.GenericHistoryView;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.workbench.IViewReference;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * The local history of a file (org.eclipse.team.ui): Team &gt; Show Local History opens the History view with the states of
 * the file, Open of a state shows its contents read-only, Compare compares it with the file, Get Contents replaces the file
 * with it, a file without states has no history, Compare With &gt; Local History... compares by double click, and Replace With
 * &gt; Local History... replaces the file with the state selected in the dialog.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalHistoryTest {
  static final String POPUP = ProjectOperationsTest.POPUP;
  static final String VIEW = "requality.history";
  static final String CATEGORY_TODAY = "Today";
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "Plain");
    plain.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "a.txt").toPath(), "one\n");
    Files.writeString(new File(plain, "nohist.txt").toPath(), "never changed\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await2("the plain project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Plain"))
          return e;
      return null;
    });
    // two states of a.txt in the local history: "one" then "two"; the file is "three"
    replaceKeepingHistory("two\n");
    replaceKeepingHistory("three\n");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static void replaceKeepingHistory(String contents) throws Exception {
    Thread.sleep(20);
    ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(new File(plain, "a.txt").getAbsolutePath()))
        .setContents(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), IResource.KEEP_HISTORY, null);
  }

  static String file(String name) {
    return ExplorerService.RESOURCE_PREFIX + new File(plain, name).getAbsolutePath();
  }

  /** The explorer popup of the items (see TeamCompareTest). */
  static JsonArray popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

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

  static CompletableFuture<JsonElement> runAsync(JsonObject item) {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  static void run(JsonObject item) throws Exception {
    runAsync(item).get(60, TimeUnit.SECONDS);
  }

  /** The shell of the History view (the view is shown in the Requality panel). */
  static String viewShell() throws Exception {
    h.call("views/open", "view", VIEW);
    return h.await2("the shell of the History view", () -> {
      String shell = null;
      for (JsonObject o : h.notifications("ui/openShell"))
        if (o.has("host") && "view".equals(o.get("host").getAsString()) && o.has("view")
            && VIEW.equals(o.get("view").getAsString()))
          shell = o.get("shell").getAsString();
      return shell;
    });
  }

  /** The ops of a shell snapshot. */
  static JsonArray ops(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  static String typeOf(JsonObject o) {
    return o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : null;
  }

  static List<String> texts(JsonObject o) {
    List<String> r = new ArrayList<>();
    if (o.has("props") && o.getAsJsonObject("props").has("texts"))
      for (JsonElement t : o.getAsJsonObject("props").getAsJsonArray("texts"))
        r.add(t.getAsString());
    return r;
  }

  /** The id of the widget of the type in the shell, or null. */
  static String widget(String shell, String type) throws Exception {
    for (JsonElement e : ops(shell))
      if (type.equals(typeOf(e.getAsJsonObject())))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The id of the widget of the type with the text (ampersands ignored), or null. */
  static String widgetWithText(String shell, String type, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(typeOf(o)))
        continue;
      List<String> t = texts(o);
      if (o.has("props") && o.getAsJsonObject("props").has("text") && !o.getAsJsonObject("props").get("text").isJsonNull())
        t.add(o.getAsJsonObject("props").get("text").getAsString());
      if (t.stream().anyMatch(x -> x.replace("&", "").equals(text)))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The revision rows of the tree in the shell: the items below the Today category, in order. */
  static List<String> todayRows(String shell) throws Exception {
    List<String> rows = new ArrayList<>();
    boolean inToday = false;
    Set<String> categories = Set.of("Today", "Yesterday", "This Month", "Older than This Month", "No revisions found");
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!"treeitem".equals(typeOf(o)))
        continue;
      List<String> t = texts(o);
      String first = t.isEmpty() ? "" : t.get(0);
      if (categories.contains(first)) {
        inToday = CATEGORY_TODAY.equals(first);
      } else if (inToday) {
        rows.add(o.get("id").getAsString());
      }
    }
    return rows;
  }

  /** Selects the tree items of the shell's tree (a click of the user). */
  static void select(String tree, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Double clicks a tree item (the default selection of the tree). */
  static void doubleClick(String tree, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "default");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Opens the context menu of the tree and runs its item with the label. */
  static void contextMenu(String shell, String tree, String label) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "menudetect");
    JsonObject d = new JsonObject();
    d.addProperty("x", 0);
    d.addProperty("y", 0);
    p.add("data", d);
    h.notify("ui/event", p);
    String menuItem = h.await2("the menu item " + label, () -> widgetWithText(shell, "menuitem", label));
    JsonObject ev = new JsonObject();
    ev.addProperty("id", menuItem);
    ev.addProperty("type", "selection");
    ev.add("data", new JsonObject());
    h.notify("ui/event", ev);
  }

  /** The tree of the shell, waiting until the revisions are shown. */
  static String treeWithRevisions(String shell) throws Exception {
    h.await("the revisions of the history", v -> {
      try {
        return todayRows(shell).size() >= 3;
      } catch (Exception e) {
        return false;
      }
    });
    return widget(shell, "tree");
  }

  /** The contents of the workspace file. */
  static String contents() throws Exception {
    return Files.readString(new File(plain, "a.txt").toPath());
  }

  @Test
  @Order(1)
  void showLocalHistoryOpensTheHistoryView() throws Exception {
    JsonArray items = popup(file("a.txt"));
    JsonObject show = item(items, "Show Local History");
    assertNotNull(show);
    assertTrue(show.get("enabled").getAsBoolean(), "Show Local History of a file");
    run(show);
    String shell = viewShell();
    String snap = h.call("ui/snapshot", "shell", shell).toString();
    assertTrue(snap.contains("Revision Time"), snap);
    // Group Revisions by Date is off by default (the Team UI preference has no default): the revisions are flat
    h.await("the revisions of the history", v -> {
      try {
        return treeItems(shell) >= 3;
      } catch (Exception e) {
        return false;
      }
    });
    assertFalse(h.call("ui/snapshot", "shell", shell).toString().contains(CATEGORY_TODAY));
    // the Group Revisions by Date action of the page groups them by date and is remembered in the preference store
    Action group = groupByDateAction();
    Display.getDefault().syncExec(group::run);
    assertTrue(TeamUIPlugin.getPreferenceStore().getBoolean(IPreferenceIds.PREF_GROUPBYDATE_MODE));
    String tree = treeWithRevisions(shell);
    assertNotNull(tree);
    assertTrue(h.call("ui/snapshot", "shell", shell).toString().contains(CATEGORY_TODAY));
  }

  @Test
  @Order(2)
  void openOfAStateShowsItsContentsReadOnly() throws Exception {
    String shell = viewShell();
    String tree = treeWithRevisions(shell);
    List<String> rows = todayRows(shell);
    String oldest = rows.get(rows.size() - 1);
    int before = h.notifications("client/openContent").size();
    doubleClick(tree, oldest);
    h.await("client/openContent", v -> h.notifications("client/openContent").size() > before);
    JsonObject p = h.notifications("client/openContent").get(before);
    assertEquals("a.txt", p.get("name").getAsString());
    assertEquals("one\n", h.call("compare/content", "id", p.get("id").getAsString()).getAsString());
  }

  @Test
  @Order(3)
  void compareOfAStateOpensTheDiff() throws Exception {
    String shell = viewShell();
    String tree = treeWithRevisions(shell);
    String oldest = todayRows(shell).get(2);
    select(tree, oldest);
    int before = h.notifications("client/openDiff").size();
    contextMenu(shell, tree, "Compare Current with Local");
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > before);
    JsonObject p = h.notifications("client/openDiff").get(before);
    assertEquals("Compare a.txt Current and Local Revision", p.get("title").getAsString(), p.toString());
  }

  /** The texts of the menu items of the shell (its context menus), in order, ampersands removed. */
  static List<String> menuTexts(String shell) throws Exception {
    List<String> r = new ArrayList<>();
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("menuitem".equals(typeOf(o)) && o.has("props") && o.getAsJsonObject("props").has("text"))
        r.add(o.getAsJsonObject("props").get("text").getAsString().replace("&", ""));
    }
    return r;
  }

  /** Whether the menu item with the text is selected (a checked radio item). */
  static boolean menuItemSelected(String shell, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if ("menuitem".equals(typeOf(o)) && props.has("text") && text.equals(props.get("text").getAsString().replace("&", "")))
        return props.has("selection") && props.get("selection").getAsBoolean();
    }
    throw new AssertionError("no menu item " + text);
  }

  @Test
  @Order(4)
  void openWithOpensARevisionInTheChosenEditor() throws Exception {
    String shell = viewShell();
    String tree = treeWithRevisions(shell);
    List<String> rows = todayRows(shell);
    String oldest = rows.get(rows.size() - 1);
    select(tree, oldest);
    // Open With > Text Editor: the revision opens read-only in the text editor (FileRevisionEditorInput).
    int before = h.notifications("client/openContent").size();
    contextMenu(shell, tree, "Text Editor");
    h.await("client/openContent", v -> h.notifications("client/openContent").size() > before);
    JsonObject p = h.notifications("client/openContent").get(before);
    assertEquals("a.txt", p.get("name").getAsString());
    assertFalse(p.has("editor"), p.toString());
    assertEquals("one\n", h.call("compare/content", "id", p.get("id").getAsString()).getAsString());
    // the context menu: Open, the Open With submenu (the editors of the revision, the preferred one checked,
    // Default Editor, Other...), then Compare (LocalHistoryPage.fillTableMenu)
    List<String> texts = menuTexts(shell);
    int open = texts.indexOf("Open");
    assertTrue(open >= 0, texts.toString());
    assertEquals(List.of("Open", "Open With", "Text Editor", "Default Editor", "Other...", "Compare Current with Local"),
        List.of(texts.get(open), texts.get(open + 1), texts.get(open + 2), texts.get(open + 3), texts.get(open + 4),
            texts.get(open + 5)), texts.toString());
    assertTrue(menuItemSelected(shell, "Text Editor"), "the preferred editor of a.txt is checked");
    assertFalse(menuItemSelected(shell, "Default Editor"));

    // The current revision is the workspace file: Open With opens the file itself (FileEditorInput).
    select(tree, rows.get(0));
    int files = h.notifications("client/openFile").size();
    contextMenu(shell, tree, "Text Editor");
    h.await("client/openFile", v -> h.notifications("client/openFile").size() > files);
    JsonObject f = h.notifications("client/openFile").get(files);
    assertEquals(new File(plain, "a.txt").getCanonicalPath(), new File(f.get("path").getAsString()).getCanonicalPath());
    assertEquals("default", f.get("editor").getAsString());

    // Other...: the Editor Selection dialog for the revision.
    select(tree, oldest);
    int shells = h.notifications("ui/openShell").size();
    contextMenu(shell, tree, "Other...");
    JsonObject dialog = h.awaitPanelShell(shells);
    assertEquals("Editor Selection", dialog.get("title").getAsString(), dialog.toString());
    String dialogShell = dialog.get("shell").getAsString();
    assertTrue(h.call("ui/snapshot", "shell", dialogShell).toString().contains("Choose the editor for opening a.txt:"));
    h.closeShell(dialogShell);
  }

  @Test
  @Order(5)
  void fileWithoutStatesHasNoHistory() throws Exception {
    JsonArray items = popup(ExplorerService.RESOURCE_PREFIX + new File(plain, "nohist.txt").getAbsolutePath());
    JsonObject show = item(items, "Show Local History");
    assertNotNull(show);
    int before = h.clientRequests.size();
    run(show);
    h.await("the no local history message", v -> {
      for (int k = before; k < h.clientRequests.size(); k++)
        if (h.clientRequests.get(k).getKey().equals("client/showMessage")
            && h.clientRequests.get(k).getValue().toString().contains("No local history available for selected resource."))
          return true;
      return false;
    });
  }

  @Test
  @Order(6)
  void compareWithLocalHistoryComparesByDoubleClick() throws Exception {
    JsonArray items = popup(file("a.txt"));
    JsonObject compare = submenu(items, "Compare With");
    assertNotNull(compare, "Compare With submenu");
    JsonObject local = item(compare.getAsJsonArray("children"), "Local History...");
    assertNotNull(local, "Compare With > Local History...");
    run(local);
    String shell = viewShell();
    String tree = treeWithRevisions(shell);
    String oldest = todayRows(shell).get(2);
    int before = h.notifications("client/openDiff").size();
    doubleClick(tree, oldest);
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > before);
    assertEquals("Compare a.txt Current and Local Revision", h.notifications("client/openDiff").get(before).get("title").getAsString());
  }

  @Test
  @Order(7)
  void replaceWithLocalHistoryReplacesTheFile() throws Exception {
    assertEquals("three\n", contents());
    JsonArray items = popup(file("a.txt"));
    JsonObject replace = submenu(items, "Replace With");
    assertNotNull(replace, "Replace With submenu");
    JsonObject local = item(replace.getAsJsonArray("children"), "Local History...");
    assertNotNull(local, "Replace With > Local History...");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(local);
    JsonObject shell = h.awaitShell(before);
    String sid = shell.get("shell").getAsString();
    // the dialog shows the states only (the modal page has no current file revision): the most recent is "two"
    String tree = h.await2("the tree of the replace dialog", () -> {
      String t = widget(sid, "tree");
      return t != null && todayRows(sid).size() == 2 ? t : null;
    });
    select(tree, todayRows(sid).get(0));
    h.await("the text compare of the selected state", v -> {
      try {
        return h.call("ui/snapshot", "shell", sid).toString().contains("Text Compare");
      } catch (Exception e) {
        return false;
      }
    });
    String replaceButton = h.await2("the Replace button", () -> widgetWithText(sid, "button", "Replace"));
    JsonObject ev = new JsonObject();
    ev.addProperty("id", replaceButton);
    ev.addProperty("type", "selection");
    ev.add("data", new JsonObject());
    h.notify("ui/event", ev);
    ran.get(60, TimeUnit.SECONDS);
    h.await("the replaced file", v -> {
      try {
        return "two\n".equals(contents());
      } catch (Exception e) {
        return false;
      }
    });
    assertFalse(contents().isEmpty());
  }

  @Test
  @Order(8)
  void getContentsReplacesTheFileWithTheState() throws Exception {
    assertEquals("two\n", contents());
    String shell = viewShell();
    String tree = treeWithRevisions(shell);
    String oldest = todayRows(shell).get(2);
    select(tree, oldest);
    h.autoAnswer.add("Overwrite Current Revision?");
    contextMenu(shell, tree, "Get Contents");
    h.await("the file with the contents of the oldest state", v -> {
      try {
        return "one\n".equals(contents());
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** The History view instance of the workbench, or null when it is not open. */
  static GenericHistoryView historyView() throws Exception {
    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    for (IViewReference r : page.getViewReferences())
      if (GenericHistoryView.ID.equals(r.getId()) && r.getView(false) instanceof GenericHistoryView v)
        return v;
    return null;
  }

  /** Whether the page of the view shows the workspace file. */
  static boolean showsFile(GenericHistoryView view, File f) throws Exception {
    Object input = Display.getDefault().syncCall(() -> view.getHistoryPage() == null ? null : view.getHistoryPage().getInput());
    if (!(input instanceof IResource r) || r.getLocation() == null)
      return false;
    return new File(r.getLocation().toOSString()).getCanonicalPath().equals(f.getCanonicalPath());
  }

  /** The number of tree items of the shell. */
  static int treeItems(String shell) throws Exception {
    int n = 0;
    for (JsonElement e : ops(shell))
      if ("treeitem".equals(typeOf(e.getAsJsonObject())))
        n++;
    return n;
  }

  /** The "Group Revisions by Date" action of the local history page shown by the History view. */
  static Action groupByDateAction() throws Exception {
    GenericHistoryView view = h.await2("the History view", LocalHistoryTest::historyView);
    LocalHistoryPage page = h.await2("the local history page",
        () -> view.getHistoryPage() instanceof LocalHistoryPage p ? p : null);
    Field f = LocalHistoryPage.class.getDeclaredField("groupByDateMode");
    f.setAccessible(true);
    return (Action) f.get(page);
  }

  /** The "Show Previous Histories" action of the view (its drop-down is not in the snapshots of the views). */
  static Action navigateAction(GenericHistoryView view) throws Exception {
    Field f = GenericHistoryView.class.getDeclaredField("navigateAction");
    f.setAccessible(true);
    return (Action) f.get(view);
  }

  /** Selects the item of the Requality Explorer (a click of the user). */
  static void selectInExplorer(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  @Test
  @Order(9)
  void showPreviousHistoriesGoesBackToTheHistoryShownBefore() throws Exception {
    File c = new File(plain, "c.txt");
    Files.writeString(c.toPath(), "c zero\n");
    Thread.sleep(20);
    TeamCompareTest.replaceKeepingHistory(c, "c one\n");
    JsonObject show = item(popup(file("c.txt")), "Show Local History");
    assertNotNull(show);
    run(show);
    GenericHistoryView view = historyView();
    assertNotNull(view, "the History view is open");
    h.await("the history of c.txt", v -> {
      try {
        return showsFile(view, c);
      } catch (Exception e) {
        return false;
      }
    });
    Action previous = navigateAction(view);
    assertTrue(previous.isEnabled(), "Show Previous Histories with a history shown before");
    Display.getDefault().syncExec(previous::run);
    h.await("the history of a.txt, shown before c.txt", v -> {
      try {
        return showsFile(view, new File(plain, "a.txt"));
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(10)
  void linkedViewFollowsTheSelectionOfTheExplorer() throws Exception {
    GenericHistoryView view = historyView();
    assertNotNull(view);
    Display.getDefault().syncExec(() -> view.setLinkingEnabled(true));
    try {
      selectInExplorer(file("a.txt"));
      h.await("the history of the selected a.txt", v -> {
        try {
          return showsFile(view, new File(plain, "a.txt"));
        } catch (Exception e) {
          return false;
        }
      });
      selectInExplorer(file("c.txt"));
      h.await("the history of the selected c.txt", v -> {
        try {
          return showsFile(view, new File(plain, "c.txt"));
        } catch (Exception e) {
          return false;
        }
      });
    } finally {
      Display.getDefault().syncExec(() -> view.setLinkingEnabled(false));
    }
    assertFalse(TeamUIPlugin.getPreferenceStore().getBoolean("pref_generichistory_view_linking"));
    selectInExplorer(file("a.txt"));
    Thread.sleep(500);
    assertTrue(showsFile(view, new File(plain, "c.txt")),
        "an unlinked view keeps its history; it shows "
            + Display.getDefault().syncCall(() -> view.getHistoryPage() == null ? null : view.getHistoryPage().getInput()));
  }

  @Test
  @Order(11)
  void linkedViewFollowsTheActiveEditor() throws Exception {
    GenericHistoryView view = historyView();
    assertNotNull(view);
    Display.getDefault().syncExec(() -> view.setLinkingEnabled(true));
    try {
      // the extension reports the file of the active text editor (GenericHistoryView.editorActivated)
      JsonObject p = new JsonObject();
      p.addProperty("path", new File(plain, "a.txt").getAbsolutePath());
      h.notify("editor/activeTextEditor", p);
      h.await("the history of the edited a.txt", v -> {
        try {
          return showsFile(view, new File(plain, "a.txt"));
        } catch (Exception e) {
          return false;
        }
      });
      p.addProperty("path", new File(plain, "c.txt").getAbsolutePath());
      h.notify("editor/activeTextEditor", p);
      h.await("the history of the edited c.txt", v -> {
        try {
          return showsFile(view, new File(plain, "c.txt"));
        } catch (Exception e) {
          return false;
        }
      });
    } finally {
      Display.getDefault().syncExec(() -> view.setLinkingEnabled(false));
    }
  }

  @Test
  @Order(12)
  void aFileDroppedOnTheViewShowsItsHistory() throws Exception {
    String shell = viewShell();
    GenericHistoryView view = h.await2("the History view", LocalHistoryTest::historyView);
    // GenericHistoryView.initDragAndDrop: the view is a drop target of resources (GenericHistoryDropAdapter)
    String target = h.await2("the drop target of the view", () -> {
      for (JsonElement e : ops(shell)) {
        JsonObject o = e.getAsJsonObject();
        if (o.has("props") && o.getAsJsonObject("props").has("dropTarget")
            && o.getAsJsonObject("props").get("dropTarget").getAsBoolean())
          return o.get("id").getAsString();
      }
      return null;
    });
    File dropped = new File(plain, "nohist.txt");
    JsonObject d = new JsonObject();
    JsonArray uris = new JsonArray();
    uris.add(dropped.toURI().toString());
    d.add("uris", uris);
    d.addProperty("text", "");
    JsonObject p = new JsonObject();
    p.addProperty("id", target);
    p.addProperty("type", "drop");
    p.add("data", d);
    h.notify("ui/event", p);
    h.await("the history of the dropped file", v -> {
      try {
        return showsFile(view, dropped);
      } catch (Exception e) {
        return false;
      }
    });
  }
}
