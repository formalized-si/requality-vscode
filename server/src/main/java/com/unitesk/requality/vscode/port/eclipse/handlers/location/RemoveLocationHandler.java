package com.unitesk.requality.vscode.port.eclipse.handlers.location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.IElementUpdater;
import com.unitesk.requality.vscode.ui.commands.UIElement;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.handlers.requirement.DeleteRequirementHandler;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class RemoveLocationHandler extends AbstractHandler
    implements IDeleteHandler, IElementUpdater {

  public void recUpdateProcessed(TreeNode node, List<UUID> toprocess) {
    toprocess.add(node.getUUId());
    for (UUID uid : node.getWorkChildrenUUIDs()) {
      TreeNode nde = node.getTreeDB().getNode(uid);
      recUpdateProcessed(nde, toprocess);
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void updateElement(UIElement element, Map parameters) {
    SelectionRecord sel = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    int cnt = 0;
    Collection<TreeNode> locs = sel.get(Location.TYPE_NAME);
    if(locs.size()<1)
      return;
    //for (TreeNode node : sel) if (node.getType().equals() cnt++;
    element.setText("Delete Location" + ((locs.size() > 1) ? "s" : ""));
  }

  boolean batch = true;

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null) return false;
    batch = false;
    Collection<TreeNode> locs = nodes.get(Location.TYPE_NAME);
    if(locs.size()>0)
      process(locs.toArray(new TreeNode[locs.size()]), 
    		  new ArrayList<UUID>(), OFlag.NONE);
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null) {
      return false;
    }
    Collection<TreeNode>locs = nodes.get(Location.TYPE_NAME);
    if(nodes.selectionByType.size()>1 || locs==null || locs.size()==0)
      return false;
    TreeDB db = null;
    TreeStateManager tsm = null;
    for (TreeNode node : locs) {
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

  @Override
  public boolean isSuitableFor(TreeNode tn) {
    return tn.getType().equals(Location.TYPE_NAME);
  }

  @Override
  public void process(TreeNode[] nodes, List<UUID> processed, byte flag)
      throws ExecutionException {
    Set<Requirement> req4check = new HashSet<>();

    for (TreeNode node : nodes) {
      if (!isSuitableFor(node)) continue;
      if (processed.contains(node.getUUId())) continue;
      Location loc = (Location) node;
      req4check.addAll(Arrays.asList(loc.getRequirements()));
      loc.delete();
      recUpdateProcessed(node, processed);
      //            List<UUID>toprocess = new ArrayList<UUID>();
      //
      //            CommonRemoveLocation.RemoveLocation(loc, null, null, batch);
      processed.add(loc.getUUId());
    }

    TreeNode[] reqs = new TreeNode[req4check.size()];
    int cnt = 0;
    String strList = "";
    for (Requirement req : req4check) {
      if (req.getLocationQIds().size() == 0 && req.sizeWorkChildren() == 0) {
        reqs[cnt++] = req;
        if (!strList.equals("")) {
          strList += ", ";
        }
        strList += req.getNameOrId();
      }
    }
    if (cnt > 0) {
      //TODO message
      TreeDB db = reqs[0].getTreeDB();
      TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
      if (!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
    	  return;
      if (MessageDialog.openQuestion(
          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
          "Delete Requirement",
          (cnt == 1
                  ? "Requirement \"" + reqs[0].getUserVisibleName(true) + "\" has "
                  : "Requirements " + strList + " have ")
              + "no locations, delete?")) {
        List<UUID> proc = new ArrayList<>();
        TreeDB dba = reqs[0]
                .getTreeDB();
        boolean started = dba
            .startTransaction("Delete Requirement" + (cnt > 1 ? "s" : ""), true);
        if (started) {
          try {
            //TODO replace to reqs.delete
            new DeleteRequirementHandler().process(reqs, proc, OFlag.NO_TRANSACTION);
            if(started) {
              dba.commit();
              started = false;
            }
          } catch (ExecutionException e) {
            RLogger.logError(e);
          }
          finally {
            if(started)
        	  dba.rollback();
          }
        }
      }
    }
    //reqMarker.fireSelectionChange();
  }

  @Override
  public String[] getWarnings(TreeNode[] nodes) {
    return null;
  }

  @Override
  public String titleOverride(TreeNode[] nodes) {
    return null;
  }
}
