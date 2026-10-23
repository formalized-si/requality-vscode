package com.unitesk.requality.eclipse.handlers;

import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseProgressMonitor;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tool.CoreUtils;

public class ClearCachesRunCheckers extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    String op = CoreUtils.getWorkspaceOperation();
    if(!op.isEmpty()){
      EclipseAPI.getDisplay().asyncExec(new Runnable(){
        @Override
        public void run() {
          MessageDialog.openError(EclipseAPI.getDisplay().getActiveShell(),
              "Clear caches error","Cannot clear caches: operation '"+op+"' is performing now.");
        }
      });
      return null;
    }else{
      EclipseTracker et = null;
      IStructuredSelection sel =
          (IStructuredSelection)
              PlatformUI.getWorkbench()
                  .getActiveWorkbenchWindow()
                  .getSelectionService()
                  .getSelection();
      if (!(sel.getFirstElement() instanceof IProject)) return null;
      IProject project = (IProject) sel.getFirstElement();
      TreeDB db = null;
      try {
        et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        db = et.getTreeDB(project, null, et.getLocalName(project));
      } catch (IOException e) {
        RLogger.logError(e);
      }
      if (db == null) return null;
      final TreeDB fdb = db;
      CoreUtils.lockWorkspace("Clear caches on "+fdb.getVisibleName());
      final EclipseTracker fet = et;
      WorkspaceJob wj = new WorkspaceJob("Clear caches on "+fdb.getVisibleName()){
        TreeDB db = fdb;
        @Override
        public boolean shouldRun() {
          if(super.shouldRun())
          {
            if(CoreUtils.busyCondition(db)){
              CoreUtils.unlockWorkspace();
              return false;
            }
            return true;
          }
          CoreUtils.unlockWorkspace();
          return false;
        }
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
          try {
            String lname = db.getLocalName();
            String pname = db.getProjectName();
            CacheStorage cs = db.getTracker().getCPCacheStorage(db);
            CacheTransactionDescription started = cs.startForcedCacheTransaction("reset checkers", 2, false);
    //          db.setDisposed();
              //db.startTransaction("force checkers");
            TreeStateManager tsm = db.getTracker().getTreeStateManager();
            TreeState[]states = new TreeState[] {TreeState.READY, TreeState.CANCELED};
            final TreeDB fdb = db;
            IStateChangeHandler ch = new AAllTreeStateChangeHandler() {
              @Override
              public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri,
                  TreeState oldState, TreeState state) {
                if(db == null)
                  db = fdb;
                tsm.removeStateHook(lname, states, this);
                try {
                  if(state == TreeState.READY) {
                	Global.runCheckers(db, true, false);
                  }
                }finally {
                  if(started!=null && started.error == null)
                    cs.commit(started);
                }
              }
            };
            tsm.addStateHook(lname, states, ch);
            db = Global.clearCachesAndReopenNonParallel(db, null, false, false);//TODO rename?
            if(db!=null)
              Global.removeReportCacheFolders(lname, pname);//TODO move?
            Global.forgetRepoManager(lname, db, false);
            if(db == null)
              db = fet.getTreeDB((TreeDB)null, lname, null, false, EclipseProgressMonitor.from(monitor));
            return Status.OK_STATUS;
          } catch (Exception ex) {
            ex.printStackTrace();
          }
          finally{
            CoreUtils.unlockWorkspace();
          }
          return Status.CANCEL_STATUS;
        }
      };
      wj.schedule();
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    IStructuredSelection sel =
        (IStructuredSelection)
            PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getSelectionService()
                .getSelection();
    if (sel.getFirstElement() instanceof IProject) {
      IProject project = (IProject) sel.getFirstElement();
      try {
        if (!project.isAccessible()) return false;
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        if (project.hasNature(ReqCatalogue.ID)&&et.getProjectLoadingState(project.getLocationURI())==ProjectLoadingState.LOADED
            &&!project.hasNature(Consts.CATALOG_BROKEN_NATURE)) {
          String name = et.getLocalName(project);
          TreeDB db = et.getTreeDB(project, null, name);

          if(db == null)
            return false;
          if (db == null || !TreeStateManager.getOne(db.getTracker()).canStartTransaction(name, null))
            return false;
          return true;
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }
    return false;
  }
}
