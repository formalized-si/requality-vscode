package com.unitesk.requality.report;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.unitesk.requality.core.MultiPart;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.SystemNames;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.valueprovider.ChildIndexProvider;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.NumericIdProvider;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.events.INodeCacheEventListener;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.MassCheckersReset;
import com.unitesk.requality.core.script.ElementMapper;
import com.unitesk.requality.core.storage.TermsManipulator;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.repo.DummyReportNodeCacheListener;
import com.unitesk.requality.report.template.TemplateFile;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class ReportUtils {
  private static TypedValue EMPTY_STRING = new TypedValue(AttributeType.STRING, "");
  private TreeDB baseDB;
  private TreeDB db;
  private Requirement rootReq;
  private ReportSettings report;//TODO maybe replace by related map
  private TemplateFile tf;
  private Map<String,String> renames = new HashMap<>();
  private Collection<TemplateFile> files;
  private ReportGenerationContext context = null;

  private static ScriptEngine engine = CoreUtils.getJSEngine();
  public TypedValue getEmptyStringTV(){
    return EMPTY_STRING;
  }
  public ReportUtils(TreeDB baseDB, TreeDB treeDB, Requirement root, ReportSettings report, TemplateFile t, Collection<TemplateFile> tfiles, ReportGenerationContext content) {
    this.baseDB = baseDB;
    db = treeDB;
    this.rootReq = root;
    this.report = report;
    this.context = content;
    tf = t;
    files = tfiles;
  }

  public TemplateFile getTemplateFile(){
    return tf;
  }
  public TreeDB getNonRevDB(){
    return getNonRevDB(db);
  }
  public TreeDB getNonRevDB(TreeDB db){
    return TreeStateManager.getOne(db.getTracker()).getRevisionInfo(db) == null 
        ? db : 
          context.getTreeDB(baseDB, db.getLocalName(), db.getProjectName(), null);
  }
  public MultiPart getMultiPart(){
    if(context.mpart!=null)
      return context.mpart;
    return db.getMultipartSettings();
  }
  
  public String getMultiPartName(TreeNode node) {
    TreeDB db = node.getTreeDB();
    TreeDB nonRIdb = getNonRevDB(db);
    String attrName =nonRIdb.getNode(Requirement.getTypeRootQId())
            .attrs().getStringAttributeValue(MultiPart.ATTR_MULTIPART_TITLE_ATTRIBUTE, TreeNode.ATTR_NAMEORID);
    return node.attrs().getStringAttributeValue(attrName, node.getId());
  }
  public static String[] getDefTerms(TreeNode node){
    String[]ret = node.attrs().getStringArrayAttributeValue(TermsManipulator.DEF_TERM_ATTR_NAME);
    if(ret!=null)
      return ret;
    return new String[0];
  }
  public boolean isSameFile(TreeNode node, TreeNode alt) {
    return ReportGenerationContext.getAdoptedReqRoot(context, node.getTreeDB(), node) ==
        ReportGenerationContext.getAdoptedReqRoot(context, alt.getTreeDB(), alt);
  }

  public static String getLinkTextPrefix(TreeNode node, TreeNode alt) {
    if(node.getTreeDB()!=alt.getTreeDB()) {
      return IDMechanics.getShownIdPrefix(node.getTreeDB());
    }
    return "";
  }
  public String getTemplFileName(TreeNode node, String id){
    for(TemplateFile tf:files)
      if(tf.getTemplatePath().equals(id)){
        TreeDB db = node.getTreeDB();
        return context.getURIResolver().getTemplateFileName(tf, db, ReportGenerationContext.getAdoptedReqRoot(context, db, node), report);//TODO use setts?
      }
    return "NOT_FOUND";
  }
  public String getTemplFileName(TreeDB db){
    ReportGenerationContext cont2 = context.getRelatedContext(db);
    if(cont2 == null)
      cont2 = context;
    return cont2.getURIResolver().getTemplateFileName(db, ReportGenerationContext.getAdoptedReqRoot(cont2, db, cont2.pageRootReq), cont2.getReportData());//rev vers?
  }
  public String getTemplFileName(TreeNode node){
    TreeDB db = node.getTreeDB();
    ReportGenerationContext cont2 = context.getRelatedContext(db);
    if(cont2 == null)
      cont2 = context;
    return cont2.getURIResolver().getTemplateFileName(db, ReportGenerationContext.getAdoptedReqRoot(cont2, db, node), cont2.getReportData());//rev vers?
  }
  public String getTemplFileName(TreeDB db, TreeNode node, boolean isRoot){
    ReportGenerationContext cont2 = context.getRelatedContext(db);
    if(cont2 == null)
      cont2 = context;
    return cont2.getURIResolver().getTemplateFileName(db, isRoot?node:ReportGenerationContext.getAdoptedReqRoot(cont2, db, node), cont2.getReportData());
  }
  public IReqURIResolver getURIResolver(){
    return context.getURIResolver();
  }
  public static String getLocalName(String s){
    int ind = s.indexOf("::");
    if(ind>-1)
      return s.substring(ind+2);
    return s;
  }
  public static String[] getDefToUseTerms(TreeNode node){
    String[] dt = node.attrs().getStringArrayAttributeValue(TermsManipulator.DEF_TERM_ATTR_NAME);
	List<String> map = TermsManipulator.getDefToUseTermsNames(node);
    List<String> nms = new ArrayList<>();
    if(map!=null&&!map.isEmpty())
    {
      for(String s:dt){
        if(map.contains(s))
          nms.add(s);
      }
    }
    return nms.toArray(new String[0]);
  }
  public TypedValue getVar(String name){
    return getVar(rootReq, name);
  }
  public TypedValue getVar(TreeNode node, String name){
    TypedValue tv = getVarMeta(node, name, null);
    if(tv == null)
      return null;
    return refineString(tv.getType(), tv.getValue(), renames, true);
  }
  public void addRenames(Map<String,String> rn){
    renames.putAll(rn);
  }
  private TypedValue refineString(AttributeType tp, Object o, Map<String, String> renames2, boolean first) {
    if(tp == AttributeType.STRING){//TODO html
      String v = o==null?"":o.toString();
      for(String s:renames.keySet())
        v = v.replaceAll("\\{"+s+"\\}", renames.get(s));
      return new TypedValue(tp, v);
    }
    if(tp == AttributeType.LIST){
      ListValue lv = (ListValue)o;
      AttributeType tpa = lv.getType();
      if(tpa == AttributeType.STRING || tpa == AttributeType.LIST)
      {
        List<Object> la = new ArrayList<>();
        for(Object va : lv.getValues()){
          TypedValue r = refineString(tpa, va, renames, false);
          la.add(r.getValue());
        }
        return new TypedValue(AttributeType.LIST, new ListValue(tpa, la));
      }
    }
    return new TypedValue(tp, o);
  }
  public Map<String,Object> getMapVar(String name){
    return getMapVarMeta(rootReq, name, Collections.EMPTY_LIST);
  }
  public Map<String,Object> getMapVarMeta(TreeNode node, String name, List<Object> meta){
    TypedValue tv = getVarMeta(node, name, meta, false);
    if(tv == null || tv.getType()!=AttributeType.LIST)
      return new HashMap<>(); 
    return toMapVar(tv.getType(),(ListValue)tv.getValue());
  }
  
  public void initMainGeneration(){
    List<INodeCacheEventListener> la = new LinkedList<>(db.getCache().getListeners());
    for(INodeCacheEventListener l:la)
      if(l instanceof DummyReportNodeCacheListener){
        DummyReportNodeCacheListener rnch = (DummyReportNodeCacheListener) l; 
        rnch.initMainGeneration();
      }
    
  }

  List<TreeDB> dbs = null;
  List<String> names = null;
  Map<String, TreeDB> rel = null;
  public Map<String, TreeDB> getRel(){
    if(context!=null){
      boolean o = context.loadRelatedProjects();
      if(!o)
      {
        rel = new TreeMap<>();
        rel.put(db.getProjectName(), db);
      }
      if(rel != null)
        return rel;
      if(rel==null){
        rel = new TreeMap<>();
        for(TreeDB db:context.getRelatedTrees())
          rel.put(db.getProjectName(), db);
      }
    }
    return rel;
  }
  public List<String> getProjectNames (TreeNode req){
    if(names != null)
      return names;
    getRel();
    Map<String,TreeDB> snames = new TreeMap<>();
    Set<String> rem = new HashSet<>();
    for(TreeDB db:getRel().values()) {
      String pname = db.getProjectName();
      TreeDB old = snames.get(pname);
      if(old!=null) {
        if(!rem.contains(pname) && !old.getLocalName().equals(pname)){
          rem.add(pname);
          String oldname = pname+"["+old.getLocalName()+"]";
          if(!snames.containsKey(oldname)){
              snames.put(oldname, old);
              rel.put(oldname, old);
          }
        }
        String newname = null;
        if(pname.equals(db.getLocalName())){
          newname = pname;
          rem.remove(pname);
        }else
          newname = pname+"["+db.getLocalName()+"]";
        snames.put(newname, db);
        rel.put(newname, db);
      }else
        snames.put(pname, db);
    }
//    if(!snames.containsKey(req.getTreeDB().getProjectName()))
//      snames.put(req.getTreeDB().getProjectName(), req.getTreeDB());
    rem.forEach(one->snames.remove(one));
    names = new LinkedList<>(snames.keySet());
    dbs = new LinkedList<>(snames.values());
    return names;
  }
  //TODO remove duplicated projectNames
  public List<TreeDB> getProjectTrees (TreeNode req){
    getProjectNames(req);
    if(dbs != null)
      return dbs;
    return Collections.emptyList();
  }
  public String getHeaderName(String name){ 
    TreeDB db = getRel().get(name);
    if(db == null)
      db = this.db;
    return getTreeName(db, name);
  }
  public String getTreeName(TreeDB db) {
    return getTreeName(db, db.getSemiProjectName());
  }
  public String getTreeName(TreeDB db, String def) {
    String n = (rootReq == null || rootReq.getTreeDB()!=db) ? null :  
      rootReq.attrs().getStringAttributeValue("ReportSubTitle");
    if(n!=null)
      return n;
    n = db.getNode("/Requirements").attrs().getStringAttributeValue("ReportSubTitle");
    if(n != null)
      return n;
    return def;
  }
  public TreeDB getTree(String name){
    TreeDB db = getRel().get(name);
    if(db == null)
      return null;
    return db;
  }
  public static Pair<Set<String>, Map<String, Map<String, List<IMark>>>> getProblemedNodes(TreeDB db, TreeNode r) {
    Map<String, Map<String, List<IMark>>> map = new HashMap<>();
    Set<String> problemRelated = new HashSet<>();
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(r,false,false,true,true,false);
    Iterator<TreeNode> it = dftw.iterator();
    db.getCheckerContainer().forceCheckQ(false);
    while(it.hasNext()){
      TreeNode node = it.next();
      IMark[] ret =  node.getCheckerProblems();
      if(ret!=null && ret.length>0){
        TreeNode n = node;
        TreeNode par = node;
        while(par!=null && !problemRelated.contains(par.getSUUId())) {
          problemRelated.add(par.getSUUId());
          par = par.getWorkParent();
        }
        Map<String, List<IMark>> probs = map.get(n.getSUUId());
        if(probs == null) {
          probs = new HashMap<>();
          map.put(n.getSUUId(), probs);
        }
        for(IMark mark:ret) {
          String cat = "";
          if(mark instanceof IMark) {
            IMark source = (IMark) mark;
            cat = source.getCheckerMessageId();
          }
          List<IMark> probsList = probs.get(cat);
          if(probsList == null) {
            probsList = new LinkedList<>();
            probs.put(cat, probsList);
          }
          probsList.add(mark);
        }
      }
    }
    return new Pair<>(problemRelated, map);
  }
  static String getAttiditionalNubericId(TreeNode o1) {
    String num = o1.attrs().getStringAttributeValue(NumericIdProvider.ATTR_KEY, "0");
    if(num.endsWith("0"))
      num = num+o1.attrs().getStringAttributeValue(ChildIndexProvider.ATTR_KEY);
    return num;
  }
  static Comparator<TreeNode> problemsComparator = new Comparator<>() {
    @Override
    public int compare(TreeNode o1, TreeNode o2) {
      if(o1 == null)
        return o2 == null? 0 : -1;
      if(o2 == null)
        return 1;
      if(o1 == o2)
        return 0;
      return getAttiditionalNubericId(o1)
          .compareTo(getAttiditionalNubericId(o2));
    }
  };
  public static Map<TreeNode, Map<String, List<IMark>>> getCheckerProblems(TreeDB db, TreeNode r) {
    TreeMap<TreeNode, Map<String, List<IMark>>> map = new TreeMap<>(problemsComparator);
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(r,false,false,true,true,false);
    Iterator<TreeNode> it = dftw.iterator();
    db.getCheckerContainer().forceCheckQ(false);
    while(it.hasNext()){
      TreeNode node = it.next();
      IMark[] ret =  node.getCheckerProblems();
      if(ret.length>0) {
        //add node and all pars to related set
        Map<String, List<IMark>> probs = map.get(node);
        if(probs == null) {
          probs = new HashMap<>();
          map.put(node, probs);
        }
        for(IMark mark:ret) {
          String cat = "";
          if(mark instanceof IMark) {
            IMark source = (IMark) mark;
            cat = source.getCheckerMessageId();
          }
          List<IMark> probsList = probs.get(cat);
          if(probsList == null) {
            probsList = new LinkedList<>();
            probs.put(cat, probsList);
          }
          probsList.add(mark);
        }
      }
    }
    return map;
  }

  public Map<String,Object> toMapVar(AttributeType type, ListValue lv){
    Map<String, Object> ret = new TreeMap<>();
    if(type == AttributeType.LIST){
      for(Object o : lv.getValues()){
        ListValue lva = (ListValue)o;
        if(lva.getType() == AttributeType.LIST){
          Iterator<Object> aaa = lva.getValues().iterator();
          ret.put(((ListValue)aaa.next()).getValues().iterator().next().toString(), ((ListValue)aaa.next()).getValues());
        }else
          if(lva.getType() != AttributeType.STRING)
            throw new RuntimeException("Unsupported type!");
        Iterator<Object> it = lva.getValues().iterator();
        ret.put(it.next().toString(), it.next());
      }
    }
    return ret;
  }

  public TypedValue getVarMeta(TreeNode node, String name, List<Object> meta){
    return getVarMeta(node, name, meta,true);
  }
  public TypedValue getVarMeta(TreeNode node, String name, List<Object> meta, boolean def){
    boolean b = node.getTreeDB().getCache().pauseListeners();
    try{
      Object defVal = null;
      if(meta!=null && meta.size()>0 && def)
        defVal = meta.get(0);
      if(name.equals("genData")){
        //from rep project
        String v = node.attrs().getStringAttributeValue(ReportSettings.ATTR_DATE);
        if (v == null) {
          AttributeDefinition ad = context.reportPropData.get(ReportSettings.ATTR_DATE);
          if(ad!=null) {
            v = (String) ad.getRawValue().getValue();
          }
          if(v == null)
            v = (String) defVal;
        }
        return new TypedValue(AttributeType.STRING, v);
      }
      TypedValue tv = null;
      //also mb root
      Attribute a = node.attrs().getAttribute(name);
      if(a!=null){
        tv = a.getTValue();
        if(tv!=null)
           return tv;
      }
      tv = API.getValue(name, node.getTreeDB(), context!=null?context.monitor:null, meta!=null?meta.toArray(new Object[0]):new Object[0]);
      if(tv == null){
        if(defVal == null)
          return null;
        else
          return new TypedValue(AttributeType.detectType(defVal), defVal);
      }
      return tv;
    }
    finally{
      if(b)
        node.getTreeDB().getCache().continueListeners();
    }
  }
  public static TreeNode[] getDefToUseNodes(TreeNode node, String revname){
	Map<TreeNode, String> nodes = TermsManipulator.getDefToUseTermsRelations(node,true, revname);
    if(nodes == null)
      return new TreeNode[0];
    Map<TreeNode, String> nd = nodes;
    if(nd == null || nd.isEmpty())
      nd = new HashMap<>();
    if(!nd.isEmpty())
      return fidSort(nd.keySet().toArray(new TreeNode[0]));
    return new TreeNode[0];
  }
  public static TreeNode[] fidSort(TreeNode[] arr){
    Arrays.sort(arr,new Comparator<TreeNode>(){
      @Override
      public int compare(TreeNode o1, TreeNode o2) {
        String fs1 = o1.getForeignNumber();
        int f1 = Integer.MAX_VALUE;
        if(fs1 != null)
          f1 = Integer.parseInt(fs1);
        String fs2 = o2.getForeignNumber();
        int f2 = Integer.MAX_VALUE;
        if(fs2 != null)
          f2 = Integer.parseInt(fs2);
        if(f1==Integer.MAX_VALUE && f2==Integer.MAX_VALUE)
          return o1.compareTo(o2);
        return Integer.compare(f1, f2);
      }
    });
    return arr;
  }
  public static String[] getUseToDefTerms(TreeNode node){
   // List<String> map = node.getTreeDB().getTermsStorage().getUseToDefTermsNames(node);
    Set<String> nms = new TreeSet<>();
    String[] dta = node.attrs().getStringArrayAttributeValue(TermsManipulator.USE_TERM_ALT_ATTR_NAME);
    for(String s:dta){
      nms.add(s);
    }
    dta = node.attrs().getStringArrayAttributeValue(TermsManipulator.USE_TERM_ATTR_NAME);
    for(String s:dta){
      nms.add(s);
    }
    return nms.toArray(new String[0]);
  }
  public static TreeNode[] getUseToDefTerms(TreeNode node, String revname){
    String nvterm = revname;
    int ind = revname.indexOf(TermsManipulator.VERSION_SEPARATOR);
    if(ind>-1)
      nvterm = nvterm.substring(0,ind);
    Map<String, Map<TreeNode, String>> nodes = node.getTreeDB().getTermsStorage().getUseToDefTermsRelations(node,revname);
    if(nodes == null)
      return new TreeNode[0];
    Map<TreeNode, String> nd = nodes.get(nvterm);
    if(nd == null || nd.isEmpty())
      nd = new HashMap<>();
    for(String sb:nodes.keySet())
    {
      String sa = sb;
      int i = sa.indexOf("::");
      if(i>-1){
        sa = sa.substring(i+2);
        if(sa.equals(revname))
        {
          Map<TreeNode, String> nd2 = nodes.get(sb);
          if(nd2 != null && !nd2.isEmpty())
            nd.putAll(nd2);
        }
      }
    }
    return nd
        .keySet().toArray(new TreeNode[0]);
  }
  public static TreeNode[] getRevertNodes(TreeNode node, String revname){
    Map<TreeNode, String> nodes = RelManager.getRevertLinks(node,true,revname);
    if(nodes!=null && nodes.size()>0)
      return nodes.keySet().toArray(new TreeNode[0]);
    return null;
  }
  public static boolean filter(TreeNode node, Object filt, String key) {
    Collection<Object> filters = null;
    if(filt instanceof Collection) {
      filters = (Collection) filt;
    }else if (filt instanceof ListValue) {
      filters = ((ListValue)filt).getValues();
    }
    if (key.startsWith("_") || filters == null) return false;
    for (Object o : filters) {
      String s = o.toString();
      if (s.startsWith("*ref")) {
        String opt = s.substring(4);
        boolean not = false;
        if (opt.startsWith("!")) {
          not = true;
          opt = opt.substring(1);
        }
        Attribute a = node.attrs().getAttribute(key);
        if (a != null
            && (a.getType() == AttributeType.REFERENCE
                || a.getListType() != null && a.getListType() == AttributeType.REFERENCE)) {
          if (opt.isEmpty()) return true;
          if (opt.startsWith("*")) return not ^ key.endsWith(opt.substring(1));
          if (opt.endsWith("*")) return not ^ key.startsWith(opt.substring(0, opt.length() - 1));
          return not ^ key.equals(opt);
        }
      } else if (s.equalsIgnoreCase(key)) return true;
    }
    return false;
  }

  public static Map<String, Attribute> getAttributesInList(final TreeNode node) {
    //TODO move to attribute storage as getVisibleAttributes
    Map<String, Attribute> attrs = new HashMap<>();
    attrs.put("id", new Attribute(new AttributeDefinition(node.getUUId(),"id",
        new ConstValueProvider(AttributeType.STRING,node.getId())),node.attrs()));//TODO remove
    for (String s : node.attrs().getAllAttributeKeys()) {
      Attribute a = node.attrs().getLocalAttribute(s);
      if (a!=null && s.startsWith("_")) /*
                   * && !TreeNode.ATTR_PREDICATE.equals(s)&&
                   * !TreeNode.ATTR_NAME.equals(s) &&
                   * !TreeNode.ATTR_TYPE.equals(s))
                   */ continue;
      if (a!=null && a.getOrigin().equals(node.getUUId())) attrs.put(s, a);
    }
    return attrs;
  }
 
  public static String getAttributes(final TreeNode node) {
    StringBuffer sb = new StringBuffer("");
    for (Attribute a : getAttributesInList(node).values()) {
      sb.append(a.getKey());
      sb.append(": ");
      sb.append(Attribute.valueToString(node.attrs(), a.getType(), a.getValue()));//TODO enum
      sb.append("\n");
    }
    return sb.toString();
  }

  private static QName styleName = new QName("class");

  public static String brTransform(String desc) {
    return desc;
  }

  public static String reworkDescription(String desc, String type, String ntv) {
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    Writer bw = new OutputStreamWriter(byteOut);
    XMLEventReader reader;
    LinkedList<String> openned = new LinkedList<>();
    if (desc.isEmpty()) return desc;
    try {
      if (type.startsWith(TextNode.TYPE_NAME) && ntv != null && ntv.equals("in_process"))
        type = "review";
      Reader r = new BufferedReader(new StringReader("<root>" + htmlEncapsulate(desc) + "</root>"));
      reader = XMLInputFactory.newInstance().createXMLEventReader(r);

      XMLEventWriter writer = XMLOutputFactory.newInstance().createXMLEventWriter(bw);
      writer.setDefaultNamespace("");
      writer.setPrefix("", "");
      LinkedList<String> watch = new LinkedList<>();
      XMLEventFactory eventFactory = XMLEventFactory.newInstance();
      while (reader.hasNext() && reader.peek() != null) {
        XMLEvent event = (XMLEvent) reader.next();
        boolean addattr = false;
        if (event.isStartElement()) {
          String tag = event.asStartElement().getName().getLocalPart();
          if (isStyleBreaking(tag) && (!openned.isEmpty() && openned.peekLast().equals("Sspan"))) {
            writer.add(eventFactory.createEndElement("", "", "span"));
            openned.pollLast(); // p?
          }
          if ((event.asStartElement().getName().getLocalPart().equals("p")
                  || event.asStartElement().getName().getLocalPart().equals("pre"))
              || event.asStartElement().getName().getLocalPart().equals("div")) addattr = true;
          openned.addLast(event.asStartElement().getName().getLocalPart());
        } else if (event.isEndElement()) {
          String tag = event.asEndElement().getName().getLocalPart();
          if (!watch.isEmpty() && watch.peek().equals(tag)) {
            if(openned.peek().equals("Sspan")){
              openned.poll();
              writer.add(eventFactory.createEndElement("", "", "span"));
            }
            watch.pollLast(); // span?
          }
          while (!openned.isEmpty() && !openned.peekLast().equals(tag)) {
            String ltag = openned.removeLast();
            if (ltag.equals("Sspan")) ltag = "span";
            writer.add(eventFactory.createEndElement("", "", ltag));
          }
          if (!openned.isEmpty() && openned.getLast().equals(tag)) openned.pollLast();
        } else if (event.isCharacters()) {
          if (!openned.contains("p") && !openned.contains("pre") && !openned.contains("div")) {
            if (!openned.peekLast().equals("Sspan")) { // TODO fix
              writer.add(eventFactory.createStartElement("", "", "span"));
              writer.add(eventFactory.createAttribute("class", type.toLowerCase()));
              writer.add(eventFactory.createAttribute("style", "display:inline;"));
              openned.addLast("Sspan");
            }
          }
        }
        boolean ignore = false;
        if (event.isStartElement()) {
          javax.xml.stream.events.Attribute a =
              event.asStartElement().getAttributeByName(styleName);
          StartElement se = event.asStartElement();
          if (a != null && addattr) {
            ignore = true;
            Iterator in = se.getNamespaces();
            String sa = "";
            if(in.hasNext())
              sa = in.next().toString();
            writer.add(eventFactory.createStartElement("","", se.getName().getLocalPart()));
            @SuppressWarnings("unchecked")
            Iterator<javax.xml.stream.events.Attribute> i = se.getAttributes();
            String rem = null;
            while (i.hasNext()) {
              javax.xml.stream.events.Attribute e = (javax.xml.stream.events.Attribute) i.next();
              if (e.getName().getLocalPart().toLowerCase().equals("class")) {
                String s = e.getValue();
                if (!s.contains(type.toLowerCase())) {
                  rem = s;
                  s = type.toLowerCase();
                }
                writer.add(eventFactory.createAttribute("class", s));
              } else
                writer.add(eventFactory.createAttribute(e.getName().getLocalPart(), e.getValue()));
            }
            if (rem != null) {
              writer.add(eventFactory.createStartElement("", "", "span"));
              openned.addLast("Sspan");
              writer.add(eventFactory.createAttribute("class", rem));
              watch.add(se.getName().getLocalPart());
            }
          }
        }
        if (!ignore) {
          try{
            if(event.isStartDocument()){
              writer.add(eventFactory.createStartDocument());
            }else
            if(event.isStartElement())
            {
              StartElement se = (StartElement)event;
              writer.add(eventFactory.createStartElement("","", se.getName().getLocalPart()));
              @SuppressWarnings("unchecked")
              Iterator<javax.xml.stream.events.Attribute> i = se.getAttributes();
              String rem = null;
              while (i.hasNext()) {
                javax.xml.stream.events.Attribute e = (javax.xml.stream.events.Attribute) i.next();
                writer.add(eventFactory.createAttribute(e.getName().getLocalPart(), e.getValue()));
              }
            }else if(event.isEndElement()){
              EndElement ee = (EndElement)event;
              writer.add(eventFactory.createEndElement("","", ee.getName().getLocalPart()));
            }else
              writer.add(event);
          }catch(XMLStreamException xse){
            writer.close();
            RLogger.logError(desc);
            xse.printStackTrace();
          }
          if (addattr) writer.add(eventFactory.createAttribute("class", type.toLowerCase()));
        }
      }
      writer.close();
      reader.close();
      byteOut.close();
      writer = null;
      eventFactory = null;
      return byteOut
          .toString()
          .replace("<root>", "")
          .replace("</root>", "")
          .replace("<?xml version=\"1.0\" ?>", "")
          .replace("<br></br>", "<br>");
    } catch (FactoryConfigurationError e) {
      e.printStackTrace();
    } catch (XMLStreamException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";
  }

  private static boolean isStyleBreaking(String tag) {
    return tag.equals("li")
        || tag.equals("ol")
        || tag.equals("span")
        || tag.equals("td")
        || tag.equals("font");
  }

  public static String getLvlTire(int lvl, String s2) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lvl - 1; i++) sb.append(s2);
    return sb.toString();
  }

  public static String replaceQuotes(String str, String repl) {
    return str.replace("'", repl).replace("\"", repl)
        .replace("”", repl).replace("”", repl).replace("“", repl)
        .replace("'", repl).replace("«", repl).replace("»", repl)
        .replace("‘", repl).replace("’", repl).replace(repl+repl, repl);
  }
  public static String htmlEscape(String str) {
    return CoreUtils.htmlEscape(str);
  }
  public static String replHtmlChars(String str) {
    return CoreUtils.replHtmlChars(str);
  }
  
  //TODO adds generic mass ops caller
  public static void callMassCheckersReset(TreeNode node){
    MassCheckersReset mcr = new MassCheckersReset();
    mcr.execute(node);
  }

  public static String htmlSpaces(String str) {
    return CoreUtils.htmlSpaces(str);
  }


  public static String urlEncode(String str) {
    return CoreUtils.URLEncode(str);
  }

  public static String htmlEncapsulate(String str) {
    if (str == null) return "";
    return CoreUtils.htmlEncapsulate(str,true);
  }

  public static String replaceSlashN(String str) {
    if (str == null) return null;
    return str;//.replace("\n", "<br>");
  }

  public Object getDocumentNodeModel(String id) {
    Document d = (Document) db.getNode(id, Path.QID);
    if (d == null) return null;
    return d.getNodeModel();
  }

  public TreeNode[] getDocuments() {
    return getDocuments(rootReq.getQualifiedId());
  }

  TreeNode[] docs = null;
  private Map<String, TreeNode[]> docsmap = new HashMap<>();
  @Deprecated
  public TreeNode[] getDocuments(String qid) {
    if(docs!=null)
      return docs;
    DeepFirstTreeWalker dftw =
            new DeepFirstTreeWalker(
               db.getNode(Document.getTypeRootQId()), true, true, false);
    Iterator<TreeNode> it = dftw.iterator();
    Set<TreeNode> docsa = new HashSet<>();
    while(it.hasNext()) {
      TreeNode node = it.next();
      if(node.getType().equals(Document.TYPE_NAME))
        docsa.add(node);
    }
    docs = docsa.toArray(new TreeNode[0]);
    return docs;
//    if (qid == null)
//      qid = Requirement.getTypeRootQId();
//    if (!docsmap.containsKey(qid)) {
//      Set<TreeNode> docs = new HashSet<>();
//      try {
//        DeepFirstTreeWalker dftw =
//            new DeepFirstTreeWalker(db.getNode(qid, Path.QID), true, true, false);
//        Iterator<TreeNode> iter = dftw.iterator();
//        while (iter.hasNext()) {
//          TreeNode node = iter.next();
//          if (node.getType().equals(Requirement.TYPE_NAME))
//            for (Location loc : ((Requirement) node).getLocations(false)) {
//              Document doc = loc.getDocument();
//              if (doc != null) docs.add(loc.getDocument());
//            }
//        }
//        docsmap.put(qid, docs.toArray(new TreeNode[0]));
//      } catch (Exception e) {
//        e.printStackTrace();
//      }
//    }
//    if (docsmap.containsKey(qid)) return docsmap.get(qid);
//    return null;
  }

  public TreeNode getNodeByUUID(String uid) {
    try {
      UUID uuid = UUID.fromString(uid);
      return rootReq.getTreeDB().getNode(uuid);
    } catch (Exception e) {
      return null;
    }
  }

  public String getFullName(TreeNode node) {
    return Requirement.getFullName(node, node.getTreeDB(), false);
  }
  public static String[] getAttrVis(TreeDB db, Object attrsO){
    String[]attrs = (attrsO instanceof List)?((List<String>)attrsO).toArray(new String[0]):(String[]) attrsO;
    String[]names = new String[attrs.length];
    for(int i = 0;i<names.length;i++){
      String raw = attrs[i];
      String sena = ReportSettings.getRevservnames(db).get(raw);
      if(sena == null || sena.length() == 0)
        sena = SystemNames.getOne(db).getVisSysName(raw);
      if(sena == null)
        sena = raw;
      names[i] = sena;
    }
    return names;
  }
  public static String[] getAttrWidth(Object attrsO, Object widthO){
    String[]attrs = (attrsO instanceof List)?((List<String>)attrsO).toArray(new String[0]):(String[]) attrsO;
    String[]width = (widthO instanceof List)?((List<String>)widthO).toArray(new String[0]):(String[]) widthO;
    String[]ret = new String[attrs.length];
    for(int i = 0;i<ret.length;i++){
      ret[i] = "";
      if(i<width.length){
        ret[i] = width[i];
        if(ret[i] == null)
          ret[i] = "";
      }
      if(ret[i].isEmpty() && attrs[i].equals("_description"))
        ret[i] = "850px";
    }
    return ret;
  }
  public static String[] getAttrFormula(Object attrsO, Object widthO){
    String[]attrs = (attrsO instanceof List)?((List<String>)attrsO).toArray(new String[0]):(String[]) attrsO;
    String[]width = (widthO instanceof List)?((List<String>)widthO).toArray(new String[0]):(String[]) widthO;
    String[]ret = new String[attrs.length];
    for(int i = 0;i<ret.length;i++){
      ret[i] = "";
      if(i<width.length){
        ret[i] = width[i];
        if(ret[i] == null)
          ret[i] = "";
      }
      if(ret[i].isEmpty() && attrs[i].equals("Производное"))
        ret[i] = "value == \"true\"?\"Пр\":value == \"false\"?\"\":value";
    }
    return ret;
  }
  public static String refineValue(String val, String key, String formula){
    String ret;
    try{
      ret = getValueByFormula(val, key, formula);
    }catch(Exception e){
      ret = val+"<br><font color=\"red\"><bold>"+e.getMessage()+"</bold></font>";
      RLogger.logError(e);
    }
    return ret;
  }
  Map<String,Map<String, Object>> maps = new HashMap<>();
  Map<String,TreeMap<String, Object>> smaps = new HashMap<>();
  private Comparator<String> numberComp = new Comparator<String>() {
    @Override
    public int compare(String o1, String o2) {
      return CoreUtils.compareNumberedStrings(o1, o2);
    }
  };
  public void resetMap(String name){
    maps.remove(name);
    smaps.remove(name);
  }
  public void addToMap(String name, String key, Object val){
    if(!maps.containsKey(name))
      maps.put(name, new HashMap<>());
    if(val.equals(""))
      val = null;
    maps.get(name).put(key, val);
    if(!smaps.containsKey(name))
      smaps.put(name,new TreeMap<>(numberComp));
    smaps.get(name).put(key,val);
  }
  public void addToMapList(String name, String key, Object val){
    if(!maps.containsKey(name))
      maps.put(name, new HashMap<>());
    if(!maps.get(name).containsKey(key))
      maps.get(name).put(key, new LinkedList<>());
    if(val.equals(""))
      val = null;
    if(val!=null)
      ((List<Object>)maps.get(name).get(key)).add(val);
  }
  public Map<String, List<Object>> getMapList(String name){
    Map<String, Object> m = maps.get(name);
    if(m == null)
      return java.util.Collections.emptyMap();
    TreeMap<String, List<Object>> ret = new TreeMap<>(numberComp);
    for(String s:m.keySet())
    {
      Object o = m.get(s);
      if(o instanceof List)
        ret.put(s, (List<Object>)o);
    }
    return ret;
  }
  public Map<String, Object> getMap(String name){
    Map<String, Object> m = smaps.get(name);
    if(m == null)
      return java.util.Collections.emptyMap();
    return smaps.get(name);
//    ret.putAll(m);
//    return ret;
  }
  private static ElementMapper em = new ElementMapper(); 
  public static String getValueByPathFormula(TreeNode node, String path,  String formula) throws ScriptException
  {
    try{
      Map<String,Object> vals = new HashMap<>();
      vals.put("key", "path");
      int lv = path.lastIndexOf("/");
      if(lv>-1)
        vals.put("lastSegment", path.substring(lv+1));
      else
        vals.put("lastSegment", path);
      vals.put("one", em.getElement(node));
      vals.put("value", path);
      vals.put("path", path);
      return getValueByFormula(vals, formula);
    }catch(Exception e){
      return CoreUtils.getTrace(e);
    }
  }
  public static String getValueByFormula(String val, String key,  String formula) throws ScriptException
  {
    Map<String,Object> vals = new HashMap<>();
    vals.put("key", key);
    vals.put("value", val);
    return getValueByFormula(vals, formula);
  }
  public static String getValueByFormula(Map<String,Object> vars,  String formula) throws ScriptException{
    if(formula == null || formula.isEmpty())
      return (String) vars.get("value");
    ScriptContext context = engine.getContext();
    for(String k:vars.keySet())
      context.setAttribute(k,vars.get(k), ScriptContext.ENGINE_SCOPE);
    return engine.eval(formula).toString();
  }
  public String getProjectPrefix(TreeDB project){
    return IDMechanics.getShownIdPrefix(project);
  }
  private void recRefineRef(StringBuilder sb, TreeNode ref, String referenceText, AttributeType type, boolean islist, Object val, Map<String, Object> setmap){
    if(val!=null){
      if(referenceText.length()>0){
        sb.append(referenceText);
        sb.append(":<br/>");
      }
      if(islist){
        ListValue lv = (ListValue)val;
        for(Object o:lv.getValues())
          recRefineRef(sb, ref, "", lv.getType(), lv.getType() == AttributeType.LIST, o, setmap);
      }else{
        if(val instanceof ReferenceValue){
          ReferenceValue rv = (ReferenceValue)val;
          TreeNode[] reta = rv.getNodes(ref.attrs());
          if(reta == null)
            setmap.put(Attribute.valueToString(ref.attrs(),type,rv.getRaw()), "");
          else
          {
            for(int i = 0;i<reta.length;i++){
              TreeNode nde = reta[i];
              if(nde == null)
                continue;
              String fid = nde.getForeignID();
              if(fid!=null){
                if(Instantiator.getRootTree(nde.getTreeDB())!=Instantiator.getRootTree(ref.getTreeDB()))
                  setmap.put(nde.getExternalForeignID(), getTemplFileName(ref)+"#"+fid);
                else
                  setmap.put(fid, "#"+fid);
              }else
                setmap.put(Attribute.valueToString(ref.attrs(),type,rv.getRaw()), "");
            }
          }
        }
        //recRefineRef(ref,setmap, )
      }
    }
  }
  public String refineRef(TreeNode ref, String referenceText, boolean isList, Object valm, Attribute refines, String mapname, int lvl ){
    if(refines!=null){
      Map<String, Object> setmap = new TreeMap<>();
      StringBuilder sb = new StringBuilder();
      Object val = refines.getValue();
      if(val!=null)
        recRefineRef(sb, ref, referenceText,refines.getType(), refines.isList(), val,setmap);
      for(String s:setmap.keySet()){
        Object v = setmap.getOrDefault(s, "");
        if(v.equals("")){
          sb.append("<span class=\"error\">");
          sb.append(s);
          sb.append("</span>");
        }else{
          sb.append("<a href = \"");
          sb.append(v);
          sb.append("\">");
          sb.append(s);
          sb.append("</a>");
        }
        sb.append("<br>/");
      }
      return sb.toString();
    }
    return "";
  }
  public String refineRev(TreeNode req, TreeNode[]nodes){
    Map<String,Object> set = Collections.emptyMap();
    for(int i = 0;i<nodes.length;i++){
      TreeNode node = nodes[i];
      if(node == null)
        continue;
      String fida = node.getForeignID();
      if(fida!=null)
      {
        if(set.isEmpty())
          set = new TreeMap<>();
        if(!isSameFile(node, req))
          set.put(node.getExternalForeignID(),getTemplFileName(req)+"#"+fida);
        else
          set.put(fida, "#"+fida);
      }
    }
    if(set.isEmpty())
      return "";
    StringBuilder sb = new StringBuilder();
    for(String s:set.keySet()){
      sb.append("<a href=\"");
      sb.append(set.get(s));
      sb.append("\">");
      sb.append(s);
      sb.append("</a><br/>");
    }
    return sb.toString();
  }

  public boolean isPr(TreeNode node){
    Attribute a = node.attrs().getAttribute("Производное");
    if(a == null)
      return false;
    if(a.getType()==AttributeType.BOOL)
      return a.getGValue();
    return a.getValue().toString().startsWith("Пр");
  }
  public String reworkImageInDescription(TreeNode node,String key, String desc, String reportLocation) throws UnsupportedEncodingException{
    return RequalityCLI.getReportDescription(node,key, node.getNodeResourcesPath().toString(), desc,
        "", "", reportLocation);
  }
  public Map<String, Map<TreeNode, String>> getLinks(TreeNode node){
    return RelManager.getLinks(node,true);
  }
  public Map<String, Map<TreeNode, String>> getRevertLinks(TreeNode node){
    return RelManager.getRevertLinks(node,true,true);
  }
}
