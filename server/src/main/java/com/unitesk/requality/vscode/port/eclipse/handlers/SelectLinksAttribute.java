package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.ReferenceNameSelectionDialog;

public class SelectLinksAttribute extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    //ToolItem ti = (ToolItem)(.widget);
    SelectionManager selman = SelectionManager.getOne();
    FSId fs = selman.getFirstActiveSource(EventSource.LCNF);
    if(fs == null)
      return null;
    Object targ =  fs.getObject();
    SelectionRecord tn = null;
    if(targ == null || !(targ instanceof RequalityLCNF)) {
      tn = SelectionManager.getOne().getSelection(fs);
    }else {
      RequalityLCNF lcnf = (RequalityLCNF) targ;
      TreeNode node = lcnf.getRootTreeNode();
      if(node!=null)
      {
        LinkedList<TreeNode>nds = new LinkedList<>();
        nds.add(node);
        tn = new SelectionRecord(nds);//TODO rewrite?
      }
    }
   // for (List<TreeNode> tn : act.values())
    if (tn!=null && tn.fullSelection.size() > 0) {
      TreeNode first = tn.getFirstNode();
      if(first == null)
        return true;
      TreeDB db = first.getTreeDB();
      String[] back = ReferenceNameSelectionDialog.getSavedState(db);
      ReferenceNameSelectionDialog asp =
          new ReferenceNameSelectionDialog(db, getShell());
      if (asp.open() == com.unitesk.requality.vscode.ui.jface.dialogs.Dialog.CANCEL)
        ReferenceNameSelectionDialog.saveState(db, back);
      else {
        String[] res = ReferenceNameSelectionDialog.getSavedState(db);
        RequalityLCNF.setFilter(
            first.getTreeDB(), res == null ? null : new HashSet<>(Arrays.asList(res)));
      }
      return true;
    }
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  private static Shell getShell() {
    if (Activator.getDefault() == null) return null;
    IWorkbench w = Activator.getDefault().getWorkbench();
    if (w == null) return null;
    IWorkbenchWindow win = w.getActiveWorkbenchWindow();
    if (win == null) return null;
    return win.getShell();
  }
}
