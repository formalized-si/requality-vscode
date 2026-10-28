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
package com.unitesk.requality.nodetype;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.LocationLabelProvider;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class Location extends TreeNode{
  public static final String TYPE_NAME = "Location";
  public static final String FRAGMENT_SEPARATOR = "|";
  public static final String FRAGMENT_SEPARATOR_REGEXP = "\\|";
  public static final Comparator<Object> FRAGMENT_SORTER=new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      if(o1 == null)
        return o2 == null ? -1 : 0;
      if(o2 == null)
        return 1;
      String os1 = o1.toString(), os2 = o2.toString();
      String[]osa1 = os1.split(FRAGMENT_SEPARATOR_REGEXP), osa2 = os2.split(FRAGMENT_SEPARATOR_REGEXP);
      if(osa1.length<2)
        return osa1.length<2 ? -1 : 0;
      if(osa2.length<2)
        return 1;
      return Integer.compare(Integer.parseInt(osa1[0]), Integer.parseInt(osa2[0]));
    }  
  };
  public static String ATTR_MARKUP = "_markup";
  //private String label;
  // private boolean isTransferred = false;

  public Location() {
    super(null, null);
  }

  public Location(TreeDB db, UUID uid) {
    super(db, uid);
    setType(TYPE_NAME);
  }

  private String getHTMLFolder() {
    TreeNode parent = getWorkParent();
    // in a case of tests
    if (parent == null) return "";
    return new File(((com.unitesk.requality.nodetype.Document) parent).getHTMLPath()).getParent();
  }

  public Object getDocumentNodeModel() {
    Document d = getDocument();
    if (d == null) return null;
    return d.getNodeModel();
  }

  @Override
  public String getId() { // hack.. beware of files
    return getSUUId();
  };

  @Override
  public String getDescription(String prefix, String postfix, String basePath, IReqURIResolver rslv) {
    String desc = getDescription();
    if (desc == null) desc = "";
    try {
      return CoreUtils.resolveURI(this,rslv, CoreUtils.htmlEncapsulate(
          RequalityCLI.getReportDescription(
              this, TreeNode.ATTR_DESCRIPTION, getHTMLFolder(), desc, prefix, postfix, basePath)));
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return null;
  }

  @Override
  public String getQualifiedId() {
    if (getWorkParent() != null)
      return getWorkParent().getQualifiedId() + "/" + getSUUId();
    RLogger.logInfo("null parent for "+getSUUId());
    return getSUUId();
  };

  public Requirement[] getRequirements() {
    return getRequirements(false);
  }

  public Requirement[] getRequirements(boolean sort) {
    return getRequirements(true,sort);
  }
  public Requirement[] getRequirements(boolean onlyCache,boolean sort) {
    Map<TreeNode, String> rev = RelManager.getRevertLinks(this,false, false, Requirement.ATTR_LOCATIONS);
    Requirement[] reqs = null;
    Set<TreeNode> reqss = new HashSet<>();
    if (rev != null) {
      reqss.addAll(rev.keySet());
    }
    int i = 0;
    reqs = new Requirement[reqss.size()];
    for (TreeNode node : reqss) 
      if (node instanceof Requirement)
        reqs[i++] = (Requirement) node;
    if(sort)
      Arrays.sort(reqs, TreeNodeComparator.getComparator(getTreeDB()));
  
    return reqs;
  }

  public static void markLocations(TreeNode r, Set<String> old) {
    if (!(r instanceof Requirement)) return;
    if (old != null) old = new HashSet<>(old);
    TreeDB db = r.getTreeDB();
    if (db.isVirtual()) db = Instantiator.getRootTree(db);
    Attribute locs = r.attrs().getAttribute(Requirement.ATTR_LOCATIONS);
    // TODO think
  }

  private static boolean hasNVirtVNParent(TreeNode r) {
    if (r == null) return false;
    if (!r.isVirtual()) return true;
    TreeNode par = r.getWorkParent();
    if (par == null || par.isRoot()) return false;
    if (!par.isVirtual()) return true;
    return hasNVirtVNParent(par);
  }

  public static Requirement[] getRequirementsFor(TreeDB db, Location loc) {
    TreeNode t = null;
    if(db.isVirtual())
      db = Instantiator.getRootTree(db);
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));
    if (TreeState.isProjectLoadable(ts)) throw new RuntimeException("Loading... " + loc.getSUUId());
    if (db.isVirtual()) db = Instantiator.getRootTree(db);
    try {
      t = loc; // slow
      if (t != null) {
        Requirement[] req = ((Location) t).getRequirements(true,true);
        if (req != null) return req;
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }

    return new Requirement[0];
  }

  private static Comparator<Requirement> reqcomparator =
      new Comparator<Requirement>() {
        @Override
        public int compare(Requirement arg0, Requirement arg1) {
          return arg0.getUserVisibleName(true).compareTo(arg1.getUserVisibleName(true));
        }
      };

  /**
   * Returns document location is related to. If there is no such document - returns null. If there
   * are two or more documents - returns first available and prints warning to console
   *
   * @return document related to location or null
   */
  public Document getDocument() {
    return (Document) getWorkParent();
  }

  public Document[] getDocuments() {
    UUID locQId = getUUId();
    return getDocumentsFor(getTreeDB(), locQId);
  }

  //TODO rewrite 
  public static Document[] getDocumentsFor(TreeDB db, UUID locQId) {
    List<Document> result = Collections.emptyList();
    for (TreeNode node :
        new DeepFirstTreeWalker(
            db.getNode(Document.getTypeRootQId(), Path.QID | Path.UV), true, true, false)) {
      if (!node.getType().equals(Document.TYPE_NAME)) {
        continue;
      }
      Document doc = (Document) node;
      if (doc.getChildrenLocations().contains(locQId)) {
        if(result.isEmpty())
          result = new ArrayList<>();
        result.add(doc);
      }
    }
    return result.toArray(new Document[0]);
  }
  
  public String formLabel(){
    return formLabel(this, getTreeDB(), getSUUId());
  }
  
  public static String formLabel(Location loc, TreeDB db, String uid) {
    Requirement[] reqs = null;
    try {
      reqs = getRequirementsFor(db, loc);
    } catch (NodeNotFoundException re) {
      // cache loading
      return "LOADING..." + loc.getId();
    } catch(RuntimeException re) {
      re.printStackTrace();
      return "LOADING..." + loc.getId();
    }
    if (reqs.length == 0) {
      return "orphan location(" + loc.getId() + ")";
    }
    String[] names = new String[reqs.length];

    int i = 0;

    Set<String> qids = new HashSet<>();
    for (Requirement curReq : reqs) {
      String qid = curReq.getQualifiedId(true);
      if (!qids.contains(qid)) {
        qids.add(qid);
        String reqName = curReq.getUserVisibleName(true);
        List<ReferenceValue> locQIds = curReq.getLocationQIds();
        int actualInd = curReq.getLocationIndex(uid);
        int curInd = actualInd + 1;
        if (locQIds.size() > 1) {
          if(actualInd == -1)
        	names[i] = reqName + "#N";
          else
            names[i] = reqName + "#" + curInd;
        } else {
          names[i] = reqName;
        }
        i++;
      }
    }
    int n = i;
    StringBuilder sb = new StringBuilder();
    sb.append(names[0]);
    for (i = 1; i < n; i++) {
      sb.append(", ");
      sb.append(names[i]);
    }
    return sb.toString();
  }

  @Override
  public IValueProvider getLabelVP() {
    return LocationLabelProvider.getOne(this);
  }

  public void resetLabel() {
    if(attrs().getLocalAttribute(TreeNode.ATTR_LABEL)!=null) {
      updateLocalAttribute(TreeNode.ATTR_LABEL);
      commitAttributes();
    }
//    String nl = Location.toString(getTreeDB(), getQualifiedId(), getSUUId());
//    if (label == null || !nl.equals(label)) {
//      label = nl;
//      getTreeDB()
//          .fireNodeChange(
//              this,
//              new ChangesSet(Collections.emptySet(),AttributeStorage.formSet(TreeNode.ATTR_ID),Collections.emptySet()),
//              Collections.EMPTY_MAP,
//              true);
//      fireNodeAttributeChange(
//          new ChangesSet(Collections.emptySet(),AttributeStorage.formSet(TreeNode.ATTR_ID),Collections.emptySet()));
//      getTreeDB()
//        .fireNodeGUIChange(
//         this,
//         new ChangesSet(Collections.emptySet(),AttributeStorage.formSet(TreeNode.ATTR_ID),Collections.emptySet()),
//         Collections.EMPTY_MAP,
//         true);
//
//      fireGUINodeAttributeChange(
//          new ChangesSet(Collections.emptySet(),AttributeStorage.formSet(TreeNode.ATTR_ID),Collections.emptySet())
//          );
//    }
  }

  @Override
  public Index getIndex() {
    return Index.create(Integer.toString(getFirstDocPosition()));
  }


  private int getFirstDocPosition() {
    String[] mup = attrs().getStringArrayAttributeValue(ATTR_MARKUP);
    if(mup.length == 0)
      return 0;
    int min = Integer.MAX_VALUE;
    for(String s:mup) {
      int pos = s.indexOf(Location.FRAGMENT_SEPARATOR);
      if(pos>0)
        min = Math.min(Integer.parseInt(s.substring(0, pos)), min);
    }
    if(min == Integer.MAX_VALUE)
      return 0;
    return min;
  }

  public int compareIndexTo(Location l2) {
    Document doc1 = getDocument(), doc2 = l2.getDocument();
    if (doc1 == null || doc2 == null) return 0;
    if (!doc1.getUUId().equals(doc2.getUUId())) return doc1.compareTo(doc2);
    return Integer.compare(getFirstDocPosition(),l2.getFirstDocPosition());
  }

  //
  @Override
  public boolean isHidden() {
    // TODO check parameters
    return super.isHidden();
  }
  // public void setTransferredLocation() {
  // isTransferred = true;
  // }
  //
  // public boolean isTransferred() {
  // return isTransferred;
  //
  // }


  public void removeFragments(Requirement node) {
    // updateRevLocations(node);
    Requirement[] reqs = getRequirements();
    if (reqs == null || reqs.length == 0) delete(); // TODO check
    else commitAttributes();
  }

  public void resetRelated(TreeNode node) {
    resetLabel();
    for (Requirement r : getRequirements(true, false))
      for (Location l : r.getLocations(true))
        if(node!=l)
          l.resetLabel();
  }

  public boolean connectedToDocument(Document o) {
    for(Document d:getDocuments())
      if(d == o)
        return true;
    return false;
  }

  public static Pair<Integer, Integer> parsePositions(String s) {
    int sepPos = s.indexOf(Location.FRAGMENT_SEPARATOR);
    if(sepPos>-1) { 
      return new Pair<Integer, Integer>(Integer.parseInt(s.substring(0,sepPos)), 
    		                            Integer.parseInt(s.substring(sepPos+1)));
    }
    return null;
  }

  public List<int[]> getFragments() {
    List<Object> markup = attrs().getListAttributeValue(ATTR_MARKUP);
    // VSCODE-PATCH: a location of a converted catalog can have no markup (no ranges)
    if (markup == null)
      return Collections.emptyList();
    List<int[]> ret = null;
    for(Object o : markup) {
      String lm = (String) o;
      if(ret == null)
        ret = new LinkedList<>();
      String[] frags = lm.split(FRAGMENT_SEPARATOR_REGEXP);
      if(frags.length<2) {
        RLogger.logError("incorrect markup "+getUUId()+" "+lm);
      }else {
        int[] fragInt = new int[frags.length];
        for(int i = 0;i < frags.length;i++)
          fragInt[i] = Integer.parseInt(frags[i]);
        ret.add(fragInt);
      }
    }
    return ret == null ? Collections.emptyList():ret;
  }
}
