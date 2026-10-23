package com.unitesk.requality.eclipse.ui.editors;

import java.util.Collection;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.operations.UndoRedoActionGroup;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.StructuralChecks;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.ISelectionAdapter;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.nodetype.Requirement;

public class UniEditorOutlinePage extends ContentOutlinePage implements IFSIdProvider, ISelectionAdapter{
  private TreeNode input;
  private FSId fsid;
  private boolean selectionEnabled = true;
  private ISelectionChangedListener selList = 
    new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        Object obj = event.getSelection();
        if(obj instanceof StructuredSelection)
        {
          StructuredSelection sel = (StructuredSelection) obj;
          if(sel!=null) {
            Object lo = sel.getFirstElement();
            if(lo!=null && lo instanceof Requirement) {
              Requirement l = (Requirement) lo;
              setSelection(l);
            //  SelectionManager.getOne().changeActiveSource(fsid.getEventSource(),fsid, false);
              if(selectionEnabled)
                SelectionManager.getOne().updateSelection(SourcePriority.EDITOR, fsid,  l);
            }
          }
        }
      }
    };

  @Override
  public void dispose() {
    super.dispose();
  }

  public UniEditorOutlinePage() {
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.OUTLINE, this, true);
  }

  @Override
  public void createControl(Composite parent) {
    super.createControl(parent);
    getTreeViewer().setLabelProvider(WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider());
    getTreeViewer().setContentProvider(new RequalityContentProvider(true));
    getTreeViewer().setComparator(new RequalitySorter());
    if (input != null) {
      getTreeViewer().setInput(new Object[] {input});
    }
    getTreeViewer().addSelectionChangedListener(selList );
    getTreeViewer().expandAll();
    IWorkbenchPartSite site =
        PlatformUI.getWorkbench()
            .getActiveWorkbenchWindow()
            .getActivePage()
            .getActivePart()
            .getSite();
    IUndoContext context = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
    new UndoRedoActionGroup(site, context, true).fillActionBars(getSite().getActionBars());
  }

  protected void setSelection(TreeNode l) {
    selectionEnabled = false;
    getTreeViewer().removeSelectionChangedListener(selList);
    try {
      if(l == null)
        getTreeViewer().setSelection(new StructuredSelection());
      else
       getTreeViewer().setSelection(new StructuredSelection(l));
    }finally {
      getTreeViewer().addSelectionChangedListener(selList);
      selectionEnabled = true;
    }
  }

  public void setInput(TreeNode node) {
    input = node;
    if (getTreeViewer() != null) {
      getTreeViewer().setInput(node);
    }
  }

  public void refresh(final TreeNode node) {
    getSite()
        .getShell()
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                if(getTreeViewer()==null || getTreeViewer().getControl().isDisposed())
                  return;
                //if control is disposing then old code may
                //throw exception
                if ((UniEditorOutlinePage.this.getControl() != null)
                    && !UniEditorOutlinePage.this.getControl().isDisposed()) {
                  getTreeViewer().refresh(node);
                }
              }
            });
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public boolean isSelectionSource() {
    return false;
  }
  @Override
  public Pair<SelectionRecord, SelectionRecord> adaptChange(FSId selectionSource, SelectionRecord visRecord, SelectionRecord oldVisRecord, SelectionRecord storedSel, SelectionRecord oldStoredSel){
    return new Pair<>(adaptChange(selectionSource, visRecord, oldVisRecord),
                       adaptChange(selectionSource, storedSel, oldStoredSel));
  }
  public SelectionRecord adaptChange(FSId selectionSource, SelectionRecord record, SelectionRecord oldRecord) {
    if(record == null)
      return null;
    Collection<TreeNode> nodes = record.getByType(Requirement.TYPE_NAME);
    for(TreeNode node:nodes)
      if(StructuralChecks.isDescendant(node, input))
        return new SelectionRecord(node);
    if((selectionSource.relatesToSource(EventSource.EDITOR) || selectionSource.isSameAs(fsid)) && record.isEmpty())
      return SelectionRecord.EMPTY;
    return oldRecord;
  }
}
