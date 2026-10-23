package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.Collection;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.resources.IProject;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.handlers.create.CreateNewTest;

public class NewMenuHide extends AbstractHandler implements IHandler {

  @Override
  public boolean isEnabled() {
    try {
      SelectionRecord nodes =
          SelectionManager.getOne().getSelection(EventSource.EXPLORER);
      TreeNode nds = null;
      if (nodes == null || nodes.fullSelection.size() == 0) 
      {
        Collection<Object> objs = SelectionManager.getOne().getSelectionObjects();
        if(objs != null && objs.size() == 1) {
          Object p = objs.iterator().next();
          if(p!=null && p instanceof IProject) {
            IProject pr = (IProject) p;
            TreeDB db = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getTreeDB(pr, null, null);
            if(db!=null) {
              TreeStateManager tsm = db.getTracker().getTreeStateManager();
              if(tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
                nds = db.getRootNode();
            }
          }
        }
        if(nds == null)
          return false;
      }else
        if(nodes.fullSelection.size() != 1)
          return false;
      if(nds == null && nodes!=null)
        nds = nodes.getFirstNode();
      if(nds == null)
        return false;
      TreeDB db = nds.getTreeDB();
      TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
      if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
        return false;

      Collection<CreateElementOperation> news = CreateElementOperation.getHandlers();
      for (CreateElementOperation nw : news)
        for (String s : nw.getElementTypes())
          if (nds.getTreeDB().getTreeLogic().canBeCreatedIn(s, nds)) 
            return true;
      if(CreateNewTest.getOne().isEnabled())
        return true;
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return false;
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }
}
