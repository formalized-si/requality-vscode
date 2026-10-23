package com.unitesk.requality.vscode.ui.ltk.resource;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.ide.undo.DeleteResourcesOperation;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringProcessor;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.participants.CheckConditionsContext;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/**
 * {@code org.eclipse.ltk.internal.core.refactoring.resource.DeleteResourcesProcessor}: deletes files, folders or projects
 * (a project with or without its contents on disk). The change runs the {@link DeleteResourcesOperation}, its preview
 * lists a "Delete '...'" change for each resource.
 */
public class DeleteResourcesProcessor extends RefactoringProcessor {
  private IResource[] resources;
  private boolean deleteContents;

  public DeleteResourcesProcessor(IResource[] resources) {
    this.resources = resources.clone();
  }

  /** {@code DeleteResourcesProcessor#getResourcesToDelete()}. */
  public IResource[] getResourcesToDelete() {
    return resources.clone();
  }

  /** {@code DeleteResourcesProcessor#setResources(IResource[], IProgressMonitor)}: the resources to delete. */
  public void setResources(IResource[] resources) {
    this.resources = resources.clone();
  }

  /** {@code DeleteResourcesProcessor#isDeleteContents()}. */
  public boolean isDeleteContents() {
    return deleteContents;
  }

  /** {@code DeleteResourcesProcessor#setDeleteContents(boolean)}. */
  public void setDeleteContents(boolean deleteContents) {
    this.deleteContents = deleteContents;
  }

  @Override
  public String getProcessorName() {
    return "Delete Resources";
  }

  @Override
  public boolean isApplicable() {
    return resources.length > 0;
  }

  @Override
  public Object[] getElements() {
    return resources.clone();
  }

  private boolean allProjects() {
    for (IResource r : resources)
      if (r.getType() != IResource.PROJECT)
        return false;
    return true;
  }

  @Override
  public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
    RefactoringStatus status = new RefactoringStatus();
    if (resources.length == 0) {
      status.addFatalError("Select the resources to delete.");
      return status;
    }
    boolean projects = false;
    boolean others = false;
    for (IResource r : resources) {
      if (r.getType() == IResource.PROJECT)
        projects = true;
      else
        others = true;
    }
    if (projects && others)
      status.addFatalError("Cannot delete projects and other resources in the same operation.");
    return status;
  }

  @Override
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
      throws CoreException, OperationCanceledException {
    RefactoringStatus result = new RefactoringStatus();
    List<IResource> remaining = new ArrayList<>(resources.length);
    for (IResource r : resources) {
      if (!r.exists())
        continue;
      remaining.add(r);
      if (r.isPhantom()) {
        result.addFatalError("Cannot delete phantom resource '" + r.getFullPath().makeRelative() + "'.");
      } else if (r.getType() == IResource.PROJECT ? deleteContents && r.isReadOnly() : r.isReadOnly()) {
        // VSCODE: a file or folder is always deleted from the file system here, so the read-only check applies to it too
        result.addFatalError("Cannot delete content for read-only resource '" + r.getFullPath().makeRelative() + "'.");
      }
    }
    if (remaining.size() != resources.length)
      resources = remaining.toArray(new IResource[0]);
    checkDirtyResources(result);
    return result;
  }

  /**
   * {@code DeleteResourcesProcessor#checkDirtyResources}: a file to delete that has unsaved changes in an editor is a
   * warning. VSCODE: the dirty documents are those of the VS Code editors ({@code client/dirtyDocuments}).
   */
  private void checkDirtyResources(RefactoringStatus result) {
    if (com.unitesk.requality.vscode.core.ServerContext.server() == null)
      return;
    com.google.gson.JsonElement dirty;
    try {
      dirty = com.unitesk.requality.vscode.core.ServerContext.client().call("client/dirtyDocuments",
          new com.google.gson.JsonObject());
    } catch (RuntimeException e) {
      return;
    }
    if (dirty == null || !dirty.isJsonArray())
      return;
    for (com.google.gson.JsonElement e : dirty.getAsJsonArray()) {
      java.io.File f = new java.io.File(e.getAsString()).getAbsoluteFile();
      for (IResource r : resources) {
        if (r.getType() == IResource.PROJECT && !((com.unitesk.requality.vscode.ui.resources.IProject) r).isOpen())
          continue;
        if (r.getLocation() == null)
          continue;
        java.io.File root = new java.io.File(r.getLocation().toOSString()).getAbsoluteFile();
        if (f.equals(root) || f.getPath().startsWith(root.getPath() + java.io.File.separator)) {
          IResource file = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace().getRoot()
              .getFileForLocation(com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(f.getPath()));
          result.addWarning("File '" + (file != null ? file.getFullPath().makeRelative().toString() : f.getPath()) + "' has unsaved changes.");
          break;
        }
      }
    }
  }

  @Override
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    List<Change> display = new ArrayList<>(resources.length);
    for (IResource r : resources)
      display.add(new ResourceChange("Delete '" + r.getName() + "'", null, false, r, null));
    boolean undoable = !(deleteContents && allProjects());
    return new ResourceChange("Delete Resources",
        new DeleteResourcesOperation(resources, "Delete Resources", deleteContents), undoable,
        resources.length == 1 ? resources[0] : null, null, display.toArray(new Change[0]))
        .withOperationOf(rs -> new DeleteResourcesOperation(rs, "Delete Resources", deleteContents));
  }
}
