package com.unitesk.requality.vscode.ui.ide.undo;

import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.operations.AbstractOperation;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IResourceRuleFactory;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.ide.undo.AbstractWorkspaceOperation}: an undoable operation on the workspace, run as one
 * workspace modification. An operation that became invalid (its resources changed since) cannot be undone or redone.
 */
public abstract class AbstractWorkspaceOperation extends AbstractOperation implements IUndoableOperation {
  private static final String ELLIPSIS = "...";
  protected static final int EXECUTE = 1;
  protected static final int UNDO = 2;
  protected static final int REDO = 3;

  protected IResource[] resources;
  private boolean isValid = true;
  protected boolean quietCompute = false;

  AbstractWorkspaceOperation(String name) {
    // Many operation names are based on the triggering action's name: the mnemonics and the ellipsis are stripped.
    super(removeMnemonics(name));
    String label = getLabel();
    if (label.endsWith(ELLIPSIS)) {
      setLabel(label.substring(0, label.length() - ELLIPSIS.length()));
    }
  }

  /** Action.removeMnemonics: the '&' of a menu label, "&&" is a literal '&'. */
  private static String removeMnemonics(String name) {
    if (name == null) {
      return "";
    }
    return name.replace("&&", "\u0000").replace("&", "").replace("\u0000", "&");
  }

  protected void setTargetResources(IResource[] resources) {
    this.resources = resources;
  }

  protected IWorkspace getWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }

  protected IResourceRuleFactory getWorkspaceRuleFactory() {
    return getWorkspace().getRuleFactory();
  }

  protected void markInvalid() {
    isValid = false;
  }

  @Override
  public boolean canExecute() {
    return isValid();
  }

  @Override
  public boolean canUndo() {
    return isValid();
  }

  @Override
  public boolean canRedo() {
    return isValid();
  }

  @Override
  public IStatus execute(IProgressMonitor monitor, final IAdaptable uiInfo) throws ExecutionException {
    try {
      getWorkspace().run(monitor1 -> doExecute(monitor1, uiInfo), getExecuteSchedulingRule(),
          IWorkspace.AVOID_UPDATE, monitor);
    } catch (final CoreException e) {
      throw new ExecutionException(UndoMessages.bind(UndoMessages.AbstractWorkspaceOperation_ExecuteErrorTitle,
          getLabel()), e);
    }
    isValid = true;
    return Status.OK_STATUS;
  }

  @Override
  public IStatus redo(IProgressMonitor monitor, final IAdaptable uiInfo) throws ExecutionException {
    IStatus status = computeRedoableStatus(monitor);
    if (!status.isOK()) {
      return status;
    }
    try {
      getWorkspace().run(monitor1 -> doExecute(monitor1, uiInfo), getRedoSchedulingRule(), IWorkspace.AVOID_UPDATE,
          monitor);
    } catch (final CoreException e) {
      throw new ExecutionException(UndoMessages.bind(UndoMessages.AbstractWorkspaceOperation_RedoErrorTitle,
          getLabel()), e);
    }
    isValid = true;
    return Status.OK_STATUS;
  }

  @Override
  public IStatus undo(IProgressMonitor monitor, final IAdaptable uiInfo) throws ExecutionException {
    IStatus status = computeUndoableStatus(monitor);
    if (!status.isOK()) {
      return status;
    }
    try {
      getWorkspace().run(monitor1 -> doUndo(monitor1, uiInfo), getUndoSchedulingRule(), IWorkspace.AVOID_UPDATE,
          monitor);
    } catch (final CoreException e) {
      throw new ExecutionException(UndoMessages.bind(UndoMessages.AbstractWorkspaceOperation_UndoErrorTitle,
          getLabel()), e);
    }
    isValid = true;
    return Status.OK_STATUS;
  }

  protected abstract void doUndo(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException;

  protected abstract void doExecute(IProgressMonitor monitor, IAdaptable uiInfo) throws CoreException;

  protected boolean isValid() {
    return isValid;
  }

  /** The workspace resources the operation works on (the affected objects). */
  public Object[] getAffectedObjects() {
    return resources;
  }

  /**
   * The validity of an execution (IAdvancedUndoableOperation.computeExecutionStatus). The side effects prompt of
   * Eclipse (model providers) is not shown here.
   */
  // VSCODE: no model provider side effects are prompted: the status is always OK.
  public IStatus computeExecutionStatus(IProgressMonitor monitor) {
    return Status.OK_STATUS;
  }

  public IStatus computeUndoableStatus(IProgressMonitor monitor) {
    return Status.OK_STATUS;
  }

  public IStatus computeRedoableStatus(IProgressMonitor monitor) {
    return Status.OK_STATUS;
  }

  protected IStatus getErrorStatus(String message) {
    String statusMessage = message;
    if (statusMessage == null) {
      statusMessage = UndoMessages.bind(UndoMessages.AbstractWorkspaceOperation_ErrorInvalidMessage, getLabel());
    }
    return new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES, 1, statusMessage, null);
  }

  protected IStatus getWarningStatus(String message, int code) {
    String statusMessage = message;
    if (statusMessage == null) {
      statusMessage = UndoMessages.bind(UndoMessages.AbstractWorkspaceOperation_GenericWarningMessage, getLabel());
    }
    return new Status(IStatus.WARNING, ResourcesPlugin.PI_RESOURCES, code, statusMessage, null);
  }

  protected boolean resourcesExist() {
    if (resources == null || resources.length == 0) {
      return false;
    }
    for (IResource resource : resources) {
      if (resource == null || !resource.exists()) {
        return false;
      }
    }
    return true;
  }

  protected boolean resourcesIncludesProjects() {
    if (resources == null || resources.length == 0) {
      return false;
    }
    for (IResource resource : resources) {
      if (resource.getType() == IResource.PROJECT) {
        return true;
      }
    }
    return false;
  }

  // VSCODE: the scheduling rules of the workspace are not used (one workspace modification at a time).
  protected ISchedulingRule getExecuteSchedulingRule() {
    return getWorkspace().getRoot();
  }

  protected ISchedulingRule getUndoSchedulingRule() {
    return getWorkspace().getRoot();
  }

  protected ISchedulingRule getRedoSchedulingRule() {
    return getExecuteSchedulingRule();
  }

  public void setQuietCompute(boolean quiet) {
    quietCompute = quiet;
  }

  @Override
  public String toString() {
    StringBuffer text = new StringBuffer(super.toString());
    text.append("\n");
    text.append(this.getClass().getName());
    appendDescriptiveText(text);
    return text.toString();
  }

  protected void appendDescriptiveText(StringBuffer text) {
    text.append(" resources: ");
    text.append(resources == null ? null : java.util.Arrays.asList(resources));
    text.append('\'');
  }

  protected Shell getShell(IAdaptable uiInfo) {
    return WorkspaceUndoUtil.getShell(uiInfo);
  }
}
