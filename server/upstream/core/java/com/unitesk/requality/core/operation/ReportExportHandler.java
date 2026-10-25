package com.unitesk.requality.core.operation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.htmlcleaner.TagNode;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class ReportExportHandler implements IReportExportOperation {

  @Override
  public void exportReport(Collection<TreeNode> nodes, String exportDir, boolean sepFolder) {
    boolean manyNodes = nodes.size() != 1;
    for (TreeNode node : nodes) {
      if (node.attrs().getAttribute(ReportData.ATTR_LAST_GENERATED) == null) return;
      if (node.getType().equals(ReportData.TYPE_NAME)) {
        if (!((ReportData) node).reportExists()) return;
      }
    }
    for (TreeNode node : nodes) {
      ReportData report = null;
      if (node instanceof ReportData) {
        report = (ReportData) node;
        exportReport(report, manyNodes ? node.getId() : null, new File(exportDir), sepFolder);
      }
    }

  }
  private RLogger log = RLogger.loggerS.getLogger();
  private static SimpleDateFormat ft = new SimpleDateFormat("yyyy.MM.dd HH-mm-ss");

  private void exportReport(ReportData node, String folder, File exportFolder, boolean sepF) {
    URI href = null;
    try {
      URI savePath = node.getSaveDir();
      //String filename = node.attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
      Date dNow = new Date();
      File repfolder = null;
      if(!sepF){
        repfolder = exportFolder;
        if(!exportFolder.exists())
          exportFolder.mkdirs();
      }else{
        repfolder = 
            new File( URI.create(
                exportFolder.toURI().toString()
                    + "/"
                    + CoreUtils.URLEncode(node.getId())));
        int num = 1;
        while(repfolder.exists()){
          repfolder = new File(URI.create(
              exportFolder.toURI().toString()
              + "/"
              + CoreUtils.URLEncode(node.getId())+"("+num+")"));
          num++;
        }
      }
      File ft = new File(savePath);
      handleRepFolder(ft, repfolder.toURI());
    } catch (Exception e) {
      handleError("Exception during export", e);
    }
  }


  private void handleRepFolder(File repfolder, URI savePath) {
    File altF = new File(savePath);
    if(!altF.exists())
      altF.mkdirs();
    for(File f:repfolder.listFiles())
    {
      String name = f.getName();
      try{
        File f2 = altF.toPath().resolve(name).toFile();
        if(f.isDirectory()){
          handleRepFolder(f, f2.toURI());
        }else
          try{
            handleHref(repfolder, name, savePath);
          } catch(IOException ioe){
            handleError("Exception during export", ioe);
          } catch (URISyntaxException e) {
            // TODO Auto-generated catch block
            handleError("Exception during export", e);
          }
      }
      catch(Exception e){
        handleError("Exception during export", e);
      }
    }
  }

  private void handleHref(File repfolder, String filename, URI savePath) throws MalformedURLException, IOException, URISyntaxException {
    handleHref(repfolder, filename, savePath, new HashSet<>());
  }

  /**
   * VSCODE-PATCH: true when the file is in the folder. An image path of a description with "../" must not read a file
   * outside the report or write one outside the export folder.
   */
  private static boolean inside(URI folder, URI file) {
    try {
      return java.nio.file.Paths.get(file).normalize().startsWith(java.nio.file.Paths.get(folder).normalize());
    } catch (RuntimeException e) {
      return false;
    }
  }
  private void handleHref(File repfolder, String filename, URI savePath,Set<String> washere) throws MalformedURLException, IOException, URISyntaxException {
    String fn = filename.toLowerCase();
    if(!fn.endsWith("htm") && !fn.endsWith("html")){
      String sname = CoreUtils.URLEncode(filename);
      URI from = URI.create(repfolder.toURI()+"/"+sname), to = URI.create(savePath+"/"+sname);
      if(inside(repfolder.toURI(), from) && inside(savePath, to))
        RequalityCLI.copyfile(from, to);
      return;
    }
    if(washere.contains(filename))
      return;
    washere.add(filename);
    URI href = URI.create(repfolder.toURI().toString() + CoreUtils.URLEncode(filename));
    if(!new File(href).exists())
      throw new FileNotFoundException("File "+href+" was not found!");
    RequalityCLI.copyfile(
      null,
      href,
      URI.create(savePath.toString() + "/" + CoreUtils.URLEncode(filename)),
      true);
    String content = RequalityCLI.getFileContent(new File(href));
    boolean changes = false;
    Matcher imgs = RequalityCLI.getImagePattern().matcher(content);//TODO mb remove
    int diff = 0;
    while (imgs.find()) {
      String tag = imgs.group(1);
      try{
        if (tag.equals("img") && imgs.group(2).equals("src")
            || tag.equals("object") && imgs.group(2).equals("data")) {
          String src = CoreUtils.URLEncode(imgs.group(4));
          if (src.startsWith("data:image")) continue;
          try {
            URI from=new URI(repfolder.toURI() + "/" + CoreUtils.URLEncode(src)), 
                to = new URI(savePath.toURL() + "/" + CoreUtils.URLEncode(src));
            if(!inside(repfolder.toURI(), from) || !inside(savePath, to))
              continue;
            File f =
                RequalityCLI.copyfile(
                    null,
                    from,
                    to,
                    true);
            if (!from.equals(to)) {
              String mg0 = imgs.group(0);
              String alt = null;
              if(mg0.indexOf("alt")>-1)
                alt = RequalityCLI.gatherParams(mg0, 4, mg0.length()).get("alt").getSecond();
              else
              if(!mg0.endsWith("/>"))//tends to be close tag
              {
                  String fs = "</"+tag+">";
                  int ct = content.indexOf(fs,imgs.end()+diff+1);//TODO rewrite
                  if(ct>-1)
                  {
                      mg0 = content.substring(imgs.start()+diff, ct+fs.length());
                      alt = content.substring(imgs.end()+diff, ct).replaceAll("[\\v\\s]+","");
                  }
              }
              String newalt = alt;
              if(alt!=null){
                File fa = new File(new URI(savePath + "/" + CoreUtils.URLEncode(alt)));
                if(fa.exists() && inside(savePath, fa.toURI())
                    && inside(repfolder.toURI(), URI.create(repfolder.toURI().toURL() + "/" + CoreUtils.URLEncode(alt)))){
                  String shorta =
                      alt;//"images/" + alt.substring(alt.lastIndexOf("/") + 1);
                  try {
                    File dest =
                        RequalityCLI.copyfile(null, fa.toURI(), 
                            URI.create(repfolder.toURI().toURL() + "/" + CoreUtils.URLEncode(alt)));
                    ;
                    if (dest.exists()) newalt = shorta.replace("\\", "/");
                  } catch (Exception ex) {
                    ex.printStackTrace();
                  }
                }
              }
              String newIm = RequalityCLI.toImg(imgs.group(0),CoreUtils.URLEncode(filename), f.getName(),alt,newalt);
              content =
                  content.replace(
                      mg0, newIm);
              diff += newIm.length()-mg0.length();
              changes = true;
            }
          } catch (IOException ioe) {
            //image cannot be copied
          }
        }
      }catch(Exception e){
        handleError("Incorrect image on export", e);
      }
    }
    if (changes) RequalityCLI.setFileContent(new File(href), content);
  }

  private static void recLook(TagNode document, Set<String> addfiles) {
    List<TagNode> nl = document.getChildren();
    for(TagNode tn:nl)
      recLook(tn, addfiles);
    Map<String, String> nnm = document.getAttributes();
    if(nnm!=null){
      String n = nnm.get("href");
      if(n!=null){
        int hcode = n.indexOf("#"); 
        if(hcode>0){
          n = n.substring(0, hcode);
          if(n.startsWith("./"))
            n = n.substring(2);
          addfiles.add(n);
        }
      }
    }
  }


  @Override
  public void handleError(String string, Exception e) {
    String msg = "";
    if(!string.isEmpty())
      msg = string;
    msg+="\n"+CoreUtils.getTrace(e);
    log.lE(msg);
  }

  private static IReportExportOperation one = new ReportExportHandler();
  public ReportExportHandler(){//TODO
  }

  public static IReportExportOperation getOne() {
    return one;
  }


  @Override
  public void setLogger(RLogger ls) {
    log = ls;
  }
}
