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
import java.util.concurrent.CopyOnWriteArrayList;
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
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareEditor;
import com.unitesk.requality.vscode.ui.compare.CompareLinks;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeManager;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * Synchronize with Patch (Team &gt; Synchronize...) in the Synchronize view: the wizard adds a participant of the patch to the
 * view, which shows its review tree; Pin, the page drop-down, Merge and Remove Current Synchronization act on the participant.
 * The patch is on the clipboard, applied to the file of the Patch project.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SynchronizeParticipantsTest {
  static final String SYNC_VIEW = "requality.synchronize";
  static final String VIEW_MENU = SynchronizeView.ID + ".viewMenu";
  static final String DROP_DOWN = SynchronizeView.SYNCHRONIZATIONS_MENU;
  static final String PARTICIPANT = "Patch";
  static final String ORIGINAL = "{\n  \"x\": 1\n}\n";
  static final String PATCHED = "{\n  \"x\": 2\n}\n";
  static final String DIFF = "--- p.json\n+++ p.json\n@@ -1,3 +1,3 @@\n {\n-  \"x\": 1\n+  \"x\": 2\n }\n";
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "Patch");
    plain.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Patch</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "p.json").toPath(), ORIGINAL);
    h.initialize(h.workspace);
    h.await2("the Patch project", () -> {
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
    try {
      h.await(text + " enabled", v -> {
        try {
          return enabled(shell, b);
        } catch (Exception e) {
          return false;
        }
      });
    } catch (AssertionError e) {
      throw new AssertionError(e.getMessage() + ": " + text(shell), e);
    }
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

  /** Selects the item with the text (a tree item, or a row of a table) of the first tree or table of the shell. */
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

  /** Selects the item with the text in the tree of the shell (the tree with the given id). */
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
    Display.getDefault().syncCall(() -> {
      v[0] = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(SynchronizeView.ID);
      return null;
    });
    return v[0] instanceof SynchronizeView sv ? sv : null;
  }

  /** The items of a menu of the server (menu/items), without a selection. */
  static JsonArray menuItems(String menu) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", menu);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The item with the label (searching the submenus), or null. */
  static JsonObject menuItem(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (label.equals(o.has("label") ? o.get("label").getAsString() : null))
        return o;
      if (o.has("children")) {
        JsonObject c = menuItem(o.getAsJsonArray("children"), label);
        if (c != null)
          return c;
      }
    }
    return null;
  }

  /** Runs the item of the last listing of the menu; the future completes when the action ends. */
  static void runMenu(String menu, JsonObject item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", menu);
    p.addProperty("index", item.get("index").getAsInt());
    h.callAsync("menu/run", p).get(60, TimeUnit.SECONDS);
  }

  /** Opens the context menu of the tree; returns when the item with the label is listed. */
  static void openContextMenu(String shell, String tree, String label) throws Exception {
    JsonObject md = new JsonObject();
    md.addProperty("id", tree);
    md.addProperty("type", "menudetect");
    JsonObject xy = new JsonObject();
    xy.addProperty("x", 0);
    xy.addProperty("y", 0);
    md.add("data", xy);
    h.notify("ui/event", md);
    h.await("the menu item " + label, v -> {
      try {
        return widget(shell, "menuitem", label) != null;
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** Opens the context menu of the tree and runs its item with the label. */
  static void contextMenu(String shell, String tree, String label) throws Exception {
    JsonObject md = new JsonObject();
    md.addProperty("id", tree);
    md.addProperty("type", "menudetect");
    JsonObject xy = new JsonObject();
    xy.addProperty("x", 0);
    xy.addProperty("y", 0);
    md.add("data", xy);
    h.notify("ui/event", md);
    h.await("the menu item " + label, v -> {
      try {
        return widget(shell, "menuitem", label) != null;
      } catch (Exception e) {
        return false;
      }
    });
    event(widget(shell, "menuitem", label), "selection", null, null);
  }

  /** The image of the file entry of the review tree (the first image of its tree item), null when it has none. */
  static String reviewImage(String shell, String file) throws Exception {
    String item = reviewItem(shell, file);
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (item != null && item.equals(o.get("id").getAsString()) && o.getAsJsonObject("props").has("images")
          && o.getAsJsonObject("props").getAsJsonArray("images").size() > 0)
        return o.getAsJsonObject("props").getAsJsonArray("images").get(0).getAsString();
    }
    return null;
  }

  /** The id of the item of the review tree (the last tree of the shell) whose text starts with the file name, or null. */
  static String reviewItem(String shell, String file) throws Exception {
    String tree = lastWidget(shell, "tree");
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null)
          && o.getAsJsonObject("props").has("texts") && o.getAsJsonObject("props").getAsJsonArray("texts").size() > 0
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().startsWith(file)
          && tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** Selects the file of the review tree of the view (the last tree of the shell) so that its context menu applies to it. */
  static void selectReviewFile(String shell, String file) throws Exception {
    String tree = lastWidget(shell, "tree");
    String item = null;
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null)
          && o.getAsJsonObject("props").has("texts") && o.getAsJsonObject("props").getAsJsonArray("texts").size() > 0
          && o.getAsJsonObject("props").getAsJsonArray("texts").get(0).getAsString().startsWith(file)
          && tree.equals(o.has("parent") && !o.get("parent").isJsonNull() ? o.get("parent").getAsString() : null))
        item = o.get("id").getAsString();
    }
    assertNotNull(item, file + " in the review tree: " + text(shell));
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

  @Test
  @Order(1)
  void synchronizeWithPatchAddsAParticipantToTheView() throws Exception {
    h.answer("client/readClipboard", p -> DIFF);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = execute("org.eclipse.team.ui.synchronizeAll", null);
    String sync = awaitShell(before, "Synchronize").get("shell").getAsString();
    selectTreeItem(sync, "Synchronize with Patch");
    press(sync, "Next >");
    // the input page: the clipboard holds the patch
    awaitWidget(sync, "button", "Next >");
    assertTrue(text(sync).contains("Patch Input Specification"), "the input page: " + text(sync));
    press(sync, "Next >");
    // the target page: the project of the patch
    awaitWidget(sync, "label", "Select the target workspace resource for the patch.");
    // the target is chosen in the tree of the target page (the last tree listing the project before the review is built)
    awaitWidget(sync, "treeitem", "Patch");
    String target = treeWithItem(sync, "Patch");
    assertNotNull(target, "the tree of the target page: " + text(sync));
    selectInTree(sync, target, "Patch");
    press(sync, "Next >");
    awaitWidget(sync, "button", "Finish");
    assertTrue(text(sync).contains("p.json"), "the file of the patch in the review: " + text(sync));
    press(sync, "Finish");
    ran.get(60, TimeUnit.SECONDS);

    // the patch is not applied: the participant is shown in the view
    assertEquals(ORIGINAL, Files.readString(new File(plain, "p.json").toPath(), StandardCharsets.UTF_8));
    ISynchronizeParticipant[] all = SynchronizeManager.getInstance().getSynchronizeParticipants();
    assertEquals(1, all.length, "one participant");
    assertEquals(PARTICIPANT, all[0].getName());
    SynchronizeView view = view();
    assertNotNull(view, "the Synchronize view is shown");
    assertTrue(view.getPartName().contains("Synchronize (" + PARTICIPANT + ")"), "the title: " + view.getPartName());
    assertTrue(view.getContentDescription().contains("Synchronize with Patch"), "the description: " + view.getContentDescription());
    h.await("the file of the patch in the view", v -> {
      try {
        return widget(viewShell(), "treeitem", null) != null && text(viewShell()).contains("p.json");
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(2)
  void pinTogglesThePinnedStateAndTheCheckedState() throws Exception {
    ISynchronizeParticipant p = SynchronizeManager.getInstance().getSynchronizeParticipants()[0];
    JsonObject pin = menuItem(menuItems(VIEW_MENU), "Pin Current Synchronization");
    assertNotNull(pin, "Pin Current Synchronization in the view menu");
    assertTrue(pin.get("enabled").getAsBoolean(), "Pin enabled with a participant");
    assertFalse(pin.get("checked").getAsBoolean(), "not pinned at first");
    runMenu(VIEW_MENU, pin);
    assertTrue(p.isPinned(), "the participant is pinned");
    assertTrue(menuItem(menuItems(VIEW_MENU), "Pin Current Synchronization").get("checked").getAsBoolean(),
        "Pin is checked");
    runMenu(VIEW_MENU, menuItem(menuItems(VIEW_MENU), "Pin Current Synchronization"));
    assertFalse(p.isPinned(), "the participant is un-pinned again");
    runMenu(VIEW_MENU, menuItem(menuItems(VIEW_MENU), "Pin Current Synchronization"));
    assertTrue(p.isPinned(), "the participant is pinned for the remaining tests");
  }

  @Test
  @Order(3)
  void dropDownListsTheParticipantAndTheSynchronizeAction() throws Exception {
    JsonArray items = menuItems(DROP_DOWN);
    JsonObject participant = menuItem(items, PARTICIPANT);
    assertNotNull(participant, PARTICIPANT + " in the page drop-down: " + items);
    assertTrue(participant.get("checked").getAsBoolean(), "the current participant is checked");
    assertNotNull(menuItem(items, "Synchronize..."), "Synchronize... in the page drop-down");
  }

  @Test
  @Order(4)
  void contextMenuOfAFileHasOpenMergeAndMarkAsMergedAsEclipse() throws Exception {
    String shell = viewShell();
    assertNotNull(shell, "the view shell");
    awaitWidget(shell, "treeitem", null);
    String tree = lastWidget(shell, "tree");
    assertNotNull(tree, "the review tree of the view: " + text(shell));
    selectReviewFile(shell, "p.json");
    openContextMenu(shell, tree, "Merge");
    // the Eclipse order: the open action of the file, then the merge actions (Overwrite is omitted for the patch)
    assertEquals(List.of("Open In Compare Editor", "Merge", "Mark as Merged"), menuLabels(shell), "the menu items of the file");
    assertTrue(enabled(shell, widget(shell, "menuitem", "Open In Compare Editor")), "Open In Compare Editor enabled");
    assertTrue(enabled(shell, widget(shell, "menuitem", "Merge")), "Merge enabled");
    assertTrue(enabled(shell, widget(shell, "menuitem", "Mark as Merged")), "Mark as Merged enabled");
  }

  @Test
  @Order(5)
  void doubleClickOpensTheCompareEditorOfTheFile() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    String item = reviewItem(shell, "p.json");
    assertNotNull(item, "p.json in the review tree: " + text(shell));
    selectReviewFile(shell, "p.json");
    int before = h.notifications("ui/openShell").size();
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "default");
    p.add("data", d);
    h.notify("ui/event", p);
    // the compare editor of the file (the .json file has its own content merge viewer, so it is a panel, not a diff)
    JsonObject editor = awaitShell(before, "p.json");
    assertEquals("panel", editor.get("host").getAsString(), "the compare editor is an editor: " + editor);
  }

  @Test
  @Order(6)
  void reviewItemOfTheFileHasTheIncomingChangeImage() throws Exception {
    String shell = viewShell();
    String image = reviewImage(shell, "p.json");
    assertNotNull(image, "the image of p.json: " + text(shell));
    Image base = baseImage();
    // the base image of the file with the overlay of the incoming change (the patch shows the changes of the right side as
    // incoming ones, DiffTreeViewer), composed beside it as DiffImageDescriptor does
    assertEquals(Icons.composeBeside(base.getPath(), "icons/compare/ovr16/r_inchg_ov.png", 22, false), image,
        "the base image of p.json with the incoming change overlay");
    // an addition has the incoming addition overlay
    assertEquals(Icons.composeBeside(base.getPath(), "icons/compare/ovr16/r_inadd_ov.png", 22, false),
        new CompareConfiguration().getImage(base, Differencer.LEFT | Differencer.ADDITION).getPath(),
        "the base image with the incoming addition overlay");
  }

  @Test
  @Order(61)
  void linkingOffLeavesTheCompareEditorAlone() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    String panel = panelShell();
    assertNotNull(panel, "the compare editor of p.json: " + text(shell));
    view().setLinkingEnabled(false);
    int reveals = reveals(panel);
    deselect(tree);
    selectReviewFile(shell, "p.json");
    h.await("the file selected", v -> {
      try {
        return treeSelection(shell).equals(List.of(reviewItem(shell, "p.json")));
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals(reveals, reveals(panel), "no reveal of the compare editor with the linking off");
  }

  @Test
  @Order(62)
  void linkingOnBringsTheCompareEditorOfTheSelectedFileToTheTop() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    String panel = panelShell();
    view().setLinkingEnabled(true);
    int reveals = reveals(panel);
    selectReviewFile(shell, "p.json");
    h.await("the compare editor of p.json revealed", v -> reveals(panel) > reveals);
  }

  @Test
  @Order(63)
  void activationOfTheCompareEditorSelectsItsFile() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    String panel = panelShell();
    view().setLinkingEnabled(true);
    deselect(tree);
    JsonObject focus = new JsonObject();
    focus.addProperty("shell", panel);
    h.notify("ui/shellFocused", focus);
    h.await("the file of the compare editor selected", v -> {
      try {
        return treeSelection(shell).equals(List.of(reviewItem(shell, "p.json")));
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(64)
  void linkingOffDoesNotSelectTheFileOfTheCompareEditor() throws Exception {
    String shell = viewShell();
    String tree = lastWidget(shell, "tree");
    String panel = panelShell();
    view().setLinkingEnabled(false);
    deselect(tree);
    JsonObject focus = new JsonObject();
    focus.addProperty("shell", panel);
    h.notify("ui/shellFocused", focus);
    Thread.sleep(300);
    assertEquals(List.of(), treeSelection(shell), "the file of the compare editor is not selected with the linking off");
  }

  @Test
  @Order(65)
  void aTextCompareIsLinkedWithItsElementThroughVsCodeDiff() throws Exception {
    Object element = new Object();
    List<Object> selected = new CopyOnWriteArrayList<>();
    view().setLinkingEnabled(true);
    int before = h.notifications("client/openDiff").size();
    CompareUI.openCompareEditor("t.txt", CompareEditor.textElement("t.txt", "a\n"), CompareEditor.textElement("t.txt", "b\n"),
        "Left", "Right", element, selected::add);
    h.await("the diff of t.txt", v -> h.notifications("client/openDiff").size() > before);
    JsonObject diff = h.notifications("client/openDiff").get(before);
    assertTrue(CompareLinks.bringToTop(element), "the compare editor of the element is brought to the top");
    List<JsonObject> sent = h.notifications("client/openDiff");
    assertEquals(diff, sent.get(sent.size() - 1), "the same sides are sent again (VS Code reveals the open diff)");
    // the extension echoes the sides of the diff it shows: the element is selected in the view
    JsonObject shown = new JsonObject();
    shown.add("left", echo(diff.getAsJsonObject("left")));
    shown.add("right", echo(diff.getAsJsonObject("right")));
    h.notify("editor/activeDiff", shown);
    h.await("the element of the diff selected", v -> selected.contains(element));
    // the closed diff is no longer linked
    h.notify("editor/diffClosed", shown);
    Thread.sleep(300);
    assertFalse(CompareLinks.bringToTop(element), "a closed diff is not brought to the top");
    view().setLinkingEnabled(false);
    assertFalse(CompareLinks.bringToTop(element), "no compare editor is brought to the top with the linking off");
  }

  /** The sides of a diff as the extension echoes them: the path, else the id. */
  static JsonObject echo(JsonObject side) {
    JsonObject o = new JsonObject();
    if (side.has("path"))
      o.add("path", side.get("path"));
    else
      o.add("id", side.get("id"));
    return o;
  }

  /** The base image of the file p.json of the Patch project (the image of its tree items before the overlay). */
  static Image baseImage() throws Exception {
    Image[] image = new Image[1];
    Display.getDefault().syncCall(() -> {
      IFile file = ResourcesPlugin.getWorkspace().getRoot()
          .getFileForLocation(Path.fromOSString(new File(plain, "p.json").getAbsolutePath()));
      image[0] = CompareUI.getImage(file);
      return null;
    });
    assertNotNull(image[0], "the image of the file");
    return image[0];
  }

  /** The compare editor panel of p.json (the last one opened for it), or null. */
  static String panelShell() {
    String shell = null;
    for (JsonObject n : h.notifications("ui/openShell"))
      if ("p.json".equals(n.has("title") ? n.get("title").getAsString() : null))
        shell = n.get("shell").getAsString();
    return shell;
  }

  /** The reveals of the shell sent to the client (the compare editor brought to the top). */
  static int reveals(String shell) {
    int count = 0;
    for (JsonObject n : h.notifications("ui/update"))
      if (n.toString().contains("\"reveal\"") && n.toString().contains("\"" + shell + "\""))
        count++;
    return count;
  }

  /** The ids of the selected items of the review tree (the last tree of the shell). */
  static List<String> treeSelection(String shell) throws Exception {
    String tree = lastWidget(shell, "tree");
    List<String> sel = new ArrayList<>();
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (tree.equals(o.get("id").getAsString()) && o.getAsJsonObject("props").has("selection"))
        for (JsonElement id : o.getAsJsonObject("props").getAsJsonArray("selection"))
          sel.add(id.getAsString());
    }
    return sel;
  }

  /** Selects the item of the tree (the item id). */
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

  /** Clears the selection of the tree. */
  static void deselect(String tree) {
    JsonObject d = new JsonObject();
    d.add("selection", new JsonArray());
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    p.add("data", d);
    h.notify("ui/event", p);
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

  @Test
  @Order(80)
  void mergeAppliesTheSelectedFileOfThePatch() throws Exception {
    String shell = viewShell();
    assertNotNull(shell, "the view shell");
    awaitWidget(shell, "treeitem", null);
    String tree = lastWidget(shell, "tree");
    assertNotNull(tree, "the review tree of the view: " + text(shell));
    selectReviewFile(shell, "p.json");
    contextMenu(shell, tree, "Merge");
    h.await("the file is merged", v -> {
      try {
        return PATCHED.equals(Files.readString(new File(plain, "p.json").toPath(), StandardCharsets.UTF_8));
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals(PATCHED, Files.readString(new File(plain, "p.json").toPath(), StandardCharsets.UTF_8));
  }

  @Test
  @Order(81)
  void removeCurrentSynchronizationRemovesTheParticipantAndShowsTheDefaultPage() throws Exception {
    JsonObject remove = menuItem(menuItems(VIEW_MENU), "Remove Current Synchronization");
    assertNotNull(remove, "Remove Current Synchronization in the view menu");
    assertTrue(remove.get("enabled").getAsBoolean(), "Remove Current Synchronization with a participant");
    // the participant is pinned (test 2): Eclipse asks before it is removed; the user confirms
    int asked = h.requests("client/showMessage").size();
    runMenu(VIEW_MENU, remove);
    assertTrue(h.requests("client/showMessage").subList(asked, h.requests("client/showMessage").size()).stream()
        .anyMatch(m -> m.toString().contains("Remove Pinned Synchronization?")), "the question of the pinned participant");
    assertEquals(0, SynchronizeManager.getInstance().getSynchronizeParticipants().length, "no participant");
    SynchronizeView view = view();
    assertNotNull(view);
    assertEquals(null, view.getParticipant(), "the default page is shown");
    assertFalse(view.getPartName().contains("Synchronize (" + PARTICIPANT + ")"), "the title is the default one");
    assertFalse(menuItem(menuItems(DROP_DOWN), PARTICIPANT) != null, "no participant in the page drop-down");
    assertFalse(menuItem(menuItems(VIEW_MENU), "Remove Current Synchronization").get("enabled").getAsBoolean(),
        "Remove Current Synchronization without a participant");
  }
}
