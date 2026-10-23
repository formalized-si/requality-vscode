package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.TabFolder} (also used for CTabFolder). */
public class TabFolder extends Composite {
  final java.util.List<TabItem> items = new ArrayList<>();
  private int selectionIndex = -1;

  public TabFolder(Composite parent, int style) {
    super(parent, style, "tabfolder");
    created();
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    java.util.List<Widget> r = new ArrayList<>(super.snapshotChildren());
    r.addAll(items);
    return r;
  }

  void addItem(TabItem item, int index) {
    if (index < 0 || index > items.size())
      items.add(item);
    else
      items.add(index, item);
    if (selectionIndex < 0)
      setSelection(0);
  }

  void removeItem(TabItem item) {
    int i = items.indexOf(item);
    items.remove(item);
    if (i == selectionIndex)
      setSelection(items.isEmpty() ? -1 : Math.min(i, items.size() - 1));
    else if (i < selectionIndex)
      selectionIndex--;
  }

  public TabItem[] getItems() {
    return items.toArray(new TabItem[0]);
  }

  public TabItem getItem(int index) {
    return items.get(index);
  }

  public int getItemCount() {
    return items.size();
  }

  public int indexOf(TabItem item) {
    return items.indexOf(item);
  }

  public int getSelectionIndex() {
    return selectionIndex;
  }

  public TabItem[] getSelection() {
    return selectionIndex < 0 ? new TabItem[0] : new TabItem[] { items.get(selectionIndex) };
  }

  public void setSelection(int index) {
    selectionIndex = index;
    set("selection", index < 0 || index >= items.size() ? null : items.get(index).id);
  }

  public void setSelection(TabItem item) {
    setSelection(items.indexOf(item));
  }

  public void setSelection(TabItem[] sel) {
    setSelection(sel.length == 0 ? -1 : items.indexOf(sel[0]));
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
  void releaseChildren() {
    for (TabItem i : getItems())
      i.dispose();
    super.releaseChildren();
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("selection".equals(type)) {
      Widget w = display.find(d.get("item").getAsString());
      if (!(w instanceof TabItem))
        return;
      selectionIndex = items.indexOf(w);
      setQuiet("selection", w.id);
      Event e = new Event();
      e.item = w;
      notifyListeners(SWT.Selection, e);
      return;
    }
    super.onClientEvent(type, d);
  }
}
