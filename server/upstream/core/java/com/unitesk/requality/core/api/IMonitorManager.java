package com.unitesk.requality.core.api;

import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.ProgressRunnable;
import com.unitesk.requality.core.storage.ITreeStorage;

public interface IMonitorManager {
  public void runUnderMonitor(String name, boolean dialog, ProgressRunnable runnable);
  public IProgressMonitor startMonitor(ITreeStorage aStorage, String name, int taskMax, boolean dialog);
}
