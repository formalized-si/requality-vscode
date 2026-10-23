package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.ide.undo.CreateMarkersOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.IShellProvider;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/**
 * {@code org.eclipse.ui.actions.AddBookmarkAction}: adds a bookmark to the selected resource, as an undoable
 * operation ("Add Bookmark"). The bookmark is named after the resource and shown in the Bookmarks view.
 */
public class AddBookmarkAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.AddBookmarkAction";
  /** The undo label of IDEWorkbenchMessages (BookmarkMessages.CreateBookmark_undoText). */
  static final String UNDO_TEXT = "Add Bookmark";

  private final IShellProvider shellProvider;

  /**
   * Creates the action. {@code promptForName} is ignored, as in Eclipse: the name of the bookmark is the name of the
   * resource.
   */
  public AddBookmarkAction(IShellProvider provider, boolean promptForName) {
    super("Add Boo&kmark...", provider::getShell);
    shellProvider = provider;
    setToolTipText("Add Bookmark");
    setId(ID);
    setActionDefinitionId(com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory.BOOKMARK.getCommandId());
  }

  @Override
  public void run() {
    List<IResource> resources = getSelectedResources();
    if (resources.isEmpty())
      return;
    IResource resource = resources.get(0);
    if (resource == null)
      return;
    Map<String, Object> attrs = new HashMap<>();
    attrs.put(IMarker.MESSAGE, resource.getName());
    try {
      WorkspaceUndoUtil.execute(new CreateMarkersOperation(IMarker.BOOKMARK, attrs, resource, UNDO_TEXT), null,
          shellProvider.getShell());
    } catch (CoreException e) {
      RLogger.logError(e);
    }
  }

  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    return super.updateSelection(selection) && getSelectedResources().size() == 1;
  }
}
