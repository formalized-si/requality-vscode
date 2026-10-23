package com.unitesk.requality.vscode.ui.compare;

import java.io.IOException;
import java.io.InputStream;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionDelegate;

/**
 * {@code org.eclipse.compare.internal.ClipboardCompare}: Compare With &gt; Clipboard of a file. The contents of the file
 * are compared with the text of the clipboard in a compare editor (the clipboard side is titled "Clipboard").
 * Run from a view (the explorer) the whole contents of the file are compared, as Eclipse does for an IViewPart.
 */
public class ClipboardCompare extends ActionDelegate {
  private static final String CLIPBOARD = "Clipboard";
  private static final String COMPARE_FAILED = "Comparision Failed";

  private IStructuredSelection selection;

  @Override
  public void selectionChanged(IAction action, ISelection sel) {
    selection = sel instanceof IStructuredSelection ? (IStructuredSelection) sel : null;
    action.setEnabled(file() != null && clipboardText() != null);
  }

  @Override
  public void run(IAction action) {
    IFile file = file();
    if (file == null)
      return;
    Shell parentShell = EditionAction.shell();
    try {
      processComparison(file, parentShell);
    } catch (Exception e) {
      RLogger.logError(e);
      MessageDialog.openError(parentShell, COMPARE_FAILED, e.getMessage());
    }
  }

  /** The one file of the selection (the file itself or the file of its mapping), or null. */
  private IFile file() {
    if (selection == null || selection.size() != 1)
      return null;
    return CompareAction.file(selection.getFirstElement());
  }

  private static void processComparison(IFile file, Shell parentShell) throws IOException, CoreException {
    String cb = clipboardText();
    if (cb == null)
      return;
    String fileContents;
    try (InputStream in = file.getContents()) {
      fileContents = new String(in.readAllBytes(), file.getCharset());
    }
    showComparison(fileContents, file.getName(), cb, parentShell);
  }

  private static void showComparison(String source, String fileName, String clipboardContents, Shell parentShell) {
    if (source == null) {
      MessageDialog.openInformation(parentShell, COMPARE_FAILED, "Failed to process selected file");
      return;
    }
    CompareConfiguration config = new CompareConfiguration();
    config.setLeftLabel(fileName);
    config.setRightLabel(CLIPBOARD);
    config.setLeftEditable(true);
    config.setRightEditable(true);
    CompareEditorInput compareInput = new CompareEditorInput(config) {
      @Override
      protected Object prepareInput(IProgressMonitor monitor) {
        return new DiffNode(CompareEditor.textElement(fileName, source),
            CompareEditor.textElement(CLIPBOARD, clipboardContents));
      }
    };
    compareInput.setTitle("Compare " + fileName + " and Clipboard");
    CompareUI.openCompareEditor(compareInput);
  }

  /** The text of the clipboard, or null when it has none. */
  static String clipboardText() {
    Clipboard clip = new Clipboard(Display.getDefault());
    try {
      Object contents = clip.getContents(TextTransfer.getInstance());
      return contents == null ? null : contents.toString();
    } finally {
      clip.dispose();
    }
  }
}
