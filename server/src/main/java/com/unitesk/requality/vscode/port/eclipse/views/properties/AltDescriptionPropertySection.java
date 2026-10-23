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
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.events.TreeNodeChangeEvent.TreeNodeChangeEventType;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.handlers.location.ILocationTab;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.vscode.port.eclipse.views.panels.ElementDescriptionPanel;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.CoreUtils;

public class AltDescriptionPropertySection extends TreeNodePropertiesTab implements ILocationTab {

  private ElementDescriptionPanel descr;
  private TreeViewer locations;
  private GridData locationsD;
  private Requirement req;
  private Composite par, locholder;
  private Label descLabel;

  public ITreeChangeListener tcl =
      new DefaultTreeChangeListener() {
        private boolean relates(TreeNode node) {
          if (req == null || locations == null) return false;
          return node.getUUId().equals(req.getUUId()) || req.getLocations(true).contains(node);
        }

        private boolean relates(NodeDesc node) {
          if (req == null || locations == null) return false;
          if (node.getUUId().equals(req.getUUId())) return true;
          //TODO check
          for (ReferenceValue l : req.getLocationQIds()) if (l.getRaw().equals(node.getSUUId()))
            return true;
          return false;
        }

        @Override
        public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent) {
          if (req == null || locations == null) return;
          if (isDisposed) {
            if (req != null) req.getTreeDB().removeListener(tcl);
            return;
          }
          //TODO needed?
          if (relates(newParent)) updateNode(newParent);
          if (relates(oldParent) && !newParent.getUUId().equals(oldParent.getUUId()))
            updateNode(oldParent);
          if (relates(node)) updateNode(node);
        }

        @Override
        public void deleted(final TreeNode nodeDesc) {
          if (req == null || locations == null) return;
          if (isDisposed) {
            if (req != null) req.getTreeDB().removeListener(tcl);
            return;
          }
          if (!relates(nodeDesc)) return;
          final TreeViewer locations = AltDescriptionPropertySection.this.locations;
          final String type = nodeDesc.getType();  
          boolean isLoc = type.equals(Location.TYPE_NAME);
          if (type.equals(Requirement.TYPE_NAME)
              && req.getUUId().equals(nodeDesc.getUUId())||
              isLoc){
            boolean refresh = false;   
            if(isLoc) {
              for(ReferenceValue rv: req.getLocationQIds())
                if(rv.getRaw().equals(nodeDesc.getSUUId()))
                  refresh = true;
            }else
              refresh = true;
            PlatformUI.getWorkbench()
              .getDisplay()
              .asyncExec(
                  new Runnable() {
                    public void run() {
                      if(isDisposed || locations.getControl().isDisposed())
                        return;
                      locations.refresh();
                    }
                  });
            }
        }

        @Override
        public void created(TreeNode node, byte flag) {
          if (req == null || locations == null) return;
          if (isDisposed) {
            if (req != null) req.getTreeDB().removeListener(tcl);
            return;
          }
          if (!relates(node)) return;
          if (req.getLocations(true).contains(node)) updateNode(node);
        }

        @Override
        public void changed(
            TreeNode node, ChangesSet<Attribute> attrs) {
          if (req == null || locations == null) return;
          if (isDisposed) {
            if (req != null) req.getTreeDB().removeListener(tcl);
            return;
          }
          if (attrs.contains(TreeNode.ATTR_DESCRIPTION)
              || attrs.contains(Requirement.ATTR_LOCATIONS)) updateNode(node);
        }

        private void updateNode(final TreeNode node) {
          final Requirement req = AltDescriptionPropertySection.this.req;
          final TreeViewer locations = AltDescriptionPropertySection.this.locations;
          WorkspaceJob j = new WorkspaceJob("Update node information on description panel for node "+node.getUserVisibleName()){

            @Override
            public IStatus runInWorkspace(IProgressMonitor monitor) {
              TreeNode toup = null;
              if(isDisposed)
                return Status.CANCEL_STATUS;
	          if (node.getType().equals(Requirement.TYPE_NAME)
	              && req.getUUId().equals(node.getUUId())) 
	            toup = node;
	          if (node.getType().equals(Location.TYPE_NAME)) {
	            for(ReferenceValue rv:req.getLocationQIds()){
	              if(rv.hasErrorsOnResolve(req.attrs()))
	                continue;
	              UUID[]nds = rv.getNodeUUIDs(req.attrs());
	              if(nds[0].equals(node.getUUId()))
	                toup = req;
	            }
	          }
	          TreeNode ftoup = toup;
	          if(toup!=null)
	            PlatformUI.getWorkbench()
	              .getDisplay()
	              .asyncExec(
	                  new Runnable() {
	                    public void run() {
                          if(locations!=null){
                              if (isDisposed || locations.getControl().isDisposed()) {
                                if (req != null) req.getTreeDB().removeListener(tcl);
                                return;
                              }else
                                locations.refresh(ftoup, true);
                            }
	                    }
	                  });
			      return Status.OK_STATUS;
			    }
			    
			  };
     	  j.schedule();
        }

