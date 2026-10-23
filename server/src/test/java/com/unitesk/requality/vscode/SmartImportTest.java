package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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

/**
 * The smart import of org.eclipse.ui.ide ("Projects from Folder or Archive", SmartImportWizard): the Open Projects from File
 * System command, the Configure and Detect Nested Projects command of the explorer popup, and the import wizard list.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmartImportTest {
  static final String POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  static final String OPEN_DIRECTORY = "org.eclipse.e4.ui.importer.openDirectory";
  static final String CONFIGURE = "org.eclipse.e4.ui.importer.configureProject";
  static final String WIZARD_TITLE = "Import Projects from File System or Archive";
  static final String CONFIGURE_LABEL = "Configure and Detect Nested Projects...";
  static ServerHarness h;
  static File plain;
  static String plainItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "plain");
    new File(plain, "docs").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), project("plain"));
    Files.writeString(new File(plain, "docs/readme.txt").toPath(), "text");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    plainItem = itemOf("plain");
    assertNotNull(plainItem);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static String project(String name) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<projectDescription><name>" + name
        + "</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n";
  }

  /** The explorer item whose label starts with the name, or null. */
  static String itemOf(String name) throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith(name))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The created widget of a shell with the type and text (first match), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (text == null)
        return o.get("id").getAsString();
      List<String> texts = new java.util.ArrayList<>();
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

  /** The wizard dialog shell with the title, opened after the given count of opened shells. */
  static JsonObject awaitWizardShell(int before, String title) throws Exception {
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

  static CompletableFuture<JsonElement> execute(String command, String item) {
    JsonObject p = new JsonObject();
    p.addProperty("command", command);
    JsonArray nodes = new JsonArray();
    if (item != null)
      nodes.add(item);
    p.add("nodes", nodes);
    return h.callAsync("command/execute", p);
  }

  /** The items of the explorer popup of the node (a submenu has its items as children). */
  static JsonArray popup(String item) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    nodes.add(item);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  static String label(JsonObject o) {
    return o.has("label") ? o.get("label").getAsString() : null;
  }

  /** The text of every widget of the shell, for the checks of the content. */
  static String text(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** Presses a button of the shell by its text. */
  static void press(String shell, String text) throws Exception {
    String b = widget(shell, "button", text);
    assertNotNull(b, text + " button");
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

  @Test
  @Order(1)
  void popupOfAnOpenProjectHasTheConfigureSubmenu() throws Exception {
    JsonObject configure = null;
    for (JsonElement e : popup(plainItem)) {
      JsonObject o = e.getAsJsonObject();
      if ("Configure".equals(label(o)) && o.has("children"))
        configure = o;
    }
    assertNotNull(configure, "Configure submenu of the popup of plain " + popup(plainItem));
    boolean found = false;
    for (JsonElement c : configure.getAsJsonArray("children"))
      if (CONFIGURE_LABEL.equals(label(c.getAsJsonObject())))
        found = c.getAsJsonObject().get("enabled").getAsBoolean();
    assertTrue(found, "Configure and Detect Nested Projects... enabled in the Configure submenu");
  }

  @Test
  @Order(2)
  void importWizardListsProjectsFromFolderOrArchive() throws Exception {
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute("org.eclipse.ui.file.import", plainItem);
    JsonObject shell = awaitWizardShell(before, "Import");
    String sid = shell.get("shell").getAsString();
    assertTrue(text(sid).contains("Projects from Folder or Archive"), "Projects from Folder or Archive in the Import dialog");
    h.closeShell(sid);
    f.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(3)
  void openProjectsFromFileSystemImportsTheEclipseProjectsOfAFolder() throws Exception {
    File src = new File(h.root, "projects-src");
    File alpha = new File(src, "alpha");
    File beta = new File(alpha, "nested/beta");
    new File(alpha, "src").mkdirs();
    new File(beta, "src").mkdirs();
    Files.writeString(new File(alpha, ".project").toPath(), project("alpha"));
    Files.writeString(new File(alpha, "src/x.txt").toPath(), "x");
    Files.writeString(new File(beta, ".project").toPath(), project("beta"));
    Files.writeString(new File(beta, "src/y.txt").toPath(), "y");

    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute(OPEN_DIRECTORY, null);
    JsonObject shell = awaitWizardShell(before, WIZARD_TITLE);
    String sid = shell.get("shell").getAsString();
    h.answer("client/directoryDialog", p -> src.getAbsolutePath());
    press(sid, "Directory...");
    h.await("the proposals of the folder", v -> {
      try {
        return text(sid).contains("beta");
      } catch (Exception e) {
        return false;
      }
    });
    press(sid, "Select All");
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    h.await("alpha and beta imported", v -> h.server().workspace().project(alpha) != null
        && h.server().workspace().project(beta) != null);
    assertNotNull(h.server().workspace().project(alpha));
    assertNotNull(h.server().workspace().project(beta));
  }

  @Test
  @Order(4)
  void configureAndDetectNestedProjectsOpensTheWizardOnTheProjectFolder() throws Exception {
    File gamma = new File(plain, "nested/gamma");
    new File(gamma, "src").mkdirs();
    Files.writeString(new File(gamma, ".project").toPath(), project("gamma"));
    Files.writeString(new File(gamma, "src/z.txt").toPath(), "z");

    JsonObject configure = null;
    for (JsonElement e : popup(plainItem)) {
      JsonObject o = e.getAsJsonObject();
      if ("Configure".equals(label(o)) && o.has("children"))
        configure = o;
    }
    assertNotNull(configure, "Configure submenu of the popup of plain");
    JsonObject command = null;
    for (JsonElement c : configure.getAsJsonArray("children"))
      if (CONFIGURE_LABEL.equals(label(c.getAsJsonObject())))
        command = c.getAsJsonObject();
    assertNotNull(command, CONFIGURE_LABEL);
    assertEquals(true, command.get("enabled").getAsBoolean());

    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> f = execute(CONFIGURE, plainItem);
    JsonObject shell = awaitWizardShell(before, WIZARD_TITLE);
    String sid = shell.get("shell").getAsString();
    h.await("the nested project of plain", v -> {
      try {
        return text(sid).contains("gamma");
      } catch (Exception e) {
        return false;
      }
    });
    press(sid, "Finish");
    f.get(60, TimeUnit.SECONDS);
    h.await("gamma imported", v -> h.server().workspace().project(gamma) != null);
    assertNotNull(h.server().workspace().project(gamma));
  }

  /**
   * A path relative to the root of the proposals as the wizard shows it (the separator of the system, as Eclipse labels
   * the folders with their absolute paths), in the JSON text of a snapshot.
   */
  static String rel(String... names) {
    return ServerHarness.jsonText(String.join(File.separator, names));
  }

  /** Opens the Open Projects from File System wizard on the folder, and waits for its proposals. */
  static String openProposals(File folder) throws Exception {
    int before = h.notifications("ui/openShell").size();
    execute(OPEN_DIRECTORY, null);
    JsonObject shell = awaitWizardShell(before, WIZARD_TITLE);
    String sid = shell.get("shell").getAsString();
    h.answer("client/directoryDialog", p -> folder.getAbsolutePath());
    press(sid, "Directory...");
    h.await("the proposals of " + folder.getName(), v -> {
      try {
        return text(sid).contains("\"" + rel(folder.getName(), ""));
      } catch (Exception e) {
        return false;
      }
    });
    return sid;
  }

  /** Sets the text of a text widget (the filter text) as the user types it. */
  static void typeText(String text, String value) {
    event(text, "modify", "text", value);
  }

  @Test
  @Order(5)
  void proposalsAreACheckboxTreeWithFolderAndImportAsColumns() throws Exception {
    File src = new File(h.root, "tree-src");
    new File(src, "one/src").mkdirs();
    new File(src, "two/src").mkdirs();
    Files.writeString(new File(src, "one/.project").toPath(), project("one"));
    Files.writeString(new File(src, "two/.project").toPath(), project("two"));

    String sid = openProposals(src);
    String snap = text(sid);
    for (String s : new String[] { "Folder", "Import as", "Hide already open projects", "Select All", "Deselect All",
        "type filter text" })
      assertTrue(snap.contains(s), s + " in the proposals");
    assertTrue(snap.contains("\"" + rel("tree-src", "one") + "\"") && snap.contains("\"" + rel("tree-src", "two") + "\""),
        "the folders relative to the root");
    assertTrue(snap.contains(" of ") && snap.contains(" selected"), "the selection count");
    h.closeShell(sid);

    // the known sources of the wizard are kept in the workbench dialog settings (the section of Eclipse's wizard)
    com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings section = com.unitesk.requality.vscode.port.eclipse.Activator
        .getDefault().getDialogSettings().getSection("SmartImportWizard");
    assertNotNull(section, "SmartImportWizard section of the dialog settings");
    assertTrue(java.util.Arrays.asList(section.getArray("com.unitesk.requality.vscode.ui.ide.datatransfer.SmartImportRootWizardPage.knownSources"))
        .contains(new File(h.root, "projects-src").getAbsolutePath()), "imported source remembered");

    // the folders imported by the previous test (alpha, beta and their parent) are shown as already imported
    String sid2 = openProposals(new File(h.root, "projects-src"));
    assertTrue(text(sid2).contains("Folder already imported as project"), "folders imported by the previous test are shown as imported " + text(sid2));
    h.closeShell(sid2);
  }

  @Test
  @Order(6)
  void theFilterTextFiltersTheProposals() throws Exception {
    File src = new File(h.root, "tree-src");
    String sid = openProposals(src);
    String filter = widget(sid, "text", null);
    assertNotNull(filter, "filter text of the proposals");
    typeText(filter, "two");
    h.await("the filtered proposals", v -> {
      try {
        String s = text(sid);
        return s.contains(rel("tree-src", "two")) && !s.contains("\"" + rel("tree-src", "one") + "\"");
      } catch (Exception e) {
        return false;
      }
    });
    typeText(filter, "");
    h.await("the unfiltered proposals", v -> {
      try {
        return text(sid).contains("\"" + rel("tree-src", "one") + "\"");
      } catch (Exception e) {
        return false;
      }
    });
    h.closeShell(sid);
  }

  @Test
  @Order(7)
  void theWorkingSetsAreAGroupWithASelectButton() throws Exception {
    File src = new File(h.root, "tree-src");
    String sid = openProposals(src);
    String snap = text(sid).replace("&", "");
    for (String s : new String[] { "Working sets", "Add project to working sets", "Working sets:", "Select...", "New..." })
      assertTrue(snap.contains(s), s + " in the working sets group");
    String enable = widget(sid, "button", "Add project to working sets");
    assertNotNull(enable, "Add project to working sets check box");
    event(enable, "selection", "selection", true);
    int before = h.notifications("ui/openShell").size();
    press(sid, "Select...");
    JsonObject dialog = awaitWizardShell(before, "Select Working Sets");
    h.closeShell(dialog.get("shell").getAsString());
    h.closeShell(sid);
  }
}
