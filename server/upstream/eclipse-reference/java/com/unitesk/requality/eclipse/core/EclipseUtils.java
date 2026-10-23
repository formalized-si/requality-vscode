package com.unitesk.requality.eclipse.core;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.ui.PlatformUI;

public class EclipseUtils {
  public static void resetUndoRedo(){
    IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
    PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().dispose(context, true, true, true);
  }

}
