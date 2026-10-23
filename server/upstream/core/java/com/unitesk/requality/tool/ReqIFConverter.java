package com.unitesk.requality.tool;

import java.io.BufferedWriter;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.xml.sax.SAXException;

import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.cli.RequalityCLI;
import com.unitesk.requality.tool.reqif.AbstractAttributeValue;
import com.unitesk.requality.tool.reqif.AttributeDefinition;
import com.unitesk.requality.tool.reqif.DatatypeDefinition;
import com.unitesk.requality.tool.reqif.DefaultAttributeValue;
import com.unitesk.requality.tool.reqif.EnumAttributeValue;
import com.unitesk.requality.tool.reqif.EnumValue;
import com.unitesk.requality.tool.reqif.EnumValueRef;
import com.unitesk.requality.tool.reqif.NodeAttributeConverter;
import com.unitesk.requality.tool.reqif.OLEHelper;
import com.unitesk.requality.tool.reqif.RelationGroup;
import com.unitesk.requality.tool.reqif.ReqIFDefinition;
import com.unitesk.requality.tool.reqif.ReqIFModel;
import com.unitesk.requality.tool.reqif.RequalityReqIfAttributesConverter;
import com.unitesk.requality.tool.reqif.RequalityReqIfStructConverter;
import com.unitesk.requality.tool.reqif.SingleAttributeConverter;
import com.unitesk.requality.tool.reqif.SpecGroup;
import com.unitesk.requality.tool.reqif.SpecHierarchy;
import com.unitesk.requality.tool.reqif.SpecHierarchyContainer;
import com.unitesk.requality.tool.reqif.SpecObject;
import com.unitesk.requality.tool.reqif.SpecObjectType;
import com.unitesk.requality.tool.reqif.SpecRelation;
import com.unitesk.requality.tool.reqif.Specification;
import com.unitesk.requality.tool.reqif.XHTMLAttributeValue;

public class ReqIFConverter {
  protected static class AttributeDef {
    String type, value, key, base;

    public AttributeDef(String ky, String tp, String val, String baseEnum) {
      type = tp;
      value = val;
      key = ky;
      base = baseEnum;
      // TODO Auto-generated constructor stub
    }

    public boolean isNullDef() {
      return this == IGNOREDEF || type == null && value == null && key == null;
    }
  }

  private static AttributeDef IGNOREDEF = new AttributeDef(null, null, null, null);
  public static Map<ReqIFModel, Map<String, Integer>> objectcount =
      new HashMap<>();
  public static Map<String, Set<String>> renames = new HashMap<>();
  private static Map<String, AttributeDef> autoval = new HashMap<>();
  // private static Set<AttributeToReqIF> convs = new
  // HashSet<ReqIFConverter.AttributeToReqIF>();
  private static RequalityReqIfStructConverter structconv;
  private static Set<String> notignore = new HashSet<>();

  private static AttributeType datatypeToReq(
      DatatypeDefinition datatype, TreeNode node, ReqIFModel model) {
    String s = datatype.suffix;
    if (s.equals("STRING")) return AttributeType.STRING;
    if (s.equals("INTEGER")) return AttributeType.INT;
    if (s.equals("XHTML")) return AttributeType.STRING;
    if (s.equals("DATE")) return AttributeType.DATE;
    if (s.equals("BOOLEAN")) return AttributeType.BOOL;
    if (s.equals("ENUMERATION")) {
      // add enum def
      return AttributeType.ENUM;
    }
    if (s.equals("REAL")) return AttributeType.FLOAT;
    return AttributeType.STRING;
  }

  private static String reqToSuffix(TreeNode node, Attribute a, ReqIFModel model) {
    AttributeType at = a.getType(); // TODO list
    switch (at) {
      case BOOL:
        return "BOOLEAN";
      case INT:
        return "INTEGER";
      case STRING:
        return "XHTML";
      case DATE:
        return "DATE";
      case FLOAT:
        return "REAL";
      case ENUM:
        return "ENUMERATION";
      default:
        break;
    }
    return null;
  }

