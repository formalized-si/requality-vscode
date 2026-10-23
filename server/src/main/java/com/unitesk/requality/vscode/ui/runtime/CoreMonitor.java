package com.unitesk.requality.vscode.ui.runtime;

import com.unitesk.requality.vscode.core.ClientProgressMonitor;

/**
 * Adapts between the Eclipse progress monitor of ported code and the Requality core monitor (the original code uses
 * {@code EclipseProgressMonitor.from(monitor)} for the same purpose).
 */
public final class CoreMonitor {
  private CoreMonitor() {
  }

  /** Core monitor reporting to an Eclipse-style monitor. */
  public static com.unitesk.requality.core.operation.IProgressMonitor from(IProgressMonitor m) {
    if (m instanceof Bridge)
      return ((Bridge) m).core;
    return new com.unitesk.requality.core.operation.IProgressMonitor() {
      private int max;
      private String task;

      @Override
      public void worked(int val) {
        m.worked(val);
      }

      @Override
      public void doneAll() {
        m.done();
      }

      @Override
      public int getRest() {
        return 0;
      }

      @Override
      public int getMax() {
        return max;
      }

      @Override
      public void beginTask(String s, int taskMax) {
        max = taskMax;
        task = s;
        m.beginTask(s, taskMax);
      }

      @Override
      public void doneTask() {
      }

      @Override
      public boolean isCanceled() {
        return m.isCanceled();
      }

      @Override
      public void cancel() {
        m.setCanceled(true);
      }

      @Override
      public String getTask() {
        return task;
      }

      @Override
      public boolean isDBLocal() {
        return false;
      }
    };
  }

  /** Eclipse-style monitor backed by a core monitor shown in VS Code. */
  public static IProgressMonitor wrap(com.unitesk.requality.core.operation.IProgressMonitor core) {
    return new Bridge(core);
  }

  static final class Bridge implements IProgressMonitor {
    final com.unitesk.requality.core.operation.IProgressMonitor core;

    Bridge(com.unitesk.requality.core.operation.IProgressMonitor core) {
      this.core = core;
    }

    @Override
    public void beginTask(String name, int totalWork) {
      core.beginTask(name, totalWork < 0 ? 1 : totalWork);
    }

    @Override
    public void done() {
      core.doneAll();
      if (core instanceof ClientProgressMonitor)
        ((ClientProgressMonitor) core).end();
    }

    @Override
    public boolean isCanceled() {
      return core.isCanceled();
    }

    @Override
    public void setCanceled(boolean value) {
      if (value)
        core.cancel();
    }

    @Override
    public void subTask(String name) {
      core.beginTask(name, 1);
      core.doneTask();
    }

    @Override
    public void setTaskName(String name) {
      subTask(name);
    }

    @Override
    public void worked(int work) {
      core.worked(work);
    }
  }
}
