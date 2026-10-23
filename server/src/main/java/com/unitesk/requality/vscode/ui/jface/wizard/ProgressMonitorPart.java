package com.unitesk.requality.vscode.ui.jface.wizard;

import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Layout;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.ProgressBar;

/**
 * {@code org.eclipse.jface.wizard.ProgressMonitorPart}: a standard implementation of an {@link IProgressMonitor}. It
 * consists of a label displaying the task and subtask name, a progress indicator, and the stop button that cancels the
 * current operation.
 *
 * <p>
 * VSCODE: the progress may be reported from a worker thread (a forked operation of the wizard container): the widgets
 * are only updated on the display thread, the updates are coalesced in its queue. The stop button is a flat push button
 * with the stop image, the compat layer has no tool bar.
 * </p>
 */
public class ProgressMonitorPart extends Composite implements IProgressMonitor {
  /** the label */
  protected Label fLabel;
  /** the current task name */
  protected volatile String fTaskName;
  /** the current sub task name */
  protected volatile String fSubTaskName = "";
  /** the progress indicator */
  protected ProgressBar fProgressIndicator;
  /** the cancel component */
  protected Control fCancelComponent;
  /** true if canceled */
  protected volatile boolean fIsCanceled;
  /** current blocked status */
  protected volatile IStatus blockedStatus;
  /** the total work of the task (UNKNOWN if none) */
  private volatile int fTotalWork = IProgressMonitor.UNKNOWN;
  /** the work done of the task */
  private volatile double fWorked;
  /** the cancel listener attached to the cancel component */
  protected Listener fCancelListener = e -> {
    setCanceled(true);
    if (fCancelComponent != null) {
      fCancelComponent.setEnabled(false);
    }
  };
  /** the stop button, enabled while an operation can be canceled */
  private Button fStopButton;
  /** {@code true} if this monitor part shows the stop button */
  private boolean fHasStopButton;
  /** coalesces the label updates queued on the display thread */
  private final AtomicBoolean labelUpdateQueued = new AtomicBoolean();
  /** coalesces the progress updates queued on the display thread */
  private final AtomicBoolean progressUpdateQueued = new AtomicBoolean();

  /**
   * Creates a {@code ProgressMonitorPart} that does not provide a stop button.
   *
   * @param parent the SWT parent of the part
   * @param layout the layout of the part, or {@code null} for its default layout
   */
  public ProgressMonitorPart(Composite parent, Layout layout) {
    this(parent, layout, false);
  }

  /**
   * Creates a {@code ProgressMonitorPart}.
   *
   * @param parent the SWT parent of the part
   * @param layout the layout of the part, or {@code null} for its default layout
   * @param createStopButton {@code true} if the part includes a stop button that cancels the running task
   */
  public ProgressMonitorPart(Composite parent, Layout layout, boolean createStopButton) {
    super(parent, SWT.NONE);
    fHasStopButton = createStopButton;
    initialize(layout);
  }

  /**
   * Attaches the progress monitor part to the given cancel component. This parameter is ignored if a stop button was
   * requested upon construction: the stop button is then enabled and serves as the cancel component.
   *
   * @param cancelComponent the control whose selection will trigger a cancel
   */
  public void attachToCancelComponent(Control cancelComponent) {
    if (fHasStopButton) {
      setCancelEnabled(true);
    } else {
      fCancelComponent = cancelComponent;
      fCancelComponent.addListener(SWT.Selection, fCancelListener);
    }
  }

  /**
   * Detaches the progress monitor part from the given cancel component. This parameter is ignored if a stop button was
   * requested upon construction: the stop button is then disabled.
   *
   * @param cancelComponent the control that was previously used as a cancel component
   */
  public void removeFromCancelComponent(Control cancelComponent) {
    if (fHasStopButton) {
      setCancelEnabled(false);
    } else {
      fCancelComponent.removeListener(SWT.Selection, fCancelListener);
      fCancelComponent = null;
    }
  }

  @Override
  public void beginTask(String name, int totalWork) {
    fTaskName = name;
    fSubTaskName = "";
    fTotalWork = totalWork;
    fWorked = 0;
    queueLabelUpdate();
    queueProgressUpdate();
    runOnDisplay(() -> {
      if (fStopButton != null && !fStopButton.isDisposed()) {
        fStopButton.setVisible(true);
      }
    });
  }

  @Override
  public void done() {
    fSubTaskName = "";
    fWorked = 0;
    runOnDisplay(() -> {
      if (!fLabel.isDisposed()) {
        fLabel.setText("");
      }
      if (!fProgressIndicator.isDisposed()) {
        fProgressIndicator.setSelection(0);
      }
      if (fStopButton != null && !fStopButton.isDisposed()) {
        fStopButton.setVisible(false);
      }
    });
  }

  /** @return the stop button of the part, or {@code null} if it has none */
  public Button getStopButton() {
    return fStopButton;
  }

