package com.unitesk.requality.core.operation.delete;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.operation.undoredo.UndoRedoMech;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

public class DeleteNodeOperation {
  private static Set<String> avoidDeleteTransactionTypes = new TreeSet<>();

  static {
    avoidDeleteTransactionTypes.add(Document.TYPE_NAME);
    avoidDeleteTransactionTypes.add(DocFolder.TYPE_NAME);
  }

  public static boolean deleteNodes(Collection<TreeNode> snodes, boolean awoidMessages) {
    try{
      Collection<TreeNode>nodes = snodes;
      if (nodes == null || nodes.size() < 1)
        return false;
      boolean canc = false;
      boolean adds = nodes.size() != 1;
      boolean sim = true;
      List<String> startedids = new ArrayList<>();
      Set<String> msges = new HashSet<>();
      Collection<TreeNode> toadd = new LinkedList<>();
      AtomicInteger ti = new AtomicInteger();;
      for (TreeNode node: nodes) 
      {
        TreeDB db = node.getTreeDB();
        if (node.isVirtual() && VirtualNode.TYPE_NAME.equals(node.getWorkParent().getType())) {
          if (((VirtualNode) node.getWorkParent()).isCollapsed()
              && node.getWorkParent().getWorkChildrenUUIDs().size() == 1) {
            
            if (!node.isVirtual())
              node = Instantiator.getRootTree(node.getTreeDB()).getNode(node.getUUId());
            int tig = ti.get();
            if(!toadd.contains(node.getWorkParent()))
              toadd.add(node.getWorkParent());
            ti.set(tig+1);
          }
        }
      }
      if (ti.get()>0) {
        for(TreeNode node:nodes){
           if(node==null)
             continue;
           int t = ti.get();
           if(!toadd.contains(node))
             toadd.add(node);
           ti.set(t+1);
        }
        //nodes.clear();
        nodes=toadd;
      }
  
      TreeNode nd = nodes.iterator().next();
      for (TreeNode node : nodes)
        if(node!=null)
          if (!node.getType().equals(nd.getType())) {
            sim = false;
            break;
          }
      SortedMap<String, IDeleteHandler> dhlist = API.getDeleteHandlers();//TODO move all to api delete handlers
      SortedMap<String, SortedMap<String, TreeNode[]>> list =
          new TreeMap<>();
      Map<String, Map<String, Integer>> lcnt =
          new TreeMap<>();
      //format like so:
      // TreeDB1
      //      -Type
      //          -node1
      //          -node2
      //      -Type2
      //      ...
      // TreeDB2
      // ...
      String overrideTitle = null;
      Iterator<String> hit = dhlist.keySet().iterator();
      StringBuilder ln = new StringBuilder();
      while (hit.hasNext()) {
        String dhname = hit.next();
        IDeleteHandler idh = dhlist.get(dhname);
        TreeNode[] nds = nodes.toArray(new TreeNode[nodes.size()]);
        String[] rmsg = idh.getWarnings(nds);
        String msg = idh.titleOverride(nds);
        if (msg != null) overrideTitle = msg;
        if (rmsg == null) continue;
        for (String m : rmsg) if (!msges.contains(m)) msges.add(m);
        for (TreeNode node : nodes) {
          //TODO remove?
          Map<String, Map<TreeNode, String>> links = RelManager.getRevertLinks(node,true, true);
          int cnt = 0;
          if(links!=null)
            for (String s : links.keySet()) if (!s.startsWith("_")) cnt++;
          if (cnt > 0) {
            if (ln.length() > 0) ln.append(", ");
            ln.append("'");
            ln.append(node.getUserVisibleName(true));
            ln.append("'");
          }
        }
      }
      String linkNodes = ln.toString();
      if (!linkNodes.isEmpty())
        msges.add("Some of selected nodes (" + linkNodes + ") are linked to another nodes.");
      SortedMap<String, TreeDB> bases = new TreeMap<>();
      SortedMap<String, WriteLock> basesLocks = new TreeMap<>();
      SortedMap<String, List<TreeNode>> nodesbytypes = new TreeMap<>();
      for (TreeNode nc : nodes) {
        if (!nodesbytypes.containsKey(nc.getType()))
          nodesbytypes.put(nc.getType(), new LinkedList<TreeNode>());
        nodesbytypes.get(nc.getType()).add(nc);
        String dbname = nc.getTreeDB().getLocalName();
        if (!bases.containsKey(dbname))
          bases.put(dbname, nc.isVRoot() ? Instantiator.getBaseTree(nc.getTreeDB()) : nc.getTreeDB());
        if (!list.containsKey(dbname)) list.put(dbname, new TreeMap<String, TreeNode[]>());
        Iterator<String> dhit = dhlist.keySet().iterator();
        while (dhit.hasNext()) {
          String dhname = dhit.next();
          IDeleteHandler idh = dhlist.get(dhname);
          if (idh.isSuitableFor(nc)) {
            if (!list.get(dbname).containsKey(dhname))
              list.get(dbname).put(dhname, new TreeNode[nodes.size()]);
            int cnt = lcnt
                .getOrDefault(dbname, Collections.emptyMap())
                .getOrDefault(dhname, 0);
            list.get(dbname).get(dhname)[cnt]=nc;
            if(!lcnt.containsKey(dbname))
              lcnt.put(dbname, new TreeMap<>());
            lcnt.get(dbname).put(dhname, cnt+1);
          }
        }
      }
      String name = overrideTitle, warns = "";
      if (name == null)
        name =
            sim ? (adds ? nd.getMultiTypeLabel() : nd.getTypeLabel()) : "Element" + (adds ? "s" : "");
      if (msges.size() > 0) for (String s : msges) warns += "- " + s + "\n";
      //int tag = warns.equals("") ? MessageDialog.QUESTION : MessageDialog.WARNING;
      String fname = name, fwarns = warns;
      boolean del = true;
      if(!awoidMessages)
        del = RMessages.openQuestion("Delete " + fname,
                  "Delete selected "
                      + fname
                      + "?"
                      + (fwarns.equals("")
                          ? ""
                          : "\n\nWarning!\n"
                             + fwarns
                             + "\nIf you still want to proceed click OK, otherwise click Cancel."),
                  false,
                  true);

      Map<CacheStorage, CacheTransactionDescription> started = new ConcurrentHashMap<>();
        
      for(TreeNode node:nodes)
      {
        TreeDB db = node.getTreeDB();
        if(!started.containsKey(db.getCacheStorage())){
          CacheTransactionDescription s = db.getCacheStorage().startCacheTransaction("Delete nodes from "+db.getVisibleName(), 2);
          if(s != null && s.error == null)
            started.put(db.getCacheStorage(), s);
        }
      }
      try{
        if (!del)
          return false;
        {
          Iterator<String> dbnameit = list.keySet().iterator();
          List<UUID> deleted = new ArrayList<>();
          List<String> rowstarted = new LinkedList<>();
          boolean error = false;
          try{
            while (dbnameit.hasNext()) {
              boolean firstrtansaction = true, commited = false;
              String dbname = dbnameit.next();
              bases.get(dbname).startChangeRow();
              rowstarted.add(dbname);
              try {
                Iterator<String> idhit = list.get(dbname).keySet().iterator();
                //first pass - node types with transactions
                while (idhit.hasNext()) {
                  String idname = idhit.next();
                  int cnt = lcnt.getOrDefault(dbname, Collections.emptyMap()).getOrDefault(idname, 0);
                  TreeNode[] pnodes = list.get(dbname).get(idname);
                  boolean avoidtransaction = avoidDeleteTransactionTypes.contains(pnodes[0].getType());
                  if (!avoidtransaction) {
                    if (firstrtansaction && cnt > 0) {
                      boolean started2 = bases.get(dbname).startTransaction("Delete " + fname);
                      if(!started2){
                    	if(!awoidMessages)
                          RMessages.openError(
                                "Delete error",
                                "Operation cannot be performed because some actions are already performing: "+bases.get(dbname).getVisibleName(),
                                "", false);
                    	else
                    	  RLogger.logError("Operation cannot be performed because some actions are already performing: "+bases.get(dbname).getVisibleName());
                        error = true;
                        break;
                      }
                      startedids.add(dbname);
                      firstrtansaction = false;
                    }
                    dhlist.get(idname).process(pnodes, deleted, 
                    				OFlag.NO_TRANSACTION);
                  }
                }
                if (firstrtansaction == false) {
                  bases.get(dbname).commit();
                  startedids.remove(dbname);
                  commited = true;
                }
                if(!error){
                  idhit = list.get(dbname).keySet().iterator();
                  //second pass - node types without transactions
                  while (idhit.hasNext()) {
                    String idname = idhit.next();
                    TreeNode[] pnodes = list.get(dbname).get(idname);
                    boolean avoidtransaction = avoidDeleteTransactionTypes.contains(pnodes[0].getType());
                    dhlist.get(idname).process(pnodes, deleted, 
                    		     	OFlag.NO_TRANSACTION);
                  }
                }
        
              } catch (Exception e) {
            	if(!awoidMessages)
                  RMessages.openError(
                        "Delete error",
                        "Operation is interrupted because of error: "+ e,
                        CoreUtils.getTrace(e.getStackTrace()), false 
                  );
            	else
            	  RLogger.logError("Operation is interrupted because of error: "+ e+"\n"+
            			  CoreUtils.getTrace(e.getStackTrace()));
                e.printStackTrace();
                if (!firstrtansaction) {
                  if (!commited && startedids.size() > 0)
                    for (String s : startedids) bases.get(s).rollback();
                  else {
                    UndoRedoMech.callUndo();
//                    IUndoContext cntx = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
//                    if (PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().canUndo(cntx))
//                      try {
//                        PlatformUI.getWorkbench()
//                            .getOperationSupport()
//                            .getOperationHistory()
//                            .undo(cntx, null, null);
//                      } catch (ExecutionException e1) {
//                        // TODO Auto-generated catch block
//                        e1.printStackTrace();
//                      }
                  }
                }
              }
            }
          }finally{
            for (String s : rowstarted) bases.get(s).stopChangeRow();
          }
        }
      }finally{
        for(Entry<CacheStorage, CacheTransactionDescription> db:started.entrySet()){
          if(db.getKey()!=null && db.getValue()!=null && db.getValue().error == null)
            db.getKey().commit(db.getValue());
        }
      }
      return false;
    }finally{
    }
  }

}
