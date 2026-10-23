package com.unitesk.requality.vscode.ui.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.core.internal.jobs.JobManager}: sends the job change events of every job to the listeners added to
 * it (the Progress view and the progress dialogs of the workbench), and the progress of the jobs to the progress
 * listeners. A listener that fails is logged and does not stop the others.
 */
public final class JobManager {
  /** Receives the progress of the jobs: the work done by a job changed (its task, sub task or amount of work). */
  public interface ProgressListener {
    void progressChanged(Job job);
  }

  private static final JobManager INSTANCE = new JobManager();
  private final List<IJobChangeListener> listeners = new CopyOnWriteArrayList<>();
  private final List<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();

  private JobManager() {
  }

  /** {@code Job.getJobManager()}: the job manager of the workbench. */
  public static JobManager getInstance() {
    return INSTANCE;
  }

  /** {@code JobManager.addJobChangeListener}: the listener gets the events of every job. */
  public void addJobChangeListener(IJobChangeListener listener) {
    if (listener != null)
      listeners.add(listener);
  }

  public void removeJobChangeListener(IJobChangeListener listener) {
    listeners.remove(listener);
  }

  public void addProgressListener(ProgressListener listener) {
    if (listener != null)
      progressListeners.add(listener);
  }

  public void removeProgressListener(ProgressListener listener) {
    progressListeners.remove(listener);
  }

  /** Sends an event to the listeners of the manager (the job's own listeners are sent by the job). */
  void fireJobChange(BiConsumer<IJobChangeListener, IJobChangeEvent> call, IJobChangeEvent event) {
    for (IJobChangeListener l : listeners)
      dispatch(call, l, event);
  }

  /** Sends one event to a listener of a job or of the manager. */
  static void dispatch(BiConsumer<IJobChangeListener, IJobChangeEvent> call, IJobChangeListener l,
      IJobChangeEvent event) {
    try {
      call.accept(l, event);
    } catch (RuntimeException e) {
      RLogger.logError(e);
    }
  }

  /** Sends the progress of a job to the progress listeners. */
  void fireProgress(Job job) {
    for (ProgressListener l : progressListeners) {
      try {
        l.progressChanged(job);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
  }
}
