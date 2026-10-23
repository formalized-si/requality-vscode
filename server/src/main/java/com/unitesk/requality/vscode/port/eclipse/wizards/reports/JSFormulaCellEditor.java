package com.unitesk.requality.vscode.port.eclipse.wizards.reports;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IInputValidator;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.DialogCellEditor;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.report.ReportUtils;

public class JSFormulaCellEditor extends DialogCellEditor {

  private class JSFormInputDialog extends InputDialog{
    protected static final String ERROR_SUBSTR = "⊙⊚";
    boolean realerror = false;
    public void setMessage(String msg, boolean realerror){
      this.realerror = realerror;
    }
    public JSFormInputDialog(
        Shell parentShell,
        String dialogTitle,
        String dialogMessage,
        String initialValue) {
      super(parentShell,dialogTitle,dialogMessage,initialValue,
        new IInputValidator(){
            @Override
            public String isValid(String newText) {
              //JSFormInputDialog.this.setMessage("", false);
              try{
                String msg = ReportUtils.getValueByFormula("0", "test", newText);
//                JSFormInputDialog.this.setMessage(msg, false);
                return msg;
              }
              catch(Exception e){
  //              JSFormInputDialog.this.setMessage(e.getMessage(), true);
                return ERROR_SUBSTR+e.getMessage();
              }
              //return null;
            }
        }
      );
      // TODO Auto-generated constructor stub
    }
    @Override
    protected Control createDialogArea(Composite parent) {
      Control c =  super.createDialogArea(parent);
      return c;
    }
    @Override
    public void setErrorMessage(String errorMessage) {
      // TODO Auto-generated method stub
      boolean error = false;
      if(errorMessage!=null &&
        errorMessage.startsWith(ERROR_SUBSTR)){
        errorMessage = errorMessage.substring(ERROR_SUBSTR.length());
        error = true;
      }
      super.setErrorMessage(errorMessage);
      if(!error)
        setOkEnabled(true);
      else
        setOkEnabled(false);
    }
    
    private void setOkEnabled(boolean b) {
      Control button = getButton(IDialogConstants.OK_ID);
      if (button != null) {
        button.setEnabled(true);//TODO think, mb allow to reset
      }
    }
  
  }

  public JSFormulaCellEditor(Table table) {
    super(table);
  }

  @Override
  protected Object openDialogBox(Control cellEditorWindow) {
    VarProc o = (VarProc) ((Table) cellEditorWindow.getParent()).getSelection()[0].getData();
    // TODO Auto-generated method stub
    InputDialog id = new JSFormInputDialog(cellEditorWindow.getShell(),
        "Edit formula", "Enter formula for '"+o.var+"'. 'value' variable stores current value of attribute. For this dialog its value equals to \"0\"", o.form
        );
    if(id.open() == Window.OK)
      return id.getValue();
    return o.form;
  }
}
