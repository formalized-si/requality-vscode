package com.unitesk.requality.core.operation;

import java.net.URI;
import java.util.UUID;

public class ReportGenResult<T> {
  /** */
  public ReportGenResult(URI href2, UUID reportParent, T nodeDesc, URI savePath2) {
    href = href2;
    report = nodeDesc;
    savePath = savePath2;
    this.reportParent = reportParent;
  }
  public UUID reportParent;
  public T report;
  public URI savePath;
  public URI href;
}
