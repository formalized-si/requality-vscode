package com.unitesk.requality.tests;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class CoverageStorage {
  private static final String ATTR_COVERAGE_FILTER = "coverageFilter";
  private TreeDB relatedTreeDB;
  public static String TEST_ID_FORMULA = "path";
  private ICoverageStorageSource source;

  @SuppressWarnings("unused")
  private String lastErrors;

  private Map<String, List<String>> testtoreqs = new HashMap<>();
  public Map<String, TreeNodeCoverageInfo> coverageInfos =
      new HashMap<>();
  public static Map<String, TestErrorInfo[]> errorsInfos = new HashMap<>();

  private String idPrefix = null;
  public static Map<String, String> fids =
	      new HashMap<>();
  
  
  public void addTest(String qid, String testPath, String label, int cnt) {
    qid = getQualifyingId(qid);
    if (!containsInfoAbout(qid)) coverageInfos.put(qid, new TreeNodeCoverageInfo());
    TreeNodeCoverageInfo cinfo = coverageInfos.get(qid);
    cinfo.registerTest(testPath, label, cnt);
  }

  public Object getCoverageSourceObject() {
    return source.getSourceObject(relatedTreeDB, new HashMap<String, Object>());
  }

  public void setInputSource(ICoverageStorageSource s) {
    source = s;
    coverageInfos = null;
    errorsInfos = null;
    testtoreqs = new HashMap<>();
    lastErrors = null;
    classes = new HashMap<>();
    s.setTargetStorage(this);
  }

  public boolean loadStorage() {
    try {
      source.setTargetStorage(this);
      coverageInfos = new HashMap<>();
      Map<String, TreeNodeCoverageInfo> baseCov = source.getCoverage();
      if(baseCov!=null)
        for(Entry<String, TreeNodeCoverageInfo> en:baseCov.entrySet()) {
          String key = getQualifyingId(en.getKey());
          coverageInfos.put(key, en.getValue());
        }
      errorsInfos = source.getErrors();
      if (coverageInfos == null
          || source.getLastErrors() != null && !source.getLastErrors().equals("")) return false;
      for (String s : coverageInfos.keySet()) {
        TreeNodeCoverageInfo ci = getCoverageInfo(s);
        if (ci != null)
          for (String path : ci.getTestPaths()) {
            if (!testtoreqs.containsKey(path)) testtoreqs.put(path, new ArrayList<String>());
            testtoreqs.get(path).add(s);
          }
      }
      return true;
    } catch (Exception e) {
      RLogger.logError(CoreUtils.getTrace(e));
      lastErrors = e.toString();
    }
    return false;
  }

  public boolean containsInfoAbout(String qid) {
    qid = getQualifyingId(qid);
    return coverageInfos.containsKey(qid);
  }

  public TreeNodeCoverageInfo getCoverageInfo(String qid) {
    qid = getQualifyingId(qid);
    return coverageInfos.get(qid);
  }
  public String getYellowAddInfo(TreeNode node)
  {
    int cl = getCoverageClass(node);
    if(cl==1){
      int cnt = 0,all = 0;;
      for(TreeNode n:node.getActiveWorkChildren()){
        if(getCoverageClass(n)>1)
          cnt++;
        if(!n.getType().equals(Comment.TYPE_NAME))
          all++;
      }
      //int all = node.getActiveWorkChildren().size();
      if(all>0)
        return"["+cnt+"/"+all+"]";
    }
    return "";
  }
  
  private String getQualifyingId(String qid) {
    return getQualifyingId(qid, false);
  }
  private String getQualifyingId(String qid, boolean deep) {
    try {
      if(idPrefix==null)
        idPrefix = IDMechanics.getShownIdPrefix(relatedTreeDB);
//      if(qid.startsWith(idPrefix))//its local qid
//        qid = qid.substring(idPrefix.length());
      String alt = deep?null:fids.get(qid);
      if(alt!=null) {
    	alt = getQualifyingId(alt,true);
    	if(alt!=null)
          return alt;
      }
      TreeNode node = relatedTreeDB.getNode(qid, Path.QID | Path.UV | Path.UUID);
      if(node!=null)
        return node.getQualifiedId(true);
    } catch (NodeNotFoundException e) {
    }
    return qid;
  }

  public boolean isCovered(String qid) {
    qid = getQualifyingId(qid);
    return isGenCovered(qid);
  }  
  public boolean isCovered(TreeNode node) {
    if(node == null)
      return false;
    String qid = node.getQualifiedId();
    return isGenCovered(qid);
  }
  
  public boolean isGenCovered(String qid) {
    if (coverageInfos == null) return false;
    TreeNodeCoverageInfo tci = coverageInfos.get(qid);
    return tci != null && tci.getTestCounts().length > 0;
  }

  
  public int getCoverageTestCount(String qid) {
    qid = getQualifyingId(qid);
    TreeNodeCoverageInfo tci = coverageInfos.get(qid);
    if (tci == null) return 0;
    return tci.getTestCounts().length;
  }

  public String[] getCoveragePaths(String qid) {
    qid = getQualifyingId(qid);
    TreeNodeCoverageInfo tci = coverageInfos.get(qid);
    if (tci == null) return new String[0];
    String[] paths = tci.getTestPaths();
    return paths;
  }
  public Map<String, String> getPathsLabels(String qid){
    qid = getQualifyingId(qid);
    TreeNodeCoverageInfo tci = coverageInfos.get(qid);
    if(tci == null)
      return Collections.emptyMap();
    return tci.getTestLabels();
  }

  private Map<TreeNode, Integer> classes = new HashMap<>();

  private void calculateClasses(TreeNode node) {
    if(node == null)
      return;
    List<TreeNode> children = node.getWorkChildren();
    boolean coverable = isCoverable(node, null);
    if (isCovered(node.getQualifiedId())) {
      classes.put(node, coverable ? 2 : 3);
      return;
    }
    int cnt = children.size(), cntyellow = 0, cntred = 0;
    for (TreeNode child : children) {
      boolean childCoverable = isCoverable(child, null);
      int c2 = childCoverable?getCoverageClass(child) : 3;
      cntred += c2 == 0 ? 1 : 0;
      cntyellow += c2 == 1 ? 1 : 0;
      if(c2 == 3)
        cnt--;
    }
    classes.put(
        node, cnt == 0 ? 0 : cntred > 0 ? (cnt - cntred > 0) ? 1 : 0 : cntyellow > 0 ? 1 : 2);
  }
  public static boolean isCoverable(TreeNode node, ReportGenerationContext context) {
    String type = node.getType();
    Boolean selfCoverable = null;
    if(context!=null) {
      String filter = (String) context.reportParameters.get(ATTR_COVERAGE_FILTER);
      if(filter!=null) {
        //TODO more proper customizable filtering 
      }
    }
    if(selfCoverable == null)
      selfCoverable = (type.equals(Requirement.TYPE_NAME) || type.equals(TestPurpose.TYPE_NAME));
    if(!selfCoverable)
      return false;
    List<TreeNode> childs = node.getWorkChildren();
    int cnt = childs.size(); 
    if(cnt == 0)
	  return true;
    for(TreeNode chi: childs)
    {
      if(chi.getType().equals(Comment.TYPE_NAME))
        cnt--;
      else
        if(isCoverable(chi, context))
          return true;//header of one of coverable nodes
    }
    if(cnt == 0)
      return selfCoverable;
    return false;//no coverable childs
  }

  public int getCoverageClass(Object obj) {
    if (!(obj instanceof TreeNode)) return 0;
    TreeNode node = (TreeNode) obj;
    if (!classes.containsKey(node)) calculateClasses(node);
    return classes.get(node);
  }

  public Integer[] getCoverageCounts(String qid) {
    qid = getQualifyingId(qid);
    TreeNodeCoverageInfo tci = coverageInfos.get(qid);
    if (tci == null) return new Integer[0];
    return tci.getTestCounts();
  }

  public OperationResult saveCoverageToFile(String filename) {
    String lastErrors = "";
    try {
      File f = new File(URI.create(filename));
      if(!f.exists())
        try {
          f.mkdirs();
          f.createNewFile();
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          RLogger.logError(CoreUtils.getTrace(e1));
        }
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      DOMImplementation impl = builder.getDOMImplementation();
      Document document = impl.createDocument(null, null, null);
      Element e = document.createElement("coverageInfo");
      document.appendChild(e);
      if (coverageInfos == null) loadStorage();
      if (coverageInfos != null)
        for (String s : coverageInfos.keySet()) {
          Element e2 = document.createElement("reqcoverage");
          e.appendChild(e2);
          TreeNodeCoverageInfo inf = coverageInfos.get(s);
          String[] paths = inf.getTestPaths();
          Integer[] cnts = inf.getTestCounts();
          e2.setAttribute("qid", s);
          if (paths.length == 0) {
            e2.setAttribute("hits", Integer.toString(inf.getCntAll()));
          } else {
            for (int i = 0; i < paths.length; i++) {
              Element k = document.createElement("covered_by");
              e2.appendChild(k);
              k.setAttribute("uri", paths[i]);
              k.setAttribute("hits", Integer.toString(cnts[i]));
              // TODO protocoles
              // if(localstate[i])
              // k.setAttribute("local",
              // Boolean.toString(localstate[i]));
            }
          }
        }
      if (errorsInfos != null)
        for (String s : errorsInfos.keySet()) {
          TestErrorInfo[] infs = errorsInfos.get(s);
          for (TestErrorInfo inf : infs) {
            Element e2 = document.createElement("error");
            e.appendChild(e2);
            String name = inf.getName();
            String desc = inf.getDescription();
            String path = inf.getRawTestPath();
            String[] qids = inf.getRequirementsQIDs();
            String uri = inf.getURI();
            e2.setAttribute("name", name);
            e2.setAttribute("testuri", path);
            if (uri != null && uri != "") e2.setAttribute("uri", uri);
            if (desc != null && !desc.equals("")) {
              Element d = document.createElement("description");
              e2.appendChild(d);
              d.setAttribute("format", inf.isPlainText() ? "plain" : "html");
              d.setTextContent(desc);
            }
            for (String qid : qids) {
              Element k = document.createElement("violates");
              e2.appendChild(k);
              k.setAttribute("uri", qid);
            }
          }
        }

      Source source = new DOMSource(e);
      // Prepare the output file
      Result result = new StreamResult(f.getPath());
      // Write the DOM document to the file
      Transformer xformer = TransformerFactory.newInstance().newTransformer();
      xformer.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
      xformer.setOutputProperty(OutputKeys.METHOD,"xml");
      xformer.transform(source, result);
      this.lastErrors = lastErrors;
      return new OperationResult(lastErrors, true);
    } catch (ParserConfigurationException e) {
      lastErrors += CoreUtils.htmlEscape(e.getMessage()) + "<br>";
    } catch (TransformerConfigurationException e1) {
      lastErrors += CoreUtils.htmlEscape(e1.getMessage()) + "<br>";
    } catch (TransformerFactoryConfigurationError e1) {
      lastErrors += CoreUtils.htmlEscape(e1.getMessage()) + "<br>";
    } catch (TransformerException e1) {
      lastErrors += CoreUtils.htmlEscape(e1.getMessage()) + "<br>";
    } finally {
    }
    this.lastErrors = lastErrors;
    return new OperationResult(lastErrors, false);
  }

  public String getLastErrors() {
    if (source != null) return source.getLastErrors();
    return "";
  }

  public TestErrorInfo[] getErrorsForPath(String path) {
    if (!errorsInfos.containsKey(path)) return null;
    return errorsInfos.get(path);
  }

  public String[] getErrorLinksForNodeAndTest(String qid, String test) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNode(qid);
    if (tei == null) return null;
    String[] links = new String[tei.length];
    int i = 0;
    for (TestErrorInfo t : tei) {
      String uri = t.getURI();
      if (uri == null) uri = "";
      links[i++] = uri;
    }
    return links;
  }

  public String[] getErrorNamesForNode(String qid) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNode(qid);
    if (tei == null) return null;
    String[] names = new String[tei.length];
    int i = 0;
    for (TestErrorInfo t : tei) names[i++] = t.getName();
    return names;
  }

  public String[] getErrorNamesForNodeAndTest(String qid, String test) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNodeAndTest(qid, test);
    if (tei == null) return null;
    String[] names = new String[tei.length];
    int i = 0;
    for (TestErrorInfo t : tei) names[i++] = t.getName();
    return names;
  }

  public boolean hasErrors(String qid) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNode(qid);
    if (tei == null || tei.length == 0) return false;
    return tei.length > 0;
  }

  public boolean hasErrors(String qid, String test) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNodeAndTest(qid, test);
    if (tei == null || tei.length == 0) return false;
    return tei.length > 0;
  }

  public String[] getErrorDescriptionsForNode(String qid) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNode(qid);
    if (tei == null) return null;
    String[] descs = new String[tei.length];
    int i = 0;
    for (TestErrorInfo t : tei) descs[i++] = t.getDescription();
    return descs;
  }

  public String[] getErrorDescriptionsForNodeAndTest(String qid, String test) {
    qid = getQualifyingId(qid);
    TestErrorInfo[] tei = getErrorsByNodeAndTest(qid, test);
    if (tei == null) return null;
    String[] descs = new String[tei.length];
    int i = 0;
    for (TestErrorInfo t : tei) descs[i++] = t.getDescription();
    return descs;
  }

  public TestErrorInfo[] getErrorsByNodeAndTest(String qid, String tst) {
    qid = getQualifyingId(qid);
    TreeNodeCoverageInfo cnci = getCoverageInfo(qid);
    if (cnci == null) return null;
    Set<TestErrorInfo> list = new HashSet<>();
    TestErrorInfo[] infos = getErrorsForPath(tst);
    if (infos != null)
      for (TestErrorInfo tei : infos) {
        String[] reqs = tei.getRequirementsQIDs();
        for (String r : reqs) {
          TreeNode node = relatedTreeDB.getNode(r, Path.QID | Path.UV);
          if (node != null && node.getQualifiedId().equals(qid)) list.add(tei);
        }
      }
    return list.toArray(new TestErrorInfo[0]);
  }

  public TestErrorInfo[] getErrorsByNode(String qid) {
    qid = getQualifyingId(qid);
    TreeNodeCoverageInfo cnci = getCoverageInfo(qid);
    if (cnci == null) return null;
    String[] tests = cnci.getTestPaths();
    Set<TestErrorInfo> list = new HashSet<>();
    for (String path : tests) {
      TestErrorInfo[] infos = getErrorsForPath(path);
      if (infos == null) continue;
      for (TestErrorInfo tei : infos) {
        String[] reqs = tei.getRequirementsQIDs();
        for (String r : reqs) {
          TreeNode node = relatedTreeDB.getNode(r, Path.QID | Path.UV);
          if (node != null && node.getQualifiedId().equals(qid)) list.add(tei);
        }
      }
    }
    return list.toArray(new TestErrorInfo[0]);
  }

  public CoverageStorage(TreeDB where) {
    relatedTreeDB = where;
    if (where == null && defStorage == null) defStorage = this;
    detectFIDs();
  }

  private void detectFIDs() {
	DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(
			relatedTreeDB.getNode(Requirement.getTypeRootQId()), false, true, false);
	Iterator<TreeNode> it = dftw.iterator();
	while(it.hasNext()) {
		TreeNode n = it.next();
		String f = n.getExternalForeignID();
		if(f!=null)
          fids.put(f, n.getQualifiedId(true));
	}
  }

  private static CoverageStorage defStorage;
  //private static Map<TreeDB, CoverageStorage> map = new HashMap<>();
  private static String META_COVERAGE_STORAGE = "COVERAGE_STORAGE";
  public static CoverageStorage getStorage(TreeDB treeDB, ICoverageStorageSource src) {
    if (treeDB == null) {
      if (defStorage == null) defStorage = new CoverageStorage(treeDB);
      return defStorage;
    }
    CoverageStorage cs = treeDB.getMetaObject(META_COVERAGE_STORAGE);
    if(cs == null) {
      synchronized (META_COVERAGE_STORAGE) {
        cs = treeDB.getMetaObject(META_COVERAGE_STORAGE);
        if(cs == null) {
          cs = new CoverageStorage(treeDB);
          treeDB.registerMetaObject(META_COVERAGE_STORAGE, cs);
        }
      }
    }
    cs.setInputSource(src);
    return cs;
  }

  public void clearStorage() {
    classes = new HashMap<>();
    coverageInfos.clear();
    errorsInfos.clear();
  }

  public void setSettings(String[] split) {
    if (source == null) return;
    source.setSettings(split);
  }

  public String[] getSettings(TreeNode node) {
    if (source == null) return null;
    return source.getSettings(node);
  }

  public String[] getQIDsForPath(String uri) {
    if (!testtoreqs.containsKey(uri)) return null;
    return testtoreqs.get(uri).toArray(new String[0]);
  }

  public TreeDB getTreeDB() {
    return relatedTreeDB;
  }

  public ICoverageStorageSource getCoverageSource() {
    return source;
  }

  public boolean isEmpty() {
    return source == null ||  source.getCoverage()==null || source.getCoverage().isEmpty();
  }
}
