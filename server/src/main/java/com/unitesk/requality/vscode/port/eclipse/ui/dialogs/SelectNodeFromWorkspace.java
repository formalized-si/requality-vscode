package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.filter.IObjectFilter;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;

public class SelectNodeFromWorkspace extends SelectNodeDialog {

  private TreeNode sel = null;
  public SelectNodeFromWorkspace(Shell shell, boolean ignoretrans, TreeNode node) {
    super(shell,null,ignoretrans);
    cnp = new RequalityContentProvider(true);
    sel = node;
    setCanSelectRoot(true);
  }
  @Override
  protected Control createDialogArea(Composite parent) {
    Control c =  super.createDialogArea(parent);
    cnp.addResFilter(new IObjectFilter<Object>(){
      @Override
      public boolean filter(Object object) {
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        if(object instanceof IProject)
        {
          IProject p = (IProject) object;
          try {
            return p.isOpen() && p.hasNature(ReqCatalogue.ID)&&
            		et.getProjectLoadingState(p.getLocationURI())==ProjectLoadingState.LOADED
            		&&!p.hasNature(Consts.CATALOG_BROKEN_NATURE);
          } catch (CoreException e) {
            e.printStackTrace();
          }
        }
        return true;
      }
    });
    if(sel != null)
      nodeTree.setSelection(new StructuredSelection(sel));
    return c;
  }
}
