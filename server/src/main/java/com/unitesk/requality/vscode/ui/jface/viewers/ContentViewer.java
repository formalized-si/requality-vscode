package com.unitesk.requality.vscode.ui.jface.viewers;

/** {@code org.eclipse.jface.viewers.ContentViewer}. */
public abstract class ContentViewer extends Viewer {
  private IContentProvider contentProvider;
  private IBaseLabelProvider labelProvider = new LabelProvider();
  private Object input;
  private final ILabelProviderListener labelListener = e -> handleLabelProviderChanged(e);

  public IContentProvider getContentProvider() {
    return contentProvider;
  }

  public void setContentProvider(IContentProvider p) {
    IContentProvider old = contentProvider;
    contentProvider = p;
    if (old != null && old != p) {
      old.inputChanged(this, input, null);
      old.dispose();
    }
    if (p != null)
      p.inputChanged(this, null, input);
    if (input != null && getControl() != null)
      refresh();
  }

  public IBaseLabelProvider getLabelProvider() {
    return labelProvider;
  }

  public void setLabelProvider(IBaseLabelProvider p) {
    if (labelProvider != null)
      labelProvider.removeListener(labelListener);
    labelProvider = p == null ? new LabelProvider() : p;
    labelProvider.addListener(labelListener);
    if (input != null && getControl() != null)
      refresh();
  }

  protected void handleLabelProviderChanged(LabelProviderChangedEvent e) {
    if (getControl() != null && !getControl().isDisposed())
      refresh();
  }

  @Override
  public Object getInput() {
    return input;
  }

  @Override
  public void setInput(Object newInput) {
    Object old = input;
    input = newInput;
    if (contentProvider != null)
      contentProvider.inputChanged(this, old, newInput);
    inputChanged(newInput, old);
  }

  protected void inputChanged(Object input, Object oldInput) {
    refresh();
  }
}
