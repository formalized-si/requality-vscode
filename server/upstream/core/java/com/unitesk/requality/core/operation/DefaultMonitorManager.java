package com.unitesk.requality.core.operation;

import com.unitesk.requality.core.api.IMonitorManager;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class DefaultMonitorManager implements IMonitorManager {
  @Override
  public void runUnderMonitor(String name, boolean dialog, ProgressRunnable pm) {
    pm.run(new ConsoleProgressMonitor());
  }

  @Override
  public IProgressMonitor startMonitor(ITreeStorage storage, String name, int taskMax, boolean dialog) {
    ConsoleProgressMonitor cpm = new ConsoleProgressMonitor();
    cpm.beginTask(name, taskMax);
    return cpm;
  }
}
