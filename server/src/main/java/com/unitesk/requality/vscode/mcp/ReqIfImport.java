package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.ReqIFConverter;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.RequalityReqIfStructConverter;
import com.unitesk.requality.tool.reqif.SpecObjectType;
import com.unitesk.requality.tool.reqif.Specification;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * import_reqif: the requirements of one specification of a ReqIF file (the exchange format of DOORS, Polarion and other
 * tools) become the requirements of a project, with the structure of the specification, in the transaction of the
 * caller. ReqIF.Text becomes the description, ReqIF.Name and ReqIF.ChapterName the name, the other attributes keep their
 * names; an enumeration becomes an enumeration of the project. The importer of Requality reads the pictures and OLE
 * objects of the XHTML values from files next to the ReqIF file and converts them with other programs: they are left
 * out (the result counts them).
 */
final class ReqIfImport {
  private ReqIfImport() {
  }

  static JsonObject run(TreeDB db, File file, String spec, boolean replace, boolean force) throws Exception {
    TreeNode requirements = db.getNode(Requirement.getTypeRootQId(), com.unitesk.requality.core.model.Path.QID);
    if (requirements == null)
      throw new ToolError("The project has no " + Requirement.getTypeRootQId());
    int existing = 0;
    for (TreeNode c : requirements.getWorkChildren())
      if (c != null && !c.isDisposed())
        existing++;
    // the importer makes /Requirements the specification: a node it does not find in the file goes
    if (existing > 0 && !replace)
      throw new ToolError(Requirement.getTypeRootQId() + " has " + existing + " nodes: the import makes it the specification "
          + "of the file, and the nodes that are not in the file go. Import into a new project (create_project), or give "
          + "replace true (revert_change brings them back)");
    Document dom = parse(file);
    int objects = removeObjects(dom.getDocumentElement());
    complete(dom);
    // the requirements match the objects of the file by ReqIF.ForeignID (the FID attribute of the project): an object
    // without one gets its identifier, so that a later import updates the same requirements
    foreignIds(dom);
    ReqIFModel model = new ReqIFModel(file.getParentFile().toURI().toString());
    try {
      model.loadDoors(dom);
    } catch (RuntimeException e) {
      throw new ToolError("The file " + file.getName() + " is no ReqIF file this importer reads: " + Core.message(e));
    }
    List<Specification> specs = new ArrayList<>(model.getSpecifications());
    if (specs.isEmpty())
      throw new ToolError("The file " + file.getName() + " has no specification");
    Specification s = choose(specs, spec);
    // every attribute of the file: the importer needs a target for each one (key "<prefix>.<name>")
    Map<String, String> attrs = new LinkedHashMap<>();
    for (SpecObjectType t : model.getSpecObjectTypes())
      for (com.unitesk.requality.tool.reqif.AttributeDefinition ad : t.getAttributeDefinitions()) {
        String name = ad.getName();
        String target;
        if ("ReqIF.Text".equals(name))
          target = TreeNode.ATTR_DESCRIPTION;
        else if ("ReqIF.Name".equals(name) || "ReqIF.ChapterName".equals(name))
          target = TreeNode.ATTR_NAMEORID;
        else
          target = com.unitesk.requality.core.attribute.Attribute.getValidName(name);
        attrs.put(ad.getNameSpacePrefix() + "." + name, target);
      }
    // the importer removes every node under /Requirements that is no object of the file: the other nodes (test
    // purposes, comments, text) wait in a folder of their own and come back under their requirements
    Map<String, String> before = new LinkedHashMap<>();
    List<TreeNode> others = new ArrayList<>();
    java.util.ArrayDeque<TreeNode> walk = new java.util.ArrayDeque<>(requirements.getWorkChildren());
    while (!walk.isEmpty()) {
      TreeNode x = walk.poll();
      if (x == null || x.isDisposed() || x.isVirtual())
        continue;
      before.put(x.getSUUId(), x.getQualifiedId());
      if (!x.getType().equals(Requirement.TYPE_NAME))
        others.add(x);
      else
        walk.addAll(x.getWorkChildren());
    }
    Map<TreeNode, java.util.UUID> parents = new LinkedHashMap<>();
    TreeNode hold = null;
    if (!others.isEmpty()) {
      TreeNode root = db.getRootNode();
      String id = "reqif-import";
      for (int i = 2; root.findWorkChild(id) != null; i++)
        id = "reqif-import-" + i;
      hold = root.createChild(id, "RestrictedFolder", TreeDB.EMPTY_TEMPLATE);
      for (TreeNode x : others) {
        parents.put(x, x.getWorkParentUUID());
        new com.unitesk.requality.core.operation.MoveOperation(x, hold, null, true).excecute();
      }
    }
    RequalityReqIfStructConverter conv = converter();
    try {
      conv.convertDoorsToReq(s, db, attrs, model);
    } catch (RuntimeException e) {
      throw new ToolError("The import of " + spec(s) + " failed: " + Core.message(e) + ". Nothing was changed");
    }
    // back under their requirements; the ones whose requirement went go too (a test purpose only with force)
    List<String> lost = new ArrayList<>();
    for (Map.Entry<TreeNode, java.util.UUID> e : parents.entrySet()) {
      TreeNode x = e.getKey();
      TreeNode parent = db.getNode(e.getValue());
      if (parent != null && !parent.isDisposed())
        new com.unitesk.requality.core.operation.MoveOperation(x, parent, null, true).excecute();
      else
        lost.add(before.get(x.getSUUId()) + " (" + x.getType() + ")");
    }
    if (!lost.isEmpty() && !force)
      throw new ToolError("The import deletes the requirements of " + lost.size() + " test purposes or other nodes that are "
          + "not in the file: " + (lost.size() > 10 ? lost.subList(0, 10) + " and " + (lost.size() - 10) + " more" : lost)
          + ". Nothing was changed. Give force true to import anyway");
    if (hold != null)
      hold.delete();
    JsonObject r = new JsonObject();
    r.addProperty("file", file.getPath());
    r.addProperty("specification", spec(s));
    int n = 0;
    java.util.ArrayDeque<TreeNode> q = new java.util.ArrayDeque<>(requirements.getWorkChildren());
    while (!q.isEmpty()) {
      TreeNode x = q.poll();
      if (x == null || x.isDisposed())
        continue;
      if (x.getType().equals(Requirement.TYPE_NAME))
        n++;
      q.addAll(x.getWorkChildren());
    }
    r.addProperty("requirements", n);
    // the nodes of the project that are not in the file any more
    JsonArray deleted = new JsonArray();
    int deletedCount = 0;
    for (Map.Entry<String, String> e : before.entrySet()) {
      TreeNode x = db.getNode(java.util.UUID.fromString(e.getKey()));
      if (x == null || x.isDisposed()) {
        if (deletedCount++ < 20)
          deleted.add(e.getValue());
      }
    }
    if (deletedCount > 0) {
      r.addProperty("deleted", deletedCount);
      r.add("deleted_first", deleted);
    }
    int kept = before.size() - deletedCount;
    if (!before.isEmpty())
      r.addProperty("kept", kept);
    // the attributes the requirements have now (besides name and description)
    r.add("attributes", RequalityTools.strings(new ArrayList<>(RequalityTools.userAttributes(requirements, 100_000))));
    if (objects > 0)
      r.addProperty("objects_left_out", objects);
    if (specs.size() > 1) {
      JsonArray otherSpecs = new JsonArray();
      for (Specification x : specs)
        if (x != s)
          otherSpecs.add(spec(x));
      r.add("other_specifications", otherSpecs);
    }
    return r;
  }

