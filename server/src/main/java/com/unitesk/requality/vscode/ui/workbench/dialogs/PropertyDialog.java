package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.preference.IPreferencePage;
import com.unitesk.requality.vscode.ui.jface.preference.PreferenceDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPropertyPage;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.dialogs.PropertyDialog}: the Properties dialog of an element (Alt+Enter), with the
 * pages of {@code org.eclipse.ui.propertyPages} applicable to it, under their categories and sorted by name, titled
 * "Properties for &lt;name&gt;". The preference pages of Window &gt; Preferences are shown in the same tree
 * ({@link #tree}, {@link #flatten}).
 */
public class PropertyDialog extends PreferenceDialog {
  /** A page contribution and its children (pages whose category is its id). */
  public static final class Node {
    final IConfigurationElement element;
    final List<Node> children = new ArrayList<>();

    Node(IConfigurationElement element) {
      this.element = element;
    }

    String name() {
      String n = element.getAttribute("name");
      return n == null ? "" : n;
    }
  }

  public PropertyDialog(Shell parent, String title, List<? extends IPreferencePage> pages) {
    super(parent, title, pages);
  }

  /**
   * {@code PropertyDialog.createDialogOn}: the dialog with the pages applicable to the element (an IAdaptable, or an
   * IStructuredSelection of several), selecting the page of pageId; null when no page applies.
   */
  public static PropertyDialog createDialogOn(Shell shell, String pageId, Object element) {
    if (element == null)
      return null;
    List<RegistryPageContributor> contributors = PropertyPageContributorManager.getManager().getApplicableContributors(element);
    String name = nameOf(element);
    if (contributors.isEmpty()) {
      if ("".equals(name))
        com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog.openInformation(shell, "Property Pages", "No property pages.");
      else
        com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog.openInformation(shell, "Property Pages",
            "No property pages for " + name + ".");
      return null;
    }
    List<IConfigurationElement> elements = new ArrayList<>();
    for (RegistryPageContributor c : contributors)
      elements.add(c.getConfigurationElement());
    List<IPreferencePage> pages = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    flatten(tree(elements), "", pages, ids, singleElement(element));
    if (pages.isEmpty())
      return null;
    PropertyDialog dialog = new PropertyDialog(shell, "Properties for " + name, pages);
    if (pageId != null && ids.contains(pageId))
      dialog.setSelectedPage(ids.indexOf(pageId));
    return dialog;
  }

  /** The element the property pages get: the object itself, none for a multiple selection. */
  static IAdaptable singleElement(Object element) {
    if (element instanceof IStructuredSelection) {
      IStructuredSelection s = (IStructuredSelection) element;
      return s.size() == 1 && s.getFirstElement() instanceof IAdaptable ? (IAdaptable) s.getFirstElement() : null;
    }
    return element instanceof IAdaptable ? (IAdaptable) element : null;
  }

  /** The name of the element in the title: up to three names, as PropertyDialog.getName. */
  static String nameOf(Object element) {
    List<Object> elements = new ArrayList<>();
    if (element instanceof IStructuredSelection)
      elements.addAll(((IStructuredSelection) element).toList());
    else
      elements.add(element);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < elements.size(); i++) {
      if (i > 2) {
        sb.append(" ...");
        break;
      }
      Object o = elements.get(i);
      com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter adapter = (com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter) com.unitesk.requality.vscode.ui.runtime.Platform
          .getAdapterManager().getAdapter(o, com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter.class);
      // VSCODE: a project handle is named by its folder; its label is the name of its description (.project).
      String label = o instanceof com.unitesk.requality.vscode.ui.resources.IProject
          ? com.unitesk.requality.vscode.ui.resources.ProjectRef.displayName((com.unitesk.requality.vscode.ui.resources.IProject) o)
          : adapter != null ? adapter.getLabel(o) : o instanceof IResource ? ((IResource) o).getName() : null;
      if (label != null) {
        if (sb.length() > 0)
          sb.append(", ");
        sb.append(label);
      }
    }
    return sb.toString();
  }

  /** Pages under their category, each level sorted by name (as the Eclipse preference tree). */
  public static List<Node> tree(List<IConfigurationElement> elements) {
    List<Node> nodes = new ArrayList<>();
    for (IConfigurationElement e : elements)
      nodes.add(new Node(e));
    List<Node> roots = new ArrayList<>();
    for (Node n : nodes) {
      String category = n.element.getAttribute("category");
      Node parent = null;
      if (category != null) {
        String last = category.contains("/") ? category.substring(category.lastIndexOf('/') + 1) : category;
        for (Node c : nodes)
          if (last.equals(c.element.getAttribute("id")))
            parent = c;
      }
      if (parent != null)
        parent.children.add(n);
      else
        roots.add(n);
    }
    sort(roots);
    return roots;
  }

  private static void sort(List<Node> nodes) {
    nodes.sort(Comparator.comparing(Node::name, String.CASE_INSENSITIVE_ORDER));
    for (Node n : nodes)
      sort(n.children);
  }

  /** The pages of the tree in order, with their indented titles and their ids. */
  public static void flatten(List<Node> nodes, String indent, List<IPreferencePage> pages, List<String> ids,
      IAdaptable element) {
    for (Node n : nodes) {
      IPreferencePage page = createPage(n.element, element);
      if (page != null) {
        if (page.getTitle() == null || page.getTitle().isEmpty())
          page.setTitle(n.name());
        if (!indent.isEmpty())
          page.setTitle(indent + page.getTitle());
        pages.add(page);
        ids.add(n.element.getAttribute("id"));
      }
      flatten(n.children, indent + "    ", pages, ids, element);
    }
  }

  /** Creates a page; a workbench page is initialized, a property page gets the element. Null when it is not available. */
  static IPreferencePage createPage(IConfigurationElement e, IAdaptable element) {
    try {
      Object o = e.createExecutableExtension("class");
      if (o instanceof IWorkbenchPreferencePage)
        ((IWorkbenchPreferencePage) o).init(PlatformUI.getWorkbench());
      if (o instanceof IWorkbenchPropertyPage && element != null)
        ((IWorkbenchPropertyPage) o).setElement(element);
      return (IPreferencePage) o;
    } catch (CoreException | ClassCastException ex) {
      com.unitesk.requality.core.log.RLogger.logInfo("Page " + e.getAttribute("class") + " is not available: " + ex.getMessage());
      return null;
    }
  }
}
