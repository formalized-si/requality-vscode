package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.workbench.IAggregateWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetLabelProvider;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager;

/**
 * The window working set (Eclipse's aggregate of the working sets of the active page): the images of the working sets,
 * the Select Working Set dialog with its three choices, the explorer filtered by the window working set, and the page's
 * working sets read again by a new manager. Uses the ETSI catalog and a plain Eclipse project.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkingSetWindowTest {
  static final String VIEW_MENU = "com.unitesk.requality.views.RequalityExplorer.viewMenu";
  static ServerHarness h;
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
      if (e.getAsJsonObject().get("label").getAsString().equals("Plain"))
        plainItem = e.getAsJsonObject().get("id").getAsString();
    }
    assertNotNull(plainItem, "the plain project is a root of the explorer");
  }

  @AfterAll
  static void stop() throws Exception {
    // the window working sets are kept by the workbench for the rest of the server's life; leave none behind
    page().setWorkingSets(new IWorkingSet[0]);
    h.close();
  }

  static IWorkbenchPage page() {
    return Workbench.INSTANCE.getActiveWorkbenchWindow().getActivePage();
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

  static JsonArray viewMenu() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", VIEW_MENU);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** Runs the view menu item without waiting: the dialog it opens is modal until it is closed. */
  static CompletableFuture<JsonElement> runAsync(String label) throws Exception {
    int index = -1;
    for (JsonElement e : viewMenu()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()) && !o.has("children"))
        index = o.get("index").getAsInt();
    }
    assertTrue(index >= 0, "view menu item " + label);
    JsonObject p = new JsonObject();
    p.addProperty("menu", VIEW_MENU);
    p.addProperty("index", index);
    return h.callAsync("menu/run", p);
  }

  static String viewState() throws Exception {
    File[] files = new File(h.storage, "workspaces").listFiles((d, n) -> n.endsWith("-explorer.xml"));
    assertNotNull(files);
    assertEquals(1, files.length, "one explorer state file for the workspace");
    return Files.readString(files[0].toPath(), StandardCharsets.UTF_8);
  }

  static File stateFile() {
    File[] files = new File(h.storage, "workspaces").listFiles((d, n) -> n.endsWith("-workingsets.xml"));
    assertNotNull(files, "the working sets file is in the server storage");
    assertEquals(1, files.length, "one working sets file for the workspace");
    return files[0];
  }

  /** The widgets of the type in the shell (a snapshot op each). */
  static List<JsonObject> widgets(String shell, String type) throws Exception {
    List<JsonObject> r = new ArrayList<>();
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (type.equals(o.has("type") ? o.get("type").getAsString() : null))
        r.add(o);
    }
    return r;
  }

  static String text(JsonObject widget) {
    if (!widget.has("props"))
      return "";
    JsonObject props = widget.getAsJsonObject("props");
    return props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString().replace("&", "") : "";
  }

  /** The button of the shell with the text (mnemonics ignored). */
  static String button(String shell, String text) throws Exception {
    for (JsonObject b : widgets(shell, "button")) {
      if (text.equals(text(b)))
        return b.get("id").getAsString();
    }
    throw new AssertionError("No button " + text + " in " + shell);
  }

  static boolean selected(String shell, String text) throws Exception {
    for (JsonObject b : widgets(shell, "button")) {
      if (text.equals(text(b)))
        return b.getAsJsonObject("props").get("selection").getAsBoolean();
    }
    throw new AssertionError("No button " + text + " in " + shell);
  }

  static void press(String shell, String text) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("id", button(shell, text));
    p.addProperty("type", "selection");
    p.add("data", new JsonObject());
    h.notify("ui/event", p);
  }

  /** The path of the image of the working set, as the label provider gives it. */
  static String imagePath(IWorkingSet set) {
    Image image = new WorkingSetLabelProvider().getImage(set);
    assertNotNull(image, "the working set has an image: " + set.getName());
    return image.getPath();
  }

  @Test
  @Order(1)
  void theWorkingSetsHaveTheirIcons() throws Exception {
    IWorkingSetManager m = Workbench.INSTANCE.getWorkingSetManager();
    IAdaptable plain = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    IWorkingSet ws = m.createWorkingSet("Plain only", new IAdaptable[] { plain });
    // the page of the IDE resource type, as the New Working Set wizard sets it
    ws.setId("org.eclipse.ui.resourceWorkingSetPage");
    m.addWorkingSet(ws);
    String wsImage = imagePath(ws);
    assertTrue(wsImage.endsWith("workset.png"), wsImage);
    assertTrue(new File(wsImage).isFile(), "the icon exists: " + wsImage);

    String windowImage = imagePath(page().getAggregateWorkingSet());
    assertTrue(windowImage.endsWith("workingsets.png"), windowImage);
    assertTrue(new File(windowImage).isFile(), "the icon exists: " + windowImage);
  }

  @Test
  @Order(2)
  void selectDialogHasTheThreeChoices() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> run = runAsync("Select Working Set...");
    JsonObject shell = h.awaitShell(before);
    String id = shell.get("shell").getAsString();
    assertEquals("Select Working Set", shell.get("title").getAsString());
    List<String> texts = new ArrayList<>();
    for (JsonObject b : widgets(id, "button"))
      texts.add(text(b));
    assertTrue(texts.containsAll(List.of("Window Working Sets", "No Working Sets", "Selected Working Sets")), texts.toString());
    h.closeShell(id);
    run.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(3)
  void windowWorkingSetFiltersTheExplorer() throws Exception {
    IWorkingSet plainOnly = Workbench.INSTANCE.getWorkingSetManager().getWorkingSet("Plain only");
    assertNotNull(plainOnly);
    page().setWorkingSets(new IWorkingSet[] { plainOnly });

    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> run = runAsync("Select Working Set...");
    JsonObject shell = h.awaitShell(before);
    String id = shell.get("shell").getAsString();
    assertTrue(selected(id, "No Working Sets"), "no working sets is the first choice");
    press(id, "Window Working Sets");
    assertTrue(selected(id, "Window Working Sets"));
    assertFalse(selected(id, "No Working Sets"));
    h.pressDefaultButton(id);
    run.get(60, TimeUnit.SECONDS);

    assertEquals(List.of("Plain"), rootLabels(), "the window working set shows the projects of the page's working sets");
    String windowName = page().getAggregateWorkingSet().getName();
    h.await("the window working set is the explorer's working set", v -> {
      try {
        return viewState().contains("currentWorkingSetName=\"" + windowName + "\"");
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(4)
  void windowWorkingSetsAreReadAgainByANewManager() throws Exception {
    IAggregateWorkingSet window = page().getAggregateWorkingSet();
    String file = Files.readString(stateFile().toPath(), StandardCharsets.UTF_8);
    assertTrue(file.contains("windowWorkingSets"), file);

    WorkingSetManager restarted = new WorkingSetManager();
    restarted.restoreState();
    assertArrayEquals(new String[] { "Plain only" }, restarted.getWindowWorkingSetNames());
    assertEquals(window.getName(), restarted.getWindowAggregateId(), "the same window working set after a restart");
    IWorkingSet restoredWindow = restarted.getWorkingSet(window.getName());
    assertNotNull(restoredWindow, "the window working set is restored");
    assertTrue(restoredWindow.isAggregateWorkingSet());
    assertEquals(1, ((IAggregateWorkingSet) restoredWindow).getComponents().length);
  }
}
