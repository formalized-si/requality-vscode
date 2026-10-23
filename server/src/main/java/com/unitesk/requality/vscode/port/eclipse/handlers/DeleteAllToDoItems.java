package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.commands.IElementUpdater;
import com.unitesk.requality.vscode.ui.commands.UIElement;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.vscode.port.views.documents.ToDoList;

public class DeleteAllToDoItems extends AbstractHandler implements IHandler, IElementUpdater {

  @SuppressWarnings("rawtypes")
  @Override
  public void updateElement(UIElement arg0, Map arg1) {
    // TODO Auto-generated method stub

  }

  @Override
  public Object execute(ExecutionEvent arg0) throws ExecutionException {

    ToDoItem item = ToDoList.getSelectedNode();

    if (item != null) {

      TreeDB db = item.getTreeDB();
      boolean started = db.startTransaction("Deleting all todo items");
      if (started) {
        try {
          TreeNode folder = db.getNode("/ToDoLinks", Path.QID);
  
          List<UUID> uids = folder.getWorkChildrenUUIDs();
          for (UUID uuid : uids) {
            db.getNode(uuid).delete();
          }
  
          folder.delete();
          if(started) {
            db.commit();
            started = false;
          }
        }finally {
          if(started)
            db.rollback();
        }
      }
    }
    return null;
  }
}
