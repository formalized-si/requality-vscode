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
package com.unitesk.requality.vscode.port.eclipse.views.properties;

import com.unitesk.requality.vscode.ui.commands.operations.OperationHistoryEvent;
import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistoryListener;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.operations.UndoRedoActionGroup;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.events.FocusAdapter;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.tabbed.AbstractPropertySection;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.ITreeNodeProvider;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionAdapter;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.LocationRequirementConverter;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.SelectionFromSource;
import com.unitesk.requality.vscode.port.eclipse.views.panels.ElementDescriptionPanel;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public abstract class TreeNodePropertiesTab extends AbstractPropertySection implements IFSIdProvider {
  protected TreeNode node;
  protected boolean isDisposed = true;
  boolean listenerWasRemoved = false;
  protected  FSId fsid;
  private IUIStateChangeListener list = new IUIStateChangeListener() {

    @Override
    public void newActiveNodes(FSId source, SelectionRecord selr) {
      SelectionManager.getOne().removeSourceListener(fsid.getEventSource(), fsid, list);
      listenerWasRemoved = true;
      boolean br = true;
      try {
        FSId realSource = source.getRootId();
        if(realSource.isSameAs(fsid) || realSource.getEventSource() == EventSource.PROPERTIES)
          return;
        Object selectionSource = source.getObject();
        Object selO = selr.getFirstObject();
        Iterator<Collection<TreeNode>> it = selr.selectionByType.values().iterator();
        Collection<TreeNode> nds = null;
        if(it.hasNext())
          nds = it.next();
        if(nds==null || nds.iterator().next() == null){//TODO set selection empty
            Object o = selr.getFirstObject();
            if(o != null) {
              if(o instanceof IProject) {
                EclipseAPI.getDisplay().asyncExec(new Runnable() {
                  @Override
                  public void run() {
                    if(isDisposed)
                      return;
                    setNotTreeNodeInput(o);
                  }
                });
                return;
              }
            } else {
              IWorkbenchPart part = selectionSource instanceof IWorkbenchPart?(IWorkbenchPart) selectionSource:null;
              ISelection sel = StructuredSelection.EMPTY;
  
              EclipseAPI.getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                  if(isDisposed)
                    return;
                  if(page!=null) {
                    page.selectionChanged(part,sel);
                  }else
                    setInput(part, sel);
                }
              });
            }
          return;
        }
        TreeNode node = nds.iterator().next();
        if(node == null)
          return;
        br = false;
        EclipseAPI.getDisplay().asyncExec(new Runnable() {
          @Override
          public void run() {
        	if(isDisposed)
              return;
            if(!listenerWasRemoved) {
              SelectionManager.getOne().removeSourceListener(fsid.getEventSource(), fsid, list);
              listenerWasRemoved = true;
            }
            if(node.isRoot() && selO!=null) {
              if(selO instanceof IProject)
                setNotTreeNodeInput(selO);
              return;
            }
            try {
              IWorkbenchPart part = selectionSource instanceof IWorkbenchPart?(IWorkbenchPart) selectionSource:null;
              ISelection sel = selO!=null?
            		  new SelectionFromSource(new Object[] {selO}, source):
                      new SelectionFromSource(node, source);
              if(page!=null && part!=null)
                page.selectionChanged(part,sel);
              else
                setInput(part, sel);
            }finally {
              if(listenerWasRemoved) {
                SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, list);
                listenerWasRemoved = false;
              }
            }
          }
        });
      }finally {
        if(listenerWasRemoved) {
          SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, list);
          listenerWasRemoved = false;
        }
      }
    }

  };
  ElementDescriptionPanel descr;
  
  INodeChangeListener nodeChangeListener = new PropertyTabNodeChangeListener();
  private DescriptionFocusAdapter standardDescAdapter;
  
  protected FocusAdapter getDescriptionFocusAdapter(ElementDescriptionPanel descr, Text adesc, TreeNode node) {
    if (standardDescAdapter == null) standardDescAdapter = new DescriptionFocusAdapter();
    standardDescAdapter.setViewer(adesc, toRequirementOrNode(node));
    this.descr = descr;
    return standardDescAdapter;
  }
  @Override
  public FSId getFSId() {
    return fsid;
  }

  private class DescriptionFocusAdapter extends FocusAdapter {
    private Text adesc;
    private TreeNode node;

    public void setViewer(Text adesc2, TreeNode node2) {
      adesc = adesc2;
      node = node2;
    }

    public void focusLost(FocusEvent e) {
      super.focusLost(e);
      if (updating) return;
      //if new attribute text is "", then remove this attribute rather than set it to "",
      //because otherwise we get modified .json-files with empty attribute added,
      //when simply navigating nodes in Requality Explorer and Properties View,
      //and this is affecting .svn
      if (node == null) {
        return;
      }
      String text = adesc.getText();
      if(!descr.isDirty() || text == null)
        return;
      final TreeNode node = toRequirementOrNode(TreeNodePropertiesTab.this.node);
      final Text adesc = DescriptionFocusAdapter.this.adesc;
      WorkspaceJob j = new WorkspaceJob("Change description for "+node.getUserVisibleName()){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(node == null)
            return Status.CANCEL_STATUS;
          String oldDescr = node.getRawDescription();
          if (oldDescr == null) {
            oldDescr = "";
          }
          String newDescr = CoreUtils.htmlEncapsulate(text);
          if (oldDescr.equals(newDescr)) {
            return Status.CANCEL_STATUS;
          }
          String foldDescr = oldDescr;
          boolean started = node.getTreeDB().startTransaction("Update " + node.getTypeLabel(), true);
          if (started) {
            try {
              node.setDescription(newDescr);
              if(node.attrs().getChanged().contains(TreeNode.ATTR_DESCRIPTION)){
                node.commitAttributes();
                if(started) {
                  started = false;
                  node.getTreeDB().commit();
                }
              }else{
                EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(adesc.isDisposed() || isDisposed)
                      return;
                    adesc.setText(foldDescr);
                  }
                });
              }
            }finally {
              if(started) 
                node.getTreeDB().rollback();
            }
          } else EclipseAPI.getDisplay().asyncExec(new Runnable(){
            @Override
            public void run() {
              if(isDisposed || adesc.isDisposed())
                return;
              adesc.setText(foldDescr);
            }
          });
          return Status.OK_STATUS;
        }
        
      };
      j.schedule();
      
      //TODO else cancel
    }

    private boolean isDirty() {
      return descr.isDirty();
    }

    public void setTreeNode(TreeNode node2) {
      node = node2;
    }
  }

  private class PropertyTabNodeChangeListener implements INodeChangeListener {

    @Override
    public void attributeChange(TreeNode source, final String what,Attribute old) {
      
      attributesChange(
          source,
          new ChangesSet<>(Collections.emptySet(),AttributeStorage.formSet(what),Collections.emptySet(),
              AttributeStorage.formAttributeMap(old),
              AttributeStorage.formAttributeMap(node.attrs().getAttribute(what)),AttributeStorage.formList(what)));
    }

    @Override
    public void deleted(TreeNode source) {
      Display d = Display.getCurrent();
      if (d == null) {
        d = EclipseAPI.getDisplay();
      }
      updating = true;
      if (node == null || source==null||(source.getQualifiedId(true) + "/").contains(node.getQualifiedId(true) + "/")) 
        d.asyncExec(
          new Runnable() {
            @Override
            public void run() {
              {
                node = null;
                setManagerSelection(null, false);
                if (isDisposed || page.getControl().isDisposed()) {
                  return;
                }
                setTreeNode(null);
              }
            }
          });
    }

    @Override
    public void moved(TreeNode source, TreeNode oldParent) {
      // TODO Auto-generated method stub

    }

    @Override
    public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
      if (node == null) {
        setManagerSelection(source, false);//TODO WTF
        setTreeNodeSafe(source);
      }else if (TreeNodePropertiesTab.this.requireUpdate(source,what)) {
        boolean af = false;
        for (String s : what.getAll()) if (affectedByAttribute(s)) af = true;
        if (af) {
          setManagerSelection(source, false);
          setTreeNodeSafe(source);
        }
      }
    }

    @Override
    public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
      // TODO Auto-generated method stub

    }
  }

  ITreeStateListener treeStateListener =
      new ITreeStateListener() {
        @Override
        public void open(TreeDB db) {
          if(node!=null && node.getTreeDB() == db) {
            setManagerSelection(node, false);
            setTreeNodeSafe(node);
          }
        }

        @Override
        public void close(TreeDB db) {
          if(node!=null && node.getTreeDB() == db)
          {
            node = null;//close on project close
            setManagerSelection(null, false);
            setTreeNodeSafe(null);
          }
        }

        @Override
        public void delete(String lname, String pname, TreeDB db) {}

        @Override
        public void settingsChanged(TreeDB db) {
          // TODO Auto-generated method stub
          
        }
      };
  protected TabbedPropertySheetPage page;
  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    page =  aTabbedPropertySheetPage;
    super.createControls(parent, aTabbedPropertySheetPage);
    IWorkbenchWindow win = PlatformUI.getWorkbench()
        .getActiveWorkbenchWindow();
    if(win!=null){
      IWorkbenchPage page2 =
          win.getActivePage();
      if(page2 != null){
        IWorkbenchPart part = page2
            .getActivePart();
        if(part != null){
          IWorkbenchPartSite site =
                   part.getSite();
          if(site != null){
            IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
            new UndoRedoActionGroup(site, context, true).fillActionBars(page.getSite().getActionBars());
          }
        }
      }
    }
    isDisposed = false;
  }

  public abstract boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what);
  
  @Override
  public boolean shouldUseExtraSpace() {
    return true;
  }

  public TreeNodePropertiesTab() {
    super();
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.PROPERTIES,this,true);
    SelectionManager.getOne().registerSelectionAdapter(fsid, new ISelectionAdapter() {
      @Override
      public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId source, SelectionRecord visRecord,
          SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel) {
        if(source!=fsid)
          return new Pair<>(visRecord, visRecord);
        return new Pair<>(visRecord, storedSel);
      }
    });
    SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.EXPLORER, LocationRequirementConverter.getOne(false, false));
    SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.LCNF, LocationRequirementConverter.getOne(false, false));
    SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, list);
    //TODO for unieditor?
  }

  protected void disposedActions(boolean finalDispose) {
    SelectionManager sman = SelectionManager.getOne();
    if(listenerWasRemoved && !finalDispose) {
      SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, list);
      listenerWasRemoved = false;
      //sman.removeSourceListener(fsid.getEventSource(), fsid, list);
    //  listenerWasRemoved = true;
    }
    if(finalDispose) {
      if(!listenerWasRemoved) {
        SelectionManager.getOne().removeSourceListener(fsid.getEventSource(), fsid, list);
        listenerWasRemoved = true;
      }
      sman.unregisterInstance(fsid);
    }
    //sman.deActivateSource(SourcePriority.SOURCE_CHANGE, EventSource.PROPERTIES, fsid, isSelectionSource());
    if (node != null) {
      node.removeGuiChangeListener(nodeChangeListener);
      if(!finalDispose)
        setManagerSelection(null, false);
    }
    //TODO check
    EclipseTracker.getTracker(DBNamesManager.DEFAULT).removeListener(Moment.AFTER,treeStateListener);
  }
  
  @Override
  public void aboutToBeHidden() {
    super.aboutToBeHidden();
    disposedActions(false);
  }
  
  @Override
  public void aboutToBeShown() {
    super.aboutToBeShown();
    SelectionManager sman = SelectionManager.getOne();
    //get selection of last active properties
    FSId id = sman.getFirstActiveSource(EventSource.PROPERTIES);
    if(id!=fsid)
      sman.changeActiveSource(SourcePriority.PROPERTIES, EventSource.PROPERTIES, fsid, isSelectionSource(), 
        sman.getActiveSource() == EventSource.PROPERTIES);
    SelectionRecord sr = sman.getSelection();
//  sman.registerInstance(fsid.getEventSource(), fsid.getObject(), fsid);
  //sman.activateSource(SourcePriority.SOURCE_CHANGE, EventSource.PROPERTIES, fsid, isSelectionSource(), false);
    if(sr != null) {
      TreeNode sel = sr.getFirstNode();
      if(sel != null)
        if(node == null || !sel.getUUId().equals(node.getUUId())) {
          setManagerSelection(sel, false);
          setTreeNodeSafe(sel);
        }
    }
    EclipseTracker.getTracker(DBNamesManager.DEFAULT).addListener(Moment.AFTER, treeStateListener);
  }
  @Override
  public void dispose() {
    isDisposed = true;
    setManagerSelection(null, false);
    super.dispose();
    disposedActions(true);
    node = null;
  }

  protected boolean updating = false;
  
  protected void setManagerSelection(TreeNode node, boolean changesource) {
    JobManager.jm.startBaseJob("Update selection for "+fsid.getFullIdString(), new RunnableWithMonitor() {
      @Override
      public void run(com.unitesk.requality.core.operation.IProgressMonitor pm) {
        try {
          SelectionManager sman = SelectionManager.getOne();
          SelectionRecord sr = sman.getLastSelection(fsid);
          if((sr == null || sr.getFirstNode()!=node)){
            sman.updateSelection(SourcePriority.PROPERTIES, fsid, node);
//            if(changesource)
//              sman.changeActiveSource(SourcePriority.PROPERTIES, fsid.getEventSource(), fsid, isSelectionSource());
          }
        }catch(Exception e) {
          RLogger.logError("Problem during setting manager selection");
          RLogger.logError(e);
        }
      }
    }, false);
  }

  protected boolean checkNodeChanges(final TreeNode newNode){
    if(newNode!=null&&node==null || node!=newNode)
      return true;
    return false;
  }
  private void setTreeNodeSafe(final TreeNode newNodeNF) {
    if (node != null) {
      node.removeGuiChangeListener(nodeChangeListener);
    }
    Display d = Display.getCurrent();
    if (d == null) {
      d = EclipseAPI.getDisplay();
    }
    updating = true;
    TreeNode newNode = toRequirementOrNode(newNodeNF);
    if(newNode != null && !newNode.isDisposed())
      d.asyncExec(
        new Runnable() {
          @Override
          public void run() {
            TreeNode anode = null;
            try {
              //  TreeNodePropertiesTab.this.node = newNode;
              if (!isDisposed && !newNode.isDisposed()) {
                anode = toRequirementOrNode(newNode);
                TreeNodePropertiesTab.this.node = anode;
                if (anode == null) {
                  updating = false;
                  return;
                }
                if (standardDescAdapter != null)
                  standardDescAdapter.setTreeNode(anode);
                if (isDisposed) {
                  updating = false;
                  return;
                }
                setManagerSelection(anode, false);//TODO mb true
                setTreeNode(anode);
                updating = false;
              }
            }finally {
              if (anode != null && !isDisposed) anode.addGuiChangeListener(nodeChangeListener);
            }
          }
        });
  }

  protected abstract void setTreeNode(TreeNode node);

  @Override
  public void setInput(IWorkbenchPart part, ISelection selection) {
    super.setInput(part, selection);
    TreeNode node = TreeNodePropertiesTab.toTreeNode(selection);
    setManagerSelection(node, false);//not update actual stored sels
    if (node != null) {

      PlatformUI.getWorkbench().getOperationSupport().getOperationHistory().addOperationHistoryListener(new IOperationHistoryListener(){

        @Override
        public void historyNotification(OperationHistoryEvent event) {
          refresh();
        }
      });
      setTreeNodeSafe(node);
    }else
      setNotTreeNodeInput(getObject(selection));
  }

  protected void setNotTreeNodeInput(Object object) {
    //method to handle not-treenode selections
  }

  protected static TreeNode toRequirementOrNode(TreeNode newNode) {
    TreeNode node = toRequirement(newNode);
    if (node != null) return node;
    return newNode;
  }

  protected static Requirement toRequirement(TreeNode node) {
    if (node instanceof Requirement) {
      return (Requirement) node;
    }

    if (node instanceof Location) {
      Location location = (Location) node;
      Requirement[] reqs = location.getRequirements(true);
      if (reqs == null) return null;
      if (reqs.length > 0) {
        return reqs[0];
      }
    }
    return null;
  }
  public static Object getObject(ISelection sel) {
    if (sel == null) return null;
    Object o = ((IStructuredSelection) sel).getFirstElement();
    return o;
  }
  public static TreeNode toTreeNode(ISelection sel) {
    if (sel == null) return null;
    Object o = ((IStructuredSelection) sel).getFirstElement();
    if (o instanceof ITreeNodeProvider) return ((ITreeNodeProvider)o).getNode();
    if (o instanceof Object[]) {
      Object[]objs = (Object[]) o;
      if(objs.length>0 && objs[0]!=null && objs[0] instanceof TreeNode)
        return (TreeNode) objs[0];
    }
    if (o instanceof TreeNode) return (TreeNode) o;
    if (o instanceof IProject) {
      IProject p = (IProject) o;
      TreeDB db = null;
      try {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        db = et.getTreeDB(p, null, et.getLocalName(p));
      } catch (IOException e) {
        // TODO Auto-generated catch block
        RLogger.logError(e);
      }
      if (db != null) {
        TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
        TreeState ts = tsm.getState(db.getLocalName(),tsm.getRevisionInfo(db));
        if(ts == TreeState.READY)
          return db.getRootNode();
        return null;
      }
    }
    return null;
  }

  public boolean isSelectionSource() {
    return false;
  }
  
  protected boolean affectedByAttribute(String a) {
    return false;
  }
  
}