  static {
    renames.put(
        TreeNode.ATTR_DESCRIPTION,
        new HashSet<String>() {
          private static final long serialVersionUID = 1656823358497593250L;

          {
            add("ReqIF.Text");
          }
        });
    // renames.put(virtualAttrChapter, new
    // HashSet<String>(){{add("ReqIF.Name");add("ReqIF.ChapterName");}});
    // renames.put(TreeNode.ATTR_DESCRIPTION, "ReqIF.Descriptiom");
    renames.put(
        TreeNode.ATTR_NAMEORID,
        new HashSet<String>() {
          private static final long serialVersionUID = -1047302638796438319L;

          {
            add("ReqIF.Name");
          }
        });
    autoval.put("Мнемоника", new AttributeDef("Мнемоника", "XHTML", "RTOS", null));
    autoval.put("Производное", new AttributeDef("Производное", "BOOLEAN", "false", null));
    autoval.put(
        "Формат примечания",
        new AttributeDef("Формат примечания", "ENUMERATION", "Не применимо", "Формат примечания"));
    notignore.add("Мнемоника");
    notignore.add("Формат примечания");
    notignore.add("ReqIF.Name");
    notignore.add("ReqIF.ForeignID");
    notignore.add("ReqIF.ChapterName");
    notignore.add("ReqIF.Text");
    notignore.add("Тип объекта");
    notignore.add("Origin");
    notignore.add("Absolute Number");
    List<NodeAttributeConverter> list = new LinkedList<>();
    final SingleAttributeConverter def =
        new SingleAttributeConverter() {
          private DatatypeDefinition detectDatatype(
              TreeNode node, Attribute attr, ReqIFModel model) {
            String suff = reqToSuffix(node, attr, model);
            if (suff == null) return null; // not supported or reference
            if (suff.toLowerCase().startsWith("enum")) {
              AppliedEnumValue aev = attr.getGValue();
              Attribute base =
                  node.getTreeDB().getRootNode().attrs().getAttribute(aev.getBaseKey());
              Set<String> vals = new HashSet<>();
              if (base != null) {
                EnumDefinitionValue edv = base.getGValue();
                for (com.unitesk.requality.core.attribute.EnumValue ev : edv.getValues())
                  vals.add(ev.getValue().toString().toLowerCase()); // TODO
                // other
                // types
              }
              int max = -1;
              DatatypeDefinition best = null;
              for (DatatypeDefinition dd : model.getDatatypes()) {
                if (dd.getSuffix().equals(suff)) {
                  Set<String> cmap = new HashSet<>(vals);
                  List<String> ovals = dd.getValues();
                  int cnt = 0;
                  for (String s : ovals) {
                    cnt += (cmap.remove(s.toLowerCase())) ? 1 : 0;
                  }
                  if (cnt > max) {
                    max = cnt;
                    best = dd;
                  }
                }
              }
              if (max > 0) return best; // TODO test
              return null;
            } else {
              for (DatatypeDefinition dd : model.getDatatypes()) {
                if (dd.getSuffix().equals(suff)) {
                  if (dd.getName() != null && dd.getName().toLowerCase().equals(suff.toLowerCase()))
                    return dd; // TODO
                }
              }
              return null;
            }
          }

          private AttributeDefinition detectAttrType(
              TreeNode node, Attribute attr, SpecObject so, ReqIFModel model) {
            if (attr.getType() == AttributeType.REFERENCE
                || attr.getListType() != null && attr.getListType() == AttributeType.REFERENCE) {
              // TODO ref
              return null;
            }
            DatatypeDefinition dd = detectDatatype(node, attr, model);
            if (dd == null) return null; // cannot convert
            Map<String, Pair<String, String>> attrs = new HashMap<>();
            attrs.put("DESC", new Pair<String, String>(null, "Requality attribute"));
            attrs.put(
                ReqIFDefinition.ID_PARAM,
                new Pair<String, String>(null, "_" + UUID.randomUUID())); // skip?
            attrs.put(ReqIFDefinition.NAME_PARAM, new Pair<String, String>(null, attr.getKey()));
            AttributeDefinition ad = so.getAttributeDefinitionByName(attr.getKey());
            if (ad != null) return ad;
            else return null;
          }

          @Override
          public boolean requality2Doors(
              TreeNode node, Attribute attr, SpecObject so, ReqIFModel model) {
            if (attr == null
                || attr.getKey() == null
                || (attr.getKey().equals("ignorename")
                    || attr.getKey().equals("Тип")
                    || attr.getKey().equals("Метод проверки"))) return false;
            String key = attr.getKey();
            AbstractAttributeValue aav = so.getAttributeValueByName(key);
            AttributeDefinition ad = null;
            if (aav == null) {
              // AttributeDefinition ad =
              // model.getAttributeDefinitionByName(key);//cannot be,
              // other types can have distinct values but...
              ad = detectAttrType(node, attr, so, model);
              if (ad == null) return false;
            } else ad = aav.definition;
            {
              // if(aav!=null){
              // attribute exists, adopt value
              String suff = ad.datatype.suffix;
              AbstractAttributeValue naav = null;
              if (suff.toUpperCase().equals("ENUMERATION")) {
                if (!attr.getType().equals(AttributeType.ENUM)) // not
                  // enum!
                  throw new RuntimeException(
                      "enumeration in doors is not an enum in requality! "
                          + attr.getKey()
                          + " on "
                          + node.getQualifiedId());
                AppliedEnumValue aev = attr.getGValue();
                if (aev != null) {
                  String v = aev.getValue().toString(); // TODO other
                  // enums
                  AbstractAttributeValue best = null;
                  for (ReqIFDefinition eaav : ad.datatype.valuesById.values()) {
                    EnumValue ev = (EnumValue) eaav;
                    if (eaav.getName().toLowerCase().equals(v.toLowerCase())) {
                      best = new EnumAttributeValue(ad, new EnumValueRef(ev));
                    }
                  }
                  if (best == null) {
                    RLogger.logInfo(
                        "WARNING! VALUE was not found! "
                            + attr.getValue().toString()
                            + " for "
                            + node.getQualifiedId()
                            + " and key "
                            + attr.getKey());
                    if (!ad.datatype.valuesById.values().isEmpty())
                      best =
                          new EnumAttributeValue(
                              ad,
                              new EnumValueRef(
                                  (EnumValue) ad.datatype.valuesById.values().iterator().next()));
                  }
                  naav = best;
                }
                // check and add enum
              } else {
                // check and add value
                if (!datatypeToReq(ad.datatype, node, model).equals(attr.getType()))
                  throw new RuntimeException(
                      "attribute type is not as expected! "
                          + attr.getKey()
                          + " on "
                          + node.getQualifiedId());
                if (attr.getType().equals(AttributeType.STRING)) {
                  String val = attr.getValue().toString();
                  val =
                      (CoreUtils.htmlEncapsulate(
                          OLEHelper.convertTablesAndPics(
                              node,
                              CoreUtils.reworkLibreDesc(
                                  node,
                                  val.trim(),
                                  true,
                                  null,
                                  model.getFidMapping(node.getTreeDB())),
                              model.getPath(),
                              model.getFidMapping(node.getTreeDB())),true));
                  // convertTextToXhtml(node, vl, val,
                  // model.getPath());
                  // TODO to xhtml
                  // val = OLEHelper.convertTablesAndPics(node, val,
                  // model.getPath(), doorsId);
                  naav = new XHTMLAttributeValue(ad, val);
                } else
                  naav =
                      new DefaultAttributeValue(
                          ad, ad.datatype.suffix, attr.getValue().toString());
              }
              if (naav == null)
                throw new RuntimeException(
                    "cannot convert " + attr.getKey() + " on " + node.getQualifiedId());
              AbstractAttributeValue xav = so.getAttributeValueByName(ad.getName());
              String val = naav.getString();
              if (val!=null && !val.isEmpty() && naav instanceof XHTMLAttributeValue) {
                val = reworkXHTML(model, naav, val);
              }
              try {
                // TODO fix compare
                if (xav == null
                    || !isSemiEqual(model.getOldPath(), model.getPath(), xav.getString(), val)) {
                  so.setValue(ad.getId(), naav);
                  markChanged(so);
                } else {
                  if (xav.getString() != null && xav.getString().contains("object")) {
                    xav.setString(doorsOLECrutch(xav.getString()));
                    moveOldOle(model.getOldPath(), model.getPath(), xav.getString());
                  }
                }
              } catch (IOException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              }
            }
            // TODO for xhtml - look for embedded
            return true;
          }

          // TODO check on new
          private String doorsOLECrutch(String string) {
            // int ind = string.indexOf("<br/> <object");
            // while(ind>-1)
            // {
            // string = string.replace("<br/> <object", "<object");
            // }
            return string;
          }

          private void moveOldOle(String oldPath, String path, String s) throws IOException {
            s = s.replace("reqif-xhtml", "");
            int pos = s.indexOf("<object");
            while (pos > -1) {
              int pose = s.indexOf(">", pos + 1);
              String repl = "";
              if (s.indexOf(pos + 1) == '/') {
                // ignore
              } else {
                String inner = s.substring(pos, pose - 1);
                // if(inner.startsWith("object")){
                int d = s.indexOf("data");
                if (d > -1) {
                  int pos1 = s.indexOf("'", d + 1), pos2 = s.indexOf("\"", d + 1);
                  if (pos1 == -1) pos1 = Integer.MAX_VALUE;
                  if (pos2 == -1) pos2 = Integer.MAX_VALUE;
                  int posq = Math.min(pos1, pos2);
                  char q = s.charAt(posq);
                  String opath = s.substring(posq + 1, s.indexOf(q, posq + 1)); // TODO
                  // check
                  RequalityCLI.copyfile(
                      java.net.URI.create(oldPath + "/" + opath), URI.create(path + "/" + opath));
                }
                // }
              }
              pos = s.indexOf("<", pose + 1);
            }
          }

          private String reworkXHTML(ReqIFModel model, AbstractAttributeValue naav, String val) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            try {
              dbf.setNamespaceAware(true);
              builder = dbf.newDocumentBuilder();
              DOMImplementation DOMImplementation = builder.getDOMImplementation();
              Document doc =
                  DOMImplementation.createDocument(
                      null, // namespace
                      "REQ-IF",
                      null /* DocumentType */);
              // Document doc = (Document) builder.newDocument();
              doc.setXmlStandalone(true);
              // Element root = ReqIFDefinition.createElement(,"REQ-IF");
              // doc.appendChild(root);
              Element root = doc.getDocumentElement();
              XHTMLAttributeValue.textToXHTML(model, val, root);
              CharArrayWriter caw = new CharArrayWriter();
              TransformerFactory tf = TransformerFactory.newInstance();
              try {
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "true");
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                transformer.transform(new DOMSource(doc, "UTF-8"), new StreamResult(caw));
              } catch (TransformerException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              }
              String res = caw.toString();
              if (caw.toString().length() > 112) res = res.substring(112);
              else return val;
              if (res.length() > 27) res = res.substring(0, res.length() - 27);
              else return val;
              return res;
            } catch (ParserConfigurationException e) {
              RLogger.logError(e);
            }
            return null;
          }

