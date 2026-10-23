package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.jface.action.GroupMarker;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.ObjectActionContributor}: an {@code objectContribution} of the popupMenus extension point.
 * It applies to a selection when every selected element is of the {@code objectClass} (adapted to it when
 * {@code adaptable}), has a name matching {@code nameFilter} and satisfies its {@code enablement}. Its {@code menu}
 * elements are added to the context menu, its {@code action} elements are placed by {@code menubarPath}.
 */
final class ObjectActionContributor {
  private final IConfigurationElement element;
  private final Class<?> objectClass;
  private final boolean adaptable;
  private final Pattern nameFilter;

  ObjectActionContributor(IConfigurationElement element) {
    this.element = element;
    String cls = element.getAttribute("objectClass");
    Class<?> c = null;
    try {
      c = cls == null ? null : Class.forName(ExtensionRegistry.portedClassName(cls));
    } catch (ClassNotFoundException | LinkageError e) {
      RLogger.logInfo("Object class " + cls + " of popup contributions is not available");
    }
    objectClass = c;
    adaptable = "true".equals(element.getAttribute("adaptable"));
    nameFilter = element.getAttribute("nameFilter") == null ? null : glob(element.getAttribute("nameFilter"));
  }

  /** A '*' and '?' pattern as a regular expression (ignoring case, as the resource name filters of Eclipse). */
  static Pattern glob(String pattern) {
    StringBuilder sb = new StringBuilder();
    for (char ch : pattern.toCharArray()) {
      if (ch == '*')
        sb.append(".*");
      else if (ch == '?')
        sb.append('.');
      else
        sb.append(Pattern.quote(String.valueOf(ch)));
    }
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
  }

  /** The number of selected elements accepted by {@code enablesFor}: "1", "2+", "n" (n elements), or "*" (any). */
  static boolean enablesFor(String enablesFor, int count) {
    if (enablesFor == null || "*".equals(enablesFor))
      return count > 0;
    if (enablesFor.endsWith("+"))
      return count >= Integer.parseInt(enablesFor.substring(0, enablesFor.length() - 1));
    if (enablesFor.startsWith("!"))
      return count != Integer.parseInt(enablesFor.substring(1));
    return count == Integer.parseInt(enablesFor);
  }

  /** Whether the contribution applies to the (non-empty) selection. */
  boolean isApplicableTo(IStructuredSelection sel) {
    if (objectClass == null || sel == null || sel.isEmpty())
      return false;
    for (Object o : sel.toList()) {
      Object a = adaptable ? Adapters.adapt(o, objectClass) : objectClass.isInstance(o) ? o : null;
      if (a == null)
        return false;
      if (nameFilter != null && !nameFilter.matcher(name(o)).matches())
        return false;
      IConfigurationElement[] en = element.getChildren("enablement");
      if (en.length > 0) {
        EvaluationContext ctx = new EvaluationContext(null, o);
        ctx.addVariable("selection", sel);
        if (!Expressions.evaluateContainer(en[0], ctx))
          return false;
      }
    }
    return true;
  }

  private static String name(Object o) {
    IResource r = Adapters.adapt(o, IResource.class);
    return r == null ? "" : r.getName();
  }

  /** The {@code action} elements of the contribution. */
  List<IConfigurationElement> actions() {
    return java.util.Arrays.asList(element.getChildren("action"));
  }

  /** The {@code menu} elements of the contribution. */
  List<IConfigurationElement> menus() {
    return java.util.Arrays.asList(element.getChildren("menu"));
  }

  /**
   * Creates the submenu of a {@code menu} element (its separators and group markers) and places it in the group of its path.
   */
  static MenuManager createMenu(IMenuManager target, IConfigurationElement m) {
    MenuManager menu = new MenuManager(label(m.getAttribute("label")), m.getAttribute("id"));
    for (IConfigurationElement g : m.getChildren()) {
      if ("separator".equals(g.getName()))
        menu.add(new Separator(g.getAttribute("name")));
      else if ("groupMarker".equals(g.getName()))
        menu.add(new GroupMarker(g.getAttribute("name")));
    }
    String path = m.getAttribute("path");
    if (path != null && hasGroup(target, path))
      target.appendToGroup(path, menu);
    else
      target.add(menu);
    return menu;
  }

  /** The action of the contribution element (an {@link ObjectPluginAction}), with its id and tooltip. */
  ObjectPluginAction createActionItem(IConfigurationElement a, IStructuredSelection sel) {
    String id = a.getAttribute("id");
    ObjectPluginAction action = new ObjectPluginAction(a, label(a.getAttribute("label")), a.getAttribute("enablesFor"));
    action.setId(id);
    action.setToolTipText(label(a.getAttribute("tooltip")));
    action.selectionChanged(sel);
    return action;
  }

  /** The ids of the actions this contribution overrides ({@code overrideActionId}). */
  void addOverrides(List<String> overridden) {
    for (IConfigurationElement a : element.getChildren("action"))
      if (a.getAttribute("overrideActionId") != null)
        overridden.add(a.getAttribute("overrideActionId"));
  }

  private static boolean hasGroup(IMenuManager menu, String group) {
    for (IContributionItem i : menu.getItems())
      if (i.isGroupMarker() && group.equals(i.getId()))
        return true;
    return false;
  }

  /** Eclipse mnemonics are not shown in the context menus of VS Code. */
  static String label(String text) {
    return text == null ? null : text.replace("&", "");
  }

  /** The contributions that apply to a selection, in the order of plugin.xml. */
  static List<ObjectActionContributor> applicable(IConfigurationElement[] elements, IStructuredSelection sel) {
    List<ObjectActionContributor> r = new ArrayList<>();
    for (IConfigurationElement e : elements) {
      if (!"objectContribution".equals(e.getName()))
        continue;
      ObjectActionContributor c = new ObjectActionContributor(e);
      if (c.isApplicableTo(sel))
        r.add(c);
    }
    return r;
  }
}
