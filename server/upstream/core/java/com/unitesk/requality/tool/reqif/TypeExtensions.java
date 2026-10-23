package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class TypeExtensions extends ReqIFDefinition {
  public static String TAG = "TYPE-EXTENSIONS";
  public List<AttributeDefExtension> defs = new LinkedList<>();

  public TypeExtensions() {
    super(Collections.<String, Pair<String, String>>emptyMap());
  }

  @Override
  public String getNameSpacePrefix() {
    return "rm-reqif";
  }

  @Override
  public String generateId() {
    // TODO Auto-generated method stub
    return null;
  }

  public static TypeExtensions load(ReqIFModel model, Node node) {
    if (node == null) return null;
    TypeExtensions te = new TypeExtensions();
    NodeList nl = node.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node n = nl.item(i);
      if (n.getNodeName().contains(AttributeDefExtension.TAG)) {
        AttributeDefExtension ade = AttributeDefExtension.load(model, n);
        if (ade != null) te.register(ade);
      }
    }
    return te;
  }

  private void register(AttributeDefExtension ade) {
    defs.add(ade);
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    for (AttributeDefExtension ade : defs) ade.save(node, doc, model);
    return node;
  }
}
