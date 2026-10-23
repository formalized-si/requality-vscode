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
package com.unitesk.requality.eclipse.views.properties;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.views.panels.AttributesPanel;
import com.unitesk.requality.eclipse.views.panels.NameEditPanel;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;

public class TestMainPropertySection extends TreeNodePropertiesTab {
  private TestPurpose testPurpose;

  private Text testPurposeID; // Test Case Number
  private Combo status; // Status
  private Text author; // Author
  private AttributesPanel attribPane; // Attributes
  private NameEditPanel textName;

  private ControlDecoration testPurposeIDDecorator;


  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = getWidgetFactory();

    parent.addFocusListener(
        new FocusListener() {
          @Override
          public void focusLost(FocusEvent e) {}

          @Override
          public void focusGained(FocusEvent e) {}
        });
    parent.setLayout(new GridLayout(2, false));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));

    wf.createLabel(parent, "Id:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    testPurposeID = wf.createText(parent, "", SWT.BORDER);
    testPurposeID.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    testPurposeIDDecorator = createDecorator(testPurposeID);
    testPurposeIDDecorator.hide();
    testPurposeID.addModifyListener(
        new ModifyListener() {

          @Override
          public void modifyText(ModifyEvent e) {
            if (testPurpose == null) return;
            if(testPurposeID.isDisposed())return;
            String newId = testPurposeID.getText().trim();
            try {
              testPurposeIDDecorator.show();
              if (testPurpose.getTreeDB().getTreeLogic().isIdValidExc(testPurpose, newId)) {
                testPurposeIDDecorator.hide();
              } else {
                testPurposeIDDecorator.setDescriptionText(
                    "Test purpose Id is not correct and Test purpose Id should not be empty");
                testPurposeIDDecorator.show();
              }
            } catch (SameIdExistsInChildren e1) {
              testPurposeIDDecorator.setDescriptionText("Element with the same Id already exists");
            } catch (IdEmpty e1) {
              testPurposeIDDecorator.setDescriptionText("Test purpose Id is empty");
            } catch (IdContainsWrongChars e1) {
              testPurposeIDDecorator.setDescriptionText(
                  "Test purpose Id contains wrong character - " + e1.getWrongCharacter());
            }
          }
        });
    testPurposeID.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (testPurpose == null) return;
            if(testPurposeID.isDisposed()) return;
            String newId = testPurposeID.getText().trim();
            testPurposeID.setText(newId);
            if (testPurpose.getId().equals(newId)) {
              return;
            }
            if (!testPurpose.getQualifiedId().equals(Requirement.getTypeRootQId())) {
              if (testPurpose.getTreeDB().getTreeLogic().isIdValid(testPurpose, newId)) {
            	  final TestPurpose testPurpose = TestMainPropertySection.this.testPurpose;
                  final Text testPurposeID = TestMainPropertySection.this.testPurposeID;
                  final TreeNode node = TestMainPropertySection.this.node;
                  WorkspaceJob j = new WorkspaceJob("Update test purpose id "+node.getUserVisibleName()){
                    @Override
                    public IStatus runInWorkspace(IProgressMonitor monitor) {
                      if(testPurpose == null)
                        return Status.CANCEL_STATUS;
                      boolean started = testPurpose.getTreeDB().startTransaction("Update Test Purpose for "+node.getUserVisibleName(), true);
                      if(started){
                        try {
                          testPurpose.setId(newId);
                          testPurpose.commitAttributes();
                          if(started) {
                            started = false;
                            testPurpose.getTreeDB().commit();
                          }
                        }finally {
                          if(started)
                            testPurpose.getTreeDB().rollback();
                        }
                      }else
                        EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                          @Override
                          public void run() {
                            if(isDisposed || testPurposeID.isDisposed()||testPurpose == null)
                              return;
                            testPurposeID.setText(testPurpose.getId());
                          }
                        });
                      return Status.OK_STATUS;
                    }
                };
                j.schedule();
              } else {
                testPurposeID.setText(testPurpose.getId());
              }
            }
          }
        });

    wf.createLabel(parent, "Name:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    textName = new NameEditPanel(parent, SWT.BORDER);
    textName.getControl().setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    wf.createLabel(parent, "Status:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    status = new Combo(parent, SWT.BORDER | SWT.READ_ONLY);
    status.setItems(new String[] {"in process", "complete", "verified"});
    status.addSelectionListener(
        new SelectionListener() {
          public void widgetSelected(SelectionEvent e) {
        	  if (testPurpose == null) return;
              if(status.isDisposed()) return;
              String oldStatus = testPurpose.attrs().getStringAttributeValue(TestPurpose.ATTR_STATUS);
              if (oldStatus == null) {
                oldStatus = "";
              }
              String newStatus =  status.getText();
              if (oldStatus.equals(newStatus)) {
                return;
              }
              String foldStatus =oldStatus; 
              final TestPurpose testPurpose = TestMainPropertySection.this.testPurpose;
              final Combo status = TestMainPropertySection.this.status;
              WorkspaceJob j = new WorkspaceJob("Update test purpose status "+node.getUserVisibleName()){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(testPurpose == null)
                    return Status.CANCEL_STATUS;
                  boolean started = testPurpose.getTreeDB().startTransaction("Update Test Purpose status for "+node.getUserVisibleName(), true);
                  if (started) {
                    try {
                      testPurpose.attrs().setConstAttribute(TestPurpose.ATTR_STATUS, newStatus,Flag.HIDDEN);
                      testPurpose.commitAttributes();
                      if(started) {
                        started = false;
                        testPurpose.getTreeDB().commit();
                      }
                    }finally {
                      if(started) 
                        testPurpose.getTreeDB().rollback();  
                    }
                  }else
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
    
                      @Override
                      public void run() {
                        if(isDisposed || status.isDisposed())
                          return;
                        status.setText(foldStatus);
                      }
                      
                    });
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
          }

          public void widgetDefaultSelected(SelectionEvent e) {}
        });

    wf.createLabel(parent, "Author:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    author = wf.createText(parent, "", SWT.BORDER);
    author.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    author.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (testPurpose == null) return;
            if(author.isDisposed()) return;
            String oldAuthor = testPurpose.attrs().getStringAttributeValue(TestPurpose.ATTR_AUTHOR);
            if (oldAuthor == null) {
              oldAuthor = "";
            }
            String newAuthor = author.getText();
            if (oldAuthor.equals(newAuthor)) {
              return;
            }
            String foldAuthor = oldAuthor;
            final TestPurpose testPurpose = TestMainPropertySection.this.testPurpose;
            final Text author = TestMainPropertySection.this.author;
            WorkspaceJob k = new WorkspaceJob("Update test purpose author for "+node.getUserVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(testPurpose == null)
                  return Status.CANCEL_STATUS;
                if (testPurpose.getTreeDB().startTransaction("Update Test Purpose author on "+node.getUserVisibleName(), true)) {
                  if (!newAuthor.equals("")) {
                    testPurpose.attrs().setConstAttribute(TestPurpose.ATTR_AUTHOR, newAuthor,Flag.HIDDEN);
                  } else {
                    testPurpose.attrs().removeAttribute(TestPurpose.ATTR_AUTHOR);
                  }
                  testPurpose.commitAttributes();
                  testPurpose.getTreeDB().commit();
                } else 
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    @Override
                    public void run() {
                      if(isDisposed || author.isDisposed())
                        return;
                      String lfoldAuthor = foldAuthor;
                      if(foldAuthor == null)
                        lfoldAuthor = "";
                      author.setText(lfoldAuthor);
                    }
                  });
                return Status.OK_STATUS;
              }
              
            };
            k.schedule();
          }
        });

    attribPane = new AttributesPanel(fsid, parent);
    GridData gridData = new GridData(GridData.FILL_BOTH);
    gridData.horizontalSpan = 2;
    attribPane.getControl().setLayoutData(gridData);
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
    if (!(node instanceof TestPurpose) || testPurposeID == null || testPurposeID.isDisposed()) {
      return;
    }
    testPurposeIDDecorator.hide();
    testPurpose = (TestPurpose) node;
    testPurposeID.setText(testPurpose.getId());
    status.select(
        status.indexOf(testPurpose.attrs().getStringAttributeValue(TestPurpose.ATTR_STATUS, "")));
    author.setText(testPurpose.attrs().getStringAttributeValue(TestPurpose.ATTR_AUTHOR, ""));
    attribPane.setNode(testPurpose, isDisposed);
    String name = node.getName();
    if (name == null) name = "";
    textName.setTreeNode(testPurpose);
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return true; //TODO fix;
  }
  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TestPurpose.ATTR_STATUS);
    ids.add(TestPurpose.ATTR_AUTHOR);
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String name:what.getAll())
      if(!name.startsWith("_") || ids.contains(name))
        return true;
    return false;
  };
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
