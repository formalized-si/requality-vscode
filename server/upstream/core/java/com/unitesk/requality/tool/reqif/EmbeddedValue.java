package com.unitesk.requality.tool.reqif;

import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public class EmbeddedValue {
  private static String TAG = "EMBEDDED-VALUE";
  public static String KEY = "KEY", OTHER = "OTHER-CONTENT";
  public String key, other_content, value;

  public EmbeddedValue(String key, String other, String val) {
    this.key = key;
    this.other_content = other;
    this.value = val;
  }

  public static EmbeddedValue load(Node child) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(child);
    return new EmbeddedValue(
        attrs.get(KEY).getSecond(), attrs.get(OTHER).getSecond(), child.getTextContent());
  }

  public void save(Node parent, Document doc, ReqIFModel model) {
    Element el = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG);
    el.setAttribute(KEY, key);
    el.setAttribute(OTHER, other_content);
    el.setTextContent(value);
    parent.appendChild(el);
    parent.appendChild(doc.createTextNode("\n"));
  }
}
