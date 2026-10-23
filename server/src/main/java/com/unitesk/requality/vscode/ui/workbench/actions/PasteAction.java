package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.function.Supplier;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.ResourceTransfer;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.PasteAction}: pastes the projects on the clipboard as
 * copies ({@link CopyProjectOperation}, one "Copy Project" dialog per project; open projects can be pasted whatever
 * the selection), and files and folders into the selected container ({@link CopyFilesAndFoldersOperation}).
 */
public class PasteAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.PasteAction";
  private final Clipboard clipboard;

  public PasteAction(Supplier<Shell> shell, Clipboard clipboard) {
    super("&Paste", shell);
    this.clipboard = clipboard;
    setToolTipText("Paste");
    setId(ID);
    setActionDefinitionId(ActionFactory.PASTE.getCommandId());
  }

  private IResource[] clipboardResources() {
    try {
      Object o = clipboard.getContents(ResourceTransfer.getInstance());
      return o instanceof IResource[] ? (IResource[]) o : null;
    } catch (RuntimeException e) {
      RLogger.logDebug("Clipboard not readable: " + e, 3);
      return null;
    }
  }

  private String[] clipboardFiles() {
    try {
      Object o = clipboard.getContents(com.unitesk.requality.vscode.ui.swt.dnd.FileTransfer.getInstance());
      return o instanceof String[] ? (String[]) o : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The container to paste into: the selected container, or the folder of the selected file. */
  private com.unitesk.requality.vscode.ui.resources.IContainer getContainer() {
    java.util.List<IResource> selection = getSelectedResources();
    if (selection.isEmpty())
      return null;
    IResource r = selection.get(0);
    if (r instanceof com.unitesk.requality.vscode.ui.resources.IFile)
      return r.getParent();
    return r instanceof com.unitesk.requality.vscode.ui.resources.IContainer
        ? (com.unitesk.requality.vscode.ui.resources.IContainer) r : null;
  }

  @Override
  public void run() {
    IResource[] resourceData = clipboardResources();
    if (resourceData != null && resourceData.length > 0 && resourceData[0].getType() == IResource.PROJECT) {
      for (IResource r : resourceData)
        new CopyProjectOperation(getShell()).copyProject((IProject) r);
      return;
    }
    com.unitesk.requality.vscode.ui.resources.IContainer container = getContainer();
    if (container == null)
      return;
    if (resourceData != null && resourceData.length > 0) {
      new CopyFilesAndFoldersOperation(getShell()).copyResources(resourceData, container);
      return;
    }
    String[] fileData = clipboardFiles();
    if (fileData != null)
      new CopyFilesAndFoldersOperation(getShell()).copyFiles(fileData, container);
  }

  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    IResource[] resourceData = clipboardResources();
    boolean isProjectRes = resourceData != null && resourceData.length > 0
        && resourceData[0].getType() == IResource.PROJECT;
    if (isProjectRes) {
      for (IResource r : resourceData)
        // make sure all resource data are open projects: they can be pasted regardless of the selection
        if (r.getType() != IResource.PROJECT || !((IProject) r).isOpen())
          return false;
      return true;
    }
    if (!getSelectedNonResources().isEmpty())
      return false;
    com.unitesk.requality.vscode.ui.resources.IContainer target = getContainer();
    if (target == null || !target.isAccessible())
      return false;
    if (resourceData != null) {
      for (IResource r : resourceData) {
        // only files and folders, and not into themselves
        if (r.getType() != IResource.FILE && r.getType() != IResource.FOLDER)
          return false;
        if (r.getType() == IResource.FOLDER && r.getFullPath().isPrefixOf(target.getFullPath()))
          return false;
      }
      return true;
    }
    return clipboardFiles() != null;
  }
}
