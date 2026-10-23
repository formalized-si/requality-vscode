package com.unitesk.requality.core.checker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.TermsManipulator;
import com.unitesk.requality.tool.CoreUtils;

public class DefTermProjectChecker implements ITreeChecker{
  public static class DefTermCheckerProblem extends CheckerTreeProblem{
    private String termName;
    public DefTermCheckerProblem(TreeNode target, String checkerMessageId, String termName, String description, String[] addData, TreeNode[]related) {
      super(target,checkerMessageId, description, addData, related);
      this.termName = termName;
    }
    public String getTermName() {
      return termName;
    }
  }
  private static final String ATTR_DEFTERMCHECKENABLED = "_defTermCheckerEnabled";
  public Map<String,TreeNode[]> uniqids = new HashMap<>();
  public Set<TreeNode> dupProblems = new HashSet<>();

  public boolean isUnique(TreeNode node, String fid) {
    if(dupProblems == null)
      return true;//TODO
    return !dupProblems.contains(node);
  }
  public DefTermProjectChecker() {}

  public boolean check(TreeDB db) {
	if(!isEnabled(db))
      return false;
    // null type is an error
    {
      Map<String, TreeNode[]> unid = new HashMap<>();
      TermsManipulator tm = db.getCMM().getTermsManipulator();
      if(tm == null)
        return false;
      List<TreeNode> notignored = new LinkedList<>();
      
      Map<String, TreeNode[]> sameDefMap = new HashMap<>();
      Map<TreeNode, List<Pair<String,TreeNode[]>>> nodeListProblems = new HashMap<>();
      
      Map<String, UUID[]> map = tm.getTCM().getDefMapDuplicants(db);
      
      for(Entry<String, UUID[]> en:map.entrySet()) {
        if(en.getValue()!=null && en.getValue().length>1) {
          notignored.clear();
          int i = 0;
          TreeNode [] nodes = new TreeNode[en.getValue().length];
          for(UUID uid: en.getValue()) 
            nodes[i++] = db.getNode(uid);
          for(TreeNode node: nodes) {
            List<Pair<String, TreeNode[]>> probs = nodeListProblems.get(node);
            if(probs == null) {
              probs = new LinkedList<>();
              nodeListProblems.put(node, probs);
            }
            probs.add(new Pair<>(en.getKey(), nodes));
          }
        }
      }
      
      final String projectName = db.getVisibleName();
      
      nodeListProblems.entrySet().parallelStream().forEach(en->{
        List<IMark> marks = new LinkedList<>();
        TreeNode node = en.getKey();
        ReentrantLock rl = node.getTreeCheckersLock();
        try {
          rl.lock();
          marks.addAll(Arrays.asList(node.getTreeCheckerProblems()));
          String nodeName = node.getName();
          boolean changed = false;
          for(Pair<String, TreeNode[]> probp:en.getValue()) {
            changed = true;

            StringBuilder sb = new StringBuilder();
            for(TreeNode nodea: probp.getSecond()) {
              if(sb.length()>0)
                sb.append(", ");
              sb.append("'");
              sb.append(nodea.getQualifiedId()); //updates problem-related list 
              sb.append("'");
            }
            String[]allData = new String[probp.getSecond().length+3];
            allData[0] = probp.getFirst();
            allData[1] = projectName;
            allData[2] = sb.toString();
            int i = 3;
            for(TreeNode nodea: probp.getSecond())
              allData[i++] = nodea.getQualifiedId();
            marks.add(new DefTermCheckerProblem(node, "problem_term_def_publication", probp.getFirst(), "The term '%s' is defined in several nodes in project '%s'(%s)",allData, probp.getSecond()));
          }

          if(changed)
            node.registerTreeCheckerProblems(marks);
        }finally {
          rl.unlock();
        }
    });
    }
    return true;
  }
  public static boolean isEnabled(TreeDB db) {
    if(db == null) {
      RLogger.logDebug("null db on uvidchecker enable state gathering "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return false;
    }
    return db.getRootNode().attrs().getBooleanAttributeValue(ATTR_DEFTERMCHECKENABLED, true);
  }
  public static void setEnabled(TreeDB db, boolean state) {
    if(db == null) {
      RLogger.logDebug("null db on uvidchecker state change "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return;
    }
	TreeNode root = db.getRootNode();
	root.attrs().setConstAttribute(ATTR_DEFTERMCHECKENABLED, state, Flag.HIDDEN);
	root.commitAttributes();
  }
}
