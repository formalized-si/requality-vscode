package com.unitesk.requality.core.operation;

import java.util.Collection;
import java.util.Map;

import com.unitesk.requality.core.exception.WorkspaceOperationPerforming;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;

public interface IGenerateReportHandler {

  Object execute(boolean lockWorkspace, Collection<ReportSettings> nodes, Map<?, ?> par, IProgressMonitor monitor, boolean openAfter) throws Exception;

  ReportGenResult[] execute(boolean lockWorkspace, TreeDB db, IProgressMonitor monitor, boolean openAfter, ReportSettings ... reportS) throws WorkspaceOperationPerforming;

  boolean isEnabled(boolean lockWorkspace, Collection<ReportSettings> nodes);

  @Deprecated
  void clearOldImages(ReportSettings report);

  String getLastError();

  ReportData getLastReport();

  void openError(String string, String string2);

  void openInformation(String string, String string2);
  
  void openReport(ReportData rd);
}
  