          private void makeNormFile(String s, String path, BufferedWriter os) throws IOException {
            // s = removeTag("object",s);
            // s = removeTag("/object",s);
            // s = removeTag("br",s);
            // s = removeTag("/br",s);
            s = s.replace("reqif-xhtml:", "");
            s = s.replace("\n", " ");
            s = s.replace("\r", " ");
            while (s.indexOf("  ") > -1) s = s.replace("  ", " ");
            s = s.trim();
            int pos = s.indexOf("<"), work = 0;
            ;
            while (pos > -1) {
              int pose = s.indexOf(">", pos + 1);
              String repl = "";
              os.write(CoreUtils.removeTag("", s.substring(work, pos)).trim());
              if (s.indexOf(pos + 1) == '/') {
                // ignore
              } else {
                String inner = s.substring(pos + 1, pose);
                if (inner.startsWith("object")) {
                  int d = inner.indexOf("data");
                  if (d > -1) {
                    int pos1 = inner.indexOf("'", d + 1), pos2 = inner.indexOf("\"", d + 1);
                    if (pos1 == -1) pos1 = Integer.MAX_VALUE;
                    if (pos2 == -1) pos2 = Integer.MAX_VALUE;
                    int posq = Math.min(pos1, pos2);
                    char q = inner.charAt(posq);
                    String opath = inner.substring(posq + 1, inner.indexOf(q, posq + 1));
                    File tempo = File.createTempFile("ole_" + UUID.randomUUID(), "");
                    tempo.deleteOnExit();
                    RTFHandler.extractWMF(
                        new File(URI.create(path + "/" + opath)), tempo.getAbsolutePath());
                    FileReader fr = new FileReader(tempo);
                    while (fr.ready()) {
                      char[] b = new char[100];
                      int len = fr.read(b);
                      os.write(b, 0, len);
                    }
                    fr.close();
                  }
                }
                os.write(repl);
              }
              work = pose + 1;
              pos = s.indexOf("<", work);
            }
            os.write(CoreUtils.removeTag("", s.substring(work, s.length())).trim());
          }

          private boolean isSemiEqual(String path1, String path2, String string, String string2)
              throws IOException {
            if (string == null) return string2 == null || string2.isEmpty();
            if (string.isEmpty()) return string2 == null || string2.isEmpty() ;
            if (string.contains("object") || string2.contains("object")) {
              File f1 = File.createTempFile("test1" + UUID.randomUUID(), ""),
                  f2 = File.createTempFile("test2" + UUID.randomUUID(), "");
              f1.deleteOnExit();
              BufferedWriter bw1 = new BufferedWriter(new FileWriter(f1)),
                  bw2 = new BufferedWriter(new FileWriter(f2));
              f2.deleteOnExit();

              while (OLEHelper.getAdditionalThreadsCount() > 0) ;
              makeNormFile(string, path1, bw1);
              makeNormFile(string2, path2, bw2);
              bw1.close();
              bw2.close();
              return false; // todo fix
              // return fileEquals(f1,f2);
            }
            return normString(string).equals(normString(string2));
          }

