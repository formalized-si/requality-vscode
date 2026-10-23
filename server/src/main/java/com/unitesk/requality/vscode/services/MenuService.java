package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.rpc.RpcException;
import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.CompoundContributionItem;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.menus.ExtensionContributionFactory;
import com.unitesk.requality.vscode.ui.workbench.services.IServiceLocator;

/**
 * Dynamic menus of the Eclipse layer ({@code <dynamic>} menu contributions, menus built by views): the extension
 * lists their items with {@code menu/items} (shown in a quick pick or as a context submenu) and runs one with
 * {@code menu/run}. Items are computed on the UI thread for the current selection.
 */
public final class MenuService implements Services.Service {
  private static final Map<String, Supplier<IContributionItem[]>> MENUS = new ConcurrentHashMap<>();
  private static final Map<String, List<IAction>> LAST = new ConcurrentHashMap<>();

  /** Registers a dynamic menu by its plugin.xml id (or a view's menu id). */
  public static void register(String menuId, Supplier<IContributionItem[]> items) {
    MENUS.put(menuId, items);
  }

  /** Registers a CompoundContributionItem (created once, asked for items on every request). */
  public static void register(String menuId, CompoundContributionItem item) {
    MENUS.put(menuId, item::items);
  }

  /** The context menu of the Requality Explorer as far as the action providers fill it (CNFActionProvider). */
  public static final String EXPLORER_POPUP = "com.unitesk.requality.views.RequalityExplorer.popup";
  public static final String EXPLORER_VIEW_MENU = "com.unitesk.requality.views.RequalityExplorer.viewMenu";

  /**
   * The popup of the Requality Explorer for its selection: the items of the action providers, then (as
   * PopupMenuExtender) the plugin.xml menu contributions of the popup (the Requality commands, Configure of
   * org.eclipse.ui.ide.application, ...) and the popupMenus objectContributions of the platform (team, compare).
   */
  public static void fillExplorerPopup(com.unitesk.requality.vscode.ui.jface.action.IMenuManager m) {
    com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf = ExplorerService.navigator();
    cnf.getNavigatorActionService().fillContextMenu(m);
    com.unitesk.requality.vscode.ui.workbench.PopupMenuExtender.contribute(EXPLORER_POPUP, (MenuManager) m, cnf.getCommonViewer().getSelection());
    com.unitesk.requality.vscode.ui.workbench.ObjectActionContributorManager.contribute((MenuManager) m, cnf.getCommonViewer().getSelection());
  }

  @Override
  public void register(RequalityServer srv) {
    // The "New" submenu of the explorer (NewMenuPoint): the node types creatable under a node tag its item.
    ExplorerService.registerContextTags((node, out) -> {
      for (com.unitesk.requality.core.operation.create.CreateElementOperation op : com.unitesk.requality.core.operation.create.CreateElementOperation
          .getHandlers())
        if (op.isEnabled(node))
          for (String type : op.getElementTypes())
            out.add("new." + type);
    });
    register(EXPLORER_POPUP, () -> {
      MenuManager m = new MenuManager();
      fillExplorerPopup(m);
      return m.getItems();
    });
    // The view menu of the explorer: the items the action providers fill into its action bars (the working set actions
    // of WorkingSetActionProvider and the other view actions of the explorer).
    register(EXPLORER_VIEW_MENU, () -> {
      com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf = ExplorerService.navigator();
      com.unitesk.requality.vscode.ui.workbench.IActionBars bars = cnf.getViewSite().getActionBars();
      cnf.getNavigatorActionService().fillActionBars(bars);
      // CommonNavigatorActionGroup: Filters and Customization..., Recent Filters
      ExplorerService.viewer().fillViewMenu(bars);
      return bars.getMenuManager().getItems();
    });
    srv.rpc().onRequest("menu/items", p -> {
      srv.requireInitialized();
      String id = Nodes.string(p, "menu");
      Supplier<IContributionItem[]> menu = MENUS.computeIfAbsent(id, MenuService::fromExtensions);
      if (menu == null)
        throw RpcException.notFound("Menu " + id + " is not available");
      // the selection and the items are computed in one task of the UI thread: no other selection change comes between
      return Display.getDefault().syncCall(() -> {
        if (p.has("nodes") && p.get("nodes").isJsonArray())
          SelectionService.select(Nodes.optString(p, "source", "explorer"), p.getAsJsonArray("nodes"));
        List<IAction> actions = new ArrayList<>();
        JsonArray items = describe(menu.get(), actions);
        LAST.put(id, actions);
        return items;
      });
    });
    srv.rpc().onRequest("menu/run", p -> {
      srv.requireInitialized();
      String id = Nodes.string(p, "menu");
      int index = Nodes.optInt(p, "index", -1);
      List<IAction> actions = LAST.get(id);
      if (actions == null || index < 0 || index >= actions.size())
        throw RpcException.notFound("Menu item is no longer available");
      IAction a = actions.get(index);
      Display.getDefault().syncExec(() -> {
        try {
          if (a.isEnabled()) {
            if (a.getStyle() == IAction.AS_CHECK_BOX)
              a.setChecked(!a.isChecked());
            a.runWithEvent(null);
          }
        } catch (RuntimeException e) {
          RLogger.logError(e);
          throw e;
        }
      });
      return null;
    });
  }

