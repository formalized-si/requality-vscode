package com.unitesk.requality.vscode.ui.workbench.progress;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;

/**
 * {@code org.eclipse.ui.internal.progress.JobsViewPreferenceDialog}: "Progress Preferences" of the Progress view,
 * opened by its view menu. Its one setting, "Show sleeping and system operations", is the {@code SHOW_SYSTEM_JOBS}
 * preference of the workbench; Restore Defaults sets it back to off in the dialog, OK stores it and refreshes the view.
 */
public class JobsViewPreferenceDialog extends Dialog {
  /** {@code JobsViewPreferenceDialog_Title}. */
  static final String TITLE = "Progress Preferences";
  /** {@code JobsViewPreferenceDialog_Note}: the check box of the sleeping and system operations. */
  static final String SHOW_SYSTEM_JOBS_TEXT = "&Show sleeping and system operations";
  /** {@code JFaceResources "defaults"}: the Restore Defaults button, as the view settings dialogs have it. */
  static final String DEFAULTS_TEXT = "Restore Defaults";
  /** The id of the Restore Defaults button (after the ids of {@link IDialogConstants}). */
  private static final int DEFAULTS_ID = 20;

  private Button showSystemJobs;

  public JobsViewPreferenceDialog(Shell parentShell) {
    super(parentShell);
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText(TITLE);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite top = (Composite) super.createDialogArea(parent);
    Composite editArea = new Composite(top, SWT.NONE);
    editArea.setLayout(new GridLayout());
    editArea.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));
    showSystemJobs = new Button(editArea, SWT.CHECK);
    showSystemJobs.setText(SHOW_SYSTEM_JOBS_TEXT);
    showSystemJobs.setSelection(ProgressManager.showSystemJobs());
    return top;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, DEFAULTS_ID, DEFAULTS_TEXT, false);
    super.createButtonsForButtonBar(parent);
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == DEFAULTS_ID)
      performDefaults();
    else
      super.buttonPressed(buttonId);
  }

  @Override
  protected void okPressed() {
    ProgressManager.getInstance().setShowSystemJobs(showSystemJobs.getSelection());
    super.okPressed();
  }

  /** The default of the preference: the system operations are not shown. */
  protected void performDefaults() {
    showSystemJobs.setSelection(
        WorkbenchPreferences.getStore().getDefaultBoolean(WorkbenchPreferences.SHOW_SYSTEM_JOBS));
  }
}
