package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.preference.PreferenceDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.window.IShellProvider;
import com.unitesk.requality.vscode.ui.workbench.actions.SelectionProviderAction;

/**
 * {@code org.eclipse.ui.dialogs.PropertyDialogAction}: opens the Properties dialog of the selected elements; enabled
 * for a selection that has property pages.
 */
public class PropertyDialogAction extends SelectionProviderAction {
  private final IShellProvider shellProvider;
  private String initialPageId;

  public PropertyDialogAction(IShellProvider shell, ISelectionProvider provider) {
    super(provider, "P&roperties");
    if (shell == null)
      throw new IllegalArgumentException("shell provider");
    this.shellProvider = shell;
    setToolTipText("Open Properties Dialog");
  }

  /** The page to select when the dialog opens (null for the last one). */
  public void setInitialPageId(String pageId) {
    initialPageId = pageId;
  }

  /** The selection has property pages (the action is enabled and the selection is not empty). */
  public boolean isApplicableForSelection() {
    if (!isEnabled())
      return false;
    return isApplicableForSelection(getStructuredSelection());
  }

  public boolean isApplicableForSelection(IStructuredSelection selection) {
    return !selection.isEmpty() && PropertyPageContributorManager.getManager().getApplicableContributors(selection).size() != 0;
  }

  @Override
  public void run() {
    PreferenceDialog dialog = createDialog();
    if (dialog != null)
      dialog.open();
  }

  /** The Properties dialog of the selection; null when nothing is selected or no page applies. */
  public PropertyDialog createDialog() {
    if (getStructuredSelection().isEmpty())
      return null;
    return PropertyDialog.createDialogOn(shellProvider.getShell(), initialPageId, getStructuredSelection());
  }

  @Override
  public void selectionChanged(IStructuredSelection selection) {
    super.selectionChanged(selection);
    setEnabled(!selection.isEmpty());
  }
}
