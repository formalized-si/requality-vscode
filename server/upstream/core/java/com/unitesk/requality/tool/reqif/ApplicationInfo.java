package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class ApplicationInfo extends ReqIFDefinition {
  public static String KEY_VERSION = "VERSION",
      KEY_DOORSREQIFVERSION = "DOORS-REQ-IF-VERSION",
      KEY_BUILDNUMBER = "BUILD-NUMBER",
      KEY_BUILDDATE = "BUILD-DATE";
  public static String TAG = "APPLICATION-INFO";

  @Override
  public String getNameSpacePrefix() {
    return "doors";
  }

  public ApplicationInfo() {
    super(Collections.<String, Pair<String, String>>emptyMap());
  }

  public Map<String, String> map = new HashMap<>();

  public static ApplicationInfo load(Node node) {
    ApplicationInfo head = new ApplicationInfo();
    NodeList nl = node.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      String name = nl.item(i).getNodeName();
      if (name != null && !name.startsWith("#")) {
        if (name.contains(":")) name = name.substring(name.indexOf(":") + 1);
        head.map.put(name, nl.item(i).getTextContent());
      }
    }
    return head;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    for (String s : map.keySet()) {
      if (s.startsWith("#")) continue;
      Element kwp =
          ReqIFDefinition.createElement(doc, getNamespace(model), getNameSpacePrefix() + ":" + s);
      kwp.setTextContent(map.get(s));
      node.appendChild(kwp);
      node.appendChild(doc.createTextNode("\n"));
    }
    return node;
  }

  @Override
  public String generateId() {
    return null;
  }
}
