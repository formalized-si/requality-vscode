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

import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.StructuredSelection;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;

public class ProjectSettingsTemplatesSection extends TreeNodePropertiesTab {

  TemplatesPanel panel;

  @Override
  public void createControls(
      org.eclipse.swt.widgets.Composite parent,
      org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage aTabbedPropertySheetPage) {
    panel = new TemplatesPanel(parent);
  };

  @Override
  public void setInput(
      org.eclipse.ui.IWorkbenchPart part, org.eclipse.jface.viewers.ISelection selection) {
    if (selection != null) {
      Object sel = ((StructuredSelection) selection).getFirstElement();
      if(sel instanceof IProject) {
        IProject p = (IProject) sel;
        if (p != null)
		  try {
		    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
			panel.setTreeDB(et.getTreeDB(p, null, et.getLocalName(p)));
		  } catch (IOException e) {
			RLogger.logError(e);
		  }
      }else if (sel instanceof TreeNode) {
        setTreeNode((TreeNode)sel);
      }
    }
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    if(node!=null && node.isRoot())
      panel.setTreeDB(node.getTreeDB());
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return false; //TODO fix;
  }

  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    return false;
  };
}