          private String normString(String string) {
            string = CoreUtils.removeTag("", string.replace("\n", " ").replace("\r", " "));
            while (string.indexOf("  ") > -1) string = string.replace("  ", " ");
            return string.trim();
          }

          private boolean fileEquals(File f1, File f2) throws IOException {
            FileReader fr1, fr2;
            fr1 = new FileReader(f1);
            fr2 = new FileReader(f2);
            char[] buf1 = new char[400], buf2 = new char[400];
            while (fr1.ready() && fr2.ready()) {
              if (fr1.ready() ^ fr2.ready()) {
                fr1.close();
                fr2.close();
                return false;
              }
              fr1.read(buf1);
              fr2.read(buf2);
              for (int i = 0; i < buf1.length; i++)
                if (buf1[i] != buf2[i]) {
                  fr1.close();
                  fr2.close();
                  return false;
                }
            }
            fr1.close();
            fr2.close();
            return true;
          }

          @Override
          public boolean doors2Requality(
              SpecObject so, AbstractAttributeValue val, TreeNode node,
              Map<String, String> attrrenames, ReqIFModel model) {
            // AttributeValues av = so.values;
            // for(String id:av.valuesByAttributeId.keySet())
            {
              // TODO compare
              AbstractAttributeValue av2 = val;
              String key = av2.definition.getName();
              String renkey = getRename(av2.definition.getNameSpacePrefix()+".",av2.definition.getNameSpacePrefix()+"."+key, attrrenames);
              Object oldval = node.attrs().getAttributeValue(renkey);
              String ov = "";
              if(oldval instanceof String) 
                ov = oldval+"\n";
              AttributeType at = datatypeToReq(av2.definition.datatype, node, model);
              if (av2 instanceof XHTMLAttributeValue) {
                // TODO from xhtml
                String nval = av2.getString();
                nval = OLEHelper.revertConvertTablesAndPics(nval, model.getPath(), node);
                nval = ov+nval;
                if(!nval.equals("null"))
                  node.attrs().setAttribute(new com.unitesk.requality.core.attribute.AttributeDefinition(node.getUUId(),
                    renkey, com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider(
                        renkey,at, com.unitesk.requality.core.attribute.AttributeDefinition.detectFlags(renkey),nval,
                        TempFolderFilesProvider.getOne(at, null, false))));
                
              } else if (av2 instanceof EnumAttributeValue) {
                TreeNode root = node.getTreeDB().getRootNode();
                Attribute adef = root.attrs().getAttribute(av2.definition.datatype.getName()); // ??
                EnumDefinitionValue edv = null;
                if (adef != null && adef.getType() == AttributeType.ENUM_DEFINITION)
                  edv = adef.getGValue();
                else
                  edv =
                      new EnumDefinitionValue(
                          AttributeType.STRING,
                          new ArrayList<com.unitesk.requality.core.attribute.EnumValue>());
                List<com.unitesk.requality.core.attribute.EnumValue> l = edv.getValues();
                Set<String> hs = new HashSet<>(av2.definition.datatype.valuesByName.keySet());
                hs.add(""); // empty for def
                for (String s : hs) {
                  boolean skip = false;
                  for (com.unitesk.requality.core.attribute.EnumValue o : l)
                    if (o.getValue().toString().equals(s)) {
                      skip = true;
                      break;
                    }
                  if (skip) break;
                  l.add(new com.unitesk.requality.core.attribute.EnumValue(s, null));
                }
                //TODO proper rename here
                root.attrs()
                    .setAttribute(
                        new com.unitesk.requality.core.attribute.AttributeDefinition(
                            root.getUUId(),av2.definition.datatype.getName(),
                            com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider(
                                av2.definition.datatype.getName(), AttributeType.ENUM_DEFINITION, 
                                com.unitesk.requality.core.attribute.AttributeDefinition.detectFlags(av2.definition.datatype.getName()),
                                new EnumDefinitionValue(AttributeType.STRING, l),
                                TempFolderFilesProvider.EMPTY)));
//                        av2.definition.datatype.getName(),
//                        new EnumDefinitionValue(AttributeType.STRING, l));
                root.commitAttributes();
                node.attrs()
                    .setAttribute(
                        new com.unitesk.requality.core.attribute.AttributeDefinition(
                            node.getUUId(),renkey, 
                            com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider(renkey, 
                                AttributeType.ENUM, com.unitesk.requality.core.attribute.AttributeDefinition.detectFlags(renkey),
                                new AppliedEnumValue(
                                    av2.getString(),
                                    null,
                                    AttributeType.STRING,
                                    av2.definition.datatype.getName()),
                                TempFolderFilesProvider.EMPTY)
                        ));
                return true;
                // TODO enum
              } else {
                String nval = ov+av2.getString();
                if(!nval.equals("null"))
                  node.attrs().setAttribute(new 
                    com.unitesk.requality.core.attribute.AttributeDefinition(node.getUUId(), 
                       renkey, com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider(
                           renkey, at, 
                            com.unitesk.requality.core.attribute.AttributeDefinition.detectFlags(renkey), nval,
                            TempFolderFilesProvider.getOne(at, null, false)))); // TODO
                // not
                // string?
              }
              // av2.definition.datatype;
            }
            return true;
          }
          // private String reqToDatatype(TreeNode node,SpecObject
          // so,ReqIFModel model,Attribute a){
          // for(AttributeDefinition
          // ad:so.definition.getAttributeDefinitions()){
          //
          // }
          // }

