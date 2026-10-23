package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.dialogs.RegistryPageContributor}: a page of {@code org.eclipse.ui.propertyPages}
 * with its applicability to an object: the {@code objectClass}, the {@code enabledWhen} expression and the
 * {@code filter} elements ({@code name="open"} of a project: the project is open).
 */
public final class RegistryPageContributor {
  private final IConfigurationElement pageElement;

  public RegistryPageContributor(IConfigurationElement pageElement) {
    this.pageElement = pageElement;
  }

  /** The page's plugin.xml element. */
  public IConfigurationElement getConfigurationElement() {
    return pageElement;
  }

  public String getPageId() {
    return pageElement.getAttribute("id");
  }

  /** The id of the page this page is a child of, or null. */
  public String getCategory() {
    return pageElement.getAttribute("category");
  }

  public String getPageName() {
    String name = pageElement.getAttribute("name");
    return name == null ? "" : name;
  }

  /** The page accepts a selection of several objects (supportsMultipleSelection, false by default). */
  public boolean supportsMultipleSelection() {
    return Boolean.parseBoolean(pageElement.getAttribute("supportsMultipleSelection"));
  }

  /** The page is applicable to the object (PropertyPageContributor.isApplicableTo of the registry contribution). */
  public boolean isApplicableTo(Object object) {
    String objectClass = pageElement.getAttribute("objectClass");
    if (objectClass != null && !Expressions.isInstance(object, objectClass))
      return false;
    IConfigurationElement[] enabledWhen = pageElement.getChildren("enabledWhen");
    if (enabledWhen.length > 0) {
      try {
        if (!Expressions.evaluateContainer(enabledWhen[0], new EvaluationContext(null, object)))
          return false;
      } catch (RuntimeException e) {
        RLogger.logError(e);
        return false;
      }
    }
    for (IConfigurationElement filter : pageElement.getChildren("filter"))
      if (!filterPasses(filter, object))
        return false;
    return true;
  }

  /** A filter element: only {@code open=true} of a project is known (an unknown filter does not match). */
  private static boolean filterPasses(IConfigurationElement filter, Object object) {
    if (!"open".equals(filter.getAttribute("name")))
      return false;
    boolean expected = Boolean.parseBoolean(filter.getAttribute("value"));
    boolean open = object instanceof IProject && ((IProject) object).isOpen();
    return open == expected;
  }

  /** Pages are equal when they have the same id (the registry creates a new object per query). */
  @Override
  public boolean equals(Object o) {
    return o instanceof RegistryPageContributor && java.util.Objects.equals(getPageId(), ((RegistryPageContributor) o).getPageId());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hashCode(getPageId());
  }
}
