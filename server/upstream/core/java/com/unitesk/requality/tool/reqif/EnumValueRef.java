package com.unitesk.requality.tool.reqif;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class EnumValueRef {
  private static String TAG = "ENUM-VALUE-REF";
  public EnumValue definition;

  public EnumValueRef(EnumValue def) {
    definition = def;
  }

  public Element save(Node par, Document doc, ReqIFModel model) {
    Element el = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG);
    par.appendChild(el);
    par.appendChild(doc.createTextNode("\n"));
    el.setTextContent(definition.getId());
    return el;
  }

  public static EnumValueRef load(Node chi, AttributeDefinition enu) {
    ReqIFDefinition ev = enu.datatype.getValueById(chi.getTextContent());
    if (ev == null)
      throw new RuntimeException("Enum value " + chi.getTextContent() + " not found!");
    return new EnumValueRef((EnumValue) ev);
  }
}
