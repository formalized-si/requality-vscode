package com.unitesk.requality.vscode.ui.navigator.workingsets;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreePath;
import com.unitesk.requality.vscode.ui.navigator.CommonDropAdapter;
import com.unitesk.requality.vscode.ui.navigator.CommonDropAdapterAssistant;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * {@code org.eclipse.ui.internal.navigator.workingsets.ProjectInWorkingSetDropAdapterAssistant}: allows dragging projects
 * between working sets in the explorer: a project dropped on a working set is added to it, and when it was dragged from
 * another working set (the top-level element of the path) it is removed from that one.
 */
public class ProjectInWorkingSetDropAdapterAssistant extends CommonDropAdapterAssistant {
  @Override
  public IStatus validateDrop(Object target, int operation, TransferData transferType) {
    // We don't currently support COPY or LINK
    if (operation != DND.DROP_MOVE) {
      return Status.CANCEL_STATUS;
    }
    IWorkingSet targetWorkingSet = Adapters.adapt(target, IWorkingSet.class);
    if (targetWorkingSet == null) {
      return Status.CANCEL_STATUS;
    }
    if (!LocalSelectionTransfer.getTransfer().isSupportedType(transferType)) {
      return Status.CANCEL_STATUS;
    }
    // Verify that we have at least one project not already in the target
    ISelection sel = LocalSelectionTransfer.getTransfer().getSelection();
    if (sel != null && !sel.isEmpty() && sel instanceof IStructuredSelection) {
      for (Object item : ((IStructuredSelection) sel).toArray()) {
        IProject project = Adapters.adapt(item, IProject.class);
        if (project != null && !workingSetContains(targetWorkingSet, project)) {
          return Status.OK_STATUS;
        }
      }
    }
    return Status.CANCEL_STATUS;
  }

  @Override
  public IStatus handleDrop(CommonDropAdapter dropAdapter, DropTargetEvent dropTargetEvent, Object target) {
    IWorkingSetManager workingSetManager = Workbench.INSTANCE.getWorkingSetManager();
    IWorkingSet targetWorkingSet = Adapters.adapt(target, IWorkingSet.class);
    ISelection sel = LocalSelectionTransfer.getTransfer().getSelection();
    if (sel instanceof ITreeSelection) {
      for (TreePath path : ((ITreeSelection) sel).getPaths()) {
        IProject project = Adapters.adapt(path.getLastSegment(), IProject.class);
        if (project != null) {
          if (!workingSetContains(targetWorkingSet, project)) {
            workingSetManager.addToWorkingSets(project, new IWorkingSet[] { targetWorkingSet });
          }
          // Check if our top-level element is a working set so that we can perform a move
          IWorkingSet sourceWorkingSet = Adapters.adapt(path.getFirstSegment(), IWorkingSet.class);
          if (sourceWorkingSet != null) {
            removeFromWorkingSet(project, sourceWorkingSet);
          }
        }
      }
    } else if (sel instanceof IStructuredSelection) {
      for (Object item : ((IStructuredSelection) sel).toArray()) {
        IProject project = Adapters.adapt(item, IProject.class);
        if (project != null && !workingSetContains(targetWorkingSet, project)) {
          workingSetManager.addToWorkingSets(project, new IWorkingSet[] { targetWorkingSet });
        }
      }
    }
    return Status.OK_STATUS;
  }

  /** Removes the project from the working set. */
  private void removeFromWorkingSet(IProject project, IWorkingSet workingSet) {
    IAdaptable[] srcElements = workingSet.getElements();
    List<IAdaptable> newSrcElements = new ArrayList<>();
    for (IAdaptable srcElement : srcElements) {
      if (!project.equals(Adapters.adapt(srcElement, IProject.class))) {
        newSrcElements.add(srcElement);
      }
    }
    IAdaptable[] adaptedNewSrcElements = workingSet.adaptElements(newSrcElements.toArray(new IAdaptable[newSrcElements.size()]));
    workingSet.setElements(adaptedNewSrcElements);
  }

  /** Whether the working set contains the project. */
  private boolean workingSetContains(IWorkingSet workingSet, IProject project) {
    for (IAdaptable element : workingSet.getElements()) {
      if (project.equals(Adapters.adapt(element, IProject.class))) {
        return true;
      }
    }
    return false;
  }
}
