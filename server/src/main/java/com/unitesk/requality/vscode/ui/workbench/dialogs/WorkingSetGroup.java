package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.dialogs.DialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/**
 * {@code org.eclipse.ui.dialogs.WorkingSetGroup}: the "Working sets" group of a wizard page that adds the new projects to the
 * working sets (see {@link WorkingSetConfigurationBlock}).
 */
public final class WorkingSetGroup {

  /** {@code WorkingSetGroup_EnableWorkingSet_button}. */
  static final String ENABLE_BUTTON_LABEL = "Add projec&t to working sets"; //$NON-NLS-1$

  private final WorkingSetConfigurationBlock workingSetBlock;

  /**
   * Creates the group.
   *
   * @param composite the parent composite
   * @param currentSelection the initial working set selection to pass to the {@link WorkingSetConfigurationBlock}
   * @param workingSetTypes the types of working sets that can be selected by the {@link WorkingSetConfigurationBlock}
   */
  public WorkingSetGroup(Composite composite, IStructuredSelection currentSelection, String[] workingSetTypes) {
    this(composite, currentSelection, workingSetTypes, new DialogSettings("WorkingSetGroup")); //$NON-NLS-1$
  }

  /**
   * Creates the group with the dialog settings that keep the selection history (the workbench dialog settings in Eclipse).
   *
   * @param composite the parent composite
   * @param currentSelection the initial working set selection to pass to the {@link WorkingSetConfigurationBlock}
   * @param workingSetTypes the types of working sets that can be selected by the {@link WorkingSetConfigurationBlock}
   * @param settings the dialog settings of the selection history
   */
  public WorkingSetGroup(Composite composite, IStructuredSelection currentSelection, String[] workingSetTypes,
      IDialogSettings settings) {
    Group workingSetGroup = new Group(composite, SWT.NONE);
    workingSetGroup.setText("Working sets"); //$NON-NLS-1$
    workingSetGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    workingSetGroup.setLayout(new GridLayout(1, false));
    workingSetBlock = new WorkingSetConfigurationBlock(settings, workingSetTypes);
    workingSetBlock.setWorkingSets(workingSetBlock.findApplicableWorkingSets(currentSelection));
    workingSetBlock.createContent(workingSetGroup);
  }

  /**
   * The working sets selected by the contained {@link WorkingSetConfigurationBlock}.
   *
   * @return the selected working sets
   */
  public IWorkingSet[] getSelectedWorkingSets() {
    return workingSetBlock.getSelectedWorkingSets();
  }
}
