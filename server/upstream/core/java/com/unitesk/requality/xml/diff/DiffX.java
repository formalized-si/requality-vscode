package com.unitesk.requality.xml.diff;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.w3c.dom.Node;
import com.unitesk.requality.xml.diff.Action.ActionType;

public class DiffX {
  private static String CLASS_NAME = DiffX.class.getName();
  private static String PKG_NAME = DiffX.class.getPackage().getName();
  private static Logger log = Logger.getLogger(PKG_NAME);

  private Map<Node, Node> mapping(Node tree1, Node tree2) {
    Object[] args = {tree1, tree2};
    log.entering(CLASS_NAME, "mapping()", args);
    Map<Node, Node> map = new IdentityHashMap<>();
    try {
      List<Node> orderedNodes1 = Util.linearizeTree(tree1),
          orderedNodes2 = Util.linearizeTree(tree2);
      for (int i = 0; i < orderedNodes1.size(); i++) {
        Node x = orderedNodes1.get(i);
        if (map.containsKey(x)) continue;
        List<Node> y = new ArrayList<>();
        for (int j = 0; j < orderedNodes2.size(); j++)
          if (Util.nodeEquals(x, orderedNodes2.get(j))) y.add(orderedNodes2.get(j));
        Map<Node, Node> map2 = new IdentityHashMap<>();
        for (int k = 0; k < y.size(); k++) {
          if (!map.containsValue(y.get(k))) {
            Map<Node, Node> map1 = matchFragment(x, y.get(k), map);
            if (map1.size() > map2.size()) map2 = map1;
          }
        }
        map.putAll(map2);
      }
      return map;
    } finally {
      log.exiting(CLASS_NAME, "mapping()", map);
    }
  }

  private Map<Node, Node> matchFragment(Node x, Node y, Map<Node, Node> map) {
    Object[] args = {x, y, map};
    log.entering(CLASS_NAME, "matchFragment()", args);
    Map<Node, Node> map1 = new IdentityHashMap<>();
    try {
      List<Node> orderedNodes1 = Util.linearizeTree(x), orderedNodes2 = Util.linearizeTree(y);
      int min_childs;
      if (orderedNodes1.size() > orderedNodes2.size()) min_childs = orderedNodes2.size();
      else min_childs = orderedNodes1.size();
      for (int i = 0; i < min_childs; i++) {
        x = orderedNodes1.get(i);
        y = orderedNodes2.get(i);
        if (!map.containsKey(x) && !map.containsValue(y) && Util.nodeEquals(x, y)) map1.put(x, y);
      }
      return map1;
    } finally {
      log.exiting(CLASS_NAME, "matchFragment()", map1);
    }
  }

  private List<Action> createActionsList(Node tree1, Node tree2, Map<Node, Node> map) {
    Object[] args = {tree1, tree2, map};
    log.entering(CLASS_NAME, "matchFragment()", args);
    List<Action> actionList = new ArrayList<>();
    try {
      List<Node> orderedNodes1 = Util.linearizeTree(tree1),
          orderedNodes2 = Util.linearizeTree(tree2);
      for (int i = 0; i < orderedNodes2.size(); i++) {
        Node y = orderedNodes2.get(i);
        if (!map.containsValue(y)) {
          actionList.add(new Action(ActionType.INSERT, y, Util.getPath(y), Util.getPosition(y)));
        } else {
          Iterator<Node> keys = map.keySet().iterator();
          Node x = null;
          while (keys.hasNext()) {
            Node current_key = keys.next();
            if (map.get(current_key).isSameNode(y)) {
              x = current_key;
              break;
            }
          }
          if (x!=null){
            if (x.isSameNode(tree1) || y.isSameNode(tree2)) continue;
            if (!map.containsKey(x.getParentNode()) && !map.containsValue(y.getParentNode())) {
              actionList.add(new Action(ActionType.DELETE, x, Util.getPath(x)));
              actionList.add(new Action(ActionType.INSERT, x, Util.getPath(y)));
            } else if (Util.getPosition(x) != Util.getPosition(y)
                || !Util.nodeEquals(x.getParentNode(), y.getParentNode())) {
              actionList.add(new Action(ActionType.DELETE, x, Util.getPath(x)));
              actionList.add(new Action(ActionType.INSERT, x, Util.getPath(y)));
            }
          }
        }
      }
      for (int j = 0; j < orderedNodes1.size(); j++) {
        Node x = orderedNodes1.get(j);
        if (!map.containsKey(x)) actionList.add(new Action(ActionType.DELETE, x, Util.getPath(x)));
      }
      return actionList;
    } finally {
      log.exiting(CLASS_NAME, "createActionsList()", actionList);
    }
  }

  public static List<Action> getActionsList(Node tree1, Node tree2) {
    DiffX diffx = new DiffX();
    Map<Node, Node> map = diffx.mapping(tree1, tree2);
    return diffx.createActionsList(tree1, tree2, map);
  }

  public static List<Node> getEqualNodes(Node tree1, Node tree2) {
    DiffX diffx = new DiffX();
    Map<Node, Node> map = diffx.mapping(tree1, tree2);
    List<Node> nodeList = new ArrayList<>();
    Iterator<Node> i = map.keySet().iterator();
    while (i.hasNext()) nodeList.add(i.next());
    return nodeList;
  }
}
