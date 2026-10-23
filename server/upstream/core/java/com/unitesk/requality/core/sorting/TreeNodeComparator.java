package com.unitesk.requality.core.sorting;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.CoreUtils;

public class TreeNodeComparator {
  private static Map<SortingSettings, Comparator<TreeNode>> comparators =
      new HashMap<>();
  private static Map<SortingSettings, Comparator<TreeNode>> gcomparators =
      new HashMap<>();
  
  public static Comparator<TreeNode> getGlobalComparator(TreeDB db) {
    if (db.isVirtual()) db = Instantiator.getRootTree(db); // fix
    // TODO think about it
    SortingSettings st = db.getSortingSettings();
    return getComparator(st);
  }
  
  public static Comparator<TreeNode> getComparator(TreeDB db) {
    if (db.isVirtual()) 
      db = Instantiator.getRootTree(db); // fix
    // TODO think about it
    SortingSettings st = db.getSortingSettings();
    return getComparator(st);
  }

  private static Map<String, Integer> order = new HashMap<>();

  static {
    order.put(DocFolder.TYPE_NAME, 1);
    order.put(InstancesFolder.TYPE_NAME, 2);
    order.put(ReportFolder.TYPE_NAME, 3);
    order.put(Requirement.TYPE_NAME, 4);
    order.put("TestProcedure", 5);
  }

