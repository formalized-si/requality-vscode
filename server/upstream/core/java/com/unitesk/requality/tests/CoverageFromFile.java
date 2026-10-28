package com.unitesk.requality.tests;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class CoverageFromFile implements ICoverageStorageSource {
  String lastErrors = "";
  private CoverageStorage targetStorage;
  HashMap<String, TreeNodeCoverageInfo> coverageInfos;
  Map<String, TestErrorInfo[]> errorInfos;
  private String filename;

  @Override
  public void setTargetStorage(CoverageStorage cs) {
    targetStorage = cs;
  }

  @Override
  public Map<String, TreeNodeCoverageInfo> getCoverage() {
    if (filename == null) return null;
    if (coverageInfos != null || loadCoverageFromFile(filename)) return coverageInfos;
    return null;
  }

  @Override
  public String getLastErrors() {
    return lastErrors;
  }

  @Override
  public void setSettings(String... setts) {
    if (setts.length < 1) return;
    filename = setts[0];
  }

  @Override
  public String getStoredDescription() {
    return "Contains path to file with coverage data"; 
  }
  @Override
  public String[] getSettings(TreeNode node) {
    return new String[] {filename};
  }

  public boolean loadCoverageFromFile(String filename) {
    OperationResult or = loadCoverageFromFileOR(filename);
    this.lastErrors = or.getMessage();
    return or.getSucceedFlag();
  }

  public OperationResult loadCoverageFromFileOR(String filename) {
    String lastErrors = "";
    InputStream is = null;
    IOException baseex = null;
    try {
      if (filename.startsWith("."))
        filename = RequalityCLI.getSemiAbsolutePath(filename, targetStorage.getTreeDB());
      URL url = null;
      try {
        url = new URL(filename);
        is = url.openStream();
      } catch (IOException ex) {
        baseex = ex;
        lastErrors += CoreUtils.htmlEscape(ex.toString()) + "<br>";
        RLogger.logError(CoreUtils.getTrace(ex));
      }
      coverageInfos = new HashMap<>();
      HashMap<String, List<TestErrorInfo>> tempErrorInfos =
          new HashMap<>();
      errorInfos = new HashMap<>();
      if(is == null)
        return new OperationResult(lastErrors, true);
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // VSCODE-PATCH: no DOCTYPE, so no external entity can read a file into the coverage data (XXE)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(is);
      NodeList nodeList = document.getDocumentElement().getChildNodes();
      int errorinc = 0;
      for (int i = 0; i < nodeList.getLength(); i++) {
        Node node = nodeList.item(i);
        if (node instanceof Element) {
          Element el = (Element) node;
          // coverage info
          if (node.getNodeName().toLowerCase().equals("reqcoverage")) {
            TreeNodeCoverageInfo nci;
            String qid = node.getAttributes().getNamedItem("qid").getNodeValue();
            if (targetStorage != null && targetStorage.getTreeDB() != null) {
              TreeNode nde = targetStorage.getTreeDB().getNode(qid, Path.QID | Path.UV | Path.UUID);
              if (nde != null) qid = nde.getQualifiedId();
            }
            if (!coverageInfos.containsKey(qid)) {
              nci = new TreeNodeCoverageInfo();
              nci.setTarget(qid);
            } else nci = coverageInfos.get(qid);
            NodeList childNodes = node.getChildNodes();
            for (int j = 0; j < childNodes.getLength(); j++) {
              Node cNode = childNodes.item(j);
              if (cNode instanceof Element) {
                Element cEl = (Element) cNode;
                String name = cNode.getNodeName();
                if (name.toLowerCase().equals("covered_by")) {
                  String path = cEl.getAttribute("uri");
                  String label = cEl.getAttribute("label");
                  int cnt = -1;
                  if (cEl.hasAttribute("hits")) cnt = Integer.parseInt(cEl.getAttribute("hits"));
                  // if(cEl.hasAttribute("uri"))
                  // local =
                  // Boolean.parseBoolean(cEl.getAttribute("local"));
                  nci.registerTest(path, label, cnt);
                }
              }
            }
            if (el.hasAttribute("hits")) nci.setCntAll(Integer.parseInt(el.getAttribute("hits")));
            coverageInfos.put(nci.getTargetQid(), nci);
          } else if (node.getNodeName().toLowerCase().equals("error")) {
            String name = "error" + errorinc;
            if (((Element) node).hasAttribute("name")) name = ((Element) node).getAttribute("name");
            String path = ((Element) node).getAttribute("testuri"), desc = "", uri = "", label = "";
            if (((Element) node).hasAttribute("uri")) uri = ((Element) node).getAttribute("uri");
            if (((Element) node).hasAttribute("label")) label = ((Element) node).getAttribute("label");
            NodeList childNodes = node.getChildNodes();
            List<String> qids = new ArrayList<>();
            boolean isPlain = true;
            for (int j = 0; j < childNodes.getLength(); j++) {
              Node cNode = childNodes.item(j);
              if (cNode instanceof Element) {
                Element cEl = (Element) cNode;
                String cname = cNode.getNodeName();
                if (cname.toLowerCase().equals("violates")) {
                  qids.add(cEl.getAttribute("qid"));
                } else if (cname.toLowerCase().equals("description")) {
                  if (cEl.hasAttribute("format"))
                    isPlain = cEl.getAttribute("format").toLowerCase().equals("plain");
                  desc = cNode.getTextContent();
                }
              }
            }
            TestErrorInfo tie =
                new TestErrorInfo(name, path, isPlain, desc, qids.toArray(new String[0]), uri);
            if (!tempErrorInfos.containsKey(path))
              tempErrorInfos.put(path, new ArrayList<TestErrorInfo>());
            tempErrorInfos.get(path).add(tie);
          }
        }
      }
      for (String s : tempErrorInfos.keySet())
        errorInfos.put(s, tempErrorInfos.get(s).toArray(new TestErrorInfo[0]));
      this.lastErrors = lastErrors;
      return new OperationResult(lastErrors, true);
    } catch (IOException ie) {
      if (baseex == null) baseex = ie;
      lastErrors += CoreUtils.htmlEscape(baseex.toString()) + "<br>";
    } catch (SAXException se) {
      lastErrors += CoreUtils.htmlEscape(se.toString()) + "<br>";
    } catch (ParserConfigurationException e) {
      lastErrors += CoreUtils.htmlEscape(e.toString()) + "<br>";
    } finally {
      try {
        if (is != null) is.close();
      } catch (IOException e) {
        lastErrors += CoreUtils.htmlEscape(e.toString()) + "<br>";
      }
    }
    this.lastErrors = lastErrors;
    return new OperationResult(lastErrors, false);
  }

  @Override
  public Map<String, TestErrorInfo[]> getErrors() {
    if (filename == null) return null;
    if (coverageInfos != null || loadCoverageFromFile(filename)) return errorInfos;
    return null;
  }

  @Override
  public Object getWizard(TreeNode db) {
    return null;
  }

  @Override
  public Object getSourceObject(TreeDB db, Map<String, Object> node) {
    Object patho = node.get("coverageFilePath");
    if (patho == null) return null;
    try {
      if (filename.startsWith("."))
        filename = RequalityCLI.getSemiAbsolutePath(filename, targetStorage.getTreeDB());
      URL url = null;
      try {
        url = new URL(filename);
        try(InputStream is = url.openStream()){}//TODO remove?
        return new File(url.toURI());
      } catch (Exception ex) {
        RLogger.logError(CoreUtils.getTrace(ex));
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return null;
  }

}
