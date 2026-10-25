package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.message.RMessages;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.port.eclipse.core.ResourceStorage;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ReviewEditor;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * The plug-in classes are the upstream ones; two defects of their upstream text are prevented by the server around
 * them: the edit mode of the Review page (set after the socket service was created) and the Change ID dialog of the
 * web editors (the case of the message consumer fell through to the file dialog). A defect of the core is patched
 * (VSCODE-PATCH): TreeLogic.canBeDeleted of the root node; a defect that the unstable core shows too:
 * AttributeStorage.getAD (removed attributes whose origin node is missing). The project lock map of ResourceStorage was unsynchronized
 * before the unstable branch; the test of its lock stays as a regression test of the upstream fix.
 */
class UpstreamDefectsTest {
  static ServerHarness h;
  static String requirement;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    String project = h.call("explorer/roots", null).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
    requirement = findItem(project, "is.Requirement");
    assertNotNull(requirement, "no requirement in the catalog");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** Breadth-first search of the explorer for an item whose context value has the tag. */
  static String findItem(String from, String tag) throws Exception {
    java.util.Deque<String> queue = new java.util.ArrayDeque<>();
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

  @Test
  void oneLockPerProjectForConcurrentThreads() throws Exception {
    IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject("UpstreamDefectsTest-lock");
    int threads = 16;
    CountDownLatch go = new CountDownLatch(1);
    List<CompletableFuture<Object>> locks = new ArrayList<>();
    for (int i = 0; i < threads; i++)
      locks.add(CompletableFuture.supplyAsync(() -> {
        try {
          go.await();
        } catch (InterruptedException e) {
          throw new IllegalStateException(e);
        }
        return ResourceStorage.getLockObject(p, UUID.randomUUID());
      }));
    go.countDown();
    Object first = locks.get(0).get(30, TimeUnit.SECONDS);
    assertNotNull(first);
    for (CompletableFuture<Object> l : locks)
      assertSame(first, l.get(30, TimeUnit.SECONDS), "every thread gets the one lock of the project");
  }

  @Test
  void theReviewPageGetsTheReadOnlyModeOfTheReviewEditor() throws Exception {
    JsonObject sel = new JsonObject();
    sel.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(requirement);
    sel.add("nodes", nodes);
    h.call("selection/set", sel);
    int before = h.notifications("ui/openShell").size();
    JsonObject ex = new JsonObject();
    ex.addProperty("command", "com.unitesk.requality.commands.ReviewEditorOpen");
    h.call("command/execute", ex);
    String shell = h.awaitPanelShell(before).get("shell").getAsString();
    ReviewEditor review = h.await2("the Review editor", () -> Display.getDefault().syncCall(() -> {
      for (IEditorReference r : PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getEditorReferences())
        if (r.getEditor(false) instanceof ReviewEditor)
          return (ReviewEditor) r.getEditor(false);
      return null;
    }));
    int expected = JSEditor.EDIT_MODE_REQ_RO | JSEditor.EDIT_MODE_TESTPURPOSE_RO | JSEditor.EDIT_MODE_TN_RO
        | JSEditor.EDIT_MODE_TESTPROCEDURE_RO | JSEditor.EDIT_MODE_TESTSTEP_RO | JSEditor.EDIT_MODE_NOT_EDIT_VIRTUAL;
    java.lang.reflect.Field soc = JSEditor.class.getDeclaredField("socservice");
    soc.setAccessible(true);
    Object service = soc.get(review);
    assertNotNull(service, "the socket service of the Review editor");
    java.lang.reflect.Field mode = service.getClass().getDeclaredField("editMode");
    mode.setAccessible(true);
    // the mode the session message of the socket service gives the page when it connects
    assertEquals(expected, mode.getInt(service));
    h.closeShell(shell);
  }

  @Test
  void theChangeIdDialogReturnsTheEnteredId() throws Exception {
    TreeNode node = Nodes.find(requirement);
    assertNotNull(node);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<Object> result = CompletableFuture.supplyAsync(() -> RMessages.openView(node.getTreeDB(),
        RMessages.ID_CHANGE_DIALOG, false, node.getSUUId(), "Change ID", "Enter the new ID", node.getId(), "false"));
    JsonObject shell = null;
    long end = System.currentTimeMillis() + 60_000;
    while (shell == null && System.currentTimeMillis() < end) {
      List<JsonObject> opened = h.notifications("ui/openShell");
      for (int i = before; i < opened.size(); i++)
        if ("Change ID".equals(opened.get(i).has("title") ? opened.get(i).get("title").getAsString() : null))
          shell = opened.get(i);
      Thread.sleep(100);
    }
    assertNotNull(shell, "the Change ID dialog");
    String sid = shell.get("shell").getAsString();
    String text = null;
    for (JsonElement e : h.call("ui/snapshot", "shell", sid).getAsJsonObject().getAsJsonArray("ops"))
      if ("text".equals(e.getAsJsonObject().has("type") ? e.getAsJsonObject().get("type").getAsString() : null))
        text = e.getAsJsonObject().get("id").getAsString();
    assertNotNull(text, "the ID field");
    JsonObject mod = new JsonObject();
    mod.addProperty("id", text);
    mod.addProperty("type", "modify");
    JsonObject d = new JsonObject();
    d.addProperty("text", "NEW_ID_1");
    d.addProperty("selStart", 8);
    d.addProperty("selEnd", 8);
    mod.add("data", d);
    h.notify("ui/event", mod);
    h.pressDefaultButton(sid);
    // the entered ID, with no file dialog after the ID dialog
    assertEquals("NEW_ID_1", result.get(60, TimeUnit.SECONDS));
    for (int i = before; i < h.notifications("ui/openShell").size(); i++)
      assertTrue(!"Open".equals(h.notifications("ui/openShell").get(i).has("title")
          ? h.notifications("ui/openShell").get(i).get("title").getAsString() : null), "no file dialog");
  }

  /**
   * The state hook of the Requality Links Explorer that clears its node (RequalityLCNF.initNewFocusCleaner) runs on the
   * UI thread, as the update of the view that reads the node: stored wrapped in the hooks of all projects, seen as added
   * by addStateHook/removeStateHook.
   */
  @Test
  void theStateHookOfTheLinksExplorerRunsOnTheUiThread() throws Exception {
    JsonObject view = new JsonObject();
    view.addProperty("view", "requality.links");
    h.call("views/open", view);
    JsonObject sel = new JsonObject();
    sel.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(requirement);
    sel.add("nodes", nodes);
    h.call("selection/set", sel);
    com.unitesk.requality.core.tree.TreeStateManager tsm = com.unitesk.requality.vscode.core.CoreBootstrap.tracker().getTreeStateManager();
    java.lang.reflect.Field f = com.unitesk.requality.core.tree.TreeStateManager.class.getDeclaredField("stateHooks");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.Map<String, java.util.Map<com.unitesk.requality.core.tree.TreeState, com.unitesk.requality.core.tree.IStateChangeHandler[]>> hooks =
        (java.util.Map<String, java.util.Map<com.unitesk.requality.core.tree.TreeState, com.unitesk.requality.core.tree.IStateChangeHandler[]>>) f.get(tsm);
    java.util.Map<com.unitesk.requality.core.tree.TreeState, com.unitesk.requality.core.tree.IStateChangeHandler[]> all = hooks.get("*");
    assertTrue(all instanceof com.unitesk.requality.vscode.core.UiStateHooks, String.valueOf(all));
    h.await("the focus cleaner of the Links Explorer", v -> {
      for (java.util.Map.Entry<com.unitesk.requality.core.tree.TreeState, com.unitesk.requality.core.tree.IStateChangeHandler[]> e : all.entrySet())
        if (e.getKey() == com.unitesk.requality.core.tree.TreeState.HALTED)
          for (com.unitesk.requality.core.tree.IStateChangeHandler stored : e.getValue())
            if (stored instanceof com.unitesk.requality.vscode.core.UiStateHooks.OnUiThread)
              return true;
      return false;
    });
    // addStateHook/removeStateHook see the handler of the view itself
    boolean original = false;
    for (com.unitesk.requality.core.tree.IStateChangeHandler added : all.get(com.unitesk.requality.core.tree.TreeState.HALTED))
      if (added.getClass().getName().startsWith("com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF$"))
        original = true;
    assertTrue(original, "the hook as the view added it");
    // a wrapped hook runs on the UI thread, whatever thread changes the state
    java.util.concurrent.CompletableFuture<Thread> ran = new java.util.concurrent.CompletableFuture<>();
    new com.unitesk.requality.vscode.core.UiStateHooks.OnUiThread(new com.unitesk.requality.core.tree.AAllTreeStateChangeHandler() {
      @Override
      public void stateChanges(com.unitesk.requality.core.model.TreeDB db, String lname, String pname,
          com.unitesk.requality.repo.RevisionInfo ri, com.unitesk.requality.core.tree.TreeState oldState,
          com.unitesk.requality.core.tree.TreeState state) {
        ran.complete(Thread.currentThread());
      }
    }).stateChanges(null, "x", "x", null, com.unitesk.requality.core.tree.TreeState.READY, com.unitesk.requality.core.tree.TreeState.HALTED);
    assertSame(Display.getDefault().getThread(), ran.get(30, TimeUnit.SECONDS));
  }

  /**
   * A project selected in the explorer is its root node in the explorer's selection record (RequalityCNF); the node
   * delete handler of the plug-in (DeleteSelection) asks the core whether it can be deleted: it cannot, and the core
   * does not fail on the parent the root does not have (TreeLogic.canBeDeleted, VSCODE-PATCH).
   */
  @Test
  void theRootNodeOfASelectedProjectCannotBeDeleted() throws Exception {
    String project = h.call("explorer/roots", null).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
    JsonObject sel = new JsonObject();
    sel.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(project);
    sel.add("nodes", nodes);
    h.call("selection/set", sel);
    TreeNode root = h.await2("the root node in the explorer's selection", () -> {
      com.unitesk.requality.core.selection.SelectionRecord r = com.unitesk.requality.core.selection.SelectionManager.getOne()
          .getSelection(com.unitesk.requality.core.selection.EventSource.EXPLORER);
      return r != null && r.fullSelection.size() == 1 && r.fullSelection.iterator().next().isRoot() ? r.fullSelection.iterator().next() : null;
    });
    assertEquals(false, root.getTreeDB().getTreeLogic().canBeDeleted(root));
    assertEquals(false, Display.getDefault().syncCall(() -> new com.unitesk.requality.vscode.port.eclipse.handlers.DeleteSelection().isEnabled()));
  }

  /**
   * The ETSI catalog has an old storage format: the core converts it when the project opens and tells the result in a
   * message. 22 requirements of the catalog have a _type whose origin node is not in the catalog. AttributeStorage.getAD
   * removed such an attribute from the attributes that the storage caches; the unstable core then loads the node
   * without a type and the conversion stopped on a NullPointerException in ResourceStorage.saveStorageAttributes, which
   * the message showed. With the VSCODE-PATCH the stored value stays.
   */
  @Test
  void theFormatConversionCompletesWithoutAnError() throws Exception {
    JsonObject done = h.await2("the message of the format conversion", () -> {
      for (JsonObject m : h.requests("client/showMessage"))
        if (m.has("message") && m.get("message").getAsString().contains("to new format was completed"))
          return m;
      return null;
    });
    assertTrue(!done.get("message").getAsString().contains("Exception"), done.toString());
  }
}
