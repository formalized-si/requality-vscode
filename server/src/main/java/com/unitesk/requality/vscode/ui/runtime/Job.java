package com.unitesk.requality.vscode.ui.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ClientProgressMonitor;
import com.unitesk.requality.vscode.core.ServerContext;

/**
 * {@code org.eclipse.core.runtime.jobs.Job}. User jobs show a progress notification in VS Code; system jobs run
 * silently; others show progress in the status bar (like the Eclipse progress view). The life cycle of the job is sent
 * to its own listeners and to the listeners of the {@link JobManager}; the task and the progress of the monitor of the
 * job are kept for the Progress view.
 */
public abstract class Job {
  public static final int NONE = 0;
  public static final int SLEEPING = 0x01;
  public static final int WAITING = 0x02;
  public static final int RUNNING = 0x04;
  public static final int INTERACTIVE = 10;
  public static final int SHORT = 20;
  public static final int LONG = 30;
  public static final int BUILD = 40;
  public static final int DECORATE = 50;
  public static final IStatus ASYNC_FINISH = new Status(IStatus.OK, "job", 1, "", null);

  private static final AtomicInteger IDS = new AtomicInteger();
  private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "requality-ui-job-" + IDS.incrementAndGet());
    t.setDaemon(true);
    return t;
  });
  private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "requality-ui-job-timer");
    t.setDaemon(true);
    return t;
  });

  private String name;
  private boolean user;
  private boolean system;
  private int priority = LONG;
  private volatile int state = NONE;
  private volatile IProgressMonitor monitor;
  private volatile CountDownLatch done = new CountDownLatch(0);
  private Object rule;
  private final Map<Object, Object> properties = new ConcurrentHashMap<>();
  private final List<IJobChangeListener> listeners = new CopyOnWriteArrayList<>();
  /** The result of the last run, set when the job is done. */
  private volatile IStatus result;
  /** The task and the sub task reported by the monitor of the current run ({@code null} before the first). */
  private volatile String taskName;
  private volatile String subTaskName;
  /** The work of the task of the current run, {@link IProgressMonitor#UNKNOWN} when it is not known. */
  private volatile int totalWork = IProgressMonitor.UNKNOWN;
  private volatile double worked;
  /** cancel() of a job that is running: the job is asked to stop (it may still complete). */
  private volatile boolean cancelRequested;
  /** The reason the job reported it is blocked (null when it is not blocked). */
  private volatile IStatus blockedStatus;

  public Job(String name) {
    this.name = name;
  }

  protected abstract IStatus run(IProgressMonitor monitor);

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setUser(boolean user) {
    this.user = user;
  }

  public boolean isUser() {
    return user;
  }

  public void setSystem(boolean system) {
    this.system = system;
  }

  public boolean isSystem() {
    return system;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public int getPriority() {
    return priority;
  }

  public void setRule(Object rule) {
    this.rule = rule;
  }

  public Object getRule() {
    return rule;
  }

  /** {@code Job.setProperty}: a property of the job (a {@code null} value removes it). */
  public void setProperty(Object key, Object value) {
    if (value == null)
      properties.remove(key);
    else
      properties.put(key, value);
  }

  /** {@code Job.getProperty}: the value of a property of the job, or {@code null}. */
  public Object getProperty(Object key) {
    return properties.get(key);
  }

  public int getState() {
    return state;
  }

  /** {@code Job.getResult}: the result of the last run, {@code null} while the job has not finished. */
  public IStatus getResult() {
    return result;
  }

  /** {@code Job.getJobManager}: the job manager the job's listeners and the progress listeners belong to. */
  public static JobManager getJobManager() {
    return JobManager.getInstance();
  }

  /** {@code Job.addJobChangeListener}: the listener gets the events of this job only. */
  public void addJobChangeListener(IJobChangeListener listener) {
    if (listener != null)
      listeners.add(listener);
  }

  /** {@code Job.removeJobChangeListener}. */
  public void removeJobChangeListener(IJobChangeListener listener) {
    listeners.remove(listener);
  }

  /** The task of the current run, as the monitor of the job reports it (or {@code null}). */
  public String getTaskName() {
    return taskName;
  }

  /** The sub task of the current run, as the monitor of the job reports it (or {@code null}). */
  public String getSubTaskName() {
    return subTaskName;
  }

  /** The percentage of the work of the current run that is done, or -1 when the amount of work is not known. */
  public int getPercentDone() {
    int total = totalWork;
    if (total <= 0)
      return -1;
    return (int) Math.min(100, Math.round(worked * 100 / total));
  }

  /** Whether cancel() asked the running job to stop (the job may still be running). */
  public boolean isCancelRequested() {
    return cancelRequested;
  }

  /** The reason the job is blocked, as its monitor reported it (null when it is not blocked). */
  public IStatus getBlockedStatus() {
    return blockedStatus;
  }

  /** {@code Job.belongsTo}: whether the job is of the family (no family by default). */
  public boolean belongsTo(Object family) {
    return false;
  }

  public boolean shouldSchedule() {
    return true;
  }

  public boolean shouldRun() {
    return true;
  }

  public final void schedule() {
    schedule(0);
  }

  /** A schedule requested while the job is about to run or running (Eclipse reschedules it when it ends). */
  private long rescheduleDelay = -1;
  private boolean aboutToRun;
  /** cancel() of a waiting or sleeping job: it does not run. */
  private boolean canceledBeforeRun;
  /** cancel() while the job starts running, before its monitor exists. */
  private boolean cancelRunning;

  /** The thread that scheduled the job held the lock of the background work: the job runs while it holds it. */
  volatile Background.Holder scheduledFrom;

  public final synchronized void schedule(long delay) {
    if (!shouldSchedule())
      return;
    scheduledFrom = Background.current();
    // JobManager.schedule: a job that is running (or about to run) is rescheduled when it is done.
    if (state == RUNNING || aboutToRun) {
      rescheduleDelay = Math.max(0, delay);
      return;
    }
    if (state == WAITING || state == SLEEPING)
      return;
    done = new CountDownLatch(1);
    start(delay);
  }

  /** Puts the job in the waiting (or sleeping) state and hands it to the pool; called with the job lock held. */
  private void start(long delay) {
    canceledBeforeRun = false;
    cancelRunning = false;
    cancelRequested = false;
    blockedStatus = null;
    result = null;
    state = delay > 0 ? SLEEPING : WAITING;
    fireChange((l, e) -> l.scheduled(e), delay, null);
    submit(delay);
  }

  /** The last hand-over of the job to the pool: an earlier one (before a sleep, a wake-up) is dropped. */
  private long submission;

  /**
   * Hands the job to the pool after the delay (a sleeping job wakes up first, {@code awake}); called with the job lock
   * held.
   */
  private void submit(long delay) {
    long sub = ++submission;
    Runnable go = () -> {
      boolean awoken = false;
      synchronized (this) {
        if (sub != submission)
          return;
        if (state == SLEEPING) {
          state = WAITING;
          awoken = true;
        } else if (state != WAITING)
          return;
      }
      if (awoken)
        fireChange((l, e) -> l.awake(e), 0, null);
      POOL.execute(() -> {
        if (claim(sub))
          execute();
      });
    };
    if (delay > 0)
      TIMER.schedule(go, delay, TimeUnit.MILLISECONDS);
    else
      go.run();
  }

  /** The pool takes the job to run it, unless it was put to sleep (or handed over again) since. */
  private synchronized boolean claim(long sub) {
    if (sub != submission || state != WAITING)
      return false;
    aboutToRun = true;
    return true;
  }

  /** Runs the job body; UIJob overrides to run on the display thread. */
  protected void execute() {
    CountDownLatch latch = done;
    boolean removed;
    synchronized (this) {
      removed = canceledBeforeRun;
      if (removed) {
        // A job canceled while waiting is removed without running.
        canceledBeforeRun = false;
        state = NONE;
        result = Status.CANCEL_STATUS;
      } else {
        aboutToRun = true;
      }
    }
    if (removed) {
      fireChange((l, e) -> l.done(e), 0, Status.CANCEL_STATUS);
      latch.countDown();
      return;
    }
    fireChange((l, e) -> l.aboutToRun(e), 0, null);
    boolean again = false;
    IStatus status = Status.OK_STATUS;
    try {
      if (!shouldRun())
        return;
      IProgressMonitor m = createMonitor();
      ProgressReporter reporter = new ProgressReporter(m);
      synchronized (this) {
        state = RUNNING;
        monitor = reporter;
        if (cancelRunning)
          reporter.setCanceled(true);
      }
      fireChange((l, e) -> l.running(e), 0, null);
      try {
        // no call of the server runs at the same time (when it sets the lock of the background work)
        IStatus[] st = new IStatus[1];
        Background.run(scheduledFrom, () -> st[0] = run(reporter));
        status = st[0];
      } finally {
        reporter.done();
      }
    } catch (Throwable t) {
      RLogger.logError(t instanceof Exception ? (Exception) t : new RuntimeException(t));
      status = new Status(IStatus.ERROR, "job", 0, String.valueOf(t.getMessage()), t);
    } finally {
      IStatus finalStatus = status;
      synchronized (this) {
        aboutToRun = false;
        monitor = null;
        blockedStatus = null;
        long delay = rescheduleDelay;
        rescheduleDelay = -1;
        if (delay >= 0) {
          // Rescheduled: the job is not done yet, join() keeps waiting for the next run.
          again = true;
          start(delay);
        } else {
          state = NONE;
          result = finalStatus == null ? Status.OK_STATUS : finalStatus;
        }
      }
      if (!again) {
        fireChange((l, e) -> l.done(e), 0, result);
        latch.countDown();
      }
    }
  }

  /** Sends a job change event to the listeners of the job and then of the job manager. */
  private void fireChange(BiConsumer<IJobChangeListener, IJobChangeEvent> call, long delay, IStatus status) {
    IJobChangeEvent event = new JobChangeEvent(this, delay, status);
    for (IJobChangeListener l : listeners)
      JobManager.dispatch(call, l, event);
    JobManager.getInstance().fireJobChange(call, event);
  }

  protected IProgressMonitor createMonitor() {
    taskName = null;
    subTaskName = null;
    totalWork = IProgressMonitor.UNKNOWN;
    worked = 0;
    if (system || ServerContext.client() == null)
      return new NullProgressMonitor();
    return CoreMonitor.wrap(new ClientProgressMonitor(ServerContext.client(), name, true, user));
  }

  /**
   * {@code Job.cancel()}: a waiting or sleeping job is removed (true); a running job is asked to stop through its
   * monitor (false, it may still complete).
   */
  public boolean cancel() {
    CountDownLatch latch;
    synchronized (this) {
      if (state == SLEEPING && !aboutToRun) {
        // JobManager.cancel: a sleeping job is removed at once (it may sleep until it is woken up)
        submission++;
        canceledBeforeRun = false;
        state = NONE;
        result = Status.CANCEL_STATUS;
        latch = done;
      } else {
        if (state == WAITING) {
          if (aboutToRun) {
            cancelRunning = true;
            return false;
          }
          canceledBeforeRun = true;
          return true;
        }
        if (state == RUNNING || aboutToRun) {
          cancelRequested = true;
          IProgressMonitor m = monitor;
          if (m != null)
            m.setCanceled(true);
          else
            cancelRunning = true;
          return false;
        }
        return true;
      }
    }
    fireChange((l, e) -> l.done(e), 0, Status.CANCEL_STATUS);
    latch.countDown();
    return true;
  }

  public void join() throws InterruptedException {
    done.await();
  }

  public boolean join(long timeoutMillis, IProgressMonitor m) throws InterruptedException {
    return done.await(timeoutMillis, TimeUnit.MILLISECONDS);
  }

  /** {@code Job.wakeUp()}: a sleeping job waits to run again ({@code awake}). */
  public void wakeUp() {
    wakeUp(0L);
  }

  /** {@code Job.wakeUp(long)}: a sleeping job waits to run again after the delay. */
  public synchronized void wakeUp(long delay) {
    if (state == SLEEPING)
      submit(Math.max(0, delay));
  }

  /**
   * {@code Job.sleep()}: a waiting job is put to sleep until it is woken up ({@code sleeping}); a sleeping job sleeps
   * until it is woken up; a running job cannot sleep.
   *
   * @return whether the job sleeps
   */
  public boolean sleep() {
    synchronized (this) {
      if (state == SLEEPING) {
        submission++;
        return true;
      }
      if (state != WAITING || aboutToRun)
        return false;
      state = SLEEPING;
      submission++;
    }
    fireChange((l, e) -> l.sleeping(e), 0, null);
    return true;
  }

  public void done(IStatus result) {
  }

  /**
   * The monitor of a run: it passes everything to the monitor of the job and keeps the task, the sub task and the work
   * for the Progress view, which is told about every change.
   */
  private final class ProgressReporter implements IProgressMonitorWithBlocking {
    private final IProgressMonitor target;

    ProgressReporter(IProgressMonitor target) {
      this.target = target;
    }

    @Override
    public void beginTask(String taskName, int totalWork) {
      target.beginTask(taskName, totalWork);
      Job.this.taskName = taskName;
      Job.this.subTaskName = null;
      Job.this.totalWork = totalWork;
      Job.this.worked = 0;
      progressChanged();
    }

    @Override
    public void done() {
      target.done();
    }

    @Override
    public void internalWorked(double work) {
      target.internalWorked(work);
      Job.this.worked += work;
      progressChanged();
    }

    @Override
    public boolean isCanceled() {
      return target.isCanceled();
    }

    @Override
    public void setCanceled(boolean value) {
      target.setCanceled(value);
    }

    @Override
    public void setTaskName(String taskName) {
      target.setTaskName(taskName);
      Job.this.taskName = taskName;
      progressChanged();
    }

    @Override
    public void subTask(String subTaskName) {
      target.subTask(subTaskName);
      Job.this.subTaskName = subTaskName;
      progressChanged();
    }

    @Override
    public void worked(int work) {
      target.worked(work);
      Job.this.worked += work;
      progressChanged();
    }

    @Override
    public void setBlocked(IStatus reason) {
      if (target instanceof IProgressMonitorWithBlocking)
        ((IProgressMonitorWithBlocking) target).setBlocked(reason);
      Job.this.blockedStatus = reason;
      progressChanged();
    }

    @Override
    public void clearBlocked() {
      if (target instanceof IProgressMonitorWithBlocking)
        ((IProgressMonitorWithBlocking) target).clearBlocked();
      Job.this.blockedStatus = null;
      progressChanged();
    }

    private void progressChanged() {
      JobManager.getInstance().fireProgress(Job.this);
    }
  }
}
