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
import java.util.stream.Collectors;

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
import com.unitesk.requality.tool.CoreUtils;

public class TestDescriptionPropertySection extends TreeNodePropertiesTab {

  private TestPurpose testPurpose;

  private Text description; // Test Case Description
  private Text results; // Expected Results
  private Composite par;

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = aTabbedPropertySheetPage.getWidgetFactory();
    par = parent;
    parent.setLayout(new GridLayout());
    //parent.setLayoutData(new GridData(GridData.FILL_BOTH));

    wf.createLabel(parent, "Test purpose description:");

    description = wf.createText(parent, "", SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    description.setLayoutData(new GridData(GridData.FILL_BOTH));
    description.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (testPurpose == null) return;
            if(description.isDisposed())return;
            String newDescr = CoreUtils.htmlEncapsulate(description.getText());
            description.setText(newDescr);
            final TestPurpose testPurpose = TestDescriptionPropertySection.this.testPurpose;
            final Text description = TestDescriptionPropertySection.this.description;
            WorkspaceJob j = new WorkspaceJob("Update test purpose description"){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(testPurpose == null)
                  return Status.CANCEL_STATUS;
                String oldDescr = testPurpose.getDescription();
                if (oldDescr == null) {
                  oldDescr = "";
                }
                if (oldDescr.equals(newDescr)) {
                  return Status.CANCEL_STATUS;
                }
                String raw = testPurpose.getRawDescription();
                boolean started = testPurpose.getTreeDB().startTransaction("Update Test Purpose", true);
                if (started) {
                  try {
                    testPurpose.setDescription(newDescr);
                    //TODO strange if
                    if(testPurpose.attrs().getChanged().contains(TreeNode.ATTR_DESCRIPTION)){
                      testPurpose.commitAttributes();
                      if(started) {
                        started = false;
                        testPurpose.getTreeDB().commit();
                      }
                    }else{
                      
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                        @Override
                        public void run() {
                          if(isDisposed || description.isDisposed())
                            return;
                          String sraw = raw;
                          if(sraw == null)
                            sraw = "";
                          description.setText(sraw); 
                        }
                      });
                    }
                  }finally {
                    if(started)
                      testPurpose.getTreeDB().rollback();
                  }
//                  testPurpose.commitAttributes();
//                  testPurpose.getTreeDB().commit();
                } else 
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    @Override
                    public void run() {
                	  if(isDisposed || description.isDisposed())
                        return;
                      String lraw = raw;
                      if(raw == null)
                        lraw = "";
                      description.setText(lraw); 
                    }
                  });
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        });

    wf.createLabel(parent, "Expected results:");

    results = wf.createText(parent, "", SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    results.setLayoutData(new GridData(GridData.FILL_BOTH));
    results.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (testPurpose == null) return;
            if(results.isDisposed())
                return;
              String newResults = CoreUtils.htmlEncapsulate(results.getText());
              results.setText(newResults);
              final TestPurpose testPurpose = TestDescriptionPropertySection.this.testPurpose;
              final Text results = TestDescriptionPropertySection.this.results;
              WorkspaceJob j = new WorkspaceJob("Change expected results for "+(node!=null?node.getUserVisibleName():"null node")){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(testPurpose == null)
                    return Status.CANCEL_STATUS;
                  String oldResults = testPurpose.getExpectedResults();
                  if (oldResults == null) {
                    oldResults = "";
                  }
                  if (oldResults.equals(newResults)) {
                    return Status.CANCEL_STATUS;
                  }
                  String foldResults = oldResults;
                  boolean started = testPurpose.getTreeDB().startTransaction("Update Test Purpose", true); 
                  if (started) {
                    try {
                      if (!newResults.equals("")) {
                        testPurpose.setExpectedResults(newResults);
                      } else {
                        testPurpose.attrs().removeAttribute(TestPurpose.ATTR_EXPECTED_RESULTS);
                      }
                      testPurpose.commitAttributes();
                      if(started) {
                        testPurpose.getTreeDB().commit();
                        started = false;
                      }
                    }finally {
                      if(started)
                        testPurpose.getTreeDB().rollback();
                    }
                  } else EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  
                    @Override
                    public void run() {
                      if(isDisposed || results.isDisposed())
                        return;
                      String lfoldResults = foldResults;
                      if(foldResults==null)
                        lfoldResults = "";
                      results.setText(lfoldResults);
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
    if (!(node instanceof TestPurpose) || description == null || description.isDisposed()) {
      return;
    }
    testPurpose = (TestPurpose) node;
    // getDescription() returns null instead of ""
    // if corresponding attribute isn't presented in node
    description.setText(testPurpose.getDescription() != null ? testPurpose.getDescription() : "");
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.grabExcessHorizontalSpace = true;
    gd.grabExcessVerticalSpace = true;
    if (par.getSize().x > 300) gd.widthHint = par.getSize().x - 300;
    else gd.widthHint = par.getSize().x;
    description.setLayoutData(gd);
    results.setText(testPurpose.getExpectedResults());
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_DESCRIPTION.equals(a)
        || TestPurpose.ATTR_EXPECTED_RESULTS.equals(a); //TODO fix;
  };
  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_DESCRIPTION);
    ids.add(TestPurpose.ATTR_EXPECTED_RESULTS);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    return what.getAll().stream().filter(ids::contains).collect(Collectors.toSet()).size()>0;
  };
}
