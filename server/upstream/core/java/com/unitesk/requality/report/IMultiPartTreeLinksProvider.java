package com.unitesk.requality.report;

import java.util.Map;

import com.unitesk.requality.report.template.TemplateFile;

public interface IMultiPartTreeLinksProvider {
  public Map<String, String> getReplacedMultiPartTreeLinks(ReportUtils utils);
  public void setMultiPartLinksMapObject(TemplateFile tf, Map<TemplateFile, Map<String, String>> map);
}
