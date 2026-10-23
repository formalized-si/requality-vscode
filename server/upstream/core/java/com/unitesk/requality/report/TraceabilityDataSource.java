package com.unitesk.requality.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.ReferenceRenames;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.report.template.IReportDataSource;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TraceabilityDataSource implements IReportDataSource {

 // private static Map<TreeDB, Map<String, String>> revert_names = null;
  private static String LINK_REL = "reference";
  private static String PARENT_REL = "parent";
  private String[] column, row;

  private Map<String, Integer> ind1;
  private HashSet<String> flist;
  private ListValue settings;
  private Map<String, Map<String, Map<String, Boolean>>> edgesList =
      new TreeMap<>();
  private Map<String, Map<String, Map<String, Boolean>>> redgesList =
      new TreeMap<>();
  private Map<String, TreeNode> edjetonode = new HashMap<>();
  private Map<String, Object> attrs;
  public static String[] defaultTemplates =
      new String[] {"<b>[1]</b> [3]", "<div style='margin-left:20px'>[2] [4]</div>"};
  public static String[][] defaultSettings =
      new String[][] {
        new String[] {"{user-visible-name}", "", "", ""},
        new String[] {
          "{user-visible-name}",
          "{this-page}#{value}",
          "<span style='color: red;'>{value}[{attribute-name}]</span>",
          "{user-visible-name}"
        },
        new String[] {"{revert-attribute-name}", "{next-page}#{value}", "", ""},
        new String[] {"{revert-attribute-name}", "{next-page}#{value}", "", ""}
      };

  public TreeNode getNode(String path) {
    return edjetonode.getOrDefault(path, null);
  }
  private static String META_TRACEABILITY_REVERT_NAME="TRACEABILITY_REVERT_NAMES";
  public static void setRevertNames(TreeDB db, Map<String, String> mp) {
    if(db == null)
      return;
    db.registerMetaObject(META_TRACEABILITY_REVERT_NAME, mp);
  }

  @Override
  public Map<String, Object> getData() {
    Map<String, Object> ret = new TreeMap<>();
    ret.put("trace", this);
    return ret;
  }

  public String getDirectName(boolean firstPage,  TreeNode req, Object filter) {
    return getDirectName(firstPage, null, req, filter);
  }
  public String getDirectName(boolean firstPage, Map<String, Boolean> rvalue, TreeNode req, Object filter) {
    if (!firstPage) return getRevertName(true, rvalue, req, filter);
    if(rvalue == null || rvalue.isEmpty()) {
      if(filter.equals("*"))
        return "References";
      return filter.toString();
    }
    StringBuilder sb = new StringBuilder();
    for(String s:rvalue.keySet()) {
      if(sb.length()>0)
        sb.append(", ");
      sb.append(s);
    }
    return sb.toString();
  }

  public String getRevertName(boolean firstPage,  TreeNode req, Object filter) {
    return getRevertName(firstPage, null, req, filter);
  }
  public String getRevertName(boolean firstPage, Map<String, Boolean> rvalue, TreeNode req, Object filter) {
    if (!firstPage) return getDirectName(true, rvalue, req, filter); // TODO without root?
    if(rvalue == null || rvalue.isEmpty()) {
      if(filter.equals("*"))
        return "Revert references";
      return getRevertName(Instantiator.getRootTree(req.getTreeDB()), filter.toString());
    }

    StringBuilder sb = new StringBuilder();
    TreeDB rootTree = Instantiator.getRootTree(req.getTreeDB());
    for(String s:rvalue.keySet()) {
      if(sb.length()>0)
        sb.append(", ");
      sb.append(getRevertName(rootTree, s));
    }
    return sb.toString();
  }

  private void addEdge(String treeNode, String treeNode2, String a, Boolean correctness) {
    if (!edgesList.containsKey(treeNode))
      edgesList.put(treeNode, new TreeMap<String, Map<String, Boolean>>());
    if (!edgesList.get(treeNode).containsKey(treeNode2))
      edgesList.get(treeNode).put(treeNode2, new TreeMap<String, Boolean>());
    edgesList.get(treeNode).get(treeNode2).put(a, correctness);

    if (!redgesList.containsKey(treeNode2))
      redgesList.put(treeNode2, new TreeMap<String, Map<String, Boolean>>());
    if (!redgesList.get(treeNode2).containsKey(treeNode))
      redgesList.get(treeNode2).put(treeNode, new TreeMap<String, Boolean>());
    redgesList.get(treeNode2).get(treeNode).put(a, correctness);
  }

  
  public String getCombinedKeys(Map<String, Boolean> rvalue) {
    if(rvalue == null || rvalue.isEmpty())
      return "";
    StringBuilder sb = new StringBuilder();
    for(String s:rvalue.keySet()) {
      if(sb.length()>0)
        sb.append(", ");
      sb.append(s);
    }
    return sb.toString();
  }
  private String formGroupValue(
      boolean firstPage, Map<String, Boolean> rvalue, int num, String cname, TreeNode rootReq, Object filter) {
    TreeNode node = null;
    try {
      node = rootReq.getTreeDB().getNode(cname, Path.QID | Path.UV | Path.UUID);
    } catch (Exception e) {
      e.printStackTrace();
    }
    String link = groups[num][1];
    String value = groups[num][0];
    if (node == null) {
      value = groups[num][2];
    } else if (!groups[num][1].equals("")) {
      // value needs to be calculated first and then ' character needs to
      // be replaced
      // {user-visible-name}","{id}","{name-or-id}","{attribute-name}"},
      // links = new
      // String[]{"","{this-page}","{next-page}","{requality-cnf}
      link = getLinkValue(node, firstPage, rvalue, rootReq, filter, cname, link);
      if (link != null)
        if (link.startsWith("requality://"))
          value =
              "<a href=\"#\" onclick='selectNode(\""
                  + link.substring(12)
                  + "\")'>"
                  + value.replace("'", "&#8216;")
                  + "</a>";
        else value = "<a href='" + link + "'>" + value.replace("'", "&#8216;") + "</a>";
    }
    if (link == null && groups[num].length > 3) value = groups[num][3];
    if (value.equals("{skip-line}")) return null;
    return value;
  }

  private String getLinkValue(
      TreeNode node,
      boolean firstPage,
      Map<String, Boolean> rvalue,
      TreeNode rootReq,
      Object filter,
      String cname,
      String link) {
	//TODO replace by BracesHanlder
    link =
        link.replace("{id}", adaptSub(root, node, node.getId()))
            .replace("{user-visible-name}", adaptSub(root, node, node.getUserVisibleName(false)))
            .replace("{attribute-name}", getDirectName(firstPage, rvalue, rootReq, filter))
            .replace("{value}", cname)
            .replace("{revert-attribute-name}", getRevertName(firstPage, rvalue, rootReq, filter))
            .replace("{name-or-id}", adaptSub(root, node, node.getNameOrId()))
            .replace("{this-page}", utils.getTemplFileName(node,firstPage?"trace.ftl":"tracerev.ftl"))
            .replace("{next-page}", utils.getTemplFileName(node,!firstPage?"trace.ftl":"tracerev.ftl"))
            .replace("{requality-cnf}", "requality://");
    int ind = link.lastIndexOf("#");
    if(ind>-1){
        if (link.contains("trace.htm#") && node.getTreeDB()==root.getTreeDB() && !hasTraces(link.substring(ind+1))) link = null;
        else if (link.contains("tracerev.htm#") && node.getTreeDB()==root.getTreeDB() && !hasRevertTraces(link.substring(ind+1))) link = null;
    }
    return link;
  }

  private static String adaptSub(TreeNode root2, TreeNode node, String nameOrId) {
    String add = "";
    if(root2.getTreeDB()!=node.getTreeDB())
      add = node.getTreeDB().getProjectName()+"::";
    return add+nameOrId;
  }

  public String formFirstString(boolean firstPage, String rname, TreeNode rootReq, Object filter) {
    try{
      return "<a id='"
        + rname
        + "'/>"
        + handleTemplate(templatemain, firstPage, filter, rname, Collections.emptyMap(), rootReq, filter);
    }catch(Exception e){
      e.printStackTrace();
    }
    return null;
  }

  public String formSecondString(boolean firstPage, String rname, Map<String, Boolean> rvalue, TreeNode rootReq, Object filter) {
    try{
      return handleTemplate(templateadditional, firstPage, filter, rname, rvalue, rootReq, filter);
    }catch(Exception e){
      e.printStackTrace();
    }
    return null;
  }

  private String handleTemplate(
      String templ,
      boolean firstPage,
      Object filter,
      String rname,
      Map<String, Boolean> rvalue, 
      TreeNode rootReq,
      Object filter2) {
    boolean stop = false; 
    while (!stop) {
      stop = true;
      for (int i = 1; i <= groups.length; i++) {
        if (templ.contains("[" + i + "]")) {
          stop = false;
          String gv = formGroupValue(firstPage, rvalue, i - 1, rname, rootReq, filter);
          if (gv == null) return "";
          templ = templ.replace("[" + i + "]", gv);
        }
      }
    }
    templ =
        templ
            .replace("{attribute-name}", getDirectName(firstPage, rvalue, rootReq, filter))
            .replace("{value}", rname)
            .replace("{revert-attribute-name}", getRevertName(firstPage, rvalue, rootReq, filter));

    TreeNode node = null;
    try {
      node = rootReq.getTreeDB().getNode(rname, Path.QID | Path.UV | Path.UUID);
      if (node != null) {
        templ =
            templ
                .replace("{user-visible-name}", adaptSub(root,node,node.getUserVisibleName(false)))
                .replace("{name-or-id}", adaptSub(root,node,node.getNameOrId()))
                .replace("{id}", adaptSub(root,node,node.getId()))
                .replace("{this-page}", utils.getTemplFileName(node)+(firstPage ? "trace.htm" : "tracerev.htm"))
                .replace("{next-page}", utils.getTemplFileName(node)+(!firstPage ? "trace.htm" : "tracerev.htm"));
        for (String s : node.attrs().getAllAttributeKeys()) {
          if(templ.indexOf("{@" + s + "}")>-1){
            Object o = node.attrs().getAttributeValue(s);
            templ = templ.replace("{@" + s + "}", o.toString());
          }
        }
      }else{
        templ = templ.replace("{this-page}", utils.getTemplFileName(rootReq)+(firstPage ? "trace.htm" : "tracerev.htm"))
            .replace("{next-page}", utils.getTemplFileName(rootReq)+(!firstPage ? "trace.htm" : "tracerev.htm"));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (attrs != null)
      for (String s : attrs.keySet()) {
        Object o = attrs.get(s);
        if (o != null) templ = templ.replace("{#" + s + "}", o.toString());
      }

    return templ;
  }

  public Entry<String, Map<String, Boolean>>[] getTraces(String key) {
    if (key.equals("*")) {
      Set<Entry<String, Map<String, Boolean>>> set = new HashSet<>();
      for (Entry<String, Map<String, Map<String, Boolean>>> s : edgesList.entrySet())set.addAll(s.getValue().entrySet());
      return  set.toArray(new Entry[0]);
    }
    if (!edgesList.containsKey(key)) return new Entry[0];
    return edgesList.get(key).entrySet().toArray(new Entry[0]);
  }

  public boolean getCorrectness(String key, String key2) {
    if (!edgesList.containsKey(key)) return false;
    if (!edgesList.get(key).containsKey(key2)) return false;
    boolean k = true;
    for (String s : edgesList.get(key).get(key2).keySet()) {
      k = k && edgesList.get(key).get(key2).get(s);
      if (!k) break;
    }
    return k;
  }

  public Entry<String, Map<String, Boolean>>[] getRevertTraces(String key) {
    if (key.equals("*")) {
      Set<Entry<String, Map<String, Boolean>>> vals = new HashSet<>();
      for (Entry<String, Map<String, Map<String, Boolean>>> en : redgesList.entrySet()) vals.addAll(en.getValue().entrySet());
      return vals.toArray(new Entry[0]);
    }
    if (!redgesList.containsKey(key)) return new Entry[0];
    return redgesList.get(key).entrySet().toArray(new Entry[0]);
  }

  private String templatemain = defaultTemplates[0], templateadditional = defaultTemplates[1];
  private String[][] groups = defaultSettings;
  private TreeNode root;
  private ReportUtils utils;

  public boolean hasReqLinked(TreeNode node, String aname, String aval) {
    Set<TreeNode> set = new HashSet<>();
    return hasReqLinked(set, node, aname, aval);
  }

  public boolean hasReqLinked(Set<TreeNode> washere, TreeNode node, String aname, String aval) {
    washere.add(node);
    for (TreeNode nde : node.getRelatedNodes(null)) {
      if (washere.contains(nde)) continue;
      Attribute a = nde.attrs().getAttribute(aname);
      if (a == null
          || a.getValue() == null
          || !a.getValue().toString().equals(aval)
          || hasReqLinked(washere, nde, aname, aval)) return true;
    }
    for (String s : node.attrs().getAllAttributeKeys()) {
      Attribute a = node.attrs().getLocalAttribute(s);
      if (a != null && a.getType().equals(AttributeType.REFERENCE)) {
        List<TreeNode> rels = node.attrs().getLinkAttributeValue(s);
        if (rels != null) {
          for (TreeNode rel : rels) {
            if (rel == null || washere.contains(rel)) continue;
            Attribute b = rel.attrs().getAttribute(aname);
            if (b == null
                || b.getValue() == null
                || !b.getValue().toString().equals(aval)
                || hasReqLinked(washere, rel, aname, aval)) return true;
          }
        }
      }
    }
    return false;
  }

  public boolean linksCorrects(TreeNode node, String aname, String aval) {
    Attribute a = node.attrs().getAttribute(aname);
    if (a != null) {
      if (a.getValue() != null
          && a.getValue().toString().toLowerCase().equals(aval.toLowerCase())) {
        return !hasReqLinked(node, aname, aval);
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  public void initData(
      ReportUtils utils,
      TreeNode root,
      String type1,
      String type2,
      String what,
      Object filter,
      Object setts,
      Object p) {
    this.utils = utils;
    this.root = root;
    attrs = (Map<String, Object>) p;
    settings = (ListValue) setts;
    if (settings != null) {
      int i = 0;
      List<Object> l = ((ListValue)settings.getValues().get(0)).getValues();
      if (l.size() == 2) {
        templatemain = l.get(0).toString();
        templateadditional = l.get(1).toString();
        i = 1;
      }
      int k = 0;
      groups = new String[settings.getValues().size() - i][];
      for (; i < settings.getValues().size(); i++) groups[k++] = ((ListValue)(settings.getValues().get(i))).getValues().toArray(new String[0]);
    }
    edjetonode.clear();
    edgesList.clear();
    redgesList.clear();
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(root, false, true, false);
    Iterator<TreeNode> it = dftw.iterator();
    ind1 = new TreeMap<>();
    List<TreeNode> t1 = new ArrayList<>(), t2 = new ArrayList<>();
    Set<TreeNode> toh = new HashSet<>();
    while (it.hasNext()) {
      TreeNode node = it.next();
      String val = node.getUserVisibleName(false);
      if (type1 == null || type1.equals(node.getType())) {
        ind1.put(val, t1.size());
        if(node.getTreeDB()==root.getTreeDB())
          t1.add(node);
      }
    }
    column = new String[t1.size()];
    row = new String[t2.size()];
    int i = 0;
    for (TreeNode s2 : t2) row[i++] = s2.getUserVisibleName(false);
    flist = null;
    if (filter != null) {
      if (filter instanceof String) {
        flist = new HashSet<>();
        flist.add((String) filter);
      }
      if (filter instanceof List) flist = new HashSet<>((List<String>) filter);
    }
    if (what.equals(PARENT_REL)) {
      flist = new HashSet<>();
      flist.add("+");
    }
    i = 0;
    if (what.equals(PARENT_REL))
      for (TreeNode s1 : t1) {
        String id = adaptSub(root,s1,s1.getUserVisibleName(false));
        edjetonode.put(id, s1);
        String pad = adaptSub(root, s1.getShownParent(), s1.getShownParent().getUserVisibleName(false));
        if (t1.contains(s1.getShownParent())) {
          edjetonode.put(pad, s1.getShownParent());
          addEdge(pad, id, "+", true);
        }
        if(s1.getTreeDB()==root.getTreeDB()){
          column[i] = id;
          i++;
        }
      }
    else if (what.equals(LINK_REL)) {
      Set<UUID> handled = new HashSet<>();
      LinkedList<TreeNode> handl = new LinkedList<>(t1);
      while (!handl.isEmpty()) {
        toh.clear();
        Iterator<TreeNode> sit = handl.iterator();
        while (sit.hasNext()) {
          TreeNode s1 = sit.next();
          if (handled.contains(s1.getUUId())) continue;
          String id = adaptSub(root, s1, s1.getUserVisibleName(false));
          edjetonode.put(id, s1);
          handled.add(s1.getUUId());
          for (Entry<String, Map<TreeNode, String>> en:RelManager.getLinks(s1,true).entrySet()) {
            String s = en.getKey();
            if ((flist == null || flist.contains(s) || flist.contains("*"))){
              Set<TreeNode> res = en.getValue().keySet();
              if (res != null && res.size() > 0)
                for (TreeNode node : res) {
                  String ada = adaptSub(root, node, node.getUserVisibleName(false));
                  addEdge(id, ada, s, true);
                  edjetonode.put(ada, node);
                  if (Instantiator.getRootTree(node.getTreeDB())
                      != Instantiator.getRootTree(s1.getTreeDB())) {
                    if (!handled.contains(node.getUUId())&&node.getTreeDB()==root.getTreeDB()) toh.add(node);
                  }
                }
              else {
                String val = null;
                Attribute at = s1.attrs().getAttribute(s);
                if(at!=null){
                  if(at.getValue() instanceof ReferenceValue)
                    val = 
                      ((ReferenceValue)at.getValue()).toString(s1.attrs())
                          .toString()
                          .replace("\n", "")
                          .replace((char) 160, ' ')
                          .trim();
                  else
                    val = at.getValue().toString().replace("\n", "")
                        .replace((char) 160, ' ')
                        .trim();
                }
                if (val != null && !val.isEmpty()) addEdge(id, val, s, true);
              }
            }
          }
          if(s1.getTreeDB()==root.getTreeDB()){
            column[i] = id;
            i++;
          }
          Map<String, Map<TreeNode, String>> rl = RelManager.getRevertLinks(s1,true,true);
          for (Entry<String, Map<TreeNode, String>> en:rl.entrySet()) {
            String s = en.getKey();
            if ((flist == null || flist.contains(s) || flist.contains("*"))){
              Set<TreeNode> res = en.getValue().keySet();
              if (res != null && res.size() > 0)
                for (TreeNode node : res){
               // if(node.getTreeDB()!=s1.getTreeDB()){
                  String ada = adaptSub(root, node, node.getUserVisibleName(false));
                  addEdge(ada, id, s, true);
                  edjetonode.put(ada, node);
                  if (Instantiator.getRootTree(node.getTreeDB())
                      != Instantiator.getRootTree(s1.getTreeDB())) {
                    if (!handled.contains(node.getUUId())&&node.getTreeDB()==root.getTreeDB()) toh.add(node);
                  }
                }
              
            }
          }
        }
        handl = new LinkedList<>(toh);
        column = Arrays.copyOf(column, column.length + toh.size());
      }
      
    }
    //Arrays.sort(column);
  }

  public boolean hasTrace(String p1, String p2) {
    return edgesList.containsKey(p1) && edgesList.get(p1).containsKey(p2);
  }

  public boolean hasRevertTrace(String p1, String p2) {
    return redgesList.containsKey(p1) && redgesList.get(p1).containsKey(p2);
  }

  public String[] getColumnNames() {
    return column;
  }

  public String[] getRowNames() {
    return row;
  }

  public boolean hasTraces(String name) {
    return edgesList.containsKey(name) && edgesList.get(name).size() > 0;
  }

  public boolean hasRevertTraces(String name) {
    return redgesList.containsKey(name) && redgesList.get(name).size() > 0;
  }

  public String loadNames(String s1, String s2) {
    return (flist == null || flist.size() > 1)
        ? Arrays.toString(edgesList.get(s1).get(s2).keySet().toArray())
        : "";
  }

  public static String getRevertName(TreeDB db, String name) { 
    Map<String, String> revert_names = db.getMetaObject(META_TRACEABILITY_REVERT_NAME);
    if(revert_names == null){
      synchronized (META_TRACEABILITY_REVERT_NAME) {
        revert_names = db.getMetaObject(META_TRACEABILITY_REVERT_NAME);
        if(revert_names == null) {
          revert_names =  ReferenceRenames.getNamesAcronyms(db);
          db.registerMetaObject(META_TRACEABILITY_REVERT_NAME, revert_names);
        }
      }
    }
    if (revert_names != null
        && revert_names.containsKey(name)) return revert_names.get(name);
    return name;
  }

  public static boolean changes(TreeDB db, Map<String, String> namesAcronyms) {
    return !ReferenceRenames.getNamesAcronyms(db).equals(namesAcronyms);
  }

}
