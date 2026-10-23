package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ExportToHtmlSettings extends Dialog {
  TreeNode req;
  Object filter;
  List refs;
  
  final Map<String, Integer> cnt = new HashMap<>();
  @Override
  protected com.unitesk.requality.vscode.ui.swt.widgets.Control createContents(Composite parent) {

    Composite p = new Composite(parent, SWT.NONE);
    p.getShell().setText("Настройки отчета-экспорта в HTML");
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
    final TreeNode req = this.req;
    final Map<String, Integer> cnt = this.cnt;
    final List refs = this.refs;
    WorkspaceJob j = new WorkspaceJob("fill reference names"){
	  @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(req == null)
            return Status.CANCEL_STATUS;
          DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(req, true, true, false);
          Iterator<TreeNode> iter = dftw.iterator();
          int sel = -1;
          while (iter.hasNext()) {
            TreeNode node = iter.next();
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
	   refs.setEnabled(false);
	   j.schedule();
	   
	   refs.addSelectionListener(
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

  public ExportToHtmlSettings(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = rep;
    Attribute ft = rep.attrs().getAttribute("filter");
    if (ft != null && ft.getType() == AttributeType.STRING)
      filter = Attribute.valueToString(rep.attrs(), ft.getType(),ft.getValue());
    req = rep.getTreeDB().getNode(((ReportSettings) rep).getStartRequirementQId(), Path.QID);
  }

  @Override
  protected void okPressed() {
  final TreeNode rep = ExportToHtmlSettings.this.rep;
    final Object filter = ExportToHtmlSettings.this.filter;
    try {
      WorkspaceJob wj = new WorkspaceJob("Change report settings"){
	    @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
          if (rep == null) return Status.CANCEL_STATUS;
          boolean started = rep.getTreeDB().startTransaction("Change report settings");
          try {
            if (!started) return Status.CANCEL_STATUS;
            if (filter != null && filter instanceof String)
  	        rep.attrs().setConstAttribute(AttributeType.STRING, "filter", filter, 0);
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
        }};
      ;
      wj.schedule();
      super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    }
  };
}
