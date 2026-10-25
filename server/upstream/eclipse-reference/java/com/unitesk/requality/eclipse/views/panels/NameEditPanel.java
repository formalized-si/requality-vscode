package com.unitesk.requality.eclipse.views.panels;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.tool.CoreUtils;

public class NameEditPanel {
  private Text instancerName;
  private TreeNode node;
  private Composite nameParent;
  private Button check;
  private boolean transactionIgnore = false;

  public NameEditPanel(final Composite parent, int swt) {
    nameParent = new Composite(parent, SWT.None);
    nameParent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    GridLayout gl = new GridLayout(2, false);
    gl.marginHeight=gl.marginWidth=0;
    nameParent.setLayout(gl);
    instancerName = new Text(nameParent, swt);
    instancerName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    instancerName.addFocusListener(
        new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (!instancerName.getText().equals(instancerName.getText().trim())) {
              instancerName.setText(instancerName.getText().trim());
            }
            if (node == null) {
              return;
            }
            if (node.getRawName() == null && instancerName.getText().equals("")
                || node.getRawName() != null && node.getRawName().equals(instancerName.getText())) {
              return;
            }
            String newName = instancerName.getText();
            final TreeNode node = NameEditPanel.this.node;
            final boolean transactionIgnore = NameEditPanel.this.transactionIgnore;
            final Text instancerName = NameEditPanel.this.instancerName;
            WorkspaceJob j = new WorkspaceJob("Update name for "+node.getUserVisibleName()){
              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                boolean cont = transactionIgnore;
                boolean started = !cont && node.getTreeDB()
                    .startTransaction("Update " + CoreUtils.splitWords(node.getType()), true);
                cont = cont || started;
                if (cont) {
                  try {
                    node.setName(newName);
                    node.commitAttributes();
                    if (started) {
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
                      if(instancerName.isDisposed()||node == null)
                        return;
                      instancerName.setText(node.getName() != null ? node.getName() : "");
                    }
                    
                  });
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        });
    check = new Button(nameParent, SWT.CHECK);
    GridData gd = new GridData(SWT.LEFT, SWT.CENTER, false,true);
    check.setLayoutData(gd);
    check.setText("Ignore");
    check.addSelectionListener(new SelectionListener() {
      @Override
      public void widgetSelected(SelectionEvent se) {
        if(node!=null) {
          TreeDB db = node.getTreeDB();
          boolean start = db.startTransaction("Change ignore name setting for "+node.getUserVisibleName()+"["+db.getProjectName()+"]");
          if(!start) {
            check.setSelection(node.attrs().getBooleanAttributeValue(TreeNode.ATTR_IGNORE_NAME, false));
            return;
          }
          try {
            boolean val = check.getSelection();
            if(val)
              node.attrs().setConstAttribute(TreeNode.ATTR_IGNORE_NAME, true, (byte)0);
            else
              node.attrs().removeAttribute(TreeNode.ATTR_IGNORE_NAME);
            node.commitAttributes();
            db.commit();
          }catch(Exception ex) {
            RLogger.logError(ex);
            db.rollback();
          }
        }
      }
      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
      }
    });
  }

  public void setTreeNode(TreeNode nde) {
    node = nde;
    if (nde != null) {
      String s = nde.getRawName();
      instancerName.setText(s == null ? "" : s);
      check.setSelection(node.attrs().getBooleanAttributeValue(TreeNode.ATTR_IGNORE_NAME, false));
    } else {
      instancerName.setText("");
      check.setSelection(false);
    }
  }

  public Text getControl() {
    return instancerName;
  }

  public void setTransactionIgnore(boolean transactionIgnore) {
    this.transactionIgnore = transactionIgnore;
  }
}
