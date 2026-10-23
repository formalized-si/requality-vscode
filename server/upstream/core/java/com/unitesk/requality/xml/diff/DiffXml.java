package com.unitesk.requality.xml.diff;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

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
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import com.unitesk.requality.core.log.RLogger;

public class DiffXml {

  private static String usage = "Usage: java Main markedFileName targetFileName outputFileName";
  private static String CLASS_NAME = DiffXml.class.getName();
  private static String PKG_NAME = DiffXml.class.getPackage().getName();
  private static Logger log = Logger.getLogger(PKG_NAME);
  private static List<Action> markupActions = null;
  private static List<Node> equalNodes = null;

  public static void main(String[] args)
      throws XPathExpressionException, ParserConfigurationException, SAXException, IOException {
    try {
      FileInputStream ins = new FileInputStream("logging.properties");
      try{
        LogManager.getLogManager().readConfiguration(ins);
        FileHandler fh = new FileHandler("diffxml.log");
        log.addHandler(fh);
        if (args.length == 0) RLogger.logInfo(usage);
        else if (args[0].equals("--test")) new DiffXml().runTests();
        else if (args.length != 3) RLogger.logInfo(usage);
        else merge(args[0], args[1], args[2]);
      }finally{
        ins.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void merge(String markedFileName, String targetFileName, String outputFileName) {
    Object[] args = {markedFileName, targetFileName, outputFileName};
    log.entering(CLASS_NAME, "merge()", args);
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document markedDoc = builder.parse(markedFileName);
      Document originalDoc = builder.parse(targetFileName);
      markupActions =
          DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
      equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
      List<Action> mergeActions =
          ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
      Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
      Document markedDocNew = builder.newDocument();
      markedDocNew.appendChild(markedDocNew.importNode(result, true));
      writeXmlFile(markedDocNew, outputFileName);
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      log.exiting(CLASS_NAME, "merge()");
    }
  }

  public static Document merge(String markedFileName, String targetFileName) {
    Object[] args = {markedFileName, targetFileName};
    log.entering(CLASS_NAME, "merge()", args);
    Document markedDocNew = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document markedDoc = builder.parse(markedFileName);
      Document originalDoc = builder.parse(targetFileName);
      markupActions =
          DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
      equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
      List<Action> mergeActions =
          ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
      Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
      markedDocNew = builder.newDocument();
      markedDocNew.appendChild(markedDocNew.importNode(result, true));
      return markedDocNew;
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
      return null;
    } catch (SAXException e) {
      e.printStackTrace();
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    } finally {
      log.exiting(CLASS_NAME, "merge()", markedDocNew);
    }
  }

  public static void merge(Reader markedFile, Reader targetFile, Writer outputFile) {
    Object[] args = {markedFile, targetFile, outputFile};
    log.entering(CLASS_NAME, "merge()", args);
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document markedDoc = builder.parse(new InputSource(markedFile));
      Document originalDoc = builder.parse(new InputSource(targetFile));
      markupActions =
          DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
      equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
      List<Action> mergeActions =
          ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
      Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
      Document markedDocNew = builder.newDocument();
      markedDocNew.appendChild(markedDocNew.importNode(result, true));
      writeXmlFile(markedDocNew, outputFile);
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (SAXException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      log.exiting(CLASS_NAME, "merge()");
    }
  }

  public static Document merge(Reader markedFile, Reader targetFile) {
    Object[] args = {markedFile, targetFile};
    log.entering(CLASS_NAME, "merge()", args);
    Document markedDocNew = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document markedDoc = builder.parse(new InputSource(markedFile));
      Document originalDoc = builder.parse(new InputSource(targetFile));
      markupActions =
          DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
      equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
      List<Action> mergeActions =
          ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
      Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
      markedDocNew = builder.newDocument();
      markedDocNew.appendChild(markedDocNew.importNode(result, true));
      return markedDocNew;
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
      return null;
    } catch (SAXException e) {
      e.printStackTrace();
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    } finally {
      log.exiting(CLASS_NAME, "merge()", markedDocNew);
    }
  }

  public static List<String> getPersistentLocations() {
    return ActionProcessor.getPersistentReqIdList(equalNodes, markupActions);
  }

  public static List<String> getNonPersistentLocations() {
    return ActionProcessor.getNonPersistentReqIdList(equalNodes, markupActions);
  }

  public static String getLocationDescription(String locationId) {
    return ActionProcessor.getLocationText(locationId, markupActions);
  }

