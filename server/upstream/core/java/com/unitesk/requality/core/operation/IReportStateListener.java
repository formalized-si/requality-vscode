package com.unitesk.requality.core.operation;

import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.report.ReportGenerationContext;

public interface IReportStateListener {
  public void reportGenerationEnds(ReportGenerationContext c, ReportData rd);
  public void reportGenerationProjectEnds(ReportGenerationContext c, ReportData rd);
}
