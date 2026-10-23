package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import com.unitesk.requality.vscode.ui.compare.ReplaceWithEditionAction;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The local history and clipboard actions of org.eclipse.compare (Compare With, Replace With and Restore from Local
 * History...) on a plain project: the file hist.txt has the states v2 and v1 in its local history (its contents are v3),
 * the deleted file dir/gone.txt has a state.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompareEditionsTest {
  static final String POPUP = ProjectOperationsTest.POPUP;
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "Editions");
    new File(plain, "dir").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Editions</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "hist.txt").toPath(), "v1\n");
    Files.writeString(new File(plain, "dir/gone.txt").toPath(), "gone\n");
    h.initialize(h.workspace);
    h.awaitProjects(0);
    h.await2("the editions project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Editions"))
          return e;
      return null;
    });
    // hist.txt: v1 -> v2 -> v3, each replacement keeps the replaced contents in the local history
    replaceKeepingHistory(new File(plain, "hist.txt"), "v2\n");
    replaceKeepingHistory(new File(plain, "hist.txt"), "v3\n");
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String file(String name) {
    return ExplorerService.RESOURCE_PREFIX + new File(plain, name).getAbsolutePath();
  }

  static IFile workspaceFile(String name) {
    return (IFile) ResourcesPlugin.getWorkspace().getRoot().findMember(new Path("/Editions/" + name));
  }

  /** Replaces the contents of the file with KEEP_HISTORY (the replaced contents become a state). */
  static void replaceKeepingHistory(File f, String contents) throws Exception {
    ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(f.getAbsolutePath()))
        .setContents(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), IResource.KEEP_HISTORY, null);
  }

  /** The explorer popup of the items (the top-level items with their enabled state, index and children). */
  static JsonArray popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The item with the label in the list (searching the submenus), or null. */
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

  static JsonObject submenu(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (label.equals(o.has("label") ? o.get("label").getAsString() : null) && o.has("children"))
        return o;
    }
    return null;
  }

  /** Runs the item of the popup request; the answer comes when the action ends. */
  static CompletableFuture<JsonElement> runAsync(JsonObject item) {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  /** The text of the widget (of the type) with the text, from a snapshot of the shell. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      if (text == null)
        return o.get("id").getAsString();
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (props.has("text") && !props.get("text").isJsonNull() && text.equals(props.get("text").getAsString()))
        return o.get("id").getAsString();
      if (props.has("texts"))
        for (JsonElement t : props.getAsJsonArray("texts"))
          if (!t.isJsonNull() && text.equals(t.getAsString()))
            return o.get("id").getAsString();
    }
    return null;
  }

  @Test
  @Order(1)
  void popupHasTheEditionAndClipboardActions() throws Exception {
    h.answer("client/readClipboard", p -> "clip text\n");
    JsonArray items = popup(file("hist.txt"));
    JsonObject replace = submenu(items, "Replace With");
    assertNotNull(replace, "Replace With submenu");
    assertNotNull(item(replace.getAsJsonArray("children"), "Previous from Local History"));
    JsonObject replaceClipboard = item(replace.getAsJsonArray("children"), "Clipboard");
    assertNotNull(replaceClipboard, "Replace With > Clipboard");
    JsonObject compare = submenu(items, "Compare With");
    assertNotNull(compare, "Compare With submenu");
    JsonObject compareClipboard = item(compare.getAsJsonArray("children"), "Clipboard");
    assertNotNull(compareClipboard, "Compare With > Clipboard");
    assertTrue(compareClipboard.get("enabled").getAsBoolean(), "Compare With > Clipboard with a clipboard text");
  }

  @Test
  @Order(2)
  void previousFromLocalHistoryRestoresThePreviousState() throws Exception {
    assertEquals("v3\n", Files.readString(new File(plain, "hist.txt").toPath()));
    JsonObject previous = item(submenu(popup(file("hist.txt")), "Replace With").getAsJsonArray("children"),
        "Previous from Local History");
    assertNotNull(previous);
    assertTrue(previous.get("enabled").getAsBoolean());
    runAsync(previous).get(60, TimeUnit.SECONDS);
    assertEquals("v2\n", Files.readString(new File(plain, "hist.txt").toPath()));
  }

  @Test
  @Order(3)
  void compareWithClipboardSendsTheClipboardAsAContent() throws Exception {
    h.answer("client/readClipboard", p -> "clip text\n");
    int before = h.notifications("client/openDiff").size();
    JsonObject clipboard = item(submenu(popup(file("hist.txt")), "Compare With").getAsJsonArray("children"), "Clipboard");
    assertNotNull(clipboard);
    runAsync(clipboard).get(60, TimeUnit.SECONDS);
    h.await("the clipboard compare", v -> h.notifications("client/openDiff").size() > before);
    JsonObject p = h.notifications("client/openDiff").get(before);
    assertEquals("Compare hist.txt and Clipboard", p.get("title").getAsString());
    JsonObject right = p.getAsJsonObject("right");
    assertEquals("Clipboard", right.get("label").getAsString());
    assertEquals("clip text\n", h.call("compare/content", "id", right.get("id").getAsString()).getAsString());
  }

  @Test
  @Order(4)
  void replaceWithClipboardReplacesTheContents() throws Exception {
    h.answer("client/readClipboard", p -> "clipped\n");
    IFile f = workspaceFile("hist.txt");
    int states = f.getHistory(null).length;
    JsonObject clipboard = item(submenu(popup(file("hist.txt")), "Replace With").getAsJsonArray("children"), "Clipboard");
    assertNotNull(clipboard);
    assertTrue(clipboard.get("enabled").getAsBoolean());
    runAsync(clipboard).get(60, TimeUnit.SECONDS);
    assertEquals("clipped\n", Files.readString(new File(plain, "hist.txt").toPath()));
    // ClipboardReplace sets the contents with IResource.FORCE only: no state is added to the local history
    assertEquals(states, workspaceFile("hist.txt").getHistory(null).length);
    // with a text editor of the file that has a selection, the selection is replaced in the editor's document
    h.answer("client/activeTextEditor", p -> {
      JsonObject e = new JsonObject();
      e.addProperty("path", new File(plain, "hist.txt").getAbsolutePath());
      e.addProperty("selectionStart", 0);
      e.addProperty("selectionEnd", 4);
      return e;
    });
    int requests = h.clientRequests.size();
    runAsync(item(submenu(popup(file("hist.txt")), "Replace With").getAsJsonArray("children"), "Clipboard"))
        .get(60, TimeUnit.SECONDS);
    JsonObject replace = null;
    for (int k = requests; k < h.clientRequests.size(); k++)
      if (h.clientRequests.get(k).getKey().equals("client/replaceText"))
        replace = h.clientRequests.get(k).getValue();
    assertNotNull(replace, "client/replaceText");
    assertEquals(0, replace.get("start").getAsInt());
    assertEquals(4, replace.get("end").getAsInt());
    assertEquals("clipped\n", replace.get("text").getAsString());
    h.answer("client/activeTextEditor", p -> null);
  }

  @Test
  @Order(5)
  void replaceWithLocalHistoryDialogReplacesWithTheSelectedEdition() throws Exception {
    // the action on the file selection (the Team action of the popup overrides it)
    ReplaceWithEditionAction action = new ReplaceWithEditionAction();
    Action ia = new Action() {
    };
    int before = h.notifications("ui/openShell").size();
    IFile f = workspaceFile("hist.txt");
    String mostRecent = new String(f.getHistory(null)[0].getContents().readAllBytes(), "UTF-8");
    Display.getDefault().asyncExec(() -> {
      action.selectionChanged(ia, new StructuredSelection(f));
      action.run(ia);
    });
    JsonObject shell = h.awaitShell(before);
    String sid = shell.get("shell").getAsString();
    assertEquals("Replace from Local History", shell.get("title").getAsString());
    // the most recent state is the selected edition
    h.await("the edition dialog", v -> {
      try {
        return widget(sid, "tree", null) != null;
      } catch (Exception e) {
        return false;
      }
    });
    // the editions (local history states) show the time image of the compare plug-in (timeIcon); Eclipse's dateIcon
    // (obj16/day_obj) is not an image of the plug-in, so the days have none
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    // the image paths in portable form (Windows separates the names of a path with a backslash)
    assertTrue(snap.replace("\\\\", "/").contains("obj16/resource_obj.png"), snap);
    assertFalse(snap.contains("day_obj"), snap);
    h.pressDefaultButton(sid);
    h.await("the replaced file", v -> {
      try {
        return mostRecent.equals(Files.readString(new File(plain, "hist.txt").toPath()));
      } catch (java.io.IOException e) {
        return false;
      }
    });
  }

  @Test
  @Order(6)
  void restoreFromLocalHistoryRestoresADeletedFile() throws Exception {
    File gone = new File(plain, "dir/gone.txt");
    ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(Path.fromOSString(gone.getAbsolutePath()))
        .delete(IResource.KEEP_HISTORY, null);
    assertFalse(gone.exists());
    JsonObject restore = item(popup(ExplorerService.RESOURCE_PREFIX + new File(plain, "dir").getAbsolutePath()),
        "Restore from Local History...");
    assertNotNull(restore, "Restore from Local History... of a folder");
    assertTrue(restore.get("enabled").getAsBoolean());
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(restore);
    JsonObject shell = h.awaitShell(before);
    String sid = shell.get("shell").getAsString();
    h.await("the deleted file is listed", v -> {
      try {
        return widget(sid, "row", "gone.txt") != null;
      } catch (Exception e) {
        return false;
      }
    });
    JsonObject d = new JsonObject();
    d.addProperty("item", widget(sid, "row", "gone.txt"));
    d.addProperty("checked", true);
    JsonObject check = new JsonObject();
    check.addProperty("id", widget(sid, "table", null));
    check.addProperty("type", "check");
    check.add("data", d);
    h.notify("ui/event", check);
    h.pressDefaultButton(sid);
    ran.get(60, TimeUnit.SECONDS);
    h.await("the restored file", v -> gone.exists());
    assertEquals("gone\n", Files.readString(gone.toPath()));
  }
}
