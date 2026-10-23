package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;

/**
 * The customization of perspectives made with Window &gt; Perspective &gt; Customize Perspective...: the items of the New,
 * Open Perspective and Show View submenus, and the menu items hidden. The customization of each perspective is kept by
 * its id in the workbench preferences ({@link WorkbenchPreferences#PERSPECTIVE_CUSTOMIZATIONS}), so it survives a restart
 * and applies whenever the perspective is shown. Reset Perspective removes it, and so does deleting a saved perspective.
 */
public final class PerspectiveCustomization {
  /** The customization of one perspective: the ids of the checked shortcuts and of the hidden menu items. */
  public static final class Customization {
    private final List<String> newWizards;
    private final List<String> perspectives;
    private final List<String> showViews;
    private final List<String> menuHidden;

    public Customization(List<String> newWizards, List<String> perspectives, List<String> showViews,
        List<String> menuHidden) {
      this.newWizards = new ArrayList<>(newWizards);
      this.perspectives = new ArrayList<>(perspectives);
      this.showViews = new ArrayList<>(showViews);
      this.menuHidden = new ArrayList<>(menuHidden);
    }

    /** The ids of the checked New submenu shortcuts (wizards). */
    public List<String> getNewWizards() {
      return new ArrayList<>(newWizards);
    }

    /** The ids of the checked Open Perspective submenu shortcuts. */
    public List<String> getPerspectives() {
      return new ArrayList<>(perspectives);
    }

    /** The ids of the checked Show View submenu shortcuts (views). */
    public List<String> getShowViews() {
      return new ArrayList<>(showViews);
    }

    /** The ids of the menu items that are hidden. */
    public List<String> getMenuHidden() {
      return new ArrayList<>(menuHidden);
    }
  }

  private static final String NEW_WIZARDS = "newWizards";
  private static final String PERSPECTIVES = "perspectives";
  private static final String SHOW_VIEWS = "showViews";
  private static final String MENU_HIDDEN = "menuHidden";

  private PerspectiveCustomization() {
  }

  /** The customization of the perspective, or null if it has none (its layout and shortcuts are the defaults). */
  public static synchronized Customization get(String perspectiveId) {
    JsonObject all = load();
    if (perspectiveId == null || !all.has(perspectiveId) || !all.get(perspectiveId).isJsonObject())
      return null;
    JsonObject o = all.getAsJsonObject(perspectiveId);
    return new Customization(strings(o, NEW_WIZARDS), strings(o, PERSPECTIVES), strings(o, SHOW_VIEWS),
        strings(o, MENU_HIDDEN));
  }

  /** Keeps the customization of the perspective, replacing the previous one. */
  public static synchronized void put(String perspectiveId, Customization c) {
    JsonObject all = load();
    JsonObject o = new JsonObject();
    o.add(NEW_WIZARDS, array(c.getNewWizards()));
    o.add(PERSPECTIVES, array(c.getPerspectives()));
    o.add(SHOW_VIEWS, array(c.getShowViews()));
    o.add(MENU_HIDDEN, array(c.getMenuHidden()));
    all.add(perspectiveId, o);
    store(all);
  }

  /** Drops the customization of the perspective (Reset Perspective, or the deletion of a saved perspective). */
  public static synchronized void remove(String perspectiveId) {
    JsonObject all = load();
    if (perspectiveId == null || !all.has(perspectiveId))
      return;
    all.remove(perspectiveId);
    store(all);
  }

  private static List<String> strings(JsonObject o, String key) {
    List<String> r = new ArrayList<>();
    if (o.has(key) && o.get(key).isJsonArray())
      for (var e : o.getAsJsonArray(key))
        r.add(e.getAsString());
    return r;
  }

  private static JsonArray array(List<String> values) {
    JsonArray a = new JsonArray();
    for (String v : values)
      a.add(v);
    return a;
  }

  /** The stored customizations by perspective id; a damaged store is read as empty. */
  private static JsonObject load() {
    String json = WorkbenchPreferences.getStore().getString(WorkbenchPreferences.PERSPECTIVE_CUSTOMIZATIONS);
    if (json == null || json.isEmpty())
      return new JsonObject();
    try {
      return JsonParser.parseString(json).getAsJsonObject();
    } catch (RuntimeException e) {
      RLogger.logError(e);
      return new JsonObject();
    }
  }

  private static void store(JsonObject all) {
    WorkbenchPreferences.getStore().setValue(WorkbenchPreferences.PERSPECTIVE_CUSTOMIZATIONS, all.toString());
  }
}
