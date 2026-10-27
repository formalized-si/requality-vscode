package com.unitesk.requality.vscode.ui.runtime;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * The work of the workbench in the background (the jobs, the runnables of the display) and the calls of a server that
 * read and change the trees: with a lock (the server without a window sets it), the background work runs only while no
 * call runs, so that a call does not read a node that a job loads or changes at the same time. Work that a thread hands
 * over while it holds the lock (a job that a call starts and waits for) runs at once. Without a lock (VS Code), all
 * work runs at once.
 */
public final class Background {
  private Background() {
  }

  /** A thread that holds the lock: the work it hands over runs at once while it holds it. */
  public static final class Holder {
    private boolean active = true;
    private int running;

    synchronized boolean enter() {
      if (!active)
        return false;
      running++;
      return true;
    }

    synchronized void leave() {
      running--;
      notifyAll();
    }

    synchronized void finish() {
      boolean interrupted = false;
      while (running != 0) {
        try {
          wait();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      active = false;
      if (interrupted)
        Thread.currentThread().interrupt();
    }
  }

  private static final ThreadLocal<Holder> HELD = new ThreadLocal<>();
  private static volatile Lock lock;

  /** The lock that the background work takes (null: none). */
  public static void lock(Lock l) {
    lock = l;
  }

  /** The holder of the current thread (null: it holds no lock). */
  public static Holder current() {
    return HELD.get();
  }

  /** Runs a task of a thread that holds the lock now (a call of the server under its lock). */
  public static <T> T holding(Supplier<T> task) {
    if (HELD.get() != null)
      return task.get();
    Holder h = new Holder();
    HELD.set(h);
    try {
      return task.get();
    } finally {
      h.finish();
      HELD.remove();
    }
  }

  /** {@link #holding(Supplier)} of a task that can throw. */
  public static <T> T holdingCall(java.util.concurrent.Callable<T> task) throws Exception {
    if (HELD.get() != null)
      return task.call();
    Holder h = new Holder();
    HELD.set(h);
    try {
      return task.call();
    } finally {
      h.finish();
      HELD.remove();
    }
  }

  /** Joins the lifetime of the originating call, including work handed over by this task. */
  private static boolean delegated(Holder from, Runnable work) {
    if (from == null || !from.enter())
      return false;
    Holder previous = HELD.get();
    HELD.set(from);
    try {
      work.run();
    } finally {
      if (previous == null)
        HELD.remove();
      else
        HELD.set(previous);
      from.leave();
    }
    return true;
  }

  /** Runs work that {@code from} handed over: under the lock (it waits for it), unless it runs at once. */
  public static void run(Holder from, Runnable work) {
    Lock l = lock;
    if (l == null || HELD.get() != null) {
      work.run();
      return;
    }
    if (delegated(from, work))
      return;
    l.lock();
    try {
      holding(() -> {
        work.run();
        return null;
      });
    } finally {
      l.unlock();
    }
  }

  /**
   * {@link #run} without a wait (the display thread must not wait: a call can wait for it): false when the lock is
   * taken now and the work did not run.
   */
  public static boolean tryRun(Holder from, Runnable work) {
    Lock l = lock;
    if (l == null || HELD.get() != null) {
      work.run();
      return true;
    }
    if (delegated(from, work))
      return true;
    if (!l.tryLock())
      return false;
    try {
      holding(() -> {
        work.run();
        return null;
      });
    } finally {
      l.unlock();
    }
    return true;
  }
}
