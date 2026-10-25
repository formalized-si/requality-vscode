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
package com.unitesk.requality.core.operation.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.SAXException;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.document.DiffProcessor.Diff;
import com.unitesk.requality.core.operation.document.DiffProcessor.Operation;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.message.RMessages;

/**
 * Handles the action in the context menu. Transfer requirements from old document to new document
 */
public class ProcessNewSpecificationVersion extends DocumentProcessor {

  private static final Object PREFFERED_DOC = "prefferedDoc";
  private static final Object FORCE = "force";
  
  public Map<String, List<NodeDesc>> newLocations = new HashMap<>();
  boolean lastSpace = false, lastText = false;
  private Set<String> remAttr = new HashSet<>();
  
  
  
  public boolean verbose = false;
  public boolean writeDebugFiles = false;

  private Document oldDocument = null;
  private Document newDocument = null;
  private TreeDB db;

  private int diffFoundSpanCounter = 0;
  private int transferedLocCounter = 0;

  private DocStringRepresentation oldDocRep = null;
  private DocStringRepresentation newDocRep = null;

  private int outsideEqualCount = 0;
  private int partiallyOutsideEqual = 0;

  private int completeReqsCount = 0;

  private int incompleteReqsCount = 0;
  private int failedReqsCount = 0;
  private Map<String, Integer> reqIdsToLocCounter = new HashMap<>();
  private Map<String, Integer> reqIdsToTransferredSpanCounter = new HashMap<>();
  private Set<String> reqIdsToTransferredPartially = new HashSet<>();
  private Set<String> reqIdsToNoContent = new HashSet<>();
  private List<TreeNode> changedLocations = new LinkedList<>();

  public void unsetShowMessageBoxWithStats() {
  }

  public void setShowMessageBoxWithStats() {
  }

  @Override
  public boolean configure(final Document doc, Map<String, Object> params, PrintStream pw) {
    if (!super.configure(doc, params, pw)) {
      return false;
    }
    TypedValue frs = (TypedValue) params.get(FORCE);
    return configure(doc, (Document)params.get(PREFFERED_DOC), 
        frs!=null? frs.getGValue():false);
  }

  public boolean configure(Document doc, Document prefferedDoc, boolean force) {
    this.db = doc.getTreeDB();
    if (doc.attrs().getAttribute(Document.ATTR_ALREADY_UPDATED) != null) {
      super.showMessageBox(
          "Document Processor",
          "This document was already updated. " + "Documents cannot be updated more than once.");
      return false;
    }

    oldDocument = showSelectAnotherDocumentDialog(doc, prefferedDoc, force);
    return oldDocument != null;
  }

  /** This method preferred to default. Can be used in console mode. */
  public boolean configure(Document doc, Document prefferedDoc) {
    return configure(doc, prefferedDoc, false);
  }

