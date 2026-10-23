package com.unitesk.requality.vscode.ui.workbench;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * {@code org.eclipse.ui.XMLMemento}: a memento of the XML format of the Eclipse workbench state files (an element per
 * memento, its type as element name, its id as the {@code id} attribute, the values as attributes, the children as
 * child elements). Backed by the JDK DOM.
 */
public final class XMLMemento implements IMemento {
  /** The tag of the id attribute. */
  public static final String TAG_ID = "id";

  private final Document document;
  private final Element element;

  private XMLMemento(Document document, Element element) {
    this.document = document;
    this.element = element;
  }

  /** {@code XMLMemento.createWriteRoot(type)}: a new memento to write. */
  public static XMLMemento createWriteRoot(String type) {
    try {
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      Element root = doc.createElement(type);
      doc.appendChild(root);
      return new XMLMemento(doc, root);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** {@code XMLMemento.createReadRoot(reader)}: the memento of a document read from the reader. */
  public static XMLMemento createReadRoot(Reader reader) throws WorkbenchException {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      Document doc = f.newDocumentBuilder().parse(new org.xml.sax.InputSource(reader));
      return new XMLMemento(doc, doc.getDocumentElement());
    } catch (Exception e) {
      throw new WorkbenchException("Could not read the memento", e);
    }
  }

  /** {@code XMLMemento.save(writer)}: writes the memento as an XML document. */
  public void save(Writer writer) throws IOException {
    try {
      Transformer t = TransformerFactory.newInstance().newTransformer();
      t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      t.setOutputProperty(OutputKeys.INDENT, "yes");
      t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
      t.transform(new DOMSource(document), new StreamResult(writer));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /** The memento as an XML string (for the tests and the messages). */
  @Override
  public String toString() {
    StringWriter w = new StringWriter();
    try {
      save(w);
    } catch (IOException e) {
      return element.getNodeName();
    }
    return w.toString();
  }

  @Override
  public IMemento createChild(String type) {
    Element child = document.createElement(type);
    element.appendChild(child);
    return new XMLMemento(document, child);
  }

  @Override
  public IMemento createChild(String type, String id) {
    Element child = document.createElement(type);
    child.setAttribute(TAG_ID, id);
    element.appendChild(child);
    return new XMLMemento(document, child);
  }

  @Override
  public Float getFloat(String key) {
    String v = getString(key);
    try {
      return v == null ? null : Float.valueOf(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public Integer getInteger(String key) {
    String v = getString(key);
    try {
      return v == null ? null : Integer.valueOf(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public String getString(String key) {
    return element.hasAttribute(key) ? element.getAttribute(key) : null;
  }

  @Override
  public Boolean getBoolean(String key) {
    String v = getString(key);
    return v == null ? null : Boolean.valueOf(v);
  }

  @Override
  public String getTextData() {
    return element.getTextContent();
  }

  @Override
  public String getType() {
    return element.getNodeName();
  }

  @Override
  public String getID() {
    return element.hasAttribute(TAG_ID) ? element.getAttribute(TAG_ID) : null;
  }

  @Override
  public IMemento getChild(String type) {
    Element e = firstChild(type);
    return e == null ? null : new XMLMemento(document, e);
  }

  @Override
  public IMemento[] getChildren(String type) {
    List<IMemento> out = new ArrayList<>();
    NodeList nodes = element.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++)
      if (nodes.item(i) instanceof Element && nodes.item(i).getNodeName().equals(type))
        out.add(new XMLMemento(document, (Element) nodes.item(i)));
    return out.toArray(new IMemento[0]);
  }

  @Override
  public IMemento[] getChildren() {
    List<IMemento> out = new ArrayList<>();
    NodeList nodes = element.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++)
      if (nodes.item(i) instanceof Element)
        out.add(new XMLMemento(document, (Element) nodes.item(i)));
    return out.toArray(new IMemento[0]);
  }

  @Override
  public String[] getAttributeKeys() {
    NamedNodeMap attrs = element.getAttributes();
    String[] keys = new String[attrs.getLength()];
    for (int i = 0; i < keys.length; i++)
      keys[i] = attrs.item(i).getNodeName();
    return keys;
  }

  @Override
  public void putFloat(String key, float value) {
    putString(key, Float.toString(value));
  }

  @Override
  public void putInteger(String key, int value) {
    putString(key, Integer.toString(value));
  }

  @Override
  public void putString(String key, String value) {
    if (value != null)
      element.setAttribute(key, value);
  }

  @Override
  public void putBoolean(String key, boolean value) {
    putString(key, String.valueOf(value));
  }

  /** Copies the attributes and the children of the memento (of a written root) into this memento. */
  @Override
  public void putMemento(IMemento memento) {
    if (!(memento instanceof XMLMemento))
      throw new IllegalArgumentException("Not an XML memento");
    Element source = ((XMLMemento) memento).element;
    NamedNodeMap attrs = source.getAttributes();
    for (int i = 0; i < attrs.getLength(); i++) {
      Attr a = (Attr) attrs.item(i);
      element.setAttribute(a.getName(), a.getValue());
    }
    NodeList children = source.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      element.appendChild(document.importNode(n, true));
    }
  }

  @Override
  public void putTextData(String data) {
    element.appendChild(document.createTextNode(data));
  }

  private Element firstChild(String type) {
    NodeList nodes = element.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++)
      if (nodes.item(i) instanceof Element && nodes.item(i).getNodeName().equals(type))
        return (Element) nodes.item(i);
    return null;
  }
}
