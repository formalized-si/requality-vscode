package com.unitesk.requality.core.model.variables;

import java.util.LinkedList;
import java.util.List;

public class ConditionBuilder {

	public static IVariableCondition parseCondition(String cond) {
		cond = StringParser.filterQuotes(cond);
		if(cond!=null) {
		  cond = cond.trim();
		  if(!cond.isEmpty()) {
            Object res = StringParser.parseCond(StringParser.opn(cond));
            if(res instanceof IVariableCondition)
              return (IVariableCondition) res;
            if(res == null)
              return IVariableCondition.TRUE;
            if(res instanceof Boolean)
              if((Boolean)res)
                return IVariableCondition.TRUE;
            return IVariableCondition.FALSE;
		  }
		}
		return IVariableCondition.TRUE;
	}
	public static IVariableChange[] parseChanges(String inp) {
		inp = StringParser.filterQuotes(inp);
		if(inp == null)
			return IVariableChange.NO_CHANGES;
		String[]vch = inp.split(",");
		List<IVariableChange> ret = null;
		for(String cond: vch) {
			if(cond!=null) {
			  cond = cond.trim();
			  if(!cond.isEmpty()) {
	        	if(cond.startsWith("V")) {
	        		int num = 2;
	        		while(Character.isDigit(cond.charAt(num)))
	                  num++;
	        		String numa = cond.substring(1, num);
	        		cond = cond.substring(cond.indexOf("=")+1, cond.length()).trim();
	        		IValueProvider change = StringParser.parseValueProvider(StringParser.opn(cond));
	        		if(change!=null) {
	        			if(ret == null)
	        				ret = new LinkedList<IVariableChange>();
	        			ret.add(new VariableSetValue(numa, change));
	        		}
	        	}
	        	else if (!cond.isEmpty())
	        		throw new RuntimeException("incorrect variable change");
	          }
			}
		}
		if(ret == null || ret.size() == 0)
			return IVariableChange.NO_CHANGES;
		return ret.toArray(new IVariableChange[0]);
	}
}
