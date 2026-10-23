package com.unitesk.requality.core.model;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.variables.IValueProvider;
import com.unitesk.requality.core.model.variables.StringParser;
import com.unitesk.requality.core.model.variables.VariablesManager;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.ReportGenerationContext;

public class IDMechanics {
//  private static ConcurrentHashMap<TreeDB, String> idPrefixCache = new ConcurrentHashMap<TreeDB, String>();
  public final static String ATTR_PROJECT_ID_PREFIX="_projectIdPrefix";
  public final static String ATTR_REPORT_NODE_ID_MODE="_reportNodeIdMode";
  public final static String ATTR_REPORT_NODE_ID_PATTERN="reportNodeIdPattern";
  public static final String DEFAULT_CUSTOM_REPORT_ID_PATTERN = "{(target."+SystemNames.SEQUENTIAL_ID_REF_NAME+")?(PROJECT_ID_PREFIX+target."+SystemNames.SEQUENTIAL_ID_REF_NAME+"):target.uuid}";
  public static String getStoredIdPrefix(TreeDB db) {
    if(db == null)
      return null;
    return db.getRootNode().attrs().getStringAttributeValue(ATTR_PROJECT_ID_PREFIX);
  }
  private static String META_SHOWN_ID_PREFIX="SHOWN_ID_PREFIX_CACHE";
  public static String getShownIdPrefix(TreeDB db) {
    if(db == null)
      return null;
    String idPref = db.getMetaObject(META_SHOWN_ID_PREFIX);
    if(idPref == null) 
      synchronized (META_SHOWN_ID_PREFIX) {
        idPref = db.getMetaObject(META_SHOWN_ID_PREFIX);
        if(idPref!=null)
          return idPref;
        String st = getStoredIdPrefix(db);
        if(st!=null) 
          idPref = st;
        else
          if(db == null)
            idPref = null;
          else
            idPref = getDefaultProjectPrefix(db);
        db.registerMetaObject(META_SHOWN_ID_PREFIX, idPref);
      }
    db.getRootNode().addChangeListener(getIdPrefixChangeListener(db.getRootNode(), db));
    return idPref;
  }
  private static INodeChangeListener getIdPrefixChangeListener(TreeNode node, TreeDB db) {
    return new INodeChangeListener() {
      @Override
      public void moved(TreeNode node, TreeNode oldParent) {
        // TODO Auto-generated method stub
        
      }
      
      @Override
      public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
        // TODO Auto-generated method stub
        
      }
      
      @Override
      public void deleted(TreeNode source) {
        // TODO Auto-generated method stub
        
      }
      
      @Override
      public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
        if(what.contains(ATTR_PROJECT_ID_PREFIX)) {
          node.removeChangeListener(this);
          db.registerMetaObject(META_SHOWN_ID_PREFIX, null);
        }
      }
      
