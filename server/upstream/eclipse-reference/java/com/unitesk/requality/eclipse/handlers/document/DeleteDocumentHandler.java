/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.eclipse.handlers.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IDeleteHandler;
import com.unitesk.requality.core.operation.RemoveRequirementLocationCollection;
import com.unitesk.requality.core.operation.delete.DeleteLocationOperation;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.storage.cache.CacheTransactionDescription;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.ui.dialogs.BigMessageDialog;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;

public class DeleteDocumentHandler extends AbstractHandler implements IDeleteHandler {
  private static final String
      WARN_LOCATIONS = "Selected Document (one or more) contains marked Locations.",
      WARN_DOCUMENTS = "Document and Document Folder deletion cannot be undone.";
  private boolean isDocUpdating = false;
  public String titleOverride(TreeNode[] nodes) {
    for (TreeNode node : nodes) {
      if(node == null)
        continue;
      if (!DocFolder.TYPE_NAME.equals(node.getType())) return null;
    }
    return "Document Folder";
  }

  public void recUpdateProcessed(TreeNode node, List<UUID> processed) {
    processed.add(node.getUUId());
    for (UUID uid : node.getWorkChildrenUUIDs()) {
      TreeNode nde = node.getTreeDB().getNode(uid);
      recUpdateProcessed(nde, processed);
    }
  }

  //start transaction and commit, please!
  private void recDelNode(Set<Document>docrem, RemoveRequirementLocationCollection docremloc, TreeNode node, List<UUID> processed, List<TreeNode>remOrder) {
    if (processed.contains(node.getUUId())) return;
    if (node instanceof Document) {
      deleteDocument(docrem,docremloc,(Document) node, processed,remOrder);
    }

    if (node instanceof DocFolder) {
      for(TreeNode chi:node.getWorkChildren())
        recDelNode(docrem, docremloc,chi, processed,remOrder);
      remOrder.add(node);
    }
  }

  List<Requirement> reqs_to_delete;
  //start transaction and commit, please!
  private void deleteDocument(Set<Document> docrem, RemoveRequirementLocationCollection docremloc, Document doc, List<UUID> processed, List<TreeNode> remOrder) {
    if (doc.attrs().getAttribute(Document.ATTR_IS_UPDATING) != null) {
      isDocUpdating = true;
      docrem.add(doc);
      doc.attrs().removeAttribute(Document.ATTR_IS_UPDATING);
      doc.commitAttributes();
    }
    removeLocationsFromRequirement(docremloc, doc, processed);

    TreeDB db = doc.getTreeDB();
    DocFolder folder = (DocFolder) db.getNode(Document.getTypeRootQId(), Path.QID);

    if (isDocUpdating) {
      Document anotherDoc = findStarredDoc(folder, db);
      if (anotherDoc != null) {
        docrem.add(anotherDoc);
        anotherDoc.attrs().removeAttribute(Document.ATTR_IS_UPDATING);
        anotherDoc.commitAttributes();
      }
    }
    remOrder.add(doc);
  }

  public Document findStarredDoc(DocFolder folder, TreeDB db) {
    Iterator<TreeNode> it = folder.workIterator();
    while (it.hasNext()) {
      TreeNode next = it.next();
      if (next instanceof DocFolder) {
        return findStarredDoc((DocFolder) next, db);
      } else if (next instanceof Document) {
        Document doc = (Document) next;
        if (doc.attrs().getAttribute(Document.ATTR_IS_UPDATING) != null) {
          return doc;
        }
      }
    }
    return null;
  }

