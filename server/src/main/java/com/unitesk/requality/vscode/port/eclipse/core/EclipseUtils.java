package com.unitesk.requality.vscode.port.eclipse.core;

import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

public class EclipseUtils {
  public static void resetUndoRedo(){
    IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
    PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().dispose(context, true, true, true);
  }

}
