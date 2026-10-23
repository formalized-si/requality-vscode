package com.unitesk.requality.tool.reqif;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public abstract class ReqIFDefinition {
  public static final String CREATED_PARAM = "Created On";
  public static final String LAST_MOD = "Last Modified On";
  public int sort = Integer.MAX_VALUE;
  public static String ID_PARAM = "IDENTIFIER",
      NAME_PARAM = "LONG-NAME",
      CHANGE_PARAM = "LAST-CHANGE",
      ATTR_DESC = "DESC";
  public Map<String, Pair<String, String>> attributes; // first in pair is
  // namespace

  public ReqIFDefinition(Map<String, Pair<String, String>> attrs) {
    this.attributes = attrs;
  }

  public String getId() {
    Pair<String, String> id = attributes.get(ID_PARAM);
    if (id == null || id.getSecond() == null) {
      id = new Pair<>(null, generateId());
      attributes.put(ID_PARAM, id);
    }
    return id.getSecond();
  }

  public final String getNamespace(ReqIFModel model) {
    return model.getNamespaceURI(getNameSpacePrefix());
  }

  public abstract String generateId();

  public String getName() {
    if (!attributes.containsKey(NAME_PARAM)) return null;
    return attributes.get(NAME_PARAM).getSecond();
  }

  public String getLastChange() {
    return attributes.get(CHANGE_PARAM).getSecond();
  }

  public String getDesc() {
    if (!attributes.containsKey(ATTR_DESC)) return null;
    return attributes.get(ATTR_DESC).getSecond();
  }

  public static Map<String, Pair<String, String>> loadAttributes(Node node) {
    Map<String, Pair<String, String>> attributes = new HashMap<>();
    NamedNodeMap nnm = node.getAttributes();
    for (int i = 0; i < nnm.getLength(); i++) {
      Node chi = nnm.item(i);
      // TODO debug
      String key = chi.getNodeName();
      String ns = chi.getNamespaceURI();
      if (key.contains(":")) {
        ns = key.substring(0, key.indexOf(":"));
        key = key.substring(key.indexOf(":") + 1);
      }
      attributes.put(key, new Pair<>(ns, chi.getNodeValue()));
    }
    return attributes;
  }

  public abstract Node save(Node node, Document doc, ReqIFModel model);

  public Node save(String tag, Node par, Document doc, ReqIFModel model) {
    Element node = null;
    String ns = getNamespace(model);
    String pre = getNameSpacePrefix();
    if (pre == null || pre.isEmpty()) pre = "";
    else if (!pre.isEmpty()) pre = pre + ":";
    if (ns == null || ns.isEmpty())
      node = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), pre + tag);
    else node = ReqIFDefinition.createElement(doc, ns, pre + tag);
    for (String s : attributes.keySet()) {
      Pair<String, String> p = attributes.get(s);
      if (p.getFirst() != null)
        ReqIFDefinition.setAttributeNS(
            node, model.getNamespaceURI(p.getFirst()), s, p.getFirst() + ":" + p.getSecond());
      else ReqIFDefinition.setAttributeNS(node, model.getNamespaceURI(""), s, p.getSecond());
    }
    par.appendChild(node);
    par.appendChild(doc.createTextNode("\n"));
    return node;
  }

  static void setAttributeNS(Element node, String first, String s, String second) {
    if (first != null && !first.isEmpty()) node.setAttributeNS(first, s, second);
    else node.setAttribute(s, second);
  }

  public static Element createElement(Document doc, String namespaceURI, String string) {
    if (namespaceURI == null || namespaceURI.isEmpty()) return doc.createElement(string);
    return doc.createElementNS(namespaceURI, string);
  }

  public String getNameSpacePrefix() {
    return null;
  }
}
