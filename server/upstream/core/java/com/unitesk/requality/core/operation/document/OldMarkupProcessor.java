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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

/** Class for automatically extracting headers into requirements */
public class OldMarkupProcessor extends SAXDocumentProcessor {
  private static final String SEPARATOR = "|";
  private int charCnt;
  private StringBuilder testBuffer = new StringBuilder();
  //Map<Integer, Requirement> startedTags = new HashMap<Integer, Requirement>();
  private LinkedList<String> started = new LinkedList<>();
  private LinkedList<UUID> startedUUID = new LinkedList<>();
  private LinkedList<String> ignoredTags = new LinkedList<>();
  private LinkedList<Integer> startedIndex = new LinkedList<>();
  private boolean bodyStarted = false;
  public boolean isLocExternal = false;
  public Map<String, List<NodeDesc>> newLocations = new ConcurrentHashMap<>();
  private List<Location> saveLocs = new LinkedList<>();
  boolean lastSpace = false;
  private TreeDB db;
  private Set<String> remAttr = new HashSet<>();
  private StringBuilder locationMarkup = null;
  private Set<String>remDesc = new HashSet<>();
  public boolean processEvent(
      XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer, TreeNode rootNode, PrintStream ps)
      throws Exception {
    if (event.isStartElement()) {
      remAttr.clear();
      StartElement se = event.asStartElement();
      String tagName = getLocalTagName(event).toLowerCase();
      boolean ignore = false;
      if(tagName.equals("body"))
        bodyStarted = true;
      else {
        Iterator<Attribute> it = se.getAttributes();
        while(it.hasNext()) {
          Attribute a = it.next();

          if(a.getName().getLocalPart().equals("name")) {
            String val = a.getValue().toLowerCase();
            if(val.contains("_toc")&&tagName.equals("a"))
              ignore = true;
          }
          if(a.getName().getLocalPart().equals("class")){
            String val = a.getValue().toLowerCase();
            if(val.contains("requality_text")) {
              ignore = true;
              int idi = val.indexOf("id_");
              int idi2 = idi+39;
              String id = val.substring(idi+3, idi2);
              UUID uid = UUID.fromString(id);
              startedUUID.addLast(uid);
              started.addLast(tagName);
              startedIndex.addLast(charCnt);
              if(locationMarkup == null)
                locationMarkup = new StringBuilder();
              break;
            }
            if(val.contains("requality_node")) {
              if(tagName.equals("a"))
                ignore = true;
              else {
                val = val.replace("requality_node", "");
                int pos = val.indexOf("id_");
                if(pos>-1) {
                  val = val.substring(0,pos)+val.substring(pos+39); 
                }
              }
            }
            if(val.trim().length() == 0) {
              remAttr.add(a.getName().getLocalPart());
            }
            if(val.toLowerCase().equals("requality_id"))
              ignore = true;
          }
        }
      }
      if(remAttr.isEmpty()) {
        if(!ignore) {
          writer.add(event);
        }else if(!event.isEndElement()&&(started.isEmpty()||!started.getLast().equals(tagName)))
          ignoredTags.addLast(tagName);
      }else {
        List<Attribute> attrs = new LinkedList<>();
        Iterator<Attribute> it = se.getAttributes();
        while(it.hasNext()) {
          Attribute a = it.next();
          if(!remAttr.contains(a.getName().getLocalPart()))
            attrs.add(a);
        }
        writer.add(eventFactory.createStartElement(se.getName(), attrs.iterator(), null));
      }
      if(!ignore)
        if(locationMarkup!=null)
          toString(event,locationMarkup);
    } else if (event.isEndElement()) {
      boolean ignore = false;
      EndElement ee = event.asEndElement();
      String tagName = ee.getName().getLocalPart().toLowerCase();
      if(tagName.equals("body"))
      {
        bodyStarted = false;
      }else {
        if(!started.isEmpty() && started.getLast().equals(tagName)) {
          started.removeLast();
          ignore = true;
          UUID locId = startedUUID.removeLast();
          Integer start = startedIndex.removeLast();
          Location loc = null;
          try {
            loc = (Location) db.getNodeExc(locId);
          }catch(NodeNotFoundException nfe) {
          }
          if(loc == null)
            loc = (Location) doc.findWorkChild(locId.toString());//some old formats can have locs with uuid!=id
          NodeDesc altLoc = null;
          
          
          String suid = loc == null ? null : loc.getSUUId();
          String newDesc = "";
          if(loc!=null && remDesc.contains(suid)) {
            AttributeDefinition ad = loc.attrs().getWorkAttributeDefinition(TreeNode.ATTR_DESCRIPTION);
            if(ad!=null && ad.getRawValue()!=null && ad.getRawValue().getValue()!=null) {
              newDesc = (String)ad.getRawValue().getValue();
            }
          }
          if(locationMarkup != null){
            newDesc = newDesc+CoreUtils.htmlEncapsulate(locationMarkup.toString());
            locationMarkup.setLength(0);
          }
          if(loc==null)
          {
            String sld = locId.toString();
            List<NodeDesc> nds = null;
            synchronized(newLocations) {
              nds = newLocations.get(sld);
            }
            if(nds != null && nds.size()>0)
            {
              for(NodeDesc nd: nds)
                if(nd.getWorkParentUUID().equals(doc.getUUId()))
                {
                  altLoc = nd;
                  break;
                }
            }
            if(altLoc == null) {
              Map<String, AttributeDefinition> amap = new HashMap<>();
              //TODO descr?
              amap.put(TreeNode.ATTR_TYPE, new AttributeDefinition(locId, TreeNode.ATTR_TYPE, 
                  new ConstValueProvider(AttributeType.STRING, Location.TYPE_NAME)));
              NodeDesc nd = new NodeDesc(db,locId, locId,null, doc.getUUId(),doc.getUUId(), sld, amap, null, null, false);
              synchronized(newLocations) {
                if(nds == null || nds.size() == 0)
                  newLocations.put(sld, new LinkedList<>());
                newLocations.get(sld).add(nd);
              }
              
              TempFolderFilesProvider added = TempFolderFilesProvider.getOne(null, false);
              //TODO hrefs?
              String val = RequalityCLI.updateImages(doc.getNodeResourcesPath(), replaceStrangePS(newDesc), added,true);
              
              nd.setAttribute(new AttributeDefinition(locId, TreeNode.ATTR_DESCRIPTION, new HtmlValueProvider(AttributeType.HTML, val, null, added)));
              altLoc = nd;
              //loc = (Location) doc.createChild(locId.toString(), Location.TYPE_NAME);
              //TODO no description
            }
          }
          List<Object> vals = null;
          if(loc != null) {
            if(!remDesc.contains(suid))
              remDesc.add(suid);
            loc.attrs().setConstAttribute(TreeNode.ATTR_DESCRIPTION, newDesc, OFlag.NONE);
            //setLocDescription(loc, (Document) loc.getWorkParent(), newDesc);
            AttributeDefinition ad = loc.attrs().getWorkAttributeDefinition(Location.ATTR_MARKUP);
            if(ad != null && ad.getType()==AttributeType.LIST)
              vals = ((ListValue)(ad.getRawValue().getValue())).getValues();
            else 
              vals = new LinkedList<Object>();
          }else
            if(altLoc!=null) {
              AttributeDefinition ad = altLoc.getAttributes().get(Location.ATTR_MARKUP);
              if(ad!=null && ad.getType()==AttributeType.LIST)
              {
                TypedValue tw = ad.getRawValue();
                if(tw.getType() == AttributeType.LIST && tw.getValue()!=null)
                  vals = (List<Object>) ((ListValue)tw.getValue()).getValues();//TODO can be problems?
              }
            }
          if(vals == null)
            vals = new LinkedList<>();
          else
            vals = new LinkedList<>(vals);
          boolean notappend = false;
          if(!vals.isEmpty())
          { 
            String startS = Integer.toString(start); 
            String last = (String) vals.get(vals.size()-1);
            if(last!=null && last.endsWith(SEPARATOR+startS)){
              int pos = last.indexOf(SEPARATOR);
              if(pos>-1){
                vals.remove(vals.size()-1);
                vals.add(last.substring(0, pos+1)+charCnt);
                notappend = true;
              }
            }
          }
          if(!notappend)
            vals.add(start+SEPARATOR+(charCnt));
          
          if(loc!=null) {
            loc.attrs().setConstAttribute(Location.ATTR_MARKUP,new ListValue(AttributeType.STRING, vals),Flag.HIDDEN);
            //TODO revert back to setD?
            //loc.setDescription(replaceStrangePS(loc.getDescription()));
            if(!saveLocs.contains(loc))
              saveLocs.add(loc);
          }else if(altLoc == null) {
            RLogger.logError("Alt loc == null on "+locId);
          }else
            altLoc.setAttribute(
                new AttributeDefinition(
                    altLoc.getUUId(),
                    Location.ATTR_MARKUP,
                    new ConstValueProvider(AttributeType.LIST,
                        new ListValue(AttributeType.STRING,vals))));
        }else
          if(ignoredTags.size()!=0 && ignoredTags.getLast().equals(tagName)){
            ignoredTags.removeLast();
            ignore = true;
          }
      }
      if(!ignore) {
        writer.add(event);
        if(locationMarkup!=null)
          toString(event,locationMarkup);
      }
    } else if (event.isCharacters()) {
      String content = event.asCharacters().getData();
      if(bodyStarted) {
        String s = content;
        
//        while(s.indexOf("  ")>-1)
//          s = s.replace("  ", " ");
        charCnt+=(s.length());
        testBuffer.append(s);
        lastSpace = false;
        if(s.endsWith(" "))
          lastSpace = true;
        if(locationMarkup!=null)
          toString(event,locationMarkup);
      }
      writer.add(event);
    } else {
      writer.add(event);
      if(locationMarkup!=null)
        toString(event,locationMarkup);
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

  private void toString(XMLEvent event, StringBuilder sb) {
    if(event.isStartElement()){
      sb.append("<");
      StartElement se = event.asStartElement();
      sb.append(se.getName().getLocalPart());
      Iterator<Attribute> it = se.getAttributes();
      while(it.hasNext()){
        Attribute a = it.next();
        String name = a.getName().getLocalPart();
        if(!name.equals("class")) {
          sb.append(" ");
          sb.append(name);
          sb.append("=\"");
          sb.append(a.getValue());
          sb.append("\"");
        }
      }
      sb.append(">");
    } else if(event.isEndElement()){
      sb.append("</");
      sb.append(event.asEndElement().getName().getLocalPart());
      sb.append(">");
    } else 
      sb.append(event.toString());
    
  }

  @Override
  protected void initProcess(TreeNode root) {
    if(!isLocExternal)
      newLocations.clear();
    bodyStarted = false;
    charCnt = 0;
    started.clear();
    lastSpace = false;
    remDesc.clear();
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
    for(Location loc:saveLocs){
      AttributeDefinition ad = loc.attrs().getWorkAttributeDefinition(TreeNode.ATTR_DESCRIPTION);
      if(ad!=null){
        setLocDescription(loc, (Document)loc.getWorkParent(), ad.getRawValue().getGValue()); 
      }
      loc.commitAttributes();
    }
  }
}
