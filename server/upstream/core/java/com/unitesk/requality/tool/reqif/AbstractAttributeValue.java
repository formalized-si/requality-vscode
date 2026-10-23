package com.unitesk.requality.tool.reqif;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class AbstractAttributeValue {
  protected static String DEF = "DEFINITION",
      TAG_PREFIX = "ATTRIBUTE-VALUE-",
      DEF_TAG = "ATTRIBUTE-DEFINITION-";
  public String suffix;
  public AttributeDefinition definition;

  public AbstractAttributeValue(AttributeDefinition definition, String suffix) {
    this.definition = definition;
    this.suffix = suffix;
  }

  public Element save(Node par, Document doc, ReqIFModel model) {
    String pre = getNameSpaceTag();
    if (pre != null && !pre.isEmpty()) pre = pre + ":";
    else pre = "";
    Element el = ReqIFDefinition.createElement(doc, getNamespace(model), pre + TAG_PREFIX + suffix);
    par.appendChild(el);

    Element def = ReqIFDefinition.createElement(doc, getNamespace(model), DEF);
    el.appendChild(def);

    Element ref =
        ReqIFDefinition.createElement(doc, getNamespace(model), DEF_TAG + suffix + "-REF");
    ref.setTextContent(definition.getId());
    def.appendChild(ref);
    return el;
  }

  protected String getNamespace(ReqIFModel model) {
    return model.getNamespaceURI(getNameSpaceTag());
  }

  public abstract String getNameSpaceTag();

  public abstract String getString();

  public abstract Object clone();

  public abstract void setString(String doorsOLECrutch);
}
