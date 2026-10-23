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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecorationRegistry;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.IAttributeFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.views.properties.ProjectSettingsMainSection;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

/** Attribute edit dialog */
public class EnumDefExtractDialog extends Dialog {
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

  private static String DEF_NAME = "NONAME";
  static String TITLE = "ENUM definition extractor dialog";
  static String LBL_ATTR_NAME = "Select a name of the attribute:";
  static String LBL_ATTR_VALUE = "Values of selected attributes:";
  static String LBL_DEF_NAME = "Input a name of ENUM definition:";

  private static String MES_EXIST = "Attribute with the same name already exists";
  private static String MES_EMPTY = "Name of the ENUM definition can not be empty";
  private static String MES_WRONG_FORMAT = "Name contains illegal characters or starts with _ or $";
  private static String MES_NOT_SEL = "Attribute name is not selected";
  private static String MES_WRONG_TYPE = "Attributes of selected name has no valid value";

  static int SINGLE_WIDTH = 267;
  ControlDecoration defNameDecorator;
  ControlDecoration attrSelDecorator;
  private Text name = null;
  private TreeDB db = null;
  private Set<String> attrNames = null;

  private com.unitesk.requality.vscode.ui.swt.widgets.List attrList = null;
  private com.unitesk.requality.vscode.ui.swt.widgets.List attrValList = null;

  private Image createdImage;
  
  private ControlDecoration createDecorator(Control c) {
    ControlDecoration controlDecoration = new ControlDecoration(c, SWT.LEFT | SWT.TOP);
//    if(createdImage!=null)
//      createdImage.dispose();//TODO test?
    createdImage = FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage();
    controlDecoration.setImage(createdImage);
    return controlDecoration;
  }

  ModifyListener updateDecorator =
      new ModifyListener() {
        @Override
        public void modifyText(ModifyEvent e) {
          String err = EnumDefExtractDialog.this.getNameError(name.getText().trim());
          if (getButton(Dialog.OK) != null) {
            if (err != null ) {
              getButton(Dialog.OK).setEnabled(false);
            } else {
              getButton(Dialog.OK).setEnabled(true);
            }
          }
          if (err != null) {
            defNameDecorator.setDescriptionText(err);
            defNameDecorator.show();
            return;
          }
          defNameDecorator.hide();
        }
      };

  SelectionListener sl =
      new SelectionListener() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          int ind = attrList.getSelectionIndex();
          String err = null, err2 = null;
          if (ind > -1) {
            String nm = attrList.getItem(ind);
            name.setText(nm);
            attrValList.removeAll();
            attrValList.setItems(getAttributeValues(db, nm));
            if (attrValList.getItemCount() == 0) err = MES_WRONG_TYPE;
          } else {
            err = MES_NOT_SEL;
            name.setText(DEF_NAME);
            attrValList.removeAll();
          }
          err2 = EnumDefExtractDialog.this.getNameError(name.getText().trim());
          if (getButton(Dialog.OK) != null) {
            if (err != null || err2 != null) {
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

  public EnumDefExtractDialog(Shell shell, TreeDB db, Set<String> names) {
    super(shell);

    this.db = db;

    this.attrNames = Collections.unmodifiableSet(names);
  }

  private String[] getAttributeValues(TreeDB db, String nm) {
    DeepFirstTreeWalker walker = new DeepFirstTreeWalker(db.getRootNode(), true, true, false);
    Iterator<TreeNode> it = walker.iterator();
    Set<String> vals = new HashSet<>();
    while (it.hasNext()) {
      AttributeStorage as = it.next().attrs();
      Attribute a = as.getAttribute(nm);
      if (a != null
          && a.getType() != AttributeType.LIST
          && a.getType() != AttributeType.RESOURCE
          && a.getType() != AttributeType.ENUM_DEFINITION
          && a.getType() != AttributeType.ENUM) {
        vals.add(Attribute.valueToString(as, a.getType(),a.getValue()));
      }
    }
    return vals.toArray(new String[0]);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite container = (Composite) super.createDialogArea(parent);
    GridLayout layout = new GridLayout();
    layout.marginWidth = layout.marginHeight = 10;
    layout.verticalSpacing = 2;
    layout.numColumns = 1;
    container.setLayout(layout);

    container.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite leftright = new Composite(container, SWT.NONE);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;
    leftright.setLayoutData(gd);
    leftright.setLayout(new GridLayout(2, true));

    Composite left = new Composite(leftright, SWT.NONE);
    left.setLayout(new GridLayout(1, true));
    left.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite right = new Composite(leftright, SWT.NONE);
    right.setLayout(new GridLayout(1, true));
    right.setLayoutData(new GridData(GridData.FILL_BOTH));

    (new Label(left, SWT.NONE)).setText(LBL_ATTR_NAME);
    attrList = new List(left, SWT.V_SCROLL | SWT.H_SCROLL);
    gd = new GridData(GridData.FILL_BOTH);
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

    Label nameLabel = new Label(container, SWT.NONE);
    nameLabel.setText(LBL_DEF_NAME);
    nameLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL, GridData.CENTER, true, true));
    name = new Text(container, SWT.BORDER);
    name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    defNameDecorator = createDecorator(name);

    attrSelDecorator = createDecorator(attrList);
    name.addModifyListener(updateDecorator);

    name.setText(DEF_NAME);

    this.getShell().setText(TITLE);
    this.getShell()
        .setImage(
            PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));
//    container.addDisposeListener(one->{
//    	if(createdImage!=null)
//    		createdImage.dispose();
//    });
    return container;
  }

  private String[] res;

  public String[] getResult() {
    return res;
  }

  @Override
  protected void okPressed() {
    String attrname = null;
    if (attrList.getSelectionIndex() > -1) attrname = attrList.getSelection()[0];
    String defname = name.getText().trim();
    res = new String[] {attrname, defname};
    super.okPressed();
  }

  protected String getNameError(String attrname) {
    for (String a : db.getRootNode().attrs().getLocalAttributeKeys()) {
      if (a.equals(attrname) == true) {
        return MES_EXIST;
      }
    }
    if (attrname.length() == 0) {
      return MES_EMPTY;
    }
    if (!Attribute.checkNameIsValid(attrname, false)) return MES_WRONG_FORMAT;

    return null;
  }
}
