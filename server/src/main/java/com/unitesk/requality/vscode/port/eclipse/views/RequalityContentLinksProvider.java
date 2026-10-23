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
package com.unitesk.requality.vscode.port.eclipse.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.jface.viewers.AbstractTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.AAllTreeStateChangeHandler;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.TraceabilityDataSource;

public class RequalityContentLinksProvider implements ITreeContentProvider {
  private TreeViewer viewer;
//  private ArrayList<TreeDB> dbs = new ArrayList<>();
//  private ArrayList<TreeDB> dbalt = new ArrayList<>();
  public RequalityContentLinksProvider() { 
    tt.getTreeStateManager().addStateHook("*", STATE_LISTENERS_CHANGE, handler);
  }

  private void refresh(TreeNode node, boolean b) {
    if (node != null) viewer.refresh(node, b);
    else viewer.refresh(b);
    viewer.expandToLevel(3);
  }

  private static boolean isShown(String w, Attribute val) {
    if( w.equals(TreeNode.ATTR_TERMS) || 
        w.equals(TreeNode.ATTR_TERMS_REV))
      return true;
    if(RequalityCNF.isShown(w))
      return true;
    if(val != null && val.isReference())
      return true;
    return false;
  }
  private ITreeChangeListener treeChangeListener =
      new DefaultTreeChangeListener() {
        private Map<UUID, TreeNode> pars = new HashMap<>();
        private void guiRefresh(final TreeNode node) {
          handl.addToch(node);
        }

        @Override
        public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent) {
          guiRefresh(oldParent);
          if (oldParent != null
              && newParent != null
              && !oldParent.getUUId().equals(newParent.getUUId())) guiRefresh(newParent);
          guiRefresh(node);
        }

        @Override
        public void deleted(TreeNode node) {
          for(Object o : viewer.getExpandedElements())
            if(o instanceof TreeNode)
              if(((TreeNode)o).getUUId().equals(node.getShownParentUUID()))
                guiRefresh((TreeNode)o);
        }

        @Override
        public void changed(TreeNode node, ChangesSet<Attribute> cs) {
          boolean changeIt = false;//, changeParent = false;
          Map<String,Attribute> newA = cs.getNewMap();
          for (Entry<String, Attribute> en:newA.entrySet()) {
            Attribute a = en.getValue();
            Attribute old = cs.getOld(en.getKey());
            if ((en.getKey().equals(TreeNode.ATTR_LABEL)||isShown(en.getKey(),a)||isShown(en.getKey(),old))) changeIt = true;
            //if (Attribute.affectsPosition(node, w)) changeParent = true;
            if (!changeIt) {
              if ((a != null) && (a.isReference())) changeIt = true;//TODO seems useless
              if (!changeIt && old != null && old.isReference())
                changeIt = true;
            }
            //if (changeParent) break;
          }
          //if (changeParent) guiRefresh(node.getShownParent());
          if (changeIt) guiRefresh(node);
        }

        @Override
        public void created(TreeNode node) {
          guiRefresh(node);
        }

