package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.VirtualNode;

public class ShowHiddenChildrenForNode extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() < 1) return null;
    Set<TreeDB> dbs = new HashSet<>();
    boolean st = false;
    try {
      for (TreeNode node : nodes.fullSelection) {
        TreeDB db= node.getTreeDB();
        boolean was = false;
        if(!dbs.contains(db)) {
          if(db.startTransaction("Show hidden chilren for project "+db.getSemiProjectName() )) {
            dbs.add(db);
            was = true;
          }
        }else was = true;
        if(was) {
          for (UUID uuid : node.getWorkChildrenUUIDs()) {
            //Instantiator.getVirtualStorage(node.getTreeDB(),Instantiator.DEFAULT_INSTANCE_NAME);
            TreeNode node2 = db.getNode(uuid);
            if (node2.getTreeDB().isHidden(uuid)) {
              VirtualNode.setHidden(node2, false);
              //ti.undeleteHidden(uuid);
            }
            if (node2.getType().equals(VirtualNode.TYPE_NAME)) {
              ((VirtualNode) node2).unCollapse();
              node2.commitAttributes();
              //TODO updatechildren
            } else if (node2.isVirtual()) {
              node2.attrs().removeAttribute(VirtualNode.ATTR_HIDDEN_STATUS);
              node2.commitAttributes();
            }
          }
        //node.getTreeDB().updateNodeChildren(node);
        }
      }
      for(TreeDB db:dbs)
        db.commit();
    } catch (Exception e) {
      for(TreeDB db:dbs)
        db.rollback();
      RLogger.logError(e);
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size()<1) {
      return false;
    }
    Set<TreeDB> dbs = new HashSet<>();
    TreeStateManager tsm = null;
    for (TreeNode node : nodes.fullSelection) {
      TreeDB db = node.getTreeDB();
      if(!dbs.contains(db)) {
        dbs.add(db);
        if(tsm == null)
          tsm = TreeStateManager.getOne(db.getTracker());
        if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
          return false;
      }
      for (UUID uid : node.getWorkChildrenUUIDs()) {
        //TODO another sts?
        if (node.getTreeDB().isHidden(uid)) return true;
        TreeNode nd = node.getTreeDB().getNode(uid);
        if (nd.getType().equals(VirtualNode.TYPE_NAME) && ((VirtualNode) nd).isCollapsed())
          return true;
      }
    }
    return false;
  }
}
