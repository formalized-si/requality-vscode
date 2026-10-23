package com.unitesk.requality.vscode.ui.workbench.actions;

import com.unitesk.requality.vscode.ui.ide.dialogs.DialogTaskProperties;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.IShellProvider;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.ui.actions.AddTaskAction}: opens the properties of a new task ("Add Task") on the selected
 * resource. Enabled for a single resource that is not a closed project.
 */
public class AddTaskAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.AddTaskAction";

  private final IShellProvider shellProvider;

  public AddTaskAction(IShellProvider provider) {
    super("Add &Task", provider::getShell);
    shellProvider = provider;
    setId(ID);
    setToolTipText("Add Task");
    setActionDefinitionId(ActionFactory.ADD_TASK.getCommandId());
  }

  /** The single selected resource, unless it is a closed project; null otherwise. */
  private IResource element(IStructuredSelection selection) {
    if (selection.size() != 1)
      return null;
    Object o = selection.getFirstElement();
    IResource resource = o instanceof IResource ? (IResource) o : null;
    if (resource instanceof IProject && !((IProject) resource).isOpen())
      return null;
    return resource;
  }

  @Override
  public void run() {
    IResource resource = element(getStructuredSelection());
    if (resource != null) {
      DialogTaskProperties dialog = new DialogTaskProperties(shellProvider.getShell());
      dialog.setResource(resource);
      dialog.open();
    }
  }

  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    return super.updateSelection(selection) && element(selection) != null;
  }
}
