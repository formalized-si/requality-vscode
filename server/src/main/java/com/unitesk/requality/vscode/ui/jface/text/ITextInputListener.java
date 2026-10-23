package com.unitesk.requality.vscode.ui.jface.text;

/** {@code org.eclipse.jface.text.ITextInputListener}. */
public interface ITextInputListener {
  void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput);

  void inputDocumentChanged(IDocument oldInput, IDocument newInput);
}
