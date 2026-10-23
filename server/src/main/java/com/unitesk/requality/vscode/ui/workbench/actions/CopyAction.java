package com.unitesk.requality.vscode.ui.workbench.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.dnd.FileTransfer;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.dnd.Transfer;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.ResourceTransfer;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.CopyAction}: puts the selected resources (siblings) on
 * the clipboard as resources, file paths and their names.
 */
public class CopyAction extends SelectionListenerAction {
  public static final String ID = "org.eclipse.ui.CopyAction";
  private final Clipboard clipboard;
  private PasteAction pasteAction;

  public CopyAction(Supplier<Shell> shell, Clipboard clipboard) {
    super("&Copy", shell);
    this.clipboard = clipboard;
    setToolTipText("Copy");
    setId(ID);
    setActionDefinitionId(ActionFactory.COPY.getCommandId());
  }

  public CopyAction(Supplier<Shell> shell, Clipboard clipboard, PasteAction pasteAction) {
    this(shell, clipboard);
    this.pasteAction = pasteAction;
  }

  @Override
  public void run() {
    List<IResource> selected = getSelectedResources();
    IResource[] resources = selected.toArray(new IResource[0]);
    List<String> fileNames = new ArrayList<>();
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < resources.length; i++) {
      if (resources[i].getLocation() != null)
        fileNames.add(resources[i].getLocation().toOSString());
      if (i > 0)
        buf.append("\n");
      buf.append(resources[i] instanceof com.unitesk.requality.vscode.ui.resources.IProject
          ? com.unitesk.requality.vscode.ui.resources.ProjectRef.displayName((com.unitesk.requality.vscode.ui.resources.IProject) resources[i])
          : resources[i].getName());
    }
    if (!fileNames.isEmpty())
      clipboard.setContents(new Object[] { resources, fileNames.toArray(new String[0]), buf.toString() },
          new Transfer[] { ResourceTransfer.getInstance(), FileTransfer.getInstance(), TextTransfer.getInstance() });
    else
      clipboard.setContents(new Object[] { resources, buf.toString() },
          new Transfer[] { ResourceTransfer.getInstance(), TextTransfer.getInstance() });
    // update the enablement of the paste action
    if (pasteAction != null && pasteAction.getStructuredSelection() != null)
      pasteAction.selectionChanged(pasteAction.getStructuredSelection());
  }

  /** Enabled for a non-empty selection of sibling resources (projects, or members of one container). */
  @Override
  protected boolean updateSelection(IStructuredSelection selection) {
    if (!getSelectedNonResources().isEmpty())
      return false;
    List<IResource> resources = getSelectedResources();
    if (resources.isEmpty())
      return false;
    boolean projSelected = selectionIsOfType(IResource.PROJECT);
    boolean fileFoldersSelected = selectionIsOfType(IResource.FILE | IResource.FOLDER);
    if (!projSelected && !fileFoldersSelected)
      return false;
    // selected resources must have a common parent
    Object parent = resources.get(0).getParent();
    for (IResource r : resources)
      if (r.getParent() == null ? parent != null : !r.getParent().equals(parent))
        return false;
    return true;
  }
}
