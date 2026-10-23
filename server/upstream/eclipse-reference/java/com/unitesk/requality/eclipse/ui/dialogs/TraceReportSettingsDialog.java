package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TraceReportSettingsDialog extends Dialog {
  TreeNode req;
  Object filter;
  final Map<String, Integer> cnt = new HashMap<>();
  List refs;

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    
  }

  @Override
  protected org.eclipse.swt.widgets.Control createContents(Composite parent) {
    parent.getShell().setText("Настройки отчета о трассируемости");
    Composite p = new Composite(parent, SWT.NONE);
    p.setLayout(new GridLayout(1, true));
    GridData gd = new GridData();
    gd.widthHint = 250;
    gd.heightHint = 300;
    parent.setLayoutData(gd);
    p.setLayoutData(gd);
    new Label(p, SWT.NONE).setText("Select target Reference name:");

    refs = new List(p, SWT.V_SCROLL);
    gd = new GridData(GridData.FILL_BOTH);
    refs.setLayoutData(gd);

    refs.setEnabled(false);
    final Map<String, Integer> cnt = this.cnt;
    final List refs = this.refs;
    final TreeNode req = this.req;
    {
      final Object filter = this.filter;
      WorkspaceJob j = new WorkspaceJob("Init reference data"){
  
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(req == null)
            return Status.CANCEL_STATUS;
          DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(req);
	        Iterator<TreeNode> iter = dftw.iterator();
	        int sel = -1;
	        while (iter.hasNext()) {
	          TreeNode node = iter.next();
	          if(node!=null)
	            for (Attribute a : node.attrs()) {
	              if (a.getType().equals(AttributeType.REFERENCE)) {
	                if (!cnt.containsKey(a.getKey())) {
	                  if (filter != null && filter instanceof String && filter.equals(a.getKey()))
	                   //TODO list
	                    sel = cnt.keySet().size();
	                  cnt.put(a.getKey(), 0);
	                }
	                cnt.put(a.getKey(), cnt.get(a.getKey()) + 1);
	              }
	            }
	        } 
	        int fsel = sel;
	        EclipseAPI.getDisplay().asyncExec(new Runnable(){
	          @Override
	          public void run() {
	            if(refs.isDisposed())
	              return;
	            for (String s : cnt.keySet()) refs.add(s + "(" + cnt.get(s) + ")");
	            if (fsel > -1) refs.select(new ArrayList<>(cnt.keySet()).indexOf(filter)); 
		        refs.setEnabled(true);
		      }
		    });
		    return Status.OK_STATUS;
		  }
      };
      j.schedule();
    }   
	refs.addSelectionListener(// TODO Auto-generated method stub
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            if(refs.isDisposed())
              return;
            if (refs.getSelectionIndex() > -1)
              filter = cnt.keySet().toArray()[refs.getSelectionIndex()];
            //TODO list
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });
	
    Composite bc = new Composite(p, SWT.NONE);
    bc.setLayout(new GridLayout(2, true));
    bc.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_END));
    Button cancelB = new Button(bc, SWT.NONE), okB = new Button(bc, SWT.NONE);

    okB.setText("Ok");
    gd = new GridData();
    gd.widthHint = 100;
    okB.setLayoutData(gd);
    okB.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(okB.isDisposed())
              return;
            okPressed();
          }
        });

    cancelB.setText("Cancel");
    gd = new GridData();
    gd.widthHint = 100;
    cancelB.setLayoutData(gd);
    cancelB.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
    	    if(cancelB.isDisposed())
              return;
            cancelPressed();
          }
        });

    parent.layout(true, true);
    return p;
  }

  TreeNode rep;
  Shell shell;

  public TraceReportSettingsDialog(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = rep;
    Attribute ft = rep.attrs().getAttribute("filter");
    if (ft != null && ft.getType() == AttributeType.STRING)
      filter = ft.getValue().toString();
    req =
        rep.getTreeDB()
            .getNode(((ReportSettings) rep).getStartRequirementQId(), Path.QID | Path.UV);
  }

  @Override
  protected void okPressed() {
    try {
	  final TreeNode rep = this.rep;
      final Object filter = this.filter;
    
      WorkspaceJob j = new WorkspaceJob("Change report settings"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(rep == null)
            return Status.CANCEL_STATUS;
          boolean started = rep.getTreeDB().startTransaction("Change report settings");
          try {
            if (!started) return Status.CANCEL_STATUS;
            if (filter != null && filter instanceof String)
              rep.attrs().setConstAttribute("filter", filter.toString(),0);
            rep.commitAttributes();
            if(started) {
              rep.getTreeDB().commit();
              started = false;
            }
          }finally {
            if(started)
              rep.getTreeDB().rollback();              
          }
          return Status.OK_STATUS;
        }
        
       };
       j.schedule();
      super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    }
  };
}
