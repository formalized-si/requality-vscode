package com.unitesk.requality.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;

import com.unitesk.requality.core.log.RLogger;

public class IMMagic {
  private static IMMagic cli;
  private String oscmd, librepath, librenew = null;
  private Runnable saveP;

  public static void init(String path, Runnable savePred) {
    cli = new IMMagic(path, savePred);
  }

  public static IMMagic getDefault() {
    return cli;
  }

  public IMMagic(String path, Runnable savePred) {
    librepath = path;
    oscmd = "bash";
    if (System.getProperty("os.name").contains("Windows")) oscmd = "cmd";
    saveP = savePred;
  }

  public String getInfo(String path) {
    return handleCommand("identify", "\"" + path + "\"");
  }

  public void convertToWMF(String path, String where) {
    File wh = new File(where);
    if (wh.getParentFile().exists()) wh.getParentFile().mkdirs();
    handleCommand("convert", "\"" + path + "\"", "\"" + where + "\"");
  }

  public void convertToSVG(String path, String where) {
    File wh = new File(where);
    if (wh.getParentFile().exists()) wh.getParentFile().mkdirs();
    handleCommand("convert", "\"" + path + "\"", "\"" + where + "\"");
  }

  public void convertToPNG(String path, String where) {
    File wh = new File(where);
    if (wh.getParentFile().exists()) wh.getParentFile().mkdirs();
    handleCommand("convert", "\"" + new File(URI.create(path)).getAbsolutePath() + "\"", "\"" + where + "\"");
  }

  public String handleCommand(String mcom, String... commands) {
    Runtime r = Runtime.getRuntime();
    String lpath = librenew;
    if (librenew == null) lpath = librepath;
    if (lpath == null || lpath.isEmpty() || lpath.equals("./im")) lpath = "";
    if(new File(lpath+File.separator+"magick").exists()||new File(lpath+File.separator+"magick.exe").exists())
      lpath = lpath +File.separator+ "magick";
    // lpath=lpath+File.separator+mcom;
    StringBuilder sb = new StringBuilder();
    for (String s : commands) {
      sb.append(s);
      sb.append(" ");
    }
    sb.setLength(sb.length() - 1);
    Process p = null;
    if(lpath.isEmpty()){
      lpath = mcom;
      mcom = null;
    }
    try {
      int add = (mcom==null?0:1)+1;
      if (oscmd.equals("cmd")) {
        String[] pars = new String[commands.length + add];
        for (int i = 0; i < commands.length; i++) pars[i + add] = commands[i];
        // pars[0] = "cmd";
        // pars[1] = "start";
        // pars[2] = "/wait";
        // pars[0] = ;
        // pb = new ProcessBuilder(pars);
        pars[0] = "\"" + lpath + "\"";
        if(mcom!=null)
          pars[1] = mcom;
        p = r.exec(pars);
      } else {
        String[] pars = new String[3];
        //for (int i = 0; i < commands.length; i++) pars[i + add+2] = commands[i];
        // pars[0] = "cmd";
        // pars[1] = "start";
        // pars[2] = "/wait";
        pars[0] = oscmd;
        pars[1] = "-lc";
        pars[2] = lpath+(mcom!=null?" "+mcom:"")+" "+sb.toString();//.replace("\"", "\\\"");
//        if(mcom!=null)
//          pars[3] = mcom;
        // pb = new ProcessBuilder(pars);
        p = r.exec(pars);
      }
      try {
        p.waitFor();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      StringBuffer sb2 = new StringBuffer();
      // Process p = pb.start();//.waitFor();
      try(InputStream is1 = p.getInputStream()){
        BufferedReader br = new BufferedReader(new InputStreamReader(is1, Charset.forName("UTF-8")));
        while (br.ready()) sb2.append(br.readLine());
        br.close();
      }
      try(InputStream is2 = p.getErrorStream()){
        BufferedReader br = new BufferedReader(new InputStreamReader(is2, Charset.forName("UTF-8")));
        while (br.ready()) sb2.append(br.readLine());
        br.close();
      }
      saveP.run(); // succed - then save;
      RLogger.logDebug("conversion of "+sb2.toString(),5 );
      return sb2.toString();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // p.waitFor();//not work on window, so...
    return null;
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

  public void crop(String ipath, String ipath2, int i, int j, int k, int l) {
    handleCommand(
        "convert",
        "\"" + ipath + "\"",
        "-crop",
        i + "x" + j + "+" + k + "+" + l,
        "\"" + ipath2 + "\"");
  }
}
