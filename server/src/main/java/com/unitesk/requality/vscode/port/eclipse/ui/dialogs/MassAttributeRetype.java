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

package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.util.Collections;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecorationRegistry;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.IAttributeFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.views.properties.ProjectSettingsMainSection;

/** Attribute edit dialog */
public class MassAttributeRetype extends Dialog {
  public static IAttributeFilter filter =
      new IAttributeFilter() {
        @Override
        public boolean filter(Attribute a) {
          if (!ProjectSettingsMainSection.filter.filter(a)) return false;
          return !a.getType().equals(AttributeType.ENUM)
              && !a.getType().equals(AttributeType.ENUM_DEFINITION)
              && !a.getType().equals(AttributeType.LIST)
              && !a.getType().equals(AttributeType.RESOURCE)
              && !a.getType().equals(AttributeType.REFERENCE);
        }
      };

  static String TITLE = "Attribute type change dialog";
  static String LBL_ATTR_NAME = "Select a name of the attribute:";
  static String LBL_ATTR_VALUE = "Values of selected attributes:";
  static String LBL_DEF_NAME = "Select new attribute types:";
  static String LBL_CHNG_NAME = "Select new attribute name:";
  private static String MES_NOT_SEL = "Attribute name is not selected";
  private static String MES_WRONG_TYPE = "Attributes of selected name has no valid value";

  static int SINGLE_WIDTH = 267;
  ControlDecoration attrSelDecorator;
  private Combo type = null;
  private TreeDB db = null;
  private Set<String> attrNames = null;
  private Text newAttrName = null;

  private List attrList = null;
  private List attrValList = null;

  private Image createdImage = null;
  
  private ControlDecoration createDecorator(Control c) {
    ControlDecoration controlDecoration = new ControlDecoration(c, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration =
        FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    if(createdImage!=null)
      createdImage.dispose();
    createdImage = fieldDecoration.getImage();
    controlDecoration.setImage(createdImage);
    return controlDecoration;
  }

  ModifyListener updateDecorator =
      new ModifyListener() {
        @Override
        public void modifyText(ModifyEvent e) {
          if (getButton(Dialog.OK) != null) {
            getButton(Dialog.OK).setEnabled(true);
          }
        }
      };

  SelectionListener sl =
      new SelectionListener() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          int ind = attrList.getSelectionIndex();
          String err = null;
          if (ind > -1) {
            String nm = attrList.getItem(ind);

            newAttrName.setText(nm);

            type.select(0);
            attrValList.removeAll();
            attrValList.setItems(Attribute.getAttributeValues(db, nm));
            if (attrValList.getItemCount() == 0) err = MES_WRONG_TYPE;
          } else {
            err = MES_NOT_SEL;
            type.select(0);
            attrValList.removeAll();
          }
          if (getButton(Dialog.OK) != null) {
            if (err != null) {
              getButton(Dialog.OK).setEnabled(false);
            } else {
              getButton(Dialog.OK).setEnabled(true);
            }
          }

          if (err != null) {
            attrSelDecorator.setDescriptionText(err);
            attrSelDecorator.show();
            return;
          }
          attrSelDecorator.hide();
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
          // TODO Auto-generated method stub

        }
      };

  public MassAttributeRetype(Shell shell, TreeDB db, Set<String> names) {
    super(shell);

    this.db = db;

    this.attrNames = Collections.unmodifiableSet(names);
  }

  @Override
  protected Control createDialogArea(Composite parent) {

    final Composite container = (Composite) super.createDialogArea(parent);
    GridLayout layout = new GridLayout();
    layout.marginWidth = layout.marginHeight = 10;
    layout.verticalSpacing = 2;
    container.setLayout(layout);
    container.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite leftright = new Composite(container, SWT.NONE);
    leftright.setLayoutData(new GridData(GridData.FILL_BOTH));
    leftright.setLayout(new GridLayout(2, true));

    Composite left = new Composite(leftright, SWT.NONE);
    left.setLayout(new GridLayout(1, true));
    left.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite right = new Composite(leftright, SWT.NONE);
    right.setLayout(new GridLayout(1, true));
    right.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite down = new Composite(container, SWT.NONE);
    down.setLayout(new GridLayout(2, true));
    down.setLayoutData(new GridData(GridData.FILL_BOTH));

    (new Label(left, SWT.NONE)).setText(LBL_ATTR_NAME);
    attrList = new List(left, SWT.V_SCROLL | SWT.H_SCROLL);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 300;
    attrList.setLayoutData(gd);
    attrList.addSelectionListener(sl);
    attrList.setItems(attrNames.toArray(new String[0]));

    (new Label(right, SWT.NONE)).setText(LBL_ATTR_VALUE);
    attrValList = new List(right, SWT.V_SCROLL | SWT.H_SCROLL | SWT.READ_ONLY);
    attrValList.setBackground(
        EclipseAPI.getDisplay().getSystemColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND));
    gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 300;
    attrValList.setLayoutData(gd);
    // new code
    Label nameLabel1 = new Label(down, SWT.NONE);
    nameLabel1.setText(LBL_CHNG_NAME);
    nameLabel1.setLayoutData(new GridData(GridData.FILL_HORIZONTAL, GridData.CENTER, true, true));
    newAttrName = new Text(down, SWT.BORDER);
    newAttrName.addModifyListener(
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
            attrSelDecorator.hide();
            getButton(Dialog.OK).setEnabled(true);
            if (!Attribute.checkNameIsValid(newAttrName.getText(), false)) {
              attrSelDecorator.show();
              getButton(Dialog.OK).setEnabled(false);
            }
          }
        });
    newAttrName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label nameLabel2 = new Label(down, SWT.NONE);
    nameLabel2.setText(LBL_DEF_NAME);
    nameLabel2.setLayoutData(new GridData(GridData.FILL_HORIZONTAL, GridData.CENTER, true, true));
    type = new Combo(down, SWT.BORDER | SWT.READ_ONLY);
    for (String tp : Attribute.getAvailableTypes(db, ProjectSettingsMainSection.filter)) type.add(tp);
    type.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    ;
    type.select(0);

    attrSelDecorator = createDecorator(attrList);
    //TODO change on selection
    //type.addSelectionListener(selectionListener);
    //name.addModifyListener(updateDecorator);

    this.getShell().setText(TITLE);
    this.getShell()
        .setImage(
            PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
    container.addDisposeListener(one->{
      if(createdImage!=null)
        createdImage.dispose();
    });
    return container;
  }

  private Object[] res;

  public Object[] getResult() {
    return res;
  }

  @Override
  protected void okPressed() {
    String attrname = null;
    String newattrname = null;
    int sel = type.getSelectionIndex();
    if (attrList.getSelectionIndex() > -1) attrname = attrList.getSelection()[0];
    //String tp = sel==-1?AttributeType.STRING.name(): type.getItems()[sel];
    AttributeType newt = AttributeType.ENUM;
    if (!newAttrName.getText().equals("")) {
      newattrname = newAttrName.getText();
    }

    if (sel < Attribute.getBuiltinTypes().length)
      newt = AttributeType.valueOf(Attribute.getBuiltinTypes()[sel]);
    res = new Object[] {attrname, newt, newattrname};
    super.okPressed();
  }
}
