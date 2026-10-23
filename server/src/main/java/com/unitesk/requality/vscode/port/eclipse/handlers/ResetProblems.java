package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.io.IOException;
import java.util.List;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.resources.IProject;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.MassCheckersReset;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;

public class ResetProblems extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    MassCheckersReset mcr = new MassCheckersReset();
    IProject p = null;//TODO selection man objects
    List<Object> lo = RequalityCNF.getSelectedObj();
    if(lo!=null && lo.size()==1 && (lo.get(0) instanceof IProject))
      p = (IProject) lo.get(0);
    TreeNode node = null;
    if(p!=null)
		try {
		  EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
		  node = et.getTreeDB(p, null, et.getLocalName(p)).getRootNode();
		} catch (IOException e) {
			RLogger.logError(e);
		}
	else {
      node = 
          SelectionManager.getFirstNode(
              SelectionManager.getOne().getSelection(EventSource.EXPLORER)); 
      
    }
    if(node!=null)
      mcr.execute(node);
    return null;
  }
  @Override
  public boolean isEnabled() {
    return true;
  };
}
