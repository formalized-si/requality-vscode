package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetAdapter;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent;
import com.unitesk.requality.vscode.ui.workbench.ResourceTransfer;

/**
 * {@code org.eclipse.team.internal.ui.history.GenericHistoryDropAdapter}: a resource dropped on the History view shows
 * its history. The drop is a link (the dragged resources are not moved or copied).
 * VSCODE: the PluginDropAdapter base (plug-in transfers) is not needed: only resources are dropped on the view.
 */
public class GenericHistoryDropAdapter extends DropTargetAdapter {
  private final GenericHistoryView view;

  public GenericHistoryDropAdapter(GenericHistoryView view) {
    this.view = view;
  }

  @Override
  public void dragEnter(DropTargetEvent event) {
    if (event.detail == DND.DROP_DEFAULT)
      event.detail = (event.operations & DND.DROP_LINK) == DND.DROP_LINK ? DND.DROP_LINK : DND.DROP_COPY;
  }

  /* Slams the detail to DROP_LINK, as we do not want to really execute a DROP_MOVE, although we want to respond to it. */
  @Override
  public void dragOver(DropTargetEvent event) {
    if ((event.operations & DND.DROP_LINK) == DND.DROP_LINK)
      event.detail = DND.DROP_LINK;
  }

  @Override
  public void drop(DropTargetEvent event) {
    if (validateDrop(event))
      performDrop(event.data);
    event.detail = DND.DROP_LINK;
  }

  private boolean validateDrop(DropTargetEvent event) {
    return event.currentDataType != null && ResourceTransfer.getInstance().isSupportedType(event.currentDataType);
  }

  public boolean performDrop(Object data) {
    if (data == null)
      return false;
    if (data instanceof IResource[] sources) {
      if (sources.length == 0)
        return false;
      IResource resource = sources[0];
      // Allow all resources types through to the view, the individual pages can decide which ones to handle
      view.showHistoryPageFor(resource, true, true, null);
      return true;
    }
    return false;
  }
}
