package com.unitesk.requality.eclipse.views.compare;

import java.util.List;
import org.eclipse.compare.ResourceNode;
import org.eclipse.core.resources.IFile;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.CoreUtils;

public class TreeNodeTypedElement extends ResourceNode {
  private TreeNode node;
  public TreeNodeTypedElement(TreeNode nde) throws LockIsBusy {
    super((IFile)EclipseTracker.getResourceByNode(nde, LockType.LOCK_WAIT));
    this.node=nde;
  }
  @Override
  public Object[] getChildren() {
    List<TreeNode>nds = node.getWorkChildren();
    Object[]ret = new Object[nds.size()];
    int i = 0;
    for(TreeNode nd: nds)
      try {
        ret[i++] = new TreeNodeTypedElement(nd);
      } catch (LockIsBusy e) {
        RLogger.logDebug(CoreUtils.getTrace(e),7);
      }
    return ret;
  }
  public TreeNode getTreeNode(){
    return node;
  }
  
}
