package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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

/**
 * {@code org.eclipse.ui.dialogs.EditorSelectionDialog} opened by Open With &gt; Other... of a file of the Requality
 * Explorer: the radio buttons, the filter, the OK button, the internal and external editors (Browse... starts a
 * program with the file), and the remembered editor of the file name.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditorSelectionDialogTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static final String TITLE = "Editor Selection";
  static final String MESSAGE = "Choose the editor for opening a.txt:";
  static ServerHarness h;
  static File plain;
  static File file;
  static File notes;
  static File tool;
  static File marker;
  static String projectItem;
  static String fileItem;
  static String notesItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    plain = new File(h.workspace, "plain");
    plain.mkdirs();
    file = new File(plain, "a.txt");
    notes = new File(plain, "notes.md");
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects>"
        + "<buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(file.toPath(), "alpha");
    Files.writeString(notes.toPath(), "# notes");
    // an executable program: it writes the path of the file it is started with into the marker file
    marker = new File(h.root, "started.txt");
    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
      // a batch file on Windows (the system starts it with cmd)
      tool = new File(h.root, "tool.cmd");
      Files.writeString(tool.toPath(), "@echo %~1> \"" + marker.getAbsolutePath() + "\"\r\n");
    } else {
      tool = new File(h.root, "tool.sh");
      Files.writeString(tool.toPath(), "#!/bin/sh\necho \"$1\" > \"" + marker.getAbsolutePath() + "\"\n");
    }
    assertTrue(tool.setExecutable(true));
    h.initialize(h.workspace);
    h.await("the plain project", v -> {
      try {
        return itemOf("plain") != null;
      } catch (Exception e) {
        return false;
      }
    });
    projectItem = itemOf("plain");
    fileItem = childItem(projectItem, "a.txt");
    notesItem = childItem(projectItem, "notes.md");
    assertNotNull(fileItem);
    assertNotNull(notesItem);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  static String childItem(String parent, String label) throws Exception {
    for (JsonElement e : h.children(parent))
      if (e.getAsJsonObject().get("label").getAsString().equals(label))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The explorer popup of the items: the menu items as the server describes them ({@code menu/items}). */
  static JsonArray popup(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The item with the label (searching the submenus), or null. */
  static JsonObject item(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()))
        return o;
      if (o.has("children")) {
        JsonObject found = item(o.getAsJsonArray("children"), label);
        if (found != null)
          return found;
      }
    }
    return null;
  }

  /** Runs the Open With &gt; Other... of the file: the dialog opens, the request returns when it closes. */
  static CompletableFuture<JsonElement> openWithOther(String fileItemId) throws Exception {
    JsonObject openWith = item(popup(fileItemId), "Open With");
    assertNotNull(openWith);
    JsonObject other = item(openWith.getAsJsonArray("children"), "Other...");
    assertNotNull(other);
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", other.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  /** The shell of the Editor Selection dialog opened after {@code before} openShell notifications. */
  static String awaitDialog(int before) throws Exception {
    String[] shell = new String[1];
    h.await("the Editor Selection dialog", v -> {
      List<JsonObject> l = h.notifications("ui/openShell");
      for (int k = before; k < l.size(); k++)
        if (TITLE.equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null)) {
          shell[0] = l.get(k).get("shell").getAsString();
          return true;
        }
      return false;
    });
    return shell[0];
  }

  /** The texts of a widget: its text, or the texts of its items (tree and table items). */
  static List<String> texts(JsonObject props) {
    List<String> r = new ArrayList<>();
    if (props.has("text") && !props.get("text").isJsonNull())
      r.add(props.get("text").getAsString());
    if (props.has("texts"))
      for (JsonElement t : props.getAsJsonArray("texts"))
        if (!t.isJsonNull())
          r.add(t.getAsString());
    return r;
  }

  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (text == null)
        return o.get("id").getAsString();
      for (String t : texts(props))
        if (text.equals(t.replace("&", "")))
          return o.get("id").getAsString();
    }
    return null;
  }

  /** The labels of the tree items of the shell. */
  static List<String> treeLabels(String shell) throws Exception {
    List<String> r = new ArrayList<>();
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("treeitem".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props"))
        r.addAll(texts(o.getAsJsonObject("props")));
    }
    return r;
  }

  /** Whether the button of the shell is enabled (the snapshot of the button says so). */
  static boolean enabled(String shell, String button) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("button".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props")
          && button.equals(o.getAsJsonObject("props").get("text").getAsString().replace("&", "")))
        return !o.getAsJsonObject("props").has("enabled") || o.getAsJsonObject("props").get("enabled").getAsBoolean();
    }
    throw new AssertionError(button + " button of " + shell);
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

  /** Selects one item of the tree widget of the shell. */
  static void selectItem(String tree, String item) {
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

  /** Presses a button of the shell by its text. */
  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button");
    event(b, "selection", null, null);
  }

  /** Clicks a radio button or a check box of the shell. */
  static void check(String shell, String text, boolean checked) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button");
    event(b, "selection", "selection", checked);
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
      throw new AssertionError(e.getMessage() + ": " + h.call("ui/snapshot", "shell", shell), e);
    }
  }

  static int openFiles() {
    return h.notifications("client/openFile").size();
  }

  /** The editor of the last client/openFile notification for the file. */
  static String lastOpenedEditor(File f) throws Exception {
    List<JsonObject> l = h.notifications("client/openFile");
    for (int k = l.size() - 1; k >= 0; k--)
      if (f.getCanonicalPath().equals(new File(l.get(k).get("path").getAsString()).getCanonicalPath()))
        return l.get(k).get("editor").getAsString();
    return null;
  }

  @Test
  @Order(1)
  void opensWithTheTitleMessageAndRadioButtons() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openWithOther(fileItem);
    String sid = awaitDialog(before);
    awaitWidget(sid, "button", "Internal editors");
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    assertTrue(snap.contains(TITLE), snap);
    assertTrue(snap.contains(MESSAGE), snap);
    for (String s : new String[] { "Internal editors", "External programs", "Browse...",
        "Use this editor for all 'a.txt' files", "Use it for all '*.txt' files" })
      assertTrue(snap.contains(s), s + " in " + snap);
    assertNotNull(widget(sid, "button", "Internal editors"));
    assertNotNull(widget(sid, "button", "External programs"));
    press(sid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(2)
  void theFilterNarrowsTheInternalEditors() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openWithOther(fileItem);
    String sid = awaitDialog(before);
    awaitWidget(sid, "treeitem", "Text Editor");
    String filter = widget(sid, "text", null);
    assertNotNull(filter);
    event(filter, "modify", "text", "zzz-no-such-editor");
    h.await("no editor matches", v -> {
      try {
        return treeLabels(sid).isEmpty();
      } catch (Exception e) {
        return false;
      }
    });
    event(filter, "modify", "text", "text");
    h.await("the text editor matches", v -> {
      try {
        return treeLabels(sid).contains("Text Editor");
      } catch (Exception e) {
        return false;
      }
    });
    for (String label : treeLabels(sid))
      assertTrue(label.toLowerCase().matches("(^|.*\\W)text.*"), label + " does not match the filter");
    press(sid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(3)
  void okIsDisabledWithoutASelectionAndOpensTheTextEditor() throws Exception {
    int before = h.notifications("ui/openShell").size();
    int opened = openFiles();
    CompletableFuture<JsonElement> f = openWithOther(fileItem);
    String sid = awaitDialog(before);
    awaitWidget(sid, "treeitem", "Text Editor");
    // External programs: no program is suggested for the file, so nothing is selected and OK is disabled
    check(sid, "External programs", true);
    h.await("the external programs", v -> {
      try {
        return treeLabels(sid).contains("System Editor");
      } catch (Exception e) {
        return false;
      }
    });
    assertFalse(enabled(sid, "OK"));
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "System Editor"));
    h.await("OK enabled by the selection", v -> {
      try {
        return enabled(sid, "OK");
      } catch (Exception e) {
        return false;
      }
    });
    // Internal editors again, the text editor: OK opens the file in it (VS Code: the default editor)
    check(sid, "Internal editors", true);
    h.await("the internal editors", v -> {
      try {
        return treeLabels(sid).contains("Text Editor");
      } catch (Exception e) {
        return false;
      }
    });
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Text Editor"));
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("the file opened in the text editor", v -> openFiles() > opened);
    assertEquals("default", lastOpenedEditor(file));
  }

  @Test
  @Order(4)
  void systemEditorOfTheExternalProgramsOpensTheFile() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openWithOther(fileItem);
    String sid = awaitDialog(before);
    check(sid, "External programs", true);
    h.await("the system editor", v -> {
      try {
        return treeLabels(sid).contains("System Editor");
      } catch (Exception e) {
        return false;
      }
    });
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "System Editor"));
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("the file opened in the system editor", v -> "system".equals(lastOpenedEditorOrNull(file)));
  }

  static String lastOpenedEditorOrNull(File f) {
    try {
      return lastOpenedEditor(f);
    } catch (Exception e) {
      return null;
    }
  }

  @Test
  @Order(5)
  void browseStartsTheChosenProgramWithTheFile() throws Exception {
    h.answer("client/fileDialog", p -> {
      JsonArray a = new JsonArray();
      a.add(tool.getAbsolutePath());
      return a;
    });
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openWithOther(fileItem);
    String sid = awaitDialog(before);
    check(sid, "External programs", true);
    press(sid, "Browse...");
    awaitWidget(sid, "treeitem", tool.getName());
    h.await("OK enabled by the program", v -> {
      try {
        return enabled(sid, "OK");
      } catch (Exception e) {
        return false;
      }
    });
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("the program ran with the file", v -> marker.isFile());
    assertEquals(file.getCanonicalPath(), new File(Files.readString(marker.toPath()).trim()).getCanonicalPath());
  }

  @Test
  @Order(6)
  void theRememberedEditorOpensTheFileNextTime() throws Exception {
    int before = h.notifications("ui/openShell").size();
    int opened = openFiles();
    CompletableFuture<JsonElement> f = openWithOther(notesItem);
    String sid = awaitDialog(before);
    awaitWidget(sid, "button", "Use this editor for all 'notes.md' files");
    check(sid, "External programs", true);
    h.await("the system editor", v -> {
      try {
        return treeLabels(sid).contains("System Editor");
      } catch (Exception e) {
        return false;
      }
    });
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "System Editor"));
    check(sid, "Use this editor for all 'notes.md' files", true);
    press(sid, "OK");
    f.get(60, TimeUnit.SECONDS);
    h.await("the file opened in the system editor", v -> openFiles() > opened);
    assertEquals("system", lastOpenedEditor(notes));
    // Open (Enter on the file) uses the editor remembered for the file name
    int n = openFiles();
    JsonObject p = new JsonObject();
    JsonArray nodes = new JsonArray();
    nodes.add(notesItem);
    p.add("nodes", nodes);
    h.call("explorer/open", p);
    h.await("Open uses the remembered editor", v -> openFiles() > n);
    assertEquals("system", lastOpenedEditor(notes));
  }
}
