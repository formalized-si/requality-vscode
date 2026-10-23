package com.unitesk.requality.vscode.ui.jface.text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/** {@code org.eclipse.jface.text.TextViewer}: a multi-line text bound to a document. */
public class TextViewer {
  private final Text text;
  private IDocument document;
  private final List<ITextInputListener> listeners = new CopyOnWriteArrayList<>();
  private final Runnable sync;

  public TextViewer(Composite parent, int style) {
    text = new Text(parent, style | SWT.MULTI);
    sync = () -> {
      if (document != null && !text.isDisposed() && !text.getText().equals(document.get()))
        text.setText(document.get());
    };
    text.addModifyListener(e -> {
      if (document != null && !text.getText().equals(document.get())) {
        if (document instanceof Document) {
          ((Document) document).listeners.remove(sync);
          document.set(text.getText());
          ((Document) document).listeners.add(sync);
        } else {
          document.set(text.getText());
        }
      }
    });
  }

  public void setDocument(IDocument doc) {
    IDocument old = document;
    for (ITextInputListener l : listeners)
      l.inputDocumentAboutToBeChanged(old, doc);
    if (old instanceof Document)
      ((Document) old).listeners.remove(sync);
    document = doc;
    if (doc instanceof Document)
      ((Document) doc).listeners.add(sync);
    text.setText(doc == null ? "" : doc.get());
    for (ITextInputListener l : listeners)
      l.inputDocumentChanged(old, doc);
  }

  /** {@code org.eclipse.jface.viewers.Viewer#setInput(Object)}: the input of a text viewer is its document. */
  public void setInput(Object input) {
    setDocument((IDocument) input);
  }

  public IDocument getDocument() {
    return document;
  }

  public Text getTextWidget() {
    return text;
  }

  public Control getControl() {
    return text;
  }

  public void setEditable(boolean editable) {
    text.setEditable(editable);
  }

  public boolean isEditable() {
    return text.getEditable();
  }

  public void addTextInputListener(ITextInputListener l) {
    listeners.add(l);
  }

  public void removeTextInputListener(ITextInputListener l) {
    listeners.remove(l);
  }

  public void refresh() {
    sync.run();
  }
}
