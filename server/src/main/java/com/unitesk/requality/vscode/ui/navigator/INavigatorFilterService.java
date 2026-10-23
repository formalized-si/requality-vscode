package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.navigator.filters.ICommonFilterDescriptor;

/** {@code org.eclipse.ui.navigator.INavigatorFilterService}: the common filters of a viewer and their activation. */
public interface INavigatorFilterService {
  ViewerFilter[] getVisibleFilters(boolean toReturnOnlyActiveFilters);

  ICommonFilterDescriptor[] getVisibleFilterDescriptors();

  ICommonFilterDescriptor[] getVisibleFilterDescriptorsForUI();

  ViewerFilter getViewerFilter(ICommonFilterDescriptor descriptor);

  boolean isActive(String aFilterId);

  void setActiveFilterIds(String[] theFilterIds);

  void activateFilterIdsAndUpdateViewer(String[] filterIdsToActivate);

  void addActiveFilterIds(String[] theFilterIds);

  void setActive(String aFilterId, boolean toMakeActive);

  void persistFilterActivationState();

  void updateViewer();
}
