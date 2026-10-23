package com.unitesk.requality.report;

public interface IReportGenerationParticipant {
  public void generationContextIsReady(ReportGenerationContext context);
  public void reportGenerationEnds(ReportGenerationContext context);
}
