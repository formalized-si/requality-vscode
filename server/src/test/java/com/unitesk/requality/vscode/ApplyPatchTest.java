package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFileState;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;

/**
 * Team &gt; Apply Patch... (org.eclipse.compare's Apply Patch wizard) on a plain Eclipse project: a patch on the clipboard
 * applied to a file, a patch file of the workspace applied to a folder of the project (with Reverse patch), and a hunk
 * that does not match (not applied; a .rej file when the option is on).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplyPatchTest {
  static final String POPUP = ProjectOperationsTest.POPUP;
  static final String ORIGINAL = "{\n  \"x\": 1\n}\n";
  static final String PATCHED = "{\n  \"x\": 2\n}\n";
  static final String DIFF = "--- p.json\n+++ p.json\n@@ -1,3 +1,3 @@\n {\n-  \"x\": 1\n+  \"x\": 2\n }\n";
  static final String GONE_DIFF = "--- gone.json\n+++ gone.json\n@@ -1,3 +1,3 @@\n {\n-  \"x\": 1\n+  \"x\": 2\n }\n";
  static final String UNMATCHED = "--- r.json\n+++ r.json\n@@ -1,3 +1,3 @@\n a\n-zzz\n+yyy\n c\n";
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "Patch");
    new File(plain, "sub").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Patch</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    h.initialize(h.workspace);
    h.await2("the patch project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Patch"))
          return e;
      return null;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String file(String name) {
    return ExplorerService.RESOURCE_PREFIX + new File(plain, name).getAbsolutePath();
  }

  static JsonArray popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  static JsonObject item(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (label.equals(o.has("label") ? o.get("label").getAsString() : null))
        return o;
      if (o.has("children")) {
        JsonObject c = item(o.getAsJsonArray("children"), label);
        if (c != null)
          return c;
      }
    }
    return null;
  }

  /** Runs the item of the last popup request; the future completes when the action ends. */
  static CompletableFuture<JsonElement> runAsync(JsonObject item) {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  /** The wizard shell opened after {@code before} shells. */
  static JsonObject awaitWizard(int before) throws Exception {
    JsonObject[] found = new JsonObject[1];
    h.await("the Apply Patch wizard", v -> {
      List<JsonObject> l = h.notifications("ui/openShell");
      for (int k = before; k < l.size(); k++)
        if ("Apply Patch".equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null)) {
          found[0] = l.get(k);
          return true;
        }
      return false;
    });
    return found[0];
  }

  static String snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The id of the widget of the type (and text, when given) in the shell; null when there is none. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      if (text == null)
        return o.get("id").getAsString();
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      List<String> texts = new java.util.ArrayList<>();
      if (props.has("text") && !props.get("text").isJsonNull())
        texts.add(props.get("text").getAsString());
      if (props.has("texts") && props.get("texts").isJsonArray())
        for (JsonElement t : props.getAsJsonArray("texts"))
          texts.add(t.getAsString());
      for (String t : texts)
        if (text.equals(t) || text.equals(t.replace("&", "")))
          return o.get("id").getAsString();
    }
    return null;
  }

  /** The last widget of the type in the shell (the newest one). */
  static String lastWidget(String shell, String type) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    String last = null;
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (type.equals(o.has("type") ? o.get("type").getAsString() : null))
        last = o.get("id").getAsString();
    }
    return last;
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

  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button");
    // a running operation of the wizard (WizardDialog.run) disables the buttons until it ends, as in Eclipse
    h.await(text + " enabled", v -> {
      try {
        return enabled(shell, b);
      } catch (Exception e) {
        return false;
      }
    });
    event(b, "selection", null, null);
  }

  /** Whether the widget of the shell is enabled (its "enabled" property is not false). */
  static boolean enabled(String shell, String id) throws Exception {
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString()))
        return !(o.has("props") && o.getAsJsonObject("props").has("enabled")
            && !o.getAsJsonObject("props").get("enabled").getAsBoolean());
    }
    return false;
  }

  /** Waits until the shell has a widget of the type and text (the failure shows the snapshot of the shell). */
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
      throw new AssertionError(e.getMessage() + ": " + snapshot(shell), e);
    }
  }

  /** Selects a top-level item of the tree of the page by its text (the project of the target). */
  static void selectTreeItem(String shell, String text) throws Exception {
    // the target page is the first page with a tree (the pages of the wizard are created in order)
    String tree = widget(shell, "tree", null);
    assertNotNull(tree, "the tree of the page");
    awaitWidget(shell, "treeitem", text);
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(widget(shell, "treeitem", text));
    d.add("selection", sel);
    d.addProperty("item", widget(shell, "treeitem", text));
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Waits for the action of the wizard to end; a failure shows the snapshot of the wizard shell. */
  static void end(CompletableFuture<JsonElement> ran, String sid) throws Exception {
    try {
      ran.get(60, TimeUnit.SECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
      throw new AssertionError("the wizard did not finish: " + snapshot(sid), e);
    }
  }

  /** Expands the file of the review tree (the last tree of the page) so that its hunks are shown. */
  static void expandReviewFile(String shell, String file) throws Exception {
    String tree = lastWidget(shell, "tree");
    String item = null;
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null) && o.getAsJsonObject("props").has("texts")
          && o.getAsJsonObject("props").getAsJsonArray("texts").size() > 0
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().startsWith(file)
          && tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null))
        item = o.get("id").getAsString();
    }
    assertNotNull(item, file + " in the review tree: " + snap);
    JsonObject d = new JsonObject();
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "expand");
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Selects the file of the review tree (the last tree of the page) so that its context menu applies to it. */
  static void selectReviewFile(String shell, String file) throws Exception {
    String tree = lastWidget(shell, "tree");
    String item = null;
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null) && o.getAsJsonObject("props").has("texts")
          && o.getAsJsonObject("props").getAsJsonArray("texts").size() > 0
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().startsWith(file)
          && tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null))
        item = o.get("id").getAsString();
    }
    assertNotNull(item, file + " in the review tree: " + snap);
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

  /** Opens the context menu of the tree and runs its item with the label (the item is listed by the menu detect). */
  static void contextMenu(String shell, String tree, String label) throws Exception {
    JsonObject md = new JsonObject();
    md.addProperty("id", tree);
    md.addProperty("type", "menudetect");
    JsonObject xy = new JsonObject();
    xy.addProperty("x", 0);
    xy.addProperty("y", 0);
    md.add("data", xy);
    h.notify("ui/event", md);
    String menuItem = null;
    for (int i = 0; menuItem == null && i < 100; i++) {
      menuItem = widget(shell, "menuitem", label);
      if (menuItem == null)
        Thread.sleep(100);
    }
    assertNotNull(menuItem, "the menu item " + label + ": " + snapshot(shell));
    event(menuItem, "selection", null, null);
  }

  /** The image of the file entry of the review tree (the first image of its tree item), null when it has none. */
  static String reviewImage(String shell, String file) throws Exception {
    String tree = lastWidget(shell, "tree");
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!"treeitem".equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.getAsJsonObject("props");
      if (!tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null))
        continue;
      if (props.has("texts") && props.getAsJsonArray("texts").size() > 0
          && props.getAsJsonArray("texts").get(0).getAsString().startsWith(file)
          && props.has("images") && props.getAsJsonArray("images").size() > 0)
        return props.getAsJsonArray("images").get(0).getAsString();
    }
    return null;
  }

  /** The items of the first combo of the shell (the patch file field of the input page), the last ones sent. */
  static List<String> comboItems(String shell) throws Exception {
    String combo = widget(shell, "combo", null);
    assertNotNull(combo, "the combo of the input page: " + snapshot(shell));
    List<String> items = new java.util.ArrayList<>();
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (combo.equals(o.has("id") ? o.get("id").getAsString() : null) && o.has("props")
          && o.getAsJsonObject("props").has("items")) {
        items.clear();
        for (JsonElement i : o.getAsJsonObject("props").getAsJsonArray("items"))
          items.add(i.getAsString());
      }
    }
    return items;
  }

  /** Whether the shell shows the text (false while the shell cannot be read). */
  static boolean shows(String shell, String text) {
    try {
      return snapshot(shell).contains(text);
    } catch (Exception e) {
      return false;
    }
  }

  static String contents(File f) throws Exception {
    return Files.readString(f.toPath(), StandardCharsets.UTF_8);
  }

  /** The state of the file before its last change (the local history, the most recent first). */
  static String previousState(File f) throws Exception {
    IFile file = (IFile) ResourcesPlugin.getWorkspace().getRoot()
        .getFileForLocation(Path.fromOSString(f.getAbsolutePath()));
    IFileState[] states = file.getHistory(null);
    assertTrue(states.length > 0, "a state of " + f.getName() + " in the local history");
    return new String(states[0].getContents().readAllBytes(), StandardCharsets.UTF_8);
  }

  @Test
  @Order(1)
  void clipboardPatchIsAppliedToTheSelectedFile() throws Exception {
    Files.writeString(new File(plain, "p.json").toPath(), ORIGINAL);
    h.answer("client/readClipboard", p -> DIFF);
    JsonObject apply = item(popup(file("p.json")), "Apply Patch...");
    assertNotNull(apply, "Apply Patch... of a file");
    assertTrue(apply.get("enabled").getAsBoolean(), "Apply Patch... of a file");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    // the input page: the clipboard holds the patch and is the input
    awaitWidget(sid, "button", "Next >");
    assertTrue(snapshot(sid).contains("Patch Input Specification"), "the input page");
    press(sid, "Next >");
    // the target page: the file is the target of the patch
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    // the review page: the file and its hunk
    awaitWidget(sid, "button", "Finish");
    String review = snapshot(sid);
    assertTrue(review.contains("Review Patch"), "the review page");
    assertTrue(review.contains("p.json"), "the file of the patch in the review");
    // the hunk matches: it is shown when the matched hunks are shown
    event(widget(sid, "button", "Show matched hunks"), "selection", "selection", true);
    expandReviewFile(sid, "p.json");
    h.await("the matched hunk in the review", v -> shows(sid, "0,3 -> 0,3"));
    // the context menu of the review tree (PatchCompareEditorInput.fillContextMenu): Exclude and Move
    JsonObject md = new JsonObject();
    String reviewTree = null;
    for (JsonElement e : h.call("ui/snapshot", "shell", sid).getAsJsonObject().getAsJsonArray("ops"))
      if ("tree".equals(e.getAsJsonObject().has("type") ? e.getAsJsonObject().get("type").getAsString() : null))
        reviewTree = e.getAsJsonObject().get("id").getAsString(); // the last tree: the one of the review page
    md.addProperty("id", reviewTree);
    md.addProperty("type", "menudetect");
    JsonObject xy = new JsonObject();
    xy.addProperty("x", 0);
    xy.addProperty("y", 0);
    md.add("data", xy);
    h.notify("ui/event", md);
    h.await("the context menu of the review tree", v -> shows(sid, "Exclude") && shows(sid, "Move"));
    press(sid, "Finish");
    end(ran, sid);
    assertEquals(PATCHED, contents(new File(plain, "p.json")));
    assertEquals(ORIGINAL, previousState(new File(plain, "p.json")), "the old contents are in the local history");
  }

  @Test
  @Order(2)
  void patchFileOfTheWorkspaceIsAppliedToTheTarget() throws Exception {
    Files.writeString(new File(plain, "p.json").toPath(), ORIGINAL);
    Files.writeString(new File(plain, "p.patch").toPath(), DIFF);
    JsonObject apply = item(popup(file("p.patch")), "Apply Patch...");
    assertNotNull(apply, "Apply Patch... of a patch file");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    // the patch is a file of the workspace: the target page comes first
    selectTreeItem(sid, "Patch");
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Finish");
    assertTrue(snapshot(sid).contains("p.json"), "the file of the patch in the review: " + snapshot(sid));
    press(sid, "Finish");
    end(ran, sid);
    assertEquals(PATCHED, contents(new File(plain, "p.json")));
  }

  @Test
  @Order(3)
  void reversePatchReversesTheChanges() throws Exception {
    Files.writeString(new File(plain, "p.json").toPath(), PATCHED);
    JsonObject apply = item(popup(file("p.patch")), "Apply Patch...");
    assertNotNull(apply);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    selectTreeItem(sid, "Patch");
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Reverse patch");
    event(widget(sid, "button", "Reverse patch"), "selection", "selection", true);
    press(sid, "Finish");
    end(ran, sid);
    assertEquals(ORIGINAL, contents(new File(plain, "p.json")));
  }

  @Test
  @Order(4)
  void unmatchedHunkIsNotAppliedAndGetsARejectFile() throws Exception {
    File r = new File(plain, "r.json");
    Files.writeString(r.toPath(), "a\nb\nc\n");
    Files.writeString(new File(plain, "r.patch").toPath(), UNMATCHED);
    JsonObject apply = item(popup(file("r.patch")), "Apply Patch...");
    assertNotNull(apply);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    selectTreeItem(sid, "Patch");
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Generate a .rej file for unmerged hunks");
    // the hunk does not match: it is shown in the review
    expandReviewFile(sid, "r.json");
    h.await("the unmatched hunk in the review", v -> shows(sid, "0,3 -> 0,3"));
    event(widget(sid, "button", "Generate a .rej file for unmerged hunks"), "selection", "selection", true);
    press(sid, "Finish");
    end(ran, sid);
    assertEquals("a\nb\nc\n", contents(r), "the hunk that does not match is not applied");
    File rej = new File(plain, "r.json.rej");
    h.await("the reject file", v -> rej.exists());
    assertTrue(contents(rej).contains("-zzz"), contents(rej));
    assertFalse(contents(r).contains("yyy"));
  }

  @Test
  @Order(5)
  void moveOfAFilePatchAppliesItToTheMovedToFile() throws Exception {
    // gone.json is not in the project: the file patch has a problem, so Move is enabled for it (PreviewPatchPage2)
    File q = new File(plain, "q.json");
    Files.writeString(q.toPath(), ORIGINAL);
    Files.writeString(new File(plain, "gone.patch").toPath(), GONE_DIFF);
    JsonObject apply = item(popup(file("gone.patch")), "Apply Patch...");
    assertNotNull(apply, "Apply Patch... of a patch file");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    selectTreeItem(sid, "Patch");
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Finish");
    selectReviewFile(sid, "gone.json");
    // Move: the context menu of the review tree opens the Retarget Patch dialog for the file patch
    int shells = h.notifications("ui/openShell").size();
    contextMenu(sid, lastWidget(sid, "tree"), "Move");
    JsonObject dialog = h.awaitPanelShell(shells);
    assertEquals("Retarget Patch", dialog.get("title").getAsString(), dialog.toString());
    String dlg = dialog.get("shell").getAsString();
    awaitWidget(dlg, "treeitem", "q.json");
    // the file q.json is the new target (OK is enabled only for a file)
    selectTreeItem(dlg, "q.json");
    press(dlg, "OK");
    awaitWidget(sid, "button", "Finish");
    press(sid, "Finish");
    end(ran, sid);
    assertEquals(PATCHED, contents(q), "the patch is applied to the moved-to file");
  }

  @Test
  @Order(6)
  void overlaysMarkTheUnmatchedFileAndTheExcludedFile() throws Exception {
    Files.writeString(new File(plain, "p.json").toPath(), ORIGINAL);
    Files.writeString(new File(plain, "r.json").toPath(), "a\nb\nc\n");
    Files.writeString(new File(plain, "both.patch").toPath(), DIFF + UNMATCHED);
    JsonObject apply = item(popup(file("both.patch")), "Apply Patch...");
    assertNotNull(apply, "Apply Patch... of a patch file");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    selectTreeItem(sid, "Patch");
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Finish");
    h.await("the files of the review", v -> shows(sid, "p.json") && shows(sid, "r.json"));
    // the file that does not match carries the error overlay; the file that matches has the plain image
    String plainImage = reviewImage(sid, "p.json");
    String unmatchedImage = reviewImage(sid, "r.json");
    assertNotNull(plainImage, "the image of p.json: " + snapshot(sid));
    assertNotNull(unmatchedImage, "the image of r.json: " + snapshot(sid));
    assertFalse(plainImage.equals(unmatchedImage), "the error overlay on the unmatched file: " + unmatchedImage);
    // an excluded file carries the removed overlay (excluded entries are hidden until Show Excluded is set)
    selectReviewFile(sid, "p.json");
    contextMenu(sid, lastWidget(sid, "tree"), "Exclude");
    event(widget(sid, "button", "Show Excluded"), "selection", "selection", true);
    h.await("the excluded file with the removed overlay", v -> {
      String img = null;
      try {
        img = reviewImage(sid, "p.json");
      } catch (Exception e) {
        return false;
      }
      return img != null && !img.equals(plainImage);
    });
    press(sid, "Cancel");
  }

  @Test
  @Order(7)
  void patchFileUsedBeforeIsListedWhenTheWizardIsReopened() throws Exception {
    Files.writeString(new File(plain, "p.json").toPath(), ORIGINAL);
    File hist = new File(plain, "hist.patch");
    Files.writeString(hist.toPath(), DIFF);
    JsonObject apply = item(popup(file("p.json")), "Apply Patch...");
    assertNotNull(apply, "Apply Patch... of a file");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(apply);
    String sid = awaitWizard(before).get("shell").getAsString();
    // the input page: the patch file is typed in the file field (the combo) and the File input is selected
    awaitWidget(sid, "button", "File");
    event(widget(sid, "button", "File"), "selection", "selection", true);
    event(widget(sid, "combo", null), "modify", "text", hist.getAbsolutePath());
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Next >");
    press(sid, "Next >");
    awaitWidget(sid, "button", "Finish");
    press(sid, "Finish");
    end(ran, sid);
    assertEquals(PATCHED, contents(new File(plain, "p.json")));
    // the wizard is opened again: the file field lists the patch file used before
    before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> again = runAsync(item(popup(file("p.json")), "Apply Patch..."));
    String sid2 = awaitWizard(before).get("shell").getAsString();
    awaitWidget(sid2, "button", "Next >");
    h.await("the patch file history", v -> {
      try {
        return comboItems(sid2).contains(hist.getAbsolutePath());
      } catch (Exception e) {
        return false;
      }
    });
    press(sid2, "Cancel");
  }
}
