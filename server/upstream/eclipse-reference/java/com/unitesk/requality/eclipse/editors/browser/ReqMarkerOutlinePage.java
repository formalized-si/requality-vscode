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

import java.util.Collection;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.operations.UndoRedoActionGroup;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionAdapter;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.service.DocumentsSocketService;
import com.unitesk.requality.core.service.GenericSocketService;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.Location;

public class ReqMarkerOutlinePage extends ContentOutlinePage
    implements ITabbedPropertySheetPageContributor, IAdaptable, IFSIdProvider, ISelectionAdapter {
  private DocumentsSocketService inputdocs;
  private ReqMarker reqMarker;
  private FSId fsid;
  private ISelectionChangedListener sellistener = new ISelectionChangedListener() {
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
      SelectionManager.getOne().removeSourceListener(fsid.getEventSource(), fsid, list);
      try {
        Object obj = event.getSelection();
        if(obj instanceof StructuredSelection)
        {
          StructuredSelection sel = (StructuredSelection) obj;
          if(sel!=null) {
            Object lo = sel.getFirstElement();
            if(lo!=null && lo instanceof Location) {
              Location l = (Location) lo;
              if(inputdocs.hasRelatedSessions(l.getDocument()))
                setViewerSelection(l);
            //  SelectionManager.getOne().changeActiveSource(fsid.getEventSource(),fsid, false);
              SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, fsid,  l);
            }
          }
        }
      }finally {
        SelectionManager.getOne().addSourceListener(fsid.getEventSource(), fsid, list);
      }
    }
  };
  private IUIStateChangeListener list = new IUIStateChangeListener() {
    
    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      SelectionManager.getOne().removeSourceListener(EventSource.OUTLINE, fsid,  list);
      try {
        //boolean clearSelection = true;
        if(inputdocs!=null)
          try {
            Collection<TreeNode> nds = nodes.get(Location.TYPE_NAME);
            if(nds!=null) {
              if(nds.size()==0)
                return;
              for(TreeNode l: nds) {
                setViewerSelection(l);
                return;
              }
            }else
              return;
//            nds = nodes.get(Requirement.TYPE_NAME);
//            if(nds!=null && nds.length > 0) {
//              Requirement req = (Requirement)nds[0];
//              if(req!=null) {
//                List<Location>locs = req.getLocations(true);
//                for(Location l:locs) {
//                  TreeNode par = l.getWorkParent();
//                  if(inputdocs.containsDoc(par)||inputdocs.getStartDocument()==par)
//                  {
//                    clearSelection = false;
//                    setViewerSelection(l);
//                    return;
//                  }
//                }
//              }
//            }
          }finally {
//            if(clearSelection)
//              setViewerSelection(null);
          }
      }finally {
        SelectionManager.getOne().addSourceListener(EventSource.OUTLINE, fsid,  list);
      }
    }
  };


  public ReqMarkerOutlinePage(ReqMarker reqMarker) {
    this.reqMarker = reqMarker;
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.OUTLINE, this, true);
    //SelectionManager.getOne().registerSelectionConverter(fsid, EventSource.EXPLORER, LocationRequirementConverter.getOne());
  }

  private void setViewerSelection(TreeNode l) {
    EclipseAPI.getDisplay().asyncExec(new Runnable() {
      
      @Override
      public void run() {
        getTreeViewer().removeSelectionChangedListener(sellistener);
        getTreeViewer().setSelection(l!=null?new StructuredSelection(l):StructuredSelection.EMPTY);
        getTreeViewer().addSelectionChangedListener(sellistener);
      }
    });
  }

  @Override
  public void createControl(Composite parent) {
    super.createControl(parent);
    getTreeViewer().addSelectionChangedListener(sellistener);
    getTreeViewer().setContentProvider(new ReqMarkerOutlineContentProvider());
    getTreeViewer()
        .setLabelProvider(
            new LabelProvider() {
              ILabelProvider lp = WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();

              @Override
              public String getText(Object element) {
                if (element instanceof String) {
                  return (String) element;
                }
                if (lp != null) {
                  return lp.getText(element);
                }
                return super.getText(element);
              }

              @Override
              public Image getImage(Object element) {
                if (lp != null) {
                  return lp.getImage(element);
                }
                return super.getImage(element);
              }
            });
    //getTreeViewer().addDoubleClickListener(doubleClickListener);
    getTreeViewer()
        .getControl()
        .addKeyListener(
            new KeyListener() {
              @Override
              public void keyReleased(KeyEvent e) {}

                @Override
                public void keyPressed(KeyEvent e) {
                  if (e.keyCode == SWT.DEL) {
                    new RemoveLocationFromRequirement(reqMarker.getTreeDB(), fsid).run();
                    e.doit = false;
                  }
                }
    });

    if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null) {
      IWorkbenchPart part =
          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
      if (part != null) {
        IWorkbenchPartSite site = part.getSite();
        new UndoRedoActionGroup(site, reqMarker.getUndoContext(), true)
            .fillActionBars(getSite().getActionBars());
      }
    }
    SelectionManager.getOne().addSourceListener(EventSource.OUTLINE, fsid,  list);
  }

  public void setInput(Object input) {
    reqMarker = (ReqMarker) input;
    this.inputdocs = reqMarker.browser.docservice;
    //TODO adds null handling?
    getTreeViewer().setInput(inputdocs);
    GenericSocketService dss = this.inputdocs;
    if (getTreeViewer() != null) {
      getTreeViewer().setInput(inputdocs);
    }
  }
  Runnable r = null;
  public void refresh() {
    if(r == null){
      r = new Runnable() {
        @Override
        public void run() {
          if(inputdocs == null)
            return;
          if(getControl().isDisposed())
            return;
          boolean docisBusy = false;
          synchronized(inputdocs.resourceNodeToSocks){
            if(r!=null && !docFree(inputdocs.resourceNodeToSocks.keySet()))
              docisBusy = true;
          }
          if(docisBusy){
            JobManager.jm.startBaseJob("Late refresh call",  new RunnableWithMonitor(){
      
              @Override
              public void run(IProgressMonitor pm) {
                boolean busy = true;
                while(busy){
                  busy = false;
                  synchronized(inputdocs.resourceNodeToSocks){
                    if(r!=null && !docFree(inputdocs.resourceNodeToSocks.keySet()))
                      busy = true;
                  }
                  try {
                    Thread.currentThread().join(1000);
                  } catch (InterruptedException e) {
                  }
                }
                if(!busy)
                  refresh();
              }
            }, false);
            return;
          }
          //if control is disposed old code may throw exception
          if ((ReqMarkerOutlinePage.this.getControl() != null)
              && !ReqMarkerOutlinePage.this.getControl().isDisposed()) {
            if (!getTreeViewer().isBusy()) getTreeViewer().refresh();
          }
          r = null;
        }
      };
      EclipseAPI.getDisplay()
          .asyncExec(r);
    }
  }


  private boolean docFree(Collection<TreeNode> values) {
    TreeDB db = null;
    TreeStateManager tsm = null;
    for (TreeNode doc:values)
    {
      if(db == null || db!=doc.getTreeDB()) {
        db = doc.getTreeDB();
        if(db!=null) {
          if(tsm == null)
            tsm = TreeStateManager.getOne(db.getTracker());
          if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
            return false;
        }
      }
    }
    return db!=null;
  }
  @Override
  public void dispose() {
    super.dispose();
    SelectionManager.getOne().removeSourceListener(EventSource.OUTLINE, fsid,  list);
    SelectionManager.getOne().unregisterInstance(fsid);
  }

  @Override
  public String getContributorId() {
    return "com.unitesk.requality.views.RequalityPropertyContributor";
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Object getAdapter(Class required) {
    // if (required == IContributedContentsView.class) {
    // return new IContributedContentsView() {
    // public IWorkbenchPart getContributingPart() {
    // return PlatformUI
    // .getWorkbench()
    // .getActiveWorkbenchWindow()
    // .getActivePage()
    // .findEditor(input);
    // }
    // };
    // }
    if (required == IPropertySheetPage.class) {
      return new TabbedPropertySheetPage(this);
    }
    return Platform.getAdapterManager().getAdapter(this, required);
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public boolean isSelectionSource() {
    return false;
  }

  public ReqMarker getReqMarker() {
    return reqMarker;
  }
  @Override
  public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId selectionSource, SelectionRecord visRecord, SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel){
    return new Pair<>(adaptChange(selectionSource, visRecord, oldVisRecord),
                       adaptChange(selectionSource, storedSel, oldStoredSel));
  }
  public SelectionRecord adaptChange(FSId selectionSource, SelectionRecord record, SelectionRecord oldRecord) {
    if(record == null)
      return null;
    Collection<TreeNode> nds = record.get(Location.TYPE_NAME);
    if(nds == null)
      return oldRecord;
    for(TreeNode l: nds)
      if(nds!=null && inputdocs.hasRelatedSessions(l.getWorkParent())) 
        return new SelectionRecord(l);
    if(record.isEmpty() && (selectionSource.relatesToSource(EventSource.EDITOR) || selectionSource.isSameAs(fsid)))
      return SelectionRecord.EMPTY;
    return oldRecord;
  }

}