  /**
   * Dynamic menu contributions of plugin.xml ({@code org.eclipse.ui.menus}): a {@code <dynamic id class>} element (a
   * CompoundContributionItem) by its id, or a {@code <menuContribution class>} (an ExtensionContributionFactory) by its
   * class name. The ported class is created once and asked for its items on every request.
   */
  private static Supplier<IContributionItem[]> fromExtensions(String id) {
    for (IConfigurationElement mc : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.menus")) {
      if (!"menuContribution".equals(mc.getName()))
        continue;
      if (id.equals(mc.getAttribute("class"))) {
        try {
          ExtensionContributionFactory f = (ExtensionContributionFactory) mc.createExecutableExtension("class");
          return () -> {
            List<IContributionItem> items = new ArrayList<>();
            f.createContributionItems(new IServiceLocator() {
              @Override
              public Object getService(Class<?> api) {
                return PlatformUI.getWorkbench().getService(api);
              }

              @Override
              public boolean hasService(Class<?> api) {
                return getService(api) != null;
              }
            }, (item, visibleWhen) -> items.add(item));
            return items.toArray(new IContributionItem[0]);
          };
        } catch (CoreException | ClassCastException e) {
          RLogger.logInfo("Menu contribution " + id + " is not available: " + e.getMessage());
          return null;
        }
      }
      IConfigurationElement dynamic = findDynamic(mc, id);
      if (dynamic != null) {
        try {
          CompoundContributionItem item = (CompoundContributionItem) dynamic.createExecutableExtension("class");
          return item::items;
        } catch (CoreException | ClassCastException e) {
          RLogger.logInfo("Dynamic menu " + id + " is not available: " + e.getMessage());
          return null;
        }
      }
    }
    return null;
  }

  private static IConfigurationElement findDynamic(IConfigurationElement e, String id) {
    for (IConfigurationElement c : e.getChildren()) {
      if ("dynamic".equals(c.getName()) && id.equals(c.getAttribute("id")))
        return c;
      IConfigurationElement r = findDynamic(c, id);
      if (r != null)
        return r;
    }
    return null;
  }

  private static JsonArray describe(IContributionItem[] items, List<IAction> actions) {
    JsonArray r = new JsonArray();
    if (items == null)
      return r;
    for (IContributionItem item : items) {
      if (item == null || !item.isVisible())
        continue;
      JsonObject o = new JsonObject();
      if (item instanceof Separator) {
        o.addProperty("separator", true);
      } else if (item instanceof ActionContributionItem) {
        IAction a = ((ActionContributionItem) item).getAction();
        o.addProperty("index", actions.size());
        actions.add(a);
        o.addProperty("label", a.getText() == null ? "" : a.getText().replace("&", "").split("\t")[0]);
        o.addProperty("enabled", a.isEnabled());
        if (a.getStyle() == IAction.AS_CHECK_BOX || a.getStyle() == IAction.AS_RADIO_BUTTON)
          o.addProperty("checked", a.isChecked());
        if (a.getToolTipText() != null)
          o.addProperty("tooltip", a.getToolTipText());
        if (a instanceof com.unitesk.requality.vscode.port.eclipse.views.actions.NewTypedTemplatedMenuPoint)
          o.addProperty("type", elementTypeOf((com.unitesk.requality.vscode.port.eclipse.views.actions.NewTypedTemplatedMenuPoint) a));
      } else if (item instanceof MenuManager) {
        MenuManager m = (MenuManager) item;
        o.addProperty("label", m.getMenuText() == null ? "" : m.getMenuText().replace("&", "").split("\t")[0]);
        o.addProperty("enabled", m.isEnabled());
        o.add("children", describe(m.getItems(), actions));
      } else if (item instanceof CompoundContributionItem) {
        for (com.google.gson.JsonElement e : describe(((CompoundContributionItem) item).items(), actions))
          r.add(e);
        continue;
      } else {
        continue;
      }
      r.add(o);
    }
    return r;
  }

  /** The node type created by a New menu item (the private field {@code type} of NewTypedTemplatedMenuPoint). */
  private static String elementTypeOf(com.unitesk.requality.vscode.port.eclipse.views.actions.NewTypedTemplatedMenuPoint point) {
    try {
      java.lang.reflect.Field f = com.unitesk.requality.vscode.port.eclipse.views.actions.NewTypedTemplatedMenuPoint.class.getDeclaredField("type");
      f.setAccessible(true);
      return (String) f.get(point);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }
}
