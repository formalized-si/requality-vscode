package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.events.VerifyListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;

/**
 * {@code org.eclipse.swt.widgets.Text}. {@link #setText} fires Modify like SWT. The renderer reports every edit
 * ({@code modify}), so {@link #getText()} is current when later events (keys, focus, buttons) arrive.
 */
public class Text extends Scrollable {
  public static final int LIMIT = Integer.MAX_VALUE;
  public static final String DELIMITER = "\n";
  private String text = "";
  private boolean editable;
  private int textLimit = LIMIT;
  private Point selection = new Point(0, 0);
  private String message = "";

  public Text(Composite parent, int style) {
    this(parent, style, "text");
  }

  /** For subclasses rendered differently (StyledText). */
  protected Text(Composite parent, int style, String kind) {
    super(parent, style, kind);
    editable = !SWT.has(style, SWT.READ_ONLY);
    set("text", text);
    created();
  }

  public void setText(String string) {
    checkWidget();
    if (string == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    String s = string;
    if (s.length() > textLimit)
      s = s.substring(0, textLimit);
    if (isListening(SWT.Verify)) {
      Event v = new Event();
      v.start = 0;
      v.end = text.length();
      v.text = s;
      notifyListeners(SWT.Verify, v);
      if (!v.doit)
        return;
      s = v.text == null ? "" : v.text;
    }
    text = s;
    set("text", s);
    notifyListeners(SWT.Modify, new Event());
  }

  public String getText() {
    return text;
  }

  public String getText(int start, int end) {
    int s = Math.max(0, start), e = Math.min(text.length() - 1, end);
    return s > e ? "" : text.substring(s, e + 1);
  }

  public void append(String string) {
    setText(text + (string == null ? "" : string));
  }

  public void insert(String string) {
    int s = Math.min(selection.x, text.length()), e = Math.min(selection.y, text.length());
    setText(text.substring(0, s) + (string == null ? "" : string) + text.substring(e));
  }

  public int getCharCount() {
    return text.length();
  }

  public int getLineCount() {
    return text.split("\n", -1).length;
  }

  public String getLineDelimiter() {
    return DELIMITER;
  }

  public void setEditable(boolean editable) {
    checkWidget();
    this.editable = editable;
    set("editable", editable);
  }

  public boolean getEditable() {
    return editable;
  }

  public void setTextLimit(int limit) {
    textLimit = limit;
    set("limit", limit == LIMIT ? null : limit);
  }

  public int getTextLimit() {
    return textLimit;
  }

  public void setMessage(String message) {
    this.message = message == null ? "" : message;
    set("message", this.message);
  }

  public String getMessage() {
    return message;
  }

  public void setSelection(int start) {
    setSelection(start, start);
  }

  public void setSelection(int start, int end) {
    selection = new Point(start, end);
    call("select", new int[] { start, end });
  }

  public void setSelection(Point p) {
    setSelection(p.x, p.y);
  }

  public Point getSelection() {
    return selection;
  }

  public String getSelectionText() {
    int s = Math.min(selection.x, text.length()), e = Math.min(selection.y, text.length());
    return s < e ? text.substring(s, e) : "";
  }

  public int getCaretPosition() {
    return selection.y;
  }

  public void selectAll() {
    setSelection(0, text.length());
  }

  public void clearSelection() {
    setSelection(selection.y);
  }

  public void copy() {
  }

  public void cut() {
  }

  public void paste() {
  }

  public void showSelection() {
  }

  public void setTopIndex(int index) {
  }

  public void setDoubleClickEnabled(boolean b) {
  }

  public void setEchoChar(char c) {
  }

  public void addModifyListener(ModifyListener l) {
    addTyped(SWT.Modify, l);
  }

  public void removeModifyListener(ModifyListener l) {
    removeTyped(SWT.Modify, l);
  }

  public void addVerifyListener(VerifyListener l) {
    addTyped(SWT.Verify, l);
  }

  public void removeVerifyListener(VerifyListener l) {
    removeTyped(SWT.Verify, l);
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
    switch (type) {
      case "modify": {
        String nt = d.get("text").getAsString();
        if (d.has("selStart"))
          selection = new Point(d.get("selStart").getAsInt(), d.get("selEnd").getAsInt());
        if (nt.equals(text))
          return;
        if (isListening(SWT.Verify)) {
          Event v = new Event();
          v.start = 0;
          v.end = text.length();
          v.text = nt;
          notifyListeners(SWT.Verify, v);
          if (!v.doit) {
            // reject: restore the previous text in the renderer
            setQuiet("text", nt);
            set("text", text);
            return;
          }
          nt = v.text == null ? "" : v.text;
          if (!nt.equals(d.get("text").getAsString())) {
            setQuiet("text", d.get("text").getAsString());
            set("text", nt);
          }
        }
        text = nt;
        setQuiet("text", nt);
        notifyListeners(SWT.Modify, new Event());
        return;
      }
      case "selection":
        if (d.has("selStart"))
          selection = new Point(d.get("selStart").getAsInt(), d.get("selEnd").getAsInt());
        return;
      case "default":
        notifyListeners(SWT.DefaultSelection, new Event());
        return;
      default:
        super.onClientEvent(type, d);
    }
  }
}
