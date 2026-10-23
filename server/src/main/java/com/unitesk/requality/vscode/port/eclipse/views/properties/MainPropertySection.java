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
package com.unitesk.requality.vscode.port.eclipse.views.properties;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecoration;
import com.unitesk.requality.vscode.ui.jface.fieldassist.FieldDecorationRegistry;
import com.unitesk.requality.vscode.ui.jface.resource.ColorRegistry;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.FocusAdapter;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.ITheme;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.views.panels.AttributesPanel;
import com.unitesk.requality.vscode.port.eclipse.views.panels.NameEditPanel;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;

public class MainPropertySection extends TreeNodePropertiesTab {

  private AttributesPanel attribPane;
  private Text textId;
  private NameEditPanel textName;
  private Label textNameLabel;
  ControlDecoration reqIdDecorator;
//  private TreeNode req;
  private Combo type;
  private Composite tp;
  private GridData tps;
  protected boolean nameEnabled = true;


  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = getWidgetFactory();

    //parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setLayout(new GridLayout(2, false));
    parent.setBackgroundMode(SWT.INHERIT_DEFAULT);

    wf.createLabel(parent, "Id:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
    textId = wf.createText(parent, "");
    textId.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    reqIdDecorator = createDecorator(textId);
    reqIdDecorator.hide();
    textId.addKeyListener(
        new KeyListener() {

          @Override
          public void keyReleased(KeyEvent e) {
            try {
              if (node == null || textId.isDisposed()) {
                return;
              }
              reqIdDecorator.show();
              if (node.getTreeDB().getTreeLogic().isIdValidExc(node, textId.getText().trim())) {
                reqIdDecorator.hide();
              } else {
                reqIdDecorator.setDescriptionText(
                    "Requirement Id is not correct and Requirement Id should not be empty");
                reqIdDecorator.show();
              }
            } catch (SameIdExistsInChildren e1) {
              reqIdDecorator.setDescriptionText("Element with the same Id already exists");
            } catch (IdEmpty e1) {
              reqIdDecorator.setDescriptionText("Requirement Id is empty");
            } catch (IdContainsWrongChars e1) {
              reqIdDecorator.setDescriptionText(
                  "Requirement Id contains wrong character - " + e1.getWrongCharacter());
            }
          }

          @Override
          public void keyPressed(KeyEvent e) {}
        });
    textId.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if(textId.isDisposed())
              return;
            if (reqIdDecorator.isVisible()) {
              reqIdDecorator.hide();
              textId.setText(node.getId().trim());
            }
            if (node == null) {
              return;
            }
            if (node.getId().equals(textId.getText())) {
              return;
            }
            if (!node.getQualifiedId().equals(Requirement.getTypeRootQId())) {
              String newId = textId.getText().trim();
              final TreeNode node = MainPropertySection.this.node;
              final Text textId = MainPropertySection.this.textId;
              WorkspaceJob j = new WorkspaceJob("Update node id "+node.getUserVisibleName()){
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if (node == null)
                    return Status.CANCEL_STATUS;
                  boolean drop = false;
                  if (node.getTreeDB().getTreeLogic().isIdValid(node, newId)) {
                    boolean started = node.getTreeDB().startTransaction("Change Requirement Id", true);
                    if(started) {
                      try {
                        node.setId(newId.trim());
                        node.commitAttributes();
                        if(started) {
                          node.getTreeDB().commit();
                          started = false;
                        }
                      }finally {
                        if(started)
                          node.getTreeDB().rollback();
                      }
                    }else
                      drop = true;
                  } else drop = true;
                  if(drop){
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      @Override
                      public void run() {
                        if(isDisposed || textId.isDisposed()||node == null)
                          return;
                        textId.setText(node.getId());
                      }
                    });
                  }
                  return Status.OK_STATUS;
                }
                  
              };
              j.schedule();
            }
          }
        });
    textNameLabel = wf.createLabel(parent, "Name:");
    textNameLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
    textNameLabel.setVisible(nameEnabled);
    textName = new NameEditPanel(parent, SWT.BORDER);
    textName.getControl().setVisible(nameEnabled);
    textName.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    tp = new Composite(parent, SWT.NONE);
    tps = new GridData(GridData.FILL_HORIZONTAL);
    tps.horizontalSpan = 2;
    tp.setLayoutData(tps);
    tp.setLayout(new GridLayout(2, false));
    wf.createLabel(tp, "Type:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
    type = new Combo(tp, SWT.READ_ONLY);
    addAvailiableTypes(node);
    type.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
    ColorRegistry colors = theme.getColorRegistry();
    type.setBackground(colors.get("org.eclipse.ui.editors.backgroundColor"));
    type.addSelectionListener(
        new SelectionListener() {

          @Override
          public void widgetSelected(SelectionEvent e) {
            if (node.getType().equals(Requirement.TYPE_NAME))
              for (UUID uid : node.getWorkChildrenUUIDs())
                if (node.getTreeDB().getNode(uid).getType().equals(Requirement.TYPE_NAME)) return;
            int ind = type.getSelectionIndex();
            final TreeNode node = MainPropertySection.this.node;
            final Combo type = MainPropertySection.this.type;
            WorkspaceJob j = new WorkspaceJob("Change type of "+node.getUserVisibleName() ){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(node == null)
                  return Status.CANCEL_STATUS;
                boolean started = node.getTreeDB().startTransaction("Change text element type");
                if (started) {
                  try {
                    if (ind < 2) {
                      node.attrs()
                          .setConstAttribute(
                              "_textNodeType", ind == 1 ? "header" : "text",Flag.HIDDEN);
                      node.setType(TextNode.TYPE_NAME);
                    } else {
                      node.setType(Requirement.TYPE_NAME);
                    }
                    node.commitAttributes();
                    if(started) {
                      started = false;
                      node.getTreeDB().commit();
                    }
                  }finally {
                    if(started)
                      node.getTreeDB().rollback();
                  }
                }else{
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    @Override
                    public void run() {
                      if(isDisposed || node == null || type.isDisposed())
                        return;
                      if(node.getType().equals(TextNode.TYPE_NAME)){
                        Attribute a = node.attrs().getAttribute("_textNodeType");
                        type.select(0);
                        if (a != null) type.select(a.getValue().toString().equals("header") ? 1 : 0);
                      }
                    }
                  });
                }
                return Status.OK_STATUS;
              }
            };
            j.schedule();
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {
            // TODO Auto-generated method stub

          }
        });

    GridData gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 2;
    attribPane = new AttributesPanel(fsid, parent);
    attribPane.getControl().setLayoutData(gridData);
    if (node != null) attribPane.setNode(node, isDisposed);
  }

  public static TreeNode getNonVNodeParent(TreeNode nde) {
    if(nde == null)
      return null;
    TreeNode node = nde.getWorkParent();
    if (node != null)
      if (VirtualNode.TYPE_NAME.equals(node.getType())) return getNonVNodeParent(node);
    return node;
  }

  private void addAvailiableTypes(TreeNode req2) {
    type.removeAll();
    type.add("TextNode(Text)");
    type.add("TextNode(Header)");
    TreeNode par = getNonVNodeParent(req2);
    if (par!=null && req2 != null
        && (Requirement.TYPE_NAME.equals(par.getType())
            || par.isRoot())) type.add("Requirement");
  }

  private ControlDecoration createDecorator(Text text) {
    ControlDecoration controlDecoration = new ControlDecoration(text, SWT.LEFT | SWT.TOP);
    FieldDecoration fieldDecoration =
        FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR);
    controlDecoration.setImage(fieldDecoration.getImage());
    return controlDecoration;
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    reqIdDecorator.hide();
    nameEnabled = false;
    if (node == null || node.isDisposed()) {
      return;
    }
    node = node;
