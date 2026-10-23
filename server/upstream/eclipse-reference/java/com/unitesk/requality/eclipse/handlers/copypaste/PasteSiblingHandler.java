package com.unitesk.requality.eclipse.handlers.copypaste;

import java.util.List;

import org.eclipse.core.commands.ExecutionException;

import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.CopyPasteHelper;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;

public class PasteSiblingHandler extends PasteIntoHandler {

  @Override
  public Object execute(org.eclipse.core.commands.ExecutionEvent event) throws ExecutionException {
    SelectionRecord parent = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (parent == null || parent.fullSelection.size() != 1) return null;
    TreeNode node = parent.getFirstNode();
    if(node == null)
      return null;
    return pasteNodes(node.getShownParent(), node, true, false, false, false, false);
  };

  public static boolean checkSibling() {
    SelectionRecord parent = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (parent == null || parent.fullSelection.size() != 1) return false;
    TreeNode node = parent.getFirstNode();
    if(node == null)
      return false; 
    List<NodeDesc> nodes = CopyPasteHelper.getInstance().getNodes();
    if (CopyPasteHelper.getInstance().isEmpty()) return false;
    for (NodeDesc nd : nodes)
      if (!node.getTreeDB().getTreeLogic().canBeCreatedIn(nd.getType(), node.getShownParent()))
        return false;

    if (node.getWorkParent().isRoot())
      return false;
    return true;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if(nodes == null ||nodes.fullSelection.size()!=1)
      return false;
    TreeNode node = nodes.getFirstNode();
    if(node == null)
      return false;
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
      return false;
    
    return checkSibling();
  }
}
