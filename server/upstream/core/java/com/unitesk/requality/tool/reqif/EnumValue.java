package com.unitesk.requality.tool.reqif;

import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class EnumValue extends ReqIFDefinition implements Comparable<EnumValue> {
  private static String TAG = "ENUM-VALUE", TAG_PROP = "PROPERTIES";

  public EnumValue(Map<String, Pair<String, String>> attributes, EmbeddedValue val) {
    super(attributes);
    this.val = val;
  }

  public EmbeddedValue val;

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node nd = super.save(TAG, par, doc, model);
    if (val != null) {
      Node props = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG_PROP);
      nd.appendChild(props);
      nd.appendChild(doc.createTextNode("\n"));
      val.save(props, doc, model);
    }
    return nd;
  }

  public static EnumValue load(Node node) {
    Map<String, Pair<String, String>> params = ReqIFDefinition.loadAttributes(node);
    EmbeddedValue ev = null;
    Node props = ReqIFModel.getFirstChild(node, TAG_PROP);
    if (props != null) {
      NodeList nl = props.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Node child = nl.item(i);
        if (child.getNodeName() != null && child.getNodeName().startsWith("EMBEDDED"))
          ev = EmbeddedValue.load(child);
      }
    }
    if (ev == null) throw new RuntimeException("Enum value was not found!");
    return new EnumValue(params, ev);
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  @Override
  public int compareTo(EnumValue o) {
    return Integer.compare(sort, o.sort);
  }
}
