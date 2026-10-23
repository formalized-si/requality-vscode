package com.unitesk.requality.core.fi;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;

public interface FIProvider {
  public IInspectionHolder initInspectionTree();
  public IInspectionResult startInspection(TreeDB db);
  public IInspectionResult startInspectionToProject(TreeDB db, TreeDB targdb);
  
  public IInspectionStep addInspectionStep(TreeDB db,TreeDB reqsProj, String desc,
      String state, List<String>reqs, String rule, String question, String prevInsp);
  public default IInspectionStep addInspectionStep(TreeDB db,TreeDB reqsProj, String desc,
      String state, List<String>reqs){
    return addInspectionStep(db,reqsProj,desc,state,reqs,null,null,null);
  }
  public default IInspectionStep addInspectionStep(TreeDB db, String desc,
      String state, List<TreeNode>reqs, String rule, String question, String prevInsp) {
    TreeDB rem = null;
    List<String> ids = new LinkedList<>();
    for(TreeNode r:reqs){
      if(r != null){
        rem = r.getTreeDB();
        ids.add(r.getExternalForeignID());
      }
    }
  return addInspectionStep(db,rem,desc,state,ids,rule,question,prevInsp);
  }
  public default IInspectionStep addInspectionStep(TreeDB db, String desc,
      String state, List<TreeNode>reqs){
    return addInspectionStep(db, desc, state, reqs);
  }
  public List<String> getRelatedNodes(IInspectionStep is);
  public List<IInspectionStep> getInspectionSteps(TreeNode node);
  
  public List<IInspectionStep> getSteps(IInspectionResult ir);
  public List<IInspectionResult> getInspections(TreeDB db);
  public List<Comment> getComments(IInspectionStep is);
  public Map<String, Comment> getCommentsMap(IInspectionResult ir);
  
}
