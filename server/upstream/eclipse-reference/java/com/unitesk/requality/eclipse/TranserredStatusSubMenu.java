package com.unitesk.requality.eclipse;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.filter.ITreeNodesFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.views.actions.NodeProcessingAction;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.Requirement.TransferStatus;

public class TranserredStatusSubMenu extends org.eclipse.ui.actions.CompoundContributionItem {
  public TranserredStatusSubMenu() {}

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection();
    if (nodes == null || nodes.fullSelection.size()<1) return false;
    boolean was = false;
    for(TreeNode node: nodes.fullSelection) {  
      TreeDB db = node.getTreeDB();
      TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
      if (db.isDisposed()||tsm.getState(db.getLocalName(),tsm.getRevisionInfo(db))!=TreeState.READY)
        return false;
      was = true;
    }
    if(!was)
      return false;
    return someElementsTranferedFilter.select(nodes).fullSelection.size() > 0;
  };

  @Override
  public boolean isVisible() {
    return isEnabled();
  };

  public static ITreeNodesFilter someElementsTranferedFilter =
      new ITreeNodesFilter() {
        @Override
        public SelectionRecord select(SelectionRecord nodes) {
          Collection<TreeNode> altList = null;
          for (TreeNode nd : nodes.fullSelection) {
            Attribute transf = nd.attrs().getAttribute(Requirement.ATTR_TRANSF_STATUS);
            if(transf != null) {
              if(altList == null || altList.isEmpty())
                altList = new ConcurrentLinkedDeque<>();
              altList.add(nd);
            }else
              if(altList == null)
                altList = Collections.emptyList();
          }
          if(altList!=null)
            return new SelectionRecord(altList);
          return nodes;
        }
      };
  private ITreeNodeFilter elementTranferedFilter =
      new ITreeNodeFilter() {
        @Override
        public boolean select(TreeNode node) {
          return node.attrs().getAttribute(Requirement.ATTR_TRANSF_STATUS) != null;
        }
      };

  private ITreeNodeFilter getTSChangeAction(final Requirement.TransferStatus minstatus) {
    return new ITreeNodeFilter() {
      @Override
      public boolean select(TreeNode node) {
        if (minstatus == null) node.attrs().removeAttribute(Requirement.ATTR_TRANSF_STATUS);
        else ((Requirement) node).setTransferProblem(minstatus);
        return true;
      }
    };
  }

  @Override
  protected IContributionItem[] getContributionItems() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection();
    TransferStatus maxstatus = TransferStatus.FAILED, minstStatus = TransferStatus.COMPLETE;
    boolean hasnoattr = false;
    if (nodes == null || nodes.fullSelection.size() < 1) return new IContributionItem[0];
    for (TreeNode node : nodes.fullSelection) {
      Attribute a = node.attrs().getAttribute(Requirement.ATTR_TRANSF_STATUS);
      if (a == null) {
        maxstatus = TransferStatus.COMPLETE;
        hasnoattr = true;
        continue;
      }
      TransferStatus ts = Requirement.TransferStatus.valueOf((String) a.getValue());
      if (ts.isBetterThan(maxstatus)) maxstatus = ts;
      if (minstStatus.isBetterThan(ts)) minstStatus = ts;
    }
    if (!hasnoattr && maxstatus != minstStatus)
      maxstatus = Requirement.TransferStatus.values()[maxstatus.ordinal() - 1];
    List<ContributionItem> items = new LinkedList<>();
    for (TransferStatus ts : Requirement.TransferStatus.values())
      if (ts.isBetterThan(maxstatus))
        items.add(
            new ActionContributionItem(
                new NodeProcessingAction(
                    "Set to " + ts.toString().toLowerCase(),
                    elementTranferedFilter,
                    someElementsTranferedFilter,
                    getTSChangeAction(ts))));
    if (someElementsTranferedFilter.select(nodes).fullSelection.size() > 0)
      items.add(
          new ActionContributionItem(
              new NodeProcessingAction(
                  "Remove",
                  elementTranferedFilter,
                  someElementsTranferedFilter,
                  new ITreeNodeFilter() {
                    @Override
                    public boolean select(TreeNode node) {
                      node.attrs().removeAttribute(Requirement.ATTR_TRANSF_STATUS);
                      node.commitAttributes();
                      return true;
                    }
                  })));
    return items.toArray(new IContributionItem[0]);
  }
}
