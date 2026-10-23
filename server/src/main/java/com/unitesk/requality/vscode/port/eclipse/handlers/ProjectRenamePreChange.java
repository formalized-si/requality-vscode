package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.nio.file.Path;

import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.RefactoringStatus;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.CoreUtils;

public class ProjectRenamePreChange extends Change {
  private String name="";
  private Path cachePath;
  private IProject p = null;
  public ProjectRenamePreChange(IProject p, java.nio.file.Path cachePath, String name) {
    this.p = p;
    this.name = name;
    this.cachePath = cachePath;
  }

  @Override
  public String getName() {
    return "Prepare before rename of "+name;
  }
  @Override
  public void initializeValidationData(IProgressMonitor pm) {
  }
  @Override
  public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return RefactoringStatus.create(Status.OK_STATUS);
  }

  @Override
  public Change perform(IProgressMonitor pm) throws CoreException {
    try {
      IFolder f = p.getFolder(CoreUtils.CACHE_FOLDER_NAME);
      if(f!=null && f.exists())
        f.refreshLocal(IResource.DEPTH_INFINITE, pm);

      IProject proj = p;
//      proj.touch(null);s
      IWorkspace works=ResourcesPlugin.getWorkspace();
      proj.touch(pm);
      if(works!=null)
          works.checkpoint(false);
      proj.refreshLocal(IProject.DEPTH_INFINITE, pm);
      EclipseTracker.getTracker(DBNamesManager.DEFAULT).addIsClosing(proj);//to avoid incorrect tree state on close
//      CacheStorage.closeRecConnection(cachePath.toFile());//close all openned connections
      CoreUtils.delete(cachePath.toFile(), true, true);
//      p.touch(pm);//force residual changes to be performed
    } catch (CoreException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return new ProjectRenamePreChange(p, cachePath, name);
  }

  @Override
  public Object getModifiedElement() {
    //TODO test
    return p;
  }

}
