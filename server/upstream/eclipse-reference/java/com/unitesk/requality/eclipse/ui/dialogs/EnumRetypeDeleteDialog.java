/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/** Attribute edit dialog */
public class EnumRetypeDeleteDialog extends Dialog {
  static String TITLE = "ENUM definition renaming dialog";

  private String msg = null;
  static int SINGLE_WIDTH = 130;
  private boolean changeId = false;
  public static int RETYPE = -1, REMOVE = -2, CHANGE_ID = -3;
  private int result = Dialog.CANCEL;

  @Override
  public int open() {
    super.open();
    return result;
  }

  public EnumRetypeDeleteDialog(Shell shell, String msg) {
    this(shell, msg, false);
  }

  public EnumRetypeDeleteDialog(Shell shell, String msg, boolean chId) {
    super(shell);
    this.msg = msg;
    this.changeId = chId;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite container = createClientContainer(parent, 1);
    container.setLayoutData(new GridData(GridData.FILL_BOTH));
    Label nameLabel = new Label(container, SWT.WRAP);
    nameLabel.setText(msg);
    GridData gd = new GridData(GridData.BEGINNING);
    gd.widthHint = SINGLE_WIDTH * (3 + (changeId ? 1 : 0));
    nameLabel.setLayoutData(gd);

    this.getShell().setText(TITLE);
    this.getShell()
        .setImage(
            PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
    return container;
  }

  private Composite createClientContainer(Composite parent, int span) {
    Composite container = new Composite(parent, SWT.NONE);
    container.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    GridLayout layout = new GridLayout();
    layout.marginWidth = layout.marginHeight = 10;
    layout.verticalSpacing = 2;
    layout.numColumns = span;
    container.setLayout(layout);
    return container;
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    if (changeId)
      createButton(parent, IDialogConstants.OK_ID, "Rename", false)
          .addListener(
              SWT.MouseDown,
              new Listener() {
                @Override
                public void handleEvent(Event event) {
                  result = CHANGE_ID;
                  EnumRetypeDeleteDialog.this.okPressed();
                }
              });

    createButton(parent, IDialogConstants.OK_ID, "Remove", false)
        .addListener(
            SWT.MouseDown,
            new Listener() {
              @Override
              public void handleEvent(Event event) {
                result = REMOVE;
                EnumRetypeDeleteDialog.this.okPressed();
              }
            });
    createButton(parent, IDialogConstants.ABORT_ID, "Change type", false)
        .addListener(
            SWT.MouseDown,
            new Listener() {
              @Override
              public void handleEvent(Event event) {
                result = RETYPE;
                EnumRetypeDeleteDialog.this.okPressed();
              }
            });
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }
}
