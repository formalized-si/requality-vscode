package com.unitesk.requality.core.operation.delete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.message.RMessages;

public class DeleteLocationOperation {
  private boolean ignoreGui;
  private static DeleteLocationOperation one = new DeleteLocationOperation();
  private DeleteLocationOperation() {
    
  }
  public static DeleteLocationOperation getOne() {
    return one;
  }
  public <type extends TreeNode> type execute(boolean ignoreTrans, Location loc) {
    if(loc == null)
      return null;
    Requirement[] req4check =  loc.getRequirements();
    AtomicBoolean dia = new AtomicBoolean(false);
    if(!ignoreGui&&!RMessages.openQuestion(
            "Delete Location",
            "Delete selected Location? ("+loc.getDescription()+")",true,true)) 
      return null;//TODO test descr
    TreeDB db = loc.getTreeDB();
    if (!db.startTransaction("Delete Location", true)) {
      if(!ignoreGui)
        RMessages.openError(
          "Delete Location Error",
          "Some actions are already performing","",false);
      return null;
    }
    try {
      if (loc != null) 
        loc.delete();
      else{
        //TODO not supported in now model
      }
      //       try{
      //          if(req4check.length>0){
      //               CommonRemoveLocation.RemoveLocation(loc, Arrays.asList(req4check), reqMarker, true);
      //
      //          }
      //          else if(req4check.length==0)
      //              CommonRemoveLocation.RemoveLocation(loc, null, reqMarker, true,locQId,reqMarker.getDocument().getTreeDB());
      //          loc.getTreeDB().commit();
      //     }
      //  catch(Exception e){
      //      RLogger.logError(e);
      //      loc.getTreeDB().rollback();
      //  }
    }finally {
      db.commit();//TODO mb end after?
    }
    Arrays.sort(req4check, getReqComparator(db));
    TreeNode[] reqs = new TreeNode[req4check.length];
    int cnt = 0;
    String strList = "";
    if (req4check != null)
      for (Requirement req : req4check) {
        if (req.getLocations(false).size() == 0 && req.sizeWorkChildren() == 0) {
          reqs[cnt++] = req;
          if (!strList.equals("")) {
            strList += ", ";
          }
          strList += req.getNameOrId();
        }
      }
    if (cnt > 0) {
      //TODO add delayed job waiting for time after transaction
      TreeDB dba = reqs[0].getTreeDB();
      TreeStateManager tsm = TreeStateManager.getOne(dba.getTracker());
      if (!tsm.canStartTransaction(dba.getLocalName(), tsm.getRevisionInfo(dba)))
    	return null;
      dia.set(false);
      final String fstrList = strList;
      final int fnc = cnt;
      if(!ignoreGui&&RMessages.openQuestion(
          "Delete Requirement",
          (fnc == 1
                  ? "Requirement \"" + reqs[0].getUserVisibleName(true) + "\" has "
                  : "Requirements " + fstrList + " have ")
              + "no locations, delete?",true,true)){
        List<UUID> proc = new ArrayList<>();
        //TreeDB db = reqs[0].getTreeDB();
        boolean started = false;
        try {
          started = db.startTransaction("Delete Requirement" + (cnt > 1 ? "s" : ""), true);
          if (started) {
            DeleteRequirementOperation.getOne().process(reqs, proc);
            db.commit();
          }
        }catch(Exception e) {
          if(started)
            db.rollback();
        }
      }
    } else{
      //TODO sel change?
    }
    return (type) loc;
  }
  public static Comparator<TreeNode> getReqComparator(TreeDB db) {
    return TreeNodeComparator.getComparator(db);
  }
  public void setIgnoreGUI(boolean b) {
    ignoreGui = b;
  }
}
