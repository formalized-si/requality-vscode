package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.navigator.filters.UserFilter;
import com.unitesk.requality.vscode.ui.navigator.filters.UserFiltersTab;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.ResourceNameRegexpFilter}: the user defined filter: hides the
 * resources whose names match an enabled pattern of the "User filters" tab (the viewer data
 * {@link UserFiltersTab#RESOURCE_REGEXP_FILTER_DATA}).
 */
public class ResourceNameRegexpFilter extends ViewerFilter {
  @Override
  @SuppressWarnings("unchecked")
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    IResource resource = element instanceof IResource ? (IResource) element : null;
    Object data = viewer == null ? null : viewer.getData(UserFiltersTab.RESOURCE_REGEXP_FILTER_DATA);
    if (resource != null && data != null)
      for (UserFilter filter : (List<UserFilter>) data)
        if (filter.isEnabled() && filter.matches(resource.getName()))
          return false;
    return true;
  }
}
