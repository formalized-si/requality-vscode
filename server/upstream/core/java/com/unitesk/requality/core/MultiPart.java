package com.unitesk.requality.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.ReferenceValue;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class MultiPart {
  public static String ATTR_MULTIPART="MultiPartDocument";
  public static String ATTR_MULTIPART_TITLE_ATTRIBUTE="MultiPartDocumentTabTitle";

  public List<TreeNode> getMultipartRoots(ReportGenerationContext context, Requirement initialRootReq, boolean allowRelated) {
    Requirement rootReq = (Requirement) initialRootReq.getTreeDB().getNode(
    		Requirement.getTypeRootQId());
    MultiPartMode mode = detectMultipartMode(initialRootReq, false);
    List<TreeNode> roots = internalDetectRootReqs(mode, initialRootReq, false);
    if(roots == null && rootReq!=initialRootReq && allowRelated){//related?{
      mode = detectMultipartMode(rootReq, false);
      List<TreeNode> mroots = internalDetectRootReqs(mode, rootReq, false);
      roots = detectRelatedRoots(context, mroots, initialRootReq);
    }
    if(roots == null || roots.isEmpty()) {//fallback to /Requirements if it have no children
      roots = new LinkedList<>();
      roots.add(initialRootReq);
    }
    return roots;
  }
  private List<TreeNode> detectRelatedRoots(ReportGenerationContext context, List<TreeNode> mroots, Requirement req) {
    LinkedList<TreeNode> tohandle = new LinkedList<>();
    List<TreeNode> roots = new LinkedList<>();
    Set<TreeNode> set = new HashSet<>();
    Set<TreeNode> handleset = new HashSet<>();
    tohandle.add(req);
    roots.add(req);
    set.add(req);
//    if(context!=null)
//      context.multiRoots = roots;
    return detectRelatedRoots(context, mroots, set, handleset, roots, tohandle);
  }
  private List<TreeNode> detectRelatedRoots(ReportGenerationContext context,
          List<TreeNode> currentMpartSet,
		  Set<TreeNode> set, Set<TreeNode> handleset, List<TreeNode> nodes,
		  LinkedList<TreeNode> tohandle) {
    while(!tohandle.isEmpty()) {
      TreeNode req = tohandle.pollFirst();
      if(handleset.contains(req))
        continue;
      handleset.add(req);
	  DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(req);
      Iterator<TreeNode> it = dftw.iterator();
      TreeDB tree = req.getTreeDB();
      TreeNode baseRoot = ReportGenerationContext.getAdoptedReqRoot(context, currentMpartSet, tree, req);
      while(it.hasNext()) {
        TreeNode node = it.next();
        Map<String, Map<TreeNode, String>> links = RelManager.getLinks(node, true);
        for(Entry<String, Map<TreeNode, String>> en: links.entrySet())
    	  if(en.getValue()!=null)
            for(Entry<TreeNode, String> en2: en.getValue().entrySet())
            {
              TreeNode alt = en2.getKey();
              TreeNode altRoot = ReportGenerationContext.getAdoptedReqRoot(context, currentMpartSet, tree, alt);
              if(baseRoot!=altRoot && altRoot!=null && !handleset.contains(altRoot) && !set.contains(altRoot)) {
                tohandle.addLast(altRoot);
                nodes.add(altRoot);
                set.add(altRoot);
              }
            }
        links = RelManager.getRevertLinks(node, true, false);
        for(Entry<String, Map<TreeNode, String>> en: links.entrySet())
          if(en.getValue()!=null)
            for(Entry<TreeNode, String> en2: en.getValue().entrySet())
            {
              TreeNode alt = en2.getKey();
              TreeNode altRoot = ReportGenerationContext.getAdoptedReqRoot(context, tree, alt);
              if(baseRoot!=altRoot && altRoot!=null && !handleset.contains(altRoot) && !set.contains(altRoot)) {
                tohandle.addLast(altRoot);
                nodes.add(altRoot);
                set.add(altRoot);
              }
            }
      }
    }
    return nodes;
  }
  public MultiPartMode detectMultipartMode(Requirement requirement, boolean recursive){
    return detectMultipartMode(requirement, recursive, MultiPartMode.NONE);
  }
  public MultiPartMode detectMultipartMode(Requirement requirement, boolean recursive, MultiPartMode modeOnNone){
    Object mpsetts = requirement.attrs().getAttributeValue(MultiPart.ATTR_MULTIPART);
    if(mpsetts!=null){
      if(mpsetts instanceof Boolean)
        return ((Boolean)mpsetts)?MultiPartMode.CHILDREN:MultiPartMode.NONE;
      if(mpsetts instanceof ListValue && ((ListValue)mpsetts).getType() == AttributeType.REFERENCE)
        return MultiPartMode.EXACT_NODES; 
      if(mpsetts instanceof ReferenceValue){
        ReferenceValue root = (ReferenceValue) mpsetts;
        if(recursive)
          if(root!=null) {
            TreeNode[] nodes = root.getNodes(requirement.attrs());
            if(nodes!=null && nodes.length>0 && nodes[0] instanceof Requirement) {
              Requirement target = (Requirement) nodes[0];
              return detectMultipartMode(target, recursive, MultiPartMode.CHILDREN);
            }
          }
        return MultiPartMode.TARGET_CHILDREN;
      }
    }
    return modeOnNone;
  }
  private List<TreeNode> internalDetectRootReqs(MultiPartMode mode, Requirement initialRootReq, boolean mpOnNull){  
    Object mpsetts = initialRootReq.attrs().getAttributeValue(MultiPart.ATTR_MULTIPART);
    boolean useMP = false;
    List<TreeNode> roots = null;
    switch(mode){
      case CHILDREN:{//use all reqs from rootReq childs
        useMP = mpsetts==null?mpOnNull:(Boolean)mpsetts;
        roots = new LinkedList<>();
        roots.addAll(initialRootReq.getSortedShownChildren(Requirement.TYPE_NAME));
        roots.addAll(initialRootReq.getSortedShownChildren(TextNode.TYPE_NAME));
      }break;
      case EXACT_NODES:{//use reqs by given list
        ListValue lv = (ListValue) mpsetts;
        List<Object> vals = lv.getValues();
        roots = new LinkedList<>();
        for(Object o:vals) {
          ReferenceValue rv = (ReferenceValue)o;
    	  TreeNode[]linked = rv.getNodes(initialRootReq.attrs());
          for(TreeNode node:linked)
            roots.add(node);
        }
        useMP = true;
      }break;
      case TARGET_CHILDREN:{
        ReferenceValue root = (ReferenceValue) mpsetts;
        if(root!=null) {
          TreeNode[] nodes = root.getNodes(initialRootReq.attrs());
          if(nodes!=null && nodes.length>0 && nodes[0] instanceof Requirement) {
            Requirement target = (Requirement) nodes[0];
            MultiPartMode mode2 = detectMultipartMode(target, false, MultiPartMode.CHILDREN);
            return internalDetectRootReqs(mode2, target, true);
          }else
            RLogger.logError("During multipart report generation "+ATTR_MULTIPART+" reference value on "+initialRootReq.getSUUId()+" led to incorrect reference value");
        }
      }break;
    }
    return roots;
  }
  public boolean isEnabled(TreeNode initialRootReq) {
    Requirement rootReq = (Requirement) initialRootReq.getTreeDB().getNode(
    		Requirement.getTypeRootQId());
    if(internalIsEnabled(initialRootReq))
      return true;
    if(rootReq!=initialRootReq)
      return internalIsEnabled(rootReq);
    return false;
  }
  private boolean internalIsEnabled(TreeNode initialRootReq) {
    Object o = initialRootReq.attrs().getAttributeValue(MultiPart.ATTR_MULTIPART);
    if(o instanceof Boolean)
      return (Boolean) o;
    if(o instanceof ListValue) { 
      return ((ListValue)o).getType() == AttributeType.REFERENCE; //TODO checks?
    }
    if(o instanceof ReferenceValue) { 
      return true; //TODO checks?
    }
    return false;
  }
  
}
