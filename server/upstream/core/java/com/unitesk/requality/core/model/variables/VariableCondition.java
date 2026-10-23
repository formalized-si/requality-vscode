package com.unitesk.requality.core.model.variables;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.ReportGenerationContext;

public class VariableCondition implements IVariableCondition {
	public VariableDefinition vd;
	public CompareType c;
	
	public Double targetConst;
	public String targetString;
	public IValueProvider target;
	private static double EPS=0.01;
	public boolean check (VariablesManager man, TreeNode node, ReportGenerationContext context) {
		Object cha = getCheckedVal(man, node, context);
		Object vla = man.getValueOrDefault(vd, node, context, 0d);
		double vl = 0d;
		if(vla instanceof Long)
			vl = (Long)vla;
		else
			if(vla instanceof Double)
				vl = (double) vla;
		double ch = 0d;
		if(cha instanceof Long)
			ch = (Long)cha;
		else
			if(targetString==null)
			  ch = (double) cha;
		switch (c) {
			case EQUAL:
				if(targetString!=null)
					return targetString.equals(vla);
				return Math.abs((double)ch-(double)vl)<EPS;
			case LESS:
				return (double)vl < (double)ch;
			case MORE:
				return (double)vl > (double)ch;
			case LESS_EQUAL:
				return (double)vl <= (double)ch;
			case MORE_EQUAL:
				return (double)vl >= (double)ch;
			case NOT_EQUAL:
				if(targetString!=null)
					return !targetString.equals(vla);
				return ch != vl;
		}
		return false;
	}
	public Object getCheckedVal(VariablesManager man, TreeNode node, ReportGenerationContext context) {
		if (targetConst == null && targetString == null)
			return target.getValue(man, node, context);
		if(targetString!=null)
			return targetString;
		return targetConst;
	}
	public void detectCondition(char ch) {
		switch (ch) {
			case '=':
				c = CompareType.EQUAL;
				break;
			case '<':
				c = CompareType.LESS;
				break;
			case '>':
				c = CompareType.MORE;
				break;
		}
	}
}
