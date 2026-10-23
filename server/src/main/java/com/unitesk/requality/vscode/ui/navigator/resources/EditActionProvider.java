package com.unitesk.requality.vscode.ui.navigator.resources;

import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.ICommonMenuConstants;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.actions.CopyAction;
import com.unitesk.requality.vscode.ui.workbench.actions.DeleteResourceAction;
import com.unitesk.requality.vscode.ui.workbench.actions.PasteAction;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.EditActionProvider}: Copy, Paste and Delete of the
 * selected resources in group.edit, and the global copy/paste/delete handlers of the view.
 */
public class EditActionProvider extends CommonActionProvider {
  private Clipboard clipboard;
  private CopyAction copyAction;
  private PasteAction pasteAction;
  private DeleteResourceAction deleteAction;

  @Override
  public void init(ICommonActionExtensionSite anActionSite) {
    super.init(anActionSite);
    Supplier<Shell> shell = () -> anActionSite.getViewSite() == null ? null : anActionSite.getViewSite().getShell();
    clipboard = new Clipboard(null);
    pasteAction = new PasteAction(shell, clipboard);
    copyAction = new CopyAction(shell, clipboard, pasteAction);
    deleteAction = new DeleteResourceAction(shell);
  }

  private IStructuredSelection selection() {
    ISelection s = getContext() == null ? null : getContext().getSelection();
    return s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY;
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    IStructuredSelection selection = selection();
    copyAction.selectionChanged(selection);
    menu.appendToGroup(ICommonMenuConstants.GROUP_EDIT, copyAction);
    pasteAction.selectionChanged(selection);
    menu.insertAfter(copyAction.getId(), pasteAction);
    deleteAction.selectionChanged(selection);
    menu.insertAfter(pasteAction.getId(), deleteAction);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    updateActionBars();
    actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(), copyAction);
    actionBars.setGlobalActionHandler(ActionFactory.PASTE.getId(), pasteAction);
    actionBars.setGlobalActionHandler(ActionFactory.DELETE.getId(), deleteAction);
  }

  @Override
  public void updateActionBars() {
    if (getContext() == null)
      return; // the actions keep the selection they were last given
    IStructuredSelection selection = selection();
    copyAction.selectionChanged(selection);
    pasteAction.selectionChanged(selection);
    deleteAction.selectionChanged(selection);
  }

  @Override
  public void dispose() {
    if (clipboard != null)
      clipboard.dispose();
    super.dispose();
  }
}
