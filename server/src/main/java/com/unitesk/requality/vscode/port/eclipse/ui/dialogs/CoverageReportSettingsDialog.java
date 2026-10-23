package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ControlEvent;
import com.unitesk.requality.vscode.ui.swt.events.ControlListener;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportUtils;
import com.unitesk.requality.tests.CoverageStorage;

public class CoverageReportSettingsDialog extends Dialog {
  protected static final String ERROR_SUBSTR = "⊙⊚";
  TreeNode req;
  Composite par;
  Composite par2;
  Composite parent;
  boolean noButtons = false;
  private String idFormula, testValue;
  
  WorkspaceJob wk = null;
  private Button hb;
  private Text tidf;
  private Text info;
  private Button cancelB, okB;
  @Override
  protected boolean isResizable() {
      return true;
  }
  @Override
  protected void cancelPressed() {
    if(wk!=null)
      wk.cancel();
    super.cancelPressed();
  }
  @Override
  protected void buttonPressed(int buttonId) {
    if(wk!=null)
      wk.cancel();
    super.buttonPressed(buttonId);
  }
  @Override
  protected com.unitesk.requality.vscode.ui.swt.widgets.Control createContents(Composite parent) {
    this.parent = parent;
    parent.getShell().setText("Настройка отчета о покрытии");
    par2 = new Composite(parent, SWT.NONE);
    par2.setLayout(new GridLayout(1, false));
    par = new Composite(par2, SWT.NONE);
    par.setLayout(new GridLayout(1, false));
    setLayoutParams(350,250);
    //new Label(par, SWT.NONE).setText("Hide buttons:");
    hb = new Button(par,SWT.CHECK);
    hb.setText("Hide buttons");
    hb.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    hb.setSelection(noButtons);
    new Label(par, SWT.NONE).setText("Test identifier formula:");
    tidf = new Text(par, SWT.NONE);
    tidf.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    new Label(par, SWT.NONE).setText("Example result:");
    info = new Text(par,SWT.H_SCROLL|SWT.V_SCROLL|SWT.READ_ONLY);
    info.setLayoutData(new GridData(GridData.FILL_BOTH));
    tidf.addModifyListener(new ModifyListener(){
    
      @Override
      public void modifyText(ModifyEvent e) {
        String val = tidf.getText();
        testVal(val);
      }
    });
    hb.setSelection(noButtons);
    hb.addSelectionListener(new SelectionListener(){
    
      @Override
      public void widgetSelected(SelectionEvent e) {
        noButtons = hb.getSelection();
      }
    
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        noButtons = hb.getSelection();
      }
    });
    //new Label(par, SWT.NONE).setText("Select second revis
//    right = new Composite(par, SWT.NONE);
//    gd = new GridData(GridData.FILL_BOTH);
//    right.setLayoutData(gd);
//    right.setLayout(new GridLayout(1, true));
//    rpan = new RevisionsPanel(right, SWT.NONE);
    
    //parent.getShell().layout(true,true);
    Composite bc = new Composite(par, SWT.NONE);
    bc.setLayout(new GridLayout(2, false));
    bc.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
    cancelB = new Button(bc, SWT.NONE);
    okB = new Button(bc, SWT.NONE);

    okB.setText("Ok");
    GridData gd = new GridData();
    gd.widthHint = 100;
    okB.setLayoutData(gd);
    okB.addListener(
        SWT.MouseDown,
        new Listener() {
          @Override
          public void handleEvent(Event event) {
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
            cancelPressed();
          }
        });
    tidf.setText(idFormula);
    getShell().addControlListener(new ControlListener(){
    
      @Override
      public void controlResized(ControlEvent e) { 
        setLayoutParams(getShell().getSize().x,getShell().getSize().y);
        parent.layout(true, true);
      }
    
      @Override
      public void controlMoved(ControlEvent e) {
        // TODO Auto-generated method stub
        
      }
    });
    return par;
  }


  private void setLayoutParams(int i, int j) {
    GridData gd0 = new GridData();
    gd0.widthHint = i-20;
    gd0.heightHint = j-30;
    par2.setLayoutData(gd0);
    GridData gd = new GridData();
    gd.widthHint = i-30;
    gd.heightHint = j-40;
    parent.setLayoutData(gd);
    par.setLayoutData(gd);
  }
  protected void testVal(String newText) {
    idFormula = newText;
    String msg = null;
    try{
      msg = ReportUtils.getValueByPathFormula(rep, testValue, newText);
//          JSFormInputDialog.this.setMessage(msg, false);
    }
    catch(Exception e){
//              JSFormInputDialog.this.setMessage(e.getMessage(), true);
      msg = ERROR_SUBSTR+e.getMessage();
    }
    info.setText(msg);
    boolean error = false;
    if(msg!=null &&
      msg.startsWith(ERROR_SUBSTR)){
      msg = msg.substring(ERROR_SUBSTR.length());
      error = true;
    }
    //setErrorMessage(msg);
    if(!error)
      setOkEnabled(true);
    else
      setOkEnabled(false);
  }


  private void setOkEnabled(boolean b) {
    okB.setEnabled(b);
  }


  TreeNode rep;
  Shell shell;

  public CoverageReportSettingsDialog(Shell shell, TreeNode rep) {
    super(shell);
    this.shell = shell;
    this.rep = rep;
    noButtons = rep.attrs().getStringAttributeValue("noAddTestButtons", "false").toLowerCase().equals("true");
    idFormula = rep.attrs().getStringAttributeValue("testIdFormula", CoverageStorage.TEST_ID_FORMULA);
    testValue = rep.getQualifiedId();
  }

  @Override
  protected void okPressed() {
    try {
	  final TreeNode rep = CoverageReportSettingsDialog.this.rep;
      final String idFormula = CoverageReportSettingsDialog.this.idFormula;
      final boolean noButtons = CoverageReportSettingsDialog.this.noButtons;
      WorkspaceJob wj = new WorkspaceJob("Change report settings"){
        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
          if(rep == null)
            return Status.CANCEL_STATUS;
          boolean started = rep.getTreeDB().startTransaction("Change report settings");
          try {
            if (!started) return Status.CANCEL_STATUS;
    
    	      rep.attrs().setConstAttribute("noAddTestButtons", Boolean.toString(noButtons), 0);
    	      if(idFormula == null || idFormula.isEmpty())
    	        rep.attrs().removeAttribute("testIdFormula");
    	      else
    	        rep.attrs().setConstAttribute("testIdFormula", idFormula, 0);
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
	  wj.schedule();
	  super.okPressed();
    } catch (Exception e) {
      e.printStackTrace();
    }
  };
}
