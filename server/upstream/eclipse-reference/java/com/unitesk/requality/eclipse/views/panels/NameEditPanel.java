package com.unitesk.requality.eclipse.views.panels;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.tool.CoreUtils;

public class NameEditPanel {
  private Text instancerName;
  private TreeNode node;
  private boolean transactionIgnore = false;

  public NameEditPanel(final Composite parent, int swt) {
    instancerName = new Text(parent, swt);
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
  }

  public void setTreeNode(TreeNode nde) {
    node = nde;
    if (nde != null) {
      String s = nde.getRawName();
      instancerName.setText(s == null ? "" : s);
    } else instancerName.setText("");
  }

  public Text getControl() {
    return instancerName;
  }

  public void setTransactionIgnore(boolean transactionIgnore) {
    this.transactionIgnore = transactionIgnore;
  }
}
