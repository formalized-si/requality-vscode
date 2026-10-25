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
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.cache.NamesCacheMan;
import com.unitesk.requality.tool.CoreUtils;

public class UVIdProjectChecker implements ITreeChecker{
  public static class UVIdCheckerProblem extends CheckerTreeProblem{
    public UVIdCheckerProblem(TreeNode target, String code, String description, String[] data, TreeNode[]related) {
      super(target, code, description, data, related);
    }
  }
  private static final String ATTR_UVIDCHECKENABLED = "_userVisibleIdCheckerEnabled";
  public Map<String,TreeNode[]> uniqids = new HashMap<>();
  public Set<TreeNode> dupProblems = new HashSet<>();

  public boolean isUnique(TreeNode node, String fid) {
    if(dupProblems == null)
      return true;//TODO
    return !dupProblems.contains(node);
  }
  public UVIdProjectChecker() {}

  public boolean check(TreeDB db) {
	if(!isEnabled(db))
      return false;
    // null type is an error
    {
      Map<String, UUID[]> unid = new HashMap<>();
      NamesCacheMan ncm = db.getCMM().getLinkManipulator().getNCM();
      Map<String, Set<UUID>> map = ncm.getAllNamesRecords();
      
      List<TreeNode> notignored = new LinkedList<>();
      List<TreeNode> checkedIgnoredParents = new LinkedList<>();
      
      Set<UUID> sameParentDuplication = new HashSet<>();
      Set<UUID> sameNameDuplication = new HashSet<>();
      Map<String, String> notIgnoredSimilar = new HashMap<>();
      Map<String, TreeNode[]> notIgnoredSimilarNodes = new HashMap<>();
      Map<UUID, String> sameParentSimilar = new HashMap<>();
      Map<UUID, TreeNode[]> sameParentSimilarNodes = new HashMap<>();

      Map<UUID, String> nodeLabel = new HashMap<>();
      Map<String, List<TreeNode>> ipChildren = new HashMap<>();
      //TODO not work for cloned clones
      for(Entry<String, Set<UUID>> en:map.entrySet()) {
        if(en.getValue()!=null && en.getValue().size()>1) {
          notignored.clear();
          for(UUID uid: en.getValue()) {
            TreeNode node = db.getNode(uid);
            if(node!=null) {
              if(!node.attrs().getBooleanAttributeValue(TreeNode.ATTR_IGNORE_NAME,false)) {
                notignored.add(node);
                nodeLabel.put(uid, node.getQualifiedId());
              }else {
                TreeNode par = node.getShownParent();
                if(par!=null && !checkedIgnoredParents.contains(par)) {
                  UUID parUid = par.getUUId(); 
                  ipChildren.clear();
                  checkedIgnoredParents.add(par);
                  for(TreeNode chi: par.getShownChildren()) {
                    String nid = chi.getNameOrId();
                    List<TreeNode> nds = ipChildren.get(nid);
                    if(nds == null) {
                      nds = new LinkedList<>();
                      ipChildren.put(nid, nds);
                    }
                    nodeLabel.put(chi.getUUId(), chi.getQualifiedId());
                    nds.add(chi);
                  }
                  for(Entry<String, List<TreeNode>> ent:ipChildren.entrySet()) {
                    if(ent.getValue()!=null && ent.getValue().size()>1) {
                      StringBuilder sb = new StringBuilder();
                      for(TreeNode chi:ent.getValue()) {
                        if(sb.length()>0)
                          sb.append(", ");
                        sb.append("'");
                        sb.append(chi.getQualifiedId()); //updates problem-related list
                        sb.append("'");
                        sameParentDuplication.add(chi.getUUId());
                      }
                      sameParentSimilar.put(parUid, sb.toString());
                      sameParentSimilarNodes.put(parUid, 
                    		  ent.getValue().toArray(new TreeNode[ent.getValue().size()]));
                    }
                  }
                }
              }
            }
            if(notignored.size()>1) {
              StringBuilder sb = new StringBuilder();
              for(TreeNode ni: notignored) {
                if(sb.length()>0)
                 sb.append(", ");
                sb.append("'");
                sb.append(ni.getQualifiedId()); //updates problem-related list 
                sb.append("'");
                
                sameNameDuplication.add(ni.getUUId()); //add to nodes with problems
              }
              notIgnoredSimilar.put(en.getKey(), sb.toString());
              notIgnoredSimilarNodes.put(en.getKey(),
                 notignored.toArray(new TreeNode[notignored.size()]));
            }
          }
        }
      }
      Set<UUID> allNodes = new HashSet<>(sameNameDuplication);
      allNodes.addAll(sameParentDuplication);
      
      final String projectName = db.getVisibleName();
      
      allNodes.parallelStream().forEach(uid->{
        TreeNode node = db.getNode(uid);
        if(node.isVirtual())
          return;
        List<IMark> marks = new LinkedList<>();
        ReentrantLock rl = node.getTreeCheckersLock();
        try {
          rl.lock();
          marks.addAll(Arrays.asList(node.getTreeCheckerProblems()));
          String nodeName = node.getName();
          boolean changed = false;
          if(sameNameDuplication.contains(uid)) {
            String sameList = notIgnoredSimilar.get(nodeName);
            TreeNode[] addNodes = notIgnoredSimilarNodes.get(nodeName);
            changed = true;
            if(addNodes == null)
              return;//clones of clones seems to made it
            String[] data = new String[addNodes.length+2];
            data[0] = nodeName;
            data[1] = sameList;
            int i = 2;
            for(TreeNode chi: addNodes)
              data[i++] = chi.getQualifiedId();
            marks.add(new UVIdCheckerProblem(node,"problem_name_duplicated", "The name '%s' is duplicated on nodes: %s",
                 data, addNodes));
          }
          if(sameParentDuplication.contains(uid)) {
            TreeNode sp = node.getShownParent();
            UUID spuid = sp.getUUId();
            nodeName = node.getNameOrId();
            String sameList = sameParentSimilar.get(spuid);
            TreeNode[] addNodes = sameParentSimilarNodes.get(spuid);
            changed = true;
            String[] data = new String[addNodes.length+2];
            data[0] = nodeName;
            data[1] = sameList;
            int i = 2;
            for(TreeNode chi: addNodes)
              data[i] = chi.getQualifiedId();
            marks.add(new UVIdCheckerProblem(node, "problem_name_not_unique_on_parent", "The nameOrId '%s' is not unique for children of the same parent: %s", 
            		data, addNodes));
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
    return db.getRootNode().attrs().getBooleanAttributeValue(ATTR_UVIDCHECKENABLED, true);
  }
  public static void setEnabled(TreeDB db, boolean state) {
    if(db == null) {
      RLogger.logDebug("null db on uvidchecker state change "+CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      return;
    }
	TreeNode root = db.getRootNode();
	root.attrs().setConstAttribute(ATTR_UVIDCHECKENABLED, state, Flag.HIDDEN);
	root.commitAttributes();
  }
}
