package com.unitesk.requality.core.operation;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class PasteHandler extends PasteIntoHandler {

  public Object execute(
      TreeNode parent,
      TreeNode lastNode,
      boolean copyTree,
      boolean skipTransaction,
      boolean skipDialogs,
      boolean autoRename,
      boolean waitAnswer) {
    AtomicReference<Object> ar = new AtomicReference<>();
    RunnableWithMonitor rwm = new RunnableWithMonitor() {

      @Override
      public void run(IProgressMonitor pm) {
        try{
          boolean se = checkSibling(lastNode), si = checkInto(parent);
          if(CopyPasteHelper.getInstance().getNodes() == null || CopyPasteHelper.getInstance().getNodes().size()==0)
              return ;
          if (lastNode != null)
            if (!se) return ;
            else{
              ar.set(pasteNodes(parent, lastNode, copyTree, skipTransaction, skipDialogs, autoRename, waitAnswer));
              return ;
            }
          //String tp = CopyPasteHelper.getInstance().getNodes().get(0).getType();
          if (!si && checkSibling(parent)){
            ar.set(pasteNodes(
                parent.getWorkParent(), parent, copyTree, skipTransaction, skipDialogs, autoRename, waitAnswer));
            return ;
          }
          if (si){
            ar.set(pasteNodes(parent, null, copyTree, skipTransaction, skipDialogs, autoRename, waitAnswer));
          }
          return ;
        }
        finally{
          synchronized (PasteHandler.this) {
            PasteHandler.this.notify(); 
          }
        }
      }
    };
    if(waitAnswer) {
      rwm.run(new ConsoleProgressMonitor());
      return ar.get();
    }
    else
      JobManager.jm.startJob("Paste nodes to "+parent.getUserVisibleName()+(lastNode==null?"":" after "+lastNode.getUserVisibleName()), rwm, false);
    return null;
  }

  public boolean checkInto() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    return checkInto(nodes);
  }
  public static boolean checkSibling(TreeNode node) {
    if(node == null)
      return false;
    TreeNode nodeAfter = node;
    node = node.getWorkParent();
    if(node == null)
      return false;
    if (node.getType().equals(Requirement.TYPE_NAME))
      return !node.getQualifiedId().equals(Requirement.getTypeRootQId());
    List<NodeDesc> nodes = CopyPasteHelper.getInstance().getNodes();
    if (CopyPasteHelper.getInstance().isEmpty()) return false;
    for (NodeDesc nd : nodes)
      if (!node.getTreeDB().getTreeLogic().canBeCreatedIn(nd.getType(), node))
        return false;
    
    return true;
  }
  public static boolean checkSibling() {
    SelectionRecord parent = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (parent == null || parent.fullSelection.size() != 1) return false;
    TreeNode node = parent.getFirstNode();
    return checkSibling(node);
  }
}
