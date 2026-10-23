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
import com.unitesk.requality.vscode.services.ExplorerService;

/**
 * The Team actions of the Requality Explorer (org.eclipse.team.ui and org.eclipse.compare popupMenus): Compare With &gt;
 * Each Other of two files (a two-way compare in the VS Code diff editor), of three files (the common ancestor is asked,
 * then a three-way compare), of two folders (the structure compare) and of two Requality nodes (the node viewer);
 * Team &gt; Share Project... opens the Share Project wizard; Team &gt; Import Project Set... of a .psf file reports the
 * repository type that cannot be found.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TeamActionsTest {
  static final String POPUP = ProjectOperationsTest.POPUP;
  static ServerHarness h;
  static File plain;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "Plain");
    new File(plain, "dirA").mkdirs();
    new File(plain, "dirB").mkdirs();
    Files.writeString(new File(plain, ".project").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription><name>Plain</name><comment></comment>"
            + "<projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "a.txt").toPath(), "one\ntwo\n");
    Files.writeString(new File(plain, "b.txt").toPath(), "one\n2\n");
    Files.writeString(new File(plain, "c.txt").toPath(), "one\ntwo\nthree\n");
    Files.writeString(new File(plain, "dirA/x.txt").toPath(), "x1\n");
    Files.writeString(new File(plain, "dirA/only-a.txt").toPath(), "only\n");
    Files.writeString(new File(plain, "dirB/x.txt").toPath(), "x2\n");
    Files.writeString(new File(plain, "set.psf").toPath(),
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<psf version=\"2.0\">"
            + "<provider id=\"org.example.missing.provider\"><project reference=\"unknown\"/></provider></psf>\n");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    h.await2("the plain project", () -> {
      for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
        if (e.getAsJsonObject().get("label").getAsString().startsWith("Plain"))
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

  static String plainItem() throws Exception {
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray())
      if (e.getAsJsonObject().get("label").getAsString().startsWith("Plain"))
        return e.getAsJsonObject().get("id").getAsString();
    return null;
  }

  /** The explorer popup of the items: the top-level items (label, enabled, index, children). */
  static JsonArray popup(String... items) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    JsonArray nodes = new JsonArray();
    for (String i : items)
      nodes.add(i);
    p.add("nodes", nodes);
    return h.call("menu/items", p).getAsJsonArray();
  }

  /** The item with the label, searching the submenus; null when there is none. */
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

  /** Runs the item of the last popup request and waits for the action to end. */
  static void run(JsonObject item) throws Exception {
    runAsync(item).get(60, TimeUnit.SECONDS);
  }

  static CompletableFuture<JsonElement> runAsync(JsonObject item) {
    JsonObject p = new JsonObject();
    p.addProperty("menu", POPUP);
    p.addProperty("index", item.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  /** The Compare With > Each Other item of the popup of the items, or null. */
  static JsonObject eachOther(String... items) throws Exception {
    JsonArray popup = popup(items);
    JsonObject compare = submenu(popup, "Compare With");
    assertNotNull(compare, "Compare With submenu");
    return item(compare.getAsJsonArray("children"), "Each Other");
  }

  /** The title of the last client/openDiff notification sent after {@code before} notifications. */
  static JsonObject openDiffAfter(int before) throws Exception {
    h.await("client/openDiff", v -> h.notifications("client/openDiff").size() > before);
    return h.notifications("client/openDiff").get(before);
  }

  static boolean showMessageContains(int before, String text) {
    for (int k = before; k < h.clientRequests.size(); k++)
      if (h.clientRequests.get(k).getKey().equals("client/showMessage")
          && h.clientRequests.get(k).getValue().toString().contains(text))
        return true;
    return false;
  }

  static String snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).toString();
  }

  /** The snapshot of the shell for a condition: empty while the shell cannot be read yet. */
  static String snapshotQuietly(String shell) {
    try {
      return snapshot(shell);
    } catch (Exception e) {
      return "";
    }
  }

  @Test
  @Order(1)
  void twoFilesCompareEachOtherInTheDiffEditor() throws Exception {
    JsonObject each = eachOther(file("a.txt"), file("b.txt"));
    assertNotNull(each, "Each Other of two files");
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of two files is enabled");
    int before = h.notifications("client/openDiff").size();
    run(each);
    JsonObject p = openDiffAfter(before);
    assertEquals("Compare ('Plain/a.txt' - 'Plain/b.txt')", p.get("title").getAsString());
    assertEquals(new File(plain, "a.txt").getAbsolutePath(), p.getAsJsonObject("left").get("path").getAsString());
    assertEquals(new File(plain, "b.txt").getAbsolutePath(), p.getAsJsonObject("right").get("path").getAsString());
    assertEquals("Plain/a.txt", p.getAsJsonObject("left").get("label").getAsString());
  }

  @Test
  @Order(2)
  void threeFilesAskTheCommonAncestorThenCompareInAPanel() throws Exception {
    JsonObject each = eachOther(file("a.txt"), file("b.txt"), file("c.txt"));
    assertNotNull(each, "Each Other of three files");
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of three files is enabled");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(each);
    JsonObject dialog = h.awaitShell(before);
    String sid = dialog.get("shell").getAsString();
    h.await("the Select Common Ancestor dialog", v -> snapshotQuietly(sid).contains("Select Common Ancestor"));
    assertTrue(snapshot(sid).contains("Plain/c.txt"), "the choice of the ancestors names the files");
    h.pressDefaultButton(sid);
    ran.get(60, TimeUnit.SECONDS);
    JsonObject panel = h.awaitPanelShell(before + 1);
    assertTrue(panel.get("title").getAsString().startsWith("Compare ('"), panel.toString());
    assertTrue(panel.get("title").getAsString().contains("- 'Plain/b.txt' -"), "three-way title: " + panel);
    h.closeShell(panel.get("shell").getAsString());
  }

  @Test
  @Order(3)
  void twoFoldersOpenTheStructureCompare() throws Exception {
    JsonObject each = eachOther(file("dirA"), file("dirB"));
    assertNotNull(each, "Each Other of two folders");
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of two folders is enabled");
    int before = h.notifications("ui/openShell").size();
    run(each);
    JsonObject panel = h.awaitPanelShell(before);
    String sid = panel.get("shell").getAsString();
    h.await("the structure compare", v -> {
      String snap = snapshotQuietly(sid);
      return snap.contains("Structure Compare") && snap.contains("only-a.txt") && snap.contains("x.txt");
    });
    String snap = snapshot(sid);
    assertTrue(panel.get("title").getAsString().contains("dirA"), panel.toString());
    h.closeShell(sid);
  }

  @Test
  @Order(4)
  void twoRequalityNodesOpenTheNodeViewer() throws Exception {
    JsonArray docs = h.call("nodes/list", "project", "TS103096", "types", List.of("Requirement")).getAsJsonArray();
    assertTrue(docs.size() >= 2, "two requirements of the catalog");
    String a = docs.get(0).getAsJsonObject().get("id").getAsString();
    String b = docs.get(1).getAsJsonObject().get("id").getAsString();
    JsonObject each = eachOther(a, b);
    assertNotNull(each, "Each Other of two nodes");
    assertTrue(each.get("enabled").getAsBoolean(), "Each Other of two nodes is enabled");
    int before = h.notifications("ui/openShell").size();
    run(each);
    // the .json type has its own content merge viewer: the compare is shown in a panel, not in the diff editor
    JsonObject panel = h.awaitPanelShell(before);
    assertTrue(panel.get("title").getAsString().contains(".json"), panel.toString());
    h.closeShell(panel.get("shell").getAsString());
  }

  @Test
  @Order(5)
  void eachOtherIsNotEnabledForOneFile() throws Exception {
    JsonObject each = eachOther(file("a.txt"));
    assertTrue(each == null || !each.get("enabled").getAsBoolean(), "Each Other of one file");
  }

  @Test
  @Order(6)
  void shareProjectOpensTheWizard() throws Exception {
    JsonArray items = popup(plainItem());
    JsonObject share = item(items, "Share Project...");
    assertNotNull(share, "Share Project... of a project");
    assertTrue(share.get("enabled").getAsBoolean(), "Share Project... of a project without a provider");
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> ran = runAsync(share);
    JsonObject shell = h.awaitShell(before);
    String sid = shell.get("shell").getAsString();
    h.await("the Share Project wizard", v -> snapshotQuietly(sid).contains("Select a repository type:"));
    assertTrue(snapshot(sid).contains("Share Project"));
    h.closeShell(sid);
    ran.get(60, TimeUnit.SECONDS);
  }

  /** The index of the top-level item with the label, or -1. */
  static int topLevel(JsonArray items, String label) {
    for (int i = 0; i < items.size(); i++) {
      JsonObject o = items.get(i).getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()))
        return i;
    }
    return -1;
  }

  @Test
  @Order(7)
  void importProjectSetReportsTheMissingRepositoryType() throws Exception {
    JsonArray items = popup(file("set.psf"));
    int team = topLevel(items, "Team");
    int imp = topLevel(items, "Import Project Set...");
    assertTrue(team >= 0, "Team at the top level");
    assertEquals(team + 1, imp, "Import Project Set... is the top-level item right after Team");
    int before = h.clientRequests.size();
    run(item(items, "Import Project Set..."));
    h.await("the import error", v -> showMessageContains(before, "An error occurred importing this project set."));
    assertTrue(showMessageContains(before, "could not be found"), "the missing repository type is reported");
  }

  /** A repository type of Share Project for the tests: one page when {@code withPage}. */
  public static class TestConfigurationWizard extends com.unitesk.requality.vscode.ui.jface.wizard.Wizard
      implements com.unitesk.requality.vscode.ui.team.ui.IConfigurationWizard {
    final boolean withPage;
    com.unitesk.requality.vscode.ui.resources.IProject project;

    TestConfigurationWizard(boolean withPage) {
      this.withPage = withPage;
    }

    @Override
    public void init(com.unitesk.requality.vscode.ui.workbench.IWorkbench workbench,
        com.unitesk.requality.vscode.ui.resources.IProject project) {
      this.project = project;
    }

    @Override
    public void addPages() {
      if (withPage)
        addPage(new com.unitesk.requality.vscode.ui.jface.wizard.WizardPage("test") {
          @Override
          public void createControl(com.unitesk.requality.vscode.ui.swt.widgets.Composite parent) {
          }
        });
    }

    @Override
    public boolean performFinish() {
      return true;
    }
  }

  /** A configuration element of org.eclipse.team.ui.configurationWizards whose class is the wizard. */
  static com.unitesk.requality.vscode.ui.runtime.IConfigurationElement wizardElement(TestConfigurationWizard[] made,
      boolean withPage) {
    return (com.unitesk.requality.vscode.ui.runtime.IConfigurationElement) java.lang.reflect.Proxy.newProxyInstance(
        TeamActionsTest.class.getClassLoader(),
        new Class<?>[] { com.unitesk.requality.vscode.ui.runtime.IConfigurationElement.class }, (proxy, m, args) -> {
          switch (m.getName()) {
            case "createExecutableExtension":
              made[0] = new TestConfigurationWizard(withPage);
              return made[0];
            case "getName":
              return "wizard";
            case "getAttribute":
              return "name".equals(args[0]) ? "Test Repository" : null;
            default:
              return null;
          }
        });
  }

  @Test
  @Order(8)
  void aSingleRepositoryTypeWithPagesIsOpenedDirectly() throws Exception {
    // ConfigureProjectWizard.shareProjects uses the only wizard registered unless it doesn't have any pages
    // (ConfigurationWizardElement.wizardHasPages / createExecutableExtension)
    com.unitesk.requality.vscode.ui.resources.IProject project =
        com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace().getRoot().getProjects()[0];
    TestConfigurationWizard[] made = new TestConfigurationWizard[1];
    var withPages = wizardElement(made, true);
    assertTrue(com.unitesk.requality.vscode.ui.team.wizards.ConfigureProjectWizard.wizardHasPages(withPages,
        new com.unitesk.requality.vscode.ui.resources.IProject[] { project }));
    assertEquals(project, made[0].project, "the wizard is initialized with the project to share");
    var noPages = wizardElement(made, false);
    org.junit.jupiter.api.Assertions.assertFalse(com.unitesk.requality.vscode.ui.team.wizards.ConfigureProjectWizard
        .wizardHasPages(noPages, new com.unitesk.requality.vscode.ui.resources.IProject[] { project }));
    // a wizard without IConfigurationWizardExtension shares one project only
    org.junit.jupiter.api.Assertions.assertNull(com.unitesk.requality.vscode.ui.team.wizards.ConfigureProjectWizard
        .createExecutableExtension(withPages, new com.unitesk.requality.vscode.ui.resources.IProject[] { project, project }));
  }
}
