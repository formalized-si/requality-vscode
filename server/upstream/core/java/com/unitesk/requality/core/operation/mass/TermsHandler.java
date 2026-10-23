package com.unitesk.requality.core.operation.mass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.TermsManipulator;
import com.unitesk.requality.nodetype.Requirement;

public class TermsHandler extends AbstractMassOperation {
  int step = 0;
  Map<String, TreeNode> defs = new HashMap<>();
  Map<String,Map<String,TreeNode>> fdefs = new HashMap<>();
  Set<TreeNode> tosave = new HashSet<>();
  List<TermNFPair> nfts = new LinkedList<>();
  public StringBuilder messages;

  private static class TermNFPair {
    public TermNFPair(TreeNode node, String s) {
      term = s;
      target = node;
    }

    public String term;
    public TreeNode target;
  }

  public TermsHandler() {
    super("TermsHandler");
    messages = new StringBuilder();
  }

  @Override
  public String getName() {
    return "Rework terms for project ";
  }

  @Override
  public boolean execute(TreeNode root) {
    step = 0;
    defs.clear();
    TreeNode store = root.getTreeDB().getRootNode();
    root.getTreeDB().clearTagsCache();
    root = root.getTreeDB().getNode(Requirement.getTypeRootQId());
    boolean b = super.execute(root);
//    if (!b) return false;
//    step++;
//    nfts.clear();
//    b = super.execute(root);
    if (!b) return false;
    for (TreeNode node : tosave) node.commitAttributes();
    tosave.clear();
    Set<String> terms = new HashSet<>();
    for (TermNFPair pr : nfts) {
      messages.append("Term '");
      messages.append(pr.term);
      messages.append("' is undefined on \n\t");
      messages.append(pr.target.getQualifiedId());
      messages.append("\n\n");
      terms.add(pr.term);
    }
    RLogger.logDebug(Arrays.toString(terms.toArray(new String[0])));
    return b;
  }

