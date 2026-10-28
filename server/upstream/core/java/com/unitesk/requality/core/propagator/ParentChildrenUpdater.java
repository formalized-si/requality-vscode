package com.unitesk.requality.core.propagator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.VirtualNode;

public class ParentChildrenUpdater extends StructChangePropogator {

  @Override
  public int getPriority() {
    return LOW;
  }
  private static Set<String>BY_IND = new HashSet<>(),
                            BY_LOC = new HashSet<>(),
                            BY_NAMES = new HashSet<>();
  static{
    // VSCODE-PATCH: Location references affect sibling order only for BY_LOCATIONS. Re-sorting the whole parent
    // for every reference added or removed makes bulk specification updates quadratic even in name/index order.
    BY_IND.add(TreeDB.INDEX_ATTRIBUTE);
    BY_IND.add(TreeNode.ATTR_NAMEORID);
    BY_IND.add(TreeNode.ATTR_PREDICATE);
    BY_IND.add(VirtualNode.ATTR_COLLAPSEVNODE);

    BY_LOC.add(TreeNode.ATTR_NAMEORID);
    BY_LOC.add(TreeNode.ATTR_PREDICATE);
    BY_LOC.add(Requirement.ATTR_LOCATIONS);
    BY_LOC.add(VirtualNode.ATTR_COLLAPSEVNODE);
    
    BY_NAMES.add(TreeNode.ATTR_NAMEORID);
    BY_NAMES.add(TreeNode.ATTR_PREDICATE);
    BY_NAMES.add(VirtualNode.ATTR_COLLAPSEVNODE);
    
    BY_IND = Collections.unmodifiableSet(BY_IND);
    BY_LOC = Collections.unmodifiableSet(BY_LOC);
    BY_NAMES = Collections.unmodifiableSet(BY_NAMES);
  }
  @Override
  public boolean shouldBeApplied(TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    if(m == Moment.BEFORE)
      return false;
    SortingSettings ss = node.getTreeDB().getSortingSettings();
    switch(ss.getMethod()){
      case BY_INDEX:{
        Set<String> al = new HashSet<>(cs.getAll());
        al.retainAll(BY_IND);
        return !al.isEmpty();
      }
      case BY_LOCATIONS:{
        Set<String> al = new HashSet<>(cs.getAll());
        al.retainAll(BY_LOC);
        return !al.isEmpty();
      }
      case BY_NAMES:{
        Set<String> al = new HashSet<>(cs.getAll());
        al.retainAll(BY_NAMES);
        return !al.isEmpty();
      }
    }
    return false;
  }

  @Override
  public boolean apply(TreeNode node, ChangesSet<Attribute> cs, Moment m) {
    TreeNode par = node.getShownParent();
    if(par!=null){
      par.updateLocalAttribute(TreeNode.ATTR_SHOWN_CHILDREN);//TODO fix commit only on changes
      par.commitAttributes();
    }
    return true;
  }
  private static ParentChildrenUpdater one = new ParentChildrenUpdater();
  public static ParentChildrenUpdater getOne(){
    return one;
  }

  @Override
  public void propagatorInited(TreeNode node) {
    // node tends to be on proper place
  }

}
