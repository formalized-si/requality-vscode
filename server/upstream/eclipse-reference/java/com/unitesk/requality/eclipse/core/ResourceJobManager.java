package com.unitesk.requality.eclipse.core;

import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import com.unitesk.requality.core.model.TreeDB;

public class ResourceJobManager {
  private ConcurrentHashMap<String, ResourceChangeJob> resourceJobs = new ConcurrentHashMap<>();
  private static String getId(TreeDB db) {
    return db.getLocalName();
  }
  public void registerJob(TreeDB db, ISchedulingRule rule, IWorkspaceRunnable job) {
   
  }
}
