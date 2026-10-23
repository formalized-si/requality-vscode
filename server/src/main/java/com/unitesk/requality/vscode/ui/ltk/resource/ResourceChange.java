package com.unitesk.requality.vscode.ui.ltk.resource;

import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ltk.core.refactoring.resource} change of the resource refactorings: the change runs the undoable
 * workspace operation of the resources ({@code org.eclipse.ui.ide.undo}), so that it is recorded in the operation
 * history of the workbench with its label (Undo Rename Resource, Undo Delete Resources, ...). An operation that is not
 * undoable (the contents of projects deleted from disk) runs without the history, as Eclipse does. The preview children
 * describe the individual changes (Delete 'a.txt', ...) that the operation performs together.
 */
public class ResourceChange extends Change {
  private final String name;
  private final IUndoableOperation operation;
  private final boolean undoable;
  private final Object modifiedElement;
  private final Shell shell;
  private final Change[] previewChildren;
  /**
   * The operation of the enabled resources when some of the preview children are disabled (Eclipse performs only the
   * enabled changes of a composite change), or null.
   */
  private java.util.function.Function<com.unitesk.requality.vscode.ui.resources.IResource[], IUndoableOperation> operationOf;

  public ResourceChange(String name, IUndoableOperation operation, boolean undoable, Object modifiedElement,
      Shell shell) {
    this(name, operation, undoable, modifiedElement, shell, new Change[0]);
  }

  public ResourceChange(String name, IUndoableOperation operation, boolean undoable, Object modifiedElement,
      Shell shell, Change[] previewChildren) {
    this.name = name;
    this.operation = operation;
    this.undoable = undoable;
    this.modifiedElement = modifiedElement;
    this.shell = shell;
    this.previewChildren = previewChildren.clone();
  }

  /** The operation of the enabled children's resources (when the user unchecks some of them in the preview). */
  public ResourceChange withOperationOf(
      java.util.function.Function<com.unitesk.requality.vscode.ui.resources.IResource[], IUndoableOperation> factory) {
    this.operationOf = factory;
    return this;
  }

  @Override
  public String getName() {
    return name;
  }

  /** The changes the preview lists under this change. */
  public Change[] getChildren() {
    return previewChildren.clone();
  }

  /** Whether the operation is recorded in the operation history (Undo). */
  public boolean isUndoable() {
    return undoable;
  }

  @Override
  public void initializeValidationData(IProgressMonitor pm) {
  }

  @Override
  public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return new RefactoringStatus();
  }

  /**
   * {@code org.eclipse.ltk.core.refactoring.Change#perform}: the operation is executed in the operation history; its
   * undo is the history's, so no undo change is returned.
   */
  @Override
  public Change perform(IProgressMonitor pm) throws CoreException {
    if (!isEnabled())
      return null;
    IUndoableOperation op = operation;
    if (operationOf != null && previewChildren.length > 0) {
      java.util.List<com.unitesk.requality.vscode.ui.resources.IResource> enabled = new java.util.ArrayList<>();
      for (Change child : previewChildren)
        if (child.isEnabled() && child.getModifiedElement() instanceof com.unitesk.requality.vscode.ui.resources.IResource r)
          enabled.add(r);
      if (enabled.isEmpty())
        return null;
      if (enabled.size() < previewChildren.length)
        op = operationOf.apply(enabled.toArray(new com.unitesk.requality.vscode.ui.resources.IResource[0]));
    }
    IProgressMonitor monitor = pm == null ? new NullProgressMonitor() : pm;
    if (undoable)
      WorkspaceUndoUtil.execute(op, monitor, shell);
    else
      WorkspaceUndoUtil.executeWithoutHistory(op, monitor, shell);
    return null;
  }

  @Override
  public Object getModifiedElement() {
    return modifiedElement;
  }
}
