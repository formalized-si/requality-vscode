package com.unitesk.requality.report;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.runtime.Platform;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.FileSpaceTimePoint;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.repo.RepoManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.tests.CoverageStorage;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.LocationHelper;

public class StatisticManager {
  private TreeDB project;
  TreeDB[] cvnProjects;
  File[] copyProjects;
  private URI filePath;
  private Requirement req;
  private CoverageStorage cs;
  private CoverageStorage basecs;
  private FileSpaceTimePoint cvsInfo;
  private HashMap<String, String> errorsInfo = null;

  private ArrayList<Long> revisionList = new ArrayList<>();
  private Long dateFrom = Long.MIN_VALUE, dateTo = Long.MAX_VALUE;

  private Integer rq = 0;
  private Integer tp = 0;
  private Integer leaves = 0;
  private Map<String, Integer> covCount = new HashMap<>();
  private List<UUID> calculated = new ArrayList<>();
  
  private ReportGenerationContext rgh;
  
  public StatisticManager(CoverageStorage cs2, TreeDB db, URI path, ReportGenerationContext rgh) {
    // IProject project = EclipseTracker.getTracker().getProject(db);
    this.rgh = rgh;
    fstpPoints.clear();
    this.project = db;
    basecs = cs2;
    this.filePath =
        URI.create(
            new File(path).getParentFile().toURI().toString()
                + ".reqcache/"
                + "statistics_"
                + basecs.getCoverageSource().getClass().getSimpleName()
                + ".xml");
    basecs.loadStorage();
    Object o = basecs.getCoverageSourceObject();
    // TODO in case of TreeNodes - load related projects
    File[] linkedProjects = null;
    try {
      if (o instanceof File
          && !(((File) o)
              .getCanonicalPath()
              .equals(new File(LocationHelper.getLocation(project, false)).getCanonicalPath())))
        linkedProjects = new File[] {(File) o};
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if (o instanceof File[]) {
      List<File> list = new ArrayList<>();
      for (File cn : (File[]) o) {
        try {
          if (!(cn.getCanonicalPath()
              .equals(new File(LocationHelper.getLocation(project, false)).getCanonicalPath())))
            list.add(cn);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
      linkedProjects = list.toArray(new File[0]);
    }
    List<TreeDB> cvn = new ArrayList<>();
    List<File> ncvn = new ArrayList<>();
    if (linkedProjects != null)
      for (File cn : linkedProjects) {
        RepoManager cvsm = Global.getRepoManager(db);
        boolean cvned = false;
        try {
          // Hack
          String p1 = cn.toURI().toString(),
              p2 = Platform.getLocation().toFile().toURI().toString();
          int c = 0;
          while (c < p1.length() && c < p2.length() && p1.charAt(c) == p2.charAt(c)) c++;
          if (c == p2.length()) {
            p1 = p1.substring(c + 1);
            // TODO check it, strange indexof
            TreeDB proj = db.getTracker().getTreeDB(db, p1.substring(0, p1.indexOf("/")),null, true, rgh.monitor);
            if (proj != null) {
              cvsm.getCVSClient();
              cvn.add(proj);
              cvned = true;
            }
          }
        } catch (Exception e) {
        }
        if (!cvned) ncvn.add(cn);
      }
    cvnProjects = cvn.toArray(new TreeDB[0]);
    copyProjects = ncvn.toArray(new File[0]);
  }

  public HashMap<String, String> getErrorMsg() {
    return this.errorsInfo;
  }

  private void gatherErrorMsg(String msg, Exception e) {
    if (msg == null) {
      msg = e.getClass().getSimpleName();
    }
    if (e != null) {
      StringBuilder st = new StringBuilder();
      for (StackTraceElement err : e.getStackTrace()) {
        st.append(err);
        st.append(System.getProperty("line.separator"));
      }
      this.errorsInfo.put(msg, st.toString());
    } else {
      this.errorsInfo.put(msg, "");
    }
  }

  public String getPath() {
    return basecs.getCoverageSource().getClass().getSimpleName();
    // Arrays.toString(basecs.getSettings(rootReq))+"
    // "+basecs.getCoverageSource().getClass().getSimpleName());
  }

  public boolean isDateCorrect(String dateAfter, String dateBefore) {
    SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);
    try {
      if ((dateAfter != null) && (!dateAfter.isEmpty())) {
        this.dateFrom = format.parse(dateAfter).getTime();
      }
      if ((dateBefore != null) && (!dateBefore.isEmpty())) {
        this.dateTo = format.parse(dateBefore).getTime();
      }
      if ((dateAfter != null)
          && (!dateAfter.isEmpty())
          && (dateBefore != null)
          && (!dateBefore.isEmpty())) {
        if (this.dateTo - this.dateFrom <= 0) {
          gatherErrorMsg("Wrong dates. Date 'from' can't be equals or after date 'to'.", null);
          RLogger.logInfo("Wrong dates. Date 'from' can't be equals or after date 'to'.");
          return false;
        }
      }
    } catch (ParseException e) {
      gatherErrorMsg("Wrong date format.", null);
      RLogger.logInfo("Wrong date format.");
      return false;
    }
    return true;
  }

  public void refreshRecordsList() {
    errorsInfo = new HashMap<String, String>();
    try {
      RepoManager cvsm = Global.getRepoManager(this.project);
      Set<FileSpaceTimePoint> ps = new HashSet<>(toList(cvsm.getRevisions(this.project)));
      for (FileSpaceTimePoint pp : ps) {
      if(pp.isBroken())
        continue;
        writeHeaderReport(pp);
      }
      errorsInfo.putAll(cvsm.getErrorsInfo());
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      RLogger.logInfo(e.toString());
    }
  }

  public boolean isInfoExist() {
    this.revisionList = new ArrayList<>();
    try {
      File file = new File(filePath);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(file);
      doc.getDocumentElement().normalize();
      NodeList nlist = doc.getElementsByTagName("statistic");

      for (int s = nlist.getLength() - 1; s >= 0; s--) {
        Node node = nlist.item(s);
        if (node.getNodeType() == Node.ELEMENT_NODE) {
          Element elm = (Element) node;
          Long attTime = Long.parseLong(elm.getAttribute("timeLong"));
          // needn't use milliseconds;
          attTime = attTime - attTime % 1000;
          if ((this.dateFrom <= attTime) && (attTime <= this.dateTo)) {
            if (!node.hasChildNodes() && !elm.hasAttribute("noChild")) {
              revisionList.add(Long.parseLong(elm.getAttribute("timeLong")));
            }
          } else {
            throw new IllegalArgumentException("Not existing revision has been found.");
          }
        }
      }
      return (this.revisionList.isEmpty()); // && speshRevTime == -1l);
    } catch (ParserConfigurationException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (IOException e) {
      gatherErrorMsg("File doesn't exist.", null);
      RLogger.logInfo("File doesn't exist "+filePath);
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
    return false;
  }

  public void getDataRecord() {
    RepoManager cvsm = Global.getRepoManager(project);
    Set<FileSpaceTimePoint> ps = new HashSet<>(toList(cvsm.getRevisions(this.project)));
    List<FileSpaceTimePoint> revList = getRevisions(cvsm, ps);
    for (FileSpaceTimePoint rev : revList) {
      getRevisionInfo(this.project, cvsm, rev);
    }
  }

  public List<FileSpaceTimePoint> getRevisions(RepoManager cvsm, Set<FileSpaceTimePoint> ps) {
    Map<Long, FileSpaceTimePoint> map = new HashMap<>();
    for (FileSpaceTimePoint pp : ps) {
      Long val = pp.getLongDate();
      if (this.revisionList.contains(val)) map.put(val, pp);
    }

    SortedSet<Long> sortedRev = new TreeSet<>();
    for (Long s : map.keySet()) {
      sortedRev.add(s);
    }
    List<FileSpaceTimePoint> l = new LinkedList<>();
    for (Long i : sortedRev) l.add(map.get(i));
    return l;
  }

  public void getRevisionInfo(TreeDB proj, RepoManager cvsm, FileSpaceTimePoint pp) {
    if(pp.isBroken())
      return;
    try {
      TreeDB odb = proj;

//      java.io.File f =
//          cvsm.getCVSClient().getContainerFromRepoS(pp.getPath(), pp.getRevision(), null);
//      CoreUtils.markDeleteOnExit(f);
//      FSStorage fs =
//          new FSStorage(findRoot(f.toPath().resolve("root").toFile(), proj.getResName()));
      //TODO slice
      IProgressMonitor mon = rgh.monitor;
      RepoManager rm = ResManager.getManager().getRepoManager(proj);
      Pair<RevisionInfo, TreeDB> dbp = rm.getRevisionTree(pp.getRevision(), true, false, mon);
      if(dbp == null || dbp.getSecond() == null)
        return;
      RevisionInfo ri = null;
      try {
        TreeDB db = dbp.getSecond();
        ri = db.revision;
        for (NodeType nt : odb.getTypes().values()) {
          db.addNodeType(nt.getTypeClass());
        }
        mon.beginTask("Statistic manager - gather related trees for "+db.getSemiLocalName(), 1);
        try {
          File f = new File(LocationHelper.getLocation(db, false));//TODO thinks
          loadRelatedProjects(cvsm, f.getParentFile().getParentFile().getAbsolutePath(), pp);
        }finally {
          mon.doneTask();
        }
        mon.beginTask("Statistic manager - gather coverage for "+db.getSemiLocalName(), 1);
        try {
          CoverageStorage cs = getNewCoverage(basecs, db);
          try {
            createNewRecord((Requirement) db.getNode(Requirement.getTypeRootQId(), Path.QID), cs, pp);
          } catch (NullPointerException e) {
            writeNoContentReport(pp.getRevision());
          }
        }finally {
          mon.doneTask();
        }
      }finally {
        if(ri!=null)
          rm.forgetRevisionTree(ri);
      }
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
  }

  FilenameFilter ff =
      new FilenameFilter() {
        @Override
        public boolean accept(File arg0, String arg1) {
          return arg1.contains(".project");
        }
      };

  private String findRoot(File f, String proj) {
    if (f == null || f.getParentFile() == null || !f.exists()) return null;
    if (f.getParentFile() != null && f.getParentFile().getName().equals("git")) {
      if (f.list(ff).length != 0) return f.toURI().toString();
      if (f.getName().equals("root")) // proj folder is git fold
      return f.getParentFile().toURI().toString();
    }
    return findRoot(f.getParentFile(), proj);
  }

  public static List<FileSpaceTimePoint> toList(Iterator<FileSpaceTimePoint> iterator){
    Iterable<FileSpaceTimePoint> iterable = () -> iterator; 
    
    return StreamSupport 
                       .stream(iterable.spliterator(), false) 
                       .collect(Collectors.toList());
  }
  
  private Map<TreeDB, FileSpaceTimePoint[]> fstpPoints = new HashMap<>();

  private void loadRelatedProjects(RepoManager cvsm, String absolutePath, FileSpaceTimePoint pp) {
    for (File c : copyProjects) copy(c, absolutePath);

    for (TreeDB c : cvnProjects) {
      if (!fstpPoints.containsKey(c)){
        List<FileSpaceTimePoint> list = toList(cvsm.getRevisions(c));
        fstpPoints.put(c, list.toArray(new FileSpaceTimePoint[0]));
      }
      FileSpaceTimePoint[] points = fstpPoints.get(c);
      int mn = 0, mx = 0;
      for (int i = 0; i < points.length; i++) {
        FileSpaceTimePoint tp = points[i];
        if(tp.isBroken())
          continue;
        if ((points[mx].getDate().before(pp.getDate())
                || tp.getDate().before(points[mx].getDate()))
            && tp.getDate().after(pp.getDate())) mx = i;
        if ((points[mn].getDate().after(pp.getDate())
                || tp.getDate().after(points[mn].getDate()))
            && tp.getDate().before(pp.getDate())) mn = i;
      }
      FileSpaceTimePoint sel = points[mn];
      try {
        if(sel.isBroken())
          return;
        File f = cvsm.getCVSClient().getContainerFromRepoS(sel.getPath(), sel.getRevision(), null);
        if(f!=null){
          copy(
            f,
            absolutePath);
          CoreUtils.markDeleteOnExit(f);
        }
      } catch (Exception e) {
        gatherErrorMsg(e.getLocalizedMessage(), e);
        e.printStackTrace();
      }
    }
  }

  private void copy(File c, String absolutePath) {
    try {
      File f = c;
      List<File> hndl = new ArrayList<>();
      List<String> path = new ArrayList<>();
      int i = 0;
      hndl.add(f);
      path.add(absolutePath);
      while (hndl.size() > i) {
        File t = hndl.get(i);
        String g = path.get(i) + File.separator + t.getName();
        if (t.isDirectory()) {
          (new File(g)).mkdir();
          for (File f1 : t.listFiles()) {
            hndl.add(f1);
            path.add(g);
          }
        } else {
          File cf = (new File(g));
          cf.createNewFile();
          FileReader fr = new FileReader(t);
          FileWriter fw = new FileWriter(cf);
          while (fr.ready()) fw.write(fr.read());
          fw.close();
          fr.close();
        }
        i++;
      }
      // c.getFolder(new Path("./")).copy(new Path(absolutePath), true,
      // null);
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
  }

  private CoverageStorage getNewCoverage(CoverageStorage base, TreeDB db) {
    CoverageStorage cs = CoverageStorage.getStorage(db, base.getCoverageSource());
    TreeNode node = null;
    try {
      node = db.getNode(Requirement.getTypeRootQId(), Path.QID);
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
    if (node != null) {
      cs.getCoverageSource().setSettings(base.getSettings(node));
      cs.loadStorage();
      return cs;
    }
    return base;
  }

  public Map<String, Integer> getArrayData(Requirement rootReq, CoverageStorage cs) {
    createNewRecord(rootReq, cs, null);
    Map<String, Integer> data = new HashMap<>();
    data.put("number of requirements", rq);
    data.put("number of test purposes", tp);
    data.put("number of leaves", leaves);
    data.put("number coverage (green status)", covCount.get("green"));
    data.put("number coverage (red status)", covCount.get("red"));
    float sum = covCount.get("green") + covCount.get("red");
    data.put(
        "percent coverage (green status)", Math.round((float) covCount.get("green") * 100 / sum));
    data.put("percent coverage (red status)", Math.round((float) covCount.get("red") * 100 / sum));
    return data;
  }

  private void createNewRecord(
      Requirement rootReq, CoverageStorage cs, FileSpaceTimePoint cvsInfo) {
    cs.loadStorage();
    req = rootReq;
    this.cs = cs;
    this.cvsInfo = cvsInfo;
    rq = 0;
    tp = 0;
    leaves = 0;
    covCount.put("green", 0);
    covCount.put("red", 0);
    calculated = new ArrayList<>();

    int sc = req.sizeShownChildren();
    if (req.getType().equals(Requirement.TYPE_NAME)) rq += 1;
    for (int i = 0; i < sc; i++) {
      TreeNode node = req.getShownChild(i);
      this.calculateStatistics(node);
    }
    if (cvsInfo != null) {
      writeContentReport();
    }
  }

  private void calculateStatistics(TreeNode sc) {
    if (calculated.contains(sc.getUUId())) return;
    // check node is requirement
    if (sc.getType().equals(Requirement.TYPE_NAME)) rq += 1;
    // check node is test purpose
    if (sc.getType().equals(TestPurpose.TYPE_NAME)) tp += 1;
    if (sc.sizeWorkChildren() == 0) {
      leaves += 1;
      // check if node is directly covered by test
      if (cs.isCovered(sc)) {
        covCount.put("green", covCount.get("green") + 1);
      } else {
        covCount.put("red", covCount.get("red") + 1);
      }
    }
    calculated.add(sc.getUUId());
    for (int i = 0; i < sc.sizeShownChildren(); i++) {
      TreeNode node = sc.getShownChild(i);
      this.calculateStatistics(node);
    }
  }

  DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
  DocumentBuilder dBuilder = null;

  private void writeHeaderReport(FileSpaceTimePoint pp) {
    try {
      File file = new File(filePath);
      if (dBuilder == null) dBuilder = dbFactory.newDocumentBuilder();
      Document doc = null;
      boolean isChanged = true;
      boolean isExist = file.exists();

      if (isExist) {
        try {
          doc = dBuilder.parse(file);
        } catch (SAXException spe) {
          isExist = false;
          file.delete();
        }
        if (doc != null&&pp!=null&&pp.getDate()!=null) {
          doc.getDocumentElement().normalize();
          boolean isInserted = false;
          Long revTime = pp.getDate().getTime();
          NodeList statList = doc.getElementsByTagName("statistic");
          for (int s = 0; s < statList.getLength(); s++) {
            Node statNode = statList.item(s);
            if (statNode.getNodeType() == Node.ELEMENT_NODE) {
              Element elm = (Element) statNode;
              Long attRevTime = Long.parseLong(elm.getAttribute("timeLong"));
              if (revTime.equals(attRevTime)) {
                isInserted = true;
                isChanged = false;
                break;
              }
              if (revTime < attRevTime) {
                Element newChild = createHeaderElement(doc, pp);
                elm.getParentNode().insertBefore(newChild, elm);
                isInserted = true;
                break;
              }
            }
          }
          if (!isInserted) {
            Node stat = doc.getElementsByTagName("statistics").item(0);
            Element newChild = createHeaderElement(doc, pp);
            stat.appendChild(newChild);
          }
        } else isExist = false;
      }
      if (!isExist) {
        doc = dBuilder.newDocument();
        Element rootlvl1 = doc.createElement("statistics");
        doc.appendChild(rootlvl1);
        Element rootlvl2 = createHeaderElement(doc, pp);
        rootlvl1.appendChild(rootlvl2);
        file.getParentFile().mkdirs();
        file.createNewFile();
      }
      if (isChanged) {
        TransformerFactory tfFactory = TransformerFactory.newInstance();
        Transformer transformer = tfFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        DOMSource source = new DOMSource(doc);
        FileOutputStream fos = new FileOutputStream(file, false);
        Result result = new StreamResult(fos);
        try {
          transformer.transform(source, result);
        } finally {
          fos.close();
        }
      }
    } catch (ParserConfigurationException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (TransformerException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (IOException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
  }

  private Element createHeaderElement(Document doc, FileSpaceTimePoint pp) {
    Element newChild = doc.createElement("statistic");
    if (pp != null&&pp.getDate()!=null) {
      newChild.setAttribute("time", pp.getDate().toString());
      newChild.setAttribute("timeLong", Long.toString(pp.getDate().getTime()));
      newChild.setAttribute("author", pp.getAuthor());
      newChild.setAttribute("numRevision", pp.getRevision());
    } else {
      newChild.setAttribute("time", new Date().toString());
      newChild.setAttribute("timeLong", String.valueOf(System.currentTimeMillis()));
      newChild.setAttribute("author", System.getProperty("user.name"));
      newChild.setAttribute("numRevision", "0");
    }
    return newChild;
  }

  private void writeContentReport() {
    try {
      File file = new File(filePath);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc;

      if (file.exists()) {
        doc = dBuilder.parse(file);
        doc.getDocumentElement().normalize();
        String rev = cvsInfo.getRevision();
        NodeList statList = doc.getElementsByTagName("statistic");
        for (int s = 0; s < statList.getLength(); s++) {
          Node statNode = statList.item(s);
          if (statNode.getNodeType() == Node.ELEMENT_NODE) {
            Element elm = (Element) statNode;
            if (rev.equals(elm.getAttribute("numRevision"))) {
              Element node = doc.createElement("reqCount");
              node.setAttribute("name", "number of requirements");
              node.setAttribute("value", rq.toString());
              elm.appendChild(node);

              node = doc.createElement("tpCount");
              node.setAttribute("name", "number of test purposes");
              node.setAttribute("value", tp.toString());
              elm.appendChild(node);

              node = doc.createElement("leaves");
              node.setAttribute("name", "leaves");
              node.setAttribute("value", leaves.toString());
              elm.appendChild(node);

              node = doc.createElement("statuses");
              for (Map.Entry<String, Integer> entry : covCount.entrySet()) {
                Element nodelvl2 = doc.createElement("status");
                nodelvl2.setAttribute("name", entry.getKey());
                nodelvl2.setAttribute("value", entry.getValue().toString());
                node.appendChild(nodelvl2);
              }
              elm.appendChild(node);
            }
          }
        }
        TransformerFactory tfFactory = TransformerFactory.newInstance();
        Transformer transformer = tfFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        DOMSource source = new DOMSource(doc);
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
          StreamResult strRes = new StreamResult(fos);
          transformer.transform(source, strRes);
        } finally {
          fos.close();
        }
      } else {
        gatherErrorMsg("File" + filePath + "doesn't exist!", null);
        RLogger.logInfo("File" + filePath + "doesn't exist!");
      }
    } catch (ParserConfigurationException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (TransformerException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (IOException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
  }

  private void writeNoContentReport(String revNum) {
    try {
      File file = new File(filePath);
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc;

      if (file.exists()) {
        doc = dBuilder.parse(file);
        doc.getDocumentElement().normalize();

        NodeList statList = doc.getElementsByTagName("statistic");
        for (int s = 0; s < statList.getLength(); s++) {
          Node statNode = statList.item(s);
          if (statNode.getNodeType() == Node.ELEMENT_NODE) {
            Element elm = (Element) statNode;
            if (revNum.equals(elm.getAttribute("numRevision"))) {
              elm.setAttribute("noChild", "true");
            }
          }
        }
        TransformerFactory tfFactory = TransformerFactory.newInstance();
        Transformer transformer = tfFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        DOMSource source = new DOMSource(doc);
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
          StreamResult strRes = new StreamResult(fos);
          transformer.transform(source, strRes);
        } finally {
          fos.close();
        }
      } else {
        gatherErrorMsg("File" + filePath + "doesn't exist!", null);
        RLogger.logInfo("File" + filePath + "doesn't exist!");
      }
    } catch (TransformerException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (IOException e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    } catch (Exception e) {
      gatherErrorMsg(e.getLocalizedMessage(), e);
      e.printStackTrace();
    }
  }
}
