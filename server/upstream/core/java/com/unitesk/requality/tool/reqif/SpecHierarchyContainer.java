package com.unitesk.requality.tool.reqif;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class SpecHierarchyContainer implements Iterable<SpecHierarchy> {
  static String TAG = "CHILDREN";
  private List<SpecHierarchy> children = new LinkedList<>();

  public SpecHierarchyContainer() {}

  public void add(SpecHierarchy one) {
    children.add(one);
  }

  @Override
  public Iterator<SpecHierarchy> iterator() {
    return children.iterator();
  }

  public static SpecHierarchyContainer load(Specification s, Node chil, ReqIFModel mode) {
    SpecHierarchyContainer shc = new SpecHierarchyContainer();
    NodeList nl = chil.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node chi = nl.item(i);
      if (chi.getNodeName().startsWith("SPEC-HIERARCHY")) {
        SpecHierarchy sh = SpecHierarchy.load(s, chi, mode);
        if (sh != null) shc.add(sh);
        ;
      }
    }
    return shc;
  }

  public void save(Node par, Document doc, ReqIFModel model) {
    if (children.size() == 0) return; // do not write empty
    Node chi = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG);
    par.appendChild(chi);
    par.appendChild(doc.createTextNode("\n"));
    for (SpecHierarchy sh : children) sh.save(chi, doc, model);
  }

  public boolean isEmpty() {
    return children == null || children.isEmpty();
  }
}
