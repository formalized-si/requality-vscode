package com.unitesk.requality.vscode.ui.compare;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.compare.internal.BinaryCompareViewer}: the compare viewer of binary contents ("Binary Compare"),
 * a message with the position of the first different byte.
 */
public class BinaryCompareViewer extends Viewer {
  private final Text fControl;
  private ICompareInput fInput;

  public BinaryCompareViewer(Composite parent, CompareConfiguration cc) {
    fControl = new Text(parent, SWT.NONE);
    fControl.setEditable(false);
    fControl.setData(CompareUI.COMPARE_VIEWER_TITLE, "Binary Compare");
    setData(CompareUI.COMPARE_VIEWER_TITLE, "Binary Compare");
  }

  @Override
  public Control getControl() {
    return fControl;
  }

  @Override
  public void setInput(Object input) {
    if (fControl != null && input instanceof ICompareInput) {
      fInput = (ICompareInput) input;
      String message = null;
      try (InputStream left = stream(fInput.getLeft()); InputStream right = stream(fInput.getRight())) {
        if (left != null && right != null) {
          int pos = 0;
          while (true) {
            int l = left.read();
            int r = right.read();
            if (l != r) {
              message = MessageFormat.format("First bytes differ at position {0}", String.valueOf(pos));
              break;
            }
            if (l == -1)
              break;
            pos++;
          }
          if (message == null)
            message = "Two binary files are identical";
        } else if (left == null && right == null) {
          message = "";
        } else if (left == null) {
          message = "Left side is empty";
        } else {
          message = "Right side is empty";
        }
      } catch (CoreException | IOException ex) {
        message = "Internal Error: " + ex.getMessage();
      }
      fControl.setText(message);
    }
  }

  private static InputStream stream(Object o) throws CoreException {
    return o instanceof IStreamContentAccessor ? ((IStreamContentAccessor) o).getContents() : null;
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
  }
}
