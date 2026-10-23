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
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.views.panels.AttributesPanel;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Requirement;

public class InstancerFolderMainPropertySection extends TreeNodePropertiesTab {
  private InstancesFolder instancer;
  private Text instancerID;
  private AttributesPanel attribPane; // Attributes
  private ControlDecoration testPurposeIDDecorator;
  private CCombo status;


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

    instancerID = wf.createText(parent, "", SWT.BORDER);
    instancerID.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    testPurposeIDDecorator = createDecorator(instancerID);
    testPurposeIDDecorator.hide();
    instancerID.addModifyListener(
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
            try {
              if (instancer == null) return;
              if(instancerID.isDisposed()) return;
              testPurposeIDDecorator.show();
              if (instancer
                  .getTreeDB()
                  .getTreeLogic()
                  .isIdValidExc(instancer, instancerID.getText().trim())) {
                testPurposeIDDecorator.hide();
              } else {
                if (instancerID.getText().trim().equals("")) {
                  testPurposeIDDecorator.setDescriptionText("Instancer Id should not be empty");
                } else {
                  testPurposeIDDecorator.setDescriptionText(
                      "Instancer Id is not correct or Element with the same Id already exists");
                }
                testPurposeIDDecorator.show();
              }
            } catch (SameIdExistsInChildren e1) {
              testPurposeIDDecorator.setDescriptionText("Node with same Id already exists");
            } catch (IdEmpty e1) {
              testPurposeIDDecorator.setDescriptionText("Test Purpose Id is empty");
            } catch (IdContainsWrongChars e1) {
              testPurposeIDDecorator.setDescriptionText(
                  "Test Purpose Id contains wrong character - " + e1.getWrongCharacter());
            }
          }
        });
    instancerID.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (instancer == null) return;
            if(instancerID.isDisposed()) return;
            String newId = instancerID.getText().trim();
            instancerID.setText(newId);
            if (instancer.getId().equals(newId)) {
              return;
            }
            if (!instancer.getQualifiedId().equals(Requirement.getTypeRootQId())) {
              if (instancer.getTreeDB().getTreeLogic().isIdValid(instancer, newId)) {
            	  final TreeNode instancer = InstancerFolderMainPropertySection.this.instancer;
                  final Text instancerID = InstancerFolderMainPropertySection.this.instancerID;
                  WorkspaceJob j = new WorkspaceJob("Update id for "+instancer.getUserVisibleName()){

                    @Override
                    public IStatus runInWorkspace(IProgressMonitor monitor) {
                      if(instancer!=null) {
                       boolean started = instancer.getTreeDB().startTransaction("Update Instancer", true);
                       if(started){
                         try {
                           instancer.setId(newId);
                           instancer.commitAttributes();
                           if(started) {
                             instancer.getTreeDB().commit();
                             started = false;
                           }
                         }finally {
                           if(started)
                             instancer.getTreeDB().rollback();
                         }
                       }else
                         EclipseAPI.getDisplay().asyncExec(new Runnable(){
                         @Override
                         public void run() {
                           if(isDisposed || instancerID.isDisposed()||instancer == null)
                             return;
                           instancerID.setText(instancer.getId());
                         }
                       });
                      }
                     return Status.OK_STATUS;
                   }
                      
                 };
                 j.schedule();
               } else {
                 instancerID.setText(instancer.getId());
               }
             }
           }
         });

    wf.createLabel(parent, "Active status:")
        .setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
    status = wf.createCCombo(parent, SWT.READ_ONLY);
    status.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    status.add("Edit", 0);
    status.add("View", 1);
    status.addModifyListener(
        new ModifyListener() {
          @Override
          public void modifyText(ModifyEvent e) {
    	    if(status.isDisposed())
              return;
            int st = status.indexOf(status.getText());
            final InstancesFolder instancer = InstancerFolderMainPropertySection.this.instancer;
            final CCombo status = InstancerFolderMainPropertySection.this.status;
            if (instancer!=null && st > -1 && st != instancer.getDisplayStatus()){
              WorkspaceJob j = new WorkspaceJob("update id for "+instancer.getUserVisibleName()){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
            	  if(instancer!=null) {
            	    boolean started = instancer.getTreeDB().startTransaction("Update Instancer", true);
                    if(started){
                      try {
                        instancer.setDisplayStatus(st);
                        instancer.commitAttributes();
                        if(started) {
                          instancer.getTreeDB().commit();
                          started = false;
                        }
                      }finally {
                        if(started)
                          instancer.getTreeDB().rollback();
                      }
                    }else
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                        @Override
                        public void run() {
                          if(isDisposed || status.isDisposed() || instancer == null)
                            return;
                          status.select(instancer.getDisplayStatus());
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
    if (!(node instanceof InstancesFolder) || isDisposed) {
      return;
    }
    instancer = (InstancesFolder) node;
    instancerID.setText(instancer.getId());
    attribPane.setNode(instancer, isDisposed);
    status.select(instancer.getDisplayStatus());
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return true;
  }

  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
    ids.add(InstancesFolder.ATTR_DISPLAY_STATUS);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s) || !s.startsWith("_"))//TODO iterator check
        return true;
    return false;
  };
  @Override
  public boolean isSelectionSource() {
    return true;
  }
}
