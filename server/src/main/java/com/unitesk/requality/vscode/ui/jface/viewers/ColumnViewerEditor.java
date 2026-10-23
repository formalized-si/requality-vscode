package com.unitesk.requality.vscode.ui.jface.viewers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.MouseEvent;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;

/**
 * {@code org.eclipse.jface.viewers.ColumnViewerEditor}: activates EditingSupport (or the legacy ICellModifier) for
 * a cell, opens the inline editor in the renderer and saves the edited value.
 */
public class ColumnViewerEditor {
  public static final int DEFAULT = 1;
  public static final int TABBING_MOVE_TO_ROW_NEIGHBOR = 1 << 1;
  public static final int TABBING_CYCLE_IN_ROW = 1 << 2;
  public static final int TABBING_HORIZONTAL = 1 << 3;
  public static final int TABBING_VERTICAL = 1 << 4;
  public static final int KEYBOARD_ACTIVATION = 1 << 5;
  public static final int KEEP_EDITOR_ON_DOUBLE_CLICK = 1 << 6;

  private final ColumnViewer viewer;
  private ColumnViewerEditorActivationStrategy strategy;
  private final List<ColumnViewerEditorActivationListener> listeners = new CopyOnWriteArrayList<>();
  private Edit current;

  private static final class Edit {
    TableItem item;
    int column;
    Object element;
    CellEditor editor;
    EditingSupport support;
    String property;
  }

  protected ColumnViewerEditor(ColumnViewer viewer, ColumnViewerEditorActivationStrategy strategy, int feature) {
    this.viewer = viewer;
    this.strategy = strategy;
  }

  ColumnViewerEditor(ColumnViewer viewer) {
    this(viewer, new ColumnViewerEditorActivationStrategy(viewer), DEFAULT);
  }

  void setStrategy(ColumnViewerEditorActivationStrategy s) {
    strategy = s;
  }

  public void addEditorActivationListener(ColumnViewerEditorActivationListener l) {
    listeners.add(l);
  }

  public void removeEditorActivationListener(ColumnViewerEditorActivationListener l) {
    listeners.remove(l);
  }

  public ViewerCell getFocusCell() {
    return current == null ? null : new ViewerCell(current.item, current.column, current.element);
  }

  boolean isActive() {
    return current != null;
  }

  /** A cell was activated in the renderer. */
  void cellActivated(TableItem item, int column, String trigger) {
    Object element = item.getData();
    ColumnViewerEditorActivationEvent ev = new ColumnViewerEditorActivationEvent(new ViewerCell(item, column, element));
    Event raw = new Event();
    raw.widget = item.getParent();
    raw.button = 1;
    switch (trigger) {
      case "dblclick":
        ev.eventType = ColumnViewerEditorActivationEvent.MOUSE_DOUBLE_CLICK_SELECTION;
        ev.sourceEvent = new MouseEvent(raw);
        break;
      case "key":
        ev.eventType = ColumnViewerEditorActivationEvent.KEY_PRESSED;
        raw.keyCode = SWT.F2;
        ev.keyCode = SWT.F2;
        ev.sourceEvent = new KeyEvent(raw);
        break;
      case "programmatic":
        ev.eventType = ColumnViewerEditorActivationEvent.PROGRAMMATIC;
        break;
      default:
        ev.eventType = ColumnViewerEditorActivationEvent.MOUSE_CLICK_SELECTION;
        ev.sourceEvent = new MouseEvent(raw);
    }
    if (!strategy.accepts(ev))
      return;
    activate(item, column, element, ev);
  }

  void activate(TableItem item, int column, Object element, ColumnViewerEditorActivationEvent ev) {
    if (current != null)
      finish(false);
    Edit e = new Edit();
    e.item = item;
    e.column = column;
    e.element = element;
    ViewerColumn vc = viewer.getViewerColumn(column);
    if (vc != null && vc.getEditingSupport() != null) {
      EditingSupport es = vc.getEditingSupport();
      if (!es.canEdit(element))
        return;
      e.editor = es.getCellEditor(element);
      if (e.editor == null)
        return;
      e.support = es;
      es.initializeCellEditorValue(e.editor, new ViewerCell(item, column, element));
    } else {
      CellEditor[] editors = viewer.getCellEditors();
      String[] props = viewer.getColumnProperties();
      ICellModifier mod = viewer.getCellModifier();
      if (mod == null || editors == null || props == null || column >= editors.length || column >= props.length
          || editors[column] == null)
        return;
      if (!mod.canModify(element, props[column]))
        return;
      e.editor = editors[column];
      e.property = props[column];
      e.editor.setValue(mod.getValue(element, props[column]));
    }
    for (ColumnViewerEditorActivationListener l : listeners)
      l.beforeEditorActivated(ev);
    if (ev.cancel)
      return;
    e.editor.activate();
    current = e;
    for (ColumnViewerEditorActivationListener l : listeners)
      l.afterEditorActivated(ev);
    if (e.editor instanceof DialogCellEditor) {
      Object v = ((DialogCellEditor) e.editor).runDialog(viewer.getControl());
      if (v != null) {
        e.editor.setValue(v);
        finish(true);
      } else {
        finish(false);
      }
      return;
    }
    JsonObject spec = e.editor.clientEditor();
    // Key and focus events of the inline field go to the editor's control (listeners added by the application).
    if (spec != null && e.editor.getControl() != null)
      spec.addProperty("control", e.editor.getControl().getWidgetId());
    ((Table) viewer.getControl()).editCell(item, column, spec);
  }

  /** The renderer closed the inline editor. */
  void cellEditDone(TableItem item, int column, JsonElement value, boolean apply) {
    Edit e = current;
    if (e == null || e.item != item || e.column != column)
      return;
    if (apply)
      e.editor.setValue(e.editor.fromClient(value));
    finish(apply);
  }

  private void finish(boolean save) {
    Edit e = current;
    if (e == null)
      return;
    ColumnViewerEditorDeactivationEvent dev = new ColumnViewerEditorDeactivationEvent(this);
    boolean doSave = save && e.editor.isValueValid();
    dev.eventType = doSave ? ColumnViewerEditorDeactivationEvent.EDITOR_SAVED : ColumnViewerEditorDeactivationEvent.EDITOR_CANCELED;
    for (ColumnViewerEditorActivationListener l : listeners)
      l.beforeEditorDeactivated(dev);
    current = null;
    e.editor.deactivate();
    if (doSave) {
      if (e.support != null)
        e.support.saveCellEditorValue(e.editor, new ViewerCell(e.item, e.column, e.element));
      else
        viewer.getCellModifier().modify(e.item, e.property, e.editor.getValue());
      e.editor.fireApplyEditorValue();
    } else {
      e.editor.fireCancelEditor();
    }
    for (ColumnViewerEditorActivationListener l : listeners)
      l.afterEditorDeactivated(dev);
  }

  void editElement(ColumnViewer v, Object element, int column) {
    Object it = v.findItem(element);
    if (!(it instanceof TableItem))
      return;
    ColumnViewerEditorActivationEvent ev = new ColumnViewerEditorActivationEvent(new ViewerCell((TableItem) it, column, element));
    ev.eventType = ColumnViewerEditorActivationEvent.PROGRAMMATIC;
    activate((TableItem) it, column, element, ev);
  }

  void cancel() {
    finish(false);
  }
}
