package com.unitesk.requality.tool.cli;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.locks.ReentrantLock;
import org.fit.cssbox.demo.PdfImageRenderer;
import org.fit.cssbox.demo.PdfImageRenderer.Type;
import com.unitesk.requality.core.log.RLogger;

public class LibreCLI {
  private static LibreCLI cli;
  private String oscmd, librepath, librenew = null;
  private ReentrantLock lock = new ReentrantLock();
  private Runnable saveP;

  public static void init(String path, Runnable savePred) {
    cli = new LibreCLI(path, savePred);
  }

  public static LibreCLI getDefault() {
    return cli;
  }

  public LibreCLI(String path, Runnable savePred) {
    librepath = path;
    oscmd = "bash";
    if (System.getProperty("os.name").contains("Windows")) oscmd = "cmd";
    saveP = savePred;
  }

  public void convertToXHTML(String path, String where) {
    handleCommand(true,
        true,
        "--convert-to",
        "\"xhtml:XHTML Writer File\"",
        "--outdir",
        "\"" + where + "\"",
        "\"" + path + "\"",
        "--headless",
        "--norestore",
        "--nolockcheck");
  }

  public void convertHTMLToPNG(String uri, String where) {
    Runtime r = Runtime.getRuntime();
    String lpath = librenew;
    if (librenew == null) lpath = librepath;
    if (lpath == null || lpath.isEmpty()) lpath = "soffice";
    // new StringBuilder();
    // Runtime.getRuntime();
    Process p = null;
    try {
      // p=r.exec(new
      // String[]{"java","-jar",ww,uri,where,"png","print:1024x50"});
      // p.waitFor();
      PdfImageRenderer pdr = new PdfImageRenderer();
      pdr.setMediaType("png");
      pdr.setWindowSize(new Dimension(1024, 50), false);
      
      FileOutputStream os = new FileOutputStream(new File(URI.create(where)));
      pdr.renderURL(uri, os, Type.PNG, "A4");
      os.close();
      pdr = new PdfImageRenderer();
      pdr.setMediaType("svg");
      pdr.setWindowSize(new Dimension(1024, 50), false);
      
      os = new FileOutputStream(new File(URI.create(where+".svg")));
      pdr.renderURL(uri, os, Type.SVG, "A4");
      os.close();
      //WebVector.main(new String[] {'"'+uri+'"', '"'+where+'"', "png", "print:1024x50"});
      //WebVector.main(new String[] {'"'+uri+'"', '"'+where + ".svg"+'"', "svg", "print:1024x50"});
      // pb.start();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // handleCommand("--convert-to","\"png\"",
    // "--outdir","\""+where+"\"","\""+path+"\"","-resize","2000x1500","--headless","--norestore","--nolockcheck");
  }

  public void convertToWMF(String path, String where) {
    handleCommand(true,
        true,
        "--nodefault",
        "--headless",
        "--norestore",
        "--nolockcheck",
        "--convert-to",
        "\"wmf\"",
        "--outdir",
        "\"" + where + "\"",
        "\"" + path + "\"");
  }

  public void convertToDOC(String path, String where) {
    handleCommand(true,
        true,
        "--convert-to",
        "\"doc:MS Word 97\"",
        "--outdir",
        "\"" + where + "\"",
        "\"" + path + "\"",
        "--headless",
        "--norestore",
        "--nolockcheck");
  }

  public void convertToXLS(String path, String where) {
    handleCommand(true,
        true,
        "--convert-to",
        "\"xls:MS Excel 97\"",
        "--headless",
        "--calc",
        "--outdir",
        "\"" + where + "\"",
        "\"" + path + "\"");
  }

  public void convertToRTF(String path, String where) {
    handleCommand(true,
        true,
        "--convert-to",
        "\"rtf:Rich Text Format\"",
        "--writer",
        "--outdir",
        "\"" + where + "\"",
        "\"" + path + "\"",
        "--headless",
        "--norestore",
        "--nolockcheck");
  }

  public void handleCommand(boolean wait,boolean silent, String... commands) {
    lock.lock();
    Runtime r = Runtime.getRuntime();
    String lpath = librenew;
    if (librenew == null) lpath = librepath;
    if (lpath == null || lpath.isEmpty()) lpath = "soffice";
    StringBuilder sb = new StringBuilder();
    for (String s : commands) {
      sb.append(s);
      sb.append(" ");
    }
    // Runtime.getRuntime();
    sb.setLength(sb.length() - 1);
    Process p = null;
    try {
      if (oscmd.equals("cmd")) {
        String[] pars = new String[commands.length + 1];
        for (int i = 0; i < commands.length; i++) pars[i + 1] = commands[i];
        // pars[0] = "cmd";
        // pars[1] = "start";
        // pars[2] = "/wait";
        // pars[0] = ;
        // pb = new ProcessBuilder(pars);
        pars[0] = "\"" + lpath + "\"";
        p = r.exec(pars);
      } else {
        String[] pars = null;
        if(silent){
          pars = new String[3];
          //for (int i = 0; i < commands.length; i++) pars[i + 4] = commands[i];
          // pars[0] = "cmd";
          // pars[1] = "start";
          // pars[2] = "/wait";
          pars[0] = oscmd;
          pars[1] = "-lc";
          pars[2] = "\""+lpath+"\" "+sb.toString();
        }else{
          pars = new String[1+commands.length];
          pars[0] = lpath;
          for (int i = 0; i < commands.length; i++) pars[i + 1] = commands[i];
        }
        // pb = new ProcessBuilder(pars);
        p = r.exec(pars);
      }
      if (wait) p.waitFor();
      InputStream is = p.getInputStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
      StringBuffer sb2 = new StringBuffer();
      while (br.ready()){
        sb2.append(br.readLine());
        sb2.append("\n");
      }
      br.close();
      br = new BufferedReader(new InputStreamReader(p.getErrorStream(), Charset.forName("UTF-8")));
      while (br.ready()){ 
         sb2.append(br.readLine());
         sb.append("\n");
      }
      br.close();
      RLogger.logInfo(sb2);
      // pb.start();
      saveP.run(); // succed - then save;
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      lock.unlock();
    }
    // p.waitFor();//not work on window, so...

  }

  public void saveSettings() {
    if (librenew != null) librepath = librenew;
    saveP.run();
  }

  public String getPath() {
    return librepath;
  }

  public void setTempPath(String librepath2) {
    librenew = librepath2;
  }

  public void setPath(String val) {
    librepath = librenew = val;
  }

  public void convertToODT(String path, String where) {
    handleCommand(true,
        true,
        "--convert-to",
        "\"odt\"",
        "--outdir",
        "\"" + where + "\"",
        "\"" + path + "\"",
        "--headless",
        "--norestore",
        "--nolockcheck");
  }
}
