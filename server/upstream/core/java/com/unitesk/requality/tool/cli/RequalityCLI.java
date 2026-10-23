/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
package com.unitesk.requality.tool.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;

import org.apache.commons.codec.digest.DigestUtils;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.attribute.valueprovider.IRelatedFilesProvider;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.GenerateReportHandler;
import com.unitesk.requality.core.operation.ReportExportHandler;
import com.unitesk.requality.core.operation.ReportGenResult;
import com.unitesk.requality.core.storage.FSStorage;
import com.unitesk.requality.core.storage.FilesTracker;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateScope;
import com.unitesk.requality.tool.Converter;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.IPathHandler;

//
public class RequalityCLI {

  public static final String cmdName = "requality";
  private static Map<String, String> usages = new HashMap<>();

  //Returns uri comatible string
  public static String getSemiAbsolutePath(String filename, TreeDB db) {
    if (db.isVirtual() && Instantiator.getRootTree(db) != null)
      db = Instantiator.getRootTree(db); // TODO base tree?
    //ITreeStorage ts = db.getStorage();
    // if(ts instanceof ResourceStorage)
    return ResManager.getManager().getLocation(db) + "/" + filename;
    // if(ts instanceof FSStorage)
    // return
    // ((FSStorage)ts).getResourceFolder(ts.getRootNode()).getParent()+"/"+filename;
    // return filename;
  }

  static {
    usages.put(
        "report",
        "Usage: "
            + cmdName
            + " report <template> <catalogue> <output folder> [-root=\"<root qid>\"] [-extraProjects=\"projectName1;projectName2\"] [-<param>=\"<value>\"]\n"
            + "Here: \n"
            + "<template> - a path to a report template (.xml file). All templates delivered with Requality plugin are placed in \"templates\" subfolder.\n"
            + "<catalogue> - a path to the Requality project to be used as a source for report generation.\n"
            + "<output folder> - a path to the folder where report to be generated.\n"
            + "<root qid> - qualified identifier of requirement to be used as a root for report generation. Default value - \"/Requirements\".\n"
            + "[-extraProjects=\"projectName1;projectName2\"] - set of project names for cross-project related issues in reports. Default value - \"\".\n"
            + "[-extraProjectsRaw=\"folderName1;folderName2\"] - set of folder names for cross-project related issues in reports in a case there are several projects with the same name in single folder. Default value - \"\".\n"
            + "[-<param>=\"<value>\"] - a set of report-specific parameters. See documentation for the corresponding report template if it supports additional parameters.");
    usages.put(
        "convert",
        "Usage: "
            + cmdName
            + " convert "
            + "<path to database> <path to document> [<document folder qid>] [<group requirement qid>]");
    usages.put(
        "fixdocs",
        "Usage: " + cmdName + " fixdocs " + "<path to database> <path to html documents>");
  }

  public static void usage() {
    log("Usage: " + cmdName + " " + "<help|convert|report|fixdocs> [params]");
  }

  public static boolean deleteDirectory(File directory) {
    if (directory.exists()) {
      File[] files = directory.listFiles();
      if (null != files) {
        for (int i = 0; i < files.length; i++) {
          if (files[i].isDirectory()) {
            deleteDirectory(files[i]);
          } else {
            files[i].delete();
          }
        }
      }
    }
    return (directory.delete());
  }