          private String getRename(String ns, String key, Map<String, String> attrrenames) {
            String ren = attrrenames.get(key);
            if(ren.equals("_nameorid"))
              ren = "_name";
            if(ren!=null && ren.startsWith(ns))
              ren = ren.substring(ns.length(),ren.length());
            if(ren == null || ren.startsWith("~")){
              if(key!=null && key.startsWith(ns))
                key = key.substring(ns.length(),key.length());
              return key;
            }
            return ren;
          }


        };
    list.add(
        new NodeAttributeConverter() {
          // TODO divide to doors and reqif
          @Override
          public boolean requality2Doors(TreeNode node, SpecObject so, ReqIFModel model) {
            boolean res = false;
            Attribute toconv     = null;
            AppliedEnumValue ev = null;
            boolean header = false;
            if (node.attrs().containsKey(TreeNode.ATTR_TYPE)) {
              if (node.attrs()
                  .getAttributeValue(TreeNode.ATTR_TYPE)
                  .equals(Requirement.TYPE_NAME)) {
                // Map<String,Pair<String,String>> params = new
                // HashMap<String,Pair<String,String>>();
                if (node.getActiveWorkChildren().size() == 0)
                  ev = new AppliedEnumValue("Требование", null, AttributeType.STRING, "Вид");
                else {
                  header = true;
                  ev = new AppliedEnumValue("Заголовок", null, AttributeType.STRING, "Вид");
                }
                // params.put(ReqIFDefinition.NAME_PARAM,)
                // so.definition.addAttributeDefinition(new
                // AttributeDefinition(locateOrCreateType(AttributeType.STRING,
                // model), null, "STRING"));
              }
            }
            if (node.attrs().containsKey("Вид")) {
              String val = node.attrs().getStringAttributeValue("Вид");
              if (val != null) {
                // AppliedEnumValue ev;
                if (val.equals("Описание"))
                  ev = new AppliedEnumValue("Описание", null, AttributeType.STRING, "Вид");
                if (val.equals("Примечание"))
                  ev = new AppliedEnumValue("Примечание", null, AttributeType.STRING, "Вид");
                if (val.equals("Обоснование"))
                  ev = new AppliedEnumValue("Обоснование", null, AttributeType.STRING, "Вид");
              }
            }
            if (ev != null) {
              toconv = new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                  node.getUUId(), "Тип объекта", new ConstValueProvider(AttributeType.ENUM, ev)), node.attrs());
              res = def.requality2Doors(node, toconv, so, model) || res;
            }
            // if(node.attrs().containsKey(TreeNode.ATTR_NAMEORID)){
            // TreeNode[]chi = node.getWorkChildren();
            // for(TreeNode nde:node.getWorkChildren())
            // {
            // String tp = nde.getType();
            // if(tp.equals(Requirement.TYPE_NAME) ||
            // tp.equals(TextNode.TYPE_NAME)){

            // toconv = new Attribute(node.getUUId(),
            // "ReqIF.Name", node.getName());
            // res = def.requality2Doors(node, toconv, so, model)||res;
            if (header) {
              toconv =
                  new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                      node.getUUId(), "ReqIF.ChapterName", 
                      com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider(
                          TreeNode.ATTR_NAME,
                          AttributeType.STRING, node.getNameOrId(),
                          TempFolderFilesProvider.EMPTY),
                      com.unitesk.requality.core.attribute.Flag.HIDDEN|
                      com.unitesk.requality.core.attribute.Flag.VIRTUAL), node.attrs());
//                  new Attribute(
//                      node.getUUId(), "ReqIF.ChapterName", node.getNameOrId());
              res = def.requality2Doors(node, toconv, so, model) || res;
              // toconv = new Attribute(Version.getUnset(),
              // node.getUUId(), "ReqIF.Name", node.getNameOrId());
              // res = def.requality2Doors(node, toconv, so, model)||res;

            } else {
              // remove for non header
              toconv =
                  new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                      node.getUUId(), "ReqIF.ChapterName",
                      com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider(
                          TreeNode.ATTR_NAME, AttributeType.STRING, "",
                          TempFolderFilesProvider.EMPTY),
                      com.unitesk.requality.core.attribute.Flag.HIDDEN|
                      com.unitesk.requality.core.attribute.Flag.VIRTUAL), node.attrs());
              //toconv = new Attribute(node.getUUId(), "ReqIF.ChapterName", "");
              res = def.requality2Doors(node, toconv, so, model) || res;
              //
              // Disables for now names for non headers
              // toconv = new Attribute(Version.getUnset(),
              // node.getUUId(), "ReqIF.ChapterName",
              // node.getName()!=null?node.getName():"");
              // res = def.requality2Doors(node, toconv, so, model)||res;
            }
            // }
            toconv =
                new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                    node.getUUId(), "Origin", new ConstValueProvider(AttributeType.STRING, 
                        "u:"
                            + node.getUserVisibleName(false)
                            + "|i:"
                            + node.getUUId()
                            + "|q:"
                            + node.getQualifiedId()),0), node.attrs());
            //toconv =
//                
//                new Attribute(
//                    Version.getUnset(),
//                    node.getUUId(),
//                    "Origin",
//                    "u:"
//                        + node.getUserVisibleName(false)
//                        + "|i:"
//                        + node.getUUId()
//                        + "|q:"
//                        + node.getQualifiedId());
            res = def.requality2Doors(node, toconv, so, model) || res;
            String desc = node.getDescription(true, true, false);
            if (desc != null) {
              desc = desc.toString().replace("\n", "<br/>");
              // if(node.getType().equals(TextNode.TYPE_NAME)){
              // Attribute tp = node.attrs().getAttribute("Вид");
              // if(tp!=null && tp.getValue()!=null)
              // desc = tp.getValue()+".<br/>"+desc;
              // }
              toconv =
                  new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                      node.getUUId(), "ReqIF.Text",
                      com.unitesk.requality.core.attribute.AttributeDefinition.detectValueProvider("ReqIF.Text",
                          AttributeType.HTML, desc, TempFolderFilesProvider.getOne(null, false)),
                      com.unitesk.requality.core.attribute.Flag.HIDDEN), node.attrs());
