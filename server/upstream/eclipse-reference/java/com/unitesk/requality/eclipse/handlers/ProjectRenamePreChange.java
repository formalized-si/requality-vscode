package com.unitesk.requality.eclipse.handlers;

import java.nio.file.Path;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
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
