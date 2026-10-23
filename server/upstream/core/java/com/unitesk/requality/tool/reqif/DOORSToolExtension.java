package com.unitesk.requality.tool.reqif;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class DOORSToolExtension extends ToolExtension {
  TypeExtensions tex = null;
  DoorsRIFDefinition drifd = null;
  ApplicationInfo ai = null;
  ReadOnlyAttributes rao = null;
  private Deleted del = null;

  public DOORSToolExtension() {}

  @Override
  public String generateId() {
    return null;
  }

  public static DOORSToolExtension load(ReqIFModel model, Node node) {
    // Map<String, Pair<String, String>> rid =
    // ReqIFDefinition.loadAttributes(node);
    DOORSToolExtension te = new DOORSToolExtension();
    te.load(node);
    Node temp = ReqIFModel.getFirstChild(node, TypeExtensions.TAG);
    if (temp != null) te.tex = TypeExtensions.load(model, temp);
    temp = ReqIFModel.getFirstChild(node, DoorsRIFDefinition.TAG);
    if (temp != null) te.drifd = DoorsRIFDefinition.load(model, temp);
    temp = ReqIFModel.getFirstChild(node, ApplicationInfo.TAG);
    if (temp != null) te.ai = ApplicationInfo.load(temp);
    temp = ReqIFModel.getFirstChild(node, ReadOnlyAttributes.TAG);
    if (temp != null) te.rao = ReadOnlyAttributes.load(model, temp);
    temp = ReqIFModel.getFirstChild(node, Deleted.TAG1);
    if (temp != null) te.setDeleted(Deleted.load(model, temp));
    return te;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    if (tex != null) tex.save(node, doc, model);
    node.appendChild(doc.createTextNode("\n"));
    if (drifd != null) drifd.save(node, doc, model);
    node.appendChild(doc.createTextNode("\n"));
    if (ai != null) ai.save(node, doc, model);
    node.appendChild(doc.createTextNode("\n"));
    if (rao != null) rao.save(node, doc, model);
    node.appendChild(doc.createTextNode("\n"));
    if (getDeleted() != null) getDeleted().save(node, doc, model);
    node.appendChild(doc.createTextNode("\n"));
    return node;
  }

  Deleted getDeleted() {
    if (del == null) del = new Deleted();
    return del;
  }

  void setDeleted(Deleted del) {
    this.del = del;
  }
}