//              toconv =
//                  
//                  new Attribute(node.getUUId(), "ReqIF.Text", desc);
              res = def.requality2Doors(node, toconv, so, model) || res;
              // toconv = new
              // Attribute(Version.getUnset(),node.getUUId(),"ReqIF.Description",desc);
              // res = def.requality2Doors(node, toconv, so, model)||res;
            }
            // if(attr.getKey().toLowerCase().equals("ignorename")||
            // attr.getKey().equals("Тип")|| attr.getKey().equals("Метод
            // проверки"))
            // return IGNOREDEF;
            String id = node.attrs().getStringAttributeValue(FIDUpdater.getFIDName(node.getTreeDB()));
            if (id == null || id.isEmpty() || id.equals("0"))
              id = so.getValueByName("ReqIF.ForeignID");
            if (id == null || id.isEmpty() || id.equals("0"))
              id = so.getValueByName("Absolute Number");
            if (id == null || id.isEmpty()) id = "0";

            if (node.attrs().getAttribute("Производное") == null) {
              toconv =
                  new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                      node.getUUId(), "Производное", new ConstValueProvider(AttributeType.ENUM, 
                          new AppliedEnumValue(" ", null, AttributeType.STRING, "Производное")),
                       0), node.attrs());
//              toconv =
//                  new Attribute(
//                      Version.getUnset(),
//                      node.getUUId(),
//                      "Производное",
//                      new AppliedEnumValue(" ", null, AttributeType.STRING, "Производное"));
            }
            res =
                def.requality2Doors(node, node.attrs().getAttribute("Производное"), so, model)
                    || res;
            toconv =
                new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                    node.getUUId(), "ReqIF.ForeignID", new ConstValueProvider(AttributeType.INT, 
                        Integer.parseInt(id)),
                     0), node.attrs());
//            toconv =
//                new Attribute(
//                    node.getUUId(), "ReqIF.ForeignID", Integer.parseInt(id));
            res = def.requality2Doors(node, toconv, so, model) || res;
            toconv =
                new Attribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                    node.getUUId(), "Absolute Number", new ConstValueProvider(AttributeType.INT, 
                        Integer.parseInt(id)),
                     0), node.attrs());
