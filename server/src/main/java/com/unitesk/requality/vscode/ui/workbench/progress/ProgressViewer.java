package com.unitesk.requality.vscode.ui.workbench.progress;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.ui.internal.progress.DetailedProgressViewer}: the jobs of the {@link ProgressManager} as one
 * {@link ProgressInfoItem} row each, or the text "No operations to display at this time." when there are none. The rows
 * of the jobs that stay are updated, not created again. A click on a row selects its job; Cancel stops the selected job
 * when it is active.
 */
public final class ProgressViewer extends Composite {
  private final Label emptyLabel;
  private final Map<Job, ProgressInfoItem> items = new LinkedHashMap<>();
  /** The job of the selected row, or null. */
  private Job selected;

  public ProgressViewer(Composite parent, int style) {
    super(parent, style);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    setLayout(layout);
    emptyLabel = new Label(this, SWT.NONE);
    emptyLabel.setText(ProgressInfoItem.NO_OPERATIONS);
    emptyLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
  }

  /** Shows the jobs of the progress manager now (on the display thread). */
  public void refresh() {
    if (isDisposed())
      return;
    List<Job> jobs = ProgressManager.getInstance().getJobs();
    for (Iterator<Map.Entry<Job, ProgressInfoItem>> it = items.entrySet().iterator(); it.hasNext();) {
      Map.Entry<Job, ProgressInfoItem> e = it.next();
      if (!jobs.contains(e.getKey())) {
        e.getValue().dispose();
        it.remove();
      }
    }
    if (selected != null && !jobs.contains(selected))
      selected = null;
    for (Job job : jobs) {
      ProgressInfoItem item = items.get(job);
      if (item == null) {
        item = new ProgressInfoItem(this, job, () -> select(job));
        items.put(job, item);
      }
      item.update();
    }
    emptyLabel.setVisible(jobs.isEmpty());
    layout(true, true);
  }

  /** The job of the selected row, or null. */
  public Job getSelectedJob() {
    return selected;
  }

  /** Selects the row of the job (the selection of the view). */
  public void select(Job job) {
    selected = job;
  }

  /** The selected job is active (waiting, sleeping or running): it can be canceled. */
  public boolean canCancelSelection() {
    return selected != null && selected.getState() != Job.NONE
        && ProgressManager.getInstance().finishedAt(selected) == null;
  }

  /** Cancel: asks the selected active job to stop (the Cancel Operation button of its row). */
  public void cancelSelection() {
    if (canCancelSelection())
      ProgressManager.getInstance().cancel(selected);
  }
}
