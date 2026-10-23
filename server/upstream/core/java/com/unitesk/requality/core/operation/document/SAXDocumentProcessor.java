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
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IRelatedFilesProvider;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.cli.RequalityCLI;

/** Abstract class for SAX document processors */
public abstract class SAXDocumentProcessor extends DocumentProcessor {
  protected static void startTag(
      XMLEventWriter writer, XMLEventFactory eventFactory, String tagName) throws Exception {
    startTag(writer, eventFactory, tagName, new HashMap<String, String>());
  }

  protected static void startTag(
      XMLEventWriter writer,
      XMLEventFactory eventFactory,
      String tagName,
      Map<String, String> attrib)
      throws Exception {
    if (writer == null) throw new Exception("Document processing is not started");
    writer.add(eventFactory.createStartElement("", "", tagName));
    for (String s : attrib.keySet()) writer.add(eventFactory.createAttribute(s, attrib.get(s)));
  }

  protected void startTag(String tagName, Map<String, String> attrib) throws Exception {
    startTag(writer, eventFactory, tagName, attrib);
  }

  protected void startTag(String tagName) throws Exception {
    startTag(writer, eventFactory, tagName);
  }

  protected static void endTag(XMLEventWriter writer, XMLEventFactory eventFactory, String tagName)
      throws Exception {
    if (writer == null) throw new Exception("Document processing is not started");
    writer.add(eventFactory.createEndElement("", "", tagName));
  }

  protected void endTag(String tagName) throws Exception {
    endTag(writer, eventFactory, tagName);
  }

  protected static void setDescription(TreeNode node, HtmlValueProvider newDesc, URI dpath) {
    node.setDescription(RequalityCLI.updateImages(dpath, newDesc.getRawValue().getValue().toString(), (IRelatedFilesProvider) newDesc.getAdded(),true));
  }

  protected static String startRequirementFragment(
      XMLEventWriter writer,
      XMLEventFactory eventFactory,
      Document doc,
      Requirement req,
      String locid)
      throws Exception {
    if (writer == null) throw new Exception("Document processing is not started");
    String locationId = locid;
    Location location = null;
    if (locationId == null) {
      location = doc.createLocation();
      locationId = location.getId().toString();
    }
    writer.add(eventFactory.createStartElement("", "", "span"));
    writer.add(eventFactory.createAttribute("class", "requality_text id_" + locationId));
    writer.add(eventFactory.createStartElement("", "", "a"));
    writer.add(eventFactory.createAttribute("name", locationId));
    writer.add(eventFactory.createAttribute("id", "id_" + locationId));
    writer.add(eventFactory.createAttribute("class", "requality_id"));
    writer.add(eventFactory.createEndElement("", "", "a"));
    if (locid == null&&location!=null) req.addLocation(location.getUUId());
    return locationId;
  }

  protected String startRequirementFragment(Requirement req) throws Exception {
    return startRequirementFragment(req, null);
  }

  protected String startRequirementFragment(Requirement req, String locid) throws Exception {
    return startRequirementFragment(writer, eventFactory, doc, req, locid);
  }

  protected static void endRequirementFragment(
      XMLEventWriter writer2, XMLEventFactory eventFactory2) throws Exception {
    if (writer2 == null) throw new Exception("Document processing is not started");
    writer2.add(eventFactory2.createEndElement("", "", "span"));
  }

  protected void endRequirementFragment() throws Exception {
    endRequirementFragment(writer, eventFactory);
  }

  protected static Requirement createRequirement(TreeDB db, String name, Requirement parent)
      throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
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

