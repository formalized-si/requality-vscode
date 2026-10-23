package com.unitesk.requality.core.api;

import java.util.Map;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.report.template.Template;
@ApiExtension
public interface IAPIReportSource{

  Map<String, Template> getReportTemplates(TreeDB db);

  Map<String, IParamValueProvider> getParamProviders();
}