      @Override
      public void attributeChange(TreeNode source, String what, Attribute old) {
        if(what.equals(ATTR_PROJECT_ID_PREFIX))
        {
          node.removeChangeListener(this);
          db.registerMetaObject(META_SHOWN_ID_PREFIX, null);
        }
      }
    };
  }
  private static String getDefaultProjectPrefix(TreeDB db) {
    String name = db.getProjectName().toUpperCase();  
    if(name.length()>0 && Character.isDigit(name.charAt(name.length()-1)))
      return name+"-";
    return name;
  }
  public static void changeIdPrefix(TreeDB db, String idPrefix, boolean saveAttrs) {
    TreeNode node = db.getRootNode();
    if(idPrefix == null || idPrefix.isEmpty() || idPrefix.toUpperCase().equals(getDefaultProjectPrefix(db).toUpperCase()))
      node.attrs().removeAttribute(ATTR_PROJECT_ID_PREFIX);
    else
      node.attrs().setConstAttribute(ATTR_PROJECT_ID_PREFIX, idPrefix, Flag.HIDDEN);
    if(saveAttrs)
      node.commitAttributes();
  }
  public static String getNodeId(TreeNode node) {
    TreeDB db = node.getTreeDB();
    switch (db.getFullNameMode()){
      default:
      case 0:
        return Requirement.getFullName(node, node.getTreeDB(), false);
      case 1:
      return node.getUserVisibleName(false);
      case 2:
        return node.getQualifiedId();
      case 3:
        String id= node.getExternalForeignID();
        if(id==null)
          id = "0";
        return id;
    }
  }
  public static ReportNodeIdIdentificationMode getReportNodeIdentificationMode(TreeDB db) {
    String attrMode = db.getRootNode().attrs().getStringAttributeValue(ATTR_REPORT_NODE_ID_MODE);
    if(attrMode!=null)
      return ReportNodeIdIdentificationMode.valueOf(attrMode); 
    return ReportNodeIdIdentificationMode.BY_SEQ_ID;
  }
  public static String getNodeReportIdPattern(TreeDB db, ReportGenerationContext context) {//TODO context?
    String pattern = (context == null || context.getReportData() == null) ? null : context.getReportData().attrs().getStringAttributeValue(ATTR_REPORT_NODE_ID_PATTERN);
    if(pattern == null)
      pattern = db.getRootNode().attrs().getStringAttributeValue(ATTR_REPORT_NODE_ID_PATTERN);
    if(pattern == null && context != null) {
      pattern = context.template.getNodeIdPattern();
    }
    if(pattern == null)
      pattern = DEFAULT_CUSTOM_REPORT_ID_PATTERN;
    return pattern;
  }
  public static void changeReportPattern(TreeDB db, String idPrefix, boolean saveAttrs) {
    TreeNode node = db.getRootNode();
    if(idPrefix == null || idPrefix.isEmpty() || idPrefix.toUpperCase().equals(getDefaultProjectPrefix(db).toUpperCase()))
      node.attrs().removeAttribute(ATTR_REPORT_NODE_ID_PATTERN);
    else
      node.attrs().setConstAttribute(ATTR_REPORT_NODE_ID_PATTERN, idPrefix, Flag.HIDDEN);
    if(saveAttrs)
      node.commitAttributes();
  }
  public static void changeNodeReportIdMode(TreeDB db, ReportNodeIdIdentificationMode mode, boolean saveAttrs) {
    TreeNode node = db.getRootNode();
    if(mode == null || mode == ReportNodeIdIdentificationMode.BY_SEQ_ID)
      node.attrs().removeAttribute(ATTR_REPORT_NODE_ID_MODE);
    else
      node.attrs().setConstAttribute(ATTR_REPORT_NODE_ID_MODE, mode.name(), Flag.HIDDEN);
    if(saveAttrs)
      node.commitAttributes();
  }
  public static String getNodeIdForReport(TreeNode target, ReportGenerationContext context) {
    TreeDB settsSource = target.getTreeDB();
    ReportNodeIdIdentificationMode rnim = getReportNodeIdentificationMode(settsSource);//or from context?
    if(rnim == ReportNodeIdIdentificationMode.BY_FULL_NAME)
      return getNodeId(target);
    if(rnim == ReportNodeIdIdentificationMode.BY_SEQ_ID)
      return target.getExternalForeignID();//TODO only external?
    String template = getNodeReportIdPattern(settsSource, context);
    return replaceConsts(template, target, context);
  }
  private static String filterQuotes(String str) {
    str = str.trim();
    if(str.startsWith("'") && str.endsWith("'") || 
       str.startsWith("\"") && str.endsWith("\"") ||
       str.startsWith("„") && str.endsWith("“")||
       str.startsWith("«") && str.endsWith("»"))
      return str.substring(1, str.length()-1);
    return str;
  }
  private static VariablesManager man = new VariablesManager(null,null);
  private static Map<String, IValueProvider> compiledProviders = new HashMap<>();
  private static String getRepl(String val, TreeNode node, ReportGenerationContext context) {
    IValueProvider vc = compiledProviders.get(val); 
    if(vc == null) {
      //get poland notation with replaced quotes on pattern, also replaces spaces and () in quotes 
      val = StringParser.reworkQuoteChars(filterQuotes(val));
      String pattern = StringParser.opn(val); 
      vc = (IValueProvider) StringParser.parseValueProvider(pattern);
      compiledProviders.put(val, vc);
    }
	if(vc!=null) {
      return (String) vc.getValue(man, node, context);
    }
    return null;
  }
  private static String replaceConsts(String val, TreeNode node, ReportGenerationContext context) {
    man.configure(node, context);//TODO different mans?
    int vars = val.indexOf("{");
    int last = 0, pos = 0;
    String ret = "";
    boolean han = false;
    while(vars>-1){
      int varsEnd = val.indexOf("}", vars+1);
      if(varsEnd>-1){
        han = true;//( are not handled for now :(
        TreeNode target = node;
        String newPart = val.substring(last, vars);//before var
        String vnameb = val.substring(vars+1, varsEnd);
        String repl = getRepl(vnameb, node, context);
        if(repl!=null) 
          newPart=repl;
        else
          newPart = "{"+vnameb+"}";
        pos=varsEnd+1;
       
        if(newPart.length() == 0) {
          //remove text part
        }else
          ret+=newPart;
        vars = val.indexOf("{", pos);
      }
    }
    return ret;
  }
}
