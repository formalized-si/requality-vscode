package com.unitesk.requality.vscode.ui.workbench.menus;

import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolBar;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolItem;

/**
 * {@code org.eclipse.ui.menus.CommandContributionItem}: a button of a view toolbar for a command (plugin.xml
 * {@code toolbar:<view id>} menu contribution). Its id is the command id; the ToolItem of the button is its data.
 */
public class CommandContributionItem extends ContributionItem {
  private final String label;
  private final String icon;
  private final boolean toggle;

  public CommandContributionItem(String commandId, String label, String icon, boolean toggle) {
    super(commandId);
    this.label = label;
    this.icon = icon;
    this.toggle = toggle;
  }

  @Override
  public void fill(ToolBar parent, int index) {
    ToolItem ti = new ToolItem(parent, toggle ? SWT.CHECK : SWT.PUSH, index);
    ti.setData(this);
    if (label != null)
      ti.setToolTipText(label);
    if (icon != null)
      ti.setImage(ImageDescriptor.fromResource(icon).createImage());
  }
}
