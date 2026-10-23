package com.unitesk.requality.eclipse.handlers;

import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.MassLocationsFixer;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;

public class TemporaryMassLocationFixer extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
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
	  EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
	  db = et.getTreeDB(project, null, et.getLocalName(project));
	} catch (IOException e) {
	  RLogger.logError(e);
	}
    if (db == null) return null;
    try {
      MassLocationsFixer mlf = new MassLocationsFixer();
      TreeNode node1 = db.getNode(Document.getTypeRootQId());
      mlf.execute(node1); //first fix docs
      TreeNode node2 = db.getNode(Requirement.getTypeRootQId());
      mlf.execute(node2); //then fix reqs
    } catch (Exception ex) {
      ex.printStackTrace();
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
    TreeStateManager tsm = null;
    if (sel.getFirstElement() instanceof IProject) {
      IProject project = (IProject) sel.getFirstElement();
      try {
        if (!project.isAccessible()) return false;
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        if (project.hasNature(ReqCatalogue.ID)&&
        		et.getProjectLoadingState(project.getLocationURI())==ProjectLoadingState.LOADED
        		&&!project.hasNature(Consts.CATALOG_BROKEN_NATURE)) {
          TreeDB db;
		  try {
		    String name = EclipseTracker.getProjectName(project);
			db = et.getTreeDB(project, null,name);
			if(tsm == null)
			  tsm = TreeStateManager.getOne(db.getTracker());
	        if(db == null || 
	        	!tsm.canStartTransaction(name, tsm.getRevisionInfo(db)))
	          return false;
		   } catch (IOException e) {
			// TODO Auto-generated catch block
			RLogger.logError(e);
  		  }
          
          return true;
        }
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return false;
  }
}
