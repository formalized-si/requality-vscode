package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences;

/**
 * Window &gt; Perspective: the perspective registry, Open Perspective (the dialog and the Team Synchronizing command),
 * Reset Perspective, Close All Perspectives and the window title with the perspective name.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerspectivesTest {
  static final String REQUALITY = "com.unitesk.requality.main.perspective";
  static final String TEAM = "org.eclipse.team.ui.TeamSynchronizingPerspective";
  static final String SYNC_VIEW = "requality.synchronize";
  static final String HISTORY_VIEW = "requality.history";
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

  /** The command with its parameters; the future ends when the command ends. */
  static CompletableFuture<JsonElement> run(String command, String perspectiveId) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    if (perspectiveId != null) {
      JsonObject params = new JsonObject();
      params.addProperty("org.eclipse.ui.perspectives.showPerspective.perspectiveId", perspectiveId);
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
    List<String> ids = new java.util.ArrayList<>();
    for (JsonObject o : h.notifications("client/showView"))
      ids.add(o.get("id").getAsString());
    return ids;
  }

  static String snapshotText(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The window title last sent to the client. */
  static String lastTitle() throws Exception {
    h.await("a window title", v -> !h.notifications("client/windowTitle").isEmpty());
    List<JsonObject> l = h.notifications("client/windowTitle");
    return l.get(l.size() - 1).get("title").getAsString();
  }

  /** Waits until the last window title sent satisfies the condition, and returns it. */
  static String awaitTitle(String what, java.util.function.Predicate<String> p) throws Exception {
    h.await(what, v -> {
      try {
        return p.test(lastTitle());
      } catch (Exception e) {
        return false;
      }
    });
    return lastTitle();
  }

  @Test
  @Order(1)
  void openPerspectiveDialogListsTheRegistry() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.perspectives.showPerspective", null);
    JsonObject shell = awaitShellTitled(before, "Open Perspective");
    String sid = shell.get("shell").getAsString();
    String text = snapshotText(sid);
    assertTrue(text.contains("Requality (default)"), text);
    assertTrue(text.contains("Team Synchronizing"), text);
    h.closeShell(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(2)
  void openingTeamSynchronizingFromTheDialogShowsItsViews() throws Exception {
    int before = h.notifications("ui/openShell").size();
    int shownBefore = h.notifications("client/showView").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.perspectives.showPerspective", null);
    String sid = awaitShellTitled(before, "Open Perspective").get("shell").getAsString();
    String row = rowOf(sid, "Team Synchronizing");
    JsonObject p = new JsonObject();
    p.addProperty("id", listOf(sid));
    p.addProperty("type", "selection");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(row);
    d.add("selection", sel);
    d.addProperty("item", row);
    p.add("data", d);
    h.notify("ui/event", p);
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("the views of Team Synchronizing", v -> shownViews().subList(Math.min(shownBefore, shownViews().size()),
        shownViews().size()).containsAll(List.of(SYNC_VIEW, HISTORY_VIEW)));
  }

  /** The id of the row of the dialog's table with the text, or null. */
  static String rowOf(String sid, String text) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", sid).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!"row".equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonArray texts = o.getAsJsonObject("props").getAsJsonArray("texts");
      if (texts.size() > 0 && text.equals(texts.get(0).getAsString()))
        return o.get("id").getAsString();
    }
    assertNotNull(null, "the row " + text);
    return null;
  }

  /** The id of the list or table of the dialog's viewer. */
  static String listOf(String sid) throws Exception {
    String id = null;
    for (JsonElement e : h.call("ui/snapshot", "shell", sid).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      String type = o.has("type") ? o.get("type").getAsString() : null;
      if ("table".equals(type) || "list".equals(type))
        return o.get("id").getAsString();
    }
    assertNotNull(id, "the perspectives list of the dialog");
    return id;
  }

  @Test
  @Order(3)
  void theWindowTitleShowsThePerspectiveWhenThePreferenceIsOn() throws Exception {
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE, true);
    run("org.eclipse.ui.perspectives.showPerspective", REQUALITY).get(60, TimeUnit.SECONDS);
    run("org.eclipse.ui.perspectives.showPerspective", TEAM).get(60, TimeUnit.SECONDS);
    String title = awaitTitle("the title with the Team Synchronizing perspective", t -> t.contains("Team Synchronizing"));
    assertEquals("Team Synchronizing - Requality", title);
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE, false);
    run("org.eclipse.ui.perspectives.showPerspective", REQUALITY).get(60, TimeUnit.SECONDS);
    assertEquals("Requality", awaitTitle("the title without the perspective", t -> t.equals("Requality")));
  }

  @Test
  @Order(4)
  void resetPerspectiveAsksAndShowsTheViewsAgain() throws Exception {
    run("org.eclipse.ui.perspectives.showPerspective", TEAM).get(60, TimeUnit.SECONDS);
    int before = h.notifications("ui/openShell").size();
    int shownBefore = h.notifications("client/showView").size();
    int resetBefore = h.notifications("client/resetViewLocations").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.window.resetPerspective", null);
    JsonObject shell = awaitShellTitled(before, "Reset Perspective");
    String text = snapshotText(shell.get("shell").getAsString());
    assertTrue(text.contains("Reset the Team Synchronizing perspective to its defaults?"), text);
    h.pressDefaultButton(shell.get("shell").getAsString());
    f.get(60, TimeUnit.SECONDS);
    h.await("the reset of the view locations", v -> h.notifications("client/resetViewLocations").size() == resetBefore + 1);
    List<JsonObject> reset = h.notifications("client/resetViewLocations");
    assertEquals(TEAM, reset.get(reset.size() - 1).get("perspective").getAsString());
    h.await("the views of Team Synchronizing shown again", v -> shownViews().subList(
        Math.min(shownBefore, shownViews().size()), shownViews().size()).containsAll(List.of(SYNC_VIEW, HISTORY_VIEW)));
  }

  @Test
  @Order(5)
  void closeAllPerspectivesLeavesNothingToReset() throws Exception {
    int hiddenBefore = h.notifications("client/hideView").size();
    run("org.eclipse.ui.window.closeAllPerspectives", null).get(60, TimeUnit.SECONDS);
    // the views of the closed perspectives are hidden in VS Code (the requests go to the client as the views close)
    h.await("the views of the closed perspectives hidden", v -> {
      List<String> hidden = new java.util.ArrayList<>();
      for (JsonObject o : h.notifications("client/hideView").subList(hiddenBefore, h.notifications("client/hideView").size()))
        hidden.add(o.get("id").getAsString());
      return hidden.containsAll(List.of(SYNC_VIEW, HISTORY_VIEW));
    });
    int before = h.notifications("ui/openShell").size();
    run("org.eclipse.ui.window.resetPerspective", null).get(60, TimeUnit.SECONDS);
    assertEquals(before, h.notifications("ui/openShell").size(), "no perspective is open: no question");
  }

  @Test
  @Order(6)
  void theTeamSynchronizingCommandOpensThePerspectiveAgain() throws Exception {
    int shownBefore = h.notifications("client/showView").size();
    run(TEAM, null).get(60, TimeUnit.SECONDS);
    h.await("the Synchronize view shown", v -> shownViews().subList(Math.min(shownBefore, shownViews().size()),
        shownViews().size()).contains(SYNC_VIEW));
  }

  @Test
  @Order(7)
  void theTitleFollowsThePreferencesOfTheTitle() throws Exception {
    // IDEWorkbenchWindowAdvisor: a change of a title preference computes the title again (Team Synchronizing is current)
    int from = titles().size();
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE, true);
    awaitTitleSince(from, "the title with the perspective", "Team Synchronizing - Requality");
    from = titles().size();
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE, false);
    awaitTitleSince(from, "the title without the product", "Team Synchronizing");
    from = titles().size();
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE, true);
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE, false);
    awaitTitleSince(from, "the title of the defaults", "Requality");
  }

  /** The window titles sent to the client, in order. */
  static List<String> titles() {
    List<String> r = new java.util.ArrayList<>();
    for (JsonObject o : h.notifications("client/windowTitle"))
      r.add(o.get("title").getAsString());
    return r;
  }

  /** Waits for a window title sent after the index (a later title of another change does not hide it). */
  static void awaitTitleSince(int from, String what, String title) throws Exception {
    h.await(what, v -> {
      List<String> l = titles();
      return l.subList(Math.min(from, l.size()), l.size()).contains(title);
    });
  }

  /** The regions of the views of the last layout sent for the perspective (client/viewLayout). */
  static java.util.Map<String, String> regions(String perspective) {
    java.util.Map<String, String> r = new java.util.HashMap<>();
    for (JsonObject n : h.notifications("client/viewLayout"))
      if (perspective.equals(n.get("perspective").getAsString())) {
        r.clear();
        for (com.google.gson.JsonElement v : n.getAsJsonArray("views"))
          r.put(v.getAsJsonObject().get("id").getAsString(), v.getAsJsonObject().get("region").getAsString());
      }
    return r;
  }

  @Test
  @Order(8)
  void theLayoutOfAPerspectiveGivesTheRegionsOfItsViews() throws Exception {
    run("org.eclipse.ui.perspectives.showPerspective", REQUALITY).get(60, TimeUnit.SECONDS);
    // ReqEditorPerspectiveFactory: the explorer left, Properties below, Outline right, the Links Explorer below Outline
    java.util.Map<String, String> req = regions(REQUALITY);
    assertEquals("left", req.get("com.unitesk.requality.views.RequalityExplorer"), req.toString());
    assertEquals("bottom", req.get("requality.properties"), req.toString());
    assertEquals("right", req.get("requality.outline"), req.toString());
    assertEquals("right", req.get("requality.links"), req.toString());
    // TeamSynchronizingPerspective: the Synchronize view in the folder left of the editor area, History below
    run("org.eclipse.ui.perspectives.showPerspective", TEAM).get(60, TimeUnit.SECONDS);
    java.util.Map<String, String> team = regions(TEAM);
    assertEquals("left", team.get(SYNC_VIEW), team.toString());
    assertEquals("bottom", team.get(HISTORY_VIEW), team.toString());
    // Reset Perspective sends the layout again, marked as a reset
    int before = h.notifications("client/viewLayout").size();
    int shells = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.window.resetPerspective", null);
    h.pressDefaultButton(awaitShellTitled(shells, "Reset Perspective").get("shell").getAsString());
    f.get(60, TimeUnit.SECONDS);
    h.await("the layout of the reset", v -> h.notifications("client/viewLayout").size() > before);
    JsonObject last = h.notifications("client/viewLayout").get(h.notifications("client/viewLayout").size() - 1);
    assertTrue(last.get("reset").getAsBoolean(), last.toString());
  }
}