  @Override
  protected boolean process(Document doc, final Reader in, final Writer out, TreeNode rootNode, PrintStream ps) {

    newDocument = (Document) doc;

    if (isCanceled()) return false;

    boolean b = processReqsTransfer(out);
    ps.append("Processing completed");
    if(b) {
      FormLocationDescriptions pdi = new FormLocationDescriptions();
      try {
        pdi.setNoTransactionsFlag(true);
        pdi.setShowMessages(false, true);
        pdi.process(doc, rootNode, null, null, getMonitor());
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }
    return b;
  }

  /**
   * Method transfers locations from old document to new document. Can be used in console mode Isn't
   * uses in eclipse plug-in mode
   */
  public void transferRequirements(Document oldDoc, Document newDoc) {

    newDocument = newDoc;
    oldDocument = oldDoc;

    this.db = oldDocument.getTreeDB();

    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    Writer out = null;
    try {
      out = new OutputStreamWriter(byteOut, encoding);
      processReqsTransfer(out);
      out.close();
      String htmlName = newDocument.getResourceName();

      if (verbose) {
        RLogger.logDebug("htmlName = " + htmlName);
      }

      ByteArrayInputStream outIn = new ByteArrayInputStream(byteOut.toByteArray());
      newDocument.setResourceContent(htmlName, outIn, new ProcessorData(), OFlag.NONE);
      outIn.close();

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private boolean processReqsTransfer(final Writer out)
      throws TransformerFactoryConfigurationError {

    transferedLocCounter = 0;
    diffFoundSpanCounter = 0;

    completeReqsCount = 0;
    incompleteReqsCount = 0;
    failedReqsCount = 0;

    try {
      if (isCanceled()) {
        return false;
      }
      if(oldDocument == newDocument)
        return false;

      newDocRep = new DocStringRepresentation(newDocument, null);

      if (isCanceled()) {
        return false;
      }

      oldDocRep = new DocStringRepresentation(oldDocument, oldDocument.getSortedWorkChildren());

      if (writeDebugFiles) {
        printCleanStringsToFiles();
      }

      if (isCanceled()) {
        return false;
      }

      Map<SpanInfo, List<SpanToTransfer>> locationsInNewFile = processDiff();

      if (isCanceled()) {
        return false;
      }

      boolean status = updateReqAttributes(locationsInNewFile);

      if (!status || isCanceled()) {
        return false;
      }

      status = setStatuses();

      if (!status || isCanceled()) {
        return false;
      }

      newDocRep.deleteRemovedNodes();
      status = createToDoLinks();

      if (verbose) {
        RLogger.logDebug("ToDo list items created");
      }

      if (!status) {
        return false;
      }

      if (isCanceled()) {
        return false;
      }

      //newDocument.setXmlDoc(newDocRep.doc);
      //newDocument.saveDOMFromXMLCache();

      if (isCanceled()) return false;

      transform(out);

      if (isCanceled()) return false;

      oldDocument.addUpdatedProblem();
      newDocument.addUpdatedProblem();

      //newDocument.setXmlDoc(newDocRep.doc);
      //newDocument.saveDOMFromXMLCache();

      newDocument.markDocumentAsUpdated();

      if (verbose) {
        RLogger.logDebug("newDocument.saveDOMFromXMLCache() is done");
      }
      RMessages.openView(db, "ToDoList");
      
      generateTextForMessageBox();

      return true;

    } catch (TransformerConfigurationException e) {
      e.printStackTrace();
    } catch (TransformerException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return false;
  }

  private void printCleanStringsToFiles() throws FileNotFoundException {
    PrintWriter oldCleanString = new PrintWriter(new File("oldCleanStr.txt"));
    //        oldCleanString.print(oldDocRep.getCleanString().replaceAll("\n", "@"));
    oldCleanString.print(oldDocRep.getCleanString());
    oldCleanString.close();

    PrintWriter oldTagsFile = new PrintWriter(new File("oldTags.txt"));
    //for (NodeLocation nodeLoc : oldDocRep.getTags()) oldTagsFile.println(nodeLoc);
    oldTagsFile.close();

    PrintWriter newCleanString = new PrintWriter(new File("newCleanStr.txt"));
    //        newCleanString.print(newDocRep.getCleanString().replaceAll("\n", "@"));
    newCleanString.print(newDocRep.getCleanString());
    newCleanString.close();
    PrintWriter newTagsFile = new PrintWriter(new File("newTags.txt"));
    //for (NodeLocation nodeLoc : newDocRep.getTags()) newTagsFile.println(nodeLoc);
    newTagsFile.close();
  }

  private boolean updateReqAttributes(Map<SpanInfo, List<SpanToTransfer>> spansInNewFile) {

    for (SpanInfo spanLoc : oldDocRep.getSpans()) {
      if (verbose) {
        RLogger.logDebug("spanLoc = " + spanLoc.spanId + " " + spanLoc.position.start+", "+spanLoc.position.end);
      }

      String id = spanLoc.getSpanId();//.substring(18);
      if (reqIdsToLocCounter.keySet().contains(id)) {
        int oldCount = reqIdsToLocCounter.get(id);
        reqIdsToLocCounter.put(id, oldCount + 1);
      } else {
        reqIdsToLocCounter.put(id, 1);
      }
    }

    if (isCanceled()) {
      return false;
    }
    String oldDocContent = oldDocRep.getCleanString();
    Map<String, UUID> idToUUID = new HashMap<>();
    for (SpanInfo spanLoc : oldDocRep.getSpans()) {

      if (verbose) {
        RLogger.logDebug("spanLoc = " + spanLoc);
      }

      if (isCanceled()) {
        return false;
      }
      String spanText = spanLoc.getText(oldDocContent);
      String spanTextBeforeTrimmed = spanText.trim();
      List<SpanToTransfer> spansToTransfer = spansInNewFile.get(spanLoc);
      if (spansToTransfer != null) {
        // currently there is max one element in locsToTransfer, this cycle is for
        // preposition of different variants to user
        boolean wasUp = false;
        String oldspanID = spanLoc.getSpanId();//.substring(18);
        for (SpanToTransfer spanToTransfer : spansToTransfer) {
          ++diffFoundSpanCounter;
          boolean forUpdate = false;
          if (!idToUUID.containsKey(spanLoc.getSpanId())) {
            idToUUID.put(spanLoc.getSpanId(), UUID.randomUUID());
          }
          UUID spanID = idToUUID.get(spanLoc.getSpanId());
          String transfText = spanToTransfer.getSpanText();
          if (spanText.contains(transfText)) {
            int pos = spanText.indexOf(transfText);
            spanText = spanText.substring(0,pos)+spanText.substring(pos+transfText.length());
            forUpdate = true;//TODO check
            //forUpdate =
              //  newDocRep.DocModify(spanToTransfer, "requality_text id_" + spanID.toString());
          } else {
            if (verbose) {
              RLogger.logDebug("Different span texts to modify. Continue.");
            }
          }
          wasUp = wasUp || forUpdate;
          if (forUpdate) {
            // adding locations in new doc for current requirement
            ++transferedLocCounter;
            String sspanID = spanID.toString();
            Location oldLoc = oldDocument.getLocation(oldspanID);

            if (reqIdsToTransferredSpanCounter.keySet().contains(oldspanID.toString())) {
              int count = reqIdsToTransferredSpanCounter.get(oldspanID.toString());
              reqIdsToTransferredSpanCounter.put(oldspanID.toString(), count + 1);
            } else {
              reqIdsToTransferredSpanCounter.put(oldspanID.toString(), 1);
            }

            Location loc = newDocument.getLocation(sspanID);
            
            if (loc == null) {
              loc = newDocument.createLocation(spanID);
              List<Object> arr = new LinkedList<>();
              arr.add(spanToTransfer.getLocation().getStart()+Location.FRAGMENT_SEPARATOR+spanToTransfer.getLocation().getEnd());
              loc.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, arr),
                  AttributeDefinition.detectFlags(Location.TYPE_NAME));
              loc.commitAttributes();
            }else {
              List<Object> arr = loc.attrs().getListAttributeValue(Location.ATTR_MARKUP);
              arr.add(spanToTransfer.getLocation().getStart()+Location.FRAGMENT_SEPARATOR+spanToTransfer.getLocation().getEnd());
              arr.sort(Location.FRAGMENT_SORTER);
              loc.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, arr),
                  AttributeDefinition.detectFlags(Location.TYPE_NAME));
              loc.commitAttributes();
            }
            if(!changedLocations.contains(loc))
              changedLocations.add(loc);
            if (oldLoc != null) {
              Requirement[] reqs = oldLoc.getRequirements();
              for (Requirement req : reqs) {
                req.addLocation(loc.getUUId());
                req.commitAttributes();
              }
            } 
          }else
            RLogger.logError("lost "+spanLoc.getText(oldDocContent));
        }
        if(!wasUp)
          RLogger.logError("lost "+spanLoc.getText(oldDocContent));
        if(spanText.trim().equals(spanTextBeforeTrimmed))
          reqIdsToNoContent.add(oldspanID);
        else if(spanText.trim().length()>0)
          reqIdsToTransferredPartially.add(oldspanID.toString());
      }else
        RLogger.logError("lost "+spanLoc.getText(oldDocContent));
    }

    return true;
  }

