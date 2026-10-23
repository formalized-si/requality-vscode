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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.tool.cli.RequalityCLI;

/** Class for automatically extracting headers into requirements */
public class FormLocationDescriptions extends SAXDocumentProcessor {
  private static final String SEPARATOR = "|";
  private int charCnt;
  private StringBuilder testBuffer = new StringBuilder();
  //Map<Integer, Requirement> startedTags = new HashMap<Integer, Requirement>();
  private boolean bodyStarted = false;
  public boolean isLocExternal = false;
  boolean lastSpace = false, lastText = false;
  private TreeDB db;
  
  private class MarkupDescription{
    public long start, end;
    public Location relatedLoc;
    public int locIndex = -1;
    public StringBuilder sb = new StringBuilder();
  }
  
  private Map<Long, List<MarkupDescription>> locsMap = null;
  private List<MarkupDescription> activeLocations = new LinkedList<>();
  
  private Long lastPos = null;
  private List<MarkupDescription> lastPosList = null;
  private Iterator<Entry<Long, List<MarkupDescription>>> lastIter = null;
  
  private MarkupDescription gatherDescription(Location loc, Object rangeDesc, int locPos) {
    MarkupDescription md = new MarkupDescription();
    md.locIndex = locPos;
    md.relatedLoc = loc;
    String range = (String) rangeDesc;
    int sepInd = range.indexOf(Location.FRAGMENT_SEPARATOR);
    md.start = Long.parseLong(range.substring(0, sepInd));
    md.end = Long.parseLong(range.substring(sepInd+1));
    return md;
  }
  @Override
  public void process(Document doc, TreeNode rootNode, PrintStream ps, Writer writer, IProgressMonitor monitor)
      throws IOException {
    locsMap = new HashMap<>();
    for(TreeNode node: doc.getActiveWorkChildren())
    {
      Location loc = (Location) node;
      List<Object> lv = loc.attrs().getListAttributeValue(Location.ATTR_MARKUP);
      int i = 0;
      for(Object d:lv) {
        MarkupDescription markupDecs = gatherDescription (loc, d, i++);
        List<MarkupDescription> descrs = locsMap.get(markupDecs.start);
        if(descrs == null) {
          descrs = new LinkedList<>();
          locsMap.put(markupDecs.start, descrs);
        }
        descrs.add(markupDecs);
      }
    }
    locsMap = Collections.unmodifiableMap(locsMap);
    lastIter = locsMap.entrySet().iterator();
    super.process(doc, rootNode, ps, writer, monitor);
  }
  public boolean processEvent(
      XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer, TreeNode rootNode, PrintStream ps)
      throws Exception {
    writer.add(event);//do not alter doc
      
    if (event.isStartElement()) {
      StartElement se = event.asStartElement();
      String tagName = getLocalTagName(event).toLowerCase();
      lastText = false;
      if(bodyStarted)
        toString(event);
      if(tagName.equals("body"))
        bodyStarted = true;
    } else if (event.isEndElement()) {
      EndElement ee = event.asEndElement();
      String tagName = ee.getName().getLocalPart().toLowerCase();
      if(tagName.equals("body"))
      {
        bodyStarted = false;
      }else {
        lastText = false;
        if(bodyStarted)
          toString(event);
      }
    } else if (event.isCharacters()) {
      String content = event.asCharacters().getData();
      if(bodyStarted) {
        String s = content;
//        if(lastText && lastSpace && (s.startsWith(" "))) {
//            //charCnt--;//correct spaces after removal of some tags
//            //tends to be incorrect, for all cases removes excess chars
//            testBuffer.setLength(testBuffer.length()-1);
//        }
        if(bodyStarted)
          toString(event);
        charCnt+=(s.length());
        testBuffer.append(s);
        lastSpace = false;
        if(s.endsWith(" "))
          lastSpace = true;
        lastText = true;
      }
    } else {
      lastText = false;
      if(bodyStarted)
        toString(event);
    }
    return true;
  }
  
  private void setLocDescription(Location loc, Document doc, String desc) {
    TempFolderFilesProvider added = TempFolderFilesProvider.getOne(null, false);
    //TODO hrefs?
    String val = RequalityCLI.updateImages(doc.getNodeResourcesPath(), desc, added,true);
    loc.attrs().setAttribute(new AttributeDefinition(loc.getUUId(), TreeNode.ATTR_DESCRIPTION, AttributeAvailability.LOCAL,
      new HtmlValueProvider(AttributeType.HTML, val, null, added),Flag.HIDDEN));
  }
  private static String INCORRECT_XMLNS="xmlns=";
  private String replaceStrangePS(String description) {
    //will not work on ns 
    if(description == null)
      return description;
    String lower = description.toLowerCase();
    int pos = lower.indexOf(INCORRECT_XMLNS);
    StringBuilder sb = new StringBuilder();
    int last = 0;
    while(pos>-1) {
      sb.append(description.substring(last, pos));
      int q1 = description.indexOf("'", pos+1);
      int q2 = description.indexOf("\"", pos+1);
      if(q1 == -1) q1 = description.length();
      if(q2 == -1) q2 = description.length();
      pos = Math.min(q1, q2);//skip first space if any
      int pend = description.indexOf(">", pos+1);
      int whitespace = description.indexOf(" ", pos+1);
      if(whitespace == -1)
        whitespace = description.length();
      last = Math.min(pend, whitespace);
      pos = lower.indexOf("xmlns=", pos+1);
    }
    sb.append(description.substring(last));
    return sb.toString(); 
  }

