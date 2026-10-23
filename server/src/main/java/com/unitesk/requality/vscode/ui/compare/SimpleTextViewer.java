package com.unitesk.requality.vscode.ui.compare;

import com.unitesk.requality.vscode.ui.compare.contentmergeviewer.TextMergeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.compare.internal.SimpleTextViewer}: the content viewer of a text element (a read-only text).
 */
public class SimpleTextViewer extends Viewer {
  private final Text fText;
  private Object fInput;

  public SimpleTextViewer(Composite parent) {
    fText = new Text(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY | SWT.BORDER);
    fText.setEditable(false);
    fText.setData(CompareUI.COMPARE_VIEWER_TITLE, "Text");
    setData(CompareUI.COMPARE_VIEWER_TITLE, "Text");
  }

  @Override
  public Control getControl() {
    return fText;
  }

  @Override
  public void setInput(Object input) {
    fInput = input;
    fText.setText(input instanceof IStreamContentAccessor ? TextMergeViewer.textOf(input) : "");
  }

  @Override
  public Object getInput() {
    return fInput;
  }

  @Override
  public ISelection getSelection() {
    return StructuredSelection.EMPTY;
  }

  @Override
  public void setSelection(ISelection s, boolean reveal) {
  }

  @Override
  public void refresh() {
    setInput(fInput);
  }
}
