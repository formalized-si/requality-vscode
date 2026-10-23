package com.unitesk.requality.xml.diff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.xml.diff.Action.ActionType;

public class ActionProcessor {

  private static String CLASS_NAME = ActionProcessor.class.getName();
  private static String PKG_NAME = ActionProcessor.class.getPackage().getName();
  private static Logger log = Logger.getLogger(PKG_NAME);

  public static List<Action> getRequirementMergeActions(
      List<Node> equalNodesList, List<Action> mergeActionList) {
    return new ActionProcessor().filterActionList(equalNodesList, mergeActionList);
  }

  public static List<String> getPersistentReqIdList(
      List<Node> equalNodesList, List<Action> mergeActionList) {
    ActionProcessor ap = new ActionProcessor();
    List<Action> filteredList = ap.filterActionList(equalNodesList, mergeActionList);
    List<String> persistentIds = new ArrayList<>();
    for (int i = 0; i < filteredList.size(); i++) {
      Action a = filteredList.get(i);
      String id = ap.getLocationId(a);
      if (id != null && !persistentIds.contains(id)) persistentIds.add(id);
    }
    return persistentIds;
  }

  public static List<String> getNonPersistentReqIdList(
      List<Node> equalNodesList, List<Action> mergeActionList) {
    ActionProcessor ap = new ActionProcessor();
    List<String> persistentIds = getPersistentReqIdList(equalNodesList, mergeActionList);
    List<String> nonPersistentIds = new ArrayList<>();
    for (int i = 0; i < mergeActionList.size(); i++) {
      Action a = mergeActionList.get(i);
      String id = ap.getLocationId(a);
      if (id != null && !persistentIds.contains(id)) nonPersistentIds.add(id);
    }
    return nonPersistentIds;
  }

  public List<Action> filterActionList(List<Node> nodeList, List<Action> actionList) {
    Map<String, Map<Node, List<Action>>> requirementMapping =
        selectRequirementRelatedActions(new ArrayList<>(actionList));
    List<Action> filteredList = new ArrayList<>();
    Iterator<String> i = requirementMapping.keySet().iterator();
    while (i.hasNext()) {
      String id = i.next();
      Map<Node, List<Action>> mapping = requirementMapping.get(id);
      Iterator<Node> j = mapping.keySet().iterator();
      while (j.hasNext()) {
        List<Action> reqRelatedActions = mapping.get(j.next());
        if (isPersistent(reqRelatedActions))
          for (int k = 0; k < reqRelatedActions.size(); k++)
            filteredList.add(reqRelatedActions.get(k));
      }
    }
    List<Action> result = new ArrayList<>();
    for (int j = 0; j < actionList.size(); j++) {
      Action a = actionList.get(j);
      for (int k = 0; k < filteredList.size(); k++) {
        if (a == filteredList.get(k)) result.add(a);
      }
    }
    return result;
  }

  public static String getLocationText(String id, List<Action> markupActions) {
    ActionProcessor ap = new ActionProcessor();
    Map<String, Map<Node, List<Action>>> requirementMapping =
        ap.selectRequirementRelatedActions(new ArrayList<>(markupActions));
    if (requirementMapping.containsKey(id)) {
      Map<Node, List<Action>> actionMapping = requirementMapping.get(id);
      String locationText = "";
      for (Node parent : actionMapping.keySet()) {
        List<Action> localActions = actionMapping.get(parent);
        Action spanAction = localActions.get(0);
        ActionType spanActionType = spanAction.getType();
        for (Action a : localActions) {
          if (a.getType() == spanActionType && a.getNode().getNodeType() == Node.TEXT_NODE) {
            String parentPath = Util.getParentPath(a.getNodePath());
            if (parentPath.equals(Util.getPath(spanAction.getNode()))) {
              locationText = locationText.concat(a.getNode().getNodeValue());
            }
          }
        }
      }
      return locationText;
    }
    return "";
  }

  private boolean isPersistent(List<Action> actionList) {
    Action spanAction = actionList.get(0);
    ActionType spanActionType = spanAction.getType();
    for (Action a : actionList) {
      if (a.getType() != spanActionType && a.getNode().getNodeType() == Node.TEXT_NODE)
        for (Action b : actionList)
          if (b.getType() == spanActionType
              && b.getNode().getNodeType() == Node.TEXT_NODE
              && a.getNode().getNodeValue().contains(b.getNode().getNodeValue())) return true;
    }
    return false;
  }

  private String getLocationId(Action a) {
    Node x = a.getNode();
    if (x.hasAttributes()) {
      Node attribute = x.getAttributes().getNamedItem("class");
      if (attribute != null) {
        String id = attribute.getNodeValue();
        if (id.contains("requality_text")) {
          id = id.substring(id.indexOf("id_") + 3);
          return id;
        }
      }
    }
    return null;
  }

