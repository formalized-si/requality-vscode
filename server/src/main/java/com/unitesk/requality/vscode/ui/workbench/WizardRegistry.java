package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * The new, import or export wizard registry: the {@code category} and {@code wizard} elements of the extension point
 * ({@code org.eclipse.ui.newWizards}, {@code org.eclipse.ui.importWizards}, {@code org.eclipse.ui.exportWizards})
 * read from the plug-ins' {@code plugin.xml}. Like {@code org.eclipse.ui.internal.wizards.AbstractWizardRegistry}, the
 * wizards form a tree of categories; a wizard without a category is a child of the root category.
 */
final class WizardRegistry implements IWizardRegistry {
  private static final Comparator<Object> BY_NAME = (a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b));

  private final String extensionPointId;
  private Category root;
  private final Map<String, Category> categories = new LinkedHashMap<>();

  WizardRegistry(String extensionPointId) {
    this.extensionPointId = extensionPointId;
  }

  private static String nameOf(Object o) {
    String name = o instanceof Category ? ((Category) o).getLabel() : ((Descriptor) o).getLabel();
    return name == null ? "" : name;
  }

  /** Reads the categories and the wizards of the extension point (once per registry). */
  private synchronized Category root() {
    if (root != null)
      return root;
    root = new Category(null, null, null);
    IConfigurationElement[] elements = ExtensionRegistry.get().getConfigurationElementsFor(extensionPointId);
    for (IConfigurationElement e : elements)
      if ("category".equals(e.getName()) && e.getAttribute("id") != null)
        categories.put(e.getAttribute("id"), new Category(e.getAttribute("id"), e.getAttribute("name"),
            e.getAttribute("parentCategory")));
    for (Category c : categories.values()) {
      Category parent = c.parentId == null ? null : categories.get(c.parentId);
      (parent == null ? root : parent).children.add(c);
      c.parent = parent == null ? root : parent;
    }
    for (IConfigurationElement e : elements)
      if ("wizard".equals(e.getName()) && e.getAttribute("id") != null) {
        Category category = categories.get(e.getAttribute("category"));
        Descriptor d = new Descriptor(e, category == null ? root : category);
        (category == null ? root : category).wizards.add(d);
      }
    root.sort();
    return root;
  }

  @Override
  public IWizardDescriptor findWizard(String id) {
    if (id == null)
      return null;
    return find(root(), id);
  }

  private static IWizardDescriptor find(Category c, String id) {
    for (Descriptor d : c.wizards)
      if (id.equals(d.getId()))
        return d;
    for (Category s : c.children) {
      IWizardDescriptor d = find(s, id);
      if (d != null)
        return d;
    }
    return null;
  }

  @Override
  public IWizardCategory findCategory(String id) {
    return id == null ? null : root().getCategoryFor(id);
  }

  @Override
  public IWizardCategory getRootCategory() {
    return root();
  }

  /** A category: the root (id null) or a {@code category} element. */
  private static final class Category implements IWizardCategory {
    private final String id;
    private final String name;
    private final String parentId;
    private Category parent;
    private final List<Category> children = new ArrayList<>();
    private final List<Descriptor> wizards = new ArrayList<>();

    Category(String id, String name, String parentId) {
      this.id = id;
      this.name = name == null ? "" : name;
      this.parentId = parentId;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getLabel() {
      return name;
    }

    @Override
    public com.unitesk.requality.vscode.ui.runtime.IPath getPath() {
      if (parent == null || id == null)
        return com.unitesk.requality.vscode.ui.runtime.Path.EMPTY;
      return parent.getPath().append(id);
    }

    @Override
    public IWizardCategory findCategory(com.unitesk.requality.vscode.ui.runtime.IPath path) {
      if (path == null || path.segmentCount() == 0)
        return this;
      for (Category c : children)
        if (path.segment(0).equals(c.id))
          return c.findCategory(path.removeFirstSegments(1));
      return null;
    }

    @Override
    public IWizardDescriptor findWizard(String wizardId) {
      return find(this, wizardId);
    }

    @Override
    public IWizardCategory getParent() {
      return parent;
    }

    @Override
    public IWizardCategory[] getCategories() {
      return children.toArray(new IWizardCategory[0]);
    }

    @Override
    public IWizardDescriptor[] getWizards() {
      return wizards.toArray(new IWizardDescriptor[0]);
    }

    /** Sorts the subcategories and the wizards by name, recursively (as the Eclipse collection comparator does). */
    void sort() {
      children.sort(BY_NAME);
      wizards.sort(BY_NAME);
      for (Category c : children)
        c.sort();
    }

    /** The category with the id in this subtree, or null. */
    private Category getCategoryFor(String categoryId) {
      if (categoryId.equals(id))
        return this;
      for (Category c : children) {
        Category f = c.getCategoryFor(categoryId);
        if (f != null)
          return f;
      }
      return null;
    }
  }

  private static final class Descriptor implements IWizardDescriptor {
    private final IConfigurationElement element;
    private final Category category;

    Descriptor(IConfigurationElement element, Category category) {
      this.element = element;
      this.category = category;
    }

    @Override
    public String getId() {
      return element.getAttribute("id");
    }

    @Override
    public String getLabel() {
      return element.getAttribute("name");
    }

    @Override
    public String getDescription() {
      IConfigurationElement[] d = element.getChildren("description");
      return d.length == 0 ? null : d[0].getValue();
    }

    @Override
    public IWizardCategory getCategory() {
      return category == null || category.id == null ? null : category;
    }

    @Override
    public String[] getTags() {
      // VSCODE: the perspective attributes (finalPerspective, preferredPerspectives) have no meaning without perspectives.
      return "true".equals(element.getAttribute("project")) ? new String[] { "project" } : new String[0];
    }

    @Override
    public IWizard createWizard() throws CoreException {
      return (IWizard) element.createExecutableExtension("class");
    }
  }
}
