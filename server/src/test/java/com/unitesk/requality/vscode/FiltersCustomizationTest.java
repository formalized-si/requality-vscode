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
 * "Filters and Customization" of the Requality Explorer (CommonNavigatorActionGroup FilterActionGroup,
 * CommonFilterSelectionDialog): the pre-set filters of the Requality plug-in and of org.eclipse.ui.navigator.resources,
 * the user filters, the content extensions, their effect on the explorer, the settings they are kept in, and Recent
 * Filters.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FiltersCustomizationTest {
  static final String VIEW_MENU = "com.unitesk.requality.views.RequalityExplorer.viewMenu";
  static ServerHarness h;
  static File plain;
  static String plainItem;
  static String catalogItem;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    plain = new File(h.workspace, "plain");
    plain.mkdirs();
    Files.writeString(new File(plain, ".project").toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>plain</name><comment></comment><projects></projects><buildSpec></buildSpec><natures></natures></projectDescription>\n");
    Files.writeString(new File(plain, "A.class").toPath(), "x");
    Files.writeString(new File(plain, "notes.txt").toPath(), "x");
    Files.writeString(new File(plain, "draft.tmp").toPath(), "x");
    h.initialize(h.workspace);
    h.awaitProjects(1);
    for (JsonElement e : h.call("explorer/roots", null).getAsJsonArray()) {
      String label = e.getAsJsonObject().get("label").getAsString();
      if (label.equals("plain"))
        plainItem = e.getAsJsonObject().get("id").getAsString();
      else if (label.startsWith("TS103096"))
        catalogItem = e.getAsJsonObject().get("id").getAsString();
    }
    assertNotNull(plainItem);
    assertNotNull(catalogItem);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static List<String> labels(JsonArray items) {
    List<String> r = new ArrayList<>();
    for (JsonElement e : items)
      r.add(e.getAsJsonObject().get("label").getAsString());
    return r;
  }

  static JsonArray viewMenu() throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", VIEW_MENU);
    return h.call("menu/items", p).getAsJsonArray();
  }

  static JsonObject find(JsonArray items, String label) {
    for (JsonElement e : items) {
      JsonObject o = e.getAsJsonObject();
      if (o.has("label") && label.equals(o.get("label").getAsString()))
        return o;
      if (o.has("children")) {
        JsonObject c = find(o.getAsJsonArray("children"), label);
        if (c != null)
          return c;
      }
    }
    return null;
  }

  static CompletableFuture<JsonElement> runViewMenu(String label) throws Exception {
    JsonObject it = find(viewMenu(), label);
    assertNotNull(it, label + " in " + viewMenu());
    JsonObject p = new JsonObject();
    p.addProperty("menu", VIEW_MENU);
    p.addProperty("index", it.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  /** The widget of the shell with the type and text (first match), or null. */
  static String widget(String shell, String type, String text) throws Exception {
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    for (JsonElement e : snap.getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject props = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      String t = props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString()
          : props.has("texts") && props.getAsJsonArray("texts").size() > 0 && !props.getAsJsonArray("texts").get(0).isJsonNull()
              ? props.getAsJsonArray("texts").get(0).getAsString() : null;
      if (text == null || text.equals(t) || t != null && text.equals(t.replace("&", "")))
        return o.get("id").getAsString();
    }
    return null;
  }

  /** The settings the extension would get back (the last client/updateSetting of the key). */
  static JsonElement lastSetting(String key) {
    JsonElement r = null;
    for (JsonObject n : h.notifications("client/updateSetting"))
      if (key.equals(n.get("key").getAsString()))
        r = n.get("value");
    return r;
  }

  @Test
  @Order(1)
  void theDialogShowsThePresetFiltersUserFiltersAndContent() throws Exception {
    assertNotNull(find(viewMenu(), "Filters and Customization..."), viewMenu().toString());
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> run = runViewMenu("Filters and Customization...");
    JsonObject shell = h.awaitPanelShell(before);
    assertEquals("Filters and Customization", shell.get("title").getAsString());
    String sid = shell.get("shell").getAsString();
    String snap = h.call("ui/snapshot", "shell", sid).toString();
    for (String s : new String[] { "Pre-set filters", "User filters", "Content",
        "Select the filters to apply (matching items will be hidden):", "Document Filter", "Types Filter",
        "Resource in Requality Project Filter", ".* resources", "*.class resources", "Closed projects",
        "Requality Database", "non Requality project provider", "Resources" })
      assertTrue(snap.contains(s), s + " in " + snap);
    // filters not shown in the UI are not listed
    assertFalse(snap.contains("Working Set\""), snap);
    assertFalse(snap.contains("User defined filters"), snap);
    h.closeShell(sid);
    run.get(60, TimeUnit.SECONDS);
  }

  @Test
  @Order(2)
  void checkingAPresetFilterHidesItsResourcesAndIsKept() throws Exception {
    assertTrue(labels(h.children(plainItem)).contains("A.class"));
    int before = h.notifications("ui/openShell").size();
    CompletableFuture<JsonElement> run = runViewMenu("Filters and Customization...");
    String sid = h.awaitPanelShell(before).get("shell").getAsString();
    // the row of "*.class resources" is checked (a check event of the table for the item)
    String table = widget(sid, "table", null);
    String row = widget(sid, "row", "*.class resources");
    assertNotNull(table, h.call("ui/snapshot", "shell", sid).toString());
    assertNotNull(row, h.call("ui/snapshot", "shell", sid).toString());
    JsonObject p = new JsonObject();
    p.addProperty("id", table);
    p.addProperty("type", "check");
    JsonObject d = new JsonObject();
    d.addProperty("item", row);
    d.addProperty("checked", true);
    p.add("data", d);
    h.notify("ui/event", p);
    Thread.sleep(300);
    h.pressDefaultButton(sid);
    run.get(60, TimeUnit.SECONDS);
    h.await("the .class file hidden", v -> {
      try {
        return !labels(h.children(plainItem)).contains("A.class");
      } catch (Exception e) {
        return false;
      }
    });
    JsonElement setting = lastSetting("explorer.filters");
    assertNotNull(setting);
    assertTrue(setting.getAsJsonObject().get("endsWithClass").getAsBoolean(), setting.toString());
    // "Recent Filters" offers the filter changed last
    JsonObject recent = find(viewMenu(), "Recent Filters");
    assertNotNull(recent, viewMenu().toString());
    assertTrue(recent.toString().contains("*.class resources"), recent.toString());
  }

  @Test
  @Order(3)
  void theSettingsComeBackAndUserFiltersHideResources() throws Exception {
    JsonObject p = new JsonObject();
    JsonObject filters = new JsonObject();
    filters.addProperty("endsWithClass", false);
    p.add("filters", filters);
    JsonArray user = new JsonArray();
    JsonObject f = new JsonObject();
    f.addProperty("pattern", "*.tmp");
    f.addProperty("enabled", true);
    user.add(f);
    p.add("userFilters", user);
    h.call("explorer/setFilters", p);
    List<String> files = labels(h.children(plainItem));
    assertTrue(files.contains("A.class"), files.toString());
    assertFalse(files.contains("draft.tmp"), files.toString());
    assertTrue(files.contains("notes.txt"), files.toString());
  }

  @Test
  @Order(4)
  void contentExtensionsCanBeDeactivated() throws Exception {
    assertTrue(h.children(catalogItem).size() > 0);
    JsonObject p = new JsonObject();
    JsonObject content = new JsonObject();
    content.addProperty("com.unitesk.requality.eclipse.CMFContent", false);
    p.add("contentExtensions", content);
    h.call("explorer/setFilters", p);
    assertEquals(0, h.children(catalogItem).size(), "no Requality content: the catalog shows no nodes");
    content.addProperty("com.unitesk.requality.eclipse.CMFContent", true);
    h.call("explorer/setFilters", p);
    assertTrue(h.children(catalogItem).size() > 0);
  }
}