  private static String spec(Specification s) {
    return s.getName() != null && !s.getName().isEmpty() ? s.getName() + " (" + s.getId() + ")" : s.getId();
  }

  private static Specification choose(List<Specification> specs, String given) {
    if (given == null) {
      if (specs.size() == 1)
        return specs.get(0);
      List<String> names = new ArrayList<>();
      for (Specification x : specs)
        names.add(spec(x));
      throw new ToolError("The file has " + specs.size() + " specifications: give one as specification " + names);
    }
    for (Specification x : specs)
      if (given.equals(x.getId()) || given.equals(x.getName()))
        return x;
    List<String> names = new ArrayList<>();
    for (Specification x : specs)
      names.add(spec(x));
    throw new ToolError("No specification " + given + " in the file " + names);
  }

  /** The converter of the core (its attribute converters are made once, in the class of the ReqIF converter). */
  private static RequalityReqIfStructConverter converter() throws ReflectiveOperationException {
    java.lang.reflect.Field f = ReqIFConverter.class.getDeclaredField("structconv");
    f.setAccessible(true);
    return (RequalityReqIfStructConverter) f.get(null);
  }

  /** The XML of the file: no DOCTYPE (no external entities), the names with their prefixes as the importer reads them. */
  static Document parse(File file) throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    dbf.setXIncludeAware(false);
    dbf.setExpandEntityReferences(false);
    dbf.setIgnoringElementContentWhitespace(true);
    try {
      return dbf.newDocumentBuilder().parse(file);
    } catch (org.xml.sax.SAXException e) {
      throw new ToolError("The file " + file.getName() + " is no XML file this importer reads: " + e.getMessage());
    }
  }

  /** Removes the objects (pictures, OLE objects) of the XHTML values; keeps their text. Returns their number. */
  static int removeObjects(Element e) {
    int n = 0;
    NodeList l = e.getChildNodes();
    for (int i = l.getLength() - 1; i >= 0; i--) {
      Node c = l.item(i);
      if (!(c instanceof Element))
        continue;
      Element x = (Element) c;
      String local = x.getNodeName().substring(x.getNodeName().indexOf(':') + 1);
      if (local.equals("object")) {
        // the text of the object (its alternative text) stays, as a paragraph of its own: [text]
        String text = x.getTextContent();
        if (text != null && !text.isBlank()) {
          String prefix = x.getNodeName().contains(":") ? x.getNodeName().substring(0, x.getNodeName().indexOf(':') + 1) : "";
          Element p = e.getOwnerDocument().createElement(prefix + "p");
          p.setTextContent("[" + text.trim() + "]");
          e.insertBefore(p, x);
        }
        e.removeChild(x);
        n++;
      } else {
        n += removeObjects(x);
      }
    }
    return n;
  }

  /**
   * Gives each spec object without a ReqIF.ForeignID value its identifier as one (with an attribute definition
   * ReqIF.ForeignID in its type when the type has none).
   */
  static void foreignIds(Document dom) {
    Element content = first(first(dom.getDocumentElement(), "CORE-CONTENT"), "REQ-IF-CONTENT");
    Element types = first(content, "SPEC-TYPES");
    Element datatypes = first(content, "DATATYPES");
    // the attribute definition ReqIF.ForeignID of each spec object type (made when missing)
    Map<String, String> definition = new java.util.HashMap<>();
    String stringType = null;
    for (Element t : children(types, "SPEC-OBJECT-TYPE")) {
      Element attrs = first(t, "SPEC-ATTRIBUTES");
      if (attrs != null)
        for (Element ad : children(attrs, null))
          if ("ReqIF.ForeignID".equals(ad.getAttribute("LONG-NAME")))
            definition.put(t.getAttribute("IDENTIFIER"), ad.getAttribute("IDENTIFIER") + "|" + ad.getNodeName());
    }
    for (Element t : children(types, "SPEC-OBJECT-TYPE")) {
      String tid = t.getAttribute("IDENTIFIER");
      if (definition.containsKey(tid))
        continue;
      if (stringType == null) {
        Element dt = dom.createElement("DATATYPE-DEFINITION-STRING");
        stringType = "_requality-foreign-id-type";
        dt.setAttribute("IDENTIFIER", stringType);
        dt.setAttribute("LONG-NAME", "ForeignID");
        dt.setAttribute("MAX-LENGTH", "1000");
        dt.setAttribute("LAST-CHANGE", "2000-01-01T00:00:00Z");
        datatypes.appendChild(dt);
      }
      Element attrs = first(t, "SPEC-ATTRIBUTES");
      if (attrs == null) {
        attrs = dom.createElement("SPEC-ATTRIBUTES");
        t.appendChild(attrs);
      }
      Element ad = dom.createElement("ATTRIBUTE-DEFINITION-STRING");
      String adid = "_requality-foreign-id-" + tid;
      ad.setAttribute("IDENTIFIER", adid);
      ad.setAttribute("LONG-NAME", "ReqIF.ForeignID");
      ad.setAttribute("LAST-CHANGE", "2000-01-01T00:00:00Z");
      Element type = dom.createElement("TYPE");
      Element ref = dom.createElement("DATATYPE-DEFINITION-STRING-REF");
      ref.setTextContent(stringType);
      type.appendChild(ref);
      ad.appendChild(type);
      attrs.appendChild(ad);
      definition.put(tid, adid + "|ATTRIBUTE-DEFINITION-STRING");
    }
    for (Element so : children(first(content, "SPEC-OBJECTS"), "SPEC-OBJECT")) {
      Element typeRef = first(first(so, "TYPE"), "SPEC-OBJECT-TYPE-REF");
      String def = typeRef == null ? null : definition.get(typeRef.getTextContent().trim());
      if (def == null)
        continue;
      String adid = def.substring(0, def.indexOf('|'));
      Element values = first(so, "VALUES");
      if (values == null) {
        values = dom.createElement("VALUES");
        so.appendChild(values);
      }
      boolean has = false;
      for (Element v : children(values, null)) {
        Element d = first(v, "DEFINITION");
        if (d != null && d.getFirstChild() != null && adid.equals(d.getTextContent().trim()))
          has = true;
      }
      if (has || !def.endsWith("ATTRIBUTE-DEFINITION-STRING"))
        continue;
      Element v = dom.createElement("ATTRIBUTE-VALUE-STRING");
      v.setAttribute("THE-VALUE", so.getAttribute("IDENTIFIER"));
      Element d = dom.createElement("DEFINITION");
      Element r = dom.createElement("ATTRIBUTE-DEFINITION-STRING-REF");
      r.setTextContent(adid);
      d.appendChild(r);
      v.appendChild(d);
      values.appendChild(v);
    }
  }

  /** The child elements of the name (null: all). */
  private static List<Element> children(Element parent, String name) {
    List<Element> out = new ArrayList<>();
    if (parent == null)
      return out;
    NodeList l = parent.getChildNodes();
    for (int i = 0; i < l.getLength(); i++)
      if (l.item(i) instanceof Element && (name == null || l.item(i).getNodeName().equals(name)))
        out.add((Element) l.item(i));
    return out;
  }

  /** Adds the parts of a ReqIF file that the schema makes optional and the importer needs. */
  static void complete(Document dom) {
    Element root = dom.getDocumentElement();
    if (root == null || !root.getNodeName().equals("REQ-IF"))
      throw new ToolError("The file is no ReqIF file (its root is " + (root == null ? "missing" : root.getNodeName()) + ")");
    Element header = child(root, "THE-HEADER", 0);
    if (child(header, "REQ-IF-HEADER", -1) == null) {
      Element h = dom.createElement("REQ-IF-HEADER");
      h.setAttribute("IDENTIFIER", "_" + java.util.UUID.randomUUID());
      header.appendChild(h);
    }
    Element core = child(root, "CORE-CONTENT", -1);
    Element content = child(core, "REQ-IF-CONTENT", -1);
    for (String part : new String[] { "DATATYPES", "SPEC-TYPES", "SPEC-OBJECTS", "SPECIFICATIONS" })
      child(content, part, -1);
    if (first(content, "SPEC-RELATIONS") != null)
      child(content, "SPEC-RELATION-GROUPS", -1);
  }

  private static Element first(Element parent, String name) {
    if (parent == null)
      return null;
    NodeList l = parent.getChildNodes();
    for (int i = 0; i < l.getLength(); i++)
      if (l.item(i) instanceof Element && l.item(i).getNodeName().equals(name))
        return (Element) l.item(i);
    return null;
  }

  /** The child of this name; made when missing (at the position {@code at}: 0 first, -1 last). */
  private static Element child(Element parent, String name, int at) {
    Element c = first(parent, name);
    if (c != null)
      return c;
    c = parent.getOwnerDocument().createElement(name);
    if (at == 0 && parent.getFirstChild() != null)
      parent.insertBefore(c, parent.getFirstChild());
    else
      parent.appendChild(c);
    return c;
  }
}
