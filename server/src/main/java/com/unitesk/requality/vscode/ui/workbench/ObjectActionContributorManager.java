package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.GroupMarker;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.ObjectActionContributorManager}: adds the {@code objectContribution}s of the popupMenus
 * extension point that apply to the selection to a context menu, in two passes as Eclipse does: first the {@code menu}
 * elements of every applicable contribution (and the ids their actions override), then their actions that are not
 * overridden. Menus and actions are inserted after the group of their path ({@code PluginActionBuilder}); a menu whose
 * actions are all elsewhere stays invisible (MenuManager). An action whose {@code enablesFor} does not accept the
 * selection is shown disabled ({@link ObjectPluginAction}).
 */
public final class ObjectActionContributorManager {
  private static final String POPUP_MENUS = "org.eclipse.ui.popupMenus";
  private static final String MB_ADDITIONS = "additions";

  private ObjectActionContributorManager() {
  }

  /** The popup contributions for the selection, added to the menu after the contributions of its own part. */
  public static void contribute(IMenuManager menu, ISelection selection) {
    if (!(selection instanceof IStructuredSelection) || selection.isEmpty())
      return;
    IStructuredSelection sel = (IStructuredSelection) selection;
    List<ObjectActionContributor> contributors = ObjectActionContributor.applicable(
        ExtensionRegistry.get().getConfigurationElementsFor(POPUP_MENUS), sel);
    // First pass: the menus, and the overrides.
    List<String> overridden = new ArrayList<>();
    for (ObjectActionContributor c : contributors) {
      for (IConfigurationElement m : c.menus())
        contributeMenu(m, menu);
      c.addOverrides(overridden);
    }
    // Second pass: the actions that are not overridden.
    for (ObjectActionContributor c : contributors)
      for (IConfigurationElement a : c.actions())
        if (!overridden.contains(a.getAttribute("id")))
          contributeMenuAction(c, a, menu, sel);
  }

  /** {@code PluginActionBuilder.BasicContribution.contributeMenu}: the menu inserted after the group of its path. */
  private static void contributeMenu(IConfigurationElement menuElement, IMenuManager mng) {
    String id = menuElement.getAttribute("id");
    String label = menuElement.getAttribute("label");
    String path = menuElement.getAttribute("path");
    if (label == null)
      return;
    String group = null;
    if (path != null) {
      int loc = path.lastIndexOf('/');
      if (loc != -1) {
        group = path.substring(loc + 1);
        path = path.substring(0, loc);
      } else {
        group = path;
        path = null;
      }
    }
    IMenuManager parent = mng;
    if (path != null) {
      parent = findMenuUsingPath(mng, path);
      if (parent == null) {
        RLogger.logDebug("Invalid Menu Extension (Path '" + path + "' is invalid): " + id, 7);
        return;
      }
    }
    if (group == null)
      group = MB_ADDITIONS;
    if (parent.find(group) == null)
      addGroup(parent, group);
    IMenuManager newMenu = findMenuUsingPath(parent, id);
    if (newMenu == null) {
      newMenu = new MenuManager(ObjectActionContributor.label(label), id);
      parent.insertAfter(group, newMenu);
    }
    for (IConfigurationElement child : menuElement.getChildren()) {
      String name = child.getAttribute("name");
      if (name == null || newMenu.find(name) != null)
        continue;
      if ("separator".equals(child.getName()))
        newMenu.add(new Separator(name));
      else if ("groupMarker".equals(child.getName()))
        newMenu.add(new GroupMarker(name));
    }
  }

  /** {@code PluginActionBuilder.BasicContribution.contributeMenuAction}: the action inserted after its group. */
  private static void contributeMenuAction(ObjectActionContributor c, IConfigurationElement a, IMenuManager menu,
      IStructuredSelection sel) {
    String menubarPath = a.getAttribute("menubarPath");
    String mpath = null;
    String mgroup = null;
    if (menubarPath != null) {
      int loc = menubarPath.lastIndexOf('/');
      if (loc != -1) {
        mpath = menubarPath.substring(0, loc);
        mgroup = menubarPath.substring(loc + 1);
      } else {
        mgroup = menubarPath;
      }
    }
    if (mpath == null && mgroup == null)
      return;
    IMenuManager parent = menu;
    if (mpath != null) {
      parent = findMenuUsingPath(menu, mpath);
      if (parent == null) {
        RLogger.logDebug("Invalid Menu Extension (Path '" + mpath + "' is invalid): " + a.getAttribute("id"), 7);
        return;
      }
    }
    if (mgroup == null)
      mgroup = MB_ADDITIONS;
    if (parent.find(mgroup) == null)
      addGroup(parent, mgroup);
    parent.insertAfter(mgroup, c.createActionItem(a, sel));
  }

  /** {@code IMenuManager.findMenuUsingPath}: the submenu at the path of menu ids ("a/b"), or null. */
  private static IMenuManager findMenuUsingPath(IMenuManager menu, String path) {
    IMenuManager m = menu;
    for (String id : path.split("/")) {
      IContributionItem item = m.find(id);
      if (!(item instanceof IMenuManager))
        return null;
      m = (IMenuManager) item;
    }
    return m;
  }

  /** {@code PluginActionBuilder.addGroup}: a separator for a group that is missing. */
  private static void addGroup(IMenuManager mgr, String name) {
    mgr.add(new Separator(name));
  }
}
