package com.unitesk.requality.core.model.variables;

public class VariableDefinition{
  public ValueType variableType;
  public String variableName;
  public String nodeName = null;
  public static VariableDefinition fromString(String sTmp) {
    VariableDefinition vd = new VariableDefinition();
    vd.variableType = ValueType.CONSTANT_VARIABLE;
    if(sTmp.startsWith("target.")) {
      sTmp = sTmp.substring(7);
      vd.variableType = ValueType.TARGET_VARIABLE;
    }
    if(sTmp.startsWith("mulipartroot.")) {
      sTmp = sTmp.substring(13);
      vd.variableType = ValueType.MROOT_VARIABLE;
    }
    if(sTmp.startsWith("report.")) {
      sTmp = sTmp.substring(7);
      vd.variableType = ValueType.REPORT_VARIABLE;
    }
    if(sTmp.startsWith("const.")) {
      sTmp = sTmp.substring(6);
      vd.variableType = ValueType.CONSTANT_VARIABLE;
    }
    if(sTmp.startsWith("root.")) {
      sTmp = sTmp.substring(5);
      vd.variableType = ValueType.ROOT_VARIABLE;
    }
    if(sTmp.startsWith("node.")) {
      sTmp = sTmp.substring(5);
      int nextDot = sTmp.indexOf(".");
      if(nextDot>-1)
      {
        String nodeId = sTmp.substring(0, nextDot);
        nodeId = StringParser.filterQuotes(nodeId).replace("%20", " ");
        vd.nodeName = nodeId;
        sTmp = sTmp.substring(nextDot+1);
      }
      vd.variableType = ValueType.DEFINED_NODE_VARIABLE;
    }
    if((sTmp.startsWith("'") || sTmp.startsWith("\"")) && sTmp.length()>2) {
      sTmp = sTmp.substring(1, sTmp.length()-1).replace("%20", " ").replace("\\n", "<br/>");
      vd.variableType = ValueType.STRING_CONST;
    }
    vd.variableName = sTmp;
    return vd;
  }
}
