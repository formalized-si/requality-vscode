package com.unitesk.requality.tool.reqif;

import java.util.Collections;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.unitesk.requality.core.model.Pair;

public class ModuleDefinition extends ReqIFDefinition {
  public static String TAG = "MODULE-DEFINITION",
      TAG_SPECCREF = Specification.TAG + "-REF",
      TAG_SPECTYPEREF = SpecificationType.TAG + "-REF",
      TAG_DDC = "DDC-MODE";
  public String ddcmode;
  public Specification spec;
  public SpecObjectType spectype;

  @Override
  public String getNameSpacePrefix() {
    return "doors";
  }

  public ModuleDefinition(String ddcmode, Specification srt) {
    super(Collections.<String, Pair<String, String>>emptyMap());
    this.ddcmode = ddcmode;
    this.spec = srt;
  }

  public ModuleDefinition(String ddcmode, SpecObjectType spec) {
    super(Collections.<String, Pair<String, String>>emptyMap());
    this.ddcmode = ddcmode;
    this.spectype = spec;
  }

  @Override
  public String generateId() {
    return null;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node nd = super.save(TAG, par, doc, model);
    Node ddc =
        ReqIFDefinition.createElement(
            doc, getNamespace(model), getNameSpacePrefix() + ":" + TAG_DDC);
    nd.appendChild(ddc);
    nd.appendChild(doc.createTextNode("\n"));
    ddc.setTextContent(ddcmode);

    if (spec != null) {
      Node n1 = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG_SPECCREF);
      n1.setTextContent(spec.getId());
      nd.appendChild(n1);
      nd.appendChild(doc.createTextNode("\n"));
    } else {
      Node n2 = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG_SPECTYPEREF);
      n2.setTextContent(spectype.getId());
      nd.appendChild(n2);
      nd.appendChild(doc.createTextNode("\n"));
    }
    return nd;
  }

  public static ModuleDefinition load(ReqIFModel model, Node node) {
    String mode = ReqIFModel.getFirstChild(node, TAG_DDC).getTextContent();
    Node n1 = ReqIFModel.getFirstChild(node, TAG_SPECCREF),
        n2 = ReqIFModel.getFirstChild(node, TAG_SPECTYPEREF);
    Specification spec = null;
    SpecObjectType st = null;
    if (n1 != null) spec = model.specificationsById.get(n1.getTextContent());
    if (n2 != null) {
      st = model.objectTypesById.get(n2.getTextContent());
      if (st == null) st = model.specRelationTypesById.get(n2.getTextContent());
    }
    if (spec != null) return new ModuleDefinition(mode, spec);
    else if (st != null) return new ModuleDefinition(mode, st);
    else return null;
  }
}
