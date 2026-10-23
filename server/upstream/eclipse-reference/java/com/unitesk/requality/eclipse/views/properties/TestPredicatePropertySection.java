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

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.TestPurpose;

public class TestPredicatePropertySection extends TreeNodePropertiesTab {

  private TestPurpose testPurpose;

  private Text predicate; // Test Predicate

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = aTabbedPropertySheetPage.getWidgetFactory();

    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setLayout(new GridLayout());

    wf.createLabel(parent, "Predicate:");

    predicate = wf.createText(parent, "", SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    predicate.setLayoutData(new GridData(GridData.FILL_BOTH));
    predicate.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (testPurpose == null) return;
            if(predicate.isDisposed()) return;
            String oldPredicate = testPurpose.getPredicate();
            if (oldPredicate == null) {
              oldPredicate = "";
            }
            String newPredicate = predicate.getText();
            if (oldPredicate.equals(newPredicate)) {
              return;
            }
            String foldPredicate = oldPredicate;
            final TestPurpose testPurpose = TestPredicatePropertySection.this.testPurpose;
            final Text predicate = TestPredicatePropertySection.this.predicate;
            WorkspaceJob j = new WorkspaceJob("Change predicate for "+node.getUserVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(testPurpose == null)
                  return Status.CANCEL_STATUS;
                boolean started = testPurpose.getTreeDB().startTransaction("Update Predicate for "+testPurpose.getUserVisibleName(), true);
                if (started) {
                  try {
                    testPurpose.setPredicate(newPredicate);
                    testPurpose.commitAttributes();
                    if(started) {
                      started = false;
                      testPurpose.getTreeDB().commit();
                    }
                  }
                  finally
                  {
                    if(started) {
                      testPurpose.getTreeDB().rollback();  
                    }
                  }
                } else EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(isDisposed || predicate.isDisposed())
                      return;
                    predicate.setText(foldPredicate);
                  }
                });
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        });
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    if (!(node instanceof TestPurpose) || predicate == null || predicate.isDisposed()) {
      return;
    }
    testPurpose = (TestPurpose) node;
    predicate.setText(testPurpose.getPredicate());
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_PREDICATE.equals(a); //TODO fix;
  }

  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    // TODO Auto-generated method stub
    return checkNodeChanges(source)||what.contains(TreeNode.ATTR_PREDICATE);
  };
}
