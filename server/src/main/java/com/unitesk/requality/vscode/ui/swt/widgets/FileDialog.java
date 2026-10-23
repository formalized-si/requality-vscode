package com.unitesk.requality.vscode.ui.swt.widgets;

import java.io.File;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.swt.widgets.FileDialog}: the native VS Code open/save dialog. */
public class FileDialog extends Dialog {
  private String[] filterExtensions = new String[0];
  private String[] filterNames = new String[0];
  private String filterPath = "";
  private String fileName = "";
  private String[] fileNames = new String[0];
  private boolean overwrite;

  public FileDialog(Shell parent) {
    this(parent, SWT.OPEN);
  }

  public FileDialog(Shell parent, int style) {
    super(parent, style);
  }

  public void setFilterExtensions(String[] ext) {
    filterExtensions = ext == null ? new String[0] : ext;
  }

  public String[] getFilterExtensions() {
    return filterExtensions;
  }

  public void setFilterNames(String[] names) {
    filterNames = names == null ? new String[0] : names;
  }

  public void setFilterPath(String path) {
    filterPath = path == null ? "" : path;
  }

  public String getFilterPath() {
    return filterPath;
  }

  public void setFileName(String name) {
    fileName = name == null ? "" : name;
  }

  public String getFileName() {
    return fileName;
  }

  public String[] getFileNames() {
    return fileNames;
  }

  public void setOverwrite(boolean b) {
    overwrite = b;
  }

  /** Returns the chosen path or null when cancelled. */
  public String open() {
    JsonObject p = new JsonObject();
    p.addProperty("mode", SWT.has(getStyle(), SWT.SAVE) ? "save" : "open");
    p.addProperty("multi", SWT.has(getStyle(), SWT.MULTI));
    p.addProperty("title", getText());
    p.add("extensions", JsonRpcConnection.toJson(filterExtensions));
    p.add("names", JsonRpcConnection.toJson(filterNames));
    p.addProperty("path", filterPath);
    p.addProperty("fileName", fileName);
    JsonElement r = ServerContext.client().call("client/fileDialog", p);
    if (r == null || r.isJsonNull())
      return null;
    JsonArray arr = r.getAsJsonArray();
    if (arr.size() == 0)
      return null;
    String first = arr.get(0).getAsString();
    File f = new File(first);
    filterPath = f.getParent() == null ? "" : f.getParent();
    fileName = f.getName();
    fileNames = new String[arr.size()];
    for (int i = 0; i < arr.size(); i++)
      fileNames[i] = new File(arr.get(i).getAsString()).getName();
    return first;
  }
}
