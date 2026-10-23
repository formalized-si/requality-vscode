package com.unitesk.requality.vscode.ui.navigator.filters;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.CommonFilterDescriptor}: a {@code commonFilter} element: its id,
 * name, description, activation by default and visibility in the UI; the filter is its class or, with a
 * {@code filterExpression}, a {@link CoreExpressionFilter}.
 */
public class CommonFilterDescriptor implements ICommonFilterDescriptor {
  private final IConfigurationElement element;
  private final IConfigurationElement filterExpression;
  private final String id;

  protected CommonFilterDescriptor(IConfigurationElement anElement) {
    element = anElement;
    String i = element.getAttribute("id");
    id = i == null ? "" : i;
    IConfigurationElement[] children = element.getChildren("filterExpression");
    filterExpression = children.length == 1 ? children[0] : null;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return element.getAttribute("name");
  }

  @Override
  public String getDescription() {
    return element.getAttribute("description");
  }

  @Override
  public boolean isActiveByDefault() {
    return Boolean.parseBoolean(element.getAttribute("activeByDefault"));
  }

  @Override
  public boolean isVisibleInUi() {
    String attr = element.getAttribute("visibleInUI");
    return attr == null || Boolean.parseBoolean(attr);
  }

  /** The filter: the expression filter, else an instance of the class; a filter showing everything on failure. */
  public ViewerFilter createFilter() {
    if (filterExpression != null)
      return new CoreExpressionFilter(filterExpression);
    try {
      return (ViewerFilter) element.createExecutableExtension("class");
    } catch (CoreException | ClassCastException e) {
      RLogger.logInfo("Common filter " + id + " is not available: " + e.getMessage());
      return new ViewerFilter() {
        @Override
        public boolean select(Viewer viewer, Object parentElement, Object element) {
          return true;
        }
      };
    }
  }

  @Override
  public String toString() {
    return "CommonFilterDescriptor[" + getName() + " (" + getId() + ")]";
  }
}
