package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;

public class AttributeDefExtension extends ReqIFDefinition {
  public static String TAG = "ATTRIBUTE-DEFINITION-EXTENSION", SATAG = "SAME-AS";
  public String sameas;
  public AttributeDefinition adefinition;
  public DatatypeDefinition definition;

  public AttributeDefExtension(DatatypeDefinition dd, String sa) {
    super(Collections.<String, Pair<String, String>>emptyMap());
    sameas = sa;
    definition = dd;
  }

  public AttributeDefExtension(AttributeDefinition dd, String sa) {
    super(Collections.<String, Pair<String, String>>emptyMap());
    sameas = sa;
    adefinition = dd;
  }

  @Override
  public String getNameSpacePrefix() {
    return "rm-reqif";
  }

  @Override
  public String generateId() {
    return null;
  }

  public static AttributeDefExtension load(ReqIFModel model, Node node) {
    if (node == null) return null;
    NodeList md = node.getChildNodes();
    String def = null;
    DatatypeDefinition dd = null;
    AttributeDefinition ad = null;
    for (int i = 0; i < md.getLength(); i++) {
      Node nd = md.item(i);
      String tag = nd.getNodeName();
      if (tag.startsWith("DATATYPE-DEFINITION")) {
        String defs = nd.getTextContent();
        dd = model.dataDefsById.get(defs);
        if (dd == null) {
          RLogger.logInfo("WARN: Datatype definition was not found! "+defs);
          return null;
        }
      }
      if (tag.startsWith("ATTRIBUTE-DEFINITION")) {
        String defs = nd.getTextContent();
        ad = model.getAttributeDef(defs);
        if (ad == null) {
          ad = model.getAttributeDef(defs);
          RLogger.logInfo("WARN: Attribute definition was not found during definition ext loading! "+defs);
          return null;
        }
      }
      if (tag.contains(SATAG)) def = nd.getTextContent();
    }
    if (def == null || (dd == null && ad == null)) return null;
    if (dd != null) return new AttributeDefExtension(dd, def);
    else return new AttributeDefExtension(ad, def);
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node now = super.save(TAG, par, doc, model);
    if (definition != null) {
      Node ddef = doc.createElement(definition.getFullName() + "-REF");
      ddef.setTextContent(definition.getId());
      now.appendChild(ddef);
    } else {
      Node ddef = doc.createElement(adefinition.getFullName() + "-REF");
      ddef.setTextContent(adefinition.getId());
      now.appendChild(ddef);
    }
    now.appendChild(doc.createTextNode("\n"));
    Node sdef =
        ReqIFDefinition.createElement(doc, getNamespace(model), getNameSpacePrefix() + ":" + SATAG);
    sdef.setTextContent(sameas);
    now.appendChild(sdef);
    now.appendChild(doc.createTextNode("\n"));
    return now;
  }
}