  private boolean setStatuses() {

    if (verbose) {
      RLogger.logDebug("reqIdsToTransferredLocCounter:");
      for (String str : reqIdsToTransferredSpanCounter.keySet()) {
        RLogger.logDebug(str + " " + reqIdsToTransferredSpanCounter.get(str));
      }
    }

    Set<UUID> requirementsFromOldDoc = new HashSet<>();

    if (verbose) {
      RLogger.logDebug("in setStatuses ");
    }

    for (UUID uuid : oldDocument.getChildrenLocations()) {//TODO mb use xml one?

      if (verbose) {
        RLogger.logDebug("uuid =  " + uuid);
      }

      Location location = oldDocument.getLocation(uuid.toString());

      if (location == null) {
        // TODO add checkers before update processor
        continue;
      }

      if (verbose) {
        RLogger.logDebug("location = " + location);
      }

      for (Requirement req : location.getRequirements()) {
        UUID requid = req.getUUId();
        if(!requirementsFromOldDoc.contains(requid))
          requirementsFromOldDoc.add(requid);
      }
    }

    if (isCanceled()) {
      return false;
    }
    
    int oldPos = -1;
    int locCount = 0;
    int locDup = 0;
    int locLost = 0;
    for(TreeNode chi : oldDocument.getWorkChildren()) {
      Location l = (Location) chi;
      int pos = Integer.parseInt(l.getIndex().toString());
      if(oldPos!=pos)
      {
        locCount++;
        oldPos = pos;
      }else
        locDup++;
      if(l.getRequirements().length == 0)
        locLost++;
    }
    for (UUID requid : requirementsFromOldDoc) {
      // uncomment if we don't want to mark structure requirements without locations with statuses overlays
      //          if (req.getLocations().isEmpty()) {
      //            continue;
      //          }
      TreeNode reqnode = db.getNode(requid);
      if (reqnode != null) {
        Requirement req = (Requirement) reqnode;
        List<Location> reqLocations = req.getLocations(false);
        int numberOfTransferredSpans = 0;
        int numberOfInitialSpans = 0;

        int numberOfLocationsInOldDoc = 0;
        int numberOfTransferredLocations = 0;
        
        boolean locChanges = false;
        int cntLost = 0;
        for (Location loc : reqLocations) {

          Integer numberOfSpansInOldDoc = 0;
          Integer numberOfSpansInNewDoc = 0;
          
          String locId = loc.getId();
          
          if (loc.getWorkParentUUID().equals(oldDocument.getUUId())) {
            if(reqIdsToTransferredPartially.contains(locId))
              locChanges = true;
            if(reqIdsToNoContent.contains(locId))
              cntLost++;
            numberOfLocationsInOldDoc++;
            if (reqIdsToTransferredSpanCounter.keySet().contains(locId)) {
              numberOfSpansInNewDoc = reqIdsToTransferredSpanCounter.get(locId);
              numberOfTransferredSpans += numberOfSpansInNewDoc;
            }
            if (reqIdsToLocCounter.get(locId) != null)
              numberOfSpansInOldDoc = reqIdsToLocCounter.get(locId);

            if (numberOfSpansInOldDoc == numberOfSpansInNewDoc) {
              numberOfTransferredLocations++;
            }
            numberOfInitialSpans += numberOfSpansInOldDoc;
          }
        }

        Requirement.TransferStatus statusOfCurrentReq = null;
        if(cntLost == reqLocations.size())//only whitespaces
          numberOfTransferredSpans = 0;
        if (numberOfLocationsInOldDoc > 0) {
          if (numberOfTransferredLocations == numberOfLocationsInOldDoc && !locChanges) {
            statusOfCurrentReq = Requirement.TransferStatus.COMPLETE;
            completeReqsCount++;
          } else {
            if ((numberOfTransferredSpans > 0)
                && (numberOfTransferredSpans != numberOfInitialSpans)) {
              statusOfCurrentReq = Requirement.TransferStatus.INCOMPLETE;
              incompleteReqsCount++;
            }
            if (numberOfTransferredSpans == 0) {
              statusOfCurrentReq = Requirement.TransferStatus.FAILED;
              failedReqsCount++;
            }
          }
        }
        if (statusOfCurrentReq != null) {
          req.setTransferProblem(statusOfCurrentReq);
        }

        if (verbose) {
          RLogger.logDebug("req = " + req);
          RLogger.logDebug("numberOfTransferredSpans = " + numberOfTransferredSpans);
          RLogger.logDebug("numberOfInitialSpans = " + numberOfInitialSpans);
          RLogger.logDebug("number of transferred locations = " + numberOfTransferredLocations);
          RLogger.logDebug("number of initial locations = " + numberOfLocationsInOldDoc);
          RLogger.logDebug("status = " + statusOfCurrentReq);
        }
      }
    }

    //     uncomment if we want to get the worst status from all children

    //     for (TreeNode node : getOrderFromLeaves(db.getRootNode())) {
    //
    //       if (verbose) {
    //         RLogger.logDebug("node = " + node);
    //       }
    //
    //       if(isCanceled()) {
    //          return false;
    //       }
    //
    //       if (node instanceof Requirement) {
    //         Requirement req = (Requirement) node;
    //         Requirement.TransferStatus worstStatus = null;
    //
    //         Attribute reqAttr = req.getAttribute(Requirement.ATTR_TRANSF_STATUS);
    //         if (reqAttr != null) {
    //           worstStatus = Requirement.TransferStatus.valueOf(reqAttr.getRawValue().toString());
    //         }
    //
    //         if (node.sizeChildren() > 0) {
    //           TreeNode[] children = node.getSortedChildren();
    //           for (TreeNode child : children) {
    //             Attribute attr = child.getAttribute(Requirement.ATTR_TRANSF_STATUS);
    //             if (attr != null) {
    //               Requirement.TransferStatus childStatus = Requirement.TransferStatus.valueOf(attr.getRawValue().toString());
    //               if (childStatus != null) {
    //                 if (worstStatus == null ) {
    //                   worstStatus = childStatus;
    //                 } else if (!childStatus.isBetterThan(worstStatus)) {
    //                   worstStatus = childStatus;
    //                 }
    //               }
    //             }
    //           }
    //         }
    //
    //         if (worstStatus != null) {
    //           req.setTransferProblem(worstStatus);
    //         }
    //       }
    //     }
    return true;
  }

