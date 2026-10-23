package com.unitesk.requality.eclipse.handlers.document;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.nodetype.DocFolder;

public class RenameDocFolderHandler extends AbstractHandler {

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || (nodes.fullSelection.size() != 1)) return null;
    final TreeNode node = nodes.getFirstNode();
    if(node == null || !node.getType().equals(DocFolder.TYPE_NAME))
      return null;
    final DocFolder parentFolder = (DocFolder) node.getWorkParent();
    String id = node.getId().trim();
    InputDialog dlg =
        new InputDialog(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
            "Change name of Document Folder",
            "Enter Document Folder name",
            id,
            new IInputValidator() {
              @Override
              public String isValid(String newText) {
                try {
                  if (!node.getTreeDB()
                      .getTreeLogic()
                      .isChildIdValidExc(parentFolder, node, newText)) return "name is not valid";
                } catch (IdEmpty e) {
                  return "enter Document Folder name";
                } catch (IdContainsWrongChars e) {
                  return "name contains wrong characters - " + e.getWrongCharacter();
                } catch (SameIdExistsInChildren e) {
                  return "child with same id already exists";
                }
                return null;
              }
            });
    if (dlg.open() == Dialog.OK) {
      TreeDB db = node.getTreeDB();
      boolean started = db.startTransaction("Change id of Document Folder", true);
      if (started) {
        try {
          String oldQId = node.getQualifiedId();
          node.setId(dlg.getValue().trim());
          node.commitAttributes();
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

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || (nodes.fullSelection.size() != 1)) return false;
    TreeNode node = nodes.getFirstNode();
    if(node == null || !node.getType().equals(DocFolder.TYPE_NAME))
      return false;
    TreeNode parent = node.getWorkParent();
    TreeDB db = node.getTreeDB();
    if (parent.getUUId().equals(db.getRootNode().getUUId())) return false;
    return true;
  }
}
