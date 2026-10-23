package com.unitesk.requality.core.operation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class PasteIntoHandler {

  public Object execute(List<TreeNode> parent, boolean wait) {
    if (parent == null || parent.size() != 1) return null;
    return pasteNodes(parent.get(0), null, true, false, false, false, wait);
  }

  protected void showError(String title, String message) {
    //do nothing by default
  }

  protected boolean showConfirm(String title, String desc) {
    return true; //do nothing by default
  }

  public String getNewName(NodeDesc node, List<NodeDesc> sourceNodes, TreeNode parent) {
    String newId = RequalityCLI.getNextId(parent, node.getType(), null);
    //        int copyCount = 1;
    //      while(parent.findChild(newId+copyCount) != null || find(sourceNodes, newId+copyCount))
    //        copyCount++;
    //newId += copyCount;
    return newId;
  }

  public Object pasteNodes(TreeNode parent, boolean copyTree, boolean wait) {
    return pasteNodes(parent, null, copyTree, false, false, false, wait);
  }

  public Object pasteNodes(
      TreeNode pparent,
      TreeNode plastnode,
      boolean copyTree,
      boolean pskipTransaction,
      boolean skipDialogs,
      boolean autoRename,
      boolean wait) {
    //TODO adds wait for some modes or returned promises
    AtomicReference<Object> ar = new AtomicReference<>();
    RunnableWithMonitor rwm = new RunnableWithMonitor() {
      
      @Override
      public void run(IProgressMonitor pm) {
        boolean  skipTransaction = pskipTransaction;
        TreeNode lastnode = plastnode;
        TreeNode parent = pparent;
        TreeDB startedDB = null;
        boolean started = false;
        try{
            List<NodeDesc> sourceNodes = null;
            if (!CopyPasteHelper.getInstance().isEmpty()) {
              TreeLogic l = parent.getTreeDB().getTreeLogic();
              sourceNodes = CopyPasteHelper.getInstance().getNodes();
              List<NodeDesc> toCopy = new ArrayList<>();
              TreeNode par = parent;
        
              String generalsstring = sourceNodes.size() != 1 ? "s" : "";
              if (recFoundWrong(parent, sourceNodes)) {
                if (!skipDialogs)
                  showError(
                      "Paste Element" + generalsstring,
                      "Copied elements contains VirtualNode that will cause recursion if added to "
                          + parent.getUserVisibleName(false));
        
                ar.set("Copied elements contains VirtualNode that will cause recursion if added to "
                    + parent.getUserVisibleName(false));
                return;
              }
              if (lastnode == null)
                for (NodeDesc node : sourceNodes)
                  if (!l.canBeChildOf(node.getType(), parent)
                      && l.canBeChildOf(node.getType(), parent.getWorkParent())) {
                    lastnode = par;
                    parent = par.getWorkParent();
                    break;
                  }
              if (lastnode == null && parent.sizeShownChildren() > 0)
                lastnode = parent.getSortedShownChildren().get(parent.sizeShownChildren() - 1);
              String toRename = "";
              int cnt = 0;
              SortedMap<String, Integer> cntMap = new TreeMap<>();
              for (NodeDesc node : sourceNodes) {
                if (!cntMap.containsKey(node.getType())) cntMap.put(node.getType(), 0);
                cntMap.put(node.getType(), cntMap.get(node.getType()) + 1);
              }
              for (NodeDesc node : sourceNodes) {
                try {
                  if (l.canBeCreatedInExc(node.getType(), parent)) {
                    TreeNode old = parent.findWorkChild(node.getId());
                    if (old != null && !l.isAutoRenamable(node.getType())) {
                      toRename += old.getId() + ", ";
                      cnt++;
                    }
                    toCopy.add(node);
                  } else {
                    {
                      if (!skipDialogs)
                        showError(
                            "Paste Element" + generalsstring,
                            "Element "
                                + node.getId()
                                + " cannot be copied to "
                                + parent.getUserVisibleName(false));
                      ar.set("Element "
                          + node.getId()
                          + " cannot be copied to "
                          + parent.getUserVisibleName(false));
                      return;
                    }
                  }
                } catch (PastingIntoLeafException e) {
                  if (node.getType().equals(TestPurpose.TYPE_NAME)) {
        
                    if (!skipDialogs)
                      showError(
                          "Paste Element" + generalsstring,
                          "Can't paste Test Purpose"
                              + (cntMap.get(node.getType()) > 1 ? "s" : "")
                              + " in "
                              + parent.getTypeLabel()
                              + " with non-Test Purpose child");
                    ar.set("Can't paste Test Purpose"
                        + (cntMap.get(node.getType()) > 1 ? "s" : "")
                        + " in "
                        + parent.getTypeLabel()
                        + " with non-Test Purpose child");
                    return;
                  }
                  if (!skipDialogs)
                    showError(
                        "Paste Element" + generalsstring,
                        "Can't paste "
                            + CoreUtils.splitWords(node.getType())
                            + (!node.getType().endsWith("s") && cntMap.get(node.getType()) > 1 ? "s" : "")
                            + " in "
                            + parent.getTypeLabel()
                            + " with Test Purpose child");
                  ar.set("Can't paste "
                      + CoreUtils.splitWords(node.getType())
                      + (!node.getType().endsWith("s") && cntMap.get(node.getType()) > 1 ? "s" : "")
                      + " in "
                      + parent.getTypeLabel()
                      + " with Test Purpose child");
                  return;
                }catch (ProjectNotLoadedException e) {
                  if (!skipDialogs)
                    showError(
                        "Paste Element" + generalsstring,
                        "Project "
                            + parent.getTreeDB().getVisibleName()
                            + " is not loaded");
                  ar.set("Project "
                      + parent.getTreeDB().getVisibleName()
                      + " is not loaded");
                  return;
                }
              }
              String parentName = parent.getUserVisibleName(true);
              if (parentName.equals(""))
                parentName = parent.getQualifiedId().substring(0, parent.getQualifiedId().indexOf("/"));
              String purposes = "", adds = cnt > 1 ? "s" : "";
              byte purpcnt = 0;
              if (!toRename.equals("")) {
                purposes +=
                    "\n- Element"
                        + adds
                        + " with id"
                        + adds
                        + " "
                        + toRename.substring(0, toRename.length() - 2)
                        + " already exist"
                        + (adds.equals("") ? "s" : "")
                        + " in "
                        + parent.getUserVisibleName(true)
                        + ".";
                purpcnt++;
              }
              boolean askrename = true;
              for (int i = 0; i < toCopy.size() && askrename; i++) {
                if (l.isAutoRenamable(toCopy.get(i).getType())) continue;
                for (int j = i + 1; j < toCopy.size(); j++)
                  if (toCopy.get(i).getId().equals(toCopy.get(j).getId())) {
                    if (l.isAutoRenamable(toCopy.get(i).getType())) continue;
                    purposes += "\n- Selection contains elements with same ids.";
                    purpcnt++;
                    cnt += 2;
                    askrename = false;
                    break;
                  }
              }
              adds = cnt > 1 ? "s" : "";
              if (!purposes.equals(""))
                if (!autoRename
                    && !skipDialogs
                    && !showConfirm(
                        "Paste Element" + generalsstring,
                        "Copied element"
                            + (generalsstring.equals("") ? " has" : generalsstring + " have")
                            + " id conflict"
                            + (purpcnt > 1 ? "s" : "")
                            + ":\n"
                            + purposes
                            + "\n"
                            + "\nConflicting element"
                            + adds
                            + " will be renamed. If you still want to proceed click OK, otherwise click Cancel.")) {
                  ar.set("Copy was interrupted - id conflict");
                  return;
                }
              started = false;
              if (!skipTransaction) {
                started = parent.getTreeDB().startTransaction("Paste Element" + generalsstring, true);
                if (!started) {
                  ar.set("Some transaction was already started");
                  return;
                }
              }
              try {
                startedDB = !skipTransaction?parent.getTreeDB():null;
                boolean flaglast = false;
                if (parent.sizeShownChildren() > 0
                    && lastnode == parent.getSortedShownChildren().get(parent.sizeShownChildren() - 1))
                  flaglast = true;
                List<TreeNode> list = new ArrayList<>();
                for (int i = 0; i < toCopy.size(); i++) {
                  NodeDesc nodeDesc = toCopy.get(i);
                  //FIDUpdater.initNodeDesc(parent.getTreeDB(), nodeDesc.getUUId(), nodeDesc.getAttributes());
                  String newId = null;
                  if (!l.isChildIdValid(parent, nodeDesc.getId())) {
                    if (l.isAutoRenamable(nodeDesc.getType())) {
                      if (nodeDesc.getType().equals(Comment.TYPE_NAME))
                        newId = RequalityCLI.getNextId(parent, nodeDesc.getType(), Comment.TYPE_NAME + " ");
                      else newId = RequalityCLI.getNextId(parent, nodeDesc.getType(), null);
                    } else {
                      newId = getNewName(nodeDesc, toCopy, parent);
                    }
                  }
                  String oldid = nodeDesc.getId();
                  if (newId != null) nodeDesc.setId(newId);
                  if (!parent
                      .getTreeDB()
                      .getTreeLogic()
                      .canBeCreatedIn(nodeDesc.getType(), nodeDesc.getId(), parent)) {
                    if (started){
                      skipTransaction = true;
                      started = false;
                      parent.getTreeDB().rollback();
                    }
                    if (!skipDialogs)
                      showError(
                          "Paste Element" + generalsstring, "Selected elements cannot be pasted together.");
                    //return Status.CANCEL_STATUS;
                    ar.set("Selected elements cannot be pasted together.;");
                    return;
                  }
                  if (parent.getTreeDB().getSortingSettings().getMethod()==SortingMethod.BY_INDEX)
                    nodeDesc.setAttribute(
                        new AttributeDefinition(nodeDesc.getUUId(),TreeDB.INDEX_ATTRIBUTE,
                            AttributeAvailability.LOCAL,new ConstValueProvider(AttributeType.STRING,
                                Index.getNextIndex(
                                    nodeDesc.getType(),
                                    parent,
                                    lastnode,
                                    nodeDesc.getType().equals("TestStep"))
                                .toString()),Flag.HIDDEN));
          
                  TreeNode newnode = nodeDesc.createNode(parent.getTreeDB(), parent.getUUId(), true, false);
                  if (flaglast && parent.sizeShownChildren() > 0) {
                    lastnode = parent.getSortedShownChildren().get(parent.sizeShownChildren() - 1);
                  } else {
                    lastnode = newnode;
                  }
                  nodeDesc.setId(oldid);
                  list.add(newnode);
                }
                startedDB = null;
                ar.set(list.toArray(new TreeNode[0]));
                if(started) {
                  parent.getTreeDB().commit();
                  started = false;
                }
              }finally {
                if(started){
                  parent.getTreeDB().rollback();
                  started = false;
                }
              }
              //return list.toArray(new TreeNode[0]);
            }
            //return null;
        }finally{
            if(started && startedDB!=null)
                startedDB.rollback();
        }
      }
    };
    if(wait)
      rwm.run(new ConsoleProgressMonitor());
    else
      JobManager.jm.startJob("Past nodes to "+pparent.getLabel()+(plastnode!=null?("after "+plastnode.getLabel()):""),
        rwm, false);
	return ar.get();
  }

  public boolean checkInto(SelectionRecord nodes) {
    if (nodes == null || nodes.fullSelection.size() != 1)
      return false;
    TreeNode target = nodes.getFirstNode();
    return checkInto(target);
  }
  public boolean checkInto(TreeNode target) {
    if(target == null)
      return false;
    if (CopyPasteHelper.getInstance().isEmpty()) 
      return false;
    List<NodeDesc> sourceNodes = CopyPasteHelper.getInstance().getNodes();
    TreeLogic l = target.getTreeDB().getTreeLogic();
    for (NodeDesc node : sourceNodes) if (!l.canBeCreatedIn(node.getType(), target)) return false;
    if (recFoundWrong(target, sourceNodes)) {
      return false;
    }
    return true;
  }

  private boolean recFoundWrong(TreeNode target, Collection<NodeDesc> sourceNodes) {
    String qid = target.getQualifiedId() + "/";
    for (NodeDesc nd : sourceNodes)
      if (nd.getType().equals(VirtualNode.TYPE_NAME))
        if (nd.getAttributes().get(VirtualNode.ATTR_TARGET) != null) {
          String targ =
              nd.getAttributes().get(VirtualNode.ATTR_TARGET).getRawValue().getValue().toString();
          if (targ != null) {
            TreeNode ntarg = target.getTreeDB().getNode(UUID.fromString(targ));
            if (ntarg != null && (ntarg.getQualifiedId() + "/").startsWith(qid)) return true;
          }
        }
    return false;
  }

  public boolean isEnabled(SelectionRecord nodes) {
    if(nodes == null ||nodes.fullSelection.size()!=1)
      return false;
    TreeNode node = nodes.getFirstNode();
    if(node == null)
      return false;
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
      return false;
    return checkInto(nodes);
  }
}
