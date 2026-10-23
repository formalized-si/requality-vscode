package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class SpecRelationType extends SpecObjectType {
  public static String TAG = "SPEC-RELATION-TYPE";

  public SpecRelationType(Map<String, Pair<String, String>> attrs) {
    super(attrs);
  }

  public static SpecRelationType load(Node nd, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(nd);
    SpecRelationType sot = new SpecRelationType(attrs);
    Node sa = ReqIFModel.getFirstChild(nd, SA);
    if (sa != null) {
      NodeList nl = sa.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Node chi = nl.item(i);
        if (chi.getNodeName() != null && chi.getNodeName().startsWith("ATTR")) {
          // TODO rec?
          AttributeDefinition ad = AttributeDefinition.load(chi, sot, model);
          sot.getAttributesMap().put(ad.getId(), ad);
        }
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

  public static SpecRelationType create(ReqIFModel model) {
    String id = "_" + UUID.randomUUID();
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(ReqIFDefinition.ID_PARAM, new Pair<String, String>(null, id));
    attrs.put(ReqIFDefinition.CHANGE_PARAM, new Pair<String, String>(null, ReqIFModel.getTime()));
    SpecRelationType srt = new SpecRelationType(attrs);
    model.specRelationTypesById.put(srt.getId(), srt);
    SpecObjectType sot = model.specObjectTypesById.values().iterator().next();
    for (AttributeDefinition ad : sot.getAttributesMap().values()) {
      if (ad.datatype.getDesc() != null && ad.datatype.getDesc().toLowerCase().contains("system"))
        srt.addAttributeDefinition(ad.clone(true));
    }
    return srt;
  }
}
