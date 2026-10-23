package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;

public class AttributeDefinition extends ReqIFDefinition {
  private static String TAG = "ATTRIBUTE-DEFINITION-", TAG_TYPE = "TYPE", TAG_DV = "DEFAULT-VALUE";
  public DatatypeDefinition datatype;
  public String suffix;
  public boolean isEditable;
  public List<AbstractAttributeValue> defvalues = new LinkedList<>();

  public AttributeDefinition(
      DatatypeDefinition datatypeid, Map<String, Pair<String, String>> attrs, String suffix) {
    super(attrs);
    this.datatype = datatypeid;
    this.suffix = suffix;
  }

  public static AttributeDefinition load(Node node, EntityWithAttributeDefs sot, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    String suff = node.getNodeName().substring(node.getNodeName().lastIndexOf("-") + 1);
    String dttp = null;
    Node tp = ReqIFModel.getFirstChild(node, TAG_TYPE);
    if (tp != null) {
      Node tpc = tp.getFirstChild();
      while (tpc != null && (tpc.getNodeName() == null || !tpc.getNodeName().startsWith("DATA")))
        tpc = tpc.getNextSibling();
      if(tpc != null)
        dttp = tpc.getTextContent();
    }
    DatatypeDefinition def = null;
    if (dttp != null)
      def = model.dataDefsById.get(dttp);
    if (def == null) throw new RuntimeException("Datatype definition not found! " + dttp);
    AttributeDefinition dd = new AttributeDefinition(def, attrs, suff);
    sot.getAttributesMap().put(dd.getId(), dd);
    Node childs = ReqIFModel.getFirstChild(node, TAG_DV);
    if (childs != null) {
      NodeList nl = childs.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Object adef = ReqIFModel.loadUValue(nl.item(i), sot);
        if (adef != null) {
          if ((adef instanceof AbstractAttributeValue))
            dd.defvalues.add((AbstractAttributeValue) adef);
          else {
            RLogger.logInfo("Type misunderstood!");
            try {
              throw new Exception("Incorrect value");
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
      }
    }
    return dd;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node dd = super.save(TAG + suffix, par, doc, model);
    Element tp = ReqIFDefinition.createElement(doc, getNamespace(model), TAG_TYPE);
    dd.appendChild(tp);
    dd.appendChild(doc.createTextNode("\n"));
    Element ref =
        ReqIFDefinition.createElement(
            doc, getNamespace(model), "DATATYPE-DEFINITION-" + suffix + "-REF");
    tp.appendChild(ref);
    tp.appendChild(doc.createTextNode("\n"));
    ref.setTextContent(datatype.getId());
    if (!defvalues.isEmpty()) {
      Node sf = ReqIFDefinition.createElement(doc, getNamespace(model), TAG_DV);
      dd.appendChild(sf);
      dd.appendChild(doc.createTextNode("\n"));
      for (AbstractAttributeValue def : defvalues) def.save(sf, doc, model);
    }
    return dd;
  }

  public List<AbstractAttributeValue> getValues() {
    return defvalues;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  public String getFullName() {
    return TAG + suffix;
  }

  public AttributeDefinition clone(boolean newid) {
    Map<String, Pair<String, String>> atrs = new HashMap<>();
    for (String s : attributes.keySet())
      atrs.put(
          s, new Pair<>(attributes.get(s).getFirst(), attributes.get(s).getSecond()));
    if (newid)
      atrs.put(ReqIFDefinition.ID_PARAM, new Pair<String, String>(null, "_" + UUID.randomUUID()));
    //TODO copy values?
    return new AttributeDefinition(datatype, atrs, suffix);
  }
}
