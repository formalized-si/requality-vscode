/**
 * ***************************************************************************** Institute for
 * System Programming of the Russian Academy of Sciences Copyright (c) 2009-2013 All rights
 * reserved. This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * ****************************************************************************
 */
package com.unitesk.requality.vscode.port.eclipse.cvs;

/*
SWT/JFace in Action
GUI Design with Eclipse 3.0
Matthew Scarpino, Stephen Holder, Stanford Ng, and Laurent Mihalkovic

ISBN: 1932394273

Publisher: Manning
*/

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

public class UsernamePasswordDialog extends Dialog {
  private static final int RESET_ID = IDialogConstants.NO_TO_ALL_ID + 1;

  private Text usernameField;

  private Text passwordField;

  String username = "";

  String passwd = "";

  public UsernamePasswordDialog(Shell parentShell) {
    super(parentShell);
  }

  protected Control createDialogArea(Composite parent) {
    Composite comp = (Composite) super.createDialogArea(parent);

    GridLayout layout = (GridLayout) comp.getLayout();
    layout.numColumns = 2;

    Label usernameLabel = new Label(comp, SWT.RIGHT);
    usernameLabel.setText("Username: ");

    usernameField = new Text(comp, SWT.SINGLE);
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    usernameField.setLayoutData(data);

    Label passwordLabel = new Label(comp, SWT.RIGHT);
    passwordLabel.setText("Password: ");

    passwordField = new Text(comp, SWT.SINGLE | SWT.PASSWORD);
    data = new GridData(GridData.FILL_HORIZONTAL);
    passwordField.setLayoutData(data);

    return comp;
  }

  protected void createButtonsForButtonBar(Composite parent) {
    super.createButtonsForButtonBar(parent);
    createButton(parent, RESET_ID, "Reset All", false);
  }

  protected void buttonPressed(int buttonId) {
    if (buttonId == RESET_ID) {
      usernameField.setText("");
      passwordField.setText("");
    } else if (buttonId == IDialogConstants.OK_ID) {
      username = usernameField.getText();
      passwd = passwordField.getText();
      super.buttonPressed(buttonId);
    } else {
      super.buttonPressed(buttonId);
    }
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return passwd;
  }
}
