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

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.Requirement;

public class RepoPropertySection extends TreeNodePropertiesTab{

  private TreeNode node;
  Button ch;
  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    parent.setLayout(new GridLayout());
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    Label l = new Label(parent, SWT.NONE);
    l.setText("Repository state:");
    ch = new Button(parent, SWT.CHECK);
    ch.setText("Local only");
    ch.addSelectionListener(new SelectionListener() {
    
    @Override
    public void widgetSelected(SelectionEvent e) {
      // TODO Auto-generated method stub
      try {
    	boolean bool = ch.getSelection();
    	final TreeNode node = RepoPropertySection.this.node;
        final Button ch = RepoPropertySection.this.ch;
        WorkspaceJob j = new WorkspaceJob("Update repo settings for "+node.getUserVisibleName()){

          @Override
          public IStatus runInWorkspace(IProgressMonitor monitor) {
             if(node == null)
              return Status.CANCEL_STATUS;
            boolean s = node.getTreeDB().startTransaction("Change repository ignore status for "+node.getUserVisibleName());
            if(!s){
              EclipseAPI.getDisplay().asyncExec(new Runnable(){
                
                @Override
                public void run() {
                  if(isDisposed || node == null || ch.isDisposed())
                    return;
                  updateCh(node.getType(), node);
                }
              });
            }else
	            try{
	              Global.getRepoManager(node.getTreeDB()).setIgnoredState( 
	                node.getWorkParent(), node, node.getId(), bool);
	              node.updateLocalAttribute(TreeNode.ATTR_REPO_STATE);
	              node.commitAttributes();
	              ResManager.getManager().refresh(node.getTreeDB(), node, 2);
                  if(s) {
                    node.getTreeDB().commit();
                    s = false;
                  }
	            }
	            finally{
	              if(s)
	                node.getTreeDB().rollback();
	            }
            return Status.OK_STATUS;
          }
          
        };
        j.schedule();
      } catch (Exception e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
        
    }
    
    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
      // TODO Auto-generated method stub
      
    }
  });
  }


  @Override
  public void aboutToBeHidden() {
  };


  @Override
  protected void setTreeNode(TreeNode node) {
    try {
      this.node = node;
      String tp = null;
      if(node!=null)
        tp = node.getType();
      updateCh(tp, node);
    } catch (Exception e) {
    // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void updateCh(String tp, TreeNode node) {
    if(ch==null || ch.isDisposed())
      return;
    ch.setEnabled(false);
    ch.setSelection(false);
    if(node == null)
      return;
    for(String s:RepositoryFilter.ALLOWED)
      if(s.equals(tp))
        ch.setEnabled(true);
    boolean ig = node.attrs().getBooleanAttributeValue(TreeNode.ATTR_REPO_STATE);
    if(ig){
      ch.setSelection(true);
      if(!node.attrs().getAttribute(TreeNode.ATTR_REPO_STATE).getOrigin().equals(node.getUUId()))
        ch.setEnabled(false);
    }
  }
  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_ID.equals(a)
        || TreeNode.ATTR_NAME.equals(a)
        || Requirement.ATTR_LOCATIONS.equals(a);
  }
  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
    ids.add(Requirement.ATTR_LOCATIONS);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s))//TODO iterator check
        return true;
    return false;
  };
}