  public static Comparator<TreeNode> getComparator(final SortingSettings settings) {
    if (!comparators.containsKey(settings)) {
      comparators.put(
          settings,
          new Comparator<TreeNode>() {
            @Override
            public int compare(TreeNode n1, TreeNode n2) {
              if (n1 == null) return -1;
              if (n2 == null) return 1;
              if (n1.getUUId().equals(n2.getUUId())) return 0;
              if (n1.isRoot() && n2.isRoot()) {
                int i = n1.getTreeDB().getProjectName().compareTo(n2.getTreeDB().getProjectName());
                if(i==0)
                	return n1.getTreeDB().getLocalName().compareTo(n2.getTreeDB().getLocalName());
                return i;
              }
              if (n1.getType() == null) return -1;
              if (n2.getType() == null) return 1;
              TreeNode wp1 = n1.getWorkParent(), wp2=n2.getWorkParent(); 
              if (wp1 != null
                  && wp2 != null) {
                if(wp1.isRoot()
                    && wp2.isRoot()){
                  Integer i1 = order.get(n1.getType()), i2 = order.get(n2.getType());
                  if (i1 != null && i2 != null)
                    if (i1 != i2) return i1.compareTo(i2);
                    else ;
                  else if (i1 != null) return -1;
                  else if (i2 != null) return 1;
                }else{
                  if(!wp1.getSUUId().equals(wp2.getSUUId())){//different parents
                    LinkedList<TreeNode> slist = new LinkedList<>();
                    while(wp1!=null){
                      slist.addFirst(wp1);
                      wp1 = wp1.getWorkParent();
                    }
                    LinkedList<TreeNode> slist2 = new LinkedList<>();
                    while(wp2!=null){
                      slist2.addFirst(wp2);
                      wp2 = wp2.getWorkParent();
                    }
                    slist.addLast(n1);
                    slist2.addLast(n2);
                    Iterator<TreeNode> it1=slist.iterator(), it2=slist2.iterator();
                    TreeNode na1 = it1.next(),na2 = it2.next();
                    while(it1.hasNext() && it2.hasNext() && na1.getSUUId().equals(na2.getSUUId()))
                    {
                      na1 = it1.next();na2 = it2.next();
                    }
                    if(na1.getSUUId().equals(na2.getSUUId())){
                      if(it1.hasNext())
                        return 1;
                      if(it2.hasNext())
                        return -1;
                    }
                    return compare(na1, na2);
                  }
                }
              }
              if (settings !=null && settings.getSeparateByTypesFlag()
                  && (n1.getType() != null)
                  && (n2.getType() != null)
                  && (n1.getType().compareTo(n2.getType()) != 0)) {
                return n1.getType().compareTo(n2.getType());
              }
              if (n1.getType().equals(Location.TYPE_NAME)
                  && n2.getType().equals(Location.TYPE_NAME)) {
                int li = ((Location) n1).compareIndexTo((Location) n2);
                if (li != 0) return li;
              } // fix to sort text nodes in proper way
              if (settings !=null && settings.getMethod() == SortingMethod.BY_LOCATIONS
                  && mayHaveLocations(n1)
                  && mayHaveLocations(n2)) {
                if (!(n1 instanceof Requirement) || !(n2 instanceof Requirement))
                  RLogger.logInfo("error type loading! " + n1.toString() + " " + n2.toString());
                else {
                  int ci = ((Requirement) n1).compareLocationIndexes((Requirement) n2);
                  if (ci != 0) return ci;
                }
              }
              int k = 0;
              if (settings!=null && (settings.getMethod() == SortingMethod.BY_LOCATIONS // &&
                  // n1.getTreeDB().getTreeLogic().usesIndexByDefault(n1.getType())
                  || settings.getMethod() == SortingMethod.BY_INDEX))
                k = n1.getIndex().compareTo(n2.getIndex());
              if (k == 0) {
                String s1 = n1.getNameOrId(), s2 = n2.getNameOrId();
                int compNames = CoreUtils.compareNumberedStrings(s1, s2);
                if(compNames == 0)
                  return CoreUtils.compareNumberedStrings(n1.getId(), n2.getId());
                return compNames;
              }
              return k;
            }

            private boolean mayHaveLocations(TreeNode n1) {
              return Requirement.TYPE_NAME.equals(n1.getType())
                  || TextNode.TYPE_NAME.equals(n1.getType());
            }
          });
    }
    return comparators.get(settings);
  }
  public static Comparator<TreeNode> getGComparator(final SortingSettings settings) {
    if (!gcomparators.containsKey(settings)) {
      gcomparators.put(
          settings,
          new Comparator<TreeNode>() {
            @Override
            public int compare(TreeNode n1, TreeNode n2) {
              if (n1 == null) return -1;
              if (n2 == null) return 1;
              if (n1.getUUId().equals(n2.getUUId())) return 0;
              TreeNode wp1=n1.getWorkParent(), wp2 = n2.getWorkParent();
              if(wp1!=null && wp2!=null && !wp1.getUUId().equals(wp2.getUUId())){
                int d1 = 0, d2 = 0;
                while(wp1!=null){
                  d1++;
                  wp1 = wp1.getWorkParent();
                }
                while(wp2!=null){
                  d2++;
                  wp2 = wp2.getWorkParent();
                }
                wp1 = n1.getWorkParent();
                wp2 = n2.getWorkParent();
                boolean isp = true;
                while (d1 != d2){
                  if (d1 > d2){
                    n1 = wp1;
                    wp1 = n1.getWorkParent();
                    d1--;
                  }else{
                    isp = false;
                    n2 = wp2;
                    wp2 = n2.getWorkParent();
                    d2--;
                  }
                }
                if(wp1!=null&&wp2!=null)
                  while(wp1!=null&&wp2!=null&&!wp1.getUUId().equals(wp2.getUUId())){
                    n1 = wp1;
                    wp1 = n1.getWorkParent();
                    n2 = wp2;
                    wp2 = n2.getWorkParent();
                  }
                if(n1.getUUId().equals(n2.getUUId()))//special case when we compare node and one of its successors
                  return isp?1:-1;//if n2 is parent of n1 then ret -1 else 1
              }
              return getComparator(settings).compare(n1, n2);
            }

            private boolean mayHaveLocations(TreeNode n1) {
              return Requirement.TYPE_NAME.equals(n1.getType())
                  || TextNode.TYPE_NAME.equals(n1.getType());
            }
          });
    }
    return gcomparators.get(settings);
  }
  
}
