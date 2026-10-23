package com.unitesk.requality.core.jobs;

import com.unitesk.requality.core.operation.IProgressMonitor;

public abstract class RunnableWithMonitor{
  
  public abstract void run(IProgressMonitor pm);
  public boolean isCanceled = false;//TODO proper interface?

  public Runnable toRunnable(IProgressMonitor pm) {
    return new Runnable() {
      @Override
      public void run() {
        if(shouldRun())
          RunnableWithMonitor.this.run(pm);
        else {
          if(isCanceled())
            return;
          try {
            Thread.currentThread().join(getTimeToReshedule());
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          run();
        }
      }
    };
  }
  public boolean isCanceled() {
    return isCanceled;
  }
  public long getTimeToReshedule() {
    return 200;
  }
  public boolean shouldRun() {
    return true;
  }

  public boolean shouldRerun() {
    return false;
  }
}
