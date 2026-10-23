package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;

/** Eclipse Job semantics of the compat job: rescheduling from shouldRun/run, join, cancel. */
class JobTest {
  @Test
  void aJobRescheduledFromShouldRunRunsLaterAndJoinWaitsForIt() throws Exception {
    AtomicInteger checks = new AtomicInteger();
    AtomicInteger runs = new AtomicInteger();
    Job j = new Job("retry") {
      @Override
      public boolean shouldRun() {
        if (checks.incrementAndGet() < 3) {
          schedule(50); // busy: try again later (GenerateReportHandler)
          return false;
        }
        return true;
      }

      @Override
      protected IStatus run(IProgressMonitor monitor) {
        runs.incrementAndGet();
        return Status.OK_STATUS;
      }
    };
    j.schedule();
    assertTrue(j.join(10000, null));
    assertEquals(3, checks.get());
    assertEquals(1, runs.get());
    assertEquals(Job.NONE, j.getState());
  }

  @Test
  void aCanceledSleepingJobDoesNotRun() throws Exception {
    AtomicInteger runs = new AtomicInteger();
    Job j = new Job("sleeping") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        runs.incrementAndGet();
        return Status.OK_STATUS;
      }
    };
    j.schedule(300);
    assertTrue(j.cancel());
    assertTrue(j.join(5000, null));
    assertEquals(0, runs.get());
  }

  @Test
  void cancelingARunningJobCancelsItsMonitor() throws Exception {
    java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
    boolean[] canceled = new boolean[1];
    Job j = new Job("running") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        started.countDown();
        long end = System.currentTimeMillis() + 5000;
        while (!monitor.isCanceled() && System.currentTimeMillis() < end)
          Thread.onSpinWait();
        canceled[0] = monitor.isCanceled();
        return Status.CANCEL_STATUS;
      }
    };
    j.setSystem(true);
    j.schedule();
    started.await();
    assertFalse(j.cancel());
    assertTrue(j.join(10000, null));
    assertTrue(canceled[0]);
  }
}
