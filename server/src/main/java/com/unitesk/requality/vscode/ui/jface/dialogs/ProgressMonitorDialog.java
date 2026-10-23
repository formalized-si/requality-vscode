package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.vscode.ui.jface.operation.IRunnableContext;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.ProgressBar;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.jface.dialogs.ProgressMonitorDialog}: a modal "Progress Information" dialog with the information
 * image, the task name, a progress indicator, the sub task name and a Cancel button.
 *
 * <p>
 * {@link #run} opens the dialog, runs the operation with the monitor of the dialog and closes the dialog after the
 * operation. With {@code fork} the operation runs in a thread while the display thread keeps dispatching events (the
 * nested loop of {@code ModalContext}); the progress may be reported from that thread, the widgets are only updated on
 * the display thread, the updates are coalesced.
 * </p>
 * <p>
 * VSCODE: the wait cursor of the dialog and the shortening of the labels are not ported: the webview sizes the labels
 * itself and has no cursors.
 * </p>
 */
public class ProgressMonitorDialog extends Dialog implements IRunnableContext {
  /** {@code ProgressMonitorDialog.title}: the title of the shell. */
  public static final String TITLE = "Progress Information";
  /** {@code ProgressMonitorDialog.message}: the task name shown when the task has none. */
  private static final String DEFAULT_TASKNAME = "Operation in progress...";
  /** the height of the label of the sub task and of the progress indicator, in dialog units */
  private static final int LABEL_DLUS = 21;
  private static final int BAR_DLUS = 9;

  /** the message label of the task (the label {@link #taskLabel} is kept for backwards compatibility) */
  protected Label messageLabel;
  /** the progress indicator control */
  protected ProgressBar progressIndicator;
  /** the label control for the task (kept for backwards compatibility) */
  protected Label taskLabel;
  /** the label control for the sub task */
  protected Label subTaskLabel;
  /** the Cancel button control */
  protected Button cancel;
  /** whether the Cancel button is enabled */
  protected boolean operationCancelableState = false;
  /** the state of the Cancel button when it is created */
  protected boolean enableCancelButton;
  /** the progress monitor of the dialog */
  private final ProgressMonitor progressMonitor = new ProgressMonitor();
  /** the nesting depth of the running runnables: the dialog cannot be closed while one runs */
  private int nestingDepth;
  /** whether the dialog is opened before the operation is run */
  private boolean openOnRun = true;

  /** {@code ProgressMonitorDialog.ProgressMonitor}: the monitor of the dialog, updates its widgets. */
  private final class ProgressMonitor implements IProgressMonitor {
    private volatile String task = "";
    private String subTask = "";
    private int totalWork = IProgressMonitor.UNKNOWN;
    private double worked;
    private volatile boolean canceled;
    /** coalesces the updates queued on the display thread */
    private final AtomicBoolean refreshQueued = new AtomicBoolean();

    @Override
    public synchronized void beginTask(String name, int totalWork) {
      task = name == null ? "" : name;
      this.totalWork = totalWork;
      worked = 0;
      refresh();
    }

    @Override
    public synchronized void done() {
      totalWork = IProgressMonitor.UNKNOWN;
      worked = 0;
      refresh();
    }

    @Override
    public void setTaskName(String name) {
      task = name == null ? "" : name;
      refresh();
    }

    @Override
    public synchronized void subTask(String name) {
      subTask = name == null ? "" : name;
      refresh();
    }

    @Override
    public void worked(int work) {
      internalWorked(work);
    }

    @Override
    public synchronized void internalWorked(double work) {
      worked += work;
      refresh();
    }

    @Override
    public boolean isCanceled() {
      return canceled;
    }

    @Override
    public void setCanceled(boolean value) {
      canceled = value;
    }

    /** Updates the widgets now on the display thread, else queues one update on it. */
    private void refresh() {
      Display display = Display.getDefault();
      if (display.isUIThread()) {
        updateWidgets();
      } else if (refreshQueued.compareAndSet(false, true)) {
        display.asyncExec(() -> {
          refreshQueued.set(false);
          updateWidgets();
        });
      }
    }

    /** Shows the task, the sub task and the work done (on the display thread). */
    private void updateWidgets() {
      String t, s;
      int total;
      double w;
      synchronized (this) {
        t = task;
        s = subTask;
        total = totalWork;
        w = worked;
      }
      if (messageLabel != null && !messageLabel.isDisposed()) {
        String text = t.isEmpty() ? DEFAULT_TASKNAME : t;
        messageLabel.setToolTipText(text);
        messageLabel.setText(text);
      }
      if (subTaskLabel != null && !subTaskLabel.isDisposed())
        subTaskLabel.setText(s);
      if (progressIndicator != null && !progressIndicator.isDisposed()) {
        progressIndicator.setMinimum(0);
        if (total <= 0) {
          // an unknown amount of work: the indicator stays at the start
          progressIndicator.setMaximum(100);
          progressIndicator.setSelection(0);
        } else {
          progressIndicator.setMaximum(total);
          progressIndicator.setSelection((int) Math.min(total, Math.round(w)));
        }
      }
    }
  }

  /**
   * Creates a progress monitor dialog under the given shell. The dialog has the title "Progress Information" and no
   * close button; {@code open} is non-blocking.
   *
   * @param parent the parent shell, or {@code null} for a top-level shell
   */
  public ProgressMonitorDialog(Shell parent) {
    super(parent);
    // no close button on the shell style
    setShellStyle(SWT.BORDER | SWT.TITLE | SWT.APPLICATION_MODAL);
    setBlockOnOpen(false);
  }

  /** Enables the Cancel button (asynchronously, on the display thread). */
  private void asyncSetOperationCancelButtonEnabled(boolean b) {
    Display.getDefault().asyncExec(() -> setOperationCancelButtonEnabled(b));
  }

  /** The Cancel button has been pressed: cancels the operation; the dialog closes after the run. */
  @Override
  protected void cancelPressed() {
    // NOTE: as in JFace, the button is disabled before the monitor is canceled
    cancel.setEnabled(false);
    progressMonitor.setCanceled(true);
    super.cancelPressed();
  }

  /** Closes the dialog only if no runnable is running (the run closes it at its end). */
  @Override
  public boolean close() {
    if (getNestingDepth() <= 0)
      return super.close();
    return false;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText(TITLE);
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    // cancel button
    createCancelButton(parent);
  }

  /** Creates the Cancel button (the default button of the dialog). */
  protected void createCancelButton(Composite parent) {
    cancel = createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
    setOperationCancelButtonEnabled(enableCancelButton);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginHeight = 10;
    layout.marginWidth = 10;
    layout.verticalSpacing = 4;
    layout.horizontalSpacing = 4;
    area.setLayout(layout);
    area.setLayoutData(new GridData(GridData.FILL_BOTH));

    // IconAndMessageDialog.createMessageArea: the information image, then the message (the task name)
    Image image = Display.getDefault().getSystemImage(SWT.ICON_INFORMATION);
    if (image != null) {
      Label imageLabel = new Label(area, SWT.NONE);
      imageLabel.setImage(image);
      imageLabel.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
    }
    messageLabel = new Label(area, SWT.LEFT | SWT.WRAP);
    messageLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    // Only set for backwards compatibility
    taskLabel = messageLabel;

    // progress indicator
    progressIndicator = new ProgressBar(area, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.heightHint = convertVerticalDLUsToPixels(BAR_DLUS);
    gd.horizontalSpan = 2;
    progressIndicator.setLayoutData(gd);

    // label showing the current sub task
    subTaskLabel = new Label(area, SWT.LEFT | SWT.WRAP);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.heightHint = convertVerticalDLUsToPixels(LABEL_DLUS);
    gd.horizontalSpan = 2;
    subTaskLabel.setLayoutData(gd);

    progressMonitor.updateWidgets();
    return area;
  }

  /**
   * Returns the progress monitor to use for operations run in this progress dialog.
   *
   * @return the progress monitor
   */
  public IProgressMonitor getProgressMonitor() {
    return progressMonitor;
  }

  /**
   * {@code ProgressMonitorDialog.run}: runs the runnable with the progress monitor of the dialog and blocks until it has
   * run, regardless of {@code fork}. The dialog is opened before the runnable is run, and closed after it completes.
   * With {@code fork} the runnable runs in a thread while the display thread dispatches events. Without it the runnable
   * runs on the display thread and is responsible for dispatching events itself.
   */
  @Override
  public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
      throws InvocationTargetException, InterruptedException {
    Display display = Display.getDefault();
    if (!display.isUIThread()) {
      // VSCODE: the dialog is shown and updated by the display thread: the run is marshalled to it (as the wizard
      // containers do)
      Object[] failure = new Object[1];
      display.syncExec(() -> {
        try {
          run(fork, cancelable, runnable);
        } catch (InvocationTargetException | InterruptedException | RuntimeException | Error e) {
          failure[0] = e;
        }
      });
      if (failure[0] instanceof InvocationTargetException)
        throw (InvocationTargetException) failure[0];
      if (failure[0] instanceof InterruptedException)
        throw (InterruptedException) failure[0];
      if (failure[0] instanceof RuntimeException)
        throw (RuntimeException) failure[0];
      if (failure[0] instanceof Error)
        throw (Error) failure[0];
      return;
    }
    setCancelable(cancelable);
    try {
      aboutToRun();
      if (fork)
        runForked(runnable);
      else
        runnable.run(progressMonitor);
    } finally {
      progressMonitor.done();
      finishedRun();
    }
  }

  /**
   * Runs the runnable in a thread while the display thread dispatches events until it is done (the nested event loop of
   * {@code ModalContext}). The updates the thread posted to the display are processed before the end of the run.
   */
  private void runForked(IRunnableWithProgress runnable) throws InvocationTargetException, InterruptedException {
    Display display = Display.getDefault();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean finished = new AtomicBoolean();
    Thread thread = new Thread(() -> {
      try {
        runnable.run(progressMonitor);
      } catch (Throwable e) {
        failure.set(e);
      } finally {
        progressMonitor.done();
        display.asyncExec(() -> finished.set(true));
      }
    }, "requality-progress-dialog");
    thread.setDaemon(true);
    thread.start();
    display.runUntil(finished::get);
    Throwable e = failure.get();
    if (e instanceof InvocationTargetException)
      throw (InvocationTargetException) e;
    if (e instanceof InterruptedException)
      throw (InterruptedException) e;
    if (e != null)
      throw new InvocationTargetException(e);
  }

  /** Returns whether the dialog should be opened before the operation is run (default {@code true}). */
  public boolean getOpenOnRun() {
    return openOnRun;
  }

  /**
   * Sets whether the dialog should be opened before the operation is run. With {@code false} the dialog is only created,
   * not shown.
   *
   * @param openOnRun {@code true} to open the dialog before run, {@code false} to only create it
   */
  public void setOpenOnRun(boolean openOnRun) {
    this.openOnRun = openOnRun;
  }

  /** @return the nesting depth of running operations */
  protected int getNestingDepth() {
    return nestingDepth;
  }

  protected void incrementNestingDepth() {
    nestingDepth++;
  }

  protected void decrementNestingDepth() {
    nestingDepth--;
  }

  protected void aboutToRun() {
    if (getOpenOnRun())
      open();
    else
      create();
    incrementNestingDepth();
  }

  protected void finishedRun() {
    decrementNestingDepth();
    close();
  }

  /**
   * Sets whether the Cancel button is enabled. Before the button exists the state is used when it is created,
   * afterwards it is set asynchronously.
   *
   * @param cancelable the state to set the button to
   */
  public void setCancelable(boolean cancelable) {
    enableCancelButton = cancelable;
    if (cancel != null)
      asyncSetOperationCancelButtonEnabled(cancelable);
  }

  protected void setOperationCancelButtonEnabled(boolean b) {
    operationCancelableState = b;
    if (cancel != null && !cancel.isDisposed())
      cancel.setEnabled(b);
  }

  /** Opens the dialog unless it is not to be opened on run and no run is in progress. */
  @Override
  public int open() {
    if (!getOpenOnRun() && getNestingDepth() == 0)
      return OK;
    int result = super.open();
    progressMonitor.updateWidgets();
    return result;
  }

  /** The dialog has no close button: the user cannot close it while the operation runs. */
  @Override
  protected boolean canHandleShellCloseEvent() {
    return false;
  }
}
