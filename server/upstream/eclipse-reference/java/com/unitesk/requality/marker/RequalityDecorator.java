package com.unitesk.requality.marker;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;

import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseCheckerContainer;
import com.unitesk.requality.eclipse.core.EclipseTracker;

public class RequalityDecorator implements ILightweightLabelDecorator {
  public Set<String> getRelatedKeys(){
    return null;
  }
  private static Set<RequalityDecorator> desc = ConcurrentHashMap.newKeySet();
  public static Set<RequalityDecorator> getAll() {
    synchronized (desc){
      return desc;
    }
  }
  public RequalityDecorator() {
    synchronized (desc){
      desc.add(this);
    }
  }
  @Override
  public void addListener(ILabelProviderListener listener) {}

  @Override
  public void dispose() {}
  @Override
  public void decorate(Object element, IDecoration decoration) {
    if (!TreeNode.class.isInstance(element)) {
      return;
    }
    TreeNode node = (TreeNode) element;
    if(node==null)
      return;
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
      return;
    if(node.isDisposed())
        return ;
    IMarker[] markers = null;
    boolean hasmarkers = false;
    try {
      IResource res = EclipseTracker.getMarkerResourceByNode(node, LockType.TRY_LOCK);
      if (res == null || !res.exists()) {
        return ;
      }
      //IMark[] probs = node.getTreeDB().getCheckerProblems(node);
      //if(probs!=null && probs.length>0)
      //EclipseCheckerContainer.applyProblemsInfo(res, probs);
      markers = res.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
      boolean hasp = markers!=null && markers.length>0;
      markers = res.findMarkers(EclipseCheckerContainer.REQUALITY_MARKER_WARN_TYPE, true, IResource.DEPTH_ZERO);
      hasp = hasp||markers!=null && markers.length>0;
      markers = res.findMarkers(EclipseCheckerContainer.REQUALITY_MARKER_TYPE, true, IResource.DEPTH_ZERO);
      hasp = hasp||markers!=null && markers.length>0;
      IMark[] probs = node.getCheckerProblems();
      hasmarkers = probs.length>0 || hasp;
      //            if (markers.length == 0) {
      //                IFolder chFolder = res.getParent().getFolder(
      //                        Path.fromOSString(node.getId()));
      //                if (chFolder.exists()) {
      //                    markers = chFolder.findMarkers(IMarker.PROBLEM, true,
      //                            IResource.DEPTH_INFINITE);
      //                }
      //            }
      if(!hasmarkers)
        return ;
      EclipseAPI.getDisplay().syncExec(new Runnable(){
        @Override
        public void run() {
          ImageDescriptor id =
              Activator.getImageDescriptor("icons/req_error.gif");
          decoration.addOverlay(id);
        }  
      });
    } catch (LockIsBusy lib ){
      RLogger.logDebug("Resource lock was busy while decorating "+node.getUUId(),7);
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  @Override
  public void removeListener(ILabelProviderListener listener) {}

  @Override
  public boolean isLabelProperty(Object element, String property) {
    return false;
  }
  
}
