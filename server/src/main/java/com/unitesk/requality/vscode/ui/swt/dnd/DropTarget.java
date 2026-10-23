package com.unitesk.requality.vscode.ui.swt.dnd;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;
import com.unitesk.requality.vscode.ui.workbench.ResourceTransfer;

/**
 * {@code org.eclipse.swt.dnd.DropTarget}: makes a control the target of drops. The listeners see the drag enter
 * (where they choose the data type and the operation), the drop accepted, and the drop with the data.
 * VSCODE: a drop in the webview carries the source control of a drag inside a server shell (its {@link DragSource}
 * gives the data), or the URIs of the files dragged from a VS Code view (files: FileTransfer, the workspace resources
 * of the files: ResourceTransfer, LocalSelectionTransfer) and the dragged text (TextTransfer).
 */
public class DropTarget {
  private final Control control;
  private final int style;
  private Transfer[] transfers = new Transfer[0];
  private final List<DropTargetListener> listeners = new CopyOnWriteArrayList<>();
  private boolean disposed;

  public DropTarget(Control control, int style) {
    this.control = control;
    this.style = style;
    control.setData(DND.DROP_TARGET_KEY, this);
    control.setDndRole("dropTarget", true);
    control.addDisposeListener(e -> dispose());
  }

  public Control getControl() {
    return control;
  }

  public int getStyle() {
    return style;
  }

  public void setTransfer(Transfer... transferAgents) {
    transfers = transferAgents == null ? new Transfer[0] : transferAgents;
  }

  public Transfer[] getTransfer() {
    return transfers;
  }

  public void addDropListener(DropTargetListener listener) {
    listeners.add(listener);
  }

  public void removeDropListener(DropTargetListener listener) {
    listeners.remove(listener);
  }

  public DropTargetListener[] getDropListeners() {
    return listeners.toArray(new DropTargetListener[0]);
  }

  public void dispose() {
    if (disposed)
      return;
    disposed = true;
    if (!control.isDisposed()) {
      control.setData(DND.DROP_TARGET_KEY, null);
      control.setDndRole("dropTarget", false);
    }
  }

  public boolean isDisposed() {
    return disposed;
  }

  private boolean accepts(TransferData type) {
    for (Transfer t : transfers)
      if (t.isSupportedType(type))
        return true;
    return false;
  }

