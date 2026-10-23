package com.unitesk.requality.vscode.ui.workbench.operations;

import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.operations.OperationHistoryActionHandler}: the Undo or Redo action of a part for an undo
 * context of the workbench operation history: its text names the operation ("Undo Copy Resources"), it is enabled
 * when the history can undo (redo), and it runs the undo (redo) of the history.
 */
public abstract class OperationHistoryActionHandler extends Action implements IAdaptable {
  private final IWorkbenchPartSite site;
  private final IUndoContext undoContext;
  private boolean pruneHistory = false;

  OperationHistoryActionHandler(IWorkbenchPartSite site, IUndoContext context) {
    super("");
    this.site = site;
    this.undoContext = context;
    update();
  }

  abstract void flush();

  abstract String getCommandString();

  abstract String getTooltipString();

  abstract String getSimpleCommandString();

  abstract String getSimpleTooltipString();

  abstract IUndoableOperation getOperation();

  abstract IStatus runCommand(IProgressMonitor pm) throws com.unitesk.requality.vscode.ui.commands.ExecutionException;

  abstract boolean shouldBeEnabled();

  IOperationHistory getHistory() {
    return PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
  }

  IUndoContext getUndoContext() {
    return undoContext;
  }

  public void setPruneHistory(boolean prune) {
    pruneHistory = prune;
  }

  Shell getShell() {
    return site == null ? PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell() : site.getShell();
  }

  @Override
  public final void run() {
    if (!shouldBeEnabled())
      return;
    try {
      IStatus status = runCommand(new NullProgressMonitor());
      if (status != null && !status.isOK() && status.getSeverity() == IStatus.ERROR)
        MessageDialog.openError(getShell(), getSimpleTooltipString() + " Problem", status.getMessage());
    } catch (com.unitesk.requality.vscode.ui.commands.ExecutionException e) {
      if (pruneHistory)
        flush();
      MessageDialog.openError(getShell(), getSimpleTooltipString() + " Problem", e.getMessage());
    }
    update();
  }

  /** The text, tool tip and enablement from the operation the history would undo (redo). */
  public void update() {
    boolean enabled = shouldBeEnabled();
    String text;
    String tooltipText;
    if (enabled) {
      // VSCODE: Eclipse appends '@' to keep the key binding text of the menu item; not used by VS Code
      tooltipText = getTooltipString().replace("{0}", getOperation().getLabel());
      text = getCommandString().replace("{0}", getOperation().getLabel().replace("&", "&&"));
    } else {
      tooltipText = "Can't " + getSimpleTooltipString();
      text = getSimpleCommandString();
    }
    setText(text);
    setToolTipText(tooltipText);
    setEnabled(enabled);
  }

  @Override
  public boolean isEnabled() {
    // the history changes outside of the handler (other parts, the server): the state is computed when asked
    return shouldBeEnabled();
  }

  @Override
  public String getText() {
    update0();
    return super.getText();
  }

  private void update0() {
    boolean enabled = shouldBeEnabled();
    if (enabled)
      super.setText(getCommandString().replace("{0}", getOperation().getLabel().replace("&", "&&")));
    else
      super.setText(getSimpleCommandString());
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getAdapter(Class<T> adapter) {
    if (adapter == Shell.class)
      return (T) getShell();
    if (adapter == IWorkbenchPartSite.class)
      return (T) site;
    return null;
  }
}
