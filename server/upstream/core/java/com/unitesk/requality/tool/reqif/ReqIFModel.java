package com.unitesk.requality.tool.reqif;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ReqIFModel {
  private String modelid = null;
  public static final String ATTR_DOORS_ID = "ForeignID";
  
  protected ReqIFHeader header = null;
  protected Map<String, DatatypeDefinition> dataDefsById =
      new HashMap<>();
  protected Map<String, SpecObjectType> specObjectTypesById = new HashMap<>();

  protected Map<String, SpecRelationType> specRelationTypesById =
      new HashMap<>();
  protected Map<String, SpecRelation> specRelationById = new HashMap<>();

  protected Map<String, SpecObjectType> objectTypesById = new HashMap<>();
  protected Map<String, SpecObject> objectById = new HashMap<>();
  protected Map<String, Specification> objectToSpec = new HashMap<>();
  protected Map<String, Integer> ind = new HashMap<>();

  protected Map<String, SpecificationType> specificationTypeById =
      new HashMap<>();
  protected Map<String, Specification> specificationsById = new HashMap<>();

  protected Map<String, SpecGroup> specRelationGroupsById = new HashMap<>();
  protected Map<UUID, Map<UUID, String>> doorsMapping = new HashMap<>();
  protected Map<UUID, Map<String, UUID>> rdoorsMapping = new HashMap<>();
  protected Set<String> ignoreSave = new HashSet<>();

  protected ToolExtensions ext = null;
  private String path, oldpath;
  private Map<String, Pair<String, String>> attrs = null;
  private Map<String, String> nameSpaceToURI = null;
  protected int maxind = 1;

  public ReqIFModel(String path) {
    this.path = path;
  }

  public String getModelId() {
    if (modelid == null) modelid = "_" + UUID.randomUUID().toString();
    return modelid;
  }

  // private static String lch = "2016-09-20T14:05:32";
  public static String getTime() {
    return AttributeType.DATE_FORMAT.format(new Date());
  }

  static Node getFirstChild(Node node, String type) {
    if (node == null) return null;
    NodeList nl = node.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      String name = nl.item(i).getNodeName();
      int nsi = name.indexOf(":");
      if (nsi > -1) {
        name.substring(0, nsi);
        name = name.substring(nsi + 1);
      }
      if (name.equals(type)) return nl.item(i);
    }
    return null;
  }

  public static String getID(Node node) {
    return node.getAttributes().getNamedItem("IDENTIFIER").getNodeValue();
  }

  public static void docToFile(Document doc, File os) {
    TransformerFactory tf = TransformerFactory.newInstance();
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(os);
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "true");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.transform(new DOMSource(doc, "UTF-8"), new StreamResult(fos));
    } catch (TransformerException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (FileNotFoundException e){// TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(fos!=null)
          fos.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public void loadDoors(Document doc) {
    // TODO simply made itembycall
    ignoreSave.clear();
    Node root = getFirstChild(doc, "REQ-IF");
    attrs = ReqIFDefinition.loadAttributes(root);
    nameSpaceToURI = new HashMap<>();
    for (String s : attrs.keySet()) {
      String val = attrs.get(s).getSecond();
      if (attrs.get(s).getFirst() != null && attrs.get(s).getFirst().equals("xmlns")
          || s.equals("xmlns")) {
        // String ns = attrs.get(s).getFirst();
        // TODO check
        nameSpaceToURI.put(s, val);
      }
    }

    if (root == null) return;
    Node head = getFirstChild(root, "THE-HEADER");
    head = getFirstChild(head, "REQ-IF-HEADER");
    header = ReqIFHeader.load(head);

    Node core = getFirstChild(root, "CORE-CONTENT");
    Node content = getFirstChild(core, "REQ-IF-CONTENT");

    Node datatypes = getFirstChild(content, "DATATYPES");

    NodeList nl = datatypes.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node dt = nl.item(i);
      String tag = dt.getNodeName();
      if (tag != null && tag.startsWith("DATATYPE-")) {
        DatatypeDefinition dd = DatatypeDefinition.load(dt, this);
        if(dd != null){
          dd.sort = i;
          dataDefsById.put(dd.getId(), dd);
        }
      }else
        if(tag!=null && !tag.startsWith("#text")){
          RLogger.logInfo("Incorrect datatype "+tag);
        }
    }

    Node spectypes = getFirstChild(content, "SPEC-TYPES");

    nl = spectypes.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node nd = nl.item(i);
      String name = nd.getNodeName();
      if (name != null) {
        if (name.startsWith("SPECIFICATION")) {
          SpecificationType st = SpecificationType.load(nd, this);
          specificationTypeById.put(st.getId(), st);
        }
        if (name.startsWith("SPEC-OBJECT")) {
          SpecObjectType sot = SpecObjectType.load(nd, this);
          specObjectTypesById.put(sot.getId(), sot);
        }
        if (name.startsWith("SPEC-RELATION")) {
          SpecRelationType sot = SpecRelationType.load(nd, this);
          specRelationTypesById.put(sot.getId(), sot);
        }
      }
    }

    Node specobjects = getFirstChild(content, "SPEC-OBJECTS");

    nl = specobjects.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node nd = nl.item(i);
      String name = nd.getNodeName();
      if (name != null) {
        if (name.startsWith("SPEC-OBJECT")) {
          // TODO remade
          SpecObject so = DoorsSpecObject.load(nd, this);
          so.sort = i;
          objectById.put(so.getId(), so);
          ind.put(so.getId(), maxind++);
        }
      }
    }

    Node specrelations = getFirstChild(content, "SPEC-RELATIONS");
    if (specrelations != null) {
      nl = specrelations.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Node nd = nl.item(i);
        String name = nd.getNodeName();
        if (name != null) {
          if (name.startsWith("SPEC-RELATION")) {
            try {
              SpecRelation so = SpecRelation.load(nd, this);
              so.sort = i;
              specRelationById.put(so.getId(), so);
            } catch (RuntimeException re) {
              RLogger.logInfo("ERROR: " + re.getMessage());
            }
          }
        }
      }
    }

    Node specifications = getFirstChild(content, "SPECIFICATIONS");
    nl = specifications.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node nd = nl.item(i);
      String name = nd.getNodeName();
      if (name != null) {
        if (name.startsWith("SPECIFICATION")) {
          Specification so = Specification.load(nd, this);
          specificationsById.put(so.getId(), so);
        }
      }
    }

    Node specrelgroups = getFirstChild(content, "SPEC-RELATION-GROUPS");
    if (specrelations != null) {
      nl = specrelgroups.getChildNodes();
      for (int i = 0; i < nl.getLength(); i++) {
        Node nd = nl.item(i);
        String name = nd.getNodeName();
        if (name != null) {
          if (name.startsWith("SPEC-GROUP")) {
            SpecGroup so = SpecGroup.load(nd, this);
            specRelationGroupsById.put(so.getId(), so);
          }
        }
      }
    }

    Node toolexts = getFirstChild(root, ToolExtensions.TAG);
    if (toolexts != null) ext = ToolExtensions.load(this, toolexts);
  }

  private DocumentBuilder builder;

  public Document saveDoors() {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    // DocumentBuilder builder = null;
    try {
      dbf.setNamespaceAware(true);
      builder = dbf.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
    DOMImplementation DOMImplementation = builder.getDOMImplementation();
    Document doc =
        DOMImplementation.createDocument(
            nameSpaceToURI.get("xmlns"), // namespace
            "REQ-IF",
            null /* DocumentType */);
    // Document doc = (Document) builder.newDocument();
    doc.setXmlStandalone(true);
    // Element root = ReqIFDefinition.createElement(,"REQ-IF");
    // doc.appendChild(root);
    Element root = doc.getDocumentElement();
    root.setPrefix("");
    for (String s : attrs.keySet()) {
      if (!nameSpaceToURI.containsKey(s))
        if (attrs.get(s).getFirst() != null && !attrs.get(s).getFirst().isEmpty())
          root.setAttribute(attrs.get(s).getFirst() + ":" + s, attrs.get(s).getSecond());
        else root.setAttribute(s, attrs.get(s).getSecond());
    }
    for (String s : nameSpaceToURI.keySet()) {
      if (s == null || s.equals("xmlns") || s.equals(""))
        root.setAttribute("xmlns", nameSpaceToURI.get("xmlns"));
      else root.setAttribute("xmlns:" + s, nameSpaceToURI.get(s));
    }

    Node head = doc.createElement("THE-HEADER");
    root.appendChild(head);

    if (header != null) header.save(head, doc, this);
    Node core = doc.createElement("CORE-CONTENT");
    root.appendChild(core);

    Node content = doc.createElement("REQ-IF-CONTENT");
    core.appendChild(content);

    Node datatypes = doc.createElement("DATATYPES");
    content.appendChild(datatypes);

    for (DatatypeDefinition dd : getSorted(dataDefsById.values())) dd.save(datatypes, doc, this);

    Node spectypes = doc.createElement("SPEC-TYPES");
    content.appendChild(spectypes);

    for (SpecificationType st : specificationTypeById.values()) st.save(spectypes, doc, this);
    for (SpecObjectType st : getSorted(specObjectTypesById.values())) st.save(spectypes, doc, this);
    for (SpecRelationType st : getSorted(specRelationTypesById.values()))
      st.save(spectypes, doc, this);

    Node specobjects = doc.createElement("SPEC-OBJECTS");
    content.appendChild(specobjects);
    List<SpecObject> sos = new ArrayList<>(objectById.values());
    Collections.sort(
        sos,
        new Comparator<SpecObject>() {

          @Override
          public int compare(SpecObject o1, SpecObject o2) {
            if (ind.containsKey(o1.getId()) && ind.containsKey(o2.getId()))
              return ind.get(o1.getId()).compareTo(ind.get(o2.getId()));
            return o1.getId().compareTo(o2.getId());
          }
        });
    for (SpecObject so : sos)
      // if(!ignoreSave.contains(so.getId()))
      so.save(specobjects, doc, this);

    if (specRelationById.values().size() > 0) {
      Node specrelations = doc.createElement("SPEC-RELATIONS");
      content.appendChild(specrelations);
      for (SpecRelation sr : getSorted(specRelationById.values()))
        sr.save(specrelations, doc, this);
    }

    Node specifications = doc.createElement("SPECIFICATIONS");
    content.appendChild(specifications);
    for (Specification so : specificationsById.values()) so.save(specifications, doc, this);
    if (specRelationById.values().size() > 0) {
      Node specrelgroups = doc.createElement("SPEC-RELATION-GROUPS");
      content.appendChild(specrelgroups);
      for (SpecGroup srg : specRelationGroupsById.values()) srg.save(specrelgroups, doc, this);
    }
    if (ext != null) ext.save(root, doc, this);
    return doc;
  }

  protected static <T extends ReqIFDefinition> List<T> getSorted(Collection<T> values) {
    List<T> l = new ArrayList<>(values);
    Collections.sort(
        l,
        new Comparator<T>() {
          @Override
          public int compare(T o1, T o2) {
            return Integer.compare(o1.sort, o2.sort);
          }
        });
    return l;
  }

  public Document convertCatalogToDocument(TreeDB db, Map<UUID, String> reqtodoors, String path) {
    return null;
  }

  public static Object loadUValue(Node node, EntityWithAttributeDefs sot) {
    String name = node.getNodeName();
    if (name != null) {
      if (name.startsWith("ENUM")) return EnumValue.load(node);
      if (name.startsWith("ATTRIBUTE-VALUE-ENUMERATION")) return EnumAttributeValue.load(node, sot);
      else if (name.startsWith("ATTRIBUTE-VALUE-XHTML")) return XHTMLAttributeValue.load(node, sot);
      else if (name.startsWith("ATTRIBUTE-VALUE-")) return DefaultAttributeValue.load(node, sot);
    }
    return null;
  }

  public static AbstractAttributeValue loadValue(Node node, SpecObjectType sot) {
    Object def = loadUValue(node, sot);
    if (def instanceof AbstractAttributeValue) return (AbstractAttributeValue) def;
    return null;
  }

  public AttributeDefinition locateAttributeDefinition(String ref) {
    for (SpecObjectType sot : specObjectTypesById.values()) {
      AttributeDefinition ad = sot.getAttributeDefinition(ref);
      if (ad != null) return ad;
    }
    return null;
  }

  public Set<Specification> getSpecifications() {
    Set<Specification> specs = new HashSet<>();
    for (Specification s : specificationsById.values()) specs.add(s);
    return specs;
  }

  
  public Set<SpecificationType> getSpecificationsTypes() {
    Set<SpecificationType> sots = new HashSet<>();
    
    for (SpecificationType sot : specificationTypeById.values()) sots.add(sot);
    return sots;
  }
  
  public Set<SpecObjectType> getSpecObjectTypes() {
    Set<SpecObjectType> sots = new HashSet<>();
    
    for (SpecObjectType sot : specObjectTypesById.values()) sots.add(sot);
    return sots;
  }

  public String getPath() {
    return path;
  }

  public Set<SpecObject> getSpecObjectsByType(String id) {
    Set<SpecObject> set = new HashSet<>();
    for (Specification s : getSpecifications()) {
      for (SpecObject so : s.getSpecObjects()) if (so.definition.getId().equals(id)) set.add(so);
    }
    return set;
  }

  public Collection<DatatypeDefinition> getDatatypes() {
    return dataDefsById.values();
  }

  private static File f = null;

  {
    try {
      f = File.createTempFile("tempMapping", "");
      f.deleteOnExit();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static void saveMapping(Map<UUID, String> dids) {
    FileWriter fw = null;
    BufferedWriter bos = null;
    try {
      fw = new FileWriter(f);
      bos = new BufferedWriter(fw);
      for (UUID uid : dids.keySet()) {
        bos.write(uid.toString() + "|" + dids.get(uid));
        bos.newLine();
      }
      
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    finally{
      try {
        if(bos!=null)
          bos.close();
        if(fw!=null)
          fw.close();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  public static HashMap<UUID, String> loadMapping() {
    HashMap<UUID, String> map = new HashMap<>();
    try {
      FileReader fr = new FileReader(f);
      BufferedReader br = new BufferedReader(fr);
      while (br.ready()) {
        String s = br.readLine();
        int pos = s.indexOf("|");
        if (pos > -1) {
          UUID uid = UUID.fromString(s.substring(0, pos));
          map.put(uid, s.substring(pos + 1));
        }
      }
      br.close();
      fr.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return map;
  }

  public static int FOREIGN = 1, ORIGIN = 2;

  public void buildOriginMaps(Map<Specification, TreeDB> mp, int m) {
    for (Specification s : mp.keySet()) {
      TreeDB db = mp.get(s);
      buildOriginMaps(s, db, m);
    }
    // saveMapping(dids);
  }

  public void buildOriginMaps(Specification s, TreeDB db, int m) {
    UUID rid = db.getRootNode().getUUId();
    TreeNode reqroot = db.getNode(Requirement.getTypeRootQId());
    rdoorsMapping.put(rid, new HashMap<String, UUID>());
    doorsMapping.put(rid, new HashMap<UUID, String>());
    doorsMapping.get(rid).put(reqroot.getUUId(), s.getId()); // TODO proper
    rdoorsMapping.get(rid).put(s.getId(), reqroot.getUUId());
    Map<String, UUID> ids = new HashMap<>();
    Map<UUID, String> dids = new HashMap<>();
    Map<UUID, Date> lmod = new HashMap<>();
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(reqroot);
    Iterator<TreeNode> it = dftw.iterator();
    if ((m & FOREIGN) == FOREIGN)
      while (it.hasNext()) {
        TreeNode node = it.next();
        Attribute a = node.attrs().getAttribute(FIDUpdater.getFIDName(node.getTreeDB()));
        if (a != null) {
          String val = a.getValue().toString();
          if (val != null) {
            UUID uid = node.getUUId();
            ids.put(val, uid);
            // dids.put(uid, val);
          }
        }
      }
    // saveMapping(dids);
    // for(Specification s:specificationsById.values())
    for (SpecObject so : s.getSpecObjects()) {
      AbstractAttributeValue id = so.getAttributeValueByName("ReqIF.ForeignID");
      UUID map = null;
      if (id != null && !getDeleted().contains(so) && (m & FOREIGN) == FOREIGN) {
        String sid = id.getString();
        if (sid != null) {
          if (ids.containsKey(sid)) {
            map = ids.get(sid);
            dids.put(map, sid);
          }
        }
      }
      // TODO mapping method selection
      if (map == null && (m & ORIGIN) == ORIGIN) {
        AbstractAttributeValue or = so.getAttributeValueByName("Origin");
        if (or != null && !getDeleted().contains(so)) {
          String val = or.getString();
          TreeNode node = null;
          String uuid = null;
          if (!val.contains("|")) node = db.getNode(or.getString(), Path.QID | Path.UUID | Path.UV);
          else {
            String[] prs = val.split("\\|");
            String uvid = null, qid = null;
            for (String i : prs) {
              if (i.startsWith("i:")) uuid = i.substring(2);
              else if (i.startsWith("u:")) uvid = i.substring(2);
              else if (i.startsWith("q:")) qid = i.substring(2);
            }
            if (uuid != null) node = db.getNode(UUID.fromString(uuid));
            if (node == null && qid != null) {
              node = db.getNode(qid, Path.QID);
            }
            if (node == null && uvid != null) {
              List<TreeNode> nodes = Collections.emptyList();
              try {
                nodes = db.getLinkResolver().findNodesByPathWithDisposed(null, null, db, uvid, Path.UV);
              } catch (NodeNotFoundException e) {
              }
              if (nodes.size() > 0) node = nodes.get(0);
            }
          }
          if(id!=null)
            if (node != null) {
              map = node.getUUId();
              dids.put(map, id.getString());
            } else {
              if (uuid != null) dids.put(UUID.fromString(uuid), id.toString()); // Try
              // gather
              // another
              // id
              RLogger.logInfo("INFO: " + or.getString() + " was not found, id note added");
              // node = db.getNode(or, Path.QID|Path.UUID|Path.UV);
            }
        }
      }
      if (map != null) {
        boolean add = true;
        Date late = null;
        try {
          late = (AttributeType.DATE_FORMAT.parse(so.getLastChange()));
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        Map<UUID, String> dmap = doorsMapping.get(rid);
        if (dmap.containsKey(map)) {
          RLogger.logInfo("origin duplication! " + map);
          Date oldlast = lmod.get(map);
          if (late == null || late.getTime() < (oldlast.getTime())) {
            rdoorsMapping.get(rid).remove(so.getId());//TODO test?
            add = true;
          }
        }
        if (add) {
          doorsMapping.get(rid).put(map, so.getId());
          rdoorsMapping.get(rid).put(so.getId(), map);
          lmod.put(map, late);
        }
      }
    }
    for (UUID su : doorsMapping.get(rid).keySet()) {
      String val = doorsMapping.get(rid).get(su);
      ReqIFDefinition ridd = getObject(val);
      if (ridd instanceof SpecObject) {
        SpecObject so = (SpecObject) ridd;
        AbstractAttributeValue aav = so.getAttributeValueByName(DOORSConstants.FNUMBER);
        if (aav != null) dids.put(su, aav.getString());
      }
    }
  }

  private ReqIFDefinition getObject(String val) {
    if (specificationsById.containsKey(val)) return specificationsById.get(val);
    if (objectById.containsKey(val)) return objectById.get(val);
    if (specRelationById.containsKey(val)) return specificationsById.get(val);
    return null;
  }

  public Map<UUID, String> getMapping(TreeDB db) {
    return doorsMapping.get(db.getRootNode().getUUId());
  }

  public Map<UUID, String> getFidMapping(TreeDB db) {
    UUID ridu = db.getRootNode().getUUId();
    Map<UUID, String> ret = new HashMap<>();
    for (UUID uid : getMapping(db).keySet()) {
      ReqIFDefinition rid = getObject(getMapping(db).get(uid));
      if (rid != null && rid instanceof SpecObject) {
        SpecObject so = (SpecObject) rid;
        AbstractAttributeValue aav = so.getAttributeValueByName("ReqIF.ForeignID");
        if (aav != null) {
          String val = aav.getString();
          if (val != null && !val.equals("0")) ret.put(uid, val);
        }
      }
    }
    return ret;
  }

  public DOORSToolExtension getDoorsExt() {
    if (ext == null) ext = createToolsExtensions();
    DOORSToolExtension dte = (DOORSToolExtension) ext.getExtension(DOORSToolExtension.TAG);
    return dte;
  }

  private ToolExtensions createToolsExtensions() {
    ToolExtensions te = new ToolExtensions();
    DOORSToolExtension ste = new DOORSToolExtension();
    te.exts.add(ste);
    ste.tex = new TypeExtensions();
    ste.drifd =
        new DoorsRIFDefinition(
            new ArrayList<ModuleDefinition>(), "_" + UUID.randomUUID().toString());
    ste.ai = new ApplicationInfo();
    ste.ai.map.put("VERSION", "9.4.0.1"); // mask to doors
    ste.ai.map.put("DOORS-REQ-IF-VERSION", "4");
    ste.ai.map.put("BUILD-NUMBER", "4");
    ste.ai.map.put("BUILD-DATE", getTime());
    ste.rao = new ReadOnlyAttributes();
    ste.setDeleted(new Deleted());
    return null;
  }

  public Set<SpecObject> getDeleted() {
    DOORSToolExtension dte = getDoorsExt();
    return new HashSet<>(dte.getDeleted().getDeleted());
  }

  public void registerDeleted(SpecObject so) {
    DOORSToolExtension dte = getDoorsExt();
    dte.getDeleted().addDeleted(so);
  }

  public Map<String, UUID> getRMapping(TreeDB db) {
    return rdoorsMapping.get(db.getRootNode().getUUId());
  }

  public Set<SpecGroup> getSpecGroups() {
    return new HashSet<>(specRelationGroupsById.values());
  }

  public static ReqIFModel createEmpty(String path) {
    ReqIFModel model = new ReqIFModel(path);
    // TODO create basic
    return model;
  }

  public String getNamespaceURI(String string) {
    if (string == null || string.equals("") || string.equals("xmlns")) return null;
    return nameSpaceToURI.get(string);
  }

  protected DocumentBuilder getBuilder() {
    return builder;
  }

  public Set<SpecRelation> getSpecRelations() {
    Set<SpecRelation> rels = new HashSet<>();
    for (SpecRelation sr : specRelationById.values()) rels.add(sr);
    return rels;
  }

  public AttributeDefinition getAttributeDef(String defs) {
    for (SpecObjectType sot : getSpecObjectTypes()) {
      AttributeDefinition ad = sot.getAttributeDefinition(defs);
      if (ad != null) return ad;
    }
    for (SpecificationType sot : getSpecificationsTypes()) {
      AttributeDefinition ad = sot.getAttributeDefinition(defs);
      if (ad != null) return ad;
    }
    return null;
  }

  public void setPath(String path2) {
    this.path = path2;
  }

  public void registerSpecificationType(SpecificationType st) {
    specificationTypeById.put(st.getId(), st);
  }

  public void registerSpecification(Specification st) {
    specificationsById.put(st.getId(), st);
  }

  public void registerSpecObjectType(SpecObjectType sot) {
    specObjectTypesById.put(sot.getId(), sot);
  }

  public void setOldPath(String path2) {
    oldpath = path2;
  }

  public String getOldPath() {
    return oldpath;
  }

  public Collection<SpecObject> getSpecObjects() {
    return objectById.values();
  }

  public void addIgnoreSave(String ign) {
    ignoreSave.add(ign);
  }

  public void propagateNotIgnore(Specification spec) {
    SpecHierarchyContainer shc = spec.getChildren();
    forceRecNI(null, shc);
  }

  private boolean forceRecNI(SpecObject so, SpecHierarchyContainer shc) {
    boolean b = so != null ? !ignoreSave.contains(so.getId()) : false;
    if (shc != null)
      for (SpecHierarchy sh : shc) {
        if (forceRecNI(sh.getSpecObject(), sh.children)) b = true;
      }
    if (b && so != null) ignoreSave.remove(so.getId());
    return b;
  }

  public boolean isIncorrect() {
    return specificationsById.size() < 1;
  }

  public SpecObjectType createObjectType() {
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(
        SpecRelationType.ID_PARAM,
        new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    attrs.put(SpecRelationType.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    SpecObjectType srt = new SpecObjectType(attrs);
    AttributeDefinition ad;
    DatatypeDefinition intdef =
        createDatatypeDefinition("INTEGER", "Integer", "http://www.w3.org/2001/XMLSchema#int");
    DatatypeDefinition datedef =
        createDatatypeDefinition("DATE", "Date", "http://www.w3.org/2001/XMLSchema#dateTime");
    DatatypeDefinition stringdef =
        createDatatypeDefinition("XHTML", "String", "http://www.w3.org/2001/XMLSchema#string");
    DatatypeDefinition createdthru = createDatatypeDefinition("ENUMERATION", "Mapping", null);

    EnumValue mane = createEnumValue("Manual Input", "0", "", "");
    createdthru.addValue(mane, 0);
    createdthru.addValue(createEnumValue("Extracting", "1", "", ""), 1);
    createdthru.addValue(createEnumValue("Copying", "2", "", ""), 2);

    DatatypeDefinition booleandef = createDatatypeDefinition("BOOLEAN", "Boolean", null);

    DatatypeDefinition tbb = createDatatypeDefinition("ENUMERATION", "TableEdgeType", null);

    tbb.addValue(createEnumValue("TableBorderNone", "0", "", ""), 0);
    tbb.addValue(createEnumValue("TableBorderFull", "0", "", ""), 1);
    tbb.addValue(createEnumValue("TableBorderDotted", "0", "", ""), 2);

    DatatypeDefinition tca = createDatatypeDefinition("ENUMERATION", "TableCellAlign", null);

    tca.addValue(createEnumValue("CellLeft", "0", "", ""), 0);
    tca.addValue(createEnumValue("CellRight", "0", "", ""), 1);
    tca.addValue(createEnumValue("CellCenter", "0", "", ""), 2);
    tca.addValue(createEnumValue("CellFull", "0", "", ""), 2);

    DatatypeDefinition tt = createDatatypeDefinition("ENUMERATION", "TableType", null);

    EnumValue tn = createEnumValue("TableNone", "0", "", "");
    tt.addValue(tn, 0);
    tt.addValue(createEnumValue("TableBase", "0", "", ""), 1);
    tt.addValue(createEnumValue("TableRow", "0", "", ""), 2);
    tt.addValue(createEnumValue("TableCell", "0", "", ""), 2);

    createAttributeDefinition(srt, intdef, "ReqIF.ForeignID", "System Attribute", true);
    createAttributeDefinition(srt, datedef, "ReqIF.ForeignCreatedOn", "System Attribute", true);

    ad =
        createAttributeDefinition(
            srt, createdthru, "ReqIF.ForeignCreatedThru", "System Attribute", true);
    ad.defvalues = new LinkedList<>();
    ad.defvalues.add(new EnumAttributeValue(ad, new EnumValueRef(mane)));

    createAttributeDefinition(srt, stringdef, "ReqIF.Description", "System Attribute", true);
    createAttributeDefinition(srt, datedef, "Last Modified On", "System Attribute", true);

    createAttributeDefinition(srt, stringdef, "ReqIF.Name", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "ReqIF.ChapterName", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "ReqIF.Text", "System Attribute", true);
    ad = createAttributeDefinition(srt, booleandef, "OLE", "System Attribute", true);
    ad.defvalues = new LinkedList<>();
    ad.defvalues.add(new DefaultAttributeValue(ad, "BOOLEAN", "false"));

    ad = createAttributeDefinition(srt, booleandef, "OLEIconic", "System Attribute", true);
    ad.defvalues = new LinkedList<>();
    ad.defvalues.add(new DefaultAttributeValue(ad, "BOOLEAN", "false"));

    ad = createAttributeDefinition(srt, booleandef, "Picture", "System Attribute", true);
    ad.defvalues = new LinkedList<>();
    ad.defvalues.add(new DefaultAttributeValue(ad, "BOOLEAN", "false"));

    createAttributeDefinition(srt, stringdef, "PictureName", "System Attribute", true);
    createAttributeDefinition(srt, intdef, "PictureNum", "System Attribute", true);

    createAttributeDefinition(srt, stringdef, "ReqIF.Prefix", "System Attribute", true);

    createAttributeDefinition(srt, tbb, "TableBottomBorder", "System Attribute", true);
    createAttributeDefinition(srt, tca, "TableCellAlign", "System Attribute", true);
    createAttributeDefinition(srt, intdef, "TableCellWidth", "System Attribute", true);
    createAttributeDefinition(srt, booleandef, "TableChangeBars", "System Attribute", true);
    createAttributeDefinition(srt, tbb, "TableLeftBorder", "System Attribute", true);
    createAttributeDefinition(srt, booleandef, "TableLinkIndicators", "System Attribute", true);
    createAttributeDefinition(srt, tbb, "TableRightBorder", "System Attribute", true);
    createAttributeDefinition(srt, booleandef, "TableShowAttrs", "System Attribute", true);
    createAttributeDefinition(srt, booleandef, "TableShowBookform", "System Attribute", true);
    createAttributeDefinition(srt, booleandef, "TableShowWide", "System Attribute", true);
    createAttributeDefinition(srt, tbb, "TableTopBorder", "System Attribute", true);

    ad = createAttributeDefinition(srt, tt, "TableType", "System Attribute", true);
    ad.defvalues = new LinkedList<>();
    ad.defvalues.add(new EnumAttributeValue(ad, new EnumValueRef(tn)));
    specObjectTypesById.put(srt.getId(), srt);
    return srt;
  }

  public SpecRelationType createRelationType() {
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(
        SpecRelationType.ID_PARAM,
        new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    attrs.put(SpecRelationType.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    SpecRelationType srt = new SpecRelationType(attrs);
    createAttributeDefinition(
        srt,
        createDatatypeDefinition("INTEGER", "Integer", "http://www.w3.org/2001/XMLSchema#int"),
        "ReqIF.ForeignID",
        "System Attribute",
        true);
    DatatypeDefinition datedef =
        createDatatypeDefinition("DATE", "Date", "http://www.w3.org/2001/XMLSchema#dateTime");
    DatatypeDefinition stringdef =
        createDatatypeDefinition("XHTML", "String", "http://www.w3.org/2001/XMLSchema#string");
    DatatypeDefinition mappingdef = createDatatypeDefinition("ENUMERATION", "Mapping", null);
    mappingdef.addValue(createEnumValue("Many-to-Many", "0", "", ""), 0);
    mappingdef.addValue(createEnumValue("Many-to-One", "0", "", ""), 1);
    mappingdef.addValue(createEnumValue("One-to-Many", "0", "", ""), 2);
    mappingdef.addValue(createEnumValue("One-to-One", "0", "", ""), 3);

    createAttributeDefinition(srt, datedef, "ReqIF.ForeignCreatedOn", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "ReqIF.Description", "System Attribute", true);
    createAttributeDefinition(srt, datedef, "Last Modified On", "System Attribute", true);
    createAttributeDefinition(srt, mappingdef, "Mapping", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "ReqIF.Name", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "ReqIF.Prefix", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "Source", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "Source Index", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "Target", "System Attribute", true);
    createAttributeDefinition(srt, stringdef, "Target Index", "System Attribute", true);
    specRelationTypesById.put(srt.getId(), srt);
    return srt;
  }

  private EnumValue createEnumValue(String name, String avkey, String avother, String avval) {
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(
        SpecRelationType.ID_PARAM,
        new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    attrs.put(SpecRelationType.NAME_PARAM, new Pair<String, String>(null, name));
    attrs.put(SpecRelationType.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    attrs.put("LONG-NAME", new Pair<String, String>(null, name));
    EmbeddedValue aev = new EmbeddedValue(avkey, avother, avval);
    return new EnumValue(attrs, aev);
  }

  private DatatypeDefinition createDatatypeDefinition(String type, String name, String sameas) {
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(
        SpecRelationType.ID_PARAM,
        new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    attrs.put(SpecRelationType.NAME_PARAM, new Pair<String, String>(null, name));
    attrs.put(SpecRelationType.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    attrs.put("LONG-NAME", new Pair<String, String>(null, name));
    DatatypeDefinition dd = new DatatypeDefinition(attrs, type);
    dataDefsById.put(dd.getId(), dd);
    if (sameas != null) {
      DOORSToolExtension dte = getDoorsExt();
      if (dte != null) {
        if (dte.tex == null) dte.tex = new TypeExtensions();
        dte.tex.defs.add(new AttributeDefExtension(dd, sameas));
      } else throw new RuntimeException("TODO create extensions");
    }
    return dd;
  }

  private AttributeDefinition createAttributeDefinition(
      SpecObjectType srt,
      DatatypeDefinition datatypeDefinition,
      String name,
      String desc,
      boolean readonly) {
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(
        SpecRelationType.ID_PARAM,
        new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    attrs.put(SpecRelationType.NAME_PARAM, new Pair<String, String>(null, name));
    attrs.put(SpecRelationType.ATTR_DESC, new Pair<String, String>(null, desc));
    attrs.put("IS_EDITABLE", new Pair<String, String>(null, "true"));
    attrs.put(SpecRelationType.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    DOORSToolExtension dte = getDoorsExt();
    // TODO enum
    AttributeDefinition ad =
        new AttributeDefinition(datatypeDefinition, attrs, datatypeDefinition.suffix);
    srt.addAttributeDefinition(ad);
    if (dte != null && readonly) {
      if (dte.rao == null) dte.rao = new ReadOnlyAttributes();
      dte.rao.defs.add(ad);
    }
    return ad;
  }

  public Specification createSpecification(String name) {
    Iterator<SpecificationType> it = specificationTypeById.values().iterator();
    SpecificationType st = null;
    if (it.hasNext()) st = it.next();
    Map<String, Pair<String, String>> sattrs;
    if (st == null) {
      sattrs = new HashMap<>();
      sattrs.put(
          SpecObject.ID_PARAM, new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
      sattrs.put(SpecObject.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
      st = new SpecificationType(sattrs);
      specificationTypeById.put(st.getId(), st);
    }
    sattrs = new HashMap<>();
    sattrs.put(
        SpecObject.ID_PARAM, new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    sattrs.put(SpecObject.NAME_PARAM, new Pair<String, String>(null, name));
    sattrs.put(SpecObject.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    Specification s = new Specification(sattrs, st);
    specificationsById.put(s.getId(), s);
    createSpecGroup(s.getId(), name + "_refs");
    DOORSToolExtension dte = getDoorsExt();
    dte.drifd.modules.add(new ModuleDefinition("DDC_FULL_MODULE", s));
    return s;
  }

  private ReqIFHeader createHeader() {
    Map<String, Pair<String, String>> sattrs = new HashMap<>();
    sattrs.put(
        SpecObject.ID_PARAM, new Pair<String, String>(null, "_" + UUID.randomUUID().toString()));
    header = new ReqIFHeader(sattrs);
    header.map.put(ReqIFHeader.KEY_REQTOOLID, "Requality");
    header.map.put(ReqIFHeader.KEY_TOOLVERSION, "1.0");
    header.map.put(ReqIFHeader.KEY_SRCTOOLID, "Requality");
    header.map.put(ReqIFHeader.KEY_TIME, getTime());
    header.map.put(ReqIFHeader.KEY_TITLE, "Requality, " + getTime());
    return header;
  }

  private SpecGroup createSpecGroup(String id, String name) {
    SpecObjectType sot = createObjectType();
    Map<String, Pair<String, String>> sattrs = new HashMap<>();
    sattrs.put(SpecObject.ID_PARAM, new Pair<String, String>(null, id));
    sattrs.put(SpecObject.NAME_PARAM, new Pair<String, String>(null, name));
    sattrs.put(SpecObject.CHANGE_PARAM, new Pair<String, String>(null, getTime()));
    SpecGroup sg = new SpecGroup(sattrs, sot);
    AttributeDefinition ad = sg.getAttributeDefinitionByName("ReqIF.Name"); // TODO
    // move
    sg.setValue(ad.getId(), new XHTMLAttributeValue(ad, name));
    return sg;
  }

  public SpecObject createSpecObject(Specification spec, String id) {
    SpecGroup sg = specRelationGroupsById.get(spec.getId());
    Map<String, Pair<String, String>> attrs = new HashMap<>();
    attrs.put(ReqIFDefinition.ID_PARAM, new Pair<String, String>(null, id));
    attrs.put(ReqIFDefinition.CHANGE_PARAM, new Pair<String, String>(null, ReqIFModel.getTime()));
    SpecObject so = new DoorsSpecObject(attrs, sg.definition);
    objectById.put(so.getId(), so);
    objectToSpec.put(so.getId(), spec);
    ind.put(so.getId(), maxind++);
    return so;
  }

  public SpecGroup getSpecGroup(String id) {
    return specRelationGroupsById.get(id);
  }

  public Specification getSpecificationById(String id) {
    return specificationsById.get(id);
  }
}
