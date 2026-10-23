package com.unitesk.requality.tool.reqif;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class EnumAttributeValue extends AbstractAttributeValue {
  private static String TAG_VALUES = "VALUES";
  private List<EnumValueRef> values = new LinkedList<>();

  public EnumAttributeValue(AttributeDefinition definition, EnumValueRef ev) {
    super(definition, "ENUMERATION");
    values.add(ev);
  }

  public EnumAttributeValue(AttributeDefinition definition, List<EnumValueRef> evs) {
    super(definition, "ENUMERATION");
    values = evs;
  }

  public void addValue(EnumValueRef val) {
    values.add(val);
  }

  public List<EnumValueRef> getValues() {
    return values;
  }

  public void setValues(List<EnumValueRef> value) {
    this.values = value;
  }

  @Override
  public Element save(Node par, Document doc, ReqIFModel model) {
    Element node = super.save(par, doc, model);
    Element el = ReqIFDefinition.createElement(doc, getNamespace(model), TAG_VALUES);
    node.appendChild(el);
    node.appendChild(doc.createTextNode("\n"));
    for (EnumValueRef evr : values) evr.save(el, doc, model);
    return node;
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
    node.getNodeName().substring(node.getNodeName().lastIndexOf("-") + 1);
    AttributeDefinition ad = sot.getAttributeDefinition(dval);
    if (ad == null) throw new RuntimeException("Attribute definition not found! " + dval);
    List<EnumValueRef> vals = new LinkedList<>();
    Node vl = ReqIFModel.getFirstChild(node, TAG_VALUES);
    if (vl != null) {
      NodeList nl = vl.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Node chi = nl.item(i);
        if (chi.getNodeName() != null && chi.getNodeName().endsWith("REF"))
          vals.add(EnumValueRef.load(chi, ad));
      }
    }
    EnumAttributeValue aval = new EnumAttributeValue(ad, vals);
    return aval;
  }

  @Override
  public String getString() {
    if (values != null)
      if (!values.isEmpty()) {
        return values.iterator().next().definition.getName(); // omg
      }
    return null;
  }

  @Override
  public String getNameSpaceTag() {
    return null;
  }

  @Override
  public Object clone() {
    return new EnumAttributeValue(definition, new ArrayList<>(getValues()));
  }

  @Override
  public void setString(String doorsOLECrutch) {
    throw new UnsupportedOperationException("Cannot set the value of enum");
  }
}
