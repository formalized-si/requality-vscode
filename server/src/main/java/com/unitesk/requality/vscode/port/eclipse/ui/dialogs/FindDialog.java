package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.util.ArrayList;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ISearchProvider;

/** Find dialog */
public class FindDialog extends Dialog {
  private static String TITLE = "Find";
  private static String LBL_FIND = "Find:";
  private ISearchProvider browser;
  private Combo combo;
  public static ArrayList<String> comboStrings = new ArrayList<>();

  private Button caseSensitive;
  private static boolean selectCaseSensitive = false;
  private SelectionListener caseSensitiveListener =
      new SelectionListener() {

        @Override
        public void widgetSelected(SelectionEvent e) {
          selectCaseSensitive = caseSensitive.getSelection();
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {}
      };

  //    private Button wrapAround;
  //    private static boolean selectWrapAround = false;
  //    private SelectionListener wrapAroundListener = new SelectionListener() {
  //
  //        @Override
  //        public void widgetSelected(SelectionEvent e) {
  //            selectWrapAround = wrapAround.getSelection();
  //        }
  //
  //        @Override
  //        public void widgetDefaultSelected(SelectionEvent e) {}
  //    };

  private ModifyListener comboListener =
      new ModifyListener() {

        @Override
        public void modifyText(ModifyEvent e) {
          if (combo.isDisposed()) return;
          if (combo.getText().isEmpty()) {
            getButton(IDialogConstants.CLIENT_ID + 1).setEnabled(false);
            getButton(IDialogConstants.CLIENT_ID + 2).setEnabled(false);
          } else {
            getButton(IDialogConstants.CLIENT_ID + 1).setEnabled(true);
            getButton(IDialogConstants.CLIENT_ID + 2).setEnabled(true);
          }
        }
      };

  public FindDialog(Shell shell, ISearchProvider jse2) {
    super(shell);
    setShellStyle(SWT.DIALOG_TRIM | SWT.CLOSE | SWT.MODELESS | SWT.BORDER | SWT.TITLE);
    browser = jse2;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    getShell().setText(TITLE);
    Composite comp1 = new Composite(parent, SWT.NONE);
    comp1.setLayout(new GridLayout(2, false));
    Composite comp3 = new Composite(comp1, SWT.NONE);
    comp3.setLayout(new GridLayout(1, false));
    GridData gd = new GridData();
    gd.verticalAlignment = GridData.BEGINNING;
    Label label = new Label(comp3, SWT.TOP);
    label.setText(LBL_FIND);
    comp3.setLayoutData(gd);
    Composite comp4 = new Composite(comp1, SWT.NONE);
    comp4.setLayout(new GridLayout());
    combo = new Combo(comp4, SWT.NONE);
    GridData gdCombo = new GridData();
    gdCombo.widthHint = 400;
    combo.setLayoutData(gdCombo);
    if (!comboStrings.isEmpty()) {
      combo.setItems(comboStrings.toArray(new String[0]));
      combo.setText(comboStrings.get(0));
    }
    if (browser != null) {
      combo.setText(browser.getSelectionText()!=null?browser.getSelectionText():"");
    }
    combo.addModifyListener(comboListener);
    caseSensitive = new Button(comp4, SWT.CHECK);
    caseSensitive.setText("Case sensitive");
    caseSensitive.addSelectionListener(caseSensitiveListener);
    caseSensitive.setSelection(selectCaseSensitive);
    //        wrapAround = new Button(comp4, SWT.CHECK);
    //        wrapAround.setText("Wrap search");
    //        wrapAround.addSelectionListener(wrapAroundListener);
    //        wrapAround.setSelection(selectWrapAround);
    return parent;
  }

  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.CANCEL_ID, "Close", false);
    Button spaceButton = createButton(parent, 3, "", false);
    spaceButton.setVisible(false);
    createButton(parent, IDialogConstants.CLIENT_ID + 1, "<<", false);
    createButton(parent, IDialogConstants.CLIENT_ID + 2, ">>", true);
    //        if (comboStrings.isEmpty()) {
    if (combo.getText().isEmpty()) {
      getButton(IDialogConstants.CLIENT_ID + 1).setEnabled(false);
      getButton(IDialogConstants.CLIENT_ID + 2).setEnabled(false);
    }
  }

  @Override
  protected void buttonPressed(int buttonId) {
    super.buttonPressed(buttonId);
    if (combo.isDisposed()) return;
    if (combo.getText().isEmpty()) return;
    saveTextToCombo();
    IEditorPart focusedEditor =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
    if (focusedEditor instanceof ISearchProvider) {
      browser = ((ISearchProvider) focusedEditor);
    }

    if (IDialogConstants.CLIENT_ID + 1 == buttonId) {
      if (browser != null) {
        browser.findText(combo.getText(), selectCaseSensitive, true, false);
      }
      //                    selectWrapAround);
    }
    if (IDialogConstants.CLIENT_ID + 2 == buttonId) {
      if (browser != null) {
        browser.findText(combo.getText(), selectCaseSensitive, false, false);
        //                    selectWrapAround);
      }
    }
  }

  private void saveTextToCombo() {
    String text = combo.getText();
    if (comboStrings.contains(text)) {
      comboStrings.remove(text);
    }
    comboStrings.add(0, text);
    combo.setItems(comboStrings.toArray(new String[0]));
    combo.setText(text);
  }
}
