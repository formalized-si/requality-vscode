package com.unitesk.requality.vscode.ui.workbench.progress;

import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.MouseAdapter;
import com.unitesk.requality.vscode.ui.swt.events.MouseEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Link;
import com.unitesk.requality.vscode.ui.swt.widgets.ProgressBar;

/**
 * {@code org.eclipse.ui.internal.progress.ProgressInfoItem}: one job of the Progress view. The first line is the name
 * of the job with its state ({@link #nameText}), the bar is its progress, the second line is its task and sub task, or
 * the message of its error as a link when it finished with an error. The button stops a job that is active (disabled
 * once it was asked to stop) and removes a finished job from the view. The image of the job is its
 * {@link IProgressConstants#ICON_PROPERTY}, the error image when it finished with an error, or the default job image.
 */
public final class ProgressInfoItem extends Composite {
  /** {@code ProgressView_NoOperations}: the text when the view has no operation. */
  public static final String NO_OPERATIONS = "No operations to display at this time.";
  /** {@code NewProgressView_CancelJobToolTip}. */
  static final String CANCEL_TOOLTIP = "Cancel Operation";
  /** {@code NewProgressView_ClearJobToolTip}. */
  static final String REMOVE_TOOLTIP = "Remove From View";
  /** {@code NewProgressView_errorDialogTitle}: the title of the error dialog a link opens. */
  static final String ERROR_TITLE = "Error Occurred";
  /** The image of a job ({@code progress/progress_task} of org.eclipse.e4.ui.progress). */
  static final String JOB_ICON = "icons/shared/progress_task.png";
  /** The image of a job that finished with an error ({@code errorstate} of org.eclipse.e4.ui.progress). */
  static final String ERROR_ICON = "icons/shared/error.png";
  static final String STOP_ICON = "icons/shared/progress_stop.png";
  static final String REMOVE_ICON = "icons/shared/progress_rem.png";

  private static final Map<String, Image> IMAGES = new HashMap<>();

  private final Job job;
  private final Label jobImage;
  private final Label nameLabel;
  private final ProgressBar progressBar;
  private final Label taskLabel;
  private final Link errorLink;
  private final Button actionButton;
  private Image ownImage;

