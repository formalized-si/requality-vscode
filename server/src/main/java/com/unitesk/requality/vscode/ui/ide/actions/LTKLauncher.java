package com.unitesk.requality.vscode.ui.ide.actions;

import com.unitesk.requality.vscode.ui.commands.Command;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.ICommandService;
import com.unitesk.requality.vscode.ui.commands.IHandlerService;
import com.unitesk.requality.vscode.ui.commands.NotDefinedException;
import com.unitesk.requality.vscode.ui.commands.NotEnabledException;
import com.unitesk.requality.vscode.ui.commands.NotHandledException;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.ide.actions.LTKLauncher}: opens the refactoring wizards of the resource actions of the
 * navigator (Rename, Delete, Move) by executing the LTK commands. A command that is not defined or not handled makes the
 * caller use its own behaviour.
 */
public final class LTKLauncher {
  private static final String LTK_DELETE_ID = "org.eclipse.ltk.ui.refactoring.commands.deleteResources";
  private static final String LTK_MOVE_ID = "org.eclipse.ltk.ui.refactoring.commands.moveResources";
  private static final String LTK_RENAME_ID = "org.eclipse.ltk.ui.refactoring.commands.renameResource";

  private LTKLauncher() {
  }

  /**
   * {@code LTKLauncher#openDeleteWizard(IStructuredSelection)}: true if the wizard can be launched. The handler works on
   * the current selection of the workbench, which is the selection of the explorer when the action runs.
   */
  public static boolean openDeleteWizard(IStructuredSelection structuredSelection) {
    return runCommand(LTK_DELETE_ID);
  }

  /** {@code LTKLauncher#openMoveWizard(IStructuredSelection)}. */
  public static boolean openMoveWizard(IStructuredSelection structuredSelection) {
    return runCommand(LTK_MOVE_ID);
  }

  /** {@code LTKLauncher#openRenameWizard(IStructuredSelection)}: the new name is asked for in the wizard. */
  public static boolean openRenameWizard(IStructuredSelection structuredSelection) {
    return runCommand(LTK_RENAME_ID);
  }

  private static boolean runCommand(String commandId) {
    ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
    Command cmd = commandService.getCommand(commandId);
    if (cmd == null || !cmd.isDefined())
      return false;
    IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
    try {
      Object commandResult = handlerService.executeCommand(commandId, null);
      if (commandResult instanceof Boolean)
        return (Boolean) commandResult;
      return true;
    } catch (ExecutionException | NotDefinedException | NotEnabledException | NotHandledException e) {
      return false;
    }
  }
}
