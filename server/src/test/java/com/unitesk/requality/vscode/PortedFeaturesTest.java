package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import com.unitesk.requality.vscode.services.PropertiesService;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The ported Eclipse layer running in the server on the real ETSI catalog: tabbed property sections, commands and
 * their handlers (plugin.xml), dynamic menus, the project property pages, preferences and wizards.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortedFeaturesTest {
  static ServerHarness h;
  static String projectItem;
  static String requirement;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    JsonArray roots = h.call("explorer/roots", null).getAsJsonArray();
    projectItem = roots.get(0).getAsJsonObject().get("id").getAsString();
    requirement = findItem(projectItem, "is.Requirement");
    assertNotNull(requirement, "no requirement in the catalog");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** Breadth-first search of the explorer for an item whose context value has the tag. */
  static String findItem(String from, String tag) throws Exception {
    Deque<String> queue = new ArrayDeque<>();
    queue.add(from);
    int visited = 0;
    while (!queue.isEmpty() && visited < 400) {
      String id = queue.poll();
      visited++;
      for (JsonElement e : h.children(id)) {
        JsonObject it = e.getAsJsonObject();
        String cv = it.has("contextValue") ? it.get("contextValue").getAsString() : "";
        if ((" " + cv + " ").contains(" " + tag + " "))
          return it.get("id").getAsString();
        if (it.has("hasChildren") && it.get("hasChildren").getAsBoolean())
          queue.add(it.get("id").getAsString());
      }
    }
    return null;
  }

  static void select(String id) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(id);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  static List<String> tabs() {
    return Display.getDefault().syncCall(() -> PropertiesService.page().getCurrentTabIds());
  }

  @Test
  @Order(1)
  void propertiesViewShowsThePortedSections() throws Exception {
    h.call("properties/open", null);
    select(requirement);
    h.await("requirement tabs", v -> tabs().contains("requality.propertyTab.advanced"));
    List<String> t = tabs();
    assertTrue(t.contains("requality.propertyTab.source"), t.toString());
    // Eclipse order of the Requality tabs (TabbedPropertyRegistry): advanced before source.
    assertTrue(t.indexOf("requality.propertyTab.advanced") < t.indexOf("requality.propertyTab.source"), t.toString());
    select(projectItem);
    h.await("project tabs", v -> tabs().contains("requality.propertyTab.main"));
  }

  @Test
  @Order(2)
  void commandsRunTheirEclipseHandlers() throws Exception {
    select(requirement);
    JsonObject p = new JsonObject();
    JsonArray cmds = new JsonArray();
    cmds.add("com.unitesk.requality.commands.CopyNodeQId");
    cmds.add("com.unitesk.requality.commands.ShowHiddenChildren");
    p.add("commands", cmds);
    JsonObject state = h.call("command/state", p).getAsJsonObject();
    JsonObject copy = state.getAsJsonObject("com.unitesk.requality.commands.CopyNodeQId");
    assertTrue(copy.get("handled").getAsBoolean(), state.toString());
    assertTrue(copy.get("enabled").getAsBoolean(), state.toString());
    // CopyNodeQId is an IElementUpdater: its menu label depends on the project's full-name mode.
    assertTrue(copy.get("label").getAsString().startsWith("Copy "), state.toString());

    int before = h.notifications("client/clipboard").size();
    JsonObject ex = new JsonObject();
    ex.addProperty("command", "com.unitesk.requality.commands.CopyNodeQId");
    h.call("command/execute", ex);
    h.await("clipboard", v -> h.notifications("client/clipboard").size() > before);
    String text = h.notifications("client/clipboard").get(before).get("text").getAsString();
    assertFalse(text.isEmpty());
  }

  @Test
  @Order(3)
  void newMenuListsTheElementsThatCanBeCreated() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", "com.unitesk.requality.CreateDynNewMenu");
    JsonArray nodes = new JsonArray();
    nodes.add(requirement);
    p.add("nodes", nodes);
    JsonArray items = h.call("menu/items", p).getAsJsonArray();
    List<String> labels = new ArrayList<>();
    for (JsonElement e : items)
      if (e.getAsJsonObject().has("label"))
        labels.add(e.getAsJsonObject().get("label").getAsString());
    assertTrue(labels.stream().anyMatch(l -> l.contains("Requirement")), labels.toString());
    // The static entries of the VS Code New submenu find their NewTypedTemplatedMenuPoint by node type...
    boolean typed = false;
    for (JsonElement e : items)
      if (e.getAsJsonObject().has("type") && "Requirement".equals(e.getAsJsonObject().get("type").getAsString()))
        typed = true;
    assertTrue(typed, items.toString());
    // ...and are shown for the items whose context value has the new.<Type> tag (CreateElementOperation.isEnabled).
    JsonObject item = h.call("explorer/item", "id", requirement).getAsJsonObject();
    assertTrue(item.get("contextValue").getAsString().contains("new.Requirement"), item.toString());
  }

  /** Opens a modal dialog by an RPC call, checks its title and closes it like the user (Cancel). */
  static void openAndCancel(String method, JsonObject params, String titlePart) throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = h.callAsync(method, params);
    JsonObject shell = h.awaitPanelShell(before);
    assertTrue(shell.get("title").getAsString().contains(titlePart), shell.toString());
    h.closeShell(shell.get("shell").getAsString());
    assertEquals(false, f.get(60, TimeUnit.SECONDS).getAsBoolean());
  }

  @Test
  @Order(4)
  void projectPropertiesShowTheRequalityPages() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("project", projectItem);
    openAndCancel("properties/openProject", p, "Properties for");
  }

  @Test
  @Order(5)
  void preferencesOpen() throws Exception {
    openAndCancel("preferences/open", new JsonObject(), "Preferences");
  }

  @Test
  @Order(6)
  void newProjectWizardOpens() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("id", "com.unitesk.requality.wizards.ReqWizard");
    openAndCancel("wizard/open", p, "");
  }

  @Test
  @Order(7)
  void uniEditorOpenShowsTheUniEditorPart() throws Exception {
    select(requirement);
    int before = h.notifications("ui/openShell").size();
    JsonObject ex = new JsonObject();
    ex.addProperty("command", "com.unitesk.requality.commands.UniEditorOpen");
    h.call("command/execute", ex);
    JsonObject shell = h.awaitPanelShell(before);
    String id = shell.get("shell").getAsString();
    // The ported UniEditor (a JSEditor EditorPart) shows the editor page of the resources server in its Browser.
    h.await("editor page", v -> {
      try {
        JsonObject snap = h.call("ui/snapshot", "shell", id).getAsJsonObject();
        for (JsonElement op : snap.getAsJsonArray("ops")) {
          JsonObject o = op.getAsJsonObject();
          if ("browser".equals(o.has("type") ? o.get("type").getAsString() : null) || o.toString().contains("\"url\""))
            if (o.toString().contains("http://") && o.toString().contains("session="))
              return true;
        }
        return false;
      } catch (Exception e) {
        return false;
      }
    });
    h.closeShell(id);
  }

  /** The text of all widgets of a shell (labels, texts, items). */
  static String shellText(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  @Test
  @Order(8)
  void compareShowsTheTreeNodeCompareViewer() throws Exception {
    // Compare With > Each Other of two requirement files: the local one and the contents of the other.
    List<java.nio.file.Path> files;
    try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.walk(new java.io.File(h.workspace, "TS103096").toPath())) {
      files = s.filter(f -> f.toString().endsWith(".json") && f.getNameCount() > 4).filter(f -> {
        try {
          String c = java.nio.file.Files.readString(f);
          return c.contains("\"Requirement\"") && c.contains("\"_name\"");
        } catch (java.io.IOException e) {
          return false;
        }
      }).limit(2).collect(java.util.stream.Collectors.toList());
    }
    assertEquals(2, files.size());
    JsonObject left = new JsonObject();
    left.addProperty("name", files.get(0).getFileName().toString());
    left.addProperty("path", files.get(0).toString());
    left.addProperty("label", "left");
    JsonObject right = new JsonObject();
    right.addProperty("name", files.get(1).getFileName().toString());
    right.addProperty("path", files.get(1).toString());
    right.addProperty("label", "right");
    right.addProperty("content", java.nio.file.Files.readString(files.get(1)));
    JsonObject p = new JsonObject();
    p.addProperty("title", "Compare");
    p.add("left", left);
    p.add("right", right);
    int before = h.notifications("ui/openShell").size();
    assertTrue(h.call("compare/open", p).getAsBoolean());
    JsonObject shell = h.awaitPanelShell(before);
    String sid = shell.get("shell").getAsString();
    String id1 = files.get(0).getFileName().toString().replaceAll("\\.json$", "");
    String id2 = files.get(1).getFileName().toString().replaceAll("\\.json$", "");
    // TreeNodeCompareViewer: an ElementComparePanel per side showing its node.
    h.await("compare panels", v -> {
      try {
        String t = shellText(sid);
        return t.contains(id1) && t.contains(id2);
      } catch (Exception e) {
        return false;
      }
    });
    h.closeShell(sid);
  }

  static List<String> popupLabels(String node) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", "com.unitesk.requality.views.RequalityExplorer.popup");
    JsonArray nodes = new JsonArray();
    nodes.add(node);
    p.add("nodes", nodes);
    List<String> labels = new ArrayList<>();
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray())
      if (e.getAsJsonObject().has("label") && e.getAsJsonObject().get("enabled").getAsBoolean())
        labels.add(e.getAsJsonObject().get("label").getAsString());
    return labels;
  }

  @Test
  @Order(9)
  void explorerActionProvidersFillTheContextMenu() throws Exception {
    // CNFActionProvider (plugin.xml actionProvider of the explorer content): Import Document... for a document
    // folder, the document processors for a document.
    String docFolder = findItem(projectItem, "is.DocFolder");
    assertNotNull(docFolder);
    List<String> folderMenu = popupLabels(docFolder);
    assertTrue(folderMenu.contains("Import Document..."), folderMenu.toString());
    String document = findItem(projectItem, "is.Document");
    assertNotNull(document);
    List<String> docMenu = popupLabels(document);
    assertTrue(docMenu.contains("HTML Headers Document Processor"), docMenu.toString());
    assertTrue(docMenu.contains("Update Document"), docMenu.toString());
    assertFalse(docMenu.contains("Import Document..."), docMenu.toString());
    // a requirement has no CNF actions of its own: the plugin.xml menu contributions of the popup (the Requality
    // commands, and the menus New (NewMenuPoint) and Edit in external browser with their own items), and as it adapts
    // to IFile and ResourceMapping the Team and Compare popup contributions of org.eclipse.team.ui and
    // org.eclipse.compare
    assertEquals(List.of("New", "Copy Full Name", "Copy", "Edit in UniEditor", "Edit in ModuleEditor", "Open Mass Ops Wizard",
        "Review", "Edit in external browser", "Team", "Compare With", "Replace With"), popupLabels(requirement));

    // Import Document... opens the DocumentImportWizard.
    popupLabels(docFolder);
    JsonObject p = new JsonObject();
    p.addProperty("menu", "com.unitesk.requality.views.RequalityExplorer.popup");
    p.add("nodes", new JsonArray());
    p.getAsJsonArray("nodes").add(docFolder);
    JsonArray items = h.call("menu/items", p).getAsJsonArray();
    int index = -1;
    for (JsonElement e : items)
      if ("Import Document...".equals(e.getAsJsonObject().has("label") ? e.getAsJsonObject().get("label").getAsString() : null))
        index = e.getAsJsonObject().get("index").getAsInt();
    int before = h.notifications("ui/openShell").size();
    JsonObject run = new JsonObject();
    run.addProperty("menu", "com.unitesk.requality.views.RequalityExplorer.popup");
    run.addProperty("index", index);
    CompletableFuture<JsonElement> f = h.callAsync("menu/run", run);
    JsonObject shell = h.awaitPanelShell(before);
    h.closeShell(shell.get("shell").getAsString());
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(10)
  void propertySheetContextMenuGetsItsContributions() throws Exception {
    h.call("properties/open", null);
    select(requirement);
    List<String> labels = Display.getDefault().syncCall(() -> {
      com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage page = com.unitesk.requality.vscode.ui.workbench.PlatformUI
          .getWorkbench().getActiveWorkbenchWindow().getActivePage();
      // The Properties view is the Eclipse PropertySheet part showing the tabbed property page.
      com.unitesk.requality.vscode.ui.workbench.IViewPart view = page.findView("org.eclipse.ui.views.PropertySheet");
      assertTrue(view instanceof com.unitesk.requality.vscode.ui.workbench.PropertySheet);
      assertTrue(((com.unitesk.requality.vscode.ui.workbench.PropertySheet) view).getCurrentPage() == PropertiesService.page());
      // A context menu registered with its site gets popup:org.eclipse.ui.views.PropertySheet ("Delete Location").
      com.unitesk.requality.vscode.ui.jface.action.MenuManager mgr = new com.unitesk.requality.vscode.ui.jface.action.MenuManager();
      mgr.setRemoveAllWhenShown(true);
      view.getSite().registerContextMenu(mgr, view.getSite().getSelectionProvider());
      com.unitesk.requality.vscode.ui.swt.widgets.Menu menu = mgr.createContextMenu(PropertiesService.page().getControl());
      menu.notifyListeners(com.unitesk.requality.vscode.ui.swt.SWT.Show, new com.unitesk.requality.vscode.ui.swt.widgets.Event());
      List<String> r = new ArrayList<>();
      for (com.unitesk.requality.vscode.ui.swt.widgets.MenuItem mi : menu.getItems())
        r.add(mi.getText());
      menu.dispose();
      return r;
    });
    assertTrue(labels.stream().anyMatch(l -> l.startsWith("Delete Location")), labels.toString());
  }

  @Test
  @Order(11)
  void apiSourceProvidesTheEclipseExtensions() throws Exception {
    // Delete handlers: the org.eclipse.ui.edit.delete handlers of plugin.xml implementing IDeleteHandler.
    java.util.SortedMap<String, com.unitesk.requality.core.operation.IDeleteHandler> dh = Display.getDefault()
        .syncCall(() -> com.unitesk.requality.vscode.port.eclipse.Activator.getDefault().getDeleteHandlers());
    assertFalse(dh.isEmpty());
    // Mass operations with their extra wizard pages (EclipseAPI).
    List<String> ids = new ArrayList<>();
    for (com.unitesk.requality.core.api.MassOppDescriptor d : com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI.getOne().loadMassOpsDescriptors())
      ids.add(d.getId());
    assertTrue(ids.contains("com.requality.attributeValueChange") && ids.contains("com.requality.attributeTypeChange"), ids.toString());
  }

  @Test
  @Order(12)
  void aNewProjectGetsTheCheckersBuilderFromItsNature() throws Exception {
    // NewRequalityProjectWizard creates the project with the Requality nature; ReqCatalogue.configure adds the
    // checkers builder, so checkers are enabled for new projects (CheckerCNFMenuBuilder.getCheckersEnabled).
    boolean enabled = Display.getDefault().syncCall(() -> {
      try {
        com.unitesk.requality.vscode.ui.resources.IProject p = com.unitesk.requality.vscode.port.eclipse.wizards.NewRequalityProjectWizard
            .performFinishNewElement("NatureTest");
        return com.unitesk.requality.vscode.port.eclipse.ui.cnf.CheckerCNFMenuBuilder.getCheckersEnabled(p);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    assertTrue(enabled);
    String dotProject = java.nio.file.Files.readString(new java.io.File(h.workspace, "NatureTest/.project").toPath());
    assertTrue(dotProject.contains("com.unitesk.requality.checkers"), dotProject);
    assertTrue(dotProject.contains("com.unitesk.requality.natures.reqcatalogue"), dotProject);
    // Rewriting the description (here: Disable Checkers) keeps the referenced projects of the .project.
    java.nio.file.Path dp = new java.io.File(h.workspace, "NatureTest/.project").toPath();
    java.nio.file.Files.writeString(dp, dotProject.replace("<projects>", "<projects>\n\t\t<project>Other</project>"));
    Display.getDefault().syncCall(() -> {
      try {
        com.unitesk.requality.vscode.ui.resources.IProject p = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
            .getWorkspace().getRoot().getProject("NatureTest");
        com.unitesk.requality.vscode.ui.resources.IProjectDescription d = p.getDescription();
        assertEquals(1, d.getReferencedProjects().length);
        d.setBuildSpec(new com.unitesk.requality.vscode.ui.resources.ICommand[0]);
        p.setDescription(d, null);
        return null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    String after = java.nio.file.Files.readString(dp);
    assertTrue(after.contains("<project>Other</project>") && !after.contains("com.unitesk.requality.checkers"), after);
  }

  @Test
  @Order(13)
  void renamingAProjectRunsTheRequalityRenameParticipant() throws Exception {
    // The explorer popup offers Rename... for a project (navigator RefactorActionProvider of plugin.xml).
    assertTrue(popupLabels(projectItem).contains("Rename..."), popupLabels(projectItem).toString());
    // Refactor > Rename: ProjectRenameParticipant checks the conditions, its changes rename the Requality project.
    com.unitesk.requality.vscode.ui.resources.IProject p = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
        .getWorkspace().getRoot().getProject("TS103096");
    com.unitesk.requality.vscode.ui.ltk.RefactoringStatus st = com.unitesk.requality.vscode.ui.navigator.resources.RenameProjectRefactoring
        .run(p, "TS103096-renamed", new com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor());
    assertFalse(st.hasFatalError(), st.getMessageMatchingSeverity(com.unitesk.requality.vscode.ui.ltk.RefactoringStatus.FATAL));
    String dotProject = java.nio.file.Files.readString(new java.io.File(h.workspace, "TS103096/.project").toPath());
    assertTrue(dotProject.contains("<name>TS103096-renamed</name>"), dotProject);
    try {
      h.await("renamed project ready", v -> {
      try {
        for (JsonElement e : h.call("workspace/projects", null).getAsJsonObject().getAsJsonArray("projects")) {
          JsonObject o = e.getAsJsonObject();
          if ("TS103096-renamed".equals(o.get("name").getAsString()) && "ready".equals(o.get("state").getAsString()))
            return true;
        }
        return false;
      } catch (Exception ex) {
        return false;
      }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + h.call("workspace/projects", null), e);
    }
    // The catalog keeps its item (its folder is not renamed: it is not at its default location); other Eclipse
    // projects of the workspace are listed too.
    JsonArray roots = h.call("explorer/roots", null).getAsJsonArray();
    assertEquals("TS103096-renamed", ServerHarness.find(roots, "id", projectItem).get("label").getAsString(), roots.toString());
  }

  @Test
  @Order(14)
  void coreViewRequestsOpenTheWorkbenchViews() throws Exception {
    // EclipseModalMessageConsumer.openView: "ToDoList" shows the Update Processor Tasks view (after a document update).
    com.unitesk.requality.core.model.TreeDB db = com.unitesk.requality.vscode.core.Nodes.find(requirement).getTreeDB();
    int before = h.notifications("client/showView").size();
    com.unitesk.requality.tool.message.RMessages.openView(db, "ToDoList", false);
    h.await("todo view shown", v -> h.notifications("client/showView").size() > before);
    assertEquals("requality.todo", h.notifications("client/showView").get(before).get("id").getAsString());
  }

  @Test
  @Order(15)
  void generateReportCreatesTheReportAndOpensTheReportViewer() throws Exception {
    // New report settings (CreateNode NodeType=ReportSettings: the default report template) in a report folder.
    String folder = findItem(projectItem, "is.ReportFolder");
    assertNotNull(folder);
    java.util.Set<String> old = new java.util.HashSet<>();
    for (JsonElement e : h.children(folder))
      old.add(e.getAsJsonObject().get("id").getAsString());
    int before = h.notifications("ui/openShell").size();
    JsonObject create = new JsonObject();
    create.addProperty("command", "com.unitesk.requality.commands.CreateNode");
    JsonObject params = new JsonObject();
    params.addProperty("requality.parameter.NodeType", "ReportSettings");
    create.add("params", params);
    JsonArray sel = new JsonArray();
    sel.add(folder);
    create.add("nodes", sel);
    CompletableFuture<JsonElement> created = h.callAsync("command/execute", create);
    // A dialog asking for the new element (if any) is confirmed with its default button.
    long end = System.currentTimeMillis() + 5000;
    while (!created.isDone() && System.currentTimeMillis() < end) {
      List<JsonObject> shells = h.notifications("ui/openShell");
      for (int k = before; k < shells.size(); k++)
        if (!"view".equals(shells.get(k).has("host") && !shells.get(k).get("host").isJsonNull() ? shells.get(k).get("host").getAsString() : null)) {
          h.pressDefaultButton(shells.get(k).get("shell").getAsString());
          before = k + 1;
        }
      Thread.sleep(200);
    }
    created.get(60, TimeUnit.SECONDS);
    String[] settings = new String[1];
    h.await("new report settings", v -> {
      try {
        for (JsonElement e : h.children(folder)) {
          JsonObject it = e.getAsJsonObject();
          if (!old.contains(it.get("id").getAsString()) && it.get("contextValue").getAsString().contains("is.ReportSettings"))
            settings[0] = it.get("id").getAsString();
        }
        return settings[0] != null;
      } catch (Exception ex) {
        return false;
      }
    });
    // GenerateReportHandler: the template with its plugin.xml data source produces a ReportData node and
    // OpenReportHandler shows it in the Report Viewer.
    int opened = h.notifications("ui/openShell").size();
    JsonObject ex = new JsonObject();
    ex.addProperty("command", "com.unitesk.requality.commands.GenerateReport");
    JsonArray nodes = new JsonArray();
    nodes.add(settings[0]);
    ex.add("nodes", nodes);
    // The handler is disabled while the tree is busy (CoreUtils.busyCondition: a cache transaction, e.g. the checkers
    // after the new report settings): the command runs once it is enabled, as the user could run it.
    h.await("Generate Report enabled for the report settings", v -> {
      try {
        h.call("command/execute", ex);
        return true;
      } catch (Exception e) {
        if (String.valueOf(e.getMessage()).contains("is not enabled"))
          return false;
        throw new IllegalStateException(e);
      }
    });
    JsonObject shell = h.awaitPanelShell(opened);
    String url = FoundationTest.browserUrl(h, shell.get("shell").getAsString());
    assertTrue(url.contains("report"), url);
    h.closeShell(shell.get("shell").getAsString());
  }

  @Test
  @Order(16)
  void theNewProjectWizardCreatesTheCatalogInTheWorkspace() throws Exception {
    // NewRequalityProjectWizard.performFinish -> tracker.createProject: the catalog is created in the workspace
    // (Eclipse: workspace root), loaded, and listed by the explorer.
    com.unitesk.requality.core.model.TreeDB db = com.unitesk.requality.vscode.port.eclipse.wizards.NewRequalityProjectWizard
        .createProject("NewCatalog", "NewCatalog");
    assertNotNull(db);
    java.io.File dir = new java.io.File(h.workspace, "NewCatalog");
    assertTrue(new java.io.File(dir, ".project").isFile(), java.util.Arrays.toString(h.workspace.list()));
    String dotProject = java.nio.file.Files.readString(new java.io.File(dir, ".project").toPath());
    assertTrue(dotProject.contains("com.unitesk.requality.natures.reqcatalogue") && dotProject.contains("com.unitesk.requality.checkers"), dotProject);
    h.await("new catalog listed", v -> {
      try {
        for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
          if ("NewCatalog".equals(e.getAsJsonObject().get("label").getAsString()))
            return true;
        return false;
      } catch (Exception ex) {
        return false;
      }
    });
  }

  @Test
  @Order(17)
  void renameActionAsksForTheNewNameAndRenames() throws Exception {
    // As in VS Code, the Links Explorer is open and shows the links of the selection while the project is renamed.
    JsonObject view = new JsonObject();
    view.addProperty("view", "requality.links");
    h.call("views/open", view);
    select(requirement);
    JsonObject p = new JsonObject();
    p.addProperty("menu", "com.unitesk.requality.views.RequalityExplorer.popup");
    JsonArray nodes = new JsonArray();
    nodes.add(projectItem);
    p.add("nodes", nodes);
    int index = -1;
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray())
      if ("Rename...".equals(e.getAsJsonObject().has("label") ? e.getAsJsonObject().get("label").getAsString() : null))
        index = e.getAsJsonObject().get("index").getAsInt();
    assertTrue(index >= 0);
    int before = h.notifications("ui/openShell").size();
    JsonObject run = new JsonObject();
    run.addProperty("menu", "com.unitesk.requality.views.RequalityExplorer.popup");
    run.addProperty("index", index);
    CompletableFuture<JsonElement> f = h.callAsync("menu/run", run);
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Rename Resource", shell.get("title").getAsString(), shell.toString());
    String sid = shell.get("shell").getAsString();
    JsonObject snap = h.call("ui/snapshot", "shell", sid).getAsJsonObject();
    String text = null;
    for (JsonElement e : snap.getAsJsonArray("ops"))
      if ("text".equals(e.getAsJsonObject().has("type") ? e.getAsJsonObject().get("type").getAsString() : null))
        text = e.getAsJsonObject().get("id").getAsString();
    assertNotNull(text, snap.toString());
    JsonObject mod = new JsonObject();
    mod.addProperty("id", text);
    mod.addProperty("type", "modify");
    JsonObject d = new JsonObject();
    d.addProperty("text", "Renamed-Again");
    d.addProperty("selStart", 13);
    d.addProperty("selEnd", 13);
    mod.add("data", d);
    h.notify("ui/event", mod);
    h.pressDefaultButton(sid);
    f.get(60, TimeUnit.SECONDS);
    h.await("renamed in the explorer", v -> {
      try {
        for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
          if ("Renamed-Again".equals(e.getAsJsonObject().get("label").getAsString()))
            return true;
        return false;
      } catch (Exception ex) {
        return false;
      }
    });
  }

  /** The element the Properties view shows (the first element of the selection of its page). */
  static Object propertiesInput() {
    return Display.getDefault().syncCall(() -> {
      com.unitesk.requality.vscode.ui.jface.viewers.ISelection sel = PropertiesService.page().getCurrentSelection();
      return sel instanceof com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection
          ? ((com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) sel).getFirstElement() : null;
    });
  }

  @Test
  @Order(100)
  void pinToSelectionKeepsTheSelectionOfThePropertiesView() throws Exception {
    h.call("properties/open", null);
    select(requirement);
    h.await("the requirement in Properties", v -> propertiesInput() instanceof com.unitesk.requality.core.model.TreeNode);
    Object pinnedInput = propertiesInput();
    JsonObject p = new JsonObject();
    p.addProperty("pinned", true);
    h.call("properties/pin", p);
    // PinPropertySheetAction: the view says where its selection comes from and keeps it
    h.await("the content description", v -> h.notifications("client/viewDescription").stream()
        .anyMatch(n -> n.get("description").getAsString().startsWith("Selection from ")));
    select(projectItem);
    Thread.sleep(500);
    assertTrue(propertiesInput() == pinnedInput, String.valueOf(propertiesInput()));
    p.addProperty("pinned", false);
    h.call("properties/pin", p);
    h.await("the project in Properties", v -> !(propertiesInput() instanceof com.unitesk.requality.core.model.TreeNode));
  }
}
