package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareEditor;
import com.unitesk.requality.vscode.ui.compare.CompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.ResourceNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The compare framework (org.eclipse.compare: CompareEditorInput, Differencer, CompareUI) and its VS Code mapping: a
 * text comparison opens the VS Code diff editor, a structure comparison opens the compare editor with the tree of
 * differences and the text compare of the selected one, no differences are reported, contents that are not files are
 * served as documents of the requality-compare scheme.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompareFrameworkTest {
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "plain");
    new File(plain, "a").mkdirs();
    new File(plain, "b").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "a/same.txt").toPath(), "same\n");
    Files.writeString(new File(plain, "b/same.txt").toPath(), "same\n");
    Files.writeString(new File(plain, "a/changed.txt").toPath(), "one\ntwo\nthree\n");
    Files.writeString(new File(plain, "b/changed.txt").toPath(), "one\n2\nthree\n");
    Files.writeString(new File(plain, "a/only-left.txt").toPath(), "left\n");
    h.initialize(h.workspace);
    h.awaitProjects(0);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static IResource resource(String path) {
    return ResourcesPlugin.getWorkspace().getRoot().findMember(new Path("/plain/" + path));
  }

  /** A compare editor input of two resources compared by the Differencer (as ResourceCompareInput does). */
  static CompareEditorInput input(String left, String right) {
    CompareConfiguration cc = new CompareConfiguration();
    cc.setLeftLabel(left);
    cc.setRightLabel(right);
    CompareEditorInput input = new CompareEditorInput(cc) {
      @Override
      protected Object prepareInput(IProgressMonitor monitor) {
        return new Differencer().findDifferences(false, monitor, null, null, new ResourceNode(resource(left)),
            new ResourceNode(resource(right)));
      }
    };
    input.setTitle("Compare ('" + left + "' - '" + right + "')");
    return input;
  }

  static void open(CompareEditorInput input) {
    Display.getDefault().asyncExec(() -> CompareUI.openCompareEditor(input));
  }

  @Test
  @Order(1)
  void twoTextFilesOpenTheVsCodeDiffEditor() throws Exception {
    int before = h.notifications("client/openDiff").size();
    open(input("a/changed.txt", "b/changed.txt"));
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > before);
    JsonObject p = h.notifications("client/openDiff").get(before);
    assertEquals("Compare ('a/changed.txt' - 'b/changed.txt')", p.get("title").getAsString());
    assertEquals(new File(plain, "a/changed.txt").getAbsolutePath(), p.getAsJsonObject("left").get("path").getAsString());
    assertEquals(new File(plain, "b/changed.txt").getAbsolutePath(), p.getAsJsonObject("right").get("path").getAsString());
    assertEquals("a/changed.txt", p.getAsJsonObject("left").get("label").getAsString());
  }

  @Test
  @Order(2)
  void equalFilesHaveNoDifferences() throws Exception {
    int before = h.clientRequests.size();
    open(input("a/same.txt", "b/same.txt"));
    h.await("the no differences message", v -> {
      for (int k = before; k < h.clientRequests.size(); k++)
        if (h.clientRequests.get(k).getKey().equals("client/showMessage")
            && h.clientRequests.get(k).getValue().toString().contains("There are no differences between the selected inputs."))
          return true;
      return false;
    });
  }

  @Test
  @Order(3)
  void twoFoldersOpenTheStructureCompare() throws Exception {
    int before = h.notifications("ui/openShell").size();
    open(input("a", "b"));
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Compare ('a' - 'b')", shell.get("title").getAsString());
    String sid = shell.get("shell").getAsString();
    h.await("the structure and the text compare", v -> {
      try {
        String snap = h.call("ui/snapshot", "shell", sid).toString();
        return snap.contains("Structure Compare") && snap.contains("changed.txt") && snap.contains("only-left.txt")
            && snap.contains("Text Compare") && snap.contains("two") && snap.contains("2\\n");
      } catch (Exception e) {
        return false;
      }
    });
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    // equal files are not differences
    assertTrue(!snap.contains("same.txt"), snap);
    h.closeShell(sid);
  }

  @Test
  @Order(4)
  void contentsThatAreNotFilesAreServedAsDocuments() throws Exception {
    int before = h.notifications("client/openDiff").size();
    ITypedElement left = CompareEditor.textElement("old.txt", "old contents\n");
    ITypedElement right = new ResourceNode(resource("a/changed.txt"));
    CompareEditorInput input = new CompareEditorInput(new CompareConfiguration()) {
      @Override
      protected Object prepareInput(IProgressMonitor monitor) {
        return new DiffNode(null, Differencer.CHANGE, null, left, right);
      }
    };
    open(input);
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > before);
    JsonObject p = h.notifications("client/openDiff").get(before);
    String id = p.getAsJsonObject("left").get("id").getAsString();
    assertEquals("old.txt", p.getAsJsonObject("left").get("name").getAsString());
    assertEquals("old contents\n", h.call("compare/content", "id", id).getAsString());
    assertNotNull(p.getAsJsonObject("right").get("path"));
    List<JsonObject> l = h.notifications("client/openDiff");
    assertTrue(l.size() > before);
  }

  @Test
  @Order(5)
  void aVsCodeSaveKeepsALocalHistoryState() throws Exception {
    com.unitesk.requality.vscode.ui.resources.IFile file = (com.unitesk.requality.vscode.ui.resources.IFile) resource("a/changed.txt");
    int before = file.getHistory(null).length;
    assertEquals(true, h.call("resources/willSave", "path", new File(plain, "a/changed.txt").getAbsolutePath()).getAsBoolean());
    assertEquals(before + 1, file.getHistory(null).length);
    assertEquals("one\ntwo\nthree\n", new String(file.getHistory(null)[0].getContents().readAllBytes(), "UTF-8"));
    // a file outside the projects of the workspace has no local history
    assertEquals(false, h.call("resources/willSave", "path", new File(h.root, "nowhere.txt").getAbsolutePath()).getAsBoolean());
  }
}
