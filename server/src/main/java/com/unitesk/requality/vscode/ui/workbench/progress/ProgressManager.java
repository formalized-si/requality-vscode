package com.unitesk.requality.vscode.ui.workbench.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.IJobChangeEvent;
import com.unitesk.requality.vscode.ui.runtime.IJobChangeListener;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.JobChangeAdapter;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;

/**
 * {@code org.eclipse.ui.internal.progress.ProgressManager}: the jobs the Progress view and the progress dialogs show.
 * The active jobs are the jobs that are scheduled and not done yet, in the order they were scheduled; the view shows
 * them without the system jobs (unless "Show sleeping and system operations" is on) and without the sleeping jobs
 * ({@code ProgressManager.isCurrentDisplaying}), except a job that went to sleep while it was shown with "Show sleeping
 * and system operations" on, which stays as a sleeping job ({@code ProgressManager.sleepJobInfo}: refreshed, not
 * removed). The finished jobs are kept when they have the KEEP property (or
 * KEEPONE) or finished with an error. A user job that is still running after {@link #SHORT_OPERATION_TIME} while
 * "Always run in background" is off opens its {@link ProgressMonitorFocusJobDialog}.
 */
public final class ProgressManager {
  /** {@code ProgressManagerUtil.SHORT_OPERATION_TIME}: a user job still running after this time opens its dialog. */
  static final int SHORT_OPERATION_TIME = 250;

  private static ProgressManager instance;

  /** The active jobs in the order they were scheduled (guarded by this). */
  private final List<Job> active = new ArrayList<>();
  /** The finished jobs kept in the views, with the time they finished (guarded by this). */
  private final Map<Job, Long> finished = new LinkedHashMap<>();
  /** Told when the jobs or their progress change; called on the thread of the job. */
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  private final IJobChangeListener jobListener = new JobChangeAdapter() {
    @Override
    public void scheduled(IJobChangeEvent event) {
      Job job = event.getJob();
      synchronized (ProgressManager.this) {
        finished.remove(job);
        if (!active.contains(job))
          active.add(job);
      }
      changed();
      if (job.isUser() && !runInBackground())
        Display.getDefault().timerExec(SHORT_OPERATION_TIME, () -> showInDialog(job));
    }

    @Override
    public void aboutToRun(IJobChangeEvent event) {
      changed();
    }

    @Override
    public void awake(IJobChangeEvent event) {
      synchronized (ProgressManager.this) {
        sleptWhileShown.remove(event.getJob());
      }
      changed();
    }

    @Override
    public void running(IJobChangeEvent event) {
      // ProgressManager.checkTasks: a KEEPONE job replaces the kept jobs of its family when it starts
      Job job = event.getJob();
      synchronized (ProgressManager.this) {
        if (isKeepOne(job))
          removeKeptOfFamily(job);
      }
      changed();
    }

    @Override
    public void sleeping(IJobChangeEvent event) {
      // ProgressManager.sleepJobInfo: a job the view shows is refreshed (as sleeping) when the sleeping and system
      // operations are shown, else removed
      Job job = event.getJob();
      synchronized (ProgressManager.this) {
        if (active.contains(job) && showSystemJobs())
          sleptWhileShown.add(job);
        else
          sleptWhileShown.remove(job);
      }
      changed();
    }

    @Override
    public void done(IJobChangeEvent event) {
      Job job = event.getJob();
      synchronized (ProgressManager.this) {
        active.remove(job);
        sleptWhileShown.remove(job);
        if (isKept(job)) {
          // FinishedJobs.removeJob: the kept jobs of the family of a KEEPONE job are removed before it is kept
          removeKeptOfFamily(job);
          finished.put(job, System.currentTimeMillis());
        }
      }
      changed();
    }
  };

  private ProgressManager() {
  }

  /** The progress manager of the workbench; it listens to the jobs from its first use. */
  public static synchronized ProgressManager getInstance() {
    if (instance == null) {
      instance = new ProgressManager();
      Job.getJobManager().addJobChangeListener(instance.jobListener);
      Job.getJobManager().addProgressListener(instance::progressChanged);
    }
    return instance;
  }

  /** The jobs that went to sleep while the view showed them with the sleeping and system operations. */
  private final java.util.Set<Job> sleptWhileShown = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

