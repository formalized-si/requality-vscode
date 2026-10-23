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
package com.unitesk.requality.eclipse.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.filter.IObjectFilter;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.repo.RevisionInfo;

public class RequalityContentProvider implements ITreeContentProvider {
  private TreeViewer viewer;
  private ArrayList<IObjectFilter<Object>> rfilters = new ArrayList<>();
  private ArrayList<ITreeNodeFilter> filters = new ArrayList<>();
  private static ITreeNodeFilter sfilter;
  private boolean checkStatic;

  public RequalityContentProvider(boolean checkStatic) {
    this.checkStatic = checkStatic;
    ResManager.getManager().getTracker().addListener(Moment.AFTER, stateListener);
  }

  public RequalityContentProvider() {
    this(true);
  }

  public boolean removeFilter(ITreeNodeFilter filter) {
    return filters.remove(filter);
  }

  public boolean addFilter(ITreeNodeFilter filter) {
    return filters.add(filter);
  }

  public boolean addResFilter(IObjectFilter<Object> filter) {
    return rfilters.add(filter);
  }
  public static boolean setStaticFilter(ITreeNodeFilter filter) {
    sfilter = filter;
    return true;
  }

  private ITreeChangeListener treeChangeListener =
      new DefaultTreeChangeListener() {
        private SortingSettings storedSortingSettings = null;
        private List<TreeNode> dirtyNodes = new ArrayList<>();

        private void guiRefresh(final TreeNode ... nodes) {
          if (viewer == null || viewer.getControl()==null || viewer.getControl().isDisposed()) {
            return;
          }
          handl.addToch(nodes);
        }

        @Override
        public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent) {
//          addToch(newParent);
//          //guiRefresh(newParent);
//          if (!oldParent.getUUId().equals(newParent.getUUId())) addToch(oldParent);
          guiRefresh(node);
        }

        @Override
        public void deleted(TreeNode node) {
          if(node == null)
            return;
          try{
            guiRefresh(node.getShownParentExc());
          }catch(NodeNotFoundException e){//ignore late del
          }
        }

        @Override
        public void changed(TreeNode node, ChangesSet<Attribute> what) {
          if(rootActions(node))
            return;
          boolean changeIt = false;//, changeParent = false;
          for (String w : what.getAll()) {
            if ((w.equals(TreeNode.ATTR_LABEL)|| RequalityCNF.isShown(w)
                ||w.equals(TreeNode.ATTR_SHOWN_CHILDREN))) changeIt = true;
            //if (Attribute.affectsPosition(node, w)) changeParent = true;
            //if (changeParent) break;
          }
          //if (changeParent) guiRefresh(node.getShownParent());
          if (changeIt) guiRefresh(node);
        }

        @Override
        public void created(TreeNode node) {
          guiRefresh(node.getShownParent());
        }
        @Override
        public void childrenOrderChanged(TreeNode node, byte flag) {
          if(!OFlag.isNoGuiUpdate(flag))
            guiRefresh(node);
        }

