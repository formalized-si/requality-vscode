package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/**
 * The Eclipse resource actions of projects in the Requality Explorer (org.eclipse.ui.navigator.resources action
 * providers: Copy, Paste, Delete, Move..., Refresh, Open/Close Project) and the IDE Project menu (Clean..., Build
 * Automatically) on the ETSI catalog, with the workspace metadata they keep.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectOperationsTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static ServerHarness h;
  static String projectItem;
  static File copyDir;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    projectItem = h.call("explorer/roots", null).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** Label → enabled of the explorer popup for the items. */
  static Map<String, Boolean> popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    Map<String, Boolean> r = new LinkedHashMap<>();
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray()) {
      JsonObject o = e.getAsJsonObject();
      // submenus (Show In, Open With) have children and no enablement
      if (o.has("label") && o.has("enabled"))
        r.put(o.get("label").getAsString(), o.get("enabled").getAsBoolean());
    }
    return r;
  }

  static JsonObject state(String command, String item) throws Exception {
    JsonObject p = new JsonObject();
    JsonArray cmds = new JsonArray();
    cmds.add(command);
    p.add("commands", cmds);
    select(item);
    return h.call("command/state", p).getAsJsonObject().getAsJsonObject(command);
  }

  static void select(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  /** The project list entry with the name, or null. */
  static JsonObject project(String name) throws Exception {
    for (JsonElement e : h.call("workspace/projects", null).getAsJsonObject().getAsJsonArray("projects"))
      if (name.equals(e.getAsJsonObject().get("name").getAsString()))
        return e.getAsJsonObject();
    return null;
  }

  static void awaitState(String name, String state) throws Exception {
    try {
      h.await(name + " " + state, v -> {
        try {
          JsonObject p = project(name);
          return state == null ? p == null : p != null && state.equals(p.get("state").getAsString());
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + h.call("workspace/projects", null), e);
    }
  }

  /** The created widgets of a shell: type and text → id (first match). */
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

  /** The workspace metadata file of the server (no VS Code workspace storage in the tests). */
  static JsonObject workspaceState() throws Exception {
    // the state file of the workspace (the other metadata files are named after it with a suffix)
    File[] fs = new File(h.storage, "workspaces").listFiles((d, n) -> n.matches("[0-9a-f]+\\.json"));
    assertNotNull(fs);
    assertEquals(1, fs.length);
    return com.google.gson.JsonParser.parseString(Files.readString(fs[0].toPath())).getAsJsonObject();
  }

  static boolean contains(JsonArray a, File f) throws Exception {
    for (JsonElement e : a)
      if (e.getAsString().equals(f.getCanonicalPath()))
        return true;
    return false;
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  @Test
  @Order(1)
  void theProjectPopupHasTheResourceActions() throws Exception {
    Map<String, Boolean> m = popup(projectItem);
    for (String l : new String[] { "Copy", "Paste", "Delete", "Move...", "Rename...", "Refresh", "Close Project",
        "Close Unrelated Projects" })
      assertTrue(m.containsKey(l), l + " in " + m);
    // Open Project only for closed projects; Build Project only when the workspace does not build automatically.
    assertFalse(m.containsKey("Open Project"), m.toString());
    assertFalse(m.containsKey("Build Project"), m.toString());
    assertTrue(m.get("Copy") && m.get("Delete") && m.get("Move...") && m.get("Close Project"), m.toString());
    // Nothing on the clipboard to paste.
    assertFalse(m.get("Paste"), m.toString());
  }

  @Test
  @Order(2)
  void editCommandsGoToTheProjectActionsOrToTheNodeHandlers() throws Exception {
    // A project: the global copy handler of the explorer (EditActionProvider's CopyAction).
    assertTrue(state("org.eclipse.ui.edit.copy", projectItem).get("enabled").getAsBoolean());
    assertTrue(state("org.eclipse.ui.edit.delete", projectItem).get("enabled").getAsBoolean());
    // A node: the Requality handlers of plugin.xml (CopyHandler, DeleteSelection), the resource actions are not there.
    String node = h.children(projectItem).get(0).getAsJsonObject().get("id").getAsString();
    JsonObject copyNode = state("org.eclipse.ui.edit.copy", node);
    assertTrue(copyNode.get("handled").getAsBoolean(), copyNode.toString());
    assertFalse(popup(node).containsKey("Close Project"));
    // Ctrl+C on the project: names on the clipboard (and the project for Paste).
    int before = h.notifications("client/clipboard").size();
    execute("org.eclipse.ui.edit.copy", projectItem).get(60, TimeUnit.SECONDS);
    h.await("clipboard", v -> h.notifications("client/clipboard").size() > before);
    assertEquals("TS103096-STF517", h.notifications("client/clipboard").get(before).get("text").getAsString());
    assertTrue(popup(projectItem).get("Paste"));
  }

  @Test
  @Order(3)
  void pasteCopiesTheProject() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.paste", projectItem);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Copy Project", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    JsonObject snap = h.call("ui/snapshot", "shell", sid).getAsJsonObject();
    assertTrue(snap.toString().contains("Copy of TS103096-STF517"), snap.toString());
    // The location of the copy is the default location of a new project named as the copy.
    assertTrue(snap.toString().contains(new File(h.workspace, "Copy of TS103096-STF517").getAbsolutePath().replace("\\", "\\\\")),
        snap.toString());
    assertFalse(snap.toString().contains("Location is the current location"), snap.toString());
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    copyDir = new File(h.workspace, "Copy of TS103096-STF517");
    h.await("the copy on disk", v -> new File(copyDir, ".project").isFile());
    assertTrue(Files.readString(new File(copyDir, ".project").toPath()).contains("<name>Copy of TS103096-STF517</name>"));
    awaitState("Copy of TS103096-STF517", "ready");
    awaitState("TS103096-STF517", "ready");
  }

  @Test
  @Order(4)
  void closeAndOpenAProject() throws Exception {
    String item = itemOf("Copy of TS103096-STF517");
    execute("org.eclipse.ui.project.closeProject", item).get(60, TimeUnit.SECONDS);
    awaitState("Copy of TS103096-STF517", "closed");
    h.await("the tree closed", v -> Nodes.findDB("Copy of TS103096-STF517") == null);
    JsonObject it = h.call("explorer/item", "id", item).getAsJsonObject();
    assertTrue(it.get("contextValue").getAsString().contains("closed"), it.toString());
    // WorkbenchProject: the closed project image (cprj_obj), the open one (prj_obj) after Open Project
    assertTrue(it.get("icon").getAsString().endsWith("cprj_obj.png") && new java.io.File(it.get("icon").getAsString()).isFile(),
        it.toString());
    assertEquals(0, h.children(item).size());
    assertFalse(ResourcesPlugin.getWorkspace().getRoot().getProject("Copy of TS103096-STF517").isOpen());
    Map<String, Boolean> m = popup(item);
    assertTrue(m.containsKey("Open Project") && !m.containsKey("Close Project"), m.toString());
    // Kept in the workspace metadata (Eclipse keeps closed projects closed across sessions).
    assertTrue(contains(workspaceState().getAsJsonArray("closed"), copyDir), workspaceState().toString());

    execute("org.eclipse.ui.project.openProject", item).get(60, TimeUnit.SECONDS);
    awaitState("Copy of TS103096-STF517", "ready");
    assertFalse(contains(workspaceState().getAsJsonArray("closed"), copyDir));
    assertTrue(h.children(item).size() > 0);
    String openIcon = h.call("explorer/item", "id", item).getAsJsonObject().get("icon").getAsString();
    assertTrue(openIcon.endsWith("project.png") && new java.io.File(openIcon).isFile(), openIcon);
  }

  @Test
  @Order(5)
  void moveAProjectToAnotherLocation() throws Exception {
    String item = itemOf("Copy of TS103096-STF517");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.move", item);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Move Project", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    File to = new File(h.root, "elsewhere/Copy of TS103096-STF517");
    String useDefault = widget(sid, "button", "Use default location");
    assertNotNull(useDefault, h.call("ui/snapshot", "shell", sid).toString());
    event(useDefault, "selection", "selection", false);
    String location = widget(sid, "text", null);
    event(location, "modify", "text", to.getAbsolutePath());
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    h.await("the project moved", v -> new File(to, ".project").isFile() && !copyDir.exists());
    awaitState("Copy of TS103096-STF517", "ready");
    assertEquals(to.getCanonicalPath(), new File(project("Copy of TS103096-STF517").get("path").getAsString()).getCanonicalPath());
    // A project outside the workspace folders is kept as an external location.
    assertTrue(contains(workspaceState().getAsJsonArray("external"), to), workspaceState().toString());
    copyDir = to;
  }

  @Test
  @Order(6)
  void deleteWithoutContentsRemovesTheProjectFromTheWorkspace() throws Exception {
    String item = itemOf("Copy of TS103096-STF517");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", item);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Delete Resources", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(h.call("ui/snapshot", "shell", sid).toString()
        .contains("Remove project 'Copy of TS103096-STF517' from the workspace?"));
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    awaitState("Copy of TS103096-STF517", null);
    assertTrue(new File(copyDir, ".project").isFile(), "the content stays on disk");
    h.call("workspace/rescan", null);
    assertNull(project("Copy of TS103096-STF517"), "a removed project is not discovered again");
    // File > Import > Existing Projects into Workspace brings it back.
    JsonObject find = new JsonObject();
    find.addProperty("root", copyDir.getParentFile().getAbsolutePath());
    JsonArray found = h.call("workspace/findProjects", find).getAsJsonArray();
    assertEquals(1, found.size(), found.toString());
    assertFalse(found.get(0).getAsJsonObject().get("inWorkspace").getAsBoolean());
    JsonObject imp = new JsonObject();
    JsonArray paths = new JsonArray();
    paths.add(copyDir.getAbsolutePath());
    imp.add("paths", paths);
    assertEquals(1, h.call("workspace/importProjects", imp).getAsJsonArray().size());
    awaitState("Copy of TS103096-STF517", "ready");
  }

  @Test
  @Order(7)
  void deleteWithContentsDeletesTheFolder() throws Exception {
    String item = itemOf("Copy of TS103096-STF517");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.delete", item);
    JsonObject shell = h.awaitPanelShell(before);
    String sid = shell.get("shell").getAsString();
    String check = widget(sid, "button", "Delete project contents on disk (cannot be undone)");
    assertNotNull(check, h.call("ui/snapshot", "shell", sid).toString());
    event(check, "selection", "selection", true);
    h.pressDefaultButton(sid);
    f.get(120, TimeUnit.SECONDS);
    awaitState("Copy of TS103096-STF517", null);
    h.await("the folder deleted", v -> !copyDir.exists());
    assertFalse(contains(workspaceState().getAsJsonArray("external"), copyDir));
  }

  @Test
  @Order(8)
  void cleanRebuildsTheProject() throws Exception {
    // The checkers builder in the build specification (as the Checkers menu or the catalog nature add it).
    com.unitesk.requality.vscode.ui.resources.IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("TS103096");
    com.unitesk.requality.vscode.ui.resources.IProjectDescription d = p.getDescription();
    com.unitesk.requality.vscode.ui.resources.ICommand c = d.newCommand();
    c.setBuilderName("com.unitesk.requality.checkers");
    d.setBuildSpec(new com.unitesk.requality.vscode.ui.resources.ICommand[] { c });
    p.setDescription(d, null);
    // EclipseChecker's full build of a loaded catalog changes its settings (fireDBSettingsChange): the workspace
    // reports it.
    int changes = h.notifications("workspace/projectsChanged").size();
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.project.cleanAction", projectItem);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Clean", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    assertTrue(h.call("ui/snapshot", "shell", sid).toString().contains("Clean projects selected below"));
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("the full build after the clean", v -> h.notifications("workspace/projectsChanged").size() > changes);
    // the build state is kept when the build job ends
    h.await("the project built", v -> {
      try {
        return contains(workspaceState().getAsJsonArray("built"), new File(h.workspace, "TS103096"));
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(9)
  void buildAutomaticallyCanBeSwitchedOff() throws Exception {
    assertFalse(state("org.eclipse.ui.project.buildAll", projectItem).get("enabled").getAsBoolean());
    execute("org.eclipse.ui.project.buildAutomatically", projectItem).get(60, TimeUnit.SECONDS);
    assertFalse(ResourcesPlugin.getWorkspace().isAutoBuilding());
    assertFalse(workspaceState().get("autoBuilding").getAsBoolean());
    List<JsonObject> ctx = new ArrayList<>(h.notifications("client/setContext"));
    JsonObject last = null;
    for (JsonObject c : ctx)
      if ("requality.autoBuilding".equals(c.get("key").getAsString()))
        last = c;
    assertNotNull(last);
    assertFalse(last.get("value").getAsBoolean());
    // Build Project (navigator) and Build All are offered now.
    assertTrue(popup(projectItem).containsKey("Build Project"), popup(projectItem).toString());
    assertTrue(state("org.eclipse.ui.project.buildAll", projectItem).get("enabled").getAsBoolean());
    execute("org.eclipse.ui.project.buildAutomatically", projectItem).get(60, TimeUnit.SECONDS);
    assertTrue(ResourcesPlugin.getWorkspace().isAutoBuilding());
  }

  static File other;

  static List<String> labels(JsonArray items) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : items)
      r.add(e.getAsJsonObject().get("label").getAsString());
    return r;
  }

  static String child(String parent, String label) throws Exception {
    for (JsonElement e : h.children(parent))
      if (label.equals(e.getAsJsonObject().get("label").getAsString()))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static void filters(boolean resourcesInCatalogs, boolean dotResources) throws Exception {
    JsonObject f = new JsonObject();
    f.addProperty("resourcesInRequalityProjects", resourcesInCatalogs);
    f.addProperty("startsWithDot", dotResources);
    h.call("explorer/setFilters", "filters", f);
  }

  @Test
  @Order(10)
  void otherProjectsAreListedWithTheirFiles() throws Exception {
    // NonReqContentProvider: an Eclipse project without the catalog nature is listed with its files.
    other = new File(h.workspace, "Other");
    new File(other, "sub").mkdirs();
    Files.writeString(new File(other, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Other</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(other, "a.txt").toPath(), "a");
    Files.writeString(new File(other, "sub/b.txt").toPath(), "b");
    h.call("workspace/rescan", null);
    String item = h.await2("the other project", () -> itemOf("Other"));
    JsonObject it = h.call("explorer/item", "id", item).getAsJsonObject();
    assertTrue(it.get("contextValue").getAsString().contains("nonRequality"), it.toString());
    // folders first; the ".* resources" filter hides .project
    assertEquals(List.of("sub", "a.txt"), labels(h.children(item)));
    filters(true, false);
    assertEquals(List.of("sub", ".project", "a.txt"), labels(h.children(item)));
    // a catalog shows its files too without the "Resource in Requality Project Filter"
    filters(false, true);
    List<String> catalog = labels(h.children(projectItem));
    assertTrue(catalog.contains("root.json") && catalog.contains("root"), catalog.toString());
    filters(true, true);
    assertFalse(labels(h.children(projectItem)).contains("root.json"));
    // the file popup: Open of OpenActionProvider is the extension's; the resource actions are the navigator's
    Map<String, Boolean> m = popup(child(item, "a.txt"));
    for (String l : new String[] { "Copy", "Paste", "Delete", "Move...", "Rename...", "Refresh" })
      assertTrue(m.containsKey(l), l + " in " + m);
  }

  @Test
  @Order(11)
  void filesAreCopiedPastedRenamedAndDeleted() throws Exception {
    String item = itemOf("Other");
    String a = child(item, "a.txt");
    String sub = child(item, "sub");
    // Copy, then Paste into the folder
    execute("org.eclipse.ui.edit.copy", a).get(60, TimeUnit.SECONDS);
    execute("org.eclipse.ui.edit.paste", sub).get(60, TimeUnit.SECONDS);
    h.await("pasted into sub", v -> new File(other, "sub/a.txt").isFile());
    // Paste into its own folder: "Name Conflict" asks for the name of the copy
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.edit.paste", a);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Name Conflict", shell.get("title").getAsString());
    assertTrue(h.call("ui/snapshot", "shell", shell.get("shell").getAsString()).toString().contains("Copy of a.txt"));
    h.pressDefaultButton(shell.get("shell").getAsString());
    f.get(60, TimeUnit.SECONDS);
    h.await("the copy", v -> new File(other, "Copy of a.txt").isFile());
    // Rename... (F2)
    before = h.notifications("ui/openShell").size();
    f = execute("org.eclipse.ui.edit.rename", child(item, "Copy of a.txt"));
    shell = h.awaitPanelShell(before);
    assertEquals("Rename Resource", shell.get("title").getAsString());
    String sid = shell.get("shell").getAsString();
    event(widget(sid, "text", null), "modify", "text", "c.txt");
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("renamed", v -> new File(other, "c.txt").isFile() && !new File(other, "Copy of a.txt").exists());
    // Delete
    before = h.notifications("ui/openShell").size();
    f = execute("org.eclipse.ui.edit.delete", child(item, "c.txt"));
    shell = h.awaitPanelShell(before);
    assertEquals("Delete Resources", shell.get("title").getAsString());
    assertTrue(h.call("ui/snapshot", "shell", shell.get("shell").getAsString()).toString()
        .contains("Delete 'c.txt' from the file system?"));
    h.pressDefaultButton(shell.get("shell").getAsString());
    f.get(60, TimeUnit.SECONDS);
    h.await("deleted", v -> !new File(other, "c.txt").exists());
    // Close Project of the other project: listed closed, without its files
    execute("org.eclipse.ui.project.closeProject", item).get(60, TimeUnit.SECONDS);
    JsonObject it = h.call("explorer/item", "id", item).getAsJsonObject();
    assertTrue(it.get("contextValue").getAsString().contains("closed"), it.toString());
    // WorkbenchProject: the closed project image (cprj_obj), the open one (prj_obj) after Open Project
    assertTrue(it.get("icon").getAsString().endsWith("cprj_obj.png") && new java.io.File(it.get("icon").getAsString()).isFile(),
        it.toString());
    assertEquals(0, h.children(item).size());
    execute("org.eclipse.ui.project.openProject", item).get(60, TimeUnit.SECONDS);
    assertEquals(List.of("sub", "a.txt"), labels(h.children(item)));
  }

  @Test
  @Order(12)
  void aFolderIsNeitherCopiedIntoItselfNorLostByAnOverwrite() throws Exception {
    String item = itemOf("Other");
    String sub = child(item, "sub");
    // Copy "sub": Paste into "sub" itself is disabled (PasteAction), and the operation refuses a subfolder.
    execute("org.eclipse.ui.edit.copy", sub).get(60, TimeUnit.SECONDS);
    assertFalse(state("org.eclipse.ui.edit.paste", sub).get("enabled").getAsBoolean());
    new File(other, "sub/inner").mkdirs();
    com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    com.unitesk.requality.vscode.ui.resources.IContainer subRes = root.getContainerForLocation(
        com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(new File(other, "sub").getAbsolutePath()));
    com.unitesk.requality.vscode.ui.resources.IContainer inner = root.getContainerForLocation(
        com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(new File(other, "sub/inner").getAbsolutePath()));
    assertEquals(0, new com.unitesk.requality.vscode.ui.workbench.actions.CopyFilesAndFoldersOperation(null)
        .copyResources(new com.unitesk.requality.vscode.ui.resources.IResource[] { subRes }, inner).length);
    assertTrue(h.requests("client/showMessage").stream()
        .anyMatch(m -> m.toString().contains("The destination folder is a subfolder of the source folder")));
    assertFalse(new File(other, "sub/inner/sub").exists());
    assertTrue(new File(other, "sub/b.txt").isFile());
    new File(other, "sub/inner").delete();
    // Overwrite of an existing folder merges: a folder "sub" elsewhere with its own file, overwritten by "sub".
    File other2 = new File(h.workspace, "Other2");
    new File(other2, "sub").mkdirs();
    Files.writeString(new File(other2, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Other2</name><natures></natures></projectDescription>\n");
    Files.writeString(new File(other2, "sub/only.txt").toPath(), "only");
    Files.writeString(new File(other2, "sub/b.txt").toPath(), "old");
    h.call("workspace/rescan", null);
    String item2 = h.await2("Other2", () -> itemOf("Other2"));
    execute("org.eclipse.ui.edit.paste", item2).get(60, TimeUnit.SECONDS);
    h.await("merged", v -> {
      try {
        return "b".equals(Files.readString(new File(other2, "sub/b.txt").toPath()));
      } catch (Exception e) {
        return false;
      }
    });
    assertTrue(new File(other2, "sub/only.txt").isFile(), "a file only in the destination stays");
    assertTrue(new File(other, "sub/b.txt").isFile(), "the source stays");
  }

  @Test
  @Order(13)
  void aProjectCannotBeCopiedIntoItself() throws Exception {
    com.unitesk.requality.vscode.ui.resources.IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("Other");
    com.unitesk.requality.vscode.ui.resources.IProjectDescription d = p.getDescription();
    d.setName("Inner");
    d.setLocationURI(new File(other, "inner/Inner").toURI());
    assertFalse(ResourcesPlugin.getWorkspace().validateProjectLocation(p,
        com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(new File(other, "inner/Inner").getAbsolutePath())).isOK());
    try {
      p.copy(d, 0, null);
      throw new AssertionError("copied into itself");
    } catch (com.unitesk.requality.vscode.ui.runtime.CoreException e) {
      assertTrue(e.getMessage().contains("inside the project"), e.getMessage());
    }
    assertFalse(new File(other, "inner").exists());
  }

  @Test
  @Order(14)
  void handlersActivatedByAPartSiteApplyOnlyWhileThePartIsActive() throws Exception {
    // JSEditor activates copy/paste handlers through its site: they must not handle the explorer's Ctrl+C.
    boolean[] ran = { false };
    com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart part = new com.unitesk.requality.vscode.ui.workbench.WorkbenchPart() {
      @Override
      public void createPartControl(com.unitesk.requality.vscode.ui.swt.widgets.Composite parent) {
      }

      @Override
      public void setFocus() {
      }
    };
    com.unitesk.requality.vscode.ui.workbench.PartSite site = new com.unitesk.requality.vscode.ui.workbench.PartSite("test.part", part);
    site.getService(com.unitesk.requality.vscode.ui.commands.IHandlerService.class).activateHandler("org.eclipse.ui.edit.copy",
        new com.unitesk.requality.vscode.ui.commands.AbstractHandler() {
          @Override
          public Object execute(com.unitesk.requality.vscode.ui.commands.ExecutionEvent event) {
            ran[0] = true;
            return null;
          }
        });
    int before = h.notifications("client/clipboard").size();
    execute("org.eclipse.ui.edit.copy", projectItem).get(60, TimeUnit.SECONDS);
    h.await("the explorer's copy", v -> h.notifications("client/clipboard").size() > before);
    assertFalse(ran[0], "the part's handler ran while the explorer was active");
    site.disposeServices();
  }
}
