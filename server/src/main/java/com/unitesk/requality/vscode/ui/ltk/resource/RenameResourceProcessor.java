package com.unitesk.requality.vscode.ui.ltk.resource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.ide.undo.MoveResourcesOperation;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.CompositeChange;
import com.unitesk.requality.vscode.ui.ltk.RefactoringProcessor;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;
import com.unitesk.requality.vscode.ui.ltk.participants.CheckConditionsContext;
import com.unitesk.requality.vscode.ui.ltk.participants.RenameArguments;
import com.unitesk.requality.vscode.ui.ltk.participants.RenameParticipant;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;

/**
 * {@code org.eclipse.ltk.internal.core.refactoring.resource.RenameResourceProcessor}: renames a file, a folder or a
 * project. A project is renamed with the rename participants of plugin.xml
 * ({@code org.eclipse.ltk.core.refactoring.renameParticipants}, here the Requality ProjectRenameParticipant): they check
 * the conditions, their pre-changes run, the project gets its new name and their changes run.
 */
public class RenameResourceProcessor extends RefactoringProcessor {
  private final IResource resource;
  private String newResourceName;

  public RenameResourceProcessor(IResource resource) {
    this.resource = resource;
    this.newResourceName = resource.getName();
  }

  /** {@code RenameResourceProcessor#getResource()}. */
  public IResource getResource() {
    return resource;
  }

  /** {@code RenameResourceProcessor#getNewResourceName()}. */
  public String getNewResourceName() {
    return newResourceName;
  }

  /** {@code RenameResourceProcessor#setNewResourceName(String)}. */
  public void setNewResourceName(String newResourceName) {
    this.newResourceName = newResourceName;
  }

  @Override
  public String getProcessorName() {
    return "Rename Resource";
  }

  @Override
  public boolean isApplicable() {
    return resource.exists();
  }

  @Override
  public Object[] getElements() {
    return new Object[] { resource };
  }

  /** The name of the project as Eclipse shows it: the name of its description (.project). */
  public static String projectName(IProject p) {
    String n = CoreUtils.getProjectName(p.getLocation().toFile());
    return n == null ? p.getName() : n;
  }

  /**
   * {@code RenameResourceProcessor#validateNewElementName(String)}: the messages of the rename input (the name is a
   * fatal error while it is not valid, so the page cannot finish).
   */
  public RefactoringStatus validateNewElementName(String newName) {
    RefactoringStatus status = new RefactoringStatus();
    if (newName == null || newName.trim().isEmpty()) {
      status.addFatalError("Enter a new name");
      return status;
    }
    if (resource.getType() == IResource.PROJECT) {
      String current = projectName((IProject) resource);
      if (newName.equals(current)) {
        status.addFatalError("'" + newName + "' is the current name");
      } else if (newName.matches(".*[\\\\/:*?\"<>|].*")) {
        status.addFatalError("'" + newName + "' is not a valid project name");
      } else {
        for (IProject other : ResourcesPlugin.getWorkspace().getRoot().getProjects())
          if (other != null && !other.equals(resource) && new File(other.getLocation().toFile(), ".project").isFile()
              && newName.equals(projectName(other))) {
            status.addFatalError("A project with that name already exists");
            break;
          }
      }
      return status;
    }
    if (newName.equals(resource.getName())) {
      status.addFatalError("'" + newName + "' is the current name");
      return status;
    }
    if (ResourcesPlugin.getWorkspace().validateName(newName, resource.getType()).matches(IStatus.ERROR)) {
      status.addFatalError("This is an invalid name for a file or folder");
      return status;
    }
    IContainer parent = resource.getParent();
    if (parent != null && parent.findMember(newName) != null)
      status.addFatalError("A file or folder with this name already exists");
    return status;
  }

  @Override
  public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
    RefactoringStatus status = new RefactoringStatus();
    if (!resource.exists())
      status.addFatalError("The resource '" + resource.getName() + "' does not exist");
    else if (resource.getParent() == null && resource.getType() != IResource.PROJECT)
      status.addFatalError("Internal Error");
    return status;
  }

  @Override
  public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
      throws CoreException, OperationCanceledException {
    RefactoringStatus status = new RefactoringStatus();
    if (resource.getType() != IResource.PROJECT)
      return status;
    for (RenameParticipant p : participants(resource, new RenameArguments(newResourceName, true)))
      status.merge(p.checkConditions(pm, context));
    return status;
  }

  @Override
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    if (resource.getType() != IResource.PROJECT) {
      IContainer parent = resource.getParent();
      return new ResourceChange("Rename '" + resource.getName() + "' to '" + newResourceName + "'",
          new MoveResourcesOperation(resource, parent.getFullPath().append(newResourceName), "Rename Resource"), true,
          resource, null);
    }
    IProject project = (IProject) resource;
    String oldName = projectName(project);
    RenameArguments args = new RenameArguments(newResourceName, true);
    List<RenameParticipant> ps = participants(project, args);
    CompositeChange main = new CompositeChange("Rename '" + oldName + "' to '" + newResourceName + "'");
    for (RenameParticipant p : ps) {
      Change c = p.createPreChange(pm);
      if (c != null)
        main.add(c);
    }
    main.add(new ProjectRenameChange(project, oldName, newResourceName));
    for (RenameParticipant p : ps) {
      Change c = p.createChange(pm);
      if (c != null)
        main.add(c);
    }
    return main;
  }

  /** The rename participants of plugin.xml enabled for the element, initialized with the arguments. */
  static List<RenameParticipant> participants(Object element, RenameArguments args) {
    List<RenameParticipant> r = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get()
        .getConfigurationElementsFor("org.eclipse.ltk.core.refactoring.renameParticipants")) {
      if (!"renameParticipant".equals(e.getName()))
        continue;
      IConfigurationElement[] en = e.getChildren("enablement");
      if (en.length > 0 && !Expressions.evaluateContainer(en[0], new EvaluationContext(null, element)))
        continue;
      try {
        RenameParticipant p = (RenameParticipant) e.createExecutableExtension("class");
        if (p.initialize(element, args))
          r.add(p);
      } catch (CoreException | ClassCastException ex) {
        RLogger.logError(ex);
      }
    }
    return r;
  }

  /**
   * {@code org.eclipse.ltk.core.refactoring.resource.RenameResourceChange} of a project: the project gets the new name
   * ({@code IProject.move} with the renamed description); a project outside the workspace root keeps its folder, like a
   * project at a custom location. Its undo renames the project back.
   */
  static final class ProjectRenameChange extends Change {
    private final IProject project;
    private final String oldName;
    private final String newName;

    ProjectRenameChange(IProject project, String oldName, String newName) {
      this.project = project;
      this.oldName = oldName;
      this.newName = newName;
    }

    @Override
    public String getName() {
      return "Rename '" + oldName + "' to '" + newName + "'";
    }

    @Override
    public void initializeValidationData(IProgressMonitor pm) {
    }

    @Override
    public RefactoringStatus isValid(IProgressMonitor pm) {
      return new RefactoringStatus();
    }

    @Override
    public Change perform(IProgressMonitor pm) throws CoreException {
      rename(project, newName, pm);
      return new ProjectRenameChange(project, newName, oldName);
    }

    private static void rename(IProject project, String name, IProgressMonitor pm) throws CoreException {
      IProjectDescription d = project.getDescription();
      d.setName(name);
      project.move(d, IResource.FORCE | IResource.SHALLOW, pm);
    }

    @Override
    public Object getModifiedElement() {
      return project;
    }
  }
}