  private boolean createToDoLinks() {

    TreeNode toDoFolder = db.getNode("/ToDoLinks", Path.QID);
    if (toDoFolder == null) {
      toDoFolder = db.getRootNode().createChild("ToDoLinks", NodeType.TYPE_NAME);
    }

    Set<Requirement> reqsWithToDoItems = new HashSet<>(); 
    for (TreeNode node : getOrderFromLeaves(db.getNode(toDoFolder.getUUId()))) {
      if (node instanceof ToDoItem) {
        ToDoItem t = (ToDoItem) node;
        if(t.NEW_DOC==null || db.getNode(t.NEW_DOC)==null){
          t.delete();
          continue;
        }
        if(t.OLD_DOC==null || db.getNode(t.OLD_DOC)==null){
          t.delete();
          continue;
        }
        Object attr_req_link =
            UUID.fromString(node.attrs().getStringAttributeValue(ToDoItem.ATTR_REQ_LINK));
        if (attr_req_link instanceof java.util.UUID) {
          UUID reqUUID = (UUID) attr_req_link;
          TreeNode taskParent = db.getNode(reqUUID);
          if (taskParent != null) {
            if (taskParent.getType().equals(Requirement.TYPE_NAME)) {
              reqsWithToDoItems.add((Requirement) taskParent);
            }
          }
        }
      }
    }

    UUID root = db.getNode(Requirement.getTypeRootQId(), Path.QID).getUUId();
    db.startChangeRow();
    try {
      for (TreeNode node : getOrderFromLeaves(db.getNode(Requirement.getTypeRootQId(), Path.QID))) {
  
        if (verbose) {
          RLogger.logDebug("node = " + node);
        }
  
        if (isCanceled()) {
          return false;
        }
  
        if (node instanceof Requirement) {
          Requirement req = (Requirement) node;
  
          if (reqsWithToDoItems.contains(req)) {
            continue;
          }
  
          if (req.getLocations(false).isEmpty()) {
            continue;
          }
  
          Requirement.TransferStatus status = null;
  
          Attribute reqAttr = req.attrs().getAttribute(Requirement.ATTR_TRANSF_STATUS);
          if (reqAttr != null) {
            status = Requirement.TransferStatus.valueOf(reqAttr.getValue().toString());
          }
  
          if (status != null) {
            TreeNode toDoItem =
                toDoFolder.createChild(
                    RequalityCLI.getNextId(toDoFolder, ToDoItem.TYPE_NAME, "link_"),
                    ToDoItem.TYPE_NAME);
            Task.Action action;
            switch (status) {
              case INCOMPLETE:
                action = Task.Action.ADD_LOCATIONS;
                break;
              case COMPLETE:
                action = Task.Action.VERIFY;
                break;
              case FAILED:
                action = Task.Action.FIND_LOCATIONS;
                break;
              default:
                action = Task.Action.FIND_LOCATIONS;
                break;
            }
  
            toDoItem.attrs().setConstAttribute(ToDoItem.ATTR_REQ_LINK, req.getSUUId(),0);
            toDoItem.attrs().setConstAttribute(ToDoItem.ATTR_TRANSF_ACTION, action.toString(),Flag.HIDDEN);
            toDoItem.attrs().setConstAttribute(ToDoItem.OLD_DOC, oldDocument.getQualifiedId(),Flag.HIDDEN);
            toDoItem.attrs().setConstAttribute(ToDoItem.NEW_DOC, newDocument.getQualifiedId(),Flag.HIDDEN);
            toDoItem.commitAttributes();
          }
        }
      }
    }finally {
      db.stopChangeRow();
    }
    if (verbose) {
      RLogger.logDebug("Before exiting ToDoLinks creation");
    }
    return true;
  }

