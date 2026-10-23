package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class SpecGroup extends SpecObject implements Iterable<RelationGroup> {
  private static String TAG = "SPEC-GROUP",
      TYPE_TAG = "TYPE",
      STR_TAG = "SPECIFICATION-TYPE-REF",
      VALUES_TAG = "VALUES",
      GROUPS_TAG = "SPEC-RELATION-GROUPS";
  public Map<String, RelationGroup> groups = new HashMap<>();

  // public SpecObjectType definition;
  // public AttributeValues values;// = new AttributeValues();
  public SpecGroup(Map<String, Pair<String, String>> attrs, SpecObjectType definition) {
    super(attrs, definition);
    // this.definition = definition;
    // values = new AttributeValues(definition);
  }

  @Override
  public Iterator<RelationGroup> iterator() {
    return groups.values().iterator();
  }

  public void add(RelationGroup group) {
    groups.put(group.getId(), group);
  }

  public static SpecGroup load(Node node, ReqIFModel model) {
    String def = null;
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    Node tp = ReqIFModel.getFirstChild(node, TYPE_TAG);
    if (tp != null) {
      Node sot = ReqIFModel.getFirstChild(tp, STR_TAG);
      if (sot != null) def = sot.getTextContent();
      SpecObjectType soto = model.specObjectTypesById.get(def);
      if (soto == null) throw new RuntimeException("Object type not found! " + def);
      SpecGroup so = new SpecGroup(attrs, soto);
      Node vals = ReqIFModel.getFirstChild(node, VALUES_TAG);
      if (vals != null) {
        NodeList nl = vals.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
          Node av = nl.item(i);
          if (av.getNodeName() != null && !av.getNodeName().startsWith("#")) {
            AbstractAttributeValue val = ReqIFModel.loadValue(av, soto);
            so.values.setValue(val.definition.getId(), val);
          }
        }
      }
      Node grps = ReqIFModel.getFirstChild(node, GROUPS_TAG);
      if (grps != null) {
        NodeList nl = grps.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
          Node av = nl.item(i);
          if (av.getNodeName() != null && !av.getNodeName().startsWith("#")) {
            RelationGroup rg = RelationGroup.load(av, model);
            so.add(rg);
          }
        }
      }
      return so;
    }
    return null;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node so = super.save(TAG, par, doc, model);

    Element tp = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TYPE_TAG);
    so.appendChild(tp);
    so.appendChild(doc.createTextNode("\n"));

    Element sotr = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), STR_TAG);
    tp.appendChild(sotr);
    tp.appendChild(doc.createTextNode("\n"));
    sotr.setTextContent(definition.getId());

    Element els = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), VALUES_TAG);
    so.appendChild(els);
    so.appendChild(doc.createTextNode("\n"));
    for (AbstractAttributeValue aav : values) aav.save(els, doc, model);

    if (groups.size() > 0) {
      Element grps = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), GROUPS_TAG);
      so.appendChild(grps);
      so.appendChild(doc.createTextNode("\n"));
      for (RelationGroup rg : this) rg.save(grps, doc, model);
    }
    return so;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }
}
