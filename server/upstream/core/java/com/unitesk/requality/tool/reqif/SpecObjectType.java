package com.unitesk.requality.tool.reqif;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class SpecObjectType extends ReqIFDefinition implements EntityWithAttributeDefs{
  private static String TAG = "SPEC-OBJECT-TYPE";
  protected static String SA = "SPEC-ATTRIBUTES";
  
  private Map<String, AttributeDefinition> attributesMap = new HashMap<>();

  public SpecObjectType(Map<String, Pair<String, String>> attrs) {
    super(attrs);
  }

  public void addAttributeDefinition(AttributeDefinition def) {
    getAttributesMap().put(def.getId(), def);
  }

  public AttributeDefinition getAttributeDefinition(String id) {
    return getAttributesMap().get(id);
  }

  public AttributeDefinition[] getAttributeDefinitionByName(String name) {
    Set<AttributeDefinition> ads = new HashSet<>();
    for (AttributeDefinition ad : getAttributesMap().values()) {
      // TODO multivalues
      if (ad.getName() != null && ad.getName().equals(name)) ads.add(ad);
    }
    return ads.toArray(new AttributeDefinition[0]);
  }

  public static SpecObjectType load(Node nd, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(nd);
    SpecObjectType sot = new SpecObjectType(attrs);
    Node sa = ReqIFModel.getFirstChild(nd, SA);
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
            AttributeDefinition ad = AttributeDefinition.load(chi, sot, model);
            ad.sort = i;
            sot.getAttributesMap().put(ad.getId(), ad);
          }
        }
      }
      for (Node chi : late) {
        AttributeDefinition ad = AttributeDefinition.load(chi, sot, model);
        sot.getAttributesMap().put(ad.getId(), ad);
      }
    }
    return sot;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    if (!getAttributesMap().isEmpty()) {
      Element sa = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SA);
      node.appendChild(sa);
      node.appendChild(doc.createTextNode("\n"));
      for (AttributeDefinition ad : getDefinitions()) ad.save(sa, doc, model);
    }
    return node;
  }

  public Collection<AttributeDefinition> getDefinitions() {
    return ReqIFModel.getSorted(attributesMap.values());
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  public Set<AttributeDefinition> getAttributeDefinitions() {
    return new HashSet<>(attributesMap.values());
  }

  public String getNameOrId() {
    String name = getName();
    if (name != null && !name.isEmpty()) return name;
    return getId();
  }

  @Override
  public Map<String, AttributeDefinition> getAttributesMap() {
    return attributesMap;
  }
}
