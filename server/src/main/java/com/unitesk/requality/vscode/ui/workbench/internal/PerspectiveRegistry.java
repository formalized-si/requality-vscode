package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;

/**
 * {@code org.eclipse.ui.internal.registry.PerspectiveRegistry}: the perspectives of plugin.xml ({@code
 * org.eclipse.ui.perspectives}) and the perspectives saved by the user (Window &gt; Perspective &gt; Save Perspective
 * As...). A saved perspective is a custom perspective; saving over a predefined one gives it a custom definition, which
 * is used instead of the layout of its perspective factory. Both are kept in the workbench preferences
 * ({@link WorkbenchPreferences#PERSPECTIVE_DEFINITIONS}), so they survive a restart. VSCODE: the layout of a perspective
 * is the list of its views (the views VS Code shows), not the placement of the views of the Eclipse layout.
 */
public final class PerspectiveRegistry implements IPerspectiveRegistry {
  /** The registry of the workbench. */
  public static final PerspectiveRegistry INSTANCE = new PerspectiveRegistry();

  /** {@code org.eclipse.ui.perspectives} of plugin.xml: the perspective class of the Requality perspective. */
  private static final String REQUALITY_PERSPECTIVE_ID = com.unitesk.requality.vscode.port.eclipse.ui.perspective.ReqEditorPerspectiveFactory.ID;

  /** A stored perspective: a saved perspective, or the custom definition of a predefined one. */
  private static final class Definition {
    final String id;
    final String label;
    final String originalId;
    final boolean predefined;
    final List<String> views;

    Definition(String id, String label, String originalId, boolean predefined, List<String> views) {
      this.id = id;
      this.label = label;
      this.originalId = originalId;
      this.predefined = predefined;
      this.views = views;
    }
  }

  /** {@code org.eclipse.ui.internal.registry.PerspectiveDescriptor}: a predefined, a saved or a customized perspective. */
  public static final class PerspectiveDescriptor implements IPerspectiveDescriptor {
    private final String id;
    private final String label;
    private final String originalId;
    private final IConfigurationElement element;
    /** The views of the custom definition (null: the layout of the perspective factory). */
    private final List<String> views;

    private PerspectiveDescriptor(String id, String label, String originalId, IConfigurationElement element,
        List<String> views) {
      this.id = id;
      this.label = label;
      this.originalId = originalId;
      this.element = element;
      this.views = views == null ? null : new ArrayList<>(views);
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getLabel() {
      if (element == null)
        return label;
      String n = element.getAttribute("name");
      return n != null ? n : id;
    }

    /** The description of a predefined perspective; empty for a saved one. */
    @Override
    public String getDescription() {
      if (element == null)
        return "";
      for (IConfigurationElement d : element.getChildren("description")) {
        String v = d.getValue();
        return v == null ? "" : v.trim();
      }
      return "";
    }

    /** The id of the perspective the layout is based on: the predefined perspective's id (for a saved one as well). */
    public String getOriginalId() {
      return originalId;
    }

    /** {@code PerspectiveDescriptor.isPredefined}: the perspective is declared by plugin.xml. */
    public boolean isPredefined() {
      return element != null;
    }

    /** {@code PerspectiveDescriptor.hasCustomDefinition}: a saved perspective, or a predefined one saved over. */
    public boolean hasCustomDefinition() {
      return views != null;
    }

    /** The views of the custom definition, or null if the perspective factory defines the layout. */
    public List<String> getViews() {
      return views == null ? null : new ArrayList<>(views);
    }

    /** The element of plugin.xml with the perspective factory; null for a saved perspective. */
    public IConfigurationElement getElement() {
      return element;
    }

    /** Perspectives are equal when their ids are. */
    @Override
    public boolean equals(Object o) {
      return o instanceof PerspectiveDescriptor && id != null && id.equals(((PerspectiveDescriptor) o).id);
    }

    @Override
    public int hashCode() {
      return String.valueOf(id).hashCode();
    }

    @Override
    public String toString() {
      return getClass().getName() + " {id=" + id + "}";
    }
  }

  private PerspectiveRegistry() {
  }

  /** The id of the perspective of the product: the default when no other one is set. */
  public static String productDefaultPerspectiveId() {
    return REQUALITY_PERSPECTIVE_ID;
  }

  @Override
  public IPerspectiveDescriptor[] getPerspectives() {
    List<Definition> defs = definitions();
    List<IPerspectiveDescriptor> l = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.perspectives")) {
      if (!"perspective".equals(e.getName()))
        continue;
      String id = e.getAttribute("id");
      Definition d = find(defs, id);
      l.add(new PerspectiveDescriptor(id, null, id, e, d != null && d.predefined ? d.views : null));
    }
    for (Definition d : defs)
      if (!d.predefined)
        l.add(new PerspectiveDescriptor(d.id, d.label, d.originalId, null, d.views));
    return l.toArray(new IPerspectiveDescriptor[0]);
  }

  @Override
  public IPerspectiveDescriptor findPerspectiveWithId(String perspectiveId) {
    if (perspectiveId != null)
      for (IPerspectiveDescriptor d : getPerspectives())
        if (perspectiveId.equals(d.getId()))
          return d;
    return null;
  }

