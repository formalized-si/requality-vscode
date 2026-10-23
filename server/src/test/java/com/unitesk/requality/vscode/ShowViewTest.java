package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.ShowViewMenu;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.views.IViewCategory;
import com.unitesk.requality.vscode.ui.workbench.views.IViewDescriptor;
import com.unitesk.requality.vscode.ui.workbench.views.IViewRegistry;

/**
 * Window &gt; Show View: the view registry (the platform views and the Requality views in their categories), the Show View
 * dialog (Other...), the command {@code org.eclipse.ui.views.showView} with a view id, and the items of the Show View menu
 * (the shortcuts of the perspective and the views opened with Other...), published as the context keys
 * {@code requality.showViewMenu.<view id>}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShowViewTest {
  static final String SHOW_VIEW = "org.eclipse.ui.views.showView";
  static final String VIEW_ID = "org.eclipse.ui.views.showView.viewId";
  static final String PROPERTIES = "org.eclipse.ui.views.PropertySheet";
  static final String OUTLINE = "org.eclipse.ui.views.ContentOutline";
  static final String BOOKMARKS = "org.eclipse.ui.views.BookmarkView";
  static final String TASKS = "org.eclipse.ui.views.TaskList";
  static final String PROBLEMS = "org.eclipse.ui.views.ProblemView";
  static final String SYNC = "org.eclipse.team.sync.views.SynchronizeView";
  static final String HISTORY = "org.eclipse.team.ui.GenericHistoryView";
  static final String REQUALITY_EXPLORER = "com.unitesk.requality.views.RequalityExplorer";
  static final String TEAM = "org.eclipse.team.ui.TeamSynchronizingPerspective";
  static final String KEY = ShowViewMenu.CONTEXT_KEY_PREFIX;
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    h.initialize(h.workspace);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** The command with the view id parameter (or without parameters); the future ends when the command ends. */
  static CompletableFuture<JsonElement> run(String command, String viewId) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    if (viewId != null) {
      JsonObject params = new JsonObject();
      params.addProperty(VIEW_ID, viewId);
      p.add("params", params);
    }
    return h.callAsync("command/execute", p);
  }

  /** The first dialog opened after {@code before} openShell notifications with the title. */
  static JsonObject awaitShellTitled(int before, String title) throws Exception {
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

  /** The ids of the views shown by the client (client/showView), in order. */
  static List<String> shownViews() {
    List<String> ids = new ArrayList<>();
    for (JsonObject o : h.notifications("client/showView"))
      ids.add(o.get("id").getAsString());
    return ids;
  }

  /** The ids of the views shown after the given count of shown views, once the client got the first of them. */
  static List<String> shownSince(int before) throws Exception {
    h.await("a view shown", v -> h.notifications("client/showView").size() > before);
    List<String> ids = shownViews();
    return ids.subList(before, ids.size());
  }

  static String snapshotText(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The id of the tree of the dialog's shell. */
  static String treeOf(String shell) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("tree".equals(o.has("type") ? o.get("type").getAsString() : null))
        return o.get("id").getAsString();
    }
    throw new AssertionError("the tree of the dialog: " + snapshotText(shell));
  }

  /** The id of the tree item of the dialog with the text, or null. */
  static String treeItem(String shell, String text) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!"treeitem".equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonArray texts = o.getAsJsonObject("props").getAsJsonArray("texts");
      if (texts.size() > 0 && text.equals(texts.get(0).getAsString()))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** Expands a category of the dialog's tree (a double click expands it as well). */
  static void expand(String shell, String text) throws Exception {
    JsonObject d = new JsonObject();
    d.addProperty("item", treeItem(shell, text));
    JsonObject p = new JsonObject();
    p.addProperty("id", treeOf(shell));
    p.addProperty("type", "expand");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Selects the tree item of the dialog with the text (replacing the selection). */
  static void select(String shell, String text) throws Exception {
    String item = treeItem(shell, text);
    assertNotNull(item, text + " in the dialog: " + snapshotText(shell));
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", treeOf(shell));
    p.addProperty("type", "selection");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** The value of the last client/setContext notification for a key, or null. */
  static String context(String key) {
    String value = null;
    for (JsonObject n : h.notifications("client/setContext"))
      if (key.equals(n.get("key").getAsString()))
        value = n.get("value").isJsonNull() ? null : n.get("value").getAsString();
    return value;
  }

  /** Waits until the context key has the expected value (10 seconds at most), then asserts it. */
  static void assertContext(String key, String expected) throws Exception {
    long end = System.currentTimeMillis() + 10_000;
    while (!expected.equals(context(key)) && System.currentTimeMillis() < end)
      Thread.sleep(100);
    assertEquals(expected, context(key), key);
  }

  /** The keys of the Show View menu of the registry, with the value expected: the views listed are true. */
  static void assertMenu(List<String> listed) throws Exception {
    for (IViewDescriptor d : PlatformUI.getWorkbench().getViewRegistry().getViews())
      assertContext(KEY + d.getId(), listed.contains(d.getId()) ? "true" : "false");
  }

  @Test
  @Order(1)
  void registryListsPlatformAndRequalityViewsInTheirCategories() {
    IViewRegistry reg = PlatformUI.getWorkbench().getViewRegistry();
    List<String> categories = new ArrayList<>();
    for (IViewCategory c : reg.getCategories())
      categories.add(c.getLabel());
    assertTrue(categories.containsAll(List.of("General", "Version Control (Team)", "Requality")), categories.toString());
    IViewCategory general = null;
    for (IViewCategory c : reg.getCategories())
      if ("org.eclipse.ui".equals(c.getId()))
        general = c;
    assertNotNull(general);
    List<String> generalViews = new ArrayList<>();
    for (IViewDescriptor d : general.getViews())
      generalViews.add(d.getId());
    assertTrue(generalViews.containsAll(List.of(PROPERTIES, OUTLINE, BOOKMARKS, TASKS, PROBLEMS)), generalViews.toString());
    assertEquals("Properties", reg.find(PROPERTIES).getLabel());
    assertEquals("Synchronize", reg.find(SYNC).getLabel());
    assertEquals("org.eclipse.team.ui", reg.find(HISTORY).getCategoryPath()[0]);
    assertEquals("History", reg.find(HISTORY).getLabel());
    assertEquals("com.unitesk.requality.reqeditor", reg.find(REQUALITY_EXPLORER).getCategoryPath()[0]);
    assertEquals(null, reg.find("no.such.view"));
  }

  @Test
  @Order(2)
  void initiallyNoItemOfTheShowViewMenuIsListed() throws Exception {
    // the Requality perspective has no show view shortcuts, and no view was opened with Other...
    h.await("the items of the Show View menu", v -> context(KEY + PROPERTIES) != null);
    assertMenu(List.of());
  }

  @Test
  @Order(3)
  void showViewDialogListsTheViewsInTheirCategories() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run(SHOW_VIEW, null);
    String sid = awaitShellTitled(before, "Show View").get("shell").getAsString();
    expand(sid, "General");
    expand(sid, "Version Control (Team)");
    expand(sid, "Requality");
    String text = snapshotText(sid);
    for (String label : List.of("Properties", "Outline", "Bookmarks", "Tasks", "Problems", "Progress", "History",
        "Synchronize", "Requality Explorer"))
      assertTrue(text.contains(label), label + " in " + text);
    h.closeShell(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(4)
  void openShowsTheSelectedViewAndItBecomesAnItemOfTheMenu() throws Exception {
    int before = h.notifications("ui/openShell").size();
    int shownBefore = h.notifications("client/showView").size();
    CompletableFuture<JsonElement> f = run(SHOW_VIEW, null);
    String sid = awaitShellTitled(before, "Show View").get("shell").getAsString();
    expand(sid, "General");
    select(sid, "Tasks");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    assertEquals(List.of(Workbench.hostView(TASKS)), shownSince(shownBefore));
    assertEquals("requality.tasks", Workbench.hostView(TASKS));
    assertContext(KEY + TASKS, "true");
    assertContext(KEY + PROPERTIES, "false");
  }

  @Test
  @Order(5)
  void commandWithAViewIdShowsTheViewWithoutTheDialog() throws Exception {
    int before = h.notifications("ui/openShell").size();
    int shownBefore = h.notifications("client/showView").size();
    h.callAsync("command/execute", commandParams(BOOKMARKS)).get(60, TimeUnit.SECONDS);
    assertEquals(List.of(Workbench.hostView(BOOKMARKS)), shownSince(shownBefore));
    List<JsonObject> dialogs = h.notifications("ui/openShell").subList(before, h.notifications("ui/openShell").size());
    for (JsonObject d : dialogs)
      assertFalse("Show View".equals(d.has("title") ? d.get("title").getAsString() : null), "no dialog: " + d);
    assertContext(KEY + BOOKMARKS, "false");
  }

  static JsonObject commandParams(String viewId) {
    JsonObject p = new JsonObject();
    p.addProperty("command", SHOW_VIEW);
    JsonObject params = new JsonObject();
    params.addProperty(VIEW_ID, viewId);
    p.add("params", params);
    return p;
  }

  @Test
  @Order(6)
  void aPerspectiveWithShortcutsSetsItsItemsAndKeepsTheOpenedViews() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("command", "org.eclipse.ui.perspectives.showPerspective");
    JsonObject params = new JsonObject();
    params.addProperty("org.eclipse.ui.perspectives.showPerspective.perspectiveId", TEAM);
    p.add("params", params);
    h.callAsync("command/execute", p).get(60, TimeUnit.SECONDS);
    assertContext(KEY + SYNC, "true");
    assertContext(KEY + OUTLINE, "true");
    assertContext(KEY + TASKS, "true");
    assertContext(KEY + PROBLEMS, "true");
    assertContext(KEY + PROPERTIES, "false");
    assertContext(KEY + BOOKMARKS, "false");
  }
}
