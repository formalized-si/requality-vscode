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
package com.unitesk.requality.nodetype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.events.TreeNodeChangeEvent.TreeNodeChangeEventType;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.operation.document.MarkupAddSpanProcessor;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.document.DeepFirstXMLDOMWalker;
import com.unitesk.requality.document.LocFragment;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

import freemarker.ext.dom.NodeModel;

public class Document extends TreeNode implements ITreeChangeListener, INodeChangeListener {
  public static final String TYPE_NAME = "Document";
  private static final String ATTRIBUTE_FILE = "_file";
  private static final String TYPE_ROOT_QID = "/Documents";
  public static final String ATTR_IS_UPDATING = "_isUpdating";
  public static final String ATTR_ALREADY_UPDATED = "_alreadyUpdated";
  public static final String ATTR_TEXT_VERSION = "_textVersion";
  public static final String LOCATION_CLASS_ID_PREFIX = "id_";
  private static final String ATTR_UTI = "_luidtoind";
  public static final String UTF_ENC = StandardCharsets.UTF_8.toString();
  public static final String ATTR_IMP = "import_in_progress";
  public boolean ignoreUpdate = false;
  
  //private Map<UUID,Integer> locationUUIdToIndex;
  private org.w3c.dom.Document xmlDoc;

  private static boolean verbose = true;
  private freemarker.ext.dom.NodeModel modelCache = null;
  private freemarker.ext.dom.NodeModel modelLocCache = null;

  @Override
  protected void addChildUUID(UUID uid) {
    //resetCache();
    super.addChildUUID(uid);
  };

  @Override
  public void removeChildUUID(UUID node) {
    super.removeChildUUID(node);

    //resetCache();
  };

  @Override
  public TreeNode findWorkChild(String id) {
    TreeNode node = super.findWorkChild(id);
    if (node != null) return node;
    try {
      return getTreeDB().getNodeExc(UUID.fromString(id));
    } catch (NodeNotFoundException e) {
      //e.printStackTrace();
    }
    return null;
  }

  public Document() {
    super(null, null);
  }

