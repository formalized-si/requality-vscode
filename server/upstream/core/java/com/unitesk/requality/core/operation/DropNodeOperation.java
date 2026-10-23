package com.unitesk.requality.core.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.core.tree.ITreeLogic.WrongProjectException;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.message.RMessages;

public class DropNodeOperation {

  private static String[] allowedAlt =
      new String[] {
        Requirement.TYPE_NAME, TextNode.TYPE_NAME, Comment.TYPE_NAME, TestPurpose.TYPE_NAME
      };
  public static boolean execute(TreeDB db, List<TreeNode> srcNodes, TreeNode aTarget, boolean alt, boolean shift) {
    List<TreeNode> workList = new ArrayList<>();
    SortedMap<UUID, String> quids = new TreeMap<>();
    for (TreeNode node : srcNodes) {
      quids.put(node.getUUId(), node.getQualifiedId());
      //it is not possible to move nodes over different treedbs
      if (node.getTreeDB() != db && (node.getTreeDB() != Instantiator.getRootTree(db)))
        return false;
    }
    TreeNode basetarget = aTarget, target = basetarget;
    TreeNode lastNode = null;
    SortedMap<String, Integer> cnt = new TreeMap<>();
    for (int ii = 0; ii < srcNodes.size(); ii++) {
      TreeNode node = srcNodes.get(ii);
      if (alt) {
        boolean validSt = false;
        for (String s : allowedAlt) if (s.equals(node.getType())) validSt = true;
        if (!validSt) return false;
      }
      boolean add = true;
      for (int j = 0; j < ii; j++) {
        TreeNode n2 = srcNodes.get(j);
        if (n2 != node
            && (quids.get(node.getUUId()) + "/").contains((quids.get(n2.getUUId())) + "/")) {
          add = false;
          break;
        }
      }
      if (add) {
        //TODO check
        if (node.isVirtual() && VirtualNode.TYPE_NAME.equals(node.getWorkParent().getType())) {
          if (((VirtualNode) node.getWorkParent()).isCollapsed()
              && node.getWorkParent().getWorkChildrenUUIDs().size() == 1)
            node = node.getWorkParent();
        }
        if (!node.isVirtual())
          node = Instantiator.getRootTree(node.getTreeDB()).getNode(node.getUUId());
        workList.add(node);
        if (!cnt.containsKey(node.getType())) cnt.put(node.getType(), 0);
        cnt.put(node.getType(), cnt.get(node.getType()) + 1);
      }
      TreeLogic l = node.getTreeDB().getTreeLogic();
      if ((!l.canBeChildOf(node.getType(), basetarget.getType())
              && l.canBeChildOf(node.getType(), basetarget.getWorkParent()))
          || shift) {
        lastNode = basetarget;
        target = basetarget.getWorkParent();
      }
    }
    if (lastNode == null && target.sizeShownChildren() > 0)
      lastNode = target.getSortedShownChildren().get(target.sizeShownChildren() - 1);
    if (workList.size() == 0) 
      return false;
    TreeLogic l = workList.get(0).getTreeDB().getTreeLogic();
    String generalssting = workList.size() > 1 ? "s" : "";
    String op = "Move";
    if (alt) op = "Reuse";
    for (TreeNode srcNode : workList) {
      boolean adds = cnt.get(srcNode.getType()) > 1;
      try {
        if (!l.canBeCopiedToExc(srcNode, target, true)) {
         RMessages.openError(
               op + " Element" + generalssting,
               (adds ? srcNode.getMultiTypeLabel() : srcNode.getTypeLabel())
                  + " cannot be "
                  + op.toLowerCase()
                  + "d to '"
                  + target.getUserVisibleName(true)
                  + "'","",false);
          return false;
        }
      } catch (PastingIntoLeafException e) {
          RMessages.openError(
            op + " Element" + generalssting,
            "Can't "
                + op.toLowerCase()
                + " "
                + (adds ? srcNode.getMultiTypeLabel() : srcNode.getTypeLabel())
                + " to "
                + target.getTypeLabel()
                + " with "
                + (srcNode.getType().equals(TestPurpose.TYPE_NAME) ? "non-" : "")
                + "Test Purpose child", "", false);
        return false;
      } catch (WrongProjectException e) {
        RMessages.openError(
          op + " Element" + generalssting,
          op.replace("e", "").toLowerCase() + "ing elements between projects is prohibited",
          "", false);
        return false;
      } catch (SameIdExistsInChildren e) {
        //not possible here
      } catch (IdEmpty e) {
        RMessages.openError(
          op + " Element" + generalssting,
          "Element id is empty","",false);
        return false;
      } catch (IdContainsWrongChars e) {
        RMessages.openError(
          op + " Element" + generalssting,
          "Element id contains wrong characters :" + e.getWrongCharacter(),
          "", false);
        return false;
      } catch (ProjectNotLoadedException e) {
        RMessages.openError(
          op + " Element" + generalssting,
          "Project " +db.getVisibleName()+" is not loaded", "", false);
        return false;
      }
      for (TreeNode node : new DeepFirstTreeWalker(srcNode, true, true, true)) {
        if (node.getQualifiedId().equals(target.getQualifiedId())) {
          return false;
        }
      }
      if (target.getTreeDB().getTreeLogic().checkVNodeRecursion(srcNode, target)) {
          //target.getTreeDB().rollback();
        RMessages.openError(
          op + " Element" + generalssting,
          "Some of selected elements contains Virtual Node that points to target or its parents. So "
              + op.toLowerCase().substring(0, op.length() - 1)
              + "ing it will cause recursion and will be prohibited. ",
              "",false);
        return false;
      }
    }
    if (!alt) {
      String fromNames = "";
      List<Integer> renIndex = new ArrayList<>();
      int ccc = 0;
      for (int i = 0; i < workList.size(); i++)
        if (!l.isChildIdValid(target, workList.get(i), workList.get(i).getId())) {
          renIndex.add(i);
          if (!workList
              .get(i)
              .getTreeDB()
              .getTreeLogic()
              .isAutoRenamable(workList.get(i).getType())) {
            fromNames += workList.get(i).getId() + ", ";
            ccc++;
          }
        }
      String purposes = "", adds = ccc > 1 ? "s" : "";
      byte purpcnt = 0;
      if (!fromNames.equals("")) {
        purposes +=
            "\n- Element"
                + adds
                + " with id"
                + adds
                + " "
                + fromNames.substring(0, fromNames.length() - 2)
                + " already exist"
                + (adds.equals("") ? "s" : "")
                + " in "
                + target.getUserVisibleName(true)
                + ".";
        purpcnt++;
      }
      boolean askrename = true;
      for (int i = 0; i < workList.size() && askrename; i++) {
        if (l.isAutoRenamable(workList.get(i).getType())) continue;
        for (int j = i + 1; j < workList.size(); j++)
          if (workList.get(i).getId().equals(workList.get(j).getId())) {
            if (l.isAutoRenamable(workList.get(i).getType())) continue;
            purposes += "\n- Selection contains elements with same ids.";
            purpcnt++;
            ccc += 2;
            askrename = false;
            break;
          }
      }
      //Shell shell = Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().shell;
      if (!purposes.equals(""))
        if (!RMessages.openQuestionOkCancel(
            op + " Element" + generalssting,
            op
                + "d element"
                + (generalssting.equals("") ? " has" : generalssting + " have")
                + " id conflict"
                + (purpcnt > 1 ? "s" : "")
                + ":\n"
                + purposes
                + "\n"
                + "\nConflicting element"
                + adds
                + " will be renamed. If you still want to proceed click OK, otherwise click Cancel.", false, true)) {
          return false;
        }
    }
    TreeDB tdb = workList.get(0).getTreeDB();
    if (alt) {
      //TODO look
      //tdb = Instantiator.getVirtualTreeDB(tdb,Instantiator.DEFAULT_INSTANCE_NAME);
    }
    boolean st = tdb.startTransaction(op + " Element" + generalssting, true);
    if (st) {
      try{
        for (int i = 0; i < workList.size(); i++)
          if(alt)
          {
            if(!(new ReuseOperaion(workList.get(i), target, lastNode, true).execute())){
              target.getTreeDB().rollback();
              RMessages.openError(
                  op + " Element" + generalssting,
                  "Selected elements cannot be " + op.toLowerCase() + "d together. ",
                  "", false);
              return false;
            }
          }else{
            if (!target.getTreeDB().getTreeLogic().canBeChildOf(workList.get(i), target, false)){
              target.getTreeDB().rollback();
              RMessages.openError(
                  op + " Element" + generalssting,
                  "Selected elements cannot be " + op.toLowerCase() + "d together. ",
                  "", false);
              return false;
            }
            TreeNode node = workList.get(i);
            MoveOperationWithAutorename mowg = new MoveOperationWithAutorename(workList, node, target, lastNode, true); 
            mowg.excecute();
            if(mowg.newId!=null){
              node.setId(mowg.newId);
              node.commitAttributes();
            }
            
          }
        if(st){
          tdb.commit();
          st = false;
        }
        return true;
      }
      catch(Exception e){
        RLogger.logError(e);
      }
      finally {
        if (st) tdb.rollback(); 
      }
    }
    return false;
  
  }

}