//    if (node instanceof Location) req = toRequirement(node);
    if (node == null || textId == null || textId.isDisposed() || node.isDisposed()) {
      return;
    }
    boolean next = true;
    if (!node.getType().equals(Requirement.TYPE_NAME) && !node.getType().equals(TextNode.TYPE_NAME))
      next = false;
    if (node.getWorkParent() != null && !node.getWorkParent().getQualifiedId().equals("/")) {
      textId.setEnabled(true);
    } else {
      textId.setEnabled(false);
    }
    if (node.getType().equals(Requirement.TYPE_NAME) || node.getType().equals(TextNode.TYPE_NAME))
      nameEnabled = true;
    if (!textId.getText().equals(node.getId())) { //against blinking
      textId.setText(node.getId());
    }
    addAvailiableTypes(node);
    if (node.getType().equals(TextNode.TYPE_NAME)) {
      Attribute a = node.attrs().getAttribute("_textNodeType");
      type.select(0);
      if (a != null) type.select(a.getValue().toString().equals("header") ? 1 : 0);
    } else if (node.getType().equals(Requirement.TYPE_NAME)) type.select(2);
    type.setEnabled(true);
    if (node.getType().equals(Requirement.TYPE_NAME))
      for (TreeNode nde : node.getWorkChildren())
        if (!node.getTreeDB().getTreeLogic().canBeChildOf(nde.getType(), TextNode.TYPE_NAME))
          type.setEnabled(false);
    textName.getControl().setVisible(nameEnabled);
    textNameLabel.setVisible(nameEnabled);
    if (!nameEnabled) {
      textName.getControl().setLayoutData(new GridData(0, 0));
      textNameLabel.setLayoutData(new GridData(0, 0));
    } else {
      textName.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      textNameLabel.setLayoutData(new GridData(GridData.BEGINNING));
    }
    textName.getControl().getParent().layout();
    textName.getControl().getParent().redraw();
    if (nameEnabled) {
      textName.setTreeNode(node);
    }
    if (attribPane != null) attribPane.setNode(node, isDisposed);
    if (next != tp.isVisible()) {
      tp.setVisible(next);
      tps.exclude = !next;
      tp.getParent().getParent().layout(true, true);
    }
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return true;
  }

  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_DESCRIPTION);
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
    ids.add(TextNode.ATTR_TNTYPE);
    ids.add(TreeNode.ATTR_TYPE);
  }
  @Override
  public void dispose() {
    if(attribPane!=null)
      attribPane.setNode(null, isDisposed);
    super.dispose();
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s) || !s.startsWith("_"))
        return true;
    return false;
  };
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