  public freemarker.ext.dom.NodeModel getNodeModelWithFragments(){
    if(modelLocCache == null)
      modelLocCache = createModelWithFragments();
    return modelLocCache;
  }
  private static File fragmentedDocs = null;
  private NodeModel createModelWithFragments() {
    List<LocFragment> allLocs = getMarkupFragments();
    if(allLocs.isEmpty())
      modelLocCache = getNodeModel();
    else
    {
      MarkupAddSpanProcessor proc = new MarkupAddSpanProcessor(allLocs);
      proc.setShowMessages(false, false);
      proc.configure(this, null, null);
      if(fragmentedDocs == null){
        try {
          fragmentedDocs = Files.createTempDirectory("ru.ispras.requality.docTempFragments").toFile();
          fragmentedDocs.mkdirs();
          fragmentedDocs.deleteOnExit();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      File fa = fragmentedDocs.toPath().resolve(getSUUId()).toFile();
      if(!fa.exists()){
        try {
          fa.createNewFile();
		} catch (IOException e) {
          RLogger.logError(e);
		}
        fa.deleteOnExit();
      }
      proc.setNoTransactionsFlag(true);
      proc.setShowMessages(false, false);
      
      try(FileWriter fw = new FileWriter(fa)){
        proc.process(this, this, null, fw, null);
      }
      //Document doc, Reader in, Writer out, TreeNode rootNode, Writer writer, PrintStream ps
      catch (IOException e) {
        RLogger.logError(e); 
      }
      try {
		modelLocCache = freemarker.ext.dom.NodeModel.parse(fa);
      } catch (SAXException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (ParserConfigurationException e) {
        e.printStackTrace();
      }
    }
	return modelLocCache;
  }

 public List<LocFragment> getMarkupFragments() {
    List<LocFragment> allLocs = null;
    for(TreeNode nde:getWorkChildren())
    {
      Location locn = (Location) nde;
      List<int[]> locs = locn.getFragments();
      for(int[]l:locs) {
        LocFragment lf = new LocFragment(l, locn);
        if(allLocs==null)
          allLocs = new LinkedList<>();
        allLocs.add(lf);
      }
    }
    if(allLocs!=null)
      allLocs.sort(LocFragment.ORDER_COMPARATOR);
    else
      allLocs = Collections.emptyList();
    return allLocs;
  }
  public boolean haveMarkup() {
    return sizeWorkChildren()>0;
  }
  private String getHtmlErrorFile(URI uri) {
   StringBuilder sb = new StringBuilder();
   sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body>");
   sb.append("No such document");
   sb.append(uri);
   sb.append("</body></html>");
   return sb.toString();
  }
//Use it only on reports
  public freemarker.ext.dom.NodeModel getNodeModel() {
    if (modelCache == null)
      try {
        modelCache = freemarker.ext.dom.NodeModel.parse(new File(getHTMLPath()));
      } catch (SAXException | IOException | ParserConfigurationException e) {
       RLogger.logDebug(e, 1);
        try(ByteArrayInputStream str = new ByteArrayInputStream((getHtmlErrorFile(getHTMLPath())).getBytes(StandardCharsets.UTF_8))){
          InputSource is = new InputSource(str);
          modelCache = freemarker.ext.dom.NodeModel.parse(is);
        } catch (IOException | SAXException | ParserConfigurationException e1) {
          RLogger.logError(e1);
        }
      }
    return modelCache;
  }

  private Element getElementChildIgnoreCase(Node node, String name) {
    for (int i = 0; i < node.getChildNodes().getLength(); i++) {
      if (node.getChildNodes().item(i).getNodeName().toLowerCase().equals(name.toLowerCase())) {
        return (Element) node.getChildNodes().item(i);
      }
    }
    return null;
  }

  

  public String getMetaCharset() {
    // TODO: check rfc for this meta
    String result = null;
    try {
      DeepFirstXMLDOMWalker dfw = new DeepFirstXMLDOMWalker(getDOM(UTF_ENC).getFirstChild());
      for (Node node : dfw) {
        if (node.getNodeName().toLowerCase().equals("meta")) {
          if (node.getAttributes().getNamedItem("http-equiv") == null) continue;
          if (!node.getAttributes()
              .getNamedItem("http-equiv")
              .getNodeValue()
              .toLowerCase()
              .equals("content-type")) continue;
          if (node.getAttributes().getNamedItem("content") == null) continue;
          String content = node.getAttributes().getNamedItem("content").getNodeValue();
          int i = content.indexOf("charset=");
          if (i < 0) return null;
          return content.substring(i + "charset=".length());
        }
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return result;
  }

  public static String getTypeRootQId() {
    return TYPE_ROOT_QID;
  }

  public Document(TreeDB db, UUID uuid) {
    super(db, uuid);
    setType(TYPE_NAME);
  }

  public void setResourceName(String name) {
    attrs().setConstAttribute(ATTRIBUTE_FILE, name, Flag.HIDDEN);
  }

  public String getResourceName() {
    return attrs().getStringAttributeValue(ATTRIBUTE_FILE);
  }

  // private static final String LOCATION_CLASS_ID_PREFIX =
  // "requality_text id_";

  public URI getHTMLPath() {
    URI path = null;
    try {
      path = getNodeResourcesPath();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (path == null || getResourceName() == null) return null;
    //URI base = URI.create(path);
    try {
      return URI.create(path.toURL() + "/" + CoreUtils.URLEncode(getResourceName()));
    } catch (MalformedURLException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return path;
  }


  public static class LocationRegion {
    public LocationRegion() {
      startNodeType = endNodeType = Node.ELEMENT_NODE;
    }

    public LocationRegion(String sX, int sT, int sOff, String eX, int eT, int eOff, String text) {
      startNodeXPath = sX;
      startNodeType = sT;
      startNodeOffset = sOff;
      endNodeXPath = eX;
      endNodeType = eT;
      endNodeOffset = eOff;
      this.text = text;
    }

    public boolean isStarted() {
      return startNodeXPath != null;
    }

    private String startNodeXPath;
    private int startNodeType;
    private int startNodeOffset;

    private String endNodeXPath;
    private int endNodeType;
    private int endNodeOffset;

    private String text = "";

    @Override
    public String toString() {
      String start = makeNodeString("start", startNodeXPath, startNodeType, startNodeOffset);
      if (endNodeXPath == null) {
        endNodeXPath = startNodeXPath;
        endNodeOffset = startNodeOffset + text.length();
      }
      String end = makeNodeString("end", endNodeXPath, endNodeType, endNodeOffset);
      return "{" + start + "," + end + ",\"text\":\"" + text + "\"}";
    }

    private String makeNodeString(String name, String xpath, int type, int offset) {
      StringBuilder sb = new StringBuilder();
      sb.append("\"");
      sb.append(name);
      sb.append("\":{\"node\":\"");
      sb.append(xpath);
      sb.append("\",\"nodeType\":");
      sb.append(type);
      sb.append(",\"offset\":");
      sb.append(offset);
      sb.append("}");
      return sb.toString();
    }

    public void text(String text) {
      if (!isText()) {
        startNodeType = endNodeType = Node.TEXT_NODE;
      }
      this.text += text;
    }

    public boolean isText() {
      return startNodeType == Node.TEXT_NODE;
    }

    public void xpath(String xpath) {
      if (!isStarted()) {
        startNodeXPath = xpath;
      } else {
        endNodeXPath = xpath;
      }
    }

    public void offset(int offset) {
      if (!isStarted()) {
        startNodeOffset = offset;
      } else {
        endNodeOffset = offset;
      }
    }
  }


  private static boolean needDropNode(Node node, Set<String> uuids) {
    if (node.getNodeName().equals("a")) {
      if (node.getAttributes().getNamedItem("class") != null) {
        String cl = node.getAttributes().getNamedItem("class").getNodeValue();
        for (String uuid : uuids) {
          if (cl.contains("id_" + uuid) || cl.contains("requality_node")) {
            return true;
          }
          if (node.getAttributes().getNamedItem("id") != null) {
            String id = node.getAttributes().getNamedItem("id").getNodeValue();
            if (cl.contains("requality_id") && id.equals("id_" + uuid)) return true;
          }
          if (node.getAttributes().getNamedItem("name") != null) {
            String id = node.getAttributes().getNamedItem("name").getNodeValue();
            if (id.equals(uuid)) return true;
          }
        }
      }
    }
    //if(node.getNodeType() == 3 && node.getNodeValue().equals("\n"))
    //return true;
    //if(node.getNodeType()==3 && node.getNodeValue().equals("\n"))
    //  return true;
    if (node.getNodeName().equals("span")) {
      String className = node.getAttributes().getNamedItem("class").getNodeValue();
      for (String uuid : uuids) {
        if (className.startsWith("requality_link requality_link_" + uuid)) {
          return true;
        }
        if (className.startsWith("requality_link requality_link_in_" + uuid)) {
          return true;
        }
      }
    }

    return false;
  }

  public Location createLocation() {
    return createLocation(null);
  }

  public Location createLocation(UUID id) {
    if (id == null) id = UUID.randomUUID();
    Location l =
        (Location)
            getTreeDB().createChildNode(id, getUUId(), id.toString(), Location.TYPE_NAME, true);
    // saveDOM(); // moved to ReqMarker.addSelectionTo
    return l;
  }

  private class RegionDescription {
    public final String id;
    public final LocationRegion r;

    public RegionDescription(String uid, LocationRegion r) {
      this.r = r;
      this.id = uid;
    }
  }

  private void fillQueue(Node node, LinkedList<Node> lnodes, Set<String> map) {
    LocationDetectionResult ldres = isLocationNode(node);
    if (ldres != LocationDetectionResult.NOT_LOCATION) {
      String id = getUUID(node);
      if (map.contains(id)) {
        lnodes.addLast(node);
        //else
        //node.getParentNode().removeChild(node);
      }
    }
    NodeList cnl = node.getChildNodes();
    Node[] nodes = new Node[cnl.getLength()];
    for (int i = 0; i < cnl.getLength(); i++) nodes[i] = cnl.item(i);
    for (int i = 0; i < nodes.length; i++) fillQueue(nodes[i], lnodes, map);
  }
  // TODO: nested location support
  private void removeLocations(
      Set<String> map, Node rnode, List<RegionDescription> regions, final int parentOffset) {
    LinkedList<Node> lnodes = new LinkedList<>();
    //Map<Node, LocationDetectionResult> locNodes =
    //  new HashMap<Node,LocationDetectionResult>();
    fillQueue(rnode, lnodes, map);
    List<Object[]> selTexts = new LinkedList<>();

    for (Node node : lnodes) {
      LocationDetectionResult ldres = isLocationNode(node);
      String id = getUUID(node);
      Node next = node.getNextSibling(), par = node.getParentNode();
      //TODO check
      if (ldres == LocationDetectionResult.NOT_LOCATION) continue;
      if (ldres == LocationDetectionResult.SPAN_LOCATION) {
        NodeList nl = node.getChildNodes(); //TODO can be bug?
        if (nl != null) {
          Node[] nodes = new Node[nl.getLength()];
          for (int i = 0; i < nl.getLength(); i++) nodes[i] = nl.item(i);
          for (int i = 0; i < nodes.length; i++) {
            recClear(id, par,node,node.getNextSibling(),nodes[i], nodes, i, map,selTexts);
          }
        }
        node.getParentNode().removeChild(node);
      } else {
        String cl = node.getAttributes().getNamedItem("class").getNodeValue();
        for (String sid : map) cl = cl.replace("id_" + sid, "");
        if (cl.indexOf("id_") < 0) cl = cl.replace("requality_node", "");
        Attr a = node.getOwnerDocument().createAttribute("requality_node_active");
        a.setValue(cl);
        node.getAttributes().setNamedItem(a);
        selTexts.add(new Object[] {false, node, id});
      }
    }
    //StringBuffer jsData = new StringBuffer();
    ListIterator<Object[]> stit = selTexts.listIterator(selTexts.size());
    while (stit.hasPrevious()) {
      Object[] st = stit.previous();
      if ((boolean) st[0]) {
        //text
        Node n = (Node) st[1];
        LocationRegion r = new LocationRegion();
        Object[] o = getElementXpath(n);
        r.startNodeXPath = r.endNodeXPath = (String) o[0];
        r.startNodeType = r.endNodeType = (Short) o[1];
        r.startNodeOffset = (int) st[2];
        r.endNodeOffset = (int) st[3];
        r.text = st[5].toString();
        RLogger.logDebug(r.toString());
        String uid = (String) st[4];
        regions.add(new RegionDescription(uid, r));
        //TODO js?
        //if(!regions.containsKey(uid))
        //  regions.put(uid, new LinkedList<Document.LocationRegion>());
        //regions.get(uid).add(r);
      } else {
        Node n = (Node) st[1];
        LocationRegion r = new LocationRegion();
        Object[] o = getElementXpath(n);
        r.startNodeXPath = r.endNodeXPath = (String) o[0];
        r.startNodeType = r.endNodeType = (Short) o[1];
        RLogger.logDebug(r.toString());
        String uid = (String) st[2];
        regions.add(new RegionDescription(uid, r));
        //jsData.append(b)
      }
    }
  }

  private void recClear(String id, Node par,Node node,Node next,Node nde, Node[] nodes, int i, Set<String> map, List<Object[]>  selTexts) {
    NodeList nl = nde.getChildNodes();
    if(nl!=null){
      Node[] nodes2 = new Node[nl.getLength()];
      for (int j = 0; j < nl.getLength(); j++) nodes2[j] = nl.item(j);
      for (int j = 0; j < nodes2.length; j++) {
        recClear(id, nde, nodes2[j],nodes2[j].getNextSibling(), nodes2[j], nodes2, j, map, selTexts);//TODO par?
      }
    }
    if (needDropNode(nde, map)) {
      nde.getParentNode().removeChild(nde);
      return;
    }
    int start = 0, len = 0;
    String text = null;
    if (nde.getNodeType() == Node.TEXT_NODE) {
      len = nde.getNodeValue().length();
      text = nde.getNodeValue();
    }
    nde = par.insertBefore(nde, node);
    if (nde.getNodeValue() != null && nde.getNodeValue().equals("\n")) return;
    if (nde.getNodeType() == Node.TEXT_NODE) {
      start = nde.getNodeValue().length() - len;
      Node prev = nde.getPreviousSibling();
      if (prev != null && prev.getNodeType() == Node.TEXT_NODE) {
        start += prev.getNodeValue().length();
        prev.setNodeValue(prev.getNodeValue() + nde.getNodeValue());
        par.removeChild(nde);
        nde = prev;
      }
      if (i == (nodes.length - 1)
          && (next != null)
          && next.getNodeType() == Node.TEXT_NODE) {
        nde.setNodeValue(nde.getNodeValue() + next.getNodeValue());
        par.removeChild(next);
        next = null;
      }
       selTexts.add(new Object[] {true, nde, start, start + len, id, text});
      //                                if(!selTexts.keySet().contains(nde))
      //                                    if(nde.getNodeValue().replace("\n", "").length()>0){
      //                                        selTexts.put(nde, nde.getNodeValue().length());
      //                                        getuid.put(nde, uuid.toString());
    }
  }

  public void saveDOM() {
    saveDOM(null);
  }

  public void saveDOM(String jsData) {
    saveDOM(jsData, null);
  }

  private static Map<Thread,Transformer> fm = new HashMap<>();
  private static Transformer getFactory(){
    Transformer f = fm.get(Thread.currentThread());
    if(f!=null)
      return f;
    TransformerFactory tFactory =  TransformerFactory.newInstance();
    tFactory.setURIResolver(new URIResolver(){
    
      @Override
      public Source resolve(String arg0, String arg1) throws TransformerException {
        return null;
      }
    });
    Transformer transformer;
    try {
      f = transformer = tFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.STANDALONE, "true");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "true");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    } catch (TransformerConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    fm.put(Thread.currentThread(), f);
    return f;
  }
  
  public void saveDOM(String jsData, org.w3c.dom.Document doc) {
    modelCache = null;
    synchronized (this) {
      if (doc == null)
        try {
          doc = getDOM(UTF_ENC);
        } catch (Exception e) {
          e.printStackTrace();
      }
    }
    org.w3c.dom.Document fdoc = doc;
    long t_start = System.currentTimeMillis();
    byte[]ba = null;
    try {
      ByteArrayOutputStream newXml = new ByteArrayOutputStream();
      //Writer result = new OutputStreamWriter(newXml, StandardCharsets.UTF_8);
      Result result = new StreamResult(newXml);
      
      long l = System.currentTimeMillis();
      //DOMImplementationLS domImplementation = (DOMImplementationLS) fdoc.getImplementation();
      DOMSource ds = new DOMSource(doc);
      getFactory().transform(ds, result);
      
      //LSSerializer lsSerializer = domImplementation.createLSSerializer();
      //lsSerializer.getDomConfig().setParameter("xml-declaration", true);
      //lsSerializer.getDomConfig().setParameter("validate", true);
      //LSOutput lo = domImplementation.createLSOutput();
    //  lo.setCharacterStream(result);
      //lo.setEncoding(StandardCharsets.UTF_8.toString());
      //lsSerializer.write(fdoc,lo);
      ba = newXml.toByteArray();
      InputStream is = new ByteArrayInputStream(ba);
      setResourceContent(getResourceName(), is, jsData, OFlag.NONE);
      is.close();
      //result.close();
      newXml.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    // TODO: optimize cache

    if (verbose) {
      long t_end = System.currentTimeMillis();
      RLogger.logDebug("time Document.saveDOM(" + jsData + ") = " + (t_end - t_start));
    }
  }

  public void saveDOMFromXMLCache() {
    long t_start = System.currentTimeMillis();
    ByteArrayOutputStream newXml = new ByteArrayOutputStream();
    try {
      org.w3c.dom.Document doc = getXmlDoc();
      Writer out = new OutputStreamWriter(newXml, "UTF-8");
      Result result = new StreamResult(out);
      Transformer t = TransformerFactory.newInstance().newTransformer();
      t.setOutputProperty(OutputKeys.METHOD,"xml");
      t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
      t.transform(new DOMSource(doc), result);
      out.close();
      InputStream is = new ByteArrayInputStream(newXml.toByteArray());
      setResourceContent(getResourceName(), is, null, OFlag.NONE);
      is.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    // TODO: optimize cache

    if (verbose) {
      long t_end = System.currentTimeMillis();
      RLogger.logDebug("time Document.saveDOMFromXMLCache() = " + (t_end - t_start));
    }
  }

  private enum LocationDetectionResult {
    NOT_LOCATION,
    SINGLE_LOC_TAG,
    SPAN_LOCATION
  };

  
  private LocationDetectionResult isLocationNode(Node node) {
    //handling imgs?
    //if (node.getNodeName().equals("span")) {
    if (node.hasAttributes()) {
      Node spanClass = node.getAttributes().getNamedItem("class");
      if (spanClass == null) {
        return LocationDetectionResult.NOT_LOCATION;
      }
      String className = spanClass.getNodeValue();
      if (node.getNodeName().equals("span")) {
        if (className.contains(LOCATION_CLASS_ID_PREFIX) && className.contains("requality_text")) {
          return LocationDetectionResult.SPAN_LOCATION;
        }
      } else if (className.contains("requality_node") && className.contains("id_"))
        return LocationDetectionResult.SINGLE_LOC_TAG;
    }
    //}
    return LocationDetectionResult.NOT_LOCATION;
  }

  private String getUUID(Node locationNode) {
    try {
      String classNames[] =
          locationNode.getAttributes().getNamedItem("class").getNodeValue().split(" ");
      for (String className : classNames) {
        if (className.startsWith(LOCATION_CLASS_ID_PREFIX)) {
          return className.substring(LOCATION_CLASS_ID_PREFIX.length());
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return null;
  }

  public Location getLocation(String locationId) {
    Location l = null;
    try {
      l = (Location) findWorkChild(locationId);
    } catch (NodeNotFoundException re) {
    }
    if (l == null)
      try {
        l = (Location) getTreeDB().getNodeExc(UUID.fromString(locationId));
      } catch (NodeNotFoundException e) {
      }
    return l;
  } 
  public org.w3c.dom.Document getDOM() throws Exception {
    return getDOM(UTF_ENC);
  }
    
  public org.w3c.dom.Document getDOM(String enc) throws Exception {

    if(xmlDoc == null)
      xmlDoc = loadDOM(enc);
    return xmlDoc;
  }
  public static org.w3c.dom.Document loadDOM(InputStream isa) throws Exception {
    return loadDOM(isa, UTF_ENC);
  }
  private static Map<Thread,DocumentBuilder> docBuilders = new HashMap<>();
  public static DocumentBuilder getDocBuilder(){
    DocumentBuilder db = docBuilders.get(Thread.currentThread());
    if(db == null){
      try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setIgnoringElementContentWhitespace(true);
        db = dbf.newDocumentBuilder();
        docBuilders.put(Thread.currentThread(), db);
      } catch (ParserConfigurationException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return db;
  }
  public static org.w3c.dom.Document loadDOM(InputStream isa, String enc) throws Exception {
    long t_start = System.currentTimeMillis();
    if (isa == null) return null;
    org.w3c.dom.Document result = null;
    try {
      InputSource is = new InputSource(isa);
      is.setEncoding(enc);
      result = getDocBuilder().parse(isa);
    } catch (SAXParseException spe) {
      
    }
    isa.close();
    if (result != null) result.normalize();

    if (verbose) {
      long t_end = System.currentTimeMillis();
      RLogger.logDebug("time Document.loadDOM() = " + (t_end - t_start));
    }

    return result;
  }
  public org.w3c.dom.Document loadDOM() throws Exception {
    return loadDOM(UTF_ENC);
  }
  
  public org.w3c.dom.Document loadDOM(String enc) throws Exception {
    InputStream in = getResourceContent(getResourceName());
    try{
      return loadDOM(in, enc);
    }
    finally{
      if(in!=null)
        in.close();
    }
  }

  private boolean containsLocationId(String[] classNames) {
    boolean requalityTextClass = false;
    boolean locationIdClass = false;

    for (String name : classNames) {
      if (name.equals("requality_text")) {
        requalityTextClass = true;
      }
      if (name.startsWith(LOCATION_CLASS_ID_PREFIX)) {
        locationIdClass = true;
      }
      if (requalityTextClass && locationIdClass) {
        return true;
      }
    }
    return requalityTextClass && locationIdClass;
  }

  private String getLocationIdClass(String[] classNames) {
    for (String name : classNames) {
      if (name.startsWith(LOCATION_CLASS_ID_PREFIX)) {
        return name;
      }
    }
    return null;
  }

  public String extractLocationText(String locUUID) {
    try {

      org.w3c.dom.Document d = getDOM(UTF_ENC);
      DeepFirstXMLDOMWalker dfw = new DeepFirstXMLDOMWalker(d.getFirstChild());

      DocumentFragment fragment = d.createDocumentFragment();
      for (Node node : dfw) {
        if (node.getNodeName().equals("span")) {
          Node spanClass = node.getAttributes().getNamedItem("class");
          if (spanClass != null) {
            String[] classNames = spanClass.getNodeValue().split(" ");

            if (containsLocationId(classNames)) {
              String locationIdClass = getLocationIdClass(classNames);
              UUID uuid =
                  UUID.fromString(locationIdClass.substring(LOCATION_CLASS_ID_PREFIX.length()));
              if (uuid.toString().equals(locUUID)) {
                for (int i = 0; i < node.getChildNodes().getLength(); i++) {
                  Node ch = node.getChildNodes().item(i);
                  if (ch.getNodeName().equalsIgnoreCase("a")
                      && ch.getAttributes().getNamedItem("name") != null
                      && ch.getAttributes().getNamedItem("name").getNodeValue().equals(locUUID)) {
                    continue;
                  }
                  fragment.appendChild(ch.cloneNode(true));
                }
              }
            }
          }
        }
      }
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      TransformerFactory transFactory = TransformerFactory.newInstance();
      Transformer transformer = transFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.METHOD,"xml");
      transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
      transformer.transform(new DOMSource(fragment), new StreamResult(result));
      result.flush();
      return result.toString("UTF-8");

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public int sizeLocationsChildren() {
    return getChildrenLocations().size();
  }
  @Override
  public String toString() {
    return getId();
  }
  
  public org.w3c.dom.Document getXmlDoc() {
    return xmlDoc;
  }

  public void setXmlDoc(org.w3c.dom.Document xmlDoc) {
    this.xmlDoc = xmlDoc;
  }

  public String createLocationRangesDescriptor(String locId) {
    try {
      getDOM(UTF_ENC);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";
  }

  // /////////////////////////
  // xpath stuff

  private static Set<Node> recLook(Node n) {
    Set<Node> list = new HashSet<>();
    NodeList nl = n.getChildNodes();

    for (int i = 0; i < nl.getLength(); i++)
      if (!list.contains(nl.item(i))) {
        list.add(nl.item(i));
        list.addAll(recLook(nl.item(i)));
      }
    return list;
  }

  public static Object[] getElementXpath(Node nodeNow) {
    Set<Node> allnodes = recLook(nodeNow.getOwnerDocument());
    Node[] aallNodes = allnodes.toArray(new Node[0]);
    String path = "";
    short type = Node.ELEMENT_NODE;
    if (nodeNow.getNodeType() == Node.TEXT_NODE) {
      type = Node.TEXT_NODE;
      nodeNow = nodeNow.getParentNode();
    }
    try {
      for (;
          nodeNow != null && nodeNow.getNodeType() == Node.ELEMENT_NODE;
          nodeNow = nodeNow.getParentNode()) {
        if (((Element) nodeNow).hasAttribute("id")) {
          short uniqueIdCount = 0;
          for (int i = 0; i < aallNodes.length; i++) {
            if (aallNodes[i].getAttributes() != null
                && aallNodes[i].getAttributes().getNamedItem("id") != null
                && aallNodes[i]
                    .getAttributes()
                    .getNamedItem("id")
                    .getNodeValue()
                    .equals(nodeNow.getAttributes().getNamedItem("id").getNodeValue()))
              uniqueIdCount++;
            if (uniqueIdCount > 1) break;
          }
          if (uniqueIdCount == 1) {
            path =
                "id(\\\""
                    + nodeNow.getAttributes().getNamedItem("id").getNodeValue()
                    + "\\\")"
                    + "/"
                    + path;
            path = path.substring(0, path.length() - 1);
            return new Object[] {path, type};
          } else {
            if (nodeNow.getLocalName() == null) RLogger.logInfo("null local name on node "+path);
            path =
                "x:"
                    + nodeNow.getLocalName().toLowerCase()
                    + "[@id=\""
                    + nodeNow.getAttributes().getNamedItem("id").getNodeValue()
                    + "\"]"
                    + "/"
                    + path;
          }
        } else {
          int i;
          Node sib;
          for (i = 1, sib = nodeNow.getPreviousSibling();
              sib != null;
              sib = sib.getPreviousSibling()) {
            if (sib.getNodeName() != null && sib.getNodeName().equals(nodeNow.getNodeName())) i++;
          }
          path = "x:" + nodeNow.getNodeName().toLowerCase() + "[" + i + "]" + "/" + path;
        }
      }
      if (path.length() == 0) return null;
      else path = "/" + path.substring(0, path.length() - 1);
      return new Object[] {path, type};
    } catch (Exception ee) {
      ee.printStackTrace();
    }
    return null;
  }

  public void duplicateImages(String path) {
    RequalityCLI.duplicateImages(this, path);
  }

  public int compareTo(Document document) {
    String qid1 = getQualifiedId(), qid2 = document.getQualifiedId();
    int i = 0, li = 0;
    while (i< qid1.length() && i < qid2.length() && i >= 0 && qid1.subSequence(0, i).equals(qid2.subSequence(0, i))) {
      li = i;
      i = qid1.indexOf("/", i + 1);
    }
    TreeNode p1 = this.getWorkParent();
    if(p1 == null)
      p1 = this;
    TreeNode p2 = document.getWorkParent();
    if(p2 == null)
      p2 = document;
    return TreeNodeComparator.getComparator(p1.getTreeDB()).compare(p1, p2);
  }

  public void addUpdatedProblem() {
    attrs().setConstAttribute(ATTR_IS_UPDATING, "updating", Flag.HIDDEN);
    commitAttributes();
  }

  public void markDocumentAsUpdated() {
    attrs().setConstAttribute(ATTR_ALREADY_UPDATED, "already updated", Flag.HIDDEN);
    commitAttributes();
  }
  
  public List<UUID> getChildrenLocations() {
    return getWorkChildrenUUIDs();
  }
  
  @Override
  public void attributeChange(TreeNode source, String what, Attribute old) {
    // TODO Auto-generated method stub

  }

  @Override
  public void moved(TreeNode source, TreeNode oldParent) {
  }

  @Override
  public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
    // TODO Auto-generated method stub

  }

  @Override
  public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
    synchronized (this) {
      if (!ignoreUpdate && m == Moment.AFTER
          && treeNode.getUUId().equals(getUUId())
          && equals(resName, getResourceName())) {
        try {
          xmlDoc = null;
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
  }

  private boolean equals(String resName, String resourceName) {
    return resName == null && resourceName == null
        || resName != null && resName.equals(resourceName);
  }

  
  public void clearCache() {
    int len = 0;
    modelCache = null;
    modelLocCache = null;
    //        if(getUUId()!=getTreeDB().getStorage().getRootNode()&&
    //          !isVirtual()&&!VirtualNode.getVirtualStorage(getTreeDB()).hasCloneParent(getUUId())){
    //              TreeNode node = VirtualNode.getVirtualTreeDB(getTreeDB()).getNode(getUUId());
    //              if (node.isVirtual())
    //                  ((Document)node).resetCache();
    //          
    xmlDoc = null;
  }

  
  public String getStringContents(){
    if(getXmlDoc() == null)
      return "~NOT LOADED~";
    DOMImplementationLS domImplementation = (DOMImplementationLS) getXmlDoc().getImplementation();
    LSSerializer lsSerializer = domImplementation.createLSSerializer();
    return lsSerializer.writeToString(getXmlDoc());
  }
  
  @Override
  public void changed(List<TreeNodeChangeEvent> changes) {
    for(TreeNodeChangeEvent ce:changes) {
      byte flag = ce.getFlag();
      if(ce.getType() == TreeNodeChangeEventType.CREATE){
        if(ce.getMoment()==Moment.BEFORE)
          beforeCreated(ce.getNodeDescTarget(), flag);
        else
          created(ce.getTreeNodeTarget(), flag);
      }else
        if(ce.getType() == TreeNodeChangeEventType.DELETE){
          deleted(ce.getTreeNodeTarget(), flag, ce.getMoment());
        }
    }
  }

  @Override
  public void changed(TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void beforeCreated(NodeDesc node, byte flag) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void created(TreeNode node, byte flag) {
    Set<UUID> torem = Collections.emptySet();
    if (node.getWorkParentUUID() != null && node.getWorkParentUUID().equals(getUUId())) {
      if (node.getType().equals(Location.TYPE_NAME)){
        torem = new HashSet<>();
        torem.add(node.getUUId());
      }
      modelLocCache = null;
    }
  }

  @Override
  public void childrenOrderChanged(TreeNode node, byte flag) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deleted(TreeNode node, byte flag, Moment m) {
    if(m == Moment.AFTER){
      Set<Location> torem = Collections.emptySet();
      if (node.getWorkParentUUID() != null && node.getWorkParentUUID().equals(getUUId())) {
        if (node.getType().equals(Location.TYPE_NAME)){
          torem = new HashSet<>();
          torem.add((Location) node);
        }
        modelLocCache = null;
      }
      //().removeLocationFromUUIdToIndex(this,torem.toArray(new Location[0]));//TODO fix
    }
  }

  @Override
  public void moved(
      TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void deleted(TreeNode source) {
    if(source.getSUUId().equals(getSUUId()))
      clearCache();
  }

  public boolean isImporting() {
    return attrs().getBooleanAttributeValue(ATTR_IMP);
  }
}