        @Override
        public void changed(final List<TreeNodeChangeEvent> changes) {
            Set<TreeNode> toch = new HashSet<>();
            for (TreeNodeChangeEvent ch : changes) {
              switch (ch.getType()) {
                case MOVE:
                  {
                    if(ch.getMoment()==Moment.BEFORE)
                      continue;
                    try{
                      TreeNode node = ch.getTreeNodeTarget();
                      guiRefresh(node);
                      TreeNode old = (TreeNode) ch.getArguments()[0];
                      if (old != null)guiRefresh(old);
                      if (old == null ||!old.equals(node.getShownParentExc())) guiRefresh(node.getShownParentExc());
                    }
                    catch(NodeNotFoundException nfe){
                      
                    }
                    //moved(ch.getTreeNodeTarget(),(TreeNode)ch.getArguments()[1],(Boolean)ch.getArguments()[0]);
                  }
                  break;
  
                case CHILDREN_ORDER_CHANGE:{
                  guiRefresh(ch.getTreeNodeTarget());
                }break;
                case CREATE:{
                  if(ch.getMoment()==Moment.BEFORE)
                    continue;
                  try{
                    TreeNode node = ch.getTreeNodeTarget().getShownParentExc();
                    guiRefresh(node);
                  }catch(NodeNotFoundException nfe){
                    
                  }
                }
                  //created(ch.getTreeNodeTarget(), (Boolean)ch.getArguments()[0]);
                break;
                case DELETE:
                  {
                    if(ch.getMoment()==Moment.BEFORE)
                      continue;
                    TreeNode nd = ch.getTreeNodeTarget();
                    try{
                      guiRefresh(nd.getShownParentExc());
                    }catch(NodeNotFoundException e){//ignore late del
                    }
                    //TODO add update of parent
                    //toch.add();
                  } //deleted(ch.getNodeDescTarget(), (Boolean)ch.getArguments()[0]);
                  break;
                case CHANGE:
                  {
                    if(ch.getMoment() == Moment.BEFORE)
                      continue;
                    TreeNode node = ch.getTreeNodeTarget();
                    if(node.isRoot())
                      if (rootActions(node))
                    	continue;
                    if(node.isDisposed())
                    	continue;
                    boolean changeIt = false, changeParent = false;
                    @SuppressWarnings("unchecked")
                    ChangesSet<Attribute> what = (ChangesSet<Attribute>) ch.getArguments()[0];
                    Map<String, Attribute> newA = what.getNewMap();
                    for (Entry<String, Attribute> en : newA.entrySet()) {
                      if ((en.getKey().equals(TreeNode.ATTR_LABEL)
                          || RequalityCNF.isShown(en.getKey()))) changeIt = true;
                      if (Attribute.affectsPosition(node, en.getKey())) changeParent = true;
                      if (changeParent) break;
                    }
                    try{
                      if (changeParent) 
                        guiRefresh(node.getShownParentExc());
                      else if (changeIt)
                        guiRefresh(node);
                    }catch(NodeNotFoundException nfe){
                      
                    }
                  }
                  break;
              }
            }
            handl.addToch(toch.toArray(new TreeNode[0]));
        }
        protected boolean rootActions(TreeNode node) {
          if (node.getUUId().equals(node.getTreeDB().getRootNode().getUUId())) {
            //refresh root
            if (!node.getTreeDB().getSortingSettings().equals(storedSortingSettings)) {
              storedSortingSettings = node.getTreeDB().getSortingSettings();
              final TreeViewer viewer = RequalityContentProvider.this.viewer;
              EclipseAPI.getDisplay().asyncExec(new Runnable() {
				
				@Override
				public void run() {
					if(viewer.getControl().isDisposed())
                        return;
                      viewer.refresh(true);
				}
			});
              return true;
            }
          }
          return false;
        }
  };
  private ITreeStateListener stateListener =
      new ITreeStateListener() {
        @Override
        public void open(TreeDB db) {
          final TreeViewer viewer = RequalityContentProvider.this.viewer;
          if(!db.getGUIListeners().contains(treeChangeListener))
            db.addGUIListener(treeChangeListener);
          EclipseAPI.getDisplay().asyncExec(new Runnable() {
            
            @Override
            public void run() {
              if(viewer == null || viewer.getControl()==null || viewer.getControl().isDisposed())
                return;
              //change to general refresh to deal with first project open
              viewer.refresh(true);
            }
          });
        }

        @Override
        public void delete(String lname, String pname, TreeDB db) {
          //TreeDB db = EclipseTracker.getTracker().getTree(proj);
          TreeStateManager tsm = null;
          RevisionInfo ri = null;
          synchronized (this) {
            tsm = db.getTracker().getTreeStateManager();
            ri = db.revision;
            db.removeGUIListener(treeChangeListener);
          }
          final TreeViewer viewer = RequalityContentProvider.this.viewer;
          final TreeStateManager ftsm = tsm;
          final IProject p = (IProject) ResManager.getManager().getTracker().getStoringEntity(db);
          final RevisionInfo fri = ri;
          if(!viewer.getControl().getDisplay().isDisposed())
            viewer.getControl().getDisplay().asyncExec(new Runnable() {
              
              @Override
              public void run() {
                if(viewer.getControl().isDisposed())
                  return;
                TreeState state = ftsm.getState(lname, fri);
                if(TreeState.isDisposed(state)) {
                  viewer.remove(db);
                }
                viewer.refresh(true);
              }
            });
        }

        @Override
        public void close(TreeDB db) {
          synchronized (this) {
            db.removeGUIListener(treeChangeListener);
          }
          final TreeViewer viewer = RequalityContentProvider.this.viewer;
          if(!viewer.getControl().getDisplay().isDisposed())
            viewer.getControl().getDisplay().asyncExec(new Runnable() {
              
              @Override
              public void run() {
                if(viewer.getControl().isDisposed())
                  return;
                viewer.refresh(true);
              }
            });
        }

        @Override
        public void settingsChanged(TreeDB db) {
          final TreeViewer viewer = RequalityContentProvider.this.viewer;
          EclipseAPI.getDisplay().asyncExec(new Runnable() {
            
            @Override
            public void run() {
              if(viewer.getControl().isDisposed())
                return;
              viewer.refresh(EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(db));
            }
          });
        }
      };
      

  private boolean filterNode(TreeNode node) {
    for (ITreeNodeFilter filter : filters) {
      if (!filter.select(node)) {
        return false;
      }
    }
    return staticFilterNode(node);
  }
  private boolean filterObject(Object node) {
    for (IObjectFilter<Object> filter : rfilters) {
      if (!filter.filter(node)) {
        return false;
      }
    }
    return true;
  }

  private boolean staticFilterNode(TreeNode node) {
    if (checkStatic) return node != null && sfilter.select(node);
    return true;
  }

  private TochHandler handl = TochHandler.EMPTY;
  @Override
  public void dispose() {
    IResTracker rt = ResManager.getManager().getTracker(); 
    rt.removeListener(Moment.AFTER, stateListener);
    synchronized (this) {
      for (TreeDB db : rt.getTreeDBs()) 
        db.removeGUIListener(treeChangeListener);//TODO more proper
    }
//    if(!viewer.getControl().isDisposed())
//      viewer.getControl().dispose();
  }

  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    IResTracker tt = ResManager.getManager().getTracker();
    tt.removeListener(Moment.AFTER, stateListener);
    if (!(viewer instanceof TreeViewer)) {
      throw new IllegalArgumentException();
    }
    this.viewer = (TreeViewer) viewer;
    handl = new TochHandler(this.viewer);
    synchronized (this) {
      for (TreeDB db : tt.getTreeDBs()) {//TODO more proper?
        db.removeGUIListener(treeChangeListener);
      }
    }
    this.viewer.getControl().addDisposeListener(new DisposeListener() {
      
      @Override
      public void widgetDisposed(DisposeEvent e) {
        if(e.widget == RequalityContentProvider.this.viewer.getControl()){
          tt.removeListener(Moment.AFTER, stateListener);
          for (TreeDB db : tt.getTreeDBs()) {//TODO more proper?
            db.removeGUIListener(treeChangeListener);
          }   
        }
      }
    });
    tt.addListener(Moment.AFTER, stateListener);
    if (newInput == null) {
      return;
    }
    synchronized (this) {
      TreeDB db = null;
      if (newInput instanceof TreeNode) {
        db =(((TreeNode) newInput).getTreeDB());
      } else if (newInput instanceof IWorkspaceRoot) {
        for(TreeDB dba : Arrays.asList(tt.getTreeDBs()))
          if(!dba.getGUIListeners().contains(treeChangeListener))
            dba.addGUIListener(treeChangeListener);
      } else if (newInput instanceof Object[]) {
        db =(((TreeNode) ((Object[]) newInput)[0]).getTreeDB());
      } else if (newInput instanceof TreeDB) {
        db =((TreeDB) newInput);
      } else {
        throw new IllegalArgumentException();
      }
      if(db!=null && !db.getGUIListeners().contains(treeChangeListener)) {
        db.addGUIListener(treeChangeListener);
      }
    }
    viewer
        .getControl()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                //TODO if control is disposing, old code will throw exception
                if ((viewer.getControl() != null)
                    && !viewer.getControl().isDisposed())
                  viewer.refresh();
              }
            });
  }

  @Override
  public Object[] getElements(Object inputElement) {
    if (inputElement instanceof IWorkspaceRoot) {
      IResTracker rt = ResManager.getManager().getTracker();
      TreeDB[]dbs = rt.getTreeDBs(); 
      Object[] ret = new Object[dbs.length];
      int i = 0;
      for(TreeDB db:dbs)
        ret[i++] = rt.getStoringEntity(db);
      return ret; 
    }
    if (inputElement instanceof TreeNode) {
      TreeNode nodeInput = (TreeNode) inputElement;
      ArrayList<TreeNode> result = new ArrayList<>();
      for (TreeNode node : nodeInput.getSortedShownChildren())
        if (node != null && filterNode(node) && filterObject(node)) {
          result.add(node);
        }
      return result.toArray(new TreeNode[0]);
    }
    if (inputElement instanceof TreeDB) {
      IProject p = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject((TreeDB) inputElement);
      if(filterObject(p))
        return new Object[] {p};
    }
    if (inputElement instanceof Object[]) {
      List<Object> projs = new LinkedList<>();
      Object[]proj = (Object[]) inputElement;
      for(Object o: proj)
        if(filterObject(o))
          projs.add(o);
      return projs.toArray(new Object[0]);
    }
    return new Object[0];
  }

  private TreeNode getTreeNode(Object parentElement) {
    TreeNode parent = null;
    EclipseTracker tt = EclipseTracker.getTracker(DBNamesManager.DEFAULT); 
    if (parentElement instanceof IProject) {
      IProject project = (IProject) parentElement;
      if (!project.isOpen()) {
        return null;
      }
      String local = tt.getLocalName(project);
      TreeStateManager tsm = tt.getTreeStateManager();
      TreeDB db = null;
	  try {
		db = tt.getTreeDB(project, null, local);
	  } catch (IOException e) {
		// TODO Auto-generated catch block
		RLogger.logError(e);
	  }
	  if(db == null)
         return null;
      TreeState ts = tsm.getState(local, tsm.getRevisionInfo(db));
      if(!TreeState.readyState(ts) && ts!=TreeState.MODIFYING)
        return null;
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
    if (parentElement instanceof TreeNode) {
      parent = (TreeNode) parentElement;
    }
    if (parentElement instanceof TreeDB) {
      parent = ((TreeDB) parentElement).getRootNode();
    }
    return parent;
  }

  @Override
  public Object[] getChildren(Object parentElement) {
    if (parentElement instanceof IProject) {
      IProject project = (IProject) parentElement;
      if (!project.isOpen()) {
        return new Object[0];
      }
    }
    TreeNode parent = getTreeNode(parentElement);
    if (parent == null) return new Object[0];
    TreeDB db = parent.getTreeDB();
    //TODO add loading state?
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));
    if(ts==TreeState.NOT_LOADED){
      final TreeViewer viewer = RequalityContentProvider.this.viewer;
      JobManager.jm.startJob("Wait for loading "+parent.getTreeDB().getVisibleName(), new RunnableWithMonitor() {
        @Override
        public boolean shouldRerun() {
          return true;
        }
        @Override
        public boolean shouldRun() {
          TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));
          return ts!=TreeState.NOT_LOADED && ts!=TreeState.LOADING;
        }
        @Override
        public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
          EclipseAPI.getDisplay().asyncExec(new Runnable(){
            @Override
            public void run() {
              if(viewer.getControl().isDisposed())
                return;
              TreeNode node = getTreeNode(parentElement);
              viewer.expandToLevel(EditorUtils.formPathOrElement(viewer.getContentProvider(),viewer.getInput(), node), 1);
            }
          });
        }
      }, false);
      return new Object[0];
    }else{
      if(TreeState.isProjectLoadable(ts))
        return new Object[0];
      ArrayList<TreeNode> result = new ArrayList<>();
      for (TreeNode node : parent.getSortedShownChildren()) {
        if (filterNode(node)) {
          result.add(node);
        }
      }
      return result.toArray(new TreeNode[0]);
    }
  }

  @Override
  public Object getParent(Object element) {
    if (element instanceof TreeNode) {
      TreeNode node = (TreeNode) element;
      if (node.isDisposed()) {
        return null;
      }
      if (node.getTreeDB().getRootNodeUUID().equals(node.getWorkParentUUID())) {
        return EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(node.getTreeDB());
      }
      try {
        return node.getShownParentExc();
      }catch(NodeNotFoundException npe) {
      }
    }
    return null;
  }

  @Override
  public boolean hasChildren(Object element) {
    TreeNode node = getTreeNode(element);
    if (node == null) return false;
    List<UUID> chi = node.getWorkChildrenUUIDs(); //TODO nodeLoaded?
    if (chi.size() == 0) return false;
    TreeDB db = node.getTreeDB();
    int x = chi.size();
    for (UUID o : chi) {
      try {
        TreeNode nde = db.getCachedNodeOrVirt(o);
        if (nde != null && !staticFilterNode(nde)) x--;
      } catch (RuntimeException re) {
        RLogger.logError(re);
        x--;
      }
    }
    return x > 0;
  }
}
