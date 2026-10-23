package com.unitesk.requality.vscode.ui.workbench.internal;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.views.IViewCategory;
import com.unitesk.requality.vscode.ui.workbench.views.IViewDescriptor;
import com.unitesk.requality.vscode.ui.workbench.views.IViewRegistry;

/**
 * {@code org.eclipse.ui.internal.registry.ViewRegistry}: the views and the categories of the {@code org.eclipse.ui.views}
 * extension point (plugin.xml, and the platform views of platform-plugin.xml). Eclipse lists the views in the order of
 * the extensions; the views in a category are listed in the category. VSCODE: a view whose class the server does not
 * have is not listed, since VS Code cannot show it.
 */
public final class ViewRegistry implements IViewRegistry {
  /** The registry of the workbench. */
  public static final ViewRegistry INSTANCE = new ViewRegistry();

  private static final String POINT = "org.eclipse.ui.views";

  private ViewRegistry() {
  }

  private static IConfigurationElement[] elements() {
    return ExtensionRegistry.get().getConfigurationElementsFor(POINT);
  }

  /** The class can be loaded without initializing it (the views are created when they are shown). */
  private static boolean classAvailable(String className) {
    if (className == null)
      return false;
    try {
      Class.forName(ExtensionRegistry.portedClassName(className), false, ViewRegistry.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  @Override
  public IViewCategory[] getCategories() {
    List<IViewCategory> r = new ArrayList<>();
    for (IConfigurationElement e : elements()) {
      String id = e.getAttribute("id");
      if ("category".equals(e.getName()) && id != null && r.stream().noneMatch(c -> c.getId().equals(id)))
        r.add(new Category(id, e.getAttribute("name")));
    }
    return r.toArray(new IViewCategory[0]);
  }

  @Override
  public IViewDescriptor[] getViews() {
    List<IViewDescriptor> r = new ArrayList<>();
    for (IConfigurationElement e : elements()) {
      String id = e.getAttribute("id");
      if (!"view".equals(e.getName()) || id == null || r.stream().anyMatch(v -> v.getId().equals(id)))
        continue;
      if (classAvailable(e.getAttribute("class")))
        r.add(new View(e));
    }
    return r.toArray(new IViewDescriptor[0]);
  }

  @Override
  public IViewDescriptor find(String id) {
    if (id == null)
      return null;
    for (IViewDescriptor v : getViews())
      if (v.getId().equals(id))
        return v;
    return null;
  }

  /** {@code org.eclipse.ui.internal.registry.ViewCategory}. */
  private static final class Category implements IViewCategory {
    private final String id;
    private final String label;

    Category(String id, String label) {
      this.id = id;
      this.label = label == null ? id : label;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getLabel() {
      return label;
    }

    @Override
    public IViewDescriptor[] getViews() {
      List<IViewDescriptor> r = new ArrayList<>();
      for (IViewDescriptor v : INSTANCE.getViews()) {
        String[] path = v.getCategoryPath();
        if (path != null && path.length > 0 && id.equals(path[0]))
          r.add(v);
      }
      return r.toArray(new IViewDescriptor[0]);
    }
  }

  /** {@code org.eclipse.ui.internal.registry.ViewDescriptor}: a view of plugin.xml. */
  private static final class View implements IViewDescriptor {
    private final IConfigurationElement element;
    private final String id;

    View(IConfigurationElement element) {
      this.element = element;
      this.id = element.getAttribute("id");
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getLabel() {
      String name = element.getAttribute("name");
      return name != null ? name : id;
    }

    @Override
    public String getDescription() {
      for (IConfigurationElement d : element.getChildren("description")) {
        String v = d.getValue();
        return v == null ? "" : v.trim();
      }
      return "";
    }

    @Override
    public ImageDescriptor getImageDescriptor() {
      String icon = element.getAttribute("icon");
      return icon == null ? null : ImageDescriptor.fromResource(icon);
    }

    @Override
    public String[] getCategoryPath() {
      String category = element.getAttribute("category");
      return category == null ? null : new String[] { category };
    }

    @Override
    public String toString() {
      return getClass().getName() + " {id=" + id + "}";
    }
  }
}
