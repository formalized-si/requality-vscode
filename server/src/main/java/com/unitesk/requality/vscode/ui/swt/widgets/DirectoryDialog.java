package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;

/** {@code org.eclipse.swt.widgets.DirectoryDialog}: the native VS Code folder picker. */
public class DirectoryDialog extends Dialog {
  private String filterPath = "";
  private String message = "";

  public DirectoryDialog(Shell parent) {
    this(parent, 0);
  }

  public DirectoryDialog(Shell parent, int style) {
    super(parent, style);
  }

  public void setFilterPath(String path) {
    filterPath = path == null ? "" : path;
  }

  public String getFilterPath() {
    return filterPath;
  }

  public void setMessage(String message) {
    this.message = message == null ? "" : message;
  }

  public String getMessage() {
    return message;
  }

  public String open() {
    JsonObject p = new JsonObject();
    p.addProperty("title", getText().isEmpty() ? message : getText());
    p.addProperty("path", filterPath);
    JsonElement r = ServerContext.client().call("client/directoryDialog", p);
    if (r == null || r.isJsonNull())
      return null;
    filterPath = r.getAsString();
    return filterPath;
  }
}
