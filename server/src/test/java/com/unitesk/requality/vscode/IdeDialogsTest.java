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
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * The dialogs of org.eclipse.ui.ide ported to the compat layer: the overwrite question of the File System import (Yes To
 * All asked once), the read-only question of the delete of a file, the working set group of the New Project wizard, and
 * the sort indicator of the proposals tree of the smart import.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdeDialogsTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static final String FILE_IMPORT = "org.eclipse.ui.file.import";
  static final String DELETE = "org.eclipse.ui.edit.delete";
  static final String OPEN_DIRECTORY = "org.eclipse.e4.ui.importer.openDirectory";
  static final String IMPORT_TITLE = "Import Projects from File System or Archive";
  static final String WORKING_SET_PAGE = "org.eclipse.ui.resourceWorkingSetPage";
  static ServerHarness h;
  static File ow;
  static File owSource;
  static File ro;
  static File sortSource;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    ow = project("ow");
    Files.writeString(new File(ow, "top.txt").toPath(), "old top");
    Files.writeString(new File(ow, "docs/readme.txt").toPath(), "old readme");
    ro = project("ro");
    Files.writeString(new File(ro, "ro.txt").toPath(), "keep me");
    owSource = new File(h.root, "ow-src");
    new File(owSource, "docs").mkdirs();
    Files.writeString(new File(owSource, "top.txt").toPath(), "new top");
    Files.writeString(new File(owSource, "docs/readme.txt").toPath(), "new readme");
    sortSource = new File(h.root, "sort-src");
    new File(sortSource, "one/src").mkdirs();
    new File(sortSource, "two/src").mkdirs();
    Files.writeString(new File(sortSource, "one/.project").toPath(), projectFile("one"));
    Files.writeString(new File(sortSource, "two/.project").toPath(), projectFile("two"));
    h.initialize(h.workspace);
    // the catalog loaded (converted from its older format): its conversion holds the workspace, which an import waits
    // for, as in Eclipse
    h.awaitProjects(1);
    h.await("the projects in the explorer", v -> {
      try {
        return itemOf("ow") != null && itemOf("ro") != null;
      } catch (Exception e) {
        return false;
      }
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** A project folder of the workspace with a .project file (a plain Eclipse project). */
  static File project(String name) throws Exception {
    File dir = new File(h.workspace, name);
    new File(dir, "docs").mkdirs();
    Files.writeString(new File(dir, ".project").toPath(), projectFile(name));
    return dir;
  }

  static String projectFile(String name) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription><name>" + name
        + "</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n";
  }

  /** The explorer item whose label starts with the name, or null. */
  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The explorer item of the file directly in the project, or null. */
  static String fileItem(String project, String file) throws Exception {
    for (JsonElement e : h.children(itemOf(project)))
      if (file.equals(e.getAsJsonObject().get("label").getAsString()))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    if (item != null)
      nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  /** The created widget of a shell with the type and text (first match), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      if (text == null)
        return o.get("id").getAsString();
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      List<String> texts = new ArrayList<>();
      if (props.has("text") && !props.get("text").isJsonNull())
        texts.add(props.get("text").getAsString());
      if (props.has("texts"))
        for (JsonElement t : props.getAsJsonArray("texts"))
          if (!t.isJsonNull())
            texts.add(t.getAsString());
      for (String t : texts)
        if (text.equals(t) || text.equals(t.replace("&", "")))
          return o.get("id").getAsString();
    }
    return null;
  }

  /** The last created widget of the type in the shell (the widgets of the current page), or null. */
  static String lastWidget(String shell, String type) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    String last = null;
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (type.equals(o.has("type") ? o.get("type").getAsString() : null))
        last = o.get("id").getAsString();
    }
    return last;
  }

  static void awaitWidget(String shell, String type, String text) throws Exception {
    h.await("the " + type + " " + text, v -> {
      try {
        return widget(shell, type, text) != null;
      } catch (Exception e) {
        return false;
      }
    });
  }

  static void event(String id, String type, String key, Object value) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    JsonObject d = new JsonObject();
    if (key != null)
      d.add(key, JsonRpcConnection.toJson(value));
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /**
   * Presses a button of the shell by its text, once it is enabled: a running operation of the wizard (WizardDialog.run,
   * for example the selection of the files after Browse...) disables the buttons until it ends, as in Eclipse, and a
   * disabled button ignores the press.
   */
  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button of " + shell);
    h.awaitEnabled(shell, b);
    event(b, "selection", null, null);
  }

  /** Checks a row of a checkbox viewer (tree or table) of the shell. */
  static void checkRow(String viewer, String row, boolean checked) {
    JsonObject d = new JsonObject();
    d.addProperty("item", row);
    d.addProperty("checked", checked);
    JsonObject p = new JsonObject();
    p.addProperty("id", viewer);
    p.addProperty("type", "check");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Selects one item of a tree widget. */
  static void selectItem(String tree, String item) {
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** The wizard or dialog shell with the title, opened after the given count of opened shells. */
  static JsonObject awaitShell(int before, String title) throws Exception {
    JsonObject[] found = new JsonObject[1];
    // an operation of the workspace waits for the jobs that hold it (the load and the build of the catalog)
    try {
      h.await("the " + title + " dialog", 180_000, v -> {
        List<JsonObject> l = h.notifications("ui/openShell");
        for (int k = before; k < l.size(); k++)
          if (title.equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null)) {
            found[0] = l.get(k);
            return true;
          }
        return false;
      });
    } catch (AssertionError e) {
      List<JsonObject> l = h.notifications("ui/openShell");
      List<String> titles = new ArrayList<>();
      for (int k = before; k < l.size(); k++)
        titles.add(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null);
      throw new AssertionError(e.getMessage() + "; the shells opened: " + titles
          + "; the messages: " + h.requests("client/showMessage"), e);
    }
    return found[0];
  }

  /** How many shells with the title were opened after the given count of opened shells. */
  static int shellsTitled(int before, String title) {
    int n = 0;
    List<JsonObject> l = h.notifications("ui/openShell");
    for (int k = before; k < l.size(); k++)
      if (title.equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null))
        n++;
    return n;
  }

  static boolean enabled(String shell, String text) {
    try {
      return isEnabled(shell, text);
    } catch (Exception e) {
      return false;
    }
  }

  /** The text field after the "Project name:" label of the new project page, or null. */
  static String nameField(String shell) throws Exception {
    boolean after = false;
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (after && "text".equals(o.get("type").getAsString()))
        return o.get("id").getAsString();
      if ("label".equals(o.get("type").getAsString()) && props.has("text")
          && "Project name:".equals(props.get("text").getAsString()))
        after = true;
    }
    return null;
  }

  /** Whether the button of the shell with the text is enabled. */
  static boolean isEnabled(String shell, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("button".equals(o.get("type").getAsString()) && o.has("props")
          && o.getAsJsonObject("props").has("text")
          && text.equals(o.getAsJsonObject("props").get("text").getAsString().replace("&", "")))
        return !o.getAsJsonObject("props").has("enabled") || o.getAsJsonObject("props").get("enabled").getAsBoolean();
    }
    return false;
  }

  static String text(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  static String read(File f) throws Exception {
    return Files.readString(f.toPath());
  }

  /** The properties of the proposals tree (sort column and direction), from its widget snapshot. */
  static JsonObject sortProps(String shell) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("props") && o.getAsJsonObject("props").has("sortDirection"))
        return o.getAsJsonObject("props");
    }
    return new JsonObject();
  }

  static String propOf(JsonObject props, String key) {
    return props.has(key) && !props.get(key).isJsonNull() ? props.get(key).getAsString() : null;
  }

  @Test
  @Order(1)
  void overwriteQuestionOffersYesToAllAndIsAskedOnce() throws Exception {
    String owItem = itemOf("ow");
    assertNotNull(owItem);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute(FILE_IMPORT, owItem);
    JsonObject shell = awaitShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "File System"));
    press(sid, "Next >");
    awaitWidget(sid, "combo", null);
    h.answer("client/directoryDialog", p -> owSource.getAbsolutePath());
    press(sid, "Browse...");
    awaitWidget(sid, "row", "top.txt");
    event(lastWidget(sid, "text"), "modify", "text", "/ow");
    press(sid, "Finish");

    // the first existing file is asked with the five buttons of the question
    JsonObject question = awaitShell(before, "Question");
    String qid = question.get("shell").getAsString();
    for (String b : new String[] { "Yes", "Yes to All", "No", "No to All", "Cancel" })
      assertNotNull(widget(qid, "button", b), b + " button of the question");
    assertTrue(text(qid).contains("Overwrite '"), "the overwrite question " + text(qid));
    press(qid, "Yes to All");
    f.get(60, TimeUnit.SECONDS);

    assertEquals(1, shellsTitled(before, "Question"), "Yes to All answers the other existing files");
    assertEquals("new top", read(new File(ow, "top.txt")));
    assertEquals("new readme", read(new File(ow, "docs/readme.txt")));
  }

  @Test
  @Order(2)
  void readOnlyFileDeletionShowsAFatalErrorAndDeletesWhenWritable() throws Exception {
    File file = new File(ro, "ro.txt");
    assertTrue(file.setWritable(false), "the file is made read-only");
    String item = fileItem("ro", "ro.txt");
    assertNotNull(item);

    // the Delete Resources wizard finds the read-only file: the status page "Found problems" has a fatal error only
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute(DELETE, item);
    JsonObject wizard = awaitShell(before, "Delete Resources");
    h.pressDefaultButton(wizard.get("shell").getAsString());
    JsonObject problems = awaitShell(before, "Found problems");
    String pid = problems.get("shell").getAsString();
    assertTrue(text(pid).contains("Cannot delete content for read-only resource 'ro/ro.txt'."), text(pid));
    press(pid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
    assertTrue(file.exists(), "a read-only file is kept");

    // the file is writable again: the wizard deletes it without problems
    assertTrue(file.setWritable(true));
    before = h.notifications("ui/openShell").size();
    f = execute(DELETE, item);
    wizard = awaitShell(before, "Delete Resources");
    h.pressDefaultButton(wizard.get("shell").getAsString());
    f.get(60, TimeUnit.SECONDS);
    h.await("the file deleted", v -> !file.exists());
  }

  @Test
  @Order(3)
  void newProjectWizardAddsTheProjectToTheChosenWorkingSet() throws Exception {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    IWorkingSet chosen = manager.createWorkingSet("Chosen", new IAdaptable[0]);
    chosen.setId(WORKING_SET_PAGE);
    manager.addWorkingSet(chosen);

    JsonArray items = popupItems(itemOf("ow"));
    int index = indexOf(submenu(items, "New"), "Project...");
    assertTrue(index >= 0, "Project... in the New submenu");
    int before = h.notifications("ui/openShell").size();
    JsonObject run = new JsonObject();
    run.addProperty("menu", POPUP);
    run.addProperty("index", index);
    CompletableFuture<JsonElement> f = h.callAsync("menu/run", run);
    JsonObject shell = awaitShell(before, "New Project");
    String sid = shell.get("shell").getAsString();

    // the Project wizard of the General category
    awaitWidget(sid, "tree", null);
    awaitWidget(sid, "treeitem", "Project");
    event(widget(sid, "tree", null), "selection", "selection", List.of(widget(sid, "treeitem", "Project")));
    try {
      h.await("the Next button enabled", v -> enabled(sid, "Next >"));
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + " " + text(sid), e);
    }
    press(sid, "Next >");

    // the working set group of the project page
    h.await("the working set group", v -> {
      try {
        return text(sid).contains("Working sets");
      } catch (Exception e) {
        return false;
      }
    });
    String enable = widget(sid, "button", "Add project to working sets");
    assertNotNull(enable, "the working set check box");
    event(enable, "selection", "selection", true);

    // the working set is chosen in the selection dialog
    int dialogBefore = h.notifications("ui/openShell").size();
    press(sid, "Select...");
    JsonObject dialog = awaitShell(dialogBefore, "Select Working Sets");
    String dialogId = dialog.get("shell").getAsString();
    h.await("the Chosen row", v -> {
      try {
        return widget(dialogId, "row", "Chosen") != null;
      } catch (Exception e) {
        return false;
      }
    });
    checkRow(widget(dialogId, "table", null), widget(dialogId, "row", "Chosen"), true);
    press(dialogId, "OK");

    // the name of the project, then the reference page (the workspace has projects), then Finish
    String nameField = h.await2("the project name field", () -> nameField(sid));
    event(nameField, "modify", "text", "WsProj");
    try {
      h.await("the Next button enabled", v -> enabled(sid, "Next >"));
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + " " + text(sid), e);
    }
    press(sid, "Next >");
    h.await("the Finish button enabled", v -> enabled(sid, "Finish"));
    press(sid, "Finish");
    f.get(120, TimeUnit.SECONDS);
    assertTrue(new File(h.workspace, "WsProj/.project").isFile(), "the project is created");
    h.await("the project in the chosen working set", v -> {
      for (IAdaptable e : chosen.getElements())
        if (e instanceof IResource r && "WsProj".equals(r.getName()))
          return true;
      return false;
    });
  }

  @Test
  @Order(4)
  void smartImportSortIndicatorFollowsTheHeaderClicks() throws Exception {
    int before = h.notifications("ui/openShell").size();
    execute(OPEN_DIRECTORY, null);
    JsonObject shell = awaitShell(before, IMPORT_TITLE);
    String sid = shell.get("shell").getAsString();
    h.answer("client/directoryDialog", p -> sortSource.getAbsolutePath());
    press(sid, "Directory...");
    h.await("the proposals of sort-src", v -> {
      try {
        return text(sid).contains("\"" + ServerHarness.jsonText("sort-src" + java.io.File.separator));
      } catch (Exception e) {
        return false;
      }
    });

    String folder = widget(sid, "column", "Folder");
    String importAs = widget(sid, "column", "Import as");
    assertNotNull(folder);
    assertNotNull(importAs);
    // the folder column is sorted ascending at first
    assertEquals(folder, propOf(sortProps(sid), "sortColumn"));
    assertEquals("up", propOf(sortProps(sid), "sortDirection"));

    event(importAs, "selection", null, null);
    h.await("the Import as column sorted", v -> {
      try {
        return importAs.equals(propOf(sortProps(sid), "sortColumn"));
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals("up", propOf(sortProps(sid), "sortDirection"));

    // a second click on the same column reverses the order
    event(importAs, "selection", null, null);
    h.await("the reversed order", v -> {
      try {
        return "down".equals(propOf(sortProps(sid), "sortDirection"));
      } catch (Exception e) {
        return false;
      }
    });

    event(folder, "selection", null, null);
    h.await("the folder column sorted", v -> {
      try {
        return folder.equals(propOf(sortProps(sid), "sortColumn"));
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals("up", propOf(sortProps(sid), "sortDirection"));
    assertFalse("down".equals(propOf(sortProps(sid), "sortDirection")));
    h.closeShell(sid);
  }

  /** The menu entries of the popup of the explorer item. */
  static JsonArray popupItems(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
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

  static int indexOf(JsonArray items, String label) {
    if (items == null)
      return -1;
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("index") && label.equals(o.get("label").getAsString()))
        return o.get("index").getAsInt();
    }
    return -1;
  }
}
