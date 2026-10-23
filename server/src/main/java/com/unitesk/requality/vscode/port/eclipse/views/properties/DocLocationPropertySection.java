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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreePath;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.handlers.location.ILocationTab;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ModuleEditor;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.UniEditor;
import com.unitesk.requality.vscode.port.eclipse.views.TochHandler;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class DocLocationPropertySection extends TreeNodePropertiesTab implements ILocationTab {

  private TreeViewer locations;
  private Document doc;

  private TochHandler toch;
  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    parent.setLayout(new GridLayout());
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));

    locations = new TreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
    toch = new TochHandler(locations);
    locations.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    locations.setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
    locations.setComparator(new RequalitySorter());
    locations.setContentProvider(
        new ITreeContentProvider() {
          @Override
          public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

          @Override
          public void dispose() {}

          @Override
          public boolean hasChildren(Object element) {
            TreeNode node = (TreeNode) element;
            if(node.getType().equals(Requirement.TYPE_NAME))
              return false;
            if (node instanceof Location) {
              return ((Location) node).getRequirements().length > 0;
            }
            return node.sizeWorkChildren() > 0;
          }

          @Override
          public Object getParent(Object element) {
            return ((TreeNode) element).getWorkParent();
          }

          @Override
          public Object[] getElements(Object inputElement) {
            return (Object[]) inputElement;
          }

          @Override
          public Object[] getChildren(Object parentElement) {
            Document docNow = doc;
            TreeNode node = (TreeNode) parentElement;
            if(docNow == null)
              return new Object[0];
            ArrayList<Object> result = new ArrayList<>();
            if (node.getType().equals(Location.TYPE_NAME)) {
              Location l = (Location) node;
              if (docNow.equals(l.getWorkParent()))
                for (Requirement r : l.getRequirements(true)) if (r != null) result.add(r);
            }
            return result.toArray();
          }
        });
    locations.addDoubleClickListener(
        new IDoubleClickListener() {

          @Override
          public void doubleClick(DoubleClickEvent event) {
            long time = CoreUtils.getTime();
            IStructuredSelection selection = (IStructuredSelection) locations.getSelection();
            NodeEditorInput input;
            if (selection.getFirstElement() instanceof Requirement) {
              Requirement doc = (Requirement) selection.getFirstElement();
              input = new NodeEditorInput(doc, ModuleEditor.ID, fsid);
              try {
                IDE.openEditor(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                    input,
                    UniEditor.ID,
                    false);
              } catch (PartInitException e) {
                e.printStackTrace();
              }
              return;
            } else if (selection.getFirstElement() instanceof Location) {
              Location loc = (Location) selection.getFirstElement();
              input = new NodeEditorInput(loc.getWorkParent(), ReqMarker.ID,fsid);
              input.setData(loc);
            } else {
              return;
            }

            try {
              String docId = input.getStorage().getNode().getQualifiedId();
              IEditorReference ref = RequalityPlugin.findMarkEditorForDocument(docId);

              TreeNode node = (TreeNode) input.getData();
              
              if (ref == null || ref.getEditor(true) == null) 
                IDE.openEditor(
                  PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                  input,
                  ReqMarker.ID,
                  false);
              setManagerSelection(node, true);
              //SelectionManager.getOne().changeActiveSource(fsid.getEventSource(), fsid, true);
            } catch (PartInitException e) {
              e.printStackTrace();
            } catch (CoreException e) {
              e.printStackTrace();
            }
          }
        });
    IViewPart view =
        PlatformUI.getWorkbench()
            .getActiveWorkbenchWindow()
            .getActivePage()
            .findView("org.eclipse.ui.views.PropertySheet");
    if (view == null) return;
    IWorkbenchPartSite site = view.getSite();
    MenuManager mgr = new MenuManager();
    mgr.setRemoveAllWhenShown(true);
    site.registerContextMenu(mgr, site.getSelectionProvider());
    locations.getControl().setMenu(mgr.createContextMenu(locations.getControl()));
  }

  public Document getDocument() {
    return doc;
  }

  @Override
  public void aboutToBeHidden() {
    Document docNow = doc;
    if (docNow != null) docNow.getTreeDB().removeGUIListener(tcl);
  };

  public ITreeChangeListener tcl =
      new DefaultTreeChangeListener() {
        @Override
        public void deleted(final TreeNode nodeDesc, byte flag, Moment m) {
          Document docNow = doc;
          if(m == Moment.AFTER)
            return;
          if (docNow == null)
            return;
          if (isDisposed) {
            if (docNow != null) docNow.getTreeDB().removeListener(tcl);
            return;
          }
          //Set<Location> locs=  getLocs();
          boolean update = false;
          if (nodeDesc.getType().equals(Location.TYPE_NAME)
              && !nodeDesc.getWorkParentUUID().equals(docNow.getUUId())) update = true;
          if (nodeDesc.getType().equals(Requirement.TYPE_NAME)) {
            List<Location> ids = ((Requirement)nodeDesc).getLocations(true);
            for (Location loc : ids) 
              if (loc.getWorkParentUUID().equals(docNow.getUUId())) update = true;
          }
          if (update){
            toch.addToch((TreeNode)null);

            Display d = locations.getControl().getDisplay();
            if(!d.isDisposed())
            {
              d.syncExec(new Runnable() {
                
                @Override
                public void run() {
                  if(locations.getControl().isDisposed())
                    return;
                  locations.setInput(getLocs().toArray());
                  locations.expandAll();
                }
              });
            }
          }
        }

        private boolean equals(Object[] input, Set<Location> locs) {
          return new HashSet<>(Arrays.asList(input)).equals(locs);
        }

        @Override
        public void changed(
            TreeNode node, ChangesSet<Attribute> params) {
          Document docNow = doc;
          if (docNow == null)
            return;
          if(node.getSUUId().equals(docNow.getSUUId()) && !params.contains(TreeNode.ATTR_CHILDREN))
            return;
          if (isDisposed) {
            if (docNow != null) docNow.getTreeDB().removeListener(tcl);
            return;
          }
          boolean ch = false;
          if (node.getType().equals(Location.TYPE_NAME)){
            if(node.getWorkParentUUID().equals(docNow.getUUId())){
              updateNode(node);
              ch = true;
            }
          
          }
          if (node.getType().equals(Requirement.TYPE_NAME)) {
            Requirement r = (Requirement) node;
            for (Location l : r.getLocations(true))
              if (docNow.getUUId().equals(l.getWorkParentUUID())){
                toch.addToch((TreeNode)null);
                Display d = locations.getControl().getDisplay();
                if(!d.isDisposed())
                {
                  d.asyncExec(new Runnable() {
                    
                    @Override
                    public void run() {
                      if(isDisposed || locations.getControl().isDisposed())
                        return;
                      locations.setInput(getLocs().toArray());
                      locations.expandAll();
                    }
                  });
                }
                return;
              }
          }
        }

        private void updateNode(final TreeNode node) {
          Document docNow = doc;
          TreeNode[] toup = null;
          if (node.getType().equals(Location.TYPE_NAME)
              && docNow.getUUId().equals(node.getWorkParentUUID()))
            toup = new TreeNode[]{ node };
          if (node.getType().equals(Requirement.TYPE_NAME)) {
            Requirement r = (Requirement) node;
            List<Location> locs = r.getLocations(true);
            toup = new TreeNode[locs.size()];
            int i = 0;
            for (Location l : locs)
              if (docNow.getUUId().equals(l.getWorkParentUUID())) toup[i++] = l;
          }
          if(toup!=null && toup.length>0){
            
            toch.addToch(toup);
          }
        }
      };

  public void refreshLocations() {
    setTreeNode(doc);
  }

  public Set<Location> getLocs(){
    Set<Location> recs = new HashSet<>();
    Document docNow = doc;
    if(docNow!=null)
      for (UUID l : docNow.getChildrenLocations()) {//TODO rewrite
        Location loc = docNow.getLocation(l.toString());
        if (loc != null && !recs.contains(loc)) recs.add(loc);
      }
    return recs;
  }
  ReentrantLock docSetLock = new ReentrantLock();
  @Override
  protected void setTreeNode(TreeNode node) {
    docSetLock.lock();
    try {
      Document docNow = doc;
      if (tcl != null && docNow != null) docNow.getTreeDB().removeGUIListener(tcl);
      docNow = toDocument(node);
      if(docNow == null)
        return;
      final Document docNowFinal = docNow; 
      final TreeViewer locations = this.locations;
      if(docNow == null && locations!=null && !locations.getControl().isDisposed())
        EclipseAPI.getDisplay().asyncExec(new Runnable() {
        @Override
        public void run() {
          if(isDisposed || locations.getControl().isDisposed())
            return;
            locations.setInput(null);
        }
      });
      if (docNow == null || locations == null || locations.getControl().isDisposed()) {
  
        return;
      }
      WorkspaceJob j = new WorkspaceJob("Set Document"){
          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            if(isDisposed)
              return Status.CANCEL_STATUS;
            docSetLock.lock();
            try {
              doc = docNowFinal;
              Object[]arr = getLocs().toArray();
              EclipseAPI.getDisplay().asyncExec(new Runnable(){
            
                @Override
                public void run() {
                  if(isDisposed || locations.getControl().isDisposed())
                    return;
                  locations.setInput(arr);
                  locations.expandAll(); 
                }
              });
              return Status.OK_STATUS;
            }finally{
              if (tcl != null && docNowFinal != null) docNowFinal.getTreeDB().addGUIListener(tcl);
              docSetLock.unlock();
            }
          }
          
      };
      j.schedule();
    }finally {
      docSetLock.unlock();
    }
  }

  protected Document toDocument(TreeNode node) {
    if (node instanceof Document) {
      return (Document) node;
    }

    if (node instanceof Location) {
      Location location = (Location) node;
      Document[] docs = location.getDocuments();
      if (docs.length > 0) {
        return docs[0];
      }
    }
    return null;
  }

  @Override
  public Map<Location, List<Requirement>> getLocationsAndRequirements() {
    Map<Location, List<Requirement>> map = new HashMap<>();
    ISelection sel = locations.getSelection();
    if (!locations.getControl().isFocusControl()
        || doc == null
        || !(sel instanceof IStructuredSelection)) {
      return null;
    }
    TreeSelection tsel = ((TreeSelection) sel);
    for (TreePath tp : tsel.getPaths()) {
      if ((tp.getFirstSegment() instanceof Location)) {
        if ((tp.getLastSegment() instanceof Requirement)) {
          Location l = (Location) tp.getFirstSegment();
          Requirement r = (Requirement) tp.getLastSegment();
          if (!map.containsKey(l)) map.put(l, new ArrayList<Requirement>());
          if (!map.get(l).contains(r)) map.get(l).add(r);
        } else {
          Location l = (Location) tp.getFirstSegment();
          if (!map.containsKey(l)) map.put(l, new ArrayList<Requirement>());
          Requirement[] reqs = l.getRequirements();
          for (Requirement r : reqs) if (!map.get(l).contains(r)) map.get(l).add(r);
        }
      }
    }
    return map.size() > 0 ? map : null;
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_ID.equals(a)
        || TreeNode.ATTR_NAME.equals(a)
        || Requirement.ATTR_LOCATIONS.equals(a);
  }
  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
    ids.add(Requirement.ATTR_LOCATIONS);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s))//TODO iterator check
        return true;
    return false;
  }

  @Override
  public Control getControl() {
    return locations.getControl();
  };
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
