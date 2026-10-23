package com.unitesk.requality.eclipse.handlers.create;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.Activator;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tests.TestDialogProvider;

public class CreateNewTest extends AHotKeyCommand{
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1)
	  try {
        return null;
      }finally {
        runActionsOnCompleted();
      }
    TreeNode node = nodes.getFirstNode();
    if(node == null) {
      try {
        return null;
      }finally {
        runActionsOnCompleted();
      }
    }
    EclipseAPI.getDisplay().asyncExec(new Runnable() {
      
      @Override
      public void run() {
        try {
          runActionsBeforeRun();
        }catch(Exception e) {
          RLogger.logError(e);
        }
        try {
          TestDialogProvider.getOne().fireTestCreation(node.getTreeDB(), node.getQualifiedId(), getShell());
        }finally {
          
        }
      }
    });
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1) return false;
    TreeNode node = nodes.getFirstNode();
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
      return false;
    return (node.getType().equals(TestPurpose.TYPE_NAME)
        || node.getType().equals(Requirement.TYPE_NAME)
        || node.getType().equals(TextNode.TYPE_NAME));
  }

  private static Shell getShell() {
    if (Activator.getDefault() == null) return null;
    IWorkbench w = Activator.getDefault().getWorkbench();
    if (w == null) return null;
    IWorkbenchWindow win = w.getActiveWorkbenchWindow();
    if (win == null) return null;
    return win.getShell();
  }
  private static CreateNewTest one;
  public static AbstractHandler getOne() {
    if(one == null)
      one = new CreateNewTest();
    return one;
  }
}