  /**
   * @param onSelect told when the row is clicked (the viewer selects the job)
   */
  public ProgressInfoItem(Composite parent, Job job, Runnable onSelect) {
    super(parent, SWT.NONE);
    this.job = job;
    GridLayout layout = new GridLayout(3, false);
    layout.marginWidth = 0;
    layout.marginHeight = 2;
    layout.horizontalSpacing = 6;
    layout.verticalSpacing = 2;
    setLayout(layout);
    setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    MouseAdapter select = new MouseAdapter() {
      @Override
      public void mouseDown(MouseEvent e) {
        if (onSelect != null)
          onSelect.run();
      }
    };
    addMouseListener(select);

    jobImage = new Label(this, SWT.NONE);
    jobImage.addMouseListener(select);
    nameLabel = new Label(this, SWT.NONE);
    nameLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    nameLabel.addMouseListener(select);
    actionButton = new Button(this, SWT.PUSH | SWT.FLAT);
    actionButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        press();
      }
    });

    new Label(this, SWT.NONE);
    progressBar = new ProgressBar(this, SWT.NONE);
    GridData barData = new GridData(GridData.FILL_HORIZONTAL);
    barData.horizontalSpan = 2;
    progressBar.setLayoutData(barData);

    new Label(this, SWT.NONE);
    taskLabel = new Label(this, SWT.LEFT);
    GridData taskData = new GridData(GridData.FILL_HORIZONTAL);
    taskData.horizontalSpan = 2;
    taskLabel.setLayoutData(taskData);
    taskLabel.addMouseListener(select);
    errorLink = new Link(this, SWT.NONE);
    GridData errorData = new GridData(GridData.FILL_HORIZONTAL);
    errorData.horizontalSpan = 2;
    errorLink.setLayoutData(errorData);
    errorLink.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        IStatus result = job.getResult();
        // VSCODE: the link opens the error of the job in an error dialog (JobErrorDialog of Eclipse)
        ErrorDialog.openError(null, ERROR_TITLE, result == null ? null : result.getMessage(), result);
      }
    });
    update();
  }

  /** The job the row shows. */
  public Job getJob() {
    return job;
  }

  /** Shows the job as it is now: its name and state, its image, its progress, its task or its error. */
  public void update() {
    Long finishedAt = ProgressManager.getInstance().finishedAt(job);
    jobImage.setImage(jobImage(finishedAt));
    nameLabel.setText(nameText(job, finishedAt));
    progressBar.setMinimum(0);
    progressBar.setMaximum(100);
    progressBar.setSelection(Math.max(0, job.getPercentDone()));
    String error = errorMessage(job);
    if (finishedAt != null) {
      taskLabel.setText("");
      errorLink.setText(error == null ? "" : "<a href=\"error\">" + escape(error) + "</a>");
      actionButton.setImage(image(REMOVE_ICON));
      actionButton.setToolTipText(REMOVE_TOOLTIP);
      actionButton.setEnabled(true);
    } else {
      taskLabel.setText(taskText(job));
      errorLink.setText("");
      actionButton.setImage(image(STOP_ICON));
      actionButton.setToolTipText(CANCEL_TOOLTIP);
      // VSCODE: a job that was asked to stop is not stopped at once: its stop button is disabled until it ends
      actionButton.setEnabled(!job.isCancelRequested());
    }
  }

  private void press() {
    ProgressManager manager = ProgressManager.getInstance();
    if (manager.finishedAt(job) != null)
      manager.removeFinished(job);
    else
      manager.cancel(job);
  }

  /**
   * {@code ProgressInfoItem.getJobNameAndStatus}: the name of the job with its state. A system job is "System: name"; a
   * job that was asked to stop is "(Cancel Requested)" while it runs and "(Canceled)" otherwise; a blocked job is
   * "(Blocked: reason)"; a running job is its name; a sleeping job "(Sleeping)"; a finished job "(Finished at
   * time)"; any other job "(Waiting)".
   */
  public static String nameText(Job job, Long finishedAt) {
    String name = job.getName();
    if (job.isSystem())
      name = MessageFormat.format("System: {0}", name);
    if (job.isCancelRequested()) {
      if (job.getState() == Job.RUNNING)
        return MessageFormat.format("{0} (Cancel Requested)", name);
      return MessageFormat.format("{0} (Canceled)", name);
    }
    IStatus blocked = job.getBlockedStatus();
    if (blocked != null)
      return MessageFormat.format("{0} (Blocked: {1})", name, blocked.getMessage());
    if (job.getState() == Job.RUNNING)
      return name;
    if (job.getState() == Job.SLEEPING)
      return MessageFormat.format("{0} (Sleeping)", name);
    if (finishedAt != null) {
      String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(finishedAt));
      return MessageFormat.format("{0} (Finished at {1})", name, time);
    }
    return MessageFormat.format("{0} (Waiting)", name);
  }

  /** The task and the sub task of a running job, with its percentage when it is known (empty otherwise). */
  public static String taskText(Job job) {
    if (job.getState() != Job.RUNNING)
      return "";
    StringBuilder text = new StringBuilder();
    String task = job.getTaskName();
    String subTask = job.getSubTaskName();
    if (task != null && !task.isEmpty())
      text.append(task);
    if (subTask != null && !subTask.isEmpty()) {
      if (text.length() > 0)
        text.append(": ");
      text.append(subTask);
    }
    if (text.length() == 0)
      return "";
    int percent = job.getPercentDone();
    if (percent >= 0)
      text.insert(0, "(" + percent + "%) ");
    return text.toString();
  }

  /** The message of the error the job finished with, or null when it did not finish with a message. */
  static String errorMessage(Job job) {
    IStatus result = job.getResult();
    if (result == null || result.getSeverity() != IStatus.ERROR)
      return null;
    String message = result.getMessage();
    return message == null || message.isEmpty() ? null : message;
  }

  /**
   * The image of the row: the icon property of the job, the error image of a job that finished with an error, or the
   * default job image.
   */
  private Image jobImage(Long finishedAt) {
    Object icon = job.getProperty(IProgressConstants.ICON_PROPERTY);
    ImageDescriptor descriptor = null;
    if (icon instanceof ImageDescriptor)
      descriptor = (ImageDescriptor) icon;
    else if (icon instanceof URL)
      descriptor = ImageDescriptor.createFromURL((URL) icon);
    if (descriptor != null) {
      if (ownImage == null)
        ownImage = descriptor.createImage();
      return ownImage;
    }
    IStatus result = job.getResult();
    if (result != null && result.getSeverity() == IStatus.ERROR)
      return image(ERROR_ICON);
    return image(JOB_ICON);
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  /** The image of a file of the shared images, created once. */
  static synchronized Image image(String path) {
    return IMAGES.computeIfAbsent(path, p -> ImageDescriptor.fromResource(p).createImage());
  }
}
