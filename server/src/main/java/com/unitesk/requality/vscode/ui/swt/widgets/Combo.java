package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.Combo} (also used for {@code CCombo}). */
public class Combo extends Composite {
  private final List<String> items = new ArrayList<>();
  private String text = "";
  private int selectionIndex = -1;
  private int visibleItemCount = 5;

  public Combo(Composite parent, int style) {
    super(parent, style, "combo");
    set("items", items);
    set("text", text);
    set("index", -1);
    created();
  }

  private void sendItems() {
    set("items", new ArrayList<>(items));
  }

  private void changeText(String t, int index, boolean notifyModify) {
    boolean changed = !t.equals(text);
    text = t;
    selectionIndex = index;
    set("text", t);
    set("index", index);
    if (changed && notifyModify)
      notifyListeners(SWT.Modify, new Event());
  }

  public void add(String item) {
    checkWidget();
    items.add(item);
    sendItems();
  }

  public void add(String item, int index) {
    checkWidget();
    items.add(index, item);
    if (selectionIndex >= index)
      selectionIndex++;
    sendItems();
    set("index", selectionIndex);
  }

  public void remove(int index) {
    checkWidget();
    items.remove(index);
    if (selectionIndex == index)
      changeText(SWT.has(style, SWT.READ_ONLY) ? "" : text, -1, true);
    else if (selectionIndex > index)
      selectionIndex--;
    sendItems();
    set("index", selectionIndex);
  }

  public void remove(int start, int end) {
    for (int i = end; i >= start; i--)
      remove(i);
  }

  public void remove(String item) {
    int i = items.indexOf(item);
    if (i < 0)
      throw new IllegalArgumentException("Item not found: " + item);
    remove(i);
  }

  public void removeAll() {
    checkWidget();
    items.clear();
    sendItems();
    changeText(SWT.has(style, SWT.READ_ONLY) ? "" : text, -1, true);
  }

  public void setItems(String... newItems) {
    checkWidget();
    items.clear();
    for (String s : newItems)
      items.add(s == null ? "" : s);
    sendItems();
    changeText(SWT.has(style, SWT.READ_ONLY) ? "" : text, -1, true);
  }

  public void setItem(int index, String s) {
    items.set(index, s);
    sendItems();
    if (index == selectionIndex)
      changeText(s, index, true);
  }

  public String[] getItems() {
    return items.toArray(new String[0]);
  }

  public String getItem(int index) {
    return items.get(index);
  }

  public int getItemCount() {
    return items.size();
  }

  public int indexOf(String s) {
    return items.indexOf(s);
  }

  public int indexOf(String s, int start) {
    for (int i = Math.max(0, start); i < items.size(); i++)
      if (items.get(i).equals(s))
        return i;
    return -1;
  }

  /** Selects an item; fires Modify when the text changes (SWT), never Selection. */
  public void select(int index) {
    checkWidget();
    if (index < 0 || index >= items.size())
      return;
    changeText(items.get(index), index, true);
  }

  public void deselect(int index) {
    if (index == selectionIndex)
      deselectAll();
  }

  public void deselectAll() {
    checkWidget();
    changeText(SWT.has(style, SWT.READ_ONLY) ? "" : text, -1, true);
  }

  public int getSelectionIndex() {
    return selectionIndex;
  }

  public void setText(String string) {
    checkWidget();
    if (string == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    String s = string;
    int i = items.indexOf(s);
    if (SWT.has(style, SWT.READ_ONLY)) {
      if (i < 0)
        return;
      changeText(s, i, true);
    } else {
      changeText(s, i, true);
    }
  }

  public String getText() {
    return text;
  }

  public void setVisibleItemCount(int count) {
    visibleItemCount = count;
  }

  public int getVisibleItemCount() {
    return visibleItemCount;
  }

  public void setTextLimit(int limit) {
  }

  public void setEditable(boolean editable) {
    set("editable", editable);
  }

  public void setListVisible(boolean visible) {
  }

  public void addModifyListener(ModifyListener l) {
    addTyped(SWT.Modify, l);
  }

  public void removeModifyListener(ModifyListener l) {
    removeTyped(SWT.Modify, l);
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
      case "selection": {
        int index = d.get("index").getAsInt();
        String t = index >= 0 && index < items.size() ? items.get(index) : text;
        boolean changed = !t.equals(text);
        text = t;
        selectionIndex = index;
        setQuiet("text", t);
        setQuiet("index", index);
        if (changed)
          notifyListeners(SWT.Modify, new Event());
        notifyListeners(SWT.Selection, new Event());
        return;
      }
      case "modify": {
        String t = d.get("text").getAsString();
        if (t.equals(text))
          return;
        text = t;
        selectionIndex = items.indexOf(t);
        setQuiet("text", t);
        setQuiet("index", selectionIndex);
        notifyListeners(SWT.Modify, new Event());
        return;
      }
      case "default":
        notifyListeners(SWT.DefaultSelection, new Event());
        return;
      default:
        super.onClientEvent(type, d);
    }
  }
}
