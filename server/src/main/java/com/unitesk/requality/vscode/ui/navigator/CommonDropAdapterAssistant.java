package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;

/**
 * {@code org.eclipse.ui.navigator.CommonDropAdapterAssistant}: contributes drop validation and handling to the
 * navigator (the explorer tree of VS Code calls the assistants of the navigator content extensions).
 */
public abstract class CommonDropAdapterAssistant {

  public CommonDropAdapterAssistant() {
  }

  /** Whether the transfer type can be dropped by this assistant (the local selection transfer of the navigator). */
  public boolean isSupportedType(TransferData transferType) {
    return LocalSelectionTransfer.getTransfer().isSupportedType(transferType);
  }

  /** {@code org.eclipse.ui.navigator.CommonDropAdapterAssistant#validateDrop}: the status of a drop on target (cancelled unless overridden). */
  public IStatus validateDrop(Object target, int operation, TransferData transferType) {
    return Status.CANCEL_STATUS;
  }

  /** The shell for the dialogs of the drop (the workbench window). */
  protected com.unitesk.requality.vscode.ui.swt.widgets.Shell getShell() {
    return com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
  }

  /** {@code org.eclipse.ui.navigator.CommonDropAdapterAssistant#handleDrop}: performs the drop on target (cancelled unless overridden). */
  public IStatus handleDrop(CommonDropAdapter dropAdapter, DropTargetEvent dropTargetEvent, Object target) {
    return Status.CANCEL_STATUS;
  }
}
