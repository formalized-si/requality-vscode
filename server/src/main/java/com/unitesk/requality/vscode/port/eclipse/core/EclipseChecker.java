package com.unitesk.requality.vscode.port.eclipse.core;

import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.Activator;

public class EclipseChecker extends IncrementalProjectBuilder {
  public static final String BUILDER_ID = "com.unitesk.requality.checkers";

//  private IResourceDeltaVisitor visitor =
//      new IResourceDeltaVisitor() {
//
//        @Override
//        public boolean visit(IResourceDelta delta) throws CoreException {
//          if (delta.getKind() == IResourceDelta.REMOVED) {
//            return false;
//          }
//          if (delta.getResource().getType() != IResource.FILE) {
//            return true;
//          }
//          TreeNode node = EclipseTracker.getNodeByResource((IFile) delta.getResource(),true);
//          if(node == null || !node.getTreeDB().isLoaded())
//            return false;
//          if (!node.isDisposed()) {
//            node.getTreeDB().getCheckerContainer().check(node, false);
//          }
//          return false;
//        }
//      };
  boolean deep = false;

  public EclipseChecker() {
    this(false);
  }

  public EclipseChecker(boolean d) {
    deep = d;
  }

  @Override
  protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
      throws CoreException {
    if(!Activator.isActive())
      return null;
    try{
      if (kind == FULL_BUILD) {
        IProject proj = getProject();
        if (proj == null) return null;
        proj.refreshLocal(IResource.DEPTH_INFINITE, null);
        String name = ResManager.getManager().getTracker().getLocalName(proj);
        TreeDB db = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getTreeDB(proj, null, name); 
        if(db == null || !TreeStateManager.getOne(db.getTracker()).isFullReady(name,null))
          return null;
        EclipseTracker.getTracker(DBNamesManager.DEFAULT).fireDBSettingsChange(Moment.BEFORE,db);
        db.getCheckerContainer().addAllLoadedToQ();
        EclipseTracker.getTracker(DBNamesManager.DEFAULT).fireDBSettingsChange(Moment.AFTER,db);
        return new IProject[]{proj};
      } else {
  //      IProject proj = getProject();
  //      if (proj == null) return null;
  //      if (getDelta(getProject()) != null) {
  //        getDelta(getProject()).accept(visitor, false);
  //      }
      }
    }catch(Exception r){
      RLogger.logError(r);
    }
    return null;
  }

}