  /** {@code IPerspectiveRegistry#findPerspectiveWithLabel}: the first perspective with the label, or null. */
  public IPerspectiveDescriptor findPerspectiveWithLabel(String label) {
    for (IPerspectiveDescriptor d : getPerspectives())
      if (d.getLabel().equals(label))
        return d;
    return null;
  }

  /** The default perspective: the one set with {@link #setDefaultPerspective}, else the product's. */
  @Override
  public String getDefaultPerspective() {
    String id = WorkbenchPreferences.getStore().getString(WorkbenchPreferences.DEFAULT_PERSPECTIVE_ID);
    if (id != null && !id.isEmpty() && findPerspectiveWithId(id) != null)
      return id;
    return REQUALITY_PERSPECTIVE_ID;
  }

  /** {@code IPerspectiveRegistry#setDefaultPerspective}: the perspective must exist; the choice is kept. */
  public void setDefaultPerspective(String id) {
    if (findPerspectiveWithId(id) != null)
      WorkbenchPreferences.getStore().setValue(WorkbenchPreferences.DEFAULT_PERSPECTIVE_ID, id);
  }

  /**
   * {@code PerspectiveRegistry.createPerspective}: a new perspective based on the original one, with the id made of the
   * original id and the label. It is kept when it is saved ({@link #savePerspective}).
   */
  public PerspectiveDescriptor createPerspective(String label, PerspectiveDescriptor original) {
    String originalId = original.getOriginalId();
    return new PerspectiveDescriptor(originalId + '.' + label, label, originalId, null, null);
  }

  /** {@code PerspectiveRegistry.validateLabel}: a name that is not blank. */
  public boolean validateLabel(String label) {
    return label != null && !label.trim().isEmpty();
  }

  /**
   * Keeps the layout of the views of a perspective: the custom definition of a predefined one, or the saved perspective.
   * The descriptor is then the one {@link #findPerspectiveWithId} returns.
   */
  public void savePerspective(PerspectiveDescriptor desc, List<String> views) {
    List<Definition> defs = definitions();
    Definition d = new Definition(desc.getId(), desc.getLabel(), desc.getOriginalId(), desc.isPredefined(),
        new ArrayList<>(views));
    int index = indexOf(defs, desc.getId());
    if (index >= 0)
      defs.set(index, d);
    else
      defs.add(d);
    store(defs);
  }

  /** {@code PerspectiveRegistry.deletePerspectives}: each saved perspective of the list is deleted. */
  public void deletePerspectives(List<IPerspectiveDescriptor> toDelete) {
    for (IPerspectiveDescriptor d : toDelete)
      deletePerspective(d);
  }

  /** {@code PerspectiveRegistry.deletePerspective}: a saved perspective is removed; a predefined one is not. */
  public void deletePerspective(IPerspectiveDescriptor toDelete) {
    PerspectiveDescriptor p = (PerspectiveDescriptor) toDelete;
    if (p.isPredefined())
      return;
    List<Definition> defs = definitions();
    defs.removeIf(d -> d.id.equals(p.getId()));
    store(defs);
    PerspectiveCustomization.remove(p.getId());
  }

  /** {@code PerspectiveRegistry.revertPerspective}: the custom definition of a predefined perspective is dropped. */
  public void revertPerspective(IPerspectiveDescriptor toRevert) {
    PerspectiveDescriptor p = (PerspectiveDescriptor) toRevert;
    if (!p.isPredefined())
      return;
    List<Definition> defs = definitions();
    defs.removeIf(d -> d.id.equals(p.getId()));
    store(defs);
  }

  private static Definition find(List<Definition> defs, String id) {
    for (Definition d : defs)
      if (d.id.equals(id))
        return d;
    return null;
  }

  private static int indexOf(List<Definition> defs, String id) {
    for (int i = 0; i < defs.size(); i++)
      if (defs.get(i).id.equals(id))
        return i;
    return -1;
  }

  /** The stored definitions, in the order they were saved; a damaged store is read as empty. */
  private static List<Definition> definitions() {
    List<Definition> r = new ArrayList<>();
    String json = WorkbenchPreferences.getStore().getString(WorkbenchPreferences.PERSPECTIVE_DEFINITIONS);
    if (json == null || json.isEmpty())
      return r;
    try {
      for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
        JsonObject o = e.getAsJsonObject();
        List<String> views = new ArrayList<>();
        for (JsonElement v : o.getAsJsonArray("views"))
          views.add(v.getAsString());
        r.add(new Definition(o.get("id").getAsString(), o.get("label").getAsString(), o.get("originalId").getAsString(),
            o.get("predefined").getAsBoolean(), views));
      }
    } catch (RuntimeException e) {
      RLogger.logError(e);
      r.clear();
    }
    return r;
  }

  private static void store(List<Definition> defs) {
    JsonArray a = new JsonArray();
    for (Definition d : defs) {
      JsonObject o = new JsonObject();
      o.addProperty("id", d.id);
      o.addProperty("label", d.label);
      o.addProperty("originalId", d.originalId);
      o.addProperty("predefined", d.predefined);
      JsonArray views = new JsonArray();
      for (String v : d.views)
        views.add(v);
      o.add("views", views);
      a.add(o);
    }
    WorkbenchPreferences.getStore().setValue(WorkbenchPreferences.PERSPECTIVE_DEFINITIONS, a.toString());
  }
}