  private void toString(XMLEvent event) {
    List<MarkupDescription> torem = null;

    String newText = null;
    StartElement se = null;
    EndElement ee = null;
    if(event.isStartDocument() || event.isEndDocument())
      return;
    if(event.isStartElement())
      se = event.asStartElement();
    else if (event.isEndElement())
      ee = event.asEndElement();
    else
      newText = event.toString();
    for(MarkupDescription md : activeLocations) {
      StringBuilder sb = md.sb;
      if(se!=null){
        sb.append("<");
        sb.append(se.getName().getLocalPart());
        Iterator<Attribute> it = se.getAttributes();
        while(it.hasNext()){
          Attribute a = it.next();
          String name = a.getName().getLocalPart();
  //        if(!name.equals("class")) {
          sb.append(" ");
          sb.append(name);
          sb.append("=\"");
          sb.append(a.getValue());
          sb.append("\"");
  //        }
        }
        sb.append(">");
      } else if(ee!=null){
        sb.append("</");
        sb.append(ee.getName().getLocalPart());
        sb.append(">");
      } else if (newText!=null){
        addDescriptionPart(md, newText);
        if(newText.length()>=md.end) {
          if(torem == null)
            torem = new LinkedList<>();
          torem.add(md);
        }
      }
    }
    if(newText!=null)
    {
      int textLen = newText.length();
      int start = charCnt, end = charCnt+textLen;
      //remove ended fragments
      if(torem!=null)
        for(MarkupDescription md: torem)
          activeLocations.remove(md);
      //update positions for updated one
      for(MarkupDescription md: activeLocations) {
        md.start-=textLen;
        md.end-=textLen;
      }
      //add new fragments
      while(lastPos!=null && lastPos<end || lastPos == null&&lastIter.hasNext()) {
        if(lastPos!=null && lastPos>=start)
        {
          for(MarkupDescription descr:lastPosList) {
            descr.start-=charCnt;
            descr.end-=charCnt;
            addDescriptionPart(descr, newText);
            if(descr.end>=textLen)//TODO think
            {
              activeLocations.add(descr);
            }
          }
        }
        Entry<Long, List<MarkupDescription>> next = 
          lastIter.hasNext() ? lastIter.next() : null;
        lastPos = next == null ? null : next.getKey();
        lastPosList = next == null ? null: next.getValue();
      }
    }
  }

  private void addDescriptionPart(MarkupDescription md, String newText) {
    int partEnd = newText.length();
    if(md.end<partEnd)
      partEnd = (int) md.end;
    md.sb.append(newText.substring(0, partEnd));
  }
  @Override
  protected void initProcess(TreeNode root) {
    if(!isLocExternal) {
      activeLocations.clear();
    }
    bodyStarted = false;
    charCnt = 0;
    lastPos = null;
    lastPosList = null;
    lastText = lastSpace = false;
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
    for(List<MarkupDescription> colset:locsMap.values())
      for(MarkupDescription loc:colset){
        AttributeDefinition ad = loc.relatedLoc.attrs().getWorkAttributeDefinition(TreeNode.ATTR_DESCRIPTION);
        String locDesc = ad!=null&&ad.getRawValue()!=null?
           ad.getRawValue().getGValue():
           null;
        if(loc.sb.length()>0) {
          File resFolder = new File(loc.relatedLoc.getNodeResourcesPath(TreeNode.ATTR_DESCRIPTION));
          TempFolderFilesProvider added = TempFolderFilesProvider.getOne(resFolder, false);
          if(locDesc == null)
            locDesc = loc.sb.toString();
          else
            locDesc += loc.sb.toString();
          setLocDescription(loc.relatedLoc, (Document)loc.relatedLoc.getWorkParent(), 
            RequalityCLI.updateImages(loc.relatedLoc.getExternalNodeResourcesPath(TreeNode.ATTR_DESCRIPTION),
              locDesc, added, true)); 
        }
        loc.relatedLoc.commitAttributes();
      }
  }
}
