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

package com.unitesk.requality.eclipse.ui.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.editors.NodeIdVerifyListener;

public class GetNodeIdDialog extends Dialog {
  private static final String LBL_ID = "Input a short identifier of element:";
  private static final String MES_EXIST = "Element with same identifier already exists!";
  private static final String MES_EMPTY = "Identifier of the element can not be empty!";

  private static int SINGLE_WIDTH = 267;
  private TreeNode parentReq = null;
  private String id = "";
  private Text idEdit = null;
  private String newId = "";
  private String title = "Identifier Editor";

  public GetNodeIdDialog(Shell shell, TreeNode node, String id) {
    super(shell);
    this.parentReq = node;
    if (id != null) this.id = id;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite container = createClientContainer(parent, 1);

    Label commentLabel = new Label(container, SWT.NONE);
    commentLabel.setText(LBL_ID);
    commentLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, true));

    idEdit = new Text(container, SWT.BORDER);
    idEdit.setLayoutData(new GridData(SINGLE_WIDTH, -1));
    idEdit.setText(id.trim());
    idEdit.selectAll();
    idEdit.addVerifyListener(new NodeIdVerifyListener(parentReq));

    this.getShell().setText(title);
    this.getShell()
        .setImage(
            PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
    return container;
  }

  protected Composite createClientContainer(Composite parent, int span) {
    Composite container = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = layout.marginHeight = 10;
    layout.verticalSpacing = 2;
    layout.numColumns = span;
    container.setLayout(layout);
    return container;
  }

  @Override
  protected void okPressed() {

    if (parentReq != null) {

      String newId = idEdit.getText().trim().replace(" ", "_");

      if (newId.length() == 0) {
        MessageDialog.openInformation(getParentShell(), title, MES_EMPTY);
        idEdit.setFocus();
        return;
      }
      if (parentReq.findWorkChild(newId) != null) {
        MessageDialog.openInformation(getParentShell(), title, MES_EXIST);
        idEdit.setFocus();
        return;
      }
    }

    newId = idEdit.getText();
    super.okPressed();
  }

  public String getResult() {
    return newId;
  }

  public void setTitle(String title) {
    this.title = title;
    if (getShell() != null && !getShell().isDisposed()) {
      getShell().setText(title);
    }
  };
}
