package com.unitesk.requality.tool.reqif;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class Deleted extends ReqIFDefinition {
  private Map<String, SpecObject> removed = new HashMap<>();

  public Deleted() {
    super(Collections.<String, Pair<String, String>>emptyMap());
  }

  public static String TAG1 = "DELETIONS", TAG2 = "DELETED-OBJECTS", TAGSOR = "SPEC-OBJECT-REF";

  @Override
  public String getNameSpacePrefix() {
    return "doors";
  }

  @Override
  public String generateId() {
    // TODO Auto-generated method stub
    return null;
  }

  public Collection<SpecObject> getDeleted() {
    return removed.values();
  }

  public void addDeleted(SpecObject so) {
    removed.put(so.getId(), so);
  }

  public static Deleted load(ReqIFModel model, Node node) {
    Node n2 = ReqIFModel.getFirstChild(node, TAG2);
    if (n2 != null) {
      NodeList nl = n2.getChildNodes();
      Deleted d = new Deleted();
      for (int i = 0; i < nl.getLength(); i++) {
        Node chi = nl.item(i);
        String tag = chi.getNodeName();
        if (tag != null && tag.equals(TAGSOR)) {
          String id = chi.getTextContent();
          SpecObject so = model.objectById.get(id);
          if (so != null) d.removed.put(so.getId(), so);
          else d.removed.put(id, null);
        }
      }
      return d;
    }
    return null;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    String pre = getNameSpacePrefix() + ":";
    Node n1 = null;
    if (!removed.isEmpty()) {
      par.appendChild(doc.createTextNode("\n"));
      n1 = super.save(TAG1, par, doc, model);
      par.appendChild(doc.createTextNode("\n"));
      Node n2 = ReqIFDefinition.createElement(doc, getNamespace(model), pre + TAG2);
      n1.appendChild(n2);
      n1.appendChild(doc.createTextNode("\n"));
      List<String> keys = new ArrayList<>(removed.keySet());
      Collections.sort(
          keys,
          new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
              if (o1.startsWith("_") && o2.startsWith("_")) {
                String o1m = o1.substring(1), o2m = o2.substring(1);
                int i1 = o1m.indexOf("_"), i2 = o2m.indexOf("_");
                if (i1 > -1 && i2 > -1 && i1 < 10 && i2 < 10) {
                  int iv1 = Integer.parseInt(o1m.substring(0, i1)),
                      iv2 = Integer.parseInt(o2m.substring(0, i2));
                  if (iv1 != 0 && i2 != 0) return Integer.compare(iv1, iv2);
                }
              }
              return o1.compareTo(o2);
            }
          });
      for (String so : keys) {
        Node ref = null;
        n2.appendChild(doc.createTextNode("\n"));
        ref = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAGSOR);
        n2.appendChild(ref);
        ref.setTextContent(so);
      }
    }
    return n1;
  }

  public void removeDeleted(SpecObject so) {
    removed.remove(so.getId());
  }
}
