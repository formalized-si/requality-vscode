package com.unitesk.requality.core.jobs;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class JobManager {
	public final static JobManager basejm = new JobManager();
	public static JobManager jm = basejm;
	private Map<RunnableWithMonitor, Thread> startedThreads = new HashMap<>();
	protected void innnerStartJob(String name, RunnableWithMonitor r, boolean alterResources) {
		RLogger.logDebug("Inner started job "+name+" "+alterResources, 7);
		innnerBaseStartJob(name, r, alterResources);
	}
	protected final void innnerBaseStartJob(String name, RunnableWithMonitor r, boolean alterResources) {
		RLogger.logDebug("Inner (base) started job "+name+" "+alterResources, 7);
		RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()), 7);
		IProgressMonitor pm = getMonitor(name,r);
		Thread t = new Thread(r.toRunnable(pm));
		startedThreads.put(r, t);
		t.setDaemon(!alterResources);
		t.start();
	}
	protected void innerStartDisplayJob(String name, RunnableWithMonitor r, boolean sync) {
		RLogger.logDebug("Display started job "+name+" "+sync, 7);
		RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()), 7);
		IProgressMonitor pm = getMonitor(name,r);
		Thread t = new Thread(r.toRunnable(pm));
		startedThreads.put(r, t);
		if(!sync) {
		  t.start();
		}else
          r.run(pm);
	}
    private void innerDropJob(RunnableWithMonitor rwp) {
        Thread t = startedThreads.remove(rwp);
        if(t!=null)
          t.interrupt();
    }
	private IProgressMonitor getMonitor(String name, RunnableWithMonitor rwm) {
		return new ConsoleProgressMonitor();
	}
	public void startJob(String name, RunnableWithMonitor rwm, Boolean alterResources) {
		innnerStartJob(name, rwm, alterResources);
	}
	public final void startBaseJob(String name, RunnableWithMonitor rwm, Boolean alterResources) {
		innnerBaseStartJob(name, rwm, alterResources);
	}
	public final void startDisplayJob(String name, RunnableWithMonitor rwm, Boolean async) {
		innerStartDisplayJob(name, rwm, async);
	}
    public void haltJob(RunnableWithMonitor rwp) {
       innerDropJob(rwp);
    }
}
