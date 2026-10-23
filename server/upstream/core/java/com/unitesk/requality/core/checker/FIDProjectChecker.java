package com.unitesk.requality.core.checker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class FIDProjectChecker implements ITreeChecker{
  public Map<String,TreeNode[]> uniqids = new HashMap<>();
  public Set<TreeNode> dupProblems = new HashSet<>();

  public boolean isUnique(TreeNode node, String fid) {
    if(dupProblems == null)
      return true;//TODO
    return !dupProblems.contains(node);
  }
  public boolean isCorrectFID(TreeNode node, String fid) {
    try {
      Integer.parseInt(fid);
      return true;
    }catch(NumberFormatException nfe) {
    }
    return false;
  }
  public FIDProjectChecker() {}

  public boolean check(TreeDB db) {
    // null type is an error
    String name = FIDUpdater.getStoredFIDNumberName(db);
    if(name!=null)
    {
      Map<String, TreeNode[]> unid = new HashMap<>();
      DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getNode(Requirement.getTypeRootQId()),false,false,true,true,false);
      Iterator<TreeNode> it = dftw.iterator();
      List<IMark> marks = new LinkedList<>();
      Set<String> wasnotunique = new HashSet<>();
      while(it.hasNext()){
        TreeNode node = it.next();
        if (node.isDisposed()
              || node.getType() == null)
          continue;
        boolean wasit = false;
        ReentrantLock rl = node.getTreeCheckersLock();
        try {
          rl.lock();
          marks.clear();
          marks.addAll(Arrays.asList(node.getTreeCheckerProblems()));
          boolean changed = false;
          String fid = node.attrs().getStringAttributeValue(name);
            
          if(fid == null){
                marks.add(new FIDCheckerProblem(node, "problem_sequential_id_not_set", "Sequential id value('%s') is not set on element '%s'", 
                		new String[] {name, node.getUserVisibleName() }, CheckerTreeProblem.NO_RELATED));
                changed = true;      
          }else{
              TreeNode[] was = unid.get(fid);
                if(was==null)
                  unid.put(fid, new TreeNode[]{node});
                else{
                  TreeNode[]unids = unid.get(fid);
                  for(TreeNode nde2:unids){
                    if(nde2.getSUUId().equals(node.getSUUId()))
                      wasit = true;
                  }
                  if(!wasit)
                  {
                    dupProblems.addAll(Arrays.asList(unids));
                    dupProblems.add(node);
                  }
                  if(!wasit){
                    TreeNode[]newunids = new TreeNode[unids.length+1];
                    int ai = 0;
                    for(TreeNode node3:unids)
                      newunids[ai++] = node3;
                    newunids[unids.length]=node;
                    unid.put(fid, newunids);
                  }
                }
                if(!isUnique(node, fid)){
                  if(!wasnotunique.contains(node.getSUUId())){
                    wasnotunique.add(node.getSUUId());

                    TreeNode[]dups = unid.get(fid);
                    
                    StringBuilder sb = new StringBuilder();
                    for(TreeNode nodea: dups) {
                      if(sb.length()>0)
                        sb.append(", ");
                      sb.append("'");
                      sb.append(nodea.getQualifiedId()); //updates problem-related list 
                      sb.append("'");
                    }
                    String[]allData = new String[dups.length+2];
                    allData[0] = name;
                    allData[1] = sb.toString();
                    int i = 2;
                    for(TreeNode nodea: dups)
                      allData[i++] = nodea.getQualifiedId();
                    TreeNode[]nds = unid.get(fid);
                    for(TreeNode nde: nds)
                     if(nde.getSUUId().equals(node.getSUUId())){
                       wasnotunique.add(node.getSUUId());
                       marks.add(new FIDCheckerProblem(node, "problem_sequential_id_not_unique", "Sequential id value('%s') is not unique on elements %s", allData, nds));
                       changed = true;   
                     }else{
                       List<IMark> pr = new LinkedList<>(Arrays.asList(nde.getTreeCheckerProblems()));
                       pr.add(new FIDCheckerProblem(node, "problem_sequential_id_not_unique", "Sequential id value('%s') is not unique on elements %s", allData, nds));
                       nde.registerTreeCheckerProblems(pr);
                     }
                   }
                }
                if(!isCorrectFID(node,fid)){
                  marks.add(new FIDCheckerProblem(node, "problem_sequential_id_non_numerical", "Sequential id value('%s') contains non-numerical value on element '%s'", 
                		  new String[] {name, node.getQualifiedId()}, CheckerTreeProblem.NO_RELATED));
                  changed = true;
                }
                if(moreMaxFID(node,fid)){
                  marks.add(new FIDCheckerProblem(node, "problem_sequential_id_maximum_exceed", "Sequential id value('%s') is more than project maximum on element '%s'",
                		  new String[] {name, node.getQualifiedId(), getMaxFID(node)}, CheckerTreeProblem.NO_RELATED));
                  changed = true;
                }
          }
          if(changed)
           node.registerTreeCheckerProblems(marks);
        }finally {
           rl.unlock();
        }
      }
    }
    return true;
  }
  private String getMaxFID(TreeNode node) {
    return node.getTreeDB().getNode(Requirement.getTypeRootQId()).
      attrs().getStringAttributeValue(FIDUpdater.getFIDMaxName(node.getTreeDB()),"0");
  }
  private boolean moreMaxFID(TreeNode node, String fid) {
    try {
      return Integer.parseInt(fid)>=Integer.parseInt(getMaxFID(node));
    }catch (NumberFormatException nfe) {
      return false;
    }
  }
}
