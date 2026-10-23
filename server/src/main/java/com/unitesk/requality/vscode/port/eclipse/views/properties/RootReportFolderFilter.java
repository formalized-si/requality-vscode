package com.unitesk.requality.vscode.port.eclipse.views.properties;

import com.unitesk.requality.vscode.ui.jface.viewers.IFilter;
import com.unitesk.requality.nodetype.ReportFolder;

public class RootReportFolderFilter implements IFilter {

  @Override
  public boolean select(Object toTest) {
    if (!(toTest instanceof ReportFolder)) return false;
    ReportFolder rf = (ReportFolder) toTest;
    return !rf.getWorkParent().getType().equals(ReportFolder.TYPE_NAME);
  }
}
