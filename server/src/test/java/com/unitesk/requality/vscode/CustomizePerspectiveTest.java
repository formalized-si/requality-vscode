package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.unitesk.requality.vscode.ui.workbench.ShowViewMenu;
import com.unitesk.requality.vscode.ui.workbench.internal.MainMenu;

/**
 * Window &gt; Perspective &gt; Customize Perspective...: the dialog with its four tabs, the Menu Visibility tree (a hidden
 * item is published as {@code requality.menuHidden.<id>} and survives a perspective switch), the Show View shortcuts
 * (published as {@code requality.showViewMenu.<view id>}), and Reset Perspective, which restores the defaults.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CustomizePerspectiveTest {
  static final String CUSTOMIZE = "org.eclipse.ui.window.customizePerspective";
  static final String RESET = "org.eclipse.ui.window.resetPerspective";
  static final String SHOW_PERSPECTIVE = "org.eclipse.ui.perspectives.showPerspective";
  static final String PERSPECTIVE_ID = "org.eclipse.ui.perspectives.showPerspective.perspectiveId";
  static final String REQUALITY = "com.unitesk.requality.main.perspective";
  static final String TEAM = "org.eclipse.team.ui.TeamSynchronizingPerspective";
  static final String TITLE = "Customize Perspective - Requality";
  static final String PREFERENCES = "requality.preferences";
  static final String PREFERENCES_LABEL = "Preferences";
  static final String PROBLEMS = "org.eclipse.ui.views.ProblemView";
  static final String MENU_KEY = MainMenu.CONTEXT_KEY_PREFIX;
  static final String SHOW_KEY = ShowViewMenu.CONTEXT_KEY_PREFIX;
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

  /** The command with the perspective id parameter (or none); the future ends when the command ends. */
  static CompletableFuture<JsonElement> run(String command, String perspectiveId) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    if (perspectiveId != null) {
      JsonObject params = new JsonObject();
      params.addProperty(PERSPECTIVE_ID, perspectiveId);
      p.add("params", params);
    }
    return h.callAsync("command/execute", p);
  }

  /** The first dialog with the title opened after {@code before} openShell notifications. */
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

  static JsonArray ops(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  static String snapshotText(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  static JsonObject byId(JsonArray ops, String id) {
    for (JsonElement e : ops)
      if (id.equals(e.getAsJsonObject().get("id").getAsString()))
        return e.getAsJsonObject();
    return null;
  }

  static String typeOf(JsonObject o) {
    return o.has("type") ? o.get("type").getAsString() : null;
  }

  static String textOf(JsonObject o) {
    if (!o.has("props") || !o.getAsJsonObject("props").has("texts"))
      return null;
    JsonArray texts = o.getAsJsonObject("props").getAsJsonArray("texts");
    return texts.size() > 0 ? texts.get(0).getAsString() : null;
  }

  /** The widget of the shell with the type and the text (mnemonics removed), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(typeOf(o)))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      String t = props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
      if (text == null || text.equals(t) || t != null && text.equals(t.replace("&", "")))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The nearest ancestor (or the widget itself) of the widget that is a tree, or null. */
  static String treeOf(String shell, String id) throws Exception {
    JsonArray ops = ops(shell);
    String cur = id;
    while (cur != null) {
      JsonObject o = byId(ops, cur);
      if (o == null)
        return null;
      if ("tree".equals(typeOf(o)))
        return cur;
      cur = o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null;
    }
    return null;
  }

  /** The tree item of the shell with the text, in the tree given (or in any tree when the tree is null). */
  static String treeItem(String shell, String tree, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!"treeitem".equals(typeOf(o)) || !text.equals(textOf(o)))
        continue;
      if (tree == null || tree.equals(treeOf(shell, o.get("id").getAsString())))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The row of the table of the shell with the first column's text, or null. */
  static String row(String shell, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("row".equals(typeOf(o)) && text.equals(textOf(o)))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The tree-viewer event: check or uncheck of a tree item, sent to the tree that holds it. */
  static void checkTreeItem(String shell, String item, boolean checked) throws Exception {
    JsonObject d = new JsonObject();
    d.addProperty("item", item);
    d.addProperty("checked", checked);
    event(treeOf(shell, item), "check", d);
  }

  static void selectTreeItem(String shell, String item) throws Exception {
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    event(treeOf(shell, item), "selection", d);
  }

  static void event(String id, String type, JsonObject data) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    p.add("data", data);
    h.notify("ui/event", p);
  }

  static void press(String shell, String label) throws Exception {
    String b = widget(shell, "button", label);
    assertNotNull(b, label + " button: " + snapshotText(shell));
    event(b, "selection", new JsonObject());
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

  /** Opens the dialog of the current perspective; the shell id and the future of the command are returned. */
  static Opened open() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run(CUSTOMIZE, null);
    String shell = awaitShellTitled(before, TITLE).get("shell").getAsString();
    return new Opened(shell, f);
  }

  static final class Opened {
    final String shell;
    final CompletableFuture<JsonElement> command;

    Opened(String shell, CompletableFuture<JsonElement> command) {
      this.shell = shell;
      this.command = command;
    }
  }

  static void showPerspective(String id) throws Exception {
    run(SHOW_PERSPECTIVE, id).get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(1)
  void theDialogHasTheFourTabsOfEclipse() throws Exception {
    showPerspective(REQUALITY);
    Opened d = open();
    String text = snapshotText(d.shell);
    for (String tab : List.of("Toolbar Visibility", "Menu Visibility", "Action Set Availability", "Shortcuts"))
      assertTrue(text.contains(tab), tab + " in " + text);
    assertNotNull(widget(d.shell, "button", "Apply and Close"), text);
    assertNotNull(widget(d.shell, "button", "Cancel"), text);
    h.closeShell(d.shell);
    d.command.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(2)
  void theMenuVisibilityTreeListsTheMenusWithTheirItems() throws Exception {
    Opened d = open();
    String menus = treeOf(d.shell, treeItem(d.shell, null, "File"));
    assertNotNull(menus, snapshotText(d.shell));
    for (String menu : List.of("File", "Edit", "Navigate", "Project", "Window", "Help"))
      assertNotNull(treeItem(d.shell, menus, menu), menu);
    for (String item : List.of("New...", "Save Perspective As...", "Reset Perspective...", "Customize Perspective...",
        "Preferences", "Requality Manual"))
      assertNotNull(treeItem(d.shell, menus, item), item);
    h.closeShell(d.shell);
    d.command.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(3)
  void aHiddenMenuItemIsPublishedAndSurvivesAPerspectiveSwitch() throws Exception {
    Opened d = open();
    String menus = treeOf(d.shell, treeItem(d.shell, null, "File"));
    checkTreeItem(d.shell, treeItem(d.shell, menus, PREFERENCES_LABEL), false);
    press(d.shell, "Apply and Close");
    d.command.get(60, TimeUnit.SECONDS);
    assertContext(MENU_KEY + PREFERENCES, "true");

    showPerspective(TEAM);
    assertContext(MENU_KEY + PREFERENCES, "false");

    showPerspective(REQUALITY);
    assertContext(MENU_KEY + PREFERENCES, "true");
  }

  @Test
  @Order(4)
  void checkingAShowViewShortcutAddsItToTheShowViewMenu() throws Exception {
    Opened d = open();
    // the Show View submenu of the combo (New, Open Perspective, Show View)
    event(widget(d.shell, "combo", null), "selection", index(2));
    selectTreeItem(d.shell, treeItem(d.shell, null, "General"));
    checkRow(d.shell, row(d.shell, "Problems"));
    press(d.shell, "Apply and Close");
    d.command.get(60, TimeUnit.SECONDS);
    assertContext(SHOW_KEY + PROBLEMS, "true");
    assertContext(SHOW_KEY + "org.eclipse.ui.views.PropertySheet", "false");
  }

  static JsonObject index(int i) {
    JsonObject d = new JsonObject();
    d.addProperty("index", i);
    return d;
  }

  /** A check of a table row: the table holds the row (its parent). */
  static void checkRow(String shell, String rowId) throws Exception {
    assertNotNull(rowId, "the Problems shortcut: " + snapshotText(shell));
    JsonObject d = new JsonObject();
    d.addProperty("item", rowId);
    d.addProperty("checked", true);
    String table = byId(ops(shell), rowId).get("parent").getAsString();
    event(table, "check", d);
  }

  @Test
  @Order(5)
  void resetPerspectiveRestoresTheShortcutsAndTheMenuVisibility() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run(RESET, null);
    String question = awaitShellTitled(before, "Reset Perspective").get("shell").getAsString();
    press(question, "Reset Perspective");
    f.get(60, TimeUnit.SECONDS);
    assertContext(MENU_KEY + PREFERENCES, "false");
    assertContext(SHOW_KEY + PROBLEMS, "false");
  }
}
