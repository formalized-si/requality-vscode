package com.unitesk.requality.xml.diff;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Util {

  public static boolean nodeEquals(Node node1, Node node2) {
    if (node1.getNodeType() != node2.getNodeType()) return false;
    if (node1.getNodeType() == Node.TEXT_NODE
        && !node1.getNodeValue().trim().equals(node2.getNodeValue().trim())) return false;
    if (!node1.getNodeName().equals(node2.getNodeName())) return false;
    if ((node1.hasAttributes() && !node2.hasAttributes())
        || (!node1.hasAttributes() && node2.hasAttributes())) return false;
    if (node1.hasAttributes()) {
      NamedNodeMap map1 = node1.getAttributes(), map2 = node2.getAttributes();
      if (map1.getLength() != map2.getLength()) return false;
      for (int j = 0; j < map1.getLength(); j++) {
        if (!map1.item(j).getNodeName().equals(map2.item(j).getNodeName())) return false;
        if (!map1.item(j).getNodeValue().equals(map2.item(j).getNodeValue())) return false;
      }
    }
    return true;
  }

  public static List<Node> linearizeTree(Node tree) {
    List<Node> orderedNodes = new ArrayList<>();
    orderedNodes.add(tree);
    for (int i = 0; i < orderedNodes.size(); i++) {
      if (!orderedNodes.get(i).hasChildNodes()) continue;
      for (Node x = orderedNodes.get(i).getFirstChild();
          x.getNextSibling() != null;
          x = x.getNextSibling()) {
        orderedNodes.add(x);
      }
      orderedNodes.add(orderedNodes.get(i).getLastChild());
    }
    return orderedNodes;
  }

  public static String getPath(Node node) {
    String path = "";
    if (node.getParentNode() == null) return path;
    NodeList siblings = node.getParentNode().getChildNodes();
    int counter = 1;
    for (int i = 0; i < siblings.getLength(); i++) {
      if (siblings.item(i).isSameNode(node)) break;
      if (siblings.item(i).getNodeName().equals(node.getNodeName())) counter++;
    }
    if (node.getNodeType() == Node.TEXT_NODE)
      path = "/".concat("text()" + "[" + counter + "]").concat(path);
    else path = "/".concat((node.getNodeName() + "[" + counter + "]")).concat(path);
    path = getPath(node.getParentNode()).concat(path);
    return path;
  }

  public static int getPosition(Node node) {
    if (node.getParentNode() == null) return 1;
    int position = 1;
    while (node.getPreviousSibling() != null) {
      node = node.getPreviousSibling();
      position++;
    }
    return position;
  }

  public static int getDepth(Action a) {
    String path = a.getNodePath();
    int counter = 0;
    while (path.indexOf("/") != -1) {
      path = path.substring(path.indexOf("/") + 1);
      counter++;
    }
    return counter;
  }

  public static int getDepth(String path) {
    int counter = 0;
    while (path.indexOf("/") != -1) {
      path = path.substring(path.indexOf("/") + 1);
      counter++;
    }
    return counter;
  }

  public static String getParentPath(String path) {
    path = path.substring(0, path.lastIndexOf("/"));
    return path;
  }

  public static boolean isAscendant(Node x, Node y) {
    while (y.getParentNode() != null) {
      if (nodeEquals(x, y.getParentNode())) return true;
      y = y.getParentNode();
    }
    return false;
  }

  public static String switchContext(String context, Action a) {
    int contextDepth = getDepth(context);
    int aDepth = getDepth(a);
    String aPath = a.getNodePath();
    if (!aPath.contains(context)) {
      int switchDepth = aDepth - contextDepth;
      if (switchDepth > 0) {
        for (int i = 0; i < contextDepth + 1; i++) aPath = aPath.substring(aPath.indexOf("/") + 1);
        aPath = context.concat("/".concat(aPath));
      }
    }
    return aPath;
  }
}
