package com.unitesk.requality.vscode.ui.swt.dnd;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;

/**
 * {@code org.eclipse.swt.dnd.Clipboard}: the system clipboard of the VS Code host. Text goes to the VS Code clipboard;
 * the other transfer types (resources, file lists) cannot, so the server keeps them along with the text they were
 * put with: they are on the clipboard as long as the VS Code clipboard still holds that text (copying anything else
 * replaces them, as on a system clipboard).
 */
public class Clipboard {
  private static final Object LOCK = new Object();
  private static java.util.Map<Transfer, Object> typed = new java.util.HashMap<>();
  private static String typedText;
  private boolean disposed;

  public Clipboard(Object display) {
  }

  public void setContents(Object[] data, Transfer[] dataTypes) {
    if (data == null || data.length == 0)
      return;
    String text = null;
    java.util.Map<Transfer, Object> other = new java.util.HashMap<>();
    for (int i = 0; i < data.length; i++) {
      Transfer t = dataTypes == null || i >= dataTypes.length ? null : dataTypes[i];
      if (data[i] instanceof String && (t == null || t instanceof TextTransfer || t instanceof HTMLTransfer)) {
        if (text == null)
          text = (String) data[i];
      } else if (t != null && data[i] != null) {
        other.put(t, data[i]);
      }
    }
    synchronized (LOCK) {
      typed = other;
      typedText = text == null ? "" : text;
    }
    JsonObject p = new JsonObject();
    p.addProperty("text", text == null ? "" : text);
    ServerContext.client().notify("client/clipboard", p);
  }

  public void setContents(Object[] data, Transfer[] dataTypes, int clipboards) {
    setContents(data, dataTypes);
  }

  public Object getContents(Transfer transfer) {
    JsonElement r = ServerContext.client().call("client/readClipboard", new JsonObject());
    String text = r == null || r.isJsonNull() ? null : r.getAsString();
    if (transfer instanceof TextTransfer)
      return text;
    synchronized (LOCK) {
      if (typedText == null || !typedText.equals(text == null ? "" : text))
        return null;
      return typed.get(transfer);
    }
  }

  public void clearContents() {
    setContents(new Object[] { "" }, new Transfer[] { TextTransfer.getInstance() });
  }

  public void dispose() {
    disposed = true;
  }

  public boolean isDisposed() {
    return disposed;
  }
}
