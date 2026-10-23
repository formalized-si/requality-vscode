package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;

/** {@code org.eclipse.ui.navigator.ILinkHelper}: links the selection of a navigator with the editors. */
public interface ILinkHelper {
  /** Activates the editor showing the given selection. */
  void activateEditor(IWorkbenchPage aPage, IStructuredSelection aSelection);

  /** Finds the navigator selection that corresponds to the given editor input. */
  IStructuredSelection findSelection(IEditorInput anInput);
}
