package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.CommandSupport;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.commands.IEvaluationContext;
import com.unitesk.requality.vscode.ui.commands.UIElement;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.PopupMenuExtender}: a context menu registered with a part site
 * ({@code registerContextMenu}) gets the plugin.xml menu contributions of {@code popup:<menu id>} (and
 * {@code popup:org.eclipse.ui.popup.any}) each time it is shown. Showing it activates the part, as the right click
 * that opens it does in Eclipse.
 */
public final class PopupMenuExtender {
  private PopupMenuExtender() {
  }

  public static void register(String menuId, MenuManager menu, ISelectionProvider provider, IWorkbenchPart part) {
    menu.addMenuListener(m -> fill(menuId, m, provider, part));
  }

  static void fill(String menuId, IMenuManager menu, ISelectionProvider provider, IWorkbenchPart part) {
    ISelection sel = provider == null ? null : provider.getSelection();
    if (part != null && Workbench.activePart() != part)
      Workbench.partActivated(part, sel);
    CommandSupport cs = CommandSupport.get();
    IEvaluationContext ctx = cs.currentState();
    if (sel != null) {
      ctx = new MenuContext(ctx, sel);
      ctx.addVariable(ISources.ACTIVE_MENU_SELECTION_NAME, sel);
    }
    // as PopupMenuExtender.menuAboutToShow: the menu contributions, then the object contributions
    for (IConfigurationElement mc : menuContributions(menuId))
      addContributions(menu, mc, cs, ctx);
    for (IConfigurationElement mc : menuContributions("org.eclipse.ui.popup.any"))
      addContributions(menu, mc, cs, ctx);
    ObjectActionContributorManager.contribute(menu, sel);
  }

  /**
   * The plugin.xml contributions of {@code popup:<menu id>} and {@code popup:org.eclipse.ui.popup.any} for the selection,
   * added to a menu built for the selection (the menu/items of the explorer, which is not shown through a part site).
   */
  public static void contribute(String menuId, IMenuManager menu, ISelection sel) {
    CommandSupport cs = CommandSupport.get();
    IEvaluationContext ctx = cs.currentState();
    if (sel != null) {
      ctx = new MenuContext(ctx, sel);
      ctx.addVariable(ISources.ACTIVE_MENU_SELECTION_NAME, sel);
    }
    for (IConfigurationElement mc : menuContributions(menuId))
      addContributions(menu, mc, cs, ctx);
    for (IConfigurationElement mc : menuContributions("org.eclipse.ui.popup.any"))
      addContributions(menu, mc, cs, ctx);
  }

  /** The menuContribution elements of plugin.xml whose location is the popup menu of the id (without its query). */
  private static List<IConfigurationElement> menuContributions(String menuId) {
    List<IConfigurationElement> r = new ArrayList<>();
    for (IConfigurationElement mc : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.menus")) {
      String uri = "menuContribution".equals(mc.getName()) ? mc.getAttribute("locationURI") : null;
      if (uri == null)
        continue;
      String loc = uri.indexOf('?') >= 0 ? uri.substring(0, uri.indexOf('?')) : uri;
      if (loc.equals("popup:" + menuId))
        r.add(mc);
    }
    return r;
  }

  /**
   * The separators, commands and submenus ({@code menu} with its own contributions) of a menuContribution, placed as its
   * locationURI says ({@code ?after=<id>} / {@code ?before=<id>}; appended when the id is not in the menu).
   */
  private static void addContributions(IMenuManager menu, IConfigurationElement mc, CommandSupport cs,
      IEvaluationContext ctx) {
    String uri = mc.getAttribute("locationURI");
    String query = uri != null && uri.indexOf('?') >= 0 ? uri.substring(uri.indexOf('?') + 1) : "";
    Placement at = new Placement(menu, query);
    addChildren(at, mc, cs, ctx);
  }

  /** The separators, commands, submenus and dynamic sections of a menuContribution or of a menu. */
  private static void addChildren(Placement at, IConfigurationElement parent, CommandSupport cs, IEvaluationContext ctx) {
    for (IConfigurationElement c : parent.getChildren()) {
      if ("separator".equals(c.getName()))
        at.add(new Separator(c.getAttribute("name")));
      else if ("command".equals(c.getName()))
        addCommand(at, c, cs, ctx);
      else if ("menu".equals(c.getName()))
        addSubmenu(at, c, cs, ctx);
      else if ("dynamic".equals(c.getName()))
        addDynamic(at, c, ctx);
    }
  }

