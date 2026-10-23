package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;

/**
 * {@code org.eclipse.ui.actions.SelectionProviderAction}: an action that follows the structured selection of a
 * selection provider (its changes are passed to {@link #selectionChanged(IStructuredSelection)}).
 */
public abstract class SelectionProviderAction extends Action implements ISelectionChangedListener {
  protected final ISelectionProvider provider;
  private IStructuredSelection selection = StructuredSelection.EMPTY;

  protected SelectionProviderAction(ISelectionProvider provider, String text) {
    super(text);
    this.provider = provider;
    provider.addSelectionChangedListener(this);
  }

  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    ISelection s = event.getSelection();
    selectionChanged(s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY);
  }

  /** The selection changed: the action updates its state for it. */
  public void selectionChanged(IStructuredSelection selection) {
    this.selection = selection == null ? StructuredSelection.EMPTY : selection;
  }

  /** The selection the action was last given. */
  protected IStructuredSelection getStructuredSelection() {
    return selection;
  }

  /** Stops following the selection provider. */
  public void dispose() {
    provider.removeSelectionChangedListener(this);
  }
}
