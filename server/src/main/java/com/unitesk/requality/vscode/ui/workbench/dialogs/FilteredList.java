package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.dialogs.FilteredList}: a filter text field above a list of the elements whose label matches
 * the pattern. The pattern is a glob ({@code *} and {@code ?}) that matches the whole label; a trailing {@code *} is
 * implied. An empty pattern shows all elements only when {@code matchEmpty} is set.
 */
public class FilteredList extends Composite {
  private final ILabelProvider labelProvider;
  private final boolean allowDuplicates;
  private final boolean matchEmpty;
  private final boolean ignoreCase;
  private final Text filter;
  private final com.unitesk.requality.vscode.ui.swt.widgets.List list;
  private final List<Object> elements = new ArrayList<>();
  private List<Object> visible = new ArrayList<>();
  private String pattern = "";

  /** {@code org.eclipse.ui.dialogs.FilteredList#FilteredList(Composite, int, ILabelProvider, boolean, boolean, boolean)}. */
  public FilteredList(Composite parent, int style, ILabelProvider labelProvider, boolean allowDuplicates,
      boolean matchEmpty, boolean ignoreCase) {
    super(parent, style, "composite");
    this.labelProvider = labelProvider;
    this.allowDuplicates = allowDuplicates;
    this.matchEmpty = matchEmpty;
    this.ignoreCase = ignoreCase;
    setLayout(new GridLayout(1, false));
    filter = new Text(this, SWT.BORDER);
    filter.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filter.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        applyFilter(filter.getText());
      }
    });
    list = new com.unitesk.requality.vscode.ui.swt.widgets.List(this,
        SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | (style & (SWT.SINGLE | SWT.MULTI)));
    list.setLayoutData(new GridData(GridData.FILL_BOTH));
    created();
  }

  /** {@code FilteredList#setElements(Object[])}: sets the elements; duplicates are dropped unless allowed. */
  public void setElements(Object[] newElements) {
    checkWidget();
    elements.clear();
    for (Object e : newElements) {
      if (allowDuplicates || !elements.contains(e))
        elements.add(e);
    }
    applyFilter(pattern);
  }

  /** {@code FilteredList#getSelection()}: the selected elements. */
  public Object[] getSelection() {
    checkWidget();
    int[] indices = list.getSelectionIndices();
    Object[] result = new Object[indices.length];
    for (int i = 0; i < indices.length; i++)
      result[i] = visible.get(indices[i]);
    return result;
  }

  /** {@code FilteredList#getSelectionIndex()}: the index of the first selected visible element, or -1. */
  public int getSelectionIndex() {
    checkWidget();
    return list.getSelectionIndex();
  }

  /** {@code FilteredList#setSelection(Object[])}: selects the visible elements that are given. */
  public void setSelection(Object[] selection) {
    checkWidget();
    List<Integer> indices = new ArrayList<>();
    for (Object o : selection) {
      int i = visible.indexOf(o);
      if (i >= 0)
        indices.add(i);
    }
    list.setSelection(indices.stream().mapToInt(Integer::intValue).toArray());
  }

  /** {@code FilteredList#setFilter(String)}: sets the filter pattern and applies it. */
  public void setFilter(String newPattern) {
    checkWidget();
    filter.setText(newPattern == null ? "" : newPattern);
  }

  /** {@code FilteredList#addSelectionListener(SelectionListener)}: forwards the selection events of the list. */
  public void addSelectionListener(SelectionListener listener) {
    checkWidget();
    list.addSelectionListener(listener);
  }

  private void applyFilter(String newPattern) {
    pattern = newPattern == null ? "" : newPattern;
    visible = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    for (Object e : elements) {
      String label = labelProvider.getText(e);
      if (matches(label)) {
        visible.add(e);
        labels.add(label);
      }
    }
    list.setItems(labels.toArray(new String[0]));
  }

  private boolean matches(String label) {
    if (pattern.isEmpty())
      return matchEmpty;
    String p = pattern.endsWith("*") ? pattern : pattern + "*";
    String text = label == null ? "" : label;
    if (ignoreCase) {
      p = p.toLowerCase();
      text = text.toLowerCase();
    }
    return glob(p, 0, text, 0);
  }

  private static boolean glob(String p, int pi, String t, int ti) {
    while (pi < p.length()) {
      char c = p.charAt(pi);
      if (c == '*') {
        for (int k = ti; k <= t.length(); k++)
          if (glob(p, pi + 1, t, k))
            return true;
        return false;
      }
      if (ti >= t.length() || (c != '?' && c != t.charAt(ti)))
        return false;
      pi++;
      ti++;
    }
    return ti == t.length();
  }
}
