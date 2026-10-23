package com.unitesk.requality.core.model;

import java.util.Collections;
import java.util.LinkedList;

public class NodeDescSet {
  public NodeDesc root;
  public LinkedList<NodeDesc> allNodesCreate;
  public LinkedList<NodeDesc> allNodesRemove;
  public static NodeDescSet fromNodeDesc(NodeDesc nd, boolean deep) {
    NodeDescSet nds = new NodeDescSet();
    nds.root = nd;
    if(deep)
      nds.allNodesRemove = NodeDesc.getFullDescSet(nd);
    else {
      nds.allNodesRemove = new LinkedList<NodeDesc>();
      nds.allNodesRemove.add(nd);
    }
    nds.allNodesCreate= new LinkedList<>(nds.allNodesRemove);
    Collections.reverse(nds.allNodesCreate);
    return nds;
  }
}
