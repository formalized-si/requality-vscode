package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.dialogs.PropertyPageContributorManager}: the registered property pages
 * ({@code org.eclipse.ui.propertyPages} of the plug-ins and of plugin.xml) and the ones applicable to an object or a
 * selection.
 */
public final class PropertyPageContributorManager {
  private static PropertyPageContributorManager sharedInstance;

  private PropertyPageContributorManager() {
  }

  public static synchronized PropertyPageContributorManager getManager() {
    if (sharedInstance == null)
      sharedInstance = new PropertyPageContributorManager();
    return sharedInstance;
  }

  /** All the registered property pages. */
  public List<RegistryPageContributor> getContributors() {
    List<RegistryPageContributor> r = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.propertyPages"))
      if ("page".equals(e.getName()))
        r.add(new RegistryPageContributor(e));
    return r;
  }

  /** The pages applicable to the object (an IStructuredSelection: the pages applicable to all its elements). */
  public List<RegistryPageContributor> getApplicableContributors(Object element) {
    if (element instanceof IStructuredSelection)
      return getApplicableContributors((IStructuredSelection) element);
    List<RegistryPageContributor> result = new ArrayList<>();
    for (RegistryPageContributor c : getContributors())
      if (c.isApplicableTo(element))
        result.add(c);
    return result;
  }

  /** The pages applicable to every element of the selection; a multiple selection needs supportsMultipleSelection. */
  public List<RegistryPageContributor> getApplicableContributors(IStructuredSelection selection) {
    boolean multi = selection.size() > 1;
    LinkedHashSet<RegistryPageContributor> result = null;
    for (Object o : selection.toList()) {
      LinkedHashSet<RegistryPageContributor> applicable = new LinkedHashSet<>();
      for (RegistryPageContributor c : getApplicableContributors(o))
        if (!multi || c.supportsMultipleSelection())
          applicable.add(c);
      if (result == null)
        result = applicable;
      else
        result.retainAll(applicable);
      if (result.isEmpty())
        return Collections.emptyList();
    }
    return result == null ? Collections.emptyList() : new ArrayList<>(result);
  }
}
