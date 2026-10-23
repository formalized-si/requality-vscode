package com.unitesk.requality.marker;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelDecorator;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.CoreUtils;

public class RepoDecorator extends RequalityDecorator implements ILabelDecorator {
  private static Set<String> KEYS;
  static {
    KEYS = new HashSet<>();
    KEYS.add(TreeNode.ATTR_REPO_STATE);
  }
  @Override
  public void decorate(Object telement, final IDecoration decoration) {
    if (TreeNode.class.isInstance(telement)) {
      TreeNode node = (TreeNode)telement;
      TreeStateManager tsm;
      if(node == null) {
        tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
        return;
      }
      try{
        TreeDB db = node.getTreeDB();
        tsm = db.getTracker().getTreeStateManager();
        if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)) 
        	|| db.getCacheStorage().isDisconnected())
          return;//TODO redecorate or wait
        try{
          if(node.attrs().getBooleanAttributeValue(TreeNode.ATTR_REPO_STATE, false)){
            EclipseAPI.getDisplay()
            .asyncExec(new Runnable() {
            
              @Override
              public void run() {
                decoration.addOverlay(Activator.getImageDescriptor("icons/req_unconnected.png"),
                    IDecoration.BOTTOM_RIGHT);
              }
            });
          }
        }catch(Exception e){
          RLogger.logError(e);
        }
      }
      finally{
      }
    }else{
      if(CoreUtils.DEBUG_LEVEL>5&&telement instanceof IProject){
        IProject p = (IProject) telement;
        TreeDB db = null;
        EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
        String name = et.getLocalName(p);
        TreeStateManager tsm = et.getTreeStateManager();
        try {
          db = et.getTreeDB(p, null, name);
        } catch (IOException e) {
          RLogger.logError(e);
        }
        if(db!=null)
        {
          if(!tsm.isFullReady(name, tsm.getRevisionInfo(db)))
            decoration.setBackgroundColor(new Color(EclipseAPI.getDisplay(),100,100,100));
        }
      }
    }
  }

  @Override
  public boolean isLabelProperty(Object element, String property) {
    return false;
  }

  @Override
  public void addListener(ILabelProviderListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeListener(ILabelProviderListener listener) {
    // TODO Auto-generated method stub

  }

  @Override
  public Image decorateImage(Image arg0, Object arg1) {
    return null;
  }

  @Override
  public Set<String> getRelatedKeys() {
    return KEYS;
  }

  @Override
  public String decorateText(String text, Object element) {
    return text;
  }
}