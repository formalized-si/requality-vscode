package com.unitesk.requality.vscode.ui.workbench.progress;

import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.runtime.IJobChangeEvent;
import com.unitesk.requality.vscode.ui.runtime.IJobChangeListener;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.JobChangeAdapter;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.ProgressBar;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;

/**
 * {@code org.eclipse.ui.internal.progress.ProgressMonitorFocusJobDialog}: the progress of a user job in a dialog while
 * "Always run in background" is off. It shows the job's name, its task and its progress, with the checkbox "Always run
 * in background" and the buttons Run in Background (the job goes on in the background), Cancel (the job is asked to
 * stop) and Details (the rows of the Progress view). The dialog closes when the job is done. As in Eclipse the dialog
 * is modeless, has no close box (Escape cancels the job like the Cancel button does) and Run in Background is its
 * default button.
 */
public class ProgressMonitorFocusJobDialog extends Dialog {
  /** {@code WorkbenchPreference_RunInBackgroundButton} and its tool tip. */
  static final String RUN_IN_BACKGROUND_CHECK = "Always r&un in background";
  static final String RUN_IN_BACKGROUND_TOOLTIP = "Run long operations in the background where possible";
  /** {@code ProgressMonitorFocusJobDialog_RunInBackgroundButton}. */
  static final String RUN_IN_BACKGROUND_BUTTON = "Run in &Background";
  /** {@code SWT.TRAVERSE_NONE}: the traversal is handled (Escape is consumed). */
  private static final int TRAVERSE_NONE = 0;
  /** The height of the bar and of the task label, in dialog units. */
  private static final int BAR_DLUS = 9;
  private static final int LABEL_DLUS = 21;

  private Job job;
  /** The state of the "Always run in background" check box (the preference when the dialog opened). */
  private boolean alwaysRunInBackground;
  private Label messageLabel;
  private ProgressBar progressIndicator;
  private Label taskLabel;
  private Button alwaysRunButton;
  private Button detailsButton;
  private ProgressViewer detailsViewer;
  private final AtomicBoolean refreshQueued = new AtomicBoolean();

  /** The job ended: the dialog closes (on the display thread). */
  private final IJobChangeListener doneListener = new JobChangeAdapter() {
    @Override
    public void done(IJobChangeEvent event) {
      event.getJob().removeJobChangeListener(this);
      Display.getDefault().asyncExec(() -> {
        if (getShell() != null && !getShell().isDisposed())
          close();
      });
    }
  };

  /** The jobs changed (on their threads): the dialog is updated once on the display thread. */
  private final Runnable changeListener = () -> {
    if (refreshQueued.compareAndSet(false, true)) {
      Display.getDefault().asyncExec(() -> {
        refreshQueued.set(false);
        updateWidgets();
      });
    }
  };

  public ProgressMonitorFocusJobDialog(Shell parentShell) {
    super(parentShell);
    setShellStyle(SWT.BORDER | SWT.TITLE | SWT.RESIZE | SWT.MAX | SWT.MODELESS);
    setBlockOnOpen(false);
  }

