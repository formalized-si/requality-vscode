package com.unitesk.requality.eclipse.handlers;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;

public class DeleteStatisticsFileHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    IStructuredSelection sel =
        (IStructuredSelection)
            PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getSelectionService()
                .getSelection();
    IProject project = (IProject) sel.getFirstElement();
    TreeNode tn = null;
	try {
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      tn = et.getTreeDB(project, null, et.getLocalName(project)).getRootNode();
	} catch (IOException e1) {
		e1.printStackTrace();
	}
	if(tn == null)
      return null;
    URI path = tn.getNodeResourcesPath();
    path = new File(path).getParentFile().toURI();

    try {
      File[] list =
          new File(path)
              .listFiles(
                  new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                      return name.toLowerCase().startsWith("statistics")
                          && name.toLowerCase().endsWith(".xml");
                    }
                  });

      for (int i = 0; i < list.length; i++) {
        if (list[i].delete()) {
          RLogger.logDebug(list[i].getName() + " is deleted!");
        } else {
          RLogger.logDebug("Delete operation is failed.");
        }
      }
    } catch (Exception e) {
      RLogger.logError(e);
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
          TreeDB db;
		  try {
	        String name = et.getLocalName(project);
	        db = et.getTreeDB(project, null, name);
		    return db!=null && TreeStateManager.getOne(db.getTracker())
		        .canStartTransaction(name, null)&&!db.isVirtual() && db.getNode("/Versions", Path.QID) == null;
		  } catch (IOException e) {
			// TODO Auto-generated catch block
			RLogger.logError(e);
		  }
        }
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    return false;
  }
}
