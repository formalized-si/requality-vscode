package com.unitesk.requality.report;

import java.io.OutputStream;
import java.util.Map;

import com.unitesk.requality.core.model.TreeNode;

public interface IReportTemplateProcessor {

  void process(Map<String, Object> reportSetts, TreeNode root, ReportGenerationContext context, Map<String, Object> data, OutputStream out) throws Exception;


}
