package com.unitesk.requality.core.model.variables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.changelog.ChangeLogRecord;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.ReportGenerationContext;

public class VariablesManager {
  private TreeNode target;
  private ReportGenerationContext context;
  //private IValueChangeReaction vcr;
  public Map<String, IValueProvider> variableValueProviders = new HashMap<>();
  public Map<String, VariableType> varType = new HashMap<>();
  public Map<String, Object> constValues = new HashMap<>();

  public void configure(TreeNode node, ReportGenerationContext context2) {
    this.target = node;
    this.context = context2;
  }
  public VariablesManager(TreeNode target, ReportGenerationContext context) {
    this.target = target;
    this.context = context;
    //this.vcr = vcr;
  }
  private void valueChanged(Long l) {
   // if(vcr!=null)
      //vcr.valueChanged(user, this, l);
  }

  public void setValue(String varName, VariableType tp, Object val) {
    varType.put(varName, tp);
    switch(tp) {
      case Provider:
        variableValueProviders.put(varName, (IValueProvider)val);
        break;
      default:
        constValues.put(varName, val);
        break;
    }
    //valueChanged(l);
  }
  public void setValue(String varName, IValueProvider val) {
    setValue(varName, VariableType.Provider, val);
    //valueChanged(l);
  }

  public void setValue(String varName, double val) {
    setValue(varName, VariableType.Double, val);
    //valueChanged(l);
  }
  public void setValue(String varName, String val) {
    setValue(varName, VariableType.String, val);
  }
  
  public void setValue(String varName, long val) {
    setValue(varName, VariableType.Long, val);
    //valueChanged(l);
  }
  public void setValue(String varName, int val) {
    setValue(varName, VariableType.Long, val);
    //valueChanged(l);
  }
  public void setValue(String varName, boolean val) {
    setValue(varName, VariableType.Boolean, val);
    //valueChanged(l);
  }

  public Object getValue(VariableDefinition vd, TreeNode target, ReportGenerationContext context) {
    if (vd == null)
      return null;
    switch(vd.variableType) {
      case CONST:
      case STRING_CONST:
        IValueProvider prov = variableValueProviders.get(vd.variableName);
        if(prov!=null)
          return prov.getValue(this, target, context);
        VariableType t = varType.get(vd.variableName);
        if(t == null)
          return null;//TODO detect?
        Object ret = constValues.get(vd.variableName);
        if(t == VariableType.String)
          return replaceConsts((String)ret);
        return ret; 
      case DEFINED_NODE_VARIABLE:{
        TreeNode nd = vd.nodeName == null? target: 
        	getDefinedNode(getTreeDB(target)
    				, this.target!=null?target:target
    				,vd.nodeName);
        if(nd!=null)
          return getNodeAttributeValue(nd, target, vd.variableName);  
        return "NODE_NOT_FOUND("+vd.nodeName+")";
      }
      case MROOT_VARIABLE:{
        TreeNode mroot = context.detectRoot(target);
        if(mroot == null)
          return null;
        return getNodeAttributeValue(mroot, target, vd.variableName);  
      }
      case REPORT_VARIABLE:{
        ReportData rdata = context == null? null : context.getReportData();
        if(rdata == null)
          return null;
        return getNodeAttributeValue(rdata, target, vd.variableName);  
      }
      case CONSTANT_VARIABLE:{
        return context.getConstantStringValue(vd.variableName, target);
      }
      case ROOT_VARIABLE:{
        if(target == null)
          return null;
        return getNodeAttributeValue(target.getTreeDB().getNode(Requirement.getTypeRootQId()), target, vd.variableName);
      }
      case TARGET_VARIABLE:{
        if(target == null)
          return null;
        return getNodeAttributeValue(target, target, vd.variableName);
      }
    }
    return null;
  }

