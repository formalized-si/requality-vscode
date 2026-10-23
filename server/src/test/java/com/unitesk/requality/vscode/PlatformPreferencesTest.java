package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;

/**
 * The platform preference pages that the port implements (Window &gt; Preferences): General, General &gt; Workspace,
 * General &gt; Workspace &gt; Local History, General &gt; Compare/Patch, Team and Team &gt; Ignored Resources.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformPreferencesTest {
  static ServerHarness h;
  static String plainDir;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    // An Eclipse project without the catalog nature, with a file and a folder.
    File plain = new File(h.workspace, "Plain");
    new File(plain, "sub").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "sub/b.txt").toPath(), "b");
    h.initialize(h.workspace);
    plainDir = plain.getAbsolutePath();
    h.await("the plain project", v -> {
      try {
        return ResourcesPlugin.getWorkspace().getRoot().getProject("Plain").exists();
      } catch (Exception e) {
        return false;
      }
    });
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** The Preferences dialog on the page (or the first one); the future ends when the dialog closes. */
  static CompletableFuture<JsonElement> openPreferences(String pageId) {
    JsonObject p = new JsonObject();
    if (pageId != null)
      p.addProperty("page", pageId);
    return h.callAsync("preferences/open", p);
  }

  static JsonArray snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  /** The widget of the type with the text (mnemonics removed), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      String t = props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
      if (text == null || text.equals(t) || t != null && text.equals(t.replace("&", "")))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The ids of the widgets of the type, in the order they were created. */
  static List<String> widgets(String shell, String type) throws Exception {
    List<String> r = new ArrayList<>();
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("op") && "create".equals(o.get("op").getAsString()) && type.equals(o.get("type").getAsString()))
        r.add(o.get("id").getAsString());
    }
    return r;
  }

  static void event(String id, String type, String key, Object value) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    JsonObject d = new JsonObject();
    d.add(key, com.unitesk.requality.vscode.rpc.JsonRpcConnection.toJson(value));
    p.add("data", d);
    h.notify("ui/event", p);
  }

  static JsonObject awaitDialog(int before, CompletableFuture<JsonElement> call) throws Exception {
    try {
      return h.awaitPanelShell(before);
    } catch (AssertionError e) {
      if (call.isDone())
        throw new AssertionError("the call ended without a dialog: " + call.getNow(null), e);
      throw e;
    }
  }

  /** The combo whose items include the value (the encoding list of the Workspace page), or null. */
  static String comboWith(String shell, String value) throws Exception {
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (!"combo".equals(o.has("type") ? o.get("type").getAsString() : null) || !o.has("props"))
        continue;
      JsonObject props = o.getAsJsonObject("props");
      if (props.has("items"))
        for (JsonElement i : props.getAsJsonArray("items"))
          if (value.equals(i.getAsString()))
            return o.get("id").getAsString();
    }
    return null;
  }

  static List<String> charsetNames() {
    return new ArrayList<>(new TreeSet<>(Charset.availableCharsets().keySet()));
  }

  @Test
  @Order(1)
  void theTreeHasThePlatformPagesNextToTheRequalityOnes() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences(null);
    JsonObject shell = awaitDialog(before, f);
    String sid = shell.get("shell").getAsString();
    String list = widget(sid, "list", null);
    assertNotNull(list, h.call("ui/snapshot", "shell", sid).toString());
    List<String> labels = new ArrayList<>();
    for (JsonElement e : snapshot(sid)) {
      JsonObject o = e.getAsJsonObject();
      if ("list".equals(o.has("type") ? o.get("type").getAsString() : null) && o.has("props")
          && o.getAsJsonObject("props").has("items"))
        for (JsonElement i : o.getAsJsonObject("props").getAsJsonArray("items"))
          labels.add(i.getAsString().trim());
    }
    for (String want : new String[] { "General", "Workspace", "Build", "Local History", "Compare/Patch", "Team",
        "Ignored Resources", "Requality" })
      assertTrue(labels.contains(want), labels.toString());
    h.closeShell(sid);
    assertEquals(false, f.get(60, TimeUnit.SECONDS).getAsBoolean());
  }

  @Test
  @Order(2)
  void maximumEntriesPerFileLimitsTheLocalHistory() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences("org.eclipse.ui.preferencePages.FileStates");
    JsonObject shell = awaitDialog(before, f);
    String sid = shell.get("shell").getAsString();
    // the texts of the page in order: days to keep files, maximum entries per file, maximum file size (MB)
    List<String> texts = widgets(sid, "text");
    assertEquals(3, texts.size(), h.call("ui/snapshot", "shell", sid).toString());
    event(texts.get(1), "modify", "text", "2");
    h.pressDefaultButton(sid);
    assertEquals(true, f.get(60, TimeUnit.SECONDS).getAsBoolean());
    assertEquals(2, ResourcesPlugin.getWorkspace().getDescription().getMaxFileStates());

    IProject plain = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    IFile file = plain.getFile("h.txt");
    file.create(new ByteArrayInputStream("v0".getBytes(StandardCharsets.UTF_8)), true, null);
    for (int i = 1; i <= 4; i++)
      file.setContents(new ByteArrayInputStream(("v" + i).getBytes(StandardCharsets.UTF_8)), IResource.KEEP_HISTORY, null);
    assertEquals(2, file.getHistory(null).length, "the states kept of a file changed 4 times");
  }

  @Test
  @Order(3)
  void singleClickOpenModeIsSentToVsCode() throws Exception {
    int notified = h.notifications("client/updateSetting").size();
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences("org.eclipse.ui.preferencePages.Workbench");
    JsonObject shell = awaitDialog(before, f);
    String sid = shell.get("shell").getAsString();
    String single = widget(sid, "button", "Single click");
    assertNotNull(single, h.call("ui/snapshot", "shell", sid).toString());
    event(single, "selection", "selection", true);
    h.pressDefaultButton(sid);
    assertEquals(true, f.get(60, TimeUnit.SECONDS).getAsBoolean());
    h.await("the open mode setting", v -> {
      List<JsonObject> l = h.notifications("client/updateSetting");
      return l.size() > notified;
    });
    List<JsonObject> l = h.notifications("client/updateSetting");
    JsonObject last = l.get(l.size() - 1);
    assertEquals("workbench.list.openMode", last.get("key").getAsString(), last.toString());
    assertEquals("singleClick", last.get("value").getAsString(), last.toString());
  }

  @Test
  @Order(5)
  void buildAutomaticallyOnTheBuildPageTurnsTheAutoBuildOff() throws Exception {
    assertTrue(ResourcesPlugin.getWorkspace().isAutoBuilding());
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences("org.eclipse.ui.preferencePages.BuildOrder");
    JsonObject shell = awaitDialog(before, f);
    String sid = shell.get("shell").getAsString();
    String auto = widget(sid, "button", "Build automatically");
    assertNotNull(auto, h.call("ui/snapshot", "shell", sid).toString());
    event(auto, "selection", "selection", false);
    h.pressDefaultButton(sid);
    assertEquals(true, f.get(60, TimeUnit.SECONDS).getAsBoolean());
    assertEquals(false, ResourcesPlugin.getWorkspace().isAutoBuilding());
    // back on for the tests that follow
    ResourcesPlugin.getWorkspace().getDescription().setAutoBuilding(true);
  }

  @Test
  @Order(4)
  void otherEncodingOfTheWorkspaceIsTheDefaultOfFilesWithoutOwnCharset() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPreferences("org.eclipse.ui.preferencePages.Workspace");
    JsonObject shell = awaitDialog(before, f);
    String sid = shell.get("shell").getAsString();
    String other = widget(sid, "button", "Other:");
    assertNotNull(other, h.call("ui/snapshot", "shell", sid).toString());
    event(other, "selection", "selection", true);
    String combo = comboWith(sid, "ISO-8859-1");
    assertNotNull(combo, h.call("ui/snapshot", "shell", sid).toString());
    event(combo, "selection", "index", charsetNames().indexOf("ISO-8859-1"));
    h.pressDefaultButton(sid);
    assertEquals(true, f.get(60, TimeUnit.SECONDS).getAsBoolean());
    assertEquals("ISO-8859-1", ResourcesPlugin.getWorkspace().getDescription().getEncoding());
    IProject plain = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    // sub/b.txt has no charset of its own: it takes the default of the workspace
    assertEquals("ISO-8859-1", plain.getFile("sub/b.txt").getCharset());
    // restore the default for the other tests of this JVM
    ResourcesPlugin.getWorkspace().getDescription().setEncoding("UTF-8");
  }
}