  private List<Action> getRelatedActions(Action a, List<Action> actionList) {
    List<Action> relatedList = new ArrayList<>();
    Node x = a.getNode();
    for (int i = 0; i < actionList.size(); i++) {
      Action b = actionList.get(i);
      Node y = b.getNode();
      String parentPath = Util.getParentPath(b.getNodePath());
      if (parentPath.equals(Util.getPath(a.getNode()))) {
        relatedList.add(b);
      }
      if (y.isSameNode(x.getNextSibling()) || y.isSameNode(x.getPreviousSibling())) {
        relatedList.add(b);
      }
    }
    return relatedList;
  }

  private Action getReplacingAction(Action a, List<Action> actionList) {
    Node span = a.getNode();
    NodeList childs = span.getChildNodes();
    String locationText = "";
    for (int i = 0; i < childs.getLength(); i++) {
      Node child = childs.item(i);
      if (child.getNodeType() == Node.TEXT_NODE && !child.getNodeValue().trim().isEmpty()) {
        locationText = child.getNodeValue();
      }
    }
    if (locationText.isEmpty()) {
      log.severe("Location text not found");
      return null;
    }
    for (int i = 0; i < actionList.size(); i++) {
      Action b = actionList.get(i);
      Node x = b.getNode();
      if (x.getNodeType() == Node.TEXT_NODE
          && x.getNodeValue().contains(locationText)
          && b.getType() != a.getType()) return b;
    }
    log.info("Corresponding delete action not found for " + a);
    return null;
  }

  private List<Action> fixContext(Action replace, List<Action> actionList) {
    String parentContext = Util.getParentPath(replace.getNodePath());
    actionList.get(0).setNodePath(Util.switchContext(parentContext, actionList.get(0)));
    parentContext = actionList.get(0).getNodePath();
    for (int i = 1; i < actionList.size(); i++) {
      actionList.get(i).setNodePath(Util.switchContext(parentContext, actionList.get(i)));
    }
    return actionList;
  }

  private Map<String, Map<Node, List<Action>>> selectRequirementRelatedActions(
      List<Action> actionList) {
    log.entering(CLASS_NAME, "selectRequirementRelatedActions()", actionList);
    Map<String, Map<Node, List<Action>>> reqMapping =
        new HashMap<>();
    try {
      for (int i = 0; i < actionList.size(); i++) {
        Action a = actionList.get(i);
        Node x = a.getNode();
        String id = getLocationId(a);
        if (id == null) continue;
        Map<Node, List<Action>> reqChunksMapping;
        if (reqMapping.containsKey(id)) reqChunksMapping = reqMapping.get(id);
        else reqChunksMapping = new HashMap<>();
        List<Action> reqRelatedActionList = new ArrayList<>();
        reqRelatedActionList.add(a);
        reqRelatedActionList.addAll(getRelatedActions(a, actionList));
        Action replace = getReplacingAction(a, actionList.subList(i + 1, actionList.size() - 1));
        if (replace != null) {
          reqRelatedActionList = fixContext(replace, reqRelatedActionList);
          reqRelatedActionList.add(replace);
        }
        if (reqChunksMapping.containsKey(x.getParentNode())) {
          List<Action> relatedActionList = reqChunksMapping.get(x.getParentNode());
          relatedActionList.addAll(reqRelatedActionList);
          reqChunksMapping.put(x.getParentNode(), relatedActionList);
        } else reqChunksMapping.put(x.getParentNode(), reqRelatedActionList);
        reqMapping.put(id, reqChunksMapping);
      }
      return reqMapping;
    } finally {
      log.exiting(CLASS_NAME, "selectRequirementRelatedActions()", reqMapping);
    }
  }

  public static Node rebuildTree(Node tree, List<Action> actionList)
      throws ParserConfigurationException {
    Object[] args = {tree, actionList};
    log.entering(CLASS_NAME, "rebuildTree()", args);
    try {
      List<Node> orderedTree = Util.linearizeTree(tree);
      for (int i = 0; i < actionList.size(); i++) {
        Action a = actionList.get(i);
        String parentPath = Util.getParentPath(a.getNodePath());
        Node x = a.getNode();
        for (int j = 0; j < orderedTree.size(); j++) {
          Node current = orderedTree.get(j);
          switch (a.getType()) {
            case DELETE:
              if (x.isSameNode(current)) {
                current.getParentNode().removeChild(current);
                orderedTree = Util.linearizeTree(tree);
              }
              break;
            case INSERT:
              if (current.getParentNode() != null && parentPath.equals(Util.getPath(current))) {
                Node newNode = current.getOwnerDocument().importNode(x, false);
                current.insertBefore(newNode, current.getChildNodes().item(a.getNodePosition()));
                orderedTree = Util.linearizeTree(tree);
                for (int k = i + 1; k < actionList.size(); k++) {
                  Action b = actionList.get(k);
                  String path = Util.getParentPath(b.getNodePath());
                  if (parentPath.equals(path)) b.setNodePosition(b.getNodePosition() + 1);
                }
              }
              break;
          }
        }
      }
    } finally {
      log.exiting(CLASS_NAME, "rebuildTree()", tree);
    }
    return tree;
  }
}
