/*
 * Copyright 2014-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

/** Class for representing document as plain text */
public class DocStringRepresentation {

  //protected Document doc;
  protected StringBuilder strRep = new StringBuilder();
  protected List<SpanInfo> spans = null;
  protected List<NodeLocation> tagsList = new ArrayList<>();
  protected Map<Node, SpanStartEnd> tagsMap = new HashMap<>();
  protected List<Integer> tagStartToTagNumber = new ArrayList<>();

  protected Set<String> addedSpanIds = new HashSet<>();
  int transferredSpansCounter = 0;
  int foundButNotTransferredCounter = 0;
  int foundInFewNodesCounter = 0;
  int transferredFromNextDOMTags = 0;

  public Map<Node, Node> debugRemovedNodes = new HashMap<>();
  private boolean verbose = false;
  private Document doc;
  
  public DocStringRepresentation(final com.unitesk.requality.nodetype.Document doc, List<TreeNode> childs) {
    this.doc = doc;
    toString(doc, strRep);
    if(childs!=null)
      for(TreeNode n: childs) {
        if(n instanceof Location) {
          Location l = (Location) n;
          List<Object> lo = l.attrs().getListAttributeValue(Location.ATTR_MARKUP);
          int sm = Integer.MAX_VALUE;
          int em = 0;
          for(Object o:lo) {
            String s = o.toString();
            int ind = s.indexOf(Location.FRAGMENT_SEPARATOR);
            int start = Integer.parseInt(s.substring(0, ind)),
                end = Integer.parseInt(s.substring(ind+1));
            if(start<sm)
              sm = start;
            if(end>em)
              em = end;
          }
          if(spans == null)
            spans = new LinkedList<>();
          spans.add(
              new SpanInfo(
                  new SpanStartEnd(sm, em), l.getSUUId()));
          }
        }
    if (verbose) {
      RLogger.logDebug("spans size = " + spans.size());
    }
    Collections.sort(tagsList);
    for (int i = 0; i < tagsList.size(); ++i) {
      tagStartToTagNumber.add(tagsList.get(i).locationPosition.start);
    }
  }

  private void toString(Document doc, StringBuilder strRep2) {
  // TODO Auto-generated method stub
    SAXTextExtractor ste = new SAXTextExtractor();
    ste.setNoTransactionsFlag(true);
    ste.setShowMessages(false, false);
    try {
      ste.process(doc, doc.getTreeDB().getNode(Requirement.getTypeRootQId()), null, null, null);
    } catch (IOException e) {
      e.printStackTrace();
    }
    strRep2.append(ste.getText());
  }

  public void buildString(final Node currentNode, boolean removeNewLines, boolean bodyStarted) {
    int start = strRep.length();
    boolean appendNewLine = false;

    if (currentNode.getNodeType() == Node.TEXT_NODE && bodyStarted) {
      String text = currentNode.getNodeValue(); 
      append(text, removeNewLines);
    } else {
      //Pattern BLOCK_ELEMENT_RE = Pattern.compile("p|h[0-9]|br|div|tr|td", Pattern.CASE_INSENSITIVE);
      String nodeName = currentNode.getNodeName();
//      if (currentNode.getNodeType() == Node.ELEMENT_NODE
//          && BLOCK_ELEMENT_RE.matcher(nodeName).matches()) {
//        removeNewLines = true;
//        appendNewLine = true;
//      }

      NodeList nl = currentNode.getChildNodes();

      for (int i = 0; i < nl.getLength(); i++) {
        buildString(nl.item(i), removeNewLines, bodyStarted || nodeName.equalsIgnoreCase("body"));
      }

      if (currentNode.getNodeName().compareTo("span") == 0) {
        Node clazz = currentNode.getAttributes().getNamedItem("class");
        if (clazz != null) {
          String spanId = clazz.getNodeValue();
          if (spanId.startsWith("requality_text ")) {
            String locString = strRep.substring(start);
            if (locString.length() > 0) {
              spans.add(
                  new SpanInfo(
                      new SpanStartEnd(start, strRep.length()), spanId));
              if (verbose) {
                RLogger.logInfo("added span <" + strRep.substring(start) + ">");
              }
            }
          }
        }
      }
//      if (appendNewLine && bodyStarted) {
//        /appendNewLine();
//      }
    }

    SpanStartEnd newLoc = new SpanStartEnd(start, strRep.length());
    NodeLocation nodeLoc = new NodeLocation(newLoc, currentNode);
    tagsList.add(nodeLoc);
    tagsMap.put(currentNode, newLoc);
  }
  private void append(String text, boolean removeNewLines) {
//    int ind = text.indexOf(" ");
//    while (ind>-1) {
//      if(ind>0 && Character.isWhitespace(text.charAt(ind-1))
//        || ind < (text.length()-1) && Character.isWhitespace(text.charAt(ind+1))){
//        text = text.substring(0, ind)+text.substring(ind+1);
//        ind--;
//      }
//      ind = text.indexOf(" ", ind+1);
//    }
      
    if (removeNewLines) {
      //      text = text.replaceAll("\r?\n", " ").replaceAll("\t|\\s{2,}", " ")/*.trim()*/;
      //      text = text.replaceAll("\r?\n", " ").replaceAll("\t", " ").replaceAll("\\s{2,}", " "); //.replaceAll("\\s[,\\.]", "!");
      text = getRawString(text);
     // text = text.replaceAll("\\s[\\.]", ".").replaceAll("\\s[,]", ",");
      //      text = text.replaceAll("\r?\n", " ").replaceAll("\t", " ").replaceAll("\\s{2,}", " ").replaceAll("\\s,", ",");
    }
    
    boolean lastEmp = false;// strRep.length()>0?strRep.charAt(strRep.length()-1)==' ':false;
    if (strRep.length() > 0 && (lastEmp /* || lastChar() == '\n'*/)) {
      if(text.length()>0 && lastEmp) {
        if(text.length()>0 && text.charAt(0)==' ')
          text = text.substring(1);
      }
    }

    if (strRep.length() > 0 && (lastEmp /* || lastChar() == '\n'*/)) {
      //      text = text.replaceFirst("^[,\\.]", "");
//      if (text.startsWith(",") || text.startsWith(".") || text.startsWith(";")) {
//        strRep.deleteCharAt(strRep.length() - 1);
//      }
    }
    strRep.append(text);
  }

  public String getCleanString() {
    return strRep.toString();
  }

  void deleteRemovedNodes() {
    for (Node node : debugRemovedNodes.keySet()) {
      Node parent = node.getParentNode();
      if (parent != null) {
        parent.removeChild(node);
      }
    }
  }

  private String getRawString(String text) {
    return text;//.replaceAll("[\\r\\t\\n\\s]+", " ").replaceAll("\\s{2,}", " ");
  }

  public List<SpanInfo> getSpans() {
    if(spans==null){
      spans = new LinkedList<>();
      for(TreeNode chi: doc.getSortedWorkChildren()){
        Location loc = (Location) chi;
        List<Object> ovals = loc.attrs().getListAttributeValue(Location.ATTR_MARKUP);
        int min = Integer.MAX_VALUE, max = 0;
        for(Object o : ovals){
          String markup = (String)o;
          Pair<Integer, Integer> pos = Location.parsePositions(markup);
          if(pos!=null){
            if(min>pos.getFirst())
              min = pos.getFirst();
            if(max<pos.getSecond())
              max = pos.getSecond();
          }
        }
        spans.add(new SpanInfo(new SpanStartEnd(min, max), loc.getSUUId()));
      }
    }
    return spans;
  }
}