  /**
   * A dynamic section ({@code <dynamic class>}: a CompoundContributionItem that makes its items when the menu is
   * filled), when its visibleWhen holds.
   */
  private static void addDynamic(Placement at, IConfigurationElement d, IEvaluationContext ctx) {
    IConfigurationElement[] vw = d.getChildren("visibleWhen");
    if (vw.length > 0 && vw[0].getChildren().length > 0 && !Expressions.evaluateContainer(vw[0], ctx))
      return;
    try {
      Object item = d.createExecutableExtension("class");
      if (item instanceof IContributionItem) {
        if (item instanceof com.unitesk.requality.vscode.ui.jface.action.ContributionItem && d.getAttribute("id") != null)
          ((com.unitesk.requality.vscode.ui.jface.action.ContributionItem) item).setId(d.getAttribute("id"));
        at.add((IContributionItem) item);
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  /** Where the items of a menuContribution go: after or before an item of the menu, in their order. */
  private static final class Placement {
    private final IMenuManager menu;
    private int index = -1;

    Placement(IMenuManager menu, String query) {
      this.menu = menu;
      if (menu instanceof MenuManager) {
        MenuManager mm = (MenuManager) menu;
        if (query.startsWith("after=") && mm.indexOf(query.substring("after=".length())) >= 0)
          index = mm.indexOf(query.substring("after=".length())) + 1;
        else if (query.startsWith("before=") && mm.indexOf(query.substring("before=".length())) >= 0)
          index = mm.indexOf(query.substring("before=".length()));
      }
    }

    void add(IContributionItem item) {
      if (index >= 0)
        ((MenuManager) menu).insert(index++, item);
      else
        menu.add(item);
    }

    void add(com.unitesk.requality.vscode.ui.jface.action.IAction action) {
      add(new com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem(action));
    }
  }

  /**
   * A submenu of a popup ({@code <menu id label>} in a menuContribution, as the menu service of Eclipse creates it): shown
   * when its visibleWhen holds and it has items; its items are the contributions to {@code popup:<menu id>}.
   */
  private static void addSubmenu(Placement parent, IConfigurationElement m, CommandSupport cs, IEvaluationContext ctx) {
    IConfigurationElement[] vw = m.getChildren("visibleWhen");
    if (vw.length > 0) {
      // checkEnabled: the menu is shown when the handler of its command is enabled
      String command = m.getAttribute("commandId");
      if ("true".equals(vw[0].getAttribute("checkEnabled")) && command != null && !commandEnabled(cs, command))
        return;
      if (vw[0].getChildren().length > 0 && !Expressions.evaluateContainer(vw[0], ctx))
        return;
    }
    String id = m.getAttribute("id");
    String label = m.getAttribute("label");
    MenuManager sub = new MenuManager(label == null ? id : label, id);
    // its own items, then the contributions to popup:<menu id>
    addChildren(new Placement(sub, ""), m, cs, ctx);
    for (IConfigurationElement mc : menuContributions(id)) {
      addContributions(sub, mc, cs, ctx);
    }
    if (sub.getItems().length > 0)
      parent.add(sub);
  }

  private static boolean commandEnabled(CommandSupport cs, String id) {
    try {
      return cs.isEnabled(id);
    } catch (RuntimeException e) {
      RLogger.logError(e);
      return false;
    }
  }

  /** CommandContributionItem: the label of the contribution or of the handler (IElementUpdater), visibleWhen. */
  private static void addCommand(Placement menu, IConfigurationElement c, CommandSupport cs, IEvaluationContext ctx) {
    String id = c.getAttribute("commandId");
    boolean enabled;
    try {
      enabled = cs.isEnabled(id);
    } catch (RuntimeException e) {
      RLogger.logError(e);
      enabled = false;
    }
    IConfigurationElement[] vw = c.getChildren("visibleWhen");
    if (vw.length > 0) {
      if ("true".equals(vw[0].getAttribute("checkEnabled")) && !enabled)
        return;
      if (vw[0].getChildren().length > 0 && !Expressions.evaluateContainer(vw[0], ctx))
        return;
    }
    String label = c.getAttribute("label");
    if (label == null)
      try {
        label = cs.getCommand(id).getName();
      } catch (com.unitesk.requality.vscode.ui.commands.NotDefinedException e) {
        label = id;
      }
    UIElement el = cs.updateElement(id);
    if (el != null && el.getText() != null)
      label = el.getText();
    Map<String, String> params = new HashMap<>();
    for (IConfigurationElement p : c.getChildren("parameter"))
      params.put(p.getAttribute("name"), p.getAttribute("value"));
    Action a = new Action(label) {
      @Override
      public void run() {
        try {
          cs.execute(id, params, null);
        } catch (Exception e) {
          RLogger.logError(e);
        }
      }
    };
    a.setId(id);
    a.setEnabled(enabled);
    menu.add(a);
  }

  /** The evaluation context of a context menu: the default variable is the menu selection. */
  private static final class MenuContext extends EvaluationContext {
    MenuContext(IEvaluationContext parent, ISelection sel) {
      super(parent, sel);
    }
  }
}