  private List<TreeNode> getOrderFromLeaves(TreeNode rootNode) {
    List<TreeNode> order = new ArrayList<>();
    bfs(rootNode, order);
    Collections.reverse(order);
    return order;
  }

  private void bfs(TreeNode node, List<TreeNode> queue) {
    queue.add(node);
    for (TreeNode child : node.getWorkChildren()) {
      bfs(child, queue);
    }
  }
  
  private class DiffPosition{
    public int posStart, posEnd;
    public DiffPosition(int start, int end) {
      this.posStart = start;
      this.posEnd = end;
    }
  }
  private class DiffDocsPosition{
    public DiffPosition oldDocPosition, newDocPosition;
    public DiffDocsPosition(DiffPosition oldDocPos, DiffPosition newDocPos) {
      this.oldDocPosition = oldDocPos;
      this.newDocPosition = newDocPos;
    }
  }

  private Map<SpanInfo, List<SpanToTransfer>> processDiff() {

    Map<SpanInfo, List<SpanToTransfer>> locationsInNewFile =
        new HashMap<>();

    DiffProcessor differ = new DiffProcessor();
    LinkedList<Diff> diffResult;

    String oldContent = oldDocRep.getCleanString();
    diffResult = differ.diff_main(oldContent, newDocRep.getCleanString(), false);
    int indexInDiffList = 0;
    if(diffResult.size() > 0) {
      diffResult.sort(new Comparator<Diff>() {
        @Override
        public int compare(Diff o1, Diff o2) {
          return Integer.compare(o1.originalOffset,o2.originalOffset);
        }
      });
      DiffDocsPosition diffPositions = new DiffDocsPosition(new DiffPosition(0, 0), new DiffPosition(0, 0));
      Diff df = diffResult.get(indexInDiffList);
      updateDiffPosition(diffPositions, df);
      for (SpanInfo spanLoc : oldDocRep.getSpans()) {
        String spanText = oldContent.substring(spanLoc.position.start, spanLoc.position.end);
        if(spanText == null)
          continue;
        int spanStart = spanLoc.getSpanPosition().getStart();
        int spanEnd = spanLoc.getSpanPosition().getEnd();
        boolean isSpanStartInsideDiffBlock =
            (spanStart <= diffPositions.oldDocPosition.posEnd)
                && (spanEnd >= diffPositions.oldDocPosition.posStart);
        while (!isSpanStartInsideDiffBlock && indexInDiffList<(diffResult.size()-1)) {
          ++indexInDiffList;
          df = diffResult.get(indexInDiffList);
          updateDiffPosition(diffPositions, df);
          isSpanStartInsideDiffBlock =
            (spanStart <= diffPositions.oldDocPosition.posEnd)
                && (spanEnd >= diffPositions.oldDocPosition.posStart);
        }
        List<SpanToTransfer> locationsToTransfer = null;
        boolean haveAddedText = false;
        boolean haveDeletedText = false;
        int fixedDiffIndex = -1;
        while(spanStart<spanEnd && (spanStart <= diffPositions.oldDocPosition.posEnd)){
          Diff currentDiff = df;
          int oldSpanStart = spanStart;
          if(currentDiff.getOperation() == Operation.DELETE){
            //missed fragment, move forward
            haveDeletedText = haveDeletedText || diffPositions.oldDocPosition.posEnd>spanStart;
            spanStart = Math.min(diffPositions.oldDocPosition.posEnd, spanEnd);
//            ++indexInDiffList;
//            df = diffResult.get(indexInDiffList);
//            updateDiffPosition(diffPositions, df);
          }else{
            if (currentDiff.getOperation() == Operation.EQUAL){
              int textStart = Math.max(spanStart, diffPositions.oldDocPosition.posStart);
              int textEnd = Math.min(spanEnd, diffPositions.oldDocPosition.posEnd);
              String diffText = df.getText().substring(textStart-diffPositions.oldDocPosition.posStart, textEnd-diffPositions.oldDocPosition.posStart);
              haveAddedText = haveAddedText || textEnd>textStart;
            //  if ((spanBeginIndex + spanTextLen) <= (currentDiff.getOriginalOffset())) {
                // the span is all inside EQUAL diff
              int startIndexInNewDoc = diffPositions.newDocPosition.posStart + spanStart-diffPositions.oldDocPosition.posStart;//maybe a part
              SpanToTransfer locToTransfer =
                new SpanToTransfer(startIndexInNewDoc, 
                  startIndexInNewDoc+textEnd-textStart, 
                  diffText);
              if(locationsToTransfer == null){
                locationsToTransfer = new ArrayList<>();
                locationsInNewFile.put(spanLoc, locationsToTransfer);
              }
              locationsToTransfer.add(locToTransfer);
              spanStart = textEnd;
      
//              } else {
//                // TODO upgrade for case of partially in equal block
//                ++partiallyOutsideEqual;
//              }
//            } else {
//              ++outsideEqualCount;
//            }
            }else if (currentDiff.getOperation() == Operation.INSERT){
            }
          }
          if(spanStart>=diffPositions.oldDocPosition.posEnd){//TODO >=
            ++indexInDiffList;
            if(indexInDiffList>=diffResult.size())//TODO proper while close
              break;
            df = diffResult.get(indexInDiffList);
            updateDiffPosition(diffPositions, df);
          }
          if(oldSpanStart == spanStart && indexInDiffList == fixedDiffIndex){
            RLogger.logError("Location update seems to stuck, update dropped for "+spanLoc.getSpanId());
            break;//TODO test
          }
          fixedDiffIndex = indexInDiffList;
          oldSpanStart = spanStart;
        }
        if(haveAddedText && haveDeletedText)
          partiallyOutsideEqual ++;
        else
          if(haveAddedText)
             outsideEqualCount ++;
      }
    }
    return locationsInNewFile;
  }

