package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/** {@code org.eclipse.ui.internal.dialogs.WorkingSetFilter}: shows the working sets of the supported types. */
public class WorkingSetFilter extends ViewerFilter {
  Set<String> workingSetIds;

  public WorkingSetFilter(Set<String> workingSetIds) {
    this.workingSetIds = workingSetIds;
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    if (element instanceof IWorkingSet workingSet) {
      String id = workingSet.getId();
      if (workingSetIds != null && id != null) {
        return workingSetIds.contains(id);
      }
    }
    return true;
  }
}