  private boolean folderHasDocumentWithLocation(DocFolder folder) {
    if (folder.sizeWorkChildren() == 0) {
      return false;
    }

    for (int i = 0; i < folder.sizeWorkChildren(); i++) {
      TreeNode node = folder.getWorkChild(i);
      if (node instanceof DocFolder) {
        if (folderHasDocumentWithLocation((DocFolder) node)) {
          return true;
        }
      }
      if (node instanceof Document) {
        Document doc = (Document) node;
        if (doc.sizeLocationsChildren() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  //start transaction and commit, please!
  private void removeLocationsFromRequirement(RemoveRequirementLocationCollection rrlc, Document doc, List<UUID> processed) {
    TreeDB db = doc.getTreeDB();
    for (int i = 0; i < doc.sizeWorkChildren(); i++) {
      Location l = (Location) doc.getWorkChild(i);
      for(Requirement r:l.getRequirements())
        rrlc.registerRemoveLocation(r, l.getUUId());//TODO only uuid?
      //docremloc.add(doc.getWorkChild(i).getUUId());
    }
  }

  @Override
  public boolean isSuitableFor(TreeNode tn) {
    return tn.getType().equals(Document.TYPE_NAME) || tn.getType().equals(DocFolder.TYPE_NAME);
  }
  private boolean dires;
  @Override
  public void process(TreeNode[] nodes, List<UUID> processed, byte flag)
      throws ExecutionException {
    int todelete = 0;
    for (TreeNode node : nodes) {
      if(node == null)
        continue;
      if (processed.contains(node.getUUId())) continue;
      if (!isSuitableFor(node) || !node.getTreeDB().getTreeLogic().canBeDeleted(node)) continue;
      todelete++;
    }
    if (todelete == 0) {
      return;
    }

    ConcurrentSkipListSet<Document> docrem = new ConcurrentSkipListSet<Document>();
    RemoveRequirementLocationCollection docremloc = new RemoveRequirementLocationCollection();
    reqs_to_delete = new LinkedList<>();
    Set<TreeDB> dbs = new HashSet<>();
    List<TreeNode> remList = new LinkedList<>();
    for (TreeNode node : nodes) {
      if(node == null)
        continue;
      if(dbs.add(node.getTreeDB()))
        node.getTreeDB().startChangeRow();
      if (processed.contains(node.getUUId())) continue;
      if (!node.getTreeDB().getTreeLogic().canBeDeleted(node)) continue;
      if (node instanceof DocFolder) {
        List<UUID> toprocess = new ArrayList<>();
        recUpdateProcessed(node, toprocess);
        recDelNode(docrem,docremloc,node, processed, remList);
        processed.addAll(toprocess);
      }
      if (node instanceof Document) {

        // TODO: document delete undo
        // node.getTreeDB().startTransaction("Delete Document");
        List<UUID> toprocess = new ArrayList<>();
        recUpdateProcessed(node, toprocess);
        deleteDocument(docrem, docremloc, (Document) node, processed, remList);
        processed.addAll(toprocess);
        // node.getTreeDB().commit();
      }
    }
    try{
      IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
      PlatformUI.getWorkbench()
          .getOperationSupport()
          .getOperationHistory()
          .dispose(context, true, true, true);
  
      ArrayList<Location> removeLocations = new ArrayList<>();
      Map<CacheStorage, CacheTransactionDescription> started = new ConcurrentHashMap<>();
      TreeStateManager tsm = null;
      try {
        for(TreeDB db:dbs) {
          if(tsm == null)
            tsm = TreeStateManager.getOne(db.getTracker());
          CacheStorage cs = db.getCacheStorage();
          CacheTransactionDescription r = cs.startCacheTransaction("delete doc from "+db.getVisibleName(), 2);
          if(r != null && r.error == null)
            started.put(cs,r);
          Set<UUID> locToRemove = docremloc.getLocationsToRemoveUUIDs();
          for(TreeNode tn: docremloc.getRequiremnetsToUpdate()) {
            if (processed.contains(tn.getUUId())) continue;
            if (tn.getType().equals(Requirement.TYPE_NAME)
                || TextNode.TYPE_NAME.equals(tn.getType())) {
              Requirement req = (Requirement) tn;
              boolean delTr = false;
              
              Collection<ReferenceValue> locs = req.getLocationQIds();
              boolean process = false;
              boolean ch = false;
              boolean hasNonDeleted = false;
              if (locs != null && !locs.isEmpty()) {
                for (ReferenceValue s : locs){
                  if(s.hasErrorsOnResolve(tn.attrs()))
                    continue;
                  UUID[] uids = s.getNodeUUIDs(tn.attrs());
                  for(UUID uid:uids) {
                    if(!locToRemove.contains(uid))
                      hasNonDeleted = true;
                    else { //TODO can be wrong
                      process = true;
                      Location l = (Location)db.getNode(uids[0]);
                      if(l!=null)
                        removeLocations.add(l);
                    }
                  }
                }
                if (!removeLocations.isEmpty()) {
                  //ch = ch || req.removeLocationNodes(removeLocations);//actually duplication
                  for (Location loc : removeLocations) {
                    processed.add(loc.getUUId());
                    if(docrem.contains(loc.getDocument())){
                      delTr = true;
                    }
                  }
                  //if(ch)
                    //req.commitAttributes();//TODO remove it?
                }
                
                if (process) {
                  if (((req.getWorkChildrenUUIDs().size() - req.getWorkChildren(Comment.TYPE_NAME).size())
                          == 0)
                      && !reqs_to_delete.contains(req)
                      && !hasNonDeleted
                      && req.getTreeDB().getTreeLogic().canBeDeleted(req))
                    reqs_to_delete.add(req);
                  ch = true;
                }
              }
              if (delTr&&req.attrs().getAttribute(Requirement.ATTR_TRANSF_STATUS) != null) {
                req.attrs().removeAttribute(Requirement.ATTR_TRANSF_STATUS);
                ch = true;
              }
              if(ch)
                req.commitAttributes();
            }
          }
        }
        for(TreeNode node:remList)
          node.delete();//actual node\folder delete
      }finally {
        for(Entry<CacheStorage, CacheTransactionDescription> db:started.entrySet()) {
          if(db.getValue().error == null)
            db.getKey().commit(db.getValue());
        }
      }
      //TODO message
      if (reqs_to_delete.size() > 0) {
    	TreeDB db = reqs_to_delete.get(0).getTreeDB();
    	reqs_to_delete.sort(DeleteLocationOperation.getReqComparator(db));
        if(tsm!=null && tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) {
	        String strList = "";
	        for (Requirement req : reqs_to_delete) strList += req.getUserVisibleName(true) + "\n";
	        if (strList.length() > 0) strList = strList.substring(0, strList.length() - 1);
	        String fstr = strList;
	        dires = false;
	        EclipseAPI.getDisplay().syncExec(new Runnable(){
	          @Override
	          public void run() {
	            dires = BigMessageDialog.open(
	                EclipseAPI.getDisplay().getActiveShell(),
	                "Delete Requirement" + ((reqs_to_delete.size() > 1) ? "s" : ""),
	                "Requirement" + (reqs_to_delete.size() == 1 ? "" : "s"),
	                fstr,
	                ((reqs_to_delete.size() > 1) ? " has" : " have") + " no locations, delete?");
	          }
	        });
	        if (dires) {
	          //TreeDB db = reqs_to_delete.get(0).getTreeDB();
	          boolean started2 = db.startTransaction(
                  "Delete Requirement" + (reqs_to_delete.size() > 1 ? "s" : ""), true); 
	          if (started2) {
	            try {
  	              for (Requirement req : reqs_to_delete) {
  	                if (!processed.contains(req.getUUId())) {
  	                  recUpdateProcessed(req, processed);
  	                  req.delete();
  	                }
  	              }
                  if(started2) {
                    db.commit();
                    started2 = false;
                  }
	            }finally {
                  if(started2) 
                    db.rollback();
	            }
	          }
	        }
	      }
      }
    }finally{
      for(TreeDB db:dbs)
        db.stopChangeRow();
    }
  }

  @Override
  public String[] getWarnings(TreeNode[] nodes) {
    Set<String> warns = new TreeSet<>();
    for (TreeNode node : nodes) {
      if(nodes == null)
        continue;
      if (DocFolder.TYPE_NAME.equals(node.getType())) {
        warns.add(WARN_DOCUMENTS);
        if (folderHasDocumentWithLocation((DocFolder) node)) warns.add(WARN_LOCATIONS);
      }
      if (Document.TYPE_NAME.equals(node.getType())) {
        warns.add(WARN_DOCUMENTS);
        if (((Document) node).sizeLocationsChildren() > 0) warns.add(WARN_LOCATIONS);
      }
    }
    return warns.toArray(new String[0]);
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }
}
