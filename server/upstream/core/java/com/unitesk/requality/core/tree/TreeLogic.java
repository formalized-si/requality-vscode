package com.unitesk.requality.core.tree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.tika.utils.SystemUtils;

import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TreeLogic implements ITreeLogic {
  private Map<String, Set<String>> linktedNodesForLink = new HashMap<>();
  private Map<String, Map<String, Set<String>>> activeLinkForNode =
      new HashMap<>();
  private Map<String, Set<String>> childmap = new HashMap<>();
  private Map<String, Set<String>> parentmap = new HashMap<>();
  private Set<String> copyable = new TreeSet<>();
  private Set<String> indexByDefault = new TreeSet<>();
  private Set<String> sibpasteavalible = new TreeSet<>();
  private Set<String> autorenamable = new TreeSet<>();
  private Set<String> templateable = new TreeSet<>();

  public TreeLogic() {
    Set<String> childs = new TreeSet<>();
    childs.add(Requirement.TYPE_NAME);
    childs.add(Comment.TYPE_NAME);
    childs.add(Location.TYPE_NAME);
    childs.add(TestPurpose.TYPE_NAME);
    childs.add(VirtualNode.TYPE_NAME);
    childs.add(TextNode.TYPE_NAME);
    childmap.put(Requirement.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add(Comment.TYPE_NAME);
    childmap.put(TestPurpose.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add(ReportFolder.TYPE_NAME);
    childs.add(ReportSettings.TYPE_NAME);
    childs.add(ReportData.TYPE_NAME);
    childmap.put(ReportFolder.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childmap.put(Comment.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childmap.put(ReportSettings.TYPE_NAME, childs);
    childmap.put(ReportData.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childmap.put(Location.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add(Location.TYPE_NAME);
    childmap.put(Document.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add(DocFolder.TYPE_NAME);
    childs.add(Document.TYPE_NAME);
    childmap.put(DocFolder.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add(Comment.TYPE_NAME);
    childs.add(TextNode.TYPE_NAME);
    childmap.put(TextNode.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childmap.put(VirtualNode.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add(VirtualNode.TYPE_NAME);
    //to support VNode changes
    childs.add(Requirement.TYPE_NAME);
    childs.add(TestPurpose.TYPE_NAME);
    childs.add(Comment.TYPE_NAME);
    childmap.put(InstancesFolder.TYPE_NAME, childs);

    childs = new TreeSet<>();
    childs.add("InspectionStep");
    childmap.put("InspectionResult", childs);

    childs = new TreeSet<>();
    childs.add("InspectionResult");
    childmap.put("InspectionsFolder", childs);

    childs = new TreeSet<>();
    childs.add(Comment.TYPE_NAME);
    childmap.put("InspectionStep", childs);

    Set<String> parents = new TreeSet<>();
    parents.add(Requirement.TYPE_NAME);
    parentmap.put(TestPurpose.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(Requirement.TYPE_NAME);
    parentmap.put(Requirement.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(ReportFolder.TYPE_NAME);
    parentmap.put(ReportFolder.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(ReportFolder.TYPE_NAME);
    parentmap.put(ReportData.TYPE_NAME, parents);
    parentmap.put(ReportSettings.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(Document.TYPE_NAME);
    parents.add(Requirement.TYPE_NAME);
    parentmap.put(Location.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(DocFolder.TYPE_NAME);
    parentmap.put(Document.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(DocFolder.TYPE_NAME);
    parentmap.put(DocFolder.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(Requirement.TYPE_NAME);
    parents.add(TestPurpose.TYPE_NAME);
    parents.add("InspectionStep");
    parentmap.put(Comment.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(Requirement.TYPE_NAME);
    parents.add(InstancesFolder.TYPE_NAME);
    parentmap.put(VirtualNode.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parentmap.put(InstancesFolder.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add(TextNode.TYPE_NAME);
    parents.add(Requirement.TYPE_NAME);
    parentmap.put(TextNode.TYPE_NAME, parents);

    parents = new TreeSet<>();
    parents.add("InspectionResult");
    parentmap.put("InspectionStep", parents);

    parents = new TreeSet<>();
    parents.add("InspectionsFolder");
    parentmap.put("InspectionResult", parents);

    parents = new TreeSet<>();
    parentmap.put("InspectionsFolder", parents);

    copyable.add(TextNode.TYPE_NAME);
    copyable.add(Comment.TYPE_NAME);
    copyable.add(ReportData.TYPE_NAME);
    copyable.add(ReportSettings.TYPE_NAME);
    copyable.add(Requirement.TYPE_NAME);
    copyable.add(TestPurpose.TYPE_NAME);
    copyable.add(VirtualNode.TYPE_NAME);

    templateable = new HashSet<>();
    templateable.add(Requirement.TYPE_NAME);
    templateable.add(TextNode.TYPE_NAME);
    templateable.add(Comment.TYPE_NAME);
    templateable.add(ReportSettings.TYPE_NAME);
    templateable.add(TestPurpose.TYPE_NAME);
    templateable.add(VirtualNode.TYPE_NAME);

    sibpasteavalible.add(Comment.TYPE_NAME);
    sibpasteavalible.add(TestPurpose.TYPE_NAME);

    autorenamable.add(Comment.TYPE_NAME);
    autorenamable.add(TextNode.TYPE_NAME);

    indexByDefault.add(TextNode.TYPE_NAME);
    indexByDefault.add(Comment.TYPE_NAME);

    //TODO moveto testdb
    childs = new TreeSet<>();
    childs.add("TestStep");
    childmap.put("TestProcedure", childs);

    childs = new TreeSet<>();
    childs.add("TestProcedureFolder");
    childs.add("TestProcedure");
    childmap.put("TestProcedureFolder", childs);

    childs = new TreeSet<>();
    childmap.put("TestStep", childs);

    parents = new TreeSet<>();
    parents.add("TestProcedureFolder");
    parentmap.put("TestProcedure", parents);

    parents = new TreeSet<>();
    parents.add("TestProcedureFolder");
    parentmap.put("TestProcedureFolder", parents);

    parents = new TreeSet<>();
    parents.add("TestProcedure");
    parentmap.put("TestStep", parents);

    copyable.add("TestStep");
    copyable.add("TestProcedure");
    copyable.add("TestProcedureFolder");
    copyable.add("TODOitem");

    sibpasteavalible.add("TestStep");
    sibpasteavalible.add(Location.TYPE_NAME);

    templateable.add("TestStep");
    templateable.add("TestProcedure");

    autorenamable.add("TestStep");
    indexByDefault.add("TestStep");
  }

  private boolean specialRequirementChildCheck(TreeNode req, String child)
      throws PastingIntoLeafException, ProjectNotLoadedException {
	TreeDB db = req.getTreeDB();
	TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db)) == TreeState.NOT_LOADED)
      throw new ProjectNotLoadedException();
    if (req.getType().equals(Requirement.TYPE_NAME) || req.getType().equals(TextNode.TYPE_NAME)) {
      List<TreeNode>chi = req.getShownChildren();
      boolean havereqs = false, havetps = false;
      for (TreeNode cn:chi) {
        String type = cn.getType();
        if (type.equals(Requirement.TYPE_NAME)) havereqs = true;
        if (type.equals(TestPurpose.TYPE_NAME)) havetps = true;
      }
      if (havereqs && child.equals(TestPurpose.TYPE_NAME)
          || havetps && (child.equals(Requirement.TYPE_NAME))) {
        throw new PastingIntoLeafException();
      }
    }
    return true;
  }

  @Override
  public boolean canBeTemplate(String type) {
    return templateable.contains(type);
  }

  @Override
  public boolean canBeParentOfExc(TreeNode what, TreeNode child)
      throws WrongProjectException, SameIdExistsInChildren, IdEmpty, IdContainsWrongChars, ProjectNotLoadedException {
    if (child == null || what == null) return false; //TODO check WTF
    TreeDB chd = child.getTreeDB(),
    		whd = what.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(chd.getTracker());
    if(tsm.getState(chd.getLocalName(),tsm.getRevisionInfo(chd)) == TreeState.NOT_LOADED)
      throw new ProjectNotLoadedException();
    if(tsm.getState(whd.getLocalName(), tsm.getRevisionInfo(whd)) == TreeState.NOT_LOADED)
      throw new ProjectNotLoadedException();
    if (!what.getTreeDB().equals(child.getTreeDB())
        && what.getTreeDB() != Instantiator.getRootTree(child.getTreeDB()))//TODO test virtual dv
      throw new WrongProjectException();
    try {
      if (child.getType().equals(VirtualNode.TYPE_NAME)
          && (((VirtualNode) child).getTarget() != null
              && !canBeChildOfExc(((VirtualNode) child).getTarget().getType(), what))) return false;
    } catch (PastingIntoLeafException e) {
      return false;
    }

    return this.canBeParentOf(what.getType(), child)
        && this.isChildIdValidExc(what, child, child.getId());
  }

  @Override
  public boolean canBeParentOf(String whatType, TreeNode child) {
    if (child == null) return false;
    TreeDB chd = child.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(chd.getTracker());
    if(tsm.getState(chd.getLocalName(), tsm.getRevisionInfo(chd))
    		== TreeState.NOT_LOADED)
      return false;
    Set<String> types = parentmap.get(child.getType());
    if (types == null) return true;
    return types.contains(whatType);
  }

  @Override
  public boolean canBeChildOfExc(TreeNode what, TreeNode parent)
      throws PastingIntoLeafException, WrongProjectException, SameIdExistsInChildren, IdEmpty,
          IdContainsWrongChars, ProjectNotLoadedException {
    return canBeChildOfExc(what, parent, true);
  }

  @Override
  public boolean canBeChildOfExc(TreeNode what, TreeNode parent, boolean checkid)
      throws PastingIntoLeafException, WrongProjectException, SameIdExistsInChildren, IdEmpty,
          IdContainsWrongChars, ProjectNotLoadedException {
    if (parent == null || what == null) return false; //TODO check WTF
    TreeDB pard = parent.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(pard.getTracker());
    if(tsm.getState(pard.getLocalName(), tsm.getRevisionInfo(pard)) == TreeState.NOT_LOADED)
      throw new ProjectNotLoadedException();
    if (!what.getTreeDB().equals(parent.getTreeDB())//TODO test virtual db
        && !parent.getTreeDB().equals(Instantiator.getRootTree(what.getTreeDB())))
      throw new WrongProjectException();
    if (what.getType().equals(VirtualNode.TYPE_NAME)
        && ((VirtualNode) what).getTarget() != null
        && !canBeChildOfExc(((VirtualNode) what).getTarget().getType(), parent)) return false;
    if(!this.canBeChildOfExc(what.getType(), parent)
        || (checkid &&! this.isChildIdValidExc(parent, what, what.getId())))
      return false;
    return reuseCheck(what,parent);
  }

  private boolean reuseCheck(TreeNode what, TreeNode parent) {
    Set<TreeNode> set = new HashSet<>();
    set.add(parent);
    addRec(new HashSet<>(set), set);
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(what,false,false,true);
    Iterator<TreeNode> it = dftw.iterator();
    while(it.hasNext()){
      TreeNode nde = it.next();
      if(set.contains(nde))
        return false;
    }
    return true;
  }

  private void addRec(Set<TreeNode> it, Set<TreeNode> set) {
    Set<TreeNode> itnext = new HashSet<>();
    for(TreeNode node:it)
    {
      set.add(node);
      TreeNode par = node.getWorkParent();
      if(par!=null)
        itnext.add(par);
      par = node.getCloneParent();
      if(par!=null)
        itnext.add(par);
    }
    if(!itnext.isEmpty())
      addRec(itnext, set);
  }

  @Override
  public boolean canBeChildOfExc(String whatType, TreeNode parent) throws PastingIntoLeafException, ProjectNotLoadedException {
    if (parent == null) return false;
    TreeDB pard = parent.getTreeDB();	
    TreeStateManager tsm = TreeStateManager.getOne(pard.getTracker());
    if(tsm.getState(pard.getLocalName(), tsm.getRevisionInfo(pard)) == TreeState.NOT_LOADED)
      throw new ProjectNotLoadedException();
    if (!specialRequirementChildCheck(parent, whatType)) return false;
    Set<String> types = childmap.get(parent.getType());
    if (types == null) return true;
    return types.contains(whatType);
  }

  public static boolean isIdValidExc(String newId) throws IdEmpty, IdContainsWrongChars {
    if (newId.length() == 0) throw new IdEmpty();
    if (newId.indexOf("/") > -1) throw new IdContainsWrongChars('/');
    return isValidFileName(newId) && newId.length() > 0;
  }

  @Override
  public boolean isIdValidExc(TreeNode node, String newId)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars {
    return isChildIdValidExc(node.getWorkParent(), node, newId);
  }

  @Override
  public boolean isChildIdValidExc(TreeNode parent, TreeNode node, String id)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars {
    if (!isIdValidExc(id)) return false;
    if (id.trim().length() == 0) return false;
    UUID uuid = null;
    if (node != null) uuid = node.getUUId();
    boolean lCCheck = SystemUtils.IS_OS_WINDOWS;
    if(lCCheck){
      for (TreeNode r : parent.getWorkChildren())
        if (r.getId().toLowerCase().equals(id.trim().toLowerCase())&& !r.getUUId().equals(uuid))
          throw new SameIdExistsInChildren();
    }
    else{
      for (TreeNode r : parent.getWorkChildren())
        if (r.getId().equals(id.trim()) && !r.getUUId().equals(uuid))
          throw new SameIdExistsInChildren();
    }
    return true;
  }

  @Override
  public boolean isChildIdValidExc(TreeNode parent, String id)
      throws SameIdExistsInChildren, IdEmpty, IdContainsWrongChars {
    return isChildIdValidExc(parent, null, id);
  }

  private static Pattern filepattern =
      Pattern.compile(
          "# Match a valid Windows filename (unspecified file system).          \n"
              + "^                                # Anchor to start of string.        \n"
              + "(?!                              # Assert filename is not: CON, PRN, \n"
              + "  (?:                            # AUX, NUL, COM1, COM2, COM3, COM4, \n"
              + "    CON|PRN|AUX|NUL|             # COM5, COM6, COM7, COM8, COM9,     \n"
              + "    COM[1-9]|LPT[1-9]            # LPT1, LPT2, LPT3, LPT4, LPT5,     \n"
              + "  )                              # LPT6, LPT7, LPT8, and LPT9...     \n"
              + "  (?:\\.[^.]*)?                  # followed by optional extension    \n"
              + "  $                              # and end of string                 \n"
              + ")                                # End negative lookahead assertion. \n"
              + "([^<>:\"/\\\\|?*\\x00-\\x1F\\ \\xA0]  # First char is not a space.  \n"
              + "[^<>:\"/\\\\|?*\\x00-\\x1F]*)?     # Zero or more valid filename chars.\n"
              + "[^<>:\"/\\\\|?*\\x00-\\x1F\\ \\xA0.]  # Last char is not a space or dot.  \n"
              + "$                                # Anchor to end of string.            ",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.COMMENTS);

  public static Boolean isValidFileName(String name) {
    return filepattern.matcher(name).matches() && !(name.startsWith(" ") || name.endsWith(" "));
  }

  @Override
  public boolean canBeCreatedInExc(String whatType, String newId, TreeNode parent)
      throws PastingIntoLeafException, SameIdExistsInChildren, IdEmpty, IdContainsWrongChars, ProjectNotLoadedException {
    return canBeCreatedInExc(whatType, parent) && isChildIdValidExc(parent, newId);
  }

  @Override
  public boolean canBeCreatedInExc(String whatType, TreeNode parent)
      throws PastingIntoLeafException, ProjectNotLoadedException {
    TreeDB pard = parent.getTreeDB();	
    TreeStateManager tsm = TreeStateManager.getOne(pard.getTracker());
    if(tsm.getState(pard.getLocalName(), tsm.getRevisionInfo(pard)) == TreeState.NOT_LOADED)
      throw new ProjectNotLoadedException();
    if (parent.isVirtual()) return false;
    return canBeChildOfExc(whatType, parent);
  }

  @Override
  public boolean canBeCopiedToExc(TreeNode what, TreeNode newParent, boolean changeId)
      throws PastingIntoLeafException, WrongProjectException, SameIdExistsInChildren, IdEmpty,
          IdContainsWrongChars, ProjectNotLoadedException {
    try {
      TreeDB pard = what.getTreeDB();
      TreeStateManager tsm = TreeStateManager.getOne(pard.getTracker());
      if(tsm.getState(pard.getLocalName(), tsm.getRevisionInfo(pard)) == TreeState.NOT_LOADED)
        throw new ProjectNotLoadedException();
      if (canBeChildOfExc(what, newParent) && canBeParentOfExc(newParent, what))
        if (changeId) {
          return true;
        } else {
          return isChildIdValidExc(newParent, what, what.getId());
        }
      return false;
    } catch (SameIdExistsInChildren sce) {
      if (changeId) return true;
      throw sce;
    }
  }

  @Override
  public Set<String> getPossibleLink(String treeNodeType) {
    Set<String> links = new TreeSet<>();
    Map<String, Set<String>> map = activeLinkForNode.get(treeNodeType);
    if (map == null) return links;
    for (Set<String> list : map.values()) if (list != null) links.addAll(list);
    return links;
  }

  @Override
  public Set<String> getPossibleLinkForAttribute(String treeNodeType, String treeNodeAttribute) {
    Set<String> links = new TreeSet<>();
    Map<String, Set<String>> map = activeLinkForNode.get(treeNodeType);
    if (map == null) return links;
    if (map.get(treeNodeAttribute) == null) return links;
    return map.get(treeNodeAttribute);
  }

  @Override
  public Set<String> getLinkedNodeTypesToLink(String linkToType) {
    return linktedNodesForLink.get(linkToType);
  }

  @Override
  public boolean canBeDeleted(TreeNode node) {
    TreeDB pard = node.getTreeDB();	
    TreeStateManager tsm = TreeStateManager.getOne(pard.getTracker());
    if(tsm.getState(pard.getLocalName(), tsm.getRevisionInfo(pard)) == TreeState.NOT_LOADED)
      return false;
    if(node.getWorkParentUUID() == null) // VSCODE-PATCH: the root node (no work parent) cannot be deleted
      return false;
    if(node.getWorkParentUUID().equals(node.getTreeDB().getRootNodeUUID())){
      String qid = node.getQualifiedId(true);
      if (node.getType().equals(Requirement.TYPE_NAME)
          && qid.equals("/Requirements")) return false;
      if (node.getType().equals(ReportFolder.TYPE_NAME) && qid.equals("/Reports"))
        return false;
      if (node.getType().equals(DocFolder.TYPE_NAME) && qid.equals("/Documents"))
        return false;
    }
    return true;
  }

  @Override
  public boolean isCopyable(TreeNode node) {
    return copyable.contains(node.getType());
  }

  @Override
  public boolean canBeParentOf(TreeNode what, TreeNode child) {
    try {
      return canBeParentOfExc(what, child);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean canBeChildOf(TreeNode what, TreeNode parent) {
    return canBeChildOf(what, parent, true);
  }

  @Override
  public boolean canBeChildOf(TreeNode what, TreeNode parent, boolean checkid) {
    try {
      return canBeChildOfExc(what, parent, checkid);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean canBeChildOf(String whatType, TreeNode parent) {
    try {
      return canBeChildOfExc(whatType, parent);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean isIdValid(TreeNode node, String newId) {
    try {
      return isIdValidExc(node, newId);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean isChildIdValid(TreeNode parent, TreeNode node, String id) {
    try {
      return isChildIdValidExc(parent, node, id);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean isChildIdValid(TreeNode parent, String id) {
    try {
      return isChildIdValidExc(parent, id);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean canBeParentOf(String whatType, String childType) {
    Set<String> types = parentmap.get(childType);
    if (types == null) return true;
    return types.contains(whatType);
  }

  @Override
  public boolean canBeChildOf(String whatType, String parentType) {
    Set<String> types = childmap.get(parentType);
    if (types == null) return true;
    return types.contains(whatType);
  }

  @Override
  public boolean canBeCreatedIn(String whatType, String newId, TreeNode parent) {
    try {
      return canBeCreatedInExc(whatType, newId, parent);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean canBeCopiedTo(TreeNode what, TreeNode newParent, boolean changeId) {
    try {
      return canBeCopiedToExc(what, newParent, changeId);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean canBeCreatedIn(String whatType, TreeNode parent) {
    try {
      return canBeCreatedInExc(whatType, parent);
    } catch (TreeLogicException e) {
    }
    return false;
  }

  @Override
  public boolean isAutoRenamable(String type) {
    if (type == null) return false;
    return autorenamable.contains(type);
  }

  @Override
  public boolean isSiblingPasteAvalible(String nodeType) {
    if (nodeType == null) return false;
    return sibpasteavalible.contains(nodeType);
  }

  public boolean usesIndexByDefault(String type) {
    if (type == null) return false;
    return indexByDefault.contains(type);
  }

  public boolean checkVNodeRecursion(TreeNode treeNode, TreeNode target) {
    String qid = target.getQualifiedId() + "/";
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(treeNode, false, true, false);
    Iterator<TreeNode> it = dftw.iterator();
    while (it.hasNext()) {
      TreeNode node = it.next();
      if (VirtualNode.TYPE_NAME.equals(node.getType())) {
        VirtualNode vn = (VirtualNode) node;
        TreeNode targ = vn.getTarget();
        if (targ != null) {
          if ((targ.getQualifiedId() + "/").contains(qid)) return true;
        }
      }
    }
    return false;
  }
  //TODO rework, add read only attrs
  public boolean changeAllowed(TreeNode node, String key, AttributeDefinition ad, boolean inner) {
    if(inner)
      return true;
    if(ad!=null&&(ad.isReadOnly() && ((ad.getFlags()&Flag.CHANGE_READ_ONLY) != Flag.CHANGE_READ_ONLY)))
      return false;
    if(!key.equals(TreeNode.ATTR_ID))
      return true;//seems to be slightly incorrect
    TreeNode par = node.getWorkParent();
    if(par!=null && par.getType().equals("DBRoot"))
      return false;
    return true;
  }

  public boolean canDeleteAttribute(TreeNode tn, String key, AttributeDefinition ad) {
    if(ad == null)
      return false;
    return changeAllowed(tn, key, ad, false)&&(!key.startsWith("_")||key.equals(TreeNode.ATTR_DESCRIPTION)
        ||key.equals(TreeNode.ATTR_NAME)||key.equals(Requirement.ATTR_LOCATIONS)||key.equals(TreeDB.INDEX_ATTRIBUTE));
  }
}
