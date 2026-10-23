package com.unitesk.requality.report.template;

import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.TreeDB;

public interface ParamDataProvider {
	public Object getDefaultValue(TreeDB db);
	public Object[] getPossibleValues(TreeDB db);
	public TemplateFileProcessor getSettingsLoader();
	public AttributeType getType();
	//TODO loading 
}
