package com.unitesk.requality.vscode.ui.workbench.operations;

import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionGroup;

/** {@code org.eclipse.ui.operations.UndoRedoActionGroup}: Undo and Redo as global actions of a part. */
public final class UndoRedoActionGroup extends ActionGroup {
  private final UndoActionHandler undoActionHandler;
  private final RedoActionHandler redoActionHandler;

  public UndoRedoActionGroup(IWorkbenchPartSite site, IUndoContext undoContext, boolean pruneHistory) {
    undoActionHandler = new UndoActionHandler(site, undoContext);
    undoActionHandler.setPruneHistory(pruneHistory);
    redoActionHandler = new RedoActionHandler(site, undoContext);
    redoActionHandler.setPruneHistory(pruneHistory);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    super.fillActionBars(actionBars);
    if (undoActionHandler != null) {
      actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(), undoActionHandler);
      actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(), redoActionHandler);
    }
  }
}
