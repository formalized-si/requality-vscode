package com.unitesk.requality.eclipse.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class LockProxy<L extends Lock> implements Lock {
  private volatile Lock lock;

  public LockProxy(L lock) {
    this.lock = lock;
  }

  @Override
  public void lock() {
    lock.lock();
  }

  @Override
  public void lockInterruptibly() throws InterruptedException {
    lock.lockInterruptibly();
  }

  @Override
  public boolean tryLock() {
    return lock.tryLock();
  }

  @Override
  public boolean tryLock(long l, TimeUnit tu) throws InterruptedException {
    return lock.tryLock(l, tu);
  }

  @Override
  public void unlock() {
    lock.unlock();
  }

  @Override
  public Condition newCondition() {
    return lock.newCondition();
  }

  public void forceUnlock() {
    if (lock instanceof ReentrantLock) {
      lock = new ReentrantLock();
    } else {
      throw new UnsupportedOperationException(
        "Cannot force unlock of lock type "
          + lock.getClass().getSimpleName());
    }
  }
}