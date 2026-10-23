package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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
 * Frames of the Requality Explorer (org.eclipse.ui.navigator.resources GoIntoActionProvider and the frame actions of
 * the CommonNavigator toolbar): Go Into a project or folder makes it the input of the viewer, i.e. the roots of the
 * VS Code tree; Back, Forward and Up move between the frames.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExplorerFramesTest {
  static ServerHarness h;
  static String catalogItem;
  static String plainItem;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "plain");
    new File(plain, "docs/sub").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "docs/readme.txt").toPath(), "text");
    Files.writeString(new File(plain, "docs/sub/a.txt").toPath(), "a");
    Files.writeString(new File(plain, "top.txt").toPath(), "top");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    catalogItem = itemOf("TS103096-STF517");
    plainItem = itemOf("plain");
    assertNotNull(catalogItem);
    assertNotNull(plainItem);
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

  static boolean isReady(String name) throws Exception {
    for (JsonElement e : h.call("workspace/projects", null).getAsJsonObject().getAsJsonArray("projects"))
      if (name.equals(e.getAsJsonObject().get("name").getAsString()))
        return "ready".equals(e.getAsJsonObject().get("state").getAsString());
    return false;
  }

  static java.util.Map<String, Boolean> popup(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", ProjectOperationsTest.POPUP);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    java.util.Map<String, Boolean> r = new java.util.LinkedHashMap<>();
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray()) {
      JsonObject o = e.getAsJsonObject();
      // submenus (Show In, Open With) have children and no enablement
      if (o.has("label") && o.has("enabled"))
        r.put(o.get("label").getAsString(), o.get("enabled").getAsBoolean());
    }
    return r;
  }

  static List<String> rootLabels() throws Exception {
    List<String> r = new ArrayList<>();
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      r.add(e.getAsJsonObject().get("label").getAsString());
    return r;
  }

  static JsonObject frames() throws Exception {
    return h.call("explorer/frames", null).getAsJsonObject();
  }

  static void execute(String command, String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    h.callAsync("command/execute", p).get(60, TimeUnit.SECONDS);
  }

  static JsonObject frameAction(String action) throws Exception {
    return h.call("explorer/frameAction", "action", action).getAsJsonObject();
  }

  @Test
  @Order(1)
  void goIntoIsInThePopupOfProjectsAndFolders() throws Exception {
    h.await("the catalog loaded", v -> {
      try {
        return isReady("TS103096-STF517");
      } catch (Exception e) {
        return false;
      }
    });
    assertTrue(popup(catalogItem).get("Go Into"), "Go Into in the catalog popup");
    assertTrue(popup(plainItem).get("Go Into"));
    String docs = ServerHarness.find(h.children(plainItem), "label", "docs").get("id").getAsString();
    assertTrue(popup(docs).get("Go Into"));
    // not for files and nodes (the enablement adapts to IProject / IFolder only)
    String file = ServerHarness.find(h.children(plainItem), "label", "top.txt").get("id").getAsString();
    assertFalse(popup(file).containsKey("Go Into"));
    String node = h.children(catalogItem).get(0).getAsJsonObject().get("id").getAsString();
    assertFalse(popup(node).containsKey("Go Into"));
    // no frames yet: the toolbar shows no frame actions
    JsonObject f = frames();
    assertFalse(f.get("back").getAsBoolean() || f.get("forward").getAsBoolean() || f.get("up").getAsBoolean(), f.toString());
  }

  @Test
  @Order(2)
  void goIntoTheCatalogShowsItsNodesAsRoots() throws Exception {
    List<String> projects = rootLabels();
    assertTrue(projects.contains("plain") && projects.contains("TS103096-STF517"), projects.toString());
    int before = h.notifications("explorer/frames").size();
    execute("org.eclipse.ui.navigate.goInto", catalogItem);
    h.await("frames notified", v -> h.notifications("explorer/frames").size() > before);
    List<String> roots = rootLabels();
    List<String> nodes = new ArrayList<>();
    for (JsonElement e : h.children(catalogItem))
      nodes.add(e.getAsJsonObject().get("label").getAsString());
    assertEquals(nodes, roots);
    JsonObject f = frames();
    assertTrue(f.get("back").getAsBoolean(), f.toString());
    assertTrue(f.get("up").getAsBoolean(), f.toString());
    assertFalse(f.get("forward").getAsBoolean(), f.toString());
    assertEquals("TS103096-STF517", f.get("titleToolTip").getAsString());
    // the previous frame is the workspace root, whose label is empty
    assertEquals("Back", f.get("backToolTip").getAsString());
    assertEquals("Up", f.get("upToolTip").getAsString());
  }

  @Test
  @Order(3)
  void backAndForward() throws Exception {
    JsonObject f = frameAction("back");
    assertFalse(f.get("back").getAsBoolean(), f.toString());
    assertTrue(f.get("forward").getAsBoolean(), f.toString());
    assertTrue(rootLabels().contains("plain"));
    assertEquals("", f.get("titleToolTip").getAsString());
    f = frameAction("forward");
    assertTrue(f.get("back").getAsBoolean(), f.toString());
    assertFalse(rootLabels().contains("plain"));
    f = frameAction("up");
    assertTrue(rootLabels().contains("plain"), "Up from the project goes to the workspace root");
    assertFalse(f.get("up").getAsBoolean(), f.toString());
    assertTrue(f.get("back").getAsBoolean(), f.toString());
  }

  @Test
  @Order(4)
  void goIntoAFolderAndUp() throws Exception {
    String docs = ServerHarness.find(h.children(plainItem), "label", "docs").get("id").getAsString();
    execute("org.eclipse.ui.navigate.goInto", docs);
    assertEquals(List.of("sub", "readme.txt"), rootLabels());
    assertEquals("docs", frames().get("titleToolTip").getAsString());
    JsonObject f = frameAction("up");
    assertEquals(List.of("docs", "top.txt"), rootLabels());
    assertEquals("plain", f.get("titleToolTip").getAsString());
    assertTrue(f.get("up").getAsBoolean());
    assertEquals("Back to docs", f.get("backToolTip").getAsString());
    frameAction("up");
    assertTrue(rootLabels().contains("TS103096-STF517"));
  }
}
