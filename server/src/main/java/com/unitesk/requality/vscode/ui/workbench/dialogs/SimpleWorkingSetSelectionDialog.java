package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.List;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.dialogs.SimpleWorkingSetSelectionDialog}: base implementation for a simple working set
 * dialog that does not contain references to non-editable or non-visible working sets. The working sets are checked in a
 * list.
 */
public class SimpleWorkingSetSelectionDialog extends AbstractWorkingSetDialog {

  /** Keeps the working sets that can be selected: visible, editable, not aggregates, of the supported types. */
  private class Filter extends ViewerFilter {
    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
      return isCompatible((IWorkingSet) element);
    }

    private boolean isCompatible(IWorkingSet set) {
      if (set.isAggregateWorkingSet()) {
        return false;
      }
      if (!set.isVisible()) {
        return false;
      }
      if (!set.isEditable()) {
        return false;
      }
      Set<String> workingSetTypeIds = getSupportedWorkingSetIds();
      if (workingSetTypeIds == null) {
        return true;
      }
      for (String workingSetTypeId : workingSetTypeIds) {
        if (workingSetTypeId.equals(set.getId())) {
          return true;
        }
      }
      return false;
    }
  }

  private CheckboxTableViewer viewer;
  private final IWorkingSet[] initialSelection;

  /**
   * Create a new instance of this class.
   *
   * @param shell the shell to parent this dialog on
   * @param workingSetTypeIds the types of working set IDs that will be shown in this dialog
   * @param selectedWorkingSets the currently selected working sets (if any)
   * @param canEdit whether or not this dialog will display edit controls
   */
  public SimpleWorkingSetSelectionDialog(Shell shell, String[] workingSetTypeIds, IWorkingSet[] selectedWorkingSets,
      boolean canEdit) {
    super(shell, workingSetTypeIds, canEdit);
    this.initialSelection = selectedWorkingSets;
    setTitle("Select Working Sets"); //$NON-NLS-1$
    setMessage("&Select working sets:"); //$NON-NLS-1$
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    GridLayout layout = new GridLayout(2, false);
    layout.marginHeight = layout.marginWidth = 0;
    layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
    Composite viewerComposite = new Composite(composite, SWT.NONE);
    viewerComposite.setLayout(layout);
    viewerComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

    viewer = CheckboxTableViewer.newCheckList(viewerComposite, SWT.BORDER);
    viewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    viewer.setLabelProvider(new WorkingSetLabelProvider());
    viewer.setContentProvider(ArrayContentProvider.getInstance());
    viewer.addFilter(new WorkingSetFilter(null));
    IWorkingSet[] workingSets = PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets();
    viewer.setInput(workingSets);
    viewer.setFilters(new Filter());
    viewer.addSelectionChangedListener(event -> handleSelectionChanged());
    viewer.setCheckedElements(initialSelection);
    GridData viewerData = new GridData(GridData.FILL_BOTH);
    viewerData.widthHint = convertWidthInCharsToPixels(50);
    viewer.getControl().setLayoutData(viewerData);

    addModifyButtons(viewerComposite);
    addSelectionButtons(composite);
    availableWorkingSetsChanged();
    applyDialogFont(composite);
    return composite;
  }

  @Override
  protected void okPressed() {
    Object[] checked = viewer.getCheckedElements();
    IWorkingSet[] workingSets = new IWorkingSet[checked.length];
    System.arraycopy(checked, 0, workingSets, 0, checked.length);
    setSelection(workingSets);
    super.okPressed();
  }

  @Override
  protected List<IWorkingSet> getSelectedWorkingSets() {
    return viewer.getStructuredSelection().toList();
  }

  @Override
  protected void availableWorkingSetsChanged() {
    viewer.setInput(PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets());
    super.availableWorkingSetsChanged();
  }

  @Override
  protected void workingSetAdded(IWorkingSet addedSet) {
    viewer.setChecked(addedSet, true);
    updateButtonAvailability();
  }

  /** Called when the selection has changed. */
  void handleSelectionChanged() {
    updateButtonAvailability();
  }

  @Override
  protected void selectAllSets() {
    viewer.setCheckedElements(PlatformUI.getWorkbench().getWorkingSetManager().getWorkingSets());
    updateButtonAvailability();
  }

  @Override
  protected void deselectAllSets() {
    viewer.setCheckedElements(new Object[0]);
    updateButtonAvailability();
  }
}
