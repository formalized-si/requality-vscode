package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class ReadOnlyAttributes extends ReqIFDefinition {
  static String TAG = "READONLY-ATTRIBUTES";
  public List<AttributeDefinition> defs = new LinkedList<>();

  public ReadOnlyAttributes() {
    super(Collections.<String, Pair<String, String>>emptyMap());
  }

  @Override
  public String getNameSpacePrefix() {
    return "doors";
  }

  @Override
  public String generateId() {
    return null;
  }

  public static ReadOnlyAttributes load(ReqIFModel model, Node node) {
    ReadOnlyAttributes roa = new ReadOnlyAttributes();
    NodeList nl = node.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node child = nl.item(i);
      String tag = child.getNodeName();
      if (tag != null && tag.startsWith("ATTRIBUTE-DEFINITION-")) {
        String ref = child.getTextContent();
        AttributeDefinition ad = model.locateAttributeDefinition(ref);
        if (ad != null) roa.defs.add(ad);
      }
    }
    return roa;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    for (AttributeDefinition ad : defs) {
      Node chi =
          ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), ad.getFullName() + "-REF");
      chi.setTextContent(ad.getId());
      node.appendChild(chi);
      node.appendChild(doc.createTextNode("\n"));
    }
    return node;
  }
}
