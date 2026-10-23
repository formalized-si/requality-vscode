package com.unitesk.requality.eclipse.handlers.copypaste;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Shell;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.tool.message.RMessages;

public class PasteIntoHandler extends AbstractHandler {
  com.unitesk.requality.core.operation.PasteIntoHandler base =
      new com.unitesk.requality.core.operation.PasteIntoHandler() {
        @Override
        protected void showError(String title, String message) {
          RMessages.openError(title, message, "", true);
        }

        @Override
        protected boolean showConfirm(String title, String desc) {
          return RMessages.openQuestionOkCancel(title, desc, false, true);
//          return MessageDialog.openConfirm(
//              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), title, desc);
        }
      };

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord parent = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (parent == null || parent.fullSelection.size() != 1) return null;
    TreeNode node = parent.getFirstNode();
    if(node == null)
      return null;
    return pasteNodes(node, null, true, false, false, false, false);
  }

  public Object pasteNodes(TreeNode parent, boolean copyTree, boolean wait) {
    return pasteNodes(parent, null, copyTree, false, false, false, wait);
  }

  public Object pasteNodes(
      TreeNode parent,
      TreeNode lastnode,
      boolean copyTree,
      boolean skipTransaction,
      boolean skipDialogs,
      boolean autoRename, 
      boolean wait) {
    Activator a = Activator.getDefault();
    Shell shell = null;
    if (a != null && Activator.getDefault().getWorkbench().getActiveWorkbenchWindow()!=null) Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getShell();
    if (a == null) skipDialogs = true;
    return base.pasteNodes(parent, lastnode, copyTree, skipTransaction, skipDialogs, autoRename, wait);
  }

  public boolean checkInto() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    return base.checkInto(nodes);
  }

  @Override
  public boolean isEnabled() {
    return checkInto();
  }
}
