package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/**
 * The New submenu of the Requality Explorer for a plain (non-Requality) project, which the navigator resources add
 * after {@code group.new} (org.eclipse.ui.navigator.resources NewActionProvider: Project..., the commonWizard shortcuts
 * Folder and File, Other...), and the new wizard selection dialog that Other... opens.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewWizardsTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static ServerHarness h;
  static String plainItem;
  static String requalityNode;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    // A plain Eclipse project: a folder with a .project file, not a Requality project.
    File plain = new File(h.workspace, "Plain");
    assertTrue(plain.mkdirs());
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>Plain</name><comment></comment><projects></projects>"
        + "<buildSpec></buildSpec><natures></natures></projectDescription>\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    // The other Eclipse projects are listed by the explorer, not by workspace/projects.
    h.await("the plain project in the explorer", v -> {
      try {
        return itemOf("Plain") != null;
      } catch (Exception e) {
        return false;
      }
    });
    plainItem = itemOf("Plain");
    requalityNode = h.children(itemOf("TS103096")).get(0).getAsJsonObject().get("id").getAsString();
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

  /** Runs a command of the workbench on the explorer item (the handler of the explorer's selection). */
  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  /** A widget event of the client: the key of the data is set to the value (null: no data). */
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

  /** The explorer popup for the items: the top-level entries (label, enabled, index, children). */
  static JsonArray popupItems(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The children of the submenu with the label, or null. */
  static JsonArray submenu(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("children") && label.equals(o.get("label").getAsString()))
        return o.getAsJsonArray("children");
    }
    return null;
  }

  /** The labels of all the entries of the menu, the submenus included. */
  static List<String> allLabels(JsonArray items) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label"))
        r.add(o.get("label").getAsString());
      if (o.has("children"))
        r.addAll(allLabels(o.getAsJsonArray("children")));
    }
    return r;
  }

  static int indexOf(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("index") && label.equals(o.get("label").getAsString()))
        return o.get("index").getAsInt();
    }
    return -1;
  }

  /** The widget snapshot of the shell. */
  static JsonObject snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject();
  }

  /** The texts of the tree items of a shell. */
  static List<String> treeItemTexts(String shell) throws Exception {
    List<String> r = new ArrayList<>();
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props")) {
        JsonObject props = o.getAsJsonObject("props");
        if (props.has("texts") && props.getAsJsonArray("texts").size() > 0)
          r.add(props.getAsJsonArray("texts").get(0).getAsString());
      }
    }
    return r;
  }

  @Test
  @Order(1)
  void theProjectPopupHasTheNewSubmenu() throws Exception {
    JsonArray items = popupItems(plainItem);
    JsonArray children = submenu(items, "New");
    assertNotNull(children, items.toString());
    List<String> labels = allLabels(children);
    for (String l : new String[] { "Project...", "Folder", "File", "Other..." })
      assertTrue(labels.contains(l), l + " in " + labels);
    // No examples category: no Example... item.
    assertFalse(labels.contains("Example..."), labels.toString());
    assertEquals("Project...", labels.get(0), labels.toString());
    // Folder and File are enabled for a project; Other... is always enabled.
    for (JsonElement e : children) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && !o.has("children") && !o.has("separator"))
        assertTrue(o.get("enabled").getAsBoolean(), o.toString());
    }
  }

  @Test
  @Order(2)
  void theRequalityNodePopupHasNoNewWizardShortcuts() throws Exception {
    // The Requality nodes get the New menu of the Requality plug-in (NewMenuPoint), not the resource shortcuts.
    List<String> labels = allLabels(popupItems(requalityNode));
    assertFalse(labels.contains("Other..."), labels.toString());
    assertFalse(labels.contains("Folder"), labels.toString());
    assertFalse(labels.contains("Project..."), labels.toString());
  }

  @Test
  @Order(3)
  void otherOpensTheNewWizardSelectionWithTheCategories() throws Exception {
    JsonArray items = popupItems(plainItem);
    int index = indexOf(submenu(items, "New"), "Other...");
    assertTrue(index >= 0, items.toString());
    int before = h.notifications("ui/openShell").size();
    JsonObject run = new JsonObject();
    run.addProperty("menu", POPUP);
    run.addProperty("index", index);
    // Other... is a menu index of the last menu/items: the items of the submenu are kept by the menu service.
    CompletableFuture<JsonElement> f = h.callAsync("menu/run", run);
    JsonObject shell = h.awaitShell(before);
    assertEquals("New", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    h.await("the categories", v -> {
      try {
        List<String> texts = treeItemTexts(sid);
        return texts.contains("General") && texts.contains("Requality");
      } catch (Exception e) {
        return false;
      }
    });
    h.closeShell(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  /** The index of the menu entry with the label in the New submenu of the popup of the item, after a popup listing. */
  static int newIndex(String item, String label) throws Exception {
    JsonArray items = popupItems(item);
    int index = indexOf(submenu(items, "New"), label);
    assertTrue(index >= 0, label + " in " + items);
    return index;
  }

  /** Runs a menu entry of the explorer popup (blocks until its dialog closes): the future of the call. */
  static CompletableFuture<JsonElement> runMenu(int index) {
    JsonObject run = new JsonObject();
    run.addProperty("menu", POPUP);
    run.addProperty("index", index);
    return h.callAsync("menu/run", run);
  }

  /** The id of the widget of the type with the text (a label or a button; the & of the mnemonic is ignored). */
  static String widgetWithText(String shell, String type, String text) throws Exception {
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null) || !o.has("props"))
        continue;
      JsonObject props = o.getAsJsonObject("props");
      if (props.has("text") && !props.get("text").isJsonNull() && text.equals(props.get("text").getAsString()))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The text field that is a child of the same composite as the label with the text (the field after a label). */
  static String textAfterLabel(String shell, String label) throws Exception {
    JsonObject snap = snapshot(shell);
    String parent = null;
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("label".equals(o.get("type").getAsString()) && o.has("props") && o.getAsJsonObject("props").has("text")
          && label.equals(o.getAsJsonObject("props").get("text").getAsString()))
        parent = o.get("parent").isJsonNull() ? null : o.get("parent").getAsString();
    }
    assertNotNull(parent, label + " in " + snap);
    String found = null;
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("text".equals(o.get("type").getAsString()) && parent.equals(o.get("parent").getAsString()))
        found = o.get("id").getAsString();
    }
    assertNotNull(found, label + " field in " + snap);
    return found;
  }

  /** Presses a button of the shell (a selection event on the button). */
  static void press(String button) {
    JsonObject ev = new JsonObject();
    ev.addProperty("id", button);
    ev.addProperty("type", "selection");
    ev.add("data", new JsonObject());
    h.notify("ui/event", ev);
  }

  /** Waits for the notification of the file opened in the client, and returns it. */
  static JsonObject openedFile(int before) throws Exception {
    h.await("the file opened", v -> h.notifications("client/openFile").size() > before);
    return h.notifications("client/openFile").get(before);
  }

  @Test
  @Order(4)
  void newFolderIsCreatedAndUndone() throws Exception {
    File docs = new File(new File(h.workspace, "Plain"), "Docs");
    int index = newIndex(plainItem, "Folder");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runMenu(index);
    JsonObject shell = h.awaitShell(before);
    assertEquals("New Folder", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    String name = textAfterLabel(sid, "Folder name:");
    event(name, "modify", "text", "Docs");
    h.await("the folder name is valid", v -> {
      try {
        return widgetWithText(sid, "button", "Finish") != null && isEnabled(sid, "Finish");
      } catch (Exception e) {
        return false;
      }
    });
    press(widgetWithText(sid, "button", "Finish"));
    f.get(120, TimeUnit.SECONDS);
    h.await("the folder on disk", v -> docs.isDirectory());
    assertTrue(new File(docs.getParentFile(), "Plain").isDirectory() || docs.isDirectory());
    // Edit > Undo removes the folder again (the CreateFolderOperation of the workbench history).
    execute("org.eclipse.ui.edit.undo", plainItem).get(60, TimeUnit.SECONDS);
    h.await("the folder removed by the undo", v -> !docs.exists());
  }

  /** Whether the button with the text is enabled in the snapshot. */
  static boolean isEnabled(String shell, String text) throws Exception {
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("button".equals(o.get("type").getAsString()) && o.has("props")
          && text.equals(o.getAsJsonObject("props").get("text").getAsString()))
        return !o.getAsJsonObject("props").has("enabled") || o.getAsJsonObject("props").get("enabled").getAsBoolean();
    }
    return false;
  }

  @Test
  @Order(5)
  void newFileIsCreatedAndOpened() throws Exception {
    File notes = new File(new File(h.workspace, "Plain"), "notes.txt");
    int index = newIndex(plainItem, "File");
    int opened = h.notifications("client/openFile").size();
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runMenu(index);
    JsonObject shell = h.awaitShell(before);
    assertEquals("Create New File", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    event(textAfterLabel(sid, "File name:"), "modify", "text", "notes.txt");
    h.await("the file name is valid", v -> {
      try {
        return isEnabled(sid, "Finish");
      } catch (Exception e) {
        return false;
      }
    });
    press(widgetWithText(sid, "button", "Finish"));
    f.get(120, TimeUnit.SECONDS);
    h.await("the file on disk", v -> notes.isFile());
    JsonObject open = openedFile(opened);
    assertEquals(notes.getCanonicalPath(), new File(open.get("path").getAsString()).getCanonicalPath());
    assertEquals("default", open.get("editor").getAsString());
  }

  @Test
  @Order(6)
  void newProjectIsCreatedFromTheProjectWizard() throws Exception {
    int index = newIndex(plainItem, "Project...");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = runMenu(index);
    JsonObject shell = h.awaitShell(before);
    assertEquals("New Project", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    // The General category (the Project wizard) and the Requality category are shown; only projects are listed.
    String tree = null;
    for (JsonElement e : snapshot(sid).getAsJsonArray("ops"))
      if ("tree".equals(e.getAsJsonObject().get("type").getAsString()))
        tree = e.getAsJsonObject().get("id").getAsString();
    assertNotNull(tree);
    String project = null;
    for (JsonElement e : snapshot(sid).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.get("type").getAsString()) && o.getAsJsonObject("props").getAsJsonArray("texts").get(0)
          .getAsString().equals("Project"))
        project = o.get("id").getAsString();
    }
    assertNotNull(project, "the Project wizard in the tree");
    assertTrue(treeItemTexts(sid).contains("Requality Project"), treeItemTexts(sid).toString());
    assertFalse(treeItemTexts(sid).contains("File"), treeItemTexts(sid).toString());
    event(tree, "selection", "selection", java.util.List.of(project));
    h.await("the Next button", v -> {
      try {
        return widgetWithText(sid, "button", "Next >") != null && isEnabled(sid, "Next >");
      } catch (Exception e) {
        return false;
      }
    });
    press(widgetWithText(sid, "button", "Next >"));
    h.await("the project name", v -> {
      try {
        return textAfterLabel(sid, "Project name:") != null;
      } catch (Exception e) {
        return false;
      }
    });
    event(textAfterLabel(sid, "Project name:"), "modify", "text", "NewProj");
    try {
      h.await("the project name is valid", v -> {
        try {
          return isEnabled(sid, "Next >");
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + snapshot(sid), e);
    }
    // The workspace has projects: the reference page follows.
    press(widgetWithText(sid, "button", "Next >"));
    h.await("the reference page", v -> {
      try {
        return treeOrTableHas(sid, "Project References");
      } catch (Exception e) {
        return false;
      }
    });
    h.await("the finish button", v -> {
      try {
        return isEnabled(sid, "Finish");
      } catch (Exception e) {
        return false;
      }
    });
    press(widgetWithText(sid, "button", "Finish"));
    f.get(120, TimeUnit.SECONDS);
    File created = new File(h.workspace, "NewProj");
    h.await("the project on disk", v -> new File(created, ".project").isFile());
    // A plain Eclipse project is listed by the explorer (the workspace/projects list has the Requality projects only).
    h.await("the project listed", v -> {
      try {
        return itemOf("NewProj") != null;
      } catch (Exception e) {
        return false;
      }
    });
    assertTrue(Files.readString(new File(created, ".project").toPath()).contains("<name>NewProj</name>"));
  }

  /** Whether the shell shows a widget whose text (or a label of a page) matches. */
  static boolean treeOrTableHas(String shell, String text) throws Exception {
    return snapshot(shell).toString().contains("\"text\":\"" + text + "\"");
  }
}
