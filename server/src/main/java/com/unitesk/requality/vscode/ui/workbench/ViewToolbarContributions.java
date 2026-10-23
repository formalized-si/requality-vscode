package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.commands.CommandSupport;
import com.unitesk.requality.vscode.ui.commands.NotDefinedException;
import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolItem;
import com.unitesk.requality.vscode.ui.workbench.menus.CommandContributionItem;

/**
 * The view toolbar contributions of plugin.xml ({@code menuContribution locationURI="toolbar:<view id>"}): each command
 * becomes a button of the view's toolbar, as the workbench adds them when the view part is created.
 */
public final class ViewToolbarContributions {
  private ViewToolbarContributions() {
  }

  /** Adds the command buttons of {@code toolbar:<viewId>} to the toolbar manager of the view. */
  public static void fill(String viewId, ToolBarManager toolbar) {
    for (IConfigurationElement mc : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.menus")) {
      if (!"menuContribution".equals(mc.getName()) || !("toolbar:" + viewId).equals(mc.getAttribute("locationURI")))
        continue;
      for (IConfigurationElement c : mc.getChildren("command")) {
        String id = c.getAttribute("commandId");
        String label = c.getAttribute("label");
        if (label == null && CommandSupport.get() != null)
          try {
            label = CommandSupport.get().getCommand(id).getName();
          } catch (NotDefinedException e) {
            label = id;
          }
        toolbar.add(new CommandContributionItem(id, label, c.getAttribute("icon"), "toggle".equals(c.getAttribute("style"))));
      }
    }
  }

  /**
   * The trigger of a command run from a view/title button: an SWT Event whose widget is the ToolItem of the command in the
   * toolbar of the view (as HandledContributionItem gives it), or null when the view has no such button.
   */
  public static Event trigger(String viewId, String commandId) {
    IViewPart view = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findView(viewId);
    if (view == null)
      return null;
    for (ToolItem ti : view.getViewSite().getActionBars().getToolBarManager().getControl().getItems()) {
      Object data = ti.getData();
      if (data instanceof ContributionItem && commandId.equals(((ContributionItem) data).getId())) {
        // SWT: a CHECK tool item changes its selection when it is clicked, before the Selection event
        if (SWT.has(ti.getStyle(), SWT.CHECK))
          ti.setSelection(!ti.getSelection());
        Event e = new Event();
        e.display = ti.getDisplay();
        e.widget = ti;
        e.item = ti;
        e.type = SWT.Selection;
        return e;
      }
    }
    return null;
  }
}
