package com.unitesk.requality.eclipse.views.properties;

import org.eclipse.jface.viewers.IFilter;
import com.unitesk.requality.nodetype.ReportFolder;

public class ReportFolderMainFilter implements IFilter {

  @Override
  public boolean select(Object toTest) {
    if (!(toTest instanceof ReportFolder)) return false;
    ReportFolder rf = (ReportFolder) toTest;
    return rf.getWorkParent().getType().equals(ReportFolder.TYPE_NAME);
  }
}
