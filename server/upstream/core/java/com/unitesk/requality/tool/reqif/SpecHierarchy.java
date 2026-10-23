package com.unitesk.requality.tool.reqif;

import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public class SpecHierarchy extends ReqIFDefinition {
  private static String TAG = "SPEC-HIERARCHY", O_TAG = "OBJECT", SOREF_TAG = "SPEC-OBJECT-REF";
  public SpecObject oref;
  public SpecHierarchyContainer children;

  public SpecHierarchy(Map<String, Pair<String, String>> attrs, SpecObject oref) {
    super(attrs);
    this.oref = oref;
  }

  public static SpecHierarchy load(Specification s, Node chi, ReqIFModel mod) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(chi);
    Node objo = ReqIFModel.getFirstChild(chi, O_TAG);
    Node sref = ReqIFModel.getFirstChild(objo, SOREF_TAG);
    SpecObject so = mod.objectById.get(sref.getTextContent());
    mod.objectToSpec.put(sref.getTextContent(), s);
    if (so == null) {
      System.err.println("SHload: Spec object was not found");
      return null;
    }
    SpecHierarchy sh = new SpecHierarchy(attrs, so);
    Node chil = ReqIFModel.getFirstChild(chi, SpecHierarchyContainer.TAG);
    if (chil != null) sh.children = SpecHierarchyContainer.load(s, chil, mod);
    return sh;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node nd = super.save(TAG, par, doc, model);
    par.appendChild(nd);
    par.appendChild(doc.createTextNode("\n"));

    Node objo = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), O_TAG);
    nd.appendChild(objo);
    nd.appendChild(doc.createTextNode("\n"));
    Node sref = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SOREF_TAG);
    objo.appendChild(sref);
    objo.appendChild(doc.createTextNode("\n"));
    sref.setTextContent(oref.getId());

    if (children != null && !children.isEmpty()) children.save(nd, doc, model);
    return nd;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }

  public SpecObject getSpecObject() {
    return oref;
  }
}
