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
package com.unitesk.requality.vscode.port.eclipse.editors.panels;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.SelectNodeDialog;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.SelectReqDialog;
import com.unitesk.requality.nodetype.Requirement;

public class SelectRequirementPanel extends AbstractPanel {
  public static final String ROOT_STRING = "/";

  private static final String BTN_REQ = "Browse...";

  private Text rootText = null;
  private TreeDB db;
  private Requirement importRoot;
  private Requirement curRoot;
  private boolean readOnly = false;

  private boolean canSelectRoot = false;

  private Button rootBut;

  public void setRequirement(Requirement req) {
    db = req.getTreeDB();
    importRoot = req;
    curRoot = req;
    if (rootText != null && !rootText.isDisposed()) rootText.setText(req.getNameOrId());
  }

  public SelectRequirementPanel(Composite parent, int style, TreeDB db) {
    super(parent, style);
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors
    GridLayout gl = new GridLayout(2, false);
    gl.horizontalSpacing = gl.verticalSpacing = 0;
    this.setLayout(gl);
    this.db = db;
    this.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    rootText = new Text(this, SWT.BORDER);
    GridData gdata = new GridData(GridData.FILL_HORIZONTAL);
    gdata.grabExcessHorizontalSpace = true;
    rootText.setLayoutData(gdata);
    rootText.setEditable(false);
    rootText.setText(ROOT_STRING);
    rootBut = new Button(this, SWT.NONE);
    rootBut.setText(BTN_REQ);
    SelectionListener rootList =
        new SelectionListener() {
          @Override
          public void widgetDefaultSelected(SelectionEvent e) {}

          @Override
          public void widgetSelected(SelectionEvent e) {
            SelectNodeDialog selDialog =
                new SelectReqDialog(getShell(), SelectRequirementPanel.this.db, false);

            selDialog.setCanSelectRoot(canSelectRoot);

            if (selDialog.open() == SelectNodeDialog.OK) {
              Requirement selected = (Requirement) selDialog.getResult();
              if (selected != null) {
                rootText.setText(selected.getNameOrId());
                curRoot = selected;
                SelectRequirementPanel.this.notifyListeners(SWT.Modify, new Event());
              }
            }
          }
        };
    rootBut.addSelectionListener(rootList);
  }

  @Override
  protected void initModifyListener() {
    rootText.addListener(SWT.Modify, super.modifyListener);
  }

  @Override
  public boolean isChanged() {
    return !importRoot.equals(curRoot);
  }

  public Requirement getSelectedRequirement() {
    return curRoot;
  }

  public void setCanSelectRoot(boolean canSelectRoot) {
    this.canSelectRoot = canSelectRoot;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    rootText.setEnabled(enabled);
    rootBut.setEnabled(enabled);
    this.readOnly = !enabled;
  }

  public void setTreeDB(TreeDB ndb) {
    db = ndb;
  }
}
