package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class SpecificationType extends ReqIFDefinition implements EntityWithAttributeDefs{
  protected static String SA = "SPEC-ATTRIBUTES";
  static String TAG = "SPECIFICATION-TYPE";
  private Map<String, AttributeDefinition> attributesMap = new HashMap<>();

  public SpecificationType(Map<String, Pair<String, String>> attrs) {
    super(attrs);
    // TODO Auto-generated constructor stub
  }

  public static SpecificationType load(Node node, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    SpecificationType st = new SpecificationType(attrs);
    Node sa = ReqIFModel.getFirstChild(node, SA);
    if (sa != null) {
      NodeList nl = sa.getChildNodes();
      List<Node> late = new LinkedList<>();
      for (int i = 0; i < nl.getLength(); i++) {
        Node chi = nl.item(i);
        String name = chi.getNodeName();
        if (name != null && name.startsWith("ATTRIBUTE")) {
          if (name.endsWith("ENUMERATION")) late.add(chi);
          else {
            // TODO rec?
            AttributeDefinition ad = AttributeDefinition.load(chi, st, model);
            ad.sort = i;
            st.attributesMap.put(ad.getId(), ad);
          }
        }
      }
      for (Node chi : late) {
        AttributeDefinition ad = AttributeDefinition.load(chi, st, model);
        st.getAttributesMap().put(ad.getId(), ad);
      }
    }
    return st;
  }

  @Override
  public Node save(Node node, Document doc, ReqIFModel model) {
    return super.save(TAG, node, doc, model);
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  @Override
  public AttributeDefinition getAttributeDefinition(String dval) {
    return attributesMap.get(dval);
  }

  @Override
  public Map<String, AttributeDefinition> getAttributesMap() {
    return attributesMap;
  }
}
