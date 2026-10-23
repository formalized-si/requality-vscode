package com.unitesk.requality.core;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.report.ReportUtils;

public interface IMultiPartDataProvider {
  public MultiPart getMultiPart(TreeNode root, ReportUtils utils, ReportGenerationContext context);
}
