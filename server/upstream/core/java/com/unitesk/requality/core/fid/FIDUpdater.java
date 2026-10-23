package com.unitesk.requality.core.fid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TemplValueProvider;
import com.unitesk.requality.core.checker.CheckerTreeProblem;
import com.unitesk.requality.core.checker.FIDCheckerProblem;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.reqif.ReqIFModel;

public class FIDUpdater {
  public final static String FID_NAME_PARAM="_fidName";
  public final static String FID_PREFIX = "_fidPrefix";
  public final static String DEFAULT_FID_NAME=ReqIFModel.ATTR_DOORS_ID;
  public static final boolean DEFAULT_RO = false;

  public static void updateFID(Pair<Pair<String,String>,Change> change, TreeNode node){
    String oname = change.getFirst().getFirst(), opref = change.getFirst().getSecond();
    Change c = change.getSecond();
    if(c == Change.NO_CHANGES)
      return ;
    TreeDB db = node.getTreeDB();
    String fname = getFIDName(db),
        fpref = getFIDPrefix(db),
        fmaxname = getFIDMaxName(fname);
 
    TreeNode reqs = node.getTreeDB().getNode(Requirement.getTypeRootQId());
    Integer i = reqs.attrs().getIntAttributeValue(fmaxname, 0);
    
    boolean nch = !fname.equals(oname), inc = false, ch = true;
    String up = null;
    
    if(c == Change.NO_PR){
      if(!node.attrs().containsKey("_"+fname)){
        node.attrs().setConstAttribute("_"+fname, i, Flag.HIDDEN);
        inc = true;
      }else{
        up = node.attrs().getStringAttributeValue("_"+fname,"0");
      }
    }
    if(c == Change.PR_SI){
      node.attrs().setConstAttribute(fname, node.attrs().getStringAttributeValue("_"+oname),Flag.HIDDEN);
      node.attrs().removeAttribute("_"+oname);
    }
    if(c == Change.SI_PR){
      if(!node.getSUUId().equals(reqs.getSUUId())){
        node.attrs().setConstAttribute("_"+fname, node.attrs().getStringAttributeValue(oname), Flag.READ_ONLY|Flag.CHANGE_READ_ONLY);
        node.attrs().removeAttribute(oname);
      }
    }
    
    if(c == Change.PR_PR){
      if(nch){
        node.attrs().setConstAttribute("_"+fname, node.attrs().getStringAttributeValue("_"+oname),Flag.HIDDEN);
        node.attrs().removeAttribute("_"+oname);
      }else 
        ch = false;
    }
    
    if(c == Change.PR_NO)
      node.attrs().removeAttribute("_"+oname);
    if(c == Change.SI_NO)
      node.attrs().removeAttribute(oname);
    
    if(c == Change.NO_SI){
      if(!node.attrs().containsKey(fname)){
        node.attrs().setConstAttribute(fname, Integer.toString(i), Flag.READ_ONLY|Flag.CHANGE_READ_ONLY);
        inc = true;
      }else{
        up = node.attrs().getStringAttributeValue(fname,"0");
      }
    }
    if(c == Change.SI_SI){
      if(nch){
        node.attrs().setConstAttribute(fname, node.attrs().getStringAttributeValue(oname), Flag.READ_ONLY|Flag.CHANGE_READ_ONLY);
        node.attrs().removeAttribute(oname);
      }else {
        ch = false;
        up = node.attrs().getStringAttributeValue(fname,"0");
      }
    }
    int upn = 0;
    if(up!=null){
      try{
        upn = Integer.parseInt(up);
        if(upn>0 && (upn+1)>=i) {
          i = upn;
          inc = true;
        }else if (upn == 0) {
          node.attrs().setConstAttribute(fname, Integer.toString(i), Flag.READ_ONLY|Flag.CHANGE_READ_ONLY);
          inc = true;
        }
      }
      catch(NumberFormatException nfe){
        RLogger.logError("cannot parse old SID on "+node.getUUId()+". It is not a number");
      }
    }
    
    if(inc)
      reqs.attrs().setConstAttribute(fmaxname, i+1, Flag.HIDDEN);
    
    if(up!=null||inc)
      reqs.commitAttributes();
    
    if(ch)
      node.commitAttributes();
  }
  public static String getFIDMaxName(TreeDB db) {
    return getFIDMaxName(getFIDName(db));
  }
  private static String getFIDMaxName(String fname) {
    if(fname == null)
      return null;
    return "_next"+fname;
  }
  public static boolean isEnabled(TreeDB db) {
    if(db == null)
      return false;
    return(db.getRootNode().attrs().getStringAttributeValue(FID_NAME_PARAM)!=null);
  }
  public static String getFIDName(TreeDB db){
    return getFIDName(db,DEFAULT_FID_NAME);
  }
  public static String getStoredFIDName(TreeDB db){
    return getFIDName(db,null);
  }
  public static String getFIDName(TreeDB db, String def) {
    if(db == null)
      return def;
    return(db.getRootNode().attrs().getStringAttributeValue(FID_NAME_PARAM,def));
  }
  public static String setSettings(TreeDB db, Pair<Pair<String, String>, Change> change, String fname, String prefix) {
//    Map<TreeDB, Map<String,TreeNode[]>> uniqids = new HashMap<>();
//    Map<TreeDB, Set<TreeNode>> dupProblems = new HashMap<>();
//    Map<TreeDB, Set<TreeNode>> numProblems = new HashMap<>();

    if(prefix!=null)
      prefix = prefix.trim();
    if(prefix == null || prefix.isEmpty()||prefix.equals(" "))
      prefix = null;
    Change c = change.getSecond();
    if(c == Change.NO_CHANGES)
      return "";
    TreeNode root = db.getRootNode();
    TreeNode reqs = db.getNode(Requirement.getTypeRootQId());
    
    String oname = change.getFirst().getFirst();
    String opref = change.getFirst().getSecond();
    if(oname!=null&&!oname.isEmpty())
      root.attrs().removeAttribute(FID_NAME_PARAM);
    if(opref!=null&&!opref.isEmpty())
      root.attrs().removeAttribute(FID_PREFIX);
    root.attrs().setConstAttribute(FID_NAME_PARAM, fname, Flag.HIDDEN);
    if(prefix!=null&&!prefix.isEmpty())
      root.attrs().setConstAttribute(FID_PREFIX, prefix, Flag.HIDDEN);
    if(c == Change.NO_PR || c == Change.NO_SI)
      root.commitAttributes();//for further check with checker
    //change readonly
    boolean nameChanged = false;
    if(oname!=null&&!oname.isEmpty()){
      if(!oname.equals(fname))
        nameChanged = true;
    }else{
      nameChanged = true;
    }
    if(nameChanged){
      List<Object> rl = db.getRootNode().attrs().getListAttributeValue(Attribute.ATTR_READ_ONLY_LIST);
      HashSet<Object> n = new HashSet<>();
      if(rl != null)
        n.addAll(rl);
      if(oname!=null&&(nameChanged))
        n.remove(oname);
      if(fname!=null&&!fname.isEmpty()&&!n.contains(fname))
        n.add(fname);
      if(n.isEmpty())
        db.getRootNode().attrs().removeAttribute(Attribute.ATTR_READ_ONLY_LIST);
      else
        db.getRootNode().attrs().setConstAttribute(Attribute.ATTR_READ_ONLY_LIST,
          new ListValue(AttributeType.STRING, new ArrayList<>(n)), 0);
      db.getRootNode().commitAttributes();
    }
    root.commitAttributes();
    if(c==Change.PR_PR && nameChanged || c == Change.PR_NO || c == Change.PR_SI)
      reqs.attrs().removeAttribute(oname);
    
    if(c==Change.NO_PR || c==Change.PR_PR || c == Change.SI_PR){
      if(c == Change.SI_PR){
        reqs.attrs().setConstAttribute("_"+fname, getOrGenerate(reqs, reqs, oname, fname), Flag.HIDDEN);
        reqs.attrs().removeAttribute(oname);
      }
      reqs.attrs().setAttribute(new AttributeDefinition(reqs.getUUId(), fname, 
          AttributeAvailability.SUBTREE,
          new TemplValueProvider(AttributeType.STRING,prefix+"{_"+fname+"}"), Flag.READ_ONLY|Flag.CHANGE_READ_ONLY));
    }
    boolean findmax = c == Change.NO_PR || c == Change.NO_SI;
    int maxval = 0;
    if(findmax)
      maxval = reqs.attrs().getIntAttributeValue("nextid", 0);
    else
      maxval = reqs.attrs().getIntAttributeValue("_next"+oname, 0);    
    if(findmax)
    {
      Set<TreeNode>localDupProblems = new HashSet<>();
      Set<TreeNode>localNumProblems = new HashSet<>();
      
      Set<TreeNode>clearProblems = new HashSet<>();
      Map<String, TreeNode[]> unid = new HashMap<>();
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(reqs,true,false,true,true,false);

      Iterator<TreeNode> it = dftw.iterator();
      int max = 0;
      //fill uniqueId map and calculate nextId
      int cnt = 0;
      while(it.hasNext())
      {
        TreeNode nde = it.next();
        nde.clearChecked();
        if(nde.hasCheckerProblems(FIDCheckerProblem.class))
          clearProblems.add(nde);
        String num = nde.attrs().getStringAttributeValue(fname);
        if(num!=null){
          TreeNode[] was = unid.get(num);
          if(was==null)
            unid.put(num, new TreeNode[]{nde});
          else{
            TreeNode[]unids = unid.get(num);
            boolean wasit = false;
            for(TreeNode nde2:unids){
              if(nde2.getSUUId().equals(nde.getSUUId()))
                wasit = true;
            }
            if(!wasit)
            {
              localDupProblems.addAll(Arrays.asList(unids));
              localDupProblems.add(nde);
            }
            if(!wasit){
              TreeNode[]newunids = new TreeNode[unids.length+1];
              int ai = 0;
              for(TreeNode node3:unids)
                newunids[ai++] = node3;
              newunids[unids.length]=nde;
              unid.put(num, newunids);
            }
          }
          try{
            int inum = Integer.parseInt(num);
            if((inum+1)>max)
              max = inum+1;
          }
          catch(NumberFormatException nfe){
            localNumProblems.add(nde);
            if(localDupProblems.contains(nde))
              cnt--;
            //problem, tends to be catched on next stage
          }
        }
      }
      clearProblems.removeAll(localNumProblems);
      clearProblems.removeAll(localDupProblems);
      int cnt1 = 0;
      Map<TreeNode,List<IMark>> probs = new HashMap<>();
      for(TreeNode node:localDupProblems)
    	  probs.put(node, new LinkedList<>());
      for(TreeNode node:localNumProblems)
    	  probs.put(node, new LinkedList<>());
      for(TreeNode node:clearProblems){
        ReentrantLock rl = node.getTreeCheckersLock();
        try {
          rl.lock();
          node.removeTreeCheckerProblems(FIDCheckerProblem.class);
          for(IMark m:node.getTreeCheckerProblems())
            probs.get(node).add(m);
        }finally {
          rl.unlock();
        }
      }

	  Set<String> wasnotunique = new HashSet<>();
      for(TreeNode node:localDupProblems){
    	  String fid = node.getForeignID();
    	  if(!wasnotunique.contains(node.getSUUId())){
              wasnotunique.add(node.getSUUId());

              StringBuilder sb = new StringBuilder();
              TreeNode[] dups = unid.get(fid);
              for(TreeNode nodea: dups) {
                if(sb.length()>0)
                  sb.append(", ");
                sb.append("'");
                sb.append(nodea.getQualifiedId()); //updates problem-related list 
                sb.append("'");
              }
              String[]allData = new String[dups.length+2];
              allData[0] = fid;
              allData[1] = sb.toString();
              int i = 2;
              for(TreeNode nodea: dups)
                allData[i++] = nodea.getQualifiedId();
              TreeNode[] nodes = unid.get(fid);
              for(TreeNode nde: nodes)
               if(nde.getSUUId().equals(node.getSUUId())){
                 wasnotunique.add(node.getSUUId());
                 probs.get(node).add(new FIDCheckerProblem(node, "problem_sequential_id_not_unique", "Sequential id value('%s') is not unique on elements %s", allData, nodes));
               }else{
                 probs.get(node).add(new FIDCheckerProblem(node, "problem_sequential_id_not_unique", "Sequential id value('%s') is not unique on elements %s", allData, nodes));
               }
             }
        cnt++;
      }
      for(TreeNode node:localNumProblems){
        probs.get(node).add(new FIDCheckerProblem(node, "problem_sequential_id_non_numerical", "Sequential id value('%s') contains non-numerical value on element '%s'", 
          new String[] {node.getForeignID(), node.getQualifiedId()},  CheckerTreeProblem.NO_RELATED));
      }    
      for(Entry<TreeNode, List<IMark>> en:probs.entrySet()){
        TreeNode chn = en.getKey();
        ReentrantLock rl = chn.getTreeCheckersLock();
        rl.lock();
        try {
          //wontwork chn.registerTreeCheckerProblems(en.getValue());
          chn.getTreeDB().getCheckerContainer().addToCheckQ(chn, false);
        }finally {
          rl.unlock();
        }
      }
      String bufferSeqIdError = ""; 
      if(cnt+cnt1>0) 
        bufferSeqIdError = " The '"+fname+"' attribute values are "+(cnt>0?(cnt1>0?"not unique and sometimes more then max":"not unique"):"sometimes more then max")+ " in some nodes.";
      if(localNumProblems.size()>0)
        return "It's impossible to save specified Sequential Identifiers settings. The '"+fname+"' attribute is not numerical in some nodes."+bufferSeqIdError+" These nodes will be marked with an error icon in the 'Requality Explorer' view. Please correct the identifiers and re-install the settings again.";
      if(!bufferSeqIdError.isEmpty()){
        return "It's impossible to save specified Sequential Identifiers settings."+bufferSeqIdError+" These nodes will be marked with an error icon in the 'Requality Explorer' view. Please correct the identifiers and re-install the settings again.";
      }
      
      if(max>maxval)
        maxval = max;
      //set calculated max value
      
    }
    if(c!=Change.PR_NO&&c!=Change.SI_NO&&nameChanged)//rename maxfid
      reqs.attrs().setConstAttribute("_next"+fname, maxval, Flag.HIDDEN);
    if(c==Change.PR_NO||c==Change.SI_NO||nameChanged)
      reqs.attrs().removeAttribute("_next"+oname);
    reqs.commitAttributes();
    return null;
  }
  private static String getOrGenerate(TreeNode rootReq, TreeNode node, String oname, String newname) {
    String attr = node.attrs().getStringAttributeValue(oname);
    if(attr == null){
      int max = rootReq.attrs().getIntAttributeValue("_next"+newname);
      rootReq.attrs().setConstAttribute("_next"+newname, max+1, Flag.HIDDEN);
      rootReq.commitAttributes();
      return Integer.toString(max);
    }
    return attr;
  }
  public static String getExternalForeignId(TreeNode node) {
    String fid = getForeignId(node);
    if(fid == null)
      return fid;
    return IDMechanics.getShownIdPrefix(node.getTreeDB()) + fid;
  }
  public static String getForeignId(TreeNode node) {
    return node.attrs().getStringAttributeValue(getFIDName(node.getTreeDB()));
  }
  public static Pair<Pair<String,String>,Change> getChangeKind(
      TreeDB db, String fname, String fpref) {
    String fname2 = getFIDName(db,"");
    String fpref2 = getFIDPrefix(db);

//    TreeNode reqs = db.getNode(Requirement.getTypeRootQId());
//    String nid = reqs.attrs().getStringAttributeValue("nextid");
//    if(nid!=null){
//      fname2 = "ForeignID";
//      fpref2 = "";
//      reqs.attrs().setConstAttribute("_next"+fname2,Integer.parseInt(nid),Flag.HIDDEN);
//      reqs.attrs().removeAttribute("nextid");
//      reqs.commitAttributes();
//    }
    
    Change change = null;
    if((fname == null && fname2.isEmpty() || fname!=null&&fname2.equals(fname)) && fpref2.equals(fpref)){
      change = Change.NO_CHANGES;
    }else
    {
      byte fs=0,fn=0;
      if(fpref2.isEmpty())
        if(fname2.isEmpty())
          fs = 0;
        else
          fs = 1;
      else
        fs = 2;
      if(fs == 0) {
        //try to detect non-prefix projects with fid
        TreeNode req = db.getNode(Requirement.getTypeRootQId());
        String fidName = getFIDName(db, DEFAULT_FID_NAME);
        String val = req.attrs().getStringAttributeValue(fidName);
        if(val != null && val.length() > 0) {
          try {
            Integer ival = Integer.parseInt(val);
            fs = 1;//was foreign
            fname2 = fidName;
          }catch(NumberFormatException nfe) {
          }
        }
      }
      if(fpref == null || fpref.isEmpty())
        if(fname == null)
          fn = 0;
        else
          fn = 1;
      else
        fn = 2;
      if(fs == 0 && fn == 0)
        return new Pair<>(new Pair<>(fname2,fpref2),Change.NO_CHANGES);
      if(fs == 0 && fn == 1)
        change = Change.NO_SI;
      if(fs == 0 && fn == 2)
        change = Change.NO_PR;
      if(fs == 1 && fn == 0)
        change = Change.SI_NO;
      if(fs == 1 && fn == 1)
        change = Change.SI_SI;
      if(fs == 1 && fn == 2)
        change = Change.SI_PR;
      if(fs == 2 && fn == 0)
        change = Change.PR_NO;
      if(fs == 2 && fn == 1)
        change = Change.PR_SI;
      if(fs == 2 && fn == 2)
        change = Change.PR_PR;
    }
    return new Pair<>(new Pair<>(fname2,fpref2),change);
  }
  public static String getFIDPrefix(TreeDB db) {
    return db.getRootNode().attrs().getStringAttributeValue(FID_PREFIX,"");
  }
  public static String getStoredFIDPrefix(TreeDB db) {
    return db.getRootNode().attrs().getStringAttributeValue(FID_PREFIX,null);
  }
  public static TreeNode initNodeDesc(
      TreeDB db, TreeNode rootReqs, UUID uid, Map<String, AttributeDefinition> attrs) {
    if(!isEnabled(db))
      return null;
    String fname = getFIDName(db), fpref = getFIDPrefix(db),  fmaxname = getFIDMaxName(fname);
    if(fpref!=null&&!fpref.isEmpty())
      fname = "_"+fname;//double vars mode
    AttributeDefinition ad = attrs.get(fname);
    if(ad == null){
      TreeNode reqs = rootReqs;
      if(reqs!=null) {
        AttributeDefinition mad = reqs.attrs().getWorkAttributeDefinition(fmaxname);
        Integer i = mad==null||mad.getRawValue()==null||mad.getType()!=AttributeType.INT?0:mad.getRawValue().getGValue();
        reqs.attrs().setConstAttribute(fmaxname, i+1, 0);
        
        ad = new AttributeDefinition(uid, fname,
            new ConstValueProvider(AttributeType.STRING, Integer.toString(i)));
        attrs.put(fname,ad);
        return reqs;
      }
    }
    return null;
  }
  public static String getForeignNumber(TreeNode node) {
    String fn = getFIDName(node.getTreeDB());
    String s = node.attrs().getStringAttributeValue("_"+fn);
    if(s!=null)
      return s;
    return node.attrs().getStringAttributeValue(fn);
  }
  public static String getFIDNumberName(TreeDB db) {
    String fn = getFIDName(db); 
    String fp = getFIDPrefix(db);
    if(fp!=null&&!fp.isEmpty())
      return "_"+fn;
    return fn;
  }
  public static String getStoredFIDNumberName(TreeDB db) {
    String fn = getStoredFIDName(db); 
    String fp = getStoredFIDPrefix(db);
    if(fn!=null && fp!=null&&!fp.isEmpty())
      return "_"+fn;
    return fn;
  }
}
