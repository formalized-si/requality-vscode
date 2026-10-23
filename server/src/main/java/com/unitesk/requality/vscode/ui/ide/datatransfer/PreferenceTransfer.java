package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.vscode.ui.jface.preference.PreferenceStore;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;

/**
 * {@code org.eclipse.ui.internal.preferences.PreferenceTransfer}: the preferences file (.epf) of the plug-in's preference
 * store. The file is the Eclipse preference format: {@code <?eclipse version="3.4"?>}, then {@code <preferences>} with the
 * {@code instance} node, the node of the plug-in and its {@code map} of key and value entries.
 */
final class PreferenceTransfer {
  private static final String NODE = ExtensionRegistry.PLUGIN_ID;

  private PreferenceTransfer() {
  }

  /** Writes the values of the plug-in's preference store to the file. */
  static void export(PreferenceStore store, File file) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
    sb.append("<?eclipse version=\"3.4\"?>\n");
    sb.append("<preferences>\n");
    sb.append("<node name=\"instance\">\n");
    sb.append("<node name=\"").append(NODE).append("\">\n");
    sb.append("<map>\n");
    for (Map.Entry<String, String> e : store.values().entrySet())
      sb.append("<entry key=\"").append(escape(e.getKey())).append("\" value=\"").append(escape(e.getValue()))
          .append("\"/>\n");
    sb.append("</map>\n</node>\n</node>\n</preferences>\n");
    if (file.getParentFile() != null)
      file.getParentFile().mkdirs();
    Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
  }

  /** Reads the entries of the plug-in's node of the file, and sets them in the preference store. */
  static int importFile(PreferenceStore store, File file) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setExpandEntityReferences(false);
    Document doc = f.newDocumentBuilder().parse(file);
    Element root = doc.getDocumentElement();
    if (root == null || !"preferences".equals(root.getNodeName()))
      throw new IOException("Not a preference file: " + file);
    int count = 0;
    for (Element instance : children(root, "node", "instance"))
      for (Element plugin : children(instance, "node", NODE))
        for (Element map : children(plugin, "map", null))
          for (Element entry : children(map, "entry", null)) {
            store.setValue(entry.getAttribute("key"), entry.getAttribute("value"));
            count++;
          }
    return count;
  }

  private static java.util.List<Element> children(Element parent, String tag, String name) {
    java.util.List<Element> r = new java.util.ArrayList<>();
    NodeList l = parent.getChildNodes();
    for (int i = 0; i < l.getLength(); i++) {
      Node n = l.item(i);
      if (n instanceof Element e && tag.equals(e.getNodeName()) && (name == null || name.equals(e.getAttribute("name"))))
        r.add(e);
    }
    return r;
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
