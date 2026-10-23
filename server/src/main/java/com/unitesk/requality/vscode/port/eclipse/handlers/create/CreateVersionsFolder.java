package com.unitesk.requality.vscode.port.eclipse.handlers.create;

import java.io.IOException;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.nodetype.InstancesFolder;

public class CreateVersionsFolder extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IStructuredSelection sel =
        (IStructuredSelection)
            PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getSelectionService()
                .getSelection();
    IProject project = (IProject) sel.getFirstElement();
    TreeDB db = null;
	try {
	  EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
	  db = et
         .getTreeDB(project, null, et.getLocalName(project));
	} catch (IOException e) {
	  RLogger.logError(e);
	}
	if(db == null)
	  return null;
    Object o = null;
    boolean started = db.startTransaction("Create Versions Folder", true);
    if (started) {
      try {
        o = createVersionsFolder(db);
        if(started)
        {
          started = false;
          db.commit();
        }
        return o;
      }finally {
        if(started)
          db.rollback();
      }
    }
    return null;
  }

  public Object createVersionsFolder(TreeDB db) {
    TreeNode n = db.getRootNode().createChild("Versions", InstancesFolder.TYPE_NAME);
    return n;
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
          TreeDB db = null;
		  try {
		    db = et.getTreeDB(project, null, et.getLocalName(project));
		  } catch (IOException e) {
			// TODO Auto-generated catch block
			RLogger.logError(e);
		  }
		  if(db == null)
            return false;
          TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
          if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
            return false;
          return !db.isVirtual() && db.getNode("/Versions", Path.QID) == null;
        }
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return false;
  }
}
