package com.unitesk.requality.vscode.ui.ide.undo;

/**
 * {@code org.eclipse.ui.internal.ide.undo.UndoMessages}: the messages of the workspace operations (the English text of
 * messages.properties, bound with {@link #bind}).
 */
final class UndoMessages {
  static final String AbstractWorkspaceOperation_ExecuteErrorTitle = "Error while executing the \"{0}\" operation";
  static final String AbstractWorkspaceOperation_UndoErrorTitle = "Error while undoing the \"{0}\" operation";
  static final String AbstractWorkspaceOperation_RedoErrorTitle = "Error while redoing the \"{0}\" operation";
  static final String AbstractWorkspaceOperation_ErrorInvalidMessage = "The operation {0} is no longer valid.";
  static final String AbstractWorkspaceOperation_GenericWarningMessage = "The operation {0} may not complete as expected.";

  static final String AbstractResourcesOperation_ResourcesDoNotExist = "Cannot complete operation because resources no longer exist.";
  static final String AbstractResourcesOperation_ResourcesAlreadyExist = "Cannot create or restore resource because it already exists.";
  static final String AbstractResourcesOperation_NotEnoughInfo = "There is not enough information to complete the resource operation.";
  static final String AbstractResourcesOperation_InvalidRestoreInfo = "There is not enough information to create or restore the resource.";
  static final String AbstractResourcesOperation_DeleteResourcesProgress = "Deleting resources...";
  static final String AbstractResourcesOperation_CreateResourcesProgress = "Creating resources...";
  static final String AbstractResourcesOperation_CopyingResourcesProgress = "Copying resources...";
  static final String AbstractResourcesOperation_MovingResources = "Moving resources...";
  static final String AbstractResourcesOperation_deletionExceptionMessage = "Multiple problems occurred while deleting resources.";

  static final String AbstractCopyOrMoveResourcesOperation_SameNameOrLocation = "Resource cannot be moved or copied to the same location and name.";
  static final String AbstractCopyOrMoveResourcesOperation_ResourceDoesNotExist = "Resource no longer exists in the workspace.";
  static final String AbstractCopyOrMoveResourcesOperation_copyProjectProgress = "Copying project...";
  static final String AbstractCopyOrMoveResourcesOperation_moveProjectProgress = "Moving project...";

  static final String CopyResourcesOperation_NotAllowedDueToDataLoss = "The original resources that were copied no longer exist. Undoing the copy is no longer valid, since data could be lost.";

  static final String MarkerOperation_ResourceDoesNotExist = "Cannot complete operation because resource no longer exists.";
  static final String MarkerOperation_MarkerDoesNotExist = "Cannot complete operation because marker no longer exists.";
  static final String MarkerOperation_NotEnoughInfo = "There is not enough information to perform the marker operation.";
  static final String MarkerOperation_CreateProgress = "Creating markers...";
  static final String MarkerOperation_DeleteProgress = "Deleting markers...";
  static final String MarkerOperation_UpdateProgress = " Updating markers...";

  private UndoMessages() {
  }

  /** The message with its {0}, {1}... replaced by the arguments (NLS.bind). */
  static String bind(String pattern, Object... args) {
    String s = pattern;
    for (int i = 0; i < args.length; i++)
      s = s.replace("{" + i + "}", String.valueOf(args[i]));
    return s;
  }
}
