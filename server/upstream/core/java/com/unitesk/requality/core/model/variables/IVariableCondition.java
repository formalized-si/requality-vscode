package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public interface IVariableCondition {
	public IVariableCondition FALSE = new IVariableCondition() {
		@Override
		public boolean check(VariablesManager man, TreeNode node, ReportGenerationContext context) {
			return false;
		}
	};
	public IVariableCondition TRUE = new IVariableCondition() {
		
		@Override
		public boolean check(VariablesManager man, TreeNode node, ReportGenerationContext context) {
			return true;
		}
	};
	public boolean check (VariablesManager man, TreeNode node, ReportGenerationContext context);
}