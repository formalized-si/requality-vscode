package com.unitesk.requality.tool.reqif;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DefaultAttributeValue extends AbstractStringAttributeValue {
  private static String TV = "THE-VALUE";

  public DefaultAttributeValue(AttributeDefinition definition, String suffix, String val) {
    super(definition, suffix);
    value = val;
  }

  @Override
  public Element save(Node par, Document doc, ReqIFModel model) {
    Element nd = super.save(par, doc, model);
    if (value != null && !value.isEmpty())
      ReqIFDefinition.setAttributeNS(nd, model.getNamespaceURI(getNamespace(model)), TV, value);
    return nd;
  }

  public static AbstractAttributeValue load(Node node, EntityWithAttributeDefs sot) {
    Node def = ReqIFModel.getFirstChild(node, DEF);
    String dval = null;
    if (def != null) {
      Node adef = def.getFirstChild();
      while (adef != null
          && (adef.getNodeName() == null || !adef.getNodeName().startsWith(DEF_TAG)))
        adef = adef.getNextSibling();
      if (adef != null) dval = adef.getTextContent();
    }
    String suff = node.getNodeName().substring(node.getNodeName().lastIndexOf("-") + 1);
    AttributeDefinition ad = sot.getAttributeDefinition(dval);
    if (ad == null) 
      throw new RuntimeException("Attribute definition was not found during value loading! " + dval);
    Node vl = node.getAttributes().getNamedItem(TV);
    String val = null;
    if (vl != null) val = vl.getNodeValue();
    else val = ad.datatype.getDefault();
    return new DefaultAttributeValue(ad, suff, val);
  }

  @Override
  public String getNameSpaceTag() {
    return null;
  }

  @Override
  public Object clone() {
    return new DefaultAttributeValue(definition, suffix, getString());
  }

  @Override
  public void setString(String doorsOLECrutch) {
    value = doorsOLECrutch;
  }
}
