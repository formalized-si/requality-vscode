package com.unitesk.requality.report.template;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.report.ReportUtils;

public interface IReportDataProvider {
  public Object getDataSource(TreeNode initialRoot, ReportUtils utils, ReportGenerationContext context);
  public void generationOver(TreeNode initialRoot, ReportUtils utils, ReportGenerationContext context);
  public void updateDataSource(Object dataSource, ReportUtils utils, ReportGenerationContext context, Requirement initialRootReq);
}
