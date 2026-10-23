package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class ReqIFHeader extends ReqIFDefinition {
  public static String KEY_TIME = "CREATION-TIME",
      KEY_REPID = "REPOSITORY-ID",
      KEY_REQTOOLID = "REQ-IF-TOOL-ID",
      KEY_TOOLVERSION = "REQ-IF-VERSION",
      KEY_SRCTOOLID = "SOURCE-TOOL-ID",
      KEY_TITLE = "TITLE";
  private static String TAG = "REQ-IF-HEADER";

  public ReqIFHeader(Map<String, Pair<String, String>> attrs) {
    super(attrs);
  }

  public Map<String, String> map = new HashMap<>();

  public static ReqIFHeader load(Node node) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    ReqIFHeader head = new ReqIFHeader(attrs);
    NodeList nl = node.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      String name = nl.item(i).getNodeName();
      if (name != null && !name.startsWith("#")) head.map.put(name, nl.item(i).getTextContent());
    }
    return head;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    for (String s : map.keySet()) {
      Element kwp = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), s);
      kwp.setTextContent(map.get(s));
      node.appendChild(kwp);
      node.appendChild(doc.createTextNode("\n"));
    }
    return node;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }
}
