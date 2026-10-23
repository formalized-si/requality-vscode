package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentDescriptor;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.navigator.INavigatorFilterService;
import com.unitesk.requality.vscode.ui.navigator.NavigatorBindings;
import com.unitesk.requality.vscode.ui.navigator.NavigatorFilterService;
import com.unitesk.requality.vscode.ui.navigator.filters.CommonFilterDescriptorManager;
import com.unitesk.requality.vscode.ui.navigator.filters.ICommonFilterDescriptor;
import com.unitesk.requality.vscode.ui.navigator.filters.UserFilter;
import com.unitesk.requality.vscode.ui.navigator.filters.UserFiltersTab;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * The {@code INavigatorContentService} of the Requality Explorer: the content extensions bound to it
 * ({@code viewerContentBinding}) and their activation, and the {@link NavigatorFilterService} of its common filters.
 * What Eclipse keeps in the navigator preferences (filter and content activation) and in the view state (the user
 * filters) is kept in the VS Code settings {@code requality.explorer.filters}, {@code requality.explorer.contentExtensions}
 * and {@code requality.explorer.userFilters}: the server updates them through the extension and receives them back
 * with {@code explorer/setFilters}.
 */
public final class ExplorerContentService implements INavigatorContentService {
  /** The Requality catalog content (nodes of the catalogs). */
  public static final String REQUALITY_CONTENT = "com.unitesk.requality.eclipse.CMFContent";
  /** The contents of projects and resources. */
  public static final String NON_REQUALITY_CONTENT = "com.unitesk.requality.nonReqNavigatorProvider";
  public static final String RESOURCE_CONTENT = "org.eclipse.ui.navigator.resourceContent";

  /** The keys of the setting requality.explorer.filters and the common filters they stand for. */
  static final Map<String, String> SETTING_KEYS = new LinkedHashMap<>();
  static {
    SETTING_KEYS.put("documents", "com.unitesk.requality.eclipse.DocFilter");
    SETTING_KEYS.put("requirements", "com.unitesk.requality.eclipse.RequirementFilter");
    SETTING_KEYS.put("types", "com.unitesk.requality.eclipse.TypesFilter");
    SETTING_KEYS.put("locations", "com.unitesk.requality.eclipse.LocationsFilter");
    SETTING_KEYS.put("reportData", "com.unitesk.requality.eclipse.ReportDataFilter");
    SETTING_KEYS.put("comments", "com.unitesk.requality.eclipse.CommentFilter");
    SETTING_KEYS.put("restrictedFolders", "com.unitesk.requality.eclipse.RestrictedFoldersFilter");
    SETTING_KEYS.put("instancesFolders", "com.unitesk.requality.eclipse.InstancesFoldersFilter");
    SETTING_KEYS.put("resourcesInRequalityProjects", "com.unitesk.requality.eclipse.hideresource");
    SETTING_KEYS.put("startsWithDot", "org.eclipse.ui.navigator.resources.filters.startsWithDot");
    SETTING_KEYS.put("endsWithClass", "org.eclipse.ui.navigator.resources.filters.endsWithClass");
    SETTING_KEYS.put("closedProjects", "org.eclipse.ui.navigator.resources.filters.closedProject");
  }

  /**
   * The filters the explorer applies to its items itself (the node types of the Requality filters, as
   * RequalityCNF.updateStaticFilter, and the resource filters it checks while listing): they are not set on the viewer.
   */
  private static final Set<String> APPLIED_BY_EXPLORER = new LinkedHashSet<>();
  static {
    for (Map.Entry<String, String> e : SETTING_KEYS.entrySet())
      if (!e.getKey().equals("endsWithClass"))
        APPLIED_BY_EXPLORER.add(e.getValue());
  }

  private static final class ContentDescriptor implements INavigatorContentDescriptor {
    private final IConfigurationElement element;

    ContentDescriptor(IConfigurationElement element) {
      this.element = element;
    }

    @Override
    public String getId() {
      return element.getAttribute("id");
    }

    @Override
    public String getName() {
      String n = element.getAttribute("name");
      return n == null ? getId() : n;
    }

    @Override
    public boolean isActiveByDefault() {
      String a = element.getAttribute("activeByDefault");
      return a == null || Boolean.parseBoolean(a);
    }
  }

