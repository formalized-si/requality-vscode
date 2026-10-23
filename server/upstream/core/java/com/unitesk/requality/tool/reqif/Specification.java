package com.unitesk.requality.tool.reqif;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public class Specification extends ReqIFDefinition {
  static String TAG = "SPECIFICATION";
  private static String TAG_REF = "SPECIFICATION-TYPE-REF";
  private static String TAG_TYPE = "TYPE";
  private static String TAG_CHILDREN = "CHILDREN";
  public SpecificationType definition;
  private SpecHierarchyContainer children;

  public Specification(Map<String, Pair<String, String>> attrs, SpecificationType definition) {
    super(attrs);
    this.definition = definition;
  }

  public static Specification load(Node nd, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(nd);
    Node tp = ReqIFModel.getFirstChild(nd, TAG_TYPE);
    Node tref = ReqIFModel.getFirstChild(tp, TAG_REF);
    String def = tref.getTextContent();
    SpecificationType st = model.specificationTypeById.get(def);
    if (st == null) throw new RuntimeException("Specification type was not found! " + def);
    Specification spec = new Specification(attrs, st);
    
    Node chil = ReqIFModel.getFirstChild(nd, TAG_CHILDREN);
    if (chil != null) spec.setChildren(SpecHierarchyContainer.load(spec, chil, model));
    return spec;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node spec = super.save(TAG, par, doc, model);
    Node tp = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG_TYPE);
    spec.appendChild(tp);
    spec.appendChild(doc.createTextNode("\n"));
    Node tred = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG_REF);
    tp.appendChild(tred);
    tp.appendChild(doc.createTextNode("\n"));
    tred.setTextContent(definition.getId());
    // Node chi =
    // ReqIFDefinition.createElement(doc,model.getNamespaceURI(""),
    // TAG_CHILDREN);
    // spec.appendChild(chi);
    // spec.appendChild(doc.createTextNode("\n"));
    getChildren().save(spec, doc, model);
    return spec;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  public void addRec(SpecHierarchyContainer shc, Set<SpecObject> set) {
    if (shc == null) return;
    for (SpecHierarchy sh : shc) {
      set.add(sh.getSpecObject());
      addRec(sh.children, set);
    }
  }

  public Set<SpecObject> getSpecObjects() {
    Set<SpecObject> specs = new HashSet<>();
    addRec(getChildren(), specs);
    return specs;
  }

  public SpecHierarchyContainer getChildren() {
    if (children == null) children = new SpecHierarchyContainer();
    return children;
  }

  public void setChildren(SpecHierarchyContainer children) {
    this.children = children;
  }
}
