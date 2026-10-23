package com.unitesk.requality.eclipse.handlers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.VirtualNode;

public class HideInstancerNode extends AbstractHandler {

  private static final TreeNode[] NO_NODES = new TreeNode[0];

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() < 1) return null;
    Collection<TreeNode>nds = nodes.getOrDefault(VirtualNode.TYPE_NAME, 
    		Collections.emptyList());
    if(nds.size()<1)
      return null;
    Set<TreeDB> dbs = new HashSet<>();
      try {
          for (TreeNode node : nds) {
            TreeDB db = node.getTreeDB();
            boolean was = false;
            if(!dbs.contains(db)) {
              if(db.startTransaction("Hide selected elements on "+db.getSemiProjectName()))
              {
                dbs.add(db);
                was = true;
              }//TODO add else when cannot start trans
            }else
              was = true;
            if(was) {
              ((VirtualNode) node).collapse();
              node.commitAttributes();
            }
            //node.getWorkParent().getTreeDB().updateNodeChildren(node.getWorkParent());
          }
          for(TreeDB db:dbs)
            db.commit();
        }catch (Exception ea) {
          RLogger.logError(ea);
          for(TreeDB db:dbs)
            db.rollback();
        }
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() == 0) {
      return false;
    }
    Collection<TreeNode> vnodes = nodes.get(VirtualNode.TYPE_NAME);
    if(vnodes == null || vnodes.size()==0)
      return false;
    TreeDB db = null;
    TreeStateManager tsm = null;
    for (TreeNode node : vnodes) {
      if(db == null || node.getTreeDB()!=db) {
    	  db = node.getTreeDB();
    	  if(tsm == null)
    	    tsm = TreeStateManager.getOne(db.getTracker());
    	  if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
    		  return false;
      }
    }
    return true;
  }
}
