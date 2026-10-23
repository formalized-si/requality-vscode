package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class VariableSetValue implements IVariableChange {
	private String id;
	private IValueProvider prov;
	public VariableSetValue(String name, IValueProvider prov) {
		this.id = name;
		this.prov = prov;
	}
	@Override
	public boolean apply(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		VariableType vt = prov.getType(man, node, context);
		Object res = prov.getValue(man, null, null);
		if(vt == null)
			vt = man.detectType(res);
 		if(vt == VariableType.Provider) {
			if(res instanceof Double)
				man.setValue(id, VariableType.Double, res);
			else {
				if(res instanceof Long)
					man.setValue(id, VariableType.Long, res);
				else if (res instanceof String)
					man.setValue(id, VariableType.String, res);
			}
		}else
			man.setValue(id, vt, res);
		return true;
	}

}
