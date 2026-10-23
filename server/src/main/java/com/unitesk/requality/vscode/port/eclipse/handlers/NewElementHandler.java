package com.unitesk.requality.vscode.port.eclipse.handlers;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.create.CreateElementOperation;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.handlers.create.AHotKeyCommand;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;

public abstract class NewElementHandler extends AHotKeyCommand{
  private static final String NODE_TYPE = "requality.parameter.NodeType";
  private CreateElementOperation ceo = null;
  {
    ceo = getCreateElementOperation();
  }
  public String getElementType() {
    if(ceo == null)
      return null; 
    String[] types = ceo.getElementTypes();
    if (types != null && types.length > 0) return types[0];
    return null;
  }
  protected abstract CreateElementOperation getCreateElementOperation();
  
  public String getShowedElementType(TreeDB db) {
    return getShowedElementType(db, getElementType());
  }

  public String getShowedElementType(TreeDB db, String tp) {
    if(ceo == null)
      return null;
    return ceo.getShowedElementType(db, tp);
  }
  @SuppressWarnings("unchecked")
  public <type extends TreeNode> type execute(boolean ignoreTrans, TreeNode par, String id, String templ, boolean nocheck, String type) {
    TreeNode nodeNext = null;
    if(RequalityCNF.shiftPressed) {
      nodeNext = par;
      par = par.getWorkParent();
    }
    return ceo.execute(ignoreTrans, par,id, templ, type, nocheck, nodeNext);
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    try {
      runActionsBeforeRun();
      SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
      if (nodes == null || nodes.fullSelection.size() != 1) return null;
      TreeNode nd = nodes.getFirstNode();
      if(nd == null)
        return null;
      String et = event.getParameter(NODE_TYPE);
      if(et == null)
        et = getElementType();
      return execute(false, nd,null, null, false, et);
    }finally {
      runActionsOnCompleted();
    }
  }
  
  @Override
  public boolean isEnabled() {
    SelectionRecord nodelist = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodelist == null || nodelist.fullSelection.size() != 1) return false;
    TreeNode nds = nodelist.getFirstNode();
    if(nds == null)
      return false;
    TreeDB tree = nds.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(tree.getTracker());
    if(!tsm.canStartTransaction(tree.getLocalName(), tsm.getRevisionInfo(tree)))
      return false;
    return ceo.isEnabled(nds);
  }
  public CreateElementOperation getCreateOP() {
    return ceo;
  }
}
