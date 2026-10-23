package com.unitesk.requality.report.template;

import com.unitesk.requality.core.attribute.AttributeType;

public class ParamTypeDefinition {
	public static final ParamTypeDefinition STRING = new ParamTypeDefinition(AttributeType.STRING);

	public AttributeType commonType;
	public ParamTypeDefinition commonListValueType;
	public String dataSourceName;
	
	public ParamTypeDefinition(AttributeType at) {
	  commonType = at;
	}
	public ParamTypeDefinition(AttributeType at, ParamTypeDefinition at2) {
	  commonType = at;
	  commonListValueType = at2;
	}
	public ParamTypeDefinition(String dt) {
      dataSourceName = dt;
	}
	@Override
	public String toString() {
		if(dataSourceName != null)
			return dataSourceName;
		if(commonListValueType!=null)
			return commonType+"["+commonListValueType.toString()+"]";
		return commonType.toString();
	}
}
