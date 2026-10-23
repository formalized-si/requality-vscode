package com.unitesk.requality.tool.reqif;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.model.Pair;

public class DoorsRIFDefinition extends ReqIFDefinition {
  public static String TAG = "DOORS-RIF-DEFINITION", TAGID = "IDENTIFIER";
  public String identifier;
  public List<ModuleDefinition> modules;

  public DoorsRIFDefinition(List<ModuleDefinition> modules, String id) {
    super(Collections.<String, Pair<String, String>>emptyMap());
    this.modules = modules;
    this.identifier = id;
  }

  @Override
  public String getNameSpacePrefix() {
    return "doors";
  }

  @Override
  public String generateId() {
    return null;
  }

  @Override
  public Node save(Node par, Document doc, ReqIFModel model) {
    String prefix = getNameSpacePrefix() + ":";
    Node nd = super.save(TAG, par, doc, model);

    Node id = ReqIFDefinition.createElement(doc, getNamespace(model), prefix + TAGID);
    nd.appendChild(id);
    nd.appendChild(doc.createTextNode("\n"));
    id.setTextContent(identifier);

    for (ModuleDefinition md : modules) if (md != null) md.save(nd, doc, model);
    return nd;
  }

  public static DoorsRIFDefinition load(ReqIFModel model, Node nd) {
    NodeList nl = nd.getChildNodes();
    String id = null;
    List<ModuleDefinition> modules = new LinkedList<>();
    for (int i = 0; i < nl.getLength(); i++) {
      Node node = nl.item(i);
      if (node.getNodeName() != null) {
        if (node.getNodeName().contains(TAGID)) id = node.getTextContent();
        else if (node.getNodeName().contains(ModuleDefinition.TAG)) {
          ModuleDefinition md = ModuleDefinition.load(model, node);
          if (md != null) modules.add(md);
        }
      }
    }
    return new DoorsRIFDefinition(modules, id);
  }

  public boolean hasModule(SpecRelationType srt) {
    for (ModuleDefinition md : modules)
      if (md.spectype != null && md.spectype.getId().equals(srt.getId())) return true;
    return false;
  }
}
