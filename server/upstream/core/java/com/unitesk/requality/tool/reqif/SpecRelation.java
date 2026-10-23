package com.unitesk.requality.tool.reqif;

import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class SpecRelation extends SpecObject {
  private static final String SRTF_TAG = "SPEC-RELATION-TYPE-REF";
  private static final String TAG = "SPEC-RELATION";
  private static final String SRC_TAG = "SOURCE";
  private static final String TRG_TAG = "TARGET";
  private static final String SOREF_TAG = "SPEC-OBJECT-REF";

  public SpecObject source, target;
  public String sourceS, targetS;

  public SpecRelation(
      Map<String, Pair<String, String>> attrs,
      SpecObjectType def,
      SpecObject idSource,
      SpecObject idTarget,
      String sourceS,
      String targetS) {
    super(attrs, def);
    if (idSource == null || idTarget == null) return;
    source = idSource;
    target = idTarget;
  }

  public static SpecRelation load(Node node, ReqIFModel model) {
    String def = null;
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(node);
    Node tp = ReqIFModel.getFirstChild(node, TYPE_TAG);
    if (tp != null) {
      String srcv = null, trgv = null;
      Node sot = ReqIFModel.getFirstChild(tp, SRTF_TAG);
      if (sot != null) def = sot.getTextContent();
      Node src = ReqIFModel.getFirstChild(node, SRC_TAG);
      if (src != null) {
        Node srclink = ReqIFModel.getFirstChild(src, SOREF_TAG);
        srcv = srclink.getTextContent();
      }
      Node trg = ReqIFModel.getFirstChild(node, TRG_TAG);
      if (trg != null) {
        Node trglink = ReqIFModel.getFirstChild(trg, SOREF_TAG);
        trgv = trglink.getTextContent();
      }
      SpecObjectType soto = model.specRelationTypesById.get(def);
      if (soto == null) throw new RuntimeException("Spec object type was not found! " + def);
      SpecObject sosrc = model.objectById.get(srcv), sotrg = model.objectById.get(trgv);
      if (sosrc == null) System.err.println("Spec object was not found! " + srcv);
      if (sotrg == null) System.err.println("Spec object was not found! " + trgv);
      SpecRelation so = new SpecRelation(attrs, soto, sosrc, sotrg, srcv, trgv);
      Node vals = ReqIFModel.getFirstChild(node, VALUES_TAG);
      if (vals != null) {
        NodeList nl = vals.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
          Node av = nl.item(i);
          if (av.getNodeName() != null && !av.getNodeName().startsWith("#")) {
            AbstractAttributeValue val = ReqIFModel.loadValue(av, soto);
            // TODO obj?
            so.setValue(val.definition.getId(), val);
          }
        }
      }
      return so;
    }
    return null;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    if (source == null || target == null) return null;
    Node so = super.save(TAG, par, doc, model);

    Element src = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SRC_TAG);
    so.appendChild(src);
    so.appendChild(doc.createTextNode("\n"));
    Element srcr = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SOREF_TAG);
    src.appendChild(srcr);
    src.appendChild(doc.createTextNode("\n"));
    srcr.setTextContent(source.getId());

    Element trg = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TRG_TAG);
    so.appendChild(trg);
    so.appendChild(doc.createTextNode("\n"));

    Element trgr = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SOREF_TAG);
    trg.appendChild(trgr);
    trg.appendChild(doc.createTextNode("\n"));
    trgr.setTextContent(target.getId());

    Element tp = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TYPE_TAG);
    so.appendChild(tp);
    so.appendChild(doc.createTextNode("\n"));
    Element sotr = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SRTF_TAG);
    tp.appendChild(sotr);
    tp.appendChild(doc.createTextNode("\n"));
    sotr.setTextContent(definition.getId());

    Element els = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), VALUES_TAG);
    so.appendChild(els);
    so.appendChild(doc.createTextNode("\n"));
    for (AbstractAttributeValue aav : values) {
      aav.save(els, doc, model);
      els.appendChild(doc.createTextNode("\n"));
    }
    return so;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID();
  }
}