  private TreeNode getDefinedNode(TreeDB treeDB, TreeNode targ, String name) {
    if(name.equals(TreeNode.ATTR_PARENT))
      return targ.getShownParent();
	return treeDB.getNode(name);
  }
  private VariableType getNodeAttributeType(TreeNode node, TreeNode target, String key) {
    if(key.startsWith("%changelog")) {
      List<ChangeLogRecord> changelog = node.getTreeDB().getChangeLog(node);
      key = key.substring(10);
      if(key.startsWith(".now")) {
         key = key.substring(4);
         ChangeLogRecord clr = changelog.get(changelog.size()-1);
         return VariableType.String;//TODO?
      }else {
        if(key.startsWith(".first")) {
          key = key.substring(4);
          ChangeLogRecord clr = changelog.get(0);
          return VariableType.String;//TODO?
        }
      }
    }
    key = adoptAttrName(node, key);
    switch(key) {
      case "project_name":
          return VariableType.String;
      case "project_proj_name":
          return VariableType.String;
      case "uuid":
          return VariableType.String;
      default:{
        Attribute a = node.attrs().getAttribute(key);
        if(a == null)
          return VariableType.String;
        return toVariableType(a.getType());
      }
    }
  }
  private VariableType toVariableType(AttributeType type) {
    switch(type) {
      case BOOL: 
        return VariableType.Boolean;
      case INT:
        return VariableType.Long;
      case FLOAT:
        return VariableType.Double;
      default: 
        return VariableType.String;
    }
  }
  private Object getNodeAttributeValue(TreeNode node, TreeNode target, String key) {
    if(key.startsWith("%changelog")) {
      List<ChangeLogRecord> changelog = node.getTreeDB().getChangeLog(node);
      key = key.substring(10);
      if(key.startsWith(".now")) {
         key = key.substring(4);
         ChangeLogRecord clr = changelog.get(changelog.size()-1);
         return getChangeLogInfo(clr, key);
      }else {
        if(key.startsWith(".first")) {
          key = key.substring(4);
          ChangeLogRecord clr = changelog.get(0);
          return getChangeLogInfo(clr, key);
        }
      }
    }
    key = adoptAttrName(node, key);
    switch(key) {
      case "project_name":
        return node.getTreeDB().getLocalName();
      case "project_proj_name":
		return node.getTreeDB().getProjectName();
      case "uuid":
        return node.getSUUId();
      default:{
        Attribute a = node.attrs().getAttribute(key);
        if(a == null)
          return null;
        VariableType vt = toVariableType(a.getType());
        switch (vt) {
          case Double:
            return (Double)a.getGValue();
          case Boolean:
            return (Boolean)a.getGValue();
          case Long:
            return (Long)a.getGValue();
          default:
            return node.attrs().getStringAttributeValue(key);
        }
      }
    }
  }
  private String getChangeLogInfo(ChangeLogRecord clr, String key) {
    if(key.isEmpty())
      return clr.toString();
    if(key.startsWith("."))
      key = key.substring(1);
	switch(key) {
      case "version":
	    return clr.version;
      case "message":
  	    return clr.message;
      case "authorEmail":
	    return clr.authorEmail;
      case "authorName":
	    return clr.authorName;
      case "date":
	    return clr.date;
    }
    return null;
  }
  private String adoptAttrName(TreeNode target2, String var) {
    String altName = SystemNames.getOne(target2.getTreeDB()).getSysName(var);
    if(altName!=null)
      return altName;
	return var;
  }
  public String replaceConsts(String val) {
    if(val == null)
      return null;
    String strval = val;
      StringBuilder sb = new StringBuilder();
      //TODO use BracesHanlder
      int o = strval.indexOf("{"), e;
      if(o==-1)
        return val;
      int last = 0;
      while (o > -1) {
        e = strval.indexOf("}", o);
        if (e > -1) {
          String name = strval.substring(o + 1, e);
          String repl = null;
          String vname = null;
          try {
            if(name.length()>1)
              vname = name.substring(1);
          }catch(NumberFormatException nfe) {
          }
          if(vname!=null) {
            if(name.startsWith("V")) {
              //TODO change?
              VariableDefinition vd = VariableDefinition.fromString(vname);
              repl = asString(getValue(vd, target, context));
            }
          }
          if(repl!=null)
          {
            sb.append(strval.substring(last,o));
            sb.append(repl);
          }else
            sb.append(strval.substring(last,e+1));
          last = e+1;
        } else e = o;
        o = strval.indexOf("{", e+1);
    }
      sb.append(strval.substring(last,strval.length()));
      return sb.toString();
    
  }
  private String asString(Object value) {
    if(value == null)
      return null;
    return value.toString();
  }
  public Double getDoubleValue(VariableDefinition vd, TreeNode node, ReportGenerationContext context) {
    Object ret = null;
    ret = getValue(vd, node, context);
    if(ret instanceof Long || ret instanceof Integer)
      return (Long) ret+0d;
    if(ret instanceof Double)
      return (Double) getValue(vd, node, context);
    return Double.parseDouble(ret.toString());
  }
  public Double getNumValue(VariableDefinition vd, TreeNode node, ReportGenerationContext context) {
    Object ret = null;
    ret = getValue(vd, node, context);
    if(ret instanceof Long || ret instanceof Integer)
      return (Long) ret+0d;
    if(ret instanceof Double)
      return (Double) getValue(vd, node, context);
    try {
      return Double.parseDouble((String) ret.toString());
    }catch(NumberFormatException nfe) {
    }
    return 0d;
  }
  public Object getValueOrDefault(VariableDefinition vd, TreeNode node, ReportGenerationContext context, Object def) {
    Object v = getValue(vd, node, context);
    return v == null ? def : v;
  }
  public static Double toNumerical(Object o, VariableType vt) {
    if(o == null)
      return null;
    switch(vt) {
      case Long:{
        return ((Long)o)+0d;
      }
      case Double:{
        return (Double)o;
      }
      case String:{
        try {
          return Double.parseDouble((String)o);
        }catch(NumberFormatException nfe) {
        }
      }
    }
    return null;
  }
  public Double getNumberValueOrDefault(VariableDefinition vd, TreeNode node, ReportGenerationContext context, Double def) {
    Object o = getValue(vd, node, context);
    if(o == null)
      return null;
    
    if(o instanceof Double)
      return (Double) o;
    if(o instanceof Long)
      return (Long) o+0d;
    if(o instanceof String)
      try {
        return Double.parseDouble((String)o);
      }catch(NumberFormatException nfe) {
      }
    return def;
  }
  public TreeDB getTreeDB(TreeNode alt) {
    if(target != null)
      return target.getTreeDB();
    if(alt!=null)
      return alt.getTreeDB();
    return null;
  }
  public VariableType getType(VariableDefinition vd, TreeNode node, ReportGenerationContext context) {
    switch(vd.variableType) {
      case CONST:
      case STRING_CONST:
        IValueProvider prov = variableValueProviders.get(vd.variableName);
        if(prov!=null)
          return prov.getType(this, target, context);
        VariableType t = varType.get(vd.variableName);
        if(t == null)
          return VariableType.String;//TODO detect?
        return t; 
      case DEFINED_NODE_VARIABLE:{
        TreeNode nd = vd.nodeName == null ? node:
        		getDefinedNode(getTreeDB(node)
        				, target!=null?target:node
        				,vd.nodeName);
        //if(nd!=null)
        return getNodeAttributeType(nd, target, vd.variableName);
        //return varType.get(vd.variableName);//TODO right? 
        
      }
      case MROOT_VARIABLE:{
        TreeNode mroot = context.detectRoot(target);
        if(mroot == null)
          return null;
        return getNodeAttributeType(mroot, target, vd.variableName);  
      }
      case REPORT_VARIABLE:{
        ReportData rdata = context == null? null : context.getReportData();
        if(rdata == null)
          return null;
        return getNodeAttributeType(rdata, target, vd.variableName);  
      }
      case CONSTANT_VARIABLE:{
        return VariableType.String;
      }
      case ROOT_VARIABLE:{
        if(target == null)
          return null;
        return getNodeAttributeType(target.getTreeDB().getNode(Requirement.getTypeRootQId()), target, vd.variableName);
      }
      case TARGET_VARIABLE:{
        if(target == null)
          return null;
        return getNodeAttributeType(target, target, vd.variableName);
      }
    }
    return varType.get(vd.variableName);
  }
//
//  public void removeValue(long item) {
//    varType.remove(item);
//    constValues.remove(item);
//    variableValueProviders.remove(item);
//    //valueChanged(item);
//  }
  public VariableType detectType(Object res) {
    if(res instanceof Double)
      return VariableType.Double;
    if(res instanceof Long)
      return VariableType.Long;
    if(res instanceof String)
      return VariableType.String;
    if(res instanceof IValueProvider)//DUNNO
      return VariableType.Provider;
    return null;
  }
}
