package com.unitesk.requality.core.operation;

import java.util.Collection;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;

public interface IReportExportOperation {

  void handleError(String string, Exception e);

  void setLogger(RLogger ls);

  void exportReport(Collection<TreeNode> nodes, String exportDir, boolean sepFolder);

}