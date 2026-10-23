package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class ToolExtensions extends ReqIFDefinition {
  public static String TAG = "TOOL-EXTENSIONS";
  public List<ToolExtension> exts = new LinkedList<>();

  public ToolExtensions() {
    super(Collections.<String, Pair<String, String>>emptyMap());
  }

  @Override
  public String generateId() {
    return null;
  }

  public static ToolExtensions load(ReqIFModel model, Node node) {
    NodeList nl = node.getChildNodes();
    ToolExtensions te = new ToolExtensions();
    for (int i = 0; i < nl.getLength(); i++) {
      Node chi = nl.item(i);
      String tag = chi.getNodeName();
      if (tag != null && tag.equals(ToolExtension.TAG)) {
        DOORSToolExtension dte = DOORSToolExtension.load(model, chi);
        if (dte != null) te.exts.add(dte);
      }
    }
    return te;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    Node node = super.save(TAG, par, doc, model);
    for (ToolExtension te : exts) te.save(node, doc, model);
    return node;
  }

  public ToolExtension getExtension(String tAG2) {
    for (ToolExtension te : exts)
      // if(te.getName().equals(tAG2))
      return te; // TODO more proper one
    return null;
  }

  public List<ToolExtension> getExtensions() {
    return exts;
  }
}
