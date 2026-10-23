package com.unitesk.requality.tool.reqif;

import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public abstract class SpecObject extends ReqIFDefinition {
  private static String TAG = "SPEC-OBJECT";
  protected static String TYPE_TAG = "TYPE";
  protected static String VALUES_TAG = "VALUES";
  protected static String SOTR_TAG = "SPEC-OBJECT-TYPE-REF";
  public SpecObjectType definition;
  public AttributeValues values;

  public SpecObject(Map<String, Pair<String, String>> attrs, SpecObjectType definition) {
    super(attrs);
    this.definition = definition;
    values = new AttributeValues(definition);
  }

  public void setValue(String definition, AbstractAttributeValue value) {
    // TODO swap to objects?
    values.setValue(definition, value);
  }

  public AbstractAttributeValue getValue(String definition) {
    return values.getValue(this.definition.getAttributeDefinition(definition));
  }

  public String getValueByName(String anumber) {
    AbstractAttributeValue aav = getAttributeValueByName(anumber);
    if (aav == null) return null;
    return aav.getString();
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node so = super.save(TAG, par, doc, model);

    Element tp = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TYPE_TAG);
    so.appendChild(tp);
    so.appendChild(doc.createTextNode("\n"));

    Element sotr = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SOTR_TAG);
    tp.appendChild(sotr);
    tp.appendChild(doc.createTextNode("\n"));
    sotr.setTextContent(definition.getId());

    Element els = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), VALUES_TAG);
    so.appendChild(els);
    so.appendChild(doc.createTextNode("\n"));
    for (AbstractAttributeValue aav : values)
      if (aav != null) {
        aav.save(els, doc, model);
        els.appendChild(doc.createTextNode("\n"));
      }
    return so;
  }

  public AbstractAttributeValue getAttributeValueByName(String anumber) {
    AttributeDefinition ad = getAttributeDefinitionByName(anumber);
    if (ad == null) return null;
    AbstractAttributeValue aav = values.getValue(ad);
    if (aav == null) return ad.datatype.getDefaultValue(ad);
    return aav;
  }

  public AttributeDefinition getAttributeDefinitionByName(String anumber) {
    AttributeDefinition[] ads = definition.getAttributeDefinitionByName(anumber);
    if (ads == null) return null;
    AttributeDefinition best = null;
    for (AttributeDefinition ad : ads) {
      if (ad.getDesc() != null && ad.getDesc().equals("System Attribute")
          || ad.getDesc().equals("")) // System
        // attr
        // is
        // prior
        if (best == null || !best.getDesc().equals("System Attribute")) best = ad;
    }
    if (best != null) return best;
    if (ads.length == 0) return null;
    return ads[0];
  }

  public boolean isValuesSimilarTo(SpecObject so) {
    return so.values.isSimilar(values);
  }
}
