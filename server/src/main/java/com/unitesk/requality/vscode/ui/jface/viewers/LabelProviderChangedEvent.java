package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.LabelProviderChangedEvent}. */
public class LabelProviderChangedEvent {
  private final IBaseLabelProvider source;
  private final Object[] elements;

  public LabelProviderChangedEvent(IBaseLabelProvider source) {
    this(source, null);
  }

  public LabelProviderChangedEvent(IBaseLabelProvider source, Object[] elements) {
    this.source = source;
    this.elements = elements;
  }

  public Object[] getElements() {
    return elements;
  }

  public Object getElement() {
    return elements == null || elements.length == 0 ? null : elements[0];
  }

  public Object getSource() {
    return source;
  }
}