  /** The jobs the views show: the active jobs that are displayed, and then the kept finished jobs. */
  public synchronized List<Job> getJobs() {
    boolean showSystem = showSystemJobs();
    List<Job> jobs = new ArrayList<>();
    for (Job job : active)
      if (!isHidden(job, showSystem) || showSystem && sleptWhileShown.contains(job) && job.getState() == Job.SLEEPING)
        jobs.add(job);
    jobs.addAll(finished.keySet());
    return jobs;
  }

  /**
   * {@code ProgressManager.isCurrentDisplaying}: a system job is not displayed unless the system jobs are shown, and a
   * sleeping job is not displayed.
   */
  static boolean isHidden(Job job, boolean showSystem) {
    return (job.isSystem() && !showSystem) || job.getState() == Job.SLEEPING;
  }

  /** Whether the Progress view shows the sleeping and system operations ({@code SHOW_SYSTEM_JOBS}). */
  public static boolean showSystemJobs() {
    return WorkbenchPreferences.getStore().getBoolean(WorkbenchPreferences.SHOW_SYSTEM_JOBS);
  }

  /** {@code ProgressManager.setShowSystemJobs}: stores the preference and refreshes the views. */
  public void setShowSystemJobs(boolean show) {
    WorkbenchPreferences.getStore().setValue(WorkbenchPreferences.SHOW_SYSTEM_JOBS, show);
    // the views are filled again (ProgressManager.getJobInfos: without the sleeping jobs)
    synchronized (this) {
      sleptWhileShown.clear();
    }
    changed();
  }

  /** The time the job finished, or {@code null} while it is active (or not kept). */
  public synchronized Long finishedAt(Job job) {
    return finished.get(job);
  }

  /** Whether a kept finished job is in the views (the Remove All Finished Operations action is enabled). */
  public synchronized boolean hasFinished() {
    return !finished.isEmpty();
  }

  /** Removes a finished job from the views. */
  public void removeFinished(Job job) {
    synchronized (this) {
      finished.remove(job);
    }
    changed();
  }

  /** {@code ClearAllAction}: removes every finished job from the views. */
  public void removeAllFinished() {
    synchronized (this) {
      finished.clear();
    }
    changed();
  }

  /** Asks a job to stop, as the Cancel Operation button does. */
  public void cancel(Job job) {
    job.cancel();
    changed();
  }

  /** {@code FinishedJobs.removeDuplicates}: removes the kept jobs that belong to the family of a KEEPONE job. */
  private void removeKeptOfFamily(Job job) {
    if (!isKeepOne(job))
      return;
    finished.keySet().removeIf(other -> other != job && other.belongsTo(job));
  }

  /** The KEEPONE property of a job. */
  static boolean isKeepOne(Job job) {
    return Boolean.TRUE.equals(job.getProperty(IProgressConstants.KEEPONE_PROPERTY));
  }

  /** Adds a listener told when the jobs or their progress change (on the thread of the job). */
  public void addListener(Runnable listener) {
    if (listener != null)
      listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** {@code FinishedJobs.keep}: a job is kept with the KEEP or KEEPONE property, or when it finished with an error. */
  static boolean isKept(Job job) {
    if (Boolean.TRUE.equals(job.getProperty(IProgressConstants.KEEP_PROPERTY))
        || Boolean.TRUE.equals(job.getProperty(IProgressConstants.KEEPONE_PROPERTY)))
      return true;
    IStatus status = job.getResult();
    return status != null && status.getSeverity() == IStatus.ERROR;
  }

  /** {@code ProgressManager.shouldRunInBackground}: the "Always run in background" preference. */
  static boolean runInBackground() {
    return WorkbenchPreferences.getStore().getBoolean(WorkbenchPreferences.RUN_IN_BACKGROUND);
  }

  /** The progress dialog of a user job that is still running when the time of a short operation is over. */
  private void showInDialog(Job job) {
    if (job.getState() == Job.NONE || runInBackground())
      return;
    new ProgressMonitorFocusJobDialog(null).show(job);
  }

  private void progressChanged(Job job) {
    boolean shown;
    synchronized (this) {
      shown = active.contains(job) || finished.containsKey(job);
    }
    if (shown)
      changed();
  }

  private void changed() {
    for (Runnable listener : listeners) {
      try {
        listener.run();
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
  }
}