  private final ExplorerViewer viewer;
  private final NavigatorFilterService filterService;
  private final Map<String, Boolean> contentActivation = new LinkedHashMap<>();
  private List<ContentDescriptor> contents;

  ExplorerContentService(ExplorerViewer viewer) {
    this.viewer = viewer;
    filterService = new NavigatorFilterService(RequalityCNF.ID, viewer, null, this::persistFilters,
        id -> !APPLIED_BY_EXPLORER.contains(id), ExplorerService::filtersChanged);
  }

  @Override
  public String getViewerId() {
    return RequalityCNF.ID;
  }

  @Override
  public void dispose() {
  }

  @Override
  public INavigatorFilterService getFilterService() {
    return filterService;
  }

  NavigatorFilterService filters() {
    return filterService;
  }

  private synchronized List<ContentDescriptor> contents() {
    if (contents == null) {
      contents = new ArrayList<>();
      List<Pattern> bindings = NavigatorBindings.contentPatterns(RequalityCNF.ID);
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.navigator.navigatorContent"))
        if ("navigatorContent".equals(e.getName()) && NavigatorBindings.matches(bindings, e.getAttribute("id")))
          contents.add(new ContentDescriptor(e));
    }
    return contents;
  }

  @Override
  public INavigatorContentDescriptor[] getVisibleExtensions() {
    return contents().toArray(new INavigatorContentDescriptor[0]);
  }

  /**
   * Whether the content extension is active: its persisted activation, else its {@code activeByDefault} (the
   * activation does not depend on the binding of the extension to the viewer, as in Eclipse).
   */
  @Override
  public synchronized boolean isActive(String extensionId) {
    Boolean b = contentActivation.get(extensionId);
    if (b != null)
      return b;
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.navigator.navigatorContent"))
      if ("navigatorContent".equals(e.getName()) && extensionId.equals(e.getAttribute("id")))
        return new ContentDescriptor(e).isActiveByDefault();
    return false;
  }

  /** Filters and Customization > Content: exactly the checked extensions of the viewer are active. */
  @Override
  public void activateExtensions(String[] extensionIds) {
    synchronized (this) {
      Set<String> active = new LinkedHashSet<>(java.util.Arrays.asList(extensionIds));
      for (ContentDescriptor d : contents())
        contentActivation.put(d.getId(), active.contains(d.getId()));
    }
    activationService.persistExtensionActivations();
    ExplorerService.refreshAllNodes();
  }

  private final Map<String, com.unitesk.requality.vscode.ui.navigator.IExtensionStateModel> stateModels = new java.util.concurrent.ConcurrentHashMap<>();

  @Override
  public com.unitesk.requality.vscode.ui.navigator.IExtensionStateModel findStateModel(String anExtensionId) {
    return stateModels.computeIfAbsent(anExtensionId, com.unitesk.requality.vscode.ui.navigator.ExtensionStateModel::new);
  }

  private final List<com.unitesk.requality.vscode.ui.navigator.IExtensionActivationListener> activationListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

  /** {@code NavigatorActivationService}: the activation of content extensions, persisted in the settings. */
  private final com.unitesk.requality.vscode.ui.navigator.INavigatorActivationService activationService = new com.unitesk.requality.vscode.ui.navigator.INavigatorActivationService() {
    @Override
    public boolean isNavigatorExtensionActive(String id) {
      return isActive(id);
    }

    @Override
    public void activateExtensions(String[] ids, boolean toDeactivateAllOthers) {
      List<String> changed = new ArrayList<>();
      synchronized (ExplorerContentService.this) {
        if (toDeactivateAllOthers)
          for (ContentDescriptor d : contents())
            contentActivation.put(d.getId(), false);
        for (String id : ids) {
          if (!isActive(id))
            changed.add(id);
          contentActivation.put(id, true);
        }
      }
      if (!changed.isEmpty())
        for (com.unitesk.requality.vscode.ui.navigator.IExtensionActivationListener l : activationListeners)
          l.onExtensionActivation(getViewerId(), changed.toArray(new String[0]), true);
    }

    @Override
    public void deactivateExtensions(String[] ids, boolean toEnableAllOthers) {
      List<String> changed = new ArrayList<>();
      synchronized (ExplorerContentService.this) {
        if (toEnableAllOthers)
          for (ContentDescriptor d : contents())
            contentActivation.put(d.getId(), true);
        for (String id : ids) {
          if (isActive(id))
            changed.add(id);
          contentActivation.put(id, false);
        }
      }
      if (!changed.isEmpty())
        for (com.unitesk.requality.vscode.ui.navigator.IExtensionActivationListener l : activationListeners)
          l.onExtensionActivation(getViewerId(), changed.toArray(new String[0]), false);
    }

    @Override
    public void persistExtensionActivations() {
      JsonObject value = new JsonObject();
      synchronized (ExplorerContentService.this) {
        for (Map.Entry<String, Boolean> e : contentActivation.entrySet())
          value.addProperty(e.getKey(), e.getValue());
      }
      updateSetting("explorer.contentExtensions", value);
    }

    @Override
    public void addExtensionActivationListener(com.unitesk.requality.vscode.ui.navigator.IExtensionActivationListener l) {
      if (!activationListeners.contains(l))
        activationListeners.add(l);
    }

    @Override
    public void removeExtensionActivationListener(com.unitesk.requality.vscode.ui.navigator.IExtensionActivationListener l) {
      activationListeners.remove(l);
    }
  };

