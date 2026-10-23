package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.CommonFilterDescriptorManager}: the common filters of the
 * {@code org.eclipse.ui.navigator.navigatorContent} extensions; the visible ones of a viewer are those bound by its
 * {@code viewerContentBinding} patterns.
 */
public final class CommonFilterDescriptorManager {
  public static final boolean FOR_UI = true;
  private static final CommonFilterDescriptorManager INSTANCE = new CommonFilterDescriptorManager();
  private Map<String, CommonFilterDescriptor> filters;

  private CommonFilterDescriptorManager() {
  }

  public static CommonFilterDescriptorManager getInstance() {
    return INSTANCE;
  }

  private synchronized Map<String, CommonFilterDescriptor> filters() {
    if (filters == null) {
      filters = new LinkedHashMap<>();
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.navigator.navigatorContent"))
        if ("commonFilter".equals(e.getName())) {
          CommonFilterDescriptor d = new CommonFilterDescriptor(e);
          filters.put(d.getId(), d);
        }
    }
    return filters;
  }

  public CommonFilterDescriptor getFilterById(String id) {
    return filters().get(id);
  }

  /** The filters bound to the viewer; {@link #FOR_UI}: only those shown in the UI. */
  public CommonFilterDescriptor[] findVisibleFilters(String viewerId, boolean forUI) {
    List<Pattern> bindings = com.unitesk.requality.vscode.ui.navigator.NavigatorBindings.contentPatterns(viewerId);
    List<CommonFilterDescriptor> r = new ArrayList<>();
    for (CommonFilterDescriptor d : filters().values())
      if (com.unitesk.requality.vscode.ui.navigator.NavigatorBindings.matches(bindings, d.getId()) && (!forUI || d.isVisibleInUi()))
        r.add(d);
    return r.toArray(new CommonFilterDescriptor[0]);
  }

  public CommonFilterDescriptor[] findVisibleFilters(String viewerId) {
    return findVisibleFilters(viewerId, false);
  }
}
