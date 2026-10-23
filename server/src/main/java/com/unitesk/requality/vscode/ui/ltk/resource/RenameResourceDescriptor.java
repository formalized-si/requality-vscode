package com.unitesk.requality.vscode.ui.ltk.resource;

import com.unitesk.requality.vscode.ui.ltk.ProcessorBasedRefactoring;
import com.unitesk.requality.vscode.ui.ltk.Refactoring;
import com.unitesk.requality.vscode.ui.ltk.RefactoringDescriptor;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * {@code org.eclipse.ltk.core.refactoring.resource.RenameResourceDescriptor}: renames the resource at the path to the
 * new name (the refactoring of {@link RenameResourceProcessor}).
 */
public class RenameResourceDescriptor extends RefactoringDescriptor {
  public static final String ID = "org.eclipse.ltk.core.refactoring.renameResourceDescriptor";
  private String project;
  private IPath resourcePath;
  private String newName;

  public void setProject(String project) {
    this.project = project;
  }

  public String getProject() {
    return project;
  }

  public void setResourcePath(IPath resourcePath) {
    this.resourcePath = resourcePath;
  }

  public void setNewName(String newName) {
    this.newName = newName;
  }

  @Override
  public Refactoring createRefactoring(RefactoringStatus status) throws CoreException {
    IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(resourcePath);
    if (resource == null)
      throw new CoreException(new com.unitesk.requality.vscode.ui.runtime.Status(
          com.unitesk.requality.vscode.ui.runtime.IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
          "Resource does not exist: " + resourcePath));
    RenameResourceProcessor processor = new RenameResourceProcessor(resource);
    processor.setNewResourceName(newName);
    return new ProcessorBasedRefactoring(processor);
  }
}
