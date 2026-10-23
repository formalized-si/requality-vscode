package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import com.unitesk.requality.vscode.services.ExplorerService;

/**
 * The Requality Explorer as the Eclipse view: the SWT tree of RequalityCNF in its shell, hosted by the VS Code view
 * requality.explorerView, with the labels and images of the explorer, its expansion, the open of a node on a double
 * click, the plug-in's popup on the tree and its selection sent to the extension.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EclipseExplorerViewTest {
  static ServerHarness h;
  static String shell;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initializeWith("eclipse", h.workspace);
    h.awaitProjects(1);
    h.await("the shell of the Eclipse view of the explorer", v -> viewShell() != null);
    shell = viewShell();
    // the view has the focus (a click in its webview): the explorer is the active part
    JsonObject f = new JsonObject();
    f.addProperty("shell", shell);
    h.notify("ui/shellFocused", f);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String viewShell() {
    String s = null;
    for (JsonObject n : h.notifications("ui/openShell"))
      if ("view".equals(n.has("host") ? n.get("host").getAsString() : null)
          && ExplorerService.RENDERED_VIEW.equals(n.has("view") ? n.get("view").getAsString() : null))
        s = n.get("shell").getAsString();
    return s;
  }

  static JsonArray ops() throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  static String tree() throws Exception {
    for (JsonElement e : ops())
      if ("tree".equals(e.getAsJsonObject().has("type") ? e.getAsJsonObject().get("type").getAsString() : null))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The tree items: {id, first text, image, parent}. */
  static List<String[]> items() throws Exception {
    List<String[]> r = new ArrayList<>();
    for (JsonElement e : ops()) {
      JsonObject o = e.getAsJsonObject();
      if (!"treeitem".equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject p = o.getAsJsonObject("props");
      String text = p.has("texts") && p.getAsJsonArray("texts").size() > 0 ? p.getAsJsonArray("texts").get(0).getAsString()
          : p.has("text") ? p.get("text").getAsString() : "";
      String image = p.has("images") && p.getAsJsonArray("images").size() > 0 && !p.getAsJsonArray("images").get(0).isJsonNull()
          ? p.getAsJsonArray("images").get(0).getAsString() : p.has("image") && !p.get("image").isJsonNull() ? p.get("image").getAsString() : null;
      r.add(new String[] { o.get("id").getAsString(), text, image,
          o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null });
    }
    return r;
  }

  static String[] item(String textPart) throws Exception {
    for (String[] it : items())
      if (it[1].contains(textPart))
        return it;
    return null;
  }

  static void event(String id, String type, JsonObject data) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    p.add("data", data == null ? new JsonObject() : data);
    h.notify("ui/event", p);
  }

  @Test
  @Order(1)
  void theTreeShowsTheProjectWithItsImage() throws Exception {
    h.await("the project in the tree", v -> {
      try {
        return item("TS103096") != null;
      } catch (Exception e) {
        return false;
      }
    });
    String[] project = item("TS103096");
    assertNotNull(project[2], "the image of the project");
    assertTrue(project[2].endsWith(".png"), project[2]);
  }

  @Test
  @Order(2)
  void expandingTheProjectShowsItsNodes() throws Exception {
    String[] project = item("TS103096");
    JsonObject d = new JsonObject();
    d.addProperty("item", project[0]);
    event(tree(), "expand", d);
    // the project may still be loading: its item, expanded, gets the nodes when the project is loaded (the items are
    // reused by position, as JFace does: the item of the project is looked up again)
    String[] loaded = h.await2("the nodes of the project", () -> {
      String[] p = item("TS103096");
      if (p == null)
        return null;
      for (String[] it : items())
        if (p[0].equals(it[3]) && it[1].contains("Documents"))
          return p;
      // expanded while it was loading (no children yet), the item was collapsed: it is expanded again, as a user does
      if (!expanded(p[0])) {
        JsonObject again = new JsonObject();
        again.addProperty("item", p[0]);
        event(tree(), "expand", again);
      }
      return null;
    });
    List<String> children = new ArrayList<>();
    for (String[] it : items())
      if (loaded[0].equals(it[3])) {
        children.add(it[1]);
        assertNotNull(it[2], "the image of " + it[1]);
      }
    assertTrue(children.stream().anyMatch(c -> c.contains("Requirements")), children.toString());
    assertTrue(children.stream().anyMatch(c -> c.contains("Documents")), children.toString());
  }

  @Test
  @Order(3)
  void selectingANodeSendsTheSelectionToTheExtension() throws Exception {
    String[] req = item("Requirements");
    int before = h.notifications("explorer/selection").size();
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(req[0]);
    d.add("selection", sel);
    d.addProperty("item", req[0]);
    event(tree(), "selection", d);
    h.await("the selection sent", v -> h.notifications("explorer/selection").size() > before);
    JsonObject last = h.notifications("explorer/selection").get(h.notifications("explorer/selection").size() - 1);
    assertEquals(1, last.getAsJsonArray("items").size(), last.toString());
    assertEquals(1, last.getAsJsonArray("nodes").size(), last.toString());
  }

  @Test
  @Order(4)
  void theContextMenuIsThePopupOfThePlugIn() throws Exception {
    JsonObject xy = new JsonObject();
    xy.addProperty("x", 0);
    xy.addProperty("y", 0);
    event(tree(), "menudetect", xy);
    // the popup of the explorer for the selected node (the action providers and the plugin.xml contributions)
    try {
      h.await("the popup of the explorer", v -> {
        try {
          return ops().toString().contains("Copy Full Name");
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      StringBuilder sb = new StringBuilder();
      for (JsonElement o : ops())
        if (o.getAsJsonObject().has("type") && o.getAsJsonObject().get("type").getAsString().startsWith("menu"))
          sb.append(o.getAsJsonObject().get("props")).append(" | ");
      throw new AssertionError(e.getMessage() + "; menus: " + sb, e);
    }
    String snap = ops().toString();
    for (String label : new String[] { "Copy Full Name" })
      assertTrue(snap.contains(label), label + " in the popup");
    // the New menu of plugin.xml (its own items: the dynamic NewMenuPoint) with the node types creatable in the folder
    String newMenu = null;
    for (JsonElement e : ops()) {
      JsonObject o = e.getAsJsonObject();
      if ("menuitem".equals(o.get("type").getAsString()) && o.getAsJsonObject("props").has("text")
          && o.getAsJsonObject("props").get("text").getAsString().replace("&", "").equals("New"))
        newMenu = o.get("id").getAsString();
    }
    assertNotNull(newMenu, "the New menu in " + snap);
    String newItems = null;
    for (JsonElement e : ops()) {
      JsonObject o = e.getAsJsonObject();
      if ("menu".equals(o.get("type").getAsString()) && newMenu.equals(parentOf(o)))
        newItems = o.get("id").getAsString();
    }
    StringBuilder types = new StringBuilder();
    for (JsonElement e : ops()) {
      JsonObject o = e.getAsJsonObject();
      if (newItems != null && newItems.equals(parentOf(o))
          && o.getAsJsonObject("props").has("text"))
        types.append(o.getAsJsonObject("props").get("text").getAsString()).append(';');
    }
    assertTrue(types.toString().contains("Requirement"), types.toString());
    // as JFace shows a menu: no separator at the start, at the end or after another separator
    for (JsonElement e : ops()) {
      JsonObject menu = e.getAsJsonObject();
      if (!"menu".equals(menu.get("type").getAsString()))
        continue;
      List<Boolean> separators = new ArrayList<>();
      for (JsonElement i : ops()) {
        JsonObject o = i.getAsJsonObject();
        if ("menuitem".equals(o.get("type").getAsString()) && menu.get("id").getAsString().equals(parentOf(o)))
          separators.add((o.get("style").getAsInt() & com.unitesk.requality.vscode.ui.swt.SWT.SEPARATOR) != 0);
      }
      String where = "the separators of menu " + menu.get("id") + ": " + separators;
      if (separators.isEmpty())
        continue;
      assertTrue(!separators.get(0) && !separators.get(separators.size() - 1), where);
      for (int k = 1; k < separators.size(); k++)
        assertTrue(!(separators.get(k) && separators.get(k - 1)), where);
    }
  }

  @Test
  @Order(5)
  void aDoubleClickOnADocumentOpensTheDocumentEditor() throws Exception {
    // CNFActionProvider's OPEN action (OpenAction -> OpenNode) on the open of a double click: the Document Editor
    String[] folder = item("Documents");
    JsonObject d = new JsonObject();
    d.addProperty("item", folder[0]);
    event(tree(), "expand", d);
    String[] doc = h.await2("a document in the tree", () -> {
      for (String[] it : items())
        if (folder[0].equals(it[3]))
          return it;
      return null;
    });
    int before = h.notifications("ui/openShell").size();
    JsonObject sel = new JsonObject();
    JsonArray s = new JsonArray();
    s.add(doc[0]);
    sel.add("selection", s);
    sel.addProperty("item", doc[0]);
    event(tree(), "selection", sel);
    event(tree(), "default", sel);
    JsonObject editor = h.awaitPanelShell(before);
    String url = FoundationTest.browserUrl(h, editor.get("shell").getAsString());
    assertTrue(url.contains("/documents/"), url);
    h.closeShell(editor.get("shell").getAsString());
  }

  static boolean expanded(String itemId) throws Exception {
    for (JsonElement e : ops()) {
      JsonObject o = e.getAsJsonObject();
      if (itemId.equals(o.get("id").getAsString()))
        return o.getAsJsonObject("props").has("expanded") && o.getAsJsonObject("props").get("expanded").getAsBoolean();
    }
    return false;
  }

  /**
   * A refresh of the explorer keeps the items of the elements it shows, expanded as they were (the items of JFace
   * AbstractTreeViewer.updateChildren); Collapse All of the view toolbar collapses them.
   */
  @Test
  @Order(7)
  void aRefreshKeepsTheItemsAndCollapseAllCollapsesThem() throws Exception {
    String[] project = item("TS103096");
    String[] requirements = item("Requirements");
    assertTrue(expanded(project[0]), "the project expanded by the tests before");
    int before = h.notifications("explorer/refresh").size();
    ExplorerService.refreshAllNodes();
    h.await("the refresh of the explorer", v -> h.notifications("explorer/refresh").size() > before);
    // the tree is refreshed on the UI thread after the notification: a later runnable runs after it
    Thread.sleep(200);
    com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncExec(() -> {
    });
    assertEquals(project[0], item("TS103096")[0], "the item of the project");
    assertEquals(requirements[0], item("Requirements")[0], "the item of the requirements");
    assertEquals(project[0], item("Requirements")[3], "the parent of the requirements");
    assertTrue(expanded(project[0]), "the project still expanded");
    h.call("explorer/collapseAll", null);
    assertTrue(!expanded(project[0]), "the project collapsed");
    assertTrue(!expanded(requirements[0]), "the requirements collapsed");
  }

  /** The VS Code view of the Eclipse view shows the part of the explorer (views/open when the view is resolved). */
  @Test
  @Order(8)
  void theVsCodeViewOpensTheExplorerPart() throws Exception {
    JsonObject view = new JsonObject();
    view.addProperty("view", ExplorerService.RENDERED_VIEW);
    h.call("views/open", view);
    assertEquals(com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID,
        com.unitesk.requality.vscode.ui.workbench.Workbench.viewOfHost(ExplorerService.RENDERED_VIEW));
  }

  /**
   * A click in the tree activates the explorer part (in Eclipse the pressed control gets the focus), also when its
   * webview reported no focus: then the plug-in's popup and the open of a double click are those of the explorer.
   */
  @Test
  @Order(9)
  void aClickInTheTreeActivatesTheExplorerPart() throws Exception {
    JsonObject view = new JsonObject();
    view.addProperty("view", "requality.links");
    h.call("views/open", view);
    String[] requirements = item("Requirements");
    Object node = elementOf(requirements[0]);
    JsonObject links = new JsonObject();
    links.addProperty("source", "links");
    JsonArray nodes = new JsonArray();
    nodes.add(com.unitesk.requality.vscode.core.Nodes.id((com.unitesk.requality.core.model.TreeNode) node));
    links.add("nodes", nodes);
    h.call("selection/set", links);
    h.await("the Links Explorer active", v -> !(activePart() instanceof com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF));
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(requirements[0]);
    d.add("selection", sel);
    d.addProperty("item", requirements[0]);
    event(tree(), "selection", d);
    h.await("the explorer active", v -> activePart() instanceof com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF);
  }

  static Object activePart() {
    return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncCall(
        com.unitesk.requality.vscode.ui.workbench.Workbench::activePart);
  }

  static String parentOf(JsonObject op) {
    return op.has("parent") && !op.get("parent").isJsonNull() ? op.get("parent").getAsString() : null;
  }

  /** The element of a tree item (the data of the TreeItem). */
  static Object elementOf(String itemId) {
    return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncCall(() -> {
      com.unitesk.requality.vscode.ui.swt.widgets.Widget w = com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().find(itemId);
      return w == null ? null : w.getData();
    });
  }

  @Test
  @Order(6)
  void draggingARequirementOntoAnotherMovesItThere() throws Exception {
    // two requirements of one folder: the leaf is dropped on the other one, which then contains it (CNFDropAssistant)
    String[] folder = item("Requirements");
    String[] parent = null;
    String[] leaf = null;
    String[] target = null;
    // descend to a folder whose children have a leaf and another requirement
    String current = folder[0];
    for (int depth = 0; depth < 8 && (leaf == null || target == null); depth++) {
      String c = current;
      JsonObject ex = new JsonObject();
      ex.addProperty("item", c);
      event(tree(), "expand", ex);
      h.await("the children of " + c, v -> {
        try {
          for (String[] it : items())
            if (c.equals(it[3]))
              return true;
          return false;
        } catch (Exception e) {
          return false;
        }
      });
      List<String[]> children = new ArrayList<>();
      for (String[] it : items())
        if (c.equals(it[3]))
          children.add(it);
      leaf = null;
      target = null;
      for (String[] it : children) {
        Object el = elementOf(it[0]);
        boolean req = el instanceof com.unitesk.requality.nodetype.Requirement;
        if (req && leaf == null && !hasChildren(it[0]))
          leaf = it;
        else if (req && target == null)
          target = it;
      }
      parent = children.isEmpty() ? null : children.get(0);
      if (leaf == null || target == null) {
        String next = null;
        for (String[] it : children)
          if (hasChildren(it[0]))
            next = it[0];
        if (next == null)
          break;
        current = next;
      }
    }
    assertNotNull(leaf, "a leaf requirement");
    assertNotNull(target, "another requirement");
    com.unitesk.requality.core.model.TreeNode source = (com.unitesk.requality.core.model.TreeNode) elementOf(leaf[0]);
    com.unitesk.requality.core.model.TreeNode into = (com.unitesk.requality.core.model.TreeNode) elementOf(target[0]);
    // a drag of the leaf (its row is selected at the start of the drag), dropped on the other requirement
    JsonObject sel = new JsonObject();
    JsonArray s = new JsonArray();
    s.add(leaf[0]);
    sel.add("selection", s);
    sel.addProperty("item", leaf[0]);
    event(tree(), "selection", sel);
    event(tree(), "dragstart", null);
    JsonObject drop = new JsonObject();
    drop.addProperty("source", tree());
    drop.addProperty("item", target[0]);
    drop.addProperty("stateMask", 0);
    event(tree(), "drop", drop);
    h.await("the requirement moved into the other one", v -> {
      com.unitesk.requality.core.model.TreeNode n = com.unitesk.requality.vscode.core.Nodes.find(com.unitesk.requality.vscode.core.Nodes.id(source));
      return n != null && n.getWorkParent() != null && n.getWorkParent().getUUId().equals(into.getUUId());
    });
  }

  static String labels(JsonElement items) {
    StringBuilder sb = new StringBuilder();
    for (JsonElement e : items.getAsJsonArray()) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label"))
        sb.append(o.get("label").getAsString()).append(o.has("enabled") && !o.get("enabled").getAsBoolean() ? "(off)" : "").append(", ");
      if (o.has("children"))
        sb.append("[").append(labels(o.get("children"))).append("] ");
    }
    return sb.toString();
  }

  static boolean hasChildren(String item) throws Exception {
    for (JsonElement e : ops()) {
      JsonObject o = e.getAsJsonObject();
      if (item.equals(o.get("id").getAsString()))
        return o.getAsJsonObject("props").has("hasChildren") && o.getAsJsonObject("props").get("hasChildren").getAsBoolean();
    }
    return false;
  }
}