  @Override
  public com.unitesk.requality.vscode.ui.navigator.INavigatorActivationService getActivationService() {
    return activationService;
  }

  /** The user filters of the viewer changed (setData of the "User filters" tab): they are kept in the settings. */
  void userFiltersChanged(List<UserFilter> filters) {
    JsonArray value = new JsonArray();
    for (UserFilter f : filters) {
      JsonObject o = new JsonObject();
      o.addProperty("pattern", f.getRegexp());
      o.addProperty("enabled", f.isEnabled());
      value.add(o);
    }
    updateSetting("explorer.userFilters", value);
  }

  /** The visible filters that are active: the setting requality.explorer.filters (true: hidden). */
  private void persistFilters(Set<String> activeVisible) {
    JsonObject value = new JsonObject();
    for (ICommonFilterDescriptor d : filterService.getVisibleFilterDescriptorsForUI()) {
      String key = settingKey(d.getId());
      value.addProperty(key, activeVisible.contains(d.getId()));
    }
    updateSetting("explorer.filters", value);
  }

  private static String settingKey(String filterId) {
    for (Map.Entry<String, String> e : SETTING_KEYS.entrySet())
      if (e.getValue().equals(filterId))
        return e.getKey();
    return filterId;
  }

  private static void updateSetting(String key, JsonElement value) {
    if (ServerContext.server() == null)
      return;
    JsonObject p = new JsonObject();
    p.addProperty("key", key);
    p.add("value", value);
    ServerContext.client().notify("client/updateSetting", p);
  }

  /**
   * The settings of the extension (explorer/setFilters): the filter activation (missing keys: the defaults), the
   * content activation and the user filters.
   */
  void applySettings(JsonObject filters, JsonObject content, JsonArray userFilters) {
    Set<String> persisted = null;
    if (filters != null && filters.size() > 0) {
      persisted = new LinkedHashSet<>();
      for (ICommonFilterDescriptor d : CommonFilterDescriptorManager.getInstance().findVisibleFilters(RequalityCNF.ID, true)) {
        JsonElement v = filters.get(settingKey(d.getId()));
        boolean on = v != null && !v.isJsonNull() ? v.getAsBoolean() : d.isActiveByDefault();
        if (on)
          persisted.add(d.getId());
      }
    }
    filterService.restoreFilterActivation(persisted);
    synchronized (this) {
      contentActivation.clear();
      if (content != null)
        for (Map.Entry<String, JsonElement> e : content.entrySet())
          if (!e.getValue().isJsonNull())
            contentActivation.put(e.getKey(), e.getValue().getAsBoolean());
    }
    List<UserFilter> uf = new ArrayList<>();
    if (userFilters != null)
      for (JsonElement e : userFilters)
        if (e.isJsonObject() && e.getAsJsonObject().has("pattern"))
          uf.add(new UserFilter(e.getAsJsonObject().get("pattern").getAsString(),
              !e.getAsJsonObject().has("enabled") || e.getAsJsonObject().get("enabled").getAsBoolean()));
    viewer.setDataQuietly(UserFiltersTab.RESOURCE_REGEXP_FILTER_DATA, uf);
    filterService.updateViewer();
  }
}
