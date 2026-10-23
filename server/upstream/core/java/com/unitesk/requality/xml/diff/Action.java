package com.unitesk.requality.xml.diff;

import org.w3c.dom.Node;

public class Action {
  public static enum ActionType {
    INSERT,
    DELETE
  };

  private ActionType type;
  private Node node;
  private String nodePath;
  private int position;

  public Action(ActionType type, Node node, String path) {
    this.type = type;
    this.node = node;
    this.nodePath = path;
  }

  public Action(ActionType type, Node node, String path, int position) {
    this.type = type;
    this.node = node;
    this.nodePath = path;
    this.position = position;
  }

  public ActionType getType() {
    return type;
  }

  public Node getNode() {
    return node;
  }

  public String getNodePath() {
    return nodePath;
  }

  public void setNodePath(String path) {
    this.nodePath = path;
  }

  public int getNodePosition() {
    return position;
  }

  public void setNodePosition(int p) {
    this.position = p;
  }

  public String toString() {
    String str;
    if (node.getNodeType() == Node.TEXT_NODE)
      str = type.toString() + " " + node.getNodeValue() + " at " + nodePath;
    else str = type.toString() + " " + node.getNodeName() + " at " + nodePath;
    return str;
  }
}
