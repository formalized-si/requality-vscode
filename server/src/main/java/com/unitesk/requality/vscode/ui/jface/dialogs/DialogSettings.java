package com.unitesk.requality.vscode.ui.jface.dialogs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * {@code org.eclipse.jface.dialogs.DialogSettings}: sections of key/value items and string lists, stored in the
 * Eclipse XML format (files are shared with Eclipse, e.g. the per-project settings of the test wizards).
 */
public class DialogSettings implements IDialogSettings {
  private static final String TAG_SECTION = "section";
  private static final String TAG_NAME = "name";
  private static final String TAG_KEY = "key";
  private static final String TAG_VALUE = "value";
  private static final String TAG_LIST = "list";
  private static final String TAG_ITEM = "item";

  private String name;
  private final Map<String, String> items = new LinkedHashMap<>();
  private final Map<String, String[]> arrayItems = new LinkedHashMap<>();
  private final Map<String, IDialogSettings> sections = new LinkedHashMap<>();

  public DialogSettings(String sectionName) {
    this.name = sectionName;
  }

  @Override
  public IDialogSettings addNewSection(String sectionName) {
    DialogSettings section = new DialogSettings(sectionName);
    addSection(section);
    return section;
  }

  @Override
  public void addSection(IDialogSettings section) {
    sections.put(section.getName(), section);
  }

  public void removeSection(IDialogSettings section) {
    if (sections.get(section.getName()) == section)
      sections.remove(section.getName());
  }

  public IDialogSettings removeSection(String sectionName) {
    return sections.remove(sectionName);
  }

  @Override
  public String get(String key) {
    return items.get(key);
  }

  @Override
  public String[] getArray(String key) {
    return arrayItems.get(key);
  }

  @Override
  public boolean getBoolean(String key) {
    return Boolean.parseBoolean(items.get(key));
  }

  @Override
  public double getDouble(String key) throws NumberFormatException {
    String setting = items.get(key);
    if (setting == null)
      throw new NumberFormatException("There is no setting associated with the key \"" + key + "\"");
    return Double.parseDouble(setting);
  }

  @Override
  public float getFloat(String key) throws NumberFormatException {
    String setting = items.get(key);
    if (setting == null)
      throw new NumberFormatException("There is no setting associated with the key \"" + key + "\"");
    return Float.parseFloat(setting);
  }

  @Override
  public int getInt(String key) throws NumberFormatException {
    String setting = items.get(key);
    if (setting == null)
      throw new NumberFormatException("There is no setting associated with the key \"" + key + "\"");
    return Integer.parseInt(setting);
  }

  @Override
  public long getLong(String key) throws NumberFormatException {
    String setting = items.get(key);
    if (setting == null)
      throw new NumberFormatException("There is no setting associated with the key \"" + key + "\"");
    return Long.parseLong(setting);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public IDialogSettings getSection(String sectionName) {
    return sections.get(sectionName);
  }

  @Override
  public IDialogSettings[] getSections() {
    return sections.values().toArray(new IDialogSettings[0]);
  }

  @Override
  public void load(Reader r) throws IOException {
    Element root;
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      root = f.newDocumentBuilder().parse(new InputSource(r)).getDocumentElement();
    } catch (Exception e) {
      // Eclipse ignores unreadable settings.
      return;
    }
    if (TAG_SECTION.equals(root.getNodeName()))
      load(root);
  }

  private void load(Element root) {
    name = root.getAttribute(TAG_NAME);
    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element))
        continue;
      Element e = (Element) node;
      switch (e.getNodeName()) {
        case TAG_ITEM:
          if (e.hasAttribute(TAG_KEY))
            items.put(e.getAttribute(TAG_KEY), e.getAttribute(TAG_VALUE));
          break;
        case TAG_LIST: {
          List<String> values = new ArrayList<>();
          NodeList list = e.getChildNodes();
          for (int j = 0; j < list.getLength(); j++)
            if (list.item(j) instanceof Element && TAG_ITEM.equals(list.item(j).getNodeName()))
              values.add(((Element) list.item(j)).getAttribute(TAG_VALUE));
          arrayItems.put(e.getAttribute(TAG_KEY), values.toArray(new String[0]));
          break;
        }
        case TAG_SECTION: {
          DialogSettings s = new DialogSettings("NoName");
          s.load(e);
          addSection(s);
          break;
        }
        default:
          break;
      }
    }
  }

  @Override
  public void load(String fileName) throws IOException {
    try (Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8))) {
      load(r);
    }
  }

  @Override
  public void put(String key, String[] value) {
    arrayItems.put(key, value);
  }

  @Override
  public void put(String key, double value) {
    put(key, String.valueOf(value));
  }

  @Override
  public void put(String key, float value) {
    put(key, String.valueOf(value));
  }

  @Override
  public void put(String key, int value) {
    put(key, String.valueOf(value));
  }

  @Override
  public void put(String key, long value) {
    put(key, String.valueOf(value));
  }

  @Override
  public void put(String key, String value) {
    items.put(key, value);
  }

  @Override
  public void put(String key, boolean value) {
    put(key, String.valueOf(value));
  }

  @Override
  public void save(Writer writer) throws IOException {
    StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    save(sb, 0);
    writer.write(sb.toString());
    writer.flush();
  }

  private static String escape(String s) {
    if (s == null)
      return "";
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '&':
          b.append("&amp;");
          break;
        case '<':
          b.append("&lt;");
          break;
        case '>':
          b.append("&gt;");
          break;
        case '"':
          b.append("&quot;");
          break;
        case '\'':
          b.append("&apos;");
          break;
        case '\n':
          b.append("&#x0A;");
          break;
        case '\r':
          b.append("&#x0D;");
          break;
        case '\t':
          b.append("&#x09;");
          break;
        default:
          b.append(c);
      }
    }
    return b.toString();
  }

  private void save(StringBuilder sb, int depth) {
    String tab = "\t".repeat(depth);
    sb.append(tab).append('<').append(TAG_SECTION).append(' ').append(TAG_NAME).append("=\"").append(escape(name))
        .append("\">\n");
    for (Map.Entry<String, String> e : items.entrySet())
      sb.append(tab).append("\t<").append(TAG_ITEM).append(' ').append(TAG_KEY).append("=\"").append(escape(e.getKey()))
          .append("\" ").append(TAG_VALUE).append("=\"").append(escape(e.getValue())).append("\"/>\n");
    for (Map.Entry<String, String[]> e : arrayItems.entrySet()) {
      sb.append(tab).append("\t<").append(TAG_LIST).append(' ').append(TAG_KEY).append("=\"").append(escape(e.getKey()))
          .append("\">\n");
      if (e.getValue() != null)
        for (String v : e.getValue())
          sb.append(tab).append("\t\t<").append(TAG_ITEM).append(' ').append(TAG_VALUE).append("=\"").append(escape(v))
              .append("\"/>\n");
      sb.append(tab).append("\t</").append(TAG_LIST).append(">\n");
    }
    for (IDialogSettings s : sections.values())
      ((DialogSettings) s).save(sb, depth + 1);
    sb.append(tab).append("</").append(TAG_SECTION).append(">\n");
  }

  @Override
  public void save(String fileName) throws IOException {
    try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
      save(w);
    }
  }

  /** {@code DialogSettings.getOrCreateSection(settings, name)}. */
  public static IDialogSettings getOrCreateSection(IDialogSettings settings, String sectionName) {
    IDialogSettings section = settings.getSection(sectionName);
    if (section == null)
      section = settings.addNewSection(sectionName);
    return section;
  }
}
