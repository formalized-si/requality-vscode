package com.unitesk.requality.eclipse.jobs;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseProgressMonitor;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;


public class EclipseJobManager extends JobManager {
	private static long maxid = 0;
	private long id = maxid++;
	@Override
	public void innerStartDisplayJob(String name, RunnableWithMonitor r, boolean sync) {
      com.unitesk.requality.core.operation.IProgressMonitor mon = ConsoleProgressMonitor.ONE;
      if(sync)
        EclipseAPI.getDisplay().syncExec(r.toRunnable(mon));
      else
        EclipseAPI.getDisplay().asyncExec(r.toRunnable(mon));
	}
	@Override
	protected void innnerStartJob(String name, RunnableWithMonitor r, boolean userVisible) {
  	  RLogger.logDebug("Inner eclipse job("+id+") started "+name+" "+userVisible, 7);
	  RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()), 7);
	  if(userVisible) {
		WorkspaceJob wj = new WorkspaceJob(name){
		    @Override
		    public boolean shouldRun() {
		      if(r!=null && !r.shouldRun()) {
    		      if(r.shouldRerun()) {
                    RLogger.logDebug("Inner eclipse job("+id+") restarted "+name+" "+userVisible, 7);
    		        this.schedule(r.getTimeToReshedule());
    		      }
    		      r.isCanceled = true;
    		      return false;
		      }
		      return super.shouldRun();
		    }
			@Override
			public IStatus runInWorkspace(org.eclipse.core.runtime.IProgressMonitor monitor) throws CoreException {
				EclipseProgressMonitor epm = new EclipseProgressMonitor(monitor);
				if(r!=null)
				  r.run(epm);
				if(epm.isCanceled())
					return Status.CANCEL_STATUS;
				return Status.OK_STATUS;
			}
		};
		wj.schedule();
	  }else {
        RLogger.logDebug("Inner eclipse base job("+id+") started "+name+" "+userVisible, 7);
        RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()), 7);

        Job wj = new Job(name){
            @Override
            public boolean shouldRun() {
              if(r!=null && !r.shouldRun()) {
                  if(r.shouldRerun()) {
                    RLogger.logDebug("Inner eclipse base job("+id+") restarted "+name+" "+userVisible, 7);
                    this.schedule(r.getTimeToReshedule());
                  }
                  r.isCanceled = true;
                  return false;
              }
              return super.shouldRun();
            }
            @Override
            protected IStatus run(IProgressMonitor monitor) {
              EclipseProgressMonitor epm = new EclipseProgressMonitor(monitor);
              if(r!=null)
                r.run(epm);
              if(epm.isCanceled())
                  return Status.CANCEL_STATUS;
              return Status.OK_STATUS;
            }
        };
        wj.schedule();
	  }
	}
}
