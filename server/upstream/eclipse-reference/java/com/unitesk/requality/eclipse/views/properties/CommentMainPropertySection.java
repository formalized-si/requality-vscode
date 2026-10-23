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
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.views.panels.ElementDescriptionPanel;
import com.unitesk.requality.nodetype.Comment;

public class CommentMainPropertySection extends TreeNodePropertiesTab {
  private Comment comment;

  private Text commentID, // Test Case Number
      commentName;
  private Text author;
  private ElementDescriptionPanel adesc;
  private Composite par;

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    TabbedPropertySheetWidgetFactory wf = getWidgetFactory();
    par = parent;
    parent.addFocusListener(
        new FocusListener() {
          @Override
          public void focusLost(FocusEvent e) {}

          @Override
          public void focusGained(FocusEvent e) {}
        });
    parent.setLayout(new GridLayout(2, false));
    //parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    wf.createLabel(parent, "Id:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    commentID = wf.createText(parent, "", SWT.BORDER);
    commentID.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    commentID.setEnabled(false);

    wf.createLabel(parent, "Name:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    commentName = wf.createText(parent, "");
    commentName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    commentName.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (!commentName.getText().equals(commentName.getText().trim())) {
              commentName.setText(commentName.getText().trim());
            }
            if (node == null) {
              return;
            }
            if (node.getName() == null && commentName.getText().equals("")
                || node.getName() != null && node.getName().equals(commentName.getText())) {
              return;
            }
            String newName = commentName.getText();
            final Text commentName = CommentMainPropertySection.this.commentName;
            final TreeNode node = CommentMainPropertySection.this.node;
            WorkspaceJob j = new WorkspaceJob("Change name for "+node.getUserVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                boolean started = node.getTreeDB().startTransaction("Update Requirement", true);
                 if (started) {
                   try {
                     node.setName(newName);
                     node.commitAttributes();
                     if(started) {
                       node.getTreeDB().commit();
                       started = false;
                     }
                   }finally {
                     if(started)
                       node.getTreeDB().rollback();
                   }
                } else
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  
                    @Override
                    public void run() {
                      if(isDisposed || commentName.isDisposed()||node ==null)
                        return;
                      commentName.setText(node.getName() != null ? node.getName() : "");
                    }
                  });
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        });

    wf.createLabel(parent, "Author:").setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

    author = wf.createText(parent, "", SWT.BORDER);
    author.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    author.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (updating) return;
            if (comment == null) return;
            String newAuthor = author.getText().trim(),
                old = comment.attrs().getStringAttributeValue(Comment.ATTR_AUTHOR);
            if (old != null && old.equals(newAuthor)) return;
            final Comment comment = CommentMainPropertySection.this.comment;
            final Text author = CommentMainPropertySection.this.author; 
            WorkspaceJob j = new WorkspaceJob("Update commit author for "+node.getUserVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                if(comment == null)
                  return Status.CANCEL_STATUS;
                boolean started = comment.getTreeDB().startTransaction("Update Comment Author on "+(comment!=null?comment.getUserVisibleName():"null node"), true); 
                if (started) {
                  try {
                    comment.attrs().setConstAttribute(Comment.ATTR_AUTHOR, newAuthor, Flag.HIDDEN);
                    comment.commitAttributes();
                    if(started) {
                      comment.getTreeDB().commit();
                      started = false;
                    }
                  }finally {
                    if(started)
                      comment.getTreeDB().rollback();
                  }
                } else EclipseAPI.getDisplay().asyncExec(new Runnable(){
                  @Override
                  public void run() {
                    if(isDisposed || author.isDisposed())
                      return;
                    author.setText(old);
                  }
                });
                return Status.OK_STATUS;
              }
                
            };
            j.schedule();
          }
        });
    GridData gridData = new GridData();
    gridData.verticalAlignment = GridData.FILL;
    gridData.horizontalSpan = 2;
    GridData gridData2 = new GridData(GridData.FILL_BOTH);
    gridData2.horizontalSpan = 2;

    
    adesc =
        new ElementDescriptionPanel(
            wf,"Description", parent, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    adesc.getControl().setLayoutData(gridData2);
    adesc.getEditControl().addFocusListener(getDescriptionFocusAdapter(adesc, adesc.getEditControl(), node));

//    adesc = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
//    adesc.setLayoutData(gridData2);
//    adesc.addFocusListener(getDescriptionFocusAdapter(adesc, node));
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    if (!(node instanceof Comment) || commentID == null || commentID.isDisposed()) {
      return;
    }
    comment = (Comment) node;
    commentID.setText(comment.getId());
    String auth = comment.attrs().getStringAttributeValue(Comment.ATTR_AUTHOR);
    author.setText(auth == null ? "" : auth);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.grabExcessHorizontalSpace = true;
    gd.grabExcessVerticalSpace = true;
    if (par.getSize().x > 300) gd.widthHint = par.getSize().x - 300;
    else gd.widthHint = par.getSize().x;
    gd.horizontalSpan = 2;
    adesc.getControl().setLayoutData(gd);
    String text = comment.getName();
    if (text == null) text = "";
    commentName.setText(text);
    final Comment comment = CommentMainPropertySection.this.comment;
    final ElementDescriptionPanel adesc = CommentMainPropertySection.this.adesc;
    WorkspaceJob j = new WorkspaceJob("Loading description for comment "+comment.getUserVisibleName()){

	   @Override
       public IStatus runInWorkspace(IProgressMonitor monitor) {
         if(comment == null)
            return Status.CANCEL_STATUS;
          String desc = comment.getDescription(true,true, false), rawdesc = comment.getRawDescription();
          if (desc == null) desc = "";
          String fdesc = desc;
          EclipseAPI.getDisplay().asyncExec(new Runnable(){
            @Override
            public void run() {
              if(isDisposed || adesc.getControl().isDisposed())
                return;
              adesc.setDocument(rawdesc,fdesc); 
            }
          });
          return Status.OK_STATUS;
        }
      };
      j.schedule();
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_TYPE.equals(a)
        || TreeNode.ATTR_ID.equals(a)
        || TreeNode.ATTR_NAME.equals(a)
        || TreeNode.ATTR_DESCRIPTION.equals(a)
        || Comment.ATTR_AUTHOR.equals(a);
  }
  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_TYPE);
    ids.add(TreeNode.ATTR_DESCRIPTION);
    ids.add(Comment.ATTR_AUTHOR);
    ids.add(TreeNode.ATTR_NAME);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s))//TODO iterator check
        return true;
    return false;
  };
}
