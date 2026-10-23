package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;
import com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry;

/**
 * Window &gt; Perspective &gt; Save Perspective As..., and Window &gt; Preferences &gt; General &gt; Perspectives: a saved
 * perspective is listed in Open Perspective and in the Perspectives page, it becomes the current one, it can be made the
 * default, saving over a predefined one gives it a custom definition that Revert drops, and Delete removes a saved one.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SavePerspectiveTest {
  static final String REQUALITY = "com.unitesk.requality.main.perspective";
  static final String TEAM = "org.eclipse.team.ui.TeamSynchronizingPerspective";
  static final String MY = "My Persp";
  static final String PAGE = "org.eclipse.ui.preferencePages.Perspectives";
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    h.initialize(h.workspace);
    IDEInternalPreferences.getStore().setValue(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE, true);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

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

  static String shellId(JsonObject shell) {
    return shell.get("shell").getAsString();
  }

  static JsonArray snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  static String snapshotText(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The id of the first widget of the type whose text (mnemonics removed) is the text, or null. */
  static String widget(String shell, String type, String text) throws Exception {
    for (JsonElement e : snapshot(shell)) {
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

  /** The id of the table row with the text (the first column), or null. */
  static String rowOf(String shell, String text) throws Exception {
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!"row".equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonArray texts = o.getAsJsonObject("props").getAsJsonArray("texts");
      if (texts.size() > 0 && text.equals(texts.get(0).getAsString()))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The id of the table of the dialog (its rows are the perspectives; the page's tree is a list). */
  static String tableOf(String shell) throws Exception {
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("table".equals(o.has("type") ? o.get("type").getAsString() : null))
        return o.get("id").getAsString();
    }
    return null;
  }

  static void select(String shell, String row) throws Exception {
    String table = tableOf(shell);
    String id = rowOf(shell, row);
    assertNotNull(id, row + " row");
    JsonObject p = new JsonObject();
    p.addProperty("id", table);
    p.addProperty("type", "selection");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(id);
    d.add("selection", sel);
    d.addProperty("item", id);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  static void type(String text, String value) {
    JsonObject p = new JsonObject();
    p.addProperty("id", text);
    p.addProperty("type", "modify");
    JsonObject d = new JsonObject();
    d.addProperty("text", value);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  static void press(String shell, String label) throws Exception {
    String b = widget(shell, "button", label);
    assertNotNull(b, label + " button");
    JsonObject p = new JsonObject();
    p.addProperty("id", b);
    p.addProperty("type", "selection");
    p.add("data", new JsonObject());
    h.notify("ui/event", p);
  }

  /** The window title last sent to the client, after the wait for one that satisfies the condition. */
  static String awaitTitle(String what, java.util.function.Predicate<String> p) throws Exception {
    h.await(what, v -> {
      List<JsonObject> l = h.notifications("client/windowTitle");
      return !l.isEmpty() && p.test(l.get(l.size() - 1).get("title").getAsString());
    });
    List<JsonObject> l = h.notifications("client/windowTitle");
    return l.get(l.size() - 1).get("title").getAsString();
  }

  /** The stored perspective definitions, read from the workbench preferences file of the server storage. */
  static String storedDefinitions() {
    File file = new File(h.storage, "org.eclipse.ui.workbench.prefs");
    return new PreferenceStore(file).getString(WorkbenchPreferences.PERSPECTIVE_DEFINITIONS);
  }

  /** Opens the Save Perspective As dialog, types the name and presses Save; the future ends with the command. */
  static CompletableFuture<JsonElement> saveAs(String name) throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.window.savePerspective", null);
    String shell = shellId(awaitShellTitled(before, "Save Perspective As..."));
    type(widget(shell, "text", null), name);
    press(shell, "Save");
    return f;
  }

  static CompletableFuture<JsonElement> openPreferences(String pageId) {
    JsonObject p = new JsonObject();
    p.addProperty("page", pageId);
    return h.callAsync("preferences/open", p);
  }

  @Test
  @Order(1)
  void theSaveDialogOpensWithTheCurrentPerspective() throws Exception {
    run("org.eclipse.ui.perspectives.showPerspective", REQUALITY).get(60, TimeUnit.SECONDS);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.window.savePerspective", null);
    String shell = shellId(awaitShellTitled(before, "Save Perspective As..."));
    String text = snapshotText(shell);
    assertTrue(text.contains("Enter or select a name to save the current"), text);
    String name = widget(shell, "text", null);
    assertNotNull(name, text);
    assertEquals("Requality", textOf(shell, name));
    assertNotNull(rowOf(shell, "Requality (default)"), text);
    h.closeShell(shell);
    f.get(60, TimeUnit.SECONDS);
  }

  static String textOf(String shell, String id) throws Exception {
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString()) && o.has("props") && o.getAsJsonObject("props").has("text"))
        return o.getAsJsonObject("props").get("text").getAsString();
    }
    return null;
  }

  @Test
  @Order(2)
  void savingANewNameMakesItTheCurrentPerspective() throws Exception {
    saveAs(MY).get(60, TimeUnit.SECONDS);
    assertEquals("My Persp - Requality", awaitTitle("the title with the saved perspective",
        t -> t.startsWith(MY)));
    assertNotNull(PerspectiveRegistry.INSTANCE.findPerspectiveWithLabel(MY));
    assertTrue(storedDefinitions().contains(MY), storedDefinitions());
  }

  @Test
  @Order(3)
  void theOpenPerspectiveDialogListsTheSavedOne() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.perspectives.showPerspective", null);
    String shell = shellId(awaitShellTitled(before, "Open Perspective"));
    assertNotNull(rowOf(shell, MY), snapshotText(shell));
    h.closeShell(shell);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(4)
  void aNameThatExistsIsConfirmedBeforeItIsReplaced() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.window.savePerspective", null);
    String shell = shellId(awaitShellTitled(before, "Save Perspective As..."));
    type(widget(shell, "text", null), MY);
    int confirmBefore = h.notifications("ui/openShell").size();
    press(shell, "Save");
    String question = shellId(awaitShellTitled(confirmBefore, "Overwrite Perspective"));
    assertTrue(snapshotText(question).contains("A perspective with the name 'My Persp' already exists."));
    press(question, "No");
    h.closeShell(shell);
    f.get(60, TimeUnit.SECONDS);
    assertEquals(1, count(storedDefinitions(), "\"label\":\"" + MY + "\""), storedDefinitions());
  }

  static int count(String s, String what) {
    int n = 0;
    for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + 1))
      n++;
    return n;
  }

  @Test
  @Order(5)
  void thePerspectivesPageListsIt() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences(PAGE);
    String shell = shellId(h.awaitPanelShell(before));
    assertNotNull(rowOf(shell, MY), snapshotText(shell));
    assertNotNull(rowOf(shell, "Requality (default)"), snapshotText(shell));
    h.closeShell(shell);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(6)
  void makeDefaultChangesTheDefaultPerspective() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences(PAGE);
    String shell = shellId(h.awaitPanelShell(before));
    select(shell, MY);
    press(shell, "Make Default");
    assertNotNull(rowOf(shell, MY + " (default)"), snapshotText(shell));
    h.pressDefaultButton(shell);
    f.get(60, TimeUnit.SECONDS);
    assertEquals(PerspectiveRegistry.INSTANCE.findPerspectiveWithLabel(MY).getId(),
        PerspectiveRegistry.INSTANCE.getDefaultPerspective());
  }

  @Test
  @Order(7)
  void savingOverAPredefinedPerspectiveGivesItACustomDefinitionThatRevertDrops() throws Exception {
    run("org.eclipse.ui.perspectives.showPerspective", TEAM).get(60, TimeUnit.SECONDS);
    int confirmBefore = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = saveAsOver("Team Synchronizing", confirmBefore);
    f.get(60, TimeUnit.SECONDS);
    assertTrue(storedDefinitions().contains("\"predefined\":true"), storedDefinitions());
    assertTrue(((PerspectiveRegistry.PerspectiveDescriptor) PerspectiveRegistry.INSTANCE.findPerspectiveWithId(TEAM))
        .hasCustomDefinition());

    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> p = openPreferences(PAGE);
    String shell = shellId(h.awaitPanelShell(before));
    select(shell, "Team Synchronizing");
    press(shell, "Revert");
    h.pressDefaultButton(shell);
    p.get(60, TimeUnit.SECONDS);
    assertFalse(storedDefinitions().contains("\"id\":\"" + TEAM + "\""), storedDefinitions());
    assertFalse(((PerspectiveRegistry.PerspectiveDescriptor) PerspectiveRegistry.INSTANCE.findPerspectiveWithId(TEAM))
        .hasCustomDefinition());
  }

  /** Save As with a name that exists: the overwrite question is answered Yes. */
  static CompletableFuture<JsonElement> saveAsOver(String name, int confirmBefore) throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = run("org.eclipse.ui.window.savePerspective", null);
    String shell = shellId(awaitShellTitled(before, "Save Perspective As..."));
    type(widget(shell, "text", null), name);
    press(shell, "Save");
    String question = shellId(awaitShellTitled(Math.max(before, confirmBefore), "Overwrite Perspective"));
    press(question, "Yes");
    return f;
  }

  @Test
  @Order(8)
  void deleteRemovesASavedPerspective() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences(PAGE);
    String shell = shellId(h.awaitPanelShell(before));
    select(shell, MY + " (default)");
    int confirmBefore = h.notifications("ui/openShell").size();
    press(shell, "Delete");
    // the saved perspective is open: the question is confirmed
    String question = shellId(awaitShellTitled(confirmBefore, "Delete Perspective"));
    assertTrue(snapshotText(question).contains("Delete the 'My Persp' perspective? It has open instances."));
    press(question, "Yes");
    assertNull(rowOf(shell, MY + " (default)"), snapshotText(shell));
    h.pressDefaultButton(shell);
    f.get(60, TimeUnit.SECONDS);
    assertNull(PerspectiveRegistry.INSTANCE.findPerspectiveWithLabel(MY));
    assertFalse(storedDefinitions().contains(MY), storedDefinitions());
    assertEquals(PerspectiveRegistry.productDefaultPerspectiveId(), PerspectiveRegistry.INSTANCE.getDefaultPerspective());
  }
}
