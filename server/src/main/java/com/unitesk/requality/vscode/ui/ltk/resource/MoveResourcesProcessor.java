package com.unitesk.requality.vscode.ui.ltk.resource;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.ide.undo.MoveResourcesOperation;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringProcessor;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.participants.CheckConditionsContext;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/**
 * {@code org.eclipse.ltk.internal.core.refactoring.resource.MoveResourcesProcessor}: moves files and folders to a
 * destination container. The change runs the {@link MoveResourcesOperation} ("Move Resources"); its preview lists a
 * "Move resource ..." change for each resource.
 */
public class MoveResourcesProcessor extends RefactoringProcessor {
  private final IResource[] resourcesToMove;
  private IContainer destination;

  public MoveResourcesProcessor(IResource[] resources) {
    this.resourcesToMove = resources.clone();
  }

  /** {@code MoveResourcesProcessor#getResourcesToMove()}. */
  public IResource[] getResourcesToMove() {
    return resourcesToMove.clone();
  }

  /** {@code MoveResourcesProcessor#getDestination()}. */
  public IContainer getDestination() {
    return destination;
  }

  /** {@code MoveResourcesProcessor#setDestination(IContainer)}. */
  public void setDestination(IContainer destination) {
    this.destination = destination;
  }

  @Override
  public String getProcessorName() {
    return "Move Resources";
  }

  @Override
  public boolean isApplicable() {
    return resourcesToMove.length > 0;
  }

  @Override
  public Object[] getElements() {
    return resourcesToMove.clone();
  }

  @Override
  public RefactoringStatus checkInitialConditions(IProgressMonitor pm) {
    RefactoringStatus status = new RefactoringStatus();
    if (resourcesToMove.length == 0) {
      status.addFatalError("Select the resources to move.");
      return status;
    }
    for (IResource r : resourcesToMove) {
      if (r.getType() == IResource.PROJECT) {
        status.addFatalError("The move resource refactoring can not be performed as the project '" + r.getName()
            + "' is not a file or folder.");
        return status;
      }
      if (!r.exists()) {
        status.addFatalError("The moved resource '" + r.getName() + "' does not exist");
        return status;
      }
    }
    return status;
  }

  /**
   * {@code MoveResourcesProcessor#validateDestination(IContainer)}: the destination must not contain a resource to
   * move, nor be inside one.
   */
  public RefactoringStatus validateDestination(IContainer container) {
    if (container == null || container instanceof com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot)
      return RefactoringStatus.createFatalErrorStatus("Invalid parent");
    if (!container.exists())
      return RefactoringStatus.createFatalErrorStatus("Destination does not exist");
    com.unitesk.requality.vscode.ui.runtime.IPath destinationPath = container.getFullPath();
    for (IResource r : resourcesToMove) {
      com.unitesk.requality.vscode.ui.runtime.IPath path = r.getFullPath();
      if (path.isPrefixOf(destinationPath) || path.equals(destinationPath))
        return RefactoringStatus.createFatalErrorStatus("Destination is inside moved resource '" + path.makeRelative() + "'");
      if (path.removeLastSegments(1).equals(destinationPath))
        return RefactoringStatus.createFatalErrorStatus("The destination contains a resource to be moved");
    }
    return new RefactoringStatus();
  }

  /** A resource of the destination with the name of a moved one is replaced (a warning, as in Eclipse). */
  @Override
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
      throws CoreException, OperationCanceledException {
    RefactoringStatus status = validateDestination(destination);
    if (status.hasFatalError())
      return status;
    for (IResource resource : resourcesToMove) {
      IResource newResource = destination.findMember(resource.getName());
      if (newResource != null)
        status.addWarning("'" + newResource.getFullPath().makeRelative() + "' already exist. It will be replaced.");
    }
    return status;
  }

  @Override
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    List<Change> display = new ArrayList<>(resourcesToMove.length);
    for (IResource r : resourcesToMove)
      display.add(new ResourceChange("Move resource '" + r.getName() + "' to '" + destination.getFullPath() + "'",
          null, false, r, null));
    return new ResourceChange("Move Resources",
        new MoveResourcesOperation(resourcesToMove, destination.getFullPath(), "Move Resources"), true,
        resourcesToMove.length == 1 ? resourcesToMove[0] : null, null, display.toArray(new Change[0]))
        .withOperationOf(rs -> new MoveResourcesOperation(rs, destination.getFullPath(), "Move Resources"));
  }
}
