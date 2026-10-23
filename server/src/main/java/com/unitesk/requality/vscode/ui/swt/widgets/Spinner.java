package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.Spinner}. */
public class Spinner extends Composite {
  private int selection, minimum, maximum = 100, increment = 1, digits;

  public Spinner(Composite parent, int style) {
    super(parent, style, "spinner");
    send();
    created();
  }

  private void send() {
    set("value", new int[] { selection, minimum, maximum, increment, digits });
  }

  public void setSelection(int v) {
    selection = Math.max(minimum, Math.min(maximum, v));
    send();
  }

  public int getSelection() {
    return selection;
  }

  public void setMinimum(int v) {
    minimum = v;
    send();
  }

  public int getMinimum() {
    return minimum;
  }

  public void setMaximum(int v) {
    maximum = v;
    send();
  }

  public int getMaximum() {
    return maximum;
  }

  public void setIncrement(int v) {
    increment = v;
    send();
  }

  public void setDigits(int v) {
    digits = v;
    send();
  }

  public void setValues(int selection, int minimum, int maximum, int digits, int increment, int pageIncrement) {
    this.minimum = minimum;
    this.maximum = maximum;
    this.digits = digits;
    this.increment = increment;
    this.selection = selection;
    send();
  }

  public String getText() {
    return String.valueOf(selection);
  }

  public void addModifyListener(ModifyListener l) {
    addTyped(SWT.Modify, l);
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("selection".equals(type)) {
      selection = d.get("selection").getAsInt();
      setQuiet("value", new int[] { selection, minimum, maximum, increment, digits });
      notifyListeners(SWT.Modify, new Event());
      notifyListeners(SWT.Selection, new Event());
      return;
    }
    super.onClientEvent(type, d);
  }
}