  private void runTests() {
    try {
      sanityTest();
      wikiTest();
      rfcTest();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void sanityTest() throws SAXException, IOException, ParserConfigurationException {
    long start = System.currentTimeMillis();
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document markedDoc = builder.parse("./Test/Sanity_marked.xml");
    Document originalDoc = builder.parse("./Test/Sanity_unmarked.xml");
    List<Action> markupActions =
        DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
    List<Node> equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
    List<Action> mergeActions =
        ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
    Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
    List<String> persistentIds = ActionProcessor.getPersistentReqIdList(equalNodes, markupActions);
    List<String> nonPersistentIds =
        ActionProcessor.getNonPersistentReqIdList(equalNodes, markupActions);
    assert persistentIds.size() == nonPersistentIds.size() : "Sanity failed";
    Document markedDocNew = builder.newDocument();
    markedDocNew.appendChild(markedDocNew.importNode(result, true));
    writeXmlFile(markedDocNew, "./Output/Sanity.xhtml");
    long finish = System.currentTimeMillis();
    RLogger.logDebug("Sanity test done in " + (finish - start) / 1000 + " secs.");
  }

  private void wikiTest() throws SAXException, IOException, ParserConfigurationException {
    long start = System.currentTimeMillis();
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document markedDoc = builder.parse("./Test/Anchoring_old_marked.xhtml");
    Document originalDoc = builder.parse("./Test/Anchoring_new_unmarked.xhtml");
    List<Action> markupActions =
        DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
    List<Node> equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
    List<Action> mergeActions =
        ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
    Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
    List<String> persistentIds = ActionProcessor.getPersistentReqIdList(equalNodes, markupActions);
    List<String> nonPersistentIds =
        ActionProcessor.getNonPersistentReqIdList(equalNodes, markupActions);
    assert persistentIds.size() == 3
        : "Wiki test failed, persistentIds.size()=" + persistentIds.size();
    assert nonPersistentIds.size() == 0
        : "Wiki test failed, nonPersistentIds.size()=" + nonPersistentIds.size();
    Document markedDocNew = builder.newDocument();
    markedDocNew.appendChild(markedDocNew.importNode(result, true));
    writeXmlFile(markedDocNew, "./Output/Wiki.xhtml");
    long finish = System.currentTimeMillis();
    RLogger.logInfo("Wiki test done in " + (finish - start) / 1000 + " secs.");
  }

  private void rfcTest() throws SAXException, IOException, ParserConfigurationException {
    long start = System.currentTimeMillis();
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document markedDoc = builder.parse("./Test/RFC 2821.xhtml");
    Document originalDoc = builder.parse("./Test/RFC 5321.xhtml");
    List<Action> markupActions =
        DiffX.getActionsList(originalDoc.getDocumentElement(), markedDoc.getDocumentElement());
    List<Node> equalNodes = DiffX.getEqualNodes(markedDoc, originalDoc);
    List<Action> mergeActions =
        ActionProcessor.getRequirementMergeActions(equalNodes, markupActions);
    Node result = ActionProcessor.rebuildTree(originalDoc.getDocumentElement(), mergeActions);
    List<String> persistentIds = ActionProcessor.getPersistentReqIdList(equalNodes, markupActions);
    List<String> nonPersistentIds =
        ActionProcessor.getNonPersistentReqIdList(equalNodes, markupActions);
    assert persistentIds.size() == 2
        : "Rfc test failed, persistentIds.size()=" + persistentIds.size();
    assert nonPersistentIds.size() == 0
        : "Rfc test failed, nonPersistentIds.size()=" + nonPersistentIds.size();
    Document markedDocNew = builder.newDocument();
    markedDocNew.appendChild(markedDocNew.importNode(result, true));
    writeXmlFile(markedDocNew, "./Output/Rfc.xhtml");
    long finish = System.currentTimeMillis();
    RLogger.logDebug("Rfc test done in " + (finish - start) / 1000 + " secs.");
  }

  private static void writeXmlFile(Document doc, String filename) {
    try {
      Source source = new DOMSource(doc);

      File file = new File(filename);
      Result result = new StreamResult(file);

      Transformer xformer = TransformerFactory.newInstance().newTransformer();
      xformer.setOutputProperty(OutputKeys.METHOD, "xml");
      xformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      xformer.transform(source, result);
    } catch (TransformerConfigurationException e) {
    } catch (TransformerException e) {
    }
  }

  private static void writeXmlFile(Document doc, Writer output) {
    try {
      Source source = new DOMSource(doc);

      Result result = new StreamResult(output);

      Transformer xformer = TransformerFactory.newInstance().newTransformer();

      xformer.setOutputProperty(OutputKeys.METHOD, "xml");
      xformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      xformer.transform(source, result);
    } catch (TransformerConfigurationException e) {
    } catch (TransformerException e) {
    }
  }
}
