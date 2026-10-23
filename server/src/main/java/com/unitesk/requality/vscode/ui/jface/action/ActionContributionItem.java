package com.unitesk.requality.vscode.ui.jface.action;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.MenuItem;

/** {@code org.eclipse.jface.action.ActionContributionItem}. */
public class ActionContributionItem extends ContributionItem {
  private final IAction action;

  public ActionContributionItem(IAction action) {
    super(action.getId());
    this.action = action;
  }

  public IAction getAction() {
    return action;
  }

  @Override
  public void fill(Menu menu, int index) {
    int style = action.getStyle() == IAction.AS_CHECK_BOX ? SWT.CHECK : action.getStyle() == IAction.AS_RADIO_BUTTON ? SWT.RADIO : SWT.PUSH;
    MenuItem mi = new MenuItem(menu, style, index);
    mi.setText(action.getText());
    mi.setEnabled(action.isEnabled());
    if (style != SWT.PUSH)
      mi.setSelection(action.isChecked());
    mi.addListener(SWT.Selection, e -> {
      if (action instanceof Action && style != SWT.PUSH)
        ((Action) action).setChecked(!action.isChecked());
      action.run();
    });
  }

  @Override
  public void fill(Composite parent) {
    Button b = new Button(parent, action.getStyle() == IAction.AS_CHECK_BOX ? SWT.TOGGLE : SWT.PUSH);
    b.setText(action.getText() == null ? "" : action.getText());
    if (action instanceof Action && ((Action) action).getToolTipText() != null)
      b.setToolTipText(((Action) action).getToolTipText());
    if (action instanceof Action && ((Action) action).getImageDescriptor() != null)
      b.setImage(((Action) action).getImageDescriptor().createImage());
    b.setEnabled(action.isEnabled());
    if (action.getStyle() == IAction.AS_CHECK_BOX)
      b.setSelection(action.isChecked());
    b.addListener(SWT.Selection, e -> {
      if (action.getStyle() == IAction.AS_CHECK_BOX)
        action.setChecked(b.getSelection());
      action.run();
    });
    // A drop-down tool item (IAction.AS_DROP_DOWN_MENU): the arrow next to the button shows the menu of its creator.
    if (action.getStyle() == IAction.AS_DROP_DOWN_MENU && action.getMenuCreator() != null) {
      Button arrow = new Button(parent, SWT.ARROW | SWT.DOWN);
      arrow.setEnabled(action.isEnabled());
      arrow.addListener(SWT.Selection, e -> {
        Menu menu = action.getMenuCreator().getMenu(arrow);
        if (menu != null)
          menu.setVisible(true);
      });
    }
  }
}
