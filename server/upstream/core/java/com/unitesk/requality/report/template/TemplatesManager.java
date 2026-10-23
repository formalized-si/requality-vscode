package com.unitesk.requality.report.template;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.Group;
import com.unitesk.requality.report.Groups;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.tool.CoreUtils;

public class TemplatesManager {
  private static final String NONE = "NONE";
  public static final String ATTR_TEMPLATE_STORE="_attrTemplateData";
  public static final String DEF_GROUP = "default";
  private Boolean inited = false, initedGroups = false;
  private static final Group DEFAULT_GROUP = new Group(DEF_GROUP, "Default group", "",-1, TemplateScope.BUILDIN);
  private final LinkedList<String> idOrder = new LinkedList<>();
  private final LinkedList<String> labelOrder = new LinkedList<>();
  private final LinkedList<String> labelSourceOrder = new LinkedList<>();
  private final LinkedList<Boolean> isTemplate = new LinkedList<>();
  private final LinkedHashMap<String, Group> groupmap = new LinkedHashMap<>();
  private final LinkedList<Group> groupOrder = new LinkedList<>();
  private final LinkedHashMap<String, Template> templmap = new LinkedHashMap<>();
//  private static Map<TreeDB, TemplatesManager> man = new HashMap<>();
  private static final TemplatesManager generic = new TemplatesManager(null);
  private static final Comparator<Group> G_COMPARER = new Comparator<Group>(){
    @Override
    public int compare(Group o1, Group o2) {
      Integer ord = Integer.compare(o1.getOrder(), o2.getOrder());
      if(ord!=0)
        return ord;
      return o1.getName().compareTo(o2.getName());
    }
  };
  private static final Comparator<Group> G_COMPARER_REV = new Comparator<Group>(){
    @Override
    public int compare(Group o1, Group o2) {
      Integer ord = Integer.compare(o2.getOrder(), o1.getOrder());
      if(ord!=0)
        return ord;
      return o2.getName().compareTo(o1.getName());
    }
  };
  private static final Comparator<Template> T_COMPARER = new Comparator<Template>(){
    @Override
    public int compare(Template o1, Template o2) {
      Integer ord = Integer.compare(o1.getOrder(), o2.getOrder());
      if(ord!=0)
        return ord;
      return o1.getName().compareTo(o2.getName());
    }
  };
  private static final String DEFAULT_TEMPLATE = "com.unitesk.requality.reports.tableview";
  private static String META_TEMPLATES_STORE="REPORT_TEMPLATE_MANAGER";
  private TreeDB tree = null;
  public static TemplatesManager getStatic(){
    return generic;
  }
  public TemplatesManager(TreeDB db) {
    tree = db;
    if(db!=null)
      db.registerMetaObject(META_TEMPLATES_STORE, this);
  }
  public static TemplatesManager getOne(TreeDB db) {
    if(db == null)
      return generic; 
    TemplatesManager tm = db.getMetaObject(META_TEMPLATES_STORE);
    if(tm == null)
      tm = new TemplatesManager(db);
    return tm;
  }
  public String getReportTemplateString(Template tpl,Requirement rootReq, ReportSettings setts, String lastGenerated){
    if(tpl == null)
      tpl = getTemplate(DEFAULT_TEMPLATE);
    String fn = NONE;
    if(tpl.getTemplates().size()>0) {
      TemplateFile tf = tpl.getTemplates().iterator().next();
      fn = tf.getOutputFileName(
    		  setts.getUserDefinedFileName(tf),
    		  tree, rootReq, setts);
    }else{
      String tm = lastGenerated;
      if(tm!=null)
        fn = tm;
    }
    if(fn == null)
      fn = NONE;
    return tpl.getName()+"|"+fn+"|"+tpl.getGroup()+"|"+getGroupName(tpl.getGroup());
  }
  public void saveReportSettings(ReportSettings rd, Template tpl, ReportGenerationContext context){
    if(tpl == null)
      tpl = getTemplate(DEFAULT_TEMPLATE);
    rd.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, tpl.getId(),Flag.HIDDEN);
    String fn = NONE;
    if(tpl.getTemplates().size()>0)
	  fn = rd.calcStartFileName(tpl.getTemplates().iterator().next(), rd.getTreeDB(), null, context);
    else{
      String tm = rd.attrs().getStringAttributeValue(ReportSettings.ATTR_LAST_GENERATED);
      if(tm!=null)
        fn = tm;
    }
    if(fn == null)
      fn = NONE;
    rd.attrs().setConstAttribute(ATTR_TEMPLATE_STORE, tpl.getName()+"|"+fn+"|"+tpl.getGroup()+"|"+getGroupName(tpl.getGroup()),Flag.HIDDEN);
    TreeNode targetReq = null;
    if(context!=null) {
      targetReq = context.pageRootReq;
      if(context.multiRoots!=null && context.multiRoots.size() > 0)
        targetReq = (Requirement) context.multiRoots.iterator().next();
    }
    if(targetReq!=null) 
      rd.attrs().setConstAttribute(ReportData.ATTR_FIRST_MROOT, targetReq, 0);
    else
      rd.attrs().removeAttribute(ReportData.ATTR_FIRST_MROOT);
  }

  private String getTplName(ReportSettings rd) {
    String s = rd.attrs().getStringAttributeValue(ATTR_TEMPLATE_STORE);
    if(s == null){
      Template t = getTemplate(rd);
      return NONE;
    }
    String[]sd = s.split("\\|");
    return sd[0];
  }
  public Template getTemplate(ReportSettings rd) {
    if(rd == null)
      return loadTemlInfo(null, DEFAULT_TEMPLATE);
    return loadTemlInfo(rd,rd.attrs().getStringAttributeValue(rd.ATTR_TEMPLATE, DEFAULT_TEMPLATE));
  }
  public String getTplFileName(ReportSettings rd, boolean fromtempl, ReportGenerationContext context){
    String ret = fromtempl?null:getStoredFileName(rd);
    if(ret == null || ret.equals(NONE))
    {
      Template t = getTemplate(rd);
      if(t!=null)
        return rd.calcStartFileName(t.getTemplates().iterator().next(), rd.getTreeDB(), null, context);
    }
    if(fromtempl)
      return getStoredFileName(rd);
    return ret;
  }
    
  private static String getStoredFileName(ReportSettings rd) {
    String tm = rd.attrs().getStringAttributeValue(ReportSettings.ATTR_LAST_GENERATED);
    if(tm!=null){
      if(tm.startsWith("\\"))
        tm = tm.substring(1);

      if(tm.startsWith("/"))
        tm = tm.substring(1);
      return tm;
    }
    String s = rd.attrs().getStringAttributeValue(ATTR_TEMPLATE_STORE);
    if(s == null)
      return NONE;
    String[]sd = s.split("\\|");
    return sd[1];
  }
  private static String getGroupName(ReportSettings rd) {
    String s = rd.attrs().getStringAttributeValue(ATTR_TEMPLATE_STORE);
    if(s == null)
      return NONE;
    String[]sd = s.split("\\|");
    return sd[3];
  }
  private static String getGroupId(ReportSettings rd) {
    String s = rd.attrs().getStringAttributeValue(ATTR_TEMPLATE_STORE);
    if(s == null)
      return NONE;
    String[]sd = s.split("\\|");
    return sd[2];
  }
  private String getGroupName(String group) {
    Group g = getGMap().get(group);
    if(g == null)
      return TemplatesManager.NONE;
    return g.getName();
  }

  public Template getTemplate(String defaultId) {
    return getOne(tree).getTMap().get(defaultId);
  }
  public Template loadTemlInfo(ReportSettings rdata, String templateId) {
    Template t = getTemplate(templateId);
    if(t == null && rdata != null)
      t = loadBrokenTemplate(rdata, templateId, TemplateScope.BUILDIN);
    if(t == null && rdata!=null)
      t = TemplatesManager.getOne(rdata.getTreeDB()).getTemplate(Template.DEFAULT_ID);
    return t;
  }

  private static Template loadBrokenTemplate(ReportSettings rdata, String templateId, TemplateScope scope) {
    String s = rdata.attrs().getStringAttributeValue(ATTR_TEMPLATE_STORE);
    if(s == null){
      String lg = rdata.attrs().getStringAttributeValue(ReportSettings.ATTR_LAST_GENERATED);
      String ti = rdata.attrs().getStringAttributeValue(ReportSettings.ATTR_TEMPLATE);
      try {
        return new Template(ti, ti, DEF_GROUP, scope);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    if(s!=null){
      String[]sd = s.split("\\|");
      try {
        return new Template(sd[1], sd[0], sd[3]!=null&&!sd[3].isEmpty()?sd[3]:sd[2], scope);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }

  public Map<String, Template> getTemplates() {
    return getTMap();
  }

  public static File getRootTemplatesFolder(TreeDB tree) {
    return getRootTemplatesFolderPath(tree).toFile();
  }
  public static java.nio.file.Path getRootTemplatesFolderPath(TreeDB tree) {
    return CoreUtils.getCacheDir(tree).getParent().resolve(Template.PROJECT_TEMPLATES_FOLDER);
  }
  
  public List<Group> getOrderedGroups() {
    synchronized (this) {
      if(!initedGroups){
	    groupmap.clear();
        groupOrder.clear();
	    Map<String, Group> groups = null;
	    if(tree!=null) {
          generic.getTMap();
          groups = new HashMap<>(generic.groupmap);
          groups.putAll(ResManager.getManager().getGroups(tree, TemplateScope.PROJECT));
	    }
	    else {
          groups = new HashMap<>(ResManager.getManager().getGroups(null, TemplateScope.ALL));
	    }
	    Map<String,Template> templs = null;
	    if(tree!=null) {
	      generic.getTMap();
          templs = new HashMap<>(generic.templmap);
          templs.putAll(API.getAPIReportTemplates(tree));
          templs.putAll(ResManager.getManager().getTemplates(tree, TemplateScope.PROJECT));
	    }else
	    {
          templs = new HashMap<>(ResManager.getManager().getTemplates(null, TemplateScope.ALL)); 
          templs.putAll(API.getAPIReportTemplates(null));
	    }
	    //groups.put(DEF_GROUP,DEFAULT_GROUP);
	    Set<Group> tohandle = new TreeSet<>(G_COMPARER_REV);
	    Map<String, Set<Group>> childs = new HashMap<>();
	    for(Entry<String, Group> s:groups.entrySet()){
	      Group g = s.getValue();
	      String id = s.getKey();
	      String par = g.getParent();
	      if(!par.isEmpty())
	      {
	        if(!childs.containsKey(par))
	          childs.put(par, new TreeSet<>(G_COMPARER_REV));
	        childs.get(par).add(g);
	      }else{
	        tohandle.add(g);
	      }
	      groupmap.put(id,g);
	    }
	    for(Template t:templs.values())
	      if(!groupmap.containsKey(t.getGroup()))
	      {
	        String id = t.getGroup();
	        Group g;
	        if(id.equals(""))
	          g = DEFAULT_GROUP;
	        else
	          g = new Group(id, id, "", Integer.MAX_VALUE, t.source);
	        tohandle.add(g);
	        groupmap.put(id, g);
	      }
	    LinkedList<Group> thl = new LinkedList<>(tohandle);
	    //Collections.reverse(thl);
	    LinkedList<Group> list = new LinkedList<>();
	    dfs(thl, childs, groupOrder);
        Collections.reverse(groupOrder);
        initedGroups = true;
      }
      return groupOrder;
    }
  }
  private static void dfs(LinkedList<Group> tohandle, Map<String, Set<Group>> childs, List<Group> list){
    while(!tohandle.isEmpty())
      dfs(tohandle.pollFirst(), tohandle, childs, list);
  }
  private static void dfs(Group now, LinkedList<Group> tohandle, Map<String, Set<Group>> childs, List<Group> list){
    Set<Group> child = childs.get(now.getId());
    if(child!=null && child.size()>0){
      LinkedList<Group> children = new LinkedList<>(child);
      while(!children.isEmpty())
        dfs(children.pollFirst(),tohandle,childs,list);
        //tohandle.addFirst(children.pollLast());
    }
    list.add(now);
    //dfs(tohandle,childs,list);
  }
  private void formOrderedList() {
    synchronized (this) {
      getGMap();
	  templmap.clear();
	  idOrder.clear();
	  labelOrder.clear();
	  labelSourceOrder.clear();
	  isTemplate.clear();
      List<Group> grs = getOrderedGroups();

      Map<String, Group> groups = null;
      if(tree!=null) {
        generic.getTMap();
        groups = new HashMap<>(generic.groupmap);
        groups.putAll(ResManager.getManager().getGroups(tree, TemplateScope.PROJECT));
      }
      else {
        groups = new HashMap<>(ResManager.getManager().getGroups(null, TemplateScope.ALL));
      }
      Map<String,Template> templs = null;
      if(tree!=null) {
        generic.getTMap();
        templs = new HashMap<>(generic.templmap);
        templs.putAll(API.getAPIReportTemplates(tree));
        templs.putAll(ResManager.getManager().getTemplates(tree, TemplateScope.PROJECT));
      }else
      {
        templs = new HashMap<>(ResManager.getManager().getTemplates(null, TemplateScope.ALL)); 
        templs.putAll(API.getAPIReportTemplates(null));
      }
	  templmap.putAll(templs);
      
	  Map<String,Integer> deep = new HashMap<>();
	  Map<String, SortedSet<Template>> templlist = new HashMap<>();
	  for(Entry<String, Template> en:templmap.entrySet())
	  {
	    Template t = en.getValue();
	    String group = t.getGroup();
	    if(!templlist.containsKey(group))
	      templlist.put(group, new TreeSet<>(T_COMPARER));
	    templlist.get(group).add(t);
	  }
      for(Group s:grs){
        String par = s.getParent();
        int deepi = 0;
        if(!par.isEmpty())
          deepi = deep.getOrDefault(par,0)+1;
        deep.put(s.getId(), deepi);
        idOrder.add(s.getId());
        isTemplate.add(false);
        labelOrder.add(indented(s.getName(),deepi*2,false));
        labelSourceOrder.add("["+s.getSource().charAt(0)+"] "+indented(s.getName(),deepi*2,false));
        if(templlist.get(s.getId())!=null)
          for(Template t:templlist.get(s.getId())){
            idOrder.add(t.getId());
            isTemplate.add(true);
            labelOrder.add(indented(t.getName(),deepi*2+2,true));
            labelSourceOrder.add("["+t.getSource().charAt(0)+"] "+indented(t.getName(),deepi*2+2,true));
            deep.put(s.getId(), deepi+1);
          }
      }
	  inited = true;
    }
  }
  private String indented(String name, int addi, boolean isTemplate) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0;i<addi;i++)
      sb.append(" ");
    if(isTemplate)
      sb.append("- ");
    sb.append(name);
    return sb.toString();
  }
  public LinkedList<Boolean> getIsTemplate() {
    synchronized (this) {
      if(!inited)
        formOrderedList(); 
    }
    return new LinkedList<>(isTemplate);
  }

  public List<String> getLabelOrder() {
	synchronized (this) {
      if(!inited)
        formOrderedList(); 
    }
    return new LinkedList<>(labelOrder);
  }
  public List<String> getLabelOrderWithSource() {
	synchronized (this) {
      if(!inited)
        formOrderedList(); 
    }
    return new LinkedList<>(labelSourceOrder);
  }
  public List<String> getIdOrder() {
    synchronized (this) {
      if(!inited)
        formOrderedList(); 
    }
    return new LinkedList<>(idOrder);
  }
  public Map<String, Template> getTMap() {
    synchronized (this) {
      if(!inited)
        formOrderedList(); 
    }
    return new HashMap<>(templmap);
  }
  public Map<String, Group> getGMap() {
    synchronized (this) {
      if(groupmap.isEmpty())
        getOrderedGroups();
    }
    return new HashMap<>(groupmap);
  }
  public void clearCaches() {
    synchronized (this) {
      inited = false;
      initedGroups = false;
      idOrder.clear();
      labelOrder.clear();
      labelSourceOrder.clear();
      isTemplate.clear();
      templmap.clear();
      groupOrder.clear();
      groupmap.clear();
    }
  }
  public static Map<String,Group> getLocalGroup(TreeDB db, TemplateScope ts) {
    if(ts == null || ts!= TemplateScope.PROJECT && ts!= TemplateScope.ALL || db == null)
      return Collections.emptyMap();
    File f2 = getRootTemplatesFolder(db);
    Map<String, Group> ret = Collections.emptyMap();
    if(f2.exists())
	    for(File f:f2.listFiles()) {
	      if(f.isDirectory()) {
	        try {
	          Groups gl = new Groups(f.toURI().toString(), TemplateScope.PROJECT);
	          if(gl!=null && gl.getAllGroups().size()>0) {
	        	ret = new HashMap<>();
	            for(Group g : gl.getAllGroups())
	              ret.put(g.getId(), g);
	          }
			} catch (IOException e) {
			}
	      }
	    }
	return ret;
  }
  public static Map<String,Template> getLocalTemplates(TreeDB db, TemplateScope ts) { 
    if(ts == null || ts!= TemplateScope.PROJECT && ts!= TemplateScope.ALL || db == null)
      return Collections.emptyMap();
    File f = getRootTemplatesFolder(db);
    Map<String, Template> ret = Collections.emptyMap();
    if(f.exists())
	    for(File f2:f.listFiles()) {
	      if(f2.isDirectory()) {
	        Template t;
			try {
	          t = new Template(f2.toURI().toString(), TemplateScope.PROJECT);
	          if(t!=null) {
	            if(ret.isEmpty())
	              ret = new HashMap<>();
	            ret.put(t.getId(), t);
	          }
			} catch (IOException e) {
			}
	      }
	    }
    return ret;
  }
  public Template get(String defaultId) {
    return getTemplates().get(defaultId);
  }
  public static String getTemplateIdForFolder(File from) {
    try {
      Template t = new Template(from.toURI().toString(), TemplateScope.PROJECT);
      if(t!=null)
        return t.getId();
    }catch(Exception e) {
    }
    return null;
  }
}