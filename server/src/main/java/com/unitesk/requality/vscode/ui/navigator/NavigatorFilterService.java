package com.unitesk.requality.vscode.ui.navigator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.navigator.filters.CommonFilterDescriptor;
import com.unitesk.requality.vscode.ui.navigator.filters.CommonFilterDescriptorManager;
import com.unitesk.requality.vscode.ui.navigator.filters.ICommonFilterDescriptor;

/**
 * {@code org.eclipse.ui.internal.navigator.NavigatorFilterService}: the common filters bound to a viewer, which of
 * them are active (the filters not visible in the UI that are active by default, then the persisted activation of
 * the visible ones, else their defaults), and the viewer filters of the active ones.
 */
// VSCODE: the activation is persisted by the owner (the Requality Explorer keeps it in the VS Code setting
// requality.explorer.filters instead of the navigator preferences); the filters the explorer applies to the items
// itself (node types, resource filters: see services/ExplorerService) are not set on the viewer as filter instances.
public class NavigatorFilterService implements INavigatorFilterService {
  private static final ViewerFilter[] NO_FILTERS = new ViewerFilter[0];
  private final String viewerId;
  private final StructuredViewer viewer;
  private final Map<ICommonFilterDescriptor, ViewerFilter> declaredViewerFilters = new HashMap<>();
  private final Set<String> activeFilters = new LinkedHashSet<>();
  private final Consumer<Set<String>> persister;
  private final Predicate<String> appliedByViewer;
  private final Runnable onUpdate;

  /**
   * @param persisted the persisted activation of the visible filters, or null for the defaults
   * @param persister keeps the activation of the visible filters
   * @param appliedByViewer whether a filter is set on the viewer as a filter instance (else the owner applies it)
   * @param onUpdate called when the viewer is updated with the active filters
   */
  public NavigatorFilterService(String viewerId, StructuredViewer viewer, Set<String> persisted,
      Consumer<Set<String>> persister, Predicate<String> appliedByViewer, Runnable onUpdate) {
    this.viewerId = viewerId;
    this.viewer = viewer;
    this.persister = persister;
    this.appliedByViewer = appliedByViewer;
    this.onUpdate = onUpdate;
    restoreFilterActivation(persisted);
  }

  /** The activation: non-UI filters active by default, then the persisted visible ones, else their defaults. */
  public synchronized void restoreFilterActivation(Set<String> persisted) {
    activeFilters.clear();
    CommonFilterDescriptor[] visible = CommonFilterDescriptorManager.getInstance().findVisibleFilters(viewerId);
    for (CommonFilterDescriptor d : visible)
      if (!d.isVisibleInUi() && d.isActiveByDefault())
        activeFilters.add(d.getId());
    if (persisted != null) {
      activeFilters.addAll(persisted);
    } else {
      for (CommonFilterDescriptor d : visible)
        if (d.isVisibleInUi() && d.isActiveByDefault())
          activeFilters.add(d.getId());
    }
  }

  @Override
  public void persistFilterActivationState() {
    Set<String> visibleActive = new LinkedHashSet<>();
    synchronized (this) {
      for (String id : activeFilters) {
        CommonFilterDescriptor d = CommonFilterDescriptorManager.getInstance().getFilterById(id);
        if (d != null && d.isVisibleInUi())
          visibleActive.add(id);
      }
    }
    if (persister != null)
      persister.accept(visibleActive);
  }

  @Override
  public ViewerFilter[] getVisibleFilters(boolean toReturnOnlyActiveFilters) {
    List<ViewerFilter> filters = new ArrayList<>();
    for (CommonFilterDescriptor d : CommonFilterDescriptorManager.getInstance().findVisibleFilters(viewerId))
      if (!toReturnOnlyActiveFilters || isActive(d.getId())) {
        ViewerFilter f = getViewerFilter(d);
        if (f != null)
          filters.add(f);
      }
    return filters.isEmpty() ? NO_FILTERS : filters.toArray(new ViewerFilter[0]);
  }

  @Override
  public ViewerFilter getViewerFilter(ICommonFilterDescriptor descriptor) {
    synchronized (declaredViewerFilters) {
      return declaredViewerFilters.computeIfAbsent(descriptor, d -> ((CommonFilterDescriptor) d).createFilter());
    }
  }

  @Override
  public ICommonFilterDescriptor[] getVisibleFilterDescriptors() {
    return CommonFilterDescriptorManager.getInstance().findVisibleFilters(viewerId);
  }

  @Override
  public ICommonFilterDescriptor[] getVisibleFilterDescriptorsForUI() {
    return CommonFilterDescriptorManager.getInstance().findVisibleFilters(viewerId, CommonFilterDescriptorManager.FOR_UI);
  }

  @Override
  public synchronized boolean isActive(String aFilterId) {
    return activeFilters.contains(aFilterId);
  }

  /** The ids of the active filters. */
  public synchronized Set<String> getActiveFilterIds() {
    return new LinkedHashSet<>(activeFilters);
  }

  @Override
  public synchronized void setActiveFilterIds(String[] theFilterIds) {
    activeFilters.clear();
    activeFilters.addAll(Arrays.asList(theFilterIds));
  }

  /** Activates exactly these filters (with the active filters not shown in the UI) when they differ. */
  @Override
  public void activateFilterIdsAndUpdateViewer(String[] filterIdsToActivate) {
    boolean update = false;
    Set<String> toActivate = new LinkedHashSet<>(Arrays.asList(filterIdsToActivate));
    List<String> nonUiVisible = new ArrayList<>();
    for (ICommonFilterDescriptor d : getVisibleFilterDescriptors()) {
      if (toActivate.contains(d.getId()) ^ isActive(d.getId()))
        update = true;
      if (!d.isVisibleInUi() && isActive(d.getId()))
        nonUiVisible.add(d.getId());
    }
    if (update) {
      toActivate.addAll(nonUiVisible);
      setActiveFilterIds(toActivate.toArray(new String[0]));
      persistFilterActivationState();
      updateViewer();
      viewer.setSelection(StructuredSelection.EMPTY);
    }
  }

  /** The viewer gets the instances of the active filters. */
  @Override
  public void updateViewer() {
    List<ViewerFilter> filters = new ArrayList<>();
    for (CommonFilterDescriptor d : CommonFilterDescriptorManager.getInstance().findVisibleFilters(viewerId))
      if (isActive(d.getId()) && appliedByViewer.test(d.getId()))
        filters.add(getViewerFilter(d));
    if (onUpdate != null)
      onUpdate.run();
    viewer.setFilters(filters.toArray(new ViewerFilter[0]));
  }

  @Override
  public synchronized void addActiveFilterIds(String[] theFilterIds) {
    activeFilters.addAll(Arrays.asList(theFilterIds));
  }

  @Override
  public synchronized void setActive(String aFilterId, boolean toMakeActive) {
    if (toMakeActive)
      activeFilters.add(aFilterId);
    else
      activeFilters.remove(aFilterId);
  }
}