  /** Opens the dialog for a job while it runs (on the display thread); it closes when the job is done. */
  public void show(Job jobToWatch) {
    job = jobToWatch;
    job.setProperty(IProgressConstants.PROPERTY_IN_DIALOG, Boolean.TRUE);
    alwaysRunInBackground = WorkbenchPreferences.getStore().getBoolean(WorkbenchPreferences.RUN_IN_BACKGROUND);
    open();
    ProgressManager.getInstance().addListener(changeListener);
    job.addJobChangeListener(doneListener);
    // the job may have ended before the listener was added
    if (job.getState() == Job.NONE)
      close();
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText(job.getName());
    // ProgressMonitorFocusJobDialog.configureShell: Escape cancels the job
    shell.addTraverseListener(e -> {
      if (e.detail == SWT.TRAVERSE_ESCAPE) {
        cancelPressed();
        e.detail = TRAVERSE_NONE;
        e.doit = true;
      }
    });
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginHeight = 10;
    layout.marginWidth = 10;
    layout.verticalSpacing = 4;
    layout.horizontalSpacing = 4;
    area.setLayout(layout);
    area.setLayoutData(new GridData(GridData.FILL_BOTH));

    messageLabel = new Label(area, SWT.LEFT | SWT.WRAP);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    messageLabel.setLayoutData(gd);

    progressIndicator = new ProgressBar(area, SWT.NONE);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.heightHint = convertVerticalDLUsToPixels(BAR_DLUS);
    gd.horizontalSpan = 2;
    progressIndicator.setLayoutData(gd);

    taskLabel = new Label(area, SWT.LEFT | SWT.WRAP);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.heightHint = convertVerticalDLUsToPixels(LABEL_DLUS);
    gd.horizontalSpan = 2;
    taskLabel.setLayoutData(gd);

    alwaysRunButton = new Button(area, SWT.CHECK);
    alwaysRunButton.setText(RUN_IN_BACKGROUND_CHECK);
    alwaysRunButton.setToolTipText(RUN_IN_BACKGROUND_TOOLTIP);
    alwaysRunButton.setSelection(alwaysRunInBackground);
    alwaysRunButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        alwaysRunInBackground = alwaysRunButton.getSelection();
      }
    });
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    alwaysRunButton.setLayoutData(gd);

    updateWidgets();
    return area;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.CLOSE_ID, RUN_IN_BACKGROUND_BUTTON, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    detailsButton = createButton(parent, IDialogConstants.DETAILS_ID, IDialogConstants.SHOW_DETAILS_LABEL, false);
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == IDialogConstants.CLOSE_ID) {
      // Run in Background: the job goes on in the background, as the notification of VS Code shows it
      job.setProperty(IProgressConstants.PROPERTY_IN_DIALOG, Boolean.FALSE);
      close();
    } else if (buttonId == IDialogConstants.DETAILS_ID) {
      toggleDetails();
    } else {
      super.buttonPressed(buttonId);
    }
  }

  /** Cancel: the job is asked to stop and the dialog closes (as the Eclipse dialog does). */
  @Override
  protected void cancelPressed() {
    job.cancel();
    super.cancelPressed();
  }

  /** Shows or hides the rows of the Progress view under the progress. */
  private void toggleDetails() {
    Composite area = (Composite) dialogArea;
    if (detailsViewer == null) {
      detailsViewer = new ProgressViewer(area, SWT.NONE);
      GridData gd = new GridData(GridData.FILL_BOTH);
      gd.horizontalSpan = 2;
      detailsViewer.setLayoutData(gd);
      detailsViewer.refresh();
      detailsButton.setText(IDialogConstants.HIDE_DETAILS_LABEL);
    } else {
      detailsViewer.dispose();
      detailsViewer = null;
      detailsButton.setText(IDialogConstants.SHOW_DETAILS_LABEL);
    }
    area.getParent().layout(true, true);
  }

  /** Shows the name, the task, the progress of the job, and the rows of the details. */
  private void updateWidgets() {
    if (job == null || messageLabel == null || messageLabel.isDisposed())
      return;
    messageLabel.setText(ProgressInfoItem.nameText(job, ProgressManager.getInstance().finishedAt(job)));
    progressIndicator.setMinimum(0);
    progressIndicator.setMaximum(100);
    progressIndicator.setSelection(Math.max(0, job.getPercentDone()));
    taskLabel.setText(ProgressInfoItem.taskText(job));
    if (detailsViewer != null && !detailsViewer.isDisposed())
      detailsViewer.refresh();
  }

  /**
   * Closes the dialog. The preference "Always run in background" is set to the state of its check box unless the dialog
   * was canceled (ProgressMonitorFocusJobDialog.close).
   */
  @Override
  public boolean close() {
    ProgressManager.getInstance().removeListener(changeListener);
    if (job != null)
      job.removeJobChangeListener(doneListener);
    if (getReturnCode() != CANCEL)
      WorkbenchPreferences.getStore().setValue(WorkbenchPreferences.RUN_IN_BACKGROUND, alwaysRunInBackground);
    return super.close();
  }

  /**
   * VSCODE: the shell has no close box (no SWT.CLOSE in its style, as in Eclipse); a close request of the window is
   * ignored, so only the buttons, Escape and the end of the job close the dialog.
   */
  @Override
  protected boolean canHandleShellCloseEvent() {
    return false;
  }
}
