package com.unitesk.requality.eclipse.views.properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.IFilter;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ProjectLoadingState;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.natures.ReqCatalogue;

public class RootNodeFilter implements IFilter {

  @Override
  public boolean select(Object toTest) {
    if (!(toTest instanceof IProject)) return false;
    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
    IProject p = (IProject) toTest;
    try {
      return p.hasNature(ReqCatalogue.ID)&&
        et.getProjectLoadingState(p.getLocationURI())==ProjectLoadingState.LOADED&&
        !p.hasNature(Consts.CATALOG_BROKEN_NATURE);
    } catch (CoreException e) {
    }
    return false;
  }
}
