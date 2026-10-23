package com.unitesk.requality.vscode.ui.workbench.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ServerContext;

/**
 * The main menus of the product (File, Edit, Navigate, Project, Window, Help), their submenus and their items, as
 * declared in {@code main-menu.json}. The Menu Visibility page of Customize Perspective... shows this tree.
 *
 * <p>VSCODE: the menus of VS Code are static, so the visibility of each item of the perspective is published as the
 * context key {@code requality.menuHidden.<item id>}: true when the item is hidden in the perspective, false otherwise
 * (see {@link #publishHidden}).
 */
public final class MainMenu {
  /** The prefix of the context keys of the items: {@code requality.menuHidden.<item id>}. */
  public static final String CONTEXT_KEY_PREFIX = "requality.menuHidden.";

  /** The resource with the menus, on the class path. */
  private static final String RESOURCE = "com/unitesk/requality/vscode/ui/workbench/main-menu.json";

  /** A menu, a submenu or an item of the main menus. */
  public static final class Item {
    private final String id;
    private final String label;
    private final List<Item> items;

    Item(String id, String label, List<Item> items) {
      this.id = id;
      this.label = label;
      this.items = items;
    }

    /** The id of the menu or item. */
    public String getId() {
      return id;
    }

    /** The label of the menu or item. */
    public String getLabel() {
      return label;
    }

    /** The submenus and items of the menu; empty for an item. */
    public List<Item> getItems() {
      return Collections.unmodifiableList(items);
    }
  }

  private static List<Item> roots;

  private MainMenu() {
  }

  /** The menus of the product, in their order. */
  public static synchronized List<Item> menus() {
    if (roots == null)
      roots = load();
    return roots;
  }

  /** The ids of all menus, submenus and items, in the order of the tree. */
  public static List<String> allIds() {
    List<String> ids = new ArrayList<>();
    collect(menus(), ids);
    return ids;
  }

  /**
   * Publishes the visibility of the menu items for the perspective: the context key of each item is true when the item
   * is hidden by the customization of the perspective (none is hidden when the id is null: no perspective is shown).
   */
  public static void publishHidden(String perspectiveId) {
    if (ServerContext.server() == null)
      return;
    List<String> hidden = new ArrayList<>();
    PerspectiveCustomization.Customization c = PerspectiveCustomization.get(perspectiveId);
    if (c != null)
      hidden = c.getMenuHidden();
    for (String id : allIds())
      ServerContext.client().setContext(CONTEXT_KEY_PREFIX + id, hidden.contains(id));
  }

  private static void collect(List<Item> items, List<String> ids) {
    for (Item i : items) {
      ids.add(i.getId());
      collect(i.getItems(), ids);
    }
  }

  private static List<Item> load() {
    List<Item> r = new ArrayList<>();
    try (InputStream in = MainMenu.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (in == null)
        return r;
      JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
      for (JsonElement e : a)
        r.add(item(e.getAsJsonObject()));
    } catch (IOException | RuntimeException e) {
      RLogger.logError(e);
    }
    return r;
  }

  private static Item item(JsonObject o) {
    List<Item> children = new ArrayList<>();
    if (o.has("items") && o.get("items").isJsonArray())
      for (JsonElement e : o.getAsJsonArray("items"))
        children.add(item(e.getAsJsonObject()));
    return new Item(o.get("id").getAsString(), o.get("label").getAsString(), children);
  }
}