  /** A drop in the webview: dragEnter, dragOver, dropAccept and drop as SWT sends them. */
  void dropped(JsonObject d) {
    String sourceId = d.has("source") && !d.get("source").isJsonNull() ? d.get("source").getAsString() : null;
    Widget sw = sourceId == null || sourceId.isEmpty() ? null : Display.getDefault().find(sourceId);
    DragSource source = sw instanceof Control ? (DragSource) ((Control) sw).getData(DND.DRAG_SOURCE_KEY) : null;
    if (source != null && !source.isDragging())
      return; // the source refused the drag (dragStart doit = false)
    List<String> paths = new ArrayList<>();
    if (source == null && d.has("uris") && d.get("uris").isJsonArray())
      for (JsonElement u : d.getAsJsonArray("uris")) {
        try {
          URI uri = new URI(u.getAsString());
          if ("file".equals(uri.getScheme()))
            paths.add(new File(uri).getAbsolutePath());
        } catch (Exception e) {
          // not a URI of a file
        }
      }
    String text = d.has("text") && !d.get("text").isJsonNull() ? d.get("text").getAsString() : "";
    IResource[] resources = resourcesOf(paths);

    List<TransferData> types = new ArrayList<>();
    int operations;
    if (source != null) {
      types.addAll(java.util.Arrays.asList(source.dataTypes()));
      operations = source.getStyle() & (DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK);
    } else {
      if (resources != null) {
        types.add(new TransferData(ResourceTransfer.TYPEID));
        types.add(new TransferData(LocalSelectionTransfer.TYPEID));
      }
      if (!paths.isEmpty())
        types.add(new TransferData(FileTransfer.TYPEID));
      if (!text.isEmpty() || !paths.isEmpty())
        types.add(new TransferData(TextTransfer.TYPEID));
      operations = DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_LINK;
    }
    TransferData current = null;
    for (TransferData t : types)
      if (accepts(t)) {
        current = t;
        break;
      }
    if (current == null)
      return;

    DropTargetEvent e = new DropTargetEvent(control);
    e.dataTypes = types.toArray(new TransferData[0]);
    e.currentDataType = current;
    e.operations = operations;
    e.detail = (style & DND.DROP_DEFAULT) != 0 ? DND.DROP_DEFAULT
        : (operations & DND.DROP_MOVE) != 0 && (style & DND.DROP_MOVE) != 0 ? DND.DROP_MOVE : DND.DROP_COPY;
    e.x = d.has("x") ? d.get("x").getAsInt() : 0;
    e.y = d.has("y") ? d.get("y").getAsInt() : 0;
    e.stateMask = d.has("stateMask") ? d.get("stateMask").getAsInt() : 0;
    String itemId = d.has("item") && !d.get("item").isJsonNull() ? d.get("item").getAsString() : null;
    e.item = itemId == null ? null : Display.getDefault().find(itemId);
    // SWT: Ctrl (Option on macOS) copies when the source allows it
    if ((e.stateMask & (com.unitesk.requality.vscode.ui.swt.SWT.CTRL | com.unitesk.requality.vscode.ui.swt.SWT.ALT)) != 0
        && (operations & DND.DROP_COPY) != 0)
      e.detail = DND.DROP_COPY;
    for (DropTargetListener l : listeners)
      l.dragEnter(e);
    if (e.detail == DND.DROP_DEFAULT)
      e.detail = (operations & DND.DROP_MOVE) != 0 ? DND.DROP_MOVE : DND.DROP_COPY;
    for (DropTargetListener l : listeners)
      l.dragOver(e);
    if (e.detail == DND.DROP_NONE || e.currentDataType == null) {
      for (DropTargetListener l : listeners)
        l.dragLeave(e);
      return;
    }
    for (DropTargetListener l : listeners)
      l.dropAccept(e);
    if (e.detail == DND.DROP_NONE)
      return;
    e.data = dataOf(e.currentDataType, source, paths, resources, text);
    if (e.data == null)
      return;
    // files dragged from VS Code: their resources are the local selection; a drag of a control of the webview has set
    // its own (its drag source, as an SWT application does in dragStart)
    if (source == null && LocalSelectionTransfer.getTransfer().isSupportedType(e.currentDataType))
      LocalSelectionTransfer.getTransfer().setSelection(new StructuredSelection(resources));
    for (DropTargetListener l : listeners)
      l.drop(e);
  }

  private static Object dataOf(TransferData type, DragSource source, List<String> paths, IResource[] resources,
      String text) {
    if (source != null)
      return source.dataFor(type);
    if (type.type == ResourceTransfer.TYPEID || type.type == LocalSelectionTransfer.TYPEID)
      return resources;
    if (type.type == FileTransfer.TYPEID)
      return paths.toArray(new String[0]);
    if (type.type == TextTransfer.TYPEID)
      return text.isEmpty() ? String.join("\n", paths) : text;
    return null;
  }

  /** The workspace resources of the dropped files, null when one of them is not in a project of the workspace. */
  private static IResource[] resourcesOf(List<String> paths) {
    if (paths.isEmpty())
      return null;
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    IResource[] r = new IResource[paths.size()];
    for (int i = 0; i < paths.size(); i++) {
      File f = new File(paths.get(i));
      IResource res = f.isDirectory() ? root.getContainerForLocation(Path.fromOSString(f.getAbsolutePath()))
          : root.getFileForLocation(Path.fromOSString(f.getAbsolutePath()));
      if (res == null || !res.exists())
        return null;
      r[i] = res;
    }
    return r;
  }
}