        @SuppressWarnings("unchecked")
        @Override
        public void changed(List<TreeNodeChangeEvent> changes) {
          int cnt = 0;
          for (TreeNodeChangeEvent ch : changes) {
            TreeNode node = ch.getTreeNodeTarget();
            if (node != null && relates(node)) {
              cnt++;
              /*switch (ch.getType()) {
              	case TreeNodeChangeEventType.CHANGE:
              		break;

              	case TreeNodeChangeEventType.CREATE:
              		break;

              	case TreeNodeChangeEventType.MOVE:
              		break;
              }*/
            }
            NodeDesc nddc = ch.getNodeDescTarget();
            if (nddc != null && relates(nddc)) {
              if (ch.getType() == TreeNodeChangeEventType.DELETE) cnt++;
              //deleted(nddc, (Boolean)ch.getArguments()[0]);
            }
          }
          if (cnt > 2) refresh();
          else {
            for (TreeNodeChangeEvent ch : changes) {
              byte flag = ch.getFlag();
              TreeNode node = ch.getTreeNodeTarget();
              if (node != null && relates(node)) {
                switch (ch.getType()) {
                  case CHANGE:
                    if(ch.getMoment() == Moment.AFTER)
                      changed(
                        node,
                        (ChangesSet<Attribute>) ch.getArguments()[0]);
                    break;

                  case CREATE:
                    if(ch.getMoment() == Moment.AFTER)
                      created(node, flag);
                    break;
                    
                  case MOVE:
                    if(ch.getMoment() == Moment.AFTER)
                    //addGuiChange(new TreeNodeChangeEvent(TreeNodeChangeEventType.MOVE, node, internal, oldParent == null ? node.getParent(true) : oldParent));
                      moved(
                        node,
                        (TreeNode) ch.getArguments()[0],
                        (TreeNode) ch.getArguments()[1]);
                    break;
                  case DELETE:{
                    if(ch.getMoment() == Moment.AFTER)
                      deleted(ch.getTreeNodeTarget());
                  }break;
                  default:
                }
              } else if (node != null && ch.getType() == TreeNodeChangeEventType.MOVE) {
                //TODO Maybe microreduction here to be made
                if (relates(node.getWorkParent()) || relates((TreeNode) ch.getArguments()[0]))
                  if(ch.getMoment() == Moment.AFTER)
                    moved(
                      node,
                      (TreeNode) ch.getArguments()[0],
                      (TreeNode) ch.getArguments()[1]);
              }
              TreeNode nddc = ch.getTreeNodeTarget();
              if (nddc != null && relates(nddc)) {
                if (ch.getType() == TreeNodeChangeEventType.DELETE)
                  if(ch.getMoment() == Moment.AFTER)
                    deleted(nddc);
              }
            }
          }
        }
      };

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors
    parent.getParent().setLayout(new GridLayout(1, true));
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = getWidgetFactory();

    //parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setLayout(new GridLayout(1, true));

    //descLabel = wf.createLabel(parent, "Alternative description:");

    descr =
        new ElementDescriptionPanel(
            wf,"Alternative description", parent, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    descr.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    descr.getEditControl().addFocusListener(getDescriptionFocusAdapter(descr, descr.getEditControl(), node));

    par = parent;
    locholder = new Composite(parent, SWT.NONE);
    locholder.setLayout(new GridLayout(1, true));
    locationsD = new GridData(GridData.FILL_BOTH);
    locholder.setLayoutData(locationsD);
    wf.createLabel(locholder, "Locations:");
    locations = new TreeViewer(locholder, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
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
            TreeNode node = (TreeNode) parentElement;
            ArrayList<Object> result = new ArrayList<>();
            if (node.getType().equals(Document.TYPE_NAME)) {
              if (req != null)
                for (int i = 0; i < node.sizeWorkChildren(); i++) {
                  Location loc = (Location) node.getWorkChild(i);
                  if (Arrays.asList(loc.getRequirements()).contains(req)) {
                    result.add(loc);
                  }
                }
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
            if (selection.getFirstElement() instanceof Document) {
              Document doc = (Document) selection.getFirstElement();
              input = new NodeEditorInput(doc, ReqMarker.ID, fsid);
            } else if (selection.getFirstElement() instanceof Location) {
              Location loc = (Location) selection.getFirstElement();
              input = new NodeEditorInput(loc.getWorkParent(),loc, ReqMarker.ID, fsid);
              input.setData(loc);
            } else {
              return;
            }

            try {
              String docId = input.getStorage().getNode().getQualifiedId();
              TreeNode node = (TreeNode) input.getData();
              
              IEditorReference ref = RequalityPlugin.findMarkEditorForDocument(docId);
              if (ref == null || ref.getEditor(true) == null) 
                IDE.openEditor(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                    input,
                    ReqMarker.ID,
                    false);
              if(node!=null)
                setManagerSelection(node, true);
          //    SelectionManager.getOne().changeActiveSource(fsid.getEventSource(), fsid, true);
              
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
    Menu menu = mgr.createContextMenu(locations.getControl());
    locations.getControl().setMenu(menu);
    site.registerContextMenu(mgr, site.getSelectionProvider());
  }

  public void refreshLocations() {
    setTreeNode(req);
  }


  @Override
  protected void setTreeNode(TreeNode node) {
    if (isDisposed) return;
    if (req != null) req.getTreeDB().removeGUIListener(tcl);
    req = toRequirement(node);
    if (req == null || locations == null || locations.getControl().isDisposed()) {
      return;
    }
    descr.setFirstLabel(
        (req.getType().equals(Requirement.TYPE_NAME) ? "Alternative d" : "D") + "escription");
    //descLabel.getParent().layout();
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.grabExcessHorizontalSpace = true;
    gd.grabExcessVerticalSpace = true;
    if (par.getSize().x > 300) gd.widthHint = par.getSize().x - 300;
    else gd.widthHint = par.getSize().x;
    descr.getControl().setLayoutData(gd);
    if (req != null){
        req.getTreeDB().addGUIListener(tcl);
        final Requirement req = this.req;
        final ElementDescriptionPanel descr = this.descr;
        final TreeViewer locations = this.locations;
        final Composite locholder = this.locholder;
        final GridData locationsD = this.locationsD;
        WorkspaceJob j = new WorkspaceJob("Init information for descriptions panel on "+req.getUserVisibleName()){
    
          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
            if(req == null || isDisposed)
              return Status.CANCEL_STATUS;
            String rawdesc = req.getRawDescription(), desc = req.getDescription(true, true, false);
            EclipseAPI.getDisplay().asyncExec(new Runnable(){
            
              @Override
              public void run() {
                if(isDisposed || descr.getControl().isDisposed())
                  return;
                descr.setDocument(rawdesc,desc); 
              }
            });
	        Set<com.unitesk.requality.nodetype.Document> docs =
		        new HashSet<>();
		    for (Location l : req.getLocations(false)) {
		      if (!docs.contains(l.getWorkParent())) {
		        docs.add((com.unitesk.requality.nodetype.Document) l.getWorkParent());
		      }
		    }
		    //locholder.setParent(par);
		    //locholder.setVisible(true);
		    //locholder.setLayoutData(new GridData(GridData.FILL_BOTH));
		    //par.layout();
		    boolean next = true;
		    if (!req.getType().equals(Requirement.TYPE_NAME) && !req.getType().equals(TextNode.TYPE_NAME))
		      next = false;
		    boolean fnext = next;
		    EclipseAPI.getDisplay().asyncExec(new Runnable(){
		    
		      @Override
		      public void run() {
		        if(isDisposed || locations.getControl().isDisposed())
		          return;
		        if (fnext) {
		          locations.setInput(docs.toArray());
		          locations.expandAll();
		        }
		        if (fnext != locholder.isVisible()) {
		          locholder.setVisible(fnext);
		          locationsD.exclude = !fnext;
		          locholder.getParent().getParent().layout(true, true);
		        }
		     
		      }
		    });
		   
		    return Status.OK_STATUS;
		  }
		  
		};
		j.schedule();
	}
  }

  @Override
  public void aboutToBeHidden() {
    if (req != null) req.getTreeDB().addGUIListener(tcl);
  };

  @Override
  public Map<Location, List<Requirement>> getLocationsAndRequirements() {
    Map<Location, List<Requirement>> map = new HashMap<>();
    ISelection sel = locations.getSelection();
    if (!locations.getControl().isFocusControl()
        || req == null
        || !(sel instanceof IStructuredSelection)) {
      return null;
    }
    IStructuredSelection ssel = (IStructuredSelection) sel;
    @SuppressWarnings("unchecked")
    java.util.Iterator<Object> it = ssel.iterator();
    List<Location> toremove = new ArrayList<>();
    while (it.hasNext()) {
      Object o = it.next();
      if (o instanceof Location) {
        if (!toremove.contains(o)) toremove.add((Location) o);
      } else if(o instanceof Document) {
        for(Location l:req.getLocations(true))//TODO load?
          if(l.getDocument() == o || l.getDocuments().length>1&&l.connectedToDocument((Document)o))
            toremove.add(l);
      }
    }
    for (Location l : toremove) {
      if (!map.containsKey(l)) map.put(l, new ArrayList<Requirement>());
      List<Requirement> list = map.get(l);
      if (!list.contains(req)) list.add(req);
      map.put(l, list);
    }
    return map.size() > 0 ? map : null;
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_TYPE.equals(a)
        || TreeNode.ATTR_ID.equals(a)
        || TreeNode.ATTR_NAME.equals(a)
        || TreeNode.ATTR_DESCRIPTION.equals(a)
        || Requirement.ATTR_LOCATIONS.equals(a);
  };

  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_TYPE);
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
    ids.add(TreeNode.ATTR_DESCRIPTION);
    ids.add(Requirement.ATTR_LOCATIONS);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s))
        return true;
    return false;
  }

  @Override
  public Control getControl() {
    return descr.getControl();
  };
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
