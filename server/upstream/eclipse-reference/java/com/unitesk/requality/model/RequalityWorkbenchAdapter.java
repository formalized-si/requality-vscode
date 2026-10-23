package com.unitesk.requality.model;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.model.WorkbenchAdapter;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.ReportFolder;

final class RequalityWorkbenchAdapter extends WorkbenchAdapter {
  @Override
  public Object getParent(Object o) {
    TreeNode node = (TreeNode) o;
    return node.getShownParent();
  }

  @Override
  public String getLabel(Object o) {
    /* if (o instanceof Requirement) {
        Requirement node = (Requirement) o;
        return node.getNameOrId();
    }
    if (o instanceof ReportData) {
        ReportData node = (ReportData) o;
        return node.getId();
    }*/
//    if(o instanceof NotLoadedReqProject){
//      return o.toString();
//    }
    if (o instanceof IProject) {
      IProject p = (IProject) o;
      EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
      try {
		if(p.hasNature(Consts.CATALOG_NATURE)) {
		  if(p.hasNature(Consts.CATALOG_BROKEN_NATURE)||EclipseTracker.isBroken(p))
		    return super.getLabel(o)+" ("+Consts.BROKEN_POSTFIX+")";
		  else
		    if(et.getProjectLoadingState(p.getLocationURI())!=ProjectLoadingState.LOADED) 
		      return super.getLabel(o)+" ("+Consts.LOADING_POSTFIX+")";
		}
       } catch (CoreException e) {
       }
    }
    if (o instanceof TreeNode) {
      TreeNode node = (TreeNode) o;
      if (node.isRoot()) {
        return ResManager.getManager().getProjectName(node.getTreeDB());
        //IProject p = EclipseTreesTracker.getTracker().getProject(node.getTreeDB());
        //return p!=null?p.getName():node.toString();
      }
      return node.toString();
    }
    return super.getLabel(o);
  }

  @Override
  public ImageDescriptor getImageDescriptor(Object object) {
//    if (object instanceof Location) {
//      Location loc = (Location) object;
//      Requirement[] reqs = loc.getRequirements();баг
//      if (reqs != null && reqs.length == 0) { //null is loading
//        return PlatformUI.getWorkbench()
//            .getSharedImages()
//            .getImageDescriptor(ISharedImages.IMG_OBJS_ERROR_TSK);
//      }
//    }
    if (object instanceof TreeNode) {
      TreeNode node = (TreeNode) object;
      if (!node.isDisposed() && node.getQualifiedId().equals("/")) {
        return PlatformUI.getWorkbench()
            .getSharedImages()
            .getImageDescriptor(IDE.SharedImages.IMG_OBJ_PROJECT);
      }
      String type = node.getType();
      if (ReportFolder.TYPE_NAME.equals(type)
          && (ReportFolder.TYPE_NAME.equals(node.getWorkParent().getType())))
        type = DocFolder.TYPE_NAME;
      ImageDescriptor result =
          Activator.getDefault().getImageRegistry().getDescriptor("nodetype." + type);
      if (result == null) {
        return Activator.getDefault().getImageRegistry().getDescriptor("nodetype.default");
      }
      return result;
    }
    return super.getImageDescriptor(object);
  }

  @Override
  public Object[] getChildren(Object o) {
    TreeNode node = (TreeNode) o;
    return node.getSortedShownChildren().toArray();
  }
}
