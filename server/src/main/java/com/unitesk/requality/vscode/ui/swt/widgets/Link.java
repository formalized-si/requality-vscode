package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.Link}: text with {@code <a href="...">} anchors; Selection carries the href. */
public class Link extends Control {
  private String text = "";

  public Link(Composite parent, int style) {
    super(parent, style, "link");
    created();
  }

  public void setText(String text) {
    if (text == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    this.text = text;
    set("text", this.text);
  }

  public String getText() {
    return text;
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  public void removeSelectionListener(SelectionListener l) {
    removeTyped(SWT.Selection, l);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("selection".equals(type)) {
      Event e = new Event();
      e.text = d.has("href") ? d.get("href").getAsString() : "";
      notifyListeners(SWT.Selection, e);
      return;
    }
    super.onClientEvent(type, d);
  }
}
