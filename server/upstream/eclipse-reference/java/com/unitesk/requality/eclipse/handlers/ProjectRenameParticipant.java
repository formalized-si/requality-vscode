package com.unitesk.requality.eclipse.handlers;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBLocalName;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.tool.CoreUtils;

public class ProjectRenameParticipant extends RenameParticipant {
  private IProject p = null;
  private Path cahceFolder = null;
  private TreeDB db = null;
  private String lname, pname;

  @Override
  protected boolean initialize(Object element) {
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    if(et.isDisabledListening(IResTracker.DISABLED_NEW_PROJECTS))
      return false;
    if (p == null) {
      p = null;
      try {
        if (element != null && element instanceof IProject
            && ((IProject) element).hasNature(EclipseTracker.REQ_CATALOG_NATURE)
            && !((IProject) element).hasNature(Consts.CATALOG_BROKEN_NATURE))// TODO test
        {
          ProjectLoadingState state = et.getProjectLoadingState(((IProject) element).getLocationURI());
          if (state == ProjectLoadingState.VERSION_UPDATE || state == ProjectLoadingState.LOADED) {
            p = (IProject) element;
            IPath rawPath = p.getRawLocation();
            lname = et.getLocalName(p);
            pname = et.getDBNamesManager().getProjectName(DBLocalName.formLocalNameKey(lname, null));
            {
              db = ResManager.getManager().getTreeDB(null, lname, pname, false, null);
              if(db!=null)
                cahceFolder = CoreUtils.getCacheDir(db);
            }
            // pname = et.getProjectName(p);
            return true;
          }
        }
        
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    } else
      return true;
    return false;
  }

  @Override
  public String getName() {
    return "Rename requality project";
  }

  @Override
  public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
      throws OperationCanceledException {
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    lname = et.getLocalName(p);
    try {
      db = et.getTreeDB(p, null, lname);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    String wop = CoreUtils.getWorkspaceOperation();
    synchronized (ResManager.RENAME_PROJECT_SYNC) {
      if (db != null && (ResManager.ignoreWorkspaceOperationInRename || wop == null || wop.isEmpty()))
        return RefactoringStatus.create(Status.OK_STATUS);
    }
    return RefactoringStatus.create(Status.CANCEL_STATUS);
  }

  @Override
  public Change createPreChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    Change prech = super.createPreChange(pm);
    Change ach = new ProjectRenamePreChange(p, cahceFolder, pname);
    if(prech == null)
      return ach;
    return new CompositeChange("Prepare before rename "+pname, 
       new Change[] {prech, ach});
  }

  @Override
  public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
    return new ProjectRenameChange(p, lname, pname, getArguments().getNewName());
  }

}
