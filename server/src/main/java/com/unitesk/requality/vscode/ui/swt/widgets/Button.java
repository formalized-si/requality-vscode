package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;

/** {@code org.eclipse.swt.widgets.Button}: PUSH, CHECK, RADIO, TOGGLE and ARROW. */
public class Button extends Control {
  private String text = "";
  private Image image;
  private boolean selection;
  private boolean grayed;

  public Button(Composite parent, int style) {
    super(parent, style == SWT.NONE ? SWT.PUSH : style, "button");
    created();
  }

  public void setText(String text) {
    checkWidget();
    if (text == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    this.text = text;
    set("text", this.text.replace("&&", "\u0000").replace("&", "").replace("\u0000", "&"));
  }

  public String getText() {
    return text;
  }

  public void setImage(Image image) {
    this.image = image;
    set("image", image == null ? null : image.getPath());
  }

  public Image getImage() {
    return image;
  }

  /** Does not fire Selection (like SWT). */
  public void setSelection(boolean selected) {
    checkWidget();
    selection = selected;
    set("selection", selected);
  }

  public boolean getSelection() {
    return selection;
  }

  public void setGrayed(boolean grayed) {
    this.grayed = grayed;
    set("grayed", grayed);
  }

  public boolean getGrayed() {
    return grayed;
  }

  public void setAlignment(int alignment) {
    set("align", alignment);
  }

  public void addSelectionListener(SelectionListener l) {
    addTyped(SWT.Selection, l);
    addTyped(SWT.DefaultSelection, l);
  }

  public void removeSelectionListener(SelectionListener l) {
    removeTyped(SWT.Selection, l);
    removeTyped(SWT.DefaultSelection, l);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if (!"selection".equals(type)) {
      super.onClientEvent(type, d);
      return;
    }
    if (!isEnabled())
      return;
    if (SWT.has(style, SWT.CHECK) || SWT.has(style, SWT.TOGGLE)) {
      selection = d.has("selection") ? d.get("selection").getAsBoolean() : !selection;
      setQuiet("selection", selection);
      notifyListeners(SWT.Selection, new Event());
    } else if (SWT.has(style, SWT.RADIO)) {
      if (selection)
        return;
      // SWT radio group: siblings are deselected and notified first.
      if (!SWT.has(parent.getStyle(), SWT.NO_RADIO_GROUP)) {
        for (Control c : parent.getChildren()) {
          if (c != this && c instanceof Button && SWT.has(c.getStyle(), SWT.RADIO) && ((Button) c).selection) {
            ((Button) c).setSelection(false);
            c.notifyListeners(SWT.Selection, new Event());
          }
        }
      }
      selection = true;
      setQuiet("selection", true);
      notifyListeners(SWT.Selection, new Event());
    } else {
      notifyListeners(SWT.Selection, new Event());
    }
  }
}
