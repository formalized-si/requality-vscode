/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
package com.unitesk.requality.tool;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Stack;
import java.util.UUID;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.create.CreateNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class Converter {
  private static QName REQ_START = new QName("span");
  private static QName REQ_END = new QName("span");

  // Stack element values
  private static String TAG_REQ = "1";
  private static String TAG_OTHER = "0";

  private static XMLEventFactory ef = XMLEventFactory.newInstance();

  // Attribute values
  private Stack<String> stack = new Stack<>();
  private Queue<XMLEvent> xmlQueue = new LinkedList<>();
  private Map<String, ArrayList<XMLEvent>> locToXml = new HashMap<>();

  // Results
  private Map<String, String[]> reqToLocations = new HashMap<>();

  private String curLocId = null;
  private boolean firstTag = true;

  public static void usage() {
    RLogger.logInfo("Usage: java -jar converter.jar <input file> <output file>");
  }

  public static void main(String[] args) {
    String file = args[0];
    // String file =
    // "/home/yuriy/svn/requality/distr/error/baselib-pam-start.html";
    if (!convert(file, file + ".xhtml")) {
      RLogger.logInfo("ERROR! " + file);
      ;
    }
  }

  @Deprecated
  public static boolean convert(String input, String output) {
    File inFile = new File(input);
    if (inFile.exists() && inFile.isFile()) {
      FileReader reader = null;
      FileWriter writer = null;
      try {
        File tempFile = File.createTempFile("cvrt", ".html");
        reader = new FileReader(inFile);
        writer = new FileWriter(tempFile);
        //TODO add convert
        // DocumentImporter.cleanHtml(reader, reader.getEncoding(), writer);
        writer.flush();
        File outFile = new File(output);

        if (outFile.getParentFile() != null) {
          outFile.getParentFile().mkdirs();
        }

        FileReader fr = new FileReader(tempFile);
        FileWriter fw = new FileWriter(outFile);
        try{
          new Converter().convert(fr, fw);
        }
        finally{
          fr.close();
          fw.close();
        }

        tempFile.delete();
        return true;
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
      finally{
        if(reader!=null)
          try {
            reader.close();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        if(writer!=null)
          try {
            writer.close();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
      }
    }
    return false;
  }

  /**
   * @param fReader
   * @param out
   * @return Map<requirement id, location ids []>
   */
  public Map<String, String[]> convert(Reader fReader, Writer out) {
    try {
      oldToNewLocMap.clear();
      reqToLocations.clear();
      locToXml.clear();
      XMLEventReader eReader = XMLInputFactory.newInstance().createXMLEventReader(fReader);

      XMLEventWriter xmlWriter = XMLOutputFactory.newInstance().createXMLEventWriter(out);

      while (eReader.hasNext()) {
        XMLEvent event = eReader.nextEvent();
        processEvent(event);
      }

      for (XMLEvent xml : xmlQueue) {
        xmlWriter.add(xml);
      }
      fReader.close();
      xmlWriter.flush();
      xmlWriter.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return reqToLocations;
  }

  public void store(Map<String, String[]> rl, String docId, TreeDB db, Requirement groupNode) {
    if (db.getNode(docId, Path.QID) == null
        || !db.getNode(docId, Path.QID).getType().equals(Document.TYPE_NAME)) {
      throw new RuntimeException("Document: " + docId + " in not in base");
    }
    boolean started = db.startTransaction("Converter", true);
    if (started) {
      try {
        for (String reqId : rl.keySet()) {
          String fullReqId = groupNode.getQualifiedId() + reqId;
          Requirement req = (Requirement) touchNode(db, fullReqId);
          for (String slocId : rl.get(reqId)) {
            UUID locId = null;
            String locQId = docId + "/" + locId;
            TreeNode node = null;
            try {
              node = db.getNode(locQId, Path.QID);
              if(node!=null)
                locId = node.getUUId();
            } catch (NodeNotFoundException e) {
              // nf
            }
            if (node == null) {
              // TODO use document.createLocation()
              Location loc =
                  (Location) CreateNode.call(db.getNode(docId, Path.QID),Location.TYPE_NAME, slocId);
              loc.setDescription(this.getLocationDescription(slocId));
              loc.commitAttributes();
              locId = loc.getUUId();
            }
            if(locId!=null)
              req.addLocation(locId);
          }
          req.commitAttributes();
        }
        if(started) {
          db.commit();
          started = false;
        }
      }finally {
        if(started)
          db.rollback();
      }
    }
  }

  public void store(Map<String, String[]> rl, String docId, TreeDB db) {
    this.store(
        rl, docId, db, (Requirement) db.getNode(Requirement.getTypeRootQId(), Path.QID | Path.UV));
  }

  private static TreeNode touchNode(TreeDB db, String qId) {
    if (qId.equals("/")) {
      return db.getRootNode();
    }
    TreeNode node = null;
    try {
      node = db.getNode(qId, Path.QID | Path.UV);
    } catch (NodeNotFoundException e) {

    }
    if (node == null && node != db.getRootNode()) {
      int ind = qId.lastIndexOf("/");
      String parId = ind > 1 ? qId.substring(0, ind) : "";
      String id = ind >= 0 ? qId.substring(ind + 1) : "";

      TreeNode parent = touchNode(db, parId);
      node = CreateNode.call(parent,Requirement.TYPE_NAME,id);
    }
    return node;
  }

  private void processEvent(XMLEvent event) {
    switch (event.getEventType()) {
      case XMLStreamConstants.START_ELEMENT:
        processStartElement(event.asStartElement());
        break;
      case XMLStreamConstants.CHARACTERS:
        processCharacters(event.asCharacters());
        break;
      case XMLStreamConstants.END_ELEMENT:
        processEndElement(event.asEndElement());
        break;
      default:
        // out.add( event );
        break;
    }
  }

  private void addStartElement(StartElement se, String type) {
    if (curLocId != null) {
      putDescXml(se);
    }
    xmlQueue.add(se);
    stack.push(type);
  }

  private void putDescXml(XMLEvent se) {
    if (locToXml.get(curLocId) == null) {
      locToXml.put(curLocId, new ArrayList<XMLEvent>());
    }
    locToXml.get(curLocId).add(se);
  }

  private static String tempId = null;
  private Map<String, String> oldToNewLocMap = new HashMap<>();

  private String startReq(StartElement xmlElem, String type) {
    tempId = null;
    StartElement newTag = cleanReqTag(xmlElem);
    if (tempId == null) {
      xmlQueue.add(newTag);
      stack.push(TAG_REQ);
    }
    if (tempId != null && oldToNewLocMap.containsKey(tempId)) curLocId = oldToNewLocMap.get(tempId);
    else curLocId = UUID.randomUUID().toString();
    if (tempId != null) oldToNewLocMap.put(tempId, curLocId);
    else putDescXml(newTag);
    return curLocId;
  }

  private static StartElement createStartReqTag(String locId) {
    Attribute a = ef.createAttribute("class", "requality_text id_" + locId);
    ArrayList<Attribute> attrs = new ArrayList<>();
    attrs.add(a);
    StartElement spanBegin = ef.createStartElement(REQ_START, attrs.iterator(), null);
    return spanBegin;
  }

  private static StartElement cleanReqTag(StartElement xmlElem) {
    ArrayList<Attribute> newAttrs = new ArrayList<>();
    @SuppressWarnings("unchecked")
    Iterator<Attribute> i = xmlElem.getAttributes();
    while (i.hasNext()) {
      Attribute at = i.next();
      if (at.getName().equals(new QName("id"))) {
        continue;
      }
      if (at.getName().equals(new QName("class"))) {
        String val = at.getValue();
        if (val != null && val.indexOf("id_") > -1) {
          tempId = val.substring(val.indexOf("id_") + 3);
        } else if (val != null
            && (val.equals("req")
                || val.equals("struct")
                || val.equals("uncheckable")
                || val.startsWith("requality_text"))) continue;
      }
      newAttrs.add(at);
    }
    StartElement newTag =
        ef.createStartElement(xmlElem.getName(), newAttrs.iterator(), xmlElem.getNamespaces());
    return newTag;
  }

  private void processStartElement(StartElement xmlElem) {

    Attribute attr = xmlElem.getAttributeByName(new QName("class"));
    if (attr == null) {
      addStartElement(xmlElem, TAG_OTHER);
      return;
    }
    if (xmlElem.getName().getLocalPart().equals("a") && removeSpan && firsta == 1) {
      String cl = xmlElem.getAttributeByName(new QName("class")).getValue();
      if (cl != null && cl.equals("requality_id")) {
        firsta++;
        return;
      }
    }
    String attrValue = attr.getValue();
    if (attrValue.equals("req")
        || attrValue.equals("struct")
        || attrValue.equals("uncheckable")
        || attrValue.contains("requality_text")) {
      if (xmlElem.getName().getLocalPart().equals("span") && attrValue.contains("requality_text")) {
        removeSpan = true;
        firsta = 1;
      }
      startReq(xmlElem, attrValue);

      String reqIds[] = null;
      if (attrValue.indexOf("id_") > -1)
        reqIds = attrValue.substring(attrValue.indexOf("id_") + 3).split(";");
      // Store requirement to location map
      Attribute aReqIds = xmlElem.getAttributeByName(new QName("id"));
      if (reqIds == null) {
        if (aReqIds == null) {
          return;
        }
        reqIds = aReqIds.getValue().split(";");
      }
      for (String reqId : reqIds) {
        String r = "/" + reqId.replace('.', '/');
        if (reqToLocations.containsKey(r)) {
          ArrayList<String> newlst = new ArrayList<>(Arrays.asList(reqToLocations.get(r)));
          if (!newlst.contains(curLocId)) newlst.add(curLocId);
          reqToLocations.put(r, newlst.toArray(new String[0]));
        } else {
          reqToLocations.put(r, new String[] {curLocId});
        }
      }
      return;
    }

    addStartElement(xmlElem, TAG_OTHER);
  }

  boolean removeSpan = false;
  short firsta = 0;

  private void processEndElement(EndElement xmlElem) {
    if (xmlElem.getName().getLocalPart().equals("a") && removeSpan && firsta == 2) {
      firsta = 3;
      return;
    }
    if (xmlElem.getName().getLocalPart().equals("span") && removeSpan) {
      removeSpan = false;
      firsta = 0;
      curLocId = null;
      firstTag = true;
      return;
    }
    String top = stack.pop();
    if (top.equals(TAG_REQ)) {
      putDescXml(xmlElem);
      curLocId = null;
      firstTag = true;
    }
    xmlQueue.add(xmlElem);
    if (curLocId != null) {
      putDescXml(xmlElem);
    }
  }

  private void processCharacters(Characters xmlText) {
    String text = xmlText.getData();
    if (text.trim().length() == 0) return;
    text = text.replace("&lt;", "<");
    text = text.replace("&gt;", ">");
    text = text.replace("&amp;", "&");
    text = text.replace("&quot;", "\"");
    text = text.replace("&apos;", "'");
    XMLEvent chars = ef.createCharacters(text);
    putDescXml(chars);
    if (curLocId != null) {
      xmlQueue.add(createStartReqTag(curLocId));
      if (firstTag) {
        firstTag = false;
        xmlQueue.add(createAStartElement(curLocId));
        xmlQueue.add(ef.createEndElement(new QName("a"), null));
      }
      xmlQueue.add(chars);
      xmlQueue.add(ef.createEndElement(REQ_END, null));
    } else {
      xmlQueue.add(chars);
    }
  }

  private static StartElement createAStartElement(String locId) {
    ArrayList<Attribute> attrs = new ArrayList<>();
    attrs.add(ef.createAttribute("id", "id_" + locId));
    attrs.add(ef.createAttribute("name", locId));
    attrs.add(ef.createAttribute("class", "requality_id"));
    return ef.createStartElement(new QName("a"), attrs.iterator(), null);
  }

  public String getLocationDescription(String locUUId) {
    try {
      StringWriter out = new StringWriter();
      XMLEventWriter xmlWriter;

      xmlWriter = XMLOutputFactory.newInstance().createXMLEventWriter(out);
      List<XMLEvent> events = locToXml.get(locUUId);
      for (XMLEvent e : events) {
        xmlWriter.add(e);
      }
      xmlWriter.flush();
      xmlWriter.close();
      return out.toString();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return null;
  }
}
