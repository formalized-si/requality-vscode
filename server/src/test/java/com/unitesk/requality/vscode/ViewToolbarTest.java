package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolItem;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/** The view toolbars of the explorer and of the links explorer: plugin.xml items, ToolItem images and checked state. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ViewToolbarTest {
  static ServerHarness h;
  static String requirement;

  static final String LINKS_DIRECTION = "com.unitesk.requality.commands.RequilityLinksDirection";
  static final String LIGHT_LINKS = "com.unitesk.requality.commands.LightLinks";
  static final String REF_SEL = "com.unitesk.requality.commands.RequilityCNFRefSel";
  static final String LINKS_VIEW = RequalityLCNF.ID;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    JsonArray roots = h.call("explorer/roots", null).getAsJsonArray();
    String projectItem = roots.get(0).getAsJsonObject().get("id").getAsString();
    requirement = findItem(projectItem, "is.Requirement");
    assertNotNull(requirement, "no requirement in the catalog");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

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
    select(id, "explorer");
  }

  static void select(String id, String source) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", source);
    JsonArray nodes = new JsonArray();
    nodes.add(id);
    p.add("nodes", nodes);
    h.call("selection/set", p);
  }

  /** Shows a view of the workbench (the explorers are shown by VS Code; the test opens them). */
  static void showView(String viewId) {
    Display.getDefault().syncExec(() -> {
      try {
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(viewId);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    });
  }

  /** The ids of the command buttons of a view toolbar, in order. */
  static List<String> toolItemIds(String viewId) {
    return Display.getDefault().syncCall(() -> {
      IViewPart view = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(viewId);
      assertNotNull(view, "view " + viewId + " is not shown");
      List<String> r = new ArrayList<>();
      for (ToolItem ti : view.getViewSite().getActionBars().getToolBarManager().getControl().getItems())
        r.add(((ContributionItem) ti.getData()).getId());
      return r;
    });
  }

  /** The value of the last client/setContext notification for a key, or null. */
  static String context(String key) {
    String value = null;
    for (JsonObject n : h.notifications("client/setContext"))
      if (key.equals(n.get("key").getAsString()))
        value = n.get("value").isJsonNull() ? null : n.get("value").getAsString();
    return value;
  }

  /** Waits until the context key has the expected value (or 10 seconds passed) and asserts it. */
  static void assertContext(String key, String expected) throws Exception {
    long end = System.currentTimeMillis() + 10_000;
    while (!expected.equals(context(key)) && System.currentTimeMillis() < end)
      Thread.sleep(100);
    assertEquals(expected, context(key), key);
  }

  static void execute(String command, String toolbarView) throws Exception {
    JsonObject ex = new JsonObject();
    ex.addProperty("command", command);
    if (toolbarView != null)
      ex.addProperty("toolbarView", toolbarView);
    h.call("command/execute", ex);
  }

  static String imageKey(String commandId) {
    return "requality.toolItem." + commandId + ".image";
  }

  @Test
  @Order(1)
  void viewToolbarsHoldThePluginXmlItems() {
    showView(RequalityCNF.ID);
    showView(LINKS_VIEW);
    assertEquals(Arrays.asList(REF_SEL), toolItemIds(RequalityCNF.ID));
    assertEquals(Arrays.asList(LINKS_DIRECTION, "com.unitesk.requality.commands.SelectLinksAttribute", LIGHT_LINKS),
        toolItemIds(LINKS_VIEW));
  }

  @Test
  @Order(2)
  void linksDirectionPublishesTheImageOfTheNewDirection() throws Exception {
    // 1 = downarrow, 2 = uparrow, 3 = biarrow; the direction cycles 3 -> 1 -> 2 -> 3
    String[] images = { null, "downarrow.png", "uparrow.png", "biarrow.png" };
    int dir = RequalityLCNF.getReferenceDirection();
    for (int i = 0; i < 2; i++) {
      dir = dir % 3 + 1;
      String expected = images[dir];
      execute(LINKS_DIRECTION, LINKS_VIEW);
      assertContext(imageKey(LINKS_DIRECTION), expected);
      assertEquals(dir, RequalityLCNF.getReferenceDirection());
    }
  }

  @Test
  @Order(3)
  void lightLinksButtonPublishesItsImage() throws Exception {
    // The button starts with the icon of its plugin.xml contribution; its state changes later through the handler.
    showView(LINKS_VIEW);
    assertContext(imageKey(LIGHT_LINKS), "link_break.png");
  }

  @Test
  @Order(4)
  void referenceSelectionButtonPublishesItsImage() throws Exception {
    // The button starts with the icon of its plugin.xml contribution (zerolink, no reference target).
    showView(RequalityCNF.ID);
    assertContext(imageKey(REF_SEL), "zerolink.png");
  }

  @Test
  @Order(5)
  void hideCompletedIsACheckButtonOfTheTasksView() throws Exception {
    String todo = "com.unitesk.requality.views.ToDoList";
    String cmd = "com.unitesk.requality.commands.HideCompletedItems";
    showView(todo);
    assertTrue(toolItemIds(todo).contains(cmd), toolItemIds(todo).toString());
    boolean before = com.unitesk.requality.vscode.port.views.documents.ToDoList.isHideCompleted();
    // a click on the toggle button: the CHECK tool item changes its selection, then the handler runs
    execute(cmd, todo);
    assertContext("requality.toolItem." + cmd + ".checked", String.valueOf(!before));
    assertEquals(!before, com.unitesk.requality.vscode.port.views.documents.ToDoList.isHideCompleted());
    execute(cmd, todo);
    assertContext("requality.toolItem." + cmd + ".checked", String.valueOf(before));
    assertEquals(before, com.unitesk.requality.vscode.port.views.documents.ToDoList.isHideCompleted());
  }
}