        @Override
        public void changed(List<TreeNodeChangeEvent> changes) {
          Set<TreeNode> toch = new HashSet<>();
          for (TreeNodeChangeEvent ch : changes) {
            switch (ch.getType()) {
              case MOVE:
                {
                  if(ch.getMoment()==Moment.BEFORE)
                    continue;
                  TreeNode node = ch.getTreeNodeTarget();
                  if(node.isDisposed())
                    continue;
                  toch.add(node);
                  TreeNode old = (TreeNode) ch.getArguments()[0];
                  if (old != null) toch.add(old);
                  if (old==null || !old.equals(node.getShownParent())) toch.add(node.getShownParent());

                  //moved(ch.getTreeNodeTarget(),(TreeNode)ch.getArguments()[1],(Boolean)ch.getArguments()[0]);
                }
                break;
              case CREATE:{
                if(ch.getMoment()==Moment.BEFORE)
                  continue;
                TreeNode node = ch.getTreeNodeTarget();
                if(node.isDisposed())
                  continue;
                toch.add(node.getShownParent());
                //created(ch.getTreeNodeTarget(), (Boolean)ch.getArguments()[0]);
              }
                break;

              case DELETE:{
                if(ch.getMoment()==Moment.BEFORE){
                  try{
                    pars.put(ch.getTreeNodeTarget().getUUId(),ch.getTreeNodeTarget().getShownParentExc());
                  }catch(NodeNotFoundException nfe){
                  }
                  continue;
                }
                TreeNode node = pars.remove(ch.getTreeNodeTarget().getUUId());
                if(node!=null)
                  toch.add(node);
                //deleted(ch.getNodeDescTarget(), (Boolean)ch.getArguments()[0]);
              }
                break;
              case CACHE_CHANGE:
                {
                  toch.add(ch.getTreeNodeTarget());
                }
                break;
              case CHANGE:
                {
                  if(ch.getMoment() != Moment.AFTER)
                    continue;
                  TreeNode node = ch.getTreeNodeTarget();
                  if(node.isDisposed())
                    continue;
                  boolean changeIt = false, changeParent = false;
                  @SuppressWarnings("unchecked")
                  ChangesSet<Attribute> what = (ChangesSet<Attribute>) ch.getArguments()[0];
                  Map<String, Attribute> newA = what.getNewMap();
                  for (String s: what.getOrder()) {
                    Attribute a = what.getNew(s);
                    Attribute old = what.getOld(s);
                    if (s.equals(TreeNode.ATTR_LABEL)|| isShown(s,a)|| isShown(s,old)) changeIt = true;
                    if (Attribute.affectsPosition(node, s)) changeParent = true;
                    if (!changeIt) {
                      if ((a != null) && (a.isReference())) changeIt = true;
                      if (!changeIt
                          && old != null
                          && old.isReference()) changeIt = true;
                    }
                    //if (changeParent) break;
                  }
                  if (changeParent)
                    toch.add(node.getShownParent());
                  if (changeIt)
                    toch.add(node);
                }
                break;
            }
          }
          final Set<TreeNode> ftoch = toch;
          if(ftoch.size()>0 &&(viewer.getInput() instanceof GRoot)){
            if(ftoch.contains(((GRoot)viewer.getInput()).node))
              handl.addToch(new TreeNode[]{null});//reduce the number of calls
            else
              handl.addToch(ftoch.toArray(new TreeNode[0]));
          }
        }
      };

  private TochHandler handl = null;
  private static TreeState[]STATE_LISTENERS_CHANGE = new TreeState[] {TreeState.HALTED, TreeState.READY};
  private IStateChangeHandler handler = new AAllTreeStateChangeHandler() {
    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState,
        TreeState state) {
      if (db == null)
        db = ResManager.getManager().getTracker().getTreeDB(null, lname, pname, false, null);
      if(db == null)
        return;
      if(state == TreeState.HALTED) {
        db.removeGUIListener(treeChangeListener);
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
          @Override
          public void run() {
            TreeNode root = getTreeNode(viewer.getInput());
            if(root!=null/* && root.getTreeDB() == db*/) {//uncomment and add proper listeners for data reset/clear
              viewer.setInput(null);//reset input for viewer
            }
          }
        });
      }else
        db.addGUIListener(treeChangeListener);
    }
  
  };
  @Override
  public void dispose() {
    tt.getTreeStateManager().removeStateHook("*", STATE_LISTENERS_CHANGE, handler);
    for(TreeDB db: tt.getTreeDBs())
      db.removeGUIListener(treeChangeListener);
  }
  private EclipseTracker tt = RequalityPlugin.getTreesTracker(DBNamesManager.DEFAULT);
  private Control con = null;
  private DisposeListener dlist = new DisposeListener() {
    @Override
    public void widgetDisposed(DisposeEvent e) {
      if(e.widget == RequalityContentLinksProvider.this.viewer.getControl()){
        for(TreeDB db: tt.getTreeDBs())
           db.removeGUIListener(treeChangeListener);
        tt.getTreeStateManager().removeStateHook("*", STATE_LISTENERS_CHANGE, handler);
      }
    }
  };
  @Override
  public void inputChanged(final Viewer viewer, Object oldInput, Object newInput) {
    if (!(viewer instanceof TreeViewer)) {
      throw new IllegalArgumentException();
    }
    this.viewer = (TreeViewer) viewer;
    boolean contchanged = false;
    if(con == null || con!=this.viewer.getControl()) {
      contchanged = true; 
      if(con!=null)
        con.removeDisposeListener(dlist);
      con=this.viewer.getControl();
      con.addDisposeListener(dlist);
      handl = new TochHandler(this.viewer, new Runnable(){
        @Override
        public void run() {
          RequalityContentLinksProvider.this.viewer.expandToLevel(3);
        }
      });
    }
//    syn
//    if (newInput == null) {//TODO think
//      for (TreeDB db : dbs) {
//        db.removeGUIListener(treeChangeListener);
//      }
//      dbs.clear();
//      return;
//    }
//    

    TreeDB tree = null;
    if(newInput!=null) {
      if (newInput instanceof GRoot) {
        tree =(((GRoot) newInput).node.getTreeDB());
      } else if (newInput instanceof IWorkspaceRoot) {
        for (TreeDB treea:tt.getTrees())
          if(treea!=null && !treea.getGUIListeners().contains(treeChangeListener))//TOOD needed?
            treea.addGUIListener(treeChangeListener);
      } else if (newInput instanceof Object[]) {
        tree =(((TreeNode) ((Object[]) newInput)[0]).getTreeDB());
      } else if (newInput instanceof TreeDB) {
        tree =((TreeDB) newInput);
      } else {
        //throw new IllegalArgumentException();
      }
    }
    if(tree!=null && !tree.getGUIListeners().contains(treeChangeListener))//TOOD needed?
      tree.addGUIListener(treeChangeListener);
    if(contchanged)
      viewer
        .getControl()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                //TODO if control is disposing, old code will throw exception
                if ((viewer.getControl() != null)
                    && !viewer.getControl().isDisposed()) {
                  RequalityContentLinksProvider.this.refresh(null, true);
                  ((AbstractTreeViewer) viewer).expandAll();
                }
              }
            });
  }

  public static class TestNodeLinksGroup {
    private boolean incoming;
    private Object[] links;
    private TreeNode par;
    private String lbl;

    public TestNodeLinksGroup(TreeNode node, String lbl, boolean incoming, Object[] links) {
      this.incoming = incoming;
      this.links = links;
      this.lbl = lbl;
      par = node;
    }

    @Override
    public String toString() {
      return lbl;
//      if (incoming) return "Incoming";
//      return "Outcoming";
    }

    public TreeNode getParent() {
      return par;
    }

    public boolean isUp() {
      return incoming;
    }

    public Object[] getLinks() {
      return links;
    }
  }

  public static class TestNodeLinks {
    private String name;
    private Object[] nodes;
    private TreeNode target;
    private boolean isup;
    private TestNodeLinksGroup par;

    public TestNodeLinks(
        TestNodeLinksGroup utg, boolean isup, TreeNode node, String name, Object[] linked) {
      nodes = linked;
      this.name = name;
      target = node;
      this.isup = isup;
      par = utg;
    }

    public boolean isUp() {
      return isup;
    }

    public Object[] getChildren() {
      return nodes;
    }

    @Override
    public String toString() {
      if (isup) //TODO another way?
      return TraceabilityDataSource.getRevertName(target.getTreeDB().isVirtual()?
            Instantiator.getRootTree(target.getTreeDB()):target.getTreeDB(), removeBrack(name));
      return name;
    }

    public TreeNode getTarget() {
      return target;
    }

    public TestNodeLinksGroup getParent() {
      return par;
    }
  }

  @Override
  public Object[] getElements(Object inputElement) {
    return getChildren(inputElement);
  }
  private TreeNode getTreeNode(Object parentElement) {
    TreeNode parent = null;
    if(parentElement == null)
      return null;
    if (parentElement instanceof IProject) {
      IProject project = (IProject) parentElement;
      if (!project.isOpen()) {
        return null;
      }
      TreeDB db = null;
      try {
        db = tt.getTreeDB(project, null, tt.getLocalName(project));
      } catch (IOException e) {
    // TODO Auto-generated catch block
        RLogger.logError(e);
      }
      if (db != null
          && db.getRootNode() != null // Root          
      // node
      // can
      // be
      // not
      // created
      // yet
      ) {
        parent = db.getRootNode();
      }
    }

    if (parentElement instanceof GRoot) {
      parent = ((GRoot) parentElement).node;
    }
    if (parentElement instanceof TreeNode) {
      parent = (TreeNode) parentElement;
    }
    if (parentElement instanceof PrefixAndTreeNode)
    {
      parent = ((PrefixAndTreeNode)parentElement).getNode();
    }
    if (parentElement instanceof TreeDB) {
      parent = ((TreeDB) parentElement).getRootNode();
    }
    return parent;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object[] getChildren(Object parentElement) {
    if (parentElement instanceof IProject) {
      IProject project = (IProject) parentElement;
      if (!project.isOpen()) {
        return new Object[0];
      }
    }
    if (parentElement instanceof TestNodeLinksGroup) {
      return ((TestNodeLinksGroup) parentElement).links;
    }
    if (parentElement instanceof GRoot && ((GRoot)parentElement).node == getTreeNode(viewer.getInput())) {
      TreeNode parent = getTreeNode(parentElement);
      TreeDB parDB = parent.getTreeDB();
      if(parDB.isDisposed())
        return new Object[0];
      Map<String, ReferenceValue[]> links = RelManager.getLinksOrErrors(parent,false);//new HashMap<>();
      Map<String, Object> rlinks = new HashMap<>();
      Map<String, Map<TreeNode, String>> revlinks =RelManager.getRevertLinks(parent,false,true);
      if (revlinks != null)
        for (String s : revlinks.keySet()){
          Map<TreeNode,String> mp = revlinks.get(s); 
          for (TreeNode nd : mp.keySet()) {
            if (!nd.isDisposed()) {
              if (!rlinks.containsKey(s)) rlinks.put(s, new ArrayList<>());
              String vers = mp.get(nd);
              ((ArrayList<Object>) rlinks.get(s)).add(new PrefixAndTreeNode(nd, "", 
                  vers == null?"":!AttributesVersioningRule.isLegal(nd.getType())?
                      "":IReferenceVersionRule.getVersionString(vers, parent.getStoredNodeVersion(parDB), " ")));
            }
          }
        }
      List<Object> tnl = new ArrayList<>();
      Set<String> fl = RequalityLCNF.getFilters(parent.getTreeDB());
      List<TestNodeLinksGroup> grps = new ArrayList<>();
      TestNodeLinksGroup utg =
          new TestNodeLinksGroup(parent, "Outcoming", false, tnl.toArray());
      if ((RequalityLCNF.getReferenceDirection() & 1) == 1) {
        Map<String, Object[]> alinks = new HashMap<>();
        List<String> names = new LinkedList<>();
        for (String s : links.keySet()) {
          String key = removeBrack(s);
          if (!key.startsWith("_") && (fl == null || fl.contains(key) || fl.isEmpty())) {
            names.add(key);
            //if (!alinks.containsKey(key)) alinks.put(key, new LinkedList<>());
            ReferenceValue[] rvs = links.get(s);
            Set<Object>alo = new HashSet<>();
            for(ReferenceValue rv:rvs)
              if(rv.hasErrorsOnResolve(parent.attrs()))
                alo.add(rv.toString());
              else{
                TreeNode[]nodes = rv.getNodes(parent.attrs());
                for(TreeNode node:nodes) {
                  String old = rv.getVersions()!=null?rv.getVersions().get(node.getUUId()):"";
                  alo.add(new PrefixAndTreeNode(node, "",!AttributesVersioningRule.isLegal(node.getType())?
                      "":rv.getVersionString(node.getTreeDB(), old,node.getStoredNodeVersion(parDB)," ")));
                }
//                alo.addAll(Arrays.asList(rv.getNodes(parent.attrs())));
              }
            alinks.put(key, alo.toArray());
          }
        }
        for (String s : alinks.keySet()) {
          if(alinks.get(s).length>0){
            if(alinks.get(s).length>1)
              tnl.add(new TestNodeLinks(utg, false, parent, s, alinks.get(s)));
            else{
              Object o = alinks.get(s)[0];
              if(o instanceof PrefixAndTreeNode){
                PrefixAndTreeNode pap = (PrefixAndTreeNode) alinks.get(s)[0];
                String addP = "";
                if(!pap.getNode().getTreeDB().equals(parent.getTreeDB()))
                  addP = pap.getNode().getTreeDB().getProjectName()+"::";
                pap.adapt(false, pap.getNode(),s+": "+addP, "");
                tnl.add(pap);
              }
              else
                tnl.add(o);
            }
          }
        }
      }
      utg.links = tnl.toArray();
      if (utg.links.length > 0) grps.add(utg);
      tnl.clear();

      Map<String, Map<TreeNode, String>> dirt = parent.getTreeDB().getTermsStorage().getUseToDefTermsRelations(parent, null);
      utg =
          new TestNodeLinksGroup(parent, "Term referrers", false, tnl.toArray());
      if ((RequalityLCNF.getReferenceDirection() & 1) == 1) {
        Map<String, Object[]> alinks = new HashMap<>();
        List<String> names = new LinkedList<>();
        for (String s : dirt.keySet()) {
          String key = removeBrack(s);
          if (!key.startsWith("_") && (fl == null || fl.contains(key) || fl.isEmpty())) {
            names.add(key);
            Set<Object>alo = new HashSet<>();
            Map<TreeNode,String> vers = dirt.get(s);
            for(TreeNode node:vers.keySet()){
              String v = vers.get(node);
              alo.add(new PrefixAndTreeNode(node, "", v==null?"":IReferenceVersionRule.getVersionString(v, node.getStoredNodeVersion(parDB), " ")));
            }
            alinks.put(key, alo.toArray());
          }
        }
        for (String s : alinks.keySet()) {
          if(alinks.get(s).length>0){
            if(alinks.get(s).length>1)
              tnl.add(new TestNodeLinks(utg, false, parent, s, alinks.get(s)));
            else{
              PrefixAndTreeNode pnt = (PrefixAndTreeNode) alinks.get(s)[0];
              pnt.adapt(true, pnt.getNode(),s, "");
              tnl.add(pnt);
            }
          }
        }
      }
      utg.links = tnl.toArray();
      if (utg.links.length > 0) grps.add(utg);
      tnl.clear();

      utg = new TestNodeLinksGroup(parent, "Incoming", true, tnl.toArray());
      if ((RequalityLCNF.getReferenceDirection() & 2) == 2) {
        Map<String, List<Object>> alinks = new HashMap<>();
        List<String> names = new LinkedList<>();
        for (String s : rlinks.keySet()) {
          String key = removeBrack(s);
          if (!key.startsWith("_") && (fl == null || fl.contains(key) || fl.isEmpty())) {
            names.add(key);
            //fix revers with versions
            if (!alinks.containsKey(key)) alinks.put(key, new LinkedList<>());
            alinks.get(key).addAll(((List<?>) rlinks.get(s)));
          }
        }
        for (String s : alinks.keySet()) {
          if(alinks.get(s).size()>1)
            tnl.add(new TestNodeLinks(utg, true, parent, s, alinks.get(s).toArray()));
          else{
            PrefixAndTreeNode node = (PrefixAndTreeNode)alinks.get(s).iterator().next();
            String addP = "";
            if(!node.getNode().getTreeDB().equals(parent.getTreeDB()))
              addP = node.getNode().getTreeDB().getProjectName()+"::";
            node.adapt(false, node.getNode(),TraceabilityDataSource.getRevertName(
                node.getNode().getTreeDB().isVirtual()?Instantiator.getRootTree(node.getNode().getTreeDB()):node.getNode().getTreeDB(), 
                    removeBrack(s))+": "+addP,
                "");
            tnl.add(node);
          }
        }
      }
      utg.links = tnl.toArray();
      if (utg.links.length > 0) grps.add(utg);
      tnl.clear();
      
      dirt = parent.getTreeDB().getTermsStorage().getDefToUseTermsRelations(parent,true);
      utg =
          new TestNodeLinksGroup(parent, "Terms", true, tnl.toArray());
      if ((RequalityLCNF.getReferenceDirection() & 2) == 2) {
        Map<String, Object[]> alinks = new HashMap<>();
        List<String> names = new LinkedList<>();
        for (String s : dirt.keySet()) {
          String key = removeBrack(s);
          if (!key.startsWith("_") && (fl == null || fl.contains(key) || fl.isEmpty())) {
            names.add(key);
            Set<Object>alo = new HashSet<>();
            Map<TreeNode, String> vers = dirt.get(s);
            for(TreeNode node:vers.keySet()){
              String v = vers.get(node);
              alo.add(new PrefixAndTreeNode(node, "", v == null?"":IReferenceVersionRule.getVersionString(v, parent.getStoredNodeVersion(parDB), " ")));
            }
            alinks.put(key, alo.toArray());
          }
        }
        for (String s : alinks.keySet()) {
          if(alinks.get(s).length>0){
            if(alinks.get(s).length>1)
              tnl.add(new TestNodeLinks(utg, false, parent, s, alinks.get(s)));
            else{
              PrefixAndTreeNode pnt =  (PrefixAndTreeNode) alinks.get(s)[0];
              pnt.adapt(true,pnt.getNode(),s, "");
              tnl.add(pnt);
            }
          }
        }
        utg.links = tnl.toArray();
        if (utg.links.length > 0) grps.add(utg);
        tnl.clear();
      }
      return grps.toArray();
    } else if (parentElement instanceof TestNodeLinks)
      return ((TestNodeLinks) parentElement).getChildren();
    return new Object[0];
  }

  private static String removeBrack(String s) {
    int pos = s.indexOf("[");
    while (pos > -1) {
      int end = s.indexOf("]", pos + 1);
      if (end > -1) {
        s = s.substring(0, pos) + s.substring(end + 1);
        pos = s.indexOf("[", pos + 1);
      } else pos = s.indexOf("[", pos);
    }
    return s;
  }

  @Override
  public Object getParent(Object element) {
    if (element instanceof TestNodeLinks) {
      return ((TestNodeLinks) element).getParent();
    }
    if (element instanceof TreeNode) {
      TreeNode node = (TreeNode) element;
      if (node.isDisposed()) {
        return null;
      }
      if (node.getTreeDB().getRootNode().equals(node.getShownParent())) {
        return EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(node.getTreeDB());
      }
      return node.getShownParent();
    }
    if (element instanceof TestNodeLinksGroup) return ((TestNodeLinksGroup) element).getParent();
    return null;
  }

  @Override
  public boolean hasChildren(Object element) {
    return getChildren(element).length > 0;
  }
}