  public static String getFileContent(File f) {
    StringBuffer fileData = new StringBuffer();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(f));
      char[] buf = new char[1024];
      int numRead = 0;
      while ((numRead = reader.read(buf)) != -1) {
        String readData = String.valueOf(buf, 0, numRead);
        fileData.append(readData);
      }
      reader.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }
    return fileData.toString();
  }

  public static void usageHelp(String param) {
    StringBuilder result = new StringBuilder();

    if (usages.containsKey(param)) {
      result.append(usages.get(param));
    } else {
      usage();
    }

    log(result.toString());
  }

  private static void log(String string) {
    RLogger.logInfo(string);
  }

  private static Map<File, Map<File, Double>> simMap = new HashMap<>();

  private static File findDir(File fl, String replace) {
    for (File f : fl.listFiles()) {
      if (f.isDirectory() && f.getName().equals(replace)) return f;
    }
    return null;
  }

  private static File findFile(File fl, String replace) {
    for (File f : fl.listFiles()) {
      if (f.isFile() && f.getName().equals(replace)) return f;
    }
    return null;
  }

  // private static
  private static Map<String, SortedMap<Double, File>> sortedPossPar =
      new HashMap<>();

  private static File locateFile(File f, String s) {
    if (!simMap.containsKey(f)) simMap.put(f, new HashMap<File, Double>());
    Double best = -1D;
    File bestF = null;
    for (File f2 : rdirs1.keySet()) {
      if (!simMap.get(f).containsKey(f2)) {
        simMap.get(f).put(f2, calcSim(f, f2));
        if (!simMap.containsKey(f2)) simMap.put(f2, new HashMap<File, Double>());
        simMap.get(f2).put(f, simMap.get(f).get(f2));
      }
      if (f != f2) {
        Double sim = simMap.get(f).get(f2);
        if (fchildnames.get(f2).contains(s)) {
          sim +=
              (findFile(f2, s.replace("html", "xhtml") + ".json") != null ? 1 : 0)
                  + (findDir(f2, s.replace("html", "xhtml") + ResManager.RESOURCES_SUFFIX) != null ? 1 : 0);
          String k = rdirs1.get(f) + "/" + s;//TODO mb test?
          if (!sortedPossPar.containsKey(k)) sortedPossPar.put(k, new TreeMap<Double, File>());
          sortedPossPar.get(k).put(sim, f2);
        }
        if (sim > best) {
          best = sim;
          bestF = f2;
        }
      }
    }
    return bestF;
  }

  private static Double calcSim(String s1, String s2) {
    int i = 0;
    int j = 0;
    while (j < s2.length() && i < s1.length() && s2.charAt(j) != s1.charAt(i)) j++;
    if (j >= s2.length()) return 0D;
    while (i < s1.length() && ((i + j) < s2.length()) && s1.charAt(i) == s2.charAt(i + j)) i++;
    return i / Math.max(s1.length(), s2.length()) * 1D;
  }

  private static Double calcSim(File f, File f2) {
    Set<String> plusSet = new HashSet<>(fchildnames.get(f));
    plusSet.addAll(fchildnames.get(f2));
    Double k = (double) plusSet.size();
    Set<String> d1 = new HashSet<>(plusSet), d2 = new HashSet<>(plusSet);
    d1.removeAll(fchildnames.get(f));
    d2.removeAll(fchildnames.get(f2));
    plusSet.removeAll(d1);
    plusSet.removeAll(d2);
    return plusSet.size() * 1.D / k + calcSim(rdirs2.get(f), rdirs1.get(f2));
  }

  static Set<File> folders = new HashSet<>();
  static Map<File, Set<String>> fchildnames = new HashMap<>();
  static Map<String, File> mapFiles = new HashMap<>();
  static Set<File> rfolders = new HashSet<>();
  static Map<String, File> dirs1 = new HashMap<>(), dirs2 = new HashMap<>();
  static Map<File, String> rdirs1 = new HashMap<>(),
      rdirs2 = new HashMap<>();
  static Map<String, Integer> cntNames = new HashMap<>();

  private static void recReq(File docroot, String pth) {
    rfolders.add(docroot);
    dirs1.put(pth, docroot);
    rdirs1.put(docroot, pth);
    fchildnames.put(docroot, new HashSet<String>());
    for (File sf : docroot.listFiles())
      if (sf.isFile() && sf.getName().endsWith(".xhtml.json"))
        fchildnames.get(docroot).add(sf.getName());
    for (File sf : docroot.listFiles())
      if (sf.isDirectory()) {
        String name = sf.getName();
        if (name.endsWith(ResManager.RESOURCES_SUFFIX)) {
          fchildnames
              .get(docroot)
              .add(name.substring(0, name.length() - 10).replace(".xhtml", ".html"));
        } else recReq(sf, pth + sf.getName() + "/");
      }
  }

  private static void recHandle(File docroot, String pth) {
    folders.add(docroot);
    dirs2.put(pth, docroot);
    rdirs2.put(docroot, pth);
    fchildnames.put(docroot, new HashSet<String>());
    for (File sf : docroot.listFiles())
      if (sf.isFile() && sf.getName().endsWith("html")) fchildnames.get(docroot).add(sf.getName());
    for (File sf : docroot.listFiles())
      if (sf.isDirectory()) recHandle(sf, pth + sf.getName() + "/");
  }

  public static void recRemove(File f) {
    for (File f2 : f.listFiles()) {
      if (f2.isFile()) f2.delete();
      else recRemove(f2);
    }
    f.delete();
  }

  public static void main(String[] args) throws Exception {
    CommandLineArgsProvider.cLineArgs = args;
    CoreUtils.unlockWorkspace();
    CoreUtils.DEBUG_LEVEL = 3;
    if (args.length == 0) {
      usage();
      return;
    }
    RLogger.logInfo("Welcome to Requality console mode! Process started...");

    if ("help".equals(args[0])) {
      String subj = args.length > 1 ? args[1] : "";
      usageHelp(subj);
      return;
    }
    if("server".equals(args[0])) {
       ConsoleProgressMonitor cpm = new ConsoleProgressMonitor();
         cpm.beginTask("Init params", 1);
         ReportSettings rs = null;
         String reportArgs[] = Arrays.copyOfRange(args, 1, args.length);
         String ep = "", epr = "";
         cpm.doneTask();
         cpm.beginTask("Init trees", 1);
         Pair<String, Object>[] repMap = new Pair[] {
            new Pair<>(ResManager.PATH, reportArgs[0]),
            new Pair<>(ResManager.TEMPL_PATH, reportArgs[1])
         };
         ResManager.getManager(repMap);
         //TreeDB db = initDB(ep, epr, toURI(reportArgs[1]), toURI(reportArgs[0]),true); //
//         db.addNodeType(Requirement.TYPE_NAME, Requirement.class);
//         db.addNodeType(Location.TYPE_NAME, Location.class);
//         db.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
//         db.addNodeType(Document.TYPE_NAME, Document.class);
//         db.addNodeType(TestPurpose.TYPE_NAME, TestPurpose.class);
//         db.addNodeType(ReportSettings.TYPE_NAME, ReportSettings.class);
//         db.addNodeType(ReportData.TYPE_NAME, ReportData.class);
//         db.addNodeType(ReportFolder.TYPE_NAME, ReportFolder.class);
//         db.addNodeType(VirtualNode.TYPE_NAME, VirtualNode.class);
//         db.setIsLoaded();
         cpm.doneTask();
         String templatePath = toURI(reportArgs[0]);

         cpm.beginTask("Load templates", 10000);
         Map<String, Template> templs = ResManager.getManager().getTemplates(null, TemplateScope.ALL);
         Template tpl = null;
         String templid = templatePath.toLowerCase();
         if(templs!=null){
           for(String s:templs.keySet()){
             String id = s.toLowerCase(), ida = id;
             int ind = id.lastIndexOf(".");
             if(ind>-1)
               ida = id.substring(ind+1);
             String name = templs.get(s).getName().toLowerCase();
             if(id.endsWith(templid) || name.startsWith(templid) || ida.startsWith(templid)){
               tpl = templs.get(s);
               break;
             }
           }
         }
         
         cpm.doneTask();
         String rootQId = Requirement.getTypeRootQId();
         //String saveTo = reportArgs[2];

         // instTree.addNodeType(Requirement.TYPE_NAME, Requirement.class);
         // instTree.addNodeType(Location.TYPE_NAME, Location.class);
         // instTree.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
         // instTree.addNodeType(Document.TYPE_NAME, Document.class);
         // instTree.addNodeType(TestPurpose.TYPE_NAME, TestPurpose.class);
         // instTree.addNodeType(VirtualNode.TYPE_NAME,VirtualNode.class);

         Map<String, String> params = new HashMap<>();
         for (int i = 3; i < reportArgs.length; i++)
           if (reportArgs[i].startsWith("-")) {
             int k = reportArgs[i].indexOf("=");
             if (k < 0) {
               log(
                   "user-defined parameter '"
                       + reportArgs[i]
                       + "' is not provided with value in form of param=\"value\"");
               continue;
             }
             String name = reportArgs[i].substring(1, k), value = reportArgs[i].substring(k + 1);
             if (value.startsWith("\"") && value.endsWith("\""))
               value = value.substring(1, value.length() - 1);
             if (name.equals("root")) rootQId = value;
             else params.put(name, value);
           }

//         TreeDB instTree =
//             db; // Instantiator.getVirtualTreeDB(db,Instantiator.DEFAULT_INSTANCE_NAME,Instantiator.getVirtualStorage(db,
         // Instantiator.DEFAULT_INSTANCE_NAME),params);
//         Requirement rootReq = null;
//         try {
//           rootReq = (Requirement) instTree.getNode(rootQId, Path.QID);
//           if (rootReq == null) {
//             rootReq = (Requirement) instTree.getNode(Requirement.getTypeRootQId(), Path.QID);
//             log("target id is not valid, default value is used");
//           }
//         } catch (Exception e) {
//           log("root id is not valid or catalog cannot be loaded");
//           rootReq = (Requirement) instTree.getNode(Requirement.getTypeRootQId(), Path.QID);
//         }
         boolean msg = false;//db.startTransaction("generate report");
         cpm.doneTask();
         try{
           cpm.beginTask("run server", 1);
           API.initSources();
           BufferedReader bis = new BufferedReader(new InputStreamReader(System.in));
           String comm = "";
           while(!comm.equalsIgnoreCase("exit")) {
             comm = bis.readLine();
           }
           cpm.doneTask();
         }
         catch(Exception e){
           log("Report generation error:");
           log(CoreUtils.getTrace(e));
         }
         finally{
//           if(msg)
//             db.rollback();
           if(rs!=null)
             rs.delete();
           cpm.doneAll();
           API.destroySources();
         }
         return;
    }else
    if ("fixdocscontent".equals(args[0])) {
      if (args.length == 2) {
        String pathToDB = args[1];

        LinkedList<File> stack = new LinkedList<>();
        stack.add(new File(pathToDB));
        pathToDB = toURI(pathToDB);
        Map<String, List<File>> pathToResFolders = new TreeMap<>();
        Map<String, List<File>> pathToFiles = new TreeMap<>();
        while (!stack.isEmpty()) {
          File f = stack.pollFirst();
          String fPath = f.getAbsolutePath();
          pathToResFolders.put(fPath, new LinkedList<File>());
          pathToFiles.put(fPath, new LinkedList<File>());
          for (File f2 : f.listFiles()) {
            if (f2.isFile() && f2.getName().endsWith(".xhtml.json")) {
              pathToFiles.get(fPath).add(f2);
            } else if (f2.isDirectory()) {
              if (f2.getName().endsWith(".xhtml"+ResManager.RESOURCES_SUFFIX)) {
                pathToResFolders.get(fPath).add(f2);
              } else stack.addLast(f2);
            }
          }
          for (File fc : pathToFiles.get(fPath)) {
            File resfold = null;
            for (File fcres : pathToResFolders.get(fPath))
              if (fc.getName()
                  .substring(0, fc.getName().length() - 5)
                  .equals(fcres.getName().substring(0, fcres.getName().length() - 10)))
                resfold = fcres;
            if (resfold != null) {
              File[] found =
                  resfold.listFiles(
                      new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                          return name.endsWith(".xhtml");
                        }
                      });
              if (found == null || found.length == 0) resfold = null;
            }
            if (resfold == null) {
              // res not found
              File fp = f.getParentFile();
              while (resfold == null && fp != null) {
                boolean cont = false;
                for (File fc2 : pathToFiles.get(fp.getAbsolutePath())) {
                  if (fc2.getName().equals(fc.getName())) {
                    cont = true;
                    break;
                  }
                }
                if (!cont) {
                  for (File fc2 : pathToResFolders.get(fp.getAbsolutePath())) {
                    if (fc.getName()
                        .substring(0, fc.getName().length() - 5)
                        .equals(fc2.getName().substring(0, fc2.getName().length() - 10))) {
                      File[] found =
                          fc2.listFiles(
                              new FilenameFilter() {
                                @Override
                                public boolean accept(File dir, String name) {
                                  return name.endsWith(".xhtml");
                                }
                              });
                      if (found == null || found.length == 0) {
                        recRemove(fc2);
                        continue;
                      }
                      resfold = new File(URI.create(f.toURI() + "/" + fc2.getName()));
                      if (resfold.exists()) recRemove(resfold);
                      Files.move(
                          fc2.toPath(), resfold.toPath(), StandardCopyOption.REPLACE_EXISTING);
                      break;
                    }
                  }
                }
                fp = fp.getParentFile();
              }
            }
            BufferedReader br =
                new BufferedReader(new java.io.InputStreamReader(new FileInputStream(fc)));
            boolean isFile = false;
            String resname = null;
            while (br.ready()) {
              String s = br.readLine();
              if (s.contains("\"_file\"")) isFile = true;
              if (s.contains("},")) isFile = false;
              if (isFile) {
                int ind = s.indexOf("xhtml");
                if (ind > -1) {
                  s = s.substring(0, ind + 5);
                  resname = s.substring(s.lastIndexOf("\"") + 1);
                }
              }
            }
            br.close();
            if (resname != null) {
              if (resfold == null) {
                // no res folder
              } else {
                @SuppressWarnings("unused")
                File[] found =
                    resfold.listFiles(
                        new FilenameFilter() {
                          @Override
                          public boolean accept(File dir, String name) {
                            return name.endsWith(".xhtml");
                          }
                        });
                // TODO rewrite
                // if(found!=null && found.length>0){
                // File mostClose = null;
                // for(File fnd:found){
                // String name = fnd.getName();
                // if(name.equals(resname))
                // mostClose = fnd;
                // else{
                // RLogger.logInfo(fnd.getName()+"
                // "+resname);
                // }
                // }
                // }else
                // {
                // //empty res
                // RLogger.logInfo(resfold.getAbsolutePath()+"
                // "+ resname);
                // }
              }
            } else {
              // no res names
            }
          }
        }
        TreeDB db = initDB("", "", pathToDB, null, false);
        try{
          db.addNodeType(Requirement.TYPE_NAME, Requirement.class);
          db.addNodeType(Location.TYPE_NAME, Location.class);
          db.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
          db.addNodeType(Document.TYPE_NAME, Document.class);
          db.addNodeType(VirtualNode.TYPE_NAME, VirtualNode.class);
          TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
          tsm.setIsLoaded(db.getLocalName(), db.getProjectName(), db, tsm.getRevisionInfo(db));
          db.startTransaction("Remove nodes");
  
          db.commit();
          return;
        }
        finally{
        }
      }
    }
    
    if ("fixdocs".equals(args[0])) {
      if (args.length != 3) {
        usageHelp(args[0]);
        return;
      }
      try {
        String dbpath = args[1];
        // docpath="/home/kildishev/Документы/work/susv4tc2/xbd.html",
        // docfoldpath="/Documents/";
        File docroot = new File(args[2]);
        File projroot = new File(dbpath);
        recReq(projroot, "");
        recHandle(docroot, "");
        for (File f : rdirs2.keySet())
          for (String s : fchildnames.get(f)) {
            if (s.equals("fesetround.html")) RLogger.logInfo(123);
            File fl = locateFile(f, s);
            mapFiles.put(rdirs2.get(f), fl);
            if (fl != null) {
              File rdir = findDir(fl, s.replace(".html", ".xhtml") + ResManager.RESOURCES_SUFFIX);
              File rfile = findFile(fl, s.replace(".html", ".xhtml") + ".json");
              if (rdir != null) {
                File cont = findFile(rdir, s.replace(".html", ".xhtml"));

                if (cont == null) {
                  if (rfile != null) rfile.delete();
                  recRemove(rdir);
                  RequalityCLI.main(
                      new String[] {
                        "convert",
                        dbpath,
                        f.toURI().toString() + "/" + s,
                        "/Documents/" + rdirs1.get(fl)
                      });
                  RLogger.logInfo("content lost " + fl.getAbsolutePath() + " " + s);
                }

              } else if (rfile == null) {
                fl = locateFile(f, s);
                RequalityCLI.main(
                    new String[] {
                      "convert",
                      dbpath,
                      f.toURI().toString() + "/" + s,
                      "/Documents/" + rdirs1.get(fl)
                    });
                // RLogger.logInfo("lost
                // "+f.getAbsolutePath()+File.separator+s+"
                // "+rdirs1.get(fl));
              } else RLogger.logInfo("reslost " + fl.getAbsolutePath() + " " + s);
            } else {
              RLogger.logInfo("Not found " + f.getAbsolutePath() + " " + s);
              RequalityCLI.main(
                  new String[] {
                    "convert",
                    dbpath,
                    f.toURI().toString() + "/" + s,
                    "/Documents/" + rdirs1.get(fl)
                  });
            }
          }
      } catch (Exception e) {
        RLogger.logError(e);
      }
      return;
    }
    if ("remove".equals(args[0])) {
      if (args.length > 2 && args.length < 6) {

        String pathToDB = ".";
        String name = args[1];
        int ind = name.lastIndexOf(File.separator);
        if (ind > -1) {
          pathToDB = name.substring(0, ind);
          name = name.substring(ind);
        }

        //TODO proper project loading
        TreeDB db = TreeDB.loadTreeInstance("test", "test",null, IResTracker.NULL, new FSStorage(new File(pathToDB).toURI(), null));//TODO IDE monitor
        db.addTreeStateHooks("test", "test");
        db.addNodeType(Requirement.TYPE_NAME, Requirement.class);
        db.addNodeType(Location.TYPE_NAME, Location.class);
        db.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
        db.addNodeType(Document.TYPE_NAME, Document.class);
        db.addNodeType(VirtualNode.TYPE_NAME, VirtualNode.class);
        if(db.startTransaction("Remove nodes")){
          for (int i = 2; i < args.length; i++) {
            String id = args[i];
            try {
              db.getNode(id, Path.QID).delete();
            } catch (Exception e) {
              RLogger.logError(e);
            }
          }
          db.commit();
        }
        return;
      }
    }
    // [<document folder qid>] [<group requirement qid>]
    if ("convert".equals(args[0])) {
      if (args.length > 2 && args.length < 6) {
        String pathToDB = args[1];
        String pathToDoc = args[2];
        String[] qids = new String[args.length - 3];
        for (int i = 3; i < args.length; i++) {
          qids[i - 3] = args[i];
        }

        File inFile = new File(pathToDoc);
        //TODO proper project loading
        TreeDB db = TreeDB.loadTreeInstance("test","test", null,IResTracker.NULL, new FSStorage(new File(pathToDB).toURI(),null));//TODO IDE monitor
        db.addTreeStateHooks("test", "test");
        db.addNodeType(Requirement.TYPE_NAME, Requirement.class);
        db.addNodeType(Location.TYPE_NAME, Location.class);
        db.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
        db.addNodeType(Document.TYPE_NAME, Document.class);
        db.addNodeType(VirtualNode.TYPE_NAME, VirtualNode.class);
        Requirement groupNode = (Requirement) db.getNode(Requirement.getTypeRootQId(), Path.QID);
        DocFolder folderNode = (DocFolder) db.getNode(Document.getTypeRootQId(), Path.QID);
        if (groupNode == null) {
          RLogger.logInfo("target catalog cannot be loaded or target node does not exist");
          return;
        }

        if(db.startTransaction("convert")){
          String name = inFile.getName().replace("html", "xhtml");
          if (qids.length > 0) folderNode = (DocFolder) db.getNode(qids[0], Path.QID);
          Document doc = (Document) folderNode.createChild(name, Document.TYPE_NAME);
          int ind = name.lastIndexOf(".");
          if (ind > -1) name = name.substring(0, ind);
          doc.setResourceName(name + ".xhtml");
          doc.commitAttributes();
          db.commit();
          Converter c = new Converter();
          FileReader fr = null;
          Writer out = null;
          try {
            File res = new File(doc.getHTMLPath()).getParentFile();
            if (!res.exists()) res.mkdirs();
  
            out = new FileWriter(new File(doc.getHTMLPath()));
            StringWriter tmpOut = new StringWriter();
            fr = new FileReader(inFile);
            //TODO fix convert
            //DocumentImporter.cleanHtml(fr, fr.getEncoding(), tmpOut);
            Map<String, String[]> result = c.convert(new StringReader(tmpOut.toString()), out);
            c.store(result, doc.getQualifiedId(), db, groupNode);
            RLogger.logInfo("OK");
          } catch (IOException e) {
            RLogger.logInfo("ERROR");
            RLogger.logError(e);
          }
          finally{
            if(out!=null)
              out.close();
            if(fr!=null)
              fr.close();
          }
        }
        return;
      }
      usageHelp("convert");
      return;
    }

    if ("report".equals(args[0])) {
      if (args.length < 4) {
        usageHelp("report");
        return;
      }
      ConsoleProgressMonitor cpm = new ConsoleProgressMonitor();
      cpm.beginTask("Init params", 10000);
      ReportSettings rs = null;
      String reportArgs[] = Arrays.copyOfRange(args, 1, args.length);
      String ep = "", epr = "";
      for(int i = 0;i<reportArgs.length;i++){
        String s=reportArgs[i];
        if(s.startsWith("-extraProjects=")){
          ep = s;
          String []rag = reportArgs;
          reportArgs = new String[reportArgs.length-1];
          for(int j = 0;j<rag.length;j++){
            if(j<i)
              reportArgs[j] = rag[j];
            if(j>i)
              reportArgs[j-1] = rag[j];
          }
          int pos = ep.indexOf("=");
          if(pos==-1)
          {
            ep = "";
          }else
          {
            ep = ep.substring(pos+1);
          }
        }
      }
      for(int i = 0;i<reportArgs.length;i++){
        String s=reportArgs[i];
        if(s.startsWith("-extraProjectsRaw=")){
          epr = s;
          String []rag = reportArgs;
          reportArgs = new String[reportArgs.length-1];
          for(int j = 0;j<rag.length;j++){
            if(j<i)
              reportArgs[j] = rag[j];
            if(j>i)
              reportArgs[j-1] = rag[j];
          }
          int pos = epr.indexOf("=");
          if(pos==-1)
          {
            epr = "";
          }else
          {
            epr = epr.substring(pos+1);
          }
        }
      }
      cpm.doneTask();
      cpm.beginTask("Init trees", 10000);
      TreeDB db = initDB(ep, epr, toURI(reportArgs[1]), toURI(reportArgs[0]),true); //
      db.addNodeType(Requirement.TYPE_NAME, Requirement.class);
      db.addNodeType(Location.TYPE_NAME, Location.class);
      db.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
      db.addNodeType(Document.TYPE_NAME, Document.class);
      db.addNodeType(TestPurpose.TYPE_NAME, TestPurpose.class);
      db.addNodeType(ReportSettings.TYPE_NAME, ReportSettings.class);
      db.addNodeType(ReportData.TYPE_NAME, ReportData.class);
      db.addNodeType(ReportFolder.TYPE_NAME, ReportFolder.class);
      db.addNodeType(VirtualNode.TYPE_NAME, VirtualNode.class);
      TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
      tsm.setIsLoaded(db.getLocalName(), db.getProjectName(), db, tsm.getRevisionInfo(db));
      cpm.doneTask();
      String templatePath = toURI(reportArgs[0]);

      cpm.beginTask("Load templates", 10000);
      Map<String, Template> templs = ResManager.getManager().getTemplates(db, TemplateScope.ALL);
      Template tpl = null;
      String templid = templatePath.toLowerCase();
      if(templs!=null){
        for(String s:templs.keySet()){
          String id = s.toLowerCase(), ida = id;
          int ind = id.lastIndexOf(".");
          if(ind>-1)
            ida = id.substring(ind+1);
          String name = templs.get(s).getName().toLowerCase();
          if(id.endsWith(templid) || name.startsWith(templid) || ida.startsWith(templid)){
            tpl = templs.get(s);
            break;
          }
        }
      }
      if(tpl == null)
        tpl = new Template(templatePath, TemplateScope.BUILDIN);

      cpm.doneTask();
      String rootQId = Requirement.getTypeRootQId();
      String saveTo = reportArgs[2];

      // instTree.addNodeType(Requirement.TYPE_NAME, Requirement.class);
      // instTree.addNodeType(Location.TYPE_NAME, Location.class);
      // instTree.addNodeType(DocFolder.TYPE_NAME, DocFolder.class);
      // instTree.addNodeType(Document.TYPE_NAME, Document.class);
      // instTree.addNodeType(TestPurpose.TYPE_NAME, TestPurpose.class);
      // instTree.addNodeType(VirtualNode.TYPE_NAME,VirtualNode.class);

      Map<String, String> params = new HashMap<>();
      for (int i = 3; i < reportArgs.length; i++)
        if (reportArgs[i].startsWith("-")) {
          int k = reportArgs[i].indexOf("=");
          if (k < 0) {
            log(
                "user-defined parameter '"
                    + reportArgs[i]
                    + "' is not provided with value in form of param=\"value\"");
            continue;
          }
          String name = reportArgs[i].substring(1, k), value = reportArgs[i].substring(k + 1);
          if (value.startsWith("\"") && value.endsWith("\""))
            value = value.substring(1, value.length() - 1);
          if (name.equals("root")) rootQId = value;
          else params.put(name, value);
        }

      TreeDB instTree =
          db; // Instantiator.getVirtualTreeDB(db,Instantiator.DEFAULT_INSTANCE_NAME,Instantiator.getVirtualStorage(db,
      // Instantiator.DEFAULT_INSTANCE_NAME),params);
      Requirement rootReq = null;
      try {
        rootReq = (Requirement) instTree.getNode(rootQId, Path.QID);
        if (rootReq == null) {
          rootReq = (Requirement) instTree.getNode(Requirement.getTypeRootQId(), Path.QID);
          log("target id is not valid, default value is used");
        }
      } catch (Exception e) {
        log("root id is not valid or catalog cannot be loaded");
        rootReq = (Requirement) instTree.getNode(Requirement.getTypeRootQId(), Path.QID);
      }
      boolean msg = false;//db.startTransaction("generate report");
      cpm.doneTask();
      try{
        cpm.beginTask("pre-generation init", 10000);
        TreeNode rf = db.getNode("/Reports");
        rs = (ReportSettings) rf.createChild(getNextId(rf, ReportSettings.TYPE_NAME,"temp"),ReportSettings.TYPE_NAME);
        rs.attrs().setConstAttribute(ReportSettings.ATTR_TEMPLATE, tpl.getId(), 0);
        rs.setCrossProjectFlag(true);
        rs.attrs().setConstAttribute(VirtualNode.ATTR_COLLAPSEVNODE, true, 0);//seems to be not needed
        for(String s:params.keySet())
          rs.attrs().setConstAttribute(s, params.get(s),0);
        rs.commitAttributes();
        GenerateReportHandler one = (GenerateReportHandler) GenerateReportHandler.getOne();
        //one.setIgnoreTrans(true);
        cpm.doneTask();
        ReportGenResult<TreeNode>[] rgr = one.execute(false, db, cpm, false, rs);
        for(ReportGenResult<TreeNode> r:rgr) {
          if(r == null)
            continue;//TODO error
          File f = new File(saveTo);
          if(!f.exists())
            f.mkdirs();
          File ff = new File(r.href);
          if(ff.exists()){
            ReportExportHandler.getOne().exportReport(
                Arrays.asList(new TreeNode[] {r.report}),
                f.toPath().toString(), false);
            log("Report is ready");
            r.report.delete();
          }else{
            log("Report generation was not succeed "+r.savePath);
          }
        }
      }
      catch(Exception e){
        log("Report generation error:");
        log(CoreUtils.getTrace(e));
      }
      finally{
        if(msg)
          db.rollback();
        if(rs!=null)
          rs.delete();
        cpm.doneAll();
      }
      return;
    }
    // if ("docclean".equals(args[0])) {
    // if (args.length == 2) {
    // docclean(args[1]);
    // } else {
    // RLogger.logInfo("neep path to database");
    // }
    // return;
    // }

    usage();
  }

  private static TreeDB initDB(String ep, String epr, String path, String reports, boolean dropCaches) {
    FilesTracker ft = (FilesTracker)ResManager.getManager().getTracker();
    org.w3c.dom.Document doc = ft.loadDocByFolder(new File(URI.create(path)));
    String name = FilesTracker.getNameFromInfo(doc);
    String pathToDB = ".";
    String names = path;
    if (names.endsWith("/")) names = names.substring(0, names.length() - 1);
    int ind = names.lastIndexOf("/");
    if (ind > -1) {
      pathToDB = names.substring(0, ind);
      names = names.substring(ind + 1);
    }
    if(name == null)
      name = names;
    if (reports != null) {
      if (reports.endsWith("/")) reports = reports.substring(0, reports.length() - 1);
      ind = reports.lastIndexOf("/");
      if (ind > -1) reports = reports.substring(0, ind + 1);
    }
    com.unitesk.requality.core.operation.IProgressMonitor mon = new ConsoleProgressMonitor();//TODO add IDE monitor
    ResManager.changeResManagerProvider(FilesTracker.PROVIDER);
    return ResManager.getManager(new Pair<String, Object>(ResManager.PATH,pathToDB), 
        new Pair<String, Object>(ResManager.TEMPL_PATH, reports),
        new Pair<String, Object>(ResManager.ALLOWED_PROJECTS,ep.isEmpty()?name:ep+";"+name),
        new Pair<String, Object>(ResManager.FORCE_RAW_NAMES,epr.isEmpty()?names:epr+";"+names),
        new Pair<String, Object>(ResManager.DROP_CACHE_ON_INIT,dropCaches))
          .getTreeDB(null,name, name, true, mon);
  }

  // private static void docclean(String path) {
  // TreeDB db = new TreeDBIO(path);
  // Map<String, Set<UUID>> docToUUIDs = new HashMap<String, Set<UUID>>();
  // for (TreeDBNode r : new DeepFirstTreeWalker(db.getRootNode(
  // Requirement.type, false))) {
  // Requirement req = (Requirement) r;
  // for (Location loc : req.getLocations()) {
  // String docId = loc.getDocId().replace('\\', '/');
  // if (docToUUIDs.get(docId) == null) {
  //
  // docToUUIDs.put(docId, new HashSet<UUID>());
  // }
  // docToUUIDs.get(docId).add(loc.getId());
  // }
  // }
  // for (String docId : docToUUIDs.keySet()) {
  // Set<UUID> locRemove = new HashSet<UUID>();
  // Document doc = db.getDocument(docId);
  // RLogger.logInfo("processing document: " + docId + " locations ("
  // + docToUUIDs.get(docId).size() + "/" + doc.sizeLocations()
  // + ")");
  //
  // for (UUID locId : doc.getLocations()) {
  // locRemove.add(locId);
  // }
  // locRemove.removeAll(docToUUIDs.get(docId));
  // for (UUID locId : locRemove) {
  // RLogger.logInfo("Remove location " + locId.toString()
  // + " from " + docId);
  // doc.removeLocation(locId);
  // }
  // }
  // }
  public static String toURI(String path) {
    File f = new File(path);
    if (f.exists()) return f.toURI().toString();
    try {
      f = new File(URI.create(path));
      if (f.exists()) return f.toURI().toString();
    } catch (Exception e) {
    }
    return path;
  }

  public static File copyfile(URI srFile, URI dtFile) throws IOException {
    return copyfile(null, srFile, dtFile, true);
  }

  public static File copyfile(TreeNode node, URI srFile, URI dtFile) throws IOException {
    return copyfile(node, srFile, dtFile, false);
  }

  public static File copyfile(TreeNode node, URI srFile, URI dtFile, boolean rewrite)
      throws IOException {
    File sourceFile = srFile.getScheme().startsWith("file")? new File(srFile):null;
    File destFile = new File(dtFile);
    if(sourceFile !=null && sourceFile.equals(destFile))
      return destFile;
    boolean localfile = true;
    if (sourceFile == null || !sourceFile.exists()) {
      localfile = false;
      // throw new IOException(sourceFile+" not exists");
    }
    ReadableByteChannel source = null;
    FileInputStream fis = null;
    try{
      if (!localfile) {
        try {
          URL website = srFile.toURL();
          InputStream is = website.openStream();
          source = Channels.newChannel(is);
          if (!source.isOpen()) {
            is.close();
            throw new IOException(srFile + " not exists");
          }
        } catch (MalformedURLException me) {
          throw new IOException(srFile + " not exists");
        } catch (IOException ie) {
          throw new IOException(srFile + " not exists");
        }
      }
      File possibleParent = destFile.getParentFile();
      if (!destFile.getParentFile().exists()) {
        // hack to not to break resource system
        if (node != null) {
          destFile =
              new File(URI.create(possibleParent.toURI().toString() + "/" + destFile.getName()));
        } else {
          possibleParent.mkdirs();
        }
      }
      if (!destFile.exists()) {
        destFile.createNewFile();
      } else if (!rewrite) {
        return destFile;
      } else {
        destFile.delete();
        destFile.createNewFile();
      }
      FileChannel destination = null;
      FileOutputStream fos = null;
      try {
        if(sourceFile!=null && (sourceFile.isDirectory() || !sourceFile.exists()))
          return sourceFile;
        if (localfile){
          fis = new FileInputStream(sourceFile);
          if(source!=null)
            source.close();
          source = fis.getChannel();
        }
        fos = new FileOutputStream(destFile);
        destination = fos.getChannel();
        destination.transferFrom(source, 0, Long.MAX_VALUE);
      } finally {
        if (destination != null) {
          destination.close();
        }
        if(fis!=null)
          fis.close();
        if (fos != null) fos.close();
      }
      if (node != null) {
        String s = destFile.getName();
        File temp = new File(destFile.getAbsolutePath() + "_temp");
        destFile.renameTo(temp);
        if (node.isResourceExists(s)) node.deleteResource(s, OFlag.NONE);
        fis = new FileInputStream(temp);
        node.setResourceContent(s, fis, OFlag.NONE);
        fis.close();
        temp.delete();
        destFile = new File(URI.create(possibleParent.toURI().toString() + "/" + s));
      }
      return destFile;
    }
    finally{
      if(source!=null)
        source.close();
    }
  }

  public static String getNumberStringFromId(String in) {
    Pattern pattern = Pattern.compile("(.*\\D|)(\\d*)$");
    Matcher m = pattern.matcher(in);
    if (m.matches()) {
      return m.group(m.groupCount());
    } else {
      return null;
    }
  }

  public static String getNextId(
      Iterable<TreeNode> workList, TreeNode parent, String type, String defaultPrefix) {
    String newId = "000";
    String fullNewId = "000";
    boolean found = false;

    for (int i = 0; i < parent.sizeWorkChildren(); i++) {
      try {
        if (parent.getWorkChild(i).getType().equals(type) || type == null) {
          String number = getNumberStringFromId(parent.getWorkChild(i).getId());
          if (number == null) {
            continue;
          }
          try {
            if (Integer.parseInt(number) >= Integer.parseInt(newId)) {
              fullNewId = parent.getWorkChild(i).getId();
              newId = number;
              found = true;
            }
          } catch (NumberFormatException e) {
          }
        }
      } catch (Exception e) {
        RLogger.logError(e);
      }
    }

    if (workList != null)
      for (TreeNode nd : workList) {
        if (nd.getType().equals(type) || type == null) {
          String number = getNumberStringFromId(nd.getId());
          if (number == null) {
            continue;
          }
          try {
            if (Integer.parseInt(number) >= Integer.parseInt(newId)) {
              fullNewId = nd.getId();
              newId = number;
              found = true;
            }
          } catch (NumberFormatException e) {
          }
        }
      }

    if (!found && defaultPrefix != null) {
      fullNewId = defaultPrefix + fullNewId;
    }

    do {
      fullNewId = incLastNumberInString(fullNewId);
    } while (parent.findWorkChild(fullNewId) != null);
    return fullNewId;
  }

  public static String getNextId(Set<String> ids) {

    String newId = "000";
    String fullNewId = "000";
    for (String id : ids) {
      String number = getNumberStringFromId(id);
      if (number == null) {
        continue;
      }
      try {
        if (Integer.parseInt(number) >= Integer.parseInt(newId)) {
          fullNewId = id;
          newId = number;
        }
      } catch (NumberFormatException e) {
      }
    }

    do {
      fullNewId = incLastNumberInString(fullNewId);
    } while (ids.contains(fullNewId));
    return fullNewId;
  }

  public static String getNextId(TreeNode parent, String type, String defaultPrefix) {
    return getNextId(null, parent, type, defaultPrefix);
  }

  // Utilites
  public static String incLastNumberInString(String str) {
    String regex = "\\d*";
    String tmp = str;

    while (!tmp.matches(regex)) tmp = tmp.substring(1);
    int diff = str.length() - tmp.length();
    String fixture = "";
    if (diff >= 0) fixture = str.substring(0, diff);
    String tail = String.valueOf(Integer.parseInt(tmp) + 1);

    if (tail.length() < tmp.length()) {
      tail = makeZeroString(tmp.length() - tail.length()) + tail;
    }
    return fixture + tail;
  }

  private static String makeZeroString(int zeroCount) {
    StringBuilder sb = new StringBuilder(zeroCount);
    for (int i = 0; i < zeroCount; i++) sb.append("0");
    return sb.toString();
  }

  public static boolean isWindowsLocalPath(String path, int offset) {
    if (path.length() >= 2 + offset
        && (path.charAt(offset) >= 'a' && path.charAt(offset) <= 'z'
            || path.charAt(offset) >= 'A' && path.charAt(offset) <= 'Z')
        && (path.charAt(offset + 1) == ':' || path.charAt(offset + 1) == '|')) return true;
    return false;
  }

  private static final Pattern patternImages =
      Pattern.compile("<(img|object)\\s+[^>]*(src|data)\\s*=\\s*(\\\"|')([^\\\"']+)(?:\\3)[^>]*>", Pattern.MULTILINE | Pattern.DOTALL);

  public static Pattern getImagePattern() {
    return patternImages;
  }

  private static void cloneDir(File source, File dest) {
    if (!source.exists()) return;
    for (File ch : source.listFiles()) {
      File newch = null;
      try {
        newch =
            new File(new URI(dest.toURI().toString() + "/" + CoreUtils.URLEncode(ch.getName())));
      } catch (URISyntaxException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      if(newch==null)
        return;
      if (ch.isDirectory()) cloneDir(ch, newch);
      else
        try {
          RequalityCLI.copyfile(ch.toURI(), newch.toURI());
        } catch (IOException e) {
          RLogger.logError(e);
        }
    }
  }

  public static String handleImage(URI dpath, String src, TreeNode n, IRelatedFilesProvider added, boolean attr) {
    // String src = matcher.group(4);
    if (src.startsWith(ResManager.RES_VAR)||src.startsWith(ResManager.ATTR_RES_VAR))
      return src;
    String pathToImage = src;
    if(src.startsWith("data:image"))
      return src;
    try {
      // search source file
      String spath = pathToImage;
      if (spath.isEmpty()) return spath;
      File srcFile = null;
      String ext = "";
      if (spath.startsWith("http:") || spath.startsWith("https:") || spath.startsWith("ftp")) {
        // suppose, file in URI format
        ReadableByteChannel source = null;
        int q = -1;
        File temp = null;
        try{
          try {
            URL website = new URL(spath);
            InputStream is = website.openStream();
            source = Channels.newChannel(is);
            q = spath.indexOf("?");
            //if (q > -1) spath = spath.substring(0, q - 1);
            if (!source.isOpen()) {
              is.close();
              throw new RuntimeException(spath + " not exists");
            }
          } catch (MalformedURLException me) {
            throw new RuntimeException(spath + " url mailformed");
          } catch (Exception ie) {
            throw new RuntimeException(spath + " exception "+ie.getMessage());
          }
          if(q == -1)
            q = spath.length();
          int point = spath.substring(0,q).lastIndexOf(".");
          
          if (point > -1 && point >= (spath.length() - 4)) {
            ext = spath.substring(point + 1);
          }
          FileChannel destination = null;
          FileOutputStream fos = null;
          
          try {
            temp = File.createTempFile("reqImages", null);
            temp.deleteOnExit();
            fos = new FileOutputStream(temp);
            destination = fos.getChannel();
            destination.transferFrom(source, 0, Long.MAX_VALUE);
          } catch (Exception e) {
            RLogger.logError(e);
          } finally {
            if (destination != null) {
              try {
                destination.close();
              } catch (IOException e) {
                RLogger.logError(e);
              }
            }
            if (fos != null)
              try {
                fos.close();
              } catch (IOException e) {
                RLogger.logError(e);
              }
          }
        }
        finally{
          if (source != null) {
            try {
              source.close();
            } catch (IOException e) {
              RLogger.logError(e);
            }
          }
        }
        try(FileImageInputStream fiis = new FileImageInputStream(temp)){
          Iterator<ImageReader> irs = ImageIO.getImageReaders(fiis);
          while (irs.hasNext()) {
            ImageReader reader = irs.next();
            try {
              ext = reader.getFormatName().toLowerCase();
            } catch (IOException e) {
              // TODO Auto-generated catch block
              RLogger.logError(e);
            }
          }
        }
        ext = clearNonCD(ext);
        String name = extractHash(temp) + "." + ext;
        try(FileInputStream fis = new FileInputStream(temp)){
          if(n!=null)//TODO look
            n.setResourceContent("data/" + name, fis, OFlag.NONE);
          else
          {
  //          File f = new File(URI.create(dpath.toString()+"/"+name));
  //          Files.copy(fis,f.toPath(),StandardCopyOption.REPLACE_EXISTING);
            added.addFile(name, fis, temp);
          }
        }
        // Files.copy(temp.toPath(),
        // nf.toPath(),StandardCopyOption.REPLACE_EXISTING);
        return (attr?ResManager.ATTR_RES_VAR:ResManager.RES_VAR)+"/" + name;
      } else {
        if (spath.startsWith("file:")) {
          try {
            spath = new File(new URI(spath)).getAbsolutePath();
          }catch(IllegalArgumentException iae) {
            spath = spath.substring(7);//cut file://
          }
        }
        spath = URLDecoder.decode(spath, "UTF-8").replace("\\\\", "/");
        
        int ind = -1;
        ind = spath.indexOf("//");
        while (ind > -1) {
          spath = spath.replace("//", "/");
          ind = spath.indexOf("//");
        }

        // Windows' shares
        int i = 5;
        while (i < Math.min(10, spath.length()) && !isWindowsLocalPath(spath, i)) i++;
        if (i < spath.length() && isWindowsLocalPath(spath, i)) spath = spath.substring(i);
        i = 0;
        while ((i + 2) < spath.length() && spath.charAt(i) == '/' && spath.charAt(i + 1) == '/')
          i++;
        if (i < spath.length()) spath = spath.substring(i);
        // srcFile = new File(spath);
        // if(!srcFile.exists())
        // if(!srcFile.exists())
        // srcFile = new File(path+File.separator+spath);//
        try {
          srcFile = new File(new URI("file:///" + decodeDrive(CoreUtils.URLEncode(spath)))); //
        } catch (Exception e) {
          System.err.println(e.toString());
        }
        if (srcFile == null || !srcFile.exists())
          try {
            srcFile = new File(new URI(dpath  + CoreUtils.URLEncode(spath)));
          } catch (Exception e) {
            System.err.println(e.toString());
          }
        if (srcFile == null || !srcFile.exists())
          try {
            srcFile = new File(new URI("file:///" + CoreUtils.URLEncode(spath))); //
            if (srcFile.exists()) spath = CoreUtils.URLEncode(spath);
          } catch (Exception e) {
            System.err.println(e.toString());
          }
        if (srcFile == null || !srcFile.exists())
          try {
            srcFile = new File(new URI(dpath + "/" + CoreUtils.URLEncode(spath)));
            if (srcFile.exists()) spath = CoreUtils.URLEncode(spath);
          } catch (Exception e) {
            System.err.println(e.toString());
          }
      }
      if (srcFile == null || !srcFile.exists() || srcFile.isDirectory()) {
        return null;
      }
      // now srcFile is absolute not URI path
      // short file name in URI (with %20 as ' ' etc.)
      String shortDestFileName = (new File(srcFile.toURI().toString())).getName();
      int nSuffix = 0;
      // divide FileName into File Name and extension
      ext = "";
      int dot = shortDestFileName.lastIndexOf('.');
      if (dot > -1) {
        ext = shortDestFileName.substring(dot);
        shortDestFileName = shortDestFileName.substring(0, dot);
      }

      String nS = "";
      String resName = URLDecoder.decode(shortDestFileName, "UTF-8");
      String descrResName = shortDestFileName;
      if(n!=null)//data???
        while (n.hasResource("data/" + resName + nS + ext)) {
          nSuffix++;
          // dstFile = new File(URI.create(n.getFileResourcesPath() + '/'
          // + shortDestFileName + nSuffix + ext));
          nS = Integer.toString(nSuffix);
        }
      // copy
      try {
        if (srcFile.exists() && srcFile.isFile()) {
          FileInputStream in = new FileInputStream(srcFile);
          try{
            if(n!=null){
              n.setResourceContent("data/" + resName + nS + ext, in, OFlag.NONE);
            }else
            {
              added.addFile(resName + nS + ext, in, srcFile);
            }
          }finally{
            in.close();
          }
        } else {
          throw new RuntimeException("Path not found or points to directory");
        }
        // if(!skip)
        return (attr?ResManager.ATTR_RES_VAR:ResManager.RES_VAR)+"/" + descrResName + nS + ext;
      } catch (Exception e) {
        RLogger.logError(e);
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return null;
  }
  
  private static String decodeDrive(String urlEncode) {
    int ind = urlEncode.indexOf("%3A");
    if(ind == 2 || ind == 3)
      return urlEncode.substring(0,ind)+":"+urlEncode.substring(ind+3);
    return urlEncode;
  }

  private static String clearNonCD(String ext) {
    int i = 0;
    while(i<ext.length() && Character.isLetterOrDigit(ext.charAt(i)))
      i++;
    if(i > 0)
      return ext.substring(0, i);
    return ext;
  }

  private static String extractHash(File temp) {
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(temp); 
      String s = DigestUtils.md5Hex(fis);
      return s;
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      RLogger.logError(e);
    }finally{
      if(fis!=null)
        try {
          fis.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          RLogger.logError(e);
        }
    }
    return UUID.randomUUID().toString();
  }

  public static String updateImages(URI dpath, String descr, IRelatedFilesProvider added, boolean attr){
    return updateImages(dpath, descr, added, new IPathHandler(){
    
      @Override
      public String handlePath(URI dpath, String src, TreeNode n, IRelatedFilesProvider added) {
        return handleImage(dpath, src, n, added, attr);
      }
    });
  }
  private static int handlePath(URI dpath,IPathHandler hndler, IRelatedFilesProvider added,String descr,int min,
      Set<String> toDel, StringBuffer ret,Pair<Integer, String> path){
    if(path!=null){
      try{
        String np = hndler.handlePath(dpath, path.getSecond().toString(), null, added);
        if (np != null && path.getFirst() > -1) {
          ret.append(descr.substring(min, path.getFirst()));
          ret.append(np);
          min = path.getFirst()+path.getSecond().length();
          
          try {
            int pos = np.indexOf(ResManager.RES_VAR);
            if (pos > -1) toDel.remove(URLDecoder.decode(np.substring(pos + ResManager.RES_VAR.length()), "UTF-8"));
            pos = np.indexOf(ResManager.ATTR_RES_VAR);
            if (pos > -1) toDel.remove(URLDecoder.decode(np.substring(pos + ResManager.ATTR_RES_VAR.length()), "UTF-8"));
   
          } catch (Exception e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        } else {
          // ret.append(path);
        }
      }catch(Exception e){
        RLogger.logError(e);
      }
    }
    return min;
  }

  public static String updateImages(URI dpath, String descr, IRelatedFilesProvider added,
      IPathHandler hndler) {
    if (dpath == null||descr == null) return descr; // no path no replace
    // Matcher matcher = patternImages.matcher(descr);
    Set<String> toDel = new HashSet<>();
//    if (n!=null)
//      for (String s : n.listResources())
//        if (s.startsWith("/"+attrDescription+"/")) toDel.add(s.substring(s.indexOf("/", 2) + 1));
    StringBuffer ret = new StringBuffer();
    int im = descr.indexOf("<img"),im2=descr.indexOf("<object"), lim = 0; // ,im2=descr.indexOf("<object");
    while (im > -1 || im2 > -1) {
      boolean isImg = true;
      int min = im;
      if(im2!=-1 && (im==-1||im2<im))
      {
        isImg = false;
        min = im2;
        
      }
      ret.append(descr.substring(lim, min));
      int end = descr.indexOf(">", min);
      if (end == -1) break; // not ended one
      //int src = descr.indexOf("src", im);
      //if (src <= end) {
      int hndl = min + (isImg?4:7);
      Map<String, Pair<Integer, String>> params = gatherParams(descr,hndl,end);
      Pair<Integer, String> path = params.get(isImg?"src":"data"),apath = null;
      if(!isImg){
        Pair<Integer, String> type = params.get("type");
        if(type !=null && type.getFirst()>-1 && !type.getSecond().startsWith("image")){
          im2=descr.indexOf("<object",end+1);
          continue;
        }
      }else
        apath = params.get("alt");
      {
        if(apath!=null && path!=null && apath.getFirst()<path.getFirst()){
          min = handlePath(dpath ,hndler, added,descr,min, toDel, ret, apath);
          min = handlePath(dpath ,hndler, added,descr,min, toDel, ret, path);
        }else{
          min = handlePath(dpath ,hndler, added,descr,min, toDel, ret, path);
          min = handlePath(dpath ,hndler, added,descr,min, toDel, ret, apath);
        }
//        if(apath!=null){
//          String np = hndler.handlePath(dpath, apath.getSecond().toString(), null, added);
//          if (np != null && apath.getFirst() > -1) {
//            ret.append(descr.substring(min, apath.getFirst()));
//            ret.append(np);
//            min = apath.getFirst()+apath.getSecond().length();
//            
//            try {
//              int pos = np.indexOf("{node.resURL}");
//              if (pos > -1) toDel.remove(URLDecoder.decode(np.substring(pos + 14), "UTF-8"));
//            } catch (Exception e) {
//              // TODO Auto-generated catch block
//              RLogger.logError(e);
//            }
//          } else {
//            // ret.append(path);
//          }
//        }
        lim = min; // TODO to end
      }
      if(isImg)
        im = descr.indexOf("<img",end);//TODO more proper
      else
        im2 = descr.indexOf("<object",end);
    }
//    if(n!=null)
//      for (String s : toDel) {
//        // TODO delete or stay?
//  
//  //      n.deleteResource(s);
//      }
    // while (matcher.find()) {
    // if(matcher.group(1).equals("img") && matcher.group(2).equals("src")
    // ||matcher.group(1).equals("object") &&
    // matcher.group(2).equals("data")){
    // }
    // }
    if (lim > -1) ret.append(descr.substring(lim));
    return ret.toString();
  }

  public static Map<String, Pair<Integer, String>> gatherParams(String descr, int hndl, int end) {
    Map<String,Pair<Integer, String>> params = new HashMap<>();
    int state = -1;
    StringBuilder name = new StringBuilder(), value=new StringBuilder();
    int ps = -1, valuestart = -1;
    while (hndl < end) {
      char c = descr.charAt(hndl);
      if((descr.charAt(hndl) == '\'' || descr.charAt(hndl) == '"')){
        if(value.toString().trim().length()==0)
          value.setLength(0);
        if(state == -1)
          state = 0;//is that valid to w3c?
        if(ps != -1){
          ps = -1;
          if(state == 1)
            state++;
        }else{
          ps = hndl;
          valuestart = -1;
        }
      }else
        if(state == 0 && c=='='){
          state++;
        }else
          if(!Character.isWhitespace(c)){
            if(state == -1)
              state = 0;
            if(state == 0)
              name.append(c);
            else
              if(state == 1){
                if(valuestart==-1)
                  valuestart = hndl;
                value.append(c);
              }
          }
          else
            if(Character.isWhitespace(c)){
              if(state == 1){
                if(ps==-1 && value.length()>0)
                  state++;
                else{
                  if(valuestart == -1)
                    valuestart = hndl;
                  value.append(c);
                }
              }
            }
        if(state == 2){
          state = 0;
          params.put(name.toString(), new Pair<>(valuestart,value.toString()));
          name.setLength(0);
          valuestart = -1;
          value.setLength(0);
        }
        hndl++;
    }
    return params;
  }

  public static String resolveRelativeImagesPath(String descr, TreeNode n) {
    if (!(n instanceof Location)) {
      return descr;
    }
    n = n.getWorkParent();
    if (!(n instanceof Document)) {
      return descr;
    }
    Matcher matcher = patternImages.matcher(descr);
    // TODO look;
    while (matcher.find()) {
      if (matcher.group(1).equals("img") && matcher.group(2).equals("src")
          || matcher.group(1).equals("object") && matcher.group(2).equals("data")) {
        String src = matcher.group(4);
        String pathToImage = src;
        pathToImage = pathToImage.replace("\\\\", "/");
        pathToImage = n.getNodeResourcesPath() + "/" + pathToImage;
        try {
          if (new File(URI.create(pathToImage)).exists()) descr = descr.replace(src, pathToImage);
        } catch (Exception e) {
          // for tab images
        }
      }
    }
    return descr;
  }

  public static void deleteOldImages(Attribute attr, String descr, TreeNode n) {
    if (attr == null || attr.getType() != AttributeType.STRING) {
      return;
    }
    deleteOldImages((String) attr.getValue(), descr, n, "", attr.getKey());
  }

  public static void deleteOldImages(String oldDescr, String descr, TreeNode n, String imgpref) {
    deleteOldImages(oldDescr, descr, n, imgpref, null);
  }

  public static void deleteOldImages(
      String oldDescr, String descr, TreeNode n, String imgpref, String ignorekey) {
    // get old images list
    Matcher matcher = patternImages.matcher(oldDescr);
    List<String> oldImages = new ArrayList<>();
    while (matcher.find()) {
      if (matcher.group(1).equals("img") && matcher.group(2).equals("src")
          || matcher.group(1).equals("object") && matcher.group(2).equals("data")) {
        String src = matcher.group(4);
        if (src.startsWith("data:image")) continue;
        String s = imgpref + src;
        if (n != null) s = src.replace(ResManager.RES_VAR, n.getNodeResourcesPath().toString());
        //TODO attr noderes
        try {
          boolean sidefile = new File(URI.create(s)).exists();
          // TODO image handling in console mode
          // if(Activator.getDefault()!=null && sidefile){
          if (sidefile) src = s;
          File f = new File(src);
          sidefile = f.exists();
          oldImages.add(f.toURI().normalize().toString());
          // }
        } catch (Exception e) {
          RLogger.logError(e);
          // for tab images
        }
      }
    }
    // get new images list
    List<String> newImages = new ArrayList<>();
    if (descr != null) {
      matcher = patternImages.matcher(descr);
      while (matcher.find()) {
        if (matcher.group(1).equals("img") && matcher.group(2).equals("src")
            || matcher.group(1).equals("object") && matcher.group(2).equals("data")) {
          String ss = matcher.group(4);
          newImages.add(URI.create(ss).normalize().toString());
        }
      }
    }
    URI specPath = null;
    // try to find a picture in string variables
    if (n != null) {
      for (String key : n.attrs().getLocalAttributeKeys()) {
        Object o = n.attrs().getAttributeValue(key);
        if (o == null || !(o instanceof String)) continue;
        String s = (String) o;
        if (key.equals(ignorekey)) continue;
        matcher = patternImages.matcher(s);
        while (matcher.find()) {
          if (matcher.group(1).equals("img") && matcher.group(2).equals("src")
              || matcher.group(1).equals("object") && matcher.group(2).equals("data"))
            try {
              String ss =
                  new URL(n.processResourcesPath(key, matcher.group(4),false)).toURI().normalize().toString();
              newImages.add(ss);
            } catch (MalformedURLException e) {
              // TODO Auto-generated catch block
              RLogger.logError(e);
            } catch (URISyntaxException e) {
              // TODO Auto-generated catch block
              RLogger.logError(e);
            }
        }
      }
      specPath = n.getNodeResourcesPath();
      if (specPath != null) {}
    }
    // remove duplicate items
    for (String newImg : newImages) {
      oldImages.remove(newImg);
    }
    // rest items - elements to delete
    for (String oldImg : oldImages) {
      // oldImg = oldImg.replaceAll("\\\\", "/");
      if (new File(oldImg).exists()) {
        if (n != null) {

          boolean inpath = specPath == null || oldImg.contains(specPath.toString());
          if (oldImg.contains("/") && inpath && new File(oldImg).exists()) {
            oldImg = oldImg.substring(oldImg.lastIndexOf('/') + 1);
            n.deleteResource(oldImg, OFlag.NONE);
          }
        } else {
          // here we suppose not to have an undo
          // on such operation

          File f = new File(oldImg);
          f.delete();
          if (f.getParentFile().list().length == 0) f.getParentFile().delete();
        }
      }
    }
  }

  public static String getReportDescription(
      TreeNode n,
      String key,
      String htmlFolderPath,
      String description,
      String prefix,
      String postfix,
      String basePath)
      throws UnsupportedEncodingException {
    if (description == null) return description;
    Matcher matcher = patternImages.matcher(description);//TODO maybe replace by dom
    int diff = 0;
    RevisionInfo ri = TreeStateManager.getOne(n.getTreeDB().getTracker()).getRevisionInfo(n.getTreeDB());
    //String adda = ri!=null?"/"+ri.getRevision().substring(0,6):"";//TODO look on it, strange
    if (htmlFolderPath != null)
      while (matcher.find()) {
        String tag = matcher.group(1);
        if (tag.equals("img") && matcher.group(2).equals("src")
            || tag.equals("object") && matcher.group(2).equals("data")) {
          String mg0 = matcher.group(0), src = matcher.group(4), newsrc = src, alt = null;
          if(mg0.indexOf("alt")>-1)
            alt = RequalityCLI.gatherParams(mg0, 4, mg0.length()).get("alt").getSecond();
          else
          if(!mg0.endsWith("/>"))//tends to be close tag
          {
            String fs = "</"+tag+">";
            int ct = description.indexOf(fs,matcher.end()+1+diff);//TODO rewrite
            if(ct>-1)
            {
              mg0 = description.substring(matcher.start()+diff, ct+fs.length());
              alt = description.substring(matcher.end()+diff, ct).replaceAll("[\\v\\s]+","");
            }
          }
          File f = locateFile(n, src, key, htmlFolderPath);
          if (f == null) continue;
          String path = f.toURI().toString();
          ;
          String newalt = alt;
          File fa = locateFile(n, alt, key, htmlFolderPath);
          String nodeId = getImageNodeId(n);
          if(fa!=null && alt!=null){
            String shorta =
                "images/" + nodeId+ "/" + alt.substring(alt.lastIndexOf("/") + 1);
            if(fa.exists()){
              try {
                File dest =
                    RequalityCLI.copyfile(null, fa.toURI(), URI.create(basePath + "/" + shorta));
                ;
                if (dest.exists()) newalt = shorta.replace("\\", "/");
              } catch (Exception ex) {
                ex.printStackTrace();
              }
            }
          }
          String shortPath =
              "images/"  + nodeId+  "/" + path.substring(path.lastIndexOf("/") + 1);
          if (!f.exists()) {
            try {
              File dest =
                  RequalityCLI.copyfile(null, f.toURI(), URI.create(basePath + "/" + shortPath));
              ;
              if (dest.exists()) newsrc = shortPath.replace("\\", "/");
            } catch (IOException ex) {
              System.err.println(ex.toString());
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          } else {
            try {
              String newPath = (basePath + "/" + shortPath); // .replace("%20","
              // ");
              File dest =
                  RequalityCLI.copyfile(null, f.getAbsoluteFile().toURI(), URI.create(newPath));
              if (dest.exists()) newsrc = shortPath;
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
          if (!src.equals(newsrc)){
            String newImg = toImg(mg0,src, newsrc,alt, newalt);
            description = description.replace(mg0, newImg);
            diff+=newImg.length()-mg0.length();
          }
        }
      }
    return description;
  }

  public static void iterateImages(
      TreeNode n,
      String description,
      String key, 
      String htmlFolderPath,
      IReqURIResolver iterables,
      boolean forceAll)
      throws UnsupportedEncodingException {
    if (description == null || iterables == null) return;
    Matcher matcher = patternImages.matcher(description);//TODO maybe replace by dom
    int diff = 0;
    RevisionInfo ri = TreeStateManager.getOne(n.getTreeDB().getTracker()).getRevisionInfo(n.getTreeDB());
    //String adda = ri!=null?"/"+ri.getRevision().substring(0,6):"";//TODO look on it, strange
    while (matcher.find()) {
      String tag = matcher.group(1);
      if (tag.equals("img") && matcher.group(2).equals("src")
          || tag.equals("object") && matcher.group(2).equals("data")) {
        String mg0 = matcher.group(0), src = matcher.group(4), newsrc = src, alt = null;
        if(mg0.indexOf("alt")>-1)
          alt = RequalityCLI.gatherParams(mg0, 4, mg0.length()).get("alt").getSecond();
        else
        if(!mg0.endsWith("/>"))//tends to be close tag
        {
          String fs = "</"+tag+">";
          int ct = description.indexOf(fs,matcher.end()+1+diff);//TODO rewrite
          if(ct>-1)
          {
            mg0 = description.substring(matcher.start()+diff, ct+fs.length());
            alt = description.substring(matcher.end()+diff, ct).replaceAll("[\\v\\s]+","");
          }
        }
        File f = locateFile(n, src, key, htmlFolderPath);//тут при вызове на отчете не находит ресурс. Нужно класть во временную папку отчета вместо постоянной чтобы видело?
        if (f == null) continue;
        String path = f.toURI().toString();
        ;
        String newalt = alt;
        File fa = locateFile(n, alt, key, htmlFolderPath);
        String nodeId = getImageNodeId(n);
        if(fa!=null && alt!=null){
          String shorta =
              "images/" + nodeId+ "/" + alt.substring(alt.lastIndexOf("/") + 1);
          if(fa.exists()){
            try {
              if (fa.exists()) newalt = shorta.replace("\\", "/");
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
        }
        String shortPath =
            "images/"  + nodeId+  "/" + path.substring(path.lastIndexOf("/") + 1);
        if (!f.exists()) {
          try {
            if (f.exists()) newsrc = shortPath.replace("\\", "/");
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        } else {
          try {
            if (f.exists()) newsrc = shortPath;
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
        if (!src.equals(newsrc) || forceAll){
          //reuse interface to show info about images
          iterables.resolve(n, src, f.toURI().toString());
        }
      }
    }
  }

  public static String toImg(String mg0, String src, String newsrc, String alt, String newalt) {
    if(!mg0.startsWith("<img"))
      RLogger.logDebug("object during handling of "+src, 8);
//    {
//      Map<String,Pair<Integer,String>> params = gatherParams(mg0, 7, mg0.length()-1);
//      StringBuilder im = new StringBuilder();
//      im.append("<img");
//      if(alt!=null){
//        im.append(" alt=\"");
//        im.append(alt);
//        im.append("\"");
//      }
//      for(String k:params.keySet()){
//        if(k.equals("type"))
//          continue;
//        if(k.equals("data")){
//          im.append(" src=\"");
//          im.append(newsrc);
//          im.append("\"");
//        }else{
//          im.append(" ");
//          im.append(k);
//          im.append("=\"");
//          im.append(params.get(k).getSecond());
//          im.append("\"");
//        }
//      }
//      im.append("/>");
//      return im.toString();
//    }
    String ret = mg0.replace(src, newsrc);
    if(alt!=null)
      ret = ret.replace(alt, newalt);//TODO no alt but newalt
    return ret;
  }

  private static File locateFile(TreeNode n, String src, String srcKey, String htmlFolderPath) {
    if(src == null || src.isEmpty())
      return null;
    String path = n.processResourcesPath(srcKey,src,false);
    String nrp = n.getNodeResourcesPath().toString();
    if (path.startsWith("data:image")) return null;
    if (path.startsWith("images"))
    {
      return new File(URI.create(htmlFolderPath+"/"+path));
    }  
    if(!path.startsWith(nrp)){
      if (path.startsWith("file:")) path = path.substring(5);
      for (int i = 0; i < Math.min(10, path.length()); i++)
        if (isWindowsLocalPath(path, i)) {
          path = path.substring(i);
          break;
        }
      path = "file:/"+path;
      //return new File(URI.create(path));
    }
    try{
      int i = 0;
      while (path.length() > (i + 2) && path.charAt(i) == '/' && path.charAt(i + 1) == '/') i++;
      path = path.substring(i);
      // path = path.replace("/", File.separator);
      // path = path.replace("\\", File.separator);
      File f = new File(URI.create(path)),
          alt = new File(URI.create(htmlFolderPath + path));
      if (!f.exists() && alt.exists()) {
  
        f = alt;
      }
      if (!f.exists()) {
        TreeNode cp = n.getCloneParent();
        if (cp != null) return locateFile(cp, src,srcKey, htmlFolderPath);
      }
      return f;
    }catch(Exception e){
      
    }
    return null;
  }

  public static void setFileContent(File file, String content) {
    try {
      FileWriter fw = new FileWriter(file);
      fw.write(content);
      fw.close();
    } catch (IOException e) {
      RLogger.logError(e);
    }
  }

  public static void duplicateImages(TreeNode node, String path) {
    URI spath = node.getNodeResourcesPath();
    if (spath == null) return;
    // if(spath.startsWith("file:"))spath = spath.substring(5);
    // if(path.startsWith("file:"))path = path.substring(5);
    File f = new File(spath);
    if (f.exists())
      for (File f2 : f.listFiles())
        if (f2.isDirectory()) {
          File dest = new File(URI.create(path + "/" + f2.getName()));
          cloneDir(f2, dest);
        }
  }

  public static String getNextId(NodeDesc parent, String type, String defaultPrefix) {
    String newId = "00";
    String fullNewId = "00";
    boolean found = false;

    for (int i = 0; i < parent.getChildrenUUId().size(); i++) {
      NodeDesc child = parent.getChildDesc(parent.getChildrenUUId().get(i));
      if (child.getType().equals(type) || type == null) {
        String number = getNumberStringFromId(child.getId());
        if (number == null) {
          continue;
        }
        try {
          if (Integer.parseInt(number) >= Integer.parseInt(newId)) {
            fullNewId = child.getId();
            newId = number;
            found = true;
          }
        } catch (NumberFormatException e) {
        }
      }
    }

    if (!found && defaultPrefix != null) {
      fullNewId = defaultPrefix + fullNewId;
    }
    boolean stop = false;
    do {
      fullNewId = incLastNumberInString(fullNewId);
      List<UUID> uids = parent.getChildrenUUId();
      stop = true;
      for (UUID uid : uids) if (parent.getChildDesc(uid).getId().equals(fullNewId)) stop = false;
    } while (!stop);
    return fullNewId;
  }

  public static String detectPrefix(TreeNode par, String type, boolean filter) {
    if(type == null)
      return "";
    switch(type) {
      case TestPurpose.TYPE_NAME:
      {
        String pid = par.getNameOrId() + "_T";
        if(filter)
          pid = CoreUtils.filterNodeId(pid);
        return pid;
      }
      case Comment.TYPE_NAME:
        return Comment.ID_PREFIX;
      case DocFolder.TYPE_NAME:
        return DocFolder.ID_PREFIX;
      case ReportFolder.TYPE_NAME:
        return ReportFolder.ID_PREFIX;
    }
    return "";
  }

  public static String getImageNodeId(TreeNode n) {
    return n.getSUUId()+(n.getTreeDB().revision!=null?n.getTreeDB().revision.getDate().getTime():"");
  }
}