  /** Creates the label, the progress indicator and the stop button of the part. */
  protected void initialize(Layout layout) {
    if (layout == null) {
      GridLayout l = new GridLayout();
      l.marginWidth = 0;
      l.marginHeight = 0;
      layout = l;
    }
    int numColumns = fHasStopButton ? 2 : 1;
    if (layout instanceof GridLayout) {
      ((GridLayout) layout).numColumns = numColumns;
    }
    setLayout(layout);

    fLabel = new Label(this, SWT.LEFT);
    fLabel.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false, numColumns, 1));

    fProgressIndicator = new ProgressBar(this, SWT.NONE);
    fProgressIndicator.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));

    if (fHasStopButton) {
      fStopButton = new Button(this, SWT.FLAT);
      fStopButton.setLayoutData(new GridData(GridData.CENTER, GridData.CENTER, false, false));
      // VSCODE: the stop tool item of Eclipse; JFace's stop.png, shipped as icons/shared/stop.png.
      Image stopImage = ImageDescriptor.fromResource("icons/shared/stop.png").createImage();
      fStopButton.setText("");
      fStopButton.setImage(stopImage);
      fStopButton.addDisposeListener(e -> stopImage.dispose());
      fStopButton.setToolTipText("Cancel Operation");
      // It would have been nice to use the fCancelListener, but that listener operates on the fCancelComponent which
      // must be a control.
      fStopButton.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          setCanceled(true);
          if (fStopButton != null) {
            fStopButton.setEnabled(false);
          }
        }
      });
      fStopButton.setEnabled(false);
      fStopButton.setVisible(false);
    }
  }

  @Override
  public void internalWorked(double work) {
    fWorked += work;
    queueProgressUpdate();
  }

  @Override
  public boolean isCanceled() {
    return fIsCanceled;
  }

  @Override
  public void setCanceled(boolean b) {
    fIsCanceled = b;
  }

  @Override
  public void setTaskName(String name) {
    fTaskName = name;
    queueLabelUpdate();
  }

  @Override
  public void subTask(String name) {
    fSubTaskName = name;
    queueLabelUpdate();
  }

  /** Shows the status as the label of the part until {@link #clearBlocked()} (Eclipse's blocked status). */
  public void setBlocked(IStatus reason) {
    blockedStatus = reason;
    queueLabelUpdate();
  }

  /** Clears the blocked status of the part. */
  public void clearBlocked() {
    blockedStatus = null;
    queueLabelUpdate();
  }

  @Override
  public void worked(int work) {
    internalWorked(work);
  }

  private void setCancelEnabled(boolean enabled) {
    runOnDisplay(() -> {
      if (fStopButton != null && !fStopButton.isDisposed()) {
        fStopButton.setEnabled(enabled);
      }
    });
  }

  /** Runs the update on the display thread: the widgets are only touched there. */
  private void runOnDisplay(Runnable update) {
    Display d = getDisplay();
    if (d.isUIThread()) {
      update.run();
    } else {
      d.asyncExec(update);
    }
  }

  /** Enqueues a label update; the updates queued meanwhile are coalesced into one. */
  private void queueLabelUpdate() {
    if (labelUpdateQueued.compareAndSet(false, true)) {
      runOnDisplay(() -> {
        labelUpdateQueued.set(false);
        updateLabel();
      });
    }
  }

  /** Enqueues a progress indicator update; the updates queued meanwhile are coalesced into one. */
  private void queueProgressUpdate() {
    if (progressUpdateQueued.compareAndSet(false, true)) {
      runOnDisplay(() -> {
        progressUpdateQueued.set(false);
        updateProgress();
      });
    }
  }

  /** Shows the task and subtask names, or the blocked status (on the display thread). */
  private void updateLabel() {
    if (fLabel.isDisposed()) {
      return;
    }
    IStatus blocked = blockedStatus;
    fLabel.setText(blocked == null ? taskLabel() : blocked.getMessage());
  }

  /** The label of the task: "task: subtask" like the JFace Set_SubTask message. */
  private String taskLabel() {
    String task = fTaskName;
    String subTask = fSubTaskName;
    boolean hasTask = task != null && task.length() > 0;
    boolean hasSubtask = subTask != null && subTask.length() > 0;
    if (hasTask) {
      return hasSubtask ? task + ": " + subTask : task;
    }
    return hasSubtask ? subTask : "";
  }

  /** Shows the work done of the task (on the display thread). */
  private void updateProgress() {
    if (fProgressIndicator.isDisposed()) {
      return;
    }
    int total = fTotalWork;
    if (total <= 0) {
      // an unknown amount of work: the indicator stays at the start
      fProgressIndicator.setMinimum(0);
      fProgressIndicator.setMaximum(100);
      fProgressIndicator.setSelection(0);
      return;
    }
    fProgressIndicator.setMinimum(0);
    fProgressIndicator.setMaximum(total);
    fProgressIndicator.setSelection((int) Math.min(total, Math.round(fWorked)));
  }
}
