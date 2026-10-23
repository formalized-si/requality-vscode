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

import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.XMLEvent;

import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;

/** Class for automatically extracting headers into requirements */
public class SAXTextExtractor extends SAXDocumentProcessor {
  private static final String SEPARATOR = "|";
  private int charCnt;
  private StringBuilder testBuffer = new StringBuilder();
  //Map<Integer, Requirement> startedTags = new HashMap<Integer, Requirement>();
  private LinkedList<String> started = new LinkedList<>();
  private LinkedList<UUID> startedUUID = new LinkedList<>();
  private LinkedList<String> ignoredTags = new LinkedList<>();
  private LinkedList<Integer> startedIndex = new LinkedList<>();
  private boolean bodyStarted = false;
  public Map<String, List<NodeDesc>> newLocations = new ConcurrentHashMap<>();
  private List<Location> saveLocs = new LinkedList<>();
  boolean lastSpace = false, lastText = false;
  private TreeDB db;
  public boolean processEvent(
      XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer, TreeNode rootNode, PrintStream ps)
      throws Exception {
    if (event.isStartElement()) {
      String tagName = getLocalTagName(event).toLowerCase();
      if(tagName.equals("body"))
        bodyStarted = true;
      lastText = false;
    } else if (event.isEndElement()) {
      EndElement ee = event.asEndElement();
      String tagName = ee.getName().getLocalPart().toLowerCase();
      if(tagName.equals("body"))
      {
        bodyStarted = false;
      }
      lastText = false;
    } else if (event.isCharacters()) {
      String content = event.asCharacters().getData();
      if(bodyStarted) {
        String s = content;
//        if(lastText && lastSpace && s.startsWith(" ")) {
//            charCnt--;//correct spaces after removal of some tags
//            testBuffer.setLength(testBuffer.length()-1);
//        }
//        while(s.indexOf("  ")>-1)
//          s = s.replace("  ", " ");
        charCnt+=(s.length());
        testBuffer.append(s);
        lastSpace = false;
        if(s.endsWith(" "))
          lastSpace = true;
        lastText = true;
      }
    } else {
      lastText = false;
    }
    writer.add(event);
    return true;
  }
  public String getText() {
    return testBuffer.toString();
  }
  private static String INCORRECT_XMLNS="xmlns=";

  @Override
  protected void initProcess(TreeNode root) {
    newLocations.clear();
    bodyStarted = false;
    charCnt = 0;
    started.clear();
    lastText = lastSpace = false;
    startedUUID.clear();
    db = root.getTreeDB();
    if(root instanceof Document)
      doc = (Document) root;
  }

  @Override
  protected void stopProcess() {//Create nodes when running from interface
//    for(Entry<String, List<NodeDesc>> en:newLocations.entrySet()) {
//      for(NodeDesc nd:en.getValue())
//        nd.createNode(db);
//    }
  }
}
