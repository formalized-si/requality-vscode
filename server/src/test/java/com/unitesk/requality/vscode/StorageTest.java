package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.core.Workspace;

/**
 * Node persistence through the ported ResourceStorage on the ETSI catalog: copy and paste, delete, undo and redo,
 * and the tree read back from disk after the project tree is closed and loaded again. The files under the project
 * folder are the ground truth.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageTest {
  static ServerHarness h;
  static File project;
  static String projectItem;
  /** A leaf requirement that is copied. */
  static String source;
  /** The node the copy is pasted into. */
  static String target;
  /** The pasted node. */
  static String pasted;
  /** Project-relative path of the file of the pasted node, and its content. */
  static String pastedFile;
  static JsonObject pastedJson;
  static JsonObject sourceJson;
  static String sourceFile;
  static Set<String> initialFiles;
  static List<String> initialTargetChildren;
  static Set<String> redoFiles;

  static final String COPY = "com.unitesk.requality.commands.CopyNode";
  static final String PASTE = "com.unitesk.requality.commands.PasteIntoNode";
  static final String DELETE = "org.eclipse.ui.edit.delete";
  static final String UNDO = "org.eclipse.ui.edit.undo";
  static final String REDO = "org.eclipse.ui.edit.redo";

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    project = new File(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    projectItem = h.call("explorer/roots", null).getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString();
    source = findItem(projectItem, "is.Requirement", false, null);
    assertNotNull(source, "no leaf requirement in the catalog");
    target = findItem(projectItem, "is.Requirement", true, source);
    assertNotNull(target, "no requirement folder in the catalog");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  @Test
  @Order(1)
  void copyAndPasteWritesTheNodeToDisk() throws Exception {
    initialFiles = jsonFiles();
    sourceFile = fileOf(uuidOf(source));
    assertNotNull(sourceFile, "no file of the source node");
    sourceJson = jsonOf(sourceFile);
    initialTargetChildren = childIds(target);

    run(COPY, source);
    run(PASTE, target);

    // The new node is in the explorer under the target and its file is in the project folder.
    h.await("the pasted node on disk and in the tree", v -> {
      Set<String> added = added(initialFiles);
      return added.size() == 1 && newChildren(initialTargetChildren).size() == 1 && tryJson(added.iterator().next()) != null;
    });
    Set<String> added = added(initialFiles);
    assertEquals(1, added.size(), added.toString());
    pastedFile = added.iterator().next();
    pastedJson = jsonOf(pastedFile);
    pasted = newChildren(initialTargetChildren).get(0);

    // Same attribute values as the source; the identity of the node is its own.
    assertEquals(attributes(sourceJson), attributes(pastedJson));
    assertEquals(uuidOf(pasted), pastedJson.get("uuid").getAsString());
    assertNotEquals(sourceJson.get("uuid").getAsString(), pastedJson.get("uuid").getAsString());
    assertNotNull(Nodes.find(pasted));
  }

  @Test
  @Order(2)
  void deleteRemovesTheFileAndTheNode() throws Exception {
    // The delete confirmation (a panel) is answered with its default button, as the user would.
    int before = h.notifications("ui/openShell").size();
    run(DELETE, pasted);
    JsonObject confirm = h.awaitPanelShell(before);
    assertTrue(confirm.get("title").getAsString().startsWith("Delete"), confirm.toString());
    h.pressDefaultButton(confirm.get("shell").getAsString());

    h.await("the file to be deleted", v -> !Files.exists(project.toPath().resolve(pastedFile)));
    // The children folder of the node (named after the file) goes with it.
    assertFalse(Files.exists(project.toPath().resolve(pastedFile.replaceAll("\\.json$", ""))));
    h.await("the node to leave the tree", v -> Nodes.find(pasted) == null);
    assertFalse(childIds(target).contains(pasted));
    assertEquals(initialTargetChildren, childIds(target));
  }

  @Test
  @Order(3)
  void undoRestoresTheNodeAndRedoRemovesItAgain() throws Exception {
    awaitUndo("Delete");
    run(UNDO);
    h.await("the file to be restored", v -> tryJson(pastedFile) != null);
    h.await("the node to be back in the tree", v -> Nodes.find(pasted) != null && childIds(target).contains(pasted));
    assertEquals(pastedJson, jsonOf(pastedFile));

    run(REDO);
    h.await("the file to be removed again", v -> !Files.exists(project.toPath().resolve(pastedFile)));
    h.await("the node to leave the tree again", v -> Nodes.find(pasted) == null);
    assertFalse(childIds(target).contains(pasted));
    redoFiles = jsonFiles();
    assertEquals(initialFiles, redoFiles);
  }

  @Test
  @Order(5)
  void treeReadBackFromDiskMatchesTheRedoState() throws Exception {
    // Close the tree of the project (as IProject.close does) and load it again from its folder.
    Workspace ws = h.server().workspace();
    ws.closeTree(project);
    h.await("the tree to be closed", v -> Nodes.find(source) == null);
    assertTrue(ws.loadProject(project));
    h.awaitProjects(1);
    h.await("the tree to be loaded", v -> Nodes.find(source) != null);

    // The state on disk is the redo state: the pasted node is absent, the requirement is unchanged.
    assertEquals(redoFiles, jsonFiles());
    assertNull(Nodes.find(pasted));
    assertEquals(initialTargetChildren, childIds(target));
    assertEquals(sourceJson, jsonOf(sourceFile));
    assertEquals(attributes(sourceJson), attributes(jsonOf(fileOf(uuidOf(source)))));
  }

  @Test
  @Order(6)
  void nodesMapToTheirFilesAndTheCheckersMarkThem() throws Exception {
    // EclipseTracker.getResourceByNode / getNodeByResource through the ResourceStorage of the tree.
    com.unitesk.requality.core.model.TreeNode n = Nodes.find(source);
    com.unitesk.requality.vscode.ui.resources.IFile f = com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker
        .getResourceByNode(n, com.unitesk.requality.core.lock.LockType.NO_LOCK);
    assertNotNull(f);
    assertEquals(new File(project, sourceFile).getCanonicalPath(), f.getLocation().toFile().getCanonicalPath());
    assertEquals(n, com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker
        .getNodeByResource(com.unitesk.requality.core.storage.DBNamesManager.DEFAULT, f));
    // The problems the checkers find for a node become problem markers of its file (EclipseCheckerContainer
    // .registerProblems: markers on the resource of the node, published to the Problems view).
    com.unitesk.requality.core.checker.CheckerContainer cc = n.getTreeDB().getCheckerContainer();
    assertTrue(cc instanceof com.unitesk.requality.vscode.port.eclipse.core.EclipseCheckerContainer, cc.getClass().getName());
    cc.registerProblems(n, new com.unitesk.requality.core.checker.IMark[] {
        new com.unitesk.requality.core.checker.CheckerProblem(n, "test_problem", "A test problem", new String[0]) });
    h.await("the problem marker of the node file", v -> {
      for (JsonObject p : h.notifications("problems/set"))
        for (JsonElement e : p.getAsJsonArray("files")) {
          JsonObject file = e.getAsJsonObject();
          if (file.get("uri").getAsString().equals(new File(project, sourceFile).toURI().toString())
              && file.getAsJsonArray("problems").toString().contains("A test problem"))
            return true;
        }
      return false;
    });
    // no problems: the markers are removed
    int before = h.notifications("problems/set").size();
    cc.registerProblems(n, new com.unitesk.requality.core.checker.IMark[0]);
    h.await("the marker removed", v -> {
      List<JsonObject> l = h.notifications("problems/set");
      for (int i = before; i < l.size(); i++)
        for (JsonElement e : l.get(i).getAsJsonArray("files"))
          if (e.getAsJsonObject().get("uri").getAsString().equals(new File(project, sourceFile).toURI().toString())
              && e.getAsJsonObject().getAsJsonArray("problems").size() == 0)
            return true;
      return false;
    });
  }

  // Before the reload of the tree: closing a tree clears its caches and, as in Eclipse (EclipseAPI.clearCaches), the
  // workbench undo history, which can happen while a later operation is checked.
  @Test
  @Order(4)
  void droppingANodeOnAnotherMovesItsFiles() throws Exception {
    // Drag and drop in the explorer: CNFDropAssistant (plugin.xml dropAssistant) moves the node into the target;
    // ResourceStorage moves its file into the folder of the target.
    String oldParent = Nodes.id(Nodes.find(source).getWorkParent());
    String oldFile = fileOf(uuidOf(source));
    JsonObject p = new JsonObject();
    JsonArray src = new JsonArray();
    src.add(source);
    p.add("sources", src);
    p.addProperty("target", target);
    p.addProperty("operation", "move");
    assertTrue(h.call("explorer/drop", p).getAsBoolean());
    h.await("the node under the target", v -> childIds(target).contains(source));
    h.await("the file moved", v -> {
      try {
        String f = fileOf(uuidOf(source));
        return f != null && !f.equals(oldFile) && !Files.exists(project.toPath().resolve(oldFile));
      } catch (IOException e) {
        return false;
      }
    });
    String movedFile = fileOf(uuidOf(source));
    String targetFile = fileOf(uuidOf(target));
    assertTrue(movedFile.startsWith(targetFile.replaceAll("\\.json$", "") + File.separator), movedFile + " under " + targetFile);
    // Undo moves it back (once the move is the operation to undo: it reaches the history after its files).
    awaitUndo("Move");
    run(UNDO);
    h.await("the node back", v -> childIds(oldParent).contains(source) && !childIds(target).contains(source));
    h.await("the file back", v -> Files.exists(project.toPath().resolve(oldFile)));
  }

  // ---- helpers ----

  /** Waits until the operation Undo would revert is the one named (its label contains the text). */
  static void awaitUndo(String labelPart) throws Exception {
    try {
      awaitUndoLabel(labelPart);
    } catch (AssertionError e) {
      com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory oh = com.unitesk.requality.vscode.ui.workbench.PlatformUI
          .getWorkbench().getOperationSupport().getOperationHistory();
      com.unitesk.requality.vscode.ui.commands.operations.IUndoContext ctx = com.unitesk.requality.vscode.ui.workbench.PlatformUI
          .getWorkbench().getOperationSupport().getUndoContext();
      StringBuilder sb = new StringBuilder();
      for (com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation op : oh.getUndoHistory(ctx))
        sb.append(op.getLabel()).append(op.canUndo() ? "" : " (cannot undo)").append("; ");
      throw new AssertionError(e.getMessage() + "; undo history: " + sb, e);
    }
  }

  static void awaitUndoLabel(String labelPart) throws Exception {
    h.await("Undo of " + labelPart, v -> {
      try {
        JsonObject p = new JsonObject();
        JsonArray cmds = new JsonArray();
        cmds.add(UNDO);
        p.add("commands", cmds);
        JsonObject st = h.call("command/state", p).getAsJsonObject().getAsJsonObject(UNDO);
        return st.get("enabled").getAsBoolean() && st.get("label").getAsString().contains(labelPart);
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** Runs an Eclipse command on the given explorer nodes (the selection), or on the current selection. */
  static void run(String command, String... nodes) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    if (nodes.length > 0) {
      JsonArray a = new JsonArray();
      for (String n : nodes)
        a.add(n);
      p.add("nodes", a);
    }
    h.call("command/execute", p);
  }

  /** Breadth-first search of the explorer for an item with the context value tag, leaf or not, other than exclude. */
  static String findItem(String from, String tag, boolean withChildren, String exclude) throws Exception {
    Deque<String> queue = new ArrayDeque<>();
    queue.add(from);
    int visited = 0;
    while (!queue.isEmpty() && visited < 400) {
      String id = queue.poll();
      visited++;
      for (JsonElement e : h.children(id)) {
        JsonObject it = e.getAsJsonObject();
        String cv = it.has("contextValue") ? it.get("contextValue").getAsString() : "";
        String iid = it.get("id").getAsString();
        boolean children = it.has("hasChildren") && it.get("hasChildren").getAsBoolean();
        if ((" " + cv + " ").contains(" " + tag + " ") && children == withChildren && !iid.equals(exclude))
          return iid;
        if (children)
          queue.add(iid);
      }
    }
    return null;
  }

  /** Ids of the explorer children of a node (unchecked, so that it can be used in a wait). */
  static List<String> childIds(String parent) {
    try {
      List<String> ids = new ArrayList<>();
      for (JsonElement e : h.children(parent))
        ids.add(e.getAsJsonObject().get("id").getAsString());
      return ids;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Children of the target in the explorer that were not there before. */
  static List<String> newChildren(List<String> before) {
    List<String> r = new ArrayList<>();
    for (String id : childIds(target))
      if (!before.contains(id))
        r.add(id);
    return r;
  }

  static String uuidOf(String nodeId) {
    return nodeId.substring(nodeId.lastIndexOf(Nodes.SEP) + Nodes.SEP.length());
  }

  /**
   * Every .json file under the project folder, relative to it. The walk skips the cache folder of the project: the
   * database there makes and removes its journal file while the walk runs.
   */
  static Set<String> jsonFiles() {
    Path root = project.toPath();
    Set<String> r = new TreeSet<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          return dir.getFileName().toString().equals(".reqcache") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
          if (attrs.isRegularFile() && f.toString().endsWith(".json"))
            r.add(root.relativize(f).toString());
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path f, IOException e) throws IOException {
          // a temporary file of a save removed after the listing of its folder
          if (e instanceof NoSuchFileException)
            return FileVisitResult.CONTINUE;
          throw e;
        }
      });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return r;
  }

  /** The node files that were not there before. */
  static Set<String> added(Set<String> before) {
    Set<String> r = jsonFiles();
    r.removeAll(before);
    return r;
  }

  /** The node file (a JSON with the uuid) of a node, project-relative; null when not found. */
  static String fileOf(String uuid) throws IOException {
    for (String rel : jsonFiles()) {
      JsonObject o = tryJson(rel);
      if (o != null && o.has("uuid") && uuid.equals(o.get("uuid").getAsString()))
        return rel;
    }
    return null;
  }

  static JsonObject jsonOf(String rel) throws IOException {
    return JsonParser.parseString(Files.readString(project.toPath().resolve(rel))).getAsJsonObject();
  }

  /** The parsed node file, or null while it does not exist or is still being written. */
  static JsonObject tryJson(String rel) {
    try {
      return jsonOf(rel);
    } catch (Exception e) {
      return null;
    }
  }

  /** Attribute name -> type and value, the content of a node file that a copy must keep. */
  static Map<String, String> attributes(JsonObject node) {
    Map<String, String> m = new TreeMap<>();
    for (Map.Entry<String, JsonElement> e : node.getAsJsonObject("attributes").entrySet()) {
      JsonObject a = e.getValue().getAsJsonObject();
      m.put(e.getKey(), a.get("type") + " " + a.get("value"));
    }
    return m;
  }
}
