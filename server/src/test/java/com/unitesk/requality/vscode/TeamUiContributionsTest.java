package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.unitesk.requality.vscode.services.ExplorerService;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.core.Team;
import com.unitesk.requality.vscode.ui.team.wizards.ExportProjectSetLocationPage;

/**
 * The Team UI contributions of org.eclipse.team.ui that the port adds: the Team Project Set import and export wizards (the
 * Team category of Import... and Export...), the Team &gt; File Content and Team &gt; Models pages, the Synchronize view and
 * the Synchronize... command with its wizard.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamUiContributionsTest {
  static final String SYNC_VIEW = "requality.synchronize";
  static final String SYNC_VIEW_MENU = "org.eclipse.team.sync.views.SynchronizeView.viewMenu";
  static ServerHarness h;
  static String plainItem;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "plain");
    new File(plain, "docs").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "docs/readme.txt").toPath(), "text");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await("the plain project", v -> {
      try {
        return plainItemOf() != null;
      } catch (Exception e) {
        return false;
      }
    });
    plainItem = plainItemOf();
    assertNotNull(plainItem);
  }

  /** The explorer item of the plain project, or null. */
  static String plainItemOf() throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith("plain"))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
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

  static JsonArray snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops");
  }

  /** The text of the shell (its snapshot as JSON), for the content checks. */
  static String text(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The id of the first widget of the type with the text (mnemonics removed), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    for (JsonElement e : snapshot(shell)) {
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

  /** The last created widget of the type in the shell (the widgets of the current page), or null. */
  static String lastWidget(String shell, String type) throws Exception {
    String last = null;
    for (JsonElement e : snapshot(shell)) {
      JsonObject o = e.getAsJsonObject();
      if (type.equals(o.has("type") ? o.get("type").getAsString() : null))
        last = o.get("id").getAsString();
    }
    return last;
  }

  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button of " + text(shell));
    awaitEnabled(shell, b);
    event(b, "selection", null, null);
  }

  /**
   * Waits (up to 10 s) until the button is enabled: a running operation of the wizard (WizardDialog.run) disables the
   * buttons until it ends, as in Eclipse. A button that stays disabled is pressed anyway (the press is then ignored).
   */
  static void awaitEnabled(String shell, String id) throws Exception {
    long end = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < end) {
      boolean enabled = false;
      for (JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
        JsonObject o = e.getAsJsonObject();
        if (id.equals(o.get("id").getAsString()))
          enabled = !(o.has("props") && o.getAsJsonObject("props").has("enabled")
              && !o.getAsJsonObject("props").get("enabled").getAsBoolean());
      }
      if (enabled)
        return;
      Thread.sleep(50);
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

  /** Waits until a widget of the type with the text is in the shell. */
  static void awaitWidget(String shell, String type, String text) throws Exception {
    h.await(type + " " + text, v -> {
      try {
        return widget(shell, type, text) != null;
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** Waits until a message of the client (client/showMessage, the message dialogs of the server) has the text. */
  static void awaitClientMessage(String what, String text) throws Exception {
    try {
      h.await(what, v -> h.requests("client/showMessage").stream().anyMatch(m -> m.toString().contains(text)));
    } catch (AssertionError e) {
      StringBuilder sb = new StringBuilder();
      for (java.util.Map.Entry<String, JsonObject> r : h.clientRequests)
        sb.append("\n").append(r.getKey()).append(' ').append(r.getValue());
      throw new AssertionError(e.getMessage() + sb, e);
    }
  }

  /** The Preferences dialog on the page (the future ends when the dialog closes). */
  static CompletableFuture<JsonElement> openPage(String pageId) {
    JsonObject p = new JsonObject();
    p.addProperty("page", pageId);
    return h.callAsync("preferences/open", p);
  }

  /** Opens the Import (or Export) wizard dialog on the plain project. */
  static JsonObject openWizardSelection(String command, String title) throws Exception {
    int before = h.notifications("ui/openShell").size();
    execute(command, plainItem);
    return awaitShell(before, title);
  }

  @Test
  @Order(1)
  void importWizardsListTheTeamProjectSet() throws Exception {
    JsonObject shell = openWizardSelection("org.eclipse.ui.file.import", "Import");
    String sid = shell.get("shell").getAsString();
    String snap = text(sid);
    assertTrue(snap.contains("Team"), "Team category in the Import dialog");
    assertTrue(snap.contains("Team Project Set"), "Team Project Set in the Import dialog");
    press(sid, "Cancel");
  }

  @Test
  @Order(2)
  void exportWizardsListTheTeamProjectSet() throws Exception {
    JsonObject shell = openWizardSelection("org.eclipse.ui.file.export", "Export");
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("Team Project Set"), "Team Project Set in the Export dialog");
    press(sid, "Cancel");
  }

  @Test
  @Order(3)
  void importOfAProjectSetWithAMissingProviderTypeReportsTheError() throws Exception {
    File psf = new File(h.root, "missing-provider.psf");
    Files.writeString(psf.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<psf version=\"2.0\">"
        + "<provider id=\"org.example.missing\"><project reference=\"x\"/></provider></psf>\n");
    JsonObject shell = openWizardSelection("org.eclipse.ui.file.import", "Import");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Team Project Set"));
    press(sid, "Next >");
    awaitWidget(sid, "combo", null);
    event(widget(sid, "combo", null), "modify", "text", psf.getAbsolutePath());
    press(sid, "Finish");
    awaitClientMessage("the error of the missing provider type",
        "Projects for repository type org.example.missing could not be loaded as the type could not be found.");
  }

  @Test
  @Order(4)
  void exportPageShowsAnEmptyProjectList() throws Exception {
    JsonObject shell = openWizardSelection("org.eclipse.ui.file.export", "Export");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Team Project Set"));
    press(sid, "Next >");
    awaitWidget(sid, "label", "Select the projects to include in the Team Project Set:");
    assertNull(widget(sid, "row", "plain"), "no project can be exported without a repository provider");
    assertFalse(text(sid).contains("\"plain\""), text(sid));
    press(sid, "Cancel");
  }

  @Test
  @Order(5)
  void fileContentPageAddsAndRemovesAnExtensionMapping() throws Exception {
    assertEquals(Team.UNKNOWN, Team.getFileContentManager().getTypeForExtension("tuiprobe"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPage("org.eclipse.team.ui.TextPreferences");
    JsonObject prefs = h.awaitPanelShell(before);
    String pid = prefs.get("shell").getAsString();
    press(pid, "Add Extension...");
    JsonObject input = awaitShell(before, "Enter File Extension");
    String iid = input.get("shell").getAsString();
    event(lastWidget(iid, "text"), "modify", "text", "tuiprobe");
    press(iid, "OK");
    String prefsId = pid;
    h.await("the new extension row", v -> {
      try {
        return text(prefsId).contains("*.tuiprobe");
      } catch (Exception e) {
        return false;
      }
    });
    press(pid, "Apply and Close");
    f.get(60, TimeUnit.SECONDS);
    // a new extension is binary by default, as in Eclipse (FileTypeTable.Item)
    assertEquals(Team.BINARY, Team.getFileContentManager().getTypeForExtension("tuiprobe"));

    // the mapping is kept: the page shows it again, and Remove deletes it
    before = h.notifications("ui/openShell").size();
    f = openPage("org.eclipse.team.ui.TextPreferences");
    prefs = h.awaitPanelShell(before);
    pid = prefs.get("shell").getAsString();
    assertTrue(text(pid).contains("*.tuiprobe"), "the saved extension is listed again");
    String row = widget(pid, "row", "*.tuiprobe");
    assertNotNull(row, text(pid));
    selectItem(widget(pid, "table", null), row);
    press(pid, "Remove");
    press(pid, "Apply and Close");
    f.get(60, TimeUnit.SECONDS);
    assertEquals(Team.UNKNOWN, Team.getFileContentManager().getTypeForExtension("tuiprobe"));
  }

  @Test
  @Order(6)
  void theModelsPageListsTheWorkspaceModel() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPage("org.eclipse.team.ui.enabledModels");
    JsonObject prefs = h.awaitPanelShell(before);
    String pid = prefs.get("shell").getAsString();
    assertTrue(text(pid).contains("Workspace"), "the Workspace model in the Models page");
    press(pid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(7)
  void theSynchronizeViewOpensWithNoParticipants() throws Exception {
    int before = h.notifications("ui/openShell").size();
    h.call("views/open", "view", SYNC_VIEW);
    h.await("the Synchronize view", v -> h.notifications("ui/openShell").stream().skip(before)
        .anyMatch(n -> "view".equals(n.has("host") ? n.get("host").getAsString() : null)
            && SYNC_VIEW.equals(n.has("view") ? n.get("view").getAsString() : null)));
  }

  @Test
  @Order(8)
  void synchronizeCommandOpensTheWizardWithSynchronizeWithPatch() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.team.ui.synchronizeAll", null);
    JsonObject shell = awaitShell(before, "Synchronize");
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("Synchronize with Patch"), "Synchronize with Patch in the Synchronize wizard");
    press(sid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
  }

  /** The shell of the Synchronize view, or null while the view is not shown. */
  static String syncViewShell() {
    for (JsonObject n : h.notifications("ui/openShell"))
      if ("view".equals(n.has("host") ? n.get("host").getAsString() : null)
          && SYNC_VIEW.equals(n.has("view") ? n.get("view").getAsString() : null))
        return n.get("shell").getAsString();
    return null;
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

  /** The user activates the view (its webview gets the focus): its part becomes the active part. */
  static void focus(String shell) {
    JsonObject p = new JsonObject();
    p.addProperty("shell", shell);
    h.notify("ui/shellFocused", p);
  }

  /** The buttons of a control tree, depth first. */
  static void buttons(Control c, List<Button> out) {
    if (c instanceof Button b)
      out.add(b);
    if (c instanceof Composite composite)
      for (Control child : composite.getChildren())
        buttons(child, out);
  }

  @Test
  @Order(9)
  void synchronizeViewMenuDisablesTheParticipantActionsWithoutAParticipant() throws Exception {
    h.call("views/open", "view", SYNC_VIEW);
    JsonArray items = menuItems(SYNC_VIEW_MENU);
    for (String label : new String[] { "Pin Current Synchronization", "Link with Editor",
        "Remove Current Synchronization", "Remove Un-Pinned Synchronizations" }) {
      JsonObject item = menuItem(items, label);
      assertNotNull(item, label + " in the view menu of the Synchronize view");
      assertFalse(item.get("enabled").getAsBoolean(), label + " without a synchronize participant");
    }
  }

  @Test
  @Order(10)
  void pasteOfAPatchFromTheClipboardOpensTheApplyPatchWizard() throws Exception {
    h.call("views/open", "view", SYNC_VIEW);
    String view = syncViewShell();
    assertNotNull(view, "the Synchronize view is shown");
    h.answer("client/readClipboard", p -> ApplyPatchTest.DIFF);
    focus(view);
    int before = h.notifications("ui/openShell").size();
    // the command without nodes: the selection of the explorer is not changed, the view stays the active part
    JsonObject p = new JsonObject();
    p.addProperty("command", "org.eclipse.ui.edit.paste");
    CompletableFuture<JsonElement> ran = h.callAsync("command/execute", p);
    String sid = awaitShell(before, "Apply Patch").get("shell").getAsString();
    awaitWidget(sid, "button", "Next >");
    // the patch is known: the wizard starts with the target page of the pasted patch
    assertTrue(text(sid).contains("Select the target workspace resource for the patch."), "the target page of the pasted patch");
    press(sid, "Cancel");
    ran.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(11)
  void theModelsPageListsTheWorkspaceAndThePatchModels() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = openPage("org.eclipse.team.ui.enabledModels");
    String pid = h.awaitPanelShell(before).get("shell").getAsString();
    assertTrue(text(pid).contains("Workspace"), "the Workspace model in the Models page");
    assertTrue(text(pid).contains("Patch Contents"), "the patch model in the Models page");
    press(pid, "Cancel");
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(12)
  void exportWizardShowsTheWorkingSetsWhenExportWorkingSetsIsChecked() throws Exception {
    JsonObject shell = openWizardSelection("org.eclipse.ui.file.export", "Export");
    String sid = shell.get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Team Project Set"));
    press(sid, "Next >");
    awaitWidget(sid, "label", "Select the projects to include in the Team Project Set:");
    String check = widget(sid, "button", "Export working sets");
    assertNotNull(check, "the Export working sets check box: " + text(sid));
    event(check, "selection", "selection", true);
    awaitWidget(sid, "label", "Select the working sets to include in the Team Project Set:");
    event(check, "selection", "selection", false);
    awaitWidget(sid, "label", "Select the projects to include in the Team Project Set:");
    press(sid, "Cancel");
  }

  @Test
  @Order(13)
  void exportLocationPageOffersTheWorkspaceDestination() throws Exception {
    // the export needs a project with a repository provider to reach the location page: the page is checked directly
    Shell[] shell = new Shell[1];
    ExportProjectSetLocationPage[] page = new ExportProjectSetLocationPage[1];
    Display.getDefault().syncCall(() -> {
      shell[0] = new Shell(SWT.NONE);
      page[0] = new ExportProjectSetLocationPage("projectSetLocationPage", "Export a Team Project Set", null);
      page[0].createControl(shell[0]);
      return null;
    });
    List<Button> buttons = new ArrayList<>();
    buttons(shell[0], buttons);
    Button workspace = null;
    Button browse = null;
    for (Button b : buttons) {
      String t = b.getText() == null ? "" : b.getText().replace("&", "");
      if ("Workspace".equals(t))
        workspace = b;
      if ("Browse ...".equals(t) && workspace != null)
        browse = b;
    }
    assertNotNull(workspace, "the Workspace destination radio button");
    assertNotNull(browse, "the Browse ... button of the workspace destination");
    assertTrue(page[0].isSaveToFileSystem(), "the file system is the destination at first");
    Button ws = workspace;
    Display.getDefault().syncCall(() -> {
      ws.notifyListeners(SWT.Selection, new Event());
      return null;
    });
    assertFalse(page[0].isSaveToFileSystem(), "the workspace is the destination after Workspace is selected");
  }

  @Test
  @Order(14)
  void importPageStartsWithTheProjectSetFileSelectedInTheWorkbench() throws Exception {
    File psf = new File(plain, "selected.psf");
    Files.writeString(psf.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<psf version=\"2.0\"/>\n");
    // the command runs with the file selected in the explorer: the wizard starts with the selection of the workbench
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = execute("org.eclipse.ui.file.import", ExplorerService.RESOURCE_PREFIX + psf.getAbsolutePath());
    String sid = awaitShell(before, "Import").get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Team Project Set"));
    press(sid, "Next >");
    awaitWidget(sid, "combo", null);
    assertTrue(text(sid).contains(ServerHarness.jsonText(psf.getAbsolutePath())), "the selected project set file is the default: " + text(sid));
    press(sid, "Cancel");
    ran.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(15)
  void importPageStartsWithTheUrlOnTheClipboard() throws Exception {
    h.answer("client/readClipboard", p -> "https://example.org/projects.psf");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = execute("org.eclipse.ui.file.import", null);
    String sid = awaitShell(before, "Import").get("shell").getAsString();
    selectItem(widget(sid, "tree", null), widget(sid, "treeitem", "Team Project Set"));
    press(sid, "Next >");
    awaitWidget(sid, "combo", null);
    assertTrue(text(sid).contains("https://example.org/projects.psf"), "the URL of the clipboard is the input: " + text(sid));
    press(sid, "Cancel");
    ran.get(60, TimeUnit.SECONDS);
  }
}
