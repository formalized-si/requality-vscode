package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import com.unitesk.requality.vscode.ui.compare.CompareUIPlugin;
import com.unitesk.requality.vscode.ui.compare.ComparePreferencePage;

/**
 * The archives and the compare filter (org.eclipse.compare: ZipFileStructureCreator, StructureDiffViewer, the Filtered
 * Members of the Compare/Patch preferences, and Compare With Other Resource): two zip files are compared as the trees of
 * the entries that differ, with the text compare of a changed entry; the filter leaves out the members of the folders and
 * of the archives; the dialog of Compare With Other Resource compares two chosen workspace files.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompareArchivesTest {
  static final String POPUP = ProjectOperationsTest.POPUP;
  static final String COMPARE_WITH_OTHER = "org.eclipse.compare.compareWithOther";
  static ServerHarness h;
  static File arch;
  static String zipShell;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    arch = new File(h.workspace, "arch");
    new File(arch, "fa").mkdirs();
    new File(arch, "fb").mkdirs();
    Files.writeString(new File(arch, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>arch</name><comment></comment><projects></projects><buildSpec></buildSpec>"
        + "<natures></natures></projectDescription>\n");
    zip(new File(arch, "a.zip"), "same.txt", "same\n", "changed.txt", "one\ntwo\nthree\n", "only-left.txt", "left\n",
        "build/out.bin", "left-bin", "docs/readme.txt", "readme\n");
    zip(new File(arch, "b.zip"), "same.txt", "same\n", "changed.txt", "one\n2\nthree\n", "build/out.bin", "right-bin",
        "docs/readme.txt", "readme\n");
    Files.writeString(new File(arch, "fa/changed.txt").toPath(), "one\ntwo\nthree\n");
    Files.writeString(new File(arch, "fb/changed.txt").toPath(), "one\n2\nthree\n");
    Files.writeString(new File(arch, "fa/only-left.txt").toPath(), "left\n");
    new File(arch, "fa/build").mkdirs();
    new File(arch, "fb/build").mkdirs();
    Files.writeString(new File(arch, "fa/build/out.bin").toPath(), "left-bin");
    Files.writeString(new File(arch, "fb/build/out.bin").toPath(), "right-bin");
    Files.writeString(new File(arch, "x.txt").toPath(), "x one\n");
    Files.writeString(new File(arch, "y.txt").toPath(), "y two\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await2("the arch project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("arch"))
          return e;
      return null;
    });
  }

  @AfterAll
  static void stop() throws Exception {
    CompareUIPlugin.getPreferenceStore().setValue(ComparePreferencePage.PATH_FILTER, "");
    h.close();
  }

  static void zip(File file, String... nameAndContents) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
      for (int i = 0; i < nameAndContents.length; i += 2) {
        out.putNextEntry(new ZipEntry(nameAndContents[i]));
        out.write(nameAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
  }

  static String file(String name) {
    return ExplorerService.RESOURCE_PREFIX + new File(arch, name).getAbsolutePath();
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

  static void run(JsonObject item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    h.callAsync("menu/run", p).get(60, TimeUnit.SECONDS);
  }

  /** The compare of two files of the explorer selection: Compare With &gt; Each Other. */
  static void eachOther(String a, String b) throws Exception {
    JsonObject compare = TeamCompareTest.submenu(popup(a, b), "Compare With");
    assertNotNull(compare, "Compare With of the two files");
    JsonObject each = TeamCompareTest.item(compare.getAsJsonArray("children"), "Each Other");
    assertNotNull(each, "Each Other");
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of two files");
    run(each);
  }

  static String snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The tree item of the shell with the text in its first column, or null. */
  static String treeItem(String shell, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!"treeitem".equals(typeOf(o)))
        continue;
      java.util.List<String> texts = texts(o);
      if (!texts.isEmpty() && texts.get(0).equals(text))
        return o.get("id").getAsString();
    }
    return null;
  }

  static JsonArray ops(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  static String typeOf(JsonObject o) {
    return o.has("type") && !o.get("type").isJsonNull() ? o.get("type").getAsString() : null;
  }

  static java.util.List<String> texts(JsonObject o) {
    java.util.List<String> r = new java.util.ArrayList<>();
    if (o.has("props") && o.getAsJsonObject("props").has("texts"))
      for (JsonElement t : o.getAsJsonObject("props").getAsJsonArray("texts"))
        r.add(t.getAsString());
    return r;
  }

  /** The id of the first widget of the type with the text (ampersands ignored) in the shell, or null. */
  static String widgetWithText(String shell, String type, String text) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(typeOf(o)))
        continue;
      java.util.List<String> t = texts(o);
      if (o.has("props") && o.getAsJsonObject("props").has("text") && !o.getAsJsonObject("props").get("text").isJsonNull())
        t.add(o.getAsJsonObject("props").get("text").getAsString());
      if (t.stream().anyMatch(x -> x.replace("&", "").equals(text)))
        return o.get("id").getAsString();
    }
    return null;
  }

  static String widget(String shell, String type) throws Exception {
    for (JsonElement e : ops(shell))
      if (type.equals(typeOf(e.getAsJsonObject())))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** Selects a tree item (a click of the user). */
  static void select(String tree, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "selection");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Opens the context menu of a control (the menu detect event of the webview). */
  static void contextMenu(String shell, String control) {
    JsonObject p = new JsonObject();
    p.addProperty("id", control);
    p.addProperty("type", "menudetect");
    JsonObject d = new JsonObject();
    d.addProperty("x", 0);
    d.addProperty("y", 0);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  /** Whether the widget of the shell is enabled (no "enabled": false property). */
  static boolean enabled(String shell, String id) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString()))
        return !(o.has("props") && o.getAsJsonObject("props").has("enabled")
            && !o.getAsJsonObject("props").get("enabled").getAsBoolean());
    }
    throw new AssertionError("no widget " + id);
  }

  static void doubleClick(String tree, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("id", tree);
    p.addProperty("type", "default");
    JsonObject d = new JsonObject();
    JsonArray sel = new JsonArray();
    sel.add(item);
    d.add("selection", sel);
    d.addProperty("item", item);
    p.add("data", d);
    h.notify("ui/event", p);
  }

  static void awaitTree(String shell, String text) throws Exception {
    h.await("the tree item " + text, v -> {
      try {
        return treeItem(shell, text) != null;
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** The panel shell of a compare editor opened after {@code before} openShell notifications. */
  static String comparePanel(int before) throws Exception {
    return h.awaitPanelShell(before).get("shell").getAsString();
  }

  @Test
  @Order(1)
  void twoZipFilesCompareTheEntriesThatDiffer() throws Exception {
    int before = h.notifications("ui/openShell").size();
    eachOther(file("a.zip"), file("b.zip"));
    zipShell = comparePanel(before);
    awaitTree(zipShell, "changed.txt");
    String snap = snapshot(zipShell);
    assertTrue(snap.contains("Zip Archive Compare"), "the structure viewer of the archives: " + snap);
    assertTrue(snap.contains("only-left.txt"), "an entry of one archive: " + snap);
    assertTrue(snap.contains("build"), "a folder of the differences: " + snap);
    assertFalse(snap.contains("same.txt"), "equal entries are not differences: " + snap);
    assertFalse(snap.contains("readme.txt"), "equal entries are not differences: " + snap);
  }

  @Test
  @Order(2)
  void doubleClickOfAChangedEntryOpensItsTextCompare() throws Exception {
    String tree = widget(zipShell, "tree");
    assertNotNull(tree);
    // the context menu of the structure viewer of the archives (DiffTreeViewer.fillContextMenu): Expand All, disabled
    // for an entry that is not a container
    select(tree, treeItem(zipShell, "changed.txt"));
    contextMenu(zipShell, tree);
    String expandAll = h.await2("Expand All", () -> widgetWithText(zipShell, "menuitem", "Expand All"));
    assertFalse(enabled(zipShell, expandAll), "Expand All of a file entry is disabled");
    assertNull(widgetWithText(zipShell, "menuitem", "Show Content Comparison"), snapshot(zipShell));

    int before = h.notifications("client/openDiff").size();
    doubleClick(tree, treeItem(zipShell, "changed.txt"));
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > before);
    JsonObject p = h.notifications("client/openDiff").get(before);
    JsonObject left = p.getAsJsonObject("left");
    assertEquals("changed.txt", left.get("name").getAsString(), p.toString());
    assertEquals("one\ntwo\nthree\n", h.call("compare/content", "id", left.get("id").getAsString()).getAsString());
    h.closeShell(zipShell);
  }

  @Test
  @Order(3)
  void theFilteredMembersLeaveOutTheEntriesOfArchivesAndFolders() throws Exception {
    CompareUIPlugin.getPreferenceStore().setValue(ComparePreferencePage.PATH_FILTER, "only-left.txt, build/");
    int before = h.notifications("ui/openShell").size();
    eachOther(file("a.zip"), file("b.zip"));
    String shell = comparePanel(before);
    awaitTree(shell, "changed.txt");
    String snap = snapshot(shell);
    assertFalse(snap.contains("only-left.txt"), "filtered entry: " + snap);
    assertTrue(snap.contains("changed.txt"), snap);
    h.closeShell(shell);

    int folders = h.notifications("ui/openShell").size();
    eachOther(file("fa"), file("fb"));
    String folderShell = comparePanel(folders);
    awaitTree(folderShell, "changed.txt");
    assertFalse(snapshot(folderShell).contains("only-left.txt"), "filtered file of the folders");
    assertFalse(snapshot(folderShell).contains("out.bin"), "filtered folder of the folders");
    h.closeShell(folderShell);
    CompareUIPlugin.getPreferenceStore().setValue(ComparePreferencePage.PATH_FILTER, "");

    int unfiltered = h.notifications("ui/openShell").size();
    eachOther(file("fa"), file("fb"));
    String all = comparePanel(unfiltered);
    awaitTree(all, "only-left.txt");
    assertTrue(snapshot(all).contains("out.bin"), "the folder without the filter");
    // the context menu of the tree of the folders (ResourceCompareInput.createDiffViewer): Show Content Comparison of
    // a file, disabled for a folder, and Expand All of the selected containers
    String tree = widget(all, "tree");
    select(tree, treeItem(all, "build"));
    contextMenu(all, tree);
    String show = h.await2("Show Content Comparison", () -> widgetWithText(all, "menuitem", "Show Content Comparison"));
    assertFalse(enabled(all, show), "Show Content Comparison of a folder is disabled");
    assertTrue(enabled(all, widgetWithText(all, "menuitem", "Expand All")), "Expand All of a folder");
    select(tree, treeItem(all, "changed.txt"));
    contextMenu(all, tree);
    h.await("Show Content Comparison enabled for a file", v -> {
      try {
        String id = widgetWithText(all, "menuitem", "Show Content Comparison");
        return id != null && enabled(all, id) && !enabled(all, widgetWithText(all, "menuitem", "Expand All"));
      } catch (Exception | AssertionError e) {
        return false;
      }
    });
    int shown = h.notifications("client/openDiff").size();
    JsonObject ev = new JsonObject();
    ev.addProperty("id", widgetWithText(all, "menuitem", "Show Content Comparison"));
    ev.addProperty("type", "selection");
    ev.add("data", new JsonObject());
    h.notify("ui/event", ev);
    h.await("client/openDiff of Show Content Comparison", v -> h.notifications("client/openDiff").size() > shown);
    assertEquals("changed.txt", h.notifications("client/openDiff").get(shown).getAsJsonObject("left").get("name").getAsString());
    h.closeShell(all);
  }

  @Test
  @Order(4)
  void compareWithOtherResourceComparesTheChosenFiles() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(file("x.txt"));
    nodes.add(file("y.txt"));
    p.add("nodes", nodes);
    h.call("selection/set", p);
    JsonObject run = new JsonObject();
    run.addProperty("command", COMPARE_WITH_OTHER);
    run.add("nodes", nodes);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = h.callAsync("command/execute", run);
    String dialog = h.awaitShell(before).get("shell").getAsString();
    String ok = h.await2("the OK button of the dialog", () -> widgetWithText(dialog, "button", "OK"));
    // the workspace fields (ancestor, left, right) are drag sources and drop targets ("Drag files from a view or
    // between dialog's fields."): the left field dragged onto the right one moves its path
    List<String> fields = h.await2("the workspace fields", () -> {
      List<String> r = dndFields(dialog);
      return r.size() == 3 ? r : null;
    });
    String left = fields.get(1), right = fields.get(2);
    assertEquals(new File(arch, "x.txt").getName(), new File(textOf(dialog, left)).getName());
    press(left, "dragstart", null);
    JsonObject drop = new JsonObject();
    drop.addProperty("source", left);
    drop.add("uris", new JsonArray());
    drop.addProperty("text", textOf(dialog, left));
    press(right, "drop", drop);
    JsonObject effect = new JsonObject();
    effect.addProperty("effect", "copy");
    press(left, "dragend", effect);
    h.await("the path moved to the right field", v -> {
      try {
        return textOf(dialog, right).endsWith("x.txt") && textOf(dialog, left).isEmpty();
      } catch (Exception e) {
        return false;
      }
    });
    // a file dragged from a VS Code view (its URI) onto the left field
    JsonObject fromView = new JsonObject();
    JsonArray uris = new JsonArray();
    uris.add(new File(arch, "y.txt").toURI().toString());
    fromView.add("uris", uris);
    fromView.addProperty("text", "");
    press(left, "drop", fromView);
    h.await("the dropped file in the left field", v -> {
      try {
        return textOf(dialog, left).endsWith("y.txt");
      } catch (Exception e) {
        return false;
      }
    });
    h.await("OK enabled", v -> {
      try {
        return enabled(dialog, ok);
      } catch (Exception e) {
        return false;
      }
    });
    JsonObject ev = new JsonObject();
    ev.addProperty("id", ok);
    ev.addProperty("type", "selection");
    ev.add("data", new JsonObject());
    int diffs = h.notifications("client/openDiff").size();
    h.notify("ui/event", ev);
    ran.get(60, TimeUnit.SECONDS);
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > diffs);
    assertEquals("Compare ('y.txt' - 'x.txt')", h.notifications("client/openDiff").get(diffs).get("title").getAsString());
  }

  /** The text fields of the shell that are drag sources (the workspace fields of the dialog), in order. */
  static List<String> dndFields(String shell) throws Exception {
    List<String> r = new java.util.ArrayList<>();
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if ("text".equals(typeOf(o)) && o.has("props") && o.getAsJsonObject("props").has("dragSource")
          && o.getAsJsonObject("props").get("dragSource").getAsBoolean()
          && o.getAsJsonObject("props").has("dropTarget"))
        r.add(o.get("id").getAsString());
    }
    return r;
  }

  /** The text of a text widget of the shell. */
  static String textOf(String shell, String id) throws Exception {
    for (JsonElement e : ops(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString()))
        return o.has("props") && o.getAsJsonObject("props").has("text") && !o.getAsJsonObject("props").get("text").isJsonNull()
            ? o.getAsJsonObject("props").get("text").getAsString() : "";
    }
    throw new AssertionError("no widget " + id);
  }

  /** The ids of the buttons of the shell with the text, in their order. */
  static java.util.List<String> buttons(String shell, String text) throws Exception {
    java.util.List<String> r = new java.util.ArrayList<>();
    for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("button".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props")
          && o.getAsJsonObject("props").has("text")
          && text.equals(o.getAsJsonObject("props").get("text").getAsString().replace("&", "")))
        r.add(o.get("id").getAsString());
    }
    return r;
  }

  static void press(String id, String type, JsonObject data) {
    JsonObject ev = new JsonObject();
    ev.addProperty("id", id);
    ev.addProperty("type", type);
    ev.add("data", data == null ? new JsonObject() : data);
    h.notify("ui/event", ev);
  }

  @Test
  @Order(5)
  void compareWithOtherResourceComparesAWorkspaceFolderWithAnExternalFolder() throws Exception {
    File dir = new File(arch, "dirA");
    dir.mkdirs();
    java.nio.file.Files.writeString(new File(dir, "same.txt").toPath(), "same\n");
    java.nio.file.Files.writeString(new File(dir, "changed.txt").toPath(), "one\n");
    File ext = new File(h.root, "externalDir");
    ext.mkdirs();
    java.nio.file.Files.writeString(new File(ext, "same.txt").toPath(), "same\n");
    java.nio.file.Files.writeString(new File(ext, "changed.txt").toPath(), "two\n");
    java.nio.file.Files.writeString(new File(ext, "added.txt").toPath(), "new\n");
    String folder = ExplorerService.RESOURCE_PREFIX + dir.getAbsolutePath();
    JsonObject p = new JsonObject();
    p.addProperty("source", "explorer");
    JsonArray nodes = new JsonArray();
    nodes.add(folder);
    p.add("nodes", nodes);
    h.call("selection/set", p);
    JsonObject run = new JsonObject();
    run.addProperty("command", COMPARE_WITH_OTHER);
    run.add("nodes", nodes);
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = h.callAsync("command/execute", run);
    String dialog = h.awaitShell(before).get("shell").getAsString();
    h.await("the sides of the dialog", v -> {
      try {
        return buttons(dialog, "External folder").size() == 3;
      } catch (Exception e) {
        return false;
      }
    });
    // the right side: External folder, Browse... chooses the external folder
    JsonObject sel = new JsonObject();
    sel.addProperty("selection", true);
    press(buttons(dialog, "External folder").get(2), "selection", sel);
    h.answer("client/directoryDialog", q -> ext.getAbsolutePath());
    Thread.sleep(300);
    press(buttons(dialog, "Browse...").get(8), "selection", null);
    String ok = h.await2("the OK button enabled", () -> {
      for (JsonElement e : h.call("ui/snapshot", "shell", dialog).getAsJsonObject().getAsJsonArray("ops")) {
        JsonObject o = e.getAsJsonObject();
        if (o.toString().contains("\"text\":\"OK\"") && !o.toString().contains("\"enabled\":false"))
          return o.get("id").getAsString();
      }
      return null;
    });
    int shells = h.notifications("ui/openShell").size();
    press(ok, "selection", null);
    ran.get(60, TimeUnit.SECONDS);
    JsonObject editor = h.awaitPanelShell(shells);
    assertEquals("Compare ('dirA' - 'externalDir')", editor.get("title").getAsString());
    String sid = editor.get("shell").getAsString();
    h.await("the differences of the folders", v -> {
      try {
        String snap = h.call("ui/snapshot", "shell", sid).toString();
        return snap.contains("changed.txt") && snap.contains("added.txt") && !snap.contains("same.txt");
      } catch (Exception e) {
        return false;
      }
    });
    h.closeShell(sid);
  }

  @Test
  @Order(6)
  void aChangedSideRefreshesTheStructureCompare() throws Exception {
    // StructureDiffViewer listens to the sides of its input (IContentChangeNotifier): new contents of a side are
    // compared again
    com.unitesk.requality.vscode.ui.resources.IResource a = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
        .getWorkspace().getRoot().findMember(new com.unitesk.requality.vscode.ui.runtime.Path("/arch/a.zip"));
    com.unitesk.requality.vscode.ui.resources.IResource b = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
        .getWorkspace().getRoot().findMember(new com.unitesk.requality.vscode.ui.runtime.Path("/arch/b.zip"));
    com.unitesk.requality.vscode.ui.compare.ResourceNode left = new com.unitesk.requality.vscode.ui.compare.ResourceNode(a);
    com.unitesk.requality.vscode.ui.compare.ResourceNode right = new com.unitesk.requality.vscode.ui.compare.ResourceNode(b);
    com.unitesk.requality.vscode.ui.compare.CompareConfiguration cc = new com.unitesk.requality.vscode.ui.compare.CompareConfiguration();
    com.unitesk.requality.vscode.ui.compare.CompareEditorInput input = new com.unitesk.requality.vscode.ui.compare.CompareEditorInput(cc) {
      @Override
      protected Object prepareInput(com.unitesk.requality.vscode.ui.runtime.IProgressMonitor monitor) {
        return new com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode(null,
            com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer.CHANGE, null, left, right);
      }
    };
    input.setTitle("Compare ('a.zip' - 'b.zip')");
    int before = h.notifications("ui/openShell").size();
    com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault()
        .asyncExec(() -> com.unitesk.requality.vscode.ui.compare.CompareUI.openCompareEditor(input));
    String shell = comparePanel(before);
    awaitTree(shell, "changed.txt");
    assertNotNull(treeItem(shell, "only-left.txt"));
    // the left side gets the contents of the right archive: its entries are those of the right one (the folders stay
    // changed elements: the structure creator gives them no contents, as in Eclipse)
    byte[] same = Files.readAllBytes(new File(arch, "b.zip").toPath());
    com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().asyncExec(() -> left.setContent(same));
    h.await("the refreshed structure compare", v -> {
      try {
        return treeItem(shell, "changed.txt") == null && treeItem(shell, "only-left.txt") == null;
      } catch (Exception e) {
        return false;
      }
    });
    h.closeShell(shell);
  }
}
