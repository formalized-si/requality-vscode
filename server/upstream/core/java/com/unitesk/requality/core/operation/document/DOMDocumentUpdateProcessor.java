/*
 * Copyright 2011-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.core.operation.document;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.cli.RequalityCLI;

public abstract class DOMDocumentUpdateProcessor extends DocumentUpdateProcessor {
  public static int POSITION_BEFORE = 0, POSITION_AFTER = 1;

  private static Node getNextElement(Node node) {
    if (node == null) return null;
    if (node.getFirstChild() != null) return node.getFirstChild();
    if (node.getNextSibling() != null) return node.getNextSibling();
    node = node.getParentNode();
    if (node == null) return node;
    Node sib = node.getNextSibling();
    while (sib == null && node != null) {
      node = node.getParentNode();
      if (node != null) sib = node.getNextSibling();
    }
    return sib;
  }

  protected static Requirement createRequirement(TreeDB db, String name) throws Exception {
    return createRequirement(db, name, null);
  }

  protected static Requirement createRequirement(TreeDB db, String name, Requirement parent)
      throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
    if (parent == null) parent = (Requirement) db.getNode("/Requirements", Path.QID);
    Requirement req =
        (Requirement)
            parent.createChild(
                RequalityCLI.getNextId(parent, Requirement.TYPE_NAME, ""), Requirement.TYPE_NAME);
    if (name != null) {
      req.setName(name);
      req.commitAttributes();
    }
    return req;
  }

  private static void markLocation(Document doc, Node node, String locId, boolean first) {
    Element startTag = doc.createElement("span");
    Attr a = doc.createAttribute("class");
    a.setValue("requality_text id_" + locId);
    startTag.setAttributeNode(a);
    if (first) {
      Element hreftag = doc.createElement("a");
      a = doc.createAttribute("id");
      a.setValue("id_" + locId);
      hreftag.setAttributeNode(a);
      a = doc.createAttribute("name");
      a.setValue(locId);
      hreftag.setAttributeNode(a);
      a = doc.createAttribute("class");
      a.setValue("requality_id");
      hreftag.setAttributeNode(a);
      startTag.appendChild(hreftag);
    }
    Node par = node.getParentNode();
    par.appendChild(startTag);
    par.replaceChild(startTag, node);
    startTag.appendChild(node);
  }
  /**
   * Create new location and link it to exist requirement node
   *
   * @param w3cdoc Document to return after update process
   * @param n1 very first node - start of location
   * @param pos1 operational rule for start node. Can be POSITION_BEFORE - in this case element will
   *     be handled or POSITION_AFTER, so element n1 will not be handled
   * @param n2 latest node - end of location
   * @param pos2 operational rule for end node. Can be POSITION_AFTER - in this case element will be
   *     handled or POSITION_BEFORE, so element n2 will not be handled
   * @param doc document node to create location in
   * @param parent requirement node to add location for
   */
  protected static Location createLocation(
      Document w3cdoc,
      Node n1,
      int pos1,
      Node n2,
      int pos2,
      com.unitesk.requality.nodetype.Document doc,
      Requirement parent) {
    Location l = doc.createLocation();
    addLocation(w3cdoc, n1, pos1, n2, pos2, l.getSUUId());
    parent.addLocation(l.getUUId());
    return l;
  }
  /**
   * Adds a part of dom to exist location
   *
   * @param w3cdoc Document to return after update process
   * @param n1 very first node - start of location
   * @param pos1 operational rule for start node. Can be POSITION_BEFORE - in this case element will
   *     be handled or POSITION_AFTER, so element n1 will not be handled
   * @param n2 latest node - end of location
   * @param pos2 operational rule for end node. Can be POSITION_AFTER - in this case element will be
   *     handled or POSITION_BEFORE, so element n2 will not be handled
   * @param locId id of location
   */
  protected static void addLocation(
      Document w3cdoc, Node n1, int pos1, Node n2, int pos2, String locId) {
    Node pos = n1;
    boolean first = true;
    boolean iter = false;
    while (pos != null && pos != n2.getNextSibling() || !iter) {
      iter = true;
      if (pos == n1 && pos1 == POSITION_AFTER) pos = getNextElement(pos);
      if (pos == n2 && pos2 == POSITION_BEFORE) pos = null;
      if (pos != null) {
        if (!pos.hasChildNodes()
            && !pos.getTextContent().replace("\n", "").trim().equals("")
            && !isMarked(pos)) {
          markLocation(w3cdoc, pos, locId, first);
          first = false;
        }
      }
      pos = getNextElement(pos);
    }
  }

  protected static com.unitesk.requality.nodetype.Document getDocumentNode(
      TreeNode rootNode, String docid) {
    return (com.unitesk.requality.nodetype.Document) rootNode.getTreeDB().getNode(docid, Path.QID);
  }

  protected static Requirement getRequirementNode(TreeNode rootNode, String reqid) {
    return (Requirement) rootNode.getTreeDB().getNode(reqid, Path.QID);
  }

  protected static Location getLocation(TreeNode rootNode, String uuid) {
    return (Location) rootNode.getTreeDB().getNode(UUID.fromString(uuid));
  }

  private static boolean isMarked(Node pos) {
    Node par = pos.getParentNode();
    if (par == null) return false;
    if (!par.getNodeName().equals("span")) return false;
    Attr a = (Attr) par.getAttributes().getNamedItem("class");
    return a != null && a.getValue().startsWith("requality_text id_");
  }

  @Override
  protected boolean process(
      com.unitesk.requality.nodetype.Document oldDoc,
      com.unitesk.requality.nodetype.Document doc,
      Reader readerold,
      Reader reader,
      Writer writer,
      TreeNode rootNode,
      PrintStream ps) {
    Document d1, d2;
    try {
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      d1 = dBuilder.parse(new InputSource(readerold));
      if (isCanceled()) return false;
      d2 = dBuilder.parse(new InputSource(reader));
      if (isCanceled()) return false;
      Document ret = process(oldDoc, doc, d1, d2, rootNode);
      if (isCanceled()) return false;
      TransformerFactory tFactory = TransformerFactory.newInstance();
      Transformer transformer = tFactory.newTransformer();

      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      DOMSource source = new DOMSource(ret);
      StreamResult result = new StreamResult(writer);
      if (isCanceled()) return false;
      transformer.transform(source, result);
      if (isCanceled()) return false;
      ps.append("Processing completed");
      return true;
    } catch (IOException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (TransformerConfigurationException e) {
      e.printStackTrace();
    } catch (TransformerException e) {
      e.printStackTrace();
    }
    return false;
  }

  protected abstract Document process(
      com.unitesk.requality.nodetype.Document oldDoc, com.unitesk.requality.nodetype.Document doc, Document olddom, Document newdom, TreeNode rootNode);
}