  private void updateDiffPosition(DiffDocsPosition diffedPosition,
    Diff df) {
     switch(df.operation) {
       case DELETE:
         diffedPosition.oldDocPosition.posStart = df.originalOffset-df.getText().length();
         diffedPosition.oldDocPosition.posEnd = df.originalOffset;
         break;
       case INSERT:
         diffedPosition.newDocPosition.posStart = df.replacementOffset-df.getText().length();
         diffedPosition.newDocPosition.posEnd = df.replacementOffset;
         break;
       case EQUAL: 
         diffedPosition.oldDocPosition.posStart = df.originalOffset-df.getText().length();
         diffedPosition.oldDocPosition.posEnd = df.originalOffset;
         diffedPosition.newDocPosition.posStart = df.replacementOffset-df.getText().length();
         diffedPosition.newDocPosition.posEnd = df.replacementOffset;
         break;
     }
  }

  public int getOutsideEqualCount() {
    return outsideEqualCount;
  }

  public int getPartiallyOutsideEqual() {
    return partiallyOutsideEqual;
  }

  private void generateTextForMessageBox() {

    long totalReqCounter = completeReqsCount + incompleteReqsCount + failedReqsCount;
    long transferredCounter = completeReqsCount + incompleteReqsCount;
    String message = "Document processing finished.\n";

    if (totalReqCounter == 0) {
      message += "There is no extracted requirements in marked document";
    } else {

      if (transferredCounter == 0) {
        message +=
            "None of the requirements have been transferred from "
                + totalReqCounter
                + " requirements.";
      } else {
        message += "Transferred " + transferredCounter + " requirement";
        if (transferredCounter > 1) {
          message += "s";
        }
        message += " from " + totalReqCounter + ".";

        if (incompleteReqsCount > 0) {
          message += "\nWhere for " + incompleteReqsCount + " requirement";
          if (incompleteReqsCount > 1) {
            message += "s";
          }
          message += " not all text fragments have been transferred.";
        }
      }
    }

    super.messageBoxMessage = message;
    super.messageBoxTitle = "Document Processor";

    if (verbose) {
      RLogger.logDebug("From MessageBox: " + messageBoxMessage);

      RLogger.logDebug("Stats:");
      RLogger.logDebug("number of spans: " + oldDocRep.getSpans().size());
      RLogger.logDebug("diff found: " + diffFoundSpanCounter + " spans to transfer");
      RLogger.logDebug("number of transferred spans: " + transferedLocCounter);
      RLogger.logDebug("outsideEqualCount: " + outsideEqualCount);
      RLogger.logDebug("partiallyOutsideEqual: " + partiallyOutsideEqual);

      RLogger.logDebug("Partially transferred number of reqs : " + incompleteReqsCount);
      RLogger.logDebug("Totally transferred number of reqs: " + completeReqsCount);
      RLogger.logDebug("Not transferred number of reqs: " + failedReqsCount);
      RLogger.logDebug("Total number of reqs in initial doc: " + totalReqCounter);
    }
  }

  private void transform(final Writer out)
      throws Exception, TransformerFactoryConfigurationError, TransformerConfigurationException,
          TransformerException {
    DOMSource domSource = new DOMSource(newDocument.getDOM());

    StreamResult result = new StreamResult(out);
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer transformer;

    transformer = tf.newTransformer();
    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(domSource, result);
  }

  public int getTransferedLocCounter() {
    return transferedLocCounter;
  }

  public int getDiffFoundSpanCounter() {
    return diffFoundSpanCounter;
  }

  public int getTotalSpansCounter() {
    return oldDocRep.getSpans().size();
  }

  public int getCompleteReqsCount() {
    return completeReqsCount;
  }

  public int getIncompleteReqsCount() {
    return incompleteReqsCount;
  }

  public int getFailedReqsCount() {
    return failedReqsCount;
  }

  public int getTotalReqsCount() {
    return (completeReqsCount + incompleteReqsCount + failedReqsCount);
  }
}
