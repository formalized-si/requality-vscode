package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class DoorsSpecObject extends SpecObject {

  public DoorsSpecObject(Map<String, Pair<String, String>> attrs, SpecObjectType definition) {
    super(attrs, definition);
  }

  @Override
  public String generateId() {
    String val = getValueByName(DOORSConstants.FNUMBER);
    if (val != null && !val.equals("0") && !val.isEmpty())
      return "_" + val + "_" + UUID.randomUUID();
    return "_" + UUID.randomUUID();
  }

  public static SpecObject load(Node node, ReqIFModel model) {
    String def = null;
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    Node tp = ReqIFModel.getFirstChild(node, TYPE_TAG);
    if (tp != null) {
      Node sot = ReqIFModel.getFirstChild(tp, SOTR_TAG);
      if (sot != null) def = sot.getTextContent();
      SpecObjectType adef = model.specObjectTypesById.get(def);
      if (adef == null) throw new RuntimeException("Spec type not found! " + def);
      SpecObject so = new DoorsSpecObject(attrs, adef);
      Node vals = ReqIFModel.getFirstChild(node, VALUES_TAG);
      if (vals != null) {
        NodeList nl = vals.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
          Node av = nl.item(i);
          if (av.getNodeName() != null && !av.getNodeName().startsWith("#")) {
            AbstractAttributeValue val = ReqIFModel.loadValue(av, adef);
            so.setValue(val.definition.getId(), val);
          }
        }
      }
      return so;
    }
    return null;
  }

  @Override
  public Object clone() throws CloneNotSupportedException {
    Map<String, Pair<String, String>> atrs = new HashMap<>();
    for (String s : attributes.keySet())
      atrs.put(
          s, new Pair<>(attributes.get(s).getFirst(), attributes.get(s).getSecond()));
    DoorsSpecObject dso = new DoorsSpecObject(atrs, definition);
    for (String s : values.valuesByAttributeId.keySet())
      dso.setValue(s, (AbstractAttributeValue) values.valuesByAttributeId.get(s).clone());
    return dso;
  }
}
