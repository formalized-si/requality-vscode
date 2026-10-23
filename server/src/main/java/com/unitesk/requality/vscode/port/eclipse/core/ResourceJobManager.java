package com.unitesk.requality.vscode.port.eclipse.core;

import java.util.concurrent.ConcurrentHashMap;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRunnable;
import com.unitesk.requality.vscode.ui.runtime.ISchedulingRule;
import com.unitesk.requality.core.model.TreeDB;

public class ResourceJobManager {
  private ConcurrentHashMap<String, ResourceChangeJob> resourceJobs = new ConcurrentHashMap<>();
  private static String getId(TreeDB db) {
    return db.getLocalName();
  }
  public void registerJob(TreeDB db, ISchedulingRule rule, IWorkspaceRunnable job) {
   
  }
}
