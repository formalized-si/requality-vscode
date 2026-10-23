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
package com.unitesk.requality.eclipse.editors.browser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.service.DocumentsSocketService;
import com.unitesk.requality.core.service.WSSocket;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.document.IDocumentAddedHandler;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

final class ReqMarkerOutlineContentProvider implements ITreeContentProvider {
  private Viewer v;
  private DocumentsSocketService dss;
//  private Set<TreeDB> dbs = new HashSet<>();
  private final IDocumentAddedHandler docHandler = new IDocumentAddedHandler() {
  
    @Override
    public void onDocAdded(TreeNode doc2) {
      if(doc2==null) {
    	  refresh(null);
    	  return;
      }
      TreeDB dba = doc2.getTreeDB();
      if(!dba.getListeners().contains(list))
        dba.addListener(list);
	  refresh(doc2);
    }
  };
  long t = -1;
  private void refresh(Object o) {
	 if(System.nanoTime()-t>200) {
		 EclipseAPI.getDisplay().asyncExec(new Runnable() {
			
			@Override
			public void run() {
			  if(!v.getControl().isDisposed())
				 v.refresh();
			}
		});
		t = System.nanoTime();
	 }
     //TODO more precise
  }
  private ITreeChangeListener list = new DefaultTreeChangeListener() {
    @Override 
    public void changed(TreeNode node, ChangesSet<Attribute> what) {
     Attribute old = what.getOld(Requirement.ATTR_LOCATIONS);
     if(old!=null && old.getType() == AttributeType.LIST) {
    	 ListValue lv = (ListValue) old.getValue();
    	 for(Object o : lv.getValues()) {
    		 Location l = null;
    		 TreeDB db = node.getTreeDB();//TODO add more trees?
    			 l = (Location) db.getNode(o.toString());
    			 if(l!=null)
    				 break;
    		 if(l!=null)
    			 refresh(l);
    	 }
     }
     if(what.contains(TreeNode.ATTR_LABEL) && isTracedLocation(node))  
       docHandler.onDocAdded((Document) node.getWorkParent());
     if(what.contains(Requirement.ATTR_LOCATIONS) && isRelatedRequirement(node))
      docHandler.onDocAdded(null);
    };
	@Override
    public void deleted(TreeNode node) {
      if(isTracedLocation(node) || isTracedDocument(node) || isRelatedRequirement(node)){
        docHandler.onDocAdded(null);//TODO?
      }
    }
    @Override
    public void created(TreeNode node) {
      if(isTracedLocation(node)){
        docHandler.onDocAdded((Document) node.getWorkParent());//TODO?
      }
    }
    private boolean isTracedDocument(TreeNode node) {
      return node.getType().equals(Document.TYPE_NAME) && 
          (dss!=null&&dss.containsDoc(node) || node==dss.getStartDocument());
    }
    private boolean isTracedLocation(TreeNode node) {
      TreeNode par = node.getWorkParent();
      return node.getType().equals(Location.TYPE_NAME) && 
          (dss!=null&&dss.containsDoc(par) || par==dss.getStartDocument());
    };
    private boolean isRelatedRequirement(TreeNode node) {
        if(!node.getType().equals(Requirement.TYPE_NAME))
           return false;
        Requirement r = (Requirement) node;
  	  for(Location l:r.getLocations(true)) {
  	    TreeNode par = l.getWorkParent();
        if(dss!=null&&dss.containsDoc(par)||par == dss.getStartDocument())
          return true;
  	  }
  	  return false;
  	}
  };
  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    v = viewer;
    if(oldInput!=null) {
      if(oldInput instanceof DocumentsSocketService) {
        DocumentsSocketService dss = (DocumentsSocketService) oldInput;
        if(dss!=null)
          dss.getTreeDB().removeListener(list);
        //TODO more listeners rems?
        if(dss!=null)
          dss.docAddHandlers.remove(docHandler);
      }
      dss = null;
    }
    if(newInput!=null) {
      if(newInput instanceof DocumentsSocketService) {
        DocumentsSocketService dss = (DocumentsSocketService) newInput;
        this.dss = dss;
        dss.docAddHandlers.add(docHandler);
        if(!dss.getTreeDB().getListeners().contains(list))
          dss.getTreeDB().addListener(list);
        TreeNode sdoc = dss.getStartDocument();
        if(sdoc!=null)
          docHandler.onDocAdded(sdoc);
        List<TreeNode> addedDoc = null;
        synchronized (dss.resourceNodeToSocks) {
          for(TreeNode d:dss.resourceNodeToSocks.keySet())
            if(d!=sdoc) {
              if(addedDoc == null)
                addedDoc = new LinkedList<>();
              addedDoc.add(d);
            }
        }
        if(addedDoc!=null)
          for(TreeNode node:addedDoc)
            docHandler.onDocAdded(node);
      }
    }
  }

  @Override
  public void dispose() {
    for(TreeDB db : ResManager.getManager().getTracker().getTreeDBs())
      db.removeListener(list);
  }

  @Override
  public boolean hasChildren(Object element) {
    return getChildren(element).length > 0;
  }

  @Override
  public Object getParent(Object element) {
    return null;
  }

  @Override
  public Object[] getElements(Object inputElement) {
    return getChildren(inputElement);
  }

  @Override
  public Object[] getChildren(Object parentElement) {
    if (parentElement instanceof DocumentsSocketService) {
      DocumentsSocketService dss = (DocumentsSocketService) parentElement;
      List<TreeNode> toh;
      synchronized (dss.resourceNodeToSocks) {
        final Map<TreeNode, List<WSSocket>> docs = dss.resourceNodeToSocks;
        toh = new LinkedList<>(docs.keySet());
      }
      List<TreeNode> locs = new ArrayList<>();
      Set<TreeNode> worked = new HashSet<>();
      Map<TreeNode,TreeNode> par = new HashMap<>();
      TreeNode start = dss.getStartDocument();
      if(start!=null && !toh.contains(start))
        toh.add(start);
      for(TreeNode doc:toh){
        if(!worked.contains(doc)){
          for(TreeNode uid:doc.getWorkChildren()){
            locs.add(uid);
            par.put(uid, doc);
          }
          worked.add(doc);
        }
      }
      Collections.sort(
          locs,
          new Comparator<TreeNode>() {
            @Override
            public int compare(TreeNode o1, TreeNode o2) {
              TreeNode u1 = o1, u2 = o2;
              if (u1 != null && u2 != null) {
                TreeNode doc1 = par.get(u1);
                TreeNode doc2 = par.get(u2);
                TreeNode t1 = u1, t2 = u2;
                if (t1 == null) return t2 != null ? -1 : 0;
                if (t2 == null) return 1;
                return t1.getIndex().compareTo(t2.getIndex());
              }
              return u1 == null ? -1 : 1;
            }
          });
      Object[] result = new Object[locs.size()];
      int i = 0;
      for (TreeNode uid : locs) {
        Location loc = (Location) uid;
        if (loc != null) {
          result[i++] = loc;
        } else {
          result[i++] = "missing: " + uid.toString();
        }
      }
      return result;
    }
    if (parentElement instanceof Document) {
      final Document doc = (Document) parentElement;
      List<TreeNode> locs = new ArrayList<>(doc.getWorkChildren());
      Collections.sort(
          locs,
          new Comparator<TreeNode>() {
            @Override
            public int compare(TreeNode o1, TreeNode o2) {
              TreeNode u1 = o1, u2 = o2;
              if (u1 != null && u2 != null) {
                TreeNode t1 = u1, t2 = u2;
                if (t1 == null) return t2 != null ? -1 : 0;
                if (t2 == null) return 1;
                return t1.getIndex().compareTo(t2.getIndex());
              }
              return u1 == null ? -1 : 1;
            }
          });
      Object[] result = new Object[locs.size()];
      int i = 0;
      for (TreeNode uid : locs) {
        Location loc = (Location)uid;
        if (loc != null) {
          result[i++] = loc;
        } else {
          result[i++] = "missing: " + uid;
        }
      }
      return result;
    }
    if (parentElement instanceof Location) {
      return new Object[] {};
    }
    if (parentElement instanceof String) {
      return new Object[] {};
    }
    return null;
  }
}
