package com.unitesk.requality.eclipse.handlers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ToDoItem;
import com.unitesk.requality.views.documents.ToDoList;

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
