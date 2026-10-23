package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** {@code org.eclipse.jface.viewers.StructuredSelection}. */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class StructuredSelection implements IStructuredSelection {
  public static final StructuredSelection EMPTY = new StructuredSelection();
  private final List elements;

  public StructuredSelection() {
    elements = Collections.emptyList();
  }

  public StructuredSelection(Object element) {
    elements = element == null ? Collections.emptyList() : Collections.singletonList(element);
  }

  public StructuredSelection(Object[] elements) {
    this.elements = elements == null ? Collections.emptyList() : new ArrayList(Arrays.asList(elements));
  }

  public StructuredSelection(List elements) {
    this.elements = elements == null ? Collections.emptyList() : new ArrayList(elements);
  }

  @Override
  public boolean isEmpty() {
    return elements.isEmpty();
  }

  @Override
  public Object getFirstElement() {
    return elements.isEmpty() ? null : elements.get(0);
  }

  @Override
  public Iterator iterator() {
    return Collections.unmodifiableList(elements).iterator();
  }

  @Override
  public int size() {
    return elements.size();
  }

  @Override
  public Object[] toArray() {
    return elements.toArray();
  }

  @Override
  public List toList() {
    return Collections.unmodifiableList(elements);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof StructuredSelection && ((StructuredSelection) o).elements.equals(elements);
  }

  @Override
  public int hashCode() {
    return elements.hashCode();
  }

  @Override
  public String toString() {
    return elements.toString();
  }
}
