package com.unitesk.requality.eclipse.handlers;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;

import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;

public class CopyNodeQId extends AbstractHandler implements IElementUpdater {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1)
      return null;
    TreeNode node = nodes.getFirstNode();
    if (node == null)
      return null;
    String nodeFullName = IDMechanics.getNodeId(node);
    Clipboard clipboard = new Clipboard(PlatformUI.getWorkbench().getDisplay());
    TextTransfer textTransfer = TextTransfer.getInstance();
    clipboard.setContents(new String[] {nodeFullName}, new Transfer[] {textTransfer});
    clipboard.dispose();
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1)
      return false;
    TreeNode node = nodes.getFirstNode();
    TreeDB dba = ResManager.getManager().getTracker().getTreeDB(node.getTreeDB(), node.getTreeDB().getLocalName(), node.getTreeDB().getProjectName(), false, node.getTreeDB().getMonitor());
    if (node == null)
      return false;
    return super.isEnabled();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void updateElement(UIElement element, Map parameters) {
    element.setText("Copy Full Name");
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1) return;
    TreeNode node = nodes.getFirstNode();
    if(node == null)
      return;
    if (node != null) {
      TreeDB db = node.getTreeDB();
      if(db.getFullNameMode() == 1)
        element.setText("Copy User-Visible Name");
      if(db.getFullNameMode() == 2)
        element.setText("Copy Qualified Id");
      if(db.getFullNameMode() == 3)
        element.setText("Copy Sequential Id");
    }
  }
}
