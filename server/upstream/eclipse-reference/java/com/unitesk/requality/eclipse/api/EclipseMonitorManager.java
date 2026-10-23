package com.unitesk.requality.eclipse.api;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;

import com.unitesk.requality.core.api.IMonitorManager;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.ProgressRunnable;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.eclipse.core.EclipseProgressMonitor;

public class EclipseMonitorManager implements IMonitorManager {

  @Override
  public void runUnderMonitor(String name, boolean dialog, ProgressRunnable rr) {
    Display d = Display.findDisplay(Thread.currentThread());
    boolean isGui = d!=null;
    if(d==null)
      d = EclipseAPI.getDisplay();
    Display fd = d;
    if(dialog)
    {
      WorkspaceJob progress = new WorkspaceJob(name){
    
        @Override
        public IStatus runInWorkspace(org.eclipse.core.runtime.IProgressMonitor monitor){
          fd.asyncExec(new Runnable(){
          
            @Override
            public void run() {
              ProgressMonitorDialog pmm = new ProgressMonitorDialog(null);
              try {
                pmm.run(
                    true,
                    true,
                    new IRunnableWithProgress() {
                      @Override
                      public void run(org.eclipse.core.runtime.IProgressMonitor monitor)
                          throws InvocationTargetException, InterruptedException {
                        rr.run(new EclipseProgressMonitor(monitor));
                      }
                    });
              } catch (InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          });
          
          return Status.OK_STATUS;
        }
      };
      progress.schedule();
      return;
    }
    WorkspaceJob progress = new WorkspaceJob(name){
    
      @Override
      public IStatus runInWorkspace(org.eclipse.core.runtime.IProgressMonitor monitor)
          throws CoreException {
        rr.run(new EclipseProgressMonitor(monitor));
        return Status.OK_STATUS;
      }
    };  
    progress.schedule();
  }
  
  @Override
  public IProgressMonitor startMonitor(ITreeStorage storage, String name,int taskMax, boolean dialog) {
    if(storage == null || storage.getMonitor() == null) {
      Object monWait = "wait"+name;
      AtomicReference<IProgressMonitor> mon = new AtomicReference<>(null);
      WorkspaceJob wj = new WorkspaceJob(name){
      
        @Override
        public IStatus runInWorkspace(org.eclipse.core.runtime.IProgressMonitor monitor)
            throws CoreException {
          EclipseProgressMonitor emm = new EclipseProgressMonitor(monitor);
          mon.set(emm);
          synchronized (monWait) {
            monWait.notify(); 
          }
          while(emm.getRest()>0 && !emm.isCanceled() && !monitor.isCanceled())
            try {
              Thread.currentThread().join(200);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          return Status.OK_STATUS;
        }
      };
      synchronized (monWait) {
        wj.schedule();
        if(mon.get() == null)
          try {
            monWait.wait();
          } catch (InterruptedException e) {
            e.printStackTrace();
          } 
      }
      return mon.get();
    }
    else
      storage.getMonitor().beginTask(name, taskMax);
    return storage.getMonitor();
  }
}
