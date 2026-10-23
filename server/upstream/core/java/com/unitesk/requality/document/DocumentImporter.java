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
package com.unitesk.requality.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.htmlcleaner.BaseToken;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleXmlSerializer;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.XmlSerializer;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.formatversion.IInputStreamProvider;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.dom.IXHTMLNode;
import com.unitesk.requality.tool.dom.XHTMLResourceCollector;

class XMLNNode implements IXHTMLNode {
  private Node cur;

  public XMLNNode(Node root) {
    cur = root;
  }

  @Override
  public String getName() {
    return cur.getNodeName();
  }

  @Override
  public IXHTMLNode[] getChildren() {
    NodeList nl = cur.getChildNodes();
    IXHTMLNode[] result = new XMLNNode[nl.getLength()];
    for (int i = 0; i < nl.getLength(); i++) {
      result[i] = new XMLNNode(nl.item(i));
    }
    return result;
  }

  @Override
  public String[] getAttributeKeys() {
    NamedNodeMap nl = cur.getAttributes();
    String[] result = new String[nl.getLength()];
    for (int i = 0; i < nl.getLength(); i++) {
      result[i] = nl.item(i).getNodeName();
    }
    return result;
  }

  @Override
  public String getAttributeValue(String key) {
    if (cur.getAttributes().getNamedItem(key) == null) {
      return null;
    }
    return cur.getAttributes().getNamedItem(key).getNodeValue();
  }

  @Override
  public void setAttribute(String key, String value) {
    if (cur.getAttributes().getNamedItem(key) == null) {
      return;
    }
    cur.getAttributes().getNamedItem(key).setNodeValue(value);
  }
}

public class DocumentImporter {

  private IDocumentProcessor processor = null;

  public void setProcessor(IDocumentProcessor processor) {
    this.processor = processor;
  }

  public void importDocument(
      Document doc, String fileName, IInputStreamProvider inFile, File htmlFolder, Map<String, Document> docFiles, TagNode tagNode, String name, TreeDB base, TreeNode node, String encoding, IProgressMonitor mon, PrintStream pw)
      throws Exception {
//    Reader reader = new InputStreamReader(new FileInputStream(tagNode), Charset.forName(encoding));

//    if (!outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
    TagNode tn2=null;
    try {
      if(!"UTF-8".equals(encoding))
        setMetaCharset(tagNode, encoding,"UTF-8");
      tn2 = cleanHtml(tagNode, encoding);
    //  reader.close();
      Document bdoc = null;
      if (base != null) 
        bdoc = (Document) doc;
      if (processor != null) {
        processor.process(doc, node, pw, null, mon);
      } else {
//        if(doc!=null){
//          doc.ignoreUpdate = true;
//          doc.setResourceContent(name, 
//            new ByteArrayInputStream(out.toString().getBytes(Charset.forName("UTF-8"))));
//        }
      }
      try{
        //writer.close();
       // IXHTMLNode rootNode = null;
        org.w3c.dom.Document fdoc = null;
        if (bdoc != null) {
          
//          if (bdoc.getDOM() != null) {
//            fdoc = bdoc.getDOM();
//            rootNode = new XMLNNode(fdoc.getFirstChild());
//          }
        }
        //final Document doc = bdoc;
        String srcHtmlFolder = htmlFolder.getAbsolutePath();
        File nodeResourceFolder = null;
        if (doc != null) {
          nodeResourceFolder = new File(doc.getHTMLPath()).getParentFile();
        }
        XHTMLResourceCollector docProc =
            new XHTMLResourceCollector(
                tn2,
                srcHtmlFolder,
                "");
        docProc.process(doc, htmlFolder, docFiles);
        if(doc!=null){
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          getXSS("UTF-8").writeToStream(tn2, baos);
          baos.close();
          ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
          try{
            doc.setResourceContent(doc.getResourceName(), bais, OFlag.NONE);
          }
          finally{
            bais.close();
          }
        }
        if(doc!=null)
          pw.println("Import succeed "+doc.getId());
      }
      finally{
        if(doc!=null)
          doc.ignoreUpdate = false;
      }
      /*
       * NodeEditorInput input = new NodeEditorInput(doc); try {
       * IDE.openEditor
       * (PlatformUI.getWorkbench().getActiveWorkbenchWindow()
       * .getActivePage(), input, ReqMarker.ID); } catch
       * (PartInitException e) { e.printStackTrace(); }
       */
    } catch (Exception e) {
      pw.println(CoreUtils.getTrace(e));
      throw e;
    } 
  }
  private static XmlSerializer getXSS(String string) {
    XmlSerializer s = xss.get("UTF-8");
    if(s == null){
      s = new SimpleXmlSerializer(getCP(string));
      xss.put(string, s);
    }
    return s;
  }