  protected static Requirement createRequirement(TreeDB db, String name) throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
    return createRequirement(db, name, (Requirement) db.getNode("/Requirements", Path.QID));
  }

  protected static Requirement createRequirement(TreeDB db) throws Exception {
    return createRequirement(db, null);
  }

  protected Requirement createRequirement(String name, Requirement parent) throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return createRequirement(doc.getTreeDB(), name, parent);
  }

  protected Requirement createRequirement(String name) throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return createRequirement(doc.getTreeDB(), name);
  }

  protected Requirement createRequirement() throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return createRequirement(doc.getTreeDB());
  }

  protected static Comment createComment(TreeDB db, String description, Requirement parent)
      throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
    Comment comm =
        (Comment)
            parent.createChild(
                RequalityCLI.getNextId(parent, Comment.TYPE_NAME, ""), Comment.TYPE_NAME);
    comm.setDescription(description);
    comm.commitAttributes();
    return comm;
  }

  protected static Comment createComment(TreeDB db, String description) throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
    return createComment(db, description, (Requirement) db.getNode("/Requirements", Path.QID));
  }

  protected Comment createComment(String description, Requirement parent) throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return createComment(doc.getTreeDB(), description, parent);
  }

  protected Comment createComment(String description) throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return createComment(doc.getTreeDB(), description);
  }

  protected static boolean removeElement(TreeDB db, String id) throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
    removeElement(getElement(db, id));
    return true;
  }

  protected boolean removeElement(String id) throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return removeElement(doc.getTreeDB(), id);
  }

  protected static boolean removeElement(TreeNode node) throws Exception {
    if (node == null) throw new Exception("Document processing is not started");
    node.delete();
    return true;
  }

  protected static boolean moveElement(TreeNode node, TreeNode newParent) throws Exception {
    node.setParent(newParent);
    return true;
  }

  protected TreeNode getElement(String qualifiedId) throws Exception {
    if (doc == null) throw new Exception("Document processing is not started");
    return getElement(doc.getTreeDB(), qualifiedId);
  }

  protected static TreeNode getElement(TreeDB db, String qualifiedId) throws Exception {
    if (db == null) throw new Exception("Document processing is not started");
    try {
      TreeNode node = db.getNode(qualifiedId, Path.QID | Path.UV);
      return node;
    } catch (Exception e) {
    }
    return null;
  }

  protected static String getLocalTagName(XMLEvent event) {
    if (!event.isStartElement() && !event.isEndElement()) return null;
    if (event.isStartElement()) return event.asStartElement().getName().getLocalPart();
    if (event.isEndElement()) return event.asEndElement().getName().getLocalPart();
    return null;
  }

  protected static String getTagAttribute(XMLEvent event, String attribName, String defValue) {
    if (!event.isStartElement()) return null;
    javax.xml.stream.events.Attribute a =
        event.asStartElement().getAttributeByName(QName.valueOf(attribName));
    if (a == null) return null;
    return a.getValue();
  }

  protected static String getTagAttribute(XMLEvent event, String attribName) {
    return getTagAttribute(event, attribName, null);
  }

  XMLEventWriter writer;
  XMLEventFactory eventFactory;

  @Override
  protected boolean process(Document doc, Reader in, Writer out, TreeNode rootNode, PrintStream ps)
      throws IOException {
    try {
      XMLInputFactory xif = XMLInputFactory.newInstance();
      xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
      xif.setProperty(XMLInputFactory.IS_COALESCING, true);
      XMLEventReader reader = xif.createXMLEventReader(in);
      writer = XMLOutputFactory.newInstance().createXMLEventWriter(out);
      eventFactory = XMLEventFactory.newInstance();
      initProcess(rootNode);
      while (reader.hasNext()) {
        XMLEvent event = (XMLEvent) reader.next();
        try {
          if (!processEvent(event, eventFactory, writer, rootNode, ps) || isCanceled()) return false;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      stopProcess();
      writer = null;
      eventFactory = null;
      return true;
    } catch (XMLStreamException e) {
      e.printStackTrace();
    } catch (FactoryConfigurationError e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
    writer = null;
    eventFactory = null;
    return false;
  }

  protected abstract void stopProcess();

  protected abstract void initProcess(TreeNode root);

  protected abstract boolean processEvent(
      XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer, TreeNode rootNode, PrintStream ps)
      throws Exception;
}
