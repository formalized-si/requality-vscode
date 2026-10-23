package com.unitesk.requality.vscode.ui.ide.actions;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourceAttributes;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.actions.ReadOnlyStateChecker}: takes a set of resources, some of which may be read only, and
 * queries the user as to whether or not they wish to continue the operation on them (Yes, Yes to All, No, Cancel).
 */
public class ReadOnlyStateChecker {
  /** {@code IDEWorkbenchMessages.ReadOnlyCheck_problems}. */
  private static final String READ_ONLY_EXCEPTION_MESSAGE = "Read-Only Checking Problems";

  private final Shell shell;
  private final String titleMessage;
  private final String mainMessage;
  private boolean yesToAllSelected = false;
  private boolean cancelSelected = false;
  private boolean ignoreLinkedResources = false;

  /**
   * Creates a new checker that parents the dialog off of the parent using the supplied title and message.
   *
   * @param parent the shell used for dialogs
   * @param title the title for dialogs
   * @param message the message for a dialog, prefaced with the name of the resource (a {@link MessageFormat} pattern)
   */
  public ReadOnlyStateChecker(Shell parent, String title, String message) {
    this.shell = parent;
    this.titleMessage = title;
    this.mainMessage = message;
  }

  /**
   * Checks an individual resource to see if it passed the read only query. If it is a file just add it, otherwise it
   * is a container and the children need to be checked too. Returns true if all items are selected and false if any
   * are skipped.
   */
  private boolean checkAcceptedResource(IResource resourceToCheck, List<IResource> selectedChildren)
      throws CoreException {
    if (resourceToCheck.getType() == IResource.FILE) {
      selectedChildren.add(resourceToCheck);
    } else if (getIgnoreLinkedResources() && resourceToCheck.isLinked()) {
      selectedChildren.add(resourceToCheck);
    } else {
      IContainer container = (IContainer) resourceToCheck;
      // if the project is closed, there's no point in checking its children
      if (container.isAccessible()) {
        // now check below
        int childCheck = checkReadOnlyResources(container.members(), selectedChildren);
        // add in the resource only if nothing was left out
        if (childCheck == IDialogConstants.YES_TO_ALL_ID) {
          selectedChildren.add(resourceToCheck);
        } else {
          // something was left out
          return false;
        }
      } else {
        selectedChildren.add(resourceToCheck);
      }
    }
    return true;
  }

  /**
   * Checks the supplied resources to see if they are read only. If so, prompts the user to see if they can be
   * processed. Returns those that were accepted.
   *
   * @param itemsToCheck resources to check
   * @return the resulting selected resources
   */
  public IResource[] checkReadOnlyResources(IResource[] itemsToCheck) {
    List<IResource> selections = new ArrayList<>();
    int result = IDialogConstants.CANCEL_ID;
    try {
      result = checkReadOnlyResources(itemsToCheck, selections);
    } catch (final CoreException exception) {
      shell.getDisplay().syncExec(() -> ErrorDialog.openError(shell, READ_ONLY_EXCEPTION_MESSAGE, null,
          exception.getStatus()));
    }

    if (result == IDialogConstants.CANCEL_ID) {
      return new IResource[0];
    }

    // all were selected so return the original items
    if (result == IDialogConstants.YES_TO_ALL_ID) {
      return itemsToCheck;
    }

    return selections.toArray(new IResource[selections.size()]);
  }

  /**
   * Checks the children of the container to see if they are read only.
   *
   * @return one of YES_TO_ALL_ID (all elements were selected), NO_ID (No was hit at some point) or CANCEL_ID (cancel
   *         was hit)
   * @param itemsToCheck the resources to check
   * @param allSelected the list of currently selected resources to add to
   */
  private int checkReadOnlyResources(IResource[] itemsToCheck, List<IResource> allSelected) throws CoreException {
    // shortcut: the user has already selected yes to all
    if (yesToAllSelected) {
      return IDialogConstants.YES_TO_ALL_ID;
    }

    boolean noneSkipped = true;
    List<IResource> selectedChildren = new ArrayList<>();

    for (IResource resourceToCheck : itemsToCheck) {
      ResourceAttributes checkAttributes = resourceToCheck.getResourceAttributes();
      if (!yesToAllSelected && shouldCheck(resourceToCheck) && checkAttributes != null
          && checkAttributes.isReadOnly()) {
        int action = queryYesToAllNoCancel(resourceToCheck);
        if (action == IDialogConstants.YES_ID) {
          boolean childResult = checkAcceptedResource(resourceToCheck, selectedChildren);
          if (!childResult) {
            noneSkipped = false;
          }
        }
        if (action == IDialogConstants.NO_ID) {
          noneSkipped = false;
        }
        if (action == IDialogConstants.CANCEL_ID) {
          cancelSelected = true;
          return IDialogConstants.CANCEL_ID;
        }
        if (action == IDialogConstants.YES_TO_ALL_ID) {
          yesToAllSelected = true;
          selectedChildren.add(resourceToCheck);
        }
      } else {
        boolean childResult = checkAcceptedResource(resourceToCheck, selectedChildren);
        if (cancelSelected) {
          return IDialogConstants.CANCEL_ID;
        }
        if (!childResult) {
          noneSkipped = false;
        }
      }
    }

    if (noneSkipped) {
      return IDialogConstants.YES_TO_ALL_ID;
    }
    allSelected.addAll(selectedChildren);
    return IDialogConstants.NO_ID;
  }

  /**
   * Returns whether the given resource should be checked for read-only state.
   *
   * @param resourceToCheck the resource to check
   * @return true to check it, false to skip it
   */
  private boolean shouldCheck(IResource resourceToCheck) {
    if (ignoreLinkedResources && resourceToCheck.isLinked()) {
      return false;
    }
    return true;
  }

  /**
   * Opens a message dialog with Yes, Yes To All, No and Cancel buttons, and returns the code that indicates the
   * selection: YES_ID, YES_TO_ALL_ID, NO_ID or CANCEL_ID.
   *
   * @param resource the resource being queried
   */
  private int queryYesToAllNoCancel(IResource resource) {
    final MessageDialog dialog = new MessageDialog(this.shell, this.titleMessage, null,
        MessageFormat.format(this.mainMessage, resource.getName()), MessageDialog.QUESTION, 0,
        IDialogConstants.YES_LABEL, IDialogConstants.YES_TO_ALL_LABEL, IDialogConstants.NO_LABEL,
        IDialogConstants.CANCEL_LABEL);
    dialog.open();
    int result = dialog.getReturnCode();
    if (result == 0) {
      return IDialogConstants.YES_ID;
    }
    if (result == 1) {
      return IDialogConstants.YES_TO_ALL_ID;
    }
    if (result == 2) {
      return IDialogConstants.NO_ID;
    }
    return IDialogConstants.CANCEL_ID;
  }

  /**
   * Returns whether linked resources are ignored.
   *
   * @return true to ignore linked resources, false to consider them
   */
  public boolean getIgnoreLinkedResources() {
    return ignoreLinkedResources;
  }

  /**
   * Sets whether linked resources are ignored. The default is false.
   *
   * @param ignore true to ignore linked resources, false to consider them
   */
  public void setIgnoreLinkedResources(boolean ignore) {
    ignoreLinkedResources = ignore;
  }
}
