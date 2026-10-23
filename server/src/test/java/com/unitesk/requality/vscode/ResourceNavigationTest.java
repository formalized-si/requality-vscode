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

/**
 * The Eclipse resource navigation of the Requality Explorer (org.eclipse.ui.navigator.resources action providers:
 * Open, Open With, Show In, Go to Resource, Import as Project) on a plain Eclipse project in the workspace with a
 * nested Eclipse project, as ProjectOperationsTest does for the Requality catalogs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceNavigationTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static ServerHarness h;
  static File plain;
  static File file;
  static File sub;
  static String projectItem;
  static String fileItem;
  static String notesItem;
  static String subItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "plain");
    sub = new File(plain, "sub");
    file = new File(plain, "a.txt");
    File notes = new File(plain, "notes.md");
    sub.mkdirs();
    project(plain, "plain");
    project(sub, "sub");
    Files.writeString(file.toPath(), "alpha");
    Files.writeString(notes.toPath(), "# notes");
    Files.writeString(new File(sub, "readme.txt").toPath(), "readme");
    h.initialize(h.workspace);
    // The other Eclipse projects (not catalogs) are listed by the explorer, not by workspace/projects.
    h.await("the plain project", v -> {
      try {
        return itemOf("plain") != null;
      } catch (Exception e) {
        return false;
      }
    });
    // The nested folder is taken out of the workspace (Remove from Workspace): a folder with a .project, not a project.
    h.server().workspace().removeProject(sub, false);
    projectItem = itemOf("plain");
    fileItem = childItem(projectItem, "a.txt");
    notesItem = childItem(projectItem, "notes.md");
    subItem = childItem(projectItem, "sub");
    assertNotNull(projectItem);
    assertNotNull(fileItem);
    assertNotNull(subItem);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** A plain Eclipse project: a folder with a .project file. */
  static void project(File dir, String name) throws Exception {
    Files.writeString(new File(dir, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>" + name + "</name><comment></comment><projects></projects>"
        + "<buildSpec></buildSpec><natures></natures></projectDescription>\n");
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static String childItem(String parent, String label) throws Exception {
    for (JsonElement e : h.children(parent))
      if (e.getAsJsonObject().get("label").getAsString().equals(label))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static JsonObject project(String name) throws Exception {
    for (JsonElement e : h.call("workspace/projects", null).getAsJsonObject().getAsJsonArray("projects"))
      if (name.equals(e.getAsJsonObject().get("name").getAsString()))
        return e.getAsJsonObject();
    return null;
  }

  /** The explorer popup of the items: the menu items as the server describes them ({@code menu/items}). */
  static JsonArray popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The item with the label (searching the submenus), or null. */
  static JsonObject item(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()))
        return o;
      if (o.has("children")) {
        JsonObject found = item(o.getAsJsonArray("children"), label);
        if (found != null)
          return found;
      }
    }
    return null;
  }

  static List<String> labels(JsonArray items) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : items)
      if (e.getAsJsonObject().has("label"))
        r.add(e.getAsJsonObject().get("label").getAsString());
    return r;
  }

  /** Runs a menu item of the popup (menu/run with the index of the item). */
  static void run(JsonObject item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    h.call("menu/run", p);
  }

  static void select(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  /** The file names of the notifications with the editor, the canonical path of the file in them. */
  static boolean openedWith(String editor) {
    try {
      for (JsonObject n : h.notifications("client/openFile"))
        if (editor.equals(n.get("editor").getAsString())
            && file.getCanonicalPath().equals(new File(n.get("path").getAsString()).getCanonicalPath()))
          return true;
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    return false;
  }

  /** Opens the items as Enter does in the explorer (explorer/open). */
  static void open(String item) throws Exception {
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    JsonObject p = new JsonObject();
    p.add("nodes", nodes);
    h.call("explorer/open", p);
  }

  static int openFiles() {
    return h.notifications("client/openFile").size();
  }

  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      String t = props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
      if (text == null || text.equals(t))
        return o.get("id").getAsString();
    }
    return null;
  }

  static void modify(String id, String text) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", "modify");
    JsonObject d = new JsonObject();
    d.addProperty("text", text);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  @Test
  @Order(1)
  void fileAndFolderPopupsOfferTheNavigationActions() throws Exception {
    JsonArray file = popup(fileItem);
    assertNotNull(item(file, "Open"), file.toString());
    JsonObject openWith = item(file, "Open With");
    assertNotNull(openWith, file.toString());
    assertEquals(List.of("Text Editor", "System Editor"),
        List.of(labels(openWith.getAsJsonArray("children")).get(0), labels(openWith.getAsJsonArray("children")).get(1)),
        openWith.toString());
    assertTrue(labels(openWith.getAsJsonArray("children")).contains("Other..."), openWith.toString());
    assertTrue(labels(openWith.getAsJsonArray("children")).contains("Default Editor"), openWith.toString());
    JsonObject showIn = item(file, "Show In");
    assertNotNull(showIn, file.toString());
    assertEquals(List.of("Project Explorer", "Properties", "System Explorer", "Terminal"),
        labels(showIn.getAsJsonArray("children")), showIn.toString());
    assertFalse(labels(file).contains("Import as Project"), file.toString());

    // A folder with a .project that is not a project of the workspace: imported; the projects are not.
    JsonArray folder = popup(subItem);
    assertNotNull(item(folder, "Import as Project"), folder.toString());
    assertNotNull(item(folder, "Show In"), folder.toString());
    assertNull(item(popup(projectItem), "Import as Project"));
    assertNull(item(popup(projectItem), "Open"));
  }

  @Test
  @Order(2)
  void openWithRemembersTheEditorOfTheFile() throws Exception {
    JsonArray before = popup(fileItem);
    // Open With > System Editor: the system application opens the file, and the file remembers it.
    JsonObject system = item(item(before, "Open With").getAsJsonArray("children"), "System Editor");
    assertNotNull(system, before.toString());
    int n = openFiles();
    run(system);
    h.await("the file opened in the system editor", v -> openedWith("system"));
    assertTrue(openFiles() > n);
    final int afterSystem = openFiles();
    // Open (Enter on the file) opens it in the remembered editor.
    n = openFiles();
    open(fileItem);
    h.await("Open uses the remembered editor", v -> openFiles() > afterSystem);
    assertEquals("system", h.notifications("client/openFile").get(openFiles() - 1).get("editor").getAsString());

    // Open With > Text Editor: the VS Code text editor, and the next Open uses it.
    JsonObject text = item(popup(fileItem), "Text Editor");
    assertNotNull(text);
    n = openFiles();
    final int beforeText = n;
    run(text);
    h.await("the file opened in the text editor", v -> openFiles() > beforeText);
    assertEquals("default", h.notifications("client/openFile").get(openFiles() - 1).get("editor").getAsString());
    final int beforeOpen = openFiles();
    open(fileItem);
    h.await("Open uses the text editor", v -> openFiles() > beforeOpen);
    assertEquals("default", h.notifications("client/openFile").get(openFiles() - 1).get("editor").getAsString());
  }

  @Test
  @Order(3)
  void showInSystemExplorerAndTerminal() throws Exception {
    select(fileItem);
    JsonObject system = item(item(popup(fileItem), "Show In").getAsJsonArray("children"), "System Explorer");
    final int n = h.notifications("client/showIn").size();
    run(system);
    h.await("client/showIn", v -> h.notifications("client/showIn").size() > n);
    JsonObject sent = h.notifications("client/showIn").get(n);
    assertEquals("system", sent.get("target").getAsString());
    assertEquals(file.getCanonicalPath(), new File(sent.get("path").getAsString()).getCanonicalPath());

    // The terminal opens in the folder of a file.
    JsonObject terminal = item(item(popup(fileItem), "Show In").getAsJsonArray("children"), "Terminal");
    final int m = h.notifications("client/showIn").size();
    run(terminal);
    h.await("client/showIn for the terminal", v -> h.notifications("client/showIn").size() > m);
    sent = h.notifications("client/showIn").get(m);
    assertEquals("terminal", sent.get("target").getAsString());
    assertEquals(plain.getCanonicalPath(), new File(sent.get("path").getAsString()).getCanonicalPath());
  }

  @Test
  @Order(4)
  void goToResourceSelectsTheResource() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = h.callAsync("command/execute", commandParams("org.eclipse.ui.navigate.goToResource"));
    JsonObject shell = h.awaitShell(before);
    assertEquals("Go To Resource", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    String pattern = widget(sid, "text", null);
    assertNotNull(pattern, h.call("ui/snapshot", "shell", sid).toString());
    modify(pattern, "notes");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("the resource revealed and selected", v -> h.notifications("explorer/reveal").stream()
        .anyMatch(p -> notesItem.equals(p.get("node").getAsString()) && p.get("select").getAsBoolean()));
  }

  static JsonObject commandParams(String command) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(projectItem);
    p.add("nodes", nodes);
    return p;
  }

  @Test
  @Order(5)
  void importAsProjectAddsTheNestedProject() throws Exception {
    JsonObject imp = item(popup(subItem), "Import as Project");
    assertNotNull(imp);
    run(imp);
    // The folder is now a project of the workspace: the explorer lists it as a project (the item is the project).
    h.await("the nested project in the workspace", v -> {
      try {
        String id = itemOf("sub");
        return id != null && id.equals("project:sub");
      } catch (Exception e) {
        return false;
      }
    });
    assertNull(item(popup("project:sub"), "Import as Project"));
  }
}
