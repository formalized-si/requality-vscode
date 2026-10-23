package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager;

/**
 * The working sets of the Requality Explorer (org.eclipse.ui.navigator.resources WorkingSetActions): the view menu items,
 * the working set that filters the explorer to its resources, the most recently used entries, the edit and select dialogs,
 * and the working sets file read again by a new manager. Uses the ETSI catalog and a plain Eclipse project.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkingSetsTest {
  static final String VIEW_MENU = "com.unitesk.requality.views.RequalityExplorer.viewMenu";
  static ServerHarness h;
  static String etsiItem;
  static String plainItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    File plain = new File(h.workspace, "Plain");
    assertTrue(plain.mkdirs());
    Files.write(new File(plain, ".project").toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription>\n\t<name>Plain</name>\n\t<comment></comment>\n\t<projects>\n\t</projects>\n"
        + "\t<buildSpec>\n\t</buildSpec>\n\t<natures>\n\t</natures>\n</projectDescription>\n")
        .getBytes(StandardCharsets.UTF_8));
    h.initialize(h.workspace);
    h.awaitProjects(1);
    for (JsonElement e : roots()) {
      String label = e.getAsJsonObject().get("label").getAsString();
      if (label.equals("Plain"))
        plainItem = e.getAsJsonObject().get("id").getAsString();
      else
        etsiItem = e.getAsJsonObject().get("id").getAsString();
    }
    assertNotNull(plainItem, "the plain project is a root of the explorer");
    assertNotNull(etsiItem, "the catalog project is a root of the explorer");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static JsonArray roots() throws Exception {
    return h.call("explorer/roots", null).getAsJsonArray();
  }

  static List<String> rootLabels() throws Exception {
    List<String> r = new ArrayList<>();
    for (JsonElement e : roots())
      r.add(e.getAsJsonObject().get("label").getAsString());
    return r;
  }

  /** The items of the explorer view menu (flattened, without separators). */
  static JsonArray viewMenu() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", VIEW_MENU);
    return h.call("menu/items", p).getAsJsonArray();
  }

  static JsonObject item(String label) throws Exception {
    JsonObject found = find(viewMenu(), label);
    if (found != null)
      return found;
    throw new AssertionError("No view menu item " + label + " in " + viewMenu());
  }

  /** An item of the menu or of its submenus ("Top Level Elements" > "Working Sets"). */
  static JsonObject find(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()) && !o.has("children"))
        return o;
      if (o.has("children")) {
        JsonObject c = find(o.getAsJsonArray("children"), label);
        if (c != null)
          return c;
      }
    }
    return null;
  }

  /** The view memento of the explorer (WorkingSetActionProvider.saveState: the active working set). */
  static String viewState() throws Exception {
    File[] files = new File(h.storage, "workspaces").listFiles((d, n) -> n.endsWith("-explorer.xml"));
    assertNotNull(files);
    assertEquals(1, files.length, "one explorer state file for the workspace");
    return Files.readString(files[0].toPath(), StandardCharsets.UTF_8);
  }

  /** Runs the view menu item (the menu/run of the last menu/items of the view menu). */
  static void run(String label) throws Exception {
    runAsync(label).get(60, TimeUnit.SECONDS);
  }

  /** Runs the view menu item without waiting: a dialog it opens is modal until it is closed. */
  static CompletableFuture<JsonElement> runAsync(String label) throws Exception {
    JsonObject it = item(label);
    JsonObject p = new JsonObject();
    p.addProperty("menu", VIEW_MENU);
    p.addProperty("index", it.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  static File stateFile() {
    File[] files = new File(h.storage, "workspaces").listFiles((d, n) -> n.endsWith("-workingsets.xml"));
    assertNotNull(files, "the working sets file is in the server storage");
    assertEquals(1, files.length, "one working sets file for the workspace");
    return files[0];
  }

  @Test
  @Order(1)
  void viewMenuHasTheWorkingSetActions() throws Exception {
    item("Select Working Set...");
    item("Deselect Working Set");
    item("Edit Active Working Set...");
  }

  @Test
  @Order(2)
  void selectWorkingSetOpensTheSelectionDialog() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> run = runAsync("Select Working Set...");
    JsonObject shell = h.awaitShell(before);
    assertEquals("Select Working Set", shell.get("title").getAsString());
    h.closeShell(shell.get("shell").getAsString());
    run.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(3)
  void aWorkingSetFiltersTheExplorerAndIsRecentlyUsed() throws Exception {
    IWorkingSetManager m = Workbench.INSTANCE.getWorkingSetManager();
    IAdaptable plain = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    IWorkingSet ws = m.createWorkingSet("Plain only", new IAdaptable[] { plain });
    // the page of the IDE resource type, as the New Working Set wizard sets it
    ws.setId("org.eclipse.ui.resourceWorkingSetPage");
    m.addWorkingSet(ws);
    m.addRecentWorkingSet(ws);
    // the window working set of the page comes first, then the recently used working sets (WorkingSetFilterActionGroup)
    assertTrue(item("1 Window Working Set") != null, "the window working set is in the view menu");
    assertTrue(item("2 Plain only") != null, "the most recently used working set is in the view menu");

    run("2 Plain only");
    assertEquals(List.of("Plain"), rootLabels(), "the working set shows its project only");
    String file = Files.readString(stateFile().toPath(), StandardCharsets.UTF_8);
    assertTrue(file.contains("Plain only"), file);
    // the active working set is kept in the view memento, as Eclipse does
    h.await("the view state saved", v -> {
      try {
        return viewState().contains("currentWorkingSetName=\"Plain only\"");
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(4)
  void editActiveWorkingSetOpensTheWizard() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> run = runAsync("Edit Active Working Set...");
    JsonObject shell = h.awaitShell(before);
    assertEquals("Edit Working Set", shell.get("title").getAsString());
    h.closeShell(shell.get("shell").getAsString());
    run.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(5)
  void deselectShowsEverythingAgain() throws Exception {
    run("Deselect Working Set");
    assertEquals(2, rootLabels().size(), "all the projects are shown: " + rootLabels());
    assertFalse(viewState().contains("currentWorkingSetName"), viewState());
  }

  @Test
  @Order(6)
  void theWorkingSetsAreReadAgainByANewManager() throws Exception {
    run("2 Plain only");
    WorkingSetManager restarted = new WorkingSetManager();
    restarted.restoreState();
    IWorkingSet ws = restarted.getWorkingSet("Plain only");
    assertNotNull(ws, "the working set is restored");
    IAdaptable[] elements = ws.getElements();
    assertEquals(1, elements.length);
    assertEquals("Plain", ((IResource) elements[0]).getName());
    assertTrue(viewState().contains("currentWorkingSetName=\"Plain only\""), viewState());
  }

  @Test
  @Order(7)
  void topLevelElementsWorkingSets() throws Exception {
    // the radio items of "Top Level Elements"
    assertTrue(item("Projects").get("checked").getAsBoolean(), viewMenu().toString());
    assertFalse(item("Working Sets").get("checked").getAsBoolean());
    run("Working Sets");
    assertTrue(item("Working Sets").get("checked").getAsBoolean());
    // the input of the viewer is the working set; as in Eclipse, no content extension of the Requality Explorer
    // (its viewerContentBinding does not include the working sets content) provides children for it
    assertEquals(List.of(), rootLabels());
    assertTrue(viewState().contains("showTopLevelWorkingSets=\"1\""), viewState());
    run("Projects");
    assertEquals(List.of("Plain"), rootLabels(), "the projects of the working set");
    assertTrue(viewState().contains("showTopLevelWorkingSets=\"0\""), viewState());
  }
}