  private static CleanerProperties getCP(String c) {
    CleanerProperties props = cp.get(c);
    if(props==null){
      props = createCleanerProperties();
      props.setCharset(c);
      cp.put(c, props);
    }
    return props;
  }

  private void setMetaCharset(TagNode tn2, String encoding, String encodingTo) {
    try {
      TagNode head = tn2.findElementByName("head", false);
      if(head==null){
        head = new TagNode("head");
        tn2.addChild(head);
      }
      //TagNode head = html[0];
      TagNode[] metas = head.getElementsByName("meta", false);
      TagNode meta = null;
      if (metas != null && metas.length > 0) {
        for(TagNode node:metas)
          if(node.getAttributeByName("content")!=null&&node.getAttributeByName("http-equiv")!=null)
            meta = node;
      }
      if(meta == null){
        meta = new TagNode("meta");
        head.addChild(meta);
      }
      Map<String,String> atrs = meta.getAttributes();
      atrs.put("http-equiv", "Content-Type");
      atrs.put("content", "text/html; charset=" + encodingTo);
      meta.setAttributes(atrs);
      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  private static Map<String, CleanerProperties> cp = new HashMap<>();
  private static Map<String,HtmlCleaner> hcm = new HashMap<>();
  private static Map<String, XmlSerializer> xss = new HashMap<>();
  public static HtmlCleaner getCleaner(String c){
    HtmlCleaner hc = hcm.get(c);
    if(hc == null){

      CleanerProperties props = getCP(c);
      hc = new HtmlCleaner(props);
      hcm.put(c, hc);
    }
    return hc;
  }
  public static TagNode cleanHtml(TagNode intHtml, String charset) throws IOException {
    try {
      HtmlCleaner hc = getCleaner(charset);

      XmlSerializer serializer = getXSS(charset);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      serializer.writeToStream(intHtml,baos, "utf-8");
      baos.close();
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      try{
        TagNode root = hc.clean(bais, "utf-8");
        root.addAttribute("xmlns", "http://www.w3.org/1999/xhtml");
        if (root.hasAttribute("xmlns:xml")) {
          root.removeAttribute("xmlns:xml");
        }
        recRemoveLiLI(root);
        return root;
      }
      finally{
        bais.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private static boolean recRemoveLiLI(TagNode root) {
    if(root.getName().equals("li"))
      if(root.getAllChildren().isEmpty())
        return true;
    if(root.getAllChildren().isEmpty())
      return false;
    Set<TagNode> trem = new HashSet<>();
    for(BaseToken node:root.getAllChildren())
      if(node instanceof TagNode)
        if(recRemoveLiLI((TagNode)node))
          trem.add((TagNode) node);
    for(TagNode node:trem)
      root.removeChild(node);
    return false;
  }

  private static CleanerProperties createCleanerProperties() {
    CleanerProperties properties = new CleanerProperties();
    properties.setAdvancedXmlEscape(true);
    properties.setTranslateSpecialEntities(true);
    properties.setRecognizeUnicodeChars(true);
    properties.setUseEmptyElementTags(false);
    properties.setNamespacesAware(true);
    properties.setUseCdataForScriptAndStyle(true);
    properties.setOmitDeprecatedTags(false);
    properties.setOmitUnknownTags(false);
    properties.setOmitDoctypeDeclaration(true);
    properties.setIgnoreQuestAndExclam(true);
    return properties;
  }
}
