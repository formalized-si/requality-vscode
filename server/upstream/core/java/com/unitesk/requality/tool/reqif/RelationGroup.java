package com.unitesk.requality.tool.reqif;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class RelationGroup extends ReqIFDefinition implements Iterable<SpecRelation> {
  private static final String SPECRELREF_TAG = "SPEC-RELATION-REF";
  private static String TAG = "RELATION-GROUP",
      RT_TAG = "RELATION-TYPE",
      SS_TAG = "SOURCE-SPECIFICATION",
      TS_TAG = "TARGET-SPECIFICATION",
      SRS_TAG = "SPEC-RELATIONS",
      RELTYPEREF_TAG = "SPECIFICATION-TYPE-REF",
      SREF_TAG = "SPECIFICATION-REF";
  public SpecRelationType relationType;
  public Specification sourceSpec, targetSpec;
  public String sourceSpecId, targetSpecId;
  public List<SpecRelation> relationIds = new LinkedList<>();

  public RelationGroup(
      Map<String, Pair<String, String>> attrs,
      SpecRelationType relationType,
      Specification sourceSpec,
      Specification targetSpec,
      String si,
      String ti) {
    super(attrs);
    this.sourceSpec = sourceSpec;
    this.targetSpec = targetSpec;
    this.sourceSpecId = si;
    this.targetSpecId = ti;
    this.relationType = relationType;
  }

  @Override
  public Iterator<SpecRelation> iterator() {
    return relationIds.iterator();
  }

  public void addRelation(SpecRelation val) {
    relationIds.add(val);
  }

  public static RelationGroup load(Node av, ReqIFModel model) {
    Map<String, Pair<String, String>> attrs = ReqIFDefinition.loadAttributes(av);
    String rt = null, ss = null, ts = null;
    Node rta = ReqIFModel.getFirstChild(av, RT_TAG);
    Node specTypeRef = ReqIFModel.getFirstChild(rta, RELTYPEREF_TAG);
    if (specTypeRef != null) rt = specTypeRef.getTextContent();

    Node ssa = ReqIFModel.getFirstChild(av, SS_TAG);
    Node sref = ReqIFModel.getFirstChild(ssa, SREF_TAG);
    ss = sref.getTextContent();

    Node tsa = ReqIFModel.getFirstChild(av, TS_TAG);
    Node tref = ReqIFModel.getFirstChild(tsa, SREF_TAG);
    ts = tref.getTextContent();

    SpecRelationType srt = model.specRelationTypesById.get(rt);
    if (srt == null) throw new RuntimeException("Relation type " + rt + " was not found");

    Specification s1 = model.specificationsById.get(ss);
    if (s1 == null) System.err.println("Specification " + ss + " was not found");

    Specification s2 = model.specificationsById.get(ts);
    if (s2 == null) System.err.println("Specification " + ts + " was not found");

    RelationGroup rg = new RelationGroup(attrs, srt, s1, s2, ss, ts);

    Node rels = ReqIFModel.getFirstChild(av, SRS_TAG);
    NodeList nl = rels.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node srr = nl.item(i);
      if (srr.getNodeName() != null && !srr.getNodeName().startsWith("#")) {
        SpecRelation sr = model.specRelationById.get(srr.getTextContent());
        if (sr == null)
          throw new RuntimeException("Spec relation " + srr.getTextContent() + " was not found");
        rg.addRelation(sr);
      }
    }
    return rg;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node nd = super.save(TAG, par, doc, model);
    Node rta = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), RT_TAG);
    nd.appendChild(rta);
    nd.appendChild(doc.createTextNode("\n"));
    Node specTypeRef =
        ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), RELTYPEREF_TAG);
    rta.appendChild(specTypeRef);
    rta.appendChild(doc.createTextNode("\n"));
    specTypeRef.setTextContent(relationType.getId());

    Node ssa = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SS_TAG);
    nd.appendChild(ssa);
    nd.appendChild(doc.createTextNode("\n"));
    Node sref = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SREF_TAG);
    ssa.appendChild(sref);
    ssa.appendChild(doc.createTextNode("\n"));
    sref.setTextContent(sourceSpec.getId());

    Node tsa = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TS_TAG);
    nd.appendChild(tsa);
    nd.appendChild(doc.createTextNode("\n"));
    Node tref = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SREF_TAG);
    tsa.appendChild(tref);
    tsa.appendChild(doc.createTextNode("\n"));
    tref.setTextContent(targetSpec.getId());

    if (relationIds.size() > 0) {
      Node rels = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SRS_TAG);
      nd.appendChild(rels);
      nd.appendChild(doc.createTextNode("\n"));
      for (SpecRelation rel : relationIds) {
        Node reln = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), SPECRELREF_TAG);
        rels.appendChild(reln);
        rels.appendChild(doc.createTextNode("\n"));
        reln.setTextContent(rel.getId());
      }
    }
    return nd;
  }

  @Override
  public String generateId() {
    return "_" + UUID.randomUUID().toString();
  }
}