//            toconv =
//                new Attribute(
//                    node.getUUId(), "Absolute Number", Integer.parseInt(id));
            res = def.requality2Doors(node, toconv, so, model) || res;

            return res;
          }

          private Set<String> ignoreTag =
              new HashSet<String>() {
                private static final long serialVersionUID = -8320556086887019403L;

                {
                  add("Тип объекта");
                  add("ReqIF.Name");
                  add("ReqIF.ChapterName");
                  add("ReqIF.Text");
                  add("TableBottomBorder");
                  add("ReqIF.ForeignCreatedOn");
                  add("ReqIF.Prefix");
                  add("PictureNum");
                  add("PictureName");
                  add("Picture");
                  add("path");
                  add("Paragraph Style");
                  add("Origin");
                  add("OLEIconic");
                  add("OLE");
                  add("ReqIF.Description");
                  add("csint_rcr_oslc_cm:status");
                  add("csint_rcr_oslc:shortTitle");
                  add("csint_rcr_dcterms:title");
                  add("csint_rcr_dcterms:description");
                  add("ReqIF.ForeignCreatedThru");
                  add("ReqIF.ForeignCreatedOn");
                  add("cmint_applied_RCR_numbers");
                  add("ReqIF.ForeignID");
                  add("TableCellAlign");
                  add("TableCellWidth");
                  add("TableChangeBars");
                  add("TableLeftBorder");
                  add("TableLinkIndicators");
                  add("TableRightBorder");
                  add("TableShowAttrs");
                  add("TableShowBookform");
                  add("TableShowWide");
                  add("TableTopBorder");
                  add("TableType");
                }
              };

          @Override
          public boolean doors2Requality(SpecObject so, TreeNode node, Map<String, String> attrrenames, ReqIFModel model) {
            // todo up
            boolean tosave = false;

            for (AbstractAttributeValue s : so.values) {
              if (!ignoreTag.contains(s.definition.getName())){
                if(def.doors2Requality(so, s, node,attrrenames, model))
                  tosave = true;
              }
              // String key = s.definition.getName();
              // if(!ignoreTag.contains(key)){//TODO white list?
              // AttributeType at =
              // datatypeToReq(s.definition.datatype,node,model);
              // node.attrs().setAttribute(at, key,
              // Attribute.convertValue(AttributeType.STRING, at,
              // s.getString()));
              // tosave = true;
              // }

            }
            String val = so.getValueByName("Тип объекта");
            if (val != null) {
              Attribute def = node.getTreeDB().getRootNode().attrs().getAttribute("Вид");
              if (def == null) {
                List<Object> vals = new ArrayList<>();
                vals.addAll(
                    so.getAttributeValueByName("Тип объекта").definition.datatype.getValues());
                List<com.unitesk.requality.core.attribute.EnumValue> ev = new ArrayList<>();
                for(Object o:vals)
                  ev.add(new com.unitesk.requality.core.attribute.EnumValue(o.toString(),null));
                node.getTreeDB()
                    .getRootNode()
                    .attrs()
                    .setAttribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                        node.getUUId(),"Вид",new ConstValueProvider(AttributeType.ENUM_DEFINITION, 
                            new EnumDefinitionValue(AttributeType.STRING, ev))));
                node.getTreeDB().getRootNode().commitAttributes();
              }
              if (val.equals("Требование") || val.equals("Заголовок")) {
                node.setType(Requirement.TYPE_NAME);
                tosave = true;
              }
              if (val.equals("Описание") || val.equals("Примечание") || val.equals("Обоснование")) {
                node.setType(TextNode.TYPE_NAME);
                node.attrs()
                    .setAttribute(new com.unitesk.requality.core.attribute.AttributeDefinition(node.getUUId(), 
                        "Вид", AttributeType.ENUM, new ConstValueProvider(AttributeType.ENUM, 
                            new AppliedEnumValue(val, null, AttributeType.STRING, "Вид"))));
                tosave = true;
              }
            }
            // val = so.getValueByName("ReqIF.Name");
            // if(val!=null){
            // node.attrs().setAttribute(TreeNode.ATTR_NAME, val);
            // tosave = true;
            // }
            val = so.getValueByName("ReqIF.ForeignID");
            if (val != null) {
              node.attrs().setAttribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                  node.getUUId(), FIDUpdater.getFIDName(node.getTreeDB()), new ConstValueProvider(AttributeType.STRING, val)));
              tosave = true;
            }
            for(AbstractAttributeValue s:so.values)
              if(attrrenames.getOrDefault(s.definition.getNameSpacePrefix()+"."+s.definition.getName(), "").equals("_nameorid")){
                val = so.getValueByName(s.definition.getName());
                if (val != null && !val.isEmpty()) {
                  node.setName(val);
                  tosave = true;
                }
              }

            // if(val!=null){
            // node.attrs().setAttribute(TreeNode.ATTR_NAME, val);
            // tosave = true;
            // }
            for(AbstractAttributeValue s:so.values)
              if(attrrenames.getOrDefault(s.definition.getNameSpacePrefix()+"."+s.definition.getName(), "").equals("_description")){
                val = so.getValueByName(s.definition.getName());
                if (val != null && !val.isEmpty()) {
                  com.unitesk.requality.core.attribute.AttributeDefinition ad = node.attrs().getWorkAttributeDefinition("_description");
                  HtmlValueProvider vp = null;
                  if(ad!=null)
                    vp = ((HtmlValueProvider)ad.getValueProvider());
//                  vp.appendValue(value)
  //                String old = ad == null?null:ad.getValue(node.attrs())==null?null:ad.getValue(node.attrs()).toString();
                  String newVal = OLEHelper.revertConvertTablesAndPics(val, model.getPath(), node);
                  if(vp==null)
                    node.setDescription(newVal);
                  else 
                    node.attrs().setAttribute(new com.unitesk.requality.core.attribute.AttributeDefinition(
                        node.getUUId(),TreeNode.ATTR_DESCRIPTION,
                        vp.createAppended(AttributeType.HTML,newVal)));
                  if(node.attrs().getChanged().contains(TreeNode.ATTR_DESCRIPTION)){
                    tosave = true;
                  }
                }
              }
            try {
              if (tosave) node.commitAttributes();
            } catch (Exception e) {
              RLogger.logError(e);
            }
            return true;
          }
        });
    RequalityReqIfAttributesConverter conv = new RequalityReqIfAttributesConverter(list, def);
    structconv = new RequalityReqIfStructConverter(conv);
  }

  public static Set<String> loadReqIFClasses(ReqIFModel model) {
    Set<String> classes = new HashSet<>();
    if (model == null) return null;
    for (SpecObjectType sot : model.getSpecObjectTypes()) {
      classes.add(sot.getName());
    }
    return classes;
  }

  // public static Document loadDoc(IFile f){
  // InputStream is;
  // try {
  //
  // if(!f.getCharset().equals("UTF-8"))
  // {
  // f.setCharset("UTF-8",null);
  // }
  // } catch (CoreException e1) {
  // e1.printStackTrace();
  // }
  // try {
  // is = f.getContents();
  // return loadDoc(is);
  // } catch (CoreException e) {
  // // TODO Auto-generated catch block
  // RLogger.logError(e);
  // }
  // return null;
  // }
  public static Document loadDoc(InputStream is) {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setIgnoringElementContentWhitespace(true);
    DocumentBuilder builder = null;
    try {
      builder = dbf.newDocumentBuilder();
      return builder.parse(is);
    } catch (SAXException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (ParserConfigurationException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return null;
  }

  public static Specification initNewSpecification(ReqIFModel model, String name) {
    return model.createSpecification(name);
  }

  public static Document convertCatalogToDocument(
      Map<Specification, TreeDB> map, ReqIFModel model, String path) {
    // String opath = model.getPath();
    // Set<Specification> it = new HashSet<>(map.keySet());
    // for(Specification s:it){
    // map.put(model.getSpecificationById(s.getId()), map.get(s));
    // map.remove(s);
    // }
    objectcount.put(model, new HashMap<String, Integer>());
    model.setOldPath(model.getPath());
    model.setPath(path);
    // Map<String,Map<String,Integer>>linksState = new
    // HashMap<String,Map<String,Integer>>();
    // for(SpecRelation sr:model.getSpecRelations())
    // {
    // if(sr.source!=null){
    // if(!linksState.containsKey(sr.source.getId()))
    // linksState.put(sr.source.getId(), new HashMap<String,Integer>());
    // if(!linksState.get(sr.source.getId()).containsKey(sr.target.getId()))
    // linksState.get(sr.source.getId()).put(sr.target.getId(),0);
    // linksState.get(sr.source.getId()).put(sr.target.getId(),linksState.get(sr.source.getId()).get(sr.target.getId())+1);
    // }
    // }
    ignored.clear();
    if (model.getSpecifications().isEmpty())
      initNewSpecification(model, map.get(null).getProjectName());
    // Specification spec = model.getSpecifications().iterator().next();
    for (Specification s : map.keySet()) {
      recAdd(ignored, s.getChildren());
      recAdd(ignored, model.getSpecGroup(s.getId()), map.keySet());
    }
    Set<String> dels = new HashSet<>();
    for (SpecObject so : model.getDeleted()) ignored.put(so.getId(), false);
    structconv.convertReqToDoors(map, model, ignored); // TODO all spec
    // TODO filter links
    for (SpecRelation sr : model.getSpecRelations()) {
      incCounter(model, "alllinks");
      // if(sr.source!=null){
      // incCounter(model,"links");
      // if(!linksState.containsKey(sr.source.getId()))
      // linksState.put(sr.source.getId(), new HashMap<String,Integer>());
      // if(!linksState.get(sr.source.getId()).containsKey(sr.target.getId()))
      // linksState.get(sr.source.getId()).put(sr.target.getId(),0);
      // linksState.get(sr.source.getId()).put(sr.target.getId(),linksState.get(sr.source.getId()).get(sr.target.getId())-1);
      // }
    }
    // for(String s:linksState.keySet())
    // for(String s2:linksState.get(s).keySet())
    // if(linksState.get(s).get(s2)!=0){
    // ignored.put(s, true);
    // ignored.put(s2, true);//TODO needed?
    // }
    for (SpecObject so : model.getDeleted())
      // if(!dels.remove(so.getId()))
      ignored.put(so.getId(), true); // new del
    for (SpecObject so : model.getSpecObjects()) {
      incCounter(model, "objects");
      // if(!state.containsKey(so.getId())||!state.get(so.getId()))
      // model.addIgnoreSave(so);
    }
    for (String ign : ignored.keySet()) model.addIgnoreSave(ign);
    for (Specification s : map.keySet()) model.propagateNotIgnore(s); // TODO check
    Document doc = model.saveDoors();
    while (OLEHelper.getAdditionalThreadsCount() > 0) // TODO wait
    ;
    OLEHelper.removeAll();
    return doc;
  }

  private static void recAdd(
      Map<String, Boolean> state2, SpecGroup specGroup, Set<Specification> specs) {
    for (RelationGroup sg : specGroup.groups.values())
      if (specs.contains(sg.targetSpec) && specs.contains(sg.sourceSpec))
        for (SpecRelation sr : sg.relationIds) {
          state2.put(sr.getId(), true);
        }
  }

  private static void recAdd(Map<String, Boolean> state2, SpecHierarchyContainer children) {
    if (children == null) return;
    Iterator<SpecHierarchy> it = children.iterator();
    while (it.hasNext()) {
      SpecHierarchy sh = it.next();
      ignored.put(sh.oref.getId(), true);
      recAdd(state2, sh.children);
    }
  }

  public static Set<String> getCounters(ReqIFModel model) {
    return objectcount.getOrDefault(model, new HashMap<String, Integer>()).keySet();
  }

  public static int getCounterVal(ReqIFModel model, String key) {
    return objectcount.getOrDefault(model, new HashMap<String, Integer>()).getOrDefault(key, 0);
  }

  public static void incCounter(ReqIFModel model, String string) {
    Map<String, Integer> cnts = objectcount.getOrDefault(model, new HashMap<String, Integer>());
    cnts.put(string, cnts.getOrDefault(string, 0) + 1);
    objectcount.put(model, cnts);
  }

  private static Map<String, Boolean> ignored = new HashMap<>();

  private static void markChanged(SpecObject so) {
    ignored.put(so.getId(), true);
    // TODO Auto-generated method stub

  }

  public static Map<String, Set<String>> loadReqIFAttributes(Document model, Set<String> types) {
    Map<String, Set<String>> attrsMap = new HashMap<>();
    Node n = model.getChildNodes().item(0);
    n = model.getFirstChild();
    if (n == null || !n.getNodeName().equals("REQ-IF")) return null;
    n = getFirstChild(n, "CORE-CONTENT");
    n = getFirstChild(n, "REQ-IF-CONTENT");
    n = getFirstChild(n, "SPEC-TYPES");
    for (int j = 0; j < n.getChildNodes().getLength(); j++) {
      Node node = n.getChildNodes().item(j);
      if (node.getNodeName().equals("SPEC-OBJECT-TYPE")) {
        Node nd = node.getAttributes().getNamedItem("LONG-NAME");
        String name = null;
        if (nd != null) name = nd.getNodeValue();
        else name = node.getAttributes().getNamedItem("IDENTIFIER").getNodeValue();
        if (types.contains(name)) {
          attrsMap.put(name, new HashSet<String>());
          attrsMap.get(name).add("Identifier");
          node = getFirstChild(node, "SPEC-ATTRIBUTES");
          if (node != null)
            for (int k = 0; k < node.getChildNodes().getLength(); k++) {
              Node nde = node.getChildNodes().item(k);
              if (nde.getNodeName().startsWith("ATTRIBUTE-DEFINITION-")) {
                String aname = null;
                Node nd2 = nde.getAttributes().getNamedItem("LONG-NAME");
                if (nd2 != null) aname = nd2.getNodeValue();
                else aname = nde.getAttributes().getNamedItem("IDENTIFIER").getNodeValue();
                attrsMap.get(name).add(aname);
              }
            }
        }
      }
    }
    return attrsMap;
  }

  public static Set<TreeDB> convertToCatalog(
      ReqIFModel reqIFModel,
      Map<String, String> mapp1,
      Map<String, String> mapp2,
      Map<String, TreeDB> spectoreq) {
    Set<TreeDB> dbs = new HashSet<>();
    Iterator <Specification> it = reqIFModel.getSpecifications().iterator();
    String name = "nf";
    for(Entry<String, TreeDB> e: spectoreq.entrySet()){
      try{
        TreeDB db = e.getValue();
        name = e.getKey();
        if (db == null) {
          
          int ind = name.indexOf("¦");
          if (ind > -1) name = name.substring(0, ind);
          db = ResManager.getManager().getTracker().createProject(name, name);
        }
        boolean st = db.startTransaction("Load data from reqif to "+name);
        try{
          dbs.add(db);
          structconv.convertDoorsToReq(it.next(), db,mapp2, reqIFModel);
          if(st) {
            db.commit();
            st = false;
          }
        }
        finally{
          if(st)
            db.rollback();
        }
      }catch(Exception ea){
        RLogger.logError("Error while creating or parsing spec "+name);
        RLogger.logError(ea);
      }
    }
    return dbs;
  }

  private static Node getFirstChild(Node node, String type) {
    if (node == null) return null;
    NodeList nl = node.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++)
      if (nl.item(i).getNodeName().equals(type)) return nl.item(i);
    return null;
  }

  public static Map<String, String> loadReqIFSpecifications(ReqIFModel reqIFModel) {
    if (reqIFModel == null) return null;
    Map<String, String> idtoname = new HashMap<>();
    for (Specification s : reqIFModel.getSpecifications()) idtoname.put(s.getId(), s.getName());
    return idtoname;
  }

//  public static int getCount(TreeDB db, String name) {
//    Map<String, Integer> cntrs = objectcount.get(db);
//    if (cntrs == null) return 0;
//    return cntrs.get(name);
//  }

  public static Document loadDoc(String res) {
    Document d = null;
    FileInputStream fr;
    try {
      fr = new FileInputStream(new File(res));
      d = loadDoc(fr);
      fr.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return d;
  }
}
