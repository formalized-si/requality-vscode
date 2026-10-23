package com.unitesk.requality.eclipse.ui.dialogs;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.filter.IObjectFilter;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;
import com.unitesk.requality.eclipse.views.RequalityContentProvider;

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
