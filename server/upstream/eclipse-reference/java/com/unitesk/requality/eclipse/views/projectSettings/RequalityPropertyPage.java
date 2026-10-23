package com.unitesk.requality.eclipse.views.projectSettings;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.PropertyPage;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public abstract class RequalityPropertyPage extends PropertyPage implements DisposeListener {
  private static Map<Shell, Set<RequalityPropertyPage>> registeredPages = new ConcurrentHashMap<>();
  public RequalityPropertyPage() {
    super();
  }

  public abstract boolean isOkOperationInTransaction();

  public abstract IStatus okPressedAction(IProgressMonitor monitor);

  public abstract boolean settingsChanged();

  public abstract TreeDB getTreeDB();

  public abstract boolean inputCorrect();

  private boolean runStarted = false;

  protected boolean inParallelUpdate(){
    return isHandledInParallel;
  }
  
  @Override
  public void widgetDisposed(DisposeEvent e) {
    Shell shell = getShell();
    synchronized (registeredPages) {
      Set<RequalityPropertyPage> pages = registeredPages.get(shell);
      if (pages == null) {
        pages = new HashSet<>();
        registeredPages.put(shell, pages);
      }
      pages.remove(this);
      if (pages.isEmpty()) registeredPages.remove(shell);
    }
  }

  public void registerPage() {
    synchronized (registeredPages) {
      Shell shell = getShell();
      Set<RequalityPropertyPage> pages = registeredPages.get(shell);
      if (pages == null) {
        shell.addDisposeListener(this);
        pages = new HashSet<>();
        registeredPages.put(shell, pages);
      }
      pages.add(this);
    }
  }

  @Override
  public void createControl(Composite parent) {
    super.createControl(parent);
    registerPage();
  }

  @Override
  public boolean isValid() {
    return getErrorMessage() == null && inputCorrect();
  }

  protected void updateStatus(String message, boolean inDisplay) {
    // Set node id nodeId to node.getQualifiedId()
    boolean newstate = message == null;
    Runnable r =
        (new Runnable() {

          @Override
          public void run() {
            if (getControl().isDisposed()) return;
            setErrorMessage(message);
            getApplyButton().setEnabled(newstate);
            getContainer().updateButtons();
          }
        });
    if (inDisplay) EclipseAPI.getDisplay().asyncExec(r);
    else r.run();
  }

  protected boolean actionsStarted = false;
  private boolean isHandledInParallel = false;
  private boolean performWasApply = false;

  private WorkspaceJob okJob = null;

  @Override
  protected void performApply() {
    performWasApply = true;
    super.performApply();
  }
  @Override
  public final boolean performOk() {
    try{
      if (!super.performOk()) 
        return false;
      if(isHandledInParallel) 
        return false;//wait untill processing is over
      if(actionsStarted) 
        return false;
      if(okJob!=null)
        return false;
      Set<RequalityPropertyPage> props = registeredPages.get(getShell());
      WorkspaceJob startedJob = null;
      try {
        TreeDB db = getTreeDB();
        com.unitesk.requality.core.operation.IProgressMonitor mon = db == null? null: db.getMonitor();
        if(mon == null)
          mon = new ConsoleProgressMonitor();
        TreeStateManager tsm = db == null ? null : TreeStateManager.getOne(db.getTracker());
        //      boolean pageWithTransaction = isOkOperationInTransaction();
        List<RequalityPropertyPage> pageTransactionChanges = Collections.emptyList();
        List<RequalityPropertyPage> pageNonTransactionChanges = Collections.emptyList();
  
  
        for (RequalityPropertyPage prop : props) {
          prop.isHandledInParallel = true;
          if (!prop.isValid() || prop.isChangePerforming() || !prop.inputCorrect()) return false;
          if (prop.settingsChanged()) {
            if (prop.isOkOperationInTransaction()){
              if(pageTransactionChanges.isEmpty())
                pageTransactionChanges = new LinkedList<>();
              pageTransactionChanges.add(prop);
            }
            else{
              if(pageNonTransactionChanges.isEmpty())
                pageNonTransactionChanges = new LinkedList<>();
              pageNonTransactionChanges.add(prop);
            }
          }
        }
        
        if (db == null
            || tsm == null
            || (pageTransactionChanges.size() > 0)
                && !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) return false;
        runStarted = true;
        Shell shell = getShell();
        String jname = "Change project settings for " + db.getVisibleName();
        final List<RequalityPropertyPage> transactList = pageTransactionChanges;
        final List<RequalityPropertyPage> nonTransactList = pageNonTransactionChanges;
        final Display display = shell.getDisplay();
        display
          .syncExec(
              new Runnable() {
                @Override
                public void run() {
                  for (RequalityPropertyPage wj : nonTransactList)
                    wj.prepareVars();
                  for (RequalityPropertyPage wj : transactList)
                    wj.prepareVars();
                }
              });
        final boolean finalizeDialog = !performWasApply;
        startedJob =
          new WorkspaceJob(jname) {
            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
              if(okJob!=this)
                return Status.CANCEL_STATUS;
              boolean completed = false;
              try {
                IStatus s = Status.OK_STATUS;
                TreeDB db1 = getTreeDB();
                if (db1 != null
                    && (transactList.size() == 0
                        || tsm.canStartTransaction(db1.getLocalName(), tsm.getRevisionInfo(db1))
                            && transactList.size() > 0)) {
                  for (RequalityPropertyPage wj : nonTransactList) {
                    s = wj.okPressedAction(null);
                    if (s == Status.CANCEL_STATUS) 
                      return Status.CANCEL_STATUS;
                  }
                  boolean stop = false;
                  if (transactList.size() > 0) {
                    stop = db1.startTransaction(jname, true);
                    if(!stop)
                      return Status.CANCEL_STATUS;
        //            db1.getStorage().setMonitor(new EclipseProgressMonitor(monitor));
                    try {
                      for (RequalityPropertyPage wj : transactList) {
                        s = wj.okPressedAction(null);
                        if (s == Status.CANCEL_STATUS) 
                          return s;
                      }
                      if (stop) {
                        db1.commit();
                        completed = true;
                        stop = false;
                      }
                    } catch (Exception e) {
                      RLogger.logError(e);
                      return Status.CANCEL_STATUS;
                    } finally {
                      db1.getStorage().setMonitor(db1.getMonitor()); // return back
                      if(stop)
                        db1.rollback();
                    }
                  }else
                    completed = true;//TODO check the number of non-transactional pages?
                }
              } finally {
                runStarted = false;
                if(okJob==this){
                  okJob = null;
                  
                  for (RequalityPropertyPage prop : props)
                    prop.isHandledInParallel = false;
                  if(completed && finalizeDialog){
                    display.asyncExec(new Runnable(){
          
                      @Override
                      public void run() {
                        shell.dispose();//TODO change?
                      }
                    });
                  }
                }
              }
              return Status.OK_STATUS;
            };
          };
        okJob = startedJob;
        startedJob.schedule();
        super.performOk();
        return false;//run in parallel
        //TODO think about okJob not to be inited?
      } finally {
        if(startedJob==null || okJob!=startedJob)
          for (RequalityPropertyPage prop : props)
            prop.isHandledInParallel = false;
      }
    }finally{
      performWasApply = false;
    }
  }

  private boolean isChangePerforming() {
    return runStarted;
  }

  public abstract void prepareVars();
}
