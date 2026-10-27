package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import com.unitesk.requality.vscode.ui.runtime.Background;

class BackgroundTest {
  @Test
  void callWaitsForDelegatedWorkAndNestedWorkInheritsTheHolder() throws Exception {
    ReentrantLock lock = new ReentrantLock();
    Background.lock(lock);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    AtomicReference<Future<?>> worker = new AtomicReference<>();
    try {
      Future<?> call = pool.submit(() -> {
        lock.lock();
        try {
          Background.holding(() -> {
            Background.Holder holder = Background.current();
            worker.set(pool.submit(() -> Background.run(holder, () -> {
              assertSame(holder, Background.current());
              started.countDown();
              await(finish);
              // This is what a job scheduling a UI job does. The caller is already leaving its body.
              // Capture the holder on the scheduling thread, as Job and Display do.
              Future<?> inherited = pool.submit(() -> Background.run(holder,
                  () -> assertSame(holder, Background.current())));
              get(inherited);
            })));
            await(started);
            return null;
          });
        } finally {
          lock.unlock();
        }
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> call.get(100, TimeUnit.MILLISECONDS));
      finish.countDown();
      call.get(5, TimeUnit.SECONDS);
      worker.get().get(5, TimeUnit.SECONDS);
      assertNull(Background.current());
    } finally {
      finish.countDown();
      pool.shutdownNow();
      Background.lock(null);
    }
  }

  @Test
  void expiredHolderCannotBypassALaterCall() throws Exception {
    ReentrantLock lock = new ReentrantLock();
    Background.lock(lock);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Background.Holder holder = Background.holding(Background::current);
      lock.lock();
      try {
        assertFalse(pool.submit(() -> Background.tryRun(holder,
            () -> fail("An expired holder must not bypass the lock"))).get(5, TimeUnit.SECONDS));
      } finally {
        lock.unlock();
      }
      assertTrue(pool.submit(() -> Background.tryRun(holder, () -> {})).get(5, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
      Background.lock(null);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void get(Future<?> future) {
    try {
      future.get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