  @Override
  public void performSingleAction(TreeNode node) {
    if (node.isDisposed()) // removed;
    return;
    if (step == 0) {
      boolean ch = false;
      String[] defsa = node.attrs().getStringArrayAttributeValue(TermsManipulator.DEF_TERM_ATTR_NAME);
      if (defsa != null && defsa.length > 0) {
        for (Object o : defsa) {
          if (defs.containsKey(o)){//.toString().toLowerCase())) {
            messages.append("WARNING! '");
            messages.append(o.toString());
            messages.append("' is defined in two places:\n\t");
            messages.append(node.getQualifiedId());
            messages.append("\nand\n\t");
            messages.append(defs.get(o.toString().toLowerCase()).getQualifiedId());
            messages.append("\n\n");
          }
          defs.put(o.toString(),node);//.toString().toLowerCase(), node);
        }
      }

      for (String s : node.attrs().getLocalAttributeKeys())
        if (s.startsWith(TermsManipulator.TERM_REF_ATTR_PREFIX)) {
          String key = s.substring(TermsManipulator.TERM_REF_ATTR_PREFIX.length());
          boolean u = false;
//          String[] uset = node.attrs().getStringArrayAttributeValue(TermsManipulator.USE_TERM_ATTR_NAME);
//          if (uset != null) for (String s2 : uset) if (s2.equals(key)) u = true;
//          uset = node.attrs().getStringArrayAttributeValue(TermsManipulator.USE_TERM_ALT_ATTR_NAME);
//          if (uset != null) for (String s2 : uset) if (s2.equals(key)) u = true;
          if (!u) {
            ch = true;
            node.attrs().removeAttribute(s);
          }
        }
      if (ch) node.commitAttributes();
    } else if (step == 1) {
      List<String> listo = new LinkedList<>();
      Set<String> /*vals = new HashSet<>(),*/valsa = new HashSet<>();
      Collection<String> va = null;
      
      Attribute term = node.attrs().getAttribute(TermsManipulator.USE_TERM_ATTR_NAME);
      boolean haswrong = term != null, chterms = false;
//      vals.addAll(gatherValues(node, term,true));
//      va = gatherValues(node, term,false);
//      for(String s:va)
//      {
//        if(!valsa.contains(s))
//          listo.add(s);
//        valsa.add(s);
//      }
      //TODO values from use-term are removed for current project
      //still they are correct when importing from libre so this code tends to be returned
      //in future
      term = node.attrs().getAttribute(TermsManipulator.USE_TERM_ALT_ATTR_NAME);
     
      //vals.addAll(gatherValues(node, term,true));
      va = gatherValues(node, term,false);
      for(String s:va)
      {
        if(!valsa.contains(s))
          listo.add(s);
        valsa.add(s);
      }
      //int size = vals.size(),sizea = valsa.size();
      
      //chterms = size!=vals.size();
      for (String rs : listo) {
        String s = rs;//.toLowerCase();
        TreeNode tdef = null;
        String adds = "";
        if(!s.contains("::")){
          tdef = defs.get(s);
        }else
        {
          TreeDB adb = null;
          int pos = s.indexOf("::");
          if (pos > -1) {
            String name = s.substring(0, pos);
            adds = name+"::";
            if(fdefs.containsKey(name)){
              tdef = fdefs.get(name).get(s.substring(pos + 2));
            }else{
             // ResManager tm = ResManager.getManager(); //TODO load?
              adb = Global.getRepoManager(node.getTreeDB()).getTreeDB(node.getTreeDB(),null, name, false,  node.getTreeDB().getMonitor());//TODO IDE monitor
              if (adb != null) {
                s = s.substring(pos + 2);
                TermsHandler th = new TermsHandler();
                th.performAction(adb.getNode(Requirement.getTypeRootQId()));
                fdefs.put(name, th.defs);
                tdef = fdefs.get(name).get(s);
              }else
                fdefs.put(name, new HashMap<>());
            }
          }
          
        }
        if (tdef == null) {
          nfts.add(new TermNFPair(node, rs));
        } else {
//          String key = TermsManipulator.TERM_REF_ATTR_PREFIX + rs;
//          AttributeDefinition termlink = null;
//          termlink = node.attrs().getWorkAttributeDefinition(key);
//          if (termlink != null) {
//            List<Object> vals2 = new ArrayList<>();
//            if (termlink.getType().equals(AttributeType.REFERENCE)) {
//              if ((adds+tdef.getSUUId()).equals(termlink.getRawValue().getValue()))
//                continue; // already exists
//              String sw = termlink.getRawValue().getValue().toString();
//              TreeNode lnode = tdef.getTreeDB().getNode(sw);
//              if (lnode != null && !lnode.isDisposed()) vals2.add((termlink.getRawValue().getValue()));
//            } else if (termlink.getType().equals(AttributeType.LIST)) {
//              for (Object o : ((ListValue)termlink.getRawValue().getGValue()).getValues()){
//                if(o instanceof ReferenceValue)
//                  o = ((ReferenceValue)o).getRaw();
//                if (!vals2.contains(o)) {
//                  String os = o.toString();
//                  int pos = os.indexOf("::");
//                  TreeDB db = node.getTreeDB();
//                  if (pos > -1) {
//                    String name = os.substring(0, pos);
//                    //ResManager tm = ResManager.getManager(); //TODO load?
//                    db = API.getRepoManager(node.getTreeDB()).getTreeDB(db, name, false);
//                    if (db != null) {
//                      os = os.substring(pos + 2);
//                    }else
//                      db = node.getTreeDB();
//                  }
//                  
//                  TreeNode lnode = db.getNode(os);
//                  if (lnode != null && !lnode.isDisposed()) vals2.add(o);
//                }
//              }
//            }
//            if (!vals2.contains(adds+tdef.getSUUId())) 
//              vals2.add(adds+tdef.getSUUId());
//            if (vals2.size() > 1) // TODO warn, more than one def
//              node.attrs().setAttribute(new AttributeDefinition(node.getUUId(), 
//                  termlink.getKey(), new ReferenceValueProvider(AttributeType.LIST,
//                      new ListValue(AttributeType.REFERENCE, vals2)),
//                      AttributeDefinition.detectFlags(termlink.getKey())));
//            else
//              node.attrs().setAttribute(new AttributeDefinition(
//                  node.getUUId(), termlink.getKey(), 
//                  new ReferenceValueProvider(AttributeType.REFERENCE, vals2.iterator().next()),
//                      AttributeDefinition.detectFlags(termlink.getKey())));
//          } else {
//            node.attrs().setAttribute(new AttributeDefinition(node.getUUId(),
//                key, new ReferenceValueProvider(AttributeType.REFERENCE, adds+tdef.getSUUId()),
//                AttributeDefinition.detectFlags(key)));
//          }
//          tosave.add(tdef);
//          tosave.add(node);
        }
      }
      if(haswrong){
        tosave.add(node);
        node.attrs().removeAttribute(TermsManipulator.USE_TERM_ATTR_NAME);
        if(chterms)
          node.attrs().setAttribute(new AttributeDefinition(
            node.getUUId(),
            TermsManipulator.USE_TERM_ALT_ATTR_NAME,
            AttributeDefinition.detectValueProvider(TermsManipulator.USE_TERM_ALT_ATTR_NAME, 
                AttributeType.LIST, new ListValue(AttributeType.STRING,
                    new ArrayList<>(valsa)), TempFolderFilesProvider.EMPTY),
                AttributeDefinition.detectFlags(TermsManipulator.USE_TERM_ALT_ATTR_NAME)
            ));
      }
    }
  }

  private static Collection<String> gatherValues(TreeNode node, Attribute term,boolean ignoreCase) {
    if (term == null) return Collections.<String>emptySet();
    if (term.getValue() != null) {
      Set<String> vals = new HashSet<>();
      if (term.getType().equals(AttributeType.LIST)) {
        List<Object> rvals = ((ListValue)term.getGValue()).getValues();
        for (Object o : rvals){
          String va = o.toString();
          if(ignoreCase)
            va = va.toLowerCase();
          vals.add(va);
        }
      } else {
        Object o = term.getValue();
        String va = Attribute.valueToString(node.attrs(),term.getType(), o);
        if(ignoreCase)
            va = va.toLowerCase();
        vals.add(va);
      }
      return vals;
    }
    return Collections.<String>emptySet();
  }

  @Override
  public void applySettings(Map<String, String> args) {
    // TODO Auto-generated method stub

  }
}
