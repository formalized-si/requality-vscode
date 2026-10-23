package com.unitesk.requality.vscode.ui.workbench.internal;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.BasicWorkingSetElementAdapter;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetElementAdapter;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetUpdater;

/**
 * {@code org.eclipse.ui.internal.registry.WorkingSetDescriptor}: a working set type declared by the
 * {@code org.eclipse.ui.workingSets} extension point (id, name, icon, the page class that edits it, the updater that
 * keeps it up to date and the element adapter).
 */
public final class WorkingSetDescriptor {
  private final IConfigurationElement element;
  private final String id;
  private final String name;
  private final String description;
  private final String pageClassName;
  private final String icon;
  private final String updaterClassName;
  private final String elementAdapterClassName;

  WorkingSetDescriptor(IConfigurationElement element) {
    this.element = element;
    this.id = element.getAttribute("id");
    this.name = element.getAttribute("name");
    this.description = element.getAttribute("description");
    this.pageClassName = element.getAttribute("pageClass");
    this.icon = element.getAttribute("icon");
    this.updaterClassName = element.getAttribute("updaterClass");
    this.elementAdapterClassName = element.getAttribute("elementAdapterClass");
  }

  public String getId() {
    return id;
  }

  /** The name of the type, shown in the working set dialogs. */
  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  /** The class of the working set page (the dialog page that edits the working set), or null. */
  public String getPageClassName() {
    return pageClassName;
  }

  /** A working set can be edited when its type has a page. */
  public boolean isEditable() {
    return pageClassName != null;
  }

  public String getUpdaterClassName() {
    return updaterClassName;
  }

  public String getElementAdapterClassName() {
    return elementAdapterClassName;
  }

  /**
   * The icon of the type; the plug-in relative path with {@code $nl$} removed. VSCODE: the icons of the platform plug-ins
   * are packaged flat in {@code icons/shared}, so an {@code icons/full/obj16} path is read from there.
   */
  public ImageDescriptor getIcon() {
    if (icon == null)
      return null;
    String path = icon.replace("$nl$/", "");
    String obj16 = "icons/full/obj16/";
    if (path.startsWith(obj16))
      path = "icons/shared/" + path.substring(obj16.length());
    return ImageDescriptor.fromResource(path);
  }

  /** A new instance of the page class (the class of the type's page), or null. */
  public com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetPage createWorkingSetPage() {
    if (pageClassName == null) {
      return null;
    }
    try {
      Class<?> c = Class.forName(com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry.portedClassName(pageClassName));
      return (com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetPage) c.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      com.unitesk.requality.core.log.RLogger.logError(e);
      return null;
    }
  }

  /** The updater of the type (a new instance), or null when the type has none. */
  public IWorkingSetUpdater createWorkingSetUpdater() {
    if (updaterClassName == null) {
      return null;
    }
    return (IWorkingSetUpdater) newInstance(ideClassName(updaterClassName));
  }

  /**
   * The element adapter of the type (a new instance), or null when the type has none. The class is given as
   * {@code class:data}; the BasicWorkingSetElementAdapter takes its data.
   */
  public IWorkingSetElementAdapter createWorkingSetElementAdapter() {
    if (elementAdapterClassName == null) {
      return null;
    }
    int colon = elementAdapterClassName.indexOf(':');
    String className = colon < 0 ? elementAdapterClassName : elementAdapterClassName.substring(0, colon);
    String data = colon < 0 ? null : elementAdapterClassName.substring(colon + 1);
    if ("org.eclipse.ui.BasicWorkingSetElementAdapter".equals(className)) {
      return new BasicWorkingSetElementAdapter(data);
    }
    return (IWorkingSetElementAdapter) newInstance(ideClassName(className));
  }

  /** The class of an Eclipse IDE class of this descriptor (the IDE classes that are ported here), else the ported class. */
  static String ideClassName(String name) {
    if ("org.eclipse.ui.internal.ide.ResourceWorkingSetUpdater".equals(name)) {
      return "com.unitesk.requality.vscode.ui.ide.ResourceWorkingSetUpdater";
    }
    return com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry.portedClassName(name);
  }

  private static Object newInstance(String className) {
    try {
      return Class.forName(className).getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      com.unitesk.requality.core.log.RLogger.logError(e);
      return null;
    }
  }

  public IConfigurationElement getConfigurationElement() {
    return element;
  }
}
