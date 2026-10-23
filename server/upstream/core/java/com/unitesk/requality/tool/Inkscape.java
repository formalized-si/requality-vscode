package com.unitesk.requality.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class Inkscape {
  private static Inkscape cli;
  private String oscmd, librepath, librenew = null;
  private Runnable saveP;

  public static void init(String path, Runnable savePred) {
    cli = new Inkscape(path, savePred);
  }

  public static Inkscape getDefault() {
    return cli;
  }

  public Inkscape(String path, Runnable savePred) {
    librepath = path;
    oscmd = "bash";
    if (System.getProperty("os.name").contains("Windows")) oscmd = "cmd";
    saveP = savePred;
  }

  public String getInfo(String path) {
    return handleCommand("identify", "\"" + path + "\"");
  }

  public void convertToWMF(String path, String where) {
    handleCommand("inkscape", "--export-wmf=\"" + where + "\"", "\"" + path + "\"");
  }

  public String handleCommand(String mcom, String... commands) {
    Runtime r = Runtime.getRuntime();
    String lpath = librenew;
    if (librenew == null) lpath = librepath;
    if (lpath == null || lpath.isEmpty()) lpath = "./im";
    lpath = lpath + "/" + mcom;
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
        p = r.exec("\"" + lpath + "\" " + sb.toString());
      } else {
        String[] pars = new String[commands.length + 2];
        for (int i = 0; i < commands.length; i++) pars[i + 2] = commands[i];
        // pars[0] = "cmd";
        // pars[1] = "start";
        // pars[2] = "/wait";
        pars[0] = "\"" + lpath + "\"";
        // pb = new ProcessBuilder(pars);
        p = r.exec(new String[] {oscmd, "-c", "\"" + lpath + "\" -- " + sb.toString()});
      }
      try {
        p.waitFor();
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      // Process p = pb.start();//.waitFor();
      InputStream is = p.getInputStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
      StringBuffer sb2 = new StringBuffer();
      while (br.ready()) sb2.append(br.readLine());
      br.close();
      saveP.run(); // succed - then save;
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
}
