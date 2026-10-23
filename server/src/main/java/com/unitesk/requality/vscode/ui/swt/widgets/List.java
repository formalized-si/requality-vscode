package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;

/** {@code org.eclipse.swt.widgets.List}. */
public class List extends Scrollable {
  private final java.util.List<String> items = new ArrayList<>();
  private final TreeSet<Integer> selection = new TreeSet<>();

  public List(Composite parent, int style) {
    super(parent, style, "list");
    set("items", items);
    set("selection", new int[0]);
    created();
  }

  private void send() {
    set("items", new ArrayList<>(items));
    sendSelection();
  }

  private void sendSelection() {
    set("selection", getSelectionIndices());
  }

  public void add(String s) {
    items.add(s);
    send();
  }

  public void add(String s, int index) {
    items.add(index, s);
    TreeSet<Integer> shifted = new TreeSet<>();
    for (int i : selection)
      shifted.add(i >= index ? i + 1 : i);
    selection.clear();
    selection.addAll(shifted);
    send();
  }

  public void remove(int index) {
    items.remove(index);
    TreeSet<Integer> shifted = new TreeSet<>();
    for (int i : selection)
      if (i != index)
        shifted.add(i > index ? i - 1 : i);
    selection.clear();
    selection.addAll(shifted);
    send();
  }

  public void remove(int[] indices) {
    int[] s = indices.clone();
    Arrays.sort(s);
    for (int i = s.length - 1; i >= 0; i--)
      remove(s[i]);
  }

  public void remove(String s) {
    int i = items.indexOf(s);
    if (i < 0)
      throw new IllegalArgumentException("Item not found: " + s);
    remove(i);
  }

  public void removeAll() {
    items.clear();
    selection.clear();
    send();
  }

  public void setItems(String... newItems) {
    items.clear();
    items.addAll(Arrays.asList(newItems));
    selection.clear();
    send();
  }

  public void setItem(int index, String s) {
    items.set(index, s);
    send();
  }

  public String[] getItems() {
    return items.toArray(new String[0]);
  }

  public String getItem(int index) {
    return items.get(index);
  }

  /**
   * {@code org.eclipse.swt.widgets.List.getItemHeight()}: the renderer does not report pixel geometry, so the height of
   * a standard 20px row is returned (as {@link Table#getItemHeight()}).
   */
  public int getItemHeight() {
    return 20;
  }

  public int getItemCount() {
    return items.size();
  }

  public int indexOf(String s) {
    return items.indexOf(s);
  }

  public int getSelectionIndex() {
    return selection.isEmpty() ? -1 : selection.first();
  }

  public int[] getSelectionIndices() {
    return selection.stream().mapToInt(Integer::intValue).toArray();
  }

  public int getSelectionCount() {
    return selection.size();
  }

  public String[] getSelection() {
    return selection.stream().map(items::get).toArray(String[]::new);
  }

  public boolean isSelected(int index) {
    return selection.contains(index);
  }

  public void select(int index) {
    if (index < 0 || index >= items.size())
      return;
    if (!SWT.has(style, SWT.MULTI))
      selection.clear();
    selection.add(index);
    sendSelection();
  }

  public void select(int[] indices) {
    for (int i : indices)
      select(i);
  }

  public void setSelection(int index) {
    selection.clear();
    select(index);
    sendSelection();
  }

  public void setSelection(int[] indices) {
    selection.clear();
    select(indices);
    sendSelection();
  }

  public void setSelection(String[] sel) {
    selection.clear();
    for (String s : sel) {
      int i = items.indexOf(s);
      if (i >= 0)
        select(i);
    }
    sendSelection();
  }

  public void deselect(int index) {
    selection.remove(index);
    sendSelection();
  }

  public void deselectAll() {
    selection.clear();
    sendSelection();
  }

  public void selectAll() {
    if (!SWT.has(style, SWT.MULTI))
      return;
    for (int i = 0; i < items.size(); i++)
      selection.add(i);
    sendSelection();
  }

  public void showSelection() {
  }

  public void setTopIndex(int index) {
  }

  public int getTopIndex() {
    return 0;
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
    if ("selection".equals(type) || "default".equals(type)) {
      selection.clear();
      for (JsonElement e : d.getAsJsonArray("indices"))
        selection.add(e.getAsInt());
      setQuiet("selection", getSelectionIndices());
      notifyListeners("default".equals(type) ? SWT.DefaultSelection : SWT.Selection, new Event());
      return;
    }
    super.onClientEvent(type, d);
  }
}
