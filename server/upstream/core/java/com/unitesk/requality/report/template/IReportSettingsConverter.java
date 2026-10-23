package com.unitesk.requality.report.template;

import java.util.Map;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.report.ReportGenerationContext;

public interface IReportSettingsConverter {
  Map<String, Object> getUpdatedSettings(ReportGenerationContext context,Map<String, Object> allData, TreeDB baseDB, TreeDB genWhere);
  Map<String, AttributeDefinition> getUpdatedDefinitionSettings(ReportGenerationContext context,Map<String, AttributeDefinition> allData, TreeDB baseDB, TreeDB genWhere);
}
