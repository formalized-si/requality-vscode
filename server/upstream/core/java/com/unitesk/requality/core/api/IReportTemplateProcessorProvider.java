package com.unitesk.requality.core.api;

import java.io.File;

import com.unitesk.requality.report.IReportTemplateProcessor;

public interface IReportTemplateProcessorProvider {
	public String getTemplateType();
	public IReportTemplateProcessor getOne(File file) throws Exception;
}
