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
import com.unitesk.requality.vscode.core.Icons;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * Synchronize with Patch with a file that has a hunk which applies and a hunk which does not: the review tree shows the file
 * and the unmatched hunk under it. The hunk item has Open In Compare Editor, Merge and Mark as Merged, with Merge and Mark as
 * Merged disabled (ApplyPatchSynchronizationActionProvider); Merge of the file applies the matching hunk only.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SynchronizeHunksTest {
  static final String SYNC_VIEW = "requality.synchronize";
  static final String PARTICIPANT = "Patch";
  static final String FILE = "h.txt";
  static final String ORIGINAL = "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n";
  /** The first hunk applies; the second one does not (its context and its removed line differ from the file). */
  static final String PATCHED = "one\nTWO\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\n";
  static final String DIFF = "--- h.txt\n+++ h.txt\n@@ -1,3 +1,3 @@\n one\n-two\n+TWO\n three\n"
      + "@@ -7,3 +7,3 @@\n SEVEN\n-EIGHT\n+8\n NINE\n";
  static final String HUNK_LABEL = "6,3 -> 6,3";
  static final String HUNK_OBJ = "icons/compare/obj16/hunk_obj.png";
  static final String ERROR_OVERLAY = "icons/compare/ovr16/error_ov.png";
  static final int COMPARE_IMAGE_WIDTH = 22;
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "Hunks");
    plain.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Hunks</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, FILE).toPath(), ORIGINAL);
    h.initialize(h.workspace);
    h.await2("the Hunks project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Hunks"))
          return e;
      return null;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    if (item != null)
      nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
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

  static JsonArray ops(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  static String text(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The id of the first widget of the type with the text (mnemonics removed), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (text == null)
        return o.get("id").getAsString();
      List<String> texts = new ArrayList<>();
      if (props.has("text") && !props.get("text").isJsonNull())
        texts.add(props.get("text").getAsString());
      if (props.has("texts"))
        for (JsonElement t : props.getAsJsonArray("texts"))
          if (!t.isJsonNull())
            texts.add(t.getAsString());
      for (String t : texts)
        if (text.equals(t) || text.equals(t.replace("&", "")))
          return o.get("id").getAsString();
    }
    return null;
  }

  /** The last widget of the type in the shell (the newest one), or null. */
  static String lastWidget(String shell, String type) throws Exception {
    String last = null;
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (type.equals(o.has("type") ? o.get("type").getAsString() : null))
        last = o.get("id").getAsString();
    }
    return last;
  }

  static boolean enabled(String shell, String id) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString()))
        return !(o.has("props") && o.getAsJsonObject("props").has("enabled")
            && !o.getAsJsonObject("props").get("enabled").getAsBoolean());
    }
    return false;
  }

  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button of " + text(shell));
    h.await(text + " enabled", v -> {
      try {
        return enabled(shell, b);
      } catch (Exception e) {
        return false;
      }
    });
    event(b, "selection", null, null);
  }

  static void awaitWidget(String shell, String type, String text) throws Exception {
    try {
      h.await(type + " " + text, v -> {
        try {
          return widget(shell, type, text) != null;
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + text(shell), e);
    }
  }

  /** The shell of a dialog opened after the given count of opened shells, with the title. */
  static JsonObject awaitShell(int before, String title) throws Exception {
    JsonObject[] found = new JsonObject[1];
    h.await("the " + title + " dialog", v -> {
      List<JsonObject> l = h.notifications("ui/openShell");
      for (int k = before; k < l.size(); k++)
        if (title.equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null)) {
          found[0] = l.get(k);
          return true;
        }
      return false;
    });
    return found[0];
  }

  /** Selects the item with the text of the first tree of the shell (a tree item, or a row of a table). */
  static void selectTreeItem(String shell, String text) throws Exception {
    String tree = widget(shell, "tree", null);
    String itemType = "treeitem";
    if (tree == null) {
      tree = widget(shell, "table", null);
      itemType = "row";
    }
    assertNotNull(tree, "the tree or table of " + text(shell));
    awaitWidget(shell, itemType, text);
    String item = widget(shell, itemType, text);
    select(tree, item);
  }

  /**
   * The last tree of the shell with a top-level item of the text. The pages of the nested wizard are created together
   * (WizardDialog.setWizard), so the trees of the later pages follow the tree of the current page.
   */
  static String treeWithItem(String shell, String text) throws Exception {
    java.util.Set<String> trees = new java.util.LinkedHashSet<>();
    String found = null;
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      String type = o.has("type") ? o.get("type").getAsString() : null;
      if ("tree".equals(type))
        trees.add(o.get("id").getAsString());
      else if ("treeitem".equals(type) && o.has("parent") && !o.get("parent").isJsonNull()
          && trees.contains(o.get("parent").getAsString()) && o.getAsJsonObject("props").has("texts")
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().equals(text))
        found = o.get("parent").getAsString();
    }
    return found;
  }

  static void selectInTree(String shell, String tree, String text) throws Exception {
    String item = null;
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null)
          && tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null)
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().equals(text))
        item = o.get("id").getAsString();
    }
    assertNotNull(item, text + " in the tree " + tree + ": " + text(shell));
    select(tree, item);
  }

  /** The shell of the Synchronize view (the last one opened for it), or null while the view is not shown. */
  static String viewShell() {
    String shell = null;
    for (JsonObject n : h.notifications("ui/openShell"))
      if ("view".equals(n.has("host") ? n.get("host").getAsString() : null)
          && SYNC_VIEW.equals(n.has("view") ? n.get("view").getAsString() : null))
        shell = n.get("shell").getAsString();
    return shell;
  }

  /** The Synchronize view of the active page, or null. */
  static SynchronizeView view() {
    Object[] v = new Object[1];
    com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncCall(() -> {
      v[0] = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(SynchronizeView.ID);
      return null;
    });
    return v[0] instanceof SynchronizeView sv ? sv : null;
  }

  /** Opens the context menu of the tree and returns when the menu items are listed. */
  static void openContextMenu(String shell, String tree) throws Exception {
    JsonObject md = new JsonObject();
    md.addProperty("id", tree);
    md.addProperty("type", "menudetect");
    JsonObject xy = new JsonObject();
    xy.addProperty("x", 0);
    xy.addProperty("y", 0);
    md.add("data", xy);
    h.notify("ui/event", md);
    h.await("the context menu", v -> {
      try {
        return widget(shell, "menuitem", "Merge") != null;
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** Opens the context menu of the tree and runs its item with the label. */
  static void contextMenu(String shell, String tree, String label) throws Exception {
    openContextMenu(shell, tree);
    event(widget(shell, "menuitem", label), "selection", null, null);
  }

  /** The labels of the menu items of the shell (without mnemonics), in order. */
  static List<String> menuLabels(String shell) throws Exception {
    List<String> labels = new ArrayList<>();
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("menuitem".equals(o.has("type") ? o.get("type").getAsString() : null) && o.getAsJsonObject("props").has("text"))
        labels.add(o.getAsJsonObject("props").get("text").getAsString().replace("&", ""));
    }
    return labels;
  }

  /** The id of the review tree item (the last tree of the shell) whose text starts with the text, or null. */
  static String reviewItem(String shell, String start) throws Exception {
    String tree = lastWidget(shell, "tree");
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null)
          && o.getAsJsonObject("props").has("texts") && o.getAsJsonObject("props").getAsJsonArray("texts").size() > 0
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().startsWith(start)
          && tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The tree items under the item (its hunks), as the first text and the first image of each. */
  static List<JsonObject> childItems(String shell, String item) throws Exception {
    List<JsonObject> children = new ArrayList<>();
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null)
          && o.has("parent") && !o.get("parent").isJsonNull() && item.equals(o.get("parent").getAsString()))
        children.add(o);
    }
    return children;
  }

  /** Expands the file of the review tree so that its hunks are shown. */
  static void expandReviewFile(String shell, String file) throws Exception {
    String tree = lastWidget(shell, "tree");
    String item = reviewItem(shell, file);
    assertNotNull(item, file + " in the review tree: " + text(shell));
    JsonObject d = new JsonObject();
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "expand");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  static void select(String tree, String item) {
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Selects the file of the review tree (the last tree of the shell) so that its context menu applies to it. */
  static void selectReviewItem(String shell, String start) throws Exception {
    String item = reviewItem(shell, start);
    assertNotNull(item, start + " in the review tree: " + text(shell));
    select(lastWidget(shell, "tree"), item);
  }

  static boolean shows(String shell, String text) {
    try {
      return text(shell).contains(text);
    } catch (Exception e) {
      return false;
    }
  }

  static String contents(File f) throws Exception {
    return Files.readString(f.toPath(), StandardCharsets.UTF_8);
  }

  @Test
  @Order(1)
  void synchronizeWithPatchShowsTheFileAndItsUnmatchedHunk() throws Exception {
    h.answer("client/readClipboard", p -> DIFF);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = execute("org.eclipse.team.ui.synchronizeAll", null);
    String sync = awaitShell(before, "Synchronize").get("shell").getAsString();
    selectTreeItem(sync, "Synchronize with Patch");
    press(sync, "Next >");
    awaitWidget(sync, "button", "Next >");
    assertTrue(text(sync).contains("Patch Input Specification"), "the input page: " + text(sync));
    press(sync, "Next >");
    awaitWidget(sync, "label", "Select the target workspace resource for the patch.");
    awaitWidget(sync, "treeitem", "Hunks");
    String target = treeWithItem(sync, "Hunks");
    assertNotNull(target, "the tree of the target page: " + text(sync));
    selectInTree(sync, target, "Hunks");
    press(sync, "Next >");
    awaitWidget(sync, "button", "Finish");
    assertTrue(text(sync).contains(FILE), "the file of the patch in the review: " + text(sync));
    press(sync, "Finish");
    ran.get(60, TimeUnit.SECONDS);

    // the patch is not applied yet
    assertEquals(ORIGINAL, contents(new File(plain, FILE)));
    h.await("the file of the patch in the view", v -> {
      try {
        return viewShell() != null && reviewItem(viewShell(), FILE) != null;
      } catch (Exception e) {
        return false;
      }
    });
    String shell = viewShell();
    expandReviewFile(shell, FILE);
    h.await("the unmatched hunk under the file", v -> {
      try {
        return shows(shell, HUNK_LABEL);
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(2)
  void theUnmatchedHunkIsShownUnderItsFileWithItsLabelAndImage() throws Exception {
    String shell = viewShell();
    assertNotNull(shell, "the view shell");
    String file = reviewItem(shell, FILE);
    assertNotNull(file, "the file in the review tree: " + text(shell));
    List<JsonObject> hunks = childItems(shell, file);
    assertEquals(1, hunks.size(), "only the unmatched hunk is shown under the file: " + text(shell));
    JsonObject props = hunks.get(0).getAsJsonObject("props");
    assertEquals(HUNK_LABEL, props.getAsJsonArray("texts").get(0).getAsString(), "the label of the hunk");
    // the unmatched hunk: the hunk image with the error overlay beside it (HunkTypedElement.getHunkErrorImage), which the
    // tree draws with the kind of the hunk (a change without direction has no overlay: DiffTreeViewer, CompareConfiguration)
    String error = Icons.composeBeside(Icons.resource(HUNK_OBJ).getAbsolutePath(), Icons.resource(ERROR_OVERLAY).getAbsolutePath(),
        COMPARE_IMAGE_WIDTH, true);
    assertEquals(new CompareConfiguration().getImage(new Image(null, error), Differencer.CHANGE).getPath(),
        props.getAsJsonArray("images").get(0).getAsString(), "the image of the hunk");
  }

  @Test
  @Order(3)
  void hunkContextMenuHasOpenMergeAndMarkAsMergedWithMergeAndMarkDisabled() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    String hunk = childItems(shell, reviewItem(shell, FILE)).get(0).get("id").getAsString();
    select(tree, hunk);
    openContextMenu(shell, tree);
    assertEquals(List.of("Open In Compare Editor", "Merge", "Mark as Merged"), menuLabels(shell), "the menu items of the hunk");
    assertTrue(enabled(shell, widget(shell, "menuitem", "Open In Compare Editor")), "Open In Compare Editor enabled");
    assertFalse(enabled(shell, widget(shell, "menuitem", "Merge")), "Merge disabled for a hunk");
    assertFalse(enabled(shell, widget(shell, "menuitem", "Mark as Merged")), "Mark as Merged disabled for a hunk");
  }

  @Test
  @Order(4)
  void fileContextMenuHasMergeEnabled() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    selectReviewItem(shell, FILE);
    openContextMenu(shell, tree);
    assertTrue(enabled(shell, widget(shell, "menuitem", "Merge")), "Merge enabled for the file");
    assertTrue(enabled(shell, widget(shell, "menuitem", "Mark as Merged")), "Mark as Merged enabled for the file");
  }

  @Test
  @Order(5)
  void mergeOfTheFileAppliesTheMatchingHunkOnly() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    selectReviewItem(shell, FILE);
    contextMenu(shell, tree, "Merge");
    h.await("the matching hunk is merged", v -> {
      try {
        return PATCHED.equals(contents(new File(plain, FILE)));
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals(PATCHED, contents(new File(plain, FILE)), "only the matching hunk is applied");
  }
}
