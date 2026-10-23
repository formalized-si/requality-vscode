package com.unitesk.requality.tool.reqif;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;

public class DatatypeDefinition extends ReqIFDefinition {
  private static String TAG_PREF = "DATATYPE-DEFINITION-";
  protected static String SV = "SPECIFIED-VALUES";
  public String suffix;
  public Map<String, ReqIFDefinition> valuesById = new HashMap<>();
  public Map<String, Set<ReqIFDefinition>> valuesByName =
      new HashMap<>();

  public DatatypeDefinition(Map<String, Pair<String, String>> attrs, String suffix) {
    super(attrs);
    this.suffix = suffix;
  }

  public static DatatypeDefinition load(Node node, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    String suff = node.getNodeName().substring(node.getNodeName().lastIndexOf("-") + 1);
    DatatypeDefinition dd = new DatatypeDefinition(attrs, suff);
    Node childs = ReqIFModel.getFirstChild(node, SV);
    if (childs != null) {
      NodeList nl = childs.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Object odef = ReqIFModel.loadUValue(nl.item(i), null); // TODO
        // test
        if (odef != null) {
          if (odef instanceof EnumValue) {
            dd.addValue((EnumValue) odef, i);
          } else {
            RLogger.logInfo("not enum value on enum def "+node.getNodeName());
            try {
              throw new Exception("not enum on enum def!");
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
      }
    }
    return dd;
  }

  void addValue(EnumValue def, int i) {
    def.sort = i;
    valuesById.put(def.getId(), def);
    String name = def.getName();
    if (name != null) {
      valuesByName.put(name, new HashSet<ReqIFDefinition>());
      valuesByName.get(name).add(def);
    }
  }

  public ReqIFDefinition getValueById(String id) {
    return valuesById.get(id);
  }

  public Set<ReqIFDefinition> getValueByKey(String id) {
    return valuesByName.get(id);
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node dd = super.save(TAG_PREF + suffix, par, doc, model);
    if (!valuesById.isEmpty()) {
      Node sf = ReqIFDefinition.createElement(doc, getNamespace(model), SV);
      dd.appendChild(sf);
      dd.appendChild(doc.createTextNode("\n"));
      List<ReqIFDefinition> vals = new ArrayList<>(valuesById.values());
      Collections.sort(
          vals,
          new Comparator<ReqIFDefinition>() {

            @Override
            public int compare(ReqIFDefinition o1, ReqIFDefinition o2) {
              return Integer.compare(o1.sort, o2.sort);
            }
          });
      for (ReqIFDefinition def : vals) def.save(sf, doc, model);
    }
    return dd;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  public String getFullName() {
    return TAG_PREF + suffix;
  }

  public String getSuffix() {
    return suffix;
  }

  public List<String> getValues() {
    List<String> bvals = new LinkedList<>();
    Collection<ReqIFDefinition> defs = ReqIFModel.getSorted(valuesById.values());
    for (ReqIFDefinition def : defs)
      if (def instanceof EnumValue) bvals.add(((EnumValue) def).getName());
    return bvals;
  }

  public String getDefault() {
    return "";
  }

  public AbstractAttributeValue getDefaultValue(AttributeDefinition ad) {
    List<AbstractAttributeValue> avs = ad.defvalues;
    if (avs != null && !avs.isEmpty()) return avs.iterator().next();
    Pair<String, String> tv = ad.attributes.get("THE-VALUE");
    if (suffix.equals("INTEGER"))
      return new DefaultAttributeValue(ad, "INTEGER", tv != null ? tv.getSecond() : null);
    if (suffix.equalsIgnoreCase("BOOLEAN"))
      return new DefaultAttributeValue(ad, "BOOLEAN", tv != null ? tv.getSecond() : null);
    else if (suffix.equals("DATE"))
      return new DefaultAttributeValue(ad, "DATE", tv != null ? tv.getSecond() : null);
    else if (suffix.equals("REAL"))
      return new DefaultAttributeValue(ad, "REAL", tv != null ? tv.getSecond() : null);
    else if (suffix.equals("XHTML")) {
      return new XHTMLAttributeValue(ad, tv != null ? tv.getSecond() : null);
    } else if (suffix.contains("ENUM")) {
      com.unitesk.requality.tool.reqif.EnumValue ev =
          (EnumValue) getValueByKey(getValues().iterator().next()).iterator().next();
      return new EnumAttributeValue(ad, new EnumValueRef(ev));
    } else return new DefaultAttributeValue(ad, suffix, "");
  }
}
