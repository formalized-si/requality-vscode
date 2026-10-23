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
import com.unitesk.requality.nodetype.Requirement;

public class AdvancedPropertySection extends TreeNodePropertiesTab {
  private Text textPredicate;
  private Requirement req;


  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = aTabbedPropertySheetPage.getWidgetFactory();

    parent.setLayout(new GridLayout());
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));

    wf.createLabel(parent, "Predicate:");

    textPredicate = wf.createText(parent, "", SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    textPredicate.setLayoutData(new GridData(GridData.FILL_BOTH));
    textPredicate.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            //if new attribute text is "", then remove this attribute rather than set it to "",
            //because otherwise we get modified .json-files with empty attribute added,
            //when simply navigating nodes in Requality Explorer and Properties View,
            //and this is affecting .svn
            if (req == null) {
              return;
            }
            String newDescr = textPredicate.getText();
            final Requirement req = AdvancedPropertySection.this.req;
            final Text textPredicate = AdvancedPropertySection.this.textPredicate;
            WorkspaceJob j = new WorkspaceJob("Change predicate for "+(node!=null?node.getUserVisibleName():"NULL")){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(req == null || isDisposed)
                  return Status.CANCEL_STATUS;
                String oldDescr = req.getPredicate();
                if (oldDescr == null) {
                  oldDescr = "";
                }
                if (oldDescr.equals(newDescr)) {
                  return Status.OK_STATUS;
                }
                String foldDescr = oldDescr;
                boolean started = req.getTreeDB().startTransaction("Update Requirement", true);
                if (started) {
                  try {
                    req.setPredicate(newDescr);
                    req.commitAttributes();
                    if(started) {
                      req.getTreeDB().commit();
                      started = false;
                    }
                  }finally {
                    if(started)
                      req.getTreeDB().rollback();
                  }
                } else EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(isDisposed || textPredicate.isDisposed())
                      return;
                    textPredicate.setText(foldDescr);
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
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_PREDICATE.equals(a);
  };

  @Override
  protected void setTreeNode(TreeNode node) {
    req = toRequirement(node);

    if (req == null || textPredicate == null || textPredicate.isDisposed()) {
      return;
    }
    textPredicate.setText(req.getPredicate());
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    return what.getAll().contains(TreeNode.ATTR_PREDICATE);
  };
}